(ns futon5.xenotype.generator
  "Generator component implementations for xenotype wiring diagrams.

   These implement the Level 2-5 primitives from metaca-terminal-vocabulary-v2.md."
  (:require [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]))

;;; ============================================================
;;; Helpers
;;; ============================================================

(defn- sigil->bits
  "Convert sigil to 8-bit string."
  [sigil]
  (or (ca/bits-for sigil) "00000000"))

(defn- bits->sigil
  "Find sigil string for given bits, or nearest match."
  [bits]
  (let [entry (or (ca/entry-for-bits bits)
                  (ca/entry-for-bits "00000000"))]
    ;; Return just the sigil string, not the full entry map
    (if (map? entry)
      (:sigil entry)
      entry)))

(defn- sigil->int
  "Convert sigil to integer [0,255]."
  [sigil]
  (let [bits (sigil->bits sigil)]
    (Integer/parseInt bits 2)))

(defn- int->sigil
  "Convert integer to sigil."
  [n]
  (let [n (mod (int n) 256)
        bits (format "%8s" (Integer/toBinaryString n))
        bits (str/replace bits " " "0")]
    (bits->sigil bits)))

(defn- hamming-weight
  "Count 1-bits in sigil."
  [sigil]
  (count (filter #(= % \1) (sigil->bits sigil))))

(defn- hamming-distance
  "Count differing bits between two sigils."
  [a b]
  (let [bits-a (sigil->bits a)
        bits-b (sigil->bits b)]
    (count (filter true? (map not= bits-a bits-b)))))

(defn- bit-op
  "Apply bitwise operation to two sigils."
  [op a b]
  (let [bits-a (sigil->bits a)
        bits-b (sigil->bits b)
        result (apply str (map (fn [x y]
                                 (case op
                                   :xor (if (= x y) \0 \1)
                                   :and (if (and (= x \1) (= y \1)) \1 \0)
                                   :or (if (or (= x \1) (= y \1)) \1 \0)
                                   :nand (if (and (= x \1) (= y \1)) \0 \1)
                                   :nor (if (or (= x \1) (= y \1)) \0 \1)))
                               bits-a bits-b))]
    (bits->sigil result)))

(defn- clamp [x lo hi]
  (max lo (min hi x)))

(defn- band-score [x center width]
  (when (and (number? x) (pos? (double width)))
    (max 0.0 (- 1.0 (/ (Math/abs (- x center)) (double width))))))

(defn- resolve-exotype-param
  [params]
  (let [exotype (cond
                  (map? (:exotype params)) (:exotype params)
                  (:exotype-sigil params) {:sigil (:exotype-sigil params)
                                           :tier (:exotype-tier params)}
                  :else nil)
        exotype (cond-> exotype
                  (:exotype-params params) (assoc :params (:exotype-params params)))]
    (when exotype
      (exotype/resolve-exotype exotype))))

(defn- seeded-rng
  [state pred self succ prev phe]
  (java.util.Random.
   (long (hash [(or (:seed state) 0)
                (:tick state)
                (:x state)
                pred self succ prev phe]))))

(defn- legacy-kernel-step
  [{:keys [pred self succ prev phe state]} params _]
  (let [kernel (or (:kernel params) :mutating-template)
        exo (resolve-exotype-param params)
        ctx (exotype/build-local-context pred self succ prev phe)
        rng (when exo (seeded-rng state pred self succ prev phe))
        kernel-spec (ca/kernel-spec-for kernel)
        kernel-spec (if exo
                      (exotype/apply-exotype kernel-spec exo ctx rng)
                      kernel-spec)
        kernel-fn (ca/kernel-fn kernel-spec)
        result (kernel-fn self pred succ ctx)]
    {:result (:sigil result)}))

(defn- new-kernel-step
  [{:keys [pred self succ prev phe]} _ _]
  (let [result (exotype/evolve-sigil-local self pred succ prev phe ca/kernels)]
    {:result (:sigil result)}))

;;; ============================================================
;;; Level 2: Sigil Operations (Atomic)
;;; ============================================================

(def level-2-registry
  {;; Representation
   :bits-for
   (fn [{:keys [sigil]} _ _]
     {:bits (sigil->bits sigil)})

   :entry-for-bits
   (fn [{:keys [bits]} _ _]
     {:sigil (bits->sigil bits)})

   :sigil-index
   (fn [{:keys [sigil]} _ _]
     {:index (sigil->int sigil)})

   :index-sigil
   (fn [{:keys [index]} params _]
     ;; Allow index from inputs or params
     (let [idx (or index (:index params) 0)]
       {:sigil (int->sigil idx)}))

   ;; Bitwise Operations
   :bit-xor
   (fn [{:keys [a b]} _ _]
     {:result (bit-op :xor a b)})

   :bit-and
   (fn [{:keys [a b]} _ _]
     {:result (bit-op :and a b)})

   :bit-or
   (fn [{:keys [a b]} _ _]
     {:result (bit-op :or a b)})

   :bit-nand
   (fn [{:keys [a b]} _ _]
     {:result (bit-op :nand a b)})

   :bit-nor
   (fn [{:keys [a b]} _ _]
     {:result (bit-op :nor a b)})

   :bit-not
   (fn [{:keys [sigil]} _ _]
     (let [bits (sigil->bits sigil)
           flipped (apply str (map #(if (= % \1) \0 \1) bits))]
       {:result (bits->sigil flipped)}))

   :bit-shift-left
   (fn [{:keys [sigil n]} _ _]
     (let [bits (sigil->bits sigil)
           n (mod (int (or n 0)) 8)
           shifted (str (subs bits n) (subs bits 0 n))]
       {:result (bits->sigil shifted)}))

   :bit-shift-right
   (fn [{:keys [sigil n]} _ _]
     (let [bits (sigil->bits sigil)
           n (mod (int (or n 0)) 8)
           shifted (str (subs bits (- 8 n)) (subs bits 0 (- 8 n)))]
       {:result (bits->sigil shifted)}))

   ;; Arithmetic Operations (sigil-level, bit-mixing via carry chains)
   :sigil-add-mod
   (fn [{:keys [a b]} _ _]
     {:result (int->sigil (+ (sigil->int a) (sigil->int b)))})

   :sigil-sub-mod
   (fn [{:keys [a b]} _ _]
     {:result (int->sigil (+ (- (sigil->int a) (sigil->int b)) 256))})

   :sigil-mul-low
   (fn [{:keys [a b]} _ _]
     {:result (int->sigil (* (sigil->int a) (sigil->int b)))})

   :sigil-avg
   (fn [{:keys [a b]} _ _]
     {:result (int->sigil (quot (+ (sigil->int a) (sigil->int b)) 2))})

   ;; Bitwise Operations (no carry propagation)
   :sigil-xor
   (fn [{:keys [a b]} _ _]
     {:result (int->sigil (bit-xor (sigil->int a) (sigil->int b)))})

   ;; Aggregation Operations
   :majority
   (fn [{:keys [sigils]} _ _]
     (if (empty? sigils)
       {:result (bits->sigil "00000000")}
       (let [bit-lists (map sigil->bits sigils)
             n (count sigils)
             threshold (/ n 2.0)
             result (apply str
                           (for [i (range 8)]
                             (let [ones (count (filter #(= (nth % i) \1) bit-lists))]
                               (if (> ones threshold) \1 \0))))]
         {:result (bits->sigil result)})))

   :minority
   (fn [{:keys [sigils]} _ _]
     (if (empty? sigils)
       {:result (bits->sigil "11111111")}
       (let [bit-lists (map sigil->bits sigils)
             n (count sigils)
             threshold (/ n 2.0)
             result (apply str
                           (for [i (range 8)]
                             (let [ones (count (filter #(= (nth % i) \1) bit-lists))]
                               (if (< ones threshold) \1 \0))))]
         {:result (bits->sigil result)})))

   :blend
   (fn [{:keys [sigils weights]} _ _]
     (if (or (empty? sigils) (empty? weights))
       {:result (bits->sigil "00000000")}
       (let [n (min (count sigils) (count weights))
             sigils (take n sigils)
             weights (take n weights)
             total-weight (reduce + 0.0 weights)
             weights (if (pos? total-weight)
                       (map #(/ % total-weight) weights)
                       (repeat n (/ 1.0 n)))
             bit-lists (map sigil->bits sigils)
             result (apply str
                           (for [i (range 8)]
                             (let [weighted-sum (reduce + 0.0
                                                        (map (fn [bits w]
                                                               (* w (if (= (nth bits i) \1) 1.0 0.0)))
                                                             bit-lists weights))]
                               (if (>= weighted-sum 0.5) \1 \0))))]
         {:result (bits->sigil result)})))

   :random-pick
   (fn [{:keys [sigils]} _ _]
     (if (empty? sigils)
       {:result (bits->sigil "00000000")}
       {:result (rand-nth (vec sigils))}))

   :modal
   (fn [{:keys [sigils]} _ _]
     (if (empty? sigils)
       {:result (bits->sigil "00000000")}
       (let [freqs (frequencies sigils)
             [mode _] (apply max-key val freqs)]
         {:result mode})))

  ;; Comparison Operations
  :similarity
  (fn [{:keys [a b]} _ _]
    {:score (- 1.0 (/ (hamming-distance a b) 8.0))})

  :distance
  (fn [{:keys [a b]} _ _]
    {:dist (hamming-distance a b)})

  :stability-band
  (fn [{:keys [a b center width]} params _]
    (let [sim (- 1.0 (/ (hamming-distance a b) 8.0))
          center (double (or center (:center params) 0.6))
          width (double (or width (:width params) 0.3))
          score (band-score sim center width)]
      {:score score}))

  :same?
  (fn [{:keys [a b]} _ _]
    {:equal (= a b)})

  :int-gt?
  (fn [{:keys [a b]} _ _]
    {:above (> (int (or a 0)) (int (or b 0)))})

   :balance
   (fn [{:keys [sigil]} _ _]
     (let [ones (hamming-weight sigil)]
       {:bal (/ (- (* 2 ones) 8) 8.0)}))

   :bit-test
   (fn [{:keys [sigil index]} _ _]
     (let [bits (sigil->bits sigil)
           idx (min 7 (max 0 (int (or index 0))))
           char-idx (- 7 idx)]  ;; bits string is MSB-first
       {:bit (= (nth bits char-idx) \1)}))

   ;; Mutation Operations
   :mutate
   (fn [{:keys [sigil rate]} _ _]
     (let [rate (double (or rate 0.1))
           bits (sigil->bits sigil)
           mutated (apply str
                          (map (fn [b]
                                 (if (< (rand) rate)
                                   (if (= b \1) \0 \1)
                                   b))
                               bits))]
       {:result (bits->sigil mutated)}))

   :mutate-toward
   (fn [{:keys [sigil target rate]} _ _]
     (let [rate (double (or rate 0.1))
           bits-s (sigil->bits sigil)
           bits-t (sigil->bits target)
           mutated (apply str
                          (map (fn [s t]
                                 (if (and (not= s t) (< (rand) rate))
                                   t
                                   s))
                               bits-s bits-t))]
       {:result (bits->sigil mutated)}))

   :crossover
   (fn [{:keys [a b point]} _ _]
     (let [point (clamp (int (or point 4)) 0 8)
           bits-a (sigil->bits a)
           bits-b (sigil->bits b)
           result (str (subs bits-a 0 point) (subs bits-b point))]
       {:result (bits->sigil result)}))

   :uniform-crossover
   (fn [{:keys [a b rate]} _ _]
     (let [rate (double (or rate 0.5))
           bits-a (sigil->bits a)
           bits-b (sigil->bits b)
           result (apply str
                         (map (fn [ba bb]
                                (if (< (rand) rate) bb ba))
                              bits-a bits-b))]
       {:result (bits->sigil result)}))})

;;; ============================================================
;;; Level 3: String/Population Operations
;;; ============================================================

(defn- shannon-entropy
  "Normalized Shannon entropy of sigil list."
  [sigils]
  (if (empty? sigils)
    0.0
    (let [n (count sigils)
          freqs (vals (frequencies sigils))
          probs (map #(/ % (double n)) freqs)
          max-entropy (Math/log (min n 256))
          entropy (- (reduce + 0.0 (map #(* % (Math/log %)) probs)))]
      (if (pos? max-entropy)
        (/ entropy max-entropy)
        0.0))))

(def level-3-registry
  {;; Entropy and Diversity
   :entropy
   (fn [{:keys [sigils]} _ _]
     {:score (shannon-entropy sigils)})

   :diversity
   (fn [{:keys [sigils]} _ _]
     (if (empty? sigils)
       {:score 0.0}
       {:score (/ (count (set sigils)) (double (count sigils)))}))

   :evenness
   (fn [{:keys [sigils]} _ _]
     (if (empty? sigils)
       {:score 0.0}
       (let [freqs (vals (frequencies sigils))
             n (count freqs)
             mean (/ (reduce + 0.0 freqs) n)
             variance (/ (reduce + 0.0 (map #(Math/pow (- % mean) 2) freqs)) n)
             std (Math/sqrt variance)
             cv (if (pos? mean) (/ std mean) 0.0)]
         {:score (max 0.0 (- 1.0 cv))})))

   :dominance
   (fn [{:keys [sigils]} _ _]
     (if (empty? sigils)
       {:score 0.0}
       (let [freqs (frequencies sigils)
             max-freq (apply max (vals freqs))]
         {:score (/ max-freq (double (count sigils)))})))

   ;; Allele Operations
   :allele-freq
   (fn [{:keys [sigils]} _ _]
     {:freq (frequencies sigils)})

   :allele-rank
   (fn [{:keys [sigils]} _ _]
     (if (empty? sigils)
       {:ranked []}
       (let [freqs (frequencies sigils)
             sorted (sort-by val > freqs)]
         {:ranked (mapv first sorted)})))

   :rare-alleles
   (fn [{:keys [sigils threshold]} _ _]
     (if (empty? sigils)
       {:rare []}
       (let [threshold (double (or threshold 0.1))
             n (count sigils)
             freqs (frequencies sigils)
             cutoff (* threshold n)]
         {:rare (vec (for [[s f] freqs :when (< f cutoff)] s))})))

   :common-alleles
   (fn [{:keys [sigils threshold]} _ _]
     (if (empty? sigils)
       {:common []}
       (let [threshold (double (or threshold 0.1))
             n (count sigils)
             freqs (frequencies sigils)
             cutoff (* threshold n)]
         {:common (vec (for [[s f] freqs :when (>= f cutoff)] s))})))

   ;; Pattern Matching
   :match-template
   (fn [{:keys [sigils template]} _ _]
     (if (or (empty? sigils) (empty? template))
       {:positions 0}
       (let [template (vec template)
             tlen (count template)
             slen (count sigils)
             matches (for [i (range (inc (- slen tlen)))
                           :when (= template (subvec (vec sigils) i (+ i tlen)))]
                       i)]
         {:positions (count matches)})))

   :find-repeats
   (fn [{:keys [sigils]} _ _]
     ;; Simple: count adjacent duplicates
     (if (< (count sigils) 2)
       {:count 0}
       (let [pairs (partition 2 1 sigils)
             repeats (count (filter (fn [[a b]] (= a b)) pairs))]
         {:count repeats})))

   :autocorr
   (fn [{:keys [sigils]} _ _]
     ;; Lag-1 autocorrelation based on similarity
     (if (< (count sigils) 2)
       {:score 0.0}
       (let [pairs (partition 2 1 sigils)
             sims (map (fn [[a b]] (- 1.0 (/ (hamming-distance a b) 8.0))) pairs)
             mean (/ (reduce + 0.0 sims) (count sims))]
         {:score mean})))

   ;; Aggregate Comparisons
   :hamming-dist
   (fn [{:keys [a b]} _ _]
     (if (or (empty? a) (empty? b))
       {:dist 0}
       (let [n (min (count a) (count b))]
         {:dist (reduce + 0 (map hamming-distance (take n a) (take n b)))})))

   :change-rate
   (fn [{:keys [a b]} _ _]
     (if (or (empty? a) (empty? b))
       {:rate 0.0}
       (let [n (min (count a) (count b))
             total-bits (* n 8)
             dist (reduce + 0 (map hamming-distance (take n a) (take n b)))]
         {:rate (/ dist (double total-bits))})))})

;;; ============================================================
;;; Level 4: Composition (Control Flow)
;;; ============================================================

(def level-4-registry
  {:if-then-else-sigil
   (fn [{:keys [cond then else]} _ _]
     {:result (if cond then else)})

   :threshold-sigil
   (fn [{:keys [score threshold above below]} _ _]
     {:result (if (>= (double (or score 0)) (double (or threshold 0.5)))
                above
                below)})

   :select-by-index
   (fn [{:keys [sigils index]} _ _]
     (if (empty? sigils)
       {:result (bits->sigil "00000000")}
       (let [idx (clamp (int (or index 0)) 0 (dec (count sigils)))]
         {:result (nth sigils idx)})))

   :select-by-score
   (fn [{:keys [sigils score]} _ _]
     (if (empty? sigils)
       {:result (bits->sigil "00000000")}
       (let [score (clamp (double (or score 0)) 0.0 1.0)
             idx (int (* score (dec (count sigils))))]
         {:result (nth sigils idx)})))})

;;; ============================================================
;;; Level 5: Hidden State Operations
;;; ============================================================

(defn- init-state []
  {:accum 0.0 :count 0 :bias nil :mode :normal :triggered false :cooldown 0})

(def level-5-registry
  {;; Accumulation
   :accum-add
   (fn [{:keys [state value decay]} _ _]
     (let [state (or state (init-state))
           decay (double (or decay 0.9))
           value (double (or value 0))
           new-accum (+ (* decay (:accum state 0.0)) value)]
       {:state (assoc state :accum new-accum)}))

   :accum-reset
   (fn [{:keys [state]} _ _]
     {:state (assoc (or state (init-state)) :accum 0.0)})

   :accum-read
   (fn [{:keys [state]} _ _]
     {:value (:accum (or state (init-state)) 0.0)})

   ;; Thresholds and Triggers
   :threshold?
   (fn [{:keys [state level]} _ _]
     (let [accum (:accum (or state (init-state)) 0.0)
           level (double (or level 0.5))]
       {:above (> accum level)}))

   :trigger-on
   (fn [{:keys [state condition]} _ _]
     (let [state (or state (init-state))]
       (if (and condition (not (:triggered state)) (zero? (:cooldown state 0)))
         {:state (assoc state :triggered true)}
         {:state state})))

   :triggered?
   (fn [{:keys [state]} _ _]
     {:triggered (:triggered (or state (init-state)) false)})

   :cooldown
   (fn [{:keys [state ticks]} _ _]
     (let [state (or state (init-state))
           ticks (int (or ticks 5))]
       {:state (-> state
                   (assoc :triggered false)
                   (assoc :cooldown ticks))}))

   ;; Preference/Bias
   :set-bias
   (fn [{:keys [state pattern]} _ _]
     {:state (assoc (or state (init-state)) :bias pattern)})

   :apply-bias
   (fn [{:keys [sigil state]} _ _]
     (let [state (or state (init-state))
           bias (:bias state)]
       (if bias
         ;; Blend sigil toward bias
         (let [bits-s (sigil->bits sigil)
               bits-b (sigil->bits bias)
               result (apply str
                             (map (fn [s b]
                                    (if (< (rand) 0.3) b s))
                                  bits-s bits-b))]
           {:result (bits->sigil result)})
         {:result sigil})))

   :learn-bias
   (fn [{:keys [state outcome]} _ _]
     ;; Placeholder: could update bias based on outcome
     {:state (or state (init-state))})})

;;; ============================================================
;;; Context Extraction
;;; ============================================================

(def context-registry
  {:context-pred
   (fn [{:keys [ctx]} _ _]
     {:sigil (:pred ctx)})

   :context-self
   (fn [{:keys [ctx]} _ _]
     {:sigil (:self ctx)})

   :context-succ
   (fn [{:keys [ctx]} _ _]
     {:sigil (:succ ctx)})

   :context-prev
   (fn [{:keys [ctx]} _ _]
     {:sigil (:prev ctx)})

   :context-phe
   (fn [{:keys [ctx]} _ _]
     {:bits (:phe ctx)})

   :context-neighbors
   (fn [{:keys [ctx]} _ _]
     {:sigils [(:pred ctx) (:self ctx) (:succ ctx)]})})

;;; ============================================================
;;; Output
;;; ============================================================

(def output-registry
  {:output-sigil
   (fn [{:keys [sigil]} _ _]
     {:out sigil})

   :output-with-state
   (fn [{:keys [sigil state]} _ _]
     {:out sigil :state-out state})})

(def kernel-registry
  {:legacy-kernel-step legacy-kernel-step
   :new-kernel-step new-kernel-step})

;;; ============================================================
;;; MetaCA core dynamics (the sci-repro cohort's occupants)
;;; ============================================================
;;
;; Added 2026-07-15 so the sci-repro wirings (nb01-nb04) actually EXECUTE.
;; They were written in the shared :diagram schema, and their headers claim "any
;; of these diagrams can be crossed over" — but they referenced five components
;; that did not exist, so they threw "Unknown component" while the ladder ran
;; fine. The unified footing was a claim about the schema, not a fact about the
;; runtime. These five make it a fact.
;;
;; CONVENTION: these read ca/core's tables, which are now WOLFRAM-STANDARD
;; (ca/core.clj:12, standardised 2026-07-15). So these components are the
;; paper's STRUCTURE (multiply / blend / mutate) under the standard order — they
;; are NOT a bit-exact reproduction of arXiv:1502.00130's figures, which used a
;; neighbourhood order with positions 3/4 swapped. The bit-exact reproduction
;; lives in the separate `scirepro` engine and stays pinned to the legacy order,
;; because its evidence IS grid-identity with the original 2014 elisp; changing
;; it would destroy the thing it exists to demonstrate.
;;
;; Under the standard order the paper's S5.3 identity restates cleanly:
;; "blending is Rule 23 censored by local logic" becomes "blending is Rule 240
;; censored by local logic" — and Rule 240 is the LEFT-SHIFT rule (output = left
;; neighbour). The 23 was an artifact of the ordering; the shift is the content.

(defn- sigil-bits [s] (ca/bits-for (str s)))

(defn- metaca-combine
  "One MetaCA cell update, bitwise over the 8 allele positions.

   :multiply (S3.1) — the centre's own rule is applied to the (left, centre,
     right) bits at each allele position.
   :blend (S3.2) — where the neighbours AGREE, copy their shared bit (this is
     the shift); otherwise fall back to the S3.1 local rule."
  [mode pred self succ]
  (let [lb (sigil-bits pred), cb (sigil-bits self), rb (sigil-bits succ)]
    (ca/sigil-for
     (apply str
            (for [i (range 8)]
              (let [l (nth lb i), c (nth cb i), r (nth rb i)]
                (if (and (= mode :blend) (= l r))
                  (Character/digit ^char l 2)
                  (ca/evolve-digits-by-rule l c r cb))))))))

;;; ============================================================
;;; The propagator: rule-permute
;;; ============================================================
;;
;; Added 2026-07-15 (M-propagators). This is the occupant of the `mutate` hole in
;; DarkTower/MetaCAExample.lean's comb:
;;   cellUpdate = (readPhe ⅋ readL ⅋ readC ⅋ readR) ◁ combine ◁ MUTATE ◁ write
;; nb01's header says it "leaves mutate empty". This fills it.
;;
;; WHAT IT IS. Reconstructing a 2014 Emacs off-by-one (the bug that produced
;; Figure 8 of arXiv:1502.00130) showed that mutation can be a CONSTRAINT
;; PROPAGATOR rather than noise: pick a neighbourhood at random and copy the
;; INVERSE of its response into another neighbourhood's response. Ordinary
;; mutation flips bits independently — a random walk with no attractor. A
;; propagator COUPLES the rule's responses and has a fixed point, which selects
;; the rule the landscape settles on. 112 of 202 sampled permutations produce a
;; persistent structured regime; the live regime is generic, the dead set is rare.
;;
;; *** THE PARAMETER IS A MAP OVER NEIGHBOURHOODS, NOT A VECTOR OVER BIT POSITIONS.
;;
;; This is not a style choice, it is forced. Two facts:
;;   1. codex-4 PROVED (commit dbb8453, exhaustive over 256 bytes x 8 actions) that
;;      the live twin (0 2 4 6)(1 3 5 7) and the dead twin (0 1 2 3)(4 5 6 7) are
;;      EXACTLY CONJUGATE as isolated systems — τ = [0 4 1 5 2 6 3 7] maps every
;;      transition of one onto the other. So every intrinsic property of σ-as-a-
;;      permutation-of-positions is identical between a σ that lives and one that
;;      dies. Position indices carry NO information about the outcome. The
;;      mechanism is necessarily relational: it is about WHICH NEIGHBOURHOODS get
;;      coupled.
;;   2. This repo now hosts TWO neighbourhood conventions. futon5's ca/core was
;;      standardised to Wolfram order on 2026-07-15; the vendored 2014 elisp uses
;;      the 256ca.el order. Only 2 of 8 bit positions mean the same neighbourhood
;;      in both. A σ expressed as positions is therefore a DIFFERENT OPERATOR in
;;      each engine — it would port silently and wrongly, and every measured
;;      live/dead result would quietly mean nothing here.
;;
;; So the parameter is {"000" "010", "001" "100", ...}: convention-independent,
;; portable, and it says what the operator actually does. Callers holding a legacy
;; position-vector must convert THROUGH the source engine's truth table; see
;; `positional-sigma->neighbourhood-sigma`.
;;
;; NOTE the operator is stochastic (one random neighbourhood per application) and
;; routes its draw through ca/rnd-int, so a run is reproducible under ca/with-seed
;; and unseeded behaviour is byte-preserved.

(defn positional-sigma->neighbourhood-sigma
  "Convert a legacy position-vector σ (e.g. [2 3 4 5 6 7 0 1] = 'rotate +2') into a
   neighbourhood map, THROUGH the truth table of the engine it was measured in.

   `source-table` is that engine's neighbourhood order. For results measured in the
   vendored 2014 elisp this is
     [\"000\" \"001\" \"010\" \"100\" \"011\" \"101\" \"110\" \"111\"]
   which is NOT ca/truth-table-3. Passing ca/truth-table-3 here for an elisp-derived
   σ is the silent-port bug this function exists to prevent."
  [sigma source-table]
  (into {} (map-indexed (fn [k nbhd] [nbhd (nth source-table (nth sigma k))])
                        source-table)))

(defn- rule-permute
  "One application: copy ¬(response to a random neighbourhood) into the response of
   the neighbourhood σ maps it to. `sigma` maps neighbourhood -> neighbourhood."
  [rule-bits sigma]
  (let [k (ca/rnd-int 8)
        src (nth ca/truth-table-3 k)
        dst (get sigma src src)
        di (.indexOf ^java.util.List (vec ca/truth-table-3) dst)
        v (if (= \0 (nth rule-bits k)) \1 \0)]
    (if (neg? di)
      rule-bits
      (str (subs rule-bits 0 di) v (subs rule-bits (inc di))))))

(defn- local-condition
  "Evaluate a named local predicate over the per-cell context. This is what a
   COMPOSITIONAL exotype conditions on -- the same information a single propagator
   ignores. Returns true/false.

   :boredom   phenotype neighbourhood [prev self next] is uniform -- nothing is
              happening locally. This is the futon5 reconstruction of baldwin's
              gate: baldwin counts how many old-context values equal the new state
              and mutates that many times, i.e. bored -> act, interesting -> hold.
              (Baldwin's is temporal, over the prior context; this is the spatial
              proxy available in a single cell update. Same SHAPE, not bit-identical.)
   :active    the negation -- the local phenotype is varied (something happening).
   :dense     the self genotype's rule has > 4 one-bits (a 'busy' local rule)."
  [cond-kw ctx]
  (let [phe (:phe ctx)
        uniform? (or (nil? phe) (apply = phe))]
    (case cond-kw
      :boredom uniform?
      :active  (not uniform?)
      :dense   (> (count (filter #{\1} (sigil-bits (or (:self ctx) ca/default-sigil)))) 4)
      uniform?)))

(def propagator-registry
  {:rule-permute
   (fn [{:keys [rule]} params _]
     (let [sigma (:sigma params)
           n (long (or (:applications params) 1))]
       {:result (ca/sigil-for
                 (reduce (fn [bits _] (rule-permute bits sigma))
                         (sigil-bits rule)
                         (range n)))}))

   ;; A COMPOSITIONAL exotype: two propagators under one local condition.
   ;; Added 2026-07-16 (M-propagators, "by example"). Joe's conjecture: baldwin and
   ;; blend are not primitives, they are COMPOSED from propagators -- baldwin is
   ;; literally switch(local-condition, mutate, hold). This is the general form.
   ;;   apply sigma-a when (local-condition ctx) holds, else sigma-b.
   ;; With sigma-b = identity and condition = :boredom, this IS baldwin's control
   ;; structure in the propagator basis. With (builder, collapser) it is a
   ;; homeostat; with (chaos, collapser) an annealer. The point of the demo is that
   ;; a switch can settle where NEITHER branch settles alone.
   :rule-permute-switch
   (fn [{:keys [rule ctx]} params _]
     (let [pred? (local-condition (:condition params :boredom) ctx)
           sigma (if pred? (:sigma-a params) (:sigma-b params))
           n (long (or (:applications params) 1))]
       {:result (ca/sigil-for
                 (reduce (fn [bits _] (rule-permute bits sigma))
                         (sigil-bits rule)
                         (range n)))}))})

(def metaca-registry
  {:multiply-cell
   (fn [{:keys [pred self succ]} _ _]
     {:result (metaca-combine :multiply pred self succ)})

   :blend-cell
   (fn [{:keys [pred self succ]} _ _]
     {:result (metaca-combine :blend pred self succ)})

   :mutate-combined-rule
   ;; nb04's mutate leg: 256ca.el mutate-rule-n over the COMBINED rule.
   ;; Draws go through ca/rnd-int, so a run is reproducible under ca/with-seed
   ;; and unseeded behaviour is byte-preserved.
   (fn [{:keys [rule]} params _]
     (let [n (long (or (:mutations params) 1))]
       {:result (ca/sigil-for (ca/mutate-rule-n (sigil-bits rule) n))}))

   :phenotype-step
   ;; One phenotype bit, updated by the cell's own genotype rule applied to the
   ;; phenotype neighbourhood. `phe` is the (prev self next) bit triple; absent
   ;; neighbours take :boundary-phenotype (256ca.el pins this to 0).
   (fn [{:keys [rule phe]} params _]
     (let [b (str (or (:boundary-phenotype params) 0))
           [l c r] (if (sequential? phe) phe [b (str phe) b])]
       {:result (ca/evolve-digits-by-rule l c r (sigil-bits rule))}))

   :output-phenotype
   (fn [{:keys [bits]} _ _]
     {:out bits})})

;;; ============================================================
;;; Combined Registry
;;; ============================================================

(def generator-registry
  "All generator component implementations."
  (merge level-2-registry
         level-3-registry
         level-4-registry
         level-5-registry
         context-registry
         kernel-registry
         metaca-registry
         propagator-registry
         output-registry))
