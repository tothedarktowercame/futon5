(ns futon5.mmca.exotype
  "Exotypes: kernel-rewrite operators derived from sigils.

   IMPORTANT: An exotype is 36 bits, not 8:
   - LEFT (8): IF / preconditions
   - EGO (8): BECAUSE / rationale (the sigil)
   - RIGHT (8): HOWEVER / risks
   - NEXT (8): THEN / outcomes
   - PHENOTYPE-FAMILY (4): 3 parents + child

   The hexagram (diagonal trace of the 6x6 matrix) identifies the physics family.
   See hexagram/lift.clj for the full structure."
  (:require [futon5.ca.core :as ca]
            [futon5.hexagram.lift :as hex-lift]))

(def ^:private trace-bent-cell?
  (boolean (System/getenv "TPG_TRACE_CELL")))

(def ^:private mix-modes
  [:none :rotate-left :rotate-right :reverse :xor-neighbor :majority :swap-halves :scramble])

(defn- rand-double
  "Get a random double [0,1), optionally using provided RNG for reproducibility."
  [^java.util.Random rng]
  (if rng
    (.nextDouble rng)
    (rand)))

(defn- rand-nth*
  "Random element from collection, optionally using provided RNG."
  [^java.util.Random rng coll]
  (if rng
    (nth coll (.nextInt rng (count coll)))
    (rand-nth coll)))

(defn- normalize-sigil [sigil]
  (cond
    (nil? sigil) nil
    (string? sigil) sigil
    (keyword? sigil) (name sigil)
    (char? sigil) (str sigil)
    :else (str sigil)))

(defn- bits->int [bits]
  (reduce (fn [acc ch]
            (+ (* acc 2) (if (= ch \1) 1 0)))
          0
          bits))

(defn- sigil-bits [sigil]
  (ca/bits-for (normalize-sigil sigil)))

(defn- sigil->params [sigil]
  (let [bits (sigil-bits sigil)
        rotation (mod (bits->int (subs bits 0 2)) 4)
        threshold-idx (bits->int (subs bits 2 5))
        match-threshold (/ (double (inc threshold-idx)) 9.0)
        invert? (= \1 (nth bits 5))
        update-prob (nth [0.25 0.5 0.75 1.0] (bits->int (subs bits 6 8)))
        idx (bits->int bits)
        mix-mode (nth mix-modes (mod idx (count mix-modes)))
        mix-shift (mod (quot idx 3) 4)]
    {:rotation rotation
     :match-threshold match-threshold
     :invert-on-phenotype? invert?
     :update-prob update-prob
     :mix-mode mix-mode
     :mix-shift mix-shift}))

(defn lift
  "Lift a sigil into its local-rule exotype."
  [sigil]
  (let [sigil (normalize-sigil sigil)]
    {:sigil sigil
     :tier :local
     :params (sigil->params sigil)}))

(defn promote
  "Promote a sigil into its super exotype."
  [sigil]
  (let [sigil (normalize-sigil sigil)]
    {:sigil sigil
     :tier :super
     :params (sigil->params sigil)}))

(defn resolve-exotype
  "Accepts a sigil, keyword, or exotype map and returns a normalized exotype."
  [exotype]
  (cond
    (nil? exotype) nil
    (map? exotype) (let [sigil (normalize-sigil (:sigil exotype))]
                     (cond-> (assoc exotype
                                    :sigil sigil
                                    :tier (or (:tier exotype) :local))
                       (nil? (:params exotype)) (assoc :params (sigil->params sigil))))
    :else (lift exotype)))

(defn all-exotypes []
  (mapv (fn [{:keys [sigil]}]
          {:sigil sigil
           :lift (lift sigil)
           :super (promote sigil)})
        (ca/sigil-entries)))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- sigil-at [s idx]
  (if (and s (<= 0 idx) (< idx (count s)))
    (str (nth s idx))
    ca/default-sigil))

(defn- bit-at [s idx]
  (if (and s (<= 0 idx) (< idx (count s)))
    (nth s idx)
    \0))

(defn- phenotype-family-at [phe-row phe-next idx]
  (str (bit-at phe-row (dec idx))
       (bit-at phe-row idx)
       (bit-at phe-row (inc idx))
       (bit-at phe-next idx)))

