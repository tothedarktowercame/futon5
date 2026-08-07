"""E1 robustness variant: same comparison as microtest6b_damage_vs_constant.py,
but acceptance noise is a per-(t, i) COMMON field shared by the twin runs
(standard common-random-numbers practice for damage spreading in stochastic CA).

Why: in the primary (as-original) protocol the twins share one sequential RNG
stream, so once the eig-arm rule layers first diverge, the twins' subsequent
acceptance noise desynchronizes and measured damage mixes causal propagation
with amplification of decorrelated noise.  The const arm can never desync
(rule layer independent of state layer), so the confound inflates the eig arm
only.  Here noise[t][i] is drawn once per seed and used by both twins, so a
decision differs between twins only when the perturbation causally changes the
acceptance probability (or the offer itself) at that (t, i).

Baseline trajectories differ from the original scripts (different noise
consumption), so the matched constant rate is recomputed within-variant.
Everything else matches microtest6b: W=80, STEPS=4000, seeds 1-12,
kappa in {1, 8, 64}, flip phen[0] at t=0, horizons 100/1000/4000.
"""
import random, math, statistics, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(HERE, "microtest5_kappa_optimum.py")).read().split('def run(')[0]
g = {}
exec(compile(src, "k", "exec"), g)
bit, rule_out, _blend_rule = g['bit'], g['rule_out'], g['blend_rule']

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
SEEDS = range(1, 1 + (int(sys.argv[1]) if len(sys.argv) > 1 else 12))
KAPPAS = (1.0, 8.0, 64.0)

_noise_cache = {}
def noise_field(seed):
    f = _noise_cache.get(seed)
    if f is None:
        r = random.Random(f"noise-{seed}")
        f = _noise_cache[seed] = [[r.random() for _ in range(W)] for _ in range(STEPS)]
    return f

def run(mode, param, seed, flip=False):
    rng = random.Random(seed)          # init only, same init as 6b/original
    geno = [rng.randrange(256) for _ in range(W)]
    phen = [rng.randrange(2) for _ in range(W)]
    if flip:
        phen[FLIP] ^= 1
    nf = noise_field(seed)
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
        row = nf[t]
        for i in range(W):
            b = blend_rule(geno[(i-1) % W], geno[i], geno[(i+1) % W])
            if b != geno[i]:
                offers += 1
                if mode == 'eig':
                    eig = sum(w[n] for n in range(8) if bit(geno[i], n) != bit(b, n))
                    p = 1.0 - math.exp(-param*eig) if eig > 0 else 0.0
                else:
                    p = param
                if row[i] < p:
                    blends += 1
                    new_geno[i] = b
        geno, phen = new_geno, new_phen
        if (t + 1) in HORIZONS:
            snaps[t + 1] = (list(phen), list(geno))
    return snaps, (blends/offers if offers else 0.0)

def damage(mode, param, seed):
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

print("E1 robustness — common per-(t,i) acceptance noise (causal damage only)")
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
    h = 100
    diffs = [d[h][0] - cD[h][0] for (d, _), (cD, _) in zip(e, c)]
    md = statistics.mean(diffs)
    se = statistics.stdev(diffs)/math.sqrt(len(diffs)) if len(diffs) > 1 else float('nan')
    print(f"  paired (per-seed) diff at t={h}: {md:+.2f} ± {se:.2f} SE   t = {md/se:+.2f}  (n={len(diffs)})")
    print(f"  per-seed state damage at t={h} (eig | const): " +
          " ".join(f"{d[h][0]}|{cD[h][0]}" for (d, _), (cD, _) in zip(e, c)))
    print()
