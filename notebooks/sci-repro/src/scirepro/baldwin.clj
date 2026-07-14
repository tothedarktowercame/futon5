(ns scirepro.baldwin
  "Baldwin-effect dynamic for the coupled genotype–phenotype MetaCA.

   Ports `evolve-sigil-with-blending-baldwin` (256ca.el:634–686) and the
   contextual driver `evolve-sigil-string-contextually` (256ca.el:~1109)
   into scirepro.  This dynamic implements the §5.2 Baldwin-effect open
   question from arXiv:1502.00130v1 and has never been run in the lab prior
   to this reproduction.

   Dynamic summary (per generation, via co-evolution):
     1. Phenotype is updated from the OLD genotype and OLD phenotype
        (evolve-phenotype-against-genotype, 256ca.el:~1180).
     2. Genotype is updated via evolve-sigil-string-contextually with the
        Baldwin variant:
        - Blend step: per allele position, if left==right copy that bit,
          else look up the center rule's local-rule bit (blend-cell).
        - Baldwin gate: for middle cells only (context non-nil), draw
          (random 3).  If the draw is 0 (≈1/3 of the time):
            mutations = count of first-3 context chars equal to the 4th
            apply mutate-rule-n with n = mutations + 2
            (each mutate-rule-n iteration draws one (random 8) position)
        - Head/tail cells: no context → no gate → no RNG draw.

   Context construction (verified against elisp, width-5 trace):
   For middle cell i (1..width-2), the context is a 4-char string:
     [old-landscape[i-1], old-landscape[i], old-landscape[i+1], new-landscape[i-1]]
   where old-landscape = [0, old-phe..., 0] and new-landscape = [0, new-phe..., 0].
   Equivalently: 3 consecutive old-phenotype neighborhood values (with 0 boundary)
   followed by 1 new-phenotype value (offset by one position from the cell).

   The co-evolution step is: new-phenotype from old genotype+phenotype,
   then new-genotype from old genotype + old phenotype + new phenotype.
   This matches co-evolve-phenotype-and-genotype (256ca.el:~1190).

   RNG is injected (not seeded) so the cross-check can force grid-identity
   against elisp.  See scirepro.baldwin-cross-check for the injection route."
  (:require [scirepro.engine :as engine]))

(defn- baldwin-blend-output
  "Compute the 8-bit blend output rule byte for (left, center, right) rule bytes.
   Mirrors the blend computation inside evolve-sigil-with-blending-baldwin
   (256ca.el:648–666): blend-cell."
  [left-rule center-rule right-rule]
  (engine/blend-cell left-rule center-rule right-rule))

