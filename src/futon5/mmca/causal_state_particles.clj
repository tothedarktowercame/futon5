(ns futon5.mmca.causal-state-particles
  "Domain and particle objects extracted from a local-causal-state field.")

(defn- modal-value [xs]
  (->> (frequencies xs)
       (sort-by (fn [[value count]] [(- count) (pr-str value)]))
       ffirst))

(defn infer-domain-automaton
  "Infer the modal spatial and temporal successor for every causal state.

   The resulting phase automaton is learned only from TRAINING-RANGE. It is a
   regular-language filter over causal states, not raw CA symbols."
  [field {:keys [training-range margin]}]
  (let [[t-start t-end] training-range
        width (count (first field))
        transitions
        (for [t (range t-start (dec t-end))
              x (range margin (dec (- width margin)))
              :let [state (get-in field [t x])
                    right (get-in field [t (inc x)])
                    next (get-in field [(inc t) x])]
              :when (every? some? [state right next])]
          [state right next])]
    (into {}
          (for [[state rows] (group-by first transitions)]
            [state {:right (modal-value (map second rows))
                    :next (modal-value (map #(nth % 2) rows))
                    :support (count rows)}]))))

(defn domain-defect-field
  "Filter EVALUATION-RANGE through a learned causal-state phase automaton.

   True cells are defects: unresolved states or transitions not licensed by the
   state's modal spatial+temporal successors."
  [field automaton {:keys [evaluation-range margin]}]
  (let [[t-start t-end] evaluation-range
        width (count (first field))]
    (mapv
     (fn [t]
       (mapv
        (fn [x]
          (let [state (get-in field [t x])
                right (get-in field [t (inc x)])
                next (get-in field [(inc t) x])
                expected (get automaton state)]
            (not (and expected
                      (= right (:right expected))
                      (= next (:next expected))))))
        (range margin (dec (- width margin)))))
     (range t-start (dec t-end)))))

(defn- neighbors [height width [t x]]
  (for [dt [-1 0 1]
        dx [-1 0 1]
        :when (not (and (zero? dt) (zero? dx)))
        :let [nt (+ t dt) nx (+ x dx)]
        :when (and (<= 0 nt) (< nt height) (<= 0 nx) (< nx width))]
    [nt nx]))

(defn- flood-component [defects start]
  (let [height (count defects)
        width (count (first defects))]
    (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
           seen #{start}]
      (if (empty? queue)
        seen
        (let [point (peek queue)
              candidates (filter #(and (get-in defects %) (not (seen %)))
                                 (neighbors height width point))]
          (recur (into (pop queue) candidates) (into seen candidates)))))))

(defn particle-components
  "Extract connected defect objects with spacetime extent metadata."
  [defects]
  (let [points (set (for [t (range (count defects))
                          x (range (count (first defects)))
                          :when (get-in defects [t x])]
                      [t x]))]
    (loop [remaining points
           components []]
      (if (empty? remaining)
        components
        (let [component (flood-component defects (first remaining))
              ts (map first component)
              xs (map second component)
              t-min (apply min ts)
              t-max (apply max ts)
              x-min (apply min xs)
              x-max (apply max xs)]
          (recur (reduce disj remaining component)
                 (conj components
                       {:area (count component)
                        :time-span (inc (- t-max t-min))
                        :space-span (inc (- x-max x-min))
                        :bounds {:t [t-min t-max] :x [x-min x-max]}})))))))

(defn decompose
  "Extract domain coverage, persistent particle objects, and the conjunction.

   A particle is a connected causal-state defect persisting for >=2 time rows.
   particle-sparsity is one minus their field density, but is explicitly zero
   when no particle exists. The aggregate is domain-coverage x
   particle-sparsity; no fitted exponent or density threshold is used."
  [field {:keys [training-range evaluation-range margin] :as opts}]
  (let [automaton (infer-domain-automaton
                   field {:training-range training-range :margin margin})
        defects (domain-defect-field
                 field automaton {:evaluation-range evaluation-range
                                  :margin margin})
        total (* (count defects) (count (first defects)))
        defect-count (count (filter true? (mapcat identity defects)))
        domain-coverage (- 1.0 (/ defect-count (double total)))
        components (particle-components defects)
        particles (filterv #(>= (:time-span %) 2) components)
        particle-area (reduce + 0 (map :area particles))
        particle-density (/ particle-area (double total))
        particle-sparsity (if (seq particles)
                            (- 1.0 particle-density)
                            0.0)]
    {:automaton automaton
     :domain-coverage domain-coverage
     :defects {:count defect-count :density (/ defect-count (double total))}
     :particles {:count (count particles)
                 :density particle-density
                 :objects particles}
     :particle-sparsity particle-sparsity
     :score (* domain-coverage particle-sparsity)
     :protocol (select-keys opts [:training-range :evaluation-range :margin])}))
