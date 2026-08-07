(ns srch-flip-threshold
  "M3 for TN-search-control-fable-answer.md.

   The decision-flip threshold delta: per cell, the smallest |d-lambda| that
   changes the argmin over {hold, adopt-left, adopt-right, blend}. Candidate
   totals are affine in lambda: T_c(l') = T_c(l) + (l'-l) * conatus_c, so the
   crossing against the winner w is at d = -(T_c - T_w)/(con_c - con_w),
   valid when l+d stays in [0,1].

   Sampled over realized states of the hunger-coupled arm (the experiment's
   own trajectory distribution), 2 seeds x 3 starts, states at
   t in {50, 100, 150, 200, 250, 300}.

   Reported against the section 1.3 window bound 0.45/sqrt(800) = 0.0159 and
   against the walk's realized dispersion (SD 0.0043 at t=800).

   usage: clojure -Sdeps '{:paths [\"src\" \"resources\"]}' -M analysis/srch_flip_threshold.clj"
  (:require [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(def design
  {:width 80 :steps 300 :sample-times #{50 100 150 200 250 300}
   :seeds [2026085100 2026085104]
   :initial-lambda 0.55
   :blend-action? true :blend-strength 0.0
   :epistemic-coefficient 0.2 :apply-probability 1.0})

(def rule-numbers {:ordered 204 :chaotic 30})

(defn- rule-sigil [rule]
  (ca/sigil-for (str/replace (format "%8s" (Integer/toBinaryString rule)) " " "0")))

(defn- initial-genotype [start width]
  (case start
    :ordered (vec (repeat width (rule-sigil (rule-numbers start))))
    :chaotic (vec (repeat width (rule-sigil (rule-numbers start))))
    :random (vec (ca/random-sigil-string width))))

(defn- initial-state [start seed]
  (ca/with-seed seed
    (let [width (:width design)
          genotype (initial-genotype start width)]
      {:arm :efe-full
       :seed seed
       :time 0
       :hunger-target (:hunger efe/preferences)
       :lambdas (vec (repeat width (:initial-lambda design)))
       :genotype genotype
       :previous-genotype genotype
       :phenotype (ca/random-phenotype-string width)
       :exotypes (grid/initial-grid :heterogeneous-fixed width)
       :blend-action? (:blend-action? design)
       :blend-strength (:blend-strength design)
       :epistemic-coefficient (:epistemic-coefficient design)
       :apply-probability (:apply-probability design)
       :self-tuning-arm :hunger-coupled
       :lambda-step-size 0.0003})))

(defn cell-delta
  "Smallest |d-lambda| flipping this cell's argmin, or nil if none in range."
  [decision]
  (let [cands (:candidates decision)
        winner (:winner decision)
        lam (double (:lambda winner))
        tw (double (:total winner))
        cw (double (:conatus winner))
        deltas
        (for [c cands
              :when (not= (:policy c) (:policy winner))
              :let [m (- (double (:total c)) tw)
                    d (- (double (:conatus c)) cw)]
              :when (and (not (zero? d)) (pos? m))
              :let [delta (/ (- m) d)]
              :when (and (<= 0.0 (+ lam delta) 1.0)
                         (not (zero? delta)))]
          (Math/abs delta))]
    (when (seq deltas) (apply min deltas))))

(defn- quantile [xs q]
  (let [s (vec (sort xs))]
    (nth s (min (dec (count s)) (int (Math/floor (* q (count s))))))))

(let [rows
      (vec
       (for [start [:ordered :chaotic :random]
             seed (:seeds design)]
         (loop [state (initial-state start seed) t 0 acc []]
           (if (> t (:steps design))
             {:start start :seed seed :deltas acc}
             (let [acc' (if ((:sample-times design) t)
                          (into acc
                                (keep cell-delta
                                      (:decisions (tuning/transmit state))))
                          acc)]
               (recur (tuning/step state) (inc t) acc'))))))
      all (mapcat :deltas rows)
      n-cells (* (count rows) (count (:sample-times design)) (:width design))
      flippable (count all)]
  (println (format "sampled cell-states: %d; with a reachable lambda-crossing: %d (%.1f%%)"
                   n-cells flippable (* 100.0 (/ (double flippable) n-cells))))
  (println (format "delta quantiles over flippable cells:"))
  (doseq [q [0.01 0.05 0.10 0.25 0.50 0.75 0.90]]
    (println (format "  q%02d  %.4f" (int (* 100 q)) (double (quantile all q)))))
  (let [window 0.0159 walk-3sd (* 3 0.00425)]
    (println (format "window bound 0.45/sqrt(800) = %.4f: fraction of ALL sampled cells with delta below it: %.4f%%"
                     window (* 100.0 (/ (count (filter #(< % window) all)) (double n-cells)))))
    (println (format "walk 3*SD(800) = %.4f: fraction of ALL sampled cells with delta below it: %.4f%%"
                     walk-3sd (* 100.0 (/ (count (filter #(< % walk-3sd) all)) (double n-cells))))))
  (doseq [{:keys [start seed deltas]} rows]
    (println (format "  %-8s seed %d: flippable %4d, min delta %.4f, median %.4f"
                     (name start) seed (count deltas)
                     (if (seq deltas) (apply min deltas) Double/NaN)
                     (if (seq deltas) (double (quantile deltas 0.5)) Double/NaN)))))
