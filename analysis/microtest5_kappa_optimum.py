"""Micro-pilot 8: does EIG-gated blending have an INTERIOR OPTIMUM in kappa?

This is the falsifier for TN-eig-definition prediction 4. If genotype domain
structure rises monotonically in kappa, the design is wrong and there is no
epistemic sweet spot -- "blend whenever anything differs" would be optimal.

Reduced model: 80-cell ring, per-cell 8-bit rule (genotype) + bit (phenotype).
Phenotype: standard ECA step under each cell's own rule.
Genotype: neighbour-agreement blend (ported from mmca-clj core.clj:79), applied
with probability 1 - exp(-kappa * EIG), where
    EIG(i) = sum_n w(n) * [bit(rule_i,n) != bit(blend_i,n)]
    w(n)   = observed frequency of phenotype neighbourhood pattern n.
The propagator is omitted: it is a per-cell bijection with no spatial coupling,
so it cannot create or destroy the spatial structure this test measures.
"""
import random, math

W, STEPS, BURN = 80, 4000, 2000

def bit(rule, n):      return (rule >> n) & 1
def rule_out(rule, l, c, r): return bit(rule, (l << 2) | (c << 1) | r)

def blend_rule(left, centre, right):
    """Agreement preserved; centre rule adjudicates disagreements."""
    out = 0
    for n in range(8):
        lb, cb, rb = bit(left, n), bit(centre, n), bit(right, n)
        v = lb if lb == rb else rule_out(centre, lb, cb, rb)
        out |= v << n
    return out

def run(kappa, seed):
    rng = random.Random(seed)
    geno = [rng.randrange(256) for _ in range(W)]
    phen = [rng.randrange(2) for _ in range(W)]
    for t in range(STEPS):
        # phenotype step, each cell under its own rule
        new_phen = [rule_out(geno[i], phen[(i-1) % W], phen[i], phen[(i+1) % W])
                    for i in range(W)]
        # w(n): how often each neighbourhood pattern actually occurs right now
        hist = [0]*8
        for i in range(W):
            hist[(phen[(i-1) % W] << 2) | (phen[i] << 1) | phen[(i+1) % W]] += 1
        w = [h / W for h in hist]
        new_geno = list(geno)
        for i in range(W):
            b = blend_rule(geno[(i-1) % W], geno[i], geno[(i+1) % W])
            if b != geno[i]:
                eig = sum(w[n] for n in range(8) if bit(geno[i], n) != bit(b, n))
                if eig > 0 and rng.random() < 1.0 - math.exp(-kappa * eig):
                    new_geno[i] = b
        geno, phen = new_geno, new_phen
    # measure genotype domain structure: mean run length of identical rules
    runs, cur, n = [], geno[0], 1
    for g in geno[1:]:
        if g == cur: n += 1
        else: runs.append(n); cur = g; n = 1
    runs.append(n)
    return sum(runs)/len(runs), len(set(geno))

print("MICRO-PILOT 8 — interior optimum in kappa?\n")
print("mean genotype domain length (higher = more structure), 4 seeds\n")
print(f"{'kappa':>8}  {'mean run':>9}  {'distinct rules':>15}")
print("-"*38)
results=[]
for kappa in [0.0, 0.5, 1.0, 2.0, 4.0, 8.0, 16.0, 32.0, 64.0, 1000.0]:
    rl = [run(kappa, s) for s in (1, 2, 3, 4)]
    mr = sum(r[0] for r in rl)/len(rl)
    md = sum(r[1] for r in rl)/len(rl)
    results.append((kappa, mr))
    print(f"{kappa:>8.1f}  {mr:>9.3f}  {md:>15.1f}")
best = max(results, key=lambda x: x[1])
interior = best[0] not in (results[0][0], results[-1][0])
print(f"\nbest kappa = {best[0]} (mean run {best[1]:.3f})")
print(f"INTERIOR OPTIMUM: {'YES — design prediction 4 holds' if interior else 'NO — prediction 4 FAILS'}")
