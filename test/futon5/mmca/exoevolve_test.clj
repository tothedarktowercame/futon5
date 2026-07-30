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

(deftest inherited-initial-genotype-is-created-used-and-point-mutated
  (testing "length-aware genomes carry the exact field used by evaluation"
    (let [genome (exoevolve/pick-exotype (java.util.Random. 31) :local 12)
          field (:initial-genotype genome)]
      (is (= 12 (count field)))
      (is (= field
             (exoevolve/initial-genotype-for
               genome (java.util.Random. 999) 12)))
      (is (= field
             (exoevolve/mutate-initial-genotype
               (java.util.Random. 32) field 0.0)))
      (let [mutated (exoevolve/mutate-initial-genotype
                      (java.util.Random. 32) field 1.0)]
        (is (= 12 (count mutated)))
        (is (every? false? (map = field mutated)))))))

(deftest inherited-initial-genotype-mutation-is-deterministic
  (testing "same seed, genome, and mutation rate produce the same field sequence"
    (let [genome (exoevolve/pick-exotype (java.util.Random. 33) :both 16)
          mutate-seq (fn []
                       (let [rng (java.util.Random. 34)]
                         (vec
                           (rest
                             (take 65
                                   (iterate
                                     #(exoevolve/mutate-exotype
                                        rng % :both
                                        {:initial-field-mutation-rate 0.2})
                                     genome))))))]
      (is (= (mutate-seq) (mutate-seq))))))

(deftest inherited-initial-genotype-survives-selection-and-reproduction
  (testing "field identity participates in selection and is inherited intact at rate zero"
    (let [base (exotype/lift "一")
          field-a (apply str (repeat 8 "一"))
          field-b (apply str (repeat 8 "乐"))
          genome-a (assoc base :initial-genotype field-a)
          genome-b (assoc base :initial-genotype field-b)
          batch [{:exotype genome-a :score {:final 1.0}}
                 {:exotype genome-b :score {:final 12.0}}]
          next-pop (exoevolve/evolve-population
                     (java.util.Random. 35)
                     [genome-a genome-b]
                     batch
                     :local
                     {:initial-field-mutation-rate 0.0})]
      (is (= genome-b (first next-pop)))
      (is (= [field-b field-b] (mapv :initial-genotype next-pop))))))

(deftest known-genome-mutation-roundtrip-covers-every-gene-domain
  (testing "a seeded mutation walk from known values reaches every gene level"
    (let [base (exotype/lift "一")
          known (-> base
                    (assoc-in [:params :gain] 0.0)
                    (assoc-in [:params :width] 3)
                    (assoc-in [:params :update-prob] 0.0)
                    (assoc :initial-genotype (apply str (repeat 10 "一"))))
          rng (java.util.Random. 20260730)
          mutations (rest
                      (take 4097
                            (iterate
                              #(exoevolve/mutate-exotype
                                 rng % :both
                                 {:initial-field-mutation-rate 0.1})
                              known)))
          decoded (map exotype/lift (map :sigil (ca/sigil-entries)))
          domain (fn [k] (set (map #(get-in % [:params k]) decoded)))
          reached (fn [k] (set (map #(get-in % [:params k]) mutations)))]
      (is (= (set exotype/gain-levels) (reached :gain)))
      (is (= (set exotype/width-levels) (reached :width)))
      (is (= (set exotype/update-prob-levels) (reached :update-prob)))
      (doseq [gene [:rotation :match-threshold :mix-mode]]
        (is (= (domain gene) (reached gene))))
      (is (some #(not= (:initial-genotype known) (:initial-genotype %))
                mutations)))))

(deftest legacy-genomes-retain-fresh-field-fallback
  (testing "old two-argument genomes remain fieldless and evaluate via fresh fields"
    (let [legacy (exoevolve/pick-exotype (java.util.Random. 41) :local)
          a (exoevolve/initial-genotype-for
              legacy (java.util.Random. 42) 10)
          b (exoevolve/initial-genotype-for
              legacy (java.util.Random. 42) 10)]
      (is (not (contains? legacy :initial-genotype)))
      (is (= 10 (count a)))
      (is (= a b)))))
