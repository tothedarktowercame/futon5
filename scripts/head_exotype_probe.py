#!/usr/bin/env python3
"""E-mission-head candidate runner: turn a mission HEAD into an AIF+ lifeform seed.

v0 of the chain HEAD-text → (pattern_exotype_bridge) → 8-bit exotype (+ nearest
anchors) → readout. Deliberately uses the bridge's *default* representation so
that representation mismatches surface honestly (the xenotype path needs
IF/HOWEVER/THEN/BECAUSE sections, which a HEAD does not have — reported, not
papered over).

Usage: head_exotype_probe.py <mission.md>   (extracts the '## HEAD' section)
"""
import re
import sys
import json
import argparse
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import numpy as np
from pattern_exotype_bridge import (load_embeddings, load_anchors,
                                    train_projector, cosine_similarity)


def extract_head(md_path: Path) -> str:
    text = md_path.read_text(encoding="utf-8")
    m = re.search(r"^## HEAD.*?$(.*?)(?=^## )", text, re.M | re.S)
    if not m:
        sys.exit("no '## HEAD' section found")
    return m.group(1).strip()


def extract_pattern_sections(md_path: Path) -> dict:
    """Extract a **IF:**/**HOWEVER:**/**THEN:**/**BECAUSE:** recast block
    (the HEAD as design pattern — Golemization input)."""
    text = md_path.read_text(encoding="utf-8")
    out = {}
    for name in ("IF", "HOWEVER", "THEN", "BECAUSE"):
        m = re.search(r"\*\*" + name + r":\*\*(.*?)(?=\n\s*\n\*\*[A-Z]+:\*\*|\n#|\Z)",
                      text, re.S)
        if m:
            out[name] = " ".join(m.group(1).split())
    return out


def confidence_from_proba(proba) -> float:
    return float(np.abs(proba - 0.5).mean() * 2)


def bits_from_proba(proba) -> str:
    return "".join(str(int(x >= 0.5)) for x in proba)


def nearest_anchor(vec, anchors):
    anchor_id, anchor = max(
        anchors.items(),
        key=lambda kv: cosine_similarity(vec, kv[1]["vector"]))
    return anchor_id, float(cosine_similarity(vec, anchor["vector"]))


def anchor_proximity(per_section):
    if per_section is None:
        return None
    strong = []
    weak = []
    for name, data in per_section.items():
        target = strong if data["cos"] >= 0.4 else weak
        target.append(name)
    return {"strong": strong, "weak": weak}


def health_reading(bit_confidence, xenotype_completeness, proximity):
    if xenotype_completeness == 0.0:
        return ("No IF/HOWEVER/THEN/BECAUSE recast block found; only the "
                "whole-HEAD exotype was computed, so xenotype health is degraded.")
    strong = proximity.get("strong", []) if proximity else []
    weak = proximity.get("weak", []) if proximity else []
    if bit_confidence < 0.35:
        tone = "alive but wobbly"
    elif bit_confidence < 0.55:
        tone = "alive with moderate signal"
    else:
        tone = "alive with strong bit signal"
    return (f"{tone}: xenotype completeness {xenotype_completeness:.2f}; "
            f"strong anchors {strong or 'none'}, weak anchors {weak or 'none'}.")


