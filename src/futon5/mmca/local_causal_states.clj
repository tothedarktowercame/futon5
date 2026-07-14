(ns futon5.mmca.local-causal-states
  "Local-causal-state reconstruction for discrete 1D spacetime fields.

   This module is substrate infrastructure shared by structural evaluators and
   generative-model consumers. It extracts finite-depth past/future light cones,
   estimates each past's conditional future morph, clusters statistically
   equivalent morphs without a fixed state count, and labels a causal-state
   field."
  (:import (org.apache.commons.math3.stat.inference ChiSquareTest)))

(defn- rectangular-grid [grid]
  (let [rows (mapv vec grid)
        widths (set (map count rows))]
    (when (or (empty? rows) (not= 1 (count widths)) (zero? (first widths)))
      (throw (ex-info "spacetime must be a non-empty rectangular grid"
                      {:rows (count rows) :widths widths})))
    rows))

(defn past-light-cone
  "Return the flattened radius-one past light cone at [T X].

   Depth one contributes the three cells at time T-1, depth two contributes
   five at T-2, and so on. Values are ordered nearest-time first and left to
   right within a depth."
  [grid t x depth]
  (vec (for [lag (range 1 (inc depth))
             sx (range (- x lag) (inc (+ x lag)))]
         (get-in grid [(- t lag) sx]))))

(defn future-light-cone
  "Return the flattened radius-one future light cone at [T X].

   Depth one contributes three cells at T+1; the present cell is not included."
  [grid t x depth]
  (vec (for [lag (range 1 (inc depth))
             sx (range (- x lag) (inc (+ x lag)))]
         (get-in grid [(+ t lag) sx]))))

(defn light-cone-samples
  "Extract aligned past/future cones at every valid point.

   Options: :past-depth, :future-depth, and optional :time-range [start end).
   Spatial margins are the maximum cone depth."
  [grid {:keys [past-depth future-depth time-range]
         :or {past-depth 2 future-depth 1}}]
  (let [grid (rectangular-grid grid)
        times (count grid)
        width (count (first grid))
        margin (max past-depth future-depth)
        valid-start past-depth
        valid-end (- times future-depth)
        [requested-start requested-end] (or time-range [valid-start valid-end])
        t-start (max valid-start requested-start)
        t-end (min valid-end requested-end)]
    (when (or (not (pos? past-depth)) (not (pos? future-depth)))
      (throw (ex-info "light-cone depths must be positive"
                      {:past-depth past-depth :future-depth future-depth})))
    (when (or (<= (- width (* 2 margin)) 0) (<= t-end t-start))
      (throw (ex-info "spacetime is too small for requested light cones"
                      {:times times :width width :past-depth past-depth
                       :future-depth future-depth :time-range time-range})))
    (mapv (fn [[t x]]
            {:t t :x x
             :past (past-light-cone grid t x past-depth)
             :future (future-light-cone grid t x future-depth)})
          (for [t (range t-start t-end)
                x (range margin (- width margin))]
            [t x]))))

(defn conditional-morphs
  "Return empirical future distributions keyed by exact past light cone."
  [samples]
  (reduce (fn [m {:keys [past future]}]
            (-> m
                (update-in [past :future-counts future] (fnil inc 0))
                (update-in [past :support] (fnil inc 0))))
          {}
          samples))

