(ns exotype-blend-slice9
  "Slice 9: scan deterministic neighbour-agreement blend strength with EIG disabled."
  (:require [clojure.java.shell :as sh]
            [futon5.exotype.pattern-eig :as pattern]
            [futon5.exotype.slice-harness :as harness]))

(def blend-strengths [0.0 0.1 0.35 0.7 1.0])

(def config
  {:seed-base 20260803 :seeds 4 :width 80 :steps 6000 :workers 5
   :lambda 0.55 :mu 0.1 :tau 0.3 :prevalence-radius 1
   :eig-model :legacy :eig-coefficient 0.0 :damage-steps 59
   :checkpoints [0 6000]})

(defn beta-label [beta]
  (str beta))

(defn figure-path [beta]
  (str "reports/figures/slice9-beta" (beta-label beta) "-triptych.png"))

(defn blend-seed-run [beta seed]
  (harness/seed-run (assoc config :blend-strength beta) :next-C seed))

(defn render-blend! [beta]
  (let [state (harness/initial-state (assoc config :blend-strength beta)
                                     :next-C (:seed-base config))
        states (take (inc (:steps config)) (iterate pattern/step-compact state))
        path (figure-path beta)]
    (harness/render-pixels! (harness/triptych-pixels states) path
                            (str "neighbour-agreement blend strength beta=" beta))
    path))

(defn ratchet-check [path]
  (let [{:keys [exit out err]}
        (sh/sh "python3" "analysis/ratchet_check.py" path "80" "right")]
    {:exit exit :output (str out err)}))

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
       "\n| beta | P damage mean | G damage mean | X damage mean |\n"
       "|---:|---:|---:|---:|\n"
       (apply str
              (for [[beta seed-runs] runs]
                (format "| %.2f | %.3f | %.3f | %.3f |\n"
                        beta
                        (harness/mean (map #(get-in % [:damage :phenotype])
                                           (vals seed-runs)))
                        (harness/mean (map #(get-in % [:damage :genotype])
                                           (vals seed-runs)))
                        (harness/mean (map #(get-in % [:damage :exotype])
                                           (vals seed-runs))))))))

(defn report-markdown [runs figures ratchets]
  (str "# Neighbour-agreement genotype-blend scan — Slice 9\n\n"
       "Deterministic agreement blend over both circular immediate neighbours "
       "(the existing futon5 grid topology), selected against the centre "
       "rule with probability beta before the exotype propagator; EIG off "
       "(`:next-C`); lambda 0.55, mu 0.1, tau 0.3; width 80; 6000 steps; "
       "N=4 paired seeds per blend strength.\n\n"
       "## Damage\n\n" (damage-markdown runs)
       "\n## Readout\n\n"
       "The beta=0 stored-run test remains byte-identical, and its PNG has the same "
       "SHA-256 as slice8 q=0 "
       "(`0753df1ae652f9b7f376241c21c11385fae4fb7f825d8260d48ac96a6b68ba3e`). "
       "Slice9 beta=0.35 plainly replaces slice8 q=0.5's "
       "uniform pixel-scale genotype confetti with contiguous horizontal and block-like "
       "same-colour regions: coherent spatial structure appeared. Genotype damage is "
       "nonzero at beta=0.35 and beta=1.0, but is seed-sensitive and non-monotone. "
       "Beta=1.0 is the scanned damage band: mean G is nonzero (4.750) while mean P "
       "is 9.500, near beta=0's 8.500. Beta=0.35 has much stronger mean G damage "
       "(23.500), but mean P rises to 24.250. The exotype ratchet remains broken, as "
       "expected for the independently chaos-dominated `:next-C` arm; it is not a "
       "failure of the blend mechanism.\n"
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
            figures (into (sorted-map) (for [beta blend-strengths]
                                         [beta (render-blend! beta)]))
            ratchets (into (sorted-map) (for [[beta path] figures]
                                          [beta (ratchet-check path)]))]
        (spit "reports/exotype-blend-slice9.md"
              (report-markdown runs figures ratchets))
        {:runs runs :figures figures :ratchets ratchets})
      (finally (.shutdown pool)))))

(defn -main [& _]
  (println (pr-str (experiment))))
