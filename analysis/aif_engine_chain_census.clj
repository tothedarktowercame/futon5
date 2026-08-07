(ns aif-engine-chain-census
  "M-E of TN-aif-engine-fable-answer.md: manifold-depth R13 in closed form.

   The rule layer under a fixed sigma is a Markov chain on 256 bytes (8 equally
   likely successors: draw k, write NOT b[k] at position A(k)). There is no
   deterministic orbit (merges + self-loops), but every expectation along the
   playout is computable exactly by 256-vector DP and cacheable per kind:

     e_t(sigma, b) = E[ q(b_t) | b_0 = b ],  e_0 = q,  e_t = M_sigma e_{t-1}.

   Chain-scored risk (the functional respects 'carried along, not choosing'):
     R_chain(sigma, b) = sum_t w_t * E[ KL( p_change(b_t) || 0.15 ) ],
   discounted w_t ~ 0.7^t, H = 12, normalised. This makes the risk channel
   byte-conditional (today it uses the byte-AVERAGED rate) and prices the blend
   action from the blended byte.

   Census on on-policy states: within-cell spread of chain-risk vs one-step
   risk, argmin change fraction, and the lambda flip fraction / actuation range
   under chain-scored base_c (P-F: essentially unchanged from depth-1).

   Read-only over src/. Run:
     clojure -Sdeps '{:paths [\"src\" \"resources\"]}' -M analysis/aif_engine_chain_census.clj"
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.policy-epistemic :as pe]
            [futon5.exotype.self-tuning :as tuning]
            [futon5.xenotype.generator :as gen]))

