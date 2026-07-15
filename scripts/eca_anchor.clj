(require '[futon5.mmca.particle-detection :as pd])

;; SeparatesEoC TRUST ANCHOR (EVALUATOR-SPEC §3.8/§3.9)
;;
;; "INSTANTIATE ECA-first (validate the signature on known EoC, including
;;  Rule 110), then MetaCA domain-discovery as its own slice."
;;
;; The bar: the conjunction must rank the CLASS-4 (complex) rules ABOVE both
;; chaotic and frozen. Rule 110 is the specific bar the shortcut measures
;; (nn-TE, distance-TE, coherence) all MISSED.

(defn eca-step [rule row]
  (let [n (count row)]
    (vec (for [i (range n)]
           (let [l (nth row (mod (dec i) n))
                 c (nth row i)
                 r (nth row (mod (inc i) n))
                 idx (+ (* 4 l) (* 2 c) r)]
             (if (bit-test rule idx) 1 0))))))

(defn eca-spacetime [rule width gens seed]
  (let [rng (java.util.Random. (long seed))
        row0 (vec (repeatedly width #(if (.nextBoolean rng) 1 0)))]
    (vec (take gens (iterate #(eca-step rule %) row0)))))

(def rules
  ;; rule, Wolfram class, expected verdict
  [[110 "class 4 COMPLEX" :fires]
   [54  "class 4 complex" :fires]
   [30  "class 3 chaotic" :fails-domain]
   [90  "class 3 chaotic" :fails-domain]
   [250 "class 2 frozen-ish" :fails-particle]
   [0   "class 1 frozen" :fails-particle]])

(println "=== SeparatesEoC TRUST ANCHOR — ECA (EVALUATOR-SPEC §3.8/§3.9) ===")
(println "conjunction: eoc-score = domain-coverage * particle-density")
(println "bar: class-4 (110, 54) must rank ABOVE chaotic (30, 90) and frozen (250, 0)")
(println)
(println (format "%-6s %-20s | %-9s %-9s %-10s %-7s" "rule" "class" "coverage" "density" "eoc-score" "states"))

(def results
  (doall
   (for [[rule label _] rules]
     (let [st (eca-spacetime rule 80 90 42)
           r (pd/observe st {:past-depth 2 :future-depth 1 :min-support 15})]
       (println (format "%-6d %-20s | %-9.3f %-9.3f %-10.4f %-7d"
                        rule label
                        (:domain-coverage r) (:particle-density r)
                        (:eoc-score r) (:n-states r)))
       {:rule rule :label label :score (:eoc-score r)
        :coverage (:domain-coverage r) :density (:particle-density r)}))))

(println)
(let [by-rule (into {} (map (juxt :rule identity) results))
      complex-scores (map #(:score (by-rule %)) [110 54])
      other-scores (map #(:score (by-rule %)) [30 90 250 0])
      min-complex (apply min complex-scores)
      max-other (apply max other-scores)
      separates? (> min-complex max-other)]
  (println (format "min(class-4) = %.4f   max(chaotic/frozen) = %.4f" min-complex max-other))
  (println)
  (println "RULE-110 SPECIFICALLY:" (format "%.4f" (:score (by-rule 110)))
           " vs chaotic rule-30:" (format "%.4f" (:score (by-rule 30))))
  (println "  rule-110 beats rule-30?" (> (:score (by-rule 110)) (:score (by-rule 30))))
  (println)
  (println "SeparatesEoC HOLDS?" separates?)
  (if separates?
    (println "  => the conjunction is a VALID EoC discriminator on the ECA anchor.")
    (println "  => FAILS the anchor. Do NOT use it as an EoC label (this is the")
    (println "     honest outcome the spec's fail-bank prepares for).")))