(defn- evidence->phenotype-family [evidence]
  (let [bits (cond
               (nil? evidence) []
               (string? evidence) (seq evidence)
               (sequential? evidence) evidence
               :else (seq (str evidence)))
        bits (map (fn [b]
                    (cond
                      (= b \1) \1
                      (= b \0) \0
                      (= b 1) \1
                      (= b 0) \0
                      (true? b) \1
                      (false? b) \0
                      (string? b) (if (= b "1") \1 \0)
                      (keyword? b) (if (= b :yang) \1 \0)
                      (number? b) (if (pos? (double b)) \1 \0)
                      :else \0))
                  bits)
        padded (concat bits (repeat \0))]
    (apply str (take 4 padded))))

(defn- kernel-fn-for [kernel-fn-map kernel]
  (or (get kernel-fn-map kernel)
      (when kernel
        (try
          (ca/kernel-fn kernel)
          (catch Exception _ nil)))
      (get kernel-fn-map :mutating-template)
      (ca/kernel-fn :mutating-template)))

(defn sample-context-at
  "Sample a local context at a specific time/position."
  [gen-history phe-history t x ^java.util.Random rng]
  (let [rows (count gen-history)
        cols (count (or (first gen-history) ""))]
    (when (and (> rows 1)
               (pos? cols)
               (<= 0 t)
               (< t (dec rows))
               (<= 0 x)
               (< x cols))
      (let [row (nth gen-history t)
            next-row (nth gen-history (inc t))
            pred (sigil-at row (dec x))
            self (sigil-at row x)
            next (sigil-at row (inc x))
            out (sigil-at next-row x)
            phe (when (and phe-history (> (count phe-history) (inc t)))
                  (let [phe-row (nth phe-history t)
                        phe-next (nth phe-history (inc t))]
                    (phenotype-family-at phe-row phe-next x)))]
        {:context-sigils [pred self next out]
         :phenotype-context phe
         :rotation (if rng (rng-int rng 4) 0)
         :coord {:t t :x x}}))))

(defn sample-context
  "Pick a single local context from the run history."
  [gen-history phe-history ^java.util.Random rng]
  (let [rows (count gen-history)
        cols (count (or (first gen-history) ""))]
    (when (and (> rows 1) (pos? cols))
      (let [t (rng-int rng (dec rows))
            x (rng-int rng cols)]
        (sample-context-at gen-history phe-history t x rng)))))

;; =============================================================================
;; 36-bit physics family extraction
;; =============================================================================

(defn context->physics-family
  "Derive physics family from full 36-bit context.
   Returns hexagram-based physics identifier.

   The context contains:
   - :context-sigils [LEFT EGO RIGHT NEXT] — 32 bits from CA neighborhood
   - :phenotype-context — phenotype family (3 parents + child)

   The hexagram (from eigenvalue diagonalization) identifies the situation.
   The primary energy (from phenotype family bits 0-1) identifies the engagement mode.
   Together: 64 × 4 = 256 physics rules."
  [context]
  (when context
    (let [physics (hex-lift/context->physics-rule context)]
      {:rule (:rule physics)
       :hexagram-id (get-in physics [:hexagram :number])
       :hexagram-name (get-in physics [:hexagram :name])
       :hexagram-lines (get-in physics [:hexagram :lines])
       :energy-key (get-in physics [:energy :key])
       :energy-name (get-in physics [:energy :name])
       :energy-dynamic (get-in physics [:energy :dynamic])
       :description (:description physics)})))

(def ^:private energy-dynamics
  "Physics modifiers for each primary energy."
  {:peng {:mutation-modifier 1.2   ; Péng expands → more mutation
          :structure-modifier 0.8
          :mode-bias :expand}
   :lu   {:mutation-modifier 0.6   ; Lǚ yields → less mutation
          :structure-modifier 1.3
          :mode-bias :yield}
   :ji   {:mutation-modifier 0.9   ; Jǐ focuses → selective mutation
          :structure-modifier 1.0
          :mode-bias :focus}
   :an   {:mutation-modifier 1.1   ; Àn pushes → sustained pressure
          :structure-modifier 0.9
          :mode-bias :momentum}})