def write_health_json(md_path, exotype_bits, whole_confidence, section_rows):
    per_section = None
    xenotype = None
    bit_confidence = whole_confidence
    completeness = 0.0
    if section_rows is not None:
        per_section = {
            name: {
                "bits": row["bits"],
                "conf": round(row["conf"], 2),
                "anchor": row["anchor"],
                "cos": round(row["cos"], 3),
            }
            for name, row in section_rows.items()
        }
        xenotype = "·".join(section_rows[name]["bits"]
                            for name in ["IF", "HOWEVER", "THEN", "BECAUSE"])
        bit_confidence = float(np.mean([row["conf"] for row in section_rows.values()]))
        completeness = 32 / 36
    proximity = anchor_proximity(per_section)
    payload = {
        "mission": md_path.stem,
        "generated-at": datetime.now(timezone.utc).isoformat(),
        "generator": "head_exotype_probe.py",
        "sigil": {
            "exotype": exotype_bits,
            "xenotype-32": xenotype,
            "per-section": per_section,
        },
        "health": {
            "bit-confidence": round(float(bit_confidence), 2),
            "xenotype-completeness": round(float(completeness), 2),
            "anchor-proximity": proximity,
            "reading": health_reading(float(bit_confidence), float(completeness), proximity),
        },
    }
    out = md_path.with_suffix(".health.json")
    out.write_text(json.dumps(payload, indent=2, sort_keys=False) + "\n",
                   encoding="utf-8")
    return out, payload


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mission", type=Path)
    parser.add_argument("--emit-health", action="store_true",
                        help="write <mission>.health.json next to the input markdown")
    args = parser.parse_args()

    md = args.mission
    head = extract_head(md)
    print(f"HEAD: {len(head)} chars from {md.name}")

    embeddings = load_embeddings()
    anchors = load_anchors(embeddings)
    projector = train_projector("ridge", anchors)

    from sentence_transformers import SentenceTransformer
    model = SentenceTransformer("sentence-transformers/all-MiniLM-L6-v2")
    vec = np.array(model.encode([head]), dtype=np.float32)

    proba = np.asarray(projector.predict_proba(vec)).reshape(-1)[:8]
    b = [int(p >= 0.5) for p in proba]
    bits = bits_from_proba(proba)
    confidence = confidence_from_proba(proba)  # 0=coin-flips, 1=certain
    print(f"\nexotype bits: {bits}   (bit-confidence {confidence:.2f})")
    print("  per-bit proba:", " ".join(f"{p:.2f}" for p in proba))
    print(f"  rotation={b[0]*2+b[1]} match-threshold={b[2]*2+b[3]} "
          f"invert-on-phenotype={bool(b[4])} update-prob-bit={b[5]} mix={b[6]*2+b[7]}")

    # Nearest anchors: which corner of the universal pattern space is this HEAD in?
    sims = sorted(((cosine_similarity(vec[0], a["vector"]), pid)
                   for pid, a in anchors.items()), reverse=True)
    print("\nnearest anchors (cos):")
    for s, pid in sims[:6]:
        print(f"  {s:.3f}  {pid}  [{anchors[pid]['bits']}]")

    # Representation-mismatch report (the honest part).
    print("\nrepresentation notes:")
    print("  - xenotype (36-bit) NOT computed: bridge derives it from "
          "IF/HOWEVER/THEN/BECAUSE sections; a HEAD has none. -> DERIVE question.")
    print(f"  - whole-text embedding of {len(head)} chars vs anchors trained on "
          "short pattern texts: length mismatch may flatten the projection.")
    if confidence < 0.35:
        print("  - bit-confidence is LOW: the ridge sees this text as near the "
              "decision boundary on most bits — default representation is weak here.")

    # v0.1 — Golemization path: HEAD recast as design pattern, section-wise.
    sections = extract_pattern_sections(md)
    section_rows = None
    if len(sections) == 4:
        print("\n=== v0.1 — HEAD recast as design pattern (Golemization) ===")
        names = ["IF", "HOWEVER", "THEN", "BECAUSE"]
        svecs = np.array(model.encode([sections[n] for n in names]), dtype=np.float32)
        xeno_bits = []
        confs = []
        section_rows = {}
        for i, n in enumerate(names):
            p = np.asarray(projector.predict_proba(svecs[i:i+1])).reshape(-1)[:8]
            sb = bits_from_proba(p)
            c = confidence_from_proba(p)
            confs.append(c)
            xeno_bits.append(sb)
            anchor_id, cos = nearest_anchor(svecs[i], anchors)
            section_rows[n] = {
                "bits": sb,
                "conf": c,
                "anchor": anchor_id,
                "cos": cos,
            }
            print(f"  {n:8s} {sb}  conf {c:.2f}")
        print(f"  xenotype-32: {'·'.join(xeno_bits)}   mean-conf {np.mean(confs):.2f} "
              f"(whole-text baseline {confidence:.2f})")
        # Per-section nearest anchor — does each AIF terminal land somewhere sane?
        for i, n in enumerate(names):
            row = section_rows[n]
            print(f"  {n:8s} nearest: {row['anchor']} "
                  f"(cos {row['cos']:.3f})")
    else:
        print(f"\n(no 4-section recast found in {md.name}; v0.1 skipped — {len(sections)}/4)")

    if args.emit_health:
        out, payload = write_health_json(md, bits, confidence, section_rows)
        print(f"\nhealth JSON: {out}")
        print(f"  bit-confidence {payload['health']['bit-confidence']:.2f}; "
              f"xenotype-completeness {payload['health']['xenotype-completeness']:.2f}")


if __name__ == "__main__":
    main()
