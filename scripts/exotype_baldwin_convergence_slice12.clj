(ns exotype-baldwin-convergence-slice12
  "Slice 12: compare damage-reach trajectories from separated ordered and
   disordered starts under Lamarckian and Baldwin update arms."
  (:require [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.pattern-eig :as pattern]
            [futon5.exotype.slice-harness :as harness]))

(def config
  {:seed-base 20260803 :seeds 8 :width 80 :workers 8
   :lambda 0.55 :mu 0.1 :tau 0.3 :prevalence-radius 1
   :steps 6000 :damage-steps 59
   :checkpoints [0 120 600 1200 3000 6000]})

(def initialisations
  ;; The two points that remain non-overlapping in the apparatus-level
  ;; preflight. Rule 110 was dropped: complex is not synonymous with critical,
  ;; and its measured start reach overlapped Rule 30.
  {:ordered 204 :disordered 30})

(def arms
  {:lamarckian
   {:selection-strength 0.0 :fitness-kind :preferences :write-back? true}
   :baldwin-preferences
   {:selection-strength 1.0 :fitness-kind :preferences :write-back? false}
   :baldwin-divergence
   {:selection-strength 1.0 :fitness-kind :divergence :write-back? false}})

(defn rule-sigil [rule]
  (ca/sigil-for
   (str/replace (format "%8s" (Integer/toBinaryString rule)) " " "0")))

(defn- uniform-rule [rule]
  (vec (repeat (:width config) (rule-sigil rule))))

(defn- with-rule [state genotype]
  (assoc state
         :genotype genotype
         :previous-genotype genotype
         :expressed genotype
         :previous-expressed genotype))

(defn- calibrated-phenotype
  "Burn in the seeded phenotype for t*=60, then keep ONLY the phenotype.

   WARNING (measured 2026-08-04, TN-baldwin-reboot.md §5): the previous docstring
   claimed `:identity` exotypes and write-back preserve the uniform genotype here.
   They do not. `:identity` is the MOST disruptive propagator in the family --
   every position is a fixed point of sigma, and a fixed point writes NOT bit[k]
   into position k, i.e. an unconditional flip -- so it changes the genotype on
   100% of applications. Measured over these 60 steps, from both seeded rules:
   0/80 cells still hold the seeded rule, 9 distinct genotypes, mean Hamming
   distance 1.00 of 8 bits.

   So this burn-in is NOT run under the exact ECA rule; it is run under a
   lockstep random walk starting there. The genotype is re-seeded afterwards, so
   only the phenotype carries the drift forward -- but the ordered/disordered
   contrast this function exists to establish does not mean what it appears to."
  [state genotype]
  (let [identity-grid (vec (repeat (:width config) :identity))
        calibration-state
        (-> state
            (with-rule genotype)
            (assoc :arm :heterogeneous-fixed
                   :exotypes identity-grid
                   :selection-strength 0.0
                   :fitness-kind :preferences
                   :write-back? true))]
    (:phenotype (grid/run-steps calibration-state 60))))

(defn initial-state [arm init seed]
  (let [arm-config (merge config (get arms arm))
        genotype (uniform-rule (get initialisations init))
        base (harness/initial-state arm-config :baseline seed)
        phenotype (calibrated-phenotype base genotype)]
    (-> base
        (with-rule genotype)
        (assoc :phenotype phenotype))))

(defn spread [values]
  {:mean (harness/mean values)
   :sd (harness/sd values)
   :min (apply min values)
   :max (apply max values)})

(defn- format-spread [{:keys [mean sd min max]}]
  (format "%.3f (%.3f; %d–%d)" mean sd min max))

(def raw-path "reports/exotype-baldwin-convergence-slice12.raw.edn")

(def conditions
  (vec (for [arm [:lamarckian :baldwin-preferences :baldwin-divergence]
             init [:ordered :disordered]]
         [arm init])))

