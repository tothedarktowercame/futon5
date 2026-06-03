#!/usr/bin/env python3
"""it-2: jraph link-prediction replication (toy graph) — the E-γ chop.

GAE-style: GCN encoder -> dot-product decoder -> negative sampling -> AUC/AP,
evaluated AGAINST heuristic baselines (common-neighbours, Adamic-Adar,
preferential-attachment). claude-6's review bar: **lift-over-heuristics**, not
absolute AUC — a GNN at 0.9 hasn't won if CN/AA are also 0.9. Replication only
(toy SBM graph); the E-γ step is swapping this toy graph for git_dep_graph_asof.
"""
import numpy as np, jax, jax.numpy as jnp, jraph, haiku as hk, optax
from sklearn.metrics import roc_auc_score, average_precision_score

rng = np.random.default_rng(0)

# ---- 1) toy graph: stochastic block model, 3 communities x 20 nodes ----
N, K = 60, 3
comm = np.repeat(np.arange(K), N // K)
P = np.where(comm[:, None] == comm[None, :], 0.30, 0.02)
A = np.triu((rng.random((N, N)) < P).astype(int), 1); A = A + A.T
pos = np.argwhere(np.triu(A, 1)); rng.shuffle(pos)
nt = max(1, int(0.15 * len(pos)))
test_pos, train_pos = pos[:nt], pos[nt:]
neg = np.argwhere(np.triu(1 - A - np.eye(N, dtype=int), 1)); rng.shuffle(neg)
test_neg, train_neg = neg[:nt], neg[nt:nt + len(train_pos)]

# train adjacency (train edges only) — for the encoder AND the heuristics
At = np.zeros((N, N), int)
for u, v in train_pos: At[u, v] = At[v, u] = 1
deg = At.sum(1)

# ---- 2) jraph graph from train edges (both directions) ----
def make_graph(e):
    s = np.r_[e[:, 0], e[:, 1]]; r = np.r_[e[:, 1], e[:, 0]]
    return jraph.GraphsTuple(nodes=jnp.eye(N, dtype=jnp.float32), edges=None,
                             senders=jnp.array(s), receivers=jnp.array(r),
                             n_node=jnp.array([N]), n_edge=jnp.array([len(s)]), globals=None)
G = make_graph(train_pos)

# ---- 3) GCN encoder (haiku) + dot-product decoder ----
def net_fn(graph):
    gc = lambda d, act: jraph.GraphConvolution(
        update_node_fn=lambda n: act(hk.Linear(d)(n)),
        add_self_edges=True, symmetric_normalization=True)
    g = gc(32, jax.nn.relu)(graph)
    g = gc(16, lambda x: x)(g)
    return g.nodes
net = hk.without_apply_rng(hk.transform(net_fn))

def edge_scores(emb, pairs):
    return jnp.sum(emb[pairs[:, 0]] * emb[pairs[:, 1]], -1)

def loss_fn(params, pos, neg):
    emb = net.apply(params, G)
    logits = jnp.concatenate([edge_scores(emb, pos), edge_scores(emb, neg)])
    labels = jnp.concatenate([jnp.ones(len(pos)), jnp.zeros(len(neg))])
    return optax.sigmoid_binary_cross_entropy(logits, labels).mean()

params = net.init(jax.random.PRNGKey(0), G)
opt = optax.adam(1e-2); opt_state = opt.init(params)
tp, tn = jnp.array(train_pos), jnp.array(train_neg)

@jax.jit
def step(params, opt_state):
    l, grad = jax.value_and_grad(loss_fn)(params, tp, tn)
    upd, opt_state = opt.update(grad, opt_state)
    return optax.apply_updates(params, upd), opt_state, l

for i in range(300):
    params, opt_state, l = step(params, opt_state)

# ---- 4) eval: GCN vs heuristics on held-out test edges ----
emb = np.array(net.apply(params, G))
y = np.r_[np.ones(nt), np.zeros(nt)]
allpairs = np.r_[test_pos, test_neg]

def heur(name):
    out = []
    for u, v in allpairs:
        cn = np.where((At[u] & At[v]))[0]
        if name == "common-neighbours": out.append(len(cn))
        elif name == "adamic-adar": out.append(sum(1/np.log(deg[w]) for w in cn if deg[w] > 1))
        elif name == "pref-attach": out.append(deg[u] * deg[v])
    return np.array(out, float)

gcn = np.sum(emb[allpairs[:, 0]] * emb[allpairs[:, 1]], -1)
rng_sig = rng.random(len(allpairs))  # null-signal sanity check

print(f"toy SBM: N={N} K={K} | train_pos={len(train_pos)} test_pos={nt} | final loss {float(l):.3f}\n")
print(f"{'signal':22} {'AUC':>6} {'AP':>6}")
for name, s in [("GCN (it-2)", gcn), ("common-neighbours", heur("common-neighbours")),
                ("adamic-adar", heur("adamic-adar")), ("pref-attach", heur("pref-attach")),
                ("null (random)", rng_sig)]:
    print(f"{name:22} {roc_auc_score(y, s):6.3f} {average_precision_score(y, s):6.3f}")
best_heur = max(roc_auc_score(y, heur(n)) for n in ["common-neighbours","adamic-adar","pref-attach"])
print(f"\nLIFT (GCN AUC - best-heuristic AUC) = {roc_auc_score(y, gcn) - best_heur:+.3f}")
print("(claude-6's bar: positive lift = the GNN earns its keep; ~0 = heuristics already suffice.)")
