#!/usr/bin/env clj -M
;; Generate test sheet for all 64 hexagram wirings
;;
;; Usage: clj -M -e '(load-file "scripts/hexagram_wiring_sheet.clj")'

(ns hexagram-wiring-sheet
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.wiring.hexagram :as hex-wiring]
            [futon5.wiring.runtime :as runtime]
            [futon5.wiring.embedding :as embed]
            [futon5.hexagram.lines :as lines]
            [futon5.ca.core :as ca])
  (:import [java.awt.image BufferedImage]
           [java.awt Color Font]
           [javax.imageio ImageIO]))

;;; ============================================================
;;; Rendering
;;; ============================================================

(defn- sigil->gray [sigil]
  (let [bits (ca/bits-for (str sigil))
        ones (count (filter #(= % \1) bits))]
    (int (* (/ ones 8.0) 255))))

(defn render-mini-spacetime
  "Render a small spacetime diagram (no labels)."
  [history width height]
  (let [rows (count history)
        cols (count (first history))
        cell-w (max 1 (/ width cols))
        cell-h (max 1 (/ height rows))
        img (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        g (.createGraphics img)]
    (.setColor g Color/WHITE)
    (.fillRect g 0 0 width height)
    (doseq [[row-idx row] (map-indexed vector history)
            [col-idx sigil] (map-indexed vector row)]
      (let [v (sigil->gray sigil)
            color (Color. v v v)
            x (int (* col-idx cell-w))
            y (int (* row-idx cell-h))]
        (.setColor g color)
        (.fillRect g x y (max 1 (int cell-w)) (max 1 (int cell-h)))))
    (.dispose g)
    img))

(defn render-hexagram-lines
  "Render hexagram lines as traditional symbol."
  [hex-lines width height]
  (let [img (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        g (.createGraphics img)
        line-height (/ height 7)
        line-width (* width 0.8)
        x-offset (* width 0.1)
        gap (* line-width 0.15)]
    (.setColor g Color/WHITE)
    (.fillRect g 0 0 width height)
    (.setColor g Color/BLACK)
    (doseq [[idx line] (map-indexed vector (reverse hex-lines))]
      (let [y (+ line-height (* idx line-height))]
        (if (= line :yang)
          ;; Solid line
          (.fillRect g (int x-offset) (int y) (int line-width) (int (* line-height 0.6)))
          ;; Broken line (two segments)
          (let [seg-width (/ (- line-width gap) 2)]
            (.fillRect g (int x-offset) (int y) (int seg-width) (int (* line-height 0.6)))
            (.fillRect g (int (+ x-offset seg-width gap)) (int y) (int seg-width) (int (* line-height 0.6)))))))
    (.dispose g)
    img))

(defn render-featured-hexagram
  "Render a larger, labeled spacetime diagram for a featured hexagram."
  [n result hex-info output-path]
  (let [history (:history result)
        ;; Larger dimensions
        spacetime-w 400
        spacetime-h 250
        hex-symbol-w 60
        hex-symbol-h 80
        padding 20
        header-h 40

        total-w (+ spacetime-w hex-symbol-w (* 3 padding))
        total-h (+ spacetime-h header-h (* 2 padding))

        img (BufferedImage. total-w total-h BufferedImage/TYPE_INT_RGB)
        g (.createGraphics img)]

    ;; Background
    (.setColor g Color/WHITE)
    (.fillRect g 0 0 total-w total-h)

    ;; Header with hexagram info
    (.setColor g Color/BLACK)
    (.setFont g (Font. "SansSerif" Font/BOLD 16))
    (.drawString g (format "#%d %s" n (:name hex-info)) padding 25)

    (.setFont g (Font. "Monospaced" Font/PLAIN 12))
    (.drawString g (or (:formula result) "?") (+ padding 150) 25)

    ;; Hexagram symbol on left
    (when-let [lines (:lines hex-info)]
      (let [hex-img (render-hexagram-lines lines hex-symbol-w hex-symbol-h)]
        (.drawImage g hex-img padding (+ header-h padding) nil)))

    ;; Spacetime diagram
    (when history
      (let [st-img (render-mini-spacetime history spacetime-w spacetime-h)]
        (.drawImage g st-img (+ hex-symbol-w (* 2 padding)) (+ header-h padding) nil)))

    ;; Metrics below spacetime
    (.setFont g (Font. "SansSerif" Font/PLAIN 11))
    (.drawString g (format "Entropy: %.3f  |  Unique sigils: %d"
                           (or (get-in result [:metrics :entropy-n]) 0.0)
                           (or (get-in result [:metrics :unique]) 0))
                 (+ hex-symbol-w (* 2 padding))
                 (+ header-h spacetime-h padding 15))

    (.dispose g)
    (ImageIO/write img "PNG" (io/file output-path))
    output-path))

;;; ============================================================
;;; Grid Sheet
;;; ============================================================

(defn generate-sheet
  "Generate an 8x8 grid sheet of all 64 hexagrams."
  [results output-path]
  (let [cell-w 120
        cell-h 100
        spacetime-w 60
        spacetime-h 40
        hex-symbol-w 30
        hex-symbol-h 40
        padding 5
        cols 8
        rows 8
        width (* cols cell-w)
        height (* rows cell-h)
        img (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        g (.createGraphics img)]

    ;; Background
    (.setColor g (Color. 250 250 250))
    (.fillRect g 0 0 width height)

    ;; Draw each hexagram
    (.setFont g (Font. "SansSerif" Font/PLAIN 9))
    (doseq [n (range 1 65)]
      (let [result (get results n)
            col (mod (dec n) 8)
            row (quot (dec n) 8)
            x (* col cell-w)
            y (* row cell-h)
            hex-info (lines/hexagram-number->hexagram n)]

        ;; Cell border
        (.setColor g (Color. 220 220 220))
        (.drawRect g x y cell-w cell-h)

        ;; Hexagram number and name
        (.setColor g Color/BLACK)
        (.drawString g (str n) (+ x 3) (+ y 12))
        (when-let [name (:name hex-info)]
          (.drawString g (subs name 0 (min 8 (count name))) (+ x 20) (+ y 12)))

        ;; Hexagram symbol
        (when-let [lines (:lines hex-info)]
          (let [hex-img (render-hexagram-lines lines hex-symbol-w hex-symbol-h)]
            (.drawImage g hex-img (+ x 3) (+ y 16) nil)))

        ;; Spacetime diagram
        (when-let [history (:history result)]
          (let [st-img (render-mini-spacetime history spacetime-w spacetime-h)]
            (.drawImage g st-img (+ x 38) (+ y 18) nil)))

        ;; Metrics
        (when-let [metrics (:metrics result)]
          (.setFont g (Font. "SansSerif" Font/PLAIN 8))
          (.drawString g (format "E:%.2f" (or (:entropy-n metrics) 0.0))
                       (+ x 3) (+ y 70))
          (.drawString g (format "U:%d" (or (:unique metrics) 0))
                       (+ x 50) (+ y 70))
          (.setFont g (Font. "SansSerif" Font/PLAIN 9)))

        ;; Formula
        (when-let [formula (:formula result)]
          (.setFont g (Font. "Monospaced" Font/PLAIN 7))
          (.drawString g (subs formula 0 (min 18 (count formula)))
                       (+ x 3) (+ y 85))
          (.setFont g (Font. "SansSerif" Font/PLAIN 9)))))

    (.dispose g)
    (ImageIO/write img "PNG" (io/file output-path))
    output-path))

;;; ============================================================
;;; Report Generation
;;; ============================================================

(defn generate-report [results output-path output-dir]
  (let [sb (StringBuilder.)
        timestamp (java.time.LocalDateTime/now)
        sorted (sort-by #(get-in (val %) [:metrics :entropy-n] 0) results)
        lowest-5 (take 5 sorted)
        highest-5 (take 5 (reverse sorted))]

    (.append sb "# Hexagram Wiring Test Sheet\n\n")
    (.append sb (str "*Generated: " timestamp "*\n\n"))
    (.append sb "All 64 I Ching hexagrams mapped to wiring diagrams and executed.\n\n")
    (.append sb "![Hexagram Grid](images/hexagram-wiring-grid.png)\n\n")
    (.append sb "---\n\n")

    ;; Entropy Extremes with images (moved up for visual impact)
    (.append sb "## Entropy Extremes\n\n")

    (.append sb "### Highest Entropy (Most Chaotic)\n\n")
    (.append sb "These hexagrams produce complex, information-rich patterns:\n\n")
    (doseq [[n result] highest-5]
      (let [hex-info (lines/hexagram-number->hexagram n)
            img-name (format "hex-%02d.png" n)]
        ;; Generate individual image
        (render-featured-hexagram n result hex-info (str output-dir "/" img-name))
        (.append sb (format "![Hexagram %d](images/%s)\n\n" n img-name))))

    (.append sb "### Lowest Entropy (Most Ordered)\n\n")
    (.append sb "These hexagrams collapse to uniform or simple patterns:\n\n")
    (doseq [[n result] lowest-5]
      (let [hex-info (lines/hexagram-number->hexagram n)
            img-name (format "hex-%02d.png" n)]
        ;; Generate individual image
        (render-featured-hexagram n result hex-info (str output-dir "/" img-name))
        (.append sb (format "![Hexagram %d](images/%s)\n\n" n img-name))))

    (.append sb "---\n\n")

    ;; Summary table
    (.append sb "## Summary by Trigram Pair\n\n")
    (.append sb "| # | Name | Lines | Formula | Entropy | Unique |\n")
    (.append sb "|---|------|-------|---------|---------|--------|\n")
    (doseq [n (range 1 65)]
      (let [result (get results n)
            hex-info (lines/hexagram-number->hexagram n)
            lines-str (apply str (map #(if (= % :yang) "⚊" "⚋") (:lines hex-info)))]
        (.append sb (format "| %d | %s | %s | `%s` | %.2f | %d |\n"
                            n
                            (or (:name hex-info) "?")
                            lines-str
                            (or (:formula result) "?")
                            (or (get-in result [:metrics :entropy-n]) 0.0)
                            (or (get-in result [:metrics :unique]) 0)))))
    (.append sb "\n")

    ;; Methodology
    (.append sb "## Methodology\n\n")
    (.append sb "Each hexagram's 6 lines determine wiring structure:\n\n")
    (.append sb "| Lines | Stage | Yang (⚊) | Yin (⚋) |\n")
    (.append sb "|-------|-------|----------|--------|\n")
    (.append sb "| 1-2 | Input | context-neighbors | context-self |\n")
    (.append sb "| 3-4 | Core | bit-xor | bit-and |\n")
    (.append sb "| 5-6 | Output | bit-xor | bit-or |\n\n")
    (.append sb "Line pairs select from 4 component variants (00, 01, 10, 11).\n")

    (spit output-path (str sb))
    output-path))

;;; ============================================================
;;; Main
;;; ============================================================

(defn -main []
  (println "=== Hexagram Wiring Test Sheet ===\n")

  (let [seed 352362012
        generations 50
        width 80
        output-dir "reports/images"

        _ (.mkdirs (io/file output-dir))
        _ (println "Generating wirings for all 64 hexagrams...")

        results
        (into {}
              (for [n (range 1 65)]
                (let [_ (when (zero? (mod n 8)) (println (str "  Processing hexagram " n "...")))
                      wiring (hex-wiring/hexagram->simple-wiring n)
                      genotype (runtime/random-genotype width seed)
                      run (try
                            (runtime/run-wiring {:wiring wiring
                                                 :genotype genotype
                                                 :generations generations
                                                 :collect-metrics? true})
                            (catch Exception e
                              (println (str "  Warning: hexagram " n " failed: " (.getMessage e)))
                              nil))
                      final-metrics (when run (last (:metrics-history run)))]
                  [n {:history (when run (take 50 (:gen-history run)))
                      :metrics {:entropy-n (:entropy-n final-metrics)
                                :unique (:unique-sigils final-metrics)}
                      :formula (get-in wiring [:meta :formula])}])))]

    (println "\nGenerating grid image...")
    (generate-sheet results (str output-dir "/hexagram-wiring-grid.png"))

    (println "Generating report with featured hexagram images...")
    (generate-report results "reports/hexagram-wiring-sheet.md" output-dir)

    (println "\nDone!")
    (println "  Grid: reports/images/hexagram-wiring-grid.png")
    (println "  Featured: reports/images/hex-*.png")
    (println "  Report: reports/hexagram-wiring-sheet.md")))

(-main)
