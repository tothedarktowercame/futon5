# Xenotype Portrait Report (2026-01-24)

This report documents the 20 xenotype-aware runs (generator + scorer) and the
resulting multi-page PDF. It is intended for agent review and reproduction.

## Summary

- 20 runs, each with a **generator wiring** (sigil-level generation) and a
  **scorer wiring** (run evaluation) drawn from Claude’s prototype libraries.
- Each run produced:
  - MMCA triptych (genotype/phenotype/exotype)
  - Mermaid generator wiring diagram
  - Mermaid scorer wiring diagram
  - One combined page image (triptych above wiring diagrams)
- All 20 pages are bundled into a single PDF:
  `resources/exotic-programming-notebook-iv.pdf`.

## Parameters (All Runs)

- Genotype length: 100
- Phenotype length: 100
- Generations: 20
- Seeds: 4243–4262 (seed = 4243 + run_index - 1)

## Tools / Runtime

- **Run execution:** `clojure -M -m xenotype-portrait-run` (not bb)
  - Babashka cannot load `commons-math3` classes required by
    `futon5.hexagram.lift`.
- **Mermaid rendering:**
  - `aa-exec -p chrome -- mmdc -i input.mmd -o output.png`
- **Image assembly:** ImageMagick (`convert`, `mogrify`, `montage`)

## Wiring Libraries Used

Generator prototypes (8):
- `resources/xenotype-wirings/*.edn`

Scorer prototypes (4):
- `resources/xenotype-scorer-wirings/*.edn`

Mermaid text is produced from the wiring diagram via a **simple** renderer
in `scripts/xenotype_portrait_run.clj` to avoid Mermaid parse errors caused by
rich labels (e.g., weight vectors in `([0.4 0.6])`).

## Wiring Inventory

Generator wirings (sorted order):
1. `:xenotype-001` — Creative + Péng
   `resources/xenotype-wirings/prototype-001-creative-peng.edn`
2. `:xenotype-038` — Receptive + Lǚ
   `resources/xenotype-wirings/prototype-038-receptive-lu.edn`
3. `:xenotype-071` — Difficulty + Jǐ
   `resources/xenotype-wirings/prototype-071-difficulty-ji.edn`
4. `:xenotype-104` — Youthful + Àn
   `resources/xenotype-wirings/prototype-104-youthful-an.edn`
5. `:xenotype-129` — Waiting + Péng
   `resources/xenotype-wirings/prototype-129-waiting-peng.edn`
6. `:xenotype-166` — Conflict + Lǚ
   `resources/xenotype-wirings/prototype-166-conflict-lu.edn`
7. `:xenotype-199` — Army + Jǐ
   `resources/xenotype-wirings/prototype-199-army-ji.edn`
8. `:xenotype-232` — Joy + Àn
   `resources/xenotype-wirings/prototype-232-joy-an.edn`

Scorer wirings (sorted order):
1. `:scorer-an` — Filament complexity
   `resources/xenotype-scorer-wirings/scorer-an-filament.edn`
2. `:scorer-ji` — Edge-of-chaos
   `resources/xenotype-scorer-wirings/scorer-ji-eoc.edn`
3. `:scorer-lu` — Stability
   `resources/xenotype-scorer-wirings/scorer-lu-stability.edn`
4. `:scorer-peng` — Diversity
   `resources/xenotype-scorer-wirings/scorer-peng-diversity.edn`

## Run Matrix (20 Runs)

Runs cycle generator (8) and scorer (4) in sorted order.

| Run | Seed | Generator | Scorer |
|-----|------|-----------|--------|
| 01 | 4243 | :xenotype-001 | :scorer-an |
| 02 | 4244 | :xenotype-038 | :scorer-ji |
| 03 | 4245 | :xenotype-071 | :scorer-lu |
| 04 | 4246 | :xenotype-104 | :scorer-peng |
| 05 | 4247 | :xenotype-129 | :scorer-an |
| 06 | 4248 | :xenotype-166 | :scorer-ji |
| 07 | 4249 | :xenotype-199 | :scorer-lu |
| 08 | 4250 | :xenotype-232 | :scorer-peng |
| 09 | 4251 | :xenotype-001 | :scorer-an |
| 10 | 4252 | :xenotype-038 | :scorer-ji |
| 11 | 4253 | :xenotype-071 | :scorer-lu |
| 12 | 4254 | :xenotype-104 | :scorer-peng |
| 13 | 4255 | :xenotype-129 | :scorer-an |
| 14 | 4256 | :xenotype-166 | :scorer-ji |
| 15 | 4257 | :xenotype-199 | :scorer-lu |
| 16 | 4258 | :xenotype-232 | :scorer-peng |
| 17 | 4259 | :xenotype-001 | :scorer-an |
| 18 | 4260 | :xenotype-038 | :scorer-ji |
| 19 | 4261 | :xenotype-071 | :scorer-lu |
| 20 | 4262 | :xenotype-104 | :scorer-peng |

