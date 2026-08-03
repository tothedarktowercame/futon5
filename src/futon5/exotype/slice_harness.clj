(ns futon5.exotype.slice-harness
  "Shared, configuration-explicit apparatus for exotype experiment slices.

   This namespace deliberately owns no experiment configuration. Every operation
   receives its config and paths as arguments, so a script cannot change another
   script's run by mutating loaded globals."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.pattern-eig :as pattern]
            [futon5.mmca.render :as render]))

(defn initial-state [config arm seed]
  (ca/with-seed seed
    (let [width (:width config)
          genotype (vec (ca/random-sigil-string width))]
      {:arm :efe-full :pattern-arm arm :seed seed :time 0
       :lambda (:lambda config) :mu (:mu config) :tau (:tau config)
       :eig-model (get config :eig-model :legacy)
       :eig-coefficient (double (get config :eig-coefficient 1.0))
       :prevalence-radius (:prevalence-radius config)
       :genotype genotype :previous-genotype genotype
       :phenotype (apply str (repeatedly width #(if (< (ca/rnd) 0.5) \0 \1)))
       :exotypes (grid/initial-grid :heterogeneous-fixed width)})))

(defn mean [xs] (/ (reduce + 0.0 xs) (double (count xs))))

(defn sd [xs]
  (if (< (count xs) 2)
    0.0
    (let [m (mean xs)]
      (Math/sqrt
       (/ (reduce + 0.0 (map #(let [d (- (double %) m)] (* d d)) xs))
          (double (dec (count xs))))))))

(defn summary [xs]
  (let [xs (vec xs) s (sd xs)]
    {:mean (mean xs) :sd s :sem (/ s (Math/sqrt (double (count xs))))
     :n (count xs)}))

(defn median [xs]
  (let [v (vec (sort xs)) n (count v) i (quot n 2)]
    (if (odd? n)
      (double (nth v i))
      (/ (+ (double (nth v (dec i))) (double (nth v i))) 2.0))))

(defn difference [a b] (count (filter true? (map not= a b))))

(defn exotype-counts [xs]
  (let [f (frequencies xs)]
    (into (sorted-map) (for [k grid/exotype-kinds] [k (get f k 0)]))))

(defn entropy [counts]
  (let [n (double (reduce + (vals counts)))]
    (- (reduce + 0.0
               (for [x (vals counts) :when (pos? x) :let [p (/ x n)]]
                 (* p (Math/log p)))))))

(defn spatial [xs]
  (let [n (double (count xs))
        agree (/ (count (filter true? (map = xs (concat (rest xs) [(first xs)])))) n)
        expected (reduce + 0.0 (map #(let [p (/ % n)] (* p p))
                                    (vals (frequencies xs))))]
    (if (< expected 1.0) (/ (- agree expected) (- 1.0 expected)) 0.0)))

(defn checkpoint [state]
  (let [counts (exotype-counts (:exotypes state))]
    {:counts counts :kind-count (count (filter pos? (vals counts)))
     :entropy (entropy counts)
     :spatial-autocorrelation (spatial (:exotypes state))}))

(defn configured-checkpoint [config state]
  (cond-> (checkpoint state)
    (:include-genotype-spatial? config)
    (assoc :genotype-spatial-autocorrelation (spatial (:genotype state)))))

(defn stop-line [config]
  (let [states (for [seed (range (:seed-base config) (+ (:seed-base config) 8))]
                 (initial-state config :next-C-plus-eig seed))
        decisions (mapcat (fn [state]
                            (map #(pattern/cell-decision :next-C-plus-eig state %)
                                 (range (:width config)))) states)]
    (into (sorted-map)
          (for [term [:risk :ambiguity :conatus :eig]
                :let [ranges (map (fn [decision]
                                    (let [values (map term (:candidates decision))]
                                      (- (apply max values) (apply min values))))
                                  decisions)
                      all-values (mapcat #(map term (:candidates %)) decisions)]]
            [term {:decisions (count decisions)
                   :fraction-discriminating
                   (/ (count (filter #(> % 1.0e-12) ranges))
                      (double (count ranges)))
                   :within-decision-range (summary ranges)
                   :candidate-median (median all-values)
                   :candidate-max (apply max all-values)
                   :median-below-max? (< (median all-values) (apply max all-values))}]))))

(defn- advance [state n] (nth (iterate pattern/step-compact state) n))
(defn- flip-phenotype [state site]
  (update state :phenotype #(apply str (assoc (vec %) site
                                              (if (= \0 (nth % site)) \1 \0)))))
(defn- flip-genotype [state site]
  (update-in state [:genotype site]
             #(let [bits (vec (ca/bits-for (str %)))]
                (ca/sigil-for
                 (apply str (update bits 0 (fn [b] (if (= \0 b) \1 \0))))))))
(defn- flip-exotype [state site]
  (update-in state [:exotypes site]
             #(nth grid/exotype-kinds
                   (mod (inc (.indexOf grid/exotype-kinds %))
                        (count grid/exotype-kinds)))))

(defn damage [config state]
  (let [site (quot (:width config) 2)
        control (advance state (:damage-steps config))]
    (into (sorted-map)
          (for [[layer perturb key] [[:phenotype flip-phenotype :phenotype]
                                     [:genotype flip-genotype :genotype]
                                     [:exotype flip-exotype :exotypes]]]
            [layer (difference (key control)
                               (key (advance (perturb state site)
                                             (:damage-steps config))))]))))

(defn seed-run
  ([config arm seed] (seed-run config arm seed {}))
  ([config arm seed state-overrides]
   (let [wanted (set (:checkpoints config))
         initial (merge (initial-state config arm seed) state-overrides)]
     (loop [state initial time 0 cps (sorted-map 0 (configured-checkpoint config initial))
            changed-steps 0 changed-cells 0 phenotype-changes 0]
       (if (= time (:steps config))
         {:checkpoints cps :changed-steps changed-steps :changed-cells changed-cells
          :phenotype-activity (/ phenotype-changes
                                 (double (* (:width config) (:steps config))))
          :genotype-rule-count (count (distinct (:genotype state)))
          :damage (damage config state)}
         (let [next (pattern/step-compact state)
               changed (difference (:exotypes state) (:exotypes next))]
           (recur next (inc time)
                  (if (wanted (inc time))
                    (assoc cps (inc time) (configured-checkpoint config next)) cps)
                  (+ changed-steps (if (pos? changed) 1 0))
                  (+ changed-cells changed)
                  (+ phenotype-changes
                     (difference (:phenotype state) (:phenotype next))))))))))

(defn save-raw! [raw-path raw]
  (spit raw-path (str (pr-str raw) "\n")))

(defn load-raw [raw-path]
  (if (.exists (io/file raw-path))
    (edn/read-string (slurp raw-path))
    (sorted-map)))

(defn run-condition!
  [config raw-path raw condition seeds run-fn]
  (let [existing (get raw condition (sorted-map))
        missing (remove #(contains? existing %) seeds)
        pool (java.util.concurrent.Executors/newFixedThreadPool (:workers config))]
    (try
      (let [tasks (mapv (fn [seed]
                          [seed (.submit pool ^java.util.concurrent.Callable
                                         #(run-fn condition seed))])
                        missing)]
        (reduce (fn [result [seed future]]
                  (let [next (assoc-in result [condition seed]
                                       (.get ^java.util.concurrent.Future future))]
                    (save-raw! raw-path next)
                    (println :completed condition seed)
                    (flush)
                    next))
                raw tasks))
      (finally (.shutdown pool)))))

(defn checkpoint-summary [runs time]
  (let [rows (map #(get-in % [:checkpoints time]) runs)]
    (cond->
     {:kind-count (summary (map :kind-count rows))
      :entropy (summary (map :entropy rows))
      :spatial-autocorrelation (summary (map :spatial-autocorrelation rows))
      :counts (into (sorted-map)
                    (for [kind grid/exotype-kinds]
                      [kind (summary (map #(get-in % [:counts kind]) rows))]))}
      (contains? (first rows) :genotype-spatial-autocorrelation)
      (assoc :genotype-spatial-autocorrelation
             (summary (map :genotype-spatial-autocorrelation rows))))))

(defn condition-summary [config runs]
  {:trajectory (into (sorted-map)
                     (for [time (:checkpoints config)]
                       [time (checkpoint-summary runs time)]))
   :changed-steps (summary (map :changed-steps runs))
   :changed-cells (summary (map :changed-cells runs))
   :phenotype-activity (summary (map :phenotype-activity runs))
   :genotype-rule-count (summary (map :genotype-rule-count runs))
   :damage (into (sorted-map)
                 (for [layer [:phenotype :genotype :exotype]]
                   [layer (summary (map #(get-in % [:damage layer]) runs))]))})

(defn contrast-metrics [config]
  (let [endpoint (last (:checkpoints config))]
    {:kind-count #(get-in % [:checkpoints endpoint :kind-count])
     :entropy #(get-in % [:checkpoints endpoint :entropy])
     :spatial-autocorrelation #(get-in % [:checkpoints endpoint :spatial-autocorrelation])
     :changed-steps :changed-steps :changed-cells :changed-cells
     :phenotype-activity :phenotype-activity :genotype-rule-count :genotype-rule-count
     :phenotype-damage #(get-in % [:damage :phenotype])
     :genotype-damage #(get-in % [:damage :genotype])
     :exotype-damage #(get-in % [:damage :exotype])}))

(defn paired-contrast [left right metric]
  (let [row (summary (map #(- (double (metric %1)) (double (metric %2)))
                          (vals left) (vals right)))
        sem (:sem row)]
    (assoc row
           :sem-multiples (if (pos? sem)
                            (/ (Math/abs (:mean row)) sem)
                            (if (zero? (:mean row)) 0.0 Double/POSITIVE_INFINITY))
           :resolved-at-two-sem? (if (pos? sem)
                                   (>= (Math/abs (:mean row)) (* 2 sem))
                                   (not (zero? (:mean row)))))))

(def exotype-colours {:builder [54 162 235] :collapser [245 166 35]
                      :chaos [220 55 65] :identity [55 190 105]})

(defn genotype-colour [x]
  (let [h (bit-and 255 (hash x))]
    [h (bit-and 255 (* 3 h)) (bit-and 255 (* 7 h))]))

(defn triptych-pixels [states]
  (mapv (fn [state]
          (vec (concat (map genotype-colour (:genotype state)) [[255 255 255]]
                       (map #(if (= % \1) [245 245 245] [15 15 15])
                            (:phenotype state))
                       [[255 255 255]]
                       (map exotype-colours (:exotypes state)))))
        states))

(defn render-pixels! [pixels path comment]
  (let [ppm (str path ".ppm")]
    (io/make-parents path)
    (render/write-ppm! ppm pixels :comment comment)
    (let [{:keys [exit err]}
          (sh/sh "convert" ppm "-filter" "point" "-resize" "1200x1200!"
                 "-strip" path)]
      (when-not (zero? exit) (throw (ex-info "convert failed" {:error err}))))
    (.delete (io/file ppm))
    path))

(defn fmt [x]
  (format "%.4f (sd %.4f; sem %.4f)" (:mean x) (:sd x) (:sem x)))
