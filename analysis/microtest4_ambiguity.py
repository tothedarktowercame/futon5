"""Micro-pilot 7: does making ambiguity CONDITIONAL ON CONFIRMATION fix the
chaos preference? Predicted before implementing.

Diagnosis (TN Finding 2): ambiguity = sum H(prediction) is minimised by extreme
predictions, and fixed-model gives chaos the most extreme parameters. So the term
rewards CONFIDENT claims regardless of whether they hold.

Fix under test: you only earn low ambiguity if your claim is actually confirmed
locally. A confidently-WRONG model gets no ambiguity credit.
"""
import math, itertools
FIXED={'builder':dict(rule_change=.78,activity=.48,diversity=.72),
       'collapser':dict(rule_change=.18,activity=.12,diversity=.18),
       'chaos':dict(rule_change=.90,activity=.78,diversity=.88),
       'identity':dict(rule_change=.02,activity=.35,diversity=.35)}
KINDS=list(FIXED); PREF=dict(rule_change=.15,hunger=.05); EPS=1e-9
clamp=lambda x: min(max(x,EPS),1-EPS)
def kl(q,p):
    q,p=clamp(q),clamp(p); return q*math.log(q/p)+(1-q)*math.log((1-q)/(1-p))
def H(p):
    p=clamp(p); return -p*math.log(p)-(1-p)*math.log(1-p)
def predict(k,act,div):
    b=FIXED[k]; na=.5*b['activity']+.5*act; nd=.5*b['diversity']+.5*div
    return dict(rule_change=b['rule_change'],activity=na,diversity=nd,
                hunger=(1-b['rule_change'])*(1-na))
def claim(k):
    b=FIXED[k]
    return dict(rule_change=b['rule_change'],activity=b['activity'],
                diversity=b['diversity'],hunger=(1-b['rule_change'])*(1-b['activity']))
def realized(act,div):
    "futon5.exotype.pattern-eig/realized-context, approximated on (act,div)."
    return dict(rule_change=1.0, activity=1.0 if act>0 else 0.0,
                diversity=1.0 if div>1/3 else 0.0, hunger=1.0 if act==0 else 0.0)
def confirmed(k,act,div):
    "claim-confirmed?: mean per-channel log-likelihood >= log(1/2)."
    nc=claim(k); r=realized(act,div)
    ll=sum(math.log(clamp(nc[c]) if r[c]==1.0 else 1-clamp(nc[c])) for c in r)/len(r)
    return ll >= math.log(0.5)

MAXAMB=4*math.log(2)   # four Bernoulli channels at maximum entropy
def total(k,act,div,lam,variant):
    p=predict(k,act,div); nc=claim(k)
    risk=sum(kl(p[ch],nc[ch]) for ch in nc)
    raw=sum(H(v) for v in p.values())
    if variant=='baseline':       amb=raw
    elif variant=='confirmed-only':
        amb=raw if confirmed(k,act,div) else MAXAMB
    elif variant=='confirm-weighted':
        amb=raw if confirmed(k,act,div) else 0.5*(raw+MAXAMB)
    return risk+amb+lam*kl(p['hunger'],PREF['hunger'])

def fixed_point(act,div,variant,lam=.55,tau=.3):
    pi={k:.25 for k in KINDS}
    for _ in range(300):
        new={k:0.0 for k in KINDS}
        for comp in itertools.product(range(4),repeat=3):
            pr=1.0
            for i in comp: pr*=pi[KINDS[i]]
            if pr<1e-12: continue
            t={k:total(k,act,div,lam,variant) for k in KINDS}
            m=min(t.values()); w={k:math.exp(-(v-m)/tau) for k,v in t.items()}
            z=sum(w.values())
            for k in KINDS: new[k]+=pr*w[k]/z
        s=sum(new.values()); pi={k:v/s for k,v in new.items()}
    return pi

print("Which kinds have CONFIRMED claims, by observation?\n")
OBS=[(0.,1/3),(0.,1.),(1/3,2/3),(2/3,2/3),(1.,1.)]
for act,div in OBS:
    ok=[k for k in KINDS if confirmed(k,act,div)]
    print(f"  act={act:.2f} div={div:.2f} -> {', '.join(ok) if ok else '(none)'}")

print("\n\nMean-field fixed point, top share by variant (lower = more mixed)\n")
print(f"{'obs':<18} " + "  ".join(f"{v:>17}" for v in ('baseline','confirmed-only','confirm-weighted')))
print("-"*74)
for act,div in OBS:
    row=[]
    for v in ('baseline','confirmed-only','confirm-weighted'):
        pi=fixed_point(act,div,v)
        top=max(pi,key=pi.get)
        row.append(f"{top[:8]} {pi[top]:.3f}")
    print(f"act={act:.2f} div={div:.2f}  " + "  ".join(f"{c:>17}" for c in row))
