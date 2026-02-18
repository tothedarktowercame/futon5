#!/usr/bin/env python3
"""Pattern → Exotype Bridge: Hexagram Skeleton Embedding

Maps all 791 futon3 patterns to 8-bit exotype encodings and 36-bit xenotype
encodings using the hexagram/exotype skeleton as ground truth and MiniLM
embeddings for semantic similarity.

Anchor points: 64 iching hexagrams + 256 iiching exotypes = 320 patterns
with known (384-dim MiniLM vector, 8-bit encoding) pairs.

Non-anchor patterns get encodings via learned projection from anchors.
Section-level (IF/HOWEVER/THEN/BECAUSE) embeddings produce 36-bit xenotypes.

Methods:
    knn   - k-NN weighted average (baseline)
    ridge - Per-bit L2-regularized logistic regression (default)
    mlp   - Small neural network (384→64→8, sigmoid)

Usage:
    python3 scripts/pattern_exotype_bridge.py                       # ridge (default)
    python3 scripts/pattern_exotype_bridge.py --method knn          # k-NN baseline
    python3 scripts/pattern_exotype_bridge.py --method mlp          # MLP
    python3 scripts/pattern_exotype_bridge.py --validate            # with hold-out validation
    python3 scripts/pattern_exotype_bridge.py --validate --compare  # compare all methods
"""

import argparse
import json
import os
import re
import sys
import warnings
from pathlib import Path

import numpy as np

warnings.filterwarnings("ignore", category=FutureWarning, module="sklearn")
warnings.filterwarnings("ignore", message=".*ill-conditioned.*")

# ---------------------------------------------------------------------------
# Paths (relative to futon5 root)
# ---------------------------------------------------------------------------
CODE_ROOT = Path(__file__).resolve().parent.parent.parent  # /home/joe/code
FUTON3 = CODE_ROOT / "futon3"
FUTON3A = CODE_ROOT / "futon3a"
FUTON5 = CODE_ROOT / "futon5"

EMBEDDINGS_PATH = FUTON3A / "resources" / "notions" / "minilm_pattern_embeddings.json"
HEXAGRAM_DIR = FUTON3 / "library" / "iching"
EXOTYPE_DIR = FUTON3 / "library" / "iiching"
LIBRARY_DIR = FUTON3 / "library"
MANIFEST_PATH = FUTON5 / "resources" / "exotype-program-manifest.edn"
OUTPUT_EDN = FUTON5 / "resources" / "pattern-exotype-bridge.edn"
OUTPUT_TSV = FUTON5 / "resources" / "pattern-exotype-bridge.tsv"

K = 5  # number of nearest anchors


# ---------------------------------------------------------------------------
# Step 1: Load and join anchor data
# ---------------------------------------------------------------------------

def load_embeddings():
    """Load MiniLM embeddings: {pattern_id: {title, vector}}"""
    with open(EMBEDDINGS_PATH) as f:
        data = json.load(f)
    result = {}
    for entry in data:
        result[entry["id"]] = {
            "title": entry["title"],
            "vector": np.array(entry["vector"], dtype=np.float32),
        }
    return result


def parse_hexagram_bits(filepath):
    """Extract 8-bit encoding from hexagram flexiarg's @mmca-interpretation section."""
    text = filepath.read_text(encoding="utf-8")
    # Look for :bits "XXXXXXXX" inside @mmca-interpretation :sigil-encoding
    m = re.search(r'@mmca-interpretation.*?:sigil-encoding\s*\{[^}]*:bits\s+"([01]{8})"',
                  text, re.DOTALL)
    if m:
        return m.group(1)
    return None


def parse_exotype_bits(filepath):
    """Extract 8-bit encoding from iiching exotype flexiarg's @bits field."""
    text = filepath.read_text(encoding="utf-8")
    m = re.search(r'^@bits\s+([01]{8})', text, re.MULTILINE)
    if m:
        return m.group(1)
    return None


def load_anchors(embeddings):
    """Build anchor set: patterns with both embedding and known 8-bit encoding.

    Returns dict: {pattern_id: {"vector": np.array, "bits": str, "source": str}}
    """
    anchors = {}

    # Hexagrams: 64 patterns
    for fpath in sorted(HEXAGRAM_DIR.glob("hexagram-*.flexiarg")):
        pattern_id = f"iching/{fpath.stem}"
        bits = parse_hexagram_bits(fpath)
        if bits and pattern_id in embeddings:
            anchors[pattern_id] = {
                "vector": embeddings[pattern_id]["vector"],
                "bits": bits,
                "source": "hexagram",
            }

    # Exotypes: 256 patterns
    for fpath in sorted(EXOTYPE_DIR.glob("exotype-[0-9]*.flexiarg")):
        pattern_id = f"iiching/{fpath.stem}"
        bits = parse_exotype_bits(fpath)
        if bits and pattern_id in embeddings:
            anchors[pattern_id] = {
                "vector": embeddings[pattern_id]["vector"],
                "bits": bits,
                "source": "exotype",
            }

    return anchors


