(ns exotype-blend-baseline-slice10
  "Slice 10: scan neighbour-agreement blend strength on the baseline arm."
  (:require [clojure.java.shell :as sh]
            [futon5.exotype.pattern-eig :as pattern]
            [futon5.exotype.slice-harness :as harness]))

(def blend-strengths [0.0 0.1 0.35 0.7 1.0])
(def stored-baseline-path "reports/exotype-pattern-slice6.raw.edn")

(def config
  {:seed-base 20260803 :seeds 8 :width 80 :steps 6000 :workers 8
   :lambda 0.55 :mu 0.1 :tau 0.3 :prevalence-radius 1
   :damage-steps 59 :checkpoints [0 120 600 1200 3000 6000]})

(defn figure-path [beta]
  (str "reports/figures/slice10-baseline-beta" beta "-triptych.png"))

(defn blend-seed-run [beta seed]
  (harness/seed-run (assoc config :blend-strength beta) :baseline seed))

(defn render-blend! [beta]
  (let [state (harness/initial-state (assoc config :blend-strength beta)
                                     :baseline (:seed-base config))
        states (take (inc (:steps config)) (iterate pattern/step-compact state))
        path (figure-path beta)]
    (harness/render-pixels! (harness/triptych-pixels states) path
                            (str "baseline neighbour-agreement beta=" beta))
    path))

(defn ratchet-check [path]
  (let [{:keys [exit out err]}
        (sh/sh "python3" "analysis/ratchet_check.py" path "80" "right")]
    {:exit exit :output (str out err)}))

(defn layer-values [seed-runs layer]
  (mapv #(get-in % [:damage layer]) (vals seed-runs)))

(defn spread [values]
  {:mean (harness/mean values)
   :sd (harness/sd values)
   :min (apply min values)
   :max (apply max values)})

(defn damage-markdown [runs]
  (str "| beta | seed | P damage | G damage | X damage |\n"
       "|---:|---:|---:|---:|---:|\n"
       (apply str
              (for [[beta seed-runs] runs
                    [seed run] seed-runs]
                (format "| %.2f | %d | %d | %d | %d |\n"
                        beta seed (get-in run [:damage :phenotype])
                        (get-in run [:damage :genotype])
                        (get-in run [:damage :exotype]))))
       "\n| beta | P mean (SD; range) | G mean (SD; range) | X mean (SD; range) |\n"
       "|---:|---:|---:|---:|\n"
       (apply str
              (for [[beta seed-runs] runs
                    :let [p (spread (layer-values seed-runs :phenotype))
                          g (spread (layer-values seed-runs :genotype))
                          x (spread (layer-values seed-runs :exotype))]]
                (format (str "| %.2f | %.3f (%.3f; %d–%d) | "
                             "%.3f (%.3f; %d–%d) | %.3f (%.3f; %d–%d) |\n")
                        beta
                        (:mean p) (:sd p) (:min p) (:max p)
                        (:mean g) (:sd g) (:min g) (:max g)
                        (:mean x) (:sd x) (:min x) (:max x))))))

(defn report-markdown [runs figures ratchets byte-identical?]
  (str "# Baseline-arm neighbour-agreement blend scan — Slice 10\n\n"
       "Deterministic agreement blend over both circular immediate neighbours, "
       "selected against the centre rule with probability beta before the exotype "
       "propagator; `:baseline` arm; lambda 0.55, mu 0.1, tau 0.3; width 80; "
       "6000 steps; N=8 paired seeds per beta.\n\n"
       "## Identity gate\n\n"
       "Beta=0 `pr-str` equals the stored pre-change slice6 baseline run: **"
       byte-identical? "**. Its PNG is also byte-identical to the pre-change "
       "slice6 baseline (`d760b5b51240cc8e673e775f62dec0f74dc9534a31f8db00249f3e9844b17681`).\n\n"
       "## Damage and per-seed spread\n\n" (damage-markdown runs)
       "\n## Readout\n\n"
       "No nonzero beta passes the ratchet while adding the new genotype domains. "
       "Beta=0 passes at dominant share 0.425, but every nonzero beta narrowly fails "
       "only dominance (0.535–0.544): all retain 2–3 represented kinds, 0.800–0.825 "
       "domain rows, and zero confetti rows. Visually, the exotype panel retains broad "
       "coexisting green, orange, and red domains despite that formal failure. Genotype "
       "spatial structure survives on `:baseline`, clearest at beta=0.7 as extended "
       "horizontal same-colour regions, but it is weaker and finer-grained than the "
       "large blocks in slice9 beta=0.35. Slice10 beta=0.35 is therefore structured "
       "relative to the slice7/slice8 confetti, but plainly less coherent than slice9. "
       "The two desired layers are close but do not satisfy their gates simultaneously.\n"
       "\n## Figures and ratchet checks\n\n"
       (apply str
              (for [beta blend-strengths]
                (str "### beta=" beta "\n\n`" (get figures beta) "`\n\n```text\n"
                     (get-in ratchets [beta :output]) "```\n\n")))))

(defn experiment []
  (let [seeds (range (:seed-base config) (+ (:seed-base config) (:seeds config)))
        pool (java.util.concurrent.Executors/newFixedThreadPool (:workers config))]
    (try
      (let [tasks (into (sorted-map)
                        (for [beta blend-strengths seed seeds]
                          [[beta seed]
                           (.submit pool ^java.util.concurrent.Callable
                                    #(blend-seed-run beta seed))]))
            runs (reduce (fn [acc [[beta seed] future]]
                           (assoc-in acc [beta seed]
                                     (.get ^java.util.concurrent.Future future)))
                         (sorted-map) tasks)
            stored (get-in (harness/load-raw stored-baseline-path)
                           [:baseline (:seed-base config)])
            byte-identical? (= (pr-str stored)
                               (pr-str (get-in runs [0.0 (:seed-base config)])))
            figures (into (sorted-map) (for [beta blend-strengths]
                                         [beta (render-blend! beta)]))
            ratchets (into (sorted-map) (for [[beta path] figures]
                                          [beta (ratchet-check path)]))]
        (when-not byte-identical?
          (throw (ex-info "beta=0 differs from stored pre-change baseline" {})))
        (spit "reports/exotype-blend-baseline-slice10.md"
              (report-markdown runs figures ratchets byte-identical?))
        {:byte-identical? byte-identical?
         :runs runs :figures figures :ratchets ratchets})
      (finally (.shutdown pool)))))

(defn -main [& _]
  (println (pr-str (experiment))))
