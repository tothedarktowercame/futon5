(ns futon5.exotype.self-tuning
  "Per-cell lambda adaptation driven only by the selected policy's local hunger.

   The update never reads damage, reach, entropy, kind counts, or any global
   statistic. Lambda and exotype updates are synchronous."
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.chain-risk :as chain-risk]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.policy-epistemic :as policy-epistemic]))

(def arms [:hunger-coupled :random-walk :fixed-0.55 :fixed-0.40 :fixed-0.70])
(def default-step-size 0.001)
(def default-hunger-target (:hunger efe/preferences))

;; The long-horizon experiment evaluates the same finite local observation
;; alphabet hundreds of millions of times.  Cache only the frozen EFE model's
;; score decomposition; the per-cell lambda term remains computed at the use
;; site.  This changes neither the local information boundary nor the policy.
(def ^:private cache-shaping-options
  "Options that change what `efe/score-policy` returns, and are therefore baked
   into a cached value. `:lambda` is deliberately NOT here: the cache stores the
   lambda = 0 total plus the raw conatus, and lambda is reapplied per cell below,
   so it varies correctly through the cache.

   Any option in this set forces the slow path. Before this existed the cache was
   consulted unconditionally, so a state asking for `:observation-model :legacy`
   or an `:apply-probability` silently got the cached DEFAULT instead -- the
   trajectory driver ignored both while `efe/cell-decision` honoured them, which
   made every trajectory-level comparison of those options invalid
   (TN-baldwin-reboot.md 57, found by codex-7)."
  #{:observation-model :apply-probability :rule-change-preference :chain-risk?
    :epistemic-coefficient :adoption-bonus})

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

(def ^:private blend-stream-tag
  "Matches `grid/apply-exotype-blend`: the blend coin is independent of the
   propagator-position draw."
  0x0B1E4D)

(def ^:private apply-stream-tag
  "Separates the apply-probability coin from both the blend coin and the
   propagator-position draw."
  0x0A7711)

(def ^:private policy-stream-tag
  "Separates the softmax policy draw from propagator, blend, and apply draws."
  0x050F7A)

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

