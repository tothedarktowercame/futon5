#!/usr/bin/env python3
"""build_code_embedding_cache.py — O4(c) code-text embedding cache (E2 RUN/DELIVER).

Streams scope-window JSON (extract_clj_scopes.bb) -> CHUNKED + RESUMABLE frozen-BGE
encode -> durable cache. Fixed-observation layer over existing :symbol/:namespace
identities (NOT R-GCN; does not reopen O1). claude-6 (E2). BGE-large, chunked+resumable
per claude-3 ruling.

  futon6/.venv/bin/python build_code_embedding_cache.py <scopes.json> <out-dir> [batch]

Resumable: each chunk -> chunks/chunk_<i>.npy; rerun skips existing chunks. At the end,
concatenates -> code-emb.npy + code-emb-ids.json + non-degeneracy on the full set.
Self-bells claude-3 at first-checkpoint and completion (urllib; localhost:7070).

:total-A-degree / :conditioning-scale come from the relation-extraction step (feeds-A?
graph), deferred — this stage is the embedding layer only.
"""
import json, sys, os, time, pathlib, resource, urllib.request
import numpy as np

os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
MODEL, DIM = "BAAI/bge-large-en-v1.5", 1024


def bell(prompt):
    try:
        data = json.dumps({"agent-id": "claude-3", "prompt": prompt}).encode()
        req = urllib.request.Request("http://localhost:7070/api/alpha/bell", data=data,
                                     headers={"Content-Type": "application/json"})
        urllib.request.urlopen(req, timeout=10).read()
    except Exception as e:
        print(f"[bell-failed] {e}")


def main():
    scopes_path = pathlib.Path(sys.argv[1])
    out = pathlib.Path(sys.argv[2]); chunks = out / "chunks"
    chunks.mkdir(parents=True, exist_ok=True)
    batch = int(sys.argv[3]) if len(sys.argv) > 3 else 32

    scopes = json.loads(scopes_path.read_text())
    n = len(scopes)
    n_chunks = (n + batch - 1) // batch
    print(f"[in] {n} scope windows, batch={batch}, {n_chunks} chunks, out={out}")

    from sentence_transformers import SentenceTransformer
    model = SentenceTransformer(MODEL)
    print(f"[model] {MODEL} ready")

    t0 = time.time()
    belled_first = False
    done_existing = sum(1 for i in range(n_chunks) if (chunks / f"chunk_{i:05d}.npy").exists())
    if done_existing:
        print(f"[resume] {done_existing}/{n_chunks} chunks already present, skipping them")

    for ci in range(n_chunks):
        cf = chunks / f"chunk_{ci:05d}.npy"
        if cf.exists():
            continue
        lo = ci * batch
        texts = [s["text"] for s in scopes[lo:lo + batch]]
        try:
            v = model.encode(texts, normalize_embeddings=True, show_progress_bar=False)
        except Exception:
            v = model.encode(texts, normalize_embeddings=True, show_progress_bar=False,
                             batch_size=4)
        np.save(cf, np.asarray(v, dtype=np.float32))
        if not belled_first:
            belled_first = True
            rss = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / (1024**2)
            bell(f"[O4(c) FIRST-CHECKPOINT — claude-6] cache WRITING + on-track. "
                 f"out={out}; chunk 0 of {n_chunks} written ({n} symbols total, batch {batch}); "
                 f"peak RSS so far {rss:.2f}GB; ETA ~{(time.time()-t0)/max(1,ci+1)*n_chunks/60:.0f}min. "
                 f"Resumable (chunks/chunk_*.npy). Not stuck. Will bell at completion.")

    # concatenate in order -> durable cache
    vecs = np.concatenate([np.load(chunks / f"chunk_{ci:05d}.npy") for ci in range(n_chunks)])
    np.save(out / "code-emb.npy", vecs)
    (out / "code-emb-ids.json").write_text(
        json.dumps([{"id": s["id"], "ns": s["ns"], "name": s["name"],
                     "kind": s.get("kind", "")} for s in scopes]))
    # coverage metadata — explicit, so 'code embedding' is never read as all-languages
    n_ns = sum(1 for s in scopes if s.get("kind") == "ns")
    (out / "metadata.json").write_text(json.dumps({
        "version": "v1",
        "model": MODEL, "dim": DIM,
        "languages": ["clojure"],
        "languages_pending": ["elisp (futon4 .el)", "python (futon6 .py)"],
        "grains": ["symbol", "namespace"],
        "n_total": n, "n_namespace": n_ns, "n_symbol": n - n_ns,
        "origin_excluded": True,
        "canonical_trees_only": "excludes .venv/.state/target/worktrees/~backups; futon3 origin excluded (O1 canonical-trees rule)",
        "degree_pending": ":total-A-degree/:conditioning-scale added by the relation-extraction (feeds-A?) step",
        "note": "O4(c) fixed-observation layer over existing :symbol/:namespace identities; NOT R-GCN; does not reopen O1. v1 = Clojure-only; Elisp/Python follow as separate per-language extractors.",
    }, indent=2))

    sims = vecs @ vecs.T
    iu = np.triu_indices(n, k=1)
    off = sims[iu]
    med, p99 = float(np.percentile(off, 50)), float(np.percentile(off, 99))
    frac = float((off > 0.95).mean())
    rss = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / (1024**2)
    dt = time.time() - t0
    msg = (f"cache COMPLETE: {out}/code-emb.npy shape={vecs.shape}; {n} symbols; "
           f"non-degeneracy cos median={med:.3f} p99={p99:.3f} frac>0.95={frac:.4f} "
           f"({'OK' if frac < 0.2 else 'DEGENERATE'}); peak RSS {rss:.2f}GB; wall {dt/60:.1f}min.")
    print(f"[done] {msg}")
    bell(f"[O4(c) COMPLETE — claude-6] {msg} :total-A-degree/:conditioning-scale pending the "
         f"relation-extraction step (feeds-A? graph).")


if __name__ == "__main__":
    main()
