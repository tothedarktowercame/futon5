(ns futon5.exotype.policy-expansion
  "Kind-indexed exotype policies with a Dirichlet pseudocount habit prior.

   The policy set is exactly `{hold} union {adopt kind X}` for every member
   of `grid/exotype-kinds`.  Positive-temperature selection is

     Q(policy) proportional to (local-count(candidate-kind) + mu)
                              * exp(-G(policy) / tau).

   Thus innovation is not a mutation branch: an absent kind is selected only
   through the same scored policy relation as every present kind.  MU enters
   nowhere except the habit-mass floor.  The implementation is stateless and
   reads only the current circular local neighbourhood."
  (:require [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.prevalence :as prevalence]))

(def default-radius 1)

(defn policy-set
  "Five stable-order policies for one cell."
  [state index]
  (into [{:policy :hold
          :candidate-exotype (nth (:exotypes state) index)}]
        (map (fn [kind]
               {:policy :adopt-kind
                :kind kind
                :candidate-exotype kind})
             grid/exotype-kinds)))

(defn- scored-candidates [arm state index radius mu]
  (let [observation (efe/local-observation state index)
        score-options (select-keys state [:lambda :rule-change-preference])]
    {:observation observation
     :candidates
     (mapv (fn [{:keys [candidate-exotype] :as policy}]
             (let [local-count
                   (prevalence/candidate-prevalence
                    (:exotypes state) index radius candidate-exotype)]
               (merge policy
                      (efe/score-policy arm candidate-exotype observation
                                        score-options)
                      {:local-count local-count
                       :mu mu
                       :habit-mass (+ (double local-count) mu)})))
           (policy-set state index))}))

