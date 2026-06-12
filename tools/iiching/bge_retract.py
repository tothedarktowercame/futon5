#!/usr/bin/env python3
"""bge_retract.py — re-run the prose retract with BGE-large-en-v1.5 (heavier than MiniLM) to see how
much of MiniLM's 2/8 is just a weak-on-math embedder. Same 64 generator-scopes, same 8 keyword-free
probes. Uses BGE's query instruction on the probe side (asymmetric retrieval). claude-6 + Joe 2026-06-02.
  futon3a/.venv/bin/python bge_retract.py
"""
import json, re, pathlib
import numpy as np
from sentence_transformers import SentenceTransformer

cb = json.loads(pathlib.Path("/tmp/iiching-codebook.json").read_text())
gens = sorted(cb.values(), key=lambda r: r["code"])
names = [g["generator"] for g in gens]
codes = [g["code"] for g in gens]
nameset = {n: i for i, n in enumerate(names)}
NLAB = pathlib.Path("/home/joe/code/nlab-content/pages")

page_text = {}
for name_f in NLAB.rglob("name"):
    nm = name_f.read_text(errors="ignore").strip().lower()
    if nm in nameset:
        c = name_f.parent / "content.md"
        if c.exists():
            t = re.sub(r"\[\[|\]\]|[#*$>`]", " ", c.read_text(errors="ignore"))
            page_text[nm] = re.sub(r"\s+", " ", t)[:500]

model = SentenceTransformer("BAAI/bge-large-en-v1.5")
QINSTR = "Represent this sentence for searching relevant passages: "
def emb(texts): return np.asarray(model.encode(texts, normalize_embeddings=True))
def embq(texts): return np.asarray(model.encode([QINSTR + t for t in texts], normalize_embeddings=True))

G_name = emb(names)
G_def = emb([f"{n}. {page_text.get(n, n)}" for n in names])

def spread(G):
    s = G @ G.T; iu = np.triu_indices(len(G), 1)
    return np.median(s[iu]), np.percentile(s[iu], 90)
for tag, G in (("name", G_name), ("def ", G_def)):
    m, p = spread(G); print(f"[BGE cell spread:{tag}] median={m:.3f} p90={p:.3f}")

probes = [
    ("a universal arrow; the left adjoint to a forgetful functor", {"adjoint", "adjunction", "adjoint functor"}),
    ("the contravariant hom-functor sending an object to its set of morphisms into a fixed object",
     {"presheaf", "representable functor", "yoneda lemma", "yoneda embedding"}),
    ("a 2-cell between two functors, one component per object, natural in the object", {"natural transformation", "natural isomorphism"}),
    ("a monoid object in the category of endofunctors under composition", {"monad"}),
    ("the fiber product; a commuting square universal among cones over a cospan", {"pullback"}),
    ("the colimit of a diagram indexed by a filtered category", {"colimit"}),
    ("an object admitting exactly one morphism into it from every object", {"terminal object"}),
    ("a functor that is injective on hom-sets", {"functor", "subcategory", "morphism"}),
]
Q = embq([t for t, _ in probes])
for tag, G in (("name", G_name), ("def ", G_def)):
    sims = Q @ G.T
    hits = 0
    print(f"\n[BGE retract demos:{tag}-anchored]")
    for i, (t, ok) in enumerate(probes):
        j = int(sims[i].argmax()); nm = names[j]; good = nm in ok; hits += good
        print(f"  {'OK ' if good else '-- '} -> {nm:24s}[{format(codes[j],'06b')}] cos={sims[i][j]:.2f}  (want {'/'.join(list(ok)[:2])}…)")
    print(f"  BGE demo accuracy ({tag}): {hits}/{len(probes)}   [MiniLM was 2/8 both]")
