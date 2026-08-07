(require '[futon5.exotype.self-tuning :as tuning] '[futon5.exotype.grid :as grid]
         '[futon5.exotype.efe :as efe] '[futon5.ca.core :as ca] '[clojure.string :as str])
(def W 250) (def MID 125) (def T 100) (def SEEDS (range 20260805 (+ 20260805 24)))
(defn cfg [seed]
  (ca/with-seed seed
    (let [g (vec (ca/random-sigil-string W))]
      {:arm :efe-full :seed seed :time 0 :hunger-target (:hunger efe/preferences)
       :lambdas (vec (repeat W 0.55)) :genotype g :previous-genotype g
       :phenotype (ca/random-phenotype-string W)
       :exotypes (grid/initial-grid :heterogeneous-fixed W)
       :blend-action? true :blend-strength 0.0 :epistemic-coefficient 0.2
       :apply-probability 1.0 :self-tuning-arm :hunger-coupled :lambda-step-size 0.0003})))
(defn rs [r] (ca/sigil-for (str/replace (format "%8s" (Integer/toBinaryString r)) " " "0")))
(defn flip [p] (apply str (update (vec p) MID #(if (= % \0) \1 \0))))
(defn dc [a b] (count (filter true? (map not= a b))))
;; ANCHOR: uniform ECA rule, genotype held fixed -- same width, same horizon, same harness
(defn phe-step [g p] (apply str (for [i (range W)]
  (let [l (Character/digit ^char (nth p (mod (dec i) W)) 2) s (Character/digit ^char (nth p i) 2)
        r (Character/digit ^char (nth p (mod (inc i) W)) 2)]
    (nth (ca/bits-for (str (nth g i))) (- 7 (+ (* 4 l) (* 2 s) r)))))))
(defn anchor [rule seed]
  (let [g (vec (repeat W (rs rule)))
        p0 (ca/with-seed seed (ca/random-phenotype-string W)) p1 (flip p0)]
    (loop [a p0 b p1 t 0] (if (= t T) (dc a b) (recur (phe-step g a) (phe-step g b) (inc t))))))
(defn layered [seed burn]
  (let [s0 (nth (iterate tuning/step (cfg seed)) burn)
        s1 (assoc s0 :phenotype (flip (:phenotype s0)))]
    (loop [a s0 b s1 t 0]
      (if (= t T) (dc (:phenotype a) (:phenotype b)) (recur (tuning/step a) (tuning/step b) (inc t))))))
(defn mn [xs] (/ (reduce + xs) (double (count xs))))
(defn se [xs] (let [m (mn xs) v (mn (map #(let [d (- % m)] (* d d)) xs))] (/ (Math/sqrt v) (Math/sqrt (count xs)))))
(println (format "  DAMAGE REACH at t=%d, width %d, %d seeds -- ALL measured in this harness\n" T W (count SEEDS)))
(println "  ECA anchors (uniform rule, genotype held fixed):")
(doseq [r [204 90 54 110 30]]
  (let [d (map #(anchor r %) SEEDS)] (println (format "    rule %-4d %6.1f +- %.1f" r (mn d) (se d)))))
(println "\n  the current configuration (4-action, kappa 0.2, policy-controlled blending):")
(doseq [b [0 250]]
  (let [d (map #(layered % b) SEEDS)]
    (println (format "    burn-in %-4d %6.1f +- %.1f" b (mn d) (se d)))))
