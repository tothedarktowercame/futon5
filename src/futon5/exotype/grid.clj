(ns futon5.exotype.grid
  "A per-cell exotype grid and four local transmission policies.

   Exotypes are names from the measured propagator vocabulary. All legacy
   positional permutations are converted through the historical Elisp
   neighbourhood ordering before use. Policies read only the cell, its two
   neighbours, and the current local phenotype neighbourhood."
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.selection :as selection]
            [futon5.xenotype.generator :as gen]))

(def exotype-kinds
  "The selectable vocabulary. Widened from 4 to 12 at S0b/H5 (TN-baldwin-reboot.md
  42.3): the EFE dynamics converged to :collapser because the score-cache only
  iterated over the four declared kinds, so the objective could never select
  the non-freezing kinds. All 12 now have kind-specific rows in the conditional
  model (S0b, 42.2).

  The original four come first to preserve ordering for any code that indexes
  positionally. The eight additions are the P2 probe kinds: the absorbing axis
  (even4/even8/even1) and the rate axis (odd53/fix2/fix3/fix4/fix6)."
  [:builder :collapser :chaos :identity
   :even4 :even8 :even1 :odd53 :fix2 :fix3 :fix4 :fix6])
(def arms [:uniform-fixed :heterogeneous-fixed :conformist :boring-triggered])

(def ^:private elisp-table
  ["000" "001" "010" "100" "011" "101" "110" "111"])

