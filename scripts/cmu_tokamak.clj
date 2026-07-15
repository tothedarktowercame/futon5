(require '[futon5.aif.retarget-demo :as d]
         '[futon5.aif.controller :as ctrl]
         '[futon5.aif.forward :as forward]
         '[futon5.mmca.particle-detection :as pd])

;; APPLY THE VALIDATED MEASURE: Cμ on :aif vs :null MetaCA runs.
;;
;; Cμ (causal-state count) just PASSED the ECA SeparatesEoC anchor where every
;; informational measure failed. Joe's question: "I'd be surprised if CSSR
;; scores are worse than null for the tokamak, though they may be."
;;
;; HONEST CAVEAT UP FRONT (EVALUATOR-SPEC §3.5): MetaCA cells are 256-valued.
;; A past light-cone of depth 2 spans 8 cells => 256^8 possible pasts. CSSR may
;; be SAMPLE-STARVED: if every past is unique, none clears min-support and NO
;; states resolve. That would itself be the finding the spec predicts
;; ("fullCell: rotation-aware but sample-starved") and points at the coarse
;; alphabet, not at the tokamak.

(defn run-arm [mode {:keys [seed windows generations length schedule]}]
  (loop [i 0, state (d/initial-state seed length), p nil]
    (if (>= i windows)
      (:gen-history state)
      (let [target (schedule i)
            opts {:seed (+ seed i) :generations generations :W generations}
            r (when (= mode :aif)
                (ctrl/choose-actions-aif state nil
                                         (assoc opts :target-c target :precision-state p)))
            action (if (= mode :null) :hold (first (:actions r)))
            fp (forward/forward-predict state action opts)]
        (recur (inc i) (:next-state fp) (:precision-state r))))))

(def cfg {:seed 1000 :windows 12 :generations 8 :length 64
          :schedule (fn [i] (if (< i 6) d/target-A d/target-B))})

(println "=== Cμ (validated on ECA anchor) applied to :aif vs :null MetaCA runs ===")
(println)

(doseq [[label mode] [[":aif (tokamak)" :aif] [":null (no control)" :null]]]
  (let [st (run-arm mode cfg)
        ;; coarse alphabet: map 256 sigils -> 2 bins, to dodge sample-starvation
        coarse (mapv (fn [row] (mapv #(if (< (int %) 20000) 0 1) (seq row))) st)
        full (pd/observe (mapv vec st) {:past-depth 2 :future-depth 1 :min-support 15})
        crs (pd/observe coarse {:past-depth 2 :future-depth 1 :min-support 15})]
    (println label)
    (println (format "   spacetime: %d gens x %d cells" (count st) (count (first st))))
    (println (format "   FULL alphabet (256 sigils): Cμ=%-4d labelled=%-6d distinct-pasts=%d"
                     (:n-states full) (:n-labelled full) (:distinct-pasts full)))
    (println (format "   COARSE alphabet (2 bins):   Cμ=%-4d labelled=%-6d distinct-pasts=%d  coverage=%.3f density=%.3f"
                     (:n-states crs) (:n-labelled crs) (:distinct-pasts crs)
                     (:domain-coverage crs) (:particle-density crs)))
    (println)))

(println "READ: if Cμ is ~equal for :aif and :null, the controller is not changing")
(println "      the CA's structural content -- which would CONFIRM the visual")
(println "      inspection (tokamak and null indistinguishable), now with a")
(println "      measure that is VALIDATED on the ECA anchor rather than a bulk average.")
