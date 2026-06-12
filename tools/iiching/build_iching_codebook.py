#!/usr/bin/env python3
"""build_iching_codebook.py — the 64-generator ICHING layer (6-bit) that the iiching (8-bit)
lifts from. Pipeline:
  1. SELECT 64 generators: aggregate the triangulated manifest by irreducible head, clean
     nlab-nav junk, rank by CT-centrality (nlab in-link), require an nlab page.
  2. LEARN similarity: each generator's nlab [[link]] out-neighbourhood -> cosine similarity
     (two generators are close if they link to the same concepts).
  3. EMBED + ASSIGN: spectral-embed the 64 to 6-D, then optimally (Hungarian) match them to the
     64 vertices of the 6-bit hypercube -> a UNIQUE 6-bit code per generator, Hamming ~= CT-distance.
  4. VALIDATE: corr(1 - similarity, Hamming). 5. arxiv prior FINAL CHECK (Joe): every generator
     must be real working-CT (arxiv_df > 0); report the distribution.
Output: /tmp/iiching-codebook.json. claude-6 + Joe, 2026-06-02.
  futon3a/.venv/bin/python build_iching_codebook.py
"""
import json, re, pathlib, collections
import numpy as np
from sklearn.manifold import SpectralEmbedding
from scipy.optimize import linear_sum_assignment

MAN = json.loads(pathlib.Path("/tmp/iiching-concept-manifest.json").read_text())
NLAB = pathlib.Path("/home/joe/code/nlab-content/pages")
LEAN = pathlib.Path("/home/joe/code/mathlib4/Mathlib/CategoryTheory")
N = 64

MODS = {'monoidal','symmetric','braided','closed','cartesian','enriched','finite','filtered',
'weighted','pointwise','full','faithful','left','right','small','locally','higher','stable',
'opposite','internal','free','regular','split','strong','lax','strict','dual','co','op',
'commutative','abelian','additive','exact','complete','accessible','presentable','model',
'simplicial','topological','differential','graded','linear','homotopy','derived','category','categories'}
JUNK = {'theory','theory - contents','contents','idea','reference','references','introduction',
'combinatorial','midpoint algebra','','-','nlab',
# physics / geometry domains (grab-bags, not categorical concepts) — Joe 2026-06-02
'quantum field theory','string theory','field theory','action functional','principal bundle',
'lie group','lie algebra','cohomology','topology','geometry','dimension','fiber','chain complex',
# "X theory" grab-bags — keep the underlying CONCEPT (topos, type), drop the -theory page
'topos theory','set theory','type theory','dependent type theory','categorical semantics','structure',
# set-theoretic + algebraic OBJECTS — a meta-theory needs no objects, only the abstract "object"
# adaptor (kept) + universal objects (terminal/initial, kept). Joe 2026-06-02.
'set','subset','function','disjoint union','equivalence relation','natural number',
'group','ring','algebra','module',
# round-2 physics/geometry/topology residue + concrete named category Top
'perturbative quantum field theory','synthetic geometry','algebraic topology','smash product',
'open cover','cocycle','top',
# round-3: plural dup of model category; domain grab-bags + algebraic-topology object
'model categories','homological algebra','logic','number','fundamental group',
# round-4: collapse the monoidal family to the single 'monoidal category' atom (Fork-A + lossy
# retract); variants (symmetric/closed/cartesian) are lift-modifiers, not base generators. + final cuts.
'closed monoidal category','symmetric monoidal category','cartesian monoidal category',
'heterotic string theory','pair','map','induction'}

def head_of(name):
    toks = [t for t in name.lower().split() if t not in MODS]
    return ' '.join(toks) if toks else name.lower()

def junky(h):
    """Systematic tail filter: kill grab-bag 'X theory' pages and set/geometry/topology OBJECTS,
    not just named ones. Keeps concepts (topos), drops grab-bags (representation theory) + objects."""
    if h in JUNK or len(h) < 3:
        return True
    toks = h.split()
    if 'theory' in toks:                              # 'X theory' = grab-bag, keep the concept not the theory
        return True
    if toks[-1] in {'set', 'space', 'manifold', 'map'}:  # set-theoretic / geometric / topological objects
        return True
    return False

# 1. aggregate manifest by head; keep best (highest-centrality) member, require all-three grounding
heads = {}
# gate: must be FORMALIZED in Lean CategoryTheory (kills nlab author-pages + non-CT for free),
# used in working math.CT (arxiv_df>0), AND non-trivially used (arxiv_df>=300 drops niche-physics
# that slipped through Lean, e.g. supergravity); WHITELIST keeps CT-central but Lean-light concepts.
WHITELIST = {'topos','presheaf','profunctor','yoneda lemma','end','coend','comma category',
             'dinatural transformation','2-category','sieve','subobject classifier'}
for r in MAN:
    h0 = head_of(r['concept'])
    if h0 in WHITELIST:
        pass
    elif r['arxiv_df'] < 300 or r['lean'] == 0:
        continue
    h = head_of(r['concept'])
    if h not in WHITELIST and junky(h):
        continue
    cur = heads.get(h)
    if cur is None or r['nlab_inlink'] > cur['nlab_inlink']:
        heads[h] = {'head': h, 'nlab_inlink': r['nlab_inlink'],
                    'arxiv_df': r['arxiv_df'], 'lean': r['lean']}
ranked = sorted(heads.values(), key=lambda r: -r['nlab_inlink'])