(defn morph-comparison
  "Chi-square homogeneity test for two empirical future-count maps.

   Returns a p-value and :equivalent? at ALPHA. Categories absent from both
   morphs are excluded. A one-category union is necessarily equivalent."
  [counts-a counts-b alpha]
  (let [categories (vec (sort-by pr-str (into (set (keys counts-a))
                                              (keys counts-b))))
        a (mapv #(long (get counts-a % 0)) categories)
        b (mapv #(long (get counts-b % 0)) categories)
        informative (keep-indexed (fn [idx _]
                                    (when (pos? (+ (nth a idx) (nth b idx))) idx))
                                  categories)
        a (mapv #(nth a %) informative)
        b (mapv #(nth b %) informative)
        p-value
        (cond
          (<= (count a) 1) 1.0
          (or (zero? (reduce + a)) (zero? (reduce + b))) 0.0
          :else
          (.chiSquareTestDataSetsComparison
           (ChiSquareTest.) (long-array a) (long-array b)))]
    {:p-value p-value
     :alpha alpha
     :equivalent? (>= p-value alpha)
     :categories (count a)}))

(defn- merge-counts [a b]
  (merge-with + a b))

(defn- compatible-state [states future-counts alpha]
  (->> states
       (map (fn [state]
              [state (morph-comparison future-counts
                                       (:future-counts state) alpha)]))
       (filter (comp :equivalent? second))
       (sort-by (fn [[state comparison]]
                  [(- (:p-value comparison)) (:id state)]))
       first))

(defn reconstruct-model
  "Reconstruct predictive local causal states from light-cone SAMPLES.

   Past morphs below MIN-SUPPORT remain unresolved. Supported morphs are
   processed in descending support order. Each is assigned to the statistically
   most compatible pooled state; when every existing state rejects homogeneity
   at ALPHA, a new state is split off. State count is inferred, never fixed."
  [samples {:keys [alpha min-support]
            :or {alpha 0.01 min-support 20}}]
  (let [morphs (conditional-morphs samples)
        ordered (sort-by (fn [[past {:keys [support]}]]
                           [(- support) (pr-str past)])
                         morphs)
        supported (filter #(>= (get-in % [1 :support]) min-support) ordered)
        unresolved (mapv first
                         (filter #(< (get-in % [1 :support]) min-support)
                                 ordered))
        result
        (reduce
         (fn [{:keys [states past->state decisions]} [past morph]]
           (if-let [[state comparison]
                    (compatible-state states (:future-counts morph) alpha)]
             (let [id (:id state)
                   updated (-> state
                               (update :members conj past)
                               (update :support + (:support morph))
                               (update :future-counts merge-counts
                                       (:future-counts morph)))]
               {:states (mapv #(if (= id (:id %)) updated %) states)
                :past->state (assoc past->state past id)
                :decisions (conj decisions
                                 {:past past :state id :action :merge
                                  :p-value (:p-value comparison)})})
             (let [id (count states)]
               {:states (conj states {:id id
                                      :members [past]
                                      :support (:support morph)
                                      :future-counts (:future-counts morph)})
                :past->state (assoc past->state past id)
                :decisions (conj decisions
                                 {:past past :state id :action :split})})))
         {:states [] :past->state {} :decisions []}
         supported)
        states
        (mapv (fn [state]
                (let [total (double (:support state))]
                  (assoc state :future-probabilities
                         (into {} (map (fn [[future count]]
                                        [future (/ count total)])
                                      (:future-counts state))))))
              (:states result))]
    {:states states
     :past->state (:past->state result)
     :unresolved-pasts unresolved
     :decisions (:decisions result)
     :params {:alpha alpha :min-support min-support}
     :sample-count (count samples)
     :distinct-pasts (count morphs)}))

(defn causal-state-field
  "Label every valid spacetime point using a reconstructed MODEL.

   Invalid margins and unsupported/unseen pasts are nil."
  [grid model {:keys [past-depth future-depth]
               :or {past-depth 2 future-depth 1}}]
  (let [grid (rectangular-grid grid)
        times (count grid)
        width (count (first grid))
        margin (max past-depth future-depth)
        past->state (:past->state model)]
    (mapv (fn [t]
            (mapv (fn [x]
                    (when (and (<= past-depth t)
                               (< t (- times future-depth))
                               (<= margin x)
                               (< x (- width margin)))
                      (get past->state
                           (past-light-cone grid t x past-depth))))
                  (range width)))
          (range times))))

(defn reconstruct
  "End-to-end local-causal-state reconstruction.

   :training-time-range limits model fitting; the returned field labels the
   whole valid grid, making held-out filtering possible for evaluator users."
  [grid {:keys [past-depth future-depth training-time-range alpha min-support]
         :or {past-depth 2 future-depth 1 alpha 0.01 min-support 20}
         :as opts}]
  (let [cone-opts {:past-depth past-depth
                   :future-depth future-depth
                   :time-range training-time-range}
        samples (light-cone-samples grid cone-opts)
        model (reconstruct-model samples {:alpha alpha
                                          :min-support min-support})
        field (causal-state-field grid model opts)]
    {:model (assoc model
                   :light-cones {:past-depth past-depth
                                 :future-depth future-depth
                                 :training-time-range training-time-range})
     :field field}))
