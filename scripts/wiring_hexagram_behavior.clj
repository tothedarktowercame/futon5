#!/usr/bin/env bb
;; Sample hexagrams from wiring run behavior
;;
;; This is the BEHAVIORAL mapping: what hexagram patterns does a wiring PRODUCE?
;; (As opposed to structural mapping based on wiring components)
;;
;; Usage: bb scripts/wiring_hexagram_behavior.clj <run-edn-path>
;;        bb scripts/wiring_hexagram_behavior.clj  # uses default L5-creative run

(ns wiring-hexagram-behavior
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; =============================================================================
;; Eigenvalue computation (simplified for bb - uses diagonal approximation)
;; =============================================================================

(defn- normalize-bits [bits size]
  (let [bits (cond
               (nil? bits) []
               (string? bits) (seq bits)
               (sequential? bits) bits
               :else (seq (str bits)))
        bits (map (fn [b]
                    (cond
                      (= b \1) 1 (= b \0) 0
                      (= b 1) 1 (= b 0) 0
                      (string? b) (if (= b "1") 1 0)
                      (number? b) (if (pos? (double b)) 1 0)
                      :else 0))
                  bits)
        padded (concat bits (repeat 0))]
    (vec (take size padded))))

(def ^:private matrix-layout
  [[:ego :right :right :right :right :right]
   [:left :ego :right :right :right :right]
   [:left :left :next :p1 :p1 :p1]
   [:left :left :p1 :next :p2 :p2]
   [:left :left :p1 :p2 :next :p3]
   [:left :left :p1 :p2 :p3 :off]])

(defn- fill-matrix [{:keys [left ego right next phenotype]}]
  (let [left (normalize-bits left 8)
        ego (normalize-bits ego 8)
        right (normalize-bits right 8)
        next (normalize-bits next 8)
        phenotype (normalize-bits phenotype 4)
        p1 (nth phenotype 0 0)
        p2 (nth phenotype 1 0)
        p3 (nth phenotype 2 0)
        off (nth phenotype 3 0)
        left-seq (atom (cycle left))
        ego-seq (atom (cycle ego))
        right-seq (atom (cycle right))
        next-seq (atom (cycle next))
        take-next (fn [aseq]
                    (let [curr @aseq]
                      (if (seq curr)
                        (let [v (first curr)]
                          (swap! aseq rest)
                          v)
                        0)))]
    (mapv (fn [row]
            (mapv (fn [label]
                    (case label
                      :left (take-next left-seq)
                      :ego (take-next ego-seq)
                      :right (take-next right-seq)
                      :next (take-next next-seq)
                      :p1 p1 :p2 p2 :p3 p3 :off off
                      0))
                  row))
          matrix-layout)))

(defn- diagonal [matrix]
  (mapv (fn [idx] (get-in matrix [idx idx])) (range 6)))

(defn- diagonal->lines [diag]
  (mapv (fn [v] (if (pos? v) :yang :yin)) diag))

;; King Wen binary sequences (top-to-bottom)
(def king-wen-binaries
  ["111111" "000000" "010100" "001010" "010111" "111010" "000010" "010000"
   "111011" "110111" "000111" "111000" "111101" "101111" "001000" "000100"
   "110100" "011001" "110000" "000011" "100101" "101001" "000001" "100000"
   "100111" "111001" "001100" "011110" "010010" "101101" "001110" "100011"
   "001111" "111100" "101000" "000101" "011101" "101110" "010001" "100010"
   "110001" "011100" "111110" "011111" "000110" "011000" "010110" "010011"
   "110101" "101011" "100100" "001001" "001011" "100110" "101100" "001101"
   "011011" "110110" "011010" "110010" "110011" "100001" "010101" "101010"])

(defn- binary->lines [binary]
  (->> (seq (or binary ""))
       reverse
       (mapv (fn [ch] (if (= ch \1) :yang :yin)))))

(defn- lines->binary [lines]
  (->> (or lines [])
       (map (fn [l] (if (= l :yang) \1 \0)))
       reverse
       (apply str)))

(defn- lines->hexagram-number [lines]
  (when (and (vector? lines) (= 6 (count lines)))
    (let [binary (lines->binary lines)]
      (some (fn [[idx bin]]
              (when (= bin binary) (inc idx)))
            (map-indexed vector king-wen-binaries)))))

;; =============================================================================
;; Sigil → bits (simplified)
;; =============================================================================

(def sigil-bits-cache (atom {}))

(defn sigil->bits [sigil]
  (if-let [cached (get @sigil-bits-cache sigil)]
    cached
    (let [;; Simple hash-based bits for unknown sigils
          bits (format "%08d"
                       (mod (Math/abs (hash sigil)) 100000000))]
      (swap! sigil-bits-cache assoc sigil bits)
      bits)))

;; =============================================================================
;; Context sampling and hexagram extraction
;; =============================================================================

(defn sample-context-at [history t x]
  (let [rows (count history)
        row (nth history t nil)
        cols (when row (count row))]
    (when (and row (pos? cols) (< t (dec rows)) (<= 0 x) (< x cols))
      (let [next-row (nth history (inc t))
            sigil-at (fn [r idx]
                       (if (and r (<= 0 idx) (< idx (count r)))
                         (str (nth r idx))
                         "工"))  ; default
            pred (sigil-at row (dec x))
            self (sigil-at row x)
            succ (sigil-at row (inc x))
            out (sigil-at next-row x)]
        {:left (sigil->bits pred)
         :ego (sigil->bits self)
         :right (sigil->bits succ)
         :next (sigil->bits out)
         :phenotype "0000"  ; simplified
         :coord {:t t :x x}}))))

(defn context->hexagram [context]
  (when context
    (let [matrix (fill-matrix context)
          diag (diagonal matrix)
          lines (diagonal->lines diag)
          number (lines->hexagram-number lines)]
      {:number number
       :lines lines
       :diag diag})))

(defn sample-hexagrams [history num-samples seed]
  (let [rng (java.util.Random. seed)
        rows (count history)
        cols (count (first history))]
    (when (and (> rows 1) (pos? cols))
      (for [_ (range num-samples)
            :let [t (.nextInt rng (dec rows))
                  x (.nextInt rng cols)
                  ctx (sample-context-at history t x)]
            :when ctx]
        (assoc (context->hexagram ctx)
               :coord (:coord ctx))))))

;; =============================================================================
;; Analysis
;; =============================================================================

(defn hexagram-distribution [hexagrams]
  (let [numbers (keep :number hexagrams)
        freqs (frequencies numbers)
        total (count numbers)]
    {:total total
     :unique (count freqs)
     :distribution (into (sorted-map) freqs)
     :mode (when (seq freqs)
             (key (apply max-key val freqs)))
     :entropy (when (pos? total)
                (- (reduce + (for [[_ cnt] freqs
                                   :let [p (/ cnt total)]
                                   :when (pos? p)]
                               (* p (Math/log p))))))}))

(defn analyze-run [run-path]
  (println "Loading run:" run-path)
  (let [run (edn/read-string (slurp run-path))
        history (or (:genotype-history run)
                    (:history run)
                    (get-in run [:result :genotype-history]))
        _ (println "History rows:" (count history))
        _ (println "History cols:" (count (first history)))

        ;; Sample hexagrams
        hexagrams (sample-hexagrams history 500 42)
        dist (hexagram-distribution hexagrams)]

    (println "\n=== Hexagram Distribution ===")
    (println "Total samples:" (:total dist))
    (println "Unique hexagrams:" (:unique dist))
    (println "Mode (most common):" (:mode dist))
    (println "Entropy:" (format "%.3f" (or (:entropy dist) 0.0)))

    (println "\n=== Top 10 Hexagrams ===")
    (doseq [[hex-num cnt] (take 10 (sort-by (comp - val) (:distribution dist)))]
      (let [pct (* 100.0 (/ cnt (:total dist)))]
        (println (format "  #%2d: %3d (%.1f%%)" hex-num cnt pct))))

    ;; Check for Rule 90 signature (63/64 prominence)
    (let [h63 (get (:distribution dist) 63 0)
          h64 (get (:distribution dist) 64 0)
          rule90-ratio (/ (+ h63 h64) (max 1 (:total dist)))]
      (println "\n=== Rule 90 Signature ===")
      (println "Hexagram 63 (既濟):" h63)
      (println "Hexagram 64 (未濟):" h64)
      (println "63+64 ratio:" (format "%.1f%%" (* 100 rule90-ratio)))
      (when (> rule90-ratio 0.1)
        (println "*** Rule 90 signature detected! ***")))

    dist))

(defn -main [& args]
  (let [run-path (or (first args)
                     "/tmp/exp-wiring-ladder2/L5-creative-seed-352362012.edn")]
    (if (.exists (io/file run-path))
      (analyze-run run-path)
      (println "Run file not found:" run-path))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