(defn- weights [candidates tau]
  (let [eligible (filter #(pos? (:habit-mass %)) candidates)
        minimum (reduce min (map :total eligible))]
    (mapv (fn [{:keys [total habit-mass]}]
            (* habit-mass
               (if (zero? tau)
                 (if (= (double total) (double minimum)) 1.0 0.0)
                 (Math/exp (/ (- minimum (double total)) tau)))))
          candidates)))

(defn- probabilities [candidates tau]
  (let [raw (weights candidates tau)
        normalizer (reduce + 0.0 raw)]
    (when-not (pos? normalizer)
      (throw (ex-info "expanded policy posterior has no positive mass"
                      {:tau tau :candidates candidates})))
    (mapv #(/ % normalizer) raw)))

(defn- draw-for [{:keys [seed time exotypes]} index]
  (let [draw-seed (+ (long (or seed 0))
                     (* 1000003 (long (or time 0)))
                     (* 9176 (long (count exotypes)))
                     (long index))]
    (.nextDouble (java.util.Random. draw-seed))))

(def ^:private cached-score
  ;; The frozen generative model reads a finite local observation. Caching its
  ;; pure score avoids recomputing identical Bernoulli KL/entropy terms in the
  ;; full grid; this cache is an implementation detail, not cell/model state.
  (memoize
   (fn [arm candidate-exotype observation score-options]
     (efe/score-policy arm candidate-exotype observation score-options))))

(defn- sample-candidate [candidates probabilities* draw]
  (loop [candidate-index 0 cumulative 0.0]
    (let [last? (= candidate-index (dec (count candidates)))
          next-cumulative (+ cumulative (nth probabilities* candidate-index))]
      (if (or last? (< draw next-cumulative))
        (assoc (nth candidates candidate-index)
               :probability (nth probabilities* candidate-index)
               :draw draw)
        (recur (inc candidate-index) next-cumulative)))))

(defn cell-decision
  "Score and sample the five-policy posterior for one current cell."
  [arm state index]
  (let [tau (double (get state :tau 0.0))
        mu (double (get state :mu 0.0))
        radius (long (get state :prevalence-radius default-radius))]
    (when (neg? tau)
      (throw (ex-info "tau must be nonnegative" {:tau tau})))
    (when (neg? mu)
      (throw (ex-info "mu must be nonnegative" {:mu mu})))
    (let [{:keys [observation candidates]}
          (scored-candidates arm state index radius mu)
          probabilities* (probabilities candidates tau)
          candidates* (mapv #(assoc %1 :probability %2)
                            candidates probabilities*)]
      {:index index
       :observation observation
       :selection :kind-policy-posterior
       :tau tau
       :mu mu
       :prevalence-radius radius
       :candidates candidates*
       :winner (sample-candidate candidates* probabilities*
                                 (draw-for state index))})))

(defn transmit
  "Return synchronous expanded-policy decisions and their exotype grid."
  [arm state]
  (let [decisions (mapv #(cell-decision arm state %)
                        (range (count (:exotypes state))))]
    {:exotypes (mapv #(get-in % [:winner :candidate-exotype]) decisions)
     :decisions decisions}))

(defn transmit-compact
  "Return only the next exotype grid, using the identical scored posterior as
  `transmit`. Long experiments use this to avoid retaining 80 complete policy
  audits in every intermediate state; it does not change policy construction,
  G, E, sampling, or the stateless draw."
  [arm state]
  (let [exotypes (:exotypes state)
        width (count exotypes)
        radius (long (get state :prevalence-radius default-radius))
        tau (double (get state :tau 0.0))
        mu (double (get state :mu 0.0))
        score-options (select-keys state [:lambda :rule-change-preference])]
    (mapv
     (fn [index]
       (let [observation (efe/local-observation state index)
             local-counts
             (frequencies
              (map #(nth exotypes %)
                   (prevalence/neighbourhood-indices width index radius)))
             candidate-kinds (into [(nth exotypes index)] grid/exotype-kinds)
             totals (mapv #(get (cached-score arm % observation score-options)
                                :total)
                          candidate-kinds)
             habit-masses (mapv #(+ (double (get local-counts % 0)) mu)
                                candidate-kinds)
             minimum (reduce min
                             (keep-indexed
                              #(when (pos? (nth habit-masses %1)) %2)
                              totals))
             raw (mapv (fn [total habit-mass]
                         (* habit-mass
                            (if (zero? tau)
                              (if (= (double total) (double minimum)) 1.0 0.0)
                              (Math/exp (/ (- minimum (double total)) tau)))))
                       totals habit-masses)
             normalizer (reduce + 0.0 raw)
             probabilities* (mapv #(/ % normalizer) raw)
             draw (draw-for state index)]
         (loop [candidate-index 0 cumulative 0.0]
           (let [last? (= candidate-index (dec (count candidate-kinds)))
                 next-cumulative (+ cumulative
                                    (nth probabilities* candidate-index))]
             (if (or last? (< draw next-cumulative))
               (nth candidate-kinds candidate-index)
               (recur (inc candidate-index) next-cumulative))))))
     (range width))))

(defn step
  "Advance all three grids; MU is used only inside the policy prior floor."
  [state]
  (let [{:keys [exotypes decisions]} (transmit (:arm state) state)
        previous (:genotype state)
        advanced (grid/step (assoc state :arm :heterogeneous-fixed))]
    (cond-> (assoc advanced
                   :arm (:arm state)
                   :previous-genotype previous
                   :exotypes exotypes
                   :efe-decisions decisions
                   :policy-expansion-decisions decisions
                   :tau (double (get state :tau 0.0))
                   :mu (double (get state :mu 0.0))
                   :prevalence-radius
                   (long (get state :prevalence-radius default-radius)))
      (contains? state :lambda) (assoc :lambda (:lambda state))
      (contains? state :rule-change-preference)
      (assoc :rule-change-preference (:rule-change-preference state)))))

(defn step-compact
  "Advance all three grids without attaching the per-cell decision audit.
  Intended only for long measurement runs after semantic equivalence with
  `step` has been checked."
  [state]
  (let [exotypes (transmit-compact (:arm state) state)
        previous (:genotype state)
        advanced (grid/step (assoc state :arm :heterogeneous-fixed))]
    (cond-> (assoc advanced
                   :arm (:arm state)
                   :previous-genotype previous
                   :exotypes exotypes
                   :tau (double (get state :tau 0.0))
                   :mu (double (get state :mu 0.0))
                   :prevalence-radius
                   (long (get state :prevalence-radius default-radius)))
      (contains? state :lambda) (assoc :lambda (:lambda state))
      (contains? state :rule-change-preference)
      (assoc :rule-change-preference (:rule-change-preference state)))))

(defn run-steps [state steps]
  (nth (iterate step state) steps))
