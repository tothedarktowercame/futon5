(ns scirepro.engine
  "Minimal reproduction engine for the 1D MetaCA dynamics in arXiv:1502.00130v1.

   The neighborhood order follows futon5/256ca.el rather than Wolfram's
   descending ECA order: 000,001,010,100,011,101,110,111."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def truth-table-3 ["000" "001" "010" "100" "011" "101" "110" "111"])

(def triple->index
  (zipmap truth-table-3 (range)))

(defn- validate-rule! [rule]
  (when-not (and (integer? rule) (<= 0 rule 255))
    (throw (ex-info "rule must be an integer in [0,255]" {:rule rule}))))

(defn- rule->bits-raw [rule]
  (mapv (fn [shift] (bit-and 1 (bit-shift-right rule shift)))
        (range 7 -1 -1)))

(def rule-bits-table
  (mapv rule->bits-raw (range 256)))

(defn rule->bits
  "Return the eight-bit representation used by 256ca.el, MSB first."
  [rule]
  (validate-rule! rule)
  (nth rule-bits-table rule))

(defn bits->rule
  [bits]
  (when-not (= 8 (count bits))
    (throw (ex-info "rule bit vector must have length 8" {:bits bits})))
  (reduce (fn [acc bit]
            (when-not (or (= bit 0) (= bit 1))
              (throw (ex-info "rule bit must be 0 or 1" {:bit bit :bits bits})))
            (+ (* 2 acc) bit))
          0
          bits))

(defn rule->bit-string [rule]
  (apply str (rule->bits rule)))

(defn bit-string->rule [s]
  (bits->rule (mapv (comp parse-long str) s)))

(defn- bit-at [rule i]
  (nth (rule->bits rule) i))

(defn- local-rule-bit [center-rule left-bit center-bit right-bit]
  (let [triple (str left-bit center-bit right-bit)
        idx (get triple->index triple)]
    (when (nil? idx)
      (throw (ex-info "unknown neighborhood triple" {:triple triple})))
    (nth (rule->bits center-rule) idx)))

(defn multiply-cell
  "S3.1 no-blending update: the central cell's rule is applied bitwise
   to the same allele positions of left, center, and right neighbors."
  [left-rule center-rule right-rule]
  (bits->rule
   (mapv (fn [i]
           (local-rule-bit center-rule
                           (bit-at left-rule i)
                           (bit-at center-rule i)
                           (bit-at right-rule i)))
         (range 8))))

(defn blend-cell
  "S3.2 blending update. If the left and right neighbor alleles agree,
   their shared bit is copied; otherwise the S3.1 local rule is used."
  [left-rule center-rule right-rule]
  (bits->rule
   (mapv (fn [i]
           (let [left-bit (bit-at left-rule i)
                 right-bit (bit-at right-rule i)]
             (if (= left-bit right-bit)
               left-bit
               (local-rule-bit center-rule left-bit (bit-at center-rule i) right-bit))))
         (range 8))))

(defn step
  ([row] (step row :multiply))
  ([row dynamic]
   (let [update-cell (case dynamic
                       :multiply multiply-cell
                       :blend blend-cell)]
     (mapv (fn [i center-rule]
             (let [left-rule (if (zero? i) 0 (nth row (dec i)))
                   right-rule (if (= i (dec (count row))) 0 (nth row (inc i)))]
               (update-cell left-rule center-rule right-rule)))
           (range)
           row))))

(defn evolve
  "Return rows 0..steps inclusive."
  ([initial steps] (evolve initial steps :multiply))
  ([initial steps dynamic]
   (when-not (and (integer? steps) (not (neg? steps)))
     (throw (ex-info "steps must be a non-negative integer" {:steps steps})))
   (vec (take (inc steps) (iterate #(step % dynamic) (vec initial))))))

(defn seeded-ic
  [seed width]
  (let [rng (java.util.Random. (long seed))]
    (mapv (fn [_] (.nextInt rng 256)) (range width))))

(defn seeded-phenotype-ic
  [seed width]
  (let [rng (java.util.Random. (long seed))]
    (mapv (fn [_] (.nextInt rng 2)) (range width))))

(defn save-ic! [path seed width]
  (let [f (io/file path)
        ic (seeded-ic seed width)]
    (.mkdirs (.getParentFile f))
    (spit f (with-out-str (prn {:seed seed :width width :ic ic})))
    ic))

(defn save-phenotype-ic! [path seed width]
  (let [f (io/file path)
        ic (seeded-phenotype-ic seed width)]
    (.mkdirs (.getParentFile f))
    (spit f (with-out-str (prn {:seed seed :width width :ic ic})))
    ic))

(defn read-ic-meta
  "Read a persisted IC artifact as its full {:seed :width :ic} map."
  [path]
  (edn/read-string (slurp path)))

(defn read-ic [path]
  (:ic (read-ic-meta path)))

(defn first-stasis-time
  "First t > 0 whose row is identical to t-1, or nil if none is observed."
  [rows]
  (first (keep-indexed (fn [idx [a b]]
                         (when (= a b) (inc idx)))
                       (partition 2 1 rows))))

(defn first-band-time
  "First t whose row remains unchanged for WINDOW consecutive rows.
   This is a deterministic stable-band proxy: every column is constant
   across the window starting at t."
  [rows window]
  (when-not (pos-int? window)
    (throw (ex-info "window must be a positive integer" {:window window})))
  (first (keep-indexed
          (fn [idx segment]
            (let [row (first segment)]
              (when (every? #(= row %) segment)
                idx)))
          (partition window 1 rows))))

(defn shannon-entropy
  "Base-2 entropy of the values in xs."
  [xs]
  (let [n (double (count xs))]
    (if (zero? n)
      0.0
      (->> xs
           frequencies
           vals
           (map (fn [count]
                  (let [p (/ count n)]
                    (- (* p (/ (Math/log p) (Math/log 2.0)))))))
           (reduce + 0.0)))))

(defn change-rate
  "Fraction of positions that differ between two equal-width rows."
  [row-a row-b]
  (when-not (= (count row-a) (count row-b))
    (throw (ex-info "rows must have equal width"
                    {:left (count row-a) :right (count row-b)})))
  (let [n (count row-a)]
    (if (zero? n)
      0.0
      (/ (count (filter false? (map = row-a row-b)))
         (double n)))))

(defn mutual-information
  "Empirical base-2 mutual information between paired categorical rows."
  [xs ys]
  (when-not (= (count xs) (count ys))
    (throw (ex-info "rows must have equal width"
                    {:left (count xs) :right (count ys)})))
  (let [n (double (count xs))]
    (if (zero? n)
      0.0
      (let [px (frequencies xs)
            py (frequencies ys)
            pxy (frequencies (map vector xs ys))]
        (reduce (fn [acc [[x y] cxy]]
                  (let [p-xy (/ cxy n)
                        p-x (/ (get px x) n)
                        p-y (/ (get py y) n)]
                    (+ acc (* p-xy (/ (Math/log (/ p-xy (* p-x p-y)))
                                      (Math/log 2.0))))))
                0.0
                pxy)))))

(defn rotate-row [row n]
  (let [v (vec row)
        c (count v)]
    (if (zero? c)
      v
      (let [shift (mod n c)]
        (vec (concat (subvec v shift) (subvec v 0 shift)))))))

(defn eca-next-bit
  "Fixed-rule ECA using the same elisp neighborhood order for comparability."
  [rule left center right]
  (local-rule-bit rule left center right))

(defn eca-step
  [rule row]
  (mapv (fn [i center]
          (let [left (if (zero? i) 0 (nth row (dec i)))
                right (if (= i (dec (count row))) 0 (nth row (inc i)))]
            (eca-next-bit rule left center right)))
        (range)
        row))

(defn eca-evolve
  [rule initial-bits steps]
  (vec (take (inc steps) (iterate #(eca-step rule %) (vec initial-bits)))))

(defn phenotype-step
  "Update phenotype bits using each cell's current genotype rule.
   Boundary phenotype neighbors are fixed 0, matching 256ca.el."
  [genotype-row phenotype-row]
  (when-not (= (count genotype-row) (count phenotype-row))
    (throw (ex-info "genotype and phenotype rows must have equal width"
                    {:genotype (count genotype-row)
                     :phenotype (count phenotype-row)})))
  (mapv (fn [i rule center]
          (let [left (if (zero? i) 0 (nth phenotype-row (dec i)))
                right (if (= i (dec (count phenotype-row))) 0 (nth phenotype-row (inc i)))]
            (eca-next-bit rule left center right)))
        (range)
        genotype-row
        phenotype-row))

(defn coupled-step
  "Figure-4 deterministic pheno-geno step.
   Phenotype is updated from the old genotype and old phenotype first;
   genotype then takes one S3.2 blending step from the old genotype."
  [{:keys [genotype phenotype]}]
  {:genotype (step genotype :blend)
   :phenotype (phenotype-step genotype phenotype)})

(defn coupled-evolve
  "Return coupled rows 0..steps inclusive as
   {:genotype [...rows...] :phenotype [...rows...]}."
  [genotype phenotype steps]
  (when-not (= (count genotype) (count phenotype))
    (throw (ex-info "genotype and phenotype ICs must have equal width"
                    {:genotype (count genotype)
                     :phenotype (count phenotype)})))
  (let [states (vec (take (inc steps)
                          (iterate coupled-step
                                   {:genotype (vec genotype)
                                    :phenotype (vec phenotype)})))]
    {:genotype (mapv :genotype states)
     :phenotype (mapv :phenotype states)}))

(defn phenotype-evolve-under-genotype
  "Phenotype baseline: evolve phenotype under a frozen genotype row."
  [genotype phenotype steps]
  (vec (take (inc steps)
             (iterate #(phenotype-step genotype %) (vec phenotype)))))

(defn genotype->initial-bits
  "Project a genotype IC to an ECA binary IC using bit position 0.
   This keeps baselines tied to the same saved ICs without adding RNG."
  [ic]
  (mapv #(bit-at % 0) ic))

(def wolfram-table-3
  "Wolfram's descending neighborhood order, kept for convention contrast."
  ["111" "110" "101" "100" "011" "010" "001" "000"])

(defn- rule-bit-under
  "Bit of RULE for the neighborhood (L C R) with the byte read in ORDER."
  [rule order l c r]
  (nth (rule->bits rule) (get (zipmap order (range)) (str l c r))))

(defn blending-censored-rule-23-proof
  "S5.3 claim, checked non-circularly: for every center rule C and every
   allele position, the blend-cell output bit equals Rule 23's own table
   entry on the neighbor-match triples (left = right) and C's entry on the
   others — i.e. blending IS 'Rule 23 censored by local logic'. The actual
   side comes from blend-cell on constructed bytes; the expected side reads
   Rule 23's bit table, so the two sides are independent.

   The identity holds when Rule 23's byte is read in the 256ca.el
   truth-table-3 order and FAILS under Wolfram's descending order (returned
   as :wolfram-descending) — the S5.3 'Rule 23' label is convention-
   dependent, the same finding family as A1."
  []
  (let [const-byte (fn [bit] (if (= 1 bit) 255 0))
        run (fn [order]
              (let [results
                    (for [center (range 256)
                          l [0 1]
                          r [0 1]
                          :let [out (blend-cell (const-byte l) center (const-byte r))]
                          i (range 8)]
                      (let [actual (nth (rule->bits out) i)
                            c (nth (rule->bits center) i)
                            expected (if (= l r)
                                       (rule-bit-under 23 order l c r)
                                       (local-rule-bit center l c r))]
                        (= actual expected)))]
                {:cases (count results)
                 :passed (count (filter true? results))
                 :ok? (every? true? results)}))]
    (assoc (run truth-table-3)
           :wolfram-descending (run wolfram-table-3))))

(defn palette-color
  [rule]
  (format "#%02x%02x%02x" rule rule rule))

(defn grid->svg
  ([rows] (grid->svg rows {:cell 3 :palette palette-color}))
  ([rows {:keys [cell palette] :or {cell 3 palette palette-color}}]
   (let [h (count rows)
         w (count (first rows))
         rects (for [[y row] (map-indexed vector rows)
                     [x v] (map-indexed vector row)]
                 (format "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"%s\"/>"
                         (* x cell) (* y cell) cell cell (palette v)))]
     (str "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 "
          (* w cell) " " (* h cell)
          "\" width=\"" (* w cell) "\" height=\"" (* h cell)
          "\" role=\"img\">"
          (str/join rects)
          "</svg>"))))

;;; ---------------------------------------------------------------------------
;;; Mutation engine (slice 4a) — explicit event-stream consumer.
;;;
;;; Resolution of ambiguity A6 (ground truth = 256ca.el):
;;;  - `mutate-rule-n` (256ca.el:571-591) flips N bits at (random 8) positions —
;;;    uniform across all 8 rule-bit positions.
;;;  - `evolve-sigil-with-blending-mutation` (256ca.el:595-627) computes the
;;;    blend output THEN applies mutate-rule-n with mutation=1 — **mutation is
;;;    AFTER the evolve step**, exactly one flip per cell per generation.
;;;  - `evolve-sigil-with-mutating-template` (256ca.el:990-1065, the DEFAULT
;;;    `evolve-sigil-fn` at line 1069) applies `balance-mutation` (256ca.el:971-986)
;;;    AFTER the evolve step: with probability 1/20, if the byte has >6 ones flip
;;;    a randomly chosen 1-bit; if <2 ones flip a randomly chosen 0-bit.
;;;  - The Figure-8 "first-bit-only" mutation described in the paper prose has
;;;    **NO code implementation** in 256ca.el.  See the A6 ledger entry.
;;;
;;; The mutation layer here models the simplest, most fundamental elisp variant:
;;; `evolve-sigil-with-blending-mutation` — blend step followed by exactly one
;;; random bit flip per cell per generation (the `mutation 1` path at line 596).
;;; The `balance-mutation` and first-bit-only variants are parameterised so the
;;; notebook (slice 4b) can sweep them without touching RNG internals.

(defn flip-bit
  "Flip allele index I (0-7, MSB-first as in rule->bits) of RULE byte."
  [rule i]
  (validate-rule! rule)
  (when-not (and (integer? i) (<= 0 i 7))
    (throw (ex-info "allele index must be 0-7" {:index i})))
  (let [mask (bit-shift-left 1 (- 7 i))]
    (bit-xor rule mask)))

(defn apply-flips
  "Apply a sequence of allele-index flips to a rule byte, left to right."
  [rule allele-indexes]
  (reduce flip-bit rule allele-indexes))

;; --- mutation event streams --------------------------------------------------
;;;
;;; A mutation stream is a persisted EDN artifact listing the (generation, cell,
;;; allele) flip events that the engine consumes.  Streams are generated by a
;;; seeded generator at a given per-cell-per-generation flip probability, then
;;; saved under resources/mutation-streams/ with the seed in the filename.
;;; The evolve-with-mutation function is then fully deterministic given (IC,
;;; stream) — there is no hidden RNG in the dynamics path.

(defn generate-mutation-stream
  "Generate a lazy sequence of {:generation :cell :allele} flip events for a
   grid of WIDTH cells over GENERATIONS generations (1..generations), at
   per-cell-per-generation rate RATE (a double in [0,1]).

   MODE controls the allele-selection distribution:
     :uniform      — allele drawn uniform from 0..7 (mutate-rule-n, line 573)
     :first-bit    — allele is always 0 (Figure-8 skewed variant; prose-only)
     :balance      — allele chosen among the majority bits of that cell's
                     *current* rule; the RATE here is the gate probability
                     (1/20 in balance-mutation).  Because balance-mutation is
                     state-dependent it cannot be a pure event stream; this
                     mode is handled separately by evolve-with-balance-mutation.

   For :uniform and :first-bit the stream is a pure function of (seed, width,
   generations, rate) and is the artifact that makes the dynamics deterministic."
  ([seed width generations rate]
   (generate-mutation-stream seed width generations rate :uniform))
  ([seed width generations rate mode]
   (when-not (and (integer? width) (pos? width))
     (throw (ex-info "width must be a positive integer" {:width width})))
   (when-not (and (integer? generations) (pos? generations))
     (throw (ex-info "generations must be a positive integer" {:generations generations})))
   (when-not (and (number? rate) (<= 0.0 (double rate) 1.0))
     (throw (ex-info "rate must be in [0,1]" {:rate rate})))
   (let [rng (java.util.Random. (long seed))
         ;; We draw a random double per (generation, cell) to decide whether
         ;; a flip occurs, then draw the allele — matching the conceptual
         ;; structure of mutate-rule-n (gate then position).  This ordering is
         ;; what makes the stream reproducible.
         threshold (double rate)]
     (case mode
       :first-bit
       (for [gen (range 1 (inc generations))
             cell (range width)
             :let [r (.nextDouble rng)]
             :when (< r threshold)]
         {:generation gen :cell cell :allele 0})
       :uniform
       (for [gen (range 1 (inc generations))
             cell (range width)
             :let [r (.nextDouble rng)]
             :when (< r threshold)]
         {:generation gen :cell cell :allele (.nextInt rng 8)})))))

(defn mutation-stream->path
  "Convention: resources/mutation-streams/<prefix>-seed-<seed>-w<width>-g<gens>-r<rate>-<mode>.edn"
  [prefix seed width generations rate mode]
  (format "resources/mutation-streams/%s-seed-%d-w%d-g%d-r%s-%s.edn"
          prefix seed width generations rate (name mode)))

(defn save-mutation-stream!
  "Generate and persist a mutation stream; return the path and metadata."
  ([prefix seed width generations rate]
   (save-mutation-stream! prefix seed width generations rate :uniform))
  ([prefix seed width generations rate mode]
   (let [events (vec (generate-mutation-stream seed width generations rate mode))
         path (mutation-stream->path prefix seed width generations rate mode)
         artifact {:seed seed :width width :generations generations
                   :rate (double rate) :mode mode :events events}]
     (.mkdirs (java.io.File. (.getParentFile (java.io.File. path))))
     (spit path (with-out-str (prn artifact)))
     {:path path :artifact artifact})))

(defn read-mutation-stream
  "Read a persisted mutation stream artifact."
  [path]
  (edn/read-string (slurp path)))

(defn stream->event-map
  "Index a mutation event stream into {generation {cell [alleles...]}}."
  [events]
  (reduce (fn [acc {:keys [generation cell allele]}]
            (update-in acc [generation cell] (fnil conj []) allele))
          {}
          events))

;; --- evolve-with-mutation ----------------------------------------------------

(defn- mutated-step
  "One generation of blend-dynamic + injected mutation events."
  [row generation event-map]
  (let [blended (step row :blend)
        gen-events (get event-map generation)]
    (if gen-events
      (mapv (fn [cell rule]
              (if-let [alleles (get gen-events cell)]
                (apply-flips rule alleles)
                rule))
            (range)
            blended)
      blended)))

(defn evolve-with-mutation
  "Evolve an IC under the blend dynamic with an injected mutation event stream.
   Returns rows 0..steps inclusive.  Fully deterministic given (IC, stream).

   STREAM may be either a sequence of {:generation :cell :allele} maps or a
   pre-indexed event-map (from stream->event-map)."
  ([initial steps stream]
   (when-not (and (integer? steps) (not (neg? steps)))
     (throw (ex-info "steps must be a non-negative integer" {:steps steps})))
   (let [event-map (if (map? stream)
                     stream
                     (stream->event-map stream))]
     (loop [rows [(vec initial)]
            gen 1]
       (if (> gen steps)
         (vec rows)
         (recur (conj rows (mutated-step (peek rows) gen event-map))
                (inc gen)))))))

;; --- Figure-8 first-bit-only convenience ------------------------------------
;;;
;;; The paper (Fig 8) describes an 'erroneously-programmed mutation' that 'only
;;; ever flips the first bit'.  This has no elisp implementation (A6 finding),
;;; but we provide it as a named variant so the notebook can measure its effect.

(defn evolve-with-first-bit-mutation
  "Evolve under blend dynamic where mutation only ever flips allele 0.
   Equivalent to evolve-with-mutation with a :first-bit stream."
  ([initial steps seed rate]
   (let [stream (generate-mutation-stream seed (count initial) steps rate :first-bit)]
     (evolve-with-mutation initial steps stream))))

;;; ---------------------------------------------------------------------------
;;; Balance-mutation mode (slice 4b) — state-dependent, seeded.
;;;
;;; Matches 256ca.el:971-986 (balance-mutation) semantics exactly:
;;;  - Gate: (random 20) < 1, i.e. probability 1/20 = 5%.
;;;  - The gate is only evaluated when popcount is >6 or <2 (elisp `and`
;;;    short-circuits on the popcount test first).
;;;  - If popcount > 6 AND gate passes: flip ONE uniformly-chosen 1-bit.
;;;  - If popcount < 2 AND gate passes: flip ONE uniformly-chosen 0-bit.
;;;  - Otherwise: byte unchanged.
;;;
;;; Because the allele selection depends on the CURRENT byte's popcount, this
;;; cannot be expressed as a pre-generated event stream (the 4a review finding).
;;; Instead it is deterministic given a persisted seed: the engine draws from
;;; java.util.Random(seed) in the same conceptual order as the elisp.
;;;
;;; Cross-check note: the elisp `balance-mutation` RNG consumption order is:
;;;   1. If ones > 6: draw (random 20) for the gate.
;;;      If gate passes: draw from randomize-sequence (Fisher-Yates over the
;;;      matching positions) — for quantity=1 this is len(matching) draws of
;;;      (random k) for k = len, len-1, ..., 1.
;;;   2. Elif ones < 2: draw (random 20) for the gate.
;;;      If gate passes: same randomize-sequence draws over 0-bit positions.
;;;   3. Else: no draw.
;;; The cross-check shadows the elisp random with a scripted sequence matched
;;; to the Clojure engine's draw order (see mutation_cross_check.clj).

(defn- popcount
  "Number of 1-bits in a rule byte (0-255)."
  [rule]
  (Integer/bitCount (int rule)))

(defn- bit-positions
  "Return allele indexes (0-7, MSB-first) where the bit equals VALUE."
  [rule value]
  (keep-indexed (fn [i b] (when (= b value) i))
                (rule->bits rule)))

(defn- select-among
  "Select one element from COLL using RNG (uniform).  Matches the elisp
   randomize-sequence + nthcdr truncation: for quantity=1 this is equivalent
   to picking a uniform random element."
  [^java.util.Random rng coll]
  (let [v (vec coll)
        n (count v)]
    (if (zero? n)
      nil
      (nth v (.nextInt rng n)))))

(defn balance-mutate-rule
  "Apply balance-mutation semantics to one rule byte, drawing from RNG.
   Returns the (possibly mutated) rule.  Matches 256ca.el:971-986.

   RNG consumption order (must match elisp for cross-check):
     1. If popcount > 6: draw gate = (.nextInt rng 20).
        If gate == 0: draw allele among 1-bit positions (uniform), flip it.
     2. Elif popcount < 2: draw gate = (.nextInt rng 20).
        If gate == 0: draw allele among 0-bit positions (uniform), flip it.
     3. Else: no draw (gate not evaluated — elisp `and` short-circuits)."
  [^java.util.Random rng rule]
  (let [ones (popcount rule)]
    (cond
      (and (> ones 6) (zero? (.nextInt rng 20)))
      (let [positions (bit-positions rule 1)
            allele (select-among rng positions)]
        (flip-bit rule allele))

      (and (< ones 2) (zero? (.nextInt rng 20)))
      (let [positions (bit-positions rule 0)
            allele (select-among rng positions)]
        (flip-bit rule allele))

      :else rule)))

(defn- balance-mutation-step
  "One generation of DYNAMIC + balance-mutation.  DYNAMIC may be :blend or
   :multiply (the elisp default evolve-sigil-with-mutating-template falls
   back to local-rule lookup = :multiply when context is nil)."
  [row _generation rng dynamic]
  (let [evolved (step row dynamic)]
    (mapv #(balance-mutate-rule rng %) evolved)))

(defn evolve-with-balance-mutation
  "Evolve an IC under DYNAMIC (:blend or :multiply) with balance-mutation
   applied after each step.  Deterministic given (IC, seed).
   Returns rows 0..steps inclusive.

   This matches the elisp default evolve-sigil-fn (evolve-sigil-with-
   mutating-template) when context is nil: the template falls back to local
   rule lookup, then balance-mutation is applied.  See A6/A7 ledger entries."
  ([initial steps seed]
   (evolve-with-balance-mutation initial steps seed :blend))
  ([initial steps seed dynamic]
   (when-not (and (integer? steps) (not (neg? steps)))
     (throw (ex-info "steps must be a non-negative integer" {:steps steps})))
   (let [rng (java.util.Random. (long seed))]
     (loop [rows [(vec initial)]
            gen 1]
       (if (> gen steps)
         (vec rows)
         (recur (conj rows (balance-mutation-step (peek rows) gen rng dynamic))
                (inc gen)))))))

;; --- uniform-random-replacement null model (C4 baseline) --------------------

(defn- random-replace-step
  "One generation of DYNAMIC + uniform-random-replacement at RATE.
   Each cell, with probability RATE, has its rule replaced by a fresh uniform
   random byte (0-255).  This is the generic-noise null for C4."
  [row _generation rng dynamic rate]
  (let [evolved (step row dynamic)
        threshold (double rate)]
    (mapv (fn [rule]
            (if (< (.nextDouble rng) threshold)
              (.nextInt rng 256)
              rule))
          evolved)))

(defn evolve-with-random-replacement
  "Evolve an IC under DYNAMIC with uniform-random-replacement at RATE.
   Deterministic given (IC, seed).  Returns rows 0..steps inclusive.
   This is the C4 null model: replaces the cell's rule with a fresh uniform
   byte at the same event rate, distinguishing structured mutation from
   generic noise."
  ([initial steps seed rate]
   (evolve-with-random-replacement initial steps seed rate :blend))
  ([initial steps seed rate dynamic]
   (when-not (and (integer? steps) (not (neg? steps)))
     (throw (ex-info "steps must be a non-negative integer" {:steps steps})))
   (let [rng (java.util.Random. (long seed))]
     (loop [rows [(vec initial)]
            gen 1]
       (if (> gen steps)
         (vec rows)
         (recur (conj rows (random-replace-step (peek rows) gen rng dynamic rate))
                (inc gen)))))))

;; --- coupled evolution with mutation (C6) -----------------------------------

(defn- coupled-mutation-step
  "Coupled pheno-geno step with first-bit-only mutation on the genotype.
   Phenotype is updated from old genotype + old phenotype first;
   genotype takes one blend step, then first-bit mutation is applied
   via the injected stream."
  [{:keys [genotype phenotype]} generation event-map]
  (let [blended (step genotype :blend)
        gen-events (get event-map generation)
        new-genotype (if gen-events
                       (mapv (fn [cell rule]
                               (if-let [alleles (get gen-events cell)]
                                 (apply-flips rule alleles)
                                 rule))
                             (range)
                             blended)
                       blended)
        new-phenotype (phenotype-step genotype phenotype)]
    {:genotype new-genotype :phenotype new-phenotype}))

(defn coupled-evolve-with-mutation
  "Coupled pheno-geno evolution with first-bit-only mutation on the genotype.
   STREAM is a sequence of {:generation :cell :allele} maps.
   Returns {:genotype [...rows...] :phenotype [...rows...]}.
   Used for C6 (Figure-8 first-bit-only variant on coupled runs)."
  [genotype phenotype steps stream]
  (when-not (= (count genotype) (count phenotype))
    (throw (ex-info "genotype and phenotype ICs must have equal width"
                    {:genotype (count genotype)
                     :phenotype (count phenotype)})))
  (let [event-map (stream->event-map stream)]
    (loop [states [{:genotype (vec genotype) :phenotype (vec phenotype)}]
           gen 1]
      (if (> gen steps)
        {:genotype (mapv :genotype states)
         :phenotype (mapv :phenotype states)}
        (recur (conj states
                     (coupled-mutation-step (peek states) gen event-map))
               (inc gen))))))
