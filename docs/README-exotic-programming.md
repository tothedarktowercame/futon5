# Exotic Programming Module (futon5)

This directory documents the exotic programming stack and how to run it.
The core loop evolves exotypes under optional xenotype scoring and ratchet memory.

## Entry Points

- `futon5.mmca.exoevolve` — exotype evolution loop with xenotype scoring + ratchet.
- `futon5.mmca.render-cli` — render MMCA runs to images.
- `futon5.arrow.mine` — post-run arrow discovery.

## Quick Runs

Baseline exotype evolution:

```sh
bb -cp src:resources -m futon5.mmca.exoevolve \
  --runs 50 \
  --length 20 \
  --generations 20 \
  --tier super \
  --seed 4242 \
  --update-every 10 \
  --log resources/exoevolve-baseline.log
```

Exotic (xenotype) scoring enabled:

```sh
bb -cp src:resources -m futon5.mmca.exoevolve \
  --runs 50 \
  --length 20 \
  --generations 20 \
  --tier super \
  --seed 4242 \
  --update-every 10 \
  --xeno-spec resources/exotic-xenotype-example.edn \
  --xeno-weight 0.25 \
  --log resources/exoevolve-exotic.log
```

## Render a Snapshot

```sh
bb -cp src:resources -m futon5.mmca.render-cli \
  --input resources/mission-17-refine-baseline.edn \
  --render-exotype \
  --out resources/mission-17-refine-baseline.ppm
```

Convert to PNG (ImageMagick):

```sh
convert resources/mission-17-refine-baseline.ppm resources/mission-17-refine-baseline.png
```

## Arrow Discovery (post-run)

```sh
bb -cp src:resources -m futon5.arrow.mine \
  --log resources/exoevolve-exotic.log \
  --contexts 5 \
  --k 2 \
  --tau 0.6 \
  --min-delta 0.05 \
  --out resources/arrow-pilot.edn
```

## Reference Docs

- `docs/exotic-programming-spec.md`
- `docs/exotic-programming-curriculum.md`
- `docs/mission-9.5-arrow-discovery.md`
- `docs/mission-9.6-arrow-atlas.md`
- `resources/exotic-programming-notebook-ii.org`
