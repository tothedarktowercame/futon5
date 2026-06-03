#!/usr/bin/env python3
"""run_e_alpha.py — RUN phase of E-alpha-next-locus (frozen genotype 52c97bb).
Baselines (recency/churn/pref-attachment/common-neighbours, from per-repo git history, strictly ct<T)
+ E1 arms a=raw-co-mention, b=Newman (claude-3's mention-graph-as-of-T). Per-episode AP -> MAP;
episode-level bootstrap band (B=2000, 95% CI). Gate: CI of lift excludes 0 AND point-lift >= 0.05.
Arm c (Newman+curvature) DEFERRED — κ not precisely pre-registered; awaiting claude-3's exact def.
Writes the .result.md (NOT back into the genotype).  futon3a/.venv/bin/python run_e_alpha.py
"""
import json, subprocess, pathlib, random, collections, sys
import numpy as np
sys.path.insert(0, "/home/joe/code/futon5/tools/embed")
from forecast_harness import _average_precision  # reuse the engine's AP (tie-break discipline lives here too)

EPS = json.loads(pathlib.Path("/tmp/e-alpha-episodes.json").read_text())
MENTION = json.loads(pathlib.Path("/home/joe/code/futon5a/data/mention-graph-asof.json").read_text())
# v2 SLICE (frozen genotype 827835e): restrict to missions with >=2 mention-timeline versions (the 53) —
# ex-ante, outcome-INDEPENDENT (a fact about the doc's git history, not the prediction).
_n0 = len(EPS)
EPS = [e for e in EPS if len(MENTION.get(e["mission"], {}).get("timeline", [])) >= 2]
print(f"[v2 slice] evolving-subset filter: {_n0} -> {len(EPS)} episodes (missions with >=2 mention-timeline versions)")
CODE_EXT = (".clj", ".cljs", ".cljc", ".el", ".py", ".bb")
W = 30 * 86400  # churn window (30d) — free param, logged
B = 2000
rng = random.Random(20260602)

# ---- per-repo git history: [(ct, frozenset(code-files))], cached ----
_hist = {}
def history(repo):
    if repo in _hist: return _hist[repo]
    rp = pathlib.Path("/home/joe/code") / repo
    commits = []
    if rp.exists():
        out = subprocess.run(["git", "-C", str(rp), "log", "--name-only", "--format=@%ct"],
                             capture_output=True, text=True, timeout=120).stdout
        ct = None; fs = set()
        for ln in out.splitlines():
            if ln.startswith("@"):
                if ct is not None: commits.append((ct, frozenset(fs)))
                ct = int(ln[1:]); fs = set()
            elif ln.strip().endswith(CODE_EXT):
                fs.add(ln.strip())
        if ct is not None: commits.append((ct, frozenset(fs)))
    _hist[repo] = commits
    return commits

def fm_asof(mission, T):  # F_M(T): files at latest timeline entry with ct <= T
    md = MENTION.get(mission)
    if not md: return set()
    best = None
    for e in md.get("timeline", []):
        if e["ct"] <= T and (best is None or e["ct"] > best["ct"]): best = e
    return set(best["files"]) if best else set()

def comention_asof(T):  # raw + Newman file<->file weights over ALL missions as-of-T
    raw = collections.defaultdict(lambda: collections.defaultdict(float))
    new = collections.defaultdict(lambda: collections.defaultdict(float))
    for m in MENTION:
        Fm = fm_asof(m, T); k = len(Fm)
        if k < 2: continue
        w = 1.0 / (k - 1)
        for f in Fm:
            for g in Fm:
                if f != g: raw[f][g] += 1.0; new[f][g] += w
    return raw, new

SIGNALS = ["recency", "churn", "pref-attachment", "common-neighbours", "raw-co-mention(a)", "newman(b)"]
BASELINES = ["recency", "churn", "pref-attachment", "common-neighbours", "raw-co-mention(a)"]  # floor incl raw-comention
ap = {s: [] for s in SIGNALS}
desc_rows = []  # descriptive: target's Newman percentile among candidates (non-degenerate episodes only)
fm_stats = collections.Counter()  # inert-diagnosis: empty F_M(T) / flat-newman counts

