(ns futon5.mmca.predictive-information-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.mmca.predictive-information :as pi]))

(deftest entropy-and-mi-estimates
  (testing "constant variables carry no predictive information"
    (is (zero? (:miller-madow
                (pi/mutual-information-estimate (repeat 100 0) (repeat 100 0))))))
  (testing "a balanced variable perfectly predicts itself"
    (let [xs (take 200 (cycle [0 1]))
          estimate (pi/mutual-information-estimate xs xs)]
      (is (< 0.99 (:miller-madow estimate) 1.02)))))

(deftest ais-rejects-frozen-grid
  (let [grid (vec (repeat 40 (vec (repeat 12 0))))
        score (pi/active-information-storage grid {:k 4 :burn-in 8})]
    (is (zero? (:ais-corrected score)))
    (is (= 32 (:samples-per-cell score)))))

(deftest transfer-entropy-rejects-frozen-grid
  (let [grid (vec (repeat 40 (vec (repeat 12 0))))
        score (pi/transfer-entropy grid {:k 4 :burn-in 8})]
    (is (zero? (:te-corrected score)))
    (is (= 20 (:directed-links score)))
    (is (= 32 (:samples-per-link score)))))

(deftest transfer-entropy-detects-directed-shift
  (let [initial (mapv #(mod (* 17 %) 2) (range 65))
        ;; Shift right: destination x receives source x-1 on every update.
        step (fn [row] (vec (cons (peek row) (pop row))))
        grid (vec (take 90 (iterate step initial)))
        score (pi/transfer-entropy grid {:k 2 :burn-in 4})
        left (filter #(= -1 (:offset %)) (:per-link score))
        right (filter #(= 1 (:offset %)) (:per-link score))]
    (is (> (reduce + (map :miller-madow left))
           (reduce + (map :miller-madow right))))))

(deftest occupants-share-the-parameterized-estimator
  (let [initial (mapv #(mod (* 17 %) 2) (range 65))
        step (fn [row] (vec (cons (peek row) (pop row))))
        grid (vec (take 90 (iterate step initial)))
        ais (pi/active-information-storage grid {:k 2 :burn-in 4})
        generic-ais (pi/predictive-information
                     grid :self-past {:k 2 :burn-in 4})
        distance (pi/distance-transfer-entropy grid -2 2 {:k 2 :burn-in 4})
        generic-distance
        (pi/predictive-information
         grid {:type :offset :d -2 :tau 2} {:k 2 :burn-in 4})]
    (is (= (:ais-corrected ais) (:score-corrected generic-ais)))
    (is (= (:te-corrected distance) (:score-corrected generic-distance)))
    (is (pos? (:te-corrected distance)))))

(deftest aggregate-fill-reuses-the-local-field
  (let [initial (mapv #(if (< % 20) (mod % 2) 0) (range 65))
        step (fn [row] (vec (cons (peek row) (pop row))))
        grid (vec (take 90 (iterate step initial)))
        mean-score (pi/predictive-information
                    grid {:type :offset :d -2 :tau 2}
                    {:k 2 :burn-in 4 :aggregate :mean})
        heterogeneous-score (pi/predictive-information
                             grid {:type :offset :d -2 :tau 2}
                             {:k 2 :burn-in 4 :aggregate :heterogeneity})]
    (is (= (:per-source mean-score) (:per-source heterogeneous-score)))
    (is (= (:mean-corrected mean-score) (:score-corrected mean-score)))
    (is (= (:heterogeneity-corrected heterogeneous-score)
           (:score-corrected heterogeneous-score)))
    (is (pos? (:score-corrected heterogeneous-score)))))

(deftest metaca-bitplane-adapter
  (let [history (vec (repeat 20 (apply str (repeat 8 "一"))))
        ais (pi/score-metaca-history history {:k 3 :burn-in 5})
        te (pi/score-metaca-transfer-entropy history {:k 3 :burn-in 5})]
    (is (= 8 (:plane-count ais) (:plane-count te)))
    (is (zero? (:mean-ais-corrected ais)))
    (is (zero? (:mean-te-corrected te)))))

(deftest metaca-alphabet-seam
  (let [history (vec (repeat 20 (apply str (repeat 8 "一"))))
        bitplanes (pi/project-history history :bitplane)
        coarse (pi/project-history history [:coarse 8])
        full-cell (pi/project-history history :full-cell)
        scores (mapv #(pi/score-metaca-distance-transfer-entropy
                       history -2 2 % {:k 3 :burn-in 5})
                     [:bitplane [:coarse 8] :full-cell])]
    (is (= 8 (count bitplanes)))
    (is (= 1 (count coarse) (count full-cell)))
    (is (= {:alphabet :coarse :bins 8} (:projection (first coarse))))
    (is (every? #(zero? (:mean-te-corrected %)) scores))))