# ---------------------------------------------------------------------------
# Step 2: k-NN projection → 8-bit exotype
# ---------------------------------------------------------------------------

def bits_to_array(bits_str):
    """Convert '01101010' to numpy array [0, 1, 1, 0, 1, 0, 1, 0]."""
    return np.array([int(b) for b in bits_str], dtype=np.float32)


def array_to_bits(arr):
    """Quantize float array to binary string via rounding."""
    return "".join(str(int(round(max(0.0, min(1.0, v))))) for v in arr)


def cosine_similarity(a, b):
    """Cosine similarity between two vectors."""
    dot = np.dot(a, b)
    norm = np.linalg.norm(a) * np.linalg.norm(b)
    if norm < 1e-10:
        return 0.0
    return float(dot / norm)


def cosine_similarity_matrix(queries, keys):
    """Compute cosine similarity matrix between query and key matrices.

    queries: (N, D)
    keys: (M, D)
    returns: (N, M) similarity matrix
    """
    q_norm = queries / (np.linalg.norm(queries, axis=1, keepdims=True) + 1e-10)
    k_norm = keys / (np.linalg.norm(keys, axis=1, keepdims=True) + 1e-10)
    return q_norm @ k_norm.T


def knn_project(query_vector, anchor_ids, anchor_vectors, anchor_bits_arrays, k=K):
    """Project a query vector to 8-bit encoding via k-NN weighted average.

    Returns (bits_str, nearest_list, confidence).
    """
    # Compute similarities
    sims = np.array([cosine_similarity(query_vector, av) for av in anchor_vectors])

    # Top-K
    top_k_idx = np.argsort(sims)[-k:][::-1]
    top_k_sims = sims[top_k_idx]

    # Weighted average of bit encodings
    weights = np.maximum(top_k_sims, 0.0)
    weight_sum = weights.sum()
    if weight_sum < 1e-10:
        weights = np.ones(k) / k
    else:
        weights = weights / weight_sum

    weighted_bits = np.zeros(8, dtype=np.float32)
    for i, idx in enumerate(top_k_idx):
        weighted_bits += weights[i] * anchor_bits_arrays[idx]

    bits_str = array_to_bits(weighted_bits)
    confidence = float(np.mean(top_k_sims))

    nearest = []
    for idx in top_k_idx:
        nearest.append({
            "id": anchor_ids[idx],
            "similarity": round(float(sims[idx]), 4),
        })

    return bits_str, nearest, confidence


def project_all_patterns(embeddings, anchors):
    """Project all non-anchor patterns to 8-bit encodings.

    Returns dict: {pattern_id: {"exotype_8bit": str, "nearest": list, "confidence": float}}
    """
    # Prepare anchor arrays
    anchor_ids = list(anchors.keys())
    anchor_vectors = np.array([anchors[aid]["vector"] for aid in anchor_ids])
    anchor_bits_arrays = np.array([bits_to_array(anchors[aid]["bits"]) for aid in anchor_ids])

    results = {}

    # Anchors keep their own encoding
    for aid in anchor_ids:
        # Still compute nearest for display, but bits are ground truth
        sims = np.array([cosine_similarity(anchors[aid]["vector"], av) for av in anchor_vectors])
        top_k_idx = np.argsort(sims)[-K - 1:][::-1]
        # Skip self
        nearest = []
        for idx in top_k_idx:
            if anchor_ids[idx] != aid:
                nearest.append({
                    "id": anchor_ids[idx],
                    "similarity": round(float(sims[idx]), 4),
                })
            if len(nearest) >= K:
                break

        results[aid] = {
            "exotype_8bit": anchors[aid]["bits"],
            "nearest": nearest,
            "confidence": 1.0,
            "is_anchor": True,
        }

    # Non-anchors: project via k-NN
    non_anchors = {pid: emb for pid, emb in embeddings.items() if pid not in anchors}
    for pid, emb in non_anchors.items():
        bits_str, nearest, confidence = knn_project(
            emb["vector"], anchor_ids, anchor_vectors, anchor_bits_arrays
        )
        results[pid] = {
            "exotype_8bit": bits_str,
            "nearest": nearest,
            "confidence": round(confidence, 4),
            "is_anchor": False,
        }

    return results


