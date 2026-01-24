(ns futon5.xenotype.generator
  "Generator component implementations for xenotype wiring diagrams.

   These implement the Level 2-5 primitives from metaca-terminal-vocabulary-v2.md."
  (:require [futon5.ca.core :as ca]))

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
        bits (clojure.string/replace bits " " "0")]
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
                                   :or (if (or (= x \1) (= y \1)) \1 \0)))
                               bits-a bits-b))]
    (bits->sigil result)))

(defn- clamp [x lo hi]
  (max lo (min hi x)))

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

   :same?
   (fn [{:keys [a b]} _ _]
     {:equal (= a b)})

   :balance
   (fn [{:keys [sigil]} _ _]
     (let [ones (hamming-weight sigil)]
       {:bal (/ (- (* 2 ones) 8) 8.0)}))

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
         output-registry))
