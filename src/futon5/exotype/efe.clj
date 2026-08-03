(ns futon5.exotype.efe
  "A local, memoryless expected-free-energy regulator for exotype transfer.

   Each cell scores three policies in stable order: hold, adopt-left, and
   adopt-right.  The fixed generative model predicts four Bernoulli local
   observations from the candidate exotype and the current three-cell locale:
   own-rule change, phenotype activity, genotype diversity, and informational
   hunger.  Hunger is the probability that the own rule stays static while the
   local phenotype remains uniform.  Thus C mentions only local predicates and
   prefers low hunger; it never observes damage, reach, bands, or global state.

   Conditional probabilities are specified constants grounded in the measured
   propagator vocabulary, then shrunk halfway toward the current local activity
   and diversity.  They are frozen for every run; there is no within-run model
   update or cell memory beyond the current and immediately previous genotype.

   The joint product-Bernoulli KL decomposes by channel.  `:risk` is the
   own-rule-change channel's KL against a conservative preference, while
   `:conatus` is the hunger channel's KL against low hunger.  `:ambiguity` is
   the entropy of all four predicted observation channels.  These contributions
   remain separately switchable and visible in every score."
  (:require [futon5.exotype.grid :as grid]))

(def efe-arms
  [:efe-full :efe-risk-only :efe-ambiguity-only :efe-no-conatus])

(def arm-flags
  {:efe-full {:risk? true :ambiguity? true :conatus? true}
   :efe-risk-only {:risk? true :ambiguity? false :conatus? true}
   :efe-ambiguity-only {:risk? false :ambiguity? true :conatus? true}
   :efe-no-conatus {:risk? true :ambiguity? true :conatus? false}})

(def fixed-model
  "Frozen P(next local observation | exotype,current local observation)."
  {:builder {:rule-change 0.78 :activity 0.48 :diversity 0.72}
   :collapser {:rule-change 0.18 :activity 0.12 :diversity 0.18}
   :chaos {:rule-change 0.90 :activity 0.78 :diversity 0.88}
   :identity {:rule-change 0.02 :activity 0.35 :diversity 0.35}})

(def preferences
  "C is local: conservative own-rule change and, load-bearingly, low hunger."
  {:rule-change 0.15 :hunger 0.05})

(def ^:private epsilon 1.0e-9)

(defn- clamp-probability [x]
  (-> (double x) (max epsilon) (min (- 1.0 epsilon))))

(defn- bernoulli-kl [q p]
  (let [q (clamp-probability q)
        p (clamp-probability p)]
    (+ (* q (Math/log (/ q p)))
       (* (- 1.0 q) (Math/log (/ (- 1.0 q) (- 1.0 p)))))))

(defn- bernoulli-entropy [p]
  (let [p (clamp-probability p)]
    (- (+ (* p (Math/log p))
          (* (- 1.0 p) (Math/log (- 1.0 p)))))))

(defn local-observation
  "Return the strictly local observation available to one cell."
  [{:keys [genotype phenotype previous-genotype]} index]
  (let [width (count genotype)
        at #(mod % width)
        indices (mapv at [(dec index) index (inc index)])
        phe (mapv #(nth phenotype %) indices)
        geno (mapv #(nth genotype %) indices)
        activity (/ (count (filter #(not= (nth phenotype index) %) phe)) 3.0)
        diversity (/ (count (distinct geno)) 3.0)
        static? (and previous-genotype
                     (= (nth previous-genotype index) (nth genotype index)))
        boring? (apply = phe)]
    {:activity activity
     :diversity diversity
     :boring? boring?
     :static? (boolean static?)
     :hungry? (boolean (and static? boring?))}))

(defn predict
  "Apply the frozen model to CANDIDATE-EXOTYPE and a local observation."
  [candidate-exotype {:keys [activity diversity]}]
  (let [{base-change :rule-change
         base-activity :activity
         base-diversity :diversity} (get fixed-model candidate-exotype)
        next-activity (+ (* 0.5 base-activity) (* 0.5 activity))
        next-diversity (+ (* 0.5 base-diversity) (* 0.5 diversity))
        next-boring (- 1.0 next-activity)
        next-hunger (* (- 1.0 base-change) next-boring)]
    {:rule-change base-change
     :activity next-activity
     :diversity next-diversity
     :hunger next-hunger}))

(defn score-policy
  "Score one candidate exotype. Lower total is preferred.

   Raw contributions are always returned even when an ablation flag removes
   them from `:total`, preventing a connected-but-invisible term. The optional
   `:lambda` weights conatus; arm-derived defaults preserve Slice 2 exactly.
   `:rule-change-preference` is a diagnostic override and never changes C."
  ([arm candidate-exotype observation]
   (score-policy arm candidate-exotype observation {}))
  ([arm candidate-exotype observation opts]
   (let [{:keys [risk? ambiguity? conatus?]} (get arm-flags arm)
         lambda (double (if (contains? opts :lambda)
                          (:lambda opts)
                          (if conatus? 1.0 0.0)))
         rule-change-preference
         (double (get opts :rule-change-preference
                      (:rule-change preferences)))
         prediction (predict candidate-exotype observation)
         risk (bernoulli-kl (:rule-change prediction)
                            rule-change-preference)
         ambiguity (reduce + (map bernoulli-entropy (vals prediction)))
         conatus (bernoulli-kl (:hunger prediction) (:hunger preferences))
         total (+ (if risk? risk 0.0)
                  (if ambiguity? ambiguity 0.0)
                  (* lambda conatus))]
     {:candidate-exotype candidate-exotype
      :prediction prediction
      :risk risk
      :ambiguity ambiguity
      :conatus conatus
      :lambda lambda
      :total total
      :enabled {:risk risk? :ambiguity ambiguity? :conatus conatus?}})))

(defn cell-decision
  "Choose hold/left/right by minimum G; exact ties prefer hold, then left."
  [arm state index]
  (let [width (count (:exotypes state))
        sources [{:policy :hold :source index}
                 {:policy :adopt-left :source (mod (dec index) width)}
                 {:policy :adopt-right :source (mod (inc index) width)}]
        observation (local-observation state index)
        score-options (select-keys state [:lambda :rule-change-preference])
        candidates (mapv (fn [{:keys [source] :as policy}]
                           (merge policy
                                  (score-policy arm
                                                (nth (:exotypes state) source)
                                                observation
                                                score-options)))
                         sources)
        winner (first (sort-by :total candidates))]
    {:index index
     :observation observation
     :candidates candidates
     :winner winner}))

(defn transmit
  "Return the next exotype grid and auditable per-cell policy decomposition."
  [arm state]
  (let [decisions (mapv #(cell-decision arm state %)
                        (range (count (:exotypes state))))]
    {:exotypes (mapv #(get-in % [:winner :candidate-exotype]) decisions)
     :decisions decisions}))

(defn step
  "Advance the three grids synchronously under an EFE arm."
  [state]
  (let [{:keys [exotypes decisions]} (transmit (:arm state) state)
        previous (:genotype state)
        advanced (grid/step (assoc state :arm :heterogeneous-fixed))]
    (cond-> (assoc advanced
                   :arm (:arm state)
                   :previous-genotype previous
                   :exotypes exotypes
                   :efe-decisions decisions)
      (contains? state :lambda) (assoc :lambda (:lambda state))
      (contains? state :rule-change-preference)
      (assoc :rule-change-preference (:rule-change-preference state)))))

(defn run-steps [state steps]
  (nth (iterate step state) steps))
