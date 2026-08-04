"""Prediction 1: at MATCHED blend rate, does EIG-gating beat constant-beta?
That is the design's real claim -- blend where it is informative, not everywhere."""
import random, math, statistics
src = open("analysis/microtest5_kappa_optimum.py").read().split('def run(')[0]
g = {}
exec(compile(src, "k", "exec"), g)
bit, rule_out, blend_rule = g['bit'], g['rule_out'], g['blend_rule']
W, STEPS = 80, 4000

def run(mode, param, seed):
    """mode 'eig': p = 1-exp(-param*EIG).  mode 'const': p = param."""
    rng = random.Random(seed)
    geno = [rng.randrange(256) for _ in range(W)]
    phen = [rng.randrange(2) for _ in range(W)]
    blends = offers = 0
    for t in range(STEPS):
        new_phen = [rule_out(geno[i], phen[(i-1)%W], phen[i], phen[(i+1)%W]) for i in range(W)]
        hist=[0]*8
        for i in range(W): hist[(phen[(i-1)%W]<<2)|(phen[i]<<1)|phen[(i+1)%W]] += 1
        w=[h/W for h in hist]
        new_geno=list(geno)
        for i in range(W):
            b = blend_rule(geno[(i-1)%W], geno[i], geno[(i+1)%W])
            if b != geno[i]:
                offers += 1
                if mode == 'eig':
                    eig = sum(w[n] for n in range(8) if bit(geno[i],n) != bit(b,n))
                    p = 1.0-math.exp(-param*eig) if eig > 0 else 0.0
                else:
                    p = param
                if rng.random() < p:
                    blends += 1; new_geno[i] = b
        geno, phen = new_geno, new_phen
    runs,cur,n=[],geno[0],1
    for x in geno[1:]:
        if x==cur: n+=1
        else: runs.append(n); cur=x; n=1
    runs.append(n)
    return sum(runs)/len(runs), (blends/offers if offers else 0.0)

SEEDS=range(1,13)
print("Prediction 1 — EIG-gated vs constant-beta at MATCHED blend rate, 12 seeds\n")
print(f"{'kappa':>7} {'blend rate':>11} {'EIG domain':>12} {'const domain':>13} {'difference':>12}")
print("-"*60)
for kappa in (1.0, 8.0, 64.0):
    e = [run('eig', kappa, s) for s in SEEDS]
    rate = statistics.mean(r[1] for r in e)
    c = [run('const', rate, s) for s in SEEDS]
    em, cm = statistics.mean(r[0] for r in e), statistics.mean(r[0] for r in c)
    sd = ((statistics.stdev([r[0] for r in e])**2 + statistics.stdev([r[0] for r in c])**2)/2)**0.5
    print(f"{kappa:>7.1f} {rate:>11.3f} {em:>12.3f} {cm:>13.3f} {em-cm:>+8.3f} ({(em-cm)/sd:+.2f} sd)")
print("\nIf EIG-gating carries information, its domains should exceed the matched")
print("constant control by a clear margin. Within noise = the gate earns nothing.")
