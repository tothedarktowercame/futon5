# Tensor Tokamak v3 Gate Compare (20 generations, 5 seeds)

## Seeds
`7 11 13 17 19`

## Commands
Baseline (pre-tune):

```bash
for s in 7 11 13 17 19; do
  bb -m scripts.tensor-tokamak \
    --seed "$s" --generations 20 --length 128 --with-phenotype \
    --out out/tokamak/compare-v3-gate-baseline/seed-"$s".edn \
    --report out/tokamak/compare-v3-gate-baseline/seed-"$s".md
done
```

Tuned gate:

```bash
for s in 7 11 13 17 19; do
  bb -m scripts.tensor-tokamak \
    --seed "$s" --generations 20 --length 128 --with-phenotype \
    --out out/tokamak/compare-v3-gate-tuned2/seed-"$s".edn \
    --report out/tokamak/compare-v3-gate-tuned2/seed-"$s".md
done
```

## Aggregate
- baseline: `{:runs 5, :attempts 20, :selected 7, :cells 31, :rew 8, :best-avg 0.5226276486688608, :final-avg 0.09586008700848493}`
- tuned2: `{:runs 5, :attempts 19, :selected 8, :cells 32, :rew 5, :best-avg 0.5226276486688608, :final-avg 0.09489687278321322}`

## Per-seed selected/final deltas
- seed 7: selected `0 -> 0`, final `0.106 -> 0.106`
- seed 11: selected `0 -> 0`, final `0.093 -> 0.093`
- seed 13: selected `2 -> 3`, final `0.088 -> 0.088`
- seed 17: selected `5 -> 4`, final `0.092 -> 0.089`
- seed 19: selected `0 -> 1`, final `0.100 -> 0.099`

## Notes
- Tuned gate adds bounded exploratory acceptance under trigger pressure.
- Exotype selection increased slightly and rewind count dropped materially.
- Final score average dipped slightly, indicating this gate is more exploratory than exploitative over short 20-generation runs.
