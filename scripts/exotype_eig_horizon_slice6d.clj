(ns exotype-eig-horizon-slice6d
  "Slice 6d: long-horizon and size-invariance measurements around c=5."
  (:require [clojure.string :as str]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.pattern-eig :as pattern]
            [futon5.exotype.slice-harness :as harness]))

(def coefficients [3.0 4.0 4.5 5.0 5.5 6.0 7.0])
(def base-config
  {:seed-base 20260803 :seeds 60 :width 80 :steps 24000 :workers 12
   :lambda 0.55 :mu 0.1 :tau 0.3 :prevalence-radius 1
   :eig-model :beta-posterior :damage-steps 59
   :include-genotype-spatial? true
   :checkpoints [0 120 600 1200 3000 6000 9000 12000
                 15000 18000 21000 24000]
   :late-window [12000 24000]
   :late-checkpoints [12000 15000 18000 21000 24000]})
(def paths
  {:raw "reports/exotype-eig-horizon-slice6d.raw.edn"
   :edn "reports/exotype-eig-horizon-slice6d.edn"
   :md "reports/exotype-eig-horizon-slice6d.md"
   :figure-prefix "reports/figures/slice6d"})

(defn condition-config [[width _]] (assoc base-config :width width))

(defn condition-run [[width coefficient] seed]
  (harness/seed-run (assoc base-config :width width)
                    :next-C-plus-eig seed
                    {:eig-model :beta-posterior
                     :eig-coefficient (double coefficient)}))

(defn paired-late-delta [runs metric]
  (let [[start end] (:late-window base-config)
        values (mapv #(- (double (get-in % [:checkpoints end metric]))
                          (double (get-in % [:checkpoints start metric]))) runs)
        stats (harness/summary values)
        sem-units (when (pos? (:sem stats)) (/ (:mean stats) (:sem stats)))
        classification
        (cond
          (and (zero? (:mean stats)) (zero? (:sem stats))) :exact-plateau
          (and sem-units (< sem-units -2.0)) :decay
          (and sem-units (> sem-units 2.0)) :growth
          :else :unresolved)]
    (assoc stats :sem-units sem-units :classification classification
           :from start :to end)))

(defn peak-summary [runs]
  (let [times (rest (:checkpoints base-config))
        rows (for [run runs
                   :let [time (apply max-key #(get-in run [:checkpoints % :entropy])
                                     times)]]
               {:time time
                :entropy (get-in run [:checkpoints time :entropy])
                :kind-count (get-in run [:checkpoints time :kind-count])})]
    {:time (harness/summary (map :time rows))
     :entropy (harness/summary (map :entropy rows))
     :kind-count (harness/summary (map :kind-count rows))
     :modal-time (first (apply max-key val (frequencies (map :time rows))))}))

(defn condition-summary [condition runs]
  (let [config (condition-config condition)
        summary (harness/condition-summary config runs)
        endpoint (:steps config)]
    (assoc summary
           :late-assessment
           {:kind-count (paired-late-delta runs :kind-count)
            :entropy (paired-late-delta runs :entropy)}
           :late-distribution
           (into (sorted-map)
                 (for [kind grid/exotype-kinds]
                   [kind (harness/summary
                          (for [run runs time (:late-checkpoints config)]
                            (get-in run [:checkpoints time :counts kind])))]))
           :peak (peak-summary runs)
           :final-dominant-kind
           (frequencies
            (map (fn [run]
                   (first (apply max-key val
                                 (get-in run [:checkpoints endpoint :counts]))))
                 runs)))))

(defn best-coefficient [summaries]
  (first
   (last
    (sort-by (fn [[_ row]] [(get-in row [:trajectory 24000 :entropy :mean])
                            (get-in row [:trajectory 24000 :kind-count :mean])])
             summaries))))

(defn condition-determinism [condition]
  (let [seed (:seed-base base-config)
        left (pr-str (condition-run condition seed))
        right (pr-str (condition-run condition seed))]
    {:condition condition :byte-identical? (= left right)
     :hash (format "%08x" (bit-and 0xffffffff (hash left)))}))

(defn captured-panels [width coefficient]
  (let [sample-every (long (Math/ceil (/ (:steps base-config) 1199.0)))
        late-start (- (:steps base-config) 600)
        initial (harness/initial-state
                 (assoc base-config :width width :eig-coefficient coefficient)
                 :next-C-plus-eig (:seed-base base-config))]
    (loop [state initial time 0 full [] early [] late []]
      (let [full (if (zero? (mod time sample-every)) (conj full state) full)
            early (if (<= time 600) (conj early state) early)
            late (if (>= time late-start) (conj late state) late)]
        (if (= time (:steps base-config))
          {:sample-every sample-every :full full :early early :late late}
          (recur (pattern/step-compact state) (inc time) full early late))))))

(defn render-condition! [width coefficient]
  (let [{:keys [sample-every full early late]} (captured-panels width coefficient)
        label (str "w" width "-c" (str/replace (format "%.3f" coefficient) "." "p"))
        render! (fn [window states]
                  (harness/render-pixels!
                   (harness/triptych-pixels states)
                   (str (:figure-prefix paths) "-" label "-" window ".png")
                   (str "Slice 6d " label " " window)))]
    {:full {:path (render! "full" full)
            :sample-every sample-every :source-window [0 24000]}
     :early {:path (render! "early-600" early)
             :sample-every 1 :source-window [0 600]}
     :late {:path (render! "late-600" late)
            :sample-every 1 :source-window [23400 24000]}}))

(defn run-conditions! [raw conditions]
  (let [seeds (range (:seed-base base-config)
                     (+ (:seed-base base-config) (:seeds base-config)))]
    (reduce (fn [result condition]
              (harness/run-condition! (condition-config condition) (:raw paths)
                                      result condition seeds condition-run))
            raw conditions)))