# ---------------------------------------------------------------------------
# Step 2b: Learned projection → 8-bit exotype (ridge / mlp)
# ---------------------------------------------------------------------------

class RidgeProjector:
    """Multi-output ridge regression with optional PCA preprocessing.

    Treats bit prediction as regression (output in [0,1]), then thresholds.
    PCA handles the 384 >> N dimensionality gap, concentrating signal
    into fewer components before ridge fits.
    """

    def __init__(self, use_pca=True, n_components=32):
        self.use_pca = use_pca
        self.n_components = n_components
        self.pca = None
        self.ridge = None
        self.X_mean = None
        self.X_std = None

    def fit(self, X, Y):
        """Fit PCA + multi-output ridge regression.

        X: (N, 384) embedding vectors
        Y: (N, 8) binary targets
        """
        from sklearn.decomposition import PCA
        from sklearn.linear_model import RidgeCV

        # Standardize
        self.X_mean = X.mean(axis=0)
        self.X_std = X.std(axis=0) + 1e-8
        X_scaled = (X - self.X_mean) / self.X_std

        if self.use_pca:
            n_comp = min(self.n_components, X.shape[0] - 1, X.shape[1])
            self.pca = PCA(n_components=n_comp)
            X_proj = self.pca.fit_transform(X_scaled)
        else:
            X_proj = X_scaled

        self.ridge = RidgeCV(alphas=np.logspace(-3, 3, 20), cv=5)
        self.ridge.fit(X_proj, Y)

    def predict_proba(self, X):
        """Predict continuous values for each bit. Returns (N, 8) clipped to [0,1]."""
        X_scaled = (X - self.X_mean) / self.X_std
        if self.use_pca:
            X_proj = self.pca.transform(X_scaled)
        else:
            X_proj = X_scaled
        raw = self.ridge.predict(X_proj)
        return np.clip(raw, 0, 1).astype(np.float32)

    def predict_bits(self, X):
        """Predict binary strings for each row."""
        probs = self.predict_proba(X)
        return probs, [(probs[i] >= 0.5).astype(int) for i in range(X.shape[0])]


class MLPProjector:
    """Small MLP: 384 → 64 → 8, trained with BCE loss."""

    def __init__(self):
        self.model = None
        self.device = "cpu"

    def fit(self, X, Y, epochs=300, lr=1e-3, weight_decay=1e-3):
        import torch
        import torch.nn as nn

        self.device = "cpu"
        X_t = torch.tensor(X, dtype=torch.float32, device=self.device)
        Y_t = torch.tensor(Y, dtype=torch.float32, device=self.device)

        self.model = nn.Sequential(
            nn.Linear(384, 64),
            nn.ReLU(),
            nn.Dropout(0.3),
            nn.Linear(64, 8),
            nn.Sigmoid(),
        ).to(self.device)

        optimizer = torch.optim.Adam(self.model.parameters(), lr=lr, weight_decay=weight_decay)
        criterion = nn.BCELoss()

        self.model.train()
        for epoch in range(epochs):
            optimizer.zero_grad()
            pred = self.model(X_t)
            loss = criterion(pred, Y_t)
            loss.backward()
            optimizer.step()

    def predict_proba(self, X):
        import torch
        self.model.eval()
        with torch.no_grad():
            X_t = torch.tensor(X, dtype=torch.float32, device=self.device)
            probs = self.model(X_t).cpu().numpy()
        return probs

    def predict_bits(self, X):
        probs = self.predict_proba(X)
        return probs, [(probs[i] >= 0.5).astype(int) for i in range(X.shape[0])]


def train_projector(method, anchors):
    """Train a projector on anchor data. Returns the fitted projector."""
    anchor_ids = list(anchors.keys())
    X = np.array([anchors[aid]["vector"] for aid in anchor_ids])
    Y = np.array([bits_to_array(anchors[aid]["bits"]) for aid in anchor_ids])

    if method == "ridge":
        proj = RidgeProjector()
        proj.fit(X, Y)
    elif method == "mlp":
        proj = MLPProjector()
        proj.fit(X, Y)
    else:
        raise ValueError(f"Unknown method: {method}")

    return proj