# 2. nlab out-link neighbourhoods (walk once; keep pages whose name is a candidate head)
cand = {r['head'] for r in ranked[:180]}
link_re = re.compile(r"\[\[([^\]|#]+)")
outlinks = {}
for name_f in NLAB.rglob("name"):
    nm = name_f.read_text(errors="ignore").strip().lower()
    if nm in cand:
        c = name_f.parent / "content.md"
        if c.exists():
            tgts = {t.strip().lower() for t in link_re.findall(c.read_text(errors="ignore"))}
            if tgts:
                outlinks[nm] = outlinks.get(nm, set()) | tgts

# final 64: top-ranked heads that have a non-empty nlab page
gens = [r for r in ranked if r['head'] in outlinks][:N]
assert len(gens) == N, f"only {len(gens)} generators with pages; widen candidate pool"
names = [g['head'] for g in gens]

# incidence over the union of link targets -> cosine similarity
vocab = sorted({t for n in names for t in outlinks[n]})
vidx = {t: i for i, t in enumerate(vocab)}
M = np.zeros((N, len(vocab)))
for i, n in enumerate(names):
    for t in outlinks[n]:
        M[i, vidx[t]] = 1.0
Mn = M / (np.linalg.norm(M, axis=1, keepdims=True) + 1e-9)
S_nlab = Mn @ Mn.T

# Lean fold: co-membership in Mathlib/CategoryTheory FILE PATHS (full-word, low-noise);
# generators appearing in overlapping files/subtrees are CT-related. Blend with nlab.
BLEND_LEAN = 0.0
def words(s): return set(re.findall(r'[a-z]{4,}', re.sub(r'(?<=[a-z])(?=[A-Z])', ' ', s).lower()))
lfiles = [str(f.relative_to(LEAN)) for f in LEAN.rglob("*.lean")]
ftoks = [words(p) for p in lfiles]
gwords = {n: (set(w for w in n.split() if w not in MODS and len(w) >= 4) or set(n.split())) for n in names}
Lm = np.zeros((N, len(lfiles)))
for i, n in enumerate(names):
    for j, ft in enumerate(ftoks):
        if any(any(gw[:5] == t[:5] for t in ft) for gw in gwords[n]):
            Lm[i, j] = 1.0
Ln = Lm / (np.linalg.norm(Lm, axis=1, keepdims=True) + 1e-9)
S_lean = Ln @ Ln.T
S = (1 - BLEND_LEAN) * S_nlab + BLEND_LEAN * S_lean
np.fill_diagonal(S, 1.0)
S = np.clip(S, 0, None) + 1e-4   # keep affinity graph connected
print(f"[lean-fold] blended nlab+Lean similarity (lean weight {BLEND_LEAN}; "
      f"{int((Lm.sum(1) > 0).sum())}/{N} generators matched Lean CategoryTheory files)")

# 3. spectral embed to 6-D, Hungarian-match to the 64 hypercube vertices
X = SpectralEmbedding(n_components=6, affinity='precomputed', random_state=0).fit_transform(S)
X = (X - X.mean(0)) / (X.std(0) + 1e-9)
verts = np.array([[1.0 if (k >> b) & 1 else -1.0 for b in range(6)] for k in range(64)])
cost = ((X[:, None, :] - verts[None, :, :]) ** 2).sum(2)   # 64 gens x 64 codes
gi, ck = linear_sum_assignment(cost)
code = {names[gi[j]]: int(ck[j]) for j in range(N)}

# 4. validate: dissimilarity vs Hamming
def ham(a, c): return bin(a ^ c).count("1")
diss, hd = [], []
for i in range(N):
    for j in range(i + 1, N):
        diss.append(1 - float(S[i, j])); hd.append(ham(code[names[i]], code[names[j]]))
corr = float(np.corrcoef(diss, hd)[0, 1])

# 5. arxiv prior FINAL CHECK
adf = np.array([g['arxiv_df'] for g in gens])
n_zero = int((adf == 0).sum())

print(f"[codebook] 64 generators selected (CT-grounded, nlab-paged)")
print(f"[validate] corr(nlab-dissimilarity, Hamming) = {corr:+.3f}  "
      f"(positive => similar generators get nearby 6-bit codes)")
print(f"[arxiv FINAL CHECK] arxiv_df over the 64: min={adf.min()} median={int(np.median(adf))} "
      f"max={adf.max()}; generators absent from math.CT = {n_zero}")
print(f"\n=== the 64-generator iching codebook (sorted by 6-bit code) ===")
for k in range(64):
    nm = next((n for n, c in code.items() if c == k), None)
    if nm:
        g = next(x for x in gens if x['head'] == nm)
        print(f"  {k:2d} {format(k,'06b')}  {nm:26s} nlab_in={g['nlab_inlink']:4d} arxiv_df={g['arxiv_df']:5d}")
print(f"\n=== Hamming-neighbour sanity (do code-neighbours look CT-related?) ===")
for probe in ['functor', 'limit', 'monad', 'adjunction']:
    if probe in code:
        c0 = code[probe]
        nb = sorted(((ham(c0, code[n]), n) for n in names if n != probe))[:4]
        print(f"  {probe:14s} -> " + ", ".join(f"{n}(H{h})" for h, n in nb))

out = {format(code[g['head']], '06b'): {"code": code[g['head']], "generator": g['head'],
       "nlab_inlink": g['nlab_inlink'], "arxiv_df": g['arxiv_df'], "lean": g['lean']} for g in gens}
pathlib.Path("/tmp/iiching-codebook.json").write_text(json.dumps(out, indent=1, sort_keys=True))
print(f"\ncodebook -> /tmp/iiching-codebook.json")
