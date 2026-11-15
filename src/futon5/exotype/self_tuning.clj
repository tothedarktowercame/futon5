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

;; P-008: S8 exotypes bypass the 12-kind caches. Both functions below compute
;; exactly what the caches would hold, on demand, memoised per sigma.
(def ^:private s8-score-memo
  (memoize
   (fn [candidate activity diversity]
     (efe/score-policy :efe-full candidate
                       {:activity activity :diversity diversity}
                       {:lambda 0.0}))))

(defn- s8-score
  "P-008b §7 item 3: guard the exotype argument before the memo lookup."
  [candidate activity diversity]
  (grid/assert-exotype-allowed! candidate)
  (s8-score-memo candidate activity diversity))

(def ^:private s8-transition-memo
  (memoize
   (fn [sigil exotype k]
     (let [truth-table (vec ca/truth-table-3)
           sigma (grid/sigma-of exotype)
           bits (ca/bits-for sigil)
           src (nth truth-table k)
           dst (get sigma src src)
           di (.indexOf ^java.util.List truth-table dst)
           value (if (= \0 (nth bits k)) \1 \0)]
       (ca/sigil-for (str (subs bits 0 di) value (subs bits (inc di))))))))

(defn- s8-transition
  "P-008b §7 item 3: guard the exotype argument before the memo lookup."
  [sigil exotype k]
  (grid/assert-exotype-allowed! exotype)
  (s8-transition-memo sigil exotype k))

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

;; --- P-008b §7 item 5: V1 mutation, step 5b-A (hit schedule only) -----------
;; Rulings 2026-11-15 (lon-claude-4, on the plan in results/p008/
;; e8-mutation-plan.txt): the mutation stream is keyed
;;   mix-seed(mix-seed(seed XOR mutation-stream-tag) + t*width + i)
;; NOT seed + t*width + i: the run seed is MIXED before the coordinates are
;; added, so consecutive seeds get unrelated mutation schedules. The older
;; keyed streams (policy, blend, apply, propagator-k) keep seed + t*width + i
;; -- for consecutive seeds their noise fields are shifted copies (seed s+1
;; at (t,i) = seed s at (t,i+1)), a known property of the SHEET reference;
;; changing them would break G1 and every nesting hash.

(def ^:private mutation-stream-tag
  "Separates the mutation-rate draw from the policy, blend, apply, and
  propagator-position streams."
  0x0E8E8E)

(defn mutation-draw-seed
  "P-008b §7 item 5 (ruling 2026-11-15): mutation stream key. The seed is
  mixed with the tag BEFORE the coordinates are added (unchecked arithmetic),
  so consecutive run seeds give unrelated mutation schedules -- unlike the
  existing streams, where seed s+1 at (t, i) repeats seed s at (t, i+1)."
  [seed time width index]
  (ca/mix-seed
   (unchecked-add
    (ca/mix-seed (bit-xor (long (or seed 0)) mutation-stream-tag))
    (unchecked-add (unchecked-multiply (long (or time 0)) (long width))
                   (long index)))))

(defn apply-transposition
  "P-008b §7 item 5, step 5b-B: p′ = τ∘p for τ = (a b). In the futon5 vector
  p[i] is the IMAGE of neighbourhood i, so composing on the left by τ swaps
  the VALUES a and b wherever they occur: p′[i] = τ(p[i]). This is NOT a
  swap of the entries at positions a and b (pinned by a test). Defined on S8
  only: a non-permutation p throws."
  [p a b]
  (when-not (grid/perm? p)
    (throw (ex-info ":s8-transposition mutation is defined on S8 only"
                    {:exotype p})))
  (mapv (fn [v] (cond (= v a) b (= v b) a :else v)) p))