(def design
  {:width 80
   :kappa 0.2
   :targets [0.05 0.17 0.25]
   :seeds [2026085300 2026085301 2026085302 2026085303]
   :checkpoint 300
   :lambda0 0.55
   :step-size 0.001
   :horizon 12
   :discount 0.7
   :risk-target 0.15
   :lambda-grid (mapv #(/ % 100.0) (range 0 101 2))})

;; ---- exact chain DP per kind --------------------------------------------

(defn- positional [kind] (gen/sigma-positional (get grid/propagators kind)))

(defn- succ ^long [a ^long b ^long k]
  (let [bit (bit-and (bit-shift-right b k) 1)
        dst (long (nth a k))
        nb (if (zero? bit) 1 0)]
    (-> b (bit-and (bit-not (bit-shift-left 1 dst))) (bit-or (bit-shift-left nb dst)))))

(def ^:private eps 1.0e-9)
(defn- kl [q p]
  (let [q (-> (double q) (max eps) (min (- 1.0 eps)))
        p (-> (double p) (max eps) (min (- 1.0 eps)))]
    (+ (* q (Math/log (/ q p)))
       (* (- 1.0 q) (Math/log (/ (- 1.0 q) (- 1.0 p)))))))

(defn- chain-risk-table
  "256-vector of discounted expected per-step KL(p_change(b_t) || risk-target)."
  [kind]
  (let [a (positional kind)
        succs (vec (for [b (range 256)] (mapv #(succ a b %) (range 8))))
        p-change (mapv (fn [b] (/ (count (filter #(not= b (nth (nth succs b) %))
                                                 (range 8))) 8.0))
                       (range 256))
        v0 (mapv #(kl % (:risk-target design)) p-change)
        m-apply (fn [v] (mapv (fn [b] (/ (reduce + (map v (nth succs b))) 8.0))
                              (range 256)))
        vs (take (:horizon design) (iterate m-apply v0))
        ws (mapv #(Math/pow (:discount design) %) (range (:horizon design)))
        z (reduce + ws)]
    (mapv (fn [b] (/ (reduce + (map (fn [w v] (* w (nth v b))) ws vs)) z))
          (range 256))))

(def chain-risk (into {} (for [k grid/exotype-kinds] [k (chain-risk-table k)])))

;; ---- byte of a genotype sigil -------------------------------------------

(defn- byte-of [sigil]
  (let [bits (ca/bits-for (str sigil))]
    (reduce (fn [acc k] (if (= \1 (nth bits k)) (bit-or acc (bit-shift-left 1 k)) acc))
            0 (range 8))))

;; ---- on-policy capture (target-sweep configuration) ---------------------

(defn- initial-state [target seed]
  (ca/with-seed seed
    (let [w (:width design) g (vec (ca/random-sigil-string w))]
      {:arm :efe-full :seed seed :time 0 :hunger-target target
       :lambdas (vec (repeat w (:lambda0 design)))
       :genotype g :previous-genotype g
       :phenotype (ca/random-phenotype-string w)
       :exotypes (grid/initial-grid :heterogeneous-fixed w)
       :blend-action? true :blend-strength 0.0
       :epistemic-coefficient (:kappa design)
       :apply-probability 1.0 :self-tuning-arm :hunger-coupled
       :lambda-step-size (:step-size design)})))

(defn- capture-state [target seed]
  (nth (iterate tuning/step (initial-state target seed)) (:checkpoint design)))

;; ---- per-cell census -----------------------------------------------------

(defn- cell-rows [state index]
  (let [width (count (:exotypes state))
        obs (efe/local-observation state index)
        own (nth (:exotypes state) index)
        own-byte (byte-of (nth (:genotype state) index))
        blended-byte (byte-of (grid/blend-rule
                               (nth (:genotype state) (mod (dec index) width))
                               (nth (:genotype state) index)
                               (nth (:genotype state) (mod (inc index) width))))
        sources [{:policy :hold :source index}
                 {:policy :adopt-left :source (mod (dec index) width)}
                 {:policy :adopt-right :source (mod (inc index) width)}
                 {:policy :blend :source index}]]
    (mapv (fn [{:keys [policy source]}]
            (let [cand (nth (:exotypes state) source)
                  x (if (= :blend policy)
                        (pe/blend-value state index)
                        (pe/pair-value own cand))
                  sc (efe/score-policy :efe-full cand obs
                                      {:lambda 0.0
                                       :epistemic-coefficient (:kappa design)
                                       :epistemic-value x})
                  b (if (= :blend policy) blended-byte own-byte)
                  r-chain (nth (get chain-risk cand) b)]
              {:policy policy
               :base (:total sc) :conatus (:conatus sc)
               :hunger (get-in sc [:prediction :hunger])
               :risk1 (:risk sc)
               :risk-chain r-chain
               ;; chain-scored base: swap the one-step risk for the chain risk
               :base-chain (+ (- (:total sc) (:risk sc)) r-chain)}))
          sources)))

(defn- argmin-idx [ts]
  (reduce (fn [best i] (if (< (nth ts i) (nth ts best)) i best))
          0 (range 1 (count ts))))

(defn- winner-at [cands base-key l]
  (argmin-idx (mapv #(+ (get % base-key) (* (double l) (:conatus %))) cands)))

(defn- mean [xs] (/ (reduce + 0.0 xs) (double (count xs))))
(defn- frac [xs] (/ (double (count (filter identity xs))) (double (count xs))))
(defn- spread [xs] (- (apply max xs) (apply min xs)))

(defn -main []
  ;; chain facts per kind
  (println "== chain-risk table facts (exact DP, H=12, discount 0.7, 256 bytes/kind) ==")
  (println "  kind        mean    sd-over-bytes   min      max")
  (doseq [k (sort grid/exotype-kinds)]
    (let [v (get chain-risk k)
          m (mean v)
          s (Math/sqrt (mean (map #(let [d (- % m)] (* d d)) v)))]
      (println (format "  %-10s %.4f   %.4f         %.4f   %.4f"
                       (name k) m s (apply min v) (apply max v)))))
  (println)
  (let [rows (vec (for [target (:targets design)
                        seed (:seeds design)
                        :let [state (capture-state target seed)]
                        index (range (:width design))]
                    (let [cands (cell-rows state index)
                          l (nth (:lambdas state) index)
                          grid-l (:lambda-grid design)
                          w1 (mapv #(winner-at cands :base %) grid-l)
                          wc (mapv #(winner-at cands :base-chain %) grid-l)
                          h1 (mapv #(:hunger (nth cands %)) w1)
                          hc (mapv #(:hunger (nth cands %)) wc)]
                      {:risk1-spread (spread (map :risk1 cands))
                       :chain-spread (spread (map :risk-chain cands))
                       :blend-hold-gap-1 (Math/abs (- (:risk1 (nth cands 3))
                                                      (:risk1 (nth cands 0))))
                       :blend-hold-gap-chain (Math/abs (- (:risk-chain (nth cands 3))
                                                          (:risk-chain (nth cands 0))))
                       :changed? (not= (winner-at cands :base l)
                                       (winner-at cands :base-chain l))
                       :flip1? (> (count (distinct w1)) 1)
                       :flipc? (> (count (distinct wc)) 1)
                       :h-range-1 (spread h1)
                       :h-range-c (spread hc)})))
        n (count rows)]
    (println (format "== on-policy census, %d cells (targets %s, t=%d) =="
                     n (pr-str (:targets design)) (:checkpoint design)))
    (println (format "  within-cell risk spread: one-step %.5f -> chain %.5f (mean)"
                     (mean (map :risk1-spread rows)) (mean (map :chain-spread rows))))
    (println (format "  blend-vs-hold risk gap:  one-step %.6f -> chain %.5f (mean; one-step is 0 by construction)"
                     (mean (map :blend-hold-gap-1 rows)) (mean (map :blend-hold-gap-chain rows))))
    (println (format "  argmin changed by chain-scoring at actual lambda: %.4f of cells"
                     (frac (map :changed? rows))))
    (println (format "  lambda flip fraction:  one-step %.4f -> chain %.4f"
                     (frac (map :flip1? rows)) (frac (map :flipc? rows))))
    (println (format "  lambda actuation range of winner-hunger: one-step %.5f -> chain %.5f"
                     (mean (map :h-range-1 rows)) (mean (map :h-range-c rows)))))
  (shutdown-agents))

(-main)
