(ns futon5.mmca.render
  "Render MMCA histories into simple image formats."
  (:require [clojure.string :as str]
            [futon5.ca.core :as ca]))

(def ^:private white [255 255 255])
(def ^:private black [0 0 0])

(defn- hex->rgb [hex]
  (try
    (let [hex (str/replace (or hex "#000000") "#" "")
          r (Integer/parseInt (subs hex 0 2) 16)
          g (Integer/parseInt (subs hex 2 4) 16)
          b (Integer/parseInt (subs hex 4 6) 16)]
      [r g b])
    (catch Exception _ black)))

(defn- sigil->rgb [sigil]
  (try
    (hex->rgb (ca/color-for sigil))
    (catch Exception _ (hex->rgb (ca/color-for ca/default-sigil)))))

(defn- phenotype->rgb [bit]
  (if (= bit \1) white black))

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

(defn- render-row [row color-fn]
  (mapv color-fn (seq (or row ""))))

(defn render-history
  "Render a history of sigil strings into RGB rows."
  [history]
  (mapv #(render-row % (fn [ch] (sigil->rgb (str ch)))) history))

(defn- exotype->rgb [pred self next out phe-bits]
  (let [base (str (ca/bits-for pred) (ca/bits-for self) (ca/bits-for next))
        extra (str (ca/bits-for out) (apply str phe-bits))
        mask (apply str (take 24 (cycle extra)))
        mixed (xor-bits base mask)]
    (bits->rgb mixed)))

(defn- exotype-row [g g-next p p-next len]
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
                            (bit-at p-next x)]]
              (exotype->rgb pred self next out phe-bits)))
          (range len))))

(defn- join-panels [panels]
  (vec (mapcat identity (interpose [white] panels))))

(defn render-history-phenotype
  "Render paired genotype/phenotype histories with a white separator."
  [gen-history phe-history]
  (let [rows (max (count gen-history) (count phe-history))
        g-len (count (or (first gen-history) ""))
        p-len (count (or (first phe-history) ""))]
    (mapv (fn [idx]
            (let [g (pad-right (nth gen-history idx "") g-len ca/default-sigil)
                  p (pad-right (nth phe-history idx "") p-len "0")
                  g-row (render-row g (fn [ch] (sigil->rgb (str ch))))
                  p-row (render-row p phenotype->rgb)]
              (vec (concat g-row [white] p-row))))
          (range rows))))

(defn- render-history-genotype-exotype
  [gen-history phe-history]
  (let [rows (count gen-history)
        g-len (count (or (first gen-history) ""))]
    (mapv (fn [idx]
            (let [g (nth gen-history idx "")
                  g-next (nth gen-history (inc idx) "")
                  p (if (seq phe-history) (nth phe-history idx "") "")
                  p-next (if (seq phe-history) (nth phe-history (inc idx) "") "")
                  g-row (render-row g (fn [ch] (sigil->rgb (str ch))))
                  e-row (exotype-row g g-next p p-next g-len)]
              (join-panels [g-row e-row])))
          (range rows))))

(defn- render-history-phenotype-exotype
  [gen-history phe-history]
  (let [rows (max (count gen-history) (count phe-history))
        g-len (count (or (first gen-history) ""))
        p-len (count (or (first phe-history) ""))]
    (mapv (fn [idx]
            (let [g (pad-right (nth gen-history idx "") g-len ca/default-sigil)
                  g-next (pad-right (nth gen-history (inc idx) "") g-len ca/default-sigil)
                  p (pad-right (nth phe-history idx "") p-len "0")
                  p-next (pad-right (nth phe-history (inc idx) "") p-len "0")
                  g-row (render-row g (fn [ch] (sigil->rgb (str ch))))
                  p-row (render-row p phenotype->rgb)
                  e-row (exotype-row g g-next p p-next g-len)]
              (join-panels [g-row p-row e-row])))
          (range rows))))

(defn- ppm-string [pixels comment]
  (let [height (count pixels)
        width (count (first pixels))
        header (str "P3\n"
                    (when comment (str "# " comment "\n"))
                    width " " height "\n255\n")]
    (str header
         (str/join
          "\n"
          (map (fn [row]
                 (->> row
                      (mapcat identity)
                      (map str)
                      (str/join " ")))
               pixels))
         "\n")))

(defn write-ppm!
  "Write pixels to a PPM file. Pixels are rows of [r g b] triples."
  [path pixels & {:keys [comment]}]
  (spit path (ppm-string pixels comment)))

(defn write-image!
  "Write pixels to an image file. Only PPM is supported in this runtime."
  [path pixels]
  (let [ext (some-> path str/lower-case (str/split #"\.") last)]
    (if (= ext "ppm")
      (write-ppm! path pixels :comment "futon5-mmca")
      (throw (ex-info "Only .ppm output is supported; use ImageMagick convert for other formats."
                      {:path path
                       :extension ext})))))

(defn render-run
  "Render a MMCA run result map to RGB pixels."
  ([result]
   (render-run result nil))
  ([{:keys [gen-history phe-history]} {:keys [exotype?]}]
   (cond
     (and exotype? (seq phe-history)) (render-history-phenotype-exotype gen-history phe-history)
     exotype? (render-history-genotype-exotype gen-history phe-history)
     (seq phe-history) (render-history-phenotype gen-history phe-history)
     :else (render-history gen-history))))

(defn render-run->file!
  "Render a MMCA run and write to path."
  ([result path]
   (write-image! path (render-run result)))
  ([result path opts]
   (write-image! path (render-run result opts))))
