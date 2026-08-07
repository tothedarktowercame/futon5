(ns srch-lambda-response
  "M4 for TN-search-control-fable-answer.md. The lambda-response curve:
   clamp the lambda field OPEN-LOOP at v (script-side: overwrite :lambdas
   after every step; src untouched), run 300 steps, and measure

     hbar(v)   = mean winner predicted hunger over the last 100 steps
     neg(v)    = fraction of cell-steps (last 100) with hunger < target 0.05
     damage(v) = the search experiment's own damage probe at t=300

   Search via the hunger-coupled sign controller is possible only if
   hbar(v) - 0.05 changes sign somewhere in [0,1]; the crossing would be the
   predicted lambda*. damage(v) says whether lambda regulates the criticality
   proxy at all.

   usage: clojure -Sdeps '{:paths [\"src\" \"resources\"]}' -M analysis/srch_lambda_response.clj"
  (:require [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(def design
  {:width 80 :steps 300 :tail 100 :damage-horizon 40
   :seeds [2026085100 2026085104 2026085109 2026085113]
   :blend-action? true :blend-strength 0.0
   :epistemic-coefficient 0.2 :apply-probability 1.0})

(def lambda-grid [0.0 0.2 0.4 0.55 0.7 0.85 1.0])
(def target (:hunger efe/preferences))

(defn- initial-state [v seed]
  (ca/with-seed seed
    (let [width (:width design)
          genotype (vec (ca/random-sigil-string width))]
      {:arm :efe-full
       :seed seed
       :time 0
       :hunger-target target
       :lambdas (vec (repeat width v))
       :genotype genotype
       :previous-genotype genotype
       :phenotype (ca/random-phenotype-string width)
       :exotypes (grid/initial-grid :heterogeneous-fixed width)
       :blend-action? (:blend-action? design)
       :blend-strength (:blend-strength design)
       :epistemic-coefficient (:epistemic-coefficient design)
       :apply-probability (:apply-probability design)
       ;; arm irrelevant: lambdas are overwritten after every step
       :self-tuning-arm :fixed-0.55
       :lambda-step-size 0.0})))

(defn- clamp [state v]
  (assoc state :lambdas (vec (repeat (:width design) v))))

(defn- clamped-step [state v]
  (clamp (tuning/step state) v))

(defn- damage-probe [state v]
  (let [site (quot (:width design) 2)
        perturbed (update state :phenotype
                          #(apply str (update (vec %) site
                                              (fn [b] (if (= b \0) \1 \0)))))
        advance (fn [s] (nth (iterate #(clamped-step % v) s)
                             (:damage-horizon design)))]
    (count (filter true? (map not=
                              (:phenotype (advance state))
                              (:phenotype (advance perturbed)))))))

(defn mean [xs] (/ (reduce + 0.0 xs) (count xs)))
(defn sd [xs] (let [m (mean xs)]
                (Math/sqrt (mean (map #(let [d (- (double %) m)] (* d d)) xs)))))

(println (format "clamped-lambda response, %d seeds, %d steps, tail %d; target=%.3f"
                 (count (:seeds design)) (:steps design) (:tail design) target))
(println "v | hbar(v) +- seed-sd | frac hunger<target | min winner-hunger | damage(v) +- seed-sd")
(doseq [v lambda-grid]
  (let [per-seed
        (for [seed (:seeds design)]
          (loop [state (initial-state v seed) t 0 hs [] neg 0 n 0]
            (if (= t (:steps design))
              {:hbar (mean hs)
               :neg-frac (/ (double neg) n)
               :hmin (apply min hs)
               :damage (damage-probe state v)}
              (let [state' (clamped-step state v)
                    tail? (>= t (- (:steps design) (:tail design)))
                    ws (when tail?
                         (mapv #(double (get-in % [:winner :prediction :hunger]))
                               (:efe-decisions state')))]
                (recur state' (inc t)
                       (if tail? (into hs ws) hs)
                       (if tail? (+ neg (count (filter #(< % target) ws))) neg)
                       (if tail? (+ n (count ws)) n))))))]
    (println (format "v=%.2f  hbar=%.4f +- %.4f  neg-frac=%.5f  min=%.4f  damage=%5.1f +- %4.1f"
                     v
                     (mean (map :hbar per-seed)) (sd (map :hbar per-seed))
                     (mean (map :neg-frac per-seed))
                     (apply min (map :hmin per-seed))
                     (mean (map :damage per-seed)) (sd (map :damage per-seed))))))
