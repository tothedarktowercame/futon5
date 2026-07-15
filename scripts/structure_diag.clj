(require '[futon5.aif.retarget-demo :as d]
         '[futon5.mmca.runtime :as runtime]
         '[futon5.mmca.metrics :as metrics])

;; Why is :structure nil? Hypothesis: windowed-macro-features prefers the
;; metrics path, but run-mmca's metrics entries carry no :temporal-autocorr,
;; so summary's :temporal-autocorr is nil -> structure nil. The gen-history
;; path (which DOES compute temporal-autocorr) is never reached.

(let [st (d/initial-state 1000 32)
      r (runtime/run-mmca {:genotype (:genotype st)
                           :phenotype (:phenotype st)
                           :generations 8
                           :kernel (:kernel st)
                           :lock-kernel false
                           :exotype (:exotype st)
                           :exotype-mode :inline
                           :seed 1000})
      mh (:metrics-history r)
      gh (:gen-history r)]
  (println "metrics-history entries:" (count mh))
  (println "gen-history entries:" (count gh))
  (println)
  (println "KEYS of a metrics entry:")
  (println "  " (sort (keys (first mh))))
  (println)
  (println "does any metrics entry carry :temporal-autocorr?"
           (boolean (some :temporal-autocorr mh)))
  (println)
  ;; What does windowed-macro-features produce from each path?
  (let [w-both (last (metrics/windowed-macro-features
                      {:metrics-history mh :gen-history gh} {:W 8 :S 8}))
        w-gen-only (last (metrics/windowed-macro-features
                          {:gen-history gh} {:W 8 :S 8}))]
    (println "via METRICS path (what forward-predict uses):")
    (println "   structure =" (:structure w-both)
             " pressure =" (:pressure w-both)
             " activity =" (:activity w-both))
    (println "via GEN-HISTORY-only path:")
    (println "   structure =" (:structure w-gen-only)
             " pressure =" (:pressure w-gen-only)
             " activity =" (:activity w-gen-only))
    (println)
    (println "=> if gen-only gives a NON-nil structure, the fix is to compute")
    (println "   temporal-autocorr from gen-history when metrics lack it.")))
