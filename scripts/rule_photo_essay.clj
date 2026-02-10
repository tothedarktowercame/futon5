#!/usr/bin/env clj -M
;; Generate a visual photo essay of CA rules
;;
;; Usage: clj -M -e '(load-file "scripts/rule_photo_essay.clj")'

(ns rule-photo-essay
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [futon5.mmca.runtime :as mmca]
            [futon5.ca.core :as ca]))

(defn random-genotype [len seed]
  (let [rng (java.util.Random. seed)
        sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly len #(nth sigils (.nextInt rng (count sigils)))))))

(defn sigil->brightness
  "Map sigil to brightness value 0-255 based on bit count."
  [sigil]
  (let [bits (ca/bits-for (str sigil))
        ones (count (filter #(= % \1) bits))]
    ;; 0 ones = dark, 8 ones = bright
    (int (* 255 (/ ones 8.0)))))

(defn brightness->block
  "Map brightness to Unicode block character."
  [b]
  (cond
    (< b 32)  " "   ; empty
    (< b 64)  "░"   ; light
    (< b 128) "▒"   ; medium
    (< b 192) "▓"   ; dark
    :else     "█")) ; full

(defn render-row-ascii [row]
  (apply str (map #(brightness->block (sigil->brightness %)) (seq row))))

(defn render-spacetime-ascii
  "Render spacetime diagram as ASCII art."
  [history max-rows max-cols]
  (let [rows (take max-rows history)
        render-row (fn [row]
                     (let [chars (take max-cols (seq row))]
                       (apply str (map #(brightness->block (sigil->brightness %)) chars))))]
    (str/join "\n" (map render-row rows))))

(defn wiring->mermaid
  "Convert wiring diagram to Mermaid flowchart."
  [wiring]
  (let [meta (:meta wiring)
        diagram (:diagram wiring)
        nodes (:nodes diagram)
        edges (:edges diagram)

        node-lines (for [{:keys [id component]} nodes]
                     (let [label (name (or component id))
                           shape (cond
                                   (str/includes? label "context") (str id "[" label "]")
                                   (str/includes? label "output") (str id "([" label "])")
                                   (str/includes? label "bit-") (str id "{" label "}")
                                   :else (str id "(" label ")"))]
                       (str "    " shape)))

        edge-lines (for [{:keys [from to to-port]} edges]
                     (if to-port
                       (str "    " from " -->|" (name to-port) "| " to)
                       (str "    " from " --> " to)))]

    (str "```mermaid\nflowchart TD\n"
         (str/join "\n" node-lines)
         "\n"
         (str/join "\n" edge-lines)
         "\n```")))

(defn run-and-capture [wiring-path seed generations width]
  (let [wiring (edn/read-string (slurp wiring-path))
        genotype (random-genotype width seed)
        phenotype (apply str (repeat width "0"))
        run (mmca/run-mmca {:genotype genotype
                            :phenotype phenotype
                            :generations generations
                            :seed seed
                            :wiring wiring})]
    {:wiring wiring
     :history (:gen-history run)
     :phe-history (:phe-history run)}))

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
        generations 60
        width 80

        sb (StringBuilder.)]

    (.append sb "# Elementary CA Rules as MMCA Wirings\n\n")
    (.append sb "A visual exploration of classic cellular automaton rules implemented as wiring diagrams.\n\n")
    (.append sb (str "**Seed**: " seed " | **Generations**: " generations " | **Width**: " width "\n\n"))
    (.append sb "---\n\n")

    (doseq [{:keys [path name class desc]} rules]
      (println "Processing" name "...")
      (let [{:keys [wiring history]} (run-and-capture path seed generations width)
            formula (get-in wiring [:meta :formula])
            spacetime (render-spacetime-ascii history generations width)
            mermaid (wiring->mermaid wiring)]

        (.append sb (str "## " name " (" class ")\n\n"))
        (.append sb (str "**" desc "**\n\n"))
        (.append sb (str "Formula: `" formula "`\n\n"))

        (.append sb "### Wiring Diagram\n\n")
        (.append sb mermaid)
        (.append sb "\n\n")

        (.append sb "### Spacetime Diagram\n\n")
        (.append sb "```\n")
        (.append sb spacetime)
        (.append sb "\n```\n\n")

        (.append sb "---\n\n")))

    ;; Add comparison section
    (.append sb "## Comparison Notes\n\n")
    (.append sb "| Rule | Wolfram Class | Key Property | Visual Character |\n")
    (.append sb "|------|---------------|--------------|------------------|\n")
    (.append sb "| 90 | 3 | Fractal (Sierpinski) | Triangular clearings |\n")
    (.append sb "| 110 | 4 | Turing-complete | Localized structures |\n")
    (.append sb "| 30 | 3 | High entropy | Chaotic, random-looking |\n")
    (.append sb "| 184 | 2 | Particle conservation | Diagonal stripes |\n")
    (.append sb "| 54 | 4 | Complex periodic | Mixed patterns |\n\n")

    (.append sb "### Hexagram Connections\n\n")
    (.append sb "- **Rule 90** (XOR) produces alternating patterns that map to hexagrams 63/64 (既濟/未濟) in bitplane analysis\n")
    (.append sb "- **Rule 184** (traffic) shows highest affinity for hexagram 11 (泰) in exotype sampling\n")
    (.append sb "- The 8-bit sigil space creates richer dynamics than binary CAs\n\n")

    (let [output-path "/home/joe/code/futon5/reports/rule-photo-essay.md"]
      (spit output-path (str sb))
      (println "\nPhoto essay written to:" output-path)
      output-path)))

(generate-essay)
