(ns futon5.mmca.diagonal-transport-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.mmca.diagonal-transport :as transport]))

(defn- eca-step [rule row]
  (let [n (count row)]
    (mapv (fn [i]
            (let [left (nth row (mod (dec i) n))
                  center (nth row i)
                  right (nth row (mod (inc i) n))
                  index (+ (* 4 left) (* 2 center) right)]
              (if (bit-test rule index) 1 0)))
          (range n))))

(defn- eca-spacetime [rule width generations seed]
  (let [rng (java.util.Random. (long seed))
        initial (vec (repeatedly width #(if (.nextBoolean rng) 1 0)))]
    (vec (take generations (iterate #(eca-step rule %) initial)))))

(defn- anchor-score [rule seed]
  (-> (eca-spacetime rule 120 100 seed)
      (transport/profile)
      (transport/median-score)))

(deftest frozen-field-has-no-innovation-transport
  (let [field (vec (repeat 30 (vec (take 80 (cycle [0 1 1 0 1])))))
        p (transport/profile field)]
    (is (every? zero? (map :score p)))
    (is (every? zero? (map :innovation-density p)))))

(deftest eca-anchor-orders-complex-above-chaotic-above-settled
  (testing "the preregistered Rule-110 bar holds independently for every seed"
    (doseq [seed [11 23 37 41 59]]
      (let [complex (map #(anchor-score % seed) [110 54])
            chaotic (map #(anchor-score % seed) [30 90])
            settled (map #(anchor-score % seed) [250 0])]
        (is (> (apply min complex) (apply max chaotic))
            (str "complex > chaotic at seed " seed))
        (is (> (apply min chaotic) (apply max settled))
            (str "chaotic > settled at seed " seed))))))

(deftest genotype-profile-keeps-rule-bits-separate
  (let [row ["00000000" "10101010" "11110000"]
        frozen (vec (repeat 30 row))
        p (transport/genotype-profile frozen)]
    (is (= 8 (count (transport/genotype-bitplanes frozen))))
    (is (every? zero? (map :score p)))
    (is (every? zero? (map :innovation-density p)))
    (is (every? #(= 8 (count (:bit-plane-scores %))) p))))

(deftest genotype-profile-rejects-coarse-rule-identifiers
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"eight-bit rule strings"
                        (transport/genotype-profile
                         (vec (repeat 3 [0 1 255]))))))