(defn count-context-matches
  "Count how many of the first 3 chars of CONTEXT-STR equal the 4th char.
   Mirrors the mutations counter in evolve-sigil-with-blending-baldwin
   (256ca.el:673–678): context-seq = string-to-list context, to-match =
   last element, count matches in (nbutlast context-seq)."
  [context-str]
  (let [chars (seq context-str)
        to-match (last chars)
        front (drop-last chars)]
    (count (filter #(= % to-match) front))))

(defn- mutate-rule-n-clj
  "Apply N bit-flips at positions drawn from RNG-FN (which must return
   values in 0..7, mirroring elisp (random 8)).  Returns the mutated
   rule byte.  Mirrors mutate-rule-n (256ca.el:571–591)."
  [rule-byte n rng-fn]
  (loop [j 0
         rule rule-byte]
    (if (>= j n)
      rule
      (let [pos (rng-fn 8)]                    ; (random 8)
        (recur (inc j) (engine/flip-bit rule pos))))))

(defn mutate-combined-rule
  "Apply Baldwin's MUTATE stage to an already-combined rule byte.

   CONTEXT-STR is the four-bit phenotype context or nil at a boundary.
   RNG-FN accepts a positive bound and returns an integer below it, matching
   the source `(random 3)` gate and `(random 8)` bit-position draws. This seam
   lets experiments combine another dynamic's COMBINE stage with Baldwin's
   verified mutation stage without reimplementing either."
  [combined-rule context-str rng-fn]
  (if (and context-str (< (rng-fn 3) 1))
    (let [mutations (count-context-matches context-str)
          n (+ mutations 2)]
      (mutate-rule-n-clj combined-rule n rng-fn))
    combined-rule))

(defn- baldwin-cell
  "Evolve one genotype cell under the Baldwin dynamic.

   LEFT-RULE, CENTER-RULE, RIGHT-RULE are rule bytes (0–255).  CONTEXT-STR
   is the 4-char phenotype context string (3 old + 1 new) or nil for
   head/tail cells.  RNG-FN returns the next shadow-random value when called.

   Returns the new rule byte for this cell."
  [left-rule center-rule right-rule context-str rng-fn]
  (let [output-rule (baldwin-blend-output left-rule center-rule right-rule)]
    (mutate-combined-rule output-rule context-str rng-fn)))

(defn build-context-quadruples
  "Build the phenotype context quadruple strings for middle cells, mirroring
   evolve-sigil-string-contextually (256ca.el:~1115–1135).

   OLD-PHE and NEW-PHE are sequences of 0/1 phenotype bits (length = width).
   Returns a vector indexed by middle-cell index i (1..width-2), where each
   element is a 4-char string:
     [old-landscape[i-1], old-landscape[i], old-landscape[i+1], new-landscape[i-1]]
   with old-landscape = [0, old-phe..., 0], new-landscape = [0, new-phe..., 0].

   Verified against elisp width-5 trace:
     old-phe=[1,0,1,0,1] new-phe=[0,1,0,1,0]
     cell 1 → \"0100\" cell 2 → \"1010\" cell 3 → \"0101\""
  [old-phe new-phe]
  (let [width (count old-phe)
        old-ls (vec (cons 0 (concat old-phe [0])))   ; [0, old-phe..., 0]
        new-ls (vec (cons 0 (concat new-phe [0])))]  ; [0, new-phe..., 0]
    (mapv (fn [i]
            (let [c0 (nth old-ls (dec i))
                  c1 (nth old-ls i)
                  c2 (nth old-ls (inc i))
                  c3 (nth new-ls (dec i))]
              (str c0 c1 c2 c3)))
          (range 1 (dec width)))))

(defn baldwin-step
  "One co-evolution generation under the Baldwin dynamic.

   STATE is {:genotype [...] :phenotype [...]} (both vectors of length width).
   RNG-FN accepts a positive bound and returns successive shadow-random values
   in 0..limit-1.

   Returns the next {:genotype :phenotype} state.

   Phenotype is updated first (from old genotype + old phenotype), then
   genotype is updated via the contextual Baldwin step using old phenotype
   (3 old cells) + new phenotype (1 new cell) as context."
  [{:keys [genotype phenotype]} rng-fn]
  (let [width (count genotype)
        ;; Step 1: new phenotype from old genotype + old phenotype.
        new-phenotype (engine/phenotype-step genotype phenotype)
        ;; Step 2: build context quadruples for middle cells (1..width-2).
        contexts (build-context-quadruples phenotype new-phenotype)
        ;; Step 3: evolve genotype.  Head/tail cells get nil context.
        new-genotype
        (mapv (fn [i center-rule]
                (let [left-rule (if (zero? i) 0 (nth genotype (dec i)))
                      right-rule (if (= i (dec width)) 0 (nth genotype (inc i)))
                      ;; Middle cells (1..width-2) get context; head/tail get nil.
                      ;; contexts vector is 0-indexed for middle cells: contexts[0] = cell 1.
                      context-str (if (or (zero? i) (= i (dec width)))
                                    nil
                                    (nth contexts (dec i)))]
                  (baldwin-cell left-rule center-rule right-rule context-str rng-fn)))
              (range)
              genotype)]
    {:genotype new-genotype
     :phenotype new-phenotype}))

(defn baldwin-evolve
  "Evolve a coupled genotype–phenotype system under the Baldwin dynamic for
   STEPS generations.  Returns {:genotype [rows] :phenotype [rows]} where
   rows 0..steps inclusive are included.

   RNG-FN accepts a positive bound and returns successive shadow-random values. The caller
   is responsible for ensuring the RNG stream matches the elisp consumption
   order (see baldwin-cross-check)."
  [genotype phenotype steps rng-fn]
  (when-not (= (count genotype) (count phenotype))
    (throw (ex-info "genotype and phenotype ICs must have equal width"
                    {:genotype (count genotype)
                     :phenotype (count phenotype)})))
  (when-not (and (integer? steps) (not (neg? steps)))
    (throw (ex-info "steps must be a non-negative integer" {:steps steps})))
  (let [states (vec (take (inc steps)
                          (iterate #(baldwin-step % rng-fn)
                                   {:genotype (vec genotype)
                                    :phenotype (vec phenotype)})))]
    {:genotype (mapv :genotype states)
     :phenotype (mapv :phenotype states)}))
