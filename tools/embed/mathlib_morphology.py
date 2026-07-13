#!/usr/bin/env python3
"""mathlib_morphology.py — first PORTABILITY/exotype test: does the FUTON 'grows-at-the-edges'
method transfer to mathlib? Layout-free version (runs identically on both substrates):
  grows-at-edges  <=>  newer modules are more PERIPHERAL in the dependency DAG
                       (lower import-IN-degree: fewer things depend on them yet).
Substrate: mathlib4 import-dependency graph + git birth dates (5yr / 32k commits). claude-6.
  python3 mathlib_morphology.py
"""
import subprocess, pathlib, re, collections
import numpy as np

REPO = pathlib.Path("/home/joe/code/mathlib4")


def path_to_mod(p):  # Mathlib/Algebra/Group/Basic.lean -> Mathlib.Algebra.Group.Basic
    return p[:-5].replace("/", ".") if p.endswith(".lean") else None


def main():
    files = [str(p.relative_to(REPO)) for p in (REPO / "Mathlib").rglob("*.lean")]
    mods = {path_to_mod(f) for f in files}
    indeg = collections.Counter()
    edges = 0
    # mathlib4 uses the Lean module system: imports are `public import Mathlib.X` (or `import`/`private import`)
    imp_re = re.compile(r"^(?:public |private )?import\s+(Mathlib[\w.]+)", re.M)
    for f in files:
        try:
            txt = (REPO / f).read_text(errors="ignore")
        except Exception:
            continue
        for m in imp_re.findall(txt):
            if m in mods:
                indeg[m] += 1; edges += 1
    print(f"[mathlib] {len(files)} modules, {edges} internal import-edges, "
          f"max in-degree {max(indeg.values()) if indeg else 0}")

    # birth dates: one git pass over full history (blobless: trees present, no blob fetch needed)
    import json
    BCACHE = pathlib.Path("/tmp/mathlib-birth.json")
    if BCACHE.exists():
        birth = {k: int(v) for k, v in json.loads(BCACHE.read_text()).items()}
    else:
        out = subprocess.run(["git", "-C", str(REPO), "log", "--diff-filter=A", "--name-only",
                              "--format=@%ct"], capture_output=True, text=True).stdout
        birth = {}
        ct = None
        for line in out.splitlines():
            if line.startswith("@"):
                ct = int(line[1:])
            elif line.endswith(".lean") and line.startswith("Mathlib/"):
                m = path_to_mod(line)
                if m is not None:
                    birth[m] = min(birth.get(m, ct), ct)   # earliest add = creation
        BCACHE.write_text(json.dumps(birth))
    print(f"[birth] {len(birth)} modules dated")

    common = [m for m in mods if m in birth]
    bs = np.array([birth[m] for m in common], float)
    ds = np.array([indeg[m] for m in common], float)
    corr = float(np.corrcoef(bs, ds)[0, 1])
    # spearman-ish via ranks (robust to the skewed in-degree dist)
    from scipy.stats import rankdata
    rc = float(np.corrcoef(rankdata(bs), rankdata(ds))[0, 1])
    print(f"[GROWS AT EDGES] corr(birth_ct, in_degree) pearson={corr:+.3f} rank={rc:+.3f}")
    print(f"   NEGATIVE => newer modules have LOWER in-degree = more peripheral = grows at the edges "
          f"(matches FUTON +0.48 on the age↔distance framing)")
    # core vs rim sanity: oldest-decile vs newest-decile mean in-degree
    order = np.argsort(bs)
    old_idx = order[:len(order)//10]; new_idx = order[-len(order)//10:]
    print(f"   oldest-decile mean in-degree {ds[old_idx].mean():.1f}  vs  "
          f"newest-decile {ds[new_idx].mean():.1f}")


if __name__ == "__main__":
    main()
