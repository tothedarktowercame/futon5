# Exotic Xenotype Mode (Example)

This example shows how to run MMCA with a minimal "exotic" xenotype spec.
The exotic scorer does not yet evaluate real CT structure; it just checks that
the expected fields exist and logs placeholders. This is enough to see the
pipeline shape and confirm that exotic scoring is wired into xenotype runs.

## Files

- `resources/exotic-xenotype-example.edn` - minimal exotic xenotype spec

## Run

```
bb -cp src:resources -m futon5.mmca.exoevolve \
  --runs 5 \
  --length 20 \
  --generations 20 \
  --tier super \
  --xeno-spec resources/exotic-xenotype-example.edn \
  --xeno-weight 1.0
```

## What to look for

In the log entries, the xenotype block will include:

- `:score` - the exotic score (placeholder mean of vision/plan/adapt signals)
- `:components` - `:vision-clarity`, `:plan-fidelity`, `:adapt-coherence`
- `:exotic` - the embedded CT stub used for scoring

This is the minimal handshake for Mission 12. Mission 13+ will replace the
placeholders with real CT metrics and evidence-backed scoring.
