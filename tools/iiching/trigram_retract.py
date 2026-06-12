#!/usr/bin/env python3
"""trigram_retract.py — the FACTORED retract Joe specified: a turn's OPERATOR -> upper trigram,
AGENT -> lower trigram, compose -> hexagram (6-bit) -> CT-atom -> (lift) iiching. Classifying each
half against only 8 well-separated trigrams sidesteps the 64-way clumping (0.569) that sank the
flat text-retract.

Convention (locked from hexagram 11 Tài @binary 000111 @trigrams [earth/heaven]):
  @binary = upper(3) ++ lower(3);  @trigrams = [upper, lower].
Trigram 3-bit codes are read off the 8 doubled hexagrams.
Validates: (1) 8-trigram spread; (2) self-recovery — each doubled-hexagram BODY (held-out, not the
descriptor) classifies to its own trigram (expect ~8/8); (3) demo compositions. claude-6 + Joe 2026-06-02.
  futon3a/.venv/bin/python trigram_retract.py
"""
import re, pathlib, json
import numpy as np
from sentence_transformers import SentenceTransformer

ICHING = pathlib.Path("/home/joe/code/futon3/library/iching")
OUT = pathlib.Path("/home/joe/code/futon5/resources/iiching-ct")

# 8 trigrams: (name, 3-bit code from doubled hexagram, standard-attribute descriptor — NOT copied
# from the flexiarg, so the self-recovery probe below is a fair held-out test)
TRIGRAMS = [
    ("heaven",   "111", "the creative: strong active initiating force, pure assertion, driving will, origination"),
    ("earth",    "000", "the receptive: yielding devoted support, nurturing, giving form, holding and carrying"),
    ("thunder",  "100", "the arousing: sudden movement and shock, the impulse to begin, startling initiative"),
    ("water",    "010", "the abysmal: danger and depth, flowing through difficulty, the unfathomable, risk"),
    ("mountain", "001", "keeping still: stopping, stability, rest, holding a boundary, immovable calm"),
    ("wind",     "011", "the gentle: penetrating gradual influence, dispersal, flexible persistent shaping"),
    ("fire",     "101", "the clinging: light and clarity, illumination, attachment, brightness and dependence"),
    ("lake",     "110", "the joyous: openness and pleasure, exchange, communication, satisfaction and ease"),
]
tnames = [t[0] for t in TRIGRAMS]
tbits = {t[0]: t[1] for t in TRIGRAMS}
bits2tri = {t[1]: t[0] for t in TRIGRAMS}

# 64 hexagram lookup by @binary
hexbin = {}
for p in ICHING.glob("hexagram-*.flexiarg"):
    txt = p.read_text(errors="ignore")
    b = re.search(r"@binary (\d{6})", txt); ti = re.search(r"@title (.+)", txt)
    if b: hexbin[b.group(1)] = (ti.group(1).strip() if ti else p.stem, p)

model = SentenceTransformer("sentence-transformers/all-MiniLM-L6-v2")
def emb(t): return np.asarray(model.encode(t, normalize_embeddings=True))

T = emb([t[2] for t in TRIGRAMS])
iu = np.triu_indices(8, 1); S = T @ T.T
print(f"[8-trigram spread] pairwise cos median={np.median(S[iu]):.3f} max={S[iu].max():.3f}   "
      f"(vs flat 64-hexagram text-retract median 0.569)")

# self-recovery: doubled-hexagram BODY -> nearest trigram (held-out: descriptor != flexiarg text)
doubled = {"111111": "heaven", "000000": "earth", "100100": "thunder", "010010": "water",
           "001001": "mountain", "011011": "wind", "101101": "fire", "110110": "lake"}
probes, gold = [], []
for bn, tri in doubled.items():
    if bn in hexbin:
        body = "\n".join(l for l in hexbin[bn][1].read_text(errors="ignore").splitlines()
                         if not l.startswith("@") and l.strip())
        probes.append(re.sub(r"\s+", " ", body)); gold.append(tri)
P = emb(probes); pred = (P @ T.T).argmax(1)
acc = sum(tnames[pred[i]] == gold[i] for i in range(len(gold)))
print(f"[trigram self-recovery] doubled-hexagram body -> own trigram: {acc}/{len(gold)} "
      f"({100*acc/len(gold):.0f}%)   (flat 64-way held-out was 28%)")

def classify_tri(text): return tnames[int((emb([text])[0] @ T.T).argmax())]
def retract(operator_text, agent_text):
    up = classify_tri(operator_text); lo = classify_tri(agent_text)
    binary = tbits[up] + tbits[lo]
    name = hexbin.get(binary, ("(?)", None))[0]
    return up, lo, binary, name

print("\n[demo] operator(->upper) + agent(->lower) => hexagram")
demos = [
    ("initiate a bold creative design with full force, assert the new architecture",
     "receive the spec and give it concrete supportive form, nurture the build"),
    ("a sudden shock — drop everything and begin a new line of attack",
     "flow carefully through the dangerous, unfathomable parts of the codebase"),
    ("illuminate and clarify exactly what the bug is, make it legible",
     "open, joyful exchange — review and communicate the fix with the team"),
]
for op, ag in demos:
    up, lo, b, nm = retract(op, ag)
    print(f"  op->{up:8s} agent->{lo:8s} => {b}  {nm}")

# persist the trigram codebook + convention
OUT.mkdir(parents=True, exist_ok=True)
(OUT / "iching-trigrams.edn").write_text(
    "{:meta {:layer :trigram :bits 3 :count 8\n"
    "        :convention \"hexagram @binary = upper(3)++lower(3); retract: operator->upper, agent->lower\"\n"
    "        :date \"2026-06-02\"}\n :trigrams [\n" +
    "\n".join(f'  {{:name :{n} :bits "{b}" :doc "{d}"}}' for n, b, d in TRIGRAMS) +
    "\n ]}\n")
print(f"\nsaved trigram codebook -> {OUT}/iching-trigrams.edn")
