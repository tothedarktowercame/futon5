#!/usr/bin/env clj -M
;; Generate PNG spacetime diagrams for CA rules
;;
;; Usage: clj -M -e '(load-file "scripts/rule_photo_essay_png.clj")'

(ns rule-photo-essay-png
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [futon5.mmca.runtime :as mmca]
            [futon5.ca.core :as ca])
  (:import [java.awt.image BufferedImage]
           [java.awt Color]
           [javax.imageio ImageIO]))

(defn random-genotype [len seed]
  (let [rng (java.util.Random. seed)
        sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly len #(nth sigils (.nextInt rng (count sigils)))))))

(defn sigil->color
  "Map sigil to color based on bit pattern."
  [sigil]
  (let [bits (ca/bits-for (str sigil))
        ;; Use bits to create RGB values
        r (+ 20 (* 25 (+ (if (= (nth bits 0) \1) 1 0)
                         (if (= (nth bits 1) \1) 2 0)
                         (if (= (nth bits 2) \1) 4 0))))
        g (+ 20 (* 25 (+ (if (= (nth bits 3) \1) 1 0)
                         (if (= (nth bits 4) \1) 2 0)
                         (if (= (nth bits 5) \1) 4 0))))
        b (+ 20 (* 25 (+ (if (= (nth bits 6) \1) 1 0)
                         (if (= (nth bits 7) \1) 2 0))))]
    (Color. (min 255 r) (min 255 g) (min 255 b))))

(defn sigil->grayscale
  "Map sigil to grayscale based on bit count."
  [sigil]
  (let [bits (ca/bits-for (str sigil))
        ones (count (filter #(= % \1) bits))
        v (int (* 255 (/ ones 8.0)))]
    (Color. v v v)))

(defn render-spacetime-png
  "Render spacetime diagram as PNG image."
  [history output-path & {:keys [cell-size color-mode]
                          :or {cell-size 4 color-mode :color}}]
  (let [rows (count history)
        cols (count (first history))
        width (* cols cell-size)
        height (* rows cell-size)
        img (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        g (.createGraphics img)
        color-fn (if (= color-mode :grayscale) sigil->grayscale sigil->color)]

    ;; Fill background
    (.setColor g Color/BLACK)
    (.fillRect g 0 0 width height)

    ;; Draw cells
    (doseq [[row-idx row] (map-indexed vector history)
            [col-idx sigil] (map-indexed vector (seq row))]
      (let [color (color-fn sigil)
            x (* col-idx cell-size)
            y (* row-idx cell-size)]
        (.setColor g color)
        (.fillRect g x y cell-size cell-size)))

    (.dispose g)
    (ImageIO/write img "PNG" (io/file output-path))
    output-path))

(defn wiring->mermaid
  "Convert wiring diagram to Mermaid flowchart."
  [wiring]
  (let [diagram (:diagram wiring)
        nodes (:nodes diagram)
        edges (:edges diagram)

        node-lines (for [{:keys [id component]} nodes]
                     (let [id-str (name id)
                           label (name (or component id))
                           shape (cond
                                   (str/includes? label "context") (str id-str "[" label "]")
                                   (str/includes? label "output") (str id-str "([" label "])")
                                   (str/includes? label "bit-") (str id-str "{" label "}")
                                   :else (str id-str "(" label ")"))]
                       (str "    " shape)))

        edge-lines (for [{:keys [from to to-port]} edges]
                     (let [from-str (name from)
                           to-str (name to)]
                       (if to-port
                         (str "    " from-str " -->|" (name to-port) "| " to-str)
                         (str "    " from-str " --> " to-str))))]

    (str "```mermaid\nflowchart TD\n"
         (str/join "\n" node-lines)
         "\n"
         (str/join "\n" edge-lines)
         "\n```")))

(defn run-and-render [rule-info seed generations width output-dir]
  (let [{:keys [path name]} rule-info
        wiring (edn/read-string (slurp path))
        genotype (random-genotype width seed)
        phenotype (apply str (repeat width "0"))

        _ (println "Running" name "...")
        run (mmca/run-mmca {:genotype genotype
                            :phenotype phenotype
                            :generations generations
                            :seed seed
                            :wiring wiring})
        history (:gen-history run)

        ;; Generate both color and grayscale versions
        color-path (str output-dir "/" (str/lower-case (str/replace name " " "-")) "-color.png")
        gray-path (str output-dir "/" (str/lower-case (str/replace name " " "-")) "-gray.png")]

    (render-spacetime-png history color-path :cell-size 4 :color-mode :color)
    (render-spacetime-png history gray-path :cell-size 4 :color-mode :grayscale)

    {:wiring wiring
     :history history
     :color-png color-path
     :gray-png gray-path}))

(defn generate-essay []
  (let [rules [{:path "data/wiring-rules/rule-090.edn"
                :name "Rule 90"
                :class "Class 3"
                :desc "XOR(left, right) - Sierpinski triangle patterns, fractal structure"}
               {:path "data/wiring-rules/rule-110.edn"
                :name "Rule 110"
                :class "Class 4"
                :desc "Turing-complete, produces localized structures and 'gliders'"}
               {:path "data/wiring-rules/rule-030.edn"
                :name "Rule 30"
                :class "Class 3"
                :desc "Chaotic, used for randomness generation in Mathematica"}
               {:path "data/wiring-rules/rule-184.edn"
                :name "Rule 184"
                :class "Class 2"
                :desc "Traffic/particle flow - conserves number of particles"}
               {:path "data/wiring-rules/rule-054.edn"
                :name "Rule 54"
                :class "Class 4"
                :desc "Complex periodic structures with localized patterns"}]

        seed 352362012
        generations 80
        width 120

        output-dir "reports/images"
        _ (.mkdirs (io/file output-dir))

        results (mapv #(assoc % :result (run-and-render % seed generations width output-dir)) rules)

        sb (StringBuilder.)]

    (.append sb "# Elementary CA Rules as MMCA Wirings\n\n")
    (.append sb "A visual exploration of classic cellular automaton rules implemented as wiring diagrams.\n\n")
    (.append sb (str "**Seed**: " seed " | **Generations**: " generations " | **Width**: " width "\n\n"))
    (.append sb "Each rule is implemented as a wiring diagram that computes the next cell state from the neighborhood.\n\n")
    (.append sb "---\n\n")

    (doseq [{:keys [name class desc result path]} results]
      (let [{:keys [wiring color-png gray-png]} result
            formula (get-in wiring [:meta :formula])
            mermaid (wiring->mermaid wiring)
            ;; Relative paths for GitHub
            color-rel (str "images/" (last (str/split color-png #"/")))
            gray-rel (str "images/" (last (str/split gray-png #"/")))]

        (.append sb (str "## " name " (" class ")\n\n"))
        (.append sb (str "**" desc "**\n\n"))
        (.append sb (str "Formula: `" formula "`\n\n"))

        (.append sb "### Wiring Diagram\n\n")
        (.append sb mermaid)
        (.append sb "\n\n")

        (.append sb "### Spacetime Diagrams\n\n")
        (.append sb "| Color (RGB from bits) | Grayscale (bit count) |\n")
        (.append sb "|:---------------------:|:---------------------:|\n")
        (.append sb (str "| ![" name " color](" color-rel ") | ![" name " gray](" gray-rel ") |\n\n"))

        (.append sb "---\n\n")))

    ;; Add comparison section
    (.append sb "## Comparison\n\n")
    (.append sb "| Rule | Wolfram Class | Key Property | Formula |\n")
    (.append sb "|------|---------------|--------------|--------|\n")
    (.append sb "| 90 | 3 (Chaotic) | Sierpinski fractal | `L XOR R` |\n")
    (.append sb "| 110 | 4 (Complex) | Turing-complete | `(C OR R) AND NOT(L AND C AND R)` |\n")
    (.append sb "| 30 | 3 (Chaotic) | High entropy | `L XOR (C OR R)` |\n")
    (.append sb "| 184 | 2 (Periodic) | Particle conservation | `(C AND R) OR (L AND NOT C)` |\n")
    (.append sb "| 54 | 4 (Complex) | Localized structures | `(NOT L AND (C XOR R)) OR (L AND NOT C)` |\n\n")

    (.append sb "## Hexagram Connections\n\n")
    (.append sb "When these rules run on 8-bit sigils, the exotype sampling shows different hexagram distributions:\n\n")
    (.append sb "- **Rule 90** (XOR) produces alternating bitplane patterns mapping to hexagrams 63/64 (既濟/未濟)\n")
    (.append sb "- **Rule 184** (traffic) shows highest affinity for hexagram 11 (泰 - Peace)\n")
    (.append sb "- The 8-bit sigil space creates richer dynamics than binary CAs\n\n")

    (.append sb "## Technical Notes\n\n")
    (.append sb "- **Color images**: RGB derived from 8 sigil bits (bits 0-2 → R, bits 3-5 → G, bits 6-7 → B)\n")
    (.append sb "- **Grayscale images**: Brightness proportional to number of 1-bits in sigil\n")
    (.append sb "- Each wiring implements the rule's boolean formula using `bit-xor`, `bit-and`, `bit-or`, `bit-not` components\n")

    (let [output-path "reports/rule-photo-essay.md"]
      (spit output-path (str sb))
      (println "\nPhoto essay written to:" output-path)
      (println "Images written to:" output-dir)
      output-path)))

(generate-essay)
