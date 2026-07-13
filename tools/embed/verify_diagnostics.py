#!/usr/bin/env python3
"""verify_diagnostics.py — M-categorical-code VERIFY spike (risk 2): do the STRUCTURAL
code-health diagnostics beat the classical/autoregressive baseline at forecasting real
evolution? File-level, leak-free (signals from <=T, target from >T), pure git. claude-6 (E2).

Target: which files get REWORKED (≥1 commit) in (T, HEAD] — well-powered (code node-churns
are plentiful), the right frame for node-arrival/densifying code.
Signals (all computed from history <= T):
  - prior-churn        : # commits touching the file pre-T   [AUTOREGRESSIVE BASELINE — the hard one]
  - coupling           : # distinct files it co-changed with pre-T   [structural]
  - damage-spread      : mean co-change burst size when it changes    [structural-dynamics, the novel one]
The pass bar: a structural diagnostic must beat prior-churn (not just random) at ranking the
files that actually got reworked. Else the diagnostics reduce to "what churned churns again."
  futon3c repo;  python3 verify_diagnostics.py
"""
import subprocess, collections, math
import numpy as np

REPO = "/home/joe/code/futon3c"


def git(*a):
    return subprocess.run(["git", "-C", REPO, *a], capture_output=True, text=True).stdout


def main():
    T = int(git("log", "-1", "--format=%ct", "HEAD~250").strip())
    asof = set(l for l in git("ls-tree", "-r", "--name-only", "HEAD~250").splitlines()
               if (l.startswith("src/") or l.startswith("dev/")) and l.endswith((".clj", ".cljc"))
               and "/test/" not in l and not l.endswith("_test.clj"))

    # one pass over history: commit -> (time, {clj files touched})
    raw = git("log", "--pretty=format:@%ct", "--name-only")
    commits = []
    ct, files = None, []
    for line in raw.splitlines():
        if line.startswith("@"):
            if ct is not None:
                commits.append((ct, [f for f in files if f.endswith((".clj", ".cljc"))]))
            ct, files = int(line[1:]), []
        elif line.strip():
            files.append(line.strip())
    if ct is not None:
        commits.append((ct, [f for f in files if f.endswith((".clj", ".cljc"))]))

    prior_churn = collections.Counter()
    post_churn = collections.Counter()
    co_partners = collections.defaultdict(set)
    burst = collections.defaultdict(list)
    for cti, fs in commits:
        fset = set(fs)
        for f in fs:
            if cti <= T:
                prior_churn[f] += 1
                co_partners[f] |= (fset - {f})
                burst[f].append(len(fset) - 1)
            else:
                post_churn[f] += 1

    nodes = [f for f in asof if prior_churn[f] >= 1]   # signals defined
    y = np.array([1 if post_churn[f] > 0 else 0 for f in nodes])
    sig = {
        "prior-churn (BASELINE)": np.array([prior_churn[f] for f in nodes], float),
        "coupling (structural)": np.array([len(co_partners[f]) for f in nodes], float),
        "damage-spread (structural-dyn)": np.array([np.mean(burst[f]) if burst[f] else 0 for f in nodes], float),
    }

    # fairer question: do structural diagnostics ADD orthogonal signal BEYOND churn?
    def z(a): return (a - a.mean()) / (a.std() + 1e-9)
    sig["churn+coupling (combined)"] = z(sig["prior-churn (BASELINE)"]) + z(sig["coupling (structural)"])
    sig["churn+damage (combined)"] = z(sig["prior-churn (BASELINE)"]) + z(sig["damage-spread (structural-dyn)"])

    def ap(scores):
        order = np.argsort(-scores, kind="stable")
        hits, s = 0, 0.0
        for i, idx in enumerate(order, 1):
            if y[idx]:
                hits += 1; s += hits / i
        return s / max(1, y.sum())

    base_rate = float(y.mean())
    print(f"[verify] T={git('log','-1','--format=%ad','--date=short','HEAD~250').strip()}; "
          f"{len(nodes)} files w/ pre-T history; {int(y.sum())}/{len(nodes)} reworked after T "
          f"(base rate {base_rate:.3f})")
    aps = {}
    for name, s in sig.items():
        aps[name] = ap(s)
        rho = float(np.corrcoef(s, np.array([post_churn[f] for f in nodes], float))[0, 1])
        print(f"  {name:32s} AP={aps[name]:.3f}  (lift over base {aps[name]-base_rate:+.3f})  "
              f"spearman-ish r={rho:+.3f}")
    b = aps["prior-churn (BASELINE)"]
    best_struct = max(aps["coupling (structural)"], aps["damage-spread (structural-dyn)"])
    best_comb = max(aps["churn+coupling (combined)"], aps["churn+damage (combined)"])
    print(f"\n[VERDICT risk-2] structural ALONE best AP {best_struct:.3f} vs baseline {b:.3f} -> "
          f"{'BEATS' if best_struct > b else 'does NOT beat'}.")
    print(f"[VERDICT risk-2] structural ADDED to churn best AP {best_comb:.3f} vs churn-alone {b:.3f} -> "
          f"structural {'ADDS orthogonal signal' if best_comb > b + 0.01 else 'adds ~nothing'} "
          f"beyond the autoregressive baseline.")


if __name__ == "__main__":
    main()
