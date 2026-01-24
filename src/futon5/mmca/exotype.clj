(ns futon5.mmca.exotype
  "Exotypes: kernel-rewrite operators derived from sigils.

   IMPORTANT: An exotype is 36 bits, not 8:
   - LEFT (8): IF / preconditions
   - EGO (8): BECAUSE / rationale (the sigil)
   - RIGHT (8): HOWEVER / risks
   - NEXT (8): THEN / outcomes
   - PHENOTYPE (4): evidence

   The hexagram (diagonal trace of the 6x6 matrix) identifies the physics family.
   See hexagram/lift.clj for the full structure."
  (:require [futon5.ca.core :as ca]
            [futon5.hexagram.lift :as hex-lift]))

(def ^:private mix-modes
  [:none :rotate-left :rotate-right :reverse :xor-neighbor :majority :swap-halves :scramble])

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
                    (str (bit-at phe-row (dec x))
                         (bit-at phe-row x)
                         (bit-at phe-row (inc x))
                         (bit-at phe-next x))))]
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
   - :phenotype-context — 4 bits from phenotype layer

   The hexagram (from eigenvalue diagonalization) identifies the situation.
   The primary energy (from phenotype bits) identifies the engagement mode.
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
        phenotype (if evidence
                    (apply str (take 4 (map #(if % \1 \0) evidence)))
                    "0000")

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

(defn apply-exotype
  "Rewrite a kernel spec using an exotype and a sampled context.

   The full 36-bit context determines physics via:
   - Hexagram (from eigenvalue diagonalization) = situation (1 of 64)
   - Primary energy (from phenotype bits) = engagement mode (1 of 4)
   - Combined: physics rule (1 of 256)

   The exotype sigil provides additional local modulation."
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
