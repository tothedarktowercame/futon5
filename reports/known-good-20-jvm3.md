# Known-good runset (20) — JVM/MMCA + legacy wiring

Date: 2026-01-25

## Purpose
Recreate the user-specified “known good” examples as a 20-run set, using JVM/MMCA for the CA runs while attaching legacy generator wiring diagrams for comparability. Produce a PDF with one CA triptych + wiring diagram per page and a structured report for agent review.

## Outputs
- Runs (EDN + images): `/tmp/futon5-known-good-20-jvm3/`
- PDF (one page per run): `out/known-good-20-jvm3/known-good-20-jvm3.pdf`
- Page images: `out/known-good-20-jvm3/pages/page-01.png` … `page-20.png`

## Run order (matches config order)
1. Mission 17a refine baseline (seed 4242, exact history)
2. Mission 17a refine exotic (sigil 工, seed 4242, exact config)
3. Mission 17a p×t sweep (u=0.60 m=0.80) baseline (seed 1281562706)
4. Mission 17a p×t sweep (u=0.60 m=0.80) baseline (seed 71312373)
5. Mission 17a compare 01 (seed 238310129) exotic
6. Mission 17a compare 02 (seed 1281562706) baseline
7. Mission 17a compare 02 (seed 1281562706) exotic
8. Mission 17a compare 09 (seed 352362012) baseline
9. Mission 17a compare 09 (seed 352362012) exotic
10. Mission 17a compare 10 (seed 201709760) baseline
11. Mission 17a compare 16 (seed 1564354771) baseline
12. Mission 17a compare 20 (seed 955468957) baseline
13. Mission 17a compare 20 (seed 955468957) exotic
14. Mission 7A top genotype #2 (score 43.10)
15. Mission 7B top exotype #1 nomination run
16. Mission 7B top exotype #1 Lock-0 (kernel only, fresh rng)
17. Mission 7B top exotype #1 Lock-1 (kernel + rng seed)
18. Mission 7B top exotype #1 Lock-2 (kernel + context schedule)
19. Mission 7B top exotype #1 locked kernel rerun
20. Mission 7B top exotype #2 randomized context (context-only baseline)

## Configuration sources
- Run list: `data/known-good-runset-20.edn`
- Mission 17a refine exact genotype/phenotype: `resources/mission-17a-refine-baseline.edn`, `resources/mission-17a-refine-exotic.edn`
- Mission 7B lock-mode semantics: `resources/exotic-programming-notebook.org` (lock-mode helper)
- Mission 7B locked kernel spec: `resources/figures/mission-07-b-top1-locked.edn`
- Legacy wiring diagrams:
  - `data/xenotype-legacy-wirings/legacy-baseline.edn`
  - `data/xenotype-legacy-wirings/legacy-exotype-gong-super.edn`
  - `data/xenotype-legacy-wirings/legacy-exotype-xiong-super-mission07.edn`
  - `data/xenotype-legacy-wirings/legacy-locked-kernel-mission07.edn`

## Key parameters
- Mission 17a exotic exotype (sigil 工, super):
  - `:rotation 0, :match-threshold 0.4444444444444444, :invert-on-phenotype? false, :update-prob 0.5, :mix-mode :rotate-left, :mix-shift 0`
- Mission 7B exotype (sigil 兄, super):
  - `:rotation 3, :match-threshold 0.5555555555555556, :invert-on-phenotype? true, :update-prob 0.25, :mix-mode :xor-neighbor, :mix-shift 0`
- Mission 7B lock-modes:
  - `:nominate` uses exotype-mode `:nominate` with captured contexts
  - `:lock0` uses seed+1
  - `:lock1` uses seed
  - `:lock2` replays captured contexts via `:exotype-contexts`
- Mission 7B context-only baseline:
  - `:exotype-mode :nominate, :exotype-context-mode :random` (sigil 兄, super)

## Known limitations / approximations
- **Mission 7A top genotype #2**: the original genotype/phenotype came from `/tmp/mission-07-a.edn` (not in repo). This run uses the recorded seed (1701878710) with a seeded random start for length 50. It is a best-effort reconstruction, not a guaranteed match to the evolved genotype.
- **Mermaid labels**: parameter blocks were removed from node labels to avoid Mermaid parse failures; wiring diagrams now show component topology only. Parameter detail remains in `data/known-good-runset-20.edn` and wiring EDNs.

## Reproduction
1) Generate runs (JVM/MMCA):
```
clojure -M -e "(do (load-file \"scripts/xenotype_regime_batch.clj\")
  (xenotype-regime-batch/-main \"--config\" \"data/known-good-runset-20.edn\"
                             \"--out-dir\" \"/tmp/futon5-known-good-20-jvm3\"))"
```

2) Render triptychs + wiring diagrams:
```
bb -cp src:resources:data scripts/xenotype_harness_render.clj \
  --runs-dir /tmp/futon5-known-good-20-jvm3 \
  --out-dir /tmp/futon5-known-good-20-jvm3
```

3) Ensure any missing wiring PNGs are rendered:
```
for mmd in /tmp/futon5-known-good-20-jvm3/*.wiring.mmd; do
  png="${mmd%.mmd}.png"
  [ -f "$png" ] || aa-exec -p chrome -- mmdc -i "$mmd" -o "$png"
done
```

4) Assemble PDF (ordered by config):
```
clojure -M -e "(do (require '[clojure.edn :as edn] '[clojure.string :as str])
  (defn sanitize-label [s] (-> s (str/replace #\"\\s+\" \"-\")
                                  (str/replace #\"[^a-zA-Z0-9._-]\" \"\")))
  (let [cfg (edn/read-string (slurp \"data/known-good-runset-20.edn\"))]
    (doseq [m (:models cfg)]
      (let [seed (:seed m)
            label (sanitize-label (str (:label m) \"-seed-\" seed))]
        (println label)))))" > /tmp/known-good-labels.txt

mkdir -p out/known-good-20-jvm3/pages
rm -f out/known-good-20-jvm3/pages/page-*.png

i=1
while IFS= read -r label; do
  triptych="/tmp/futon5-known-good-20-jvm3/${label}.png"
  wiring="/tmp/futon5-known-good-20-jvm3/${label}.wiring.png"
  out="out/known-good-20-jvm3/pages/page-$(printf '%02d' $i).png"
  convert "$triptych" "$wiring" +append "$out"
  i=$((i+1))
done < /tmp/known-good-labels.txt

convert out/known-good-20-jvm3/pages/page-*.png out/known-good-20-jvm3/known-good-20-jvm3.pdf
```

## Notes for reviewers
- This runset intentionally mirrors the user list and expands the “lock notes” entry to cover lock-0/1/2 and adds the locked-kernel rerun to reach 20 total.
- The generator wiring files are “legacy kernel” wrappers for comparability; CA evolution is executed by JVM/MMCA.
- If exact Mission 7A top genotype strings are recovered later, rerun item 14 with explicit `:genotype` and `:phenotype` in `data/known-good-runset-20.edn`.