(defn rule->physics-params
  "Map a physics rule (0-255) to physics parameters.

   Rule = hexagram (0-63) × 4 + energy (0-3)

   The hexagram provides base parameters; the energy modulates them."
  [rule]
  (let [{:keys [hexagram energy]} (hex-lift/rule->hexagram+energy rule)
        energy-key (:key energy)
        energy-mod (get energy-dynamics energy-key)

        ;; Base params from hexagram family (8 families of 8 hexagrams each)
        family (quot (dec hexagram) 8)
        base (case family
               0 {:physics-mode :expansion    :mutation-bias 0.7 :structure-weight 0.3}
               1 {:physics-mode :conservation :mutation-bias 0.2 :structure-weight 0.8}
               2 {:physics-mode :adaptation   :mutation-bias 0.5 :structure-weight 0.5}
               3 {:physics-mode :momentum     :mutation-bias 0.6 :structure-weight 0.4}
               4 {:physics-mode :conditional  :mutation-bias 0.4 :structure-weight 0.5}
               5 {:physics-mode :differentiation :mutation-bias 0.5 :structure-weight 0.3}
               6 {:physics-mode :transformation :mutation-bias 0.8 :structure-weight 0.2}
               7 {:physics-mode :consolidation :mutation-bias 0.3 :structure-weight 0.7}
               {:physics-mode :neutral :mutation-bias 0.5 :structure-weight 0.5})

        ;; Apply energy modulation
        mutation-bias (* (:mutation-bias base)
                         (or (:mutation-modifier energy-mod) 1.0))
        structure-weight (* (:structure-weight base)
                            (or (:structure-modifier energy-mod) 1.0))]

    {:rule rule
     :hexagram hexagram
     :energy energy-key
     :physics-mode (:physics-mode base)
     :energy-mode (:mode-bias energy-mod)
     :mutation-bias (min 1.0 (max 0.0 mutation-bias))
     :structure-weight (min 1.0 (max 0.0 structure-weight))
     :description (str "Hex " hexagram " (" (name (:physics-mode base)) ") × "
                       (:name energy) " (" (name (:mode-bias energy-mod)) ")")}))

(defn hexagram->physics-params
  "Map a hexagram to physics parameters.
   DEPRECATED: Use rule->physics-params for full 256-rule physics."
  [hexagram-id]
  ;; Default to Péng energy for backwards compatibility
  (rule->physics-params (hex-lift/hexagram+energy->rule (dec (or hexagram-id 1)) 0)))

(def ^:private sigil-table
  "Cached vector of all 256 sigils for index-based lookup."
  (delay (mapv :sigil (ca/sigil-entries))))

(defn index->sigil
  "Get a sigil by index (0-255)."
  [idx]
  (nth @sigil-table (mod idx 256)))

(defn pattern->physics
  "Map a design pattern's structure to physics parameters.

   A design pattern has:
   - IF (LEFT): preconditions — what context enables this pattern
   - BECAUSE (EGO): rationale — why this pattern works
   - HOWEVER (RIGHT): risks — what could go wrong
   - THEN (NEXT): outcomes — what happens if pattern is followed
   - evidence (PHENOTYPE): supporting observations

   This function converts these to the 36-bit exotype representation
   and derives the physics family from the hexagram trace."
  [{:keys [if because however then evidence] :as pattern}]
  (let [;; Convert pattern fields to sigils via hash → index into 256 table
        field->sigil (fn [field]
                       (if field
                         (index->sigil (mod (Math/abs (hash field)) 256))
                         (index->sigil 0)))
        left (field->sigil if)
        ego (field->sigil because)
        right (field->sigil however)
        next-sigil (field->sigil then)
        phenotype (evidence->phenotype-family evidence)

        ;; Build context structure
        context {:context-sigils [left ego right next-sigil]
                 :phenotype-context phenotype}

        ;; Derive physics family
        physics-family (context->physics-family context)
        physics-params (when physics-family
                         (hexagram->physics-params (:hexagram-id physics-family)))]

    {:pattern-id (:id pattern)
     :context context
     :physics-family physics-family
     :physics-params physics-params
     :mapping {:left-from :if
               :ego-from :because
               :right-from :however
               :next-from :then
               :phenotype-from :evidence}}))

;; =============================================================================
;; 256 RUNNABLE EXOTYPES: Rule → Kernel + Params
;; =============================================================================

