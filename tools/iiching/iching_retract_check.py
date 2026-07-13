#!/usr/bin/env python3
"""iching_retract_check.py — validate Joe's claim: the RETRACT belongs on the classical iching
hexagram patterns (Taoist text -> flexiarg), whose RICH grounded text should embed cleanly --- unlike
the bare CT-concept names that gave 2/8. The retract lands a turn on a hexagram (= a 6-bit code = a
CT-atom by @binary); the CT meaning is added by the LIFT to iiching.

Measures: (1) cell spread among the 64 hexagram patterns; (2) HELD-OUT self-recovery — split each
pattern's prose in half, anchor on half-A, retract half-B, does it land on its own hexagram (top1/top3)?
claude-6 + Joe, 2026-06-02.
  futon3a/.venv/bin/python iching_retract_check.py
"""
import re, pathlib
import numpy as np
from sentence_transformers import SentenceTransformer

ICHING = pathlib.Path("/home/joe/code/futon3/library/iching")
pats = sorted(ICHING.glob("hexagram-*.flexiarg"))
names, binary, bodies = [], [], []
for p in pats:
    txt = p.read_text(errors="ignore")
    b = re.search(r"@binary (\d{6})", txt)
    title = re.search(r"@title (.+)", txt)
    body = "\n".join(l for l in txt.splitlines() if not l.startswith("@") and l.strip())
    names.append(title.group(1).strip() if title else p.stem)
    binary.append(b.group(1) if b else "??????")
    bodies.append(re.sub(r"\s+", " ", body))

model = SentenceTransformer("sentence-transformers/all-MiniLM-L6-v2")
def emb(t): return np.asarray(model.encode(t, normalize_embeddings=True))

# halves for held-out test
def half(s, first):
    w = s.split(); m = len(w) // 2
    return " ".join(w[:m] if first else w[m:])
A = emb([half(b, True) for b in bodies])    # anchors (half A)
B = emb([half(b, False) for b in bodies])    # probes  (half B)
full = emb(bodies)

iu = np.triu_indices(64, 1)
S = full @ full.T
print(f"[iching cell spread] 64 hexagram patterns: pairwise cos median={np.median(S[iu]):.3f} "
      f"p90={np.percentile(S[iu],90):.3f}   (CT-name retract was 0.222 median)")

sims = B @ A.T
top1 = sum(int(sims[i].argmax()) == i for i in range(64))
top3 = sum(i in sims[i].argsort()[-3:] for i in range(64))
print(f"[held-out self-recovery] half-B retracts to OWN hexagram via half-A anchor: "
      f"top1={top1}/64 ({100*top1/64:.0f}%)  top3={top3}/64 ({100*top3/64:.0f}%)   (CT-name was 58% top1)")

# show a few hexagram -> 6-bit code (= CT-atom address) linkages
print("\n[hexagram = 6-bit code = CT-atom address]")
for i in (0, 1, 28, 63):
    print(f"  {binary[i]}  {names[i]}")
