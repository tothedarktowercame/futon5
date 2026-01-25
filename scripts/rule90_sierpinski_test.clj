#!/usr/bin/env clj -M
;; Test Rule 90 with single-point seed to verify Sierpinski triangle
;;
;; Usage: clj -M -e '(load-file "scripts/rule90_sierpinski_test.clj")'

(ns rule90-sierpinski-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [futon5.mmca.runtime :as mmca]
            [futon5.ca.core :as ca])
  (:import [java.awt.image BufferedImage]
           [java.awt Color]
           [javax.imageio ImageIO]))

;; First, let's test pure Rule 90 manually without MMCA framework
(defn pure-rule90-step
  "Apply Rule 90 (XOR of neighbors) to a binary row."
  [row]
  (let [n (count row)]
    (vec (for [i (range n)]
           (let [left (get row (dec i) 0)
                 right (get row (inc i) 0)]
             (bit-xor left right))))))

(defn run-pure-rule90
  "Run pure Rule 90 for n generations from initial row."
  [initial-row generations]
  (loop [history [initial-row]
         row initial-row
         gen 0]
    (if (>= gen generations)
      history
      (let [next-row (pure-rule90-step row)]
        (recur (conj history next-row) next-row (inc gen))))))

(defn render-binary-history
  "Render binary history as PNG."
  [history output-path cell-size]
  (let [rows (count history)
        cols (count (first history))
        width (* cols cell-size)
        height (* rows cell-size)
        img (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        g (.createGraphics img)]

    (.setColor g Color/WHITE)
    (.fillRect g 0 0 width height)

    (.setColor g Color/BLACK)
    (doseq [[row-idx row] (map-indexed vector history)
            [col-idx bit] (map-indexed vector row)]
      (when (= bit 1)
        (.fillRect g (* col-idx cell-size) (* row-idx cell-size) cell-size cell-size)))

    (.dispose g)
    (ImageIO/write img "PNG" (io/file output-path))
    output-path))

(defn single-point-genotype
  "Create a genotype with single '1' sigil in center, rest '0' sigils."
  [width]
  (let [;; Find sigils that map to all-zeros and all-ones bits
        sigils (ca/sigil-entries)
        zero-sigil (first (filter #(= "00000000" (ca/bits-for (:sigil %))) sigils))
        one-sigil (first (filter #(= "11111111" (ca/bits-for (:sigil %))) sigils))
        ;; Fallback to first sigil with mostly 0s and one with mostly 1s
        zero-sig (or (:sigil zero-sigil)
                     (:sigil (first (sort-by #(count (filter (fn [c] (= c \1)) (ca/bits-for (:sigil %)))) sigils))))
        one-sig (or (:sigil one-sigil)
                    (:sigil (last (sort-by #(count (filter (fn [c] (= c \1)) (ca/bits-for (:sigil %)))) sigils))))
        mid (quot width 2)]
    (println "Zero sigil:" zero-sig "bits:" (ca/bits-for zero-sig))
    (println "One sigil:" one-sig "bits:" (ca/bits-for one-sig))
    (apply str (concat (repeat mid zero-sig)
                       [one-sig]
                       (repeat (- width mid 1) zero-sig)))))

(defn -main []
  (let [width 151
        generations 75
        output-dir "reports/images"]

    (.mkdirs (io/file output-dir))

    ;; Test 1: Pure Rule 90 (no MMCA)
    (println "\n=== Test 1: Pure Rule 90 (binary, single-point seed) ===")
    (let [initial (vec (concat (repeat (quot width 2) 0) [1] (repeat (quot width 2) 0)))
          history (run-pure-rule90 initial generations)
          path (str output-dir "/rule-90-pure-sierpinski.png")]
      (render-binary-history history path 4)
      (println "Wrote:" path))

    ;; Test 2: MMCA Rule 90 with single-point seed
    (println "\n=== Test 2: MMCA Rule 90 (single-point seed) ===")
    (let [wiring (edn/read-string (slurp "data/wiring-rules/rule-090.edn"))
          genotype (single-point-genotype width)
          _ (println "Initial genotype (first 20 chars):" (subs genotype 0 (min 20 (count genotype))))
          phenotype (apply str (repeat width "0"))
          run (mmca/run-mmca {:genotype genotype
                              :phenotype phenotype
                              :generations generations
                              :seed 42
                              :wiring wiring})
          history (:gen-history run)]

      ;; Extract bitplane 0 and render
      (println "Extracting bitplanes...")
      (doseq [plane-idx [0 7]]
        (let [bitplane (for [row history]
                         (vec (for [sigil (seq row)]
                                (let [bits (ca/bits-for (str sigil))]
                                  (if (= (nth bits plane-idx) \1) 1 0)))))
              path (str output-dir "/rule-90-mmca-sierpinski-bit" plane-idx ".png")]
          (render-binary-history bitplane path 4)
          (println "Wrote:" path))))

    (println "\nDone! Compare pure vs MMCA outputs.")))

(-main)
