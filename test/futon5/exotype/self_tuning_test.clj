(ns futon5.exotype.self-tuning-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.chain-risk :as chain-risk]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(defn- baseline-step [state]
  (let [decisions
        (mapv (fn [index]
                (let [lambda (double (nth (:lambdas state) index))
                      decision (efe/cell-decision
                                :efe-full (assoc state :lambda lambda) index)
                      hunger (double (get-in decision [:winner :prediction :hunger]))]
                  (assoc decision :lambda lambda :selected-hunger hunger
                         :next-lambda
                         (tuning/next-lambda (:self-tuning-arm state) state index
                                             lambda hunger))))
              (range (count (:exotypes state))))
        exotypes (mapv #(get-in % [:winner :candidate-exotype]) decisions)
        lambdas (mapv :next-lambda decisions)
        previous (:genotype state)
        advanced (grid/step (assoc state :arm :heterogeneous-fixed))]
    (-> advanced
        (assoc :arm :efe-full :self-tuning-arm (:self-tuning-arm state)
               :previous-genotype previous :exotypes exotypes :lambdas lambdas
               :lambda-step-size (:lambda-step-size state)
               :hunger-target (:hunger-target state)
               :efe-decisions decisions :self-tuning-decisions decisions))))

(deftest hunger-feedback-has-the-required-sign
  (let [state {:lambda-step-size 0.01 :hunger-target 0.05
               :seed 1 :time 0}]
    (is (= 0.51 (tuning/next-lambda :hunger-coupled state 0 0.5 0.7)))
    (is (= 0.49 (tuning/next-lambda :hunger-coupled state 0 0.5 0.01)))
    (is (= 0.5 (tuning/next-lambda :hunger-coupled state 0 0.5 0.05)))))

(deftest clipping-and-fixed-arms
  (let [state {:lambda-step-size 0.1 :hunger-target 0.05
               :seed 1 :time 0}]
    (is (= 1.0 (tuning/next-lambda :hunger-coupled state 0 0.99 0.8)))
    (is (= 0.0 (tuning/next-lambda :hunger-coupled state 0 0.01 0.0)))
    (is (= 0.55 (tuning/next-lambda :fixed-0.55 state 0 0.1 0.9)))
    (is (= 0.4 (tuning/next-lambda :fixed-0.40 state 0 0.9 0.0)))
    (is (= 0.7 (tuning/next-lambda :fixed-0.70 state 0 0.1 0.9)))))

(deftest random-walk-is-stateless-and-does-not-read-hunger
  (let [state {:lambda-step-size 0.01 :hunger-target 0.05
               :seed 20260803 :time 17}
        low (tuning/next-lambda :random-walk state 9 0.5 0.0)
        high (tuning/next-lambda :random-walk state 9 0.5 1.0)]
    (is (= low high))
    (is (= low (tuning/next-lambda :random-walk state 9 0.5 0.4)))
    (is (< (Math/abs (- 0.01 (Math/abs (- low 0.5)))) 1.0e-12))))

(deftest global-observables-are-not-accepted-by-the-update-interface
  (testing "only arm/state/index/lambda/hunger enter next-lambda"
    (is (= 5 (count (first (:arglists (meta #'tuning/next-lambda))))))))

(deftest optimized-long-horizon-step-is-baseline-identical
  (let [initial
        (ca/with-seed 991
          (let [genotype (vec (ca/random-sigil-string 12))]
            {:arm :efe-full :self-tuning-arm :hunger-coupled
             :seed 991 :time 0 :lambda-step-size 0.001 :hunger-target 0.05
             :lambdas (mapv #(/ % 11.0) (range 12))
             :genotype genotype :previous-genotype genotype
             :phenotype (apply str (repeatedly 12 #(if (< (ca/rnd) 0.5) \0 \1)))
             :exotypes (grid/initial-grid :heterogeneous-fixed 12)}))]
    (loop [fast initial, baseline initial, remaining 20]
      (when (pos? remaining)
        (let [fast-next (tuning/step fast)
              baseline-next (baseline-step baseline)]
          (is (= baseline-next fast-next))
          (recur fast-next baseline-next (dec remaining)))))))

(defn- seeded-state [seed width]
  (ca/with-seed seed
    (let [genotype (vec (ca/random-sigil-string width))]
      {:arm :efe-full :self-tuning-arm :hunger-coupled
       :seed seed :time 0 :lambda-step-size 0.001 :hunger-target 0.05
       :lambdas (vec (repeat width 0.55))
       :genotype genotype :previous-genotype genotype
       :phenotype (ca/random-phenotype-string width)
       :exotypes (grid/initial-grid :heterogeneous-fixed width)})))

(deftest default-transform-controls-are-byte-compatible
  (testing "explicit beta=0 and p=1 preserve the prior trajectory exactly"
    (doseq [seed [17 991 20260803 424242]]
      (let [initial (seeded-state seed 24)
            baseline (take 61 (iterate tuning/step initial))
            explicit (take 61 (iterate tuning/step
                                       (assoc initial :blend-strength 0.0
                                                      :apply-probability 1.0)))]
        (doseq [[before after] (map vector baseline explicit)]
          (is (= (select-keys before [:genotype :phenotype :exotypes])
                 (select-keys after [:genotype :phenotype :exotypes]))
              (str "default transform drift at seed " seed))))))
  (testing "p=0 leaves each rule unchanged even when blending is enabled"
    (let [state (seeded-state 17 24)]
      (is (= (:genotype state)
             (#'tuning/genotype-step
              (assoc state :blend-strength 0.75 :apply-probability 0.0)))))))

(deftest blending-spreads-rule-content-laterally
  (testing "a one-cell genotype perturbation reaches farther with beta=0.75"
    (let [seed 20260803
          width 80
          initial (seeded-state seed width)
          perturbed (update initial :genotype assoc 0
                            (ca/sigil-for
                             (apply str (map ca/flip-bit
                                             (ca/bits->ints
                                              (ca/bits-for
                                               (str (first (:genotype initial)))))))))
          reach (fn [beta]
                  (let [control (nth (iterate tuning/step
                                             (assoc initial :blend-strength beta)) 60)
                        treatment (nth (iterate tuning/step
                                               (assoc perturbed :blend-strength beta)) 60)]
                    (count (filter false?
                                   (map = (:genotype control)
                                        (:genotype treatment))))))
          no-blend (reach 0.0)
          blended (reach 0.75)]
      (is (< no-blend blended)
          (str "blend did not increase lateral reach: beta=0 reached " no-blend
               ", beta=0.75 reached " blended)))))

(defn- uniform-builder-state [seed width]
  (assoc (seeded-state seed width)
         :exotypes (vec (repeat width :builder))
         :epistemic-coefficient 100.0
         :blend-strength 0.0))

(deftest winning-blend-action-writes-rule-but-not-exotype
  (let [state (assoc (uniform-builder-state 17 24) :blend-action? true)
        index 7
        left (nth (:genotype state) (dec index))
        centre (nth (:genotype state) index)
        right (nth (:genotype state) (inc index))
        expected (grid/blend-rule left centre right)
        decision (tuning/cell-decision state index)
        advanced (tuning/step state)]
    (is (= 4 (count (:candidates decision))))
    (is (= :blend (get-in decision [:winner :policy])))
    (is (= expected (nth (:genotype advanced) index)))
    (is (= (nth (:exotypes state) index)
           (nth (:exotypes advanced) index)))))

(deftest blend-action-opens-a-policy-controlled-content-channel
  (let [width 24
        control (uniform-builder-state 17 width)
        original-bits (ca/bits-for (str (first (:genotype control))))
        replacement (ca/sigil-for
                     (apply str (map ca/flip-bit
                                     (ca/bits->ints original-bits))))
        treatment (update control :genotype assoc 0 replacement)
        run-twins (fn [enabled?]
                    [(vec (take 11 (iterate tuning/step
                                            (assoc control
                                                   :blend-action? enabled?))))
                     (vec (take 11 (iterate tuning/step
                                            (assoc treatment
                                                   :blend-action? enabled?))))])
        cross-cell-witness
        (fn [[controls treatments]]
          (first
           (for [time (range 1 11)
                 index (range 1 width)
                 :when (not= (nth (:genotype (nth controls time)) index)
                             (nth (:genotype (nth treatments time)) index))
                 :when (every?
                        (fn [prior-time]
                          (= (nth (:exotypes (nth controls prior-time)) index)
                             (nth (:exotypes (nth treatments prior-time)) index)))
                        (range (inc time)))]
             {:time time :index index})))
        without-action (cross-cell-witness (run-twins false))
        with-action (cross-cell-witness (run-twins true))]
    (is (nil? without-action)
        "the three sigma-only actions must not create a cross-cell rule channel")
    (is (some? with-action)
        "blend action must diverge a neighbour's rule while its sigma stays equal")))

(deftest precision-weighted-policy-selection-is-inspectable
  (let [state (assoc (uniform-builder-state 17 24)
                     :blend-action? true
                     :policy-precision 4.0)
        decision (tuning/cell-decision state 7)
        probabilities (:policy-probabilities decision)
        probability-by-policy (into {}
                                    (map (juxt :policy :probability)
                                         probabilities))
        candidates-by-cost (sort-by :total (:candidates decision))
        probabilities-by-cost (mapv #(probability-by-policy (:policy %))
                                    candidates-by-cost)]
    (is (< (Math/abs (- 1.0 (reduce + (map :probability probabilities))))
           1.0e-12))
    (is (apply >= probabilities-by-cost)
        "lower-cost candidates must have weakly greater softmax probability")
    (is (= (mapv :policy (:candidates decision))
           (mapv :policy probabilities)))
    (let [tags (mapv var-get [#'tuning/blend-stream-tag
                              #'tuning/apply-stream-tag
                              #'tuning/policy-stream-tag])]
      (is (= 3 (count (set tags))))
      (is (every? (complement zero?) tags)))))

(deftest precision-weighted-trajectories-are-deterministic
  (let [initial (assoc (seeded-state 991 24)
                       :blend-action? true
                       :epistemic-coefficient 0.2
                       :policy-precision 16.0)
        first-run (vec (take 31 (iterate tuning/step initial)))
        second-run (vec (take 31 (iterate tuning/step initial)))]
    (is (= first-run second-run))))

(deftest chain-risk-prices-hold-and-blend-from-their-own-bytes
  (let [state (assoc (uniform-builder-state 17 24)
                     :blend-action? true :chain-risk? true)
        index 7
        genotype (:genotype state)
        centre (nth genotype index)
        blended (grid/blend-rule (nth genotype (dec index))
                                 centre
                                 (nth genotype (inc index)))
        candidates (into {}
                         (map (juxt :policy identity)
                              (:candidates (tuning/cell-decision state index))))]
    (is (= (chain-risk/risk :builder (chain-risk/byte-of centre))
           (:risk (candidates :hold))))
    (is (= (chain-risk/risk :builder (chain-risk/byte-of blended))
           (:risk (candidates :blend))))
    (is (not= (:risk (candidates :hold)) (:risk (candidates :blend))))))
