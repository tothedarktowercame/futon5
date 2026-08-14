;; Is the no-policy "default" state morphologically the found regime, or only
;; label-level coexistence?  (fable, 2026-08-08; provoked by Joe: "I don't
;; think the interesting regime is a default -- I didn't find it until
;; yesterday.")
;;
;; One estimator, two conditions, six seeds each:
;;   :hetfixed  operators held fixed, no policy (the "default" of the ruling)
;;   :valley    policy ON at beta=8, kappa=0.1 (the bisection-found exemplar)
;; Stats over the last 500 steps: late frozen fraction, circular settled-run
;; widths (median / p90 / max), and phenotype turnover (mean changed fraction).
;; Writes reports/morphology-default-check.edn.
(require '[clojure.string :as str] '[clojure.java.io :as io] '[clojure.pprint :as pp])
(let [src (slurp "scripts/local_compressibility_grid.clj")
      driver "(apply -main *command-line-args*)"]
  (load-string (str/replace src driver "")))
(require '[futon5.ca.core :as ca] '[futon5.exotype.grid :as grid])

(def objective-ns (find-ns 'local-compressibility-grid))
(def initial-components @(ns-resolve objective-ns 'initial-components))
(def metaca-state @(ns-resolve objective-ns 'metaca-state))
(def metaca-step @(ns-resolve objective-ns 'checked-metaca-step))

(def WIDTH 250) (def STEPS 3000) (def W 15) (def LATE 500)
(def SEEDS (vec (range 2026102000 2026102006)))

(defn circular-runs [settled]
  ;; lengths of maximal circular runs of true
  (if (every? true? settled)
    [WIDTH]
    (let [start (first (filter #(not (nth settled %)) (range WIDTH)))]
      (loop [i 0 cur 0 acc []]
        (if (= i WIDTH)
          (if (pos? cur) (conj acc cur) acc)
          (let [v (nth settled (mod (+ start 1 i) WIDTH))]
            (cond v (recur (inc i) (inc cur) acc)
                  (pos? cur) (recur (inc i) 0 (conj acc cur))
                  :else (recur (inc i) 0 acc))))))))

(defn quantile [xs q]
  (if (empty? xs) nil
      (nth (vec (sort xs)) (min (dec (count xs)) (int (* q (count xs)))))))

(defn run-one [arm seed]
  (let [step-fn (if (= arm :hetfixed) grid/step metaca-step)
        st (if (= arm :hetfixed)
             (let [{:keys [genotype phenotype exotypes]} (initial-components seed)]
               {:arm :heterogeneous-fixed :seed seed :time 0
                :genotype genotype :phenotype phenotype :exotypes exotypes})
             (metaca-state 8.0 0.1 seed))]
    (loop [s st t 0
           run-len (vec (repeat WIDTH 1))
           widths [] frozen [] churn []]
      (if (= t STEPS)
        {:arm arm :seed seed
         :frozen-late (/ (reduce + frozen) (max 1 (count frozen)))
         :churn-late (/ (reduce + churn) (max 1 (count churn)))
         :w-med (quantile widths 0.5) :w-p90 (quantile widths 0.9)
         :w-max (when (seq widths) (apply max widths)) :n-seg (count widths)}
        (let [prev (:phenotype s)
              s' (step-fn s)
              phe (:phenotype s')
              changed (mapv not= prev phe)
              run-len' (mapv (fn [c r] (if c 1 (inc r))) changed run-len)
              settled (mapv #(> % W) run-len')
              late? (>= t (- STEPS LATE))]
          (recur s' (inc t) run-len'
                 (if late? (into widths (circular-runs settled)) widths)
                 (if late? (conj frozen (/ (double (count (filter true? settled))) WIDTH)) frozen)
                 (if late? (conj churn (/ (double (count (filter true? changed))) WIDTH)) churn)))))))

(def results
  (vec (pmap (fn [[arm seed]] (run-one arm seed))
             (for [arm [:hetfixed :valley] seed SEEDS] [arm seed]))))

(println "arm       seed        frozen  churn   w-med  w-p90  w-max  n-seg")
(doseq [{:keys [arm seed frozen-late churn-late w-med w-p90 w-max n-seg]} results]
  (printf "%-9s %d  %6.3f %6.3f %6s %6s %6s %6d%n"
          (name arm) seed (double frozen-late) (double churn-late)
          (str w-med) (str w-p90) (str w-max) n-seg)
  (flush))
(spit "reports/morphology-default-check.edn"
      (with-out-str (pp/pprint {:date "2026-08-08" :width WIDTH :steps STEPS
                                :w W :late LATE :results results})))
(println "MORPH_CHECK_DONE")
(shutdown-agents)
