#!/usr/bin/env clj -M
;; Bitplane decomposition of CA rule output
;;
;; Usage: clj -M -e '(load-file "scripts/bitplane_decomposition.clj")'

(ns bitplane-decomposition
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [futon5.mmca.runtime :as mmca]
            [futon5.ca.core :as ca])
  (:import [java.awt.image BufferedImage]
           [java.awt Color Graphics2D Font]
           [javax.imageio ImageIO]))

(defn random-genotype [len seed]
  (let [rng (java.util.Random. seed)
        sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly len #(nth sigils (.nextInt rng (count sigils)))))))

(defn sigil->bits
  "Get 8 bits from a sigil as vector of 0/1."
  [sigil]
  (let [bits (ca/bits-for (str sigil))]
    (mapv #(if (= % \1) 1 0) bits)))

(defn history->bitplanes
  "Decompose history into 8 bitplane histories.
   Returns vector of 8 histories, each a vector of rows of 0/1."
  [history]
  (let [;; For each generation, extract all 8 bitplanes
        decomposed (for [row history]
                     (let [sigil-bits (mapv sigil->bits (seq row))]
                       ;; sigil-bits is vec of [b0 b1 b2 b3 b4 b5 b6 b7] per sigil
                       ;; We want 8 planes, each a row of bits
                       (for [plane-idx (range 8)]
                         (mapv #(nth % plane-idx) sigil-bits))))]
    ;; Transpose: from [gen -> [plane -> bits]] to [plane -> [gen -> bits]]
    (for [plane-idx (range 8)]
      (vec (for [gen decomposed]
             (vec (nth gen plane-idx)))))))

(defn render-bitplane-png
  "Render a single bitplane as black/white PNG."
  [bitplane-history output-path cell-size]
  (let [rows (count bitplane-history)
        cols (count (first bitplane-history))
        width (* cols cell-size)
        height (* rows cell-size)
        img (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        g (.createGraphics img)]

    ;; White background
    (.setColor g Color/WHITE)
    (.fillRect g 0 0 width height)

    ;; Draw black cells for 1s
    (.setColor g Color/BLACK)
    (doseq [[row-idx row] (map-indexed vector bitplane-history)
            [col-idx bit] (map-indexed vector row)]
      (when (= bit 1)
        (.fillRect g (* col-idx cell-size) (* row-idx cell-size) cell-size cell-size)))

    (.dispose g)
    (ImageIO/write img "PNG" (io/file output-path))
    output-path))

(defn render-all-bitplanes-grid
  "Render all 8 bitplanes in a 2x4 grid with labels."
  [bitplanes output-path cell-size]
  (let [rows (count (first bitplanes))
        cols (count (first (first bitplanes)))
        plane-width (* cols cell-size)
        plane-height (* rows cell-size)
        label-height 20
        padding 10
        ;; 2 rows x 4 cols of bitplanes
        total-width (+ (* 4 plane-width) (* 5 padding))
        total-height (+ (* 2 (+ plane-height label-height)) (* 3 padding))
        img (BufferedImage. total-width total-height BufferedImage/TYPE_INT_RGB)
        g (.createGraphics img)]

    ;; Gray background
    (.setColor g (Color. 240 240 240))
    (.fillRect g 0 0 total-width total-height)

    ;; Set font for labels
    (.setFont g (Font. "SansSerif" Font/BOLD 14))

    ;; Draw each bitplane
    (doseq [plane-idx (range 8)]
      (let [grid-row (quot plane-idx 4)
            grid-col (mod plane-idx 4)
            x-offset (+ padding (* grid-col (+ plane-width padding)))
            y-offset (+ padding (* grid-row (+ plane-height label-height padding)))
            bitplane (nth bitplanes plane-idx)]

        ;; Label
        (.setColor g Color/BLACK)
        (.drawString g (str "Bit " plane-idx) (+ x-offset 5) (+ y-offset 15))

        ;; White background for bitplane
        (.setColor g Color/WHITE)
        (.fillRect g x-offset (+ y-offset label-height) plane-width plane-height)

        ;; Draw black cells
        (.setColor g Color/BLACK)
        (doseq [[row-idx row] (map-indexed vector bitplane)
                [col-idx bit] (map-indexed vector row)]
          (when (= bit 1)
            (.fillRect g
                       (+ x-offset (* col-idx cell-size))
                       (+ y-offset label-height (* row-idx cell-size))
                       cell-size cell-size)))))

    (.dispose g)
    (ImageIO/write img "PNG" (io/file output-path))
    output-path))

(defn run-decomposition []
  (let [wiring-path "data/wiring-rules/rule-090.edn"
        wiring (edn/read-string (slurp wiring-path))
        seed 352362012
        generations 100
        width 150

        _ (println "Running Rule 90...")
        genotype (random-genotype width seed)
        phenotype (apply str (repeat width "0"))
        run (mmca/run-mmca {:genotype genotype
                            :phenotype phenotype
                            :generations generations
                            :seed seed
                            :wiring wiring})
        history (:gen-history run)

        _ (println "Decomposing into bitplanes...")
        bitplanes (history->bitplanes history)

        output-dir "reports/images"
        _ (.mkdirs (io/file output-dir))]

    ;; Render individual bitplanes
    (println "Rendering individual bitplanes...")
    (doseq [i (range 8)]
      (let [path (str output-dir "/rule-90-bitplane-" i ".png")]
        (render-bitplane-png (nth bitplanes i) path 3)
        (println "  Wrote:" path)))

    ;; Render combined grid
    (println "Rendering combined grid...")
    (let [grid-path (str output-dir "/rule-90-bitplanes-grid.png")]
      (render-all-bitplanes-grid bitplanes grid-path 3)
      (println "  Wrote:" grid-path))

    (println "\nDone! Check reports/images/ for bitplane decomposition.")))

(run-decomposition)
