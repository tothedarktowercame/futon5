(ns exotype-eig-coefficient-slice6c
  "Slice 6c: corrected-EIG coefficient sweep. Measurements only."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.exotype.pattern-eig :as pattern]
            [futon5.exotype.slice-harness :as harness]))

(def coefficients [0.0 0.5 1.0 1.2 1.35 1.45 1.475 1.5
                   1.6 1.75 2.0 2.5 3.0 5.0 7.5 10.0])
(def config
  {:seed-base 20260803 :seeds 60 :width 80 :steps 6000 :workers 12
   :lambda 0.55 :mu 0.1 :tau 0.3 :prevalence-radius 1
   :eig-model :beta-posterior :damage-steps 59
   :checkpoints [0 120 600 1200 3000 6000]})
(def paths
  {:raw "reports/exotype-eig-coefficient-slice6c.raw.edn"
   :edn "reports/exotype-eig-coefficient-slice6c.edn"
   :md "reports/exotype-eig-coefficient-slice6c.md"
   :figure-prefix "reports/figures/slice6c-c-"})

(defn coefficient-label [coefficient]
  (str/replace (format "%.3f" coefficient) "." "p"))

(defn coefficient-state [coefficient seed]
  (harness/initial-state (assoc config :eig-coefficient coefficient)
                         :next-C-plus-eig seed))

