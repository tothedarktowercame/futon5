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
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.selection :as selection]
            [futon5.xenotype.generator :as gen]))

(def efe-arms
  [:efe-full :efe-risk-only :efe-ambiguity-only :efe-no-conatus])

(def arm-flags
  {:efe-full {:risk? true :ambiguity? true :conatus? true}
   :efe-risk-only {:risk? true :ambiguity? false :conatus? true}
   :efe-ambiguity-only {:risk? false :ambiguity? true :conatus? true}
   :efe-no-conatus {:risk? true :ambiguity? true :conatus? false}})

(def ^:private declared-channels
  "HAND-DECLARED, not derived. Nothing has measured `:activity` or `:diversity`,
   and whether they are functions of sigma is unknown -- do NOT assume symmetry
   with `:rule-change`. Deliberately left standing (Joe, 2026-08-04: \"if
   :activity and :diversity need sorting in a different way we can do so
   later\"). H1 covers `:rule-change` only."
  {:builder {:activity 0.48 :diversity 0.72}
   :collapser {:activity 0.12 :diversity 0.18}
   :chaos {:activity 0.78 :diversity 0.88}
   :identity {:activity 0.35 :diversity 0.35}})

(def fixed-model
  "Frozen P(next local observation | exotype, current local observation).

   `:rule-change` is DERIVED from each propagator's sigma (H1, 2026-08-04). It
   used to be four typed-in constants, of which three were wrong -- `:identity`
   declared 0.02 against a measured 1.000, a factor of fifty. The name was the
   camouflage: `rule-permute` writes NOT bit[k] into position sigma(k), so a
   fixed point of sigma flips unconditionally, and the identity permutation is
   nothing but fixed points. It is the MOST disruptive propagator in the family,
   not the least.

   Deriving the column means it cannot drift from the operator it describes
   again. Derived (uniform bytes) vs the old declared values:

     kind        derived   was     fix(sigma)   floor = fix/8
     :identity    1.0000   0.02        8           1.000
     :builder     0.8125   0.78        5           0.625
     :chaos       0.5625   0.90        1           0.125
     :collapser   0.5000   0.18        0           0.000

   NOTE this does NOT change which exotype the EFE selects: `:chaos` wins under
   both the old and the derived values (tested, 24 observations, 18/24 -> 20/24).
   \"Chaos always wins\" is structural to the objective, not an artefact of the
   numbers -- see TN-baldwin-reboot.md 15.2 and
   TN-metaca-baldwin-micro-pilots.md before touching the objective."
  (into {}
        (for [[kind channels] declared-channels]
          [kind (assoc channels
                       :rule-change
                       (gen/rule-change-rate (get grid/propagators kind)))])))

(def preferences
  "C is local: conservative own-rule change and, load-bearingly, low hunger.

   *** READ BEFORE WORKING ON THE OBJECTIVE (G1). ***

   `TN-metaca-baldwin-micro-pilots.md`, micro-pilot 7, reports that **the exotype
   objective cannot be repaired from inside**: chaos's claims are confirmed at
   every active observation, so no accuracy-based correction displaces it. This
   file carried no pointer to that note until 2026-08-04, and the result was
   consequently re-derived from scratch during H1 (TN-baldwin-reboot.md 19.2 --
   the same note WAS co-located in `scripts/exotype_blend_mu_sweep_slice11.clj`,
   where it was used correctly).

   Two halves, with different standing -- do not conflate them:

   - VERIFIED, and RNG-independent: `:chaos` is the argmin over the observation
     grid as a property of `score-policy`, which is a pure function. Pinned by
     `invariants-test/chaos-is-the-structural-argmin`. Correcting `fixed-model`
     does not change it (18/24 -> 20/24).
   - NOT VERIFIED: the stronger dynamical claim that no accuracy-based correction
     can displace chaos was measured on the PRE-FIX substrate, before the per-cell
     seeding defect was found (TN-baldwin-reboot.md 2). It falls under the 13
     disposition like every other pre-fix result and has not been re-run."
  selection/preference-targets)

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

(defn observation-bin
  "The conditional model's key: [exotype, activity-count, diversity-count].

   `local-observation` reports activity and diversity as k/3, so the numerator is
   the honest key -- integers, and no floating-point equality in a lookup.
   Activity is 0..2 (self never differs from self) and diversity is 1..3."
  [exotype {:keys [activity diversity]}]
  [exotype
   (Math/round (* 3.0 (double activity)))
   (Math/round (* 3.0 (double diversity)))])

(def min-bin-samples
  "A derived bin is trusted only above this many observations; below it, `predict`
   falls back to the global row.

   Measured on the shipped resource: 3 of 25 bins fall below 30 (n = 4, 15, 20)
   and they hold 39 of 28,620 samples (0.14%) -- rare local states whose per-bin
   means would be noise. The raw counts stay in the resource so this policy lives
   in code, where it is visible and testable, rather than being baked into the data.

   NOT A MEASURED OPTIMUM (codex-12 #6). No threshold sweep or held-out comparison
   establishes 30 over 20, 40, or a shrinkage estimator, and held-out error is
   dominated by the dense bins either way. Treat this as a low-impact heuristic;
   if a decision ever turns on a sparse bin, sweep it first."
  30)

(def conditional-model
  "P(next local observation | exotype, current local observation), DERIVED by
   measuring the substrate rather than declared.

   Regenerate with `scripts/derive_conditional_model.clj`; determinism, coverage
   and out-of-sample accuracy are pinned by `futon5.exotype.invariants-test`.
   Held-out mean absolute error against the legacy path
   (TN-baldwin-reboot.md 28):

     channel      null     legacy    derived
     activity    0.1799    0.2309    0.1801    (no gain -- and none is possible,
                                                see below)
     diversity   0.0281    0.2306    0.0126    18x better than legacy
     hunger      0.1805    0.2171    0.1681    23% better than legacy

   The legacy model is WORSE THAN A CONSTANT on every channel. `:activity` is the
   one channel the derived model cannot improve, and that is structural rather
   than a modelling failure: `phenotype-step` reads the CURRENT genotype, so an
   exotype chosen at t changes the genotype at t+1 and the phenotype only at t+2.
   Next-step activity is therefore not a function of this step's exotype."
  (delay (some-> (io/resource "futon5/exotype/conditional-model.edn")
                 slurp
                 edn/read-string)))

(defn- predict-legacy
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

(defn predict
  "Apply the model to CANDIDATE-EXOTYPE and a local observation.

   MODEL-KIND is `:derived` (DEFAULT since 2026-08-04 -- the measured conditional
   table) or `:legacy` (the frozen three-numbers-per-kind table blended 50/50
   with the observation, preserved byte-for-byte for comparison).

   `:derived` became the default because the legacy model is WORSE THAN
   PREDICTING A CONSTANT on every channel out of sample (TN-baldwin-reboot.md
   28.1). `:legacy` is retained, not deprecated: it is the baseline every
   comparison is made against, and slice results predating the switch were
   produced under it."
  ([candidate-exotype observation]
   (predict candidate-exotype observation :derived))
  ([candidate-exotype observation model-kind]
   ;; Reject unknown selectors rather than falling through to legacy. Model choice
   ;; materially changes results, so a typo like :dervied silently selecting the
   ;; known-worse model is a configuration defect, not a default. (codex-12 #4.)
   (when-not (#{:legacy :derived} model-kind)
     (throw (ex-info "unknown observation model" {:model-kind model-kind
                                                  :known #{:legacy :derived}})))
   (if (= :derived model-kind)
     (let [m (or @conditional-model
                 (throw (ex-info "conditional model resource missing; run scripts/derive_conditional_model.clj"
                                 {:regenerate "scripts/derive_conditional_model.clj"})))
           candidate (get-in m [:bins (observation-bin candidate-exotype observation)])
           row (if (and candidate (>= (long (:n candidate)) min-bin-samples))
                 candidate
                 (:global m))]
       ;; S0: compute the rate from sigma rather than reading `fixed-model`, which
       ;; only has rows for the four DECLARED kinds. Reading it there capped the
       ;; selectable vocabulary at the hand-typed table and NPE'd on any other
       ;; kind -- so the objective could not even be OFFERED the EoC-capable
       ;; propagators (36.1 had to score them through an ad-hoc harness).
       ;; Identical for the declared four, since `fixed-model` derives the same
       ;; value the same way.
       {:rule-change (gen/rule-change-rate (get grid/propagators candidate-exotype))
        :activity (:activity row)
        :diversity (:diversity row)
        :hunger (:hunger row)})
     (predict-legacy candidate-exotype observation))))


(defn score-policy
  "Score one candidate exotype. Lower total is preferred.

   Raw contributions are always returned even when an ablation flag removes
   them from `:total`, preventing a connected-but-invisible term. The optional
   `:lambda` weights conatus; arm-derived defaults preserve Slice 2 exactly.
   `:rule-change-preference` is a diagnostic override and never changes C.
   `:observation-model` selects `:derived` (default) or `:legacy`; see `predict`.
   `cell-decision` forwards it, so trajectory-level comparisons honour it."
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
         prediction (predict candidate-exotype observation
                             (get opts :observation-model :derived))
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
        ;; :observation-model MUST be threaded here. Omitting it meant a state
        ;; requesting :legacy silently executed :derived, so every trajectory-level
        ;; legacy comparison through cell-decision/transmit/step was invalid while
        ;; direct predict/score-policy comparisons looked correct. (codex-12 #1.)
        score-options (select-keys state [:lambda :rule-change-preference :observation-model])
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
