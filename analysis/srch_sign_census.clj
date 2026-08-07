(ns srch-sign-census
  "M2 for TN-search-control-fable-answer.md.

   (a) Static census of the derived conditional model: which rows predict
       hunger < 0.05 (the lambda-coupling target), and for which
       (kind, activity, diversity) bins.
   (b) Dynamic census: re-run the exotype_search dynamics (same initial-state
       construction, same design constants) for a few seeds x starts, 300
       steps, and count sign(hunger_winner - 0.05) per cell-step directly;
       report which candidate kinds / observation bins produce the negative
       signs, and when.

   usage: clojure -Sdeps '{:paths [\"src\" \"resources\"]}' -M analysis/srch_sign_census.clj"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(def target (:hunger efe/preferences))

(println (format "hunger target = %.3f" target))
(println)
(println "== (a) Static census of conditional-model.edn hunger rows ==")
(let [m (edn/read-string (slurp (io/resource "futon5/exotype/conditional-model.edn")))
      bins (:bins m)
      global (:global m)
      rows (for [[k row] bins] {:bin k :hunger (:hunger row) :n (:n row)})
      trusted (filter #(>= (long (:n %)) 30) rows)
      below (filter #(< (double (:hunger %)) target) trusted)
      untrusted-below (filter #(and (< (long (:n %)) 30)
                                    (< (double (:hunger %)) target)) rows)]
  (println (format "global row hunger: %.4f (sample-count=%d)"
                   (double (:hunger global)) (long (:sample-count m))))
  (println (format "%d bins total, %d trusted (n>=30); trusted hunger min/median/max: %.4f / %.4f / %.4f"
                   (count rows) (count trusted)
                   (apply min (map :hunger trusted))
                   (nth (sort (map :hunger trusted)) (quot (count trusted) 2))
                   (apply max (map :hunger trusted))))
  (println (format "trusted bins with hunger < target: %d" (count below)))
  (doseq [r (sort-by :hunger below)]
    (println (format "  bin %-28s hunger=%.4f n=%d" (pr-str (:bin r)) (double (:hunger r)) (long (:n r)))))
  (println (format "untrusted (n<30, falls back to global %.4f) bins with hunger < target: %d %s"
                   (double (:hunger global))
                   (count untrusted-below)
                   (pr-str (mapv :bin untrusted-below)))))

;; ---- dynamic census: mirror scripts/exotype_search.clj construction exactly ----

(def design
  {:width 80 :steps 300
   :seeds [2026085100 2026085101 2026085104 2026085109]
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

(defn- initial-state [start arm-map seed]
  (ca/with-seed seed
    (let [width (:width design)
          genotype (initial-genotype start width)]
      (merge
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
        :apply-probability (:apply-probability design)}
       arm-map))))

(println)
(println "== (b) Dynamic census: hunger-coupled, 4 seeds x 3 starts x 300 steps ==")
(doseq [start [:ordered :chaotic :random]]
  (let [stats
        (for [seed (:seeds design)]
          (loop [state (initial-state start {:self-tuning-arm :hunger-coupled
                                             :lambda-step-size 0.0003} seed)
                 t 0
                 neg 0 zero 0 pos 0
                 neg-by-window {}          ; window -> count
                 neg-kinds {}              ; winner candidate kind -> count
                 hunger-min 2.0]
            (if (= t (:steps design))
              {:neg neg :zero zero :pos pos :neg-by-window neg-by-window
               :neg-kinds neg-kinds :hunger-min hunger-min}
              (let [state' (tuning/step state)
                    decisions (:efe-decisions state')
                    hs (mapv #(double (get-in % [:winner :prediction :hunger])) decisions)
                    kinds (mapv #(get-in % [:winner :candidate-exotype]) decisions)
                    window (cond (< t 25) :t<25 (< t 100) :t25-100 :else :t>=100)
                    negs (count (filter #(< % target) hs))
                    zeros (count (filter #(== (double target) %) hs))
                    nk (reduce (fn [m [h k]] (if (< h target) (update m k (fnil inc 0)) m))
                               neg-kinds (map vector hs kinds))]
                (recur state' (inc t)
                       (+ neg negs) (+ zero zeros)
                       (+ pos (- (count hs) negs zeros))
                       (update neg-by-window window (fnil + 0) negs)
                       nk
                       (double (apply min hunger-min hs)))))))
        total (reduce + (map #(+ (:neg %) (:zero %) (:pos %)) stats))
        neg (reduce + (map :neg stats))
        zero (reduce + (map :zero stats))
        by-window (apply merge-with + (map :neg-by-window stats))
        kinds (apply merge-with + (map :neg-kinds stats))
        hmin (apply min (map :hunger-min stats))]
    (println (format "%-8s cell-steps=%d  neg=%d (%.4f%%)  zero=%d  neg by window %s  neg winner-kinds %s  min winner-hunger=%.4f"
                     (name start) total neg (* 100.0 (/ (double neg) total)) zero
                     (pr-str (into (sorted-map) by-window))
                     (pr-str kinds) hmin))))
