#!/usr/bin/env python3
"""o4b_conditioning_check.py — O4(b): is dS/dA finite + well-scaled across the size
extremes, with degree-aware :conditioning-scale = 1/sqrt(max(1, total-A-degree))?

SPARSE feeds-A only (no dense 7534x7534). The multi-target differentiable choice is
file-dependency (ns -> required ns); softmax over each source's targets; band from the
O4(c) BGE embedding cosine. Reports per-source grad-norm distribution WITH vs WITHOUT
:conditioning-scale, and the real extreme (highest-out-degree ns vs low). claude-6 (E2).

  futon5/.venv-tpg/bin/python o4b_conditioning_check.py
"""
import json, pathlib
import numpy as np

D = pathlib.Path("/home/joe/code/futon5/data/code-embeddings")
C, W = 0.60, 0.12


def main():
    import jax, jax.numpy as jnp
    from jax import grad
    from jax.ops import segment_sum, segment_max
    jax.config.update("jax_enable_x64", True)

    emb = np.load(D / "code-emb.npy")
    rel = json.loads((D / "relations.json").read_text())
    ids = json.loads((D / "code-emb-ids.json").read_text())
    N = rel["n"]
    deg = np.asarray(rel["degree"], dtype=np.float64)
    fdep = np.asarray(rel["fdep_edges"], dtype=np.int64)        # [E,2] (src,dst)
    E = len(fdep)
    src, dst = fdep[:, 0], fdep[:, 1]

    # band satisfaction per edge from the O4(c) embedding (constant)
    cos = np.sum(emb[src] * emb[dst], axis=1).astype(np.float64)
    band = np.exp(-((cos - C) / W) ** 2)
    condscale = 1.0 / np.sqrt(np.maximum(1.0, deg))             # O4(b) spec, degree-aware

    src_j = jnp.asarray(src); band_j = jnp.asarray(band)
    cs_src = jnp.asarray(condscale[src])
    outdeg = np.asarray(segment_sum(jnp.ones(E), src_j, num_segments=N))

    def seg_softmax(a):
        m = segment_max(a, src_j, num_segments=N)
        ea = jnp.exp(a - m[src_j])
        Z = segment_sum(ea, src_j, num_segments=N)
        return ea / Z[src_j]

    def loss_with(a):    # conditioning-scaled
        return -jnp.sum(cs_src * seg_softmax(a) * band_j)
    def loss_without(a):
        return -jnp.sum(seg_softmax(a) * band_j)

    a0 = jnp.zeros(E)
    def per_src_gradnorm(lossfn):
        g = np.asarray(grad(lossfn)(a0))
        gn2 = np.asarray(segment_sum(jnp.asarray(g * g), src_j, num_segments=N))
        return np.sqrt(gn2)                                    # [N], per source node

    gn_with = per_src_gradnorm(loss_with)
    gn_without = per_src_gradnorm(loss_without)

    active = np.where(outdeg >= 2)[0]                          # only multi-target sources grad
    def stats(gn):
        v = gn[active]
        v = v[v > 0]
        return (float(v.min()), float(np.median(v)), float(v.max()),
                float(v.max() / np.median(v)), bool(np.all(np.isfinite(gn))))

    mn_w, md_w, mx_w, ratio_w, fin_w = stats(gn_with)
    mn_o, md_o, mx_o, ratio_o, fin_o = stats(gn_without)
    print(f"[O4b] {E} file-dep edges; {len(active)} multi-target source ns (grad-bearing)")
    print(f"[WITHOUT condscale] grad-norm min/med/max = {mn_o:.2e}/{md_o:.2e}/{mx_o:.2e}  "
          f"max/med={ratio_o:.2f}  finite={fin_o}")
    print(f"[WITH    condscale] grad-norm min/med/max = {mn_w:.2e}/{md_w:.2e}/{mx_w:.2e}  "
          f"max/med={ratio_w:.2f}  finite={fin_w}")

    # the real size extreme: highest-out-degree source ns vs a low one
    hi = active[np.argmax(outdeg[active])]
    lo = active[np.argmin(outdeg[active])]
    for tag, i in [("HIGH-degree ns", hi), ("low-degree ns", lo)]:
        print(f"[{tag}] {ids[i]['id']}  out-deg={int(outdeg[i])} total-A-deg={int(deg[i])} "
              f"condscale={condscale[i]:.3f}  grad-norm with={gn_with[i]:.2e} without={gn_without[i]:.2e}")

    passed = fin_w and ratio_w < 10 and ratio_w <= ratio_o + 1e-9
    print(f"\n[VERDICT O4(b)] dS/dA finite={fin_w}; WITH-condscale max/med={ratio_w:.2f} "
          f"(vs {ratio_o:.2f} without) => {'PASS' if passed else 'REVIEW'} — degree-aware "
          f":conditioning-scale keeps the gradient well-scaled across the real out-degree extremes "
          f"(no high-degree node swamps).")


if __name__ == "__main__":
    main()
