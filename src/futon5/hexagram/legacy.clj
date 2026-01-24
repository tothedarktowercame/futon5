(ns futon5.hexagram.legacy
  "Legacy hexagram lift helpers kept for backwards compatibility.

   Deprecated: prefer futon5.hexagram.lift/eigenvalue-diagonal and
   futon5.hexagram.lift/exotype->hexagram-lines for new work."
  (:require [futon5.hexagram.lift :as lift]))

(defn diagonal
  "Extract the diagonal from a square matrix.
   Deprecated: use eigenvalue-based extraction instead."
  [matrix]
  (mapv (fn [idx]
          (get-in matrix [idx idx]))
        (range (count matrix))))

(defn diagonal->hexagram-lines
  "Convert a diagonal to a 6-line vector (:yin/:yang), bottom-to-top.
   Deprecated: use eigenvalue-based extraction instead."
  [diag]
  (mapv (fn [v] (if (lift/yang? v) :yang :yin)) diag))

(defn exotype->hexagram-lines-legacy
  "Lift exotype bits into a 6-line vector using simple diagonal extraction.
   Deprecated: use lift/exotype->hexagram-lines for eigenvalue-based extraction."
  [exotype-bits]
  (-> (lift/exotype->6x6 exotype-bits)
      diagonal
      diagonal->hexagram-lines))
