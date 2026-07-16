"""The propagator as a CONSTRAINT SYSTEM — solve it, don't simulate it.

The operator `bit[f(k)] := NOT bit[k]` imposes the constraint g[f(k)] = ~g[k].
The attractor is that system's solution structure, which has exactly three parts:

  FREE VARS   bits never written (not in image of f) -> pinned by blending/boundary
              == THE SCAFFOLD
  CHAIN       satisfiable constraints -> determined by propagation from the free vars
              == THE STRUCTURE
  UNSAT CORE  odd cycles, esp. fixed points f(k)=k giving g[k] = ~g[k]
              == THE MOTION (these bits flicker; they can never settle)

Figure 8 falls out analytically: free={7}, unsat={0}, solutions {42,170}.
See holes/F-what-the-propagator-actually-does.md
"""
import itertools

def analyse(f, n=8):
    free  = [j for j in range(n) if j not in set(f(k) for k in range(n))]
    unsat = [k for k in range(n) if f(k) == k]
    return free, unsat

def solve(f, n=8, pin=0):
    free, unsat = analyse(f, n)
    g = [None]*n
    for j in free: g[j] = pin
    for _ in range(n):
        for k in range(n):
            if g[k] is not None and f(k) != k and g[f(k)] is None:
                g[f(k)] = 1 - g[k]
    out = set()
    for bits in itertools.product([0,1], repeat=len(unsat)):
        h = list(g)
        for b, k in zip(bits, unsat): h[k] = b
        if all(x is not None for x in h): out.add(''.join(map(str, h)))
    return free, unsat, sorted(out)

if __name__ == "__main__":
    free, unsat, sols = solve(lambda k: max(k-1, 0))
    print("THE EMACS BUG  g[max(k-1,0)] = NOT g[k]")
    print(f"  free (scaffold) {free}   unsat (motion) {unsat}")
    print(f"  solutions {sols} = {sorted(int(s,2) for s in sols)}   <- 42/170 DERIVED\n")
    print("IDENTITY:", analyse(lambda k: k), "-> 8/8 unsat = pure noise (the ants' worst arm)")
    nofree = sum(1 for p in itertools.permutations(range(8))
                 if not analyse(lambda k, p=p: p[k])[0])
    print(f"PERMUTATIONS with no free var: {nofree}/40320 -- onto, so NEVER a scaffold.")
