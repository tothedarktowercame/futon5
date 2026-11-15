;; Step 6c: enumerate End(8) strata and the sqrt-allocation table.
;; SHEET reference, discovery only. All vectors in the futon5 index
;; convention (Wolfram order): index(f) = sum_k f(k)*8^(7-k).
;; Run from the p008 worktree:
;;   nice -n 10 clojure -J-XX:ActiveProcessorCount=4 -M results/p008/e8-strata-enumerate.clj
;; Writes results/p008/e8-strata-allocation.txt.
(require '[clojure.string :as str]
         '[futon5.exotype.e8-strata-test :as st])

(def t0 (System/nanoTime))
(def stats (st/strata-stats))
(def dt (double (/ (- (System/nanoTime) t0) 1e9)))
(println "enumeration seconds:" (format "%.1f" dt))

(def strata (into {} (:strata stats)))
(def n-strata (count strata))
;; Section 4 says "minimum 2 ... aiming for about 150", but the
;; enumeration has 134 strata: the minimum ALONE allocates 2*134 = 268
;; > 150, so no alpha reaches 150 from below. The bisected alpha for
;; target 150 is therefore degenerate (~0): every stratum at the
;; minimum. We report that allocation (the one the rule as written
;; produces) plus a non-degenerate sqrt allocation at target 300.
(def alpha-150 (st/alpha-for-target strata 150))
(def alpha-300 (st/alpha-for-target strata 300))
(def alloc-round (st/allocate strata alpha-150))
(def alloc-floor (st/allocate strata alpha-150 :round :floor))
(def alloc-300 (st/allocate strata alpha-300))
(def bug [1 2 4 5 3 6 7 7])
(def bug-key (st/stratum-key bug))

(defn row [[[rank part] {:keys [size classes]}]]
  (str "| " rank " | " (pr-str part) " | " size " | " classes " | "
       (get (:counts alloc-round) [rank part]) " | "
       (get (:counts alloc-300) [rank part]) " |"
       (if (= bug-key [rank part]) " <-- bug's stratum" "")))

(def r8-strata (filter (fn [[[r _]]] (= 8 r)) (:strata stats)))
(def r8-size-sum (reduce + (map (comp :size val) r8-strata)))
(def checks-line
  (apply str
    "Checks: sizes sum " (:total-maps stats) " (= 8^8 16777216: "
    (= 16777216 (:total-maps stats)) "); classes sum "
    (:total-classes stats) " (= 951: " (= 951 (:total-classes stats))
    "); rank-8 strata " (:perm-strata stats) " = 22, their sizes sum "
    r8-size-sum " = 40320: " (= 40320 r8-size-sum)
    "); classes by rank " (pr-str (:by-rank-classes stats))
    " (matches 7da77cc [1 14 75 221 314 229 75 22]: "
    (= [1 14 75 221 314 229 75 22] (:by-rank-classes stats))
    "); sizes by rank " (pr-str (:by-rank-sizes stats))
    " (matches section 2 Burnside [8 7112 324576 2857680 7056000 5362560"
    " 1128960 40320]: "
    (= [8 7112 324576 2857680 7056000 5362560 1128960 40320]
       (:by-rank-sizes stats)) ")."))

(def alloc-line
  (apply str
    "Allocation rule (section 4 as written): n_s = max(2, round-half-up(alpha * sqrt(size_s))).\n"
    "CONTRADICTION with section 4: the minimum 2 alone allocates 2 x "
    n-strata " = " (* 2 n-strata) " maps, which already EXCEEDS the\n"
    "'aiming for about 150' target. No alpha makes the total 150; the smallest\n"
    "alpha with total >= 150 is alpha ~ 0 (bisected to 1e-9: "
    (format "%.9f" alpha-150) "), at which every stratum sits AT the\n"
    "minimum and the total is " (:total alloc-round)
    " (rounding is then irrelevant: the floor alternative gives the same "
    (:total alloc-floor) ").\n"
    "For comparison, a non-degenerate sqrt allocation at target 300 uses\n"
    "alpha = " (format "%.9f" alpha-300) ", total " (:total alloc-300)
    " (round-half-up)."))

(def bug-line
  (apply str
    "The bug " (pr-str bug) " has stratum key " (pr-str bug-key)
    " (rank = image size 7, recurrent cycle-part [1]); its allocation count is "
    (get (:counts alloc-round) bug-key)
    " (>= 2 by the minimum; the stratum has size "
    (:size (get strata bug-key)) " and " (:classes (get strata bug-key))
    " class). NOTE the bug's rank is 7, not 1: section 1's 'rank-7 case'\n"
    "remark uses rank = image size, and so does this table."))

(def out
  (str/join "\n"
    (concat
      ["# End(8) strata enumeration and sqrt-allocation (P-008b §4, §7 item 6)"
       ""
       "SHEET reference, discovery only. Convention: all vectors in the"
       "futon5 index order (Wolfram order); index(f) = sum_k f(k)*8^(7-k)."
       "Stratum = (rank, cycle-part of f restricted to its recurrent set),"
       "the §4 allocation key. Sizes are counts of maps, classes are"
       "conjugation (functional-graph) classes inside the stratum."
       ""
       (format "Enumeration: one orbit-marking pass over 8^8 maps, %.1f s." dt)
       ""
       checks-line
       ""
       (format "Number of strata: %d." n-strata)
       ""
       alloc-line
       ""
       bug-line
       ""
       "| rank | cycle-part | size | classes | alloc min-2 (alpha~0) | alloc target-300 |"
       "|---|---|---|---|---|---|"]
      (map row (:strata stats))
      [""])))

(spit "results/p008/e8-strata-allocation.txt" out)
(println "strata:" (count strata) "alpha150:" (format "%.9f" alpha-150)
         "total-min:" (:total alloc-round)
         "alpha300:" (format "%.9f" alpha-300) "total300:" (:total alloc-300))
(println "bug key:" bug-key "count:" (get (:counts alloc-round) bug-key))
(println "checks:" (:total-maps stats) (:total-classes stats)
         (:perm-strata stats) (:by-rank-classes stats))
(shutdown-agents)
