(ns exotype-efe-slice2b
  "Fixed-grid conatus-weight sweep following the Slice-2 endpoint result."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.mmca.render :as render]))

(def ^:private default-lambdas
  "The first sweep. claude-11 specified it dense near ZERO, on the guess that the
   transition sat close to 0 because a preferred-hunger of 0.05 already gave full chaos.
   That guess was WRONG: the flip is between 0.4 and 0.7, and this grid has no point
   inside that interval. Eight of its twelve points sit below 0.1 where nothing happens.
   So the original run establishes only that lambda<=0.4 gives :identity and
   lambda>=0.7 gives :chaos -- it does NOT establish the absence of an interior, because
   the interval where the system actually changes was never sampled."
  [0.0 0.001 0.002 0.005 0.01 0.02 0.05 0.1 0.2 0.4 0.7 1.0])

(defn- parse-lambdas
  "Override the sweep grid via SLICE2B_LAMBDAS (comma-separated). Defaults to the
   original vector so the committed artifact regenerates byte-identically."
  []
  (if-let [raw (System/getenv "SLICE2B_LAMBDAS")]
    (mapv #(Double/parseDouble (str/trim %)) (str/split raw #","))
    default-lambdas))

(def config
  {:seed-base 20260803 :seeds 100 :width 80 :steps 120
   :risk-probe-seeds 5
   :lambdas (parse-lambdas)
   :risk-preferences [0.15 0.4 0.6]})

(defn- initial-state [seed lambda & [rule-change-preference]]
  (ca/with-seed seed
    (let [genotype (vec (ca/random-sigil-string (:width config)))]
      (cond-> {:arm :efe-full :seed seed :time 0 :lambda lambda
               :genotype genotype :previous-genotype genotype
               :phenotype (apply str
                                 (repeatedly (:width config)
                                             #(if (< (ca/rnd) 0.5) \0 \1)))
               :exotypes (grid/initial-grid :heterogeneous-fixed (:width config))}
        rule-change-preference
        (assoc :rule-change-preference rule-change-preference)))))

(defn- trajectory [state]
  (vec (take (inc (:steps config)) (iterate efe/step state))))

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
  (let [n (count values)
        sd (sample-sd values)]
    {:mean (mean values) :sd sd :sem (/ sd (Math/sqrt (double n))) :n n}))

(defn- median [values]
  (let [ordered (vec (sort values))
        n (count ordered)
        middle (quot n 2)]
    (if (odd? n)
      (double (nth ordered middle))
      (/ (+ (double (nth ordered (dec middle)))
            (double (nth ordered middle))) 2.0))))

(defn- distribution [values]
  (merge (summary values)
         {:min (double (reduce min values))
          :median (median values)
          :max (double (reduce max values))}))

(defn- difference [left right]
  (count (filter true? (map not= left right))))

(defn- grid-activity [states]
  (let [changes (mapv #(difference (:exotypes %1) (:exotypes %2))
                      states (rest states))]
    {:changed-steps (count (filter pos? changes))
     :changed-cells (reduce + 0 changes)}))

(defn- phenotype-activity [states]
  (/ (reduce + 0.0
             (map #(difference (:phenotype %1) (:phenotype %2))
                  states (rest states)))
     (double (* (:width config) (:steps config)))))

(defn- exotype-counts [state]
  (let [counts (frequencies (:exotypes state))]
    (into (sorted-map)
          (map #(vector % (get counts % 0)) grid/exotype-kinds))))

(defn- shannon-entropy [counts]
  (let [total (double (reduce + (vals counts)))]
    (- (reduce + 0.0
               (for [count (vals counts) :when (pos? count)
                     :let [p (/ count total)]]
                 (* p (Math/log p)))))))

(defn- seed-run [lambda seed]
  (let [states (trajectory (initial-state seed lambda))
        final-state (peek states)
        counts (exotype-counts final-state)
        activity (grid-activity states)]
    {:final-exotypes counts
     :kind-count (count (filter pos? (vals counts)))
     :entropy (shannon-entropy counts)
     :changed-steps (:changed-steps activity)
     :changed-cells (:changed-cells activity)
     :phenotype-activity (phenotype-activity states)
     :genotype-rule-count (count (distinct (:genotype final-state)))}))

(defn- lambda-summary [runs]
  {:final-exotypes
   (into (sorted-map)
         (for [kind grid/exotype-kinds]
           [kind (summary (map #(get-in % [:final-exotypes kind]) runs))]))
   :kind-count (summary (map :kind-count runs))
   :entropy (summary (map :entropy runs))
   :changed-steps (summary (map :changed-steps runs))
   :changed-cells (summary (map :changed-cells runs))
   :phenotype-activity (summary (map :phenotype-activity runs))
   :genotype-rule-count (summary (map :genotype-rule-count runs))})

(defn- paired-contrast [raw left right metric]
  (let [deltas (mapv #(- (double (metric (nth (get raw right) %)))
                          (double (metric (nth (get raw left) %))))
                     (range (:seeds config)))
        stats (summary deltas)]
    (assoc stats
           :direction :right-minus-left
           :sem-units (when (pos? (:sem stats))
                        (/ (:mean stats) (:sem stats)))
           :more-than-2-sem? (and (pos? (:sem stats))
                                  (> (/ (:mean stats) (:sem stats)) 2.0)))))

(defn- risk-spread [preference]
  (let [runs
        (mapv
         (fn [seed]
           (loop [state (initial-state seed 1.0 preference)
                  remaining (:steps config)
                  result []]
             (if (zero? remaining)
               result
               (let [next-state (efe/step state)]
                 (recur next-state (dec remaining)
                        (into result
                              (map #(get-in % [:winner :risk])
                                   (:efe-decisions next-state))))))))
         (map #(+ (:seed-base config) %)
              (range (:risk-probe-seeds config))))
        pooled (vec (mapcat identity runs))]
    {:pooled-selected-policies (distribution pooled)
     :seed-means (summary (map mean runs))}))

(defn- lambda-label [lambda]
  (str/replace (format "%.3f" lambda) "." "p"))

(defn- render-lambda! [label lambda]
  (let [states (trajectory (initial-state (:seed-base config) lambda))
        pixels (render/render-history-phenotype
                (mapv #(apply str (:genotype %)) states)
                (mapv :phenotype states))
        path (str "reports/figures/slice2b-" label
                  "-lambda-" (lambda-label lambda) ".png")
        ppm (str path ".ppm")]
    (io/make-parents path)
    (render/write-ppm! ppm pixels :comment (str "lambda=" lambda))
    (let [{:keys [exit err]} (sh/sh "convert" ppm "-strip" path)]
      (when-not (zero? exit)
        (throw (ex-info "ImageMagick conversion failed"
                        {:lambda lambda :error err}))))
    (.delete (io/file ppm))
    path))

(defn experiment []
  (let [seeds (mapv #(+ (:seed-base config) %) (range (:seeds config)))
        raw (into (sorted-map)
                  (for [lambda (:lambdas config)]
                    [lambda (mapv #(seed-run lambda %) seeds)]))
        summaries (into (sorted-map)
                        (map (fn [[lambda runs]]
                               [lambda (lambda-summary runs)])) raw)
        maximum-entropy (reduce max (map #(get-in summaries [% :entropy :mean])
                                        (:lambdas config)))
        maximizers (vec (filter #(= maximum-entropy
                                   (get-in summaries [% :entropy :mean]))
                                (:lambdas config)))
        peak-lambda (first maximizers)
        endpoints [0.0 1.0]
        endpoint-contrasts
        (into (sorted-map)
              (for [endpoint endpoints]
                [endpoint
                 {:entropy (paired-contrast raw endpoint peak-lambda :entropy)
                  :kind-count (paired-contrast raw endpoint peak-lambda
                                               :kind-count)}]))
        risk (into (sorted-map)
                   (for [preference (:risk-preferences config)]
                     [preference (risk-spread preference)]))
        figures {:lambda-0 (render-lambda! "endpoint-zero" 0.0)
                 :lambda-max (render-lambda! "diversity-max" peak-lambda)
                 :lambda-1 (render-lambda! "endpoint-one" 1.0)}]
    {:kind :exotype-efe-slice2b :schema 1 :config config :seeds seeds
     :lambdas summaries
     :diversity-maximum {:criterion :mean-shannon-entropy
                         :lambda peak-lambda
                         :maximizers maximizers
                         :maximum-mean maximum-entropy
                         :endpoint-contrasts endpoint-contrasts}
     :risk-preference-spread risk
     :figures figures
     :modelling-choices
     {:sweep :fixed-no-adaptive-refinement
      :entropy {:log-base :natural :zero-counts :omitted}
      :peak-tie-break :smallest-lambda
      :risk-probe {:seeds (:risk-probe-seeds config)
                   :scope :selected-policies
                   :default-unchanged true}
      :render-seed (:seed-base config)}}))

(defn- fmt [{:keys [mean sd sem]}]
  (format "%.4f (sd %.4f; sem %.4f)" mean sd sem))

(defn markdown [result]
  (str "# Exotype EFE Slice 2b — conatus-weight sweep\n\n"
       "Fixed, prerequested lambda grid `" (pr-str (get-in result [:config :lambdas]))
       "`; fixed seeds `" (first (:seeds result)) "`–`" (last (:seeds result))
       "`; N=`" (count (:seeds result)) "` per lambda. No adaptive refinement was performed.\n\n"
       "| lambda | kinds present | Shannon entropy | changed steps | changed cells | phenotype activity | genotype rules |\n"
       "|---:|---:|---:|---:|---:|---:|---:|\n"
       (apply str
              (for [[lambda row] (:lambdas result)]
                (format "| %.3f | %s | %s | %s | %s | %s | %s |\n"
                        lambda (fmt (:kind-count row)) (fmt (:entropy row))
                        (fmt (:changed-steps row)) (fmt (:changed-cells row))
                        (fmt (:phenotype-activity row))
                        (fmt (:genotype-rule-count row)))))
       "\n## Final exotype distributions\n\nEvery cell count is mean, sd, and sem across 100 seeds.\n\n```clojure\n"
       (pr-str (into (sorted-map)
                     (map (fn [[lambda row]] [lambda (:final-exotypes row)]))
                     (:lambdas result)))
       "\n```\n\n## Diversity maximum\n\n```clojure\n"
       (pr-str (:diversity-maximum result))
       "\n```\n\nThe maximisation criterion was mean Shannon entropy. `:maximizers` reports every tie; `:lambda` is the smallest tied value used only as the representative render. `:more-than-2-sem?` is the prerequested paired comparison with each endpoint.\n\n"
       "## Risk-preference diagnostic\n\nThe default remains `0.15`. Alternative values are diagnostics only. `:pooled-selected-policies` gives cell-step spread; `:seed-means` gives the directly comparable between-seed spread for five fixed 120-step seeds.\n\n```clojure\n"
       (pr-str (:risk-preference-spread result))
       "\n```\n\n## Modelling choices\n\n```clojure\n"
       (pr-str (:modelling-choices result))
       "\n```\n\n## Spacetime panels\n\n"
       (apply str (for [[label path] (:figures result)]
                    (str "- `" (name label) "`: `" path "`\n")))))

(defn- write-result! [result edn-path md-path]
  (io/make-parents edn-path)
  (spit edn-path (str (pr-str result) "\n"))
  (spit md-path (markdown result)))

(defn -main [& [edn-path md-path]]
  (let [result (experiment)
        edn-path (or edn-path "reports/exotype-efe-slice2b.edn")
        md-path (or md-path "reports/exotype-efe-slice2b.md")]
    (write-result! result edn-path md-path)
    (println :diversity-max (get-in result [:diversity-maximum :lambda]))
    (doseq [[lambda row] (:lambdas result)]
      (println lambda :kinds (get-in row [:kind-count :mean])
               :entropy (get-in row [:entropy :mean])))
    (println :wrote edn-path md-path)))

(apply -main *command-line-args*)
(shutdown-agents)