def project_all_patterns_ml(embeddings, anchors, projector):
    """Project all patterns using a trained ML projector.

    Returns dict: {pattern_id: {"exotype_8bit": str, "nearest": list, "confidence": float}}
    """
    # Prepare anchor data for nearest-neighbor display
    anchor_ids = list(anchors.keys())
    anchor_vectors = np.array([anchors[aid]["vector"] for aid in anchor_ids])

    results = {}

    # All pattern vectors
    all_pids = list(embeddings.keys())
    all_vecs = np.array([embeddings[pid]["vector"] for pid in all_pids])

    # Predict
    probs, bits_list = projector.predict_bits(all_vecs)

    # Compute nearest anchors for display (batch cosine similarity)
    sim_matrix = cosine_similarity_matrix(all_vecs, anchor_vectors)

    for i, pid in enumerate(all_pids):
        is_anchor = pid in anchors
        if is_anchor:
            bits_str = anchors[pid]["bits"]
            confidence = 1.0
        else:
            bits_str = array_to_bits(bits_list[i])
            # Confidence: mean probability of predicted bits (how decisive the model is)
            bit_probs = probs[i]
            confidence = round(float(np.mean(np.maximum(bit_probs, 1 - bit_probs))), 4)

        # Top-K nearest anchors
        sims = sim_matrix[i]
        top_k_idx = np.argsort(sims)[::-1]
        nearest = []
        for idx in top_k_idx:
            if is_anchor and anchor_ids[idx] == pid:
                continue
            nearest.append({
                "id": anchor_ids[idx],
                "similarity": round(float(sims[idx]), 4),
            })
            if len(nearest) >= K:
                break

        results[pid] = {
            "exotype_8bit": bits_str,
            "nearest": nearest,
            "confidence": confidence,
            "is_anchor": is_anchor,
        }

    return results


# ---------------------------------------------------------------------------
# Step 3: Section-level embedding → 36-bit xenotype
# ---------------------------------------------------------------------------

SECTION_PATTERN = re.compile(
    r'^\s+\+\s+(IF|HOWEVER|THEN|BECAUSE)\s*:\s*\n?(.*?)(?=\n\s+\+\s+(?:IF|HOWEVER|THEN|BECAUSE|NEXT-STEPS|evidence)|(?:\n@|\Z))',
    re.MULTILINE | re.DOTALL | re.IGNORECASE
)


def parse_sections(filepath):
    """Extract IF/HOWEVER/THEN/BECAUSE text from a flexiarg file.

    Returns dict with keys 'IF', 'HOWEVER', 'THEN', 'BECAUSE' (uppercase).
    Missing sections are None.
    """
    text = filepath.read_text(encoding="utf-8")
    sections = {"IF": None, "HOWEVER": None, "THEN": None, "BECAUSE": None}

    for m in SECTION_PATTERN.finditer(text):
        key = m.group(1).upper()
        if key in sections:
            # Clean up the extracted text
            raw = m.group(2).strip()
            # Remove leading indentation and + evidence lines
            lines = []
            for line in raw.split("\n"):
                stripped = line.strip()
                if stripped.startswith("+ evidence:"):
                    break
                lines.append(stripped)
            sections[key] = " ".join(lines).strip()

    return sections


def find_flexiarg(pattern_id):
    """Find the flexiarg file for a pattern ID."""
    # pattern_id like "software-design/adapter-pattern" → library/software-design/adapter-pattern.flexiarg
    fpath = LIBRARY_DIR / f"{pattern_id}.flexiarg"
    if fpath.exists():
        return fpath
    return None


def load_all_sections(embeddings):
    """Load section text for all patterns that have flexiarg files.

    Returns dict: {pattern_id: {"IF": str, "HOWEVER": str, "THEN": str, "BECAUSE": str}}
    """
    all_sections = {}
    for pid in embeddings:
        fpath = find_flexiarg(pid)
        if fpath:
            sections = parse_sections(fpath)
            if any(v is not None for v in sections.values()):
                all_sections[pid] = sections
    return all_sections