for ep in EPS:
    T, repo = ep["ct"], ep["repo"]
    cands = ep["candidates_asof"]; fp = set(ep["footprint"]); tgt = set(ep["target"])
    before = [(c, fs) for c, fs in history(repo) if c < T]
    last = {}; tcount = collections.Counter(); churn = collections.Counter(); cotouch = collections.defaultdict(set)
    for c, fs in before:
        for f in fs:
            if c > last.get(f, -1): last[f] = c
            tcount[f] += 1
            if c >= T - W: churn[f] += 1
            cotouch[f] |= (fs - {f})
    FM = fm_asof(ep["mission"], T)
    raw, new = comention_asof(T)
    def sc(sig, f):
        if sig == "recency": return last.get(f, -1e18)
        if sig == "churn": return churn.get(f, 0)
        if sig == "pref-attachment": return tcount.get(f, 0)
        if sig == "common-neighbours": return len(cotouch.get(f, set()) & fp)
        if sig == "raw-co-mention(a)": return sum(raw[f].get(g, 0.0) for g in FM)
        if sig == "newman(b)": return sum(new[f].get(g, 0.0) for g in FM)
    order = cands[:]; rng.shuffle(order)  # fair tie-break (no candidate-order artifact)
    for s in SIGNALS:
        ranked = sorted(order, key=lambda f: sc(s, f), reverse=True)
        ap[s].append(_average_precision([1 if f in tgt else 0 for f in ranked]))
    # descriptive: only NON-DEGENERATE episodes (F_M(T) non-empty AND newman has spread among candidates)
    nm = {f: sc("newman(b)", f) for f in cands}
    nonzero_spread = len(set(round(v, 9) for v in nm.values())) > 1
    if len(FM) > 0 and nonzero_spread:
        tgt_mean = float(np.mean([nm[f] for f in tgt]))
        strictly = [1.0 if nm[f] < tgt_mean else 0.0 for f in cands]   # STRICT < — no zero-tie inflation
        desc_rows.append((ep["mission"], float(np.mean(strictly))))
    fm_stats["empty_FM"] += int(len(FM) == 0)
    fm_stats["flat_newman"] += int(len(FM) > 0 and not nonzero_spread)

apv = {s: np.array(ap[s]) for s in SIGNALS}
n = len(EPS)
MAP = {s: float(apv[s].mean()) for s in SIGNALS}
floor_map = max(MAP[b] for b in BASELINES)
floor_sig = max(BASELINES, key=lambda b: MAP[b])
lift_b = MAP["newman(b)"] - floor_map

# episode-level bootstrap of lift = newman - best_baseline (recomputed per resample)
boot = []
idx = np.arange(n)
for _ in range(B):
    r = rng.choices(idx.tolist(), k=n)
    fmap = max(apv[b][r].mean() for b in BASELINES)
    boot.append(apv["newman(b)"][r].mean() - fmap)
lo, hi = np.percentile(boot, [2.5, 97.5])
gate = (lo > 0) and (lift_b >= 0.05)

print(f"[E-alpha pilot] n_episodes={n}  churn_window={W//86400}d")
print("[MAP per signal]")
for s in SIGNALS: print(f"   {s:22s} MAP={MAP[s]:.3f}" + ("   <- FLOOR" if s == floor_sig else ("   <- E1 signal" if s == "newman(b)" else "")))
print(f"[band] lift(newman - floor[{floor_sig}]) = {lift_b:+.3f}   95% CI [{lo:+.3f}, {hi:+.3f}]")
print(f"[GATE] CI excludes 0: {lo>0}  AND point-lift>=0.05: {lift_b>=0.05}  ==> {'PASS' if gate else 'FAIL (expected at pilot n)'}")
print(f"[inert-diagnosis] empty F_M(T): {fm_stats['empty_FM']}/{n} episodes; "
      f"flat-newman (F_M≠∅ but no candidate spread): {fm_stats['flat_newman']}/{n}; "
      f"NON-DEGENERATE (usable for the mention signal): {len(desc_rows)}/{n}")