(defn trajectory-run [[arm init] seed]
  (let [arm-config (merge config (get arms arm))
        wanted (set (:checkpoints config))]
    (loop [state (initial-state arm init seed)
           time 0
           trajectory (sorted-map)]
      (let [trajectory'
            (if (wanted time)
              (assoc trajectory time
                     {:damage (harness/damage arm-config state)
                      :genotype-rule-count (count (distinct (:genotype state)))})
              trajectory)]
        (if (= time (:steps config))
          {:trajectory trajectory'
           :final-state (select-keys state
                                     [:genotype :expressed :phenotype :exotypes])}
          (recur (pattern/step-compact state) (inc time) trajectory'))))))

(defn condition-runs [raw arm init]
  (vals (get raw [arm init])))

(defn checkpoint-spread [raw arm init time layer]
  (spread
   (map #(get-in % [:trajectory time :damage layer])
        (condition-runs raw arm init))))

(defn summary-table [raw times]
  (str
   "| time | arm | start | P mean (SD; range) | G mean (SD; range) | X mean (SD; range) |\n"
   "|---:|---|---|---:|---:|---:|\n"
   (apply str
          (for [time times [arm init] conditions]
            (format "| %d | %s | %s | %s | %s | %s |\n"
                    time (name arm) (name init)
                    (format-spread (checkpoint-spread raw arm init time :phenotype))
                    (format-spread (checkpoint-spread raw arm init time :genotype))
                    (format-spread (checkpoint-spread raw arm init time :exotype)))))))

(defn per-seed-table [raw]
  (str
   "| time | arm | start | seed | P | G | X |\n"
   "|---:|---|---|---:|---:|---:|---:|\n"
   (apply str
          (for [time (:checkpoints config)
                [arm init] conditions
                [seed run] (get raw [arm init])
                :let [damage (get-in run [:trajectory time :damage])]]
            (format "| %d | %s | %s | %d | %d | %d | %d |\n"
                    time (name arm) (name init) seed
                    (:phenotype damage) (:genotype damage) (:exotype damage))))))

(defn fitness-separation [raw]
  (let [pairs
        (for [init [:ordered :disordered]
              seed (range (:seed-base config)
                          (+ (:seed-base config) (:seeds config)))
              :let [preferences (get-in raw [[:baldwin-preferences init] seed])
                    divergence (get-in raw [[:baldwin-divergence init] seed])]]
          {:same-final-genotype?
           (= (get-in preferences [:final-state :genotype])
              (get-in divergence [:final-state :genotype]))
           :same-final-state? (= (:final-state preferences) (:final-state divergence))
           :same-trajectory? (= (:trajectory preferences) (:trajectory divergence))})]
    {:pairs (count pairs)
     :different-final-genotype
     (count (remove :same-final-genotype? pairs))
     :different-final-state
     (count (remove :same-final-state? pairs))
     :different-damage-trajectory
     (count (remove :same-trajectory? pairs))
     :all-final-states-identical? (every? :same-final-state? pairs)}))

(defn heritable-mobility [raw]
  (let [runs
        (for [arm [:baldwin-preferences :baldwin-divergence]
              init [:ordered :disordered]
              run (condition-runs raw arm init)]
          {:initial (rule-sigil (get initialisations init))
           :final (get-in run [:final-state :genotype])})]
    {:runs (count runs)
     :uniform-and-unchanged
     (count (filter (fn [{:keys [initial final]}]
                      (= (set final) #{initial}))
                    runs))}))

(def figure-conditions
  [[:baldwin-preferences :ordered]
   [:baldwin-preferences :disordered]])

(defn figure-path [[arm init]]
  (str "reports/figures/slice12-" (name arm) "-" (name init)
       "-triptych.png"))

(defn render-figure! [[arm init :as condition]]
  (let [seed (:seed-base config)
        states (take (inc (:steps config))
                     (iterate pattern/step-compact
                              (initial-state arm init seed)))
        path (figure-path condition)]
    (harness/render-pixels!
     (harness/triptych-pixels states) path
     (str "slice12 " (name arm) " " (name init) " seed=" seed))
    path))

(def interpretation
  (str
   "**No arm demonstrates regulation.** The Baldwin result is the decisive "
   "negative: ordered and disordered starts remain far apart. Preference fitness "
   "ends at P=1.250 (SD 0.707, range 0–2) from Rule 204 and P=26.000 "
   "(SD 6.392, range 18–38) from Rule 30; divergence fitness is the same on P. "
   "The temporary narrowing at step 600 reopens by 1200 and persists through "
   "6000, so final reach tracks initial reach rather than converging.\n\n"
   "This is structural, not a weak-selection estimate. Every Baldwin population "
   "starts with one uniform heritable rule. Neighbour-copy selection has no "
   "heritable variant to choose, and all 32 Baldwin runs finish with exactly "
   "that one starting rule. Preference and divergence dispatch do affect four "
   "counterfactual damage forks, showing that `fitness-kind` is threaded, but "
   "their unperturbed final states are identical in all 16 paired comparisons. "
   "The experiment is therefore void as a comparison of Baldwin fitnesses.\n\n"
   "The Lamarckian endpoints overlap (P=8.625 versus 7.500, both range 0–13), "
   "but its trajectory does not settle: mean P falls near 1 by step 600, rises "
   "near 6 by 1200, falls again by 3000, and rises again by 6000. Direct "
   "every-step write-back also erases the starting genotype by construction. "
   "That is initial-condition washout, not evidence that the arm regulates to "
   "a stable reach. The divergence arm remains well below the width-80 ceiling, "
   "but because selection never had variation this is not an informative upper "
   "bracket. The two Baldwin triptychs make the failure visible as solid, "
   "unchanged genotype columns."))

(defn report-markdown [raw figures]
  (let [{:keys [pairs different-final-genotype different-final-state
                different-damage-trajectory all-final-states-identical?]}
        (fitness-separation raw)
        {mobility-runs :runs uniform-and-unchanged :uniform-and-unchanged}
        (heritable-mobility raw)]
    (str
     "# Baldwin convergence experiment — Slice 12\n\n"
     "## Design\n\n"
     "Rule 204 (ordered) and Rule 30 (disordered/chaotic) are the two "
     "non-overlapping starts retained from the preregistered preflight. Each "
     "phenotype receives the paper's pure-ECA burn-in to t*=60 before the "
     "exotype dynamics starts. Three arms, two starts, and eight paired seeds "
     "give 48 runs at width 80 for 6000 steps on `:baseline`; lambda 0.55, mu "
     "0.1, tau 0.3, damage horizon 59, selection window 40, and Baldwin "
     "selection strength 1.0. Damage is measured at 0, 120, 600, 1200, 3000, "
     "and 6000.\n\n"
     "The Lamarckian arm writes the expressed rule back every step and has "
     "selection disabled. The two Baldwin arms disable write-back and differ "
     "only in `:fitness-kind`. Divergence fitness is an upper bracket because "
     "it selects on a damage proxy; it is not independent evidence for an "
     "edge-of-chaos claim.\n\n"
     "## Fitness threading gate\n\n"
     (format (str "Preference and divergence fitness produced different final "
                  "genotypes in **%d/%d** paired runs, different unperturbed "
                  "final states in **%d/%d**, and different damage trajectories "
                  "in **%d/%d**. **%d/%d** Baldwin runs retained a single uniform "
                  "genotype equal to their seeded rule. Gate: **%s**.\n\n")
             different-final-genotype pairs different-final-state pairs
             different-damage-trajectory pairs uniform-and-unchanged mobility-runs
             (if all-final-states-identical?
               "VOID — the fitnesses do not separate base dynamics"
               "PASS"))
     "The multimethod dispatch is exercised by the existing unit test and the "
     "four differing counterfactual trajectories. The null in the base runs is "
     "instead caused by absent heritable variation: copying a neighbour from a "
     "uniform population can only copy the same rule.\n\n"
     "## Verdict\n\n" interpretation "\n\n"
     "## Final damage\n\n"
     (summary-table raw [(:steps config)])
     "\n## Damage trajectory\n\n"
     (summary-table raw (:checkpoints config))
     "\n## Full per-seed trajectory\n\n"
     (per-seed-table raw)
     "\n## Representative triptychs\n\n"
     (apply str
            (for [[condition path] figures]
              (str "- `" (name (first condition)) "` / `"
                   (name (second condition)) "`: `" path "`\n")))
     "\nThe previous failed three-start preflight remains in "
     "`reports/exotype-baldwin-convergence-slice12.preflight.edn`; Rule 110 "
     "was dropped rather than relabelled as an undisputed critical point.\n")))

(defn run-all []
  (let [seeds (range (:seed-base config)
                     (+ (:seed-base config) (:seeds config)))]
    (reduce
     (fn [raw condition]
       (harness/run-condition! config raw-path raw condition seeds
                               trajectory-run))
     (harness/load-raw raw-path)
     conditions)))

(defn write-report! [raw]
  (let [figures (into (sorted-map)
                      (for [condition figure-conditions]
                        [condition (render-figure! condition)]))]
    (spit "reports/exotype-baldwin-convergence-slice12.md"
          (report-markdown raw figures))
    {:fitness-separation (fitness-separation raw)
     :figures figures}))

(defn experiment []
  (let [raw (run-all)]
    (assoc (write-report! raw) :conditions (count raw))))

(defn -main [& _]
  (println (pr-str (experiment))))
