;; interrupter_epi_summary.clj — summarize the 13 v2 cells in reports-remote/epi/.
;; Part of TN-interrupter-fable-answer.md. Runs under babashka or clojure.
;;   bb scripts/interrupter_epi_summary.clj
(ns interrupter-epi-summary
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def dir "reports-remote/epi")

(defn- mean [xs] (/ (reduce + 0.0 xs) (double (count xs))))
(defn- sd [xs]
  (let [m (mean xs) n (count xs)]
    (Math/sqrt (/ (reduce + 0.0 (map #(let [d (- (double %) m)] (* d d)) xs))
                  (double (dec n))))))

(defn- fmt [x] (format "%.3f" (double x)))

(def cells
  (for [f (sort (map str (.list (io/file dir))))
        :when (re-matches #"cell-.*\.edn" f)]
    f))

(defn -main []
  (doseq [f cells]
    (let [{:keys [apply-probability arm runs scale churn-control]}
          (edn/read-string (slurp (str dir "/" f)))
          dshare (map :dominant-share runs)
          hshare (map :halting-capable-share runs)
          arate (map :adoption-rate runs)
          selx (map :selected-x runs)
          doms (frequencies (map :dominant runs))
          ;; early damage = phenotype-damage at first checkpoint (t=20)
          dmg20 (map #(get-in % [:trajectory 20 :phenotype-damage]) runs)
          dmg600 (map #(get-in % [:trajectory 600 :phenotype-damage]) runs)]
      (println (format "p=%.2f arm=%-14s n=%d" apply-probability (name arm) (count runs)))
      (println "  dominant kinds:" (into (sorted-map) doms))
      (println (str "  dominant-share " (fmt (mean dshare)) " sd " (fmt (sd dshare))
                    " | halting-share " (fmt (mean hshare)) " sd " (fmt (sd hshare))
                    " | adoption " (fmt (mean arate))
                    " | selected-x " (fmt (mean selx))))
      (println (str "  dmg@20 " (fmt (mean dmg20)) " sd " (fmt (sd dmg20))
                    " | dmg@600 " (fmt (mean dmg600)) " sd " (fmt (sd dmg600))
                    (when churn-control
                      (str " | matched bonus " (:bonus churn-control)
                           " (target adoption " (fmt (:target-adoption-rate churn-control)) ")"))
                    (when scale (str " | kappa " (fmt (:kappa scale)))))))))

(-main)
