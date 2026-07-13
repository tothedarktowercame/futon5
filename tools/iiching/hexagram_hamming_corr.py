#!/usr/bin/env python3
"""Place-neuron premise test: does I Ching hexagram MEANING-distance correlate with
hexagram HAMMING-distance? (turn: 2026-06-12)

If Hamming-near hexagrams are also meaning-near, then matching CT concepts to hexagrams
by meaning inherits good Hamming locality "for free" (Joe's place-neuron bet). If not, no
projection gives it for free.

Embeds the 64 rich flexiarg texts (futon3/library/iching/) with BGE — title + prose body
only, with @binary/@trigrams/@number STRIPPED so the structural code can't leak into the
embedding. Correlates pairwise cosine-distance vs Hamming-distance of the @binary codes.

Run: /home/joe/code/.venv/bin/python -P futon5/tools/iiching/hexagram_hamming_corr.py
"""
import pathlib, re, sys
import numpy as np
from itertools import combinations

LIB = pathlib.Path("/home/joe/code/futon3/library/iching")
STRIP_PREFIXES = ("@binary", "@trigrams", "@number", "@sigils", "@flexiarg",
                  "@audience", "@tone", "@style")

def load():
    rows = []
    for f in sorted(LIB.glob("hexagram-*.flexiarg")):
        txt = f.read_text()
        m = re.search(r"@binary\s+([01]{6})", txt)
        if not m:
            print(f"  WARN no @binary in {f.name}", file=sys.stderr); continue
        code = m.group(1)
        # embed-text: keep @title + prose, drop structural metadata lines
        keep = [ln for ln in txt.splitlines()
                if not ln.strip().startswith(STRIP_PREFIXES)]
        rows.append({"name": f.stem, "code": code, "text": "\n".join(keep).strip()})
    return rows

def hamming(a, b):
    return sum(x != y for x, y in zip(a, b))

def main():
    rows = load()
    print(f"loaded {len(rows)} hexagrams")
    from sentence_transformers import SentenceTransformer
    model = SentenceTransformer("BAAI/bge-large-en-v1.5")  # documents: NO query prefix
    V = model.encode([r["text"] for r in rows], normalize_embeddings=True,
                     batch_size=16, show_progress_bar=False)
    V = np.asarray(V)
    pairs = list(combinations(range(len(rows)), 2))
    cos_d = np.array([1.0 - float(V[i] @ V[j]) for i, j in pairs])      # cosine distance
    ham_d = np.array([hamming(rows[i]["code"], rows[j]["code"]) for i, j in pairs])
    # correlations
    from scipy.stats import spearmanr, pearsonr
    sr = spearmanr(ham_d, cos_d); pr = pearsonr(ham_d, cos_d)
    print(f"\nPAIRS={len(pairs)}")
    print(f"Spearman(ham, cos-dist) = {sr.statistic:+.4f}  (p={sr.pvalue:.2e})")
    print(f"Pearson (ham, cos-dist) = {pr.statistic:+.4f}  (p={pr.pvalue:.2e})")
    print("  (positive ⇒ Hamming-near hexagrams ARE meaning-near ⇒ place-neuron viable)")
    print(f"  baseline reference: codebook nlab-graph hamming-vs-dissimilarity = 0.27")
    # mean cosine-dist by hamming bucket (the shape of the relationship)
    print("\nmean cosine-distance by Hamming bucket:")
    for h in range(1, 7):
        sel = cos_d[ham_d == h]
        if len(sel):
            print(f"  ham={h}: n={len(sel):4d}  mean_cos_d={sel.mean():.4f}")
    # a few nearest-meaning pairs and their Hamming
    order = np.argsort(cos_d)
    print("\n10 nearest-MEANING hexagram pairs (are they Hamming-near?):")
    for k in order[:10]:
        i, j = pairs[k]
        print(f"  {rows[i]['name'][:22]:22} ~ {rows[j]['name'][:22]:22}  "
              f"cos_d={cos_d[k]:.3f} ham={ham_d[k]}")

if __name__ == "__main__":
    main()
