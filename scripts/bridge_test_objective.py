#!/usr/bin/env python3
"""Bridge test for the INTRINSIC objective (PLAN step 2).

E0 asked whether an internal quantity predicts DAMAGE REACH and found nothing --
but damage reach is an observer-side counterfactual the agent cannot perceive, and
its band turned out to be an artifact of t=100.  This asks the same question of the
local-compressibility objective, which IS computable from the system's own output.

Gate: can (halting share, change rate) predict mid_range on HELD-OUT cells, better
than predicting the mean?  If not, no learned policy over those observables can
steer, and controller #4 must not be built.

Honest baseline throughout: R2 vs predicting the training mean.  A model that cannot
beat the mean has learned nothing, whatever its in-sample fit looks like.
"""
import csv, sys, statistics as st, itertools

def load(objective_csv, observable_csv):
    obj = {}
    for r in csv.DictReader(open(objective_csv)):
        k = (float(r['gamma']), float(r['kappa']))
        obj.setdefault(k, []).append(float(r['mid_range']))
    obs = {}
    for r in csv.DictReader(open(observable_csv)):
        k = (float(r['gamma']), float(r['kappa']))
        obs.setdefault(k, []).append((float(r['halting']), float(r['change'])))
    cells = sorted(set(obj) & set(obs))
    X = [(st.mean([h for h, _ in obs[k]]), st.mean([c for _, c in obs[k]])) for k in cells]
    y = [st.mean(obj[k]) for k in cells]
    return cells, X, y

def fit(X, y):
    """OLS with intercept on 2 predictors -- normal equations, no numpy dependency."""
    n = len(X)
    A = [[1.0, x[0], x[1]] for x in X]
    ATA = [[sum(A[i][a] * A[i][b] for i in range(n)) for b in range(3)] for a in range(3)]
    ATy = [sum(A[i][a] * y[i] for i in range(n)) for a in range(3)]
    # gaussian elimination
    M = [row[:] + [ATy[i]] for i, row in enumerate(ATA)]
    for c in range(3):
        p = max(range(c, 3), key=lambda r: abs(M[r][c]))
        if abs(M[p][c]) < 1e-12: return None
        M[c], M[p] = M[p], M[c]
        for r in range(3):
            if r != c:
                f = M[r][c] / M[c][c]
                for k in range(c, 4): M[r][k] -= f * M[c][k]
    return [M[i][3] / M[i][i] for i in range(3)]

def predict(b, x): return b[0] + b[1] * x[0] + b[2] * x[1]

def loo(X, y):
    """Leave-one-out held-out prediction. With ~35 cells this uses the data honestly."""
    preds = []
    for i in range(len(X)):
        Xtr = X[:i] + X[i+1:]; ytr = y[:i] + y[i+1:]
        b = fit(Xtr, ytr)
        preds.append(predict(b, X[i]) if b else st.mean(ytr))
    return preds

def r2(y, p):
    m = st.mean(y)
    ss_res = sum((a - b) ** 2 for a, b in zip(y, p))
    ss_tot = sum((a - m) ** 2 for a in y)
    return 1 - ss_res / ss_tot if ss_tot > 0 else float('nan')

if __name__ == '__main__':
    cells, X, y = load(sys.argv[1], sys.argv[2])
    print(f"  cells with both objective and observables: {len(cells)}")
    print(f"  objective mid_range: mean {st.mean(y):.4f}  SD {st.pstdev(y):.4f}  "
          f"range [{min(y):.3f}, {max(y):.3f}]")
    p = loo(X, y)
    R2 = r2(y, p)
    print(f"\n  LEAVE-ONE-OUT held-out R2 of (halting, change) -> mid_range:  {R2:+.4f}")
    print(f"  baseline (predict the training mean):                          0.0000")
    mae = st.mean([abs(a - b) for a, b in zip(y, p)])
    mae0 = st.mean([abs(a - st.mean(y)) for a in y])
    print(f"  held-out MAE {mae:.4f}   vs mean-baseline MAE {mae0:.4f}")
    print(f"\n  VERDICT: {'PASSES -- observables carry usable signal about the objective'
                          if R2 > 0.2 else
                          'FAILS -- no better than the mean; do NOT build controller #4'}")
    b = fit(X, y)
    if b: print(f"\n  in-sample coefficients: mid = {b[0]:+.3f} {b[1]:+.3f}*halting {b[2]:+.3f}*change")
    print(f"  in-sample R2 (NOT the gate, shown only to expose overfit): {r2(y, [predict(b,x) for x in X]):+.4f}")
