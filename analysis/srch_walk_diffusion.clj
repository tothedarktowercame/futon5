(ns srch-walk-diffusion
  "M1b for TN-search-control-fable-answer.md. The random-walk arm's lambda-SD
   in the artifacts is SUB-diffusive (ratio to s*sqrt(t) falls 0.87 -> 0.50 by
   t=800). This replicates `self_tuning/random-direction` exactly (same seed
   arithmetic, java.util.Random first draw) and measures the direction
   process itself: per-cell temporal autocorrelation, cross-cell correlation,
   and displacement scaling.

   usage: clojure -M analysis/srch_walk_diffusion.clj"
  (:require [clojure.string :as str]))

(defn direction ^double [^long seed ^long time ^long index]
  (let [draw-seed (+ seed (* 1000003 time) (* 9176 index) 44119)]
    (if (< (.nextDouble (java.util.Random. draw-seed)) 0.5) -1.0 1.0)))

(def seeds [2026085100 2026085101 2026085102 2026085103])
(def width 80)
(def steps 800)

(defn mean [xs] (/ (reduce + 0.0 xs) (count xs)))
(defn sd [xs] (let [m (mean xs)]
                (Math/sqrt (mean (map #(let [d (- (double %) m)] (* d d)) xs)))))

;; directions[seed][t][i]
(doseq [seed seeds]
  (let [dirs (vec (for [t (range steps)]
                    (vec (for [i (range width)] (direction seed t i)))))
        ;; per-cell displacement at t=800 in units of s
        disp (vec (for [i (range width)]
                    (reduce + 0.0 (map #(nth % i) dirs))))
        ;; lag-1 temporal autocorrelation pooled over cells
        lag1 (let [pairs (for [i (range width) t (range (dec steps))]
                           [(nth (nth dirs t) i) (nth (nth dirs (inc t)) i)])
                   xs (map first pairs) ys (map second pairs)
                   mx (mean xs) my (mean ys)
                   cov (mean (map (fn [[x y]] (* (- x mx) (- y my))) pairs))]
               (/ cov (* (sd xs) (sd ys))))
        ;; step-mean correlation across cells: SD over t of the cell-mean step,
        ;; vs 1/sqrt(width) expected if independent
        stepmeans (mapv mean dirs)
        mean-bias (mean (mapcat identity dirs))]
    (println (format "seed %d: mean direction %+.4f | per-cell disp SD %.2f (iid: %.2f) | cross-cell disp SD %.2f | lag-1 autocorr %+.4f | SD of per-step cell-mean %.4f (iid: %.4f)"
                     seed mean-bias
                     (sd disp) (Math/sqrt steps)
                     (sd disp)
                     lag1
                     (sd stepmeans) (/ 1.0 (Math/sqrt width))))))