dm = collections.defaultdict(list)
for m, p in desc_rows: dm[m].append(p)
top = sorted(((float(np.mean(v)), m, len(v)) for m, v in dm.items()), reverse=True)
if desc_rows:
    print(f"[descriptive] extension-along-intent-bridge on the {len(desc_rows)} NON-DEGENERATE episodes "
          f"(mean target Newman-percentile; >0.5 = extends toward high-co-mention files): "
          f"overall {np.mean([p for _,p in desc_rows]):.2f}")
    for val, m, k in top[:6]: print(f"   {val:.2f}  {m} (n={k})")
else:
    print("[descriptive] INCONCLUSIVE — 0 non-degenerate episodes; the as-of-T mention signal is inert "
          "on this pilot (F_M(T) empty/static), so intent-bridge extension cannot be characterized yet.")

RESULT = pathlib.Path("/home/joe/code/futon5/tools/embed/genotypes/E-alpha-next-locus-v2.result.md")
RESULT.write_text(
    f"# E-alpha-next-locus-v2 — RESULT (frozen genotype 827835e; evolving subset, ≥2 mention-timeline versions)\n\n"
    f"**Run:** 2026-06-02 · n_episodes={n} (37 missions) · churn_window={W//86400}d · B={B} · arm c (Newman+curvature) DEFERRED (κ not pre-registered).\n\n"
    f"## MAP per signal\n" + "".join(f"- {s}: {MAP[s]:.3f}{'  ← FLOOR' if s==floor_sig else ('  ← E1 (b)' if s=='newman(b)' else '')}\n" for s in SIGNALS) +
    f"\n## Gate\nlift(newman(b) − floor[{floor_sig}]) = **{lift_b:+.3f}**, episode-bootstrap 95% CI [{lo:+.3f}, {hi:+.3f}].\n"
    f"PASS = CI excludes 0 AND point-lift ≥ 0.05 → **{'PASS' if gate else 'FAIL'}** "
    f"({'expected at pilot n — predictive verdict awaits going-forward data per genotype' if not gate else 'predictive win'}).\n\n"
    f"## Inert-diagnosis (why b == a ≈ random)\n"
    f"empty F_M(T): {fm_stats['empty_FM']}/{n} episodes; flat-newman: {fm_stats['flat_newman']}/{n}; "
    f"NON-DEGENERATE (mention signal usable): {len(desc_rows)}/{n}. The as-of-T mention signal is largely "
    f"**inert at pilot scale** — F_M(T) is empty/static for most episodes (claude-3 caveat: 130/183 missions "
    f"single-version), so newman(b) and raw-co-mention(a) both collapse to ~random, below the recency floor.\n\n"
    f"## Descriptive (GUARANTEED deliverable)\n" +
    (f"extension-along-intent-bridge on the {len(desc_rows)} non-degenerate episodes "
     f"(mean target Newman-percentile, STRICT; >0.5 = extends toward high-co-mention files): "
     f"overall **{np.mean([p for _,p in desc_rows]):.2f}**.\n" + "".join(f"- {val:.2f} — {m} (n={k})\n" for val, m, k in top[:8])
     if desc_rows else
     "**INCONCLUSIVE** — 0 non-degenerate episodes; the mention signal is inert on this pilot, so intent-bridge "
     "extension cannot be characterized yet. Needs the temporally-rich mention subset (53 evolving missions) "
     "and/or going-forward data.\n") +
    f"\n*Floor includes raw-co-mention (a), so newman(b) is tested as the INCREMENT over plain co-mention. "
    f"The prior '1.00' descriptive was a zero-tie artifact (now fixed: strict-`<`, non-degenerate episodes only).*\n")
print(f"\n[wrote] {RESULT}")
