(ns futon5.exotype.self-tuning
  "Per-cell lambda adaptation driven only by the selected policy's local hunger.

   The update never reads damage, reach, entropy, kind counts, or any global
   statistic. Lambda and exotype updates are synchronous."
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]))

(def arms [:hunger-coupled :random-walk :fixed-0.55 :fixed-0.40 :fixed-0.70])
(def default-step-size 0.001)
(def default-hunger-target (:hunger efe/preferences))

;; The long-horizon experiment evaluates the same finite local observation
;; alphabet hundreds of millions of times.  Cache only the frozen EFE model's
;; score decomposition; the per-cell lambda term remains computed at the use
;; site.  This changes neither the local information boundary nor the policy.
(def ^:private score-cache
  (delay
    (into {}
          (for [candidate grid/exotype-kinds
                activity [0.0 (/ 1.0 3.0) (/ 2.0 3.0)]
                diversity [(/ 1.0 3.0) (/ 2.0 3.0) 1.0]]
            [[candidate activity diversity]
             (efe/score-policy :efe-full candidate
                               {:activity activity :diversity diversity}
                               {:lambda 0.0})]))))

(def ^:private genotype-transition-cache
  (delay
    (let [truth-table (vec ca/truth-table-3)]
      (into {}
            (for [{:keys [sigil bits]} (ca/sigil-entries)]
              [sigil
               (into {}
                     (for [exotype grid/exotype-kinds
                           :let [sigma (get grid/propagators exotype)]]
                       [exotype
                        (mapv (fn [k]
                                (let [src (nth truth-table k)
                                      dst (get sigma src src)
                                      di (.indexOf ^java.util.List truth-table dst)
                                      value (if (= \0 (nth bits k)) \1 \0)]
                                  (ca/sigil-for
                                   (str (subs bits 0 di) value
                                        (subs bits (inc di))))))
                              (range 8))]))])))))

(defn clip-unit [x]
  (-> (double x) (max 0.0) (min 1.0)))

(defn- signed-step [value target step-size]
  (cond
    (> value target) step-size
    (< value target) (- step-size)
    :else 0.0))

(defn- random-direction
  "NB deliberately NOT `ca/mix-seed`-wrapped. The per-cell stride here is 9176,
   not 1, and at that separation the raw first draws are already independent
   (measured: 2.00 of 2 distinct outcomes per step, versus 1.06 at stride 1).
   Mixing would change the trajectory for no correctness gain. TN-baldwin-reboot 2."
  [{:keys [seed time]} index]
  (let [draw-seed (+ (long (or seed 0))
                     (* 1000003 (long (or time 0)))
                     (* 9176 (long index))
                     44119)]
    ;; rng-audit:raw-ok -- stride 9176 between cells, measured independent
    ;; (2.00 of 2 distinct outcomes per step); mixing would move a trajectory
    ;; for no correctness gain. Enforced by exotype/invariants-test.
    (if (< (.nextDouble (java.util.Random. draw-seed)) 0.5) -1.0 1.0)))

(defn next-lambda
  "One local update. HUNGER is ignored by the random-walk null."
  [arm state index lambda hunger]
  (let [step-size (double (get state :lambda-step-size default-step-size))
        target (double (get state :hunger-target default-hunger-target))]
    (case arm
      :hunger-coupled
      (clip-unit (+ lambda (signed-step hunger target step-size)))
      :random-walk
      (clip-unit (+ lambda (* step-size (random-direction state index))))
      :fixed-0.55 0.55
      :fixed-0.40 0.40
      :fixed-0.70 0.70
      (throw (ex-info "unknown self-tuning arm" {:arm arm :available arms})))))

(defn cell-decision [state index]
  (let [lambda (double (nth (:lambdas state) index))
        width (count (:exotypes state))
        sources [{:policy :hold :source index}
                 {:policy :adopt-left :source (mod (dec index) width)}
                 {:policy :adopt-right :source (mod (inc index) width)}]
        observation (efe/local-observation state index)
        candidates
        (mapv (fn [{:keys [source] :as policy}]
                (let [candidate (nth (:exotypes state) source)
                      cached (get @score-cache
                                  [candidate (:activity observation)
                                   (:diversity observation)])]
                  (merge policy cached
                         {:lambda lambda
                          :total (+ (:total cached) (* lambda (:conatus cached)))})))
              sources)
        decision {:index index :observation observation :candidates candidates
                  :winner (first (sort-by :total candidates))}
        hunger (double (get-in decision [:winner :prediction :hunger]))]
    (assoc decision
           :lambda lambda
           :selected-hunger hunger
           :next-lambda (next-lambda (:self-tuning-arm state) state index
                                    lambda hunger))))

(defn transmit [state]
  (let [decisions (mapv #(cell-decision state %)
                        (range (count (:exotypes state))))]
    {:exotypes (mapv #(get-in % [:winner :candidate-exotype]) decisions)
     :lambdas (mapv :next-lambda decisions)
     :decisions decisions}))

(defn- phenotype-step [genotype phenotype]
  (let [width (count genotype)]
    (apply str
           (map (fn [index]
                  (let [left (Character/digit
                              ^char (nth phenotype (mod (dec index) width)) 2)
                        self (Character/digit ^char (nth phenotype index) 2)
                        right (Character/digit
                               ^char (nth phenotype (mod (inc index) width)) 2)
                        rule (ca/bits-for (str (nth genotype index)))]
                    (nth rule (- 7 (+ (* 4 left) (* 2 self) right)))))
                (range width)))))

(defn- genotype-step [{:keys [genotype exotypes seed time]}]
  (let [width (count genotype)]
    (mapv (fn [index sigil exotype]
            ;; Must mirror grid/apply-exotype exactly: this is a cached
            ;; re-implementation of the same draw, and it carried its own copy of
            ;; the first-draw defect (TN-baldwin-reboot.md 2). ca/mix-seed here
            ;; is what keeps the two paths identical.
            (let [draw-seed (+ (long seed) (* (long time) width) index)
                  k (.nextInt (java.util.Random. (ca/mix-seed draw-seed)) 8)]
              (get-in @genotype-transition-cache [(str sigil) exotype k])))
          (range width) genotype exotypes)))

(defn step
  "Advance phenotype, genotype, exotype, and lambda fields synchronously."
  [state]
  (let [{:keys [exotypes lambdas decisions]} (transmit state)
        previous (:genotype state)
        advanced {:arm :efe-full
                  :seed (:seed state)
                  :time (inc (:time state))
                  :phenotype (phenotype-step previous (:phenotype state))
                  :genotype (genotype-step state)
                  :exotypes exotypes}]
    (assoc advanced
           :self-tuning-arm (:self-tuning-arm state)
           :previous-genotype previous
           :lambdas lambdas
           :lambda-step-size
           (double (get state :lambda-step-size default-step-size))
           :hunger-target
           (double (get state :hunger-target default-hunger-target))
           :efe-decisions decisions
           :self-tuning-decisions decisions)))

(defn run-steps [state steps]
  (nth (iterate step state) steps))
