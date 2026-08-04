(ns futon5.exotype.pattern-eig
  "Pattern-valued exotypes and local, stigmergic epistemic value.

   A pattern's NEXT claim is a probability vector over the four observations
   already predicted by the frozen EFE model.  No cell acquires memory: EIG is
   the entropy of current claim-confirmation outcomes among nearby holders of
   the candidate pattern.  Risk compares the predicted resulting context with
   that candidate's own NEXT claim, rather than with one global preference."
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.policy-expansion :as expansion]
            [futon5.exotype.prevalence :as prevalence]))

(def arms [:baseline :next-C :next-C-plus-eig :eig-only])

(def patterns
  "Minimal pattern content. S0b/H5: built from predict :derived for all 12 kinds
   rather than from fixed-model (which only has the declared four). Hunger is
   derived from the prediction exactly as in efe/predict."
  (into {}
        (for [kind grid/exotype-kinds
              :let [p (efe/predict kind {:activity 0.0 :diversity 1.0} :derived)]]
          [kind {:kind kind
                 :next {:rule-change (:rule-change p)
                        :activity (:activity p)
                        :diversity (:diversity p)
                        :hunger (:hunger p)}}])))

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

(def beta-prior
  "Unfitted symmetric prior for corrected local epistemic uncertainty."
  {:alpha 1.0 :beta 1.0})

(defn corrected-local-eig
  "Beta-posterior uncertainty about a candidate pattern's local salience.

   This is normalized posterior variance, scaled to ln(2). Beta(1,1) with no
   holders therefore has maximum value ln(2). Evidence always sharpens the
   posterior, while mixed confirmations retain more uncertainty than unanimous
   outcomes at the same holder count. All observations are recomputed from the
   current neighbourhood; the posterior is not stored in a cell."
  [state index radius kind]
  (let [indices (prevalence/neighbourhood-indices
                 (count (:exotypes state)) index radius)
        holders (filter #(= kind (nth (:exotypes state) %)) indices)
        outcomes (map #(claim-confirmed? (get-in patterns [kind :next])
                                         (realized-context state %))
                      holders)
        successes (double (count (filter true? outcomes)))
        failures (double (- (count outcomes) successes))
        alpha (+ (:alpha beta-prior) successes)
        beta (+ (:beta beta-prior) failures)
        mass (+ alpha beta)
        posterior-variance (/ (* alpha beta) (* mass mass (inc mass)))
        prior-variance (/ 1.0 12.0)]
    (* (Math/log 2.0) (/ posterior-variance prior-variance))))

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
        eig-model (get state :eig-model :legacy)
        eig-fn (case eig-model
                 :legacy local-eig
                 :beta-posterior corrected-local-eig)
        eig (eig-fn state index
                    (long (get state :prevalence-radius 1)) kind)
        eig-coefficient (double (get state :eig-coefficient 1.0))
        weighted-eig (* eig-coefficient eig)
        lambda (double (get state :lambda 0.55))
        total (case arm
                :next-C (+ risk (:ambiguity base) (* lambda (:conatus base)))
                :next-C-plus-eig (- (+ risk (:ambiguity base)
                                       (* lambda (:conatus base))) weighted-eig)
                :eig-only (- (* lambda (:conatus base)) weighted-eig))]
    (assoc base
           :pattern (get patterns kind)
           :eig-model eig-model
           :risk risk
           :eig eig
           :eig-coefficient eig-coefficient
           :weighted-eig weighted-eig
           :total total
           :enabled {:risk (not= arm :eig-only)
                     :ambiguity (not= arm :eig-only)
                     :conatus true
                     :eig (contains? #{:next-C-plus-eig :eig-only} arm)})))

(defn- draw-for [{:keys [seed time exotypes]} index]
  ;; mix-seed: per-cell stride 1 -- see prevalence/draw-for, TN-baldwin-reboot 2.
  (.nextDouble
   (java.util.Random.
    (ca/mix-seed
     (+ (long (or seed 0)) (* 1000003 (long (or time 0)))
        (* 9176 (long (count exotypes))) (long index))))))

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
           :eig-model (get state :eig-model :legacy)
           :eig-coefficient (double (get state :eig-coefficient 1.0))
           :prevalence-radius (:prevalence-radius state))))

(defn step-compact
  "Long-run equivalent of `step`, omitting the per-cell decision audit from
   the returned state. The sampled decisions and state transition are equal."
  [state]
  (let [arm (:pattern-arm state)
        exotypes (if (= arm :baseline)
                   (expansion/transmit-compact :efe-full state)
                   (:exotypes (transmit arm state)))
        previous (:genotype state)
        advanced (grid/step (assoc state :arm :heterogeneous-fixed))]
    (assoc advanced :arm :efe-full :pattern-arm arm
           :previous-genotype previous :exotypes exotypes
           :lambda (:lambda state) :tau (:tau state) :mu (:mu state)
           :eig-model (get state :eig-model :legacy)
           :eig-coefficient (double (get state :eig-coefficient 1.0))
           :prevalence-radius (:prevalence-radius state))))
