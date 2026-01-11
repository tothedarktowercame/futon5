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

(defn- render-row [row color-fn]
  (mapv color-fn (seq (or row ""))))

(defn render-history
  "Render a history of sigil strings into RGB rows."
  [history]
  (mapv #(render-row % (fn [ch] (sigil->rgb (str ch)))) history))

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
  [{:keys [gen-history phe-history]}]
  (if (seq phe-history)
    (render-history-phenotype gen-history phe-history)
    (render-history gen-history)))

(defn render-run->file!
  "Render a MMCA run and write to path."
  [result path]
  (write-image! path (render-run result)))
