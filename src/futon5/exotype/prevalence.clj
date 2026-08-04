(ns futon5.exotype.prevalence
  "Stateless, stigmergic policy priors for the exotype EFE regulator.

   E is read from the current exotype grid: a candidate exotype's prior is its
   prevalence in the cell's circular radius-r neighbourhood.  No counter,
   history, decay register, or other cell-local state is introduced."
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]))

(def default-radius 1)

(defn neighbourhood-indices
  "Circular indices from INDEX-RADIUS through INDEX+RADIUS, inclusive."
  [width index radius]
  {:pre [(pos? width) (nat-int? radius)]}
  (mapv #(mod % width) (range (- index radius) (inc (+ index radius)))))

(defn candidate-prevalence
  "Count CANDIDATE in the current circular neighbourhood."
  [exotypes index radius candidate]
  (let [indices (neighbourhood-indices (count exotypes) index radius)]
    (count (filter #(= candidate (nth exotypes %)) indices))))

(defn- policy-sources [width index]
  [{:policy :hold :source index}
   {:policy :adopt-left :source (mod (dec index) width)}
   {:policy :adopt-right :source (mod (inc index) width)}])

(defn- scored-candidates [arm state index radius]
  (let [exotypes (:exotypes state)
        observation (efe/local-observation state index)
        score-options (select-keys state [:lambda :rule-change-preference])]
    {:observation observation
     :candidates
     (mapv (fn [{:keys [source] :as policy}]
             (let [candidate (nth exotypes source)]
               (merge policy
                      (efe/score-policy arm candidate observation score-options)
                      {:prevalence
                       (candidate-prevalence exotypes index radius candidate)})))
           (policy-sources (count exotypes) index))}))

(defn- softmax-probabilities [candidates tau]
  (let [minimum (reduce min (map :total candidates))
        weights (mapv (fn [{:keys [total prevalence]}]
                        (* (double prevalence)
                           (Math/exp (/ (- minimum (double total))
                                        (double tau)))))
                      candidates)
        normalizer (reduce + 0.0 weights)]
    (when-not (pos? normalizer)
      (throw (ex-info "prevalence softmax has no positive mass"
                      {:tau tau :candidates candidates})))
    (mapv #(/ % normalizer) weights)))

(defn- draw-for
  "One deterministic, stateless draw for a run/time/cell coordinate."
  [{:keys [seed time exotypes]} index]
  (let [width (count exotypes)
        draw-seed (+ (long (or seed 0))
                     (* 1000003 (long (or time 0)))
                     (* 9176 (long width))
                     (long index))]
    ;; mix-seed: the per-cell stride here is 1, so raw seeds give every cell the
    ;; same first draw (measured 1.06 distinct outcomes of 2). TN-baldwin-reboot 2.
    (.nextDouble (java.util.Random. (ca/mix-seed draw-seed)))))

(defn- sample-candidate [candidates probabilities draw]
  (loop [index 0
         cumulative 0.0]
    (let [last? (= index (dec (count candidates)))
          next-cumulative (+ cumulative (nth probabilities index))]
      (if (or last? (< draw next-cumulative))
        (assoc (nth candidates index)
               :probability (nth probabilities index)
               :draw draw)
        (recur (inc index) next-cumulative)))))

(defn cell-decision
  "Choose hold/left/right using current prevalence and EFE score.

   TAU=0 is the genuine zero-temperature limit and delegates to the legacy
   argmin-G decision, including its exact tie order and absence of a draw."
  [arm state index]
  (let [tau (double (get state :tau 0.0))
        radius (long (get state :prevalence-radius default-radius))]
    (when (neg? tau)
      (throw (ex-info "tau must be nonnegative" {:tau tau})))
    (if (zero? tau)
      (assoc (efe/cell-decision arm state index)
             :selection :argmin-g
             :tau tau)
      (let [{:keys [observation candidates]}
            (scored-candidates arm state index radius)
            probabilities (softmax-probabilities candidates tau)
            candidates (mapv #(assoc %1 :probability %2)
                             candidates probabilities)]
        {:index index
         :observation observation
         :selection :prevalence-softmax
         :tau tau
         :prevalence-radius radius
         :candidates candidates
         :winner (sample-candidate candidates probabilities
                                   (draw-for state index))}))))

(defn transmit
  "Return the next exotype grid and auditable, synchronous decisions."
  [arm state]
  (let [decisions (mapv #(cell-decision arm state %)
                        (range (count (:exotypes state))))]
    {:exotypes (mapv #(get-in % [:winner :candidate-exotype]) decisions)
     :decisions decisions}))

(defn step
  "Advance all grids. E is recomputed from the current grid on every step."
  [state]
  (let [{:keys [exotypes decisions]} (transmit (:arm state) state)
        previous (:genotype state)
        advanced (grid/step (assoc state :arm :heterogeneous-fixed))]
    (cond-> (assoc advanced
                   :arm (:arm state)
                   :previous-genotype previous
                   :exotypes exotypes
                   :efe-decisions decisions
                   :prevalence-decisions decisions
                   :tau (double (get state :tau 0.0))
                   :prevalence-radius
                   (long (get state :prevalence-radius default-radius)))
      (contains? state :lambda) (assoc :lambda (:lambda state))
      (contains? state :rule-change-preference)
      (assoc :rule-change-preference (:rule-change-preference state)))))

(defn run-steps [state steps]
  (nth (iterate step state) steps))
