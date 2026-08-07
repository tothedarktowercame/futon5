"""E1 (TN-epistemic-term-evaluation §7): gated vs matched-constant, scored on
DAMAGE REACH instead of mean rule domain length.

Everything inherited unchanged from microtest6_eig_vs_constant.py: W=80,
STEPS=4000, seeds 1-12, kappa in {1, 8, 64}, matched-constant rate = mean
realized blend rate of the eig runs, shared bit/rule_out/blend_rule from
microtest5, identical sequential RNG usage (unperturbed trajectories are
bit-identical to the original comparison's).

Only the scored observable changes.  Twin protocol per (mode, param, seed):
one run as before; one copy from the same seed with phen[0] flipped at t=0.
Damage = count of differing state cells at horizons t = 100 (primary, the
calibrated instrument's horizon), 1000, 4000.  Rule-layer damage recorded at
the same horizons as a harness check: in const mode it must be exactly 0
(offers depend only on geno and p is fixed, so the rule layer is independent
of the state layer by construction).

No argmax verdict line.  Signs and seed-level spread reported per kappa.
"""
import random, math, statistics, os

HERE = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(HERE, "microtest5_kappa_optimum.py")).read().split('def run(')[0]
g = {}
exec(compile(src, "k", "exec"), g)
bit, rule_out, _blend_rule = g['bit'], g['rule_out'], g['blend_rule']

# blend_rule is pure; memoize for speed.  Bit-identical results, no RNG impact.
_bcache = {}
def blend_rule(l, c, r):
    k = (l, c, r)
    v = _bcache.get(k)
    if v is None:
        v = _bcache[k] = _blend_rule(l, c, r)
    return v

W, STEPS = 80, 4000
HORIZONS = (100, 1000, 4000)
FLIP = 0
SEEDS = range(1, 13)
KAPPAS = (1.0, 8.0, 64.0)

def run(mode, param, seed, flip=False):
    """mode 'eig': p = 1-exp(-param*EIG).  mode 'const': p = param.
    Identical to microtest6.run except: optional t=0 state flip, and
    (phen, geno) snapshots at HORIZONS.  Returns (snaps, blend_rate)."""
    rng = random.Random(seed)
    geno = [rng.randrange(256) for _ in range(W)]
    phen = [rng.randrange(2) for _ in range(W)]
    if flip:
        phen[FLIP] ^= 1
    blends = offers = 0
    snaps = {}
    for t in range(STEPS):
        new_phen = [rule_out(geno[i], phen[(i-1) % W], phen[i], phen[(i+1) % W])
                    for i in range(W)]
        hist = [0]*8
        for i in range(W):
            hist[(phen[(i-1) % W] << 2) | (phen[i] << 1) | phen[(i+1) % W]] += 1
        w = [h / W for h in hist]
        new_geno = list(geno)
        for i in range(W):
            b = blend_rule(geno[(i-1) % W], geno[i], geno[(i+1) % W])
            if b != geno[i]:
                offers += 1
                if mode == 'eig':
                    eig = sum(w[n] for n in range(8) if bit(geno[i], n) != bit(b, n))
                    p = 1.0 - math.exp(-param*eig) if eig > 0 else 0.0
                else:
                    p = param
                if rng.random() < p:
                    blends += 1
                    new_geno[i] = b
        geno, phen = new_geno, new_phen
        if (t + 1) in HORIZONS:
            snaps[t + 1] = (list(phen), list(geno))
    return snaps, (blends/offers if offers else 0.0)

def damage(mode, param, seed):
    """Twin run; per-horizon (state damage, rule damage)."""
    base, rate = run(mode, param, seed, flip=False)
    pert, _ = run(mode, param, seed, flip=True)
    out = {}
    for h in HORIZONS:
        pb, gb = base[h]
        pp, gp = pert[h]
        out[h] = (sum(a != b for a, b in zip(pb, pp)),
                  sum(a != b for a, b in zip(gb, gp)))
    return out, rate

def summarize(vals):
    return statistics.mean(vals), (statistics.stdev(vals) if len(vals) > 1 else 0.0)

print("E1 — EIG-gated vs constant-beta at MATCHED blend rate, scored on DAMAGE REACH")
print(f"W={W} STEPS={STEPS} seeds={list(SEEDS)} flip=phen[{FLIP}] at t=0; horizons={HORIZONS}\n")

for kappa in KAPPAS:
    e = [damage('eig', kappa, s) for s in SEEDS]
    rate = statistics.mean(r for _, r in e)
    c = [damage('const', rate, s) for s in SEEDS]
    print(f"kappa = {kappa:g}   matched blend rate = {rate:.3f}")
    print(f"  {'horizon':>7} {'eig dmg':>15} {'const dmg':>15} {'diff':>7} {'diff/sd':>8} "
          f"{'e>c':>4} {'e<c':>4} {'tie':>4} {'eig geno-dmg':>13} {'const geno-dmg':>15}")
    for h in HORIZONS:
        ed = [d[h][0] for d, _ in e]
        cd = [d[h][0] for d, _ in c]
        eg = [d[h][1] for d, _ in e]
        cg = [d[h][1] for d, _ in c]
        em, es = summarize(ed)
        cm, cs = summarize(cd)
        sd = ((es**2 + cs**2)/2)**0.5
        z = (em - cm)/sd if sd > 0 else float('nan')
        gt = sum(a > b for a, b in zip(ed, cd))
        lt = sum(a < b for a, b in zip(ed, cd))
        tie = len(ed) - gt - lt
        egm, _ = summarize(eg)
        cgm, _ = summarize(cg)
        chk = "OK" if all(v == 0 for v in cg) else "VIOLATION"
        print(f"  {h:>7} {em:>7.2f}±{es:<6.2f} {cm:>7.2f}±{cs:<6.2f} {em-cm:>+7.2f} {z:>+8.2f} "
              f"{gt:>4} {lt:>4} {tie:>4} {egm:>13.2f} {cgm:>10.2f} {chk}")
    # paired statistics at primary horizon (seeds shared between arms)
    h = 100
    diffs = [d[h][0] - cD[h][0] for (d, _), (cD, _) in zip(e, c)]
    md = statistics.mean(diffs)
    se = statistics.stdev(diffs)/math.sqrt(len(diffs)) if len(diffs) > 1 else float('nan')
    print(f"  paired (per-seed) diff at t={h}: {md:+.2f} ± {se:.2f} SE   t = {md/se:+.2f}  (n={len(diffs)})")
    print(f"  per-seed state damage at t={h} (eig | const): " +
          " ".join(f"{d[h][0]}|{cD[h][0]}" for (d, _), (cD, _) in zip(e, c)))
    print()

print("Const-arm geno-dmg must be 0 at every horizon (rule layer independent of")
print("state layer in const mode); any VIOLATION above invalidates the harness.")
print("No verdict line on purpose: read signs, spreads, and absolute levels.")
