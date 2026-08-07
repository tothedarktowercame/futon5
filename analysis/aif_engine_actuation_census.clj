(ns aif-engine-actuation-census
  "M-A + M-D of TN-aif-engine-fable-answer.md (2026-08-05).

   On states sampled from the closed loop itself (target-sweep configuration),
   compute per cell the decision layer's EXACT response to lambda_i:

     candidate totals are lines  total_c(l) = base_c + l * conatus_c,
     so the argmin winner is piecewise-constant in l (lower envelope).

   Census items:
     1. flip fraction     -- does the argmin change anywhere in l in [0,1]?
     2. actuation range   -- max-min of winner-hunger over l (what a sign
                             controller could possibly feel through lambda)
     3. candidate spread  -- max-min of h_c across candidates (sensor
                             discrimination: mechanism (a) vs (b))
     4. softmax census    -- E[h](l) and P(h_win > target | l) exactly, per
                             gamma; the 1/2-crossing predicts interior
                             attractors in advance
     5. depth-2 argmin    -- mean-field rollout; does depth restore lambda
                             gain? (prediction: no -- still piecewise-constant)

   Read-only over src/. Run:
     clojure -Sdeps '{:paths [\"src\" \"resources\"]}' -M analysis/aif_engine_actuation_census.clj"
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.policy-epistemic :as pe]
            [futon5.exotype.self-tuning :as tuning]))

