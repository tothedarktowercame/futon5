(require '[futon5.exotype.self-tuning :as tuning] '[futon5.exotype.grid :as grid]
         '[futon5.exotype.efe :as efe] '[futon5.ca.core :as ca])
;; h-bar(lambda): mean realized winner-hunger with lambda CLAMPED at v.
;; Gates T1/T2. Search is possible iff h-bar(v) - target crosses zero in (0,1);
;; the crossing point is the predicted attractor lambda*. A FLAT h-bar means
;; lambda is a weak actuator and no target choice gives a sharp attractor.
(def W 80) (def BURN 150) (def SEEDS (range 5000 5016))
(defn state [v seed] (ca/with-seed seed (let [g (vec (ca/random-sigil-string W))]
  {:arm :efe-full :seed seed :time 0 :hunger-target (:hunger efe/preferences)
   :lambdas (vec (repeat W v)) :genotype g :previous-genotype g
   :phenotype (ca/random-phenotype-string W)
   :exotypes (grid/initial-grid :heterogeneous-fixed W)
   :blend-action? true :blend-strength 0.0 :epistemic-coefficient 0.2
   :apply-probability 1.0 :self-tuning-arm :hunger-coupled :lambda-step-size 0.0})))
(defn clamp [v st] (assoc st :lambdas (vec (repeat W v))))
(defn hbar [v seed]
  (let [s (nth (iterate (fn [st] (clamp v (tuning/step st))) (state v seed)) BURN)]
    (let [hs (map #(get-in % [:winner :prediction :hunger]) (:efe-decisions s))]
      (/ (reduce + hs) (double (count hs))))))
(defn mn [xs] (/ (reduce + xs) (double (count xs))))
(defn se [xs] (let [m (mn xs)] (/ (Math/sqrt (mn (map #(let [d (- % m)] (* d d)) xs))) (Math/sqrt (count xs)))))
(def VS [0.0 0.1 0.2 0.3 0.4 0.5 0.6 0.7 0.8 0.9 1.0])
(def rows (mapv (fn [v] (let [h (mapv #(hbar v %) SEEDS)] [v (mn h) (se h)])) VS))
(println (format "  h-bar(lambda), %d seeds, %d burn-in steps. TARGET = %.4f\n" (count SEEDS) BURN (:hunger efe/preferences)))
(println "  lambda    h-bar        SE      error vs 0.05   error vs realized median")
(def med (nth (sort (map second rows)) (quot (count rows) 2)))
(doseq [[v h s] rows]
  (println (format "  %.2f    %.4f   %.4f       %+.4f              %+.4f" v h s (- h 0.05) (- h med))))
;; slope test: is h-bar actually a function of lambda at all?
(let [n (count rows) mx (mn (map first rows)) my (mn (map second rows))
      sxy (reduce + (map (fn [[x y _]] (* (- x mx) (- y my))) rows))
      sxx (reduce + (map (fn [[x _ _]] (let [d (- x mx)] (* d d))) rows))
      slope (/ sxy sxx)
      resid (map (fn [[x y _]] (- y (+ my (* slope (- x mx))))) rows)
      s-err (Math/sqrt (/ (/ (reduce + (map #(* % %) resid)) (- n 2)) sxx))]
  (println (format "\n  SLOPE of h-bar in lambda: %+.5f +- %.5f   t = %+.2f   %s"
                   slope s-err (/ slope s-err)
                   (if (> (Math/abs (/ slope s-err)) 2.0) "actuator WORKS" "FLAT -- lambda is a weak actuator")))
  (println (format "  range of h-bar over lambda in [0,1]: %.4f  (target 0.05 is %s this range)"
                   (- (apply max (map second rows)) (apply min (map second rows)))
                   (if (< 0.05 (apply min (map second rows))) "BELOW" "inside"))))
