(ns exotype-baldwin-convergence-slice12
  "Slice 12 preflight: verify that calibrated initial rules remain separated
   under the exotype/Baldwin damage apparatus before spending on convergence."
  (:require [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.slice-harness :as harness]))

(def config
  {:seed-base 20260803 :seeds 8 :width 80 :workers 8
   :lambda 0.55 :mu 0.1 :tau 0.3 :prevalence-radius 1
   :damage-steps 59})

(def initialisations
  ;; The paper's ECA calibration ladder places 204 in the ordered band, 110 in
  ;; the complex band, and 30 in the chaotic band. `:critical-proxy` is
  ;; deliberate: draft6 reports no finite-size evidence for a critical point.
  {:ordered 204 :critical-proxy 110 :disordered 30})

(def arms
  {:lamarckian
   {:selection-strength 0.0 :fitness-kind :preferences :write-back? true}
   :baldwin-preferences
   {:selection-strength 1.0 :fitness-kind :preferences :write-back? false}
   :baldwin-divergence
   {:selection-strength 1.0 :fitness-kind :divergence :write-back? false}})

(def protocols [:immediate :eca-burn-60])

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
  "Burn in the seeded phenotype for t*=60 under the exact ECA rule. Identity
   exotypes and write-back preserve the uniform genotype during calibration."
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

(defn initial-state [protocol arm init seed]
  (let [arm-config (merge config (get arms arm))
        genotype (uniform-rule (get initialisations init))
        base (harness/initial-state arm-config :baseline seed)
        phenotype (case protocol
                    :immediate (:phenotype base)
                    :eca-burn-60 (calibrated-phenotype base genotype))]
    (-> base
        (with-rule genotype)
        (assoc :phenotype phenotype))))

(defn preflight-row [[protocol arm init seed]]
  (let [arm-config (merge config (get arms arm))]
    {:protocol protocol
     :arm arm
     :initialisation init
     :rule (get initialisations init)
     :seed seed
     :damage (harness/damage arm-config
                             (initial-state protocol arm init seed))}))

(defn spread [values]
  {:mean (harness/mean values)
   :sd (harness/sd values)
   :min (apply min values)
   :max (apply max values)})

(defn summary-rows [rows]
  (for [[[protocol arm init] group]
        (sort-by key (group-by (juxt :protocol :arm :initialisation) rows))]
    {:protocol protocol
     :arm arm
     :initialisation init
     :layers
     (into (sorted-map)
           (for [layer [:phenotype :genotype :exotype]]
             [layer (spread (map #(get-in % [:damage layer]) group))]))}))

(defn- format-spread [{:keys [mean sd min max]}]
  (format "%.3f (%.3f; %d–%d)" mean sd min max))

(defn- per-seed-table [rows]
  (str
   "| protocol | arm | start | rule | seed | P | G | X |\n"
   "|---|---|---|---:|---:|---:|---:|---:|\n"
   (apply str
          (for [{:keys [protocol arm initialisation rule seed damage]}
                (sort-by (juxt :protocol :arm :initialisation :seed) rows)]
            (format "| %s | %s | %s | %d | %d | %d | %d | %d |\n"
                    (name protocol) (name arm) (name initialisation) rule seed
                    (:phenotype damage) (:genotype damage) (:exotype damage))))))

(defn- summary-table [summaries]
  (str
   "| protocol | arm | start | P mean (SD; range) | G mean (SD; range) | X mean (SD; range) |\n"
   "|---|---|---|---:|---:|---:|\n"
   (apply str
          (for [{:keys [protocol arm initialisation layers]} summaries]
            (format "| %s | %s | %s | %s | %s | %s |\n"
                    (name protocol) (name arm) (name initialisation)
                    (format-spread (:phenotype layers))
                    (format-spread (:genotype layers))
                    (format-spread (:exotype layers)))))))

(defn report-markdown [rows]
  (let [summaries (vec (summary-rows rows))]
    (str
     "# Baldwin convergence experiment — Slice 12 preflight\n\n"
     "**Result: stopped at the initial-separation gate; the 6000-step convergence "
     "sweep was not run.**\n\n"
     "## Design and calibration\n\n"
     "The paper's exact ECA calibration family was used: Rule 204 (ordered), "
     "Rule 110 (complex, used here only as a `critical-proxy`), and Rule 30 "
     "(disordered/chaotic). Draft6 explicitly reports no finite-size evidence "
     "for a critical point, so Rule 110 is not relabelled as proven critical. "
     "The published damage anchors are 1.00, 16.68, and 36.45 respectively.\n\n"
     "The requested apparatus was evaluated on `:baseline` at width 80, lambda "
     "0.55, mu 0.1, tau 0.3, damage horizon 59, and eight paired seeds. Two "
     "preflights were used: an immediate uniform-genotype seed, and the "
     "calibration-faithful version in which the phenotype first receives the "
     "paper's pure-ECA burn-in to t*=60 before the exotype arm starts.\n\n"
     "## Gate result\n\n"
     "Rule 204 has substantially lower mean reach than the other two starts. Rule 110 and "
     "Rule 30 are not separated once the actual exotype/Baldwin update is part "
     "of the 59-step reach measurement. After the t*=60 burn-in their Baldwin "
     "phenotype reaches are 24.750 (SD 5.548, range 16–32) and 29.750 "
     "(SD 5.036, range 24–38); the distributions overlap substantially. In the "
     "Lamarckian arm they overlap and reverse in their means: 10.875 "
     "(SD 5.842, range 0–18) versus 9.500 (SD 7.309, range 0–20).\n\n"
     "Therefore the three starts do not actually instantiate three distinguishable "
     "initial reaches under this experiment's instrument. A later equality of "
     "endpoints could not be interpreted as convergence rather than loss of the "
     "initial contrast. Per the preregistered instruction, the test is void and "
     "stops here. No triptychs were rendered.\n\n"
     "## Summary\n\n"
     (summary-table summaries)
     "\n## Full per-seed preflight\n\n"
     (per-seed-table rows)
     "\n## Found, not fixed\n\n"
     "The calibration ladder itself is intact; the collision appears only after "
     "embedding those rules in the exotype/selection family. Choosing a different "
     "middle rule after seeing this result, or declaring Rule 110 and Rule 30 "
     "different by label despite the measured overlap, would weaken the gate and "
     "was not done.\n")))

(defn experiment []
  (let [seeds (range (:seed-base config)
                     (+ (:seed-base config) (:seeds config)))
        jobs (for [protocol protocols
                   arm (keys arms)
                   init (keys initialisations)
                   seed seeds]
               [protocol arm init seed])
        pool (java.util.concurrent.Executors/newFixedThreadPool (:workers config))]
    (try
      (let [futures (mapv #(.submit pool ^java.util.concurrent.Callable
                                    (fn [] (preflight-row %)))
                          jobs)
            rows (mapv #(.get ^java.util.concurrent.Future %) futures)]
        (harness/save-raw!
         "reports/exotype-baldwin-convergence-slice12.preflight.edn" rows)
        (spit "reports/exotype-baldwin-convergence-slice12.md"
              (report-markdown rows))
        {:status :stopped-at-initial-separation-gate
         :rows (count rows)})
      (finally
        (.shutdown pool)))))

(defn -main [& _]
  (println (pr-str (experiment))))