(defn- positional [s]
  (mapv #(Character/digit ^char % 10) s))

(def propagators
  "Four σ, hand-picked and named, out of 40,320 permutations -- or 16,777,216 maps
   if the family is widened to include the 2015 bug's shape. **Why these four has
   never been justified** (TN-baldwin-reboot.md 15, an open hole).

   The names mislead and the measured coordinates are the antidote. Every σ has a
   computable signature; nothing here needs to be taken on trust:

     kind        fix(σ)  cycle type      rate    floor   bytes immune to it
     :identity     8     (1^8)          1.0000   1.000         0
     :builder      5     (3,1,1,1,1,1)  0.8125   0.625         0
     :chaos        1     (4,3,1)        0.5625   0.125         0
     :collapser    0     (6,2)          0.5000   0.000         4

   `:identity` is the MOST disruptive of the four, not the least -- a fixed point
   of σ is an unconditional flip (see `gen/rule-permute`). `:collapser` is the only
   all-even cycle type and so the only one admitting a byte it cannot change.

   Rates are DERIVED by `gen/rule-change-rate`, not declared; the floor
   `fix(σ)/8` holds for any byte distribution. Both are pinned by
   `futon5.exotype.invariants-test`."
  {:builder (gen/positional-sigma->neighbourhood-sigma
             (positional "51034267") elisp-table)
   :collapser (gen/positional-sigma->neighbourhood-sigma
               (positional "10345672") elisp-table)
   :chaos (gen/positional-sigma->neighbourhood-sigma
           (positional "13407265") elisp-table)
   :identity (gen/positional-sigma->neighbourhood-sigma
              [0 1 2 3 4 5 6 7] elisp-table)

   ;; --- P2 vocabulary probe (TN-baldwin-reboot.md 30-31) -------------------
   ;; NOT in `exotype-kinds`, so the default vocabulary and everything derived
   ;; from it are unchanged. These exist to test one claim: `:rule-change` cannot
   ;; distinguish a propagator that can FREEZE from one that cannot, because
   ;; rate = 0.5 + fix/16 and both of these have fix = 0, hence rate 0.5000
   ;; exactly -- while one has 16 absorbing bytes and the other none.
   ;; The five ALL-EVEN cycle types are the entire immune-byte axis, and every
   ;; one of them has fix = 0, hence rate 0.5000. Together with an odd control at
   ;; the same rate this is a five-point dose-response the model cannot see.
   :even4 (gen/positional-sigma->neighbourhood-sigma
           (positional "10325476") elisp-table)   ; (2,2,2,2) -- 16 immune bytes
   :even8 (gen/positional-sigma->neighbourhood-sigma
           (positional "12305476") elisp-table)   ; (4,2,2)   --  8 immune bytes
   :even1 (gen/positional-sigma->neighbourhood-sigma
           (positional "12345670") elisp-table)   ; (8)       --  2 immune bytes
   :odd53 (gen/positional-sigma->neighbourhood-sigma
           (positional "12340675") elisp-table)   ; (5,3)     --  0 immune bytes

   ;; The RATE axis. rate = 0.5 + fix/16 exactly, so fix is the only rate dial and
   ;; it has nine notches. The default four cover fix 0,1,5,8; these fill the
   ;; contiguous middle gap 2,3,4,6 (fix=7 is impossible). All have 0 absorbing
   ;; bytes, since absorbing requires all-even cycles which force fix=0.
   :fix2 (gen/positional-sigma->neighbourhood-sigma
          (positional "12345067") elisp-table)    ; (6,1,1)         rate 0.6250
   :fix3 (gen/positional-sigma->neighbourhood-sigma
          (positional "12340567") elisp-table)    ; (5,1,1,1)       rate 0.6875
   :fix4 (gen/positional-sigma->neighbourhood-sigma
          (positional "12304567") elisp-table)    ; (4,1,1,1,1)     rate 0.7500
   :fix6 (gen/positional-sigma->neighbourhood-sigma
          (positional "10234567") elisp-table)})  ; (2,1,1,1,1,1,1) rate 0.8750

(defn boring?
  "The three-bit circular phenotype neighbourhood is uniform."
  [phenotype index]
  (let [width (count phenotype)
        at #(nth phenotype (mod % width))]
    (= (at (dec index)) (at index) (at (inc index)))))

(defn initial-grid
  "Create the exotype grid for ARM. Transmitting arms share the same seeded
   heterogeneous initialization as :heterogeneous-fixed."
  [arm width]
  (case arm
    :uniform-fixed (vec (repeat width :builder))
    (:heterogeneous-fixed :conformist :boring-triggered)
    (vec (repeatedly width #(ca/rnd-nth exotype-kinds)))
    (throw (ex-info "unknown exotype-grid arm" {:arm arm :available arms}))))

(defn- local-majority [grid index]
  (let [width (count grid)
        self (nth grid index)
        values [(nth grid (mod (dec index) width))
                self
                (nth grid (mod (inc index) width))]
        winner (first (for [[value n] (frequencies values) :when (>= n 2)] value))]
    (or winner self)))

(defn transmit
  "Advance exotypes synchronously under ARM. No policy reads global state."
  [arm grid phenotype]
  (case arm
    (:uniform-fixed :heterogeneous-fixed) grid
    :conformist
    (mapv #(local-majority grid %) (range (count grid)))
    :boring-triggered
    (mapv (fn [index]
            (if (boring? phenotype index)
              (nth grid (mod (dec index) (count grid)))
              (nth grid index)))
          (range (count grid)))
    (throw (ex-info "unknown exotype-grid arm" {:arm arm :available arms}))))

(defn apply-exotype
  "Apply EXOTYPE to a local rule. At transfer fraction Q, the rule source is
   taken from the fixed offset +1 neighbour with probability Q; otherwise it
   remains the cell's own rule. The three-argument arity is the byte-compatible
   legacy path."
  ([sigil exotype draw-seed]
   (ca/with-mixed-seed draw-seed
     (ca/sigil-for (#'gen/rule-permute (ca/bits-for (str sigil))
                                       (get propagators exotype)))))
  ([sigil neighbour-sigil exotype q draw-seed]
   (when-not (<= 0.0 (double q) 1.0)
     (throw (ex-info "transfer fraction must be in [0,1]" {:transfer-fraction q})))
   (if (zero? (double q))
     (apply-exotype sigil exotype draw-seed)
     (ca/with-mixed-seed draw-seed
       (let [source (if (< (ca/rnd) (double q))
                      neighbour-sigil
                      sigil)]
         (ca/sigil-for (#'gen/rule-permute (ca/bits-for (str source))
                                           (get propagators exotype))))))))

(defn blend-rule
  "Deterministically blend LEFT and RIGHT around CENTRE, following the 2014
   neighbour-agreement rule. Where the neighbours agree, retain their bit;
   where they disagree, evaluate CENTRE on that (left, centre, right) triple."
  [left centre right]
  (let [left-bits (ca/bits->ints (ca/bits-for (str left)))
        centre-bits (ca/bits->ints (ca/bits-for (str centre)))
        right-bits (ca/bits->ints (ca/bits-for (str right)))
        centre-rule (ca/local-rule-table (str centre))]
    (ca/sigil-for
     (ca/ints->bits
      (mapv (fn [left-bit centre-bit right-bit]
              (if (= left-bit right-bit)
                left-bit
                (get centre-rule (str left-bit centre-bit right-bit))))
            left-bits centre-bits right-bits)))))

(def ^:private blend-stream-tag
  "Separates the blend coin's stream from the propagator draw's."
  0x0B1E4D)

(defn apply-exotype-blend
  "Choose the complete two-neighbour blend with probability BETA, otherwise
   choose SIGIL, then apply EXOTYPE. The blend coin has a deterministic stream
   distinct from the propagator draw. TRANSFER-FRACTION retains its existing
   optional source-transfer semantics after this selection."
  [left sigil right exotype transfer-fraction beta draw-seed]
  (when-not (<= 0.0 (double beta) 1.0)
    (throw (ex-info "blend strength must be in [0,1]" {:blend-strength beta})))
  (if (zero? (double beta))
    (apply-exotype sigil right exotype transfer-fraction draw-seed)
    (let [blended (blend-rule left sigil right)
          ;; Stream tag, then avalanche-mix. The previous form XORed the seed
          ;; with 0x5DEECE66D -- the very constant java.util.Random XORs it back
          ;; with -- so it cancelled the scramble instead of adding entropy.
          blend? (ca/with-mixed-seed (bit-xor (long draw-seed) blend-stream-tag)
                   (< (ca/rnd) (double beta)))
          source (if blend? blended sigil)]
      (apply-exotype source right exotype transfer-fraction draw-seed))))

(defn- phenotype-step [genotype phenotype]
  (let [width (count genotype)]
    (apply str
           (for [index (range width)]
             (ca/evolve-digits-by-rule
              (str (nth phenotype (mod (dec index) width)))
              (str (nth phenotype index))
              (str (nth phenotype (mod (inc index) width)))
              (ca/bits-for (str (nth genotype index))))))))

(defn- expressed-grid
  [genotype exotypes transfer-fraction blend-strength seed time]
  (let [width (count genotype)]
    (mapv (fn [index sigil exotype]
            (apply-exotype-blend
             (nth genotype (mod (dec index) width))
             sigil
             (nth genotype (mod (inc index) width))
             exotype transfer-fraction blend-strength
             (+ (long seed) (* (long time) width) index)))
          (range width) genotype exotypes)))

(defn step
  "Advance phenotype, genotype, and exotype grids synchronously."
  [{:keys [genotype phenotype exotypes arm seed time transfer-fraction blend-strength
           selection-strength fitness-kind write-back? expressed previous-expressed
           selection-window]
    :or {seed 0 time 0 transfer-fraction 0.0 blend-strength 0.0
         selection-strength 0.0 fitness-kind :preferences write-back? true}
    :as state}]
  (let [selection-family? (or (contains? state :selection-strength)
                              (contains? state :fitness-kind)
                              (contains? state :write-back?)
                              (false? write-back?))
        behaviour-rules (if selection-family? (or expressed genotype) genotype)
        selection-result
        (when selection-family?
          (selection/advance
           {:genotypes genotype
            :expressed behaviour-rules
            :previous-expressed (or previous-expressed behaviour-rules)
            :phenotype phenotype
            :window selection-window
            :fitness-kind fitness-kind
            :selection-strength (if write-back? 0.0 selection-strength)
            :draw-seed (bit-xor (+ (long seed) (* (long time) (count genotype)))
                                0xC0FFEE)}))
        heritable-base (if (and selection-family? (false? write-back?))
                         (:genotype selection-result)
                         genotype)
        next-expressed (expressed-grid heritable-base exotypes transfer-fraction
                                       blend-strength seed time)
        next-genotype (if (and selection-family? (false? write-back?))
                        heritable-base
                        next-expressed)
        advanced
        {:arm arm
         :seed seed
         :time (inc time)
         :phenotype (phenotype-step behaviour-rules phenotype)
         :genotype next-genotype
         ;; The EGO that produced this NEXT. Not new state: the five-read
         ;; breakdown (LEFT RIGHT EGO NEXT PHENO) already implies it -- NEXT's
         ;; previous genotype IS the EGO, so from generation 2 onward "previous"
         ;; is available in the standard exotype model and was merely not being
         ;; written down (Joe, 2026-08-04). Only EGO's own predecessor at
         ;; generation 1 is genuinely undefined.
         ;;
         ;; `self-tuning/step` has always carried it; `grid/step` did not, which
         ;; left `local-observation`'s :static? and :hungry? structurally false
         ;; past t=0. TN-baldwin-reboot.md 24-25.
         :previous-genotype genotype
         :exotypes (transmit arm exotypes phenotype)}]
    (cond-> advanced
      (contains? state :transfer-fraction)
      (assoc :transfer-fraction transfer-fraction)

      (contains? state :blend-strength)
      (assoc :blend-strength blend-strength)

      selection-family?
      (assoc :selection-strength selection-strength
             :fitness-kind fitness-kind
             :write-back? write-back?
             :expressed next-expressed
             :previous-expressed behaviour-rules
             :selection-window (:window selection-result)))))

(defn run-steps [state steps]
  (nth (iterate step state) steps))