(def ^:private family->kernel
  "Map hexagram families to base kernel behaviors."
  {0 :blending           ; Creative (1-8) — creative mixing
   1 :multiplication     ; Taming (9-16) — conservative
   2 :ad-hoc-template    ; Following (17-24) — adaptive
   3 :blending-mutation  ; Innocence (25-32) — forward motion
   4 :blending-baldwin   ; Retreat (33-40) — phenotype-conditional
   5 :collection-template ; Decrease (41-48) — differentiating
   6 :mutating-template  ; Revolution (49-56) — transformation
   7 :blending})         ; Gentle (57-64) — gentle consolidation

(def ^:private energy->kernel-params
  "Energy modulation of kernel parameters."
  {:peng {:mutation-rate 0.30 :bit-bias :yang :template-strictness 0.5}
   :lu   {:mutation-rate 0.10 :bit-bias :preserve :template-strictness 0.8}
   :ji   {:mutation-rate 0.20 :bit-bias :selective :template-strictness 0.9}
   :an   {:mutation-rate 0.25 :bit-bias :momentum :template-strictness 0.6}})

(defn rule->kernel-spec
  "Map a physics rule (0-255) to a runnable kernel specification.

   Returns:
   {:kernel :kernel-keyword
    :params {:mutation-rate N :bit-bias K ...}
    :rule N
    :hexagram N
    :energy :key}"
  [rule]
  (let [{:keys [hexagram energy]} (hex-lift/rule->hexagram+energy rule)
        family (quot (dec hexagram) 8)
        base-kernel (get family->kernel family :mutating-template)
        energy-key (:key energy)
        energy-params (get energy->kernel-params energy-key {})]
    {:kernel base-kernel
     :params energy-params
     :rule rule
     :hexagram hexagram
     :energy energy-key
     :description (str (name base-kernel) " + " (:name energy))}))

(defn context->kernel-spec
  "Compute kernel specification from local 36-bit context.

   This is the key function for local exotype computation:
   context → hexagram → energy → rule → kernel + params"
  [context]
  (let [physics (hex-lift/context->physics-rule context)]
    (rule->kernel-spec (:rule physics))))

;; =============================================================================
;; LOCAL EVOLUTION (per-cell physics)
;; =============================================================================

(defn build-local-context
  "Build a 36-bit context from local CA neighborhood.

   Arguments:
   - pred: predecessor sigil (LEFT)
   - ego: self sigil (EGO)
   - succ: successor sigil (RIGHT)
   - prev: previous generation's value at this position (NEXT)
   - phenotype-family: 4-bit phenotype family (3 parents + child)"
  [pred ego succ prev phenotype-family]
  {:context-sigils [(or pred ca/default-sigil)
                    (or ego ca/default-sigil)
                    (or succ ca/default-sigil)
                    (or prev ego ca/default-sigil)]
   :phenotype-context (or phenotype-family "0000")})

(defn evolve-sigil-local
  "Evolve a single sigil using locally-computed physics rule.

   The 36-bit context (pred, ego, succ, prev, phenotype-family) determines
   which kernel and parameters to use for this specific cell.

   Returns {:sigil S :rule N :kernel K}"
  [ego pred succ prev phenotype-family kernel-fn-map]
  (let [context (build-local-context pred ego succ prev phenotype-family)
        {:keys [kernel params rule]} (context->kernel-spec context)
        kernel-fn (kernel-fn-for kernel-fn-map kernel)
        ;; Add params to context for kernel use
        ctx-with-params (merge context params)
        result (kernel-fn ego pred succ ctx-with-params)]
    {:sigil (:sigil result)
     :rule rule
     :kernel kernel}))

(defn evolve-string-local
  "Evolve entire genotype string with per-cell local physics.

   Each cell computes its own 36-bit context and applies the
   corresponding physics rule. Different cells may use different kernels.

   Arguments:
   - genotype: current generation string
   - phenotype: phenotype row (1 bit per cell; used to derive phenotype family)
   - prev-genotype: previous generation (for NEXT context)
   - kernel-fn-map: map of kernel-keyword → function

   Returns {:genotype S :rules [r0 r1 ...] :kernels [k0 k1 ...]}"
  [genotype phenotype prev-genotype kernel-fn-map]
  (let [len (count genotype)
        letters (vec (map str (seq genotype)))
        phe-row (or phenotype (apply str (repeat len "0")))
        phe-next (when phenotype
                   (ca/evolve-phenotype-against-genotype genotype phenotype))
        prev-letters (vec (map str (seq (or prev-genotype genotype))))
        default ca/default-sigil

        results
        (mapv (fn [idx]
                (let [pred (get letters (dec idx) default)
                      ego (get letters idx)
                      succ (get letters (inc idx) default)
                      prev (get prev-letters idx ego)
                      ;; Get phenotype family bits for this position
                      phe (phenotype-family-at phe-row phe-next idx)]
                  (evolve-sigil-local ego pred succ prev phe kernel-fn-map)))
              (range len))]
    {:genotype (apply str (map :sigil results))
     :rules (mapv :rule results)
     :kernels (mapv :kernel results)}))