(defn apply-point
  "P-008b §7 item 5, step 5b-B: f(k) := j. p′ = p with p′[k] = j; j may
  equal p[k] (a no-op hit, probability 1/8) -- the caller records it as a
  hit all the same. Any integer vector p is accepted; the result may be
  non-injective (End(8)), allowed only under grid/*allow-end8?*."
  [p k j]
  (assoc p k j))

(defn check-mutation!
  "Fail-closed validation of the mutation config, called at the top of
  transmit BEFORE any decision is computed. An unknown :mutation value
  throws; :e8-point throws when grid/*allow-end8?* is not bound true (the
  End(8) config flag), because its results are non-injective vectors that
  only the flag-on guards accept."
  [mutation]
  (when (some? mutation)
    (case mutation
      :s8-transposition nil
      :e8-point (when-not grid/*allow-end8?*
                  (throw (ex-info
                          ":e8-point mutation requires End(8): bind grid/*allow-end8?* true (config :allow-end8? true)"
                          {:mutation mutation :allow-end8? grid/*allow-end8?*})))
      (throw (ex-info "unknown :mutation value"
                      {:mutation mutation
                       :available [:s8-transposition :e8-point]})))))

(defn mutation-draw
  "All mutation draws for cell INDEX inside ONE ca/with-seed scope on the
  mutation stream key (step 5b-B). First the rate draw, a single
  (ca/rnd-int 10000) exactly as in step 5b-A -- the hit schedule is
  unchanged; hit iff the draw is below round(10000*mu). On a hit the
  operator's draws follow in the SAME scope: :s8-transposition draws
  a = (rnd-int 8), b0 = (rnd-int 7), b = b0+1 if b0 >= a else b0 (a
  uniform ordered pair of distinct values, i.e. a uniform transposition);
  :e8-point draws k = (rnd-int 8), j = (rnd-int 8). On no hit, no further
  draws.

  P-008b §7 item 6 (ruling 2026-11-15, exotype main 5176228): on a hit, a
  winner that is a keyword kind is replaced by its S8 twin (grid/kind->s8)
  and the operator is applied to THAT vector. The record carries :pre (the
  keyword, so the readout can count kind->vector conversions),
  :pre-vector (the twin), :post (the mutated twin) and :op as for vectors.
  The fallback from the ruling: a keyword with no entry in
  grid/propagators, or whose twin fails the map-equality predicate
  ((grid/sigma-of kind) = (grid/sigma-of twin); a non-permutation twin
  fails outright), is left as it is and recorded
  {:pre kind :post kind :op {:kind :skipped-keyword}}; the rate draw still
  happened. Returns nil on no hit, else {:pre :pre-vector? :post :op}.
  Throws if 10000*mu is not integral (within 1e-9), or if :mu is missing
  while mutation is configured."
  [state index exotype]
  (when-not (contains? state :mu)
    (throw (ex-info "mutation configured but :mu is missing" {:state-keys (keys state)})))
  (let [mu (double (:mu state))
        scaled (* 10000.0 mu)
        threshold (Math/round scaled)]
    (when-not (< (Math/abs (- scaled threshold)) 1e-9)
      (throw (ex-info "mutation mu must make 10000*mu an integer"
                      {:mu mu :scaled scaled})))
    (ca/with-seed (mutation-draw-seed (:seed state) (:time state)
                                      (count (:exotypes state)) index)
      (when (< (ca/rnd-int 10000) threshold)
        (letfn [(operator [v]
                  (case (:mutation state)
                    :s8-transposition
                    (let [a (ca/rnd-int 8)
                          b0 (ca/rnd-int 7)
                          b (if (>= b0 a) (inc b0) b0)]
                      {:post (apply-transposition v a b)
                       :op {:kind :s8-transposition :a a :b b}})
                    :e8-point
                    (let [k (ca/rnd-int 8)
                          j (ca/rnd-int 8)]
                      {:post (apply-point v k j)
                       :op {:kind :e8-point :k k :j j}})))]
          (if (vector? exotype)
            (merge {:pre exotype} (operator exotype))
            (let [twin (when (contains? grid/propagators exotype)
                         (grid/kind->s8 exotype))]
              (if (and twin
                       (grid/perm? twin)
                       (= (grid/sigma-of exotype) (grid/sigma-of twin)))
                (merge {:pre exotype :pre-vector twin} (operator twin))
                {:pre exotype :post exotype
                 :op {:kind :skipped-keyword}}))))))))

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
                               (if-let [base (if (grid/s8? candidate)
                                               (s8-score candidate (:activity observation)
                                                         (:diversity observation))
                                               (get @score-cache
                                                    [candidate (:activity observation)
                                                     (:diversity observation)]))]
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
  (let [mutation (:mutation state)
        ;; P-008b §7 item 5, step 5b-B: fail-closed config check BEFORE any
        ;; decision is computed -- unknown :mutation values, and :e8-point
        ;; without grid/*allow-end8?*, throw at the first transmit call.
        _ (check-mutation! mutation)
        decisions (mapv #(cell-decision state %)
                        (range (count (:exotypes state))))
        ;; P-008b §7 item 5: when mutation is configured, draw the per-cell
        ;; mutation AFTER the winner is chosen, inside one keyed scope
        ;; (mutation-draw). A hit replaces that cell's next exotype with
        ;; :post and is recorded on the decision as {:pre :post :op}. The
        ;; winner entry in the decision is left as it is. When :mutation is
        ;; absent or nil, no draw happens and no key is added, so the output
        ;; is identical to today.
        decisions (if (nil? mutation)
                    decisions
                    (mapv (fn [decision]
                            ;; P-008b §7 item 6: :row-source records which model
                            ;; row the WINNER's acting exotype was scored from --
                            ;; :s8-predictor when it is a vector (routes through
                            ;; efe/s8-predictor, fail-closed), :keyword-cache
                            ;; when a keyword (conditional keyword bins). Computed
                            ;; from the winner's own type; efe.clj untouched.
                            ;; Added only when mutation is configured, so the
                            ;; mutation-off pr-str surface is unchanged.
                            (let [winner (get-in decision [:winner :candidate-exotype])
                                  hit (mutation-draw state (:index decision) winner)]
                              (cond-> (assoc decision
                                             :row-source (if (grid/s8? winner)
                                                           :s8-predictor
                                                           :keyword-cache))
                                hit (assoc :mutation-hit hit))))
                          decisions))]
    {:exotypes (mapv (fn [decision]
                       (if-let [hit (:mutation-hit decision)]
                         (:post hit)
                         (get-in decision [:winner :candidate-exotype])))
                     decisions)
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
                    (if (grid/s8? exotype)
                      (s8-transition (str source) exotype k)
                      (get-in @genotype-transition-cache [(str source) exotype k])))
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
      (assoc :adoption-bonus (double (:adoption-bonus state)))
      ;; P-008b §7 item 5, step 5b-A: mutation config is trajectory state in
      ;; the same pattern -- dropped here would silently turn it off after the
      ;; first step. :mu is passed through verbatim (not doubled): it is a
      ;; rate, and mutation-hit? validates it.
      (contains? state :mutation)
      (assoc :mutation (:mutation state))
      (contains? state :mu)
      (assoc :mu (:mu state)))))

(defn run-steps [state steps]
  (nth (iterate step state) steps))
