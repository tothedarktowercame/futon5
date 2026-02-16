#!/usr/bin/env clj -M
;; Test wiring embedding via path signatures
;;
;; Usage: clj -M -e '(load-file "scripts/test_wiring_embedding.clj")'

(ns test-wiring-embedding
  (:require [clojure.edn :as edn]
            [clojure.pprint :refer [pprint print-table]]
            [futon5.wiring.embedding :as embed]))

(defn load-wiring [path]
  (edn/read-string (slurp path)))

(defn -main []
  (println "=== Wiring Path Signature Analysis ===\n")

  ;; Load all rule wirings
  (let [wirings {:rule-090 (load-wiring "data/wiring-rules/rule-090.edn")
                 :rule-110 (load-wiring "data/wiring-rules/rule-110.edn")
                 :rule-030 (load-wiring "data/wiring-rules/rule-030.edn")
                 :rule-184 (load-wiring "data/wiring-rules/rule-184.edn")
                 :rule-054 (load-wiring "data/wiring-rules/rule-054.edn")}

        ;; Try to load L5-creative if it exists
        l5-path "data/wiring-ladder/level-5-creative.edn"
        wirings (if (.exists (java.io.File. l5-path))
                  (assoc wirings :l5-creative (load-wiring l5-path))
                  wirings)]

    ;; Print signature reports for each
    (println "--- Path Signatures ---\n")
    (doseq [[id wiring] (sort-by key wirings)]
      (println (str "### " (name id)))
      (let [report (embed/signature-report wiring)]
        (println "  Paths:" (:path-count report))
        (println "  Max depth:" (:max-depth report))
        (println "  Inputs:" (:input-components report))
        (println "  Signatures:")
        (doseq [sig (:signatures report)]
          (println "    " sig))
        (println)))

    ;; Distance matrix
    (println "--- Jaccard Distance Matrix ---\n")
    (let [ids (sort (keys wirings))
          header (concat [""] (map name ids))
          rows (for [id-a ids]
                 (concat [(name id-a)]
                         (for [id-b ids]
                           (format "%.2f"
                                   (embed/signature-distance
                                    (get wirings id-a)
                                    (get wirings id-b))))))]
      (println (apply str (interpose " | " header)))
      (println (apply str (repeat (+ 10 (* 8 (count ids))) "-")))
      (doseq [row rows]
        (println (apply str (interpose " | " row)))))
    (println)

    ;; Similarity matrix (might be more intuitive)
    (println "--- Jaccard Similarity Matrix ---\n")
    (let [ids (sort (keys wirings))
          header (concat [""] (map name ids))
          rows (for [id-a ids]
                 (concat [(name id-a)]
                         (for [id-b ids]
                           (format "%.2f"
                                   (embed/signature-similarity
                                    (get wirings id-a)
                                    (get wirings id-b))))))]
      (println (apply str (interpose " | " header)))
      (println (apply str (repeat (+ 10 (* 8 (count ids))) "-")))
      (doseq [row rows]
        (println (apply str (interpose " | " row)))))
    (println)

    ;; Landmark coordinates for each wiring
    (println "--- Landmark Coordinates (similarity to each rule) ---\n")
    (let [landmarks (select-keys wirings [:rule-090 :rule-110 :rule-030 :rule-184 :rule-054])]
      (doseq [[id wiring] (sort-by key wirings)]
        (let [coords (embed/landmark-coordinates wiring landmarks)]
          (println (str (name id) ":"))
          (doseq [[landmark sim] (sort-by key coords)]
            (println (format "  %s: %.2f" (name landmark) sim)))
          (println))))

    (println "=== Done ==="))

  nil)

(-main)
