(ns interrupter-twins
  "Layer-resolved twin-divergence measurement for TN-interrupter-fable-answer.md.

   Per (p, arm, seed): burn 300 steps from the exact v2 initial state, then run
   three 60-step twin pairs against the baseline continuation, all with shared
   seeds (common noise):
     :sigma  -- exotypes[40] replaced by the next kind in the vocabulary cycle
     :pheno  -- phenotype bit 40 flipped
   and, separately for the blend demonstration, the same :pheno perturbation with
   :blend-strength 0.15 on BOTH twins (the content channel switched on).

   Recorded per twin pair:
     - Prop-1' ordering violations: a cell j (excluding the sigma-perturbed site)
       whose genotype diverges at recorded time t+1 without an exotype divergence
       at j at any recorded time <= t. Prediction: ZERO for beta=0; nonzero for
       beta=0.15.
     - divergence masses and cone half-widths (max |j - site| circular) per layer
       (phenotype / exotype / genotype) at horizons 10, 30, 60.
     - decision sensitivity at the burnt-in state: fraction of cells whose winner
       kind changes when one neighbouring phenotype bit is flipped.

   Run: clojure -Sdeps '{:paths [\"src\" \"resources\"]}' -M scripts/interrupter_twins.clj"
  (:require [clojure.pprint :as pprint]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(def width 80)
(def site 40)
(def burn 300)
(def horizon 60)
(def lambda 0.55)
(def kappa 0.47821902791182086)
(def bonuses {0.3 0.15, 0.6 0.8, 1.0 0.1})
(def seeds (vec (range 2026084300 2026084308)))

(defn initial-state [seed]
  (ca/with-seed seed
    (let [genotype (vec (ca/random-sigil-string width))]
      {:arm :efe-full :seed seed :time 0
       :self-tuning-arm :hunger-coupled
       :lambda-step-size 0.0
       :hunger-target (:hunger efe/preferences)
       :lambdas (vec (repeat width lambda))
       :genotype genotype :previous-genotype genotype
       :phenotype (apply str (repeatedly width #(if (< (ca/rnd) 0.5) \0 \1)))
       :exotypes (grid/initial-grid :heterogeneous-fixed width)})))

(defn arm-options [arm p]
  (case arm
    :off {:epistemic-coefficient 0.0 :adoption-bonus 0.0}
    :epistemic {:epistemic-coefficient kappa :adoption-bonus 0.0}
    :matched-churn {:epistemic-coefficient 0.0 :adoption-bonus (get bonuses p)}))

(defn- perturb [state kind]
  (case kind
    :sigma (update state :exotypes
                   (fn [exos]
                     (let [own (nth exos site)
                           next-kind (->> (cycle grid/exotype-kinds)
                                          (drop-while #(not= % own))
                                          second)]
                       (assoc exos site next-kind))))
    :pheno (update state :phenotype
                   #(apply str (update (vec %) site
                                       (fn [b] (if (= b \0) \1 \0)))))))

(defn- masks [a b]
  {:phe (mapv not= (:phenotype a) (:phenotype b))
   :exo (mapv not= (:exotypes a) (:exotypes b))
   :gen (mapv not= (:genotype a) (:genotype b))})

(defn- circ-dist [j] (min (mod (- j site) width) (mod (- site j) width)))

(defn- cone [mask]
  (let [hits (keep-indexed (fn [j d] (when d j)) mask)]
    {:mass (count hits)
     :half-width (if (seq hits) (apply max (map circ-dist hits)) 0)}))

(defn- run-twins
  "Advance BASE and TWIN together for HORIZON steps; return mask sequence."
  [base twin]
  (loop [a base b twin t 0 out []]
    (if (= t horizon)
      out
      (let [a' (tuning/step a) b' (tuning/step b)]
        (recur a' b' (inc t) (conj out (masks a' b')))))))

(defn- ordering-violations
  "Cells j (excluding EXCLUDE) whose genotype first diverges at index t in the
   mask sequence without exotype divergence at j at any index < t.
   Genotype at recorded step t is computed from the exotype field at step t-1
   (recorded index t-1); index -1 is the (identical or sigma-perturbed) start."
  [mask-seq exclude start-exo-diff]
  (for [j (range width)
        :when (not (exclude j))
        :let [gen-first (first (keep-indexed
                                (fn [t m] (when (nth (:gen m) j) t)) mask-seq))]
        :when gen-first
        :let [exo-before (or (nth start-exo-diff j)
                             (some #(nth (:exo (nth mask-seq %)) j)
                                   (range gen-first)))]
        :when (not exo-before)]
    [j gen-first]))

(defn run-one [seed p arm]
  (let [state0 (merge (initial-state seed) {:apply-probability p} (arm-options arm p))
        burnt (nth (iterate tuning/step state0) burn)
        sensitivity
        (let [flip (fn [s j] (update s :phenotype
                                     #(apply str (update (vec %) (mod j width)
                                                         (fn [b] (if (= b \0) \1 \0))))))]
          (/ (count (for [i (range width)
                          :let [w0 (get-in (tuning/cell-decision burnt i)
                                           [:winner :candidate-exotype])
                                w1 (get-in (tuning/cell-decision (flip burnt (dec i)) i)
                                           [:winner :candidate-exotype])]
                          :when (not= w0 w1)]
                      i))
             (double width)))
        run-pair
        (fn [kind beta]
          (let [base (cond-> burnt beta (assoc :blend-strength beta))
                twin (perturb base kind)
                start-exo (mapv not= (:exotypes base) (:exotypes twin))
                ms (run-twins base twin)
                exclude (if (= kind :sigma) #{site} #{})
                viol (ordering-violations ms exclude start-exo)]
            {:violations (count viol)
             :sample-violations (vec (take 5 viol))
             :cones (into {} (for [t [10 30 60]]
                               [t (into {} (for [[k m] (nth ms (dec t))]
                                             [k (cone m)]))]))}))]
    {:seed seed :p p :arm arm
     :sensitivity sensitivity
     :sigma (run-pair :sigma nil)
     :pheno (run-pair :pheno nil)
     :pheno-blend (run-pair :pheno 0.15)}))

(defn- mean [xs] (/ (reduce + 0.0 xs) (double (count xs))))

(defn -main []
  (let [rows (vec (for [p [0.3 0.6 1.0]
                        arm [:off :epistemic :matched-churn]
                        seed seeds]
                    (do (binding [*out* *err*] (println "twin" p arm seed))
                        (run-one seed p arm))))]
    (spit "analysis/interrupter-twins-rows.edn"
          (with-out-str (pprint/pprint rows)))
    (doseq [[[p arm] group] (sort-by key (group-by (juxt :p :arm) rows))]
      (println (format "p=%.2f %-14s sensitivity %.3f" p (name arm)
                       (mean (map :sensitivity group))))
      (doseq [kind [:sigma :pheno :pheno-blend]]
        (let [g (map kind group)
              v (reduce + (map :violations g))
              at (fn [t layer field] (mean (map #(get-in % [:cones t layer field]) g)))]
          (println (format "   %-12s viol %3d | t=30 mass phe %5.1f exo %5.1f gen %5.1f | half-width phe %4.1f exo %4.1f gen %4.1f | t=60 gen mass %5.1f hw %4.1f"
                           (name kind) v
                           (at 30 :phe :mass) (at 30 :exo :mass) (at 30 :gen :mass)
                           (at 30 :phe :half-width) (at 30 :exo :half-width) (at 30 :gen :half-width)
                           (at 60 :gen :mass) (at 60 :gen :half-width))))))))

(-main)
