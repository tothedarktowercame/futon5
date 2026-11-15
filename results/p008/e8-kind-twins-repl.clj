;; Step 6b twin predicate: (= (sigma-of kind) (s8->sigma (kind->s8 kind)))
;; per the ruling of 2026-11-15 (exotype main 5176228). s8->sigma is
;; private, so the script reaches it through #' ; the public-path check
;; (sigma-of on the twin vector, which routes permutations through
;; s8->sigma, grid.clj:171-177) is also computed and must agree.
(require '[futon5.exotype.grid :as grid])

(def s8->sigma #'futon5.exotype.grid/s8->sigma)

(defn sigma-of* [x] (grid/sigma-of x))

(def kinds [:builder :collapser :chaos :identity
            :even4 :even8 :even1 :odd53 :fix2 :fix3 :fix4 :fix6
            :even44 :odd332])

(println "kind\thas-sigma\tkind->s8\truling-predicate\tpublic-path\tsame")
(doseq [k kinds]
  (let [has (some? (get grid/propagators k))
        v (grid/kind->s8 k)
        p (and has (= (get grid/propagators k) (s8->sigma v)))
        q (and has (= (sigma-of* k) (sigma-of* v)))]
    (println (name k) "\t" has "\t" (pr-str v) "\t" p "\t" q "\t" (= p q))))
(shutdown-agents)