;; =============================================================================
;; EXOTYPE COMPOSITION
;; =============================================================================

(defn compose-rules-sequential
  "Compose two rules sequentially: apply A then B.

   Returns a composite kernel function."
  [rule-a rule-b kernel-fn-map]
  (let [spec-a (rule->kernel-spec rule-a)
        spec-b (rule->kernel-spec rule-b)
        kernel-a (kernel-fn-for kernel-fn-map (:kernel spec-a))
        kernel-b (kernel-fn-for kernel-fn-map (:kernel spec-b))]
    (fn [sigil pred next context]
      (let [intermediate (kernel-a sigil pred next (merge context (:params spec-a)))
            final (kernel-b (:sigil intermediate) pred next (merge context (:params spec-b)))]
        final))))

(defn compose-rules-blend
  "Compose two rules by blending outputs.

   Each rule produces a result; final output is bit-wise majority vote."
  [rule-a rule-b kernel-fn-map]
  (let [spec-a (rule->kernel-spec rule-a)
        spec-b (rule->kernel-spec rule-b)
        kernel-a (kernel-fn-for kernel-fn-map (:kernel spec-a))
        kernel-b (kernel-fn-for kernel-fn-map (:kernel spec-b))]
    (fn [sigil pred next context]
      (let [result-a (kernel-a sigil pred next (merge context (:params spec-a)))
            result-b (kernel-b sigil pred next (merge context (:params spec-b)))
            bits-a (ca/bits-for (:sigil result-a))
            bits-b (ca/bits-for (:sigil result-b))
            ;; Bit-wise majority (with random tiebreaker)
            rng (:rng context)
            blended (apply str
                           (map (fn [a b]
                                  (cond (= a b) a
                                        (< (rand-double rng) 0.5) a
                                        :else b))
                                bits-a bits-b))]
        (ca/entry-for-bits blended)))))

(defn compose-rules-conditional
  "Compose rules conditionally: use A if condition, else B.

   Condition is a function (context) → boolean."
  [rule-a rule-b condition-fn kernel-fn-map]
  (let [spec-a (rule->kernel-spec rule-a)
        spec-b (rule->kernel-spec rule-b)
        kernel-a (kernel-fn-for kernel-fn-map (:kernel spec-a))
        kernel-b (kernel-fn-for kernel-fn-map (:kernel spec-b))]
    (fn [sigil pred next context]
      (if (condition-fn context)
        (kernel-a sigil pred next (merge context (:params spec-a)))
        (kernel-b sigil pred next (merge context (:params spec-b)))))))

(defn compose-rules-matrix
  "Compose rules via parameter matrix multiplication.

   The mutation and structure parameters are combined multiplicatively,
   giving a new effective parameter set."
  [rule-a rule-b]
  (let [params-a (rule->physics-params rule-a)
        params-b (rule->physics-params rule-b)
        ;; Multiplicative composition of normalized parameters
        composed-mutation (* (:mutation-bias params-a)
                             (:mutation-bias params-b))
        composed-structure (* (:structure-weight params-a)
                              (:structure-weight params-b))
        ;; Combined rule info
        composed-desc (str "(" (:description params-a) ") × ("
                           (:description params-b) ")")]
    {:rule-a rule-a
     :rule-b rule-b
     :mutation-bias composed-mutation
     :structure-weight composed-structure
     :description composed-desc}))

