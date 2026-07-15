(require '[futon5.aif.retarget-demo :as d]
         '[futon5.aif.forward :as forward])

;; CONTROL-AUTHORITY PROBE
;; Does varying the two control knobs (update-prob, match-threshold) actually
;; move the macro-features? If the reachable range is tiny, no controller can
;; confine — claim (A) would fail for a real reason (insufficient actuator
;; authority), not because the demo picked bad targets.

(println "=== CONTROL-AUTHORITY PROBE ===")
(println "does varying the knobs move the macro-features?")
(println (format "%-9s %-9s | %-8s %-8s %-8s %-8s %s"
                 "upd-prob" "match-th" "press" "select" "struct" "activ" "regime"))

(def results
  (doall
   (for [up [0.05 0.5 1.0]
         mt [0.0 0.5 1.0]]
     (let [st0 (d/initial-state 1000 32)
           st (assoc-in st0 [:exotype :params]
                        (assoc (get-in st0 [:exotype :params])
                               :update-prob up :match-threshold mt))
           fp (forward/forward-predict st :hold {:seed 1000 :generations 8 :W 8})
           m (:mean fp)]
       (println (format "%-9.2f %-9.2f | %-8.3f %-8.3f %-8.3f %-8.3f %s"
                        (double up) (double mt)
                        (double (or (:pressure m) -1.0))
                        (double (or (:selectivity m) -1.0))
                        (double (or (:structure m) -1.0))
                        (double (or (:activity m) -1.0))
                        (str (:regime m))))
       m))))

(println)
(doseq [ch [:pressure :selectivity :structure :activity]]
  (let [vs (keep #(get % ch) results)
        lo (when (seq vs) (apply min vs))
        hi (when (seq vs) (apply max vs))]
    (println (format "  %-12s reachable range: %.3f .. %.3f   (span %.3f)"
                     (name ch) (double (or lo 0)) (double (or hi 0))
                     (double (- (or hi 0) (or lo 0)))))))
(println)
(println "If spans are ~0, the knobs have no authority over the macro-features")
(println "and NO controller can confine -> claim (A) fails for a real reason.")
