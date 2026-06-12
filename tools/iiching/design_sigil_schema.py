#!/usr/bin/env python3
"""design_sigil_schema.py — Fork A (collapse) + Fork B (Hamming-legible) bit-field for the
iiching=reduced-CT meta-theory. Reads /tmp/iiching-concept-manifest.json (the triangulated
ranking) and assigns each load-bearing concept an 8-bit sigil whose bits MEAN something.

LAYOUT (bit7..bit0):
  bits 7-5  ROLE        (3 bits, 8 roles) — the "opcode": what kind of CT entity
  bit  4    DUAL        (global) — co-/op-/right: a concept and its dual differ by EXACTLY this bit
  bits 3-0  MODIFIERS   (4 bits) — role-specific "operands" (collapse families to head+flags)

So Hamming(A,B) ~= conceptual distance: dual flips 1 bit; each added structure flips 1 bit.
This is a DRAFT schema — the role taxonomy + per-role modifier vocab are the ratification points.
claude-6 + Joe, 2026-06-02.
  futon6/.venv/bin/python design_sigil_schema.py
"""
import json, re, pathlib

MAN = json.loads(pathlib.Path("/tmp/iiching-concept-manifest.json").read_text())

ROLES = {  # bits 7-5
    0: "CATEGORY",      # category, monoidal/abelian/model category, topos, site
    1: "OBJECT",        # terminal object, internal hom, monoid, group object
    2: "MORPHISM",      # morphism, mono/epi/iso, fibration
    3: "FUNCTOR",       # functor, faithful/full, forgetful
    4: "TWOCELL",       # natural transformation, modification
    5: "UNIVERSAL",     # limit/colimit, adjoint, Kan extension, (co)product, pullback
    6: "PROPERTY",      # exact, complete, cartesian-closed, accessible
    7: "OPERATION",     # composition, tensor, nerve, realization, localization
}
# per-role modifier vocab (bit3,bit2,bit1,bit0). DRAFT — dominant roles defined; others coarse.
MODS = {
    0: ["monoidal", "symmetric/braided", "closed", "enriched"],   # CATEGORY
    5: ["finite/binary", "filtered", "weighted", "pointwise"],     # UNIVERSAL
    3: ["faithful", "full", "adjoint", "monoidal"],                # FUNCTOR
    1: ["zero", "monoid", "group", "projective"],                  # OBJECT
    2: ["iso", "regular", "split", "strong"],                      # MORPHISM
    4: ["natural", "lax", "icon", "mate"],                         # TWOCELL
    6: ["exact", "complete", "closed", "accessible"],              # PROPERTY
    7: ["tensor", "internal", "free", "localized"],                # OPERATION
}
DUAL_LEX = ["colimit", "cocone", "coproduct", "comonad", "coend", "coalgebra",
            "coequalizer", "cokernel", "pushout", "right adjoint", "opposite", "epi"]

def role_of(n):
    if "functor" in n: return 3
    if "transformation" in n or "modification" in n: return 4
    if any(k in n for k in ("limit", "colimit", "adjoint", "kan extension", "product",
                            "pullback", "pushout", "cone", "coend", " end", "equalizer",
                            "kernel", "extension")): return 5
    if "category" in n or "categories" in n or n in ("topos", "site", "locale", "quasicategory"): return 0
    if any(k in n for k in ("morphism", "fibration", " map", "arrow", "epi", "mono", "iso ", "isomorphism")): return 2
    if "object" in n or "hom" in n or "monoid" in n or "group" in n or "algebra" in n: return 1
    if any(k in n for k in ("exact", "complete", "accessible", "presentable", "closed")) and "category" not in n: return 6
    return 7

def dual_of(n):
    return int(any(d in n for d in DUAL_LEX))

def mods_of(n, role):
    vocab = MODS[role]; bits = 0
    for i, kw in enumerate(vocab):  # i=0 -> bit3 ... i=3 -> bit0
        keys = kw.split("/")
        if any(k in n for k in keys):
            bits |= (1 << (3 - i))
    return bits

def encode(name):
    n = name.lower()
    r = role_of(n)
    return (r << 5) | (dual_of(n) << 4) | mods_of(n, r), r

def b(code): return format(code, "08b")
def ham(a, c): return bin(a ^ c).count("1")

# restrict to the CHOSEN meta-theory vocabulary (top-256 by composite rank), then collapse
TOP = MAN[:256]
code_head, code_members = {}, {}
for row in TOP:
    code, r = encode(row["concept"])
    code_members.setdefault(code, []).append(row["concept"])
    if code not in code_head:
        code_head[code] = (row["concept"], r)

filled = sorted(code_head)
collisions = {c: v for c, v in code_members.items() if len(v) > 1}
n_collided = sum(len(v) for v in collisions.values())
print(f"[coverage] top-256 chosen concepts -> {len(filled)} distinct codes "
      f"({len(filled)/256*100:.0f}% of the 256-space)")
print(f"[collisions] {len(collisions)} codes hold >1 chosen concept; "
      f"{n_collided} concepts ({n_collided/256*100:.0f}%) collide = modifier vocab too thin there")
print(f"   worst: " + "; ".join(f"{b(c)}={len(v)}" for c, v in
      sorted(collisions.items(), key=lambda kv: -len(kv[1]))[:5]))

def b(code): return format(code, "08b")
def ham(a, c): return bin(a ^ c).count("1")

print("\n=== Fork B check 1: a concept and its DUAL differ by exactly 1 bit (the dual bit) ===")
for a, c in [("limit", "colimit"), ("product", "coproduct"), ("monad", "comonad"),
             ("equalizer", "coequalizer"), ("left adjoint", "right adjoint")]:
    ca, _ = encode(a); cc, _ = encode(c)
    print(f"  {a:14s} {b(ca)}   {c:14s} {b(cc)}   Hamming={ham(ca, cc)}")

print("\n=== Fork A+B check 2: a FAMILY collapses to head + modifier bits ===")
for fam in ["category", "monoidal category", "symmetric monoidal category",
            "closed monoidal category", "braided monoidal category"]:
    ce, r = encode(fam)
    print(f"  {fam:30s} {b(ce)}  role={ROLES[r]:9s} mods={[MODS[r][i] for i in range(4) if ce>>(3-i)&1]}")

print("\n=== sample of the filled manifest (top 24 codes by rank) ===")
shown = set()
for row in MAN:
    code, r = encode(row["concept"])
    if code in shown: continue
    shown.add(code)
    print(f"  {b(code)}  {ROLES[r]:9s}  {code_head[code][0]:30s}  (+{len(code_members[code])-1} collapsed)")
    if len(shown) >= 24: break

out = {b(c): {"role": ROLES[code_head[c][1]], "head": code_head[c][0],
              "members": code_members[c][:8]} for c in filled}
pathlib.Path("/tmp/iiching-sigil-schema.json").write_text(json.dumps(out, indent=1))
print(f"\ndraft schema -> /tmp/iiching-sigil-schema.json ({len(filled)} codes)")
