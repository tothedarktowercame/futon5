(ns futon5.hexagram.lift-variants
  "Alternative 36-bit situation keys for exotype neighbourhoods.

   The resulting hexagram is a neighbourhood-class key, not an operator
   codebook.  Eigen ordering uses a deterministic upper-half rank encoding:
   eigenvalues are ranked by [value, original-index], and the three largest
   receive yang lines.  The index tie-break makes repeated spectra stable."
  (:require [clojure.string :as str]
            [futon5.hexagram.lift :as lift]
            [futon5.hexagram.lines :as lines])
  (:import [java.nio.charset StandardCharsets]
           [java.security MessageDigest]
           [org.apache.commons.math3.linear Array2DRowRealMatrix
            EigenDecomposition]))

(def variants
  [:eigen-sign :eigen-ordering :eigen-magnitude :symmetrised :random])

(defn- bits36 [bits]
  (let [xs (map #(if (or (= % 1) (= % \1) (= % :yang)) 1 0) bits)]
    (vec (take 36 (concat xs (repeat 0))))))

(defn- median [xs]
  (let [v (vec (sort xs))]
    (/ (+ (double (nth v 2)) (double (nth v 3))) 2.0)))

(defn- eigenvalues
  "Real parts in Commons Math decomposition order. Unlike the legacy lift's
   magnitude-sorted public result, retaining positions lets rank and magnitude
   variants encode which spectral slot crosses their threshold."
  [matrix]
  (let [rows (into-array (map double-array matrix))
        decomposition (EigenDecomposition. (Array2DRowRealMatrix. rows))]
    (vec (.getRealEigenvalues decomposition))))

(defn- rank-lines [eigenvalues]
  (let [ranked (sort-by (fn [[idx value]] [value idx])
                        (map-indexed vector eigenvalues))
        upper (set (map first (take-last 3 ranked)))]
    (mapv #(if (contains? upper %) 1 0) (range 6))))

(defn- magnitude-lines [eigenvalues]
  (let [magnitudes (mapv #(Math/abs (double %)) eigenvalues)
        threshold (median magnitudes)]
    (mapv #(if (>= % threshold) 1 0) magnitudes)))

(defn- symmetrise [matrix]
  (mapv (fn [i]
          (mapv (fn [j]
                  (/ (+ (double (get-in matrix [i j]))
                        (double (get-in matrix [j i])))
                     2.0))
                (range 6)))
        (range 6)))

(defn- sha-lines [seed bits]
  (let [payload (str (long seed) ":" (str/join (bits36 bits)))
        digest (.digest (MessageDigest/getInstance "SHA-256")
                        (.getBytes payload StandardCharsets/UTF_8))
        byte0 (bit-and 0xff (aget digest 0))]
    (mapv #(if (bit-test byte0 %) 1 0) (range 6))))

(defn exotype->lines
  "Return six :yin/:yang lines for VARIANT and a 36-bit exotype.
   RANDOM-SEED affects only the mandatory :random control."
  ([variant bits] (exotype->lines variant bits 0))
  ([variant bits random-seed]
   (let [bits (bits36 bits)
         matrix (lift/exotype->6x6 bits)
         numeric
         (case variant
           :eigen-sign
           (mapv #(if (= :yang %) 1 0) (lift/exotype->hexagram-lines bits))
           :eigen-ordering (rank-lines (eigenvalues matrix))
           :eigen-magnitude (magnitude-lines (eigenvalues matrix))
           :symmetrised (mapv #(if (pos? (double %)) 1 0)
                              (eigenvalues (symmetrise matrix)))
           :random (sha-lines random-seed bits)
           (throw (ex-info "unknown lift variant"
                           {:variant variant :available variants})))]
     (mapv #(if (pos? %) :yang :yin) numeric))))

(defn exotype->hexagram
  "One interface for all lift variants; returns the standard hexagram map."
  ([variant bits] (exotype->hexagram variant bits 0))
  ([variant bits random-seed]
   (lines/lines->hexagram (exotype->lines variant bits random-seed))))
