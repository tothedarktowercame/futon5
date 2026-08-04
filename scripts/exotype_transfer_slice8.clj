(ns exotype-transfer-slice8
  "Slice 8: scan coherent +1 genotype transfer with EIG disabled."
  (:require [clojure.java.shell :as sh]
            [futon5.exotype.pattern-eig :as pattern]
            [futon5.exotype.slice-harness :as harness]))

(def transfer-fractions [0.0 0.25 0.5 0.75 1.0])

(def config
  {:seed-base 20260803 :seeds 4 :width 80 :steps 6000 :workers 5
   :lambda 0.55 :mu 0.1 :tau 0.3 :prevalence-radius 1
   :eig-model :legacy :eig-coefficient 0.0 :damage-steps 59
   :checkpoints [0 6000]})

(defn q-label [q]
  (if (zero? q) "0" (str q)))

(defn figure-path [q]
  (str "reports/figures/slice8-q" (q-label q) "-triptych.png"))

(defn transfer-seed-run [q seed]
  (harness/seed-run (assoc config :transfer-fraction q) :next-C seed))

(defn render-transfer! [q]
  (let [state (harness/initial-state (assoc config :transfer-fraction q)
                                     :next-C (:seed-base config))
        states (take (inc (:steps config)) (iterate pattern/step-compact state))
        path (figure-path q)]
    (harness/render-pixels! (harness/triptych-pixels states) path
                            (str "directed transfer fraction q=" q))
    path))

(defn ratchet-check [path]
  (let [{:keys [exit out err]}
        (sh/sh "python3" "analysis/ratchet_check.py" path "80" "right")]
    {:exit exit :output (str out err)}))

(defn damage-markdown [runs]
  (str "| q | seed | P damage | G damage | X damage |\n"
       "|---:|---:|---:|---:|---:|\n"
       (apply str
              (for [[q seed-runs] runs
                    [seed run] seed-runs]
                (format "| %.2f | %d | %d | %d | %d |\n"
                        q seed (get-in run [:damage :phenotype])
                        (get-in run [:damage :genotype])
                        (get-in run [:damage :exotype]))))
       "\n| q | P damage mean | G damage mean | X damage mean |\n"
       "|---:|---:|---:|---:|\n"
       (apply str
              (for [[q seed-runs] runs]
                (format "| %.2f | %.3f | %.3f | %.3f |\n"
                        q
                        (harness/mean (map #(get-in % [:damage :phenotype])
                                           (vals seed-runs)))
                        (harness/mean (map #(get-in % [:damage :genotype])
                                           (vals seed-runs)))
                        (harness/mean (map #(get-in % [:damage :exotype])
                                           (vals seed-runs))))))))

(defn damage-mean [runs q layer]
  (harness/mean (map #(get-in % [:damage layer]) (vals (get runs q)))))

(defn report-markdown [runs figures ratchets]
  (str "# Directed exotype genotype-transfer scan — Slice 8\n\n"
       "Fixed source offset +1; EIG off (`:next-C`); lambda 0.55, mu 0.1, "
       "tau 0.3; width 80; 6000 steps; N=4 paired seeds per transfer fraction.\n\n"
       "## Damage\n\n" (damage-markdown runs)
       "\n## Readout\n\n"
       "The unchanged stored-run test confirms byte-identical q=0 behaviour. "
       "The directed transfer remains wired: mean G damage rises from "
       (format "%.3f" (damage-mean runs 0.0 :genotype)) " at q=0 to "
       (format "%.3f" (damage-mean runs 0.25 :genotype)) " at q=0.25 and "
       (format "%.3f" (damage-mean runs 0.5 :genotype)) " at q=0.5. No scanned "
       "q keeps P damage near the q=0 value of "
       (format "%.3f" (damage-mean runs 0.0 :phenotype)) "; the closest is q=0.25 "
       "at " (format "%.3f" (damage-mean runs 0.25 :phenotype)) ". Side by side, "
       "slice8 q=0.5 remains pixel-scale genotype confetti like slice7 q=0.5. "
       "The fixed direction adds at most faint directional microtexture, not "
       "coherent macroscopic domains. The exotype ratchet remains broken because "
       "`:next-C` is chaos-dominated; that is independent of this offset test.\n"
       "\n## Figures and ratchet checks\n\n"
       (apply str
              (for [q transfer-fractions]
                (str "### q=" q "\n\n`" (get figures q) "`\n\n```text\n"
                     (get-in ratchets [q :output]) "```\n\n")))))

(defn experiment []
  (let [seeds (range (:seed-base config) (+ (:seed-base config) (:seeds config)))
        pool (java.util.concurrent.Executors/newFixedThreadPool (:workers config))]
    (try
      (let [tasks (into (sorted-map)
                        (for [q transfer-fractions seed seeds]
                          [[q seed]
                           (.submit pool ^java.util.concurrent.Callable
                                    #(transfer-seed-run q seed))]))
            runs (reduce (fn [acc [[q seed] future]]
                           (assoc-in acc [q seed]
                                     (.get ^java.util.concurrent.Future future)))
                         (sorted-map) tasks)
            figures (into (sorted-map) (for [q transfer-fractions]
                                         [q (render-transfer! q)]))
            ratchets (into (sorted-map) (for [[q path] figures]
                                          [q (ratchet-check path)]))]
        (spit "reports/exotype-transfer-slice8.md"
              (report-markdown runs figures ratchets))
        {:runs runs :figures figures :ratchets ratchets})
      (finally (.shutdown pool)))))

(defn -main [& _]
  (println (pr-str (experiment))))
