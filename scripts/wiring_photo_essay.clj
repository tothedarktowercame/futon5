#!/usr/bin/env clj -M
;; Generate visual photo essay of elementary CA rules as wirings
;;
;; Uses the new futon5.wiring.runtime which properly executes wiring diagrams.
;;
;; Usage: clj -M -e '(load-file "scripts/wiring_photo_essay.clj")'

(ns wiring-photo-essay
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.wiring.runtime :as wiring]
            [futon5.ca.core :as ca])
  (:import [java.awt.image BufferedImage]
           [java.awt Color]
           [javax.imageio ImageIO]))

;;; ============================================================
;;; Image Rendering
;;; ============================================================

(defn sigil->gray
  "Convert sigil to grayscale value 0-255."
  [sigil]
  (let [bits (ca/bits-for (str sigil))
        ones (count (filter #(= % \1) bits))]
    (int (* (/ ones 8.0) 255))))

(defn sigil->color
  "Convert sigil to RGB color based on bit patterns."
  [sigil]
  (let [bits (ca/bits-for (str sigil))
        ;; Use different bit groups for R, G, B
        r-bits (take 3 bits)
        g-bits (take 3 (drop 2 bits))
        b-bits (take 3 (drop 5 bits))
        to-val (fn [bs] (int (* (/ (count (filter #(= % \1) bs)) 3.0) 255)))]
    (Color. (to-val r-bits) (to-val g-bits) (to-val b-bits))))

(defn render-spacetime
  "Render genotype history as a spacetime diagram."
  [history output-path cell-size color-fn]
  (let [rows (count history)
        cols (count (first history))
        width (* cols cell-size)
        height (* rows cell-size)
        img (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        g (.createGraphics img)]
    ;; White background
    (.setColor g Color/WHITE)
    (.fillRect g 0 0 width height)
    ;; Draw cells
    (doseq [[row-idx row] (map-indexed vector history)
            [col-idx sigil] (map-indexed vector row)]
      (let [color (color-fn sigil)]
        (.setColor g color)
        (.fillRect g (* col-idx cell-size) (* row-idx cell-size) cell-size cell-size)))
    (.dispose g)
    (ImageIO/write img "PNG" (io/file output-path))
    output-path))

(defn render-grayscale [history output-path cell-size]
  (render-spacetime history output-path cell-size
                    (fn [sigil]
                      (let [v (sigil->gray sigil)]
                        (Color. v v v)))))

(defn render-color [history output-path cell-size]
  (render-spacetime history output-path cell-size sigil->color))

(defn render-bitplane
  "Render a single bitplane as black/white."
  [history plane-idx output-path cell-size]
  (render-spacetime history output-path cell-size
                    (fn [sigil]
                      (let [bits (ca/bits-for (str sigil))
                            bit (nth bits plane-idx)]
                        (if (= bit \1) Color/BLACK Color/WHITE)))))

;;; ============================================================
;;; Report Generation
;;; ============================================================

(def rules
  [{:id :rule-090 :name "Rule 90" :path "data/wiring-rules/rule-090.edn"
    :class 3 :description "XOR(L,R) - Sierpinski triangle, additive"}
   {:id :rule-110 :name "Rule 110" :path "data/wiring-rules/rule-110.edn"
    :class 4 :description "Turing-complete, localized structures"}
   {:id :rule-030 :name "Rule 30" :path "data/wiring-rules/rule-030.edn"
    :class 3 :description "L XOR (C OR R) - chaotic, used for randomness"}
   {:id :rule-184 :name "Rule 184" :path "data/wiring-rules/rule-184.edn"
    :class 2 :description "Traffic flow, particle conservation"}
   {:id :rule-054 :name "Rule 54" :path "data/wiring-rules/rule-054.edn"
    :class 4 :description "Complex periodic structures"}])

(defn generate-mermaid [wiring-id]
  (case wiring-id
    :rule-090
    "```mermaid
flowchart LR
    L[\"L (pred)\"] --> XOR{XOR}
    R[\"R (succ)\"] --> XOR
    XOR --> OUT([output])
```"
    :rule-110
    "```mermaid
flowchart LR
    L[\"L\"] --> AND1{AND}
    C[\"C\"] --> AND1
    C --> OR{OR}
    R[\"R\"] --> OR
    AND1 --> AND2{AND}
    R --> AND2
    AND2 --> NOT{NOT}
    OR --> FINAL{AND}
    NOT --> FINAL
    FINAL --> OUT([output])
```"
    :rule-030
    "```mermaid
flowchart LR
    L[\"L\"] --> XOR{XOR}
    C[\"C\"] --> OR{OR}
    R[\"R\"] --> OR
    OR --> XOR
    XOR --> OUT([output])
```"
    :rule-184
    "```mermaid
flowchart LR
    L[\"L\"] --> AND2{AND}
    C[\"C\"] --> AND1{AND}
    C --> NOT{NOT}
    R[\"R\"] --> AND1
    NOT --> AND2
    AND1 --> OR{OR}
    AND2 --> OR
    OR --> OUT([output])
```"
    :rule-054
    "```mermaid
flowchart LR
    L[\"L\"] --> NOT1{NOT}
    C[\"C\"] --> XOR{XOR}
    R[\"R\"] --> XOR
    NOT1 --> AND1{AND}
    XOR --> AND1
    C --> NOT2{NOT}
    L --> AND2{AND}
    NOT2 --> AND2
    AND1 --> OR{OR}
    AND2 --> OR
    OR --> OUT([output])
```"
    ""))

(defn format-metrics-summary [metrics-history]
  (let [mid-idx (quot (count metrics-history) 2)
        mid-metrics (nth metrics-history mid-idx nil)
        final-metrics (last metrics-history)]
    (format "Entropy: %.2f → %.2f | Unique sigils: %d → %d"
            (or (:entropy-n (first metrics-history)) 0.0)
            (or (:entropy-n final-metrics) 0.0)
            (or (:unique-sigils (first metrics-history)) 0)
            (or (:unique-sigils final-metrics) 0))))

(defn generate-report [results output-path]
  (let [sb (StringBuilder.)
        timestamp (java.time.LocalDateTime/now)]

    (.append sb "# Elementary CA Rules as Wiring Diagrams\n\n")
    (.append sb (str "*Generated: " timestamp "*\n\n"))
    (.append sb "This essay visualizes elementary cellular automaton rules implemented as\n")
    (.append sb "wiring diagrams in the futon5 xenotype system. Each rule is executed through\n")
    (.append sb "the `futon5.wiring.runtime` interpreter, producing spacetime diagrams that\n")
    (.append sb "show the evolution of 8-bit sigil states.\n\n")
    (.append sb "---\n\n")

    ;; Overview table
    (.append sb "## Overview\n\n")
    (.append sb "| Rule | Wolfram Class | Formula | Description |\n")
    (.append sb "|------|---------------|---------|-------------|\n")
    (doseq [{:keys [id name class description]} rules]
      (let [formula (case id
                      :rule-090 "L ⊕ R"
                      :rule-110 "(C ∨ R) ∧ ¬(L ∧ C ∧ R)"
                      :rule-030 "L ⊕ (C ∨ R)"
                      :rule-184 "(C ∧ R) ∨ (L ∧ ¬C)"
                      :rule-054 "(¬L ∧ (C ⊕ R)) ∨ (L ∧ ¬C)"
                      "")]
        (.append sb (format "| %s | %d | `%s` | %s |\n" name class formula description))))
    (.append sb "\n")

    ;; Each rule section
    (doseq [{:keys [id name path class description] :as rule} rules
            :let [result (get results id)]]
      (.append sb (str "## " name "\n\n"))
      (.append sb (str "**Wolfram Class " class "**: " description "\n\n"))

      ;; Wiring diagram
      (.append sb "### Wiring Diagram\n\n")
      (.append sb (generate-mermaid id))
      (.append sb "\n\n")

      ;; Metrics
      (.append sb "### Run Statistics\n\n")
      (.append sb (format "- **Generations**: %d\n" (:generations result)))
      (.append sb (format "- **Width**: %d cells\n" (count (first (:gen-history result)))))
      (.append sb (format "- **Seed**: %d\n" (:seed result)))
      (.append sb (str "- **Dynamics**: " (format-metrics-summary (:metrics-history result)) "\n\n"))

      ;; Images
      (.append sb "### Spacetime Diagrams\n\n")
      (.append sb "| Color (8-bit) | Grayscale | Bitplane 0 |\n")
      (.append sb "|---------------|-----------|------------|\n")
      (let [id-str (clojure.core/name id)]
        (.append sb (format "| ![%s color](images/%s-wiring-color.png) | ![%s gray](images/%s-wiring-gray.png) | ![%s bit0](images/%s-wiring-bit0.png) |\n\n"
                            name id-str name id-str name id-str)))

      (.append sb "---\n\n"))

    ;; Methodology
    (.append sb "## Methodology\n\n")
    (.append sb "Each rule is implemented as a wiring diagram with these components:\n\n")
    (.append sb "- **Context extractors**: `:context-pred`, `:context-self`, `:context-succ`\n")
    (.append sb "- **Boolean operations**: `:bit-xor`, `:bit-and`, `:bit-or`, `:bit-not`\n")
    (.append sb "- **Output**: `:output-sigil`\n\n")
    (.append sb "The wiring interpreter (`futon5.xenotype.interpret/evaluate-diagram`) executes\n")
    (.append sb "the diagram in topological order for each cell at each generation.\n\n")
    (.append sb "**Note**: These are 8-bit sigil CAs, not binary CAs. Each sigil represents\n")
    (.append sb "256 possible states. The boolean operations work bitwise across all 8 bits,\n")
    (.append sb "producing richer dynamics than traditional binary CAs.\n\n")

    ;; Files
    (.append sb "## Files\n\n")
    (.append sb "- **Wiring definitions**: `data/wiring-rules/rule-*.edn`\n")
    (.append sb "- **Runtime**: `src/futon5/wiring/runtime.clj`\n")
    (.append sb "- **This script**: `scripts/wiring_photo_essay.clj`\n")
    (.append sb "- **Images**: `reports/images/*-wiring-*.png`\n")

    (spit output-path (str sb))
    (println "Report written to:" output-path)
    output-path))

;;; ============================================================
;;; Main
;;; ============================================================

(defn -main []
  (let [seed 352362012
        generations 100
        width 150
        cell-size 3
        output-dir "reports/images"

        _ (.mkdirs (io/file output-dir))
        _ (println "Generating wiring photo essay...")
        _ (println "Seed:" seed)
        _ (println "Generations:" generations)
        _ (println "Width:" width)
        _ (println)

        results
        (into {}
              (for [{:keys [id name path]} rules]
                (do
                  (println "Running" name "...")
                  (let [w (wiring/load-wiring path)
                        genotype (wiring/random-genotype width seed)
                        run (wiring/run-wiring {:wiring w
                                                :genotype genotype
                                                :generations generations})
                        history (:gen-history run)

                        ;; Render images
                        base (str output-dir "/" (clojure.core/name id) "-wiring")
                        _ (render-color history (str base "-color.png") cell-size)
                        _ (render-grayscale history (str base "-gray.png") cell-size)
                        _ (render-bitplane history 0 (str base "-bit0.png") cell-size)]
                    (println "  Generated images for" name)
                    [id (assoc run :seed seed)]))))]

    (println)
    (generate-report results "reports/rule-photo-essay.md")
    (println "\nDone!")))

(-main)
