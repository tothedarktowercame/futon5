"""Micro-pilot 10: PRE-BUILD check. Does fitness-weighted neighbour-copying
assimilate anything in this substrate, or does the genotype distribution just drift?

If selection cannot move the genotype distribution, the selective filter is too
weak to be the missing Baldwin ingredient and TN-baldwin-selection-strategy is
dead before it is built. Reduced model as in micro-pilot 8.
"""
import random, math, statistics
def bit(r,n): return (r>>n)&1
def rule_out(r,l,c,x): return bit(r,(l<<2)|(c<<1)|x)
W, STEPS, WIN = 80, 3000, 40

def run(mode, strength, seed):
    rng = random.Random(seed)
    geno=[rng.randrange(256) for _ in range(W)]
    phen=[rng.randrange(2) for _ in range(W)]
    changes=[0]*W; active=[0]*W
    for t in range(STEPS):
        new_phen=[rule_out(geno[i],phen[(i-1)%W],phen[i],phen[(i+1)%W]) for i in range(W)]
        for i in range(W):
            if new_phen[i]!=phen[i]: active[i]+=1
        if t%WIN==WIN-1:
            # fitness over the window
            if mode=='none':
                fit=[0.0]*W
            elif mode=='preferences':
                # C term: conservative rule change (0.15), low hunger (0.05).
                # hunger ~ being boring = not active.
                fit=[-(abs(changes[i]/WIN-0.15) + abs((1-active[i]/WIN)-0.05)) for i in range(W)]
            elif mode=='divergence':
                # two-replica proxy: disagreement with neighbours
                fit=[ (1 if phen[i]!=phen[(i-1)%W] else 0)+(1 if phen[i]!=phen[(i+1)%W] else 0)
                      for i in range(W)]
            new_geno=list(geno)
            for i in range(W):
                j=(i-1)%W if rng.random()<0.5 else (i+1)%W
                d=fit[j]-fit[i]
                if d>0 and rng.random() < strength*d:
                    new_geno[i]=geno[j]; changes[i]+=1
            geno=new_geno
            changes=[0]*W; active=[0]*W
        phen=new_phen
    return len(set(geno)), max(geno.count(g) for g in set(geno))/W

print("MICRO-PILOT 10 — does selection assimilate? 8 seeds\n")
print(f"{'fitness':>13} {'strength':>9} {'distinct rules':>15} {'largest share':>14}")
print("-"*56)
for mode,strength in [('none',0.0),('preferences',0.3),('preferences',1.0),
                      ('divergence',0.3),('divergence',1.0)]:
    r=[run(mode,strength,s) for s in range(1,9)]
    print(f"{mode:>13} {strength:>9.1f} {statistics.mean(x[0] for x in r):>15.1f}"
          f" {statistics.mean(x[1] for x in r):>14.3f}")
print("\nDrift baseline is 'none'. Assimilation = markedly fewer distinct rules")
print("and a larger dominant share than drift.")
