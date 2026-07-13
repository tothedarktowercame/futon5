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

(defn rule->bits
  "Return the eight-bit representation used by 256ca.el, MSB first."
  [rule]
  (when-not (<= 0 rule 255)
    (throw (ex-info "rule must be an integer in [0,255]" {:rule rule})))
  (mapv (fn [shift] (bit-and 1 (bit-shift-right rule shift)))
        (range 7 -1 -1)))

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

(defn censored-rule-23-bit
  [left _center right local-rule-bit-value]
  (if (= left right)
    left
    local-rule-bit-value))

(defn blending-censored-rule-23-proof
  "Exhaustively check all local rules and allele triples against the
   deterministic censoring rule used by 256ca.el."
  []
  (let [checks (for [rule (range 256)
                     left [0 1]
                     center [0 1]
                     right [0 1]
                     :let [local (local-rule-bit rule left center right)
                           blended (if (= left right) left local)]]
                 (= blended (censored-rule-23-bit left center right local)))]
    {:cases (count checks)
     :passed (count (filter true? checks))
     :ok? (every? true? checks)}))

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
