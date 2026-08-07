(ns aif-engine-sensitivity
  "M-C of TN-aif-engine-fable-answer.md, plus the fine-grid crossing screen.

   Part 1 (fine screen): on on-policy states, P(h_win > target | lambda) at
   gamma = 16 over a FINE target grid 0.150..0.175 -- the atom band where any
   interior crossing would have to hide -- plus the per-cell lambda leverage
   |P_i(l=0) - P_i(l=1)|.

   Part 2 (authority): one-bit observation sensitivity as a function of
   (kappa, selection rule). For each captured cell: flip the LEFT neighbour's
   phenotype bit, recompute the candidate decomposition, and compare action
   distributions: argmin flip indicator vs softmax total-variation distance.
   kappa is re-applied analytically: total = base0 + lambda*conatus - kappa*x.

   Read-only over src/. Run:
     clojure -Sdeps '{:paths [\"src\" \"resources\"]}' -M analysis/aif_engine_sensitivity.clj"
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.policy-epistemic :as pe]
            [futon5.exotype.self-tuning :as tuning]))

(def design
  {:width 80
   :run-kappa 0.2
   :target 0.17
   :seeds [2026085300 2026085301 2026085302 2026085303]
   :checkpoints #{300 600}
   :max-t 600
   :lambda0 0.55
   :step-size 0.001
   :kappas [0.0 0.2 0.478 1.0]
   :gammas [4.0 16.0 64.0]
   :fine-targets (mapv #(+ 0.150 (* 0.0025 %)) (range 11))})

(defn- initial-state [seed]
  (ca/with-seed seed
    (let [w (:width design) g (vec (ca/random-sigil-string w))]
      {:arm :efe-full :seed seed :time 0 :hunger-target (:target design)
       :lambdas (vec (repeat w (:lambda0 design)))
       :genotype g :previous-genotype g
       :phenotype (ca/random-phenotype-string w)
       :exotypes (grid/initial-grid :heterogeneous-fixed w)
       :blend-action? true :blend-strength 0.0
       :epistemic-coefficient (:run-kappa design)
       :apply-probability 1.0 :self-tuning-arm :hunger-coupled
       :lambda-step-size (:step-size design)})))

(defn- capture-states [seed]
  (loop [s (initial-state seed) t 0 acc []]
    (let [acc' (if (contains? (:checkpoints design) t) (conj acc s) acc)]
      (if (= t (:max-t design)) acc'
          (recur (tuning/step s) (inc t) acc')))))

(defn- decomp
  "Per candidate: base0 (risk+ambiguity, kappa-free), conatus, hunger, x."
  [state index]
  (let [width (count (:exotypes state))
        obs (efe/local-observation state index)
        own (nth (:exotypes state) index)
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
                                       :epistemic-coefficient 0.0
                                       :epistemic-value 0.0})]
              {:policy policy :base0 (:total sc) :conatus (:conatus sc)
               :hunger (get-in sc [:prediction :hunger]) :x x}))
          sources)))

(defn- totals [cands l kappa]
  (mapv #(+ (:base0 %) (* (double l) (:conatus %)) (* (- (double kappa)) (:x %))) cands))

(defn- argmin-idx [ts]
  (reduce (fn [best i] (if (< (nth ts i) (nth ts best)) i best))
          0 (range 1 (count ts))))

(defn- probs [ts gamma]
  (let [m (apply min ts)
        ws (mapv #(Math/exp (* (- (double gamma)) (- % m))) ts)
        z (reduce + ws)]
    (mapv #(/ % z) ws)))

(defn- tv [p q] (* 0.5 (reduce + (map #(Math/abs (- ^double %1 ^double %2)) p q))))

(defn- flip-phenotype-bit [state index]
  (update state :phenotype
          #(apply str (update (vec %) index (fn [b] (if (= b \0) \1 \0))))))

(defn- mean [xs] (/ (reduce + 0.0 xs) (double (count xs))))

(defn -main []
  (let [states (vec (for [seed (:seeds design) s (capture-states seed)] s))
        cells (vec (for [s states i (range (:width design))]
                     {:state s :index i
                      :lambda (nth (:lambdas s) i)
                      :cands (decomp s i)}))]
    (println (format "captured %d states, %d cells (target %.2f run)"
                     (count states) (count cells) (:target design)))
    ;; ---- Part 1: fine crossing screen at gamma 16, run-kappa applied
    (println)
    (println "== fine-grid crossing screen (gamma 16, kappa 0.2, pooled) ==")
    (let [g 16.0
          p-at (fn [l tgt]
                 (mean (map (fn [{:keys [cands]}]
                              (let [ps (probs (totals cands l (:run-kappa design)) g)]
                                (reduce + (map (fn [p c] (if (> (:hunger c) tgt) p 0.0))
                                               ps cands))))
                            cells)))]
      (println "  target |  P(l=0) | P(l=0.5) |  P(l=1) | crossing?")
      (doseq [tgt (:fine-targets design)]
        (let [p0 (p-at 0.0 tgt) p5 (p-at 0.5 tgt) p1 (p-at 1.0 tgt)]
          (println (format "  %.4f |  %.4f |   %.4f |  %.4f | %s"
                           tgt p0 p5 p1
                           (cond (and (> p0 0.5) (< p1 0.5)) "CROSSES"
                                 (and (< p0 0.5) (> p1 0.5)) "crosses upward"
                                 :else "no")))))
      ;; per-cell lambda leverage at the run target
      (let [lev (map (fn [{:keys [cands]}]
                       (let [pv (fn [l]
                                  (let [ps (probs (totals cands l (:run-kappa design)) g)]
                                    (reduce + (map (fn [p c] (if (> (:hunger c) (:target design)) p 0.0))
                                                   ps cands))))]
                         (Math/abs (- (pv 0.0) (pv 1.0)))))
                     cells)]
        (println (format "  per-cell lambda leverage |P_i(0)-P_i(1)| at tgt %.2f: mean %.4f  p90 %.4f  max %.4f"
                         (:target design) (mean lev)
                         (nth (vec (sort lev)) (int (* 0.9 (count cells))))
                         (apply max lev)))))
    ;; ---- Part 2: authority (one-bit observation sensitivity)
    (println)
    (println "== one-bit observation sensitivity vs (kappa, selection) ==")
    (println "  perturbation: flip left neighbour's phenotype bit; lambda_i as captured.")
    (let [pert-cells
          (mapv (fn [{:keys [state index] :as cell}]
                  (let [w (:width design)
                        s' (flip-phenotype-bit state (mod (dec index) w))]
                    (assoc cell :cands' (decomp s' index))))
                cells)]
      (println "  kappa | argmin flip |   TV g=4 |  TV g=16 |  TV g=64")
      (doseq [kappa (:kappas design)]
        (let [rows (map (fn [{:keys [lambda cands cands']}]
                          (let [t1 (totals cands lambda kappa)
                                t2 (totals cands' lambda kappa)]
                            {:flip (if (not= (argmin-idx t1) (argmin-idx t2)) 1.0 0.0)
                             :tvs (mapv #(tv (probs t1 %) (probs t2 %)) (:gammas design))}))
                        pert-cells)]
          (println (format "  %.3f |      %.4f |   %.4f |   %.4f |   %.4f"
                           kappa (mean (map :flip rows))
                           (mean (map #(nth (:tvs %) 0) rows))
                           (mean (map #(nth (:tvs %) 1) rows))
                           (mean (map #(nth (:tvs %) 2) rows)))))))
    (shutdown-agents)))

(-main)
