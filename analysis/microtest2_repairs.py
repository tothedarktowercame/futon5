"""Micro-pilot 4: do candidate repairs produce a non-degenerate preference structure?

Success = (a) a domain interior retains its resident for >1 kind, and
          (b) different observation states select different kinds.
"""
import math, copy
BASE = {'builder':  dict(rule_change=.78, activity=.48, diversity=.72),
        'collapser':dict(rule_change=.18, activity=.12, diversity=.18),
        'chaos':    dict(rule_change=.90, activity=.78, diversity=.88),
        'identity': dict(rule_change=.02, activity=.35, diversity=.35)}
PREF = dict(rule_change=.15, hunger=.05)
EPS=1e-9
clamp=lambda x: min(max(x,EPS),1-EPS)
def kl(q,p):
    q,p=clamp(q),clamp(p); return q*math.log(q/p)+(1-q)*math.log((1-q)/(1-p))
def H(p):
    p=clamp(p); return -p*math.log(p)-(1-p)*math.log(1-p)

def make(model, w_risk=1.0, w_amb=1.0):
    def predict(k,act,div):
        b=model[k]; na=.5*b['activity']+.5*act; nd=.5*b['diversity']+.5*div
        return dict(rule_change=b['rule_change'],activity=na,diversity=nd,
                    hunger=(1-b['rule_change'])*(1-na))
    def claim(k):
        b=model[k]
        return dict(rule_change=b['rule_change'],activity=b['activity'],
                    diversity=b['diversity'],hunger=(1-b['rule_change'])*(1-b['activity']))
    def eig(s,f):
        a,b=1.0+s,1.0+f; m=a+b; return math.log(2)*((a*b)/(m*m*(m+1)))/(1/12)
    def total(k,act,div,lam,c,hc,hf):
        p=predict(k,act,div); nc=claim(k)
        return (w_risk*sum(kl(p[ch],nc[ch]) for ch in nc)
                + w_amb*sum(H(v) for v in p.values())
                + lam*kl(p['hunger'],PREF['hunger']) - c*eig(hc,hf))
    return total

OBS=[(a,d) for a in (0.,1/3,2/3,1.) for d in (1/3,2/3,1.)]
def evaluate(total, c, lam=.55, tau=.3):
    retained=[]; winners=set()
    for resident in BASE:
        keep=0
        for act,div in OBS:
            t={k: total(k,act,div,lam,c,*( (3,0) if k==resident else (0,0))) for k in BASE}
            m=min(t.values()); w={k:math.exp(-(v-m)/tau) for k,v in t.items()}
            z=sum(w.values()); p={k:v/z for k,v in w.items()}
            win=max(p,key=p.get); winners.add(win)
            if win==resident: keep+=1
        retained.append((resident, keep))
    return retained, winners

# balanced model: same mean |p-.5| for every kind, directions preserved
def rebalance(model, target=.25):
    out=copy.deepcopy(model)
    for k,v in out.items():
        ext=sum(abs(x-.5) for x in v.values())/3
        s=target/ext
        for ch in v: v[ch]=clamp(.5+(v[ch]-.5)*s)
    return out

CHAOS_UNPRED = copy.deepcopy(BASE)
CHAOS_UNPRED['chaos']=dict(rule_change=.55,activity=.52,diversity=.55)

REPAIRS=[("(none) baseline",            make(BASE)),
         ("A rebalanced extremeness",   make(rebalance(BASE))),
         ("B risk x3",                  make(BASE, w_risk=3.0)),
         ("B risk x6",                  make(BASE, w_risk=6.0)),
         ("C ambiguity x0.5",           make(BASE, w_amb=0.5)),
         ("D chaos truly unpredictable",make(CHAOS_UNPRED)),
         ("A+B rebalanced + risk x3",   make(rebalance(BASE), w_risk=3.0))]
for c in (0.0, 1.475):
    print(f"\n{'='*72}\nc = {c}   (domain interiors retained, out of 12 observation states)")
    print(f"{'repair':<30} " + " ".join(f"{k[:6]:>7}" for k in BASE) + "   distinct winners")
    print("-"*72)
    for name,tot in REPAIRS:
        ret,win=evaluate(tot,c)
        cells=" ".join(f"{n:>7}" for _,n in ret)
        flag="  <== NON-DEGENERATE" if len(win)>1 and sum(n for _,n in ret)>12 else ""
        print(f"{name:<30} {cells}   {len(win)}{flag}")

print("\n\n" + "="*72)
print("MICRO-PILOT 5: with risk x6, how much EIG can the structure survive?")
print("total retention across all 4 residents (max 48), and distinct winners\n")
print(f"{'c':>7} {'retained/48':>12} {'winners':>9}   {'structure'}")
print("-"*56)
tot6=make(BASE, w_risk=6.0)
for c in (0.0,0.05,0.1,0.2,0.3,0.5,0.75,1.0,1.475,3.0):
    ret,win=evaluate(tot6,c)
    tr=sum(n for _,n in ret)
    verdict=("non-degenerate" if tr>=12 and len(win)>=3 else
             "weak" if tr>=6 else "COLLAPSED")
    print(f"{c:>7} {tr:>12} {len(win):>9}   {verdict}")
print("\nper-kind retention at the largest c that still holds:")
for c in (0.1,0.2,0.3):
    ret,win=evaluate(tot6,c)
    print(f"  c={c:<5} " + "  ".join(f"{k[:6]}={n}" for k,n in ret))
