(ns srch-lambda0-probe
  "M5 for TN-search-control-fable-answer.md: mini version of test T1.
   Run the hunger-coupled arm from lambda0 in {0.2, 0.9} (random start; the
   state start is irrelevant by the mixing result). Search predicts descent
   from 0.9 toward a common interior attractor; the ramp hypothesis H1
   predicts maximum-rate rise from BOTH starts, pinning at 1.0 from 0.9 at
   t = (1-0.9)/0.0003 = 333.

   usage: clojure -Sdeps '{:paths [\"src\" \"resources\"]}' -M analysis/srch_lambda0_probe.clj"
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(def design
  {:width 80 :steps 400 :checkpoints #{0 50 100 200 300 400}
   :seeds [2026085100 2026085104 2026085109 2026085113]
   :blend-action? true :blend-strength 0.0
   :epistemic-coefficient 0.2 :apply-probability 1.0
   :step-size 0.0003})

(defn- initial-state [lambda0 seed]
  (ca/with-seed seed
    (let [width (:width design)
          genotype (vec (ca/random-sigil-string width))]
      {:arm :efe-full
       :seed seed
       :time 0
       :hunger-target (:hunger efe/preferences)
       :lambdas (vec (repeat width lambda0))
       :genotype genotype
       :previous-genotype genotype
       :phenotype (ca/random-phenotype-string width)
       :exotypes (grid/initial-grid :heterogeneous-fixed width)
       :blend-action? (:blend-action? design)
       :blend-strength (:blend-strength design)
       :epistemic-coefficient (:epistemic-coefficient design)
       :apply-probability (:apply-probability design)
       :self-tuning-arm :hunger-coupled
       :lambda-step-size (:step-size design)})))

(defn mean [xs] (/ (reduce + 0.0 xs) (count xs)))

(println "lambda0 | t | mean-lambda over seeds | ramp clip(lambda0 + s t) | pinned-at-1 fraction")
(doseq [lambda0 [0.2 0.9]]
  (let [per-seed
        (for [seed (:seeds design)]
          (loop [state (initial-state lambda0 seed) t 0 acc {}]
            (let [acc' (if ((:checkpoints design) t)
                         (assoc acc t {:mean (mean (:lambdas state))
                                       :pinned (/ (count (filter #(>= (double %) 1.0)
                                                                 (:lambdas state)))
                                                  (double (:width design)))})
                         acc)]
              (if (= t (:steps design))
                acc'
                (recur (tuning/step state) (inc t) acc')))))]
    (doseq [t (sort (:checkpoints design))]
      (println (format "l0=%.1f  t=%3d  lam=%7.4f  ramp=%7.4f  pinned=%.3f"
                       lambda0 t
                       (mean (map #(get-in % [t :mean]) per-seed))
                       (min 1.0 (+ lambda0 (* (:step-size design) t)))
                       (mean (map #(get-in % [t :pinned]) per-seed)))))))
