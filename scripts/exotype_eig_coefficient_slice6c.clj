(System/setProperty "futon5.exotype.pattern.library-only" "true")
(load-file "scripts/exotype_pattern_slice6.clj")
(in-ns 'exotype-pattern-slice6)
(require '[clojure.string :as str])

(def coefficients [0.0 0.5 1.0 1.2 1.35 1.45 1.475 1.5
                   1.6 1.75 2.0 2.5 3.0 5.0])
(def coefficient-config
  {:seed-base 20260803 :seeds 60 :width 80 :steps 6000 :workers 12
   :lambda 0.55 :mu 0.1 :tau 0.3 :prevalence-radius 1
   :eig-model :beta-posterior :damage-steps 59
   :checkpoints [0 120 600 1200 3000 6000]})
(alter-var-root #'config (constantly coefficient-config))
(alter-var-root #'raw-path
                (constantly "reports/exotype-eig-coefficient-slice6c.raw.edn"))

(def coefficient-edn-path "reports/exotype-eig-coefficient-slice6c.edn")
(def coefficient-md-path "reports/exotype-eig-coefficient-slice6c.md")

(defn coefficient-label [coefficient]
  (str/replace (format "%.3f" coefficient) "." "p"))

(defn initial-state-coefficient [coefficient seed]
  (assoc (initial-state :next-C-plus-eig seed)
         :eig-model :beta-posterior
         :eig-coefficient (double coefficient)))

(defn coefficient-stop-line [coefficient]
  (let [states (for [seed (range (:seed-base config) (+ (:seed-base config) 8))]
                 (initial-state-coefficient coefficient seed))
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
                   :within-decision-range (summary ranges)}]))))

(defn coefficient-seed-run [coefficient seed]
  (let [wanted (set (:checkpoints config))
        initial (initial-state-coefficient coefficient seed)]
    (loop [state initial time 0 cps (sorted-map 0 (checkpoint initial))
           changed-steps 0 changed-cells 0 phenotype-changes 0]
      (if (= time (:steps config))
        {:checkpoints cps :changed-steps changed-steps :changed-cells changed-cells
         :phenotype-activity (/ phenotype-changes
                                (double (* (:width config) (:steps config))))
         :genotype-rule-count (count (distinct (:genotype state)))
         :damage (damage state)}
        (let [next (pattern/step-compact state)
              changed (difference (:exotypes state) (:exotypes next))]
          (recur next (inc time)
                 (if (wanted (inc time)) (assoc cps (inc time) (checkpoint next)) cps)
                 (+ changed-steps (if (pos? changed) 1 0))
                 (+ changed-cells changed)
                 (+ phenotype-changes
                    (difference (:phenotype state) (:phenotype next)))))))))

