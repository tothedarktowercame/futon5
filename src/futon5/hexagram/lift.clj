(ns futon5.hexagram.lift
  "Lift exotype/context bits into hexagram representations.

   The key operation is DIAGONALIZATION: the 6x6 exotype matrix is
   eigendecomposed, and the signs of the 6 eigenvalues determine
   the hexagram lines (yang if positive, yin if non-positive).

   This ensures all four quadrants (LEFT/EGO/RIGHT/NEXT) contribute
   to the hexagram identity through the eigenvalue structure."
  (:require [clojure.java.io :as io]
            [futon5.ca.core :as ca]
            [futon5.hexagram.lines :as lines]))

(def ^:private matrix-shape 6)

(def ^:private pod-eigs-path
  (or (System/getenv "POD_EIGS_PATH")
      "pod-eigs/target/release/pod-eigs"))

(def ^:private babashka?
  (boolean (System/getProperty "babashka.version")))

(def ^:private pod-eigs-fn
  (delay
    (when babashka?
      (try
        (when (.exists (io/file pod-eigs-path))
          ((requiring-resolve 'babashka.pods/load-pod) pod-eigs-path)
          (requiring-resolve 'pod.eigs/eigenvalues))
        (catch Throwable _ nil)))))

(def ^:private eigenvalue-cache-limit 10000)
(defonce ^:private eigenvalue-cache (atom {}))

(def ^:private matrix-layout
  [[:ego :right :right :right :right :right]
   [:left :ego :right :right :right :right]
   [:left :left :next :p1 :p1 :p1]
   [:left :left :p1 :next :p2 :p2]
   [:left :left :p1 :p2 :next :p3]
   [:left :left :p1 :p2 :p3 :off]])

(defn- normalize-bits
  [bits size]
  (let [bits (cond
               (nil? bits) []
               (string? bits) (seq bits)
               (sequential? bits) bits
               :else (seq (str bits)))
        bits (map (fn [b]
                    (cond
                      (= b \1) 1
                      (= b \0) 0
                      (= b 1) 1
                      (= b 0) 0
                      (string? b) (if (= b "1") 1 0)
                      (keyword? b) (if (= b :yang) 1 0)
                      (number? b) (if (pos? (double b)) 1 0)
                      :else 0))
                  bits)
        padded (concat bits (repeat 0))]
    (vec (take size padded))))

(defn- cycle-at [v]
  (let [n (count v)]
    (when (pos? n)
      (cycle v))))

(defn- fill-matrix
  [{:keys [left ego right next phenotype]}]
  (let [left (normalize-bits left 8)
        ego (normalize-bits ego 8)
        right (normalize-bits right 8)
        next (normalize-bits next 8)
        phenotype (normalize-bits phenotype 4)
        p1 (nth phenotype 0 0)
        p2 (nth phenotype 1 0)
        p3 (nth phenotype 2 0)
        off (nth phenotype 3 0)
        left-seq (atom (cycle-at left))
        ego-seq (atom (cycle-at ego))
        right-seq (atom (cycle-at right))
        next-seq (atom (cycle-at next))
        take-next (fn [aseq]
                    (let [curr @aseq]
                      (if (seq curr)
                        (let [value (first curr)]
                          (swap! aseq rest)
                          value)
                        0)))]
    (mapv (fn [row]
            (mapv (fn [label]
                    (case label
                      :left (take-next left-seq)
                      :ego (take-next ego-seq)
                      :right (take-next right-seq)
                      :next (take-next next-seq)
                      :p1 p1
                      :p2 p2
                      :p3 p3
                      :off off
                      0))
                  row))
          matrix-layout)))

(defn- bits->matrix
  [bits]
  (let [bits (normalize-bits bits (* matrix-shape matrix-shape))]
    (mapv (fn [row]
            (vec (take matrix-shape (drop (* row matrix-shape) bits))))
          (range matrix-shape))))

(defn matrix->bits
  "Flatten a 6x6 matrix into a 36-bit vector (row-major)."
  [matrix]
  (normalize-bits (mapcat identity matrix)
                  (* matrix-shape matrix-shape)))

(defn segments->exotype-bits
  "Convert structured exotype segments into a 36-bit vector."
  [{:keys [left ego right next phenotype] :as segments}]
  (matrix->bits (fill-matrix segments)))

(defn exotype->6x6
  "Arrange 36 exotype bits into a 6x6 matrix.
  Accepts either a 36-bit sequence or a map with :left/:ego/:right/:next/:phenotype."
  [exotype-bits]
  (if (map? exotype-bits)
    (fill-matrix exotype-bits)
    (bits->matrix exotype-bits)))

(defn diagonal
  "Extract the 6-element diagonal from a 6x6 matrix.
   DEPRECATED: Use eigenvalue-diagonal for principled hexagram extraction."
  [matrix]
  (mapv (fn [idx]
          (get-in matrix [idx idx]))
        (range matrix-shape)))

(defn- matrix->array2d
  "Convert a Clojure 6x6 matrix to a Java double[][] array."
  [matrix]
  (into-array (map double-array
                   (for [row matrix]
                     (map double row)))))

(defn- java-eigenvalues
  "Compute eigenvalues via Apache Commons Math, using reflection to avoid hard deps in bb."
  [matrix]
  (let [arr (matrix->array2d matrix)
        rm-class (Class/forName "org.apache.commons.math3.linear.Array2DRowRealMatrix")
        ed-class (Class/forName "org.apache.commons.math3.linear.EigenDecomposition")
        rm-ctor (.getConstructor rm-class (into-array Class [(class arr)]))
        rm (.newInstance rm-ctor (object-array [arr]))
        ed-ctor (.getConstructor ed-class (into-array Class [(class rm)]))
        eigen (.newInstance ed-ctor (object-array [rm]))
        reals (vec (.getRealEigenvalues eigen))]
    (vec (sort-by #(- (Math/abs %)) reals))))

(defn- pod-eigenvalues
  "Compute eigenvalues via the pod; returns real parts sorted by abs desc."
  [matrix]
  (let [pod-fn @pod-eigs-fn]
    (when pod-fn
      (let [resp (pod-fn {:rows matrix :symmetric false})
            pairs (:eigenvalues resp)
            reals (mapv first pairs)]
        (vec (sort-by #(- (Math/abs %)) reals))))))

(defn- cached-eigenvalues
  [matrix compute-fn]
  (let [key (vec (map double (mapcat identity matrix)))]
    (if-let [hit (get @eigenvalue-cache key)]
      hit
      (let [value (compute-fn matrix)]
        (swap! eigenvalue-cache
               (fn [m]
                 (let [m' (assoc m key value)]
                   (if (> (count m') eigenvalue-cache-limit)
                     {}
                     m'))))
        value))))

(defn eigenvalues
  "Compute eigenvalues of a 6x6 matrix.
   Returns a vector of 6 eigenvalues (real parts only, sorted by magnitude descending)."
  [matrix]
  (cached-eigenvalues
   matrix
   (fn [m]
     (or (pod-eigenvalues m)
         (java-eigenvalues m)))))

(defn eigenvalue-signs
  "Compute the signs of eigenvalues as hexagram lines.
   Returns 6 elements: 1 for positive eigenvalue, 0 for non-positive."
  [matrix]
  (let [eigs (eigenvalues matrix)]
    (mapv (fn [ev] (if (pos? ev) 1 0)) eigs)))

(defn eigenvalue-diagonal
  "Diagonalize the matrix and extract hexagram lines from eigenvalue signs.

   This is the principled compression: 36 bits → 6 eigenvalues → 6 signs.
   All quadrants (LEFT/EGO/RIGHT/NEXT/PHENOTYPE) contribute to the
   eigenvalue structure, so all aspects of the design pattern affect
   the resulting hexagram."
  [matrix]
  (eigenvalue-signs matrix))

(defn yang?
  "Return true if a value is interpreted as yang."
  [value]
  (cond
    (= value :yang) true
    (= value :yin) false
    (= value \1) true
    (= value \0) false
    (string? value) (not= value "0")
    (number? value) (> (double value) 0.5)
    :else false))

(defn diagonal->hexagram-lines
  "Convert a diagonal to a 6-line vector (:yin/:yang), bottom-to-top."
  [diag]
  (mapv (fn [v] (if (yang? v) :yang :yin)) diag))

(defn exotype->hexagram-lines-legacy
  "Lift exotype bits into a 6-line vector using simple diagonal extraction.
   DEPRECATED: Use exotype->hexagram-lines for eigenvalue-based extraction."
  [exotype-bits]
  (-> (exotype->6x6 exotype-bits)
      diagonal
      diagonal->hexagram-lines))

(defn exotype->hexagram-lines
  "Lift exotype bits into a 6-line vector (:yin/:yang) via eigenvalue diagonalization.

   The 6x6 matrix is eigendecomposed, and the signs of the 6 eigenvalues
   determine the hexagram lines. This ensures all quadrants (IF/BECAUSE/
   HOWEVER/THEN) contribute to the hexagram identity."
  [exotype-bits]
  (let [matrix (exotype->6x6 exotype-bits)
        signs (eigenvalue-diagonal matrix)]
    (mapv (fn [s] (if (pos? s) :yang :yin)) signs)))

(defn exotype->hexagram
  "Lift exotype bits into a hexagram map."
  [exotype-bits]
  (lines/lines->hexagram (exotype->hexagram-lines exotype-bits)))

(defn context->segments
  "Build exotype segments from a sampled exotype context."
  [{:keys [context-sigils phenotype-context]}]
  (let [[left ego right next] (map ca/bits-for context-sigils)]
    {:left left
     :ego ego
     :right right
     :next next
     :phenotype phenotype-context}))

(defn context->hexagram
  "Lift a sampled exotype context into a hexagram map."
  [context]
  (-> context
      context->segments
      exotype->hexagram))

;; =============================================================================
;; 256 PHYSICS RULES = 64 hexagrams × 4 primary energies
;; =============================================================================

(def primary-energies
  "The four primary energies (四正) from Tai Chi.
   These determine HOW to engage with the situation the hexagram describes."
  [{:id 0 :key :peng :name "Péng" :hanzi "掤" :action "Ward Off"
    :dynamic :expand :description "Create space, establish boundaries"}
   {:id 1 :key :lu   :name "Lǚ"   :hanzi "捋" :action "Roll Back"
    :dynamic :yield :description "Yield, redirect, absorb"}
   {:id 2 :key :ji   :name "Jǐ"   :hanzi "擠" :action "Press"
    :dynamic :focus :description "Concentrate force, direct attention"}
   {:id 3 :key :an   :name "Àn"   :hanzi "按" :action "Push"
    :dynamic :momentum :description "Forward drive, sustained pressure"}])

(defn energy-by-id [id]
  (nth primary-energies (mod id 4)))

(defn energy-by-key [k]
  (first (filter #(= k (:key %)) primary-energies)))

(defn hexagram+energy->rule
  "Combine hexagram (0-63) and energy (0-3) into physics rule (0-255).

   Rule = hexagram * 4 + energy

   This gives a unique rule for each (situation, engagement-mode) pair."
  [hexagram-num energy-id]
  (let [hex (mod (or hexagram-num 0) 64)
        eng (mod (or energy-id 0) 4)]
    (+ (* hex 4) eng)))

(defn rule->hexagram+energy
  "Decompose a physics rule (0-255) into hexagram and energy.

   Returns {:hexagram n :energy energy-map}"
  [rule]
  (let [rule (mod (or rule 0) 256)
        hex-num (quot rule 4)
        eng-id (mod rule 4)]
    {:hexagram (inc hex-num)  ; King Wen numbering is 1-64
     :energy (energy-by-id eng-id)}))

(defn context->energy
  "Derive primary energy from context.

   Uses the phenotype bits to select energy:
   - Bits 0-1 of phenotype → energy index (0-3)"
  [{:keys [phenotype-context]}]
  (let [bits (or phenotype-context "00")
        b0 (if (= (nth bits 0 \0) \1) 1 0)
        b1 (if (= (nth bits 1 \0) \1) 2 0)
        energy-id (+ b0 b1)]
    (energy-by-id energy-id)))

(defn context->physics-rule
  "Derive the full 256-space physics rule from a sampled context.

   The rule combines:
   - Hexagram (from eigenvalue diagonalization of 36-bit matrix)
   - Primary energy (from phenotype bits 0-1)

   Returns {:rule n :hexagram {...} :energy {...}}"
  [context]
  (let [hexagram (context->hexagram context)
        hex-num (or (:number hexagram) 1)
        energy (context->energy context)
        rule (hexagram+energy->rule (dec hex-num) (:id energy))]
    {:rule rule
     :hexagram hexagram
     :energy energy
     :description (str (:name hexagram) " + " (:name energy) " (" (:action energy) ")")}))
