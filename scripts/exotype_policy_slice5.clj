(ns exotype-policy-slice5
  "Slice 5: resumable pseudocount-floor x conatus-weight experiment."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.policy-expansion :as expansion]
            [futon5.mmca.render :as render])
  (:import [java.nio.file CopyOption Files StandardCopyOption]))

(def config
  {:seed-base 20260803
   ;; Measured 51.02 s/run at width 40. Four workers make N=4 one wave;
   ;; N=8 would be two waves and exceed the Agency cap with this 25-cell grid.
   :seeds 4
   :width 40
   :steps 6000
   :workers 4
   :benchmark-seconds-per-run 51.02
   :tau 0.3
   :prevalence-radius 1
   :mus [0.0 0.03 0.1 0.3 1.0]
   :lambdas [0.4 0.525 0.55 0.575 0.7]
   :checkpoints [0 120 600 1200 3000 4800 6000]
   :plateau-start 4800})

(def raw-path "reports/exotype-policy-slice5.raw.edn")
(def partial-path "reports/exotype-policy-slice5.partial.edn")
(def edn-path "reports/exotype-policy-slice5.edn")
(def md-path "reports/exotype-policy-slice5.md")

(defn- seeds []
  (mapv #(+ (:seed-base config) %) (range (:seeds config))))

(defn- initial-state [mu lambda seed]
  (ca/with-seed seed
    (let [width (:width config)
          genotype (vec (ca/random-sigil-string width))]
      {:arm :efe-full :seed seed :time 0
       :lambda lambda :tau (:tau config) :mu mu
       :prevalence-radius (:prevalence-radius config)
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
                  (map #(let [delta (- (double %) average)] (* delta delta))
                       values))
          (double (dec (count values))))))))

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

(defn- categorical-spatial [values]
  (let [n (double (count values))
        shifted (concat (rest values) [(first values)])
        agreement (/ (count (filter true? (map = values shifted))) n)
        expected (reduce + 0.0
                         (map #(let [p (/ % n)] (* p p))
                              (vals (frequencies values))))
        corrected (if (< expected 1.0)
                    (/ (- agreement expected) (- 1.0 expected)) 0.0)]
    {:neighbour-agreement agreement :autocorrelation corrected}))

(defn- checkpoint [state]
  (let [counts (exotype-counts (:exotypes state))
        spatial (categorical-spatial (:exotypes state))]
    {:counts counts
     :kind-count (count (filter pos? (vals counts)))
     :entropy (entropy counts)
     :neighbour-agreement (:neighbour-agreement spatial)
     :spatial-autocorrelation (:autocorrelation spatial)}))

(defn seed-run [mu lambda seed]
  (let [wanted (set (:checkpoints config))
        initial (initial-state mu lambda seed)]
    (loop [state initial time 0
           checkpoints (sorted-map 0 (checkpoint initial))
           changed-steps 0 changed-cells 0 phenotype-changes 0]
      (if (= time (:steps config))
        {:checkpoints checkpoints
         :changed-steps changed-steps
         :changed-cells changed-cells
         :phenotype-activity
         (/ phenotype-changes (double (* (:width config) (:steps config))))
         :genotype-rule-count (count (distinct (:genotype state)))}
        (let [next-state (expansion/step state)
              next-time (inc time)
              changed (difference (:exotypes state) (:exotypes next-state))
              phenotype-changed
              (difference (:phenotype state) (:phenotype next-state))]
          (recur next-state next-time
                 (if (wanted next-time)
                   (assoc checkpoints next-time (checkpoint next-state))
                   checkpoints)
                 (+ changed-steps (if (pos? changed) 1 0))
                 (+ changed-cells changed)
                 (+ phenotype-changes phenotype-changed)))))))

