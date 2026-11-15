;; Step 6a discovery REPL: item 2 (kind -> vector twins) and item 4 (bug
;; Gamma-conjugates). Run from the p008 worktree; read-only.
(require '[futon5.exotype.grid :as grid]
         '[futon5.xenotype.generator :as gen]
         '[futon5.ca.core :as ca])

(println "=== item 2: the 14 propagators and their vector twins ===")
(doseq [k [:builder :collapser :chaos :identity
           :even4 :even8 :even1 :odd53 :fix2 :fix3 :fix4 :fix6
           :even44 :odd332]]
  (let [sigma (get grid/propagators k)
        v (grid/kind->s8 k)
        twin? (= (grid/sigma-of v) sigma)
        perm? (grid/perm? v)]
    (println (name k) "twin" v "exact-twin" twin? "perm" perm?
             "cycle-type" (when perm? (grid/cycle-type v)))))

(println)
(println "=== item 4: the bug and its Gamma conjugates ===")
(def bug-pos [0 0 1 2 3 4 5 6])
(def elisp-tbl ["000" "001" "010" "100" "011" "101" "110" "111"])
(def bug-sigma (gen/positional-sigma->neighbourhood-sigma bug-pos elisp-tbl))
(def bug-v (gen/sigma-positional bug-sigma))
(println "bug vector (sigma-positional of elisp shim):" bug-v
         "perm?" (grid/perm? bug-v))

;; Gamma = <mirror, complement>: permutations of the 8 neighbourhoods
;; (DESIGN-P008b 4, "Gamma caveat"; c and m are themselves permutations).
;; complement flips every bit; mirror reverses bit order (011 <-> 110).
(defn nbhd-complement [s] (apply str (map #(if (= % \1) \0 \1) s)))
(defn nbhd-mirror [s] (apply str (reverse s)))
(def tt (vec ca/truth-table-3))
(defn perm-of [g] (mapv #(.indexOf ^java.util.List tt (g (nth tt %))) (range 8)))
(def c (perm-of nbhd-complement))
(def m (perm-of nbhd-mirror))
(println "complement permutation:" c)
(println "mirror permutation:" m)
(defn conj-map [f g] ;; f' = g o f o g^-1 on vectors
  (let [ginv (into {} (map-indexed (fn [i j] [j i]) g))]
    (mapv (fn [k] (nth g (nth f (get ginv k)))) (range 8))))
(def gs {:id (vec (range 8)) :c c :m m :cm (mapv c m)})
(doseq [[n g] gs]
  (let [f (conj-map bug-v g)]
    (println (name n) f "distinct-from-bug" (not= f bug-v))))
(println "distinct conjugate vectors:"
         (count (distinct (map (fn [[_ g]] (conj-map bug-v g)) gs))))
(println "fg-class bug:" (grid/fg-class bug-v))
(doseq [[n g] gs] (println "fg-class" (name n) (grid/fg-class (conj-map bug-v g))))
(shutdown-agents)