(defn experiment []
  (let [width-80-conditions (mapv #(vector 80 %) coefficients)
        raw-80 (run-conditions! (harness/load-raw (:raw paths)) width-80-conditions)
        summaries-80 (into (sorted-map)
                           (for [[_ coefficient :as condition] width-80-conditions]
                             [coefficient (condition-summary
                                           condition (vals (get raw-80 condition)))]))
        best (best-coefficient summaries-80)
        size-condition [160 best]
        raw (run-conditions! raw-80 [size-condition])
        size-summaries
        (into (sorted-map)
              (for [width [80 160]
                    :let [condition [width best]]]
                [width (condition-summary condition (vals (get raw condition)))]))]
    {:kind :exotype-eig-horizon-slice6d :schema 1
     :config base-config :coefficients coefficients
     :throughput-plan
     {:measured-reference-runs-per-minute 121.0
      :reference {:width 80 :steps 6000}
      :estimated-width-80-runs-per-minute 30.25
      :estimated-width-160-runs-per-minute 15.125
      :width-80-runs 420 :width-160-runs 60
      :estimated-total-minutes (+ (/ 420.0 30.25) (/ 60.0 15.125))}
     :width-80 summaries-80
     :best-coefficient-selection
     {:coefficient best :criterion [:max-final-entropy :max-final-kind-count]}
     :size-invariance size-summaries
     :determinism (condition-determinism [80 best])
     :figures {80 (render-condition! 80 best)
               160 (render-condition! 160 best)}
     :modelling-choices
     {:eig-model :beta-posterior :eig-prior pattern/beta-prior
      :lambda 0.55 :mu 0.1 :tau 0.3
      :late-classification {:paired-window (:late-window base-config)
                            :threshold-sems 2.0}
      :best-coefficient [:max-final-entropy :max-final-kind-count]
      :no-cell-memory true
      :forbidden-eig-inputs [:damage :reach :band :entropy :kind-count
                             :global-statistic]}}))

(defn fmt [summary] (harness/fmt summary))

(defn markdown [result]
  (str "# EIG c=5 neighbourhood: horizon and size — Slice 6d\n\n"
       "Measurements only. N=60, horizon=24000; lambda=0.55, mu=0.1, tau=0.3.\n\n"
       "## Throughput plan\n\n```clojure\n" (pr-str (:throughput-plan result)) "\n```\n\n"
       "## Width 80 long horizon\n\n"
       "| c | dominant runs | kinds @24000 | entropy @24000 | late kinds | class | late entropy | class | genotype rules | exotype spatial | genotype spatial | P damage | G damage | X damage |\n"
       "|---:|---|---:|---:|---:|---|---:|---|---:|---:|---:|---:|---:|---:|\n"
       (apply str
              (for [[coefficient row] (:width-80 result)
                    :let [end (get-in row [:trajectory 24000])]]
                (format (str "| %.3f | `%s` | %s | %s | %s | %s | %s | %s | "
                             "%s | %s | %s | %s | %s | %s |\n")
                        coefficient (pr-str (:final-dominant-kind row))
                        (fmt (:kind-count end)) (fmt (:entropy end))
                        (fmt (get-in row [:late-assessment :kind-count]))
                        (name (get-in row [:late-assessment :kind-count :classification]))
                        (fmt (get-in row [:late-assessment :entropy]))
                        (name (get-in row [:late-assessment :entropy :classification]))
                        (fmt (:genotype-rule-count row))
                        (fmt (:spatial-autocorrelation end))
                        (fmt (:genotype-spatial-autocorrelation end))
                        (fmt (get-in row [:damage :phenotype]))
                        (fmt (get-in row [:damage :genotype]))
                        (fmt (get-in row [:damage :exotype])))))
       "\n## Size invariance at selected coefficient\n\n"
       "Selected by maximum final entropy, then final kind count: c="
       (get-in result [:best-coefficient-selection :coefficient]) ".\n\n"
       "| width | dominant runs | final distribution | kinds | entropy | genotype rules | exotype spatial | genotype spatial |\n"
       "|---:|---|---|---:|---:|---:|---:|---:|\n"
       (apply str
              (for [[width row] (:size-invariance result)
                    :let [end (get-in row [:trajectory 24000])]]
                (format "| %d | `%s` | `%s` | %s | %s | %s | %s | %s |\n"
                        width (pr-str (:final-dominant-kind row))
                        (pr-str (:counts end)) (fmt (:kind-count end))
                        (fmt (:entropy end)) (fmt (:genotype-rule-count row))
                        (fmt (:spatial-autocorrelation end))
                        (fmt (:genotype-spatial-autocorrelation end)))))
       "\n## Full trajectories and late-window distributions\n\n```clojure\n"
       (pr-str
        (into (sorted-map)
              (for [[coefficient row] (:width-80 result)]
                [coefficient {:trajectory (:trajectory row)
                              :peak (:peak row)
                              :late-distribution (:late-distribution row)}])))
       "\n```\n\n## Determinism, panels, and modelling choices\n\n"
       "Full-history panels sample every 21st state (about 20 steps per row); early and late panels retain every state in their 600-step windows.\n\n```clojure\n"
       (pr-str (select-keys result [:determinism :figures :modelling-choices]))
       "\n```\n\nNo scientific verdict is made here.\n"))

(defn -main [& _]
  (let [result (experiment)]
    (spit (:edn paths) (str (pr-str result) "\n"))
    (spit (:md paths) (markdown result))
    (println :wrote (:edn paths) (:md paths))))

(apply -main *command-line-args*)
(shutdown-agents)