def embed_sections(all_sections, embeddings, anchors, method="knn"):
    """Embed section-level text with MiniLM and project each to 8 bits.

    Uses sentence-transformers only for patterns that have section text.
    Falls back to full-pattern encoding for missing sections.

    For method='ridge' or 'mlp', trains a per-section projector on anchor
    section embeddings. For 'knn', uses k-NN weighted projection.

    Returns dict: {pattern_id: {"section_bits": {section: str}}}
    """
    from sentence_transformers import SentenceTransformer

    section_names = ["IF", "HOWEVER", "THEN", "BECAUSE"]
    texts_to_embed = {}  # (pid, section) -> text

    for pid, sections in all_sections.items():
        for sname in section_names:
            if sections.get(sname):
                texts_to_embed[(pid, sname)] = sections[sname]

    if not texts_to_embed:
        return {}

    # Batch embed all section texts
    print(f"  Embedding {len(texts_to_embed)} section texts with MiniLM...")
    model = SentenceTransformer("sentence-transformers/all-MiniLM-L6-v2")

    keys = list(texts_to_embed.keys())
    texts = [texts_to_embed[k] for k in keys]
    vectors = model.encode(texts, show_progress_bar=True, batch_size=64)

    section_vectors = {}
    for i, k in enumerate(keys):
        section_vectors[k] = vectors[i].astype(np.float32)

    # Collect and embed anchor section texts
    print("  Embedding anchor section texts...")
    anchor_section_texts = {}
    for aid in anchors:
        fpath = find_flexiarg(aid)
        if fpath:
            sections = parse_sections(fpath)
            for sname in section_names:
                if sections.get(sname):
                    anchor_section_texts[(aid, sname)] = sections[sname]

    a_keys = list(anchor_section_texts.keys())
    anchor_section_vectors = {}
    if a_keys:
        a_texts = [anchor_section_texts[k] for k in a_keys]
        a_vectors = model.encode(a_texts, show_progress_bar=False, batch_size=64)
        for i, k in enumerate(a_keys):
            anchor_section_vectors[k] = a_vectors[i].astype(np.float32)

    # For ML methods, train a per-section projector
    section_projectors = {}
    if method in ("ridge", "mlp"):
        for sname in section_names:
            pool_vecs = []
            pool_bits = []
            for aid in anchors:
                if (aid, sname) in anchor_section_vectors:
                    pool_vecs.append(anchor_section_vectors[(aid, sname)])
                    pool_bits.append(bits_to_array(anchors[aid]["bits"]))
            if len(pool_vecs) >= 20:  # need minimum samples to train
                X = np.array(pool_vecs)
                Y = np.array(pool_bits)
                if method == "ridge":
                    proj = RidgeProjector()
                else:
                    proj = MLPProjector()
                proj.fit(X, Y)
                section_projectors[sname] = proj

    results = {}

    for pid in all_sections:
        section_bits = {}

        for sname in section_names:
            if (pid, sname) not in section_vectors:
                section_bits[sname] = None
                continue

            query_vec = section_vectors[(pid, sname)]

            if sname in section_projectors:
                # ML projection
                probs, bits_list = section_projectors[sname].predict_bits(
                    query_vec.reshape(1, -1)
                )
                section_bits[sname] = array_to_bits(bits_list[0])
            else:
                # k-NN fallback
                pool_ids = []
                pool_vecs = []
                pool_bits = []
                for aid in anchors:
                    if (aid, sname) in anchor_section_vectors:
                        pool_ids.append(aid)
                        pool_vecs.append(anchor_section_vectors[(aid, sname)])
                        pool_bits.append(bits_to_array(anchors[aid]["bits"]))

                if len(pool_ids) >= K:
                    bits_str, _, _ = knn_project(
                        query_vec, pool_ids, np.array(pool_vecs),
                        np.array(pool_bits), k=K
                    )
                    section_bits[sname] = bits_str
                else:
                    section_bits[sname] = None

        results[pid] = {"section_bits": section_bits}

    return results


def assemble_xenotype(exotype_8bit, section_bits):
    """Assemble 36-bit xenotype from section-level bits.

    Format: IF(8) + HOWEVER(8) + THEN(8) + BECAUSE(8) + phenotype_nibble(4) = 36 bits
    Missing sections fall back to the pattern's overall 8-bit exotype.
    Phenotype nibble = XOR of each byte's low 2 bits.
    """
    section_names = ["IF", "HOWEVER", "THEN", "BECAUSE"]
    bytes_list = []

    for sname in section_names:
        if section_bits.get(sname):
            bytes_list.append(section_bits[sname])
        else:
            bytes_list.append(exotype_8bit)

    # Phenotype nibble: XOR of each byte's low 2 bits, giving 4*(2 bits) = 8 bits,
    # then take the low 4 bits. Simpler: XOR all 4 bytes, take low nibble.
    xor_val = 0
    for b in bytes_list:
        xor_val ^= int(b, 2)
    phenotype = format(xor_val & 0x0F, "04b")

    xenotype = " ".join(bytes_list) + " " + phenotype
    return xenotype


# ---------------------------------------------------------------------------
# Step 4: Output
# ---------------------------------------------------------------------------

