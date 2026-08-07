(ns aif-engine-softmax-loop
  "M-B of TN-aif-engine-fable-answer.md: the closed-loop softmax test.

   Script-local copy of the decision layer with SAMPLED softmax selection
   P(c) ~ exp(-gamma * total_c(lambda)); everything else identical to
   `self_tuning/step` (its private genotype/phenotype steps are called via
   vars, unmodified). The hunger-coupled lambda controller reads the SAMPLED
   winner's predicted hunger, exactly as the argmin engine reads its argmin
   winner's.

   Census prediction (2.2): NO (gamma, target) yields two-sided interior
   convergence, because the lambda->hunger gain is ~0.005 against state noise
   ~0.045: targets below the h-atom (~0.166) ramp lambda up, targets above ramp
   it down, and targets inside the atom band give near-zero drift everywhere
   (indifference, not attraction). This run tests that end to end from both
   lambda0 = 0.1 and 0.9.

   Read-only over src/. Run:
     clojure -Sdeps '{:paths [\"src\" \"resources\"]}' -M analysis/aif_engine_softmax_loop.clj"
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.policy-epistemic :as pe]
            [futon5.exotype.self-tuning :as tuning]))

(def genotype-step #'futon5.exotype.self-tuning/genotype-step)
(def phenotype-step #'futon5.exotype.self-tuning/phenotype-step)

(def design
  {:width 80
   :kappa 0.2
   :gamma 16.0
   :steps 1000
   :checkpoints [0 100 200 400 600 800 1000]
   :seeds [2026086000 2026086001 2026086002]
   :targets [0.14 0.165 0.20]
   :lambda0s [0.10 0.90]
   :step-size 0.001
   :sample-tag 0x50F7A9})

(defn- initial-state [target lambda0 seed]
  (ca/with-seed seed
    (let [w (:width design) g (vec (ca/random-sigil-string w))]
      {:arm :efe-full :seed seed :time 0 :hunger-target target
       :lambdas (vec (repeat w lambda0))
       :genotype g :previous-genotype g
       :phenotype (ca/random-phenotype-string w)
       :exotypes (grid/initial-grid :heterogeneous-fixed w)
       :blend-action? true :blend-strength 0.0
       :epistemic-coefficient (:kappa design)
       :apply-probability 1.0 :self-tuning-arm :hunger-coupled
       :lambda-step-size (:step-size design)})))

(defn- softmax-cell-decision [state index]
  (let [lambda (double (nth (:lambdas state) index))
        width (count (:exotypes state))
        obs (efe/local-observation state index)
        own (nth (:exotypes state) index)
        sources [{:policy :hold :source index}
                 {:policy :adopt-left :source (mod (dec index) width)}
                 {:policy :adopt-right :source (mod (inc index) width)}
                 {:policy :blend :source index}]
        cands (mapv (fn [{:keys [policy source] :as cp}]
                      (let [cand (nth (:exotypes state) source)
                            x (if (= :blend policy)
                                (pe/blend-value state index)
                                (pe/pair-value own cand))
                            sc (efe/score-policy :efe-full cand obs
                                                {:lambda 0.0
                                                 :epistemic-coefficient (:kappa design)
                                                 :epistemic-value x})]
                        (merge cp sc
                               {:lambda lambda
                                :total (+ (:total sc) (* lambda (:conatus sc)))})))
                    sources)
        gamma (:gamma design)
        m (apply min (map :total cands))
        ws (mapv #(Math/exp (* (- gamma) (- (:total %) m))) cands)
        z (reduce + ws)
        draw-seed (ca/mix-seed
                   (bit-xor (+ (long (:seed state))
                               (* (long (:time state)) width)
                               index)
                            (long (:sample-tag design))))
        u (* z (.nextDouble (java.util.Random. draw-seed)))
        winner (loop [i 0 acc 0.0]
                 (let [acc' (+ acc (nth ws i))]
                   (if (or (>= acc' u) (= i (dec (count cands))))
                     (nth cands i)
                     (recur (inc i) acc'))))
        hunger (double (get-in winner [:prediction :hunger]))]
    {:index index :winner winner :selected-hunger hunger
     :next-lambda (tuning/next-lambda :hunger-coupled state index lambda hunger)}))

(defn- softmax-step [state]
  (let [decisions (mapv #(softmax-cell-decision state %)
                        (range (count (:exotypes state))))
        exotypes (mapv #(get-in % [:winner :candidate-exotype]) decisions)
        lambdas (mapv :next-lambda decisions)
        previous (:genotype state)]
    (assoc state
           :time (inc (:time state))
           :phenotype (@phenotype-step previous (:phenotype state))
           :genotype (@genotype-step state decisions)
           :exotypes exotypes
           :previous-genotype previous
           :lambdas lambdas
           :last-hungers (mapv :selected-hunger decisions))))

(defn- mean [xs] (/ (reduce + 0.0 xs) (double (count xs))))

(defn- run-cell [target lambda0 seed]
  (let [wanted (set (:checkpoints design))]
    (loop [s (initial-state target lambda0 seed) t 0 acc (sorted-map) over 0 n 0]
      (let [acc' (if (wanted t) (assoc acc t (mean (:lambdas s))) acc)
            ;; hunger>target share over the last 200 steps
            tail? (> t (- (:steps design) 200))
            over' (if (and tail? (:last-hungers s))
                    (+ over (count (filter #(> % target) (:last-hungers s)))) over)
            n' (if (and tail? (:last-hungers s)) (+ n (count (:last-hungers s))) n)]
        (if (= t (:steps design))
          {:seed seed :trajectory acc' :p-over (/ (double over') (max 1.0 (double n')))}
          (recur (softmax-step s) (inc t) acc' over' n'))))))

(defn -main []
  (println (format "softmax closed loop: gamma %.0f, kappa %.1f, step %.3f, %d steps, %d seeds"
                   (:gamma design) (:kappa design) (:step-size design)
                   (:steps design) (count (:seeds design))))
  (println)
  (println "| target | lambda0 | mean-lambda(200) | (400) | (600) | (800) | (1000) | P(h>tgt) last-200 | verdict |")
  (println "|---:|---:|---:|---:|---:|---:|---:|---:|---|")
  (doseq [target (:targets design)
          lambda0 (:lambda0s design)]
    (let [runs (mapv #(run-cell target lambda0 %) (:seeds design))
          at (fn [t] (mean (map #(get-in % [:trajectory t]) runs)))
          fin (at 1000)
          drift (- fin (at 800))
          p-over (mean (map :p-over runs))]
      (println (format "| %.3f | %.2f | %.4f | %.4f | %.4f | %.4f | %.4f | %.3f | %s |"
                       target lambda0 (at 200) (at 400) (at 600) (at 800) fin p-over
                       (cond (and (< 0.01 fin 0.99) (< (Math/abs drift) 0.02)) "INTERIOR-settled"
                             (< 0.01 fin 0.99) (format "interior but drifting %+.3f/200" drift)
                             :else "boundary")))))
  (shutdown-agents))

(-main)
