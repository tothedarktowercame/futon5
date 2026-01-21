(ns futon5.mmca.register-shift
  "Shift MMCA registers for alternate scoring.

  exotype -> genotype (256-color / byte stream)
  genotype -> phenotype (black/white via luminance threshold)"
  (:require [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.metrics :as metrics]))

(def ^:private default-threshold 128.0)

(defn- hex->rgb [hex]
  (try
    (let [hex (str/replace (or hex "#000000") "#" "")
          r (Integer/parseInt (subs hex 0 2) 16)
          g (Integer/parseInt (subs hex 2 4) 16)
          b (Integer/parseInt (subs hex 4 6) 16)]
      [r g b])
    (catch Exception _ [0 0 0])))

(defn- sigil->rgb [sigil]
  (try
    (hex->rgb (ca/color-for (ca/safe-sigil sigil)))
    (catch Exception _
      (hex->rgb (ca/color-for ca/default-sigil)))))

(defn- luminance
  [[r g b]]
  (+ (* 0.2126 r) (* 0.7152 g) (* 0.0722 b)))

(defn- sigil->bw
  [sigil]
  (if (>= (luminance (sigil->rgb sigil)) default-threshold) \1 \0))

(defn- pad-right [s len fill]
  (let [s (or s "")]
    (cond
      (= (count s) len) s
      (< (count s) len) (str s (apply str (repeat (- len (count s)) fill)))
      :else (subs s 0 len))))

(defn- bits->byte [bits]
  (Integer/parseInt bits 2))

(defn- bits->rgb [bits]
  (let [bits (pad-right (or bits "") 24 "0")
        channels (partition 8 bits)]
    (mapv (fn [chunk] (bits->byte (apply str chunk))) channels)))

(defn- xor-bits [a b]
  (apply str (map (fn [x y] (if (= x y) \0 \1)) a b)))

(defn- sigil-at [s idx]
  (if (and s (<= 0 idx) (< idx (count s)))
    (str (nth s idx))
    ca/default-sigil))

(defn- bit-at [s idx]
  (if (and s (<= 0 idx) (< idx (count s)))
    (nth s idx)
    \0))

(defn- exotype->rgb [pred self next out phe-bits]
  (let [base (str (ca/bits-for pred) (ca/bits-for self) (ca/bits-for next))
        extra (str (ca/bits-for out) (apply str phe-bits))
        mask (apply str (take 24 (cycle extra)))
        mixed (xor-bits base mask)]
    (bits->rgb mixed)))

(defn- exotype-row->bytes [g g-next p p-next len]
  (let [g (pad-right g len ca/default-sigil)
        g-next (pad-right g-next len ca/default-sigil)
        p (pad-right p len "0")
        p-next (pad-right p-next len "0")]
    (mapv (fn [x]
            (let [pred (sigil-at g (dec x))
                  self (sigil-at g x)
                  next (sigil-at g (inc x))
                  out (sigil-at g-next x)
                  phe-bits [(bit-at p (dec x))
                            (bit-at p x)
                            (bit-at p (inc x))
                            (bit-at p-next x)]
                  rgb (exotype->rgb pred self next out phe-bits)]
              (int (Math/round (luminance rgb)))))
          (range len))))

(defn- bytes->string [bytes]
  (apply str (map (fn [b] (char (bit-and (int b) 0xFF))) bytes)))

(defn shift-phenotype-history
  "Map genotype (sigils) to phenotype (bw bits) by luminance."
  [gen-history]
  (let [rows (count gen-history)
        len (count (or (first gen-history) ""))]
    (mapv (fn [idx]
            (let [g (pad-right (nth gen-history idx "") len ca/default-sigil)]
              (apply str (map sigil->bw g))))
          (range rows))))

(defn shift-genotype-history
  "Map exotype (gen+phe) to a 256-color byte stream."
  [gen-history phe-history]
  (let [rows (count gen-history)
        len (count (or (first gen-history) ""))]
    (mapv (fn [idx]
            (let [g (nth gen-history idx "")
                  g-next (nth gen-history (inc idx) "")
                  p (if (seq phe-history) (nth phe-history idx "") "")
                  p-next (if (seq phe-history) (nth phe-history (inc idx) "") "")]
              (-> (exotype-row->bytes g g-next p p-next len)
                  bytes->string)))
          (range rows))))

(defn register-shift-summary
  "Return summary metrics after register shift."
  [{:keys [gen-history phe-history]}]
  (let [shift-phe (shift-phenotype-history gen-history)
        shift-gen (shift-genotype-history gen-history phe-history)
        gen-summary (metrics/summarize-series shift-gen)
        phe-summary (metrics/summarize-series shift-phe)
        gen-comp (double (or (:composite-score gen-summary) 0.0))
        phe-comp (double (or (:composite-score phe-summary) 0.0))]
    {:shift/gen-summary gen-summary
     :shift/phe-summary phe-summary
     :shift/gen-composite gen-comp
     :shift/phe-composite phe-comp
     :shift/composite (/ (+ gen-comp phe-comp) 2.0)}))
