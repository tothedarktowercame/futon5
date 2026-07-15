(require '[futon5.ca.core :as ca]
         '[futon5.mmca.runtime :as runtime]
         '[futon5.mmca.exotype :as exotype]
         '[futon5.mmca.metrics :as metrics])

;; DOES THE MODE EXPLAIN THE DEAD ACTUATOR?
;;
;; exotype.clj:750-752 reads (:update-prob params) and gates cell updates on it.
;; But futon5.aif.forward hardcodes :exotype-mode :inline, which runtime.clj
;; itself marks "(deprecated) — old GLOBAL kernel steering", vs :local-physics
;; = "the correct implementation".
;;
;; The paired test showed update-prob has EXACTLY zero effect under :inline.
;; Hypothesis: :inline never reads the exotype params, so the tokamak's actions
;; were written to a field the run does not consume — the same class of bug as
;; the original cyberant (wiring written to :cyber-pattern :config, never read).
;;
;; Test: paired (same seed, knob varied) under BOTH modes.

(def geno (ca/with-seed 99 (ca/random-sigil-string 48)))

(defn pressure [seed mode up sigil]
  (ca/with-seed seed
    (let [exo (-> (exotype/resolve-exotype {:sigil sigil :tier :super})
                  (assoc-in [:params :update-prob] up)
                  (assoc-in [:params :match-threshold] 0.5))
          r (runtime/run-mmca {:genotype geno :phenotype nil :generations 10
                               :kernel :mutating-template :lock-kernel false
                               :exotype exo :exotype-mode mode :seed seed})
          w (last (metrics/windowed-macro-features
                   {:metrics-history (:metrics-history r) :gen-history (:gen-history r)}
                   {:W 10 :S 10}))]
      (double (or (:pressure w) 0.5)))))

(doseq [sig ["一" "工" "乐"]
        mode [:inline :local-physics]]
  (println (format "=== sigil %s (bits %s)  mode %s ===" sig (ca/bits-for sig) mode))
  (let [ds (for [s (range 4000 4010)]
             (let [lo (pressure s mode 0.05 sig)
                   hi (pressure s mode 1.0 sig)]
               (- hi lo)))
        ds (vec ds)
        nonzero (count (remove #(< (Math/abs %) 1e-9) ds))
        m (/ (reduce + ds) (double (count ds)))]
    (println (format "   paired d per seed: %s" (pr-str (mapv #(Double/parseDouble (format "%.3f" %)) ds))))
    (println (format "   pairs where the knob changed ANYTHING: %d/%d   mean d = %+.4f"
                     nonzero (count ds) m))
    (println (if (zero? nonzero)
               "   -> ACTUATOR DEAD in this mode (knob writes a field the run never reads)"
               "   -> ACTUATOR LIVE in this mode (the knob genuinely moves the CA)"))
    (println)))

(println "If :local-physics is LIVE and :inline is DEAD, the tokamak's actuator was")
(println "disconnected by a MODE MISMATCH — forward.clj:144 hardcodes the deprecated")
(println "mode. That is the same bug class as the original cyberant, and it is a")
(println "one-line fix rather than a dead premise.")
