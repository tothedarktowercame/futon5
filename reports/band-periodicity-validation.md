# Band Periodicity Validation (Phenotype‑Only CA)

Date: 2026-01-25

## Goal
Validate the band periodicity detector using simple phenotype‑only CAs with known temporal cycles.

## Method
Used `scripts/phenotype_ca_periodicity_smoke.clj` to generate elementary CA histories and analyze periodicity via `futon5.mmca.band-analysis`.

## Results
### Rule 51 (NOT of center) — period 2
Command:
```
bb -cp src:resources:scripts scripts/phenotype_ca_periodicity_smoke.clj \
  --rule 51 --length 16 --generations 40 --init random --seed 4242
```
Output (first rows):
```
0100010100110011
1011101011001100
0100010100110011
1011101011001100
...
```
Detected:
- Row periodicity: YES (period=2, strength=1.00)

### Rule 170 (shift) — period = width
Command:
```
bb -cp src:resources:scripts scripts/phenotype_ca_periodicity_smoke.clj \
  --rule 170 --length 12 --generations 40 --init random --seed 4242
```
Detected:
- Row periodicity: YES (period=12, strength=1.00)

### Rule 15 (baseline test)
Command:
```
bb -cp src:resources:scripts scripts/phenotype_ca_periodicity_smoke.clj \
  --rule 15 --length 16 --generations 40 --init random --seed 4242
```
Detected:
- Row periodicity: no

## Notes
- Periodicity detection is for **periods ≥ 2** (period‑1 is a frozen row, already covered by freeze metrics).
- Requires history length ≥ 2 × max‑period (default max‑period=20).