(defn run-coefficient! [raw coefficient seeds]
  (let [existing (get raw coefficient (sorted-map))
        missing (remove #(contains? existing %) seeds)
        pool (java.util.concurrent.Executors/newFixedThreadPool (:workers config))]
    (try
      (let [tasks (mapv (fn [seed]
                          [seed (.submit pool ^java.util.concurrent.Callable
                                         #(coefficient-seed-run coefficient seed))])
                        missing)]
        (reduce (fn [r [seed future]]
                  (let [next (assoc-in r [coefficient seed]
                                       (.get ^java.util.concurrent.Future future))]
                    (save-raw! next)
                    (println :completed :coefficient coefficient :seed seed)
                    (flush)
                    next))
                raw tasks))
      (finally (.shutdown pool)))))

(defn peak-summary [runs]
  (let [times (:checkpoints config)
        rows (for [run runs
                   :let [peak-time (apply max-key
                                          #(get-in run [:checkpoints % :entropy]) times)]]
               {:time peak-time
                :entropy (get-in run [:checkpoints peak-time :entropy])
                :kind-count (get-in run [:checkpoints peak-time :kind-count])})]
    {:time (summary (map :time rows))
     :entropy (summary (map :entropy rows))
     :kind-count (summary (map :kind-count rows))
     :modal-time (first (apply max-key val (frequencies (map :time rows))))}))

(defn coefficient-summary [runs]
  (assoc (arm-summary runs)
         :peak (peak-summary runs)
         :final-dominant-kind
         (frequencies
          (map (fn [run]
                 (first (apply max-key val
                               (get-in run [:checkpoints 6000 :counts])))) runs))))

(defn coefficient-contrast [raw coefficient]
  (into (sorted-map)
        (for [[metric measure] contrast-metrics]
          [metric (paired-contrast (get raw coefficient) (get raw 0.0) measure)])))

(defn render-coefficient! [coefficient]
  (let [states (take (inc (:steps config))
                     (iterate pattern/step-compact
                              (initial-state-coefficient coefficient
                                                         (:seed-base config))))
        pixels (mapv (fn [s]
                       (vec (concat (map genotype-colour (:genotype s)) [[255 255 255]]
                                    (map #(if (= % \1) [245 245 245] [15 15 15])
                                         (:phenotype s)) [[255 255 255]]
                                    (map exotype-colours (:exotypes s))))) states)
        path (str "reports/figures/slice6c-c-" (coefficient-label coefficient)
                  "-triptych.png")
        ppm (str path ".ppm")]
    (clojure.java.io/make-parents path)
    (render/write-ppm! ppm pixels :comment (str "EIG coefficient " coefficient))
    (let [{:keys [exit err]} (clojure.java.shell/sh
                              "convert" ppm "-filter" "point"
                              "-resize" "1200x1200!" "-strip" path)]
      (when-not (zero? exit) (throw (ex-info "convert failed" {:error err}))))
    (.delete (clojure.java.io/file ppm)) path))

(defn best-sustained-coefficient [summaries]
  (first (apply max-key
                (fn [[_ row]] [(get-in row [:trajectory 6000 :entropy :mean])
                               (get-in row [:trajectory 6000 :kind-count :mean])])
                summaries)))

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
                        (fmt (:kind-count end)) (fmt (:entropy end))
                        (fmt (get-in row [:peak :kind-count]))
                        (fmt (get-in row [:peak :entropy]))
                        (get-in row [:peak :modal-time])
                        (fmt (:changed-steps row)) (fmt (:phenotype-activity row))
                        (fmt (:genotype-rule-count row))
                        (fmt (get-in row [:damage :phenotype]))
                        (fmt (get-in row [:damage :genotype]))
                        (fmt (get-in row [:damage :exotype]))
                        (fmt (:spatial-autocorrelation end)))))
       "\n## Full trajectories\n\n```clojure\n"
       (pr-str (into (sorted-map) (for [[c row] (:coefficients result)]
                                        [c (:trajectory row)])))
       "\n```\n\n## Paired contrasts from c=0\n\n```clojure\n"
       (pr-str (:contrasts result))
       "\n```\n\n## Determinism, parity, and figures\n\n```clojure\n"
       (pr-str (select-keys result [:determinism :c-one-parity :figures
                                    :modelling-choices]))
       "\n```\n\nNo scientific verdict is made here.\n"))

(defn coefficient-experiment []
  (let [seeds (range (:seed-base config) (+ (:seed-base config) (:seeds config)))
        raw (reduce #(run-coefficient! %1 %2 seeds) (load-raw) coefficients)
        summaries (into (sorted-map)
                        (for [coefficient coefficients]
                          [coefficient (coefficient-summary
                                        (vals (get raw coefficient)))]))
        best (best-sustained-coefficient summaries)
        figure-coefficients (distinct [0.0 1.475 best])]
    {:kind :exotype-eig-coefficient-slice6c :schema 1 :config config
     :coefficients summaries
     :stop-line (into (sorted-map) (for [c coefficients]
                                     [c (coefficient-stop-line c)]))
     :contrasts (into (sorted-map) (for [c coefficients :when (not (zero? c))]
                                         [c (coefficient-contrast raw c)]))
     :determinism (coefficient-determinism)
     :c-one-parity {:reference :slice6b-next-C-plus-eig
                    :equal? (= (get raw 1.0)
                               (:next-C-plus-eig
                                (clojure.edn/read-string
                                 (slurp "reports/exotype-pattern-slice6b.raw.edn"))))}
     :figures (into (sorted-map) (for [c figure-coefficients]
                                       [c (render-coefficient! c)]))
     :modelling-choices {:eig-model :beta-posterior
                         :eig-prior pattern/beta-prior
                         :coefficient-role :pure-multiplier
                         :forbidden-inputs [:damage :reach :band :entropy
                                            :kind-count :global-statistic]}}))

(defn -main-coefficient [& _]
  (let [result (coefficient-experiment)]
    (spit coefficient-edn-path (str (pr-str result) "\n"))
    (spit coefficient-md-path (coefficient-markdown result))
    (println :wrote coefficient-edn-path coefficient-md-path)))

(apply -main-coefficient *command-line-args*)
(shutdown-agents)