## Outputs

- **PDF (20 pages):** `resources/exotic-programming-notebook-iv.pdf`
- Per-run artifacts:
  - `/tmp/futon5-xenotype-portrait-20/run-01` … `/run-20`
  - `triptych.ppm`, `triptych.png`
  - `xenotype-generator.mmd`, `xenotype-generator.png`
  - `xenotype-scorer.mmd`, `xenotype-scorer.png`
  - `xenotype-diagrams.png` (generator + scorer side-by-side)
  - `page.png` (triptych stacked above wiring diagrams)

## Sample Run EDN Files

For agent review, three sample runs are saved in-repo:
- `data/xenotype-portrait-samples/run-01.edn`
- `data/xenotype-portrait-samples/run-08.edn`
- `data/xenotype-portrait-samples/run-15.edn`

These correspond to runs:
- Run 01: :xenotype-001 + :scorer-an (seed 4243)
- Run 08: :xenotype-232 + :scorer-peng (seed 4250)
- Run 15: :xenotype-199 + :scorer-lu (seed 4257)

## Reproduction Command (Single Run)

Example (run 01 wiring pair):

```bash
clojure -Sdeps '{:paths ["src" "resources" "scripts"]}' -M -m xenotype-portrait-run \
  --out-dir /tmp/futon5-xenotype-portrait-20/run-01 \
  --length 100 \
  --phenotype-length 100 \
  --generations 20 \
  --seed 4243 \
  --gen-wiring-path resources/xenotype-wirings/prototype-001-creative-peng.edn \
  --score-wiring-path resources/xenotype-scorer-wirings/scorer-an-filament.edn
```

Then render diagrams and assemble a page:

```bash
convert /tmp/futon5-xenotype-portrait-20/run-01/triptych.ppm \
  /tmp/futon5-xenotype-portrait-20/run-01/triptych.png
mogrify -resize 250% /tmp/futon5-xenotype-portrait-20/run-01/triptych.png

aa-exec -p chrome -- mmdc \
  -i /tmp/futon5-xenotype-portrait-20/run-01/xenotype-generator.mmd \
  -o /tmp/futon5-xenotype-portrait-20/run-01/xenotype-generator.png

aa-exec -p chrome -- mmdc \
  -i /tmp/futon5-xenotype-portrait-20/run-01/xenotype-scorer.mmd \
  -o /tmp/futon5-xenotype-portrait-20/run-01/xenotype-scorer.png

mogrify -resize 250% /tmp/futon5-xenotype-portrait-20/run-01/xenotype-generator.png \
                   /tmp/futon5-xenotype-portrait-20/run-01/xenotype-scorer.png
montage /tmp/futon5-xenotype-portrait-20/run-01/xenotype-generator.png \
        /tmp/futon5-xenotype-portrait-20/run-01/xenotype-scorer.png \
        -tile 2x1 -geometry +20+20 /tmp/futon5-xenotype-portrait-20/run-01/xenotype-diagrams.png

convert /tmp/futon5-xenotype-portrait-20/run-01/triptych.png \
  /tmp/futon5-xenotype-portrait-20/run-01/xenotype-diagrams.png \
  -append /tmp/futon5-xenotype-portrait-20/run-01/page.png
```

## Notes for Agents

- **Generator + scorer wiring** are now explicit and documented; do not treat
  xenotype as evaluation-only.
- If Mermaid parsing fails, ensure the `.mmd` source uses the simple renderer
  (labels in brackets, no `([…])` syntax), or regenerate with the current
  `xenotype-portrait-run` script.
- If using babashka, note the classpath issue with `commons-math3`.