def edn_str(s):
    """Escape a string for EDN output."""
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def write_edn(results, embeddings, section_data, n_anchors, output_path, method="ridge"):
    """Write the pattern-exotype-bridge.edn file."""
    lines = []
    lines.append("{:meta {:generated \"2026-02-16\"")
    lines.append("        :model \"all-MiniLM-L6-v2\"")
    lines.append(f"        :method \"{method}\"")
    lines.append(f"        :n-anchors {n_anchors}")
    lines.append(f"        :n-patterns {len(results)}")
    lines.append(f"        :k {K}}}")
    lines.append(" :patterns")
    lines.append(" [")

    for pid in sorted(results.keys()):
        r = results[pid]
        title = embeddings[pid]["title"]
        xenotype = section_data.get(pid, {}).get("xenotype_36bit", r["exotype_8bit"] * 4 + " 0000")

        lines.append(f"  {{:pattern-id {edn_str(pid)}")
        lines.append(f"   :title {edn_str(title)}")
        lines.append(f"   :exotype-8bit {edn_str(r['exotype_8bit'])}")
        lines.append(f"   :xenotype-36bit {edn_str(xenotype)}")
        lines.append(f"   :is-anchor {str(r.get('is_anchor', False)).lower()}")
        lines.append(f"   :confidence {r['confidence']}")

        # Nearest hexagrams (limit to K)
        nearest = r.get("nearest", [])[:K]
        if nearest:
            lines.append("   :nearest-hexagrams [")
            for n in nearest:
                lines.append(f"     {{:id {edn_str(n['id'])} :similarity {n['similarity']}}}")
            lines.append("   ]")

        lines.append("  }")

    lines.append(" ]}")

    output_path.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {output_path} ({len(results)} patterns)")


def write_tsv(results, embeddings, section_data, output_path):
    """Write human-readable TSV for inspection."""
    lines = []
    lines.append("\t".join([
        "pattern_id", "title", "exotype_8bit", "xenotype_36bit",
        "is_anchor", "confidence", "nearest_1", "sim_1", "nearest_2", "sim_2"
    ]))

    for pid in sorted(results.keys()):
        r = results[pid]
        title = embeddings[pid]["title"]
        xenotype = section_data.get(pid, {}).get("xenotype_36bit", "")
        nearest = r.get("nearest", [])

        n1_id = nearest[0]["id"] if len(nearest) > 0 else ""
        n1_sim = str(nearest[0]["similarity"]) if len(nearest) > 0 else ""
        n2_id = nearest[1]["id"] if len(nearest) > 1 else ""
        n2_sim = str(nearest[1]["similarity"]) if len(nearest) > 1 else ""

        lines.append("\t".join([
            pid, title, r["exotype_8bit"], xenotype,
            str(r.get("is_anchor", False)), str(r["confidence"]),
            n1_id, n1_sim, n2_id, n2_sim
        ]))

    output_path.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {output_path} ({len(results)} rows)")


# ---------------------------------------------------------------------------
# Step 5: Validation
# ---------------------------------------------------------------------------

def hamming_distance(a, b):
    """Hamming distance between two bit strings."""
    return sum(x != y for x, y in zip(a, b))