(defn- atomic-spit! [path value]
  (let [target (.toPath (io/file path))
        temporary (.toPath (io/file (str path ".tmp")))]
    (io/make-parents path)
    (spit (.toFile temporary) (str (pr-str value) "\n"))
    (Files/move temporary target
                (into-array CopyOption
                            [StandardCopyOption/REPLACE_EXISTING
                             StandardCopyOption/ATOMIC_MOVE]))))

(defn- load-raw []
  (if (.exists (io/file raw-path))
    (edn/read-string (slurp raw-path))
    (sorted-map)))

(defn- condition-runs [raw mu lambda]
  (get-in raw [mu lambda] (sorted-map)))

(defn- run-condition! [raw mu lambda]
  (let [existing (condition-runs raw mu lambda)
        missing (remove #(contains? existing %) (seeds))
        pool (java.util.concurrent.Executors/newFixedThreadPool
              (:workers config))]
    (try
      (let [tasks (mapv (fn [seed]
                          [seed (.submit pool
                                         ^java.util.concurrent.Callable
                                         (fn [] (seed-run mu lambda seed)))])
                        missing)]
        (reduce (fn [current [seed future]]
                  (let [next-raw
                        (assoc-in current [mu lambda seed]
                                  (.get ^java.util.concurrent.Future future))]
                    (atomic-spit! raw-path next-raw)
                    (println :completed :mu mu :lambda lambda :seed seed)
                    (flush)
                    next-raw))
                raw tasks))
      (finally (.shutdown pool)))))

(defn- checkpoint-summary [runs time]
  (let [rows (map #(get-in % [:checkpoints time]) runs)]
    {:kind-count (summary (map :kind-count rows))
     :entropy (summary (map :entropy rows))
     :neighbour-agreement (summary (map :neighbour-agreement rows))
     :spatial-autocorrelation (summary (map :spatial-autocorrelation rows))
     :counts (into (sorted-map)
                   (for [kind grid/exotype-kinds]
                     [kind (summary (map #(get-in % [:counts kind]) rows))]))}))

(defn- paired-delta [runs metric]
  (let [start (:plateau-start config)
        end (:steps config)
        values (mapv #(- (double (get-in % [:checkpoints end metric]))
                           (double (get-in % [:checkpoints start metric])))
                     runs)
        stats (summary values)
        sem-units (when (pos? (:sem stats)) (/ (:mean stats) (:sem stats)))
        classification
        (cond
          (and (zero? (:mean stats)) (zero? (:sem stats))) :exact-plateau
          (and sem-units (< sem-units -2.0)) :decay
          (and sem-units (> sem-units 2.0)) :growth
          :else :unresolved)]
    (assoc stats :sem-units sem-units :classification classification
           :from start :to end)))

(defn- condition-summary [runs]
  {:trajectory (into (sorted-map)
                     (for [time (:checkpoints config)]
                       [time (checkpoint-summary runs time)]))
   :plateau-assessment
   {:kind-count (paired-delta runs :kind-count)
    :entropy (paired-delta runs :entropy)}
   :plateau-exotype-distribution
   (get-in (checkpoint-summary runs (:steps config)) [:counts])
   :changed-steps (summary (map :changed-steps runs))
   :changed-cells (summary (map :changed-cells runs))
   :phenotype-activity (summary (map :phenotype-activity runs))
   :genotype-rule-count (summary (map :genotype-rule-count runs))
   :spatial-autocorrelation
   (get-in (checkpoint-summary runs (:steps config))
           [:spatial-autocorrelation])})

(defn- result-from-raw [raw]
  (let [conditions
        (into (sorted-map)
              (for [mu (:mus config)]
                [mu (into (sorted-map)
                          (for [lambda (:lambdas config)
                                :let [by-seed (condition-runs raw mu lambda)]
                                :when (= (count by-seed) (:seeds config))]
                            [lambda (condition-summary
                                     (mapv by-seed (seeds)))]))]))]
    {:kind :exotype-policy-slice5
     :schema 1
     :config config
     :seeds (seeds)
     :conditions conditions
     :entropy-maxima
     (into (sorted-map)
           (for [[mu lambdas] conditions :when (= (count lambdas)
                                                   (count (:lambdas config)))
                 :let [maximum (reduce max
                                       (map #(get-in % [:trajectory 6000
                                                        :entropy :mean])
                                            (vals lambdas)))]]
             [mu {:lambdas
                  (vec (for [[lambda row] lambdas
                             :when (= maximum
                                      (get-in row [:trajectory 6000
                                                   :entropy :mean]))]
                         lambda))
                  :mean-entropy maximum}]))
     :modelling-choices
     {:policy-space :hold-plus-one-policy-per-exotype-kind
      :habit-prior "local count(candidate kind) + mu"
      :innovation :inside-scored-policy-relation-only
      :tau (:tau config)
      :neighbourhood {:topology :circular :radius 1 :includes-self true}
      :sampling :stateless-seed-time-cell
      :updates :synchronous
      :plateau-assessment {:window [(:plateau-start config) (:steps config)]
                           :resolved-threshold-sems 2.0}
      :spatial :chance-corrected-circular-neighbour-agreement
      :scope-cut {:measured-seconds-per-run 51.02
                  :width-cut-from 80 :width-used 40
                  :seeds-cut-from 100 :seeds-used 4
                  :grid-points-retained 25
                  :reason :fit-agency-cap}}}))

(def ^:private exotype-colours
  {:builder [54 162 235] :collapser [245 166 35]
   :chaos [220 55 65] :identity [55 190 105]})

(defn- mu-label [mu]
  (str/replace (format "%.2f" mu) "." "p"))

(defn- lambda-label [lambda]
  (str/replace (format "%.3f" lambda) "." "p"))

(defn- render-condition! [mu lambda]
  (let [states (take (inc (:steps config))
                     (iterate expansion/step
                              (initial-state mu lambda (:seed-base config))))
        pixels (mapv #(mapv exotype-colours (:exotypes %)) states)
        path (format "reports/figures/slice5-mu-%s-lambda-%s.png"
                     (mu-label mu) (lambda-label lambda))
        ppm (str path ".ppm")]
    (io/make-parents path)
    (render/write-ppm! ppm pixels :comment (str "slice5 mu=" mu " lambda=" lambda))
    (let [{:keys [exit err]} (sh/sh "convert" ppm "-filter" "point"
                                    "-resize" "800x1200!" "-strip" path)]
      (when-not (zero? exit)
        (throw (ex-info "Image conversion failed" {:path path :error err}))))
    (.delete (io/file ppm))
    path))

(defn- render-figures! []
  (let [pool (java.util.concurrent.Executors/newFixedThreadPool
              (:workers config))]
    (try
      (into (sorted-map)
            (for [mu (:mus config)
                  :let [future (.submit pool
                                        ^java.util.concurrent.Callable
                                        (fn [] (render-condition! mu 0.55)))]]
              [mu (.get ^java.util.concurrent.Future future)]))
      (finally (.shutdown pool)))))

(defn- determinism-check []
  (let [run #(pr-str (seed-run 0.1 0.55 (:seed-base config)))
        left (future (run))
        right (future (run))
        left-value @left right-value @right]
    {:condition {:mu 0.1 :lambda 0.55 :seed (:seed-base config)}
     :comparison :complete-seed-run-pr-str
     :byte-identical? (= left-value right-value)
     :hash (format "%08x" (bit-and 0xffffffff (hash left-value)))}))

(defn experiment []
  (let [raw
        (reduce (fn [current [mu lambda]]
                  (let [next-raw (run-condition! current mu lambda)
                        partial (result-from-raw next-raw)]
                    (atomic-spit! partial-path partial)
                    next-raw))
                (load-raw)
                (for [mu (:mus config) lambda (:lambdas config)] [mu lambda]))]
    (assoc (result-from-raw raw)
           :determinism (determinism-check)
           :figures (render-figures!))))

(defn- fmt [{:keys [mean sd sem]}]
  (format "%.4f (sd %.4f; sem %.4f)" mean sd sem))

(defn markdown [result]
  (str "# Expanded exotype-policy Slice 5\n\n"
       "Fixed tau `0.3`; width `40`; horizon `6000`; N=`4` per condition. A measured width-40 run cost `51.02s`; width was cut from 80 and N from 100 so the complete 25-condition grid fits the Agency cap. All five mu values and all five lambda values were retained.\n\n"
       "## Diversity trajectory\n\n"
       "Each cell is `kind-count / Shannon entropy` (mean; full SD/SEM in EDN).\n\n"
       "| mu | lambda | t=0 | t=120 | t=600 | t=1200 | t=3000 | t=4800 | t=6000 |\n"
       "|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n"
       (apply str
              (for [[mu lambdas] (:conditions result)
                    [lambda row] lambdas]
                (str "| " mu " | " lambda " | "
                     (str/join " | "
                               (for [time (:checkpoints config)]
                                 (format "%.2f / %.4f"
                                         (get-in row [:trajectory time
                                                      :kind-count :mean])
                                         (get-in row [:trajectory time
                                                      :entropy :mean]))))
                     " |\n")))
       "\n## Late-window plateau assessment\n\n"
       "Deltas are paired from t=4800 to t=6000.\n\n"
       "| mu | lambda | kind delta | kind class | entropy delta | entropy class |\n"
       "|---:|---:|---:|---|---:|---|\n"
       (apply str
              (for [[mu lambdas] (:conditions result)
                    [lambda row] lambdas]
                (format "| %.2f | %.3f | %s | %s | %s | %s |\n"
                        mu lambda
                        (fmt (get-in row [:plateau-assessment :kind-count]))
                        (name (get-in row [:plateau-assessment :kind-count
                                           :classification]))
                        (fmt (get-in row [:plateau-assessment :entropy]))
                        (name (get-in row [:plateau-assessment :entropy
                                           :classification])))))
       "\n## Endpoint activity and spatial structure\n\n"
       "| mu | lambda | changed steps | changed cells | phenotype activity | genotype rules | exotype autocorrelation |\n"
       "|---:|---:|---:|---:|---:|---:|---:|\n"
       (apply str
              (for [[mu lambdas] (:conditions result)
                    [lambda row] lambdas]
                (format "| %.2f | %.3f | %s | %s | %s | %s | %s |\n"
                        mu lambda (fmt (:changed-steps row))
                        (fmt (:changed-cells row)) (fmt (:phenotype-activity row))
                        (fmt (:genotype-rule-count row))
                        (fmt (:spatial-autocorrelation row)))))
       "\n## Plateau exotype distributions\n\n```clojure\n"
       (pr-str (into (sorted-map)
                     (for [[mu lambdas] (:conditions result)]
                       [mu (into (sorted-map)
                                 (for [[lambda row] lambdas]
                                   [lambda (:plateau-exotype-distribution row)]))])))
       "\n```\n\n## Entropy maxima by mu\n\n```clojure\n"
       (pr-str (:entropy-maxima result))
       "\n```\n\n## Determinism and modelling choices\n\n```clojure\n"
       (pr-str (select-keys result [:determinism :modelling-choices]))
       "\n```\n\n## Spacetime panels at lambda 0.55\n\n"
       (apply str (for [[mu path] (:figures result)]
                    (str "- mu `" mu "`: `" path "`\n")))
       "\nMeasurements only; no sustained-diversity or edge-of-chaos conclusion is declared.\n"))

(defn -main [& _]
  (let [result (experiment)]
    (atomic-spit! edn-path result)
    (spit md-path (markdown result))
    (println :determinism (:determinism result))
    (println :entropy-maxima (:entropy-maxima result))
    (println :wrote edn-path md-path)))

(apply -main *command-line-args*)
(shutdown-agents)
