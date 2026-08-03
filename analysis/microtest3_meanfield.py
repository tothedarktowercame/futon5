"""Micro-pilot 6: mean-field fixed point of the exotype mixture, validated
against measured runs.

pi_{t+1}[k] = E_{nbhd ~ Multinomial(3, pi_t)} P(select k | nbhd, observation)
Holders of a kind supply its Beta evidence; confirmation rate r is a parameter.
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
def eig(s,f):
    a,b=1.0+s,1.0+f; m=a+b; return math.log(2)*((a*b)/(m*m*(m+1)))/(1/12)
def total(k,act,div,lam,c,hc,hf,w_risk=1.0):
    p=predict(k,act,div); nc=claim(k)
    return (w_risk*sum(kl(p[ch],nc[ch]) for ch in nc) + sum(H(v) for v in p.values())
            + lam*kl(p['hunger'],PREF['hunger']) - c*eig(hc,hf))

def step(pi,act,div,lam,c,tau=.3,r=1.0,w_risk=1.0):
    """One mean-field update over all 3-cell neighbourhood compositions."""
    new={k:0.0 for k in KINDS}
    for comp in itertools.product(range(4),repeat=3):          # 3 neighbour slots
        ks=[KINDS[i] for i in comp]
        prob=1.0
        for kk in ks: prob*=pi[kk]
        if prob<1e-12: continue
        counts={k:ks.count(k) for k in KINDS}
        t={}
        for k in KINDS:
            n=counts[k]; hc=n*r; hf=n*(1-r)
            t[k]=total(k,act,div,lam,c,hc,hf,w_risk)
        m=min(t.values()); w={k:math.exp(-(v-m)/tau) for k,v in t.items()}
        z=sum(w.values())
        for k in KINDS: new[k]+=prob*w[k]/z
    s=sum(new.values())
    return {k:v/s for k,v in new.items()}

def fixed_point(act,div,lam,c,**kw):
    pi={k:.25 for k in KINDS}
    for _ in range(400): pi=step(pi,act,div,lam,c,**kw)
    return pi

print("MEAN-FIELD FIXED POINT vs MEASURED RUNS\n")
print("Slice 6d measured final mixtures (60 seeds):")
print("  w80  c=3.0 : builder .643  chaos .348  collapser .009  identity .000")
print("  w160 c=3.0 : builder .000  chaos .933  collapser .000  identity .067")
print("\nmean-field prediction at c=3.0, lambda=.55, tau=.3 (width-independent):")
print(f"  {'obs (act,div)':<16} " + "  ".join(f"{k[:8]:>8}" for k in KINDS))
for act,div in [(0.,1/3),(1/3,2/3),(2/3,2/3),(1.,1.)]:
    pi=fixed_point(act,div,.55,3.0)
    print(f"  act={act:.2f} div={div:.2f} " + "  ".join(f"{pi[k]:>8.3f}" for k in KINDS))
print("\nNOTE: mean-field has NO width dependence by construction, yet the two")
print("measured runs differ drastically at identical c. That is the test.")
