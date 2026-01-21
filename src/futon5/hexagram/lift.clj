(ns futon5.hexagram.lift
  "Lift exotype/context bits into hexagram representations."
  (:require [futon5.ca.core :as ca]
            [futon5.hexagram.lines :as lines]))

(def ^:private matrix-shape 6)

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
  "Extract the 6-element diagonal from a 6x6 matrix."
  [matrix]
  (mapv (fn [idx]
          (get-in matrix [idx idx]))
        (range matrix-shape)))

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

(defn exotype->hexagram-lines
  "Lift exotype bits into a 6-line vector (:yin/:yang)."
  [exotype-bits]
  (-> (exotype->6x6 exotype-bits)
      diagonal
      diagonal->hexagram-lines))

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
