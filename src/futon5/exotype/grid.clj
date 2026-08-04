(ns futon5.exotype.grid
  "A per-cell exotype grid and four local transmission policies.

   Exotypes are names from the measured propagator vocabulary. All legacy
   positional permutations are converted through the historical Elisp
   neighbourhood ordering before use. Policies read only the cell, its two
   neighbours, and the current local phenotype neighbourhood."
  (:require [futon5.ca.core :as ca]
            [futon5.xenotype.generator :as gen]))

(def exotype-kinds [:builder :collapser :chaos :identity])
(def arms [:uniform-fixed :heterogeneous-fixed :conformist :boring-triggered])

(def ^:private elisp-table
  ["000" "001" "010" "100" "011" "101" "110" "111"])

(defn- positional [s]
  (mapv #(Character/digit ^char % 10) s))

(def propagators
  {:builder (gen/positional-sigma->neighbourhood-sigma
             (positional "51034267") elisp-table)
   :collapser (gen/positional-sigma->neighbourhood-sigma
               (positional "10345672") elisp-table)
   :chaos (gen/positional-sigma->neighbourhood-sigma
           (positional "13407265") elisp-table)
   :identity (gen/positional-sigma->neighbourhood-sigma
              [0 1 2 3 4 5 6 7] elisp-table)})

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
   (ca/with-seed draw-seed
     (ca/sigil-for (#'gen/rule-permute (ca/bits-for (str sigil))
                                       (get propagators exotype)))))
  ([sigil neighbour-sigil exotype q draw-seed]
   (when-not (<= 0.0 (double q) 1.0)
     (throw (ex-info "transfer fraction must be in [0,1]" {:transfer-fraction q})))
   (if (zero? (double q))
     (apply-exotype sigil exotype draw-seed)
     (ca/with-seed draw-seed
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
          blend? (ca/with-seed (bit-xor (long draw-seed) 0x5DEECE66D)
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

(defn step
  "Advance phenotype, genotype, and exotype grids synchronously."
  [{:keys [genotype phenotype exotypes arm seed time transfer-fraction blend-strength]
    :or {seed 0 time 0 transfer-fraction 0.0 blend-strength 0.0}
    :as state}]
  (let [width (count genotype)
        advanced
        {:arm arm
         :seed seed
         :time (inc time)
         :phenotype (phenotype-step genotype phenotype)
         :genotype
         (mapv (fn [index sigil exotype]
                 (apply-exotype-blend
                  (nth genotype (mod (dec index) width))
                  sigil
                  (nth genotype (mod (inc index) width))
                  exotype transfer-fraction blend-strength
                  (+ (long seed) (* (long time) width) index)))
               (range width) genotype exotypes)
         :exotypes (transmit arm exotypes phenotype)}]
    (cond-> advanced
      (contains? state :transfer-fraction)
      (assoc :transfer-fraction transfer-fraction)

      (contains? state :blend-strength)
      (assoc :blend-strength blend-strength))))

(defn run-steps [state steps]
  (nth (iterate step state) steps))
