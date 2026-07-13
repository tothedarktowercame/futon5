#!/usr/bin/env python3
"""build_concept_manifest.py — first-cut iiching concept manifest by triangulating three
git-backed CT corpora (the "reduced subset of CT as meta-theory", target 256 = the 8-bit sigil space).

Three orthogonal signals per candidate concept:
  USAGE       arXiv math.CT doc-frequency  (futon6/data/ct-term-prior.json, 9742 eprints)
  CENTRALITY  nlab in-link count           (/home/joe/code/nlab-content — [[wikilink]] graph)
  FORMALIZED  Lean CategoryTheory presence (mathlib4/Mathlib/CategoryTheory def/class names)

Candidate vocabulary = nlab page titles (human-readable concept names). The triangulation
auto-filters to CT: the Lean signal is CT-only and arXiv df is math.CT-only, so non-CT nlab pages
sink. Composite = sum of per-signal ranks. Output: /tmp/iiching-concept-manifest.json (full ranking)
+ top-256 markdown draft for Joe to ratify. claude-6 + Joe, 2026-06-02.
  futon6/.venv/bin/python build_concept_manifest.py
"""
import json, re, collections, pathlib
import numpy as np

PRIOR = "/home/joe/code/futon6/data/ct-term-prior.json"
NLAB = pathlib.Path("/home/joe/code/nlab-content/pages")
LEAN = pathlib.Path("/home/joe/code/mathlib4/Mathlib/CategoryTheory")
OUT = pathlib.Path("/tmp/iiching-concept-manifest.json")

# --- USAGE: arXiv math.CT doc-frequency ---
d = json.load(open(PRIOR))
ndoc, uni, big = d["n_docs"], d["unigram_df"], d["bigram_df"]
def arxiv_df(title):
    w = title.lower().split()
    if len(w) == 1: return uni.get(w[0], 0)
    if len(w) == 2: return big.get(title.lower(), 0)
    bs = [big.get(f"{w[i]} {w[i+1]}", 0) for i in range(len(w) - 1)]
    return max(bs) if bs else 0

# --- CENTRALITY: nlab titles + [[wikilink]] in-degree ---
link_re = re.compile(r"\[\[([^\]|#]+)")
inlink = collections.Counter()
titles = []
n_pages = 0
for name_f in NLAB.rglob("name"):
    n_pages += 1
    title = name_f.read_text(errors="ignore").strip()
    if title:
        titles.append(title)
    c = name_f.parent / "content.md"
    if c.exists():
        for tgt in link_re.findall(c.read_text(errors="ignore")):
            inlink[tgt.strip().lower()] += 1

# --- FORMALIZED: Lean CategoryTheory concept tokens ---
name_re = re.compile(r"^(?:structure|class|def|abbrev) ([A-Z][A-Za-z0-9']+)", re.M)
camel_re = re.compile(r"[A-Z][a-z0-9']*")
lean_tok = collections.Counter()
lean_n = 0
for f in LEAN.rglob("*.lean"):
    for nm in name_re.findall(f.read_text(errors="ignore")):
        lean_n += 1
        for t in camel_re.findall(nm):
            tl = t.lower()
            if tl not in ("is", "has", "of", "to", "the"):
                lean_tok[tl] += 1

# --- score every distinct nlab concept ---
rows, seen = [], set()
for title in titles:
    key = title.lower()
    if key in seen or len(title) > 55 or any(ch in title for ch in "(){}$\\<>"):
        continue
    seen.add(key)
    adf = arxiv_df(title)
    nin = inlink.get(key, 0)
    lean = sum(lean_tok.get(w, 0) for w in key.split())
    if adf == 0 and nin == 0 and lean == 0:
        continue
    rows.append({"concept": title, "arxiv_df": adf, "nlab_inlink": nin, "lean": lean})

# composite = sum of per-signal ranks (rank 1 = highest); robust to scale differences
def rank_desc(vals):
    order = np.argsort(np.argsort(-np.asarray(vals, float)))
    return order  # 0 = top
adf_r = rank_desc([r["arxiv_df"] for r in rows])
nin_r = rank_desc([r["nlab_inlink"] for r in rows])
lean_r = rank_desc([r["lean"] for r in rows])
for i, r in enumerate(rows):
    r["score"] = int(adf_r[i] + nin_r[i] + lean_r[i])  # lower = better
    r["signals"] = sum(1 for k in ("arxiv_df", "nlab_inlink", "lean") if r[k] > 0)
rows.sort(key=lambda r: r["score"])

OUT.write_text(json.dumps(rows, indent=0))
print(f"[corpora] nlab pages={n_pages}  distinct scored concepts={len(rows)}  "
      f"Lean CT names={lean_n} ({len(lean_tok)} tokens)  arxiv docs={ndoc}")
tri = [r for r in rows if r["signals"] == 3]
print(f"[triangulated] {len(tri)} concepts present in ALL THREE corpora (the robust core)")
print(f"\n--- top 40 of the top-256 manifest (rank by composite, * = in all 3 corpora) ---")
for r in rows[:40]:
    star = "*" if r["signals"] == 3 else " "
    print(f" {star} {r['concept'][:34]:34s} arxiv_df={r['arxiv_df']:6d}  nlab_in={r['nlab_inlink']:5d}  lean={r['lean']:4d}")
print(f"\nfull ranking -> {OUT}  (take rows[:256] for the manifest)")
