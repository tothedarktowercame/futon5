(ns futon5.exotype.pattern-eig
  "Pattern-valued exotypes and local, stigmergic epistemic value.

   A pattern's NEXT claim is a probability vector over the four observations
   already predicted by the frozen EFE model.  No cell acquires memory: EIG is
   the entropy of current claim-confirmation outcomes among nearby holders of
   the candidate pattern.  Risk compares the predicted resulting context with
   that candidate's own NEXT claim, rather than with one global preference."
  (:require [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.policy-expansion :as expansion]
            [futon5.exotype.prevalence :as prevalence]))

(def arms [:baseline :next-C :next-C-plus-eig :eig-only])

(def patterns
  "Minimal pattern content. NEXT uses the existing observation ABI; hunger is
   derived from the pattern's rule-change and activity claims exactly as in
   `efe/predict`."
  (into {}
        (for [[kind {:keys [rule-change activity diversity]}] efe/fixed-model]
          [kind {:kind kind
                 :next {:rule-change rule-change
                        :activity activity
                        :diversity diversity
                        :hunger (* (- 1.0 rule-change)
                                   (- 1.0 activity))}}])))

(def ^:private epsilon 1.0e-9)
(def confirmation-log-likelihood-floor (Math/log 0.5))

(defn- clamp-probability [x]
  (-> (double x) (max epsilon) (min (- 1.0 epsilon))))

(defn- bernoulli-kl [q p]
  (let [q (clamp-probability q)
        p (clamp-probability p)]
    (+ (* q (Math/log (/ q p)))
       (* (- 1.0 q) (Math/log (/ (- 1.0 q) (- 1.0 p)))))))

(defn- bernoulli-entropy [p]
  (if (or (zero? p) (= 1.0 (double p)))
    0.0
    (- (+ (* p (Math/log p))
          (* (- 1.0 p) (Math/log (- 1.0 p)))))))

(defn realized-context
  "The four currently observable local outcomes, encoded as Bernoulli draws."
  [state index]
  (let [{:keys [activity diversity hungry? static?]}
        (efe/local-observation state index)]
    {:rule-change (if static? 0.0 1.0)
     :activity (if (pos? activity) 1.0 0.0)
     :diversity (if (> diversity (/ 1.0 3.0)) 1.0 0.0)
     :hunger (if hungry? 1.0 0.0)}))

(defn claim-confirmed?
  "A NEXT claim is salient when its mean per-channel log likelihood is at
   least log(1/2). This fixed chance threshold is declared, not fitted."
  [next-claim realized]
  (let [log-likelihood
        (/ (reduce + 0.0
                   (for [[channel outcome] realized
                         :let [p (clamp-probability (get next-claim channel))]]
                     (Math/log (if (= 1.0 outcome) p (- 1.0 p)))))
           (double (count realized)))]
    (>= log-likelihood confirmation-log-likelihood-floor)))

(defn local-eig
  "Entropy of present confirmation/disconfirmation outcomes among current
   radius-neighbours holding KIND. No holders means no local evidence and EIG 0."
  [state index radius kind]
  (let [indices (prevalence/neighbourhood-indices
                 (count (:exotypes state)) index radius)
        holders (filter #(= kind (nth (:exotypes state) %)) indices)
        outcomes (map #(claim-confirmed? (get-in patterns [kind :next])
                                         (realized-context state %))
                      holders)]
    (if (seq outcomes)
      (bernoulli-entropy
       (/ (count (filter true? outcomes)) (double (count outcomes))))
      0.0)))

(defn- pattern-score [arm state index kind]
  (let [observation (efe/local-observation state index)
        base (efe/score-policy :efe-full kind observation
                               {:lambda (double (get state :lambda 0.55))})
        prediction (:prediction base)
        next-claim (get-in patterns [kind :next])
        risk (reduce + 0.0
                     (for [channel (keys next-claim)]
                       (bernoulli-kl (get prediction channel)
                                     (get next-claim channel))))
        eig (local-eig state index
                       (long (get state :prevalence-radius 1)) kind)
        lambda (double (get state :lambda 0.55))
        total (case arm
                :next-C (+ risk (:ambiguity base) (* lambda (:conatus base)))
                :next-C-plus-eig (- (+ risk (:ambiguity base)
                                       (* lambda (:conatus base))) eig)
                :eig-only (- (* lambda (:conatus base)) eig))]
    (assoc base
           :pattern (get patterns kind)
           :risk risk
           :eig eig
           :total total
           :enabled {:risk (not= arm :eig-only)
                     :ambiguity (not= arm :eig-only)
                     :conatus true
                     :eig (contains? #{:next-C-plus-eig :eig-only} arm)})))

(defn- draw-for [{:keys [seed time exotypes]} index]
  (.nextDouble
   (java.util.Random.
    (+ (long (or seed 0)) (* 1000003 (long (or time 0)))
       (* 9176 (long (count exotypes))) (long index)))))

(defn cell-decision
  "Score the kind-expanded policy set. Baseline delegates to the committed
   Slice-5 implementation; the other arms alter only the declared G terms."
  [arm state index]
  (if (= arm :baseline)
    (expansion/cell-decision :efe-full state index)
    (let [radius (long (get state :prevalence-radius 1))
          mu (double (get state :mu 0.1))
          tau (double (get state :tau 0.3))
          kinds (into [(nth (:exotypes state) index)] grid/exotype-kinds)
          counts (frequencies
                  (map #(nth (:exotypes state) %)
                       (prevalence/neighbourhood-indices
                        (count (:exotypes state)) index radius)))
          candidates (mapv (fn [kind]
                             (assoc (pattern-score arm state index kind)
                                    :candidate-exotype kind
                                    :habit-mass (+ (double (get counts kind 0)) mu)))
                           kinds)
          minimum (reduce min (map :total candidates))
          weights (mapv #(* (:habit-mass %)
                            (Math/exp (/ (- minimum (:total %)) tau)))
                        candidates)
          normalizer (reduce + 0.0 weights)
          probabilities (mapv #(/ % normalizer) weights)
          draw (draw-for state index)
          winner (loop [i 0 cumulative 0.0]
                   (let [next (+ cumulative (nth probabilities i))]
                     (if (or (= i (dec (count candidates))) (< draw next))
                       (assoc (nth candidates i) :probability (nth probabilities i)
                              :draw draw)
                       (recur (inc i) next))))]
      {:index index :observation (efe/local-observation state index)
       :candidates (mapv #(assoc %1 :probability %2) candidates probabilities)
       :winner winner :tau tau :mu mu :prevalence-radius radius})))

(defn transmit [arm state]
  (if (= arm :baseline)
    (expansion/transmit :efe-full state)
    (let [decisions (mapv #(cell-decision arm state %)
                          (range (count (:exotypes state))))]
      {:decisions decisions
       :exotypes (mapv #(get-in % [:winner :candidate-exotype]) decisions)})))

(defn step [state]
  (let [arm (:pattern-arm state)
        {:keys [exotypes decisions]} (transmit arm state)
        previous (:genotype state)
        advanced (grid/step (assoc state :arm :heterogeneous-fixed))]
    (assoc advanced :arm :efe-full :pattern-arm arm
           :previous-genotype previous :exotypes exotypes
           :pattern-decisions decisions
           :lambda (:lambda state) :tau (:tau state) :mu (:mu state)
           :prevalence-radius (:prevalence-radius state))))
