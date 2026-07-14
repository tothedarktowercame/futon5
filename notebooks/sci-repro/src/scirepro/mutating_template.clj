(ns scirepro.mutating-template
  "CONTEXTUAL port of `evolve-sigil-with-mutating-template`
  (256ca.el:990-1065) — the phenotype-driven mutating template path —
  plus the coupled driver mirroring `evolve-sigil-string-contextually`
  (256ca.el:1109) and `co-evolve-phenotype-and-genotype`
  (256ca.el:1192).

  SCOPE (R-repro-5b): the CONTEXT != nil branch only.  When CONTEXT is
  supplied (a 4-int quadruple: 3 old phenotype cells + 1 new state, boundary
  cells 0) the function builds a 4-candidate template from the context and
  uses it to drive the per-bit combine, falling back to the local rule on
  no match.  The non-contextual path (context=nil => template=nil => pure
  local rule) is R-repro-5 (zai-6, engine.clj) and is NOT duplicated here.

  This namespace deliberately does NOT edit engine.clj.  It requires
  scirepro.engine for the reusable helpers (rule<->bits, multiply-cell /
  local-rule-bit, phenotype-step, flip-bit, truth-table-3) and carries its
  OWN copy of `balance-mutation` (flagged :balance-mutation/dedupe-candidate
  for the reviewer to fold into engine.clj alongside zai-6's copy).

  TEMPLATE CONSTRUCTION (256ca.el:1023-1033):
    actual = context quadruple (4 ints)
    template = [actual,
                (bitflip actual)           ; flip ALL bits
                (bitflip actual '(1))      ; flip index 1 only
                (bitflip actual '(0 2 3))] ; flip indices 0,2,3
  where bitflip (256ca.el:747-767): no arg => flip every element (mod 2);
  list arg => flip exactly those indices.  Each candidate is a 4-int vector.

  PER-BIT COMBINE (256ca.el:1039-1064):
    For each of the 8 (parent, self, next) bit-triples i:
      parent-generation = [p-bit, s-bit, n-bit]   ; the first 3 of a candidate
      scan template in order; on the first candidate whose (butlast candidate 1)
      (= first 3 elements) EQUALS parent-generation, use (car (last candidate))
      (= 4th element) as the output bit.
      If no candidate matches, fall back to the local rule: the center
      genotype's bit at the truth-table-3 index of the (p,s,n) triple
      (= engine/local-rule-bit / multiply-cell).

  TAIL (256ca.el:1065): (get-genotype-from-rule (balance-mutation output)).
  balance-mutation (256ca.el:971-986) is ported below.

  DRIVER: `evolve-sigil-string-contextually` (256ca.el:1109) threads the
  phenotype context quadruples.  For a sigil string of length L:
    - cell 0 (head) and cell L-1 (tail) are evolved with NO context
      (3-arg evolve-sigil-fn => template=nil => local rule + balance-mutation).
    - middle cells 1..L-2 are evolved with a context quadruple built from
      the wrapped old/new phenotype landscapes (boundary 0 on each side):
        old-w = [0, old[0], ..., old[L-1], 0]   (length L+2)
        new-w = [0, new[0], ..., new[L-1], 0]   (length L+2)
        quad[k] = [old-w[k], old-w[k+1], old-w[k+2], new-w[k]]
      and middle cell at string-index i (1..L-2) receives quad[i-1].
      (cl `map` stops at the shortest seq, so only the first L-2 quads are
      consumed; the trailing two are unused — reproduced exactly.)

  The multi-generation loop is `co-evolve-phenotype-and-genotype`
  (256ca.el:1192) iterated by `run-for-generations-3` (256ca.el:1217):
    new-phenotype = phenotype-step(old-genotype, old-phenotype)
    new-genotype  = evolve-sigil-string-contextually(old-genotype,
                                                     old-phenotype,
                                                     new-phenotype)
  i.e. the OLD phenotype is the old-landscape and the just-computed NEW
  phenotype is the new-landscape.  This is the coupled pheno->geno dynamic."
  (:require [scirepro.engine :as engine]))

;; ---------------------------------------------------------------------------
;; bitflip (256ca.el:747-767) — operates on a vector of 0/1 ints.
;; ---------------------------------------------------------------------------

(defn- bitflip-all
  "Flip every element of a 0/1 int vector (mod 2).  Mirrors (bitflip the-list)."
  [xs]
  (mapv #(mod (inc ^long %) 2) xs))

(defn- bitflip-indices
  "Flip exactly the elements at INDICES of a 0/1 int vector (mod 2).
  Mirrors (bitflip the-list flip-here).  INDICES are 0-based and unique;
  the elisp mutates in `mapc` order over flip-here, but since each flip is
  an independent xor on a distinct position the result is order-independent."
  [xs indices]
  (let [idx-set (set indices)]
    (map-indexed (fn [i x]
                   (if (idx-set i)
                     (mod (inc ^long x) 2)
                     x))
                 xs)))

(defn- bitflip
  "Port of elisp bitflip (256ca.el:747-767).  XS is a vector of 0/1 ints.
  INDICES nil => flip all; otherwise flip exactly those indices."
  ([xs] (bitflip-all xs))
  ([xs indices] (bitflip-indices xs indices)))

;; ---------------------------------------------------------------------------
;; Template construction (256ca.el:1023-1033).
;; ---------------------------------------------------------------------------

(defn build-template
  "Build the 4-candidate template from a CONTEXT quadruple (vector of 4 0/1 ints).
  Returns a vector of 4 candidate vectors, each length 4:
    [actual, (bitflip actual), (bitflip actual [1]), (bitflip actual [0 2 3])].
  Mirrors 256ca.el:1023-1033 exactly."
  [context]
  (let [actual (vec context)]
    [(vec actual)
     (vec (bitflip actual))
     (vec (bitflip actual [1]))
     (vec (bitflip actual [0 2 3]))]))

;; ---------------------------------------------------------------------------
;; Per-bit combine (256ca.el:1039-1064).
;; ---------------------------------------------------------------------------

(defn- template-match
  "Scan TEMPLATE in order; return the 4th element of the first candidate whose
  first 3 elements EQUAL parent-generation (a 3-element vector), else nil.
  Mirrors the elisp while-loop: (equal parent-generation (butlast candidate 1))
  => (car (last candidate))."
  [template parent-generation]
  (reduce (fn [_ candidate]
            (if (= parent-generation (subvec candidate 0 3))
              (reduced (nth candidate 3))
              nil))
          nil
          template))

(defn combine-with-template
  "Compute the new 8-bit genotype byte for a cell given left/center/right rule
  bytes and a CONTEXT quadruple (or nil).

  When CONTEXT is non-nil, build the template and for each allele position i
  (0..7) try to match the (left-bit, center-bit, right-bit) triple against the
  template candidates' first-3; on match use the candidate's 4th element as the
  output bit, else fall back to the center genotype's local-rule bit
  (engine/local-rule-bit).  When CONTEXT is nil the template is nil and every
  position falls back to the local rule (= engine/multiply-cell).

  Returns the new rule byte (0..255), BEFORE balance-mutation."
  [left-rule center-rule right-rule context]
  (let [template (when context (build-template context))
        left-bits (engine/rule->bits left-rule)
        center-bits (engine/rule->bits center-rule)
        right-bits (engine/rule->bits right-rule)]
    (engine/bits->rule
     (mapv (fn [i]
             (let [parent-generation [(nth left-bits i)
                                      (nth center-bits i)
                                      (nth right-bits i)]
                   matched (when template
                             (template-match template parent-generation))]
               (if (some? matched)
                 matched
                 ;; local-rule fallback: center genotype's bit for this triple
                 (engine/eca-next-bit center-rule
                                      (nth left-bits i)
                                      (nth center-bits i)
                                      (nth right-bits i)))))
           (range 8)))))

;; ---------------------------------------------------------------------------
;; balance-mutation (256ca.el:971-986) — LOCAL COPY, flagged for dedupe.
;; ---------------------------------------------------------------------------
;; NOTE: :balance-mutation/dedupe-candidate
;; This is a faithful port of 256ca.el:971-986 (balance-mutation) plus its
;; helpers randomly-flip-some-selected-values (942-968) and randomize-sequence
;; (934-941).  zai-6 is porting the SAME function into engine.clj for the
;; non-contextual slice (R-repro-5).  The reviewer (claude-1) will dedupe the
;; two independent copies into one canonical engine.clj definition; the fact
;; that two independent ports must agree is itself a correctness check.

(defn- popcount
  "Number of 1-bits in a rule byte (0-255)."
  [rule]
  (Integer/bitCount (int rule)))

(defn- bit-positions
  "Allele indexes (0-7, MSB-first) where the bit equals VALUE."
  [rule value]
  (keep-indexed (fn [i b] (when (= b value) i))
                (engine/rule->bits rule)))

(defn- randomize-and-select-last
  "Faithfully replicate elisp randomize-sequence (256ca.el:934-941) Fisher-Yates
  over COLL, then return the LAST element (matching (nthcdr (- n 1) shuffled)
  for to-flip=1 in randomly-flip-some-selected-values, 256ca.el:942-968).

  The elisp loops `for i from 0 upto (- len 1)`, drawing (random (- len i))
  each iteration (len draws total: sizes len, len-1, ..., 1) and swaps
  vector[i] with vector[i + draw].  We consume RNG in the EXACT same order so
  a shadow-random injection cross-check reaches grid-identity.

  Returns the selected element, or nil if COLL is empty."
  [^java.util.Random rng coll]
  (let [v (vec coll)
        n (count v)]
    (when (pos? n)
      (if (= n 1)
        (first v)
        (loop [v v
               i 0]
          (if (>= i n)
            (nth v (dec n))
            (let [remaining (- n i)
                  r (.nextInt rng remaining)
                  j (+ i r)]
              (recur (assoc v
                            i (nth v j)
                            j (nth v i))
                     (inc i)))))))))

(defn balance-mutate-rule
  "Apply balance-mutation (256ca.el:971-986) to one rule byte, drawing from RNG.
  Returns the (possibly mutated) rule.

  RNG consumption order (must match elisp for cross-check):
    1. If popcount > 6: draw gate = (.nextInt rng 20).
       If gate == 0: draw the full Fisher-Yates over 1-bit positions and flip
       the selected bit.
    2. Elif popcount < 2: draw gate = (.nextInt rng 20).
       If gate == 0: draw the full Fisher-Yates over 0-bit positions.
    3. Else: no draw (elisp `and` short-circuits on the popcount test first).

  :balance-mutation/dedupe-candidate — see namespace doc."
  {:balance-mutation/dedupe-candidate true}
  [^java.util.Random rng rule]
  (let [ones (popcount rule)]
    (cond
      (and (> ones 6) (zero? (.nextInt rng 20)))
      (let [positions (bit-positions rule 1)
            allele (randomize-and-select-last rng positions)]
        (engine/flip-bit rule allele))

      (and (< ones 2) (zero? (.nextInt rng 20)))
      (let [positions (bit-positions rule 0)
            allele (randomize-and-select-last rng positions)]
        (engine/flip-bit rule allele))

      :else rule)))

(defn balance-mutate-rule-recorded
  "Like balance-mutate-rule but returns {:rule new-rule :draws [...]} where each
  draw is the integer value returned by (.nextInt rng k).  The :draws sequence
  is exactly what the elisp shadow-random must return, in order, for the
  cross-check injection.

  Uses the same full-Fisher-Yates draw count as balance-mutate-rule so the draw
  order is elisp-faithful by construction.

  :balance-mutation/dedupe-candidate — see namespace doc."
  {:balance-mutation/dedupe-candidate true}
  [^java.util.Random rng rule]
  (let [ones (popcount rule)]
    (cond
      (> ones 6)
      (let [gate (.nextInt rng 20)]
        (if (zero? gate)
          (let [positions (bit-positions rule 1)
                v (vec positions)
                n (count v)]
            (if (zero? n)
              {:rule rule :draws [gate]}
              (loop [vv v
                     i 0
                     draws [gate]]
                (if (>= i n)
                  {:rule (engine/flip-bit rule (nth vv (dec n)))
                   :draws draws}
                  (let [remaining (- n i)
                        r (.nextInt rng remaining)
                        j (+ i r)]
                    (recur (assoc vv
                                  i (nth vv j)
                                  j (nth vv i))
                           (inc i)
                           (conj draws r)))))))
          {:rule rule :draws [gate]}))

      (< ones 2)
      (let [gate (.nextInt rng 20)]
        (if (zero? gate)
          (let [positions (bit-positions rule 0)
                v (vec positions)
                n (count v)]
            (if (zero? n)
              {:rule rule :draws [gate]}
              (loop [vv v
                     i 0
                     draws [gate]]
                (if (>= i n)
                  {:rule (engine/flip-bit rule (nth vv (dec n)))
                   :draws draws}
                  (let [remaining (- n i)
                        r (.nextInt rng remaining)
                        j (+ i r)]
                    (recur (assoc vv
                                  i (nth vv j)
                                  j (nth vv i))
                           (inc i)
                           (conj draws r)))))))
          {:rule rule :draws [gate]}))

      :else
      {:rule rule :draws []})))

;; ---------------------------------------------------------------------------
;; evolve-sigil-with-mutating-template (single cell, 256ca.el:990-1065).
;; ---------------------------------------------------------------------------

(defn evolve-cell
  "Evolve a single genotype cell.  LEFT/CENTER/RIGHT are rule bytes (0..255);
  boundary neighbors are passed as 0 (the 一 sigil's rule).  CONTEXT is either
  nil (no template => local rule) or a 4-element vector of 0/1 ints (the
  phenotype quadruple).  Draws from RNG for balance-mutation.

  Returns the new rule byte.  Mirrors the full elisp function body:
  combine-with-template then balance-mutate-rule."
  [^java.util.Random rng left-rule center-rule right-rule context]
  (let [combined (combine-with-template left-rule center-rule right-rule context)]
    (balance-mutate-rule rng combined)))

(defn evolve-cell-recorded
  "Like evolve-cell but returns {:rule new-rule :draws [...]}.  The :draws are
  the RNG values consumed by balance-mutation, in order, for shadow-random
  injection."
  [^java.util.Random rng left-rule center-rule right-rule context]
  (let [combined (combine-with-template left-rule center-rule right-rule context)]
    (balance-mutate-rule-recorded rng combined)))

;; ---------------------------------------------------------------------------
;; evolve-sigil-string-contextually (256ca.el:1109) — one generation.
;; ---------------------------------------------------------------------------

(defn- wrap-landscape
  "Wrap a phenotype bit row with boundary 0 on each side, mirroring the elisp
  (cons (string-to-char \"0\") (string-to-list landscape)) nconc (list \"0\").
  Returns a vector of length (count row)+2."
  [phenotype-row]
  (into [0] (concat phenotype-row [0])))

(defn- context-quadruples
  "Build the context quadruples for the MIDDLE cells (indices 1..L-2) of a
  sigil string of length L, given the old and new phenotype rows (each length
  L).  Returns a vector of L-2 quadruples, each a 4-element vector of 0/1 ints:
    quad[k] = [old-w[k], old-w[k+1], old-w[k+2], new-w[k]]   for k = 0..L-3
  where old-w / new-w are the boundary-wrapped landscapes.  Middle cell at
  string-index i receives quad[i-1].  Mirrors 256ca.el:1128-1142 exactly."
  [old-phenotype new-phenotype]
  (let [old-w (wrap-landscape old-phenotype)
        new-w (wrap-landscape new-phenotype)
        L (count old-phenotype)]
    (mapv (fn [k]
            [(nth old-w k)
             (nth old-w (inc k))
             (nth old-w (+ k 2))
             (nth new-w k)])
          (range (- L 2)))))

(defn evolve-sigil-string-contextually
  "One generation of the contextual genotype update, mirroring
  evolve-sigil-string-contextually (256ca.el:1109).

  GENOTYPE-ROW is a vector of rule bytes (0..255).  OLD-PHENOTYPE and
  NEW-PHENOTYPE are vectors of 0/1 bits of equal length.  Draws from RNG for
  balance-mutation on every cell.

  Cell order (CRITICAL for RNG lockstep): 0 (head, no context), 1..L-2 (middle,
  with context), L-1 (tail, no context) — exactly as the elisp `(concat (second
  head) (map ...) (second tail))` evaluates left-to-right, and each cell's
  balance-mutation consumes RNG in that cell order.

  Returns the new genotype row (vector of rule bytes)."
  [^java.util.Random rng genotype-row old-phenotype new-phenotype]
  (let [L (count genotype-row)
        quads (context-quadruples old-phenotype new-phenotype)]
    (cond
      ;; Width 1: only the head/tail cell, no context.
      (= L 1)
      [(evolve-cell rng 0 (nth genotype-row 0) 0 nil)]

      :else
      (let [head (evolve-cell rng
                              0
                              (nth genotype-row 0)
                              (nth genotype-row 1)
                              nil)
            ;; CRITICAL RNG order: the elisp evaluates `head` and `tail` as
            ;; let* bindings BEFORE the middle map, so balance-mutation RNG is
            ;; consumed in order cell-0, cell-(L-1), then cells 1..L-2.
            tail (evolve-cell rng
                              (nth genotype-row (- L 2))
                              (nth genotype-row (dec L))
                              0
                              nil)
            middle (mapv (fn [i]
                           (evolve-cell rng
                                        (nth genotype-row (dec i))
                                        (nth genotype-row i)
                                        (nth genotype-row (inc i))
                                        (nth quads (dec i))))
                         (range 1 (dec L)))]
        (into [head] (conj middle tail))))))

(defn evolve-sigil-string-contextually-recorded
  "Like evolve-sigil-string-contextually but returns {:row new-row :draws [...]}
  where :draws is the flat sequence of RNG values consumed by balance-mutation
  across all cells, in cell consumption order (head, tail, middle).  For
  shadow-random injection."
  [^java.util.Random rng genotype-row old-phenotype new-phenotype]
  (let [L (count genotype-row)
        quads (context-quadruples old-phenotype new-phenotype)]
    (cond
      (= L 1)
      (let [r (evolve-cell-recorded rng 0 (nth genotype-row 0) 0 nil)]
        {:row [(:rule r)] :draws (:draws r)})

      :else
      (let [head-r (evolve-cell-recorded rng
                                         0
                                         (nth genotype-row 0)
                                         (nth genotype-row 1)
                                         nil)
            ;; CRITICAL RNG order: head and tail are elisp let* bindings
            ;; evaluated before the middle map.  See non-recorded variant.
            tail-r (evolve-cell-recorded rng
                                          (nth genotype-row (- L 2))
                                          (nth genotype-row (dec L))
                                          0
                                          nil)
            middle-rs (mapv (fn [i]
                              (evolve-cell-recorded rng
                                                    (nth genotype-row (dec i))
                                                    (nth genotype-row i)
                                                    (nth genotype-row (inc i))
                                                    (nth quads (dec i))))
                            (range 1 (dec L)))
            row (into [(:rule head-r)]
                      (conj (mapv :rule middle-rs) (:rule tail-r)))
            draws (-> (concat (:draws head-r)
                              (:draws tail-r)
                              (mapcat :draws middle-rs))
                      vec)]
        {:row row :draws draws}))))

;; ---------------------------------------------------------------------------
;; co-evolve-phenotype-and-genotype (256ca.el:1192) — one coupled step.
;; ---------------------------------------------------------------------------

(defn coupled-contextual-step
  "One coupled pheno->geno step, mirroring co-evolve-phenotype-and-genotype
  (256ca.el:1192):
    new-phenotype = engine/phenotype-step(old-genotype, old-phenotype)
    new-genotype  = evolve-sigil-string-contextually(old-genotype,
                                                     old-phenotype, new-phenotype)
  Returns {:genotype new-genotype :phenotype new-phenotype}.  Draws from RNG."
  [^java.util.Random rng {:keys [genotype phenotype]}]
  (let [new-phenotype (engine/phenotype-step genotype phenotype)
        new-genotype (evolve-sigil-string-contextually rng
                                                       genotype
                                                       phenotype
                                                       new-phenotype)]
    {:genotype new-genotype :phenotype new-phenotype}))

(defn coupled-contextual-step-recorded
  "Recorded variant of coupled-contextual-step.  Returns
  {:genotype new-genotype :phenotype new-phenotype :draws [...]} where :draws
  is the RNG sequence consumed by the genotype update's balance-mutation."
  [^java.util.Random rng {:keys [genotype phenotype]}]
  (let [new-phenotype (engine/phenotype-step genotype phenotype)
        {:keys [row draws]} (evolve-sigil-string-contextually-recorded
                             rng genotype phenotype new-phenotype)]
    {:genotype row :phenotype new-phenotype :draws draws}))

;; ---------------------------------------------------------------------------
;; Multi-generation driver (run-for-generations-3, 256ca.el:1217).
;; ---------------------------------------------------------------------------

(defn coupled-contextual-evolve
  "Evolve a coupled genotype/phenotype IC for STEPS generations under the
  contextual mutating-template dynamic.  Deterministic given (IC, seed).
  Returns {:genotype [rows...] :phenotype [rows...]} with rows 0..steps
  inclusive."
  [genotype phenotype steps seed]
  (when-not (and (integer? steps) (not (neg? steps)))
    (throw (ex-info "steps must be a non-negative integer" {:steps steps})))
  (when-not (= (count genotype) (count phenotype))
    (throw (ex-info "genotype and phenotype ICs must have equal width"
                    {:genotype (count genotype)
                     :phenotype (count phenotype)})))
  (let [rng (java.util.Random. (long seed))]
    (loop [states [{:genotype (vec genotype) :phenotype (vec phenotype)}]
           gen 1]
      (if (> gen steps)
        {:genotype (mapv :genotype states)
         :phenotype (mapv :phenotype states)}
        (recur (conj states (coupled-contextual-step rng (peek states)))
               (inc gen))))))

(defn coupled-contextual-evolve-recorded
  "Like coupled-contextual-evolve but also returns the full flat sequence of
  RNG draw values (for shadow-random injection into elisp).
  Returns {:genotype [rows...] :phenotype [rows...] :all-draws [...]}."
  [genotype phenotype steps seed]
  (when-not (and (integer? steps) (not (neg? steps)))
    (throw (ex-info "steps must be a non-negative integer" {:steps steps})))
  (when-not (= (count genotype) (count phenotype))
    (throw (ex-info "genotype and phenotype ICs must have equal width"
                    {:genotype (count genotype)
                     :phenotype (count phenotype)})))
  (let [rng (java.util.Random. (long seed))]
    (loop [states [{:genotype (vec genotype) :phenotype (vec phenotype)}]
           gen 1
           all-draws []]
      (if (> gen steps)
        {:genotype (mapv :genotype states)
         :phenotype (mapv :phenotype states)
         :all-draws all-draws}
        (let [{:keys [genotype phenotype draws]}
              (coupled-contextual-step-recorded rng (peek states))]
          (recur (conj states {:genotype genotype :phenotype phenotype})
                 (inc gen)
                 (into all-draws draws)))))))
