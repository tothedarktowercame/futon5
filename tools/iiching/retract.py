#!/usr/bin/env python3
"""retract.py — wire the ICHING RETRACT (the scope-recognizer, futon6 Bayesian-mining sense):
project any text onto its nearest of the 64 iching generator-scopes (Voronoi cell) -> 6-bit code.
The 1st-order/forgetful direction of the retract -| lift adjunction.

Key experiment: anchor each generator-scope on its NAME (=> reduces to keyword matching, the
symbols-trap) vs on its DEFINITION (nlab Idea text => a robust SCOPE, recognizes paraphrase). Reports
cell spread + demo accuracy on synthetic CT descriptions for BOTH, and soft-entry (relation/differential)
health. Saves the definition-anchored embeddings. claude-6 + Joe, 2026-06-02.
  futon3a/.venv/bin/python retract.py
"""
import json, re, pathlib
import numpy as np
from sentence_transformers import SentenceTransformer

cb = json.loads(pathlib.Path("/tmp/iiching-codebook.json").read_text())  # builder's machine-readable mirror
gens = sorted(cb.values(), key=lambda r: r["code"])
names = [g["generator"] for g in gens]
codes = [g["code"] for g in gens]
nameset = {n: i for i, n in enumerate(names)}
NLAB = pathlib.Path("/home/joe/code/nlab-content/pages")
OUT = pathlib.Path("/home/joe/code/futon5/resources/iiching-ct")

# nlab Idea/definition text per generator
page_text = {}
for name_f in NLAB.rglob("name"):
    nm = name_f.read_text(errors="ignore").strip().lower()
    if nm in nameset:
        c = name_f.parent / "content.md"
        if c.exists():
            t = re.sub(r"\[\[|\]\]|[#*$>`]", " ", c.read_text(errors="ignore"))
            page_text[nm] = re.sub(r"\s+", " ", t)[:500]

model = SentenceTransformer("sentence-transformers/all-MiniLM-L6-v2")
def emb(texts): return np.asarray(model.encode(texts, normalize_embeddings=True))

G_name = emb(names)                                                       # bare-name anchor
G_def = emb([f"{n}. {page_text.get(n, n)}" for n in names])               # definition anchor (scope)

def spread(G):
    s = G @ G.T; iu = np.triu_indices(len(G), 1)
    return np.median(s[iu]), np.percentile(s[iu], 90)
for tag, G in (("name", G_name), ("def ", G_def)):
    m, p = spread(G)
    print(f"[cell spread:{tag}] pairwise cos median={m:.3f} p90={p:.3f}")

probes = [  # (description, set of acceptable scopes)
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
def retract(G, text):
    s = emb([text])[0] @ G.T; i = int(s.argmax())
    return names[i], format(codes[i], "06b"), float(s[i])

for tag, G in (("name", G_name), ("def ", G_def)):
    hits = 0
    print(f"\n[retract demos:{tag}-anchored]")
    for t, ok in probes:
        nm, b, s = retract(G, t)
        good = nm in ok
        hits += good
        print(f"  {'OK ' if good else '-- '} -> {nm:24s}[{b}] cos={s:.2f}  (want {'/'.join(list(ok)[:2])}…)")
    print(f"  demo accuracy ({tag}): {hits}/{len(probes)}")

# soft-entry health under the definition anchor
print("\n[soft-entry health:def] nearest-3 generators to relation/differential pages:")
for soft in ("relation", "differential"):
    if soft in page_text:
        s = emb([page_text[soft]])[0] @ G_def.T
        near = [names[j] for j in s.argsort()[-3:][::-1]]
        print(f"  {soft:12s} page -> {near}")

np.save(OUT / "iching-generator-embeddings.npy", G_def)
(OUT / "iching-generator-embeddings.meta.json").write_text(json.dumps(
    {"model": "sentence-transformers/all-MiniLM-L6-v2", "anchor": "name + nlab Idea (scope-definition)",
     "order": names, "codes": codes}, indent=1))
print(f"\nsaved definition-anchored generator-scope embeddings -> {OUT}/iching-generator-embeddings.npy")