def validate_holdout(embeddings, anchors, method="knn", n_folds=10, verbose=True):
    """Hold-out validation: leave out hexagrams, predict their encodings."""
    hexagram_ids = [aid for aid in anchors if anchors[aid]["source"] == "hexagram"]
    np.random.seed(42)
    np.random.shuffle(hexagram_ids)

    fold_size = max(1, len(hexagram_ids) // n_folds)
    holdout = hexagram_ids[:fold_size]
    train_anchors = {aid: anchors[aid] for aid in anchors if aid not in holdout}

    if verbose:
        print(f"\n=== Hold-out Validation ({method}) ===")
        print(f"Held out {len(holdout)} hexagrams, training on {len(train_anchors)} anchors")

    if method == "knn":
        anchor_ids = list(train_anchors.keys())
        anchor_vectors = np.array([train_anchors[aid]["vector"] for aid in anchor_ids])
        anchor_bits_arrays = np.array([bits_to_array(train_anchors[aid]["bits"]) for aid in anchor_ids])

        distances = []
        for hid in holdout:
            true_bits = anchors[hid]["bits"]
            pred_bits, nearest, conf = knn_project(
                embeddings[hid]["vector"], anchor_ids, anchor_vectors, anchor_bits_arrays
            )
            dist = hamming_distance(true_bits, pred_bits)
            distances.append(dist)
            if verbose:
                print(f"  {hid}: true={true_bits} pred={pred_bits} hamming={dist} "
                      f"conf={conf:.3f} nearest={nearest[0]['id']}")
    else:
        projector = train_projector(method, train_anchors)
        distances = []
        for hid in holdout:
            true_bits = anchors[hid]["bits"]
            vec = embeddings[hid]["vector"].reshape(1, -1)
            probs, bits_list = projector.predict_bits(vec)
            pred_bits = array_to_bits(bits_list[0])
            dist = hamming_distance(true_bits, pred_bits)
            distances.append(dist)
            if verbose:
                prob_str = " ".join(f"{p:.2f}" for p in probs[0])
                print(f"  {hid}: true={true_bits} pred={pred_bits} hamming={dist} "
                      f"probs=[{prob_str}]")

    mean_dist = np.mean(distances)
    if verbose:
        print(f"\nMean hamming distance: {mean_dist:.2f} / 8 bits")
        print(f"Bit accuracy: {1 - mean_dist / 8:.1%}")
    return mean_dist


def validate_holdout_cv(embeddings, anchors, method="knn", n_folds=10):
    """Full cross-validation over hexagram holdouts."""
    hexagram_ids = [aid for aid in anchors if anchors[aid]["source"] == "hexagram"]
    np.random.seed(42)
    np.random.shuffle(hexagram_ids)

    fold_size = max(1, len(hexagram_ids) // n_folds)
    all_distances = []

    for fold in range(n_folds):
        start = fold * fold_size
        end = min(start + fold_size, len(hexagram_ids))
        holdout = hexagram_ids[start:end]
        if not holdout:
            continue

        train_anchors = {aid: anchors[aid] for aid in anchors if aid not in holdout}

        if method == "knn":
            anchor_ids = list(train_anchors.keys())
            anchor_vectors = np.array([train_anchors[aid]["vector"] for aid in anchor_ids])
            anchor_bits_arrays = np.array([bits_to_array(train_anchors[aid]["bits"]) for aid in anchor_ids])

            for hid in holdout:
                true_bits = anchors[hid]["bits"]
                pred_bits, _, _ = knn_project(
                    embeddings[hid]["vector"], anchor_ids, anchor_vectors, anchor_bits_arrays
                )
                all_distances.append(hamming_distance(true_bits, pred_bits))
        else:
            projector = train_projector(method, train_anchors)
            for hid in holdout:
                true_bits = anchors[hid]["bits"]
                vec = embeddings[hid]["vector"].reshape(1, -1)
                _, bits_list = projector.predict_bits(vec)
                pred_bits = array_to_bits(bits_list[0])
                all_distances.append(hamming_distance(true_bits, pred_bits))

    return np.mean(all_distances), np.std(all_distances)


def compare_methods(embeddings, anchors):
    """Run cross-validated comparison of all methods."""
    print(f"\n{'='*60}")
    print("Method Comparison (10-fold CV over hexagram holdouts)")
    print(f"{'='*60}")

    for method in ["knn", "ridge", "mlp"]:
        mean_dist, std_dist = validate_holdout_cv(embeddings, anchors, method=method)
        bit_acc = 1 - mean_dist / 8
        print(f"  {method:6s}: hamming={mean_dist:.2f}±{std_dist:.2f}  "
              f"bit_acc={bit_acc:.1%}")

    print()


def validate_domain_coherence(results, embeddings):
    """Check that patterns in the same domain get similar encodings."""
    print(f"\n=== Domain Coherence ===")

    # Group by namespace
    domains = {}
    for pid in results:
        if "/" in pid:
            ns = pid.split("/")[0]
            domains.setdefault(ns, []).append(pid)

    for ns in sorted(domains.keys()):
        pids = domains[ns]
        if len(pids) < 3:
            continue

        bits_arrays = [bits_to_array(results[pid]["exotype_8bit"]) for pid in pids]
        # Compute mean pairwise hamming distance within domain
        total_dist = 0
        count = 0
        for i in range(len(bits_arrays)):
            for j in range(i + 1, len(bits_arrays)):
                total_dist += np.sum(np.abs(bits_arrays[i] - bits_arrays[j]))
                count += 1
        mean_intra = total_dist / max(count, 1)

        print(f"  {ns}: {len(pids)} patterns, mean intra-domain hamming={mean_intra:.2f}")


def validate_semantic_sanity(results, embeddings):
    """Spot-check: print nearest hexagram for a few non-anchor patterns."""
    print(f"\n=== Semantic Sanity Spot-Check ===")

    # Pick some interesting non-anchor patterns
    spot_checks = [
        "software-design/adapter-pattern",
        "ants/cargo-return-discipline",
        "ants/baseline-cyber-ant",
        "paramitas/equanimity",
        "gauntlet/umwelt-not-architecture",
        "meta/designing-thoughts-not-text",
    ]

    for pid in spot_checks:
        if pid in results and not results[pid].get("is_anchor"):
            r = results[pid]
            nearest = r.get("nearest", [])
            hex_nearest = [n for n in nearest if n["id"].startswith("iching/")]
            top_hex = hex_nearest[0] if hex_nearest else nearest[0] if nearest else {"id": "?", "similarity": 0}
            print(f"  {pid}")
            print(f"    → exotype: {r['exotype_8bit']} (conf={r['confidence']})")
            print(f"    → nearest hexagram: {top_hex['id']} (sim={top_hex['similarity']})")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Pattern → Exotype Bridge")
    parser.add_argument("--method", choices=["knn", "ridge", "mlp"], default="ridge",
                        help="Projection method (default: ridge)")
    parser.add_argument("--validate", action="store_true", help="Run validation checks")
    parser.add_argument("--compare", action="store_true",
                        help="Compare all methods (requires --validate)")
    parser.add_argument("--no-sections", action="store_true",
                        help="Skip section-level embedding (faster, 8-bit only)")
    args = parser.parse_args()

    method = args.method

    print("=" * 60)
    print("Pattern → Exotype Bridge: Hexagram Skeleton Embedding")
    print(f"  method={method}")
    print("=" * 60)

    # Step 1: Load data
    print("\n[Step 1] Loading embeddings and anchor data...")
    embeddings = load_embeddings()
    print(f"  Loaded {len(embeddings)} pattern embeddings")

    anchors = load_anchors(embeddings)
    n_hex = sum(1 for a in anchors.values() if a["source"] == "hexagram")
    n_exo = sum(1 for a in anchors.values() if a["source"] == "exotype")
    print(f"  Anchors: {len(anchors)} ({n_hex} hexagrams + {n_exo} exotypes)")

    # Step 2: Project all patterns to 8-bit exotype encodings
    if method == "knn":
        print(f"\n[Step 2] k-NN projection (k={K})...")
        results = project_all_patterns(embeddings, anchors)
    else:
        print(f"\n[Step 2] Training {method} projector on {len(anchors)} anchors...")
        projector = train_projector(method, anchors)
        print(f"  Projecting all patterns...")
        results = project_all_patterns_ml(embeddings, anchors, projector)

    n_projected = sum(1 for r in results.values() if not r.get("is_anchor"))
    print(f"  Projected {n_projected} non-anchor patterns")

    # Step 3: Section-level xenotype
    section_data = {}
    if not args.no_sections:
        print(f"\n[Step 3] Building 36-bit xenotype encodings from section text (method={method})...")
        all_sections = load_all_sections(embeddings)
        print(f"  Found section text for {len(all_sections)} patterns")

        section_results = embed_sections(all_sections, embeddings, anchors, method=method)
        print(f"  Computed section-level bits for {len(section_results)} patterns")

        for pid in results:
            exotype_8bit = results[pid]["exotype_8bit"]
            sbits = section_results.get(pid, {}).get("section_bits", {})
            xenotype = assemble_xenotype(exotype_8bit, sbits)
            section_data[pid] = {"xenotype_36bit": xenotype, "section_bits": sbits}
    else:
        print("\n[Step 3] Skipped (--no-sections)")
        for pid in results:
            exotype_8bit = results[pid]["exotype_8bit"]
            xenotype = " ".join([exotype_8bit] * 4) + " " + format(int(exotype_8bit, 2) & 0x0F, "04b")
            section_data[pid] = {"xenotype_36bit": xenotype}

    # Step 4: Output
    print(f"\n[Step 4] Writing output files...")
    write_edn(results, embeddings, section_data, len(anchors), OUTPUT_EDN, method=method)
    write_tsv(results, embeddings, section_data, OUTPUT_TSV)

    # Step 5: Validation
    if args.validate:
        print(f"\n[Step 5] Running validation...")
        if args.compare:
            compare_methods(embeddings, anchors)
        else:
            validate_holdout(embeddings, anchors, method=method)
        validate_domain_coherence(results, embeddings)
        validate_semantic_sanity(results, embeddings)
    else:
        print(f"\n[Summary]")
        validate_semantic_sanity(results, embeddings)

    print(f"\nDone. Output: {OUTPUT_EDN}")


if __name__ == "__main__":
    main()
