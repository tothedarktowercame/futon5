(require '[futon5.mmca.particle-detection :as pd])

;; Cμ (STATISTICAL COMPLEXITY) TRUST ANCHOR — multi-seed robustness.
;;
;; The domain/particle conjunction FAILED the anchor (chaotic rule-90 outscored
;; rule-110) because "domain = constant causal state" is the wrong test: a
;; domain is a REGULAR background, not a uniform one.
;;
;; But the same reconstruction exposes the causal-state COUNT = statistical
;; complexity Cμ, the classic computational-mechanics structure measure.
;; Crutchfield: entropy-rate is maximised by CHAOS, Cμ peaks at the EDGE.
;; Cμ is structural, not informational -- the fail-bank's measures were all
;; informational. Is the separation robust across seeds, or one lucky draw?

(defn eca-step [rule row]
  (let [n (count row)]
    (vec (for [i (range n)]
           (let [l (nth row (mod (dec i) n))
                 c (nth row i)
                 r (nth row (mod (inc i) n))]
             (if (bit-test rule (+ (* 4 l) (* 2 c) r)) 1 0))))))

(defn eca-spacetime [rule width gens seed]
  (let [rng (java.util.Random. (long seed))
        row0 (vec (repeatedly width #(if (.nextBoolean rng) 1 0)))]
    (vec (take gens (iterate #(eca-step rule %) row0)))))

(def rules [[110 "class 4 COMPLEX"] [54 "class 4 complex"]
            [30 "class 3 chaotic"] [90 "class 3 chaotic"]
            [250 "class 2 frozen"] [0 "class 1 frozen"]])
(def seeds [11 23 37 41 59])

(defn- mean [xs] (/ (double (reduce + xs)) (count xs)))

(println "=== Cμ (causal-state count) ANCHOR — 5 seeds/rule ===")
(println (format "%-6s %-18s | %-26s %-7s %-7s" "rule" "class" "Cμ per seed" "mean" "min"))

(def rows
  (doall
   (for [[rule label] rules]
     (let [cs (vec (for [s seeds]
                     (:n-states (pd/observe (eca-spacetime rule 80 90 s)
                                            {:past-depth 2 :future-depth 1
                                             :min-support 15}))))]
       (println (format "%-6d %-18s | %-26s %-7.1f %-7d"
                        rule label (pr-str cs) (mean cs) (apply min cs)))
       {:rule rule :label label :cs cs :mean (mean cs) :min (apply min cs)}))))

(println)
(let [by (into {} (map (juxt :rule identity) rows))
      complex-min (apply min (map #(:min (by %)) [110 54]))
      other-max (apply max (map #(:mean (by %)) [30 90 250 0]))
      sep? (> complex-min other-max)]
  (println (format "worst-case class-4 Cμ = %d   |   best-case non-class-4 mean Cμ = %.1f"
                   complex-min other-max))
  (println)
  (println "Rule-110 (the bar the shortcut measures all MISSED):"
           (format "mean Cμ %.1f" (:mean (by 110))))
  (println "  vs chaotic rule-30 mean Cμ:" (format "%.1f" (:mean (by 30))))
  (println "  vs chaotic rule-90 mean Cμ:" (format "%.1f" (:mean (by 90))))
  (println)
  (println "SeparatesEoC on Cμ HOLDS?" sep?)
  (when sep?
    (println "  => Cμ ranks class-4 above chaotic AND frozen on every seed.")
    (println "     A candidate VALID EoC discriminator -- structural, not informational."))
  (when-not sep?
    (println "  => FAILS. Bank it with the others; do not use as an EoC label.")))
