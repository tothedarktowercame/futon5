"""Micro-pilot 1: is a domain interior stable? Computed, not simulated.

Faithful port of futon5.exotype.efe/predict + score-policy and
pattern_eig/pattern-score for arm :next-C-plus-eig.
"""
import math, itertools

FIXED = {'builder':  dict(rule_change=.78, activity=.48, diversity=.72),
         'collapser':dict(rule_change=.18, activity=.12, diversity=.18),
         'chaos':    dict(rule_change=.90, activity=.78, diversity=.88),
         'identity': dict(rule_change=.02, activity=.35, diversity=.35)}
PREF = dict(rule_change=.15, hunger=.05)
EPS = 1e-9
clamp = lambda x: min(max(x, EPS), 1-EPS)
def kl(q,p):
    q,p = clamp(q),clamp(p); return q*math.log(q/p)+(1-q)*math.log((1-q)/(1-p))
def H(p):
    p = clamp(p); return -p*math.log(p)-(1-p)*math.log(1-p)

def predict(kind, act, div):
    b = FIXED[kind]
    na = .5*b['activity'] + .5*act
    nd = .5*b['diversity'] + .5*div
    return dict(rule_change=b['rule_change'], activity=na, diversity=nd,
                hunger=(1-b['rule_change'])*(1-na))

def next_claim(kind):
    b = FIXED[kind]
    return dict(rule_change=b['rule_change'], activity=b['activity'],
                diversity=b['diversity'],
                hunger=(1-b['rule_change'])*(1-b['activity']))

def eig(successes, failures):
    a, b = 1.0+successes, 1.0+failures; m = a+b
    return math.log(2)*((a*b)/(m*m*(m+1)))/(1/12)

def total(kind, act, div, lam, c, holders_conf, holders_fail):
    p = predict(kind, act, div); nc = next_claim(kind)
    risk = sum(kl(p[ch], nc[ch]) for ch in nc)
    ambiguity = sum(H(v) for v in p.values())
    conatus = kl(p['hunger'], PREF['hunger'])
    return risk + ambiguity + lam*conatus - c*eig(holders_conf, holders_fail)

LAM, ACT, DIV = 0.55, 2/3, 2/3      # mid-range observation
print("DOMAIN INTERIOR: resident kind held by all 3 neighbours (confirmed),")
print("candidate kinds held by none. Lower total = selected.\n")
print(f"{'c':>5}  " + "  ".join(f"{k:>10}" for k in FIXED) + "   winner")
print("-"*72)
for c in (0.0, 0.5, 1.0, 1.475, 3.0, 5.0, 7.0):
    for resident in ['builder']:
        row = {}
        for k in FIXED:
            hc, hf = (3, 0) if k == resident else (0, 0)
            row[k] = total(k, ACT, DIV, LAM, c, hc, hf)
        win = min(row, key=row.get)
        print(f"{c:>5}  " + "  ".join(f"{row[k]:>10.3f}" for k in FIXED) +
              f"   {win}{'  <-- resident holds' if win==resident else '  <-- DEFECTS'}")

# ---- micro-pilot 2: the full 12-state observation space, softmax at tau ----
def softmax_probs(act, div, lam, c, resident, tau=0.3):
    tot = {}
    for k in FIXED:
        hc, hf = (3, 0) if k == resident else (0, 0)
        tot[k] = total(k, act, div, lam, c, hc, hf)
    m = min(tot.values())
    w = {k: math.exp(-(v-m)/tau) for k, v in tot.items()}
    z = sum(w.values())
    return {k: v/z for k, v in w.items()}

print("\n\nMICRO-PILOT 2: P(select chaos) over the full observation space")
print("resident=builder (a domain interior), tau=0.3, lambda=0.55\n")
print(f"{'act':>5} {'div':>5} | " + " ".join(f"c={c:<5}" for c in (0.0,1.475,3.0,5.0)))
print("-"*52)
worst = []
for act in (0.0, 1/3, 2/3, 1.0):
    for div in (1/3, 2/3, 1.0):
        cells = []
        for c in (0.0, 1.475, 3.0, 5.0):
            p = softmax_probs(act, div, 0.55, c, 'builder')
            cells.append(p['chaos'])
        worst.append((min(cells), act, div))
        print(f"{act:>5.2f} {div:>5.2f} | " + " ".join(f"{v:>7.3f}" for v in cells))
mn, a, d = min(worst)
print(f"\nlowest P(chaos) anywhere in the space: {mn:.3f} at act={a:.2f} div={d:.2f}")
print("P(chaos) > 0.5 everywhere means the interior cannot hold ANY other kind.")

# ---- micro-pilot 3: which term creates the chaos preference at c=0? ----
print("\n\nMICRO-PILOT 3: term decomposition at c=0 (no EIG), act=2/3 div=2/3")
print("lower = preferred\n")
print(f"{'kind':<11} {'risk':>8} {'ambiguity':>10} {'lam*conatus':>12} {'TOTAL':>8}")
print("-"*54)
rows={}
for k in FIXED:
    p = predict(k, 2/3, 2/3); nc = next_claim(k)
    r = sum(kl(p[ch], nc[ch]) for ch in nc)
    a = sum(H(v) for v in p.values())
    co = 0.55*kl(p['hunger'], PREF['hunger'])
    rows[k]=(r,a,co,r+a+co)
    print(f"{k:<11} {r:>8.3f} {a:>10.3f} {co:>12.3f} {r+a+co:>8.3f}")
best=min(rows,key=lambda k:rows[k][3])
print(f"\nwinner at c=0: {best}")
for i,name in enumerate(('risk','ambiguity','lam*conatus')):
    lo=min(rows,key=lambda k:rows[k][i])
    spread=max(rows[k][i] for k in rows)-min(rows[k][i] for k in rows)
    print(f"  {name:<12} minimised by {lo:<10} spread {spread:.3f}")
print("\nfixed-model extremeness (mean |p-0.5| over the 3 declared channels):")
for k,v in FIXED.items():
    ext=sum(abs(x-.5) for x in v.values())/3
    print(f"  {k:<11} {ext:.3f}")
