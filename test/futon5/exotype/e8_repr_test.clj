(ns futon5.exotype.e8-repr-test
  "P-008b §7 item 1 (representation only): perm? and e8->sigma.

  e8->sigma is deliberately NOT wired into sigma-of: no config reaches
  sigma-of today (grid.clj, self_tuning.clj:79, efe.clj:144/291/319,
  chain_risk.clj:32 all pass only the exotype), so the :allow-end8? dispatch
  is a pending design choice (claude-1/lon-claude-4), per the packet's STOP
  clause. What is tested here: perm? itself; e8->sigma = s8->sigma on 50
  seeded permutations; the existing fail-closed throw without any flag; and
  what the e8 table IS for the non-injective vector at positions 0 and 7."
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]))

(def f-noninjective [0 0 1 2 3 4 5 6])

(deftest perm?-recognises-permutations
  (testing "perm? (§7 item 1)"
    (is (true? (grid/perm? [7 6 5 4 3 2 1 0])))
    (is (true? (grid/perm? [0 1 2 3 4 5 6 7])))
    (is (false? (grid/perm? f-noninjective)) "repeated entry")
    (is (false? (grid/perm? [0 1 2 3 4 5 6])) "wrong length")
    (is (false? (grid/perm? [0 1 2 3 4 5 6 8])) "out of range")
    (is (false? (grid/perm? [0 1 2 3 4 5 6.0 7])) "non-integer entry")
    (is (false? (grid/perm? :identity)) "not a vector")))

(deftest e8->sigma-agrees-with-s8->sigma-on-permutations
  (testing "50 seeded random permutations: e8->sigma = s8->sigma exactly"
    (let [perms (ca/with-seed 20261115
                  (vec (repeatedly 50 #(#'grid/random-s8))))
          e8 #'grid/e8->sigma
          s8 #'grid/s8->sigma]
      (is (= 50 (count perms)))
      (is (every? grid/perm? perms))
      (doseq [p perms]
        (is (= (s8 p) (e8 p)) (str "table mismatch for " p))))))

(deftest sigma-of-stays-fail-closed-without-the-flag
  (testing "without :allow-end8?, a non-injective vector hits the existing throw"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"S8 exotype is not a permutation of 0..7"
         (grid/sigma-of f-noninjective))))
  (testing "e8->sigma accepts it; the table at positions 0 and 7 is as stated"
    (let [tt (vec ca/truth-table-3)
          table (#'grid/e8->sigma f-noninjective)]
      (is (= (nth tt 0) (get table (nth tt 0))))
      (is (= (nth tt 6) (get table (nth tt 7))))
      ;; f(0) = 0: the neighbourhood named by truth-table-3[0] maps to itself.
      ;; f(7) = 6: the neighbourhood named by truth-table-3[7] maps to the one
      ;; truth-table-3[6] names (the entry 6 of f, NOT position 6's image).
      (is (= 8 (count table))))))

(deftest e8->sigma-rejects-anything-else
  (testing "out-of-range entries and wrong shapes still throw"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #"E8 exotype"
         (#'grid/e8->sigma [0 0 1 2 3 4 5 8])))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #"E8 exotype"
         (#'grid/e8->sigma [0 0 1 2 3 4 5])))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #"E8 exotype"
         (#'grid/e8->sigma :identity)))))

;; --- step 1b: the *allow-end8?* flag (option A ruling, claude-1 2026-11-15) ---

(deftest flag-off-stays-fail-closed
  (testing "flag off (default): sigma-of on a non-injective vector throws the existing message"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"S8 exotype is not a permutation of 0..7"
         (grid/sigma-of f-noninjective)))))

(deftest flag-on-converts-non-injective-vectors
  (testing "flag on: the same call returns the e8 table"
    (let [tt (vec ca/truth-table-3)
          table (binding [grid/*allow-end8?* true]
                  (grid/sigma-of f-noninjective))]
      (is (= (nth tt 0) (get table (nth tt 0))))
      (is (= (nth tt 6) (get table (nth tt 7)))))))

(deftest flag-does-not-leak
  (testing "after the binding exits, the call throws again"
    (binding [grid/*allow-end8?* true]
      (is (map? (grid/sigma-of f-noninjective))))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"S8 exotype is not a permutation of 0..7"
         (grid/sigma-of f-noninjective))))
  (testing "flag-on first, flag-off second, same vector: memoisation must not leak"
    ;; e8->sigma and s8->sigma are separate memoized fns, and the flag-off
    ;; path never calls e8->sigma; the throw proves no cached e8 table is
    ;; served under the s8->sigma path.
    (is (map? (binding [grid/*allow-end8?* true]
                (grid/sigma-of f-noninjective))))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"S8 exotype is not a permutation of 0..7"
         (grid/sigma-of f-noninjective)))))

(deftest flag-on-leaves-the-s8-path-identical
  (testing "flag on, 50 seeded permutations: sigma-of = flag-off result exactly"
    (let [perms (ca/with-seed 20261116
                  (vec (repeatedly 50 #(#'grid/random-s8))))
          off (mapv grid/sigma-of perms)
          on (binding [grid/*allow-end8?* true]
               (mapv grid/sigma-of perms))]
      (is (= 50 (count perms)))
      (is (every? grid/perm? perms))
      (is (= off on)))))