(defn- softmax-probabilities [candidates gamma]
  (when-not (and (Double/isFinite gamma) (not (neg? gamma)))
    (throw (ex-info "policy precision must be a finite non-negative number"
                    {:policy-precision gamma})))
  (let [logits (mapv #(- (* gamma (double (:total %)))) candidates)
        maximum (reduce max logits)
        weights (mapv #(Math/exp (- % maximum)) logits)
        denominator (reduce + 0.0 weights)]
    (mapv #(/ % denominator) weights)))

(defn- sample-policy [candidates probabilities draw]
  (loop [index 0 cumulative 0.0]
    (let [next-cumulative (+ cumulative (nth probabilities index))]
      (if (or (< draw next-cumulative)
              (= index (dec (count candidates))))
        (nth candidates index)
        (recur (inc index) next-cumulative)))))

(defn cell-decision [state index]
  (let [lambda (double (nth (:lambdas state) index))
        width (count (:exotypes state))
        sources (cond-> [{:policy :hold :source index}
                         {:policy :adopt-left :source (mod (dec index) width)}
                         {:policy :adopt-right :source (mod (inc index) width)}]
                  (:blend-action? state)
                  (conj {:policy :blend :source index}))
        observation (efe/local-observation state index)
        own-exotype (nth (:exotypes state) index)
        candidates
        (mapv (fn [{:keys [source policy] :as candidate-policy}]
                (let [candidate (nth (:exotypes state) source)
                      shaping (select-keys state cache-shaping-options)
                      blend? (= :blend policy)
                      risk-byte (when (:chain-risk? state)
                                  (chain-risk/byte-of
                                   (if blend?
                                     (grid/blend-rule
                                      (nth (:genotype state)
                                           (mod (dec index) width))
                                      (nth (:genotype state) index)
                                      (nth (:genotype state)
                                           (mod (inc index) width)))
                                     (nth (:genotype state) index))))
                      epistemic-x (if blend?
                                    (policy-epistemic/blend-value state index)
                                    (policy-epistemic/pair-value
                                     own-exotype candidate))
                      cached (if (or blend? (seq shaping))
                               ;; slow path: the state asks for something the cache
                               ;; was not built with, or this is the rule-writing
                               ;; action whose state-local value cannot be cached.
                               (efe/score-policy
                                :efe-full candidate observation
                                (cond-> (assoc shaping :lambda 0.0)
                                  (:chain-risk? state)
                                  (assoc :risk-value
                                         (chain-risk/risk candidate risk-byte))
                                  (or blend?
                                      (contains? state :epistemic-coefficient))
                                  (assoc :epistemic-value epistemic-x)
                                  (contains? state :adoption-bonus)
                                  (assoc :adoption?
                                         (and (not= source index)
                                              (not= candidate own-exotype)))))
                               (if-let [base (get @score-cache
                                                 [candidate (:activity observation)
                                                  (:diversity observation)])]
                                 base
                                 (throw (ex-info "exotype absent from the score cache"
                                                 {:exotype candidate
                                                  :cached-kinds grid/exotype-kinds}))))]
                  (merge candidate-policy cached
                         {:lambda lambda
                          :total (+ (:total cached) (* lambda (:conatus cached)))})))
              sources)
        precision (when (contains? state :policy-precision)
                    (double (:policy-precision state)))
        probabilities (when (some? precision)
                        (softmax-probabilities candidates precision))
        selection-seed (+ (long (or (:seed state) 0))
                          (* (long (or (:time state) 0)) width)
                          index)
        winner (if probabilities
                 (sample-policy
                  candidates probabilities
                  (ca/with-mixed-seed
                    (bit-xor selection-seed policy-stream-tag)
                    (ca/rnd)))
                 (first (sort-by :total candidates)))
        decision (cond-> {:index index :observation observation
                          :candidates candidates :winner winner}
                   probabilities
                   (assoc :policy-probabilities
                          (mapv (fn [candidate probability]
                                  {:policy (:policy candidate)
                                   :probability probability})
                                candidates probabilities)))
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

(defn- genotype-step
  ([state]
   (genotype-step state nil))
  ([{:keys [genotype exotypes seed time blend-strength apply-probability]}
    decisions]
   (let [width (count genotype)
         beta (double (or blend-strength 0.0))
         p (double (if (nil? apply-probability) 1.0 apply-probability))]
    (when-not (<= 0.0 beta 1.0)
      (throw (ex-info "blend strength must be in [0,1]"
                      {:blend-strength beta})))
    (when-not (<= 0.0 p 1.0)
      (throw (ex-info "apply probability must be in [0,1]"
                      {:apply-probability p})))
     (mapv (fn [index sigil exotype]
            ;; Must mirror grid/apply-exotype exactly: this is a cached
            ;; re-implementation of the same draw, and it carried its own copy of
            ;; the first-draw defect (TN-baldwin-reboot.md 2). ca/mix-seed here
            ;; is what keeps the two paths identical.
            (let [draw-seed (+ (long seed) (* (long time) width) index)
                  left (nth genotype (mod (dec index) width))
                  right (nth genotype (mod (inc index) width))
                  policy (some-> decisions (nth index) :winner :policy)
                  source (if (and (pos? beta)
                                  (ca/with-mixed-seed
                                    (bit-xor (long draw-seed) blend-stream-tag)
                                    (< (ca/rnd) beta)))
                           (grid/blend-rule left sigil right)
                           sigil)
                  apply? (or (== 1.0 p)
                             (and (pos? p)
                                  (ca/with-mixed-seed
                                    (bit-xor (long draw-seed) apply-stream-tag)
                                    (< (ca/rnd) p))))]
              (if (= :blend policy)
                (grid/blend-rule left sigil right)
                (if apply?
                  (let [k (.nextInt (java.util.Random. (ca/mix-seed draw-seed)) 8)]
                    (get-in @genotype-transition-cache [(str source) exotype k]))
                  sigil))))
           (range width) genotype exotypes))))

(defn step
  "Advance phenotype, genotype, exotype, and lambda fields synchronously."
  [state]
  (let [{:keys [exotypes lambdas decisions]} (transmit state)
        previous (:genotype state)
        advanced {:arm :efe-full
                  :seed (:seed state)
                  :time (inc (:time state))
                  :phenotype (phenotype-step previous (:phenotype state))
                  :genotype (genotype-step state decisions)
                  :exotypes exotypes}]
    (cond-> (assoc advanced
                   :self-tuning-arm (:self-tuning-arm state)
                   :previous-genotype previous
                   :lambdas lambdas
                   :lambda-step-size
                   (double (get state :lambda-step-size default-step-size))
                   :hunger-target
                   (double (get state :hunger-target default-hunger-target))
                   :efe-decisions decisions
                   :self-tuning-decisions decisions)
      ;; Score-shaping controls are trajectory state. Dropping one here silently
      ;; turns it off after the first step; absent controls remain absent so the
      ;; legacy pr-str surface is byte-identical.
      (contains? state :observation-model)
      (assoc :observation-model (:observation-model state))
      (contains? state :apply-probability)
      (assoc :apply-probability (double (:apply-probability state)))
      (contains? state :blend-strength)
      (assoc :blend-strength (double (:blend-strength state)))
      (contains? state :blend-action?)
      (assoc :blend-action? (boolean (:blend-action? state)))
      (contains? state :policy-precision)
      (assoc :policy-precision (double (:policy-precision state)))
      (contains? state :chain-risk?)
      (assoc :chain-risk? (boolean (:chain-risk? state)))
      (contains? state :rule-change-preference)
      (assoc :rule-change-preference (double (:rule-change-preference state)))
      (contains? state :epistemic-coefficient)
      (assoc :epistemic-coefficient (double (:epistemic-coefficient state)))
      (contains? state :adoption-bonus)
      (assoc :adoption-bonus (double (:adoption-bonus state))))))

(defn run-steps [state steps]
  (nth (iterate step state) steps))
