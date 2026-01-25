#!/usr/bin/env clj -M
;; Run a wiring diagram as a cellular automaton
;;
;; This script properly executes wiring diagrams through the xenotype interpreter,
;; applying the wiring rule to each cell at each generation.
;;
;; Usage: clj -M -e '(load-file "scripts/run_wiring_ca.clj")'

(ns run-wiring-ca
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [futon5.ca.core :as ca]
            [futon5.xenotype.interpret :as interpret]
            [futon5.xenotype.generator :as generator])
  (:import [java.awt.image BufferedImage]
           [java.awt Color]
           [javax.imageio ImageIO]))

(defn evolve-cell-with-wiring
  "Apply wiring diagram to a single cell, given its neighborhood context."
  [diagram pred-sigil self-sigil succ-sigil]
  (let [ctx {:pred (str pred-sigil)
             :self (str self-sigil)
             :succ (str succ-sigil)}
        result (interpret/evaluate-diagram (:diagram diagram) {:ctx ctx} generator/generator-registry)
        output-node (:output (:diagram diagram))
        output-value (get-in result [:node-values output-node :out])]
    (or output-value self-sigil)))

(defn evolve-genotype-with-wiring
  "Evolve entire genotype string using wiring diagram."
  [diagram genotype]
  (let [len (count genotype)
        chars (vec (seq genotype))]
    (apply str
           (for [i (range len)]
             (let [pred (get chars (mod (dec i) len))
                   self (get chars i)
                   succ (get chars (mod (inc i) len))]
               (evolve-cell-with-wiring diagram pred self succ))))))

(defn run-wiring-ca
  "Run a wiring diagram as a CA for multiple generations.
   Returns {:gen-history [strings...]}"
  [{:keys [diagram genotype generations]}]
  (loop [history [genotype]
         current genotype
         gen 0]
    (if (>= gen generations)
      {:gen-history history}
      (let [next-gen (evolve-genotype-with-wiring diagram current)]
        (recur (conj history next-gen) next-gen (inc gen))))))

;; Pure Rule 90 for comparison
(defn pure-rule90-step [row]
  (let [n (count row)]
    (vec (for [i (range n)]
           (let [left (get row (dec i) 0)
                 right (get row (inc i) 0)]
             (bit-xor left right))))))

(defn run-pure-rule90 [initial-row generations]
  (loop [history [initial-row]
         row initial-row
         gen 0]
    (if (>= gen generations)
      history
      (let [next-row (pure-rule90-step row)]
        (recur (conj history next-row) next-row (inc gen))))))

;; Rendering
(defn render-binary-history [history output-path cell-size]
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

(defn sigil-history->bitplane
  "Extract a single bitplane from sigil history."
  [history plane-idx]
  (for [row history]
    (vec (for [sigil (seq row)]
           (let [bits (ca/bits-for (str sigil))]
             (if (= (nth bits plane-idx) \1) 1 0))))))

(defn render-sigil-bitplane [history plane-idx output-path cell-size]
  (let [bitplane (sigil-history->bitplane history plane-idx)]
    (render-binary-history bitplane output-path cell-size)))

;; Test sigils: find ones that map to all-0 and all-1 bits
(defn find-binary-sigils []
  (let [sigils (ca/sigil-entries)
        zero-sigil (first (filter #(= "00000000" (ca/bits-for (:sigil %))) sigils))
        one-sigil (first (filter #(= "11111111" (ca/bits-for (:sigil %))) sigils))]
    {:zero (or (:sigil zero-sigil) (:sigil (first sigils)))
     :one (or (:sigil one-sigil) (:sigil (last sigils)))}))

(defn single-point-genotype [width]
  (let [{:keys [zero one]} (find-binary-sigils)
        mid (quot width 2)]
    (println "Zero sigil:" zero "bits:" (ca/bits-for zero))
    (println "One sigil:" one "bits:" (ca/bits-for one))
    (apply str (concat (repeat mid zero)
                       [one]
                       (repeat (- width mid 1) zero)))))

(defn -main []
  (let [width 151
        generations 75
        output-dir "reports/images"]

    (.mkdirs (io/file output-dir))

    ;; Test 1: Pure Rule 90 (binary, single-point seed)
    (println "\n=== Test 1: Pure Rule 90 (binary) ===")
    (let [initial (vec (concat (repeat (quot width 2) 0) [1] (repeat (quot width 2) 0)))
          history (run-pure-rule90 initial generations)
          path (str output-dir "/rule-90-pure-sierpinski.png")]
      (render-binary-history history path 4)
      (println "Wrote:" path))

    ;; Test 2: Wiring Rule 90 with single-point seed
    (println "\n=== Test 2: Wiring Rule 90 (single-point seed) ===")
    (let [wiring (edn/read-string (slurp "data/wiring-rules/rule-090.edn"))
          genotype (single-point-genotype width)
          _ (println "Initial genotype length:" (count genotype))
          _ (println "First 10 chars:" (subs genotype 0 (min 10 (count genotype))))
          run (run-wiring-ca {:diagram wiring
                              :genotype genotype
                              :generations generations})
          history (:gen-history run)]

      (println "Generations run:" (count history))

      ;; Extract bitplanes and render
      (doseq [plane-idx [0 7]]
        (let [path (str output-dir "/rule-90-wiring-sierpinski-bit" plane-idx ".png")]
          (render-sigil-bitplane history plane-idx path 4)
          (println "Wrote:" path))))

    ;; Test 3: Quick validation - does the wiring produce XOR?
    (println "\n=== Test 3: XOR validation ===")
    (let [wiring (edn/read-string (slurp "data/wiring-rules/rule-090.edn"))
          {:keys [zero one]} (find-binary-sigils)]
      (println "Testing XOR truth table:")
      (doseq [[l r] [[zero zero] [zero one] [one zero] [one one]]]
        (let [result (evolve-cell-with-wiring wiring l zero r)]
          (println (format "  XOR(%s, %s) = %s  [bits: %s XOR %s = %s]"
                           (ca/bits-for l) (ca/bits-for r) (ca/bits-for result)
                           l r result)))))

    (println "\nDone! Compare pure vs wiring outputs.")))

(-main)
