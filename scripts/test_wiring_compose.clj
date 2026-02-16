#!/usr/bin/env clj -M
;; Test wiring composition and breeding
;;
;; Usage: clj -M -e '(load-file "scripts/test_wiring_compose.clj")'

(ns test-wiring-compose
  (:require [clojure.pprint :refer [pprint]]
            [futon5.wiring.compose :as compose]
            [futon5.wiring.embedding :as embed]
            [futon5.wiring.runtime :as runtime]))

(defn -main []
  (println "=== Wiring Composition Tests ===\n")

  (let [rules (compose/load-ca-rules)
        l5 (compose/load-wiring "data/wiring-ladder/level-5-creative.edn")

        ;; Test 1: Boost L5-creative with Rule 90 (XOR mode)
        _ (println "--- Test 1: L5-creative ⊕ Rule-90 (XOR boost) ---")
        boosted (compose/boost l5 (:rule-090 rules) :xor)
        _ (println "Created:" (get-in boosted [:meta :id]))
        _ (println "Nodes:" (count (get-in boosted [:diagram :nodes])))
        _ (println "Edges:" (count (get-in boosted [:diagram :edges])))

        ;; Check embedding
        _ (println "\nPath signature:")
        sig-report (embed/signature-report boosted)
        _ (println "  Paths:" (:path-count sig-report))
        _ (println "  Max depth:" (:max-depth sig-report))
        _ (println "  Inputs:" (:input-components sig-report))
        _ (println)

        ;; Compare to landmarks
        landmarks (embed/load-landmarks)
        _ (println "Landmark similarities:")
        coords (embed/landmark-coordinates boosted landmarks)
        _ (doseq [[lm sim] (sort-by val > coords)]
            (println (format "  %s: %.2f" (name lm) sim)))
        _ (println)

        ;; Test 2: Serial composition Rule-90 → Rule-110
        _ (println "--- Test 2: Rule-90 → Rule-110 (serial) ---")
        serial (compose/compose-serial (:rule-090 rules) (:rule-110 rules))
        _ (println "Created:" (get-in serial [:meta :id]))
        _ (println "Nodes:" (count (get-in serial [:diagram :nodes])))
        serial-sig (embed/signature-report serial)
        _ (println "Paths:" (:path-count serial-sig))
        _ (println "Max depth:" (:max-depth serial-sig))
        _ (println)

        ;; Test 3: Crossover between Rule-030 and Rule-184
        _ (println "--- Test 3: Crossover Rule-030 × Rule-184 ---")
        children (compose/crossover (:rule-030 rules) (:rule-184 rules))
        _ (if children
            (do
              (println "Child A:" (get-in (first children) [:meta :id]))
              (println "Child B:" (get-in (second children) [:meta :id]))
              (let [child-a-sig (embed/signature-report (first children))]
                (println "Child A paths:" (:path-count child-a-sig))))
            (println "No compatible crossover point found"))
        _ (println)

        ;; Test 4: Run the boosted wiring
        _ (println "--- Test 4: Running boosted wiring ---")
        genotype (runtime/random-genotype 50 42)
        _ (println "Running L5-creative ⊕ Rule-90...")
        run-result (try
                     (runtime/run-wiring {:wiring boosted
                                          :genotype genotype
                                          :generations 20
                                          :collect-metrics? true})
                     (catch Exception e
                       (println "Error running boosted wiring:" (.getMessage e))
                       nil))]

    (when run-result
      (println "Generations:" (:generations run-result))
      (let [final-metrics (last (:metrics-history run-result))]
        (println "Final entropy:" (format "%.3f" (or (:entropy-n final-metrics) 0.0)))
        (println "Final unique sigils:" (:unique-sigils final-metrics))))

    (println "\n=== Done ==="))

  nil)

(-main)