(def design
  {:width 80
   :kappa 0.2
   :targets [0.05 0.17 0.25]
   :seeds [2026085300 2026085301 2026085302 2026085303]
   :checkpoints #{100 300 600}
   :max-t 600
   :lambda0 0.55
   :step-size 0.001
   :gammas [1.0 4.0 16.0 64.0]
   :lambda-grid (mapv #(/ % 100.0) (range 0 101 2))   ; 51 pts for envelope
   :soft-grid (mapv #(/ % 10.0) (range 0 11))          ; 11 pts for softmax
   :target-grid (mapv #(/ % 100.0) (range 10 26))})    ; 0.10..0.25

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

(defn- capture-states [target seed]
  (loop [s (initial-state target seed) t 0 acc []]
    (let [acc' (if (contains? (:checkpoints design) t) (conj acc s) acc)]
      (if (= t (:max-t design)) acc'
          (recur (tuning/step s) (inc t) acc')))))

;; ---- candidate line decomposition (mirrors tuning/cell-decision slow path)

(defn- candidate-lines [state index]
  (let [width (count (:exotypes state))
        obs (efe/local-observation state index)
        own (nth (:exotypes state) index)
        sources [{:policy :hold :source index}
                 {:policy :adopt-left :source (mod (dec index) width)}
                 {:policy :adopt-right :source (mod (inc index) width)}
                 {:policy :blend :source index}]]
    {:observation obs
     :own own
     :lambda (nth (:lambdas state) index)
     :cands
     (mapv (fn [{:keys [policy source]}]
             (let [cand (nth (:exotypes state) source)
                   x (if (= :blend policy)
                       (pe/blend-value state index)
                       (pe/pair-value own cand))
                   sc (efe/score-policy :efe-full cand obs
                                        {:lambda 0.0
                                         :epistemic-coefficient (:kappa design)
                                         :epistemic-value x})]
               {:policy policy :kind cand
                :base (:total sc) :conatus (:conatus sc)
                :hunger (get-in sc [:prediction :hunger])
                :pred (:prediction sc)}))
           sources)}))

(defn- argmin-idx
  "First strictly-minimal index; ties keep the earlier candidate, matching the
   stable sort-by in the engine."
  [totals]
  (reduce (fn [best i] (if (< (nth totals i) (nth totals best)) i best))
          0 (range 1 (count totals))))

(defn- winner-at [cands l]
  (argmin-idx (mapv #(+ (:base %) (* l (:conatus %))) cands)))

;; ---- depth-2 mean-field rollout (argmin at both steps; neighbours frozen)

(defn- depth2-lines [state index {:keys [cands]}]
  (let [width (count (:exotypes state))
        left (nth (:exotypes state) (mod (dec index) width))
        right (nth (:exotypes state) (mod (inc index) width))]
    (mapv (fn [{:keys [kind pred] :as c}]
            (let [obs2 {:activity (:activity pred) :diversity (:diversity pred)}
                  next-kinds [kind left right]      ; hold / adopt-l / adopt-r
                  nexts (mapv (fn [k2]
                                (let [sc (efe/score-policy
                                          :efe-full k2 obs2
                                          {:lambda 0.0
                                           :epistemic-coefficient (:kappa design)
                                           :epistemic-value (pe/pair-value kind k2)})]
                                  {:base (:total sc) :conatus (:conatus sc)}))
                              next-kinds)]
              (assoc c :nexts nexts)))
          cands)))

(defn- depth2-winner-at [d2cands l]
  (argmin-idx
   (mapv (fn [{:keys [base conatus nexts]}]
           (+ base (* l conatus)
              (apply min (map #(+ (:base %) (* l (:conatus %))) nexts))))
         d2cands)))

;; ---- per-cell census

(defn- softmax-probs [cands l gamma]
  (let [ts (mapv #(+ (:base %) (* l (:conatus %))) cands)
        m (apply min ts)
        ws (mapv #(Math/exp (* (- gamma) (- % m))) ts)
        z (reduce + ws)]
    (mapv #(/ % z) ws)))

(defn- cell-census [state index]
  (let [{:keys [cands lambda] :as row} (candidate-lines state index)
        grid-l (:lambda-grid design)
        winners (mapv #(winner-at cands %) grid-l)
        hs (mapv #(:hunger (nth cands %)) winners)
        actual-w (winner-at cands lambda)
        totals-at (mapv #(+ (:base %) (* (double lambda) (:conatus %))) cands)
        sorted-tot (sort totals-at)
        d2 (depth2-lines state index row)
        d2-winners (mapv #(depth2-winner-at d2 %) grid-l)
        d2-hs (mapv #(:hunger (nth cands %)) d2-winners)]
    {:flip? (> (count (distinct winners)) 1)
     :h-range (- (apply max hs) (apply min hs))
     :cand-spread (let [ch (map :hunger cands)]
                    (- (apply max ch) (apply min ch)))
     :winner-h (:hunger (nth cands actual-w))
     :gap (- (second sorted-tot) (first sorted-tot))
     :d2-flip? (> (count (distinct d2-winners)) 1)
     :d2-h-range (- (apply max d2-hs) (apply min d2-hs))
     :d2-differs? (not= (depth2-winner-at d2 lambda) actual-w)
     ;; softmax census: per gamma, E[h] and the indicator masses on the coarse grid
     :soft (into {}
                 (for [g (:gammas design)]
                   [g (mapv (fn [l]
                              (let [ps (softmax-probs cands l g)
                                    eh (reduce + (map * ps (map :hunger cands)))
                                    p-over (fn [tgt]
                                             (reduce + (map (fn [p c] (if (> (:hunger c) tgt) p 0.0))
                                                            ps cands)))]
                                {:l l :eh eh
                                 :p-over (mapv p-over (:target-grid design))}))
                            (:soft-grid design))]))}))

;; ---- aggregation

(defn- mean [xs] (/ (reduce + 0.0 xs) (double (count xs))))
(defn- sd [xs] (let [m (mean xs)] (Math/sqrt (mean (map #(let [d (- % m)] (* d d)) xs)))))
(defn- frac [xs] (/ (double (count (filter identity xs))) (double (count xs))))

(defn -main []
  (let [rows (vec
              (for [target (:targets design)
                    seed (:seeds design)
                    state (capture-states target seed)
                    index (range (:width design))]
                (assoc (cell-census state index)
                       :target target :seed seed :time (:time state))))
        n (count rows)]
    (println (format "census rows: %d  (targets %s, %d seeds, checkpoints %s)"
                     n (pr-str (:targets design)) (count (:seeds design))
                     (pr-str (sort (:checkpoints design)))))
    (println)
    (println "== 1-3. argmin envelope census (pooled over all captured states) ==")
    (println (format "  flip fraction (argmin changes somewhere in lambda [0,1]): %.4f" (frac (map :flip? rows))))
    (println (format "  actuation range of winner-hunger over lambda: mean %.5f  sd %.5f  p90 %.5f  max %.5f"
                     (mean (map :h-range rows)) (sd (map :h-range rows))
                     (nth (vec (sort (map :h-range rows))) (int (* 0.9 n)))
                     (apply max (map :h-range rows))))
    (println (format "  conditional on flip: mean actuation range %.5f  (n=%d)"
                     (let [f (filter :flip? rows)] (if (seq f) (mean (map :h-range f)) 0.0))
                     (count (filter :flip? rows))))
    (println (format "  candidate hunger spread across candidates: mean %.5f  sd %.5f  frac > 0.01: %.4f"
                     (mean (map :cand-spread rows)) (sd (map :cand-spread rows))
                     (frac (map #(> (:cand-spread %) 0.01) rows))))
    (println (format "  winner hunger at actual lambda: mean %.5f  sd %.5f" (mean (map :winner-h rows)) (sd (map :winner-h rows))))
    (println (format "  decision gap at actual lambda: median %.5f  mean %.5f"
                     (nth (vec (sort (map :gap rows))) (quot n 2)) (mean (map :gap rows))))
    (doseq [target (:targets design)]
      (let [sub (filter #(= target (:target %)) rows)]
        (println (format "    [target %.2f] winner-h mean %.5f  flip-frac %.4f  h-range mean %.5f"
                         target (mean (map :winner-h sub)) (frac (map :flip? sub)) (mean (map :h-range sub))))))
    (println)
    (println "== 5. depth-2 mean-field rollout under argmin (M-D) ==")
    (println (format "  depth-2 winner differs from depth-1 at actual lambda: %.4f of cells" (frac (map :d2-differs? rows))))
    (println (format "  depth-2 flip fraction over lambda: %.4f   (depth-1: %.4f)"
                     (frac (map :d2-flip? rows)) (frac (map :flip? rows))))
    (println (format "  depth-2 actuation range: mean %.5f   (depth-1: %.5f)"
                     (mean (map :d2-h-range rows)) (mean (map :h-range rows))))
    (println)
    (println "== 4. softmax census: E[h](lambda), pooled ==")
    (println "  gamma |  E[h] l=0 |  E[h] l=0.5 |  E[h] l=1 |  range")
    (doseq [g (:gammas design)]
      (let [eh-at (fn [i] (mean (map #(get-in % [:soft g i :eh]) rows)))
            e0 (eh-at 0) e5 (eh-at 5) e10 (eh-at 10)]
        (println (format "  %5.0f |   %.5f |     %.5f |   %.5f |  %+.5f" g e0 e5 e10 (- e10 e0)))))
    (println)
    (println "== 4b. P(h_win > target | lambda) crossings (interior-attractor screen) ==")
    (println "  For each gamma: targets whose pooled P crosses 1/2 inside lambda (0,1),")
    (println "  with the interpolated crossing lambda* (P decreasing in lambda).")
    (doseq [g (:gammas design)]
      (let [p-curve (fn [ti] (mapv (fn [i] (mean (map #(get-in % [:soft g i :p-over ti]) rows)))
                                   (range (count (:soft-grid design)))))
            findings
            (for [ti (range (count (:target-grid design)))
                  :let [tgt (nth (:target-grid design) ti)
                        ps (p-curve ti)
                        p0 (first ps) p1 (last ps)]]
              (cond
                (and (> p0 0.5) (< p1 0.5))
                (let [i (first (filter #(< (nth ps %) 0.5) (range (count ps))))
                      la (nth (:soft-grid design) (dec i)) lb (nth (:soft-grid design) i)
                      pa (nth ps (dec i)) pb (nth ps i)
                      l* (+ la (* (- lb la) (/ (- pa 0.5) (- pa pb))))]
                  (format "    tgt %.2f: CROSSES, lambda* ~ %.3f  (P: %.3f -> %.3f)" tgt l* p0 p1))
                (and (< p0 0.5) (> p1 0.5))
                (format "    tgt %.2f: crosses UPWARD (unexpected sign)  (P: %.3f -> %.3f)" tgt p0 p1)
                :else
                (format "    tgt %.2f: no crossing  (P: %.3f -> %.3f)" tgt p0 p1)))]
        (println (format "  gamma %.0f:" g))
        (doseq [f findings] (println f))))
    (shutdown-agents)))

(-main)
