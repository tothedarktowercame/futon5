(require '[futon5.ca.core :as ca]
         '[futon5.mmca.runtime :as runtime])

;; Behaviour-preservation + reproducibility check for the ca/core seeding seam.
;; The bar: seeded => identical trajectory; unseeded => still stochastic (i.e.
;; every existing caller behaves exactly as it did before).

(def geno (ca/with-seed 99 (ca/random-sigil-string 48)))

(defn run [seed]
  (runtime/run-mmca {:genotype geno
                     :phenotype nil
                     :generations 25
                     :kernel :mutating-template
                     :lock-kernel false
                     :exotype nil
                     :exotype-mode :inline
                     :seed seed}))

(println "=== 1. SEEDED: same seed twice -> identical trajectory? ===")
(let [a (ca/with-seed 4242 (:gen-history (run 4242)))
      b (ca/with-seed 4242 (:gen-history (run 4242)))]
  (println "   rows:" (count a) " IDENTICAL?" (= a b)))

(println "=== 2. SEEDED: different seed -> different trajectory? (not a constant fn) ===")
(let [a (ca/with-seed 4242 (:gen-history (run 4242)))
      c (ca/with-seed 1234 (:gen-history (run 4242)))]
  (println "   differs under seed 1234?" (not= a c)))

(println "=== 3. UNSEEDED: still stochastic? (historical behaviour preserved) ===")
(let [a (:gen-history (run 7))
      b (:gen-history (run 7))]
  (println "   two unseeded runs differ?" (not= a b)
           " (expected true: global rand, exactly as before)"))

(println "=== 4. helper unit checks ===")
(println "   random-sigil-string reproducible under seed?"
         (= (ca/with-seed 5 (ca/random-sigil-string 30))
            (ca/with-seed 5 (ca/random-sigil-string 30))))
(println "   mutate-rule-n reproducible under seed?"
         (= (ca/with-seed 5 (ca/mutate-rule-n "00000000" 3))
            (ca/with-seed 5 (ca/mutate-rule-n "00000000" 3))))
(println "   mutate-rule-n still varies unseeded?"
         (not= (repeatedly 6 #(ca/mutate-rule-n "00000000" 3))
               (repeat 6 (ca/mutate-rule-n "00000000" 3))))
