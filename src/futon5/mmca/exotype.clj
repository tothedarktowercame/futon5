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

   The hexagram (diagonal trace) identifies which of 64 physics families."
  [context]
  (when context
    (let [hexagram (hex-lift/context->hexagram context)
          hex-num (or (:number hexagram) 0)]
      {:hexagram-id hex-num
       :hexagram-name (:name hexagram)
       :lines (:lines hexagram)
       ;; Coarse grouping: 64 hexagrams → 8 families (like eight energies)
       :energy-family (keyword (str "family-" (inc (quot (dec (max 1 hex-num)) 8))))})))

(defn hexagram->physics-params
  "Map a hexagram to physics parameters.

   This is the key function that defines stable physics from the 36-bit context.
   The hexagram determines behavior; the EGO sigil only modulates within that."
  [hexagram-id]
  (let [;; Group into 8 families (0-7) based on hexagram number
        family (if (and hexagram-id (pos? hexagram-id))
                 (quot (dec hexagram-id) 8)
                 0)]
    (case family
      ;; Family 0 (hex 1-8): Creative/Receptive — expansion
      0 {:physics-mode :expansion
         :mutation-bias 0.7
         :structure-weight 0.3
         :description "Creative expansion"}

      ;; Family 1 (hex 9-16): Small Taming — conservation
      1 {:physics-mode :conservation
         :mutation-bias 0.2
         :structure-weight 0.8
         :description "Conservation and accumulation"}

      ;; Family 2 (hex 17-24): Following — adaptation
      2 {:physics-mode :adaptation
         :mutation-bias 0.5
         :structure-weight 0.5
         :description "Following and adaptation"}

      ;; Family 3 (hex 25-32): Innocence — momentum
      3 {:physics-mode :momentum
         :mutation-bias 0.6
         :structure-weight 0.4
         :description "Innocent forward motion"}

      ;; Family 4 (hex 33-40): Retreat — phenotype-conditional
      4 {:physics-mode :conditional
         :mutation-bias 0.4
         :structure-weight 0.5
         :phenotype-weight 0.6
         :description "Retreat and conditioning"}

      ;; Family 5 (hex 41-48): Decrease — differentiation
      5 {:physics-mode :differentiation
         :mutation-bias 0.5
         :structure-weight 0.3
         :description "Decrease and differentiation"}

      ;; Family 6 (hex 49-56): Revolution — transformation
      6 {:physics-mode :transformation
         :mutation-bias 0.8
         :structure-weight 0.2
         :description "Revolution and transformation"}

      ;; Family 7 (hex 57-64): Gentle — consolidation
      7 {:physics-mode :consolidation
         :mutation-bias 0.3
         :structure-weight 0.7
         :description "Gentle consolidation"}

      ;; Default
      {:physics-mode :neutral
       :mutation-bias 0.5
       :structure-weight 0.5
       :description "Neutral dynamics"})))

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

   The full 36-bit context determines physics family via hexagram.
   The exotype sigil provides local modulation within that family."
  [kernel exotype context ^java.util.Random rng]
  (when (and kernel exotype context)
    (let [;; Extract full 36-bit physics family from context
          physics-family (context->physics-family context)
          physics-params (when physics-family
                           (hexagram->physics-params (:hexagram-id physics-family)))

          ;; EGO-derived params (8-bit modulation within physics family)
          {:keys [tier params]} (resolve-exotype exotype)
          params (or params {})

          ;; Physics family's mutation-bias overrides update-prob when available
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
                ;; Add physics family info to context for downstream use
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
          ;; Record which physics family was active
          (cond-> physics-family (assoc :physics-family (:hexagram-id physics-family)))))))
