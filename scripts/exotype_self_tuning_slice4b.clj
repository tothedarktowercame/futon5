(ns exotype-self-tuning-slice4b
  "Slice 4b: locally hunger-coupled per-cell lambda adaptation."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]
            [futon5.mmca.render :as render]))

(def config
  {:seed-base 20260803 :seeds 100 :width 80 :steps 6000
   :lambda-step-size 0.001 :hunger-target 0.05
   :damage-steps 59
   :checkpoints [0 120 600 1200 2400 3600 4800 6000]
   :arms tuning/arms})

(defn- fixed-lambda [arm]
  (case arm :fixed-0.55 0.55 :fixed-0.40 0.40 :fixed-0.70 0.70 nil))

(defn- initial-lambdas [arm seed width]
  (if-let [value (fixed-lambda arm)]
    (vec (repeat width value))
    (let [rng (java.util.Random. (+ 730201 (long seed)))]
      (mapv (fn [_] (.nextDouble rng)) (range width)))))

(defn- initial-state [arm seed]
  (ca/with-seed seed
    (let [width (:width config)
          genotype (vec (ca/random-sigil-string width))]
      {:arm :efe-full :self-tuning-arm arm :seed seed :time 0
       :lambda-step-size (:lambda-step-size config)
       :hunger-target (:hunger-target config)
       :lambdas (initial-lambdas arm seed width)
       :genotype genotype :previous-genotype genotype
       :phenotype (apply str
                         (repeatedly width #(if (< (ca/rnd) 0.5) \0 \1)))
       :exotypes (grid/initial-grid :heterogeneous-fixed width)})))

(defn- mean [values]
  (/ (reduce + 0.0 values) (double (count values))))

(defn- sample-sd [values]
  (if (< (count values) 2)
    0.0
    (let [average (mean values)]
      (Math/sqrt
       (/ (reduce + 0.0
                  (map #(let [d (- (double %) average)] (* d d)) values))
          (double (dec (count values))))))))

(defn- median [values]
  (let [ordered (vec (sort values))
        n (count ordered)
        middle (quot n 2)]
    (if (odd? n)
      (double (nth ordered middle))
      (/ (+ (double (nth ordered (dec middle)))
            (double (nth ordered middle))) 2.0))))

(defn- summary [values]
  (let [values (vec values) n (count values) sd (sample-sd values)]
    {:mean (mean values) :sd sd :sem (/ sd (Math/sqrt (double n))) :n n}))

(defn- difference [left right]
  (count (filter true? (map not= left right))))

(defn- exotype-counts [exotypes]
  (let [freqs (frequencies exotypes)]
    (into (sorted-map)
          (for [kind grid/exotype-kinds] [kind (get freqs kind 0)]))))

(defn- entropy [counts]
  (let [total (double (reduce + (vals counts)))]
    (- (reduce + 0.0
               (for [n (vals counts) :when (pos? n)
                     :let [p (/ n total)]]
                 (* p (Math/log p)))))))

(defn- numeric-lag-one [values]
  (let [values (mapv double values)
        average (mean values)
        centered (mapv #(- % average) values)
        denominator (reduce + 0.0 (map #(* % %) centered))
        numerator (reduce + 0.0
                          (map * centered (concat (rest centered)
                                                  [(first centered)])))]
    (if (pos? denominator) (/ numerator denominator) 0.0)))

(defn- categorical-spatial [values]
  (let [n (double (count values))
        observed (/ (count (filter true?
                                   (map = values
                                        (concat (rest values) [(first values)])))) n)
        expected (reduce + 0.0
                         (map #(let [p (/ % n)] (* p p))
                              (vals (frequencies values))))
        kappa (if (< expected 1.0)
                (/ (- observed expected) (- 1.0 expected))
                0.0)]
    {:neighbour-agreement observed :chance-corrected kappa}))

(defn- checkpoint [state]
  (let [counts (exotype-counts (:exotypes state))
        categorical (categorical-spatial (:exotypes state))]
    {:lambda-mean (mean (:lambdas state))
     :lambda-median (median (:lambdas state))
     :lambda-sd (sample-sd (:lambdas state))
     :lambda-autocorrelation (numeric-lag-one (:lambdas state))
     :exotype-autocorrelation (:chance-corrected categorical)
     :exotype-neighbour-agreement (:neighbour-agreement categorical)
     :kind-count (count (filter pos? (vals counts)))
     :entropy (entropy counts)
     :counts counts}))

(defn- flip-phenotype [state site]
  (update state :phenotype
          #(apply str (assoc (vec %) site (if (= \0 (nth % site)) \1 \0)))))

(defn- flip-genotype [state site]
  (update-in state [:genotype site]
             (fn [sigil]
               (let [bits (vec (ca/bits-for (str sigil)))]
                 (ca/sigil-for
                  (apply str (update bits 0 #(if (= \0 %) \1 \0))))))))

(defn- flip-exotype [state site]
  (update-in state [:exotypes site]
             (fn [value]
               (nth grid/exotype-kinds
                    (mod (inc (.indexOf grid/exotype-kinds value))
                         (count grid/exotype-kinds))))))

(defn- advance-n [state n]
  (nth (iterate tuning/step state) n))

(defn- damage [state]
  (let [site (quot (:width config) 2)
        control (advance-n state (:damage-steps config))]
    (into (sorted-map)
          (for [[layer perturb key]
                [[:phenotype flip-phenotype :phenotype]
                 [:genotype flip-genotype :genotype]
                 [:exotype flip-exotype :exotypes]]]
            [layer (difference (key control)
                               (key (advance-n (perturb state site)
                                               (:damage-steps config))))]))))

(defn- seed-run [arm seed]
  (let [wanted (set (:checkpoints config))
        initial (initial-state arm seed)]
    (loop [state initial
           time 0
           checkpoints (sorted-map 0 (checkpoint initial))
           changed-steps 0 changed-cells 0 phenotype-changes 0]
      (if (= time (:steps config))
        {:checkpoints checkpoints
         :final-lambdas (:lambdas state)
         :changed-steps changed-steps :changed-cells changed-cells
         :phenotype-activity
         (/ phenotype-changes (double (* (:width config) (:steps config))))
         :genotype-rule-count (count (distinct (:genotype state)))
         :damage (damage state)}
        (let [next-state (tuning/step state)
              next-time (inc time)
              changed (difference (:exotypes state) (:exotypes next-state))
              phe-changed (difference (:phenotype state) (:phenotype next-state))]
          (recur next-state next-time
                 (if (wanted next-time)
                   (assoc checkpoints next-time (checkpoint next-state))
                   checkpoints)
                 (+ changed-steps (if (pos? changed) 1 0))
                 (+ changed-cells changed)
                 (+ phenotype-changes phe-changed)))))))

(defn- histogram [values]
  (let [counts (frequencies
                (map #(min 19 (long (Math/floor (* 20.0 (double %))))) values))]
    (into (sorted-map)
          (for [index (range 20)]
            [(format "%.2f-%.2f" (/ index 20.0) (/ (inc index) 20.0))
             (get counts index 0)]))))

(defn- checkpoint-summary [runs time]
  (let [rows (map #(get-in % [:checkpoints time]) runs)]
    {:lambda-mean (summary (map :lambda-mean rows))
     :lambda-median (summary (map :lambda-median rows))
     :lambda-within-run-sd (summary (map :lambda-sd rows))
     :lambda-autocorrelation (summary (map :lambda-autocorrelation rows))
     :exotype-autocorrelation (summary (map :exotype-autocorrelation rows))
     :exotype-neighbour-agreement
     (summary (map :exotype-neighbour-agreement rows))
     :kind-count (summary (map :kind-count rows))
     :entropy (summary (map :entropy rows))
     :counts (into (sorted-map)
                   (for [kind grid/exotype-kinds]
                     [kind (summary (map #(get-in % [:counts kind]) rows))]))}))

(defn- arm-summary [runs]
  (let [pooled (vec (mapcat :final-lambdas runs))
        pooled-summary (assoc (summary pooled) :median (median pooled))
        run-means (map #(mean (:final-lambdas %)) runs)
        run-mean-summary (summary run-means)]
    {:lambda-distribution
     {:pooled pooled-summary
      :run-means run-mean-summary
      :histogram (histogram pooled)
      :distance-from-0.55-sd
      {:pooled (when (> (:sd pooled-summary) 1.0e-12)
                 (/ (Math/abs (- (:mean pooled-summary) 0.55))
                    (:sd pooled-summary)))
       :run-means (when (> (:sd run-mean-summary) 1.0e-12)
                    (/ (Math/abs (- (:mean run-mean-summary) 0.55))
                       (:sd run-mean-summary)))}}
     :trajectory (into (sorted-map)
                       (for [time (:checkpoints config)]
                         [time (checkpoint-summary runs time)]))
     :changed-steps (summary (map :changed-steps runs))
     :changed-cells (summary (map :changed-cells runs))
     :phenotype-activity (summary (map :phenotype-activity runs))
     :genotype-rule-count (summary (map :genotype-rule-count runs))
     :damage (into (sorted-map)
                   (for [layer [:phenotype :genotype :exotype]]
                     [layer (summary (map #(get-in % [:damage layer]) runs))]))}))

(def ^:private raw-checkpoint-path
  "reports/exotype-self-tuning-slice4b.raw.edn")

(defn- load-raw-checkpoint []
  (if (.exists (io/file raw-checkpoint-path))
    (edn/read-string (slurp raw-checkpoint-path))
    (sorted-map)))

(defn- save-raw-checkpoint! [raw]
  (spit raw-checkpoint-path (str (pr-str raw) "\n")))

(defn- run-arm! [arm seeds raw]
  (let [existing (get raw arm (sorted-map))
        missing (remove #(contains? existing %) seeds)
        pool (java.util.concurrent.Executors/newFixedThreadPool 4)]
    (try
      (let [tasks (mapv (fn [seed]
                          [seed (.submit pool
                                         ^java.util.concurrent.Callable
                                         (fn [] (seed-run arm seed)))])
                        missing)
            completed
            (reduce (fn [rows [seed future]]
                      (let [next-rows
                            (assoc rows seed
                                   (.get ^java.util.concurrent.Future future))]
                        (save-raw-checkpoint! (assoc raw arm next-rows))
                        (println :completed arm seed)
                        (flush)
                        next-rows))
                    existing tasks)]
        [(assoc raw arm completed) (mapv completed seeds)])
      (finally (.shutdown pool)))))

(defn- determinism-check []
  (let [left (pr-str (seed-run :hunger-coupled (:seed-base config)))
        right (pr-str (seed-run :hunger-coupled (:seed-base config)))]
    {:arm :hunger-coupled :seed (:seed-base config)
     :comparison :complete-seed-run-pr-str
     :byte-identical? (= left right)
     :hash (format "%08x" (bit-and 0xffffffff (hash left)))}))

(def ^:private exotype-colours
  {:builder [54 162 235] :collapser [245 166 35]
   :chaos [220 55 65] :identity [55 190 105]})

(defn- lambda-colour [x]
  (let [v (long (Math/round (* 255.0 (double x))))]
    [v (long (- 255 (Math/abs (- v 128)))) (- 255 v)]))

(defn- write-panel! [arm kind pixels]
  (let [path (str "reports/figures/slice4b-" (name arm) "-" (name kind) ".png")
        ppm (str path ".ppm")]
    (io/make-parents path)
    (render/write-ppm! ppm pixels :comment (str (name arm) " " (name kind)))
    (let [{:keys [exit err]} (sh/sh "convert" ppm "-filter" "point"
                                    "-resize" "800x1200!" "-strip" path)]
      (when-not (zero? exit)
        (throw (ex-info "Image conversion failed" {:path path :error err}))))
    (.delete (io/file ppm)) path))

(defn- render-arm! [arm]
  (let [states (vec (take (inc (:steps config))
                          (iterate tuning/step
                                   (initial-state arm (:seed-base config)))))]
    {:exotypes (write-panel! arm :exotypes
                             (mapv #(mapv exotype-colours (:exotypes %)) states))
     :lambdas (write-panel! arm :lambdas
                            (mapv #(mapv lambda-colour (:lambdas %)) states))}))

(defn- result-base [seeds]
  {:kind :exotype-self-tuning-slice4b :schema 1 :config config :seeds seeds
   :arms (sorted-map)
   :modelling-choices
   {:feedback-input :selected-policy-predicted-hunger
      :forbidden-feedback-inputs [:damage :reach :band :entropy :kind-count
                                  :global-statistic]
      :hunger-target-source :existing-efe-preference
      :initial-adaptive-lambdas :seeded-uniform-0-1
      :update {:type :sign-step :step-size (:lambda-step-size config)
               :clip [0.0 1.0] :synchronous true}
      :random-walk {:directions :equiprobable :same-step-size true
                    :sampling :stateless-seed-time-index}
      :damage {:start :step-6000 :delta (:damage-steps config)
               :site :midpoint :layers [:phenotype :genotype :exotype]}
      :spatial {:lambda :circular-lag-one-pearson
                :exotype :chance-corrected-circular-neighbour-agreement
                :zero-variance :reported-zero}
      :histogram {:bins 20 :width 0.05 :last-bin-includes-1 true}
    :distance-from-0.55 :absolute-distance-divided-by-sd}})

(defn- checkpoint-result! [result]
  (spit "reports/exotype-self-tuning-slice4b.partial.edn"
        (str (pr-str result) "\n")))

(def ^:private contrast-metrics
  {:lambda-mean #(mean (:final-lambdas %))
   :kind-count #(get-in % [:checkpoints 6000 :kind-count])
   :entropy #(get-in % [:checkpoints 6000 :entropy])
   :lambda-autocorrelation
   #(get-in % [:checkpoints 6000 :lambda-autocorrelation])
   :exotype-autocorrelation
   #(get-in % [:checkpoints 6000 :exotype-autocorrelation])
   :changed-steps :changed-steps
   :changed-cells :changed-cells
   :phenotype-activity :phenotype-activity
   :genotype-rule-count :genotype-rule-count
   :phenotype-damage #(get-in % [:damage :phenotype])
   :genotype-damage #(get-in % [:damage :genotype])
   :exotype-damage #(get-in % [:damage :exotype])})

(defn- paired-contrast [left right measure]
  (let [differences
        (map (fn [[left-row right-row]]
               (- (double (measure left-row))
                  (double (measure right-row))))
             (map vector (vals left) (vals right)))
        row (summary differences)
        sem (:sem row)]
    (assoc row
           :sem-multiples
           (if (pos? sem)
             (/ (Math/abs (:mean row)) sem)
             (if (zero? (:mean row)) 0.0 Double/POSITIVE_INFINITY))
           :resolved-at-two-sem?
           (if (pos? sem)
             (>= (Math/abs (:mean row)) (* 2.0 sem))
             (not (zero? (:mean row)))))))

(defn- paired-contrasts [raw]
  (into (sorted-map)
        (for [right [:random-walk :fixed-0.55]]
          [(keyword (str "hunger-coupled-minus-" (name right)))
           (into (sorted-map)
                 (for [[metric measure] contrast-metrics]
                   [metric (paired-contrast (:hunger-coupled raw)
                                            (get raw right) measure)]))])))

(defn experiment []
  (let [seeds (mapv #(+ (:seed-base config) %) (range (:seeds config)))
        [measured raw]
        (reduce (fn [[result raw] arm]
                  (println :starting arm)
                  (flush)
                  (let [[next-raw runs] (run-arm! arm seeds raw)
                        next-result (assoc-in result [:arms arm] (arm-summary runs))]
                    (checkpoint-result! next-result)
                    (println :completed arm)
                    (flush)
                    [next-result next-raw]))
                [(result-base seeds) (load-raw-checkpoint)] (:arms config))]
    (assoc measured
           :contrasts (paired-contrasts raw)
           :determinism (determinism-check)
           :figures (into (sorted-map)
                          (for [arm (:arms config)] [arm (render-arm! arm)])))))

(defn- fmt [x]
  (format "%.4f (sd %.4f; sem %.4f)" (:mean x) (:sd x) (:sem x)))

(defn markdown [result]
  (str "# Exotype self-tuning Slice 4b\n\n"
       "Fixed N=`100`, width=`80`, horizon=`6000`, hunger target=`0.05`, lambda step=`0.001`.\n\n"
       "| arm | lambda mean | lambda median | lambda sd | distance from 0.55 (sd) | kinds | entropy | lambda autocorr | exotype autocorr |\n"
       "|---|---:|---:|---:|---:|---:|---:|---:|---:|\n"
       (apply str
              (for [[arm row] (:arms result)
                    :let [end (get-in row [:trajectory 6000])
                          dist (:lambda-distribution row)]]
                (format "| %s | %s | %.4f | %.4f | %s | %s | %s | %s | %s |\n"
                        (name arm) (fmt (:run-means dist))
                        (get-in dist [:pooled :median]) (get-in dist [:pooled :sd])
                        (pr-str (get-in dist [:distance-from-0.55-sd :run-means]))
                        (fmt (:kind-count end)) (fmt (:entropy end))
                        (fmt (:lambda-autocorrelation end))
                        (fmt (:exotype-autocorrelation end)))))
       "\n## Activity and damage\n\n"
       "| arm | changed steps | changed cells | phenotype activity | genotype rules | phenotype damage | genotype damage | exotype damage |\n"
       "|---|---:|---:|---:|---:|---:|---:|---:|\n"
       (apply str
              (for [[arm row] (:arms result)]
                (format "| %s | %s | %s | %s | %s | %s | %s | %s |\n"
                        (name arm) (fmt (:changed-steps row))
                        (fmt (:changed-cells row)) (fmt (:phenotype-activity row))
                        (fmt (:genotype-rule-count row))
                        (fmt (get-in row [:damage :phenotype]))
                        (fmt (get-in row [:damage :genotype]))
                        (fmt (get-in row [:damage :exotype])))))
       "\n## Paired contrasts\n\n"
       "Differences are paired by seed. `resolved` means |mean| >= 2 SEM; no scientific verdict is inferred.\n\n"
       "| comparison | metric | mean difference | sd | sem | SEM multiples | resolved |\n"
       "|---|---|---:|---:|---:|---:|---|\n"
       (apply str
              (for [[comparison rows] (:contrasts result)
                    [metric row] rows]
                (format "| %s | %s | %.6f | %.6f | %.6f | %.3f | %s |\n"
                        (name comparison) (name metric) (:mean row) (:sd row)
                        (:sem row) (:sem-multiples row)
                        (if (:resolved-at-two-sem? row) "yes" "UNRESOLVED"))))
       "\n## Lambda histograms\n\n```clojure\n"
       (pr-str (into (sorted-map)
                     (for [[arm row] (:arms result)]
                       [arm (get-in row [:lambda-distribution :histogram])])))
       "\n```\n\n## Trajectories\n\n```clojure\n"
       (pr-str (into (sorted-map)
                     (for [[arm row] (:arms result)] [arm (:trajectory row)])))
       "\n```\n\n## Determinism and modelling choices\n\n```clojure\n"
       (pr-str (select-keys result [:determinism :modelling-choices]))
       "\n```\n\n## Spacetime panels\n\n"
       (apply str
              (for [[arm panels] (:figures result) [kind path] panels]
                (str "- `" (name arm) "` " (name kind) ": `" path "`\n")))
       "\nMeasurements only; interpretation is reserved for review.\n"))

(defn -main [& _]
  (let [result (experiment)]
    (spit "reports/exotype-self-tuning-slice4b.edn" (str (pr-str result) "\n"))
    (spit "reports/exotype-self-tuning-slice4b.md" (markdown result))
    (println :determinism (:determinism result))
    (doseq [[arm row] (:arms result)]
      (println arm :lambda (get-in row [:lambda-distribution :run-means :mean])
               :kinds (get-in row [:trajectory 6000 :kind-count :mean])
               :entropy (get-in row [:trajectory 6000 :entropy :mean])))
    (println :wrote "reports/exotype-self-tuning-slice4b.edn"
             "reports/exotype-self-tuning-slice4b.md")))

(apply -main *command-line-args*)
(shutdown-agents)
