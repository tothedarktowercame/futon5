(ns futon5.mmca.exoevolve-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.mmca.exoevolve :as exoevolve]
            [futon5.mmca.exotype :as exotype]))

(defn- mutation-sequence [seed genome n]
  (let [rng (java.util.Random. seed)]
    (rest
      (take (inc n)
            (iterate #(exoevolve/mutate-exotype rng % :both) genome)))))

(deftest gain-decodes-full-domain
  (testing "sigil decoding exposes all eight gain levels including 0 and 1"
    (let [decoded (->> (range 256)
                       (map (comp :sigil #(nth (ca/sigil-entries) %)))
                       (map (comp :gain :params exotype/lift))
                       set)]
      (is (= (set exotype/gain-levels) decoded))
      (is (contains? decoded 0.0))
      (is (contains? decoded 1.0)))))

(deftest gain-mutation-reaches-full-domain-deterministically
  (testing "seeded mutation reaches every gain level and repeats exactly"
    (let [genome (exotype/lift "一")
          mutations-a (vec (mutation-sequence 20260730 genome 256))
          mutations-b (vec (mutation-sequence 20260730 genome 256))
          reached (set (map #(get-in % [:params :gain]) mutations-a))]
      (is (= (set exotype/gain-levels) reached))
      (is (= mutations-a mutations-b)))))

(deftest gain-survives-selection-and-reproduction
  (testing "selection distinguishes otherwise identical genomes by inherited gain"
    (let [low (assoc-in (exotype/lift "一") [:params :gain] 0.0)
          high (assoc-in (exotype/lift "一") [:params :gain] 1.0)
          batch [{:exotype low :score {:final 1.0}}
                 {:exotype high :score {:final 9.0}}]
          next-pop (exoevolve/evolve-population
                     (java.util.Random. 17) [low high] batch :local)]
      (is (= high (first next-pop)))
      (is (every? #(contains? (:params %) :gain) next-pop))
      (is (every? (set exotype/gain-levels)
                  (map #(get-in % [:params :gain]) next-pop))))))

(deftest width-decodes-and-mutation-reaches-full-domain
  (testing "all four odd widths decode and are reachable by seeded mutation"
    (let [decoded (->> (range 256)
                       (map (comp :sigil #(nth (ca/sigil-entries) %)))
                       (map (comp :width :params exotype/lift))
                       set)]
      (is (= (set exotype/width-levels) decoded)))
    (let [mutations (mutation-sequence 404 (exotype/lift "一") 256)
          reached (set (map #(get-in % [:params :width]) mutations))]
      (is (= (set exotype/width-levels) reached)))))

(deftest width-survives-selection-and-reproduction
  (testing "selection distinguishes otherwise identical genomes by inherited width"
    (let [narrow (assoc-in (exotype/lift "一") [:params :width] 3)
          wide (assoc-in (exotype/lift "一") [:params :width] 9)
          batch [{:exotype narrow :score {:final 2.0}}
                 {:exotype wide :score {:final 8.0}}]
          next-pop (exoevolve/evolve-population
                     (java.util.Random. 23) [narrow wide] batch :local)]
      (is (= wide (first next-pop)))
      (is (every? #(contains? (:params %) :width) next-pop))
      (is (every? (set exotype/width-levels)
                  (map #(get-in % [:params :width]) next-pop))))))

(deftest zero-update-prob-is-reachable-with-legacy-decoding-intact
  (testing "legacy sigils retain their four meanings while mutation adds zero"
    (is (= [0.25 0.5 0.75 1.0]
           (mapv #(get-in (exotype/lift %) [:params :update-prob])
                 ["一" "乙" "二" "丁"])))
    (let [mutations (mutation-sequence 505 (exotype/lift "一") 256)
          reached (set (map #(get-in % [:params :update-prob]) mutations))]
      (is (= (set exotype/update-prob-levels) reached))
      (is (contains? reached 0.0)))))

(deftest update-prob-survives-selection-and-reproduction
  (testing "selection distinguishes otherwise identical genomes by update probability"
    (let [fixed (assoc-in (exotype/lift "一") [:params :update-prob] 0.0)
          plastic (assoc-in (exotype/lift "一") [:params :update-prob] 1.0)
          batch [{:exotype plastic :score {:final 1.0}}
                 {:exotype fixed :score {:final 10.0}}]
          next-pop (exoevolve/evolve-population
                     (java.util.Random. 29) [plastic fixed] batch :local)]
      (is (= fixed (first next-pop)))
      (is (every? #(contains? (:params %) :update-prob) next-pop))
      (is (every? (set exotype/update-prob-levels)
                  (map #(get-in % [:params :update-prob]) next-pop))))))
