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

(defn save-ic! [path seed width]
  (let [f (io/file path)
        ic (seeded-ic seed width)]
    (.mkdirs (.getParentFile f))
    (spit f (with-out-str (prn {:seed seed :width width :ic ic})))
    ic))

(defn read-ic [path]
  (:ic (edn/read-string (slurp path))))

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
