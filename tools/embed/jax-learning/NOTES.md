# JAX/jraph paired learning loop — replication only (no new research yet)

**Goal:** build JAX/graph-NN chops by replicating known-good demonstrators
(`E-jax-demonstrators.md`: jraph link-prediction is the E-γ scaffold), before
applying anything to our substrate. Replication, not application.

**Env:** `futon5/.venv-tpg` — jax 0.9.0.1, jaxlib 0.9.0.1, **jraph 0.0.6**, optax,
dm-haiku — all installed and **compatible under jax 0.9** (the real risk, cleared).

## Iteration log

- **it-0 — core forward pass.** Built a `jraph.GraphsTuple` (4 nodes/4 edges) and ran a
  `jraph.GraphNetwork` (sum-aggregate message passing) forward. OK. *Learned:* the
  `GraphsTuple` fields (`nodes, edges, senders, receivers, n_node, n_edge, globals`) and
  that the GNN is `update_{edge,node,global}_fn` composed over scatter/gather.
- **it-1 — canonical example replicated.** Ran jraph's bundled `zacharys_karate_club.py`
  (haiku GCN + optax) end-to-end → **0.97 accuracy** by step 29. *Learned:* the full
  known-good loop — `hk.transform` params, `GraphConvolution`, `optax.adam` update,
  semi-supervised node mask, the `jax.value_and_grad` train step.

## Next iterations (planned)

- **it-2 — link prediction (the E-γ chop).** GCN encoder → dot-product edge decoder →
  negative sampling → eval AUC/AP. **(claude-6 review, folded in):**
  - Build the GCN as a **`forecast_harness` *signal*** — NOT a standalone loop — so it
    transfers to E-γ directly. Use the E-α baseline set + **Adamic-Adar, recency, churn**.
  - **Leak-free as-of-T split** + a **null-signal sanity check** (random signal ≈ floor).
  - **Pre-register the real bar: heuristics are competitive** — CN / Adamic-Adar also hit
    AUC ~0.9 on link-pred, so the bar is **lift-over-heuristics, not absolute AUC**. (The
    chop-lesson: a GNN that "gets 0.9" usually hasn't beaten the cheap baselines.)
  - Toy graph still (replication); the candidate-coverage wall (E-α) is **structurally
    avoided** once candidates live in a dep/co-touch graph.
- **it-3 — (APPLICATION, parked):** it-2 → E-γ is then just **swap the toy graph for
  `git_dep_graph_asof`** (dep/co-touch candidates reach code-locus targets — the E-α wall
  gone by construction). That's research, gated on Joe; not yet.

## Discipline
Replicate known-good demonstrators; build mechanics; no new research; bank the chops.
The jraph link-pred recipe (it-2) is what plugs into claude-6's `forecast_harness` later.
