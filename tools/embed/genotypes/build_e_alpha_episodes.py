#!/usr/bin/env python3
"""build_e_alpha_episodes.py — :extract phase of E-alpha-next-locus (genotype 52c97bb).
Per-sha git file-extraction (READ-ONLY) from the aligned backfill rows -> per-mission episodes.
Episode (M, c_i), i>=2 (M's commits ordered by ct): footprint = code-files from c_1..c_{i-1};
target = NEW code-files in c_i (extension). Candidate pool = code-files known in M's repo(s) as-of-T.
Code-files only: .clj/.cljs/.cljc/.el/.py/.bb (genotype scope). Writes /tmp/e-alpha-episodes.json.
  futon3a/.venv/bin/python build_e_alpha_episodes.py
"""
import json, subprocess, pathlib, collections

BACKFILL = pathlib.Path("/home/joe/code/futon5a/data/turn-commit-mission-backfill.json")
CODE_EXT = (".clj", ".cljs", ".cljc", ".el", ".py", ".bb")
def repo_path(repo): return pathlib.Path("/home/joe/code") / repo

recs = json.loads(BACKFILL.read_text())["records"]
aligned = [r for r in recs if r.get("bestguess_mission") and r.get("turn_epoch")]
print(f"[slice] {len(recs)} records -> {len(aligned)} aligned (non-null mission ∧ turn_epoch)")

# per-sha code-files via git show (read-only); cache by (repo,sha)
filecache, treecache = {}, {}
def files_of(repo, sha):  # code-files CHANGED by this commit
    key = (repo, sha)
    if key in filecache: return filecache[key]
    rp = repo_path(repo); fs = set()
    if rp.exists():
        try:
            out = subprocess.run(["git", "-C", str(rp), "show", "--name-only", "--format=", sha],
                                 capture_output=True, text=True, timeout=20).stdout
            fs = {ln.strip() for ln in out.splitlines() if ln.strip().endswith(CODE_EXT)}
        except Exception: pass
    filecache[key] = fs; return fs
def tree_of(repo, sha):  # ALL code-files EXISTING in the repo as-of this commit (candidate universe)
    key = (repo, sha)
    if key in treecache: return treecache[key]
    rp = repo_path(repo); fs = set()
    if rp.exists():
        try:
            out = subprocess.run(["git", "-C", str(rp), "ls-tree", "-r", "--name-only", sha],
                                 capture_output=True, text=True, timeout=30).stdout
            fs = {ln.strip() for ln in out.splitlines() if ln.strip().endswith(CODE_EXT)}
        except Exception: pass
    treecache[key] = fs; return fs

# group by mission, order by ct
by_m = collections.defaultdict(list)
for r in aligned:
    by_m[r["bestguess_mission"]].append(r)
for m in by_m: by_m[m].sort(key=lambda r: r["ct"])

episodes = []
missions_used = 0
for m, commits in by_m.items():
    # attach files
    enriched = [(c["ct"], c["repo"], files_of(c["repo"], c["sha"]), c["sha"]) for c in commits]
    enriched = [e for e in enriched if e[2]]  # keep commits that touched >=1 code-file
    if len(enriched) < 2: continue
    missions_used += 1
    footprint = set()
    for i, (ct, repo, fs, sha) in enumerate(enriched):
        if i >= 1:
            target = fs - footprint                          # NEW code-files (extension)
            universe = tree_of(repo, sha) - footprint        # repo code-files as-of-T, not already touched
            target &= universe                               # findable positives (target ⊆ candidate universe)
            if target and len(universe) > len(target):       # need >=1 negative to rank against
                episodes.append({
                    "mission": m, "ct": ct, "repo": repo, "sha": sha,
                    "footprint": sorted(footprint),
                    "target": sorted(target),
                    "candidates_asof": sorted(universe),      # repo code-files as-of-T minus footprint
                })
        footprint |= fs

print(f"[episodes] missions with >=2 code-touching commits: {missions_used}")
print(f"[episodes] usable episodes (non-empty NEW-file target): {len(episodes)}")
if episodes:
    fp = [len(e["footprint"]) for e in episodes]
    tg = [len(e["target"]) for e in episodes]
    cd = [len(e["candidates_asof"]) for e in episodes]
    import statistics as st
    print(f"  footprint size:  median {int(st.median(fp))}  max {max(fp)}")
    print(f"  target size:     median {int(st.median(tg))}  max {max(tg)}")
    print(f"  candidate pool:  median {int(st.median(cd))}  max {max(cd)}")
    print(f"  episodes per mission: {collections.Counter(e['mission'] for e in episodes).most_common(5)}")
pathlib.Path("/tmp/e-alpha-episodes.json").write_text(json.dumps(episodes))
print(f"[wrote] /tmp/e-alpha-episodes.json ({len(episodes)} episodes)")
print(f"[power-note] n_episodes={len(episodes)} — pilot scale; band CIs will be wide (expected per genotype).")
