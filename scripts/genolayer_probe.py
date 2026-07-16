"""The MetaCA GENOTYPE layer alone — no phenotype, no rendering.

Transcribed from 256ca-2014-12-29-BUGGY.el:551-583 (evolve-sigil-with-blending-mutation),
whose 4th arg is literally named `ignore`. Structural reimplementation, NOT bit-exact
(the real engine has an unusual head/tail/interior order); the Elisp harness is ground truth.
"""
import random, collections

TT3 = ["000","001","010","100","011","101","110","111"]   # legacy order, el:97-105
BOUNDARY = "00000000"                                      # sigil "!" = rule 0

def rule_number(g):
    """legacy genotype string -> standard Wolfram rule number"""
    return sum(int(g[i]) * (1 << int(TT3[i], 2)) for i in range(8))

def mutate(g, n, rng):
    """el:527 mutate-genotype-n. read 0-based pos, write at (goto-char pos) which is
    1-based and CLAMPS 0 -> point-min. So bit[max(pos-1,0)] := not bit[pos]."""
    for _ in range(n):
        pos = rng.randrange(8)
        val = '1' if g[pos] == '0' else '0'
        w = max(pos - 1, 0)
        g = g[:w] + val + g[w+1:]
    return g

def step_cell(p, s, n, rng, mutations=2):
    """el:551. blend where neighbours agree; else APPLY THE CELL'S OWN BYTE AS A RULE
    to the triple of i-th bits. Then mutate."""
    local_rule = dict(zip(TT3, s))
    out = []
    for i in range(8):
        if p[i] == '0' and n[i] == '0':   out.append('0')
        elif p[i] == '1' and n[i] == '1': out.append('1')
        else:                             out.append(local_rule[p[i] + s[i] + n[i]])
    return mutate(''.join(out), mutations, rng)

def run(width=60, gens=120, seed=0):
    rng = random.Random(seed)
    row = [''.join(rng.choice('01') for _ in range(8)) for _ in range(width)]
    for _ in range(gens):
        nxt = []
        for j in range(width):
            p = row[j-1] if j > 0 else BOUNDARY
            n = row[j+1] if j < width-1 else BOUNDARY
            nxt.append(step_cell(p, row[j], n, rng))
        row = nxt
    return row

# NOTE ON ENCODING: the paper's "42/170" are the genotype string read as PLAIN BINARY,
# matching the sigil colours (#2a2a2a = 0x2a = 42, #aaaaaa = 0xaa = 170). They are NOT
# legacy->standard Wolfram rule numbers -- under rule_number() the same two strings are
# 76/77. Mind the convention (the A1/A4 bit-order finding).

if __name__ == "__main__":
    print("GENOTYPE LAYER ALONE -- no phenotype, no CA, no rendering")
    print("(structural reimpl of 256ca-2014-12-29-BUGGY.el:551-583; the Elisp harness is ground truth)\n")
    hits = 0
    for s in range(15):
        c = collections.Counter(run(seed=s))
        strs = sorted(c)
        as_bin = sorted(int(g, 2) for g in strs)
        ok = as_bin == [42, 170]
        hits += ok
        print(f"  seed {s:2d}: {strs} -> as plain binary {as_bin}"
              f"{'   <== 42/170' if ok else ''}")
    print(f"\nreached exactly {{42,170}}: {hits}/15")
    print("compare baldwin-repro/README.md: the Elisp blending-mutation control is 15/15.\n")
    a, b = "00101010", "10101010"
    print(f"42 = 0b{a}   170 = 0b{b}   differ only at index {[i for i in range(8) if a[i]!=b[i]]}")
    print("index 0 is the bit the propagator writes TWICE (k=0 self-flip + k=1) and")
    print("therefore the one bit that can never settle: k=0 demands g[0] = NOT g[0].")
    print("bit 7 is never written at all -- nothing shifts into the top of the cascade.")
