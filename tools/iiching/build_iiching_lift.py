#!/usr/bin/env python3
"""build_iiching_lift.py — freeze the iching codebook + LIFT it to iiching.

  iiching sigil (8 bits) = iching generator (6 bits, high) x exotype kernel-rewrite (2 bits, low)
  = "apply 2nd-order operator R to 1st-order CT primitive G" = a change-of-change.

The 4 rewrites are exactly the morphisms of `futon5/src/futon5/ct/exotype_programs.clj` that target
:kernel-spec (the change-rule itself): gate-update, contextual-mutate, set-mix, normalize. 64x4=256.

Reads /tmp/iiching-codebook.json (the frozen 64); writes canonical EDN into
futon5/resources/iiching-ct/. claude-6 + Joe, 2026-06-02.
  futon3a/.venv/bin/python build_iiching_lift.py
"""
import json, pathlib

cb = json.loads(pathlib.Path("/tmp/iiching-codebook.json").read_text())
gens = sorted(cb.values(), key=lambda r: r["code"])
assert len(gens) == 64

# the 2-bit lift: the 4 kernel-spec -> kernel-spec rewrites of exotype-program-category
REWRITES = [
    ("gate-update",       "gate/condition the rule on context (enable/disable a hook)"),
    ("contextual-mutate", "mutate the rule from context (rewrite the kernel locally)"),
    ("set-mix",           "blend/mix rule variants (interpolate kernels)"),
    ("normalize",         "renormalize the rule (restore well-formedness)"),
]

OUT = pathlib.Path("/home/joe/code/futon5/resources/iiching-ct")
OUT.mkdir(parents=True, exist_ok=True)

def edn_str(s): return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'

# --- canonical iching codebook (6-bit, 64) ---
cl = ['{:meta {:layer :iching',
      '        :bits 6 :count 64',
      '        :semantics "the 1st-order CT generator basis (retract target); lossy codebook"',
      '        :selection "triangulated arxiv(math.CT df) + nlab in-link + Lean CategoryTheory; curated by Joe"',
      '        :similarity "nlab [[link]] graph -> spectral embed -> Hungarian assign to 6-bit hypercube"',
      '        :hamming-vs-dissimilarity-corr 0.27',
      '        :built-by "futon5/tools/iiching/build_iching_codebook.py"',
      '        :date "2026-06-02"}',
      ' :generators [']
for g in gens:
    cl.append(f'  {{:code {g["code"]} :bits "{format(g["code"],"06b")}" '
              f':generator {edn_str(g["generator"])} '
              f':arxiv-df {g["arxiv_df"]} :nlab-inlink {g["nlab_inlink"]} :lean {g["lean"]}}}')
cl.append(' ]}')
(OUT / "iiching-ct-codebook.edn").write_text("\n".join(cl) + "\n")

# --- iiching lift (8-bit, 256) = generator x rewrite ---
ll = ['{:meta {:layer :iiching',
      '        :bits 8 :count 256',
      '        :lift "iching generator (6 high bits) x exotype kernel-rewrite (2 low bits)"',
      '        :semantics "2nd-order: apply a change-of-change operator R to CT primitive G"',
      '        :source "futon5/src/futon5/ct/exotype_programs.clj morphisms targeting :kernel-spec"',
      '        :adjunction "iching retract (6-bit) -| iiching lift (8-bit); the join is the 2-bit operator"',
      '        :date "2026-06-02"}',
      ' :rewrites [' + ' '.join(
          f'{{:idx {i} :bits "{format(i,"02b")}" :rewrite :{r} :doc {edn_str(d)}}}'
          for i, (r, d) in enumerate(REWRITES)) + ']',
      ' :sigils [']
for g in gens:
    for ri, (r, _d) in enumerate(REWRITES):
        sig = (g["code"] << 2) | ri
        ll.append(f'  {{:sigil {sig} :bits "{format(sig,"08b")}" '
                  f':generator {edn_str(g["generator"])} :generator-code {g["code"]} '
                  f':rewrite :{r}}}')
ll.append(' ]}')
(OUT / "iiching-ct-lift.edn").write_text("\n".join(ll) + "\n")

print(f"[frozen] {len(gens)} iching generators -> {OUT}/iiching-ct-codebook.edn")
print(f"[lifted] {len(gens)*4} iiching sigils  -> {OUT}/iiching-ct-lift.edn")
print(f"\n4 lift-rewrites (the 2 low bits): " + ", ".join(r for r, _ in REWRITES))
print(f"\nsample sigils (generator x rewrite):")
for g in gens[:3] + [x for x in gens if x['generator'] in ('functor','adjunction','monad')]:
    for ri, (r, _) in enumerate(REWRITES):
        sig = (g["code"] << 2) | ri
        print(f"  {format(sig,'08b')}  {g['generator']:22s} | {r}")
    print()
