(ns futon5.hexagram.lambda-lift
  "A dynamically grounded six-bit situation key for MetaCA contexts.

   The input is the CURRENT 36-bit local context: four eight-bit ECA rule
   tables (LEFT, EGO, RIGHT, NEXT) followed by the four phenotype-family
   bits.  Nothing is cached from an initial condition.  Consequently a cell
   using this lift reads Langton's lambda from whichever four rules occupy
   those local roles at that moment.

   Packing, bottom line first:

   0..3  one bit per current LEFT/EGO/RIGHT/NEXT rule; yang when its lookup
         lambda is at least 1/2, yin otherwise;
   4     locale heterogeneity; yang when LEFT/EGO/RIGHT are not all the same;
   5     phenotype density; yang when at least two of the four family bits
         are one.

   The half-inclusive tie rule is fixed prospectively: lambda=1/2 and
   phenotype density=1/2 map to yang.  This packing uses exactly one bit for
   each registered quantity and introduces no learned thresholds."
  (:require [futon5.hexagram.lines :as lines]))

(def eca-lambda-table
  "Lookup table from every ECA number to Langton's lambda, taking zero as
   the quiescent output.  It is data, rather than a calculation tied to an
   initial sigil: any current eight-bit rule table can be looked up."
  (into (sorted-map)
        (for [rule (range 256)]
          [rule (/ (double (Integer/bitCount rule)) 8.0)])))

(defn- bit [value]
  (if (or (= value 1) (= value \1) (= value :yang) (true? value)) 1 0))

(defn- bits36 [values]
  (vec (take 36 (concat (map bit values) (repeat 0)))))

(defn- bits->eca-number [rule-bits]
  (reduce (fn [n value] (+ (* 2 n) value)) 0 rule-bits))

(defn rule-lambda
  "Look up lambda for a current eight-bit ECA rule table."
  [rule-bits]
  (get eca-lambda-table
       (bits->eca-number (vec (take 8 (concat (map bit rule-bits)
                                              (repeat 0)))))))

(defn context->lines
  "Encode one current 36-bit local context as six yin/yang lines."
  [context-bits]
  (let [bits (bits36 context-bits)
        [left ego right next] (mapv vec (partition 8 (take 32 bits)))
        phenotype (subvec bits 32 36)
        lambda-lines (mapv #(if (>= (rule-lambda %) 0.5) :yang :yin)
                           [left ego right next])
        heterogeneous? (not= 1 (count (distinct [left ego right])))
        dense? (>= (reduce + phenotype) 2)]
    (conj lambda-lines
          (if heterogeneous? :yang :yin)
          (if dense? :yang :yin))))

(defn context->hexagram
  "Return the standard hexagram map for a current local context."
  [context-bits]
  (lines/lines->hexagram (context->lines context-bits)))
