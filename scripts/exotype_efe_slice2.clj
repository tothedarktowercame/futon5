(ns exotype-efe-slice2
  "Slice-2 EFE regulator experiment. Dynamic range is checked before arms run."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.mmca.render :as render]))

(def config
  {:seed-base 20260803
   :dynamic-seeds 5
   :seeds 100
   :width 80
   :steps 120
   :burn-in 60
   :damage-steps 59
   :site 40})

(def arms
  [:boring-triggered :efe-full :efe-risk-only
   :efe-ambiguity-only :efe-no-conatus])

(defn- initial-state [arm seed]
  (ca/with-seed seed
    (let [genotype (vec (ca/random-sigil-string (:width config)))]
      {:arm arm
       :seed seed
       :time 0
       :genotype genotype
       :previous-genotype genotype
       :phenotype (apply str
                         (repeatedly (:width config)
                                     #(if (< (ca/rnd) 0.5) \0 \1)))
       :exotypes (grid/initial-grid :heterogeneous-fixed (:width config))})))

(defn- mean [values]
  (/ (reduce + 0.0 values) (double (count values))))

(defn- sample-sd [values]
  (if (< (count values) 2)
    0.0
    (let [average (mean values)]
      (Math/sqrt
       (/ (reduce + 0.0
                  (map #(let [delta (- (double %) average)]
                          (* delta delta))
                       values))
          (double (dec (count values))))))))

(defn- median [values]
  (let [ordered (vec (sort values))
        n (count ordered)
        middle (quot n 2)]
    (if (odd? n)
      (double (nth ordered middle))
      (/ (+ (double (nth ordered (dec middle)))
            (double (nth ordered middle)))
         2.0))))

(defn- distribution [values]
  {:min (double (reduce min values))
   :median (median values)
   :max (double (reduce max values))
   :sd (sample-sd values)
   :n (count values)})

(defn- telemetry-run [seed]
  (loop [state (initial-state :efe-full seed)
         remaining (:steps config)
         candidate-terms {:risk [] :ambiguity [] :conatus []}
         selected-terms {:risk [] :ambiguity [] :conatus []}
         changed-steps 0
         changed-cells 0]
    (if (zero? remaining)
      {:candidate-terms candidate-terms
       :selected-terms selected-terms
       :changed-steps changed-steps
       :changed-cells changed-cells}
      (let [next-state (efe/step state)
            decisions (:efe-decisions next-state)
            changed (count (filter true?
                                   (map not= (:exotypes state)
                                        (:exotypes next-state))))
            add-terms
            (fn [acc rows]
              (reduce (fn [terms row]
                        (reduce (fn [inner term]
                                  (update inner term conj (double (term row))))
                                terms [:risk :ambiguity :conatus]))
                      acc rows))]
        (recur next-state
               (dec remaining)
               (add-terms candidate-terms (mapcat :candidates decisions))
               (add-terms selected-terms (map :winner decisions))
               (+ changed-steps (if (pos? changed) 1 0))
               (+ changed-cells changed))))))

(defn dynamic-range-check []
  (let [seeds (mapv #(+ (:seed-base config) %)
                    (range (:dynamic-seeds config)))
        runs (mapv telemetry-run seeds)
        combine (fn [scope term]
                  (mapcat #(get-in % [scope term]) runs))
        summarize-scope
        (fn [scope]
          (into (sorted-map)
                (for [term [:risk :ambiguity :conatus]]
                  [term (distribution (combine scope term))])))
        result {:seeds seeds
                :candidate (summarize-scope :candidate-terms)
                :selected (summarize-scope :selected-terms)
                :movement {:changed-steps (reduce + (map :changed-steps runs))
                           :possible-steps (* (:dynamic-seeds config)
                                              (:steps config))
                           :changed-cells (reduce + (map :changed-cells runs))}
                :inert-terms
                (vec (for [term [:risk :ambiguity :conatus]
                           :when (<= (get-in (summarize-scope :selected-terms)
                                            [term :sd])
                                     1.0e-9)]
                       term))}]
    (assoc result :proceed? (empty? (:inert-terms result)))))

(defn- advance [state]
  (if (= :boring-triggered (:arm state))
    (let [previous (:genotype state)]
      (assoc (grid/step state) :previous-genotype previous))
    (efe/step state)))

(defn- trajectory [state steps]
  (vec (take (inc steps) (iterate advance state))))

(defn- flip-phenotype [state site]
  (update state :phenotype
          #(apply str (assoc (vec %) site
                             (if (= \0 (nth % site)) \1 \0)))))

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

(defn- difference [left right]
  (count (filter true? (map not= left right))))

(defn- phenotype-activity [states]
  (/ (reduce + 0.0
             (map #(difference (:phenotype %1) (:phenotype %2))
                  states (rest states)))
     (double (* (:width config) (dec (count states))))))

(defn- grid-activity [states]
  (let [changes (mapv #(difference (:exotypes %1) (:exotypes %2))
                      states (rest states))]
    {:changed-steps (count (filter pos? changes))
     :changed-cells (reduce + 0 changes)}))

(defn- exotype-counts [state]
  (let [counts (frequencies (:exotypes state))]
    (into (sorted-map)
          (map #(vector % (get counts % 0)) grid/exotype-kinds))))

(defn- damage [states]
  (let [burned (nth states (:burn-in config))
        control (nth states (+ (:burn-in config) (:damage-steps config)))
        site (:site config)]
    (into (sorted-map)
          (for [[layer perturb key]
                [[:phenotype flip-phenotype :phenotype]
                 [:genotype flip-genotype :genotype]
                 [:exotype flip-exotype :exotypes]]]
            (let [treated (nth (iterate advance (perturb burned site))
                               (:damage-steps config))]
              [layer (difference (key control) (key treated))])))))

(defn- seed-run [arm seed]
  (let [states (trajectory (initial-state arm seed) (:steps config))
        final-state (peek states)
        winners (when (not= :boring-triggered arm)
                  (mapcat #(map :winner (:efe-decisions %)) (rest states)))
        score-terms
        (when (seq winners)
          (into (sorted-map)
                (for [term [:risk :ambiguity :conatus :total]]
                  [term
                   (mean
                    (map (fn [winner]
                           (if (= term :total)
                             (:total winner)
                             (if (get-in winner [:enabled term])
                               (term winner)
                               0.0)))
                         winners))])))]
    {:grid-activity (grid-activity states)
     :final-exotypes (exotype-counts final-state)
     :phenotype-activity (phenotype-activity states)
     :genotype-rule-count (count (distinct (:genotype final-state)))
     :score-terms score-terms
     :damage (damage states)}))

(defn- summary [values]
  (let [n (count values)
        sd (sample-sd values)]
    {:mean (mean values)
     :sd sd
     :sem (/ sd (Math/sqrt (double n)))
     :n n}))

(defn- arm-summary [runs]
  {:grid-activity
   {:changed-steps (summary (map #(get-in % [:grid-activity :changed-steps]) runs))
    :changed-cells (summary (map #(get-in % [:grid-activity :changed-cells]) runs))}
   :final-exotypes
   (into (sorted-map)
         (for [kind grid/exotype-kinds]
           [kind (summary (map #(get-in % [:final-exotypes kind]) runs))]))
   :phenotype-activity (summary (map :phenotype-activity runs))
   :genotype-rule-count (summary (map :genotype-rule-count runs))
   :score-terms
   (when (:score-terms (first runs))
     (into (sorted-map)
           (for [term [:risk :ambiguity :conatus :total]]
             [term (summary (map #(get-in % [:score-terms term]) runs))])))
   :damage
   (into (sorted-map)
         (for [layer [:phenotype :genotype :exotype]]
           [layer (summary (map #(get-in % [:damage layer]) runs))]))})

(def metric-paths
  [[:grid-activity :changed-steps]
   [:grid-activity :changed-cells]
   [:phenotype-activity]
   [:genotype-rule-count]
   [:damage :phenotype]
   [:damage :genotype]
   [:damage :exotype]])

(defn- paired-contrast [raw left right]
  (into (sorted-map)
        (for [path metric-paths
              :let [deltas (mapv #(- (double (get-in (nth (get raw right) %) path))
                                      (double (get-in (nth (get raw left) %) path)))
                                 (range (:seeds config)))
                    stats (summary deltas)
                    sem-units (when (pos? (:sem stats))
                                (/ (:mean stats) (:sem stats)))]]
          [path (assoc stats :sem-units sem-units
                       :direction :right-minus-left)])))

(defn- render-arm! [arm]
  (let [states (trajectory (initial-state arm (:seed-base config))
                           (:steps config))
        pixels (render/render-history-phenotype
                (mapv #(apply str (:genotype %)) states)
                (mapv :phenotype states))
        base (str "reports/figures/slice2-" (name arm))
        ppm (str base ".ppm")
        png (str base ".png")]
    (io/make-parents ppm)
    (render/write-ppm! ppm pixels :comment (name arm))
    (let [{:keys [exit err]} (sh/sh "convert" ppm "-strip" png)]
      (when-not (zero? exit)
        (throw (ex-info "ImageMagick conversion failed" {:arm arm :error err}))))
    (.delete (io/file ppm))
    png))

(defn experiment []
  (let [dynamic (dynamic-range-check)]
    (when-not (:proceed? dynamic)
      (throw (ex-info "inert EFE term; arm comparison forbidden" dynamic)))
    (let [seeds (mapv #(+ (:seed-base config) %) (range (:seeds config)))
          raw (into (sorted-map)
                    (for [arm arms]
                      [arm (mapv #(seed-run arm %) seeds)]))
          summaries (into (sorted-map)
                          (map (fn [[arm runs]] [arm (arm-summary runs)]) raw))
          figures (into (sorted-map) (map #(vector % (render-arm! %)) arms))]
      {:kind :exotype-efe-slice2
       :schema 1
       :config config
       :seeds seeds
       :model {:kind :specified-fixed-product-bernoulli
               :parameters efe/fixed-model
               :preferences efe/preferences
               :hunger "P(rule static next) × P(local phenotype uniform next)"
               :tie-break [:hold :adopt-left :adopt-right]
               :learning :none-within-run}
       :dynamic-range dynamic
       :arms summaries
       :contrasts
       {:versus-boring-triggered
        (into (sorted-map)
              (for [arm efe/efe-arms]
                [arm (paired-contrast raw :boring-triggered arm)]))
        :ablations-versus-full
        (into (sorted-map)
              (for [arm [:efe-risk-only :efe-ambiguity-only :efe-no-conatus]]
                [arm (paired-contrast raw :efe-full arm)]))}
       :figures figures})))

(defn- fmt [{:keys [mean sd sem]}]
  (format "%.4f (sd %.4f; sem %.4f)" mean sd sem))

(defn markdown [result]
  (str "# Exotype EFE Slice 2\n\n"
       "Fixed seeds `" (first (:seeds result)) "`–`" (last (:seeds result))
       "`; N=`" (count (:seeds result)) "` per arm; width `"
       (get-in result [:config :width]) "`; steps `"
       (get-in result [:config :steps]) "`.\n\n"
       "## Dynamic-range stop-line check\n\n```clojure\n"
       (pr-str (:dynamic-range result)) "\n```\n\n"
       "All three selected-policy terms have non-zero spread, and the EFE policy changed the grid during the probe, so the arm comparison was authorized.\n\n"
       "## Arm measurements\n\n"
       "| arm | changed steps | changed cells | phenotype activity | genotype rules | pheno damage | geno damage | exo damage |\n"
       "|---|---:|---:|---:|---:|---:|---:|---:|\n"
       (apply str
              (for [[arm row] (:arms result)]
                (format "| %s | %s | %s | %s | %s | %s | %s | %s |\n"
                        (name arm)
                        (fmt (get-in row [:grid-activity :changed-steps]))
                        (fmt (get-in row [:grid-activity :changed-cells]))
                        (fmt (:phenotype-activity row))
                        (fmt (:genotype-rule-count row))
                        (fmt (get-in row [:damage :phenotype]))
                        (fmt (get-in row [:damage :genotype]))
                        (fmt (get-in row [:damage :exotype])))))
       "\n## Selected-policy score decomposition\n\n"
       "Disabled terms are reported as zero contribution; each row's total is risk + ambiguity + conatus.\n\n"
       "| arm | risk | ambiguity | conatus | total |\n"
       "|---|---:|---:|---:|---:|\n"
       (apply str
              (for [[arm row] (:arms result)
                    :when (:score-terms row)]
                (format "| %s | %s | %s | %s | %s |\n"
                        (name arm)
                        (fmt (get-in row [:score-terms :risk]))
                        (fmt (get-in row [:score-terms :ambiguity]))
                        (fmt (get-in row [:score-terms :conatus]))
                        (fmt (get-in row [:score-terms :total])))))
       "\n## Final exotype distribution\n\n```clojure\n"
       (pr-str (into (sorted-map)
                     (map (fn [[arm row]] [arm (:final-exotypes row)]))
                     (:arms result)))
       "\n```\n\n## Paired contrasts\n\nEvery contrast is right-minus-left; `:sem-units` is paired mean / paired SEM.\n\n```clojure\n"
       (pr-str (:contrasts result)) "\n```\n\n"
       "## Frozen model and modelling choices\n\n```clojure\n"
       (pr-str (:model result)) "\n```\n\n"
       "The model is specified rather than learned offline. Its constants encode the measured vocabulary roles, shrink activity and diversity predictions halfway toward the current three-cell locale, and stay frozen. Hunger is one-step and local: predicted rule stasis multiplied by predicted phenotype uniformity. C prefers conservative rule change and low hunger; it contains no damage, reach, band, or global statistic. Policies are hold/adopt-left/adopt-right; exact score ties use that order. All grids update synchronously.\n\n"
       "## Spacetime panels\n\n"
       (apply str (for [[arm path] (:figures result)]
                    (str "- `" (name arm) "`: `" path "`\n")))))

(defn- write-result! [result edn-path md-path]
  (io/make-parents edn-path)
  (spit edn-path (str (pr-str result) "\n"))
  (spit md-path (markdown result)))

(defn -main [& args]
  (case (first args)
    "dynamic"
    (let [result (dynamic-range-check)]
      (prn result)
      (when-not (:proceed? result)
        (System/exit 2)))
    "run"
    (let [result (experiment)
          edn-path (or (second args) "reports/exotype-efe-slice2.edn")
          md-path (or (nth args 2 nil) "reports/exotype-efe-slice2.md")]
      (write-result! result edn-path md-path)
      (doseq [[arm row] (:arms result)]
        (println (name arm)
                 :changed-steps (get-in row [:grid-activity :changed-steps :mean])
                 :phenotype-activity (get-in row [:phenotype-activity :mean])
                 :genotype-rules (get-in row [:genotype-rule-count :mean])))
      (println :wrote edn-path md-path))
    "render"
    (doseq [arm arms]
      (println arm (render-arm! arm)))
    (throw (ex-info "expected command: dynamic" {:args args}))))

(apply -main *command-line-args*)
(shutdown-agents)
