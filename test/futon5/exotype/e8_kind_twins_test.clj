(ns futon5.exotype.e8-kind-twins-test
  "P-008b §7 item 6 (ruling 2026-11-15, exotype main 5176228): on a
  mutation hit a keyword kind is replaced by its S8 vector (kind->s8
  kind) and that vector is mutated. The requirement is MAP EQUALITY only:
  (= (sigma-of kind) (s8->sigma (kind->s8 kind))) for every kind used.

  s8->sigma is private, so the test uses the public-path form
  (= (sigma-of kind) (sigma-of (kind->s8 kind))) -- the same comparison,
  because sigma-of on a keyword returns (get propagators kind)
  (grid.clj:169-177) and on a permutation vector routes through
  s8->sigma (grid.clj:171-173) -- AND cross-checks the private var for
  the 12 selectable kinds.

  Result pinned here: 0 of the 12 kinds are immutable, so the 'kinds
  stay' branch of the ruling applies (option (b); :e8-v1 keeps
  'initial S8 or 12 kinds')."
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.exotype.grid :as grid]))

(def ^:private kinds
  "The 12 selectable kinds (grid/exotype-kinds)."
  grid/exotype-kinds)

(def ^:private twins
  "The table in results/p008/e8-kind-twins.txt, asserted per kind."
  {:builder  [0 1 5 3 4 7 6 2]
   :collapser [5 0 1 4 2 3 7 6]
   :chaos    [2 1 5 7 0 4 3 6]
   :identity [0 1 2 3 4 5 6 7]
   :even4    [1 0 4 5 2 3 7 6]
   :even8    [1 0 4 7 2 3 5 6]
   :even1    [7 0 1 4 2 3 5 6]
   :odd53    [2 0 1 4 7 3 5 6]
   :fix2     [0 1 7 4 2 3 5 6]
   :fix3     [0 1 2 4 7 3 5 6]
   :fix4     [0 1 2 7 4 3 5 6]
   :fix6     [0 1 2 3 4 5 7 6]
   ;; not in exotype-kinds; no registered arm uses them (for completeness)
   :even44   [4 0 1 7 2 3 5 6]
   :odd332   [1 0 3 4 2 7 5 6]})

(deftest kind-twins-table
  (testing "every kind has a sigma, the recorded twin vector, and the
            ruling predicate (sigma-of kind) = (s8->sigma twin) holds"
    (doseq [k (keys twins)]
      (testing (name k)
        (is (contains? grid/propagators k) "has a sigma")
        (is (= (get twins k) (grid/kind->s8 k)) "twin vector as recorded")
        (is (= (grid/sigma-of k) (grid/sigma-of (get twins k)))
            "public-path ruling predicate")
        (when (contains? (set kinds) k)
          (is (= (get grid/propagators k)
                 (#'grid/s8->sigma (get twins k)))
              "direct s8->sigma ruling predicate"))))))

(deftest wrong-twin-fails-the-predicate
  (testing "a deliberately wrong vector for :collapser is NOT the same map"
    ;; the true twin is [5 0 1 4 2 3 7 6]; swapping two entries keeps it a
    ;; permutation but a DIFFERENT map
    (let [wrong [5 1 0 4 2 3 7 6]]
      (is (grid/perm? wrong) "the bad case is still a permutation")
      (is (not= (grid/sigma-of :collapser) (grid/sigma-of wrong)))
      (is (not= (#'grid/s8->sigma wrong) (get grid/propagators :collapser))))))

(deftest immutable-count-is-zero
  (testing "0 of the 12 kinds are immutable: the 'kinds stay' branch applies"
    (let [immutable (filter (fn [k]
                              (not (= (grid/sigma-of k)
                                      (grid/sigma-of (grid/kind->s8 k)))))
                            kinds)]
      (is (= [] immutable) (str "immutable kinds: " immutable))
      (is (<= (count immutable) 2)
          "at most 2 immutable keeps :e8-v1 at 'initial S8 or 12 kinds'"))))
