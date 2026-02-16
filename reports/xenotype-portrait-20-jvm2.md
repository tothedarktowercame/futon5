# Xenotype portrait batch (JVM, fixed seed)

Date: 2026-01-25

## Purpose
Generate 20 comparable CA runs using a single seed and 20 different wiring diagrams (generator + evaluator), then render one CA triptych + wiring diagram per page in a PDF.

## Configuration
- Engine: JVM (Clojure)
- Seed: 4242
- Grid: 100 x 100
- Generations: 100
- Output root: `/tmp/futon5-xenotype-portrait-20-jvm2/`
- PDF: `out/xenotype-portrait-20-jvm2/xenotype-portrait-20-jvm2.pdf`

## Wiring set (20)
1. `resources/xenotype-wirings/prototype-1.edn`
2. `resources/xenotype-wirings/prototype-2.edn`
3. `resources/xenotype-wirings/prototype-3.edn`
4. `resources/xenotype-wirings/prototype-4.edn`
5. `resources/xenotype-wirings/prototype-5.edn`
6. `resources/xenotype-wirings/prototype-6.edn`
7. `resources/xenotype-wirings/prototype-7.edn`
8. `resources/xenotype-wirings/prototype-8.edn`
9. `data/xenotype-legacy-wirings/legacy-baseline.edn`
10. `data/xenotype-legacy-wirings/legacy-exotype-gong-super.edn`
11. `data/xenotype-legacy-wirings/legacy-exotype-xiong-super.edn`
12. `data/xenotype-legacy-wirings/new-local-physics.edn`
13. `data/xenotype-legacy-wirings/legacy-exotype-var-01.edn`
14. `data/xenotype-legacy-wirings/legacy-exotype-var-02.edn`
15. `data/xenotype-legacy-wirings/legacy-exotype-var-03.edn`
16. `data/xenotype-legacy-wirings/legacy-exotype-var-04.edn`
17. `data/xenotype-legacy-wirings/legacy-exotype-var-05.edn`
18. `data/xenotype-legacy-wirings/legacy-exotype-var-06.edn`
19. `data/xenotype-legacy-wirings/legacy-exotype-var-07.edn`
20. `data/xenotype-legacy-wirings/legacy-exotype-var-08.edn`

## Outputs per run
Each `run-XX/` contains:
- `run.edn` (full run config and results)
- `triptych.ppm` and `triptych.png`
- `xenotype-generator.mmd` and `xenotype-generator.png`
- `xenotype-scorer.mmd` (no PNG rendered)

## PDF assembly
Each page is a horizontal append of `triptych.png` + `xenotype-generator.png` from the corresponding run.

## Reproduction (outline)
For each wiring in the list above:
1) Run the JVM portrait command (script loaded via `load-file`):

```
clojure -M -e "(do (load-file \"scripts/xenotype_portrait_run.clj\")
  (futon5.scripts.xenotype-portrait-run/run {:seed 4242
    :length 100 :generations 100
    :wiring \"<WIRING_FILE>\"
    :out-dir \"/tmp/futon5-xenotype-portrait-20-jvm2/run-XX\"}))"
```

2) Convert PPM to PNG and render wiring diagrams (mermaid):

```
convert triptych.ppm triptych.png

aa-exec -p chrome -- mmdc -i xenotype-generator.mmd -o xenotype-generator.png
```

3) Assemble the PDF:

```
convert out/xenotype-portrait-20-jvm2/pages/page-*.png out/xenotype-portrait-20-jvm2/xenotype-portrait-20-jvm2.pdf
```

## Notes for agents
- This batch fixes the seed across all wirings to make regime comparisons easier.
- Output directories are in `/tmp` for speed; if permanence is needed, copy into `out/`.
- The evaluator wiring diagram is present as `.mmd` but not rendered; if needed, run `mmdc` for `xenotype-scorer.mmd`.
