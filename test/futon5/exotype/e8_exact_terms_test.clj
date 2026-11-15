(ns futon5.exotype.e8-exact-terms-test
  "P-008b §7 item 3 closing condition (amended): numerical check that the
  exact terms are correct for non-injective f, by exhaustive enumeration of
  the ENGINE's one-step write distribution (all 256 bytes x 8 draws) against
  chain-risk's successor construction and gen/rule-change-rate.

  Which engine function is enumerated, and how the draw is forced:
  `gen/rule-permute` (private; the function `grid/apply-exotype` calls on
  every CA-step exotype write). Its draw is `ca/rnd-int 8`; the test forces
  draw k deterministically with `with-redefs` binding `ca/rnd-int` to
  (constantly k) around the call. The write rule itself is never re-derived
  here: the test only converts byte <-> bit-string (bookkeeping: character k
  of the bits string is bit k of the byte, the same convention
  chain-risk/byte-of uses).

  What is compared, exactly:
  (a) the 256 x 8 successor table from `gen/rule-permute` against the same
      table built with chain-risk's private `successor` (the function
      `kind-column` builds its column from) on `gen/sigma-positional` of
      f's sigma -- exact equality of every entry;
  (b) the fraction of (byte, draw) pairs whose successor differs from the
      byte, over uniformly distributed bytes, against
      `gen/rule-change-rate` of f's sigma. `rule-change-rate` IS that
      quantity under the uniform-byte measure: (1/8) sum_k [1 if sigma
      fixes k, 1/2 if addressable, 0 otherwise]; the enumeration is the
      same measure, so exact equality is expected.

  The bug map is not typed in: it is the historical 2015 Emacs bug
  k -> max(k-1, 0) as a position vector in the EMBER-VENDORED ELISP order,
  converted with `gen/positional-sigma->neighbourhood-sigma` through
  grid's private `elisp-table` (the repo's existing historical shim), then
  to a futon5 End(8) vector with `gen/sigma-positional`."
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.chain-risk :as chain-risk]
            [futon5.exotype.grid :as grid]
            [futon5.xenotype.generator :as gen]))

(defn- byte->bits [b]
  (apply str (map #(if (bit-test b %) \1 \0) (range 8))))

(defn- bits->byte [s]
  (reduce (fn [b k] (if (= \1 (nth s k)) (bit-or b (bit-shift-left 1 k)) b))
          0 (range 8)))

(defn- engine-successors
  "The 256 x 8 successor table from the ENGINE: gen/rule-permute on EXOTYPE's
  sigma, with the draw forced to each k in turn via with-redefs on ca/rnd-int."
  [exotype]
  (binding [grid/*allow-end8?* true]
    (let [sigma (grid/sigma-of exotype)]
      (mapv (fn [b]
              (let [bits (byte->bits b)]
                (mapv (fn [k]
                        (with-redefs [ca/rnd-int (constantly (long k))]
                          (bits->byte (#'gen/rule-permute bits sigma))))
                      (range 8))))
            (range 256)))))

(defn- chain-risk-successors
  "The same table from chain-risk's own successor function (what kind-column
  builds its column from), on gen/sigma-positional of EXOTYPE's sigma."
  [exotype]
  (binding [grid/*allow-end8?* true]
    (let [positions (gen/sigma-positional (grid/sigma-of exotype))]
      (mapv (fn [b]
              (mapv #(#'chain-risk/successor positions b %) (range 8)))
            (range 256)))))

(defn- changed-fraction
  "Fraction of (byte, draw) pairs whose successor differs from the byte."
  [successors]
  (/ (count (for [b (range 256)
                  k (range 8)
                  :when (not= b (nth (nth successors b) k))]
              1))
     (* 256.0 8)))

(defn- check-exotype
  [exotype]
  (let [engine (engine-successors exotype)
        reference (chain-risk-successors exotype)]
    (is (= reference engine) (str "successor table mismatch for " exotype))
    (when-not (= reference engine)
      (doseq [b (range 256) k (range 8)]
        (when-not (= (nth (nth reference b) k) (nth (nth engine b) k))
          (is false (str "first mismatch: byte " b " draw " k " engine "
                         (nth (nth engine b) k) " chain-risk "
                         (nth (nth reference b) k)))
          (throw (ex-info "stop at first mismatch" {:byte b :draw k})))))
    (binding [grid/*allow-end8?* true]
      (is (= (gen/rule-change-rate (grid/sigma-of exotype))
             (changed-fraction engine))
          (str "rule-change-rate mismatch for " exotype)))))

(defn bug-map
  "The 2015 bug through the historical elisp shim: position vector
  k -> max(k-1,0) in the vendored elisp neighbourhood order, converted by
  gen/positional-sigma->neighbourhood-sigma through grid's elisp-table,
  then to a futon5 vector by gen/sigma-positional."
  []
  (let [bug-position-vector [0 0 1 2 3 4 5 6]
        nbhd (gen/positional-sigma->neighbourhood-sigma
              bug-position-vector @#'grid/elisp-table)]
    (binding [grid/*allow-end8?* true]
      (gen/sigma-positional nbhd))))

(defn- seeded-noninjective-with-triple
  "Seeded draw of a non-injective 8-vector with at least one position
  carrying >= 3 preimages."
  [seed]
  (ca/with-seed seed
    (loop []
      (let [f (vec (repeatedly 8 #(ca/rnd-int 8)))
            preimage-counts (vals (frequencies f))]
        (if (and (not (grid/perm? f))
                 (some #(>= % 3) preimage-counts))
          f
          (recur))))))

(deftest exact-terms-match-the-engine-bug-map
  (let [f (bug-map)]
    (testing "the bug map via the elisp shim is a non-injective vector"
      (is (vector? f))
      (is (not (grid/perm? f)))
      (is (some #(>= % 2) (vals (frequencies f)))))
    (testing "successor table and rule-change-rate match exactly"
      (check-exotype f))))

(deftest exact-terms-match-the-engine-seeded-noninjective
  (testing "five seeded non-injective f, each with a >=3-preimage position"
    (let [fs (mapv seeded-noninjective-with-triple
                   [20261130 20261131 20261132 20261133 20261134])]
      (is (= 5 (count fs)))
      (doseq [f fs]
        (is (not (grid/perm? f)) (str "f=" f))
        (is (some #(>= % 3) (vals (frequencies f))) (str "f=" f))
        (check-exotype f)))))

(deftest exact-terms-match-the-engine-permutation-controls
  (testing "named kind control: :chaos (keyword, old path)"
    (check-exotype :chaos))
  (testing "seeded permutation control"
    (let [p (ca/with-seed 20261135 (#'grid/random-s8))]
      (is (grid/perm? p))
      (check-exotype p))))

(deftest wrong-reference-fails
  (testing "a deliberately wrong reference (two entries swapped) must differ"
    (let [f (bug-map)
          f-wrong (assoc f 2 (nth f 5) 5 (nth f 2))]
      (is (not= f f-wrong))
      (is (not= (chain-risk-successors f)
                (chain-risk-successors f-wrong))
          "swapping two entries must change the successor table")
      ;; and the check on the wrong map must fail: the swapped table is not
      ;; what the engine does to f
      (is (not= (engine-successors f)
                (chain-risk-successors f-wrong))))))