(defn coefficient-stop-line [coefficient]
  (let [states (for [seed (range (:seed-base config) (+ (:seed-base config) 8))]
                 (coefficient-state coefficient seed))
        decisions (mapcat (fn [state]
                            (map #(pattern/cell-decision :next-C-plus-eig state %)
                                 (range (:width config)))) states)]
    (into (sorted-map)
          (for [term [:risk :ambiguity :conatus :weighted-eig]
                :let [ranges (map (fn [decision]
                                    (let [values (map term (:candidates decision))]
                                      (- (apply max values) (apply min values))))
                                  decisions)]]
            [term {:fraction-discriminating
                   (/ (count (filter #(> % 1.0e-12) ranges))
                      (double (count ranges)))
                   :within-decision-range (harness/summary ranges)}]))))

(defn coefficient-seed-run [coefficient seed]
  (harness/seed-run config :next-C-plus-eig seed
                    {:eig-model :beta-posterior
                     :eig-coefficient (double coefficient)}))

(defn peak-summary [runs]
  (let [times (rest (:checkpoints config))
        rows (for [run runs
                   :let [peak-time (apply max-key
                                          #(get-in run [:checkpoints % :entropy]) times)]]
               {:time peak-time
                :entropy (get-in run [:checkpoints peak-time :entropy])
                :kind-count (get-in run [:checkpoints peak-time :kind-count])})]
    {:time (harness/summary (map :time rows))
     :entropy (harness/summary (map :entropy rows))
     :kind-count (harness/summary (map :kind-count rows))
     :modal-time (first (apply max-key val (frequencies (map :time rows))))}))

(defn coefficient-summary [runs]
  (assoc (harness/condition-summary config runs)
         :peak (peak-summary runs)
         :final-dominant-kind
         (frequencies
          (map (fn [run]
                 (first (apply max-key val
                               (get-in run [:checkpoints 6000 :counts])))) runs))))

(defn coefficient-contrast [raw coefficient]
  (into (sorted-map)
        (for [[metric measure] (harness/contrast-metrics config)]
          [metric (harness/paired-contrast
                   (get raw coefficient) (get raw 0.0) measure)])))

(defn render-coefficient! [coefficient]
  (let [states (take (inc (:steps config))
                     (iterate pattern/step-compact
                              (coefficient-state coefficient (:seed-base config))))]
    (harness/render-pixels!
     (harness/triptych-pixels states)
     (str (:figure-prefix paths) (coefficient-label coefficient) "-triptych.png")
     (str "EIG coefficient " coefficient))))

(defn best-sustained-coefficient [summaries]
  (first
   (last
    (sort-by (fn [[_ row]] [(get-in row [:trajectory 6000 :entropy :mean])
                            (get-in row [:trajectory 6000 :kind-count :mean])])
             summaries))))

(defn coefficient-determinism []
  (let [left (pr-str (coefficient-seed-run 1.0 (:seed-base config)))
        right (pr-str (coefficient-seed-run 1.0 (:seed-base config)))]
    {:coefficient 1.0 :byte-identical? (= left right)
     :hash (format "%08x" (bit-and 0xffffffff (hash left)))}))

(defn coefficient-markdown [result]
  (str "# EIG coefficient sweep — Slice 6c\n\n"
       "Measurements only. N=60, width=80, horizon=6000; lambda=0.55, mu=0.1, tau=0.3.\n\n"
       "## Within-decision stop-line\n\n```clojure\n"
       (pr-str (:stop-line result)) "\n```\n\n"
       "## Endpoint and trajectory peaks\n\n"
       "| c | dominant runs | kinds | entropy | peak kinds | peak entropy | modal peak time | changed steps | phenotype activity | genotype rules | P damage | G damage | X damage | spatial |\n"
       "|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n"
       (apply str
              (for [[coefficient row] (:coefficients result)
                    :let [end (get-in row [:trajectory 6000])]]
                (format (str "| %.3f | `%s` | %s | %s | %s | %s | %d | %s | %s | "
                             "%s | %s | %s | %s | %s |\n")
                        coefficient (pr-str (:final-dominant-kind row))
                        (harness/fmt (:kind-count end)) (harness/fmt (:entropy end))
                        (harness/fmt (get-in row [:peak :kind-count]))
                        (harness/fmt (get-in row [:peak :entropy]))
                        (get-in row [:peak :modal-time])
                        (harness/fmt (:changed-steps row))
                        (harness/fmt (:phenotype-activity row))
                        (harness/fmt (:genotype-rule-count row))
                        (harness/fmt (get-in row [:damage :phenotype]))
                        (harness/fmt (get-in row [:damage :genotype]))
                        (harness/fmt (get-in row [:damage :exotype]))
                        (harness/fmt (:spatial-autocorrelation end)))))
       "\n## Full trajectories\n\n```clojure\n"
       (pr-str (into (sorted-map)
                     (for [[coefficient row] (:coefficients result)]
                       [coefficient (:trajectory row)])))
       "\n```\n\n## Paired contrasts from c=0\n\n```clojure\n"
       (pr-str (:contrasts result))
       "\n```\n\n## Determinism, parity, and figures\n\n```clojure\n"
       (pr-str (select-keys result [:determinism :c-one-parity :figures
                                    :modelling-choices]))
       "\n```\n\nNo scientific verdict is made here.\n"))

(defn experiment []
  (let [seeds (range (:seed-base config) (+ (:seed-base config) (:seeds config)))
        run-fn coefficient-seed-run
        raw (reduce #(harness/run-condition! config (:raw paths) %1 %2 seeds run-fn)
                    (harness/load-raw (:raw paths)) coefficients)
        summaries (into (sorted-map)
                        (for [coefficient coefficients]
                          [coefficient (coefficient-summary
                                        (vals (get raw coefficient)))]))
        best (best-sustained-coefficient summaries)
        figure-coefficients (distinct [0.0 1.475 best])]
    {:kind :exotype-eig-coefficient-slice6c :schema 1 :config config
     :coefficients summaries
     :stop-line (into (sorted-map)
                      (for [coefficient coefficients]
                        [coefficient (coefficient-stop-line coefficient)]))
     :contrasts (into (sorted-map)
                      (for [coefficient coefficients :when (not (zero? coefficient))]
                        [coefficient (coefficient-contrast raw coefficient)]))
     :determinism (coefficient-determinism)
     :c-one-parity {:reference :slice6b-next-C-plus-eig
                    :equal? (= (get raw 1.0)
                               (:next-C-plus-eig
                                (edn/read-string
                                 (slurp "reports/exotype-pattern-slice6b.raw.edn"))))}
     :figures (into (sorted-map)
                    (for [coefficient figure-coefficients]
                      [coefficient (render-coefficient! coefficient)]))
     :modelling-choices {:eig-model :beta-posterior
                         :eig-prior pattern/beta-prior
                         :coefficient-role :pure-multiplier
                         :forbidden-inputs [:damage :reach :band :entropy
                                            :kind-count :global-statistic]}}))

(defn -main [& _]
  (let [result (experiment)]
    (spit (:edn paths) (str (pr-str result) "\n"))
    (spit (:md paths) (coefficient-markdown result))
    (println :wrote (:edn paths) (:md paths))))

(apply -main *command-line-args*)
(shutdown-agents)
