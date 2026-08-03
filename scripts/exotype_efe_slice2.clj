(ns exotype-efe-slice2
  "Slice-2 EFE regulator experiment. Dynamic range is checked before arms run."
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]))

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

(defn -main [& args]
  (case (first args)
    "dynamic"
    (let [result (dynamic-range-check)]
      (prn result)
      (when-not (:proceed? result)
        (System/exit 2)))
    (throw (ex-info "expected command: dynamic" {:args args}))))

(apply -main *command-line-args*)
