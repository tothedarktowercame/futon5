(ns futon5.arrow.normalize
  "Normalization of MMCA traces into regime descriptors."
  (:require [futon5.mmca.metrics :as metrics]))

(defn word-class
  "Classify a run summary into a coarse regime."
  [{:keys [avg-change avg-entropy-n temporal-autocorr]}]
  (cond
    (and (number? avg-change) (<= avg-change 0.05)) :dead
    (and (number? avg-change) (<= avg-change 0.2)
         (number? avg-entropy-n) (<= avg-entropy-n 0.3)) :stable
    (and (number? temporal-autocorr) (>= temporal-autocorr 0.7)) :oscillating
    :else :chaotic))

(defn normalize
  "Normalize a run result to a compact descriptor."
  [result]
  (let [summary (metrics/summarize-run result)
        wc (word-class summary)
        macro [(double (or (:avg-change summary) 0.0))
               (double (or (:avg-entropy-n summary) 0.0))
               (double (or (:temporal-autocorr summary) 0.0))]]
    {:word-class wc
     :macro-vec macro
     :summary summary}))

(defn regime-sig
  "Generate a stable hash for a descriptor."
  [descriptor]
  (let [bins (fn [x]
               (cond
                 (not (number? x)) 0
                 (< x 0.33) 0
                 (< x 0.66) 1
                 :else 2))
        macro (:macro-vec descriptor)
        coarse (when (seq macro)
                 (mapv bins macro))]
    (hash {:word-class (:word-class descriptor)
           :macro-bins coarse})))

(defn macro-distance
  "Euclidean distance between macro-vecs."
  [a b]
  (when (and (seq a) (seq b) (= (count a) (count b)))
    (Math/sqrt
     (reduce + 0.0 (map (fn [x y]
                          (let [d (- (double x) (double y))]
                            (* d d)))
                        a b)))))
