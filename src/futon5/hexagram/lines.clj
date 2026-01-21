(ns futon5.hexagram.lines
  "Hexagram line helpers and King Wen ordering."
  (:require [futon5.mmca.iching :as iching]))

(def ^:private king-wen-binaries
  ;; Generated from futon3/library/iching @trigrams (upper/lower) using the
  ;; standard trigram bit mapping in futon3/HOWTO-hexagrams.md.
  ["111111" "000000" "010100" "001010" "010111" "111010" "000010" "010000"
   "111011" "110111" "000111" "111000" "111101" "101111" "001000" "000100"
   "110100" "011001" "110000" "000011" "100101" "101001" "000001" "100000"
   "100111" "111001" "001100" "011110" "010010" "101101" "001110" "100011"
   "001111" "111100" "101000" "000101" "011101" "101110" "010001" "100010"
   "110001" "011100" "111110" "011111" "000110" "011000" "010110" "010011"
   "110101" "101011" "100100" "001001" "001011" "100110" "101100" "001101"
   "011011" "110110" "011010" "110010" "110011" "100001" "010101" "101010"])

(defn- bit->line [ch]
  (if (= ch \1) :yang :yin))

(defn- line->bit [line]
  (if (= line :yang) \1 \0))

(defn binary->lines
  "Convert a 6-bit binary string (top-to-bottom) into lines (bottom-to-top)."
  [binary]
  (->> (seq (or binary ""))
       reverse
       (mapv bit->line)))

(defn lines->binary
  "Convert lines (bottom-to-top) into a 6-bit binary string (top-to-bottom)."
  [lines]
  (->> (or lines [])
       (map line->bit)
       reverse
       (apply str)))

(def ^:private king-wen-lines
  (mapv binary->lines king-wen-binaries))

(defn hexagram-number->lines
  "Lookup hexagram lines (bottom-to-top) by King Wen number (1-64)."
  [n]
  (when (and (integer? n) (<= 1 n 64))
    (nth king-wen-lines (dec n))))

(defn lines->hexagram-number
  "Lookup King Wen number for a 6-line vector (bottom-to-top)."
  [lines]
  (when (and (vector? lines) (= 6 (count lines)))
    (some (fn [[idx entry]]
            (when (= entry lines) (inc idx)))
          (map-indexed vector king-wen-lines))))

(defn lines->hexagram
  "Build a hexagram map from a 6-line vector."
  [lines]
  (let [lines (vec lines)
        number (lines->hexagram-number lines)
        name (when number
               (some (fn [entry]
                       (when (= (:index entry) number) (:name entry)))
                     @iching/core-hexagrams))]
    {:lines lines
     :number number
     :name name}))

(defn hexagram-number->hexagram
  "Build a hexagram map from a King Wen number."
  [n]
  (when-let [lines (hexagram-number->lines n)]
    (lines->hexagram lines)))
