(require '[clojure.string :as str])
(let [src (slurp "scripts/local_compressibility_grid.clj")]
  (load-string (str/replace src "(apply -main *command-line-args*)" "")))
(def ns* (find-ns 'local-compressibility-grid))
(def metaca-state @(ns-resolve ns* 'metaca-state))
(def metaca-step @(ns-resolve ns* 'checked-metaca-step))
(require 'futon5.exotype.efe 'futon5.exotype.grid)
(let [g (:global @@(resolve 'futon5.exotype.efe/conditional-model))]
  (reset! @(resolve 'futon5.exotype.efe/s8-predictor) (fn [_ _] g)))
(def ct @(resolve 'futon5.exotype.grid/cycle-type))
(def k->s8 @(resolve 'futon5.exotype.grid/kind->s8))
(println "kind->s8 cycle types" (into {} (for [k @(resolve 'futon5.exotype.grid/exotype-kinds)] [k (ct (k->s8 k))])))
(let [st0 (metaca-state 10.0 0.1 2026102000)
      st0 (assoc st0 :exotypes (binding [futon5.ca.core/*rng* (java.util.Random. 5)] ((resolve 'futon5.exotype.grid/initial-grid) :s8-uniform (count (:exotypes st0)))))
      t0 (System/currentTimeMillis)
      st (nth (iterate metaca-step st0) 50)]
  (println "ok 50 steps" (- (System/currentTimeMillis) t0) "ms; distinct exotypes" (count (distinct (:exotypes st))) "of" (count (:exotypes st))
           "cycle types" (take 6 (sort-by (comp - val) (frequencies (map ct (:exotypes st)))))))
(shutdown-agents)