(defn make-composite-exotype
  "Create a composite exotype from multiple rules.

   composition-mode can be:
   - :sequential — apply rules in order
   - :blend — bit-wise blend outputs
   - :matrix — multiply parameters

   Returns a function (sigil pred next context) → result"
  [rules composition-mode kernel-fn-map]
  (case composition-mode
    :sequential
    (reduce (fn [composed-fn rule]
              (let [spec (rule->kernel-spec rule)
                    kernel-fn (kernel-fn-for kernel-fn-map (:kernel spec))]
                (fn [sigil pred next context]
                  (let [intermediate (composed-fn sigil pred next context)]
                    (kernel-fn (:sigil intermediate) pred next
                               (merge context (:params spec)))))))
            (fn [sigil _ _ _] {:sigil sigil})
            rules)

    :blend
    (fn [sigil pred next context]
      (let [results (map (fn [rule]
                           (let [spec (rule->kernel-spec rule)
                                 kernel-fn (kernel-fn-for kernel-fn-map (:kernel spec))]
                             (kernel-fn sigil pred next (merge context (:params spec)))))
                         rules)
            all-bits (map #(ca/bits-for (:sigil %)) results)
            ;; Majority vote per bit position
            rng (:rng context)
            blended (apply str
                           (for [i (range 8)]
                             (let [votes (map #(nth % i) all-bits)
                                   ones (count (filter #(= \1 %) votes))
                                   zeros (count (filter #(= \0 %) votes))]
                               (cond (> ones zeros) \1
                                     (< ones zeros) \0
                                     :else (rand-nth* rng [\0 \1])))))]
        (ca/entry-for-bits blended)))

    :matrix
    (let [composed-params (reduce (fn [acc rule]
                                    (let [params (rule->physics-params rule)]
                                      {:mutation-bias (* (:mutation-bias acc)
                                                         (:mutation-bias params))
                                       :structure-weight (* (:structure-weight acc)
                                                            (:structure-weight params))}))
                                  {:mutation-bias 1.0 :structure-weight 1.0}
                                  rules)
          ;; Use first rule's kernel as base
          base-spec (rule->kernel-spec (first rules))
          base-kernel (kernel-fn-for kernel-fn-map (:kernel base-spec))]
      (fn [sigil pred next context]
        (base-kernel sigil pred next (merge context composed-params))))))

;; =============================================================================
;; GLOBAL + LOCAL BENDING
;; =============================================================================

(defn make-bent-evolution
  "Create an evolution function where a global rule bends all local rules.

   The global rule provides a 'stance' or 'physics mode' for the run.
   Each cell still computes its local rule from its 36-bit context.
   The effective rule at each cell is the composition of global and local.

   Arguments:
   - global-rule: The global physics rule (0-255) or a keyword like :baldwin
   - bend-mode: How to compose (:sequential, :blend, :matrix)
   - kernel-fn-map: Map of kernel keywords to functions
   - rng (optional): java.util.Random for reproducible randomness

   Returns a function (genotype phenotype prev-genotype) → evolved-genotype"
  ([global-rule bend-mode kernel-fn-map]
   (make-bent-evolution global-rule bend-mode kernel-fn-map nil))
  ([global-rule bend-mode kernel-fn-map ^java.util.Random rng]
   (let [;; Resolve global rule if it's a keyword
         global-rule-num (cond
                           (number? global-rule) global-rule
                           (= global-rule :baldwin) 148  ; Family 4 + Péng
                           (= global-rule :blending) 0   ; Family 0 + Péng
                           (= global-rule :creative) 0
                           (= global-rule :conservative) 40  ; Family 1 + Péng
                           (= global-rule :adaptive) 68  ; Family 2 + Péng
                           (= global-rule :transformative) 200  ; Family 6 + Péng
                           :else 0)
         global-spec (rule->kernel-spec global-rule-num)
         global-kernel (kernel-fn-for kernel-fn-map (:kernel global-spec))
         global-params (:params global-spec)
         global-physics (rule->physics-params global-rule-num)]

     (fn [genotype phenotype prev-genotype]
       (let [len (count genotype)
             letters (vec (map str (seq genotype)))
             phe-row (or phenotype (apply str (repeat len "0")))
             phe-next (when phenotype
                        (ca/evolve-phenotype-against-genotype genotype phenotype))
             prev-letters (vec (map str (seq (or prev-genotype genotype))))
             default ca/default-sigil]

             (->> (range len)
                  (map (fn [idx]
                         (let [pred (get letters (dec idx) default)
                               ego (get letters idx)
                               succ (get letters (inc idx) default)
                           prev (get prev-letters idx ego)
                           phe (phenotype-family-at phe-row phe-next idx)

                               ;; Compute local physics once per cell (rule + kernel spec).
                               local-ctx (assoc (build-local-context pred ego succ prev phe) :rng rng)
                               _ (when trace-bent-cell?
                                   (printf "[bent] idx=%d phase=local-physics-start%n" idx)
                                   (flush))
                               local-physics (hex-lift/context->physics-rule local-ctx)
                               local-rule (:rule local-physics)
                               local-spec (rule->kernel-spec local-rule)
                               _ (when trace-bent-cell?
                                   (printf "[bent] idx=%d phase=local-physics-done rule=%d kernel=%s%n"
                                           idx (long local-rule) (name (:kernel local-spec)))
                                   (flush))

                           ;; Bend local with global
                           result (case bend-mode
                                    ;; Sequential: global then local
                                    :sequential
                                    (let [intermediate (global-kernel ego pred succ
                                                                      (merge local-ctx global-params))
                                          local-kernel (kernel-fn-for kernel-fn-map (:kernel local-spec))]
                                      (local-kernel (:sigil intermediate) pred succ
                                                    (merge local-ctx (:params local-spec))))

                                    ;; Blend: average global and local outputs
                                    :blend
                                    (let [global-result (global-kernel ego pred succ
                                                                       (merge local-ctx global-params))
                                          local-kernel (kernel-fn-for kernel-fn-map (:kernel local-spec))
                                          local-result (local-kernel ego pred succ
                                                                     (merge local-ctx (:params local-spec)))
                                          bits-g (ca/bits-for (:sigil global-result))
                                          bits-l (ca/bits-for (:sigil local-result))
                                          blended (apply str
                                                         (map (fn [g l]
                                                                (cond (= g l) g
                                                                      (< (rand-double rng) 0.5) g
                                                                      :else l))
                                                              bits-g bits-l))]
                                      (ca/entry-for-bits blended))

                                    ;; Matrix: compose parameters, use global kernel
                                    :matrix
                                    (let [local-params (rule->physics-params local-rule)
                                          composed-params {:mutation-bias (* (:mutation-bias global-physics 0.5)
                                                                             (:mutation-bias local-params))
                                                           :structure-weight (* (:structure-weight global-physics 0.5)
                                                                                (:structure-weight local-params))}]
                                      (global-kernel ego pred succ (merge local-ctx composed-params)))

                                    ;; Default: just use local
                                    (let [local-kernel (kernel-fn-for kernel-fn-map (:kernel local-spec))]
                                      (local-kernel ego pred succ (merge local-ctx (:params local-spec)))))]
                       (:sigil result))))
              (apply str)))))))
(defn evolve-with-global-exotype
  "Evolve a genotype using a global exotype that bends local physics.

   This is the main entry point for MMCA runs with exotype physics.

   Arguments:
   - genotype: Current generation string
   - phenotype: Phenotype row (1 bit per cell; used to derive phenotype family)
   - prev-genotype: Previous generation (for NEXT context)
   - global-rule: Global physics rule (number or keyword)
   - bend-mode: How global bends local (:sequential, :blend, :matrix)
   - kernel-fn-map: Map of kernel keywords to functions
   - rng (optional): java.util.Random for reproducible randomness

   Returns the evolved genotype string."
  ([genotype phenotype prev-genotype global-rule bend-mode kernel-fn-map]
   (let [evolve-fn (make-bent-evolution global-rule bend-mode kernel-fn-map)]
     (evolve-fn genotype phenotype prev-genotype)))
  ([genotype phenotype prev-genotype global-rule bend-mode kernel-fn-map ^java.util.Random rng]
   (let [evolve-fn (make-bent-evolution global-rule bend-mode kernel-fn-map rng)]
     (evolve-fn genotype phenotype prev-genotype))))

(defn apply-exotype
  "Rewrite a kernel spec using an exotype and a sampled context.

   DEPRECATED: This function modifies a GLOBAL kernel, not per-cell physics.
   Use evolve-string-local or evolve-with-global-exotype for the new system.

   The full 36-bit context determines physics via:
   - Hexagram (from eigenvalue diagonalization) = situation (1 of 64)
   - Primary energy (from phenotype family bits 0-1) = engagement mode (1 of 4)
   - Combined: physics rule (1 of 256)

   The exotype sigil provides additional local modulation."
  {:deprecated "2026-01-24"}
  [kernel exotype context ^java.util.Random rng]
  (when (and kernel exotype context)
    (let [;; Extract full 256-rule physics from context
          physics-family (context->physics-family context)
          physics-params (when physics-family
                           (rule->physics-params (:rule physics-family)))

          ;; EGO-derived params (8-bit modulation within physics)
          {:keys [tier params]} (resolve-exotype exotype)
          params (or params {})

          ;; Physics rule's mutation-bias drives update probability
          base-prob (if physics-params
                      (:mutation-bias physics-params)
                      (or (:update-prob params) 1.0))
          update-prob (double base-prob)
          update? (< (.nextDouble rng) update-prob)

          ;; Merge physics-level and sigil-level params
          ctx (cond-> context
                (contains? params :match-threshold) (assoc :match-threshold (:match-threshold params))
                (contains? params :invert-on-phenotype?) (assoc :invert-on-phenotype? (:invert-on-phenotype? params))
                (contains? params :rotation) (assoc :rotation (:rotation params))
                ;; Add physics info to context for downstream use
                physics-family (assoc :physics-family physics-family)
                physics-params (assoc :physics-params physics-params))

          spec (ca/kernel-spec-for kernel)
          spec (if update?
                 (ca/mutate-kernel-spec-contextual spec ctx)
                 spec)
          spec (if (= :super tier)
                 (assoc spec
                        :mix-mode (:mix-mode params)
                        :mix-shift (:mix-shift params))
                 spec)]
      (-> (ca/normalize-kernel-spec spec)
          (assoc :label nil)
          ;; Record which physics rule was active
          (cond-> physics-family (assoc :physics-rule (:rule physics-family)))))))

;; =============================================================================
;; RUNTIME INVARIANT CHECKING
;; =============================================================================

(def ^:dynamic *exotype-system*
  "Which exotype system is active.

   Valid values:
   - :local-physics — per-cell 36-bit physics (correct)
   - :global-kernel — old global kernel steering (deprecated)"
  :global-kernel)

(defn assert-local-physics!
  "Throw if local physics is not active.

   Call this at the start of scoring/evaluation to ensure
   we're not accidentally scoring runs from the old system."
  []
  (when (not= *exotype-system* :local-physics)
    (throw (ex-info "Local physics not active! Cannot trust run results."
                    {:exotype-system *exotype-system*
                     :expected :local-physics
                     :hint "Use evolve-string-local or evolve-with-global-exotype"}))))

(defn run-metadata
  "Generate metadata for a run indicating which exotype system was used.

   Include this in run results for Invariant 4 verification."
  []
  {:exotype-system *exotype-system*
   :timestamp (System/currentTimeMillis)
   :version "2026-01-24"})

(defmacro with-local-physics
  "Execute body with local physics enabled.

   This sets *exotype-system* to :local-physics so that
   assert-local-physics! will pass."
  [& body]
  `(binding [*exotype-system* :local-physics]
     ~@body))

(defn validate-run-metadata
  "Check that run metadata indicates local physics was used.

   Returns true if valid, throws if invalid."
  [run-result]
  (let [meta (:exotype-metadata run-result)]
    (cond
      (nil? meta)
      (throw (ex-info "Run missing exotype metadata! Cannot trust results."
                      {:run-keys (keys run-result)
                       :hint "Run was produced by old system"}))

      (not= :local-physics (:exotype-system meta))
      (throw (ex-info "Run used deprecated exotype system!"
                      {:exotype-system (:exotype-system meta)
                       :expected :local-physics}))

      :else true)))

(defn summarize-rules
  "Summarize the physics rules used in a run.

   Returns a map with:
   - :total — number of cells evolved
   - :unique — number of distinct rules used
   - :rule-counts — frequency map of rules
   - :kernel-counts — frequency map of kernels"
  [{:keys [rules kernels]}]
  (when (seq rules)
    {:total (count rules)
     :unique (count (set rules))
     :rule-counts (frequencies rules)
     :kernel-counts (frequencies kernels)}))

(defn log-rule-distribution
  "Print a summary of physics rules used in a run.

   Use this to verify Invariant 1: rules should vary by cell."
  [{:keys [rules kernels] :as result}]
  (let [summary (summarize-rules result)]
    (when summary
      (println "=== Exotype Rule Distribution ===")
      (println "Total cells:" (:total summary))
      (println "Unique rules:" (:unique summary))
      (println "Rule diversity:" (float (/ (:unique summary) (:total summary))))
      (println "Kernels used:" (keys (:kernel-counts summary)))
      summary)))
