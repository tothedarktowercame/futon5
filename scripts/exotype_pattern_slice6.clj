(ns exotype-pattern-slice6
  "Slice 6: pattern NEXT claims and local epistemic value. Measurements only."
  (:require [futon5.exotype.pattern-eig :as pattern]
            [futon5.exotype.slice-harness :as harness]))

(def config
  {:seed-base 20260803 :seeds 60 :width 80 :steps 6000 :workers 12
   :lambda 0.55 :mu 0.1 :tau 0.3 :prevalence-radius 1
   :damage-steps 59 :checkpoints [0 120 600 1200 3000 6000]
   :arms pattern/arms})

(def paths
  {:raw "reports/exotype-pattern-slice6.raw.edn"
   :edn "reports/exotype-pattern-slice6.edn"
   :md "reports/exotype-pattern-slice6.md"
   :figure-prefix "reports/figures/slice6"})

(defn contrasts [raw]
  (into (sorted-map)
        (for [[label left right] [[:next-C-minus-baseline :next-C :baseline]
                                  [:next-C-plus-eig-minus-next-C
                                   :next-C-plus-eig :next-C]]]
          [label (into (sorted-map)
                       (for [[name metric] (harness/contrast-metrics config)]
                         [name (harness/paired-contrast
                                (get raw left) (get raw right) metric)]))])))

(defn render-arm! [arm]
  (let [states (take (inc (:steps config))
                     (iterate pattern/step-compact
                              (harness/initial-state config arm (:seed-base config))))]
    (harness/render-pixels!
     (harness/triptych-pixels states)
     (str (:figure-prefix paths) "-" (name arm) "-triptych.png")
     (name arm))))

(defn determinism []
  (let [left (pr-str (harness/seed-run config :next-C-plus-eig (:seed-base config)))
        right (pr-str (harness/seed-run config :next-C-plus-eig (:seed-base config)))]
    {:byte-identical? (= left right)
     :hash (format "%08x" (bit-and 0xffffffff (hash left)))}))

(defn markdown [result]
  (str "# Exotype patterns and local EIG — Slice 6\n\n"
       "Measurements only. N=60, width=80, horizon=6000; lambda=0.55, mu=0.1, tau=0.3.\n\n"
       "## Within-decision stop-line\n\n```clojure\n" (pr-str (:stop-line result)) "\n```\n\n"
       "## Endpoint and activity\n\n| arm | kinds | entropy | spatial | changed steps | changed cells | phenotype activity | genotype rules | P damage | G damage | X damage |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n"
       (apply str
              (for [[arm row] (:arms result)
                    :let [end (get-in row [:trajectory 6000])]]
                (format "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n"
                        (name arm) (harness/fmt (:kind-count end))
                        (harness/fmt (:entropy end))
                        (harness/fmt (:spatial-autocorrelation end))
                        (harness/fmt (:changed-steps row))
                        (harness/fmt (:changed-cells row))
                        (harness/fmt (:phenotype-activity row))
                        (harness/fmt (:genotype-rule-count row))
                        (harness/fmt (get-in row [:damage :phenotype]))
                        (harness/fmt (get-in row [:damage :genotype]))
                        (harness/fmt (get-in row [:damage :exotype])))))
       "\n## Trajectories\n\n```clojure\n"
       (pr-str (into (sorted-map)
                     (for [[arm row] (:arms result)] [arm (:trajectory row)])))
       "\n```\n\n## Paired contrasts\n\nA contrast is marked resolved only at |mean| >= 2 SEM.\n\n```clojure\n"
       (pr-str (:contrasts result))
       "\n```\n\n## Modelling choices and determinism\n\n```clojure\n"
       (pr-str (select-keys result [:modelling-choices :determinism :figures]))
       "\n```\n\nNo scientific verdict is made here.\n"))

(defn experiment []
  (let [probe (harness/stop-line config)]
    (when-not (pos? (get-in probe [:eig :fraction-discriminating]))
      (throw (ex-info "STOP-LINE: EIG does not discriminate within decisions" probe)))
    (let [seeds (range (:seed-base config) (+ (:seed-base config) (:seeds config)))
          run-fn #(harness/seed-run config %1 %2)
          raw (reduce #(harness/run-condition! config (:raw paths) %1 %2 seeds run-fn)
                      (harness/load-raw (:raw paths)) (:arms config))]
      {:kind :exotype-pattern-slice6 :schema 1 :config config
       :stop-line probe
       :modelling-choices
       {:next :existing-four-channel-probability-vector
        :claim-test {:statistic :mean-channel-log-likelihood :floor :log-one-half}
        :eig :entropy-of-current-local-holder-confirmations
        :no-cell-memory true :eig-coefficient 1.0
        :risk :sum-bernoulli-kl-prediction-to-candidate-next
        :eig-only-retains-conatus true
        :forbidden-inputs [:damage :reach :band :entropy :kind-count :global-statistic]}
       :arms (into (sorted-map)
                   (for [arm (:arms config)]
                     [arm (harness/condition-summary config (vals (get raw arm)))]))
       :contrasts (contrasts raw)
       :determinism (determinism)
       :figures (into (sorted-map)
                      (for [arm (:arms config)] [arm (render-arm! arm)]))})))

(defn -main [& _]
  (let [result (experiment)]
    (spit (:edn paths) (str (pr-str result) "\n"))
    (spit (:md paths) (markdown result))
    (println :wrote (:edn paths) (:md paths))))

(apply -main *command-line-args*)
(shutdown-agents)
