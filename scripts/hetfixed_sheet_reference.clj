;; Het-fixed reference at SHEET scale (fable, 2026-08-08).
;;
;; Claim to pin (TN-part-iii-endogenous-ruling.md S3): absorption at width 250
;; is created by the EFE policy layer at high beta, not by the substrate.
;; Test: identical initial components to the sheet runs (same seeds, same
;; genotype/phenotype/exotype draw via local_compressibility_grid's
;; initial-components), but operators HELD FIXED (grid/step, no adoption, no
;; policy).  Coexistence label after 3000 steps.
(require '[clojure.string :as str])
(let [src (slurp "scripts/local_compressibility_grid.clj")
      driver "(apply -main *command-line-args*)"]
  (load-string (str/replace src driver "")))
(require '[futon5.ca.core :as ca] '[futon5.exotype.grid :as grid])

(def initial-components @(ns-resolve (find-ns 'local-compressibility-grid) 'initial-components))

(def WIDTH 250)
(def STEPS 3000)
(def W 15)
(def SEEDS (vec (range 2026102000 2026102006)))

(defn run-ref [seed]
  (let [{:keys [genotype phenotype exotypes]} (initial-components seed)
        st {:arm :heterogeneous-fixed :seed seed :time 0
            :genotype genotype :phenotype phenotype :exotypes exotypes}]
    (loop [s st t 0
           run-len (vec (repeat WIDTH 1))
           last-change 0
           late-settled []]
      (if (= t STEPS)
        (let [settled (/ (reduce + late-settled) (max 1 (count late-settled)))
              absorbed (<= last-change (- STEPS 100))]
          {:seed seed :absorbed absorbed :settled settled
           :coexist (and (not absorbed) (<= 0.02 settled 0.98))})
        (let [prev (:phenotype s)
              s' (grid/step s)
              phe (:phenotype s')
              changed (mapv not= prev phe)
              run-len' (mapv (fn [c r] (if c 1 (inc r))) changed run-len)
              settled-frac (/ (double (count (filter #(> % W) run-len'))) WIDTH)]
          (recur s' (inc t) run-len'
                 (if (some true? changed) (inc t) last-change)
                 (if (>= t (- STEPS 200)) (conj late-settled settled-frac) late-settled)))))))

(println "seed          absorbed  settled  coexist")
(let [rs (mapv (fn [seed]
                 (let [r (run-ref seed)]
                   (printf "%d %9s %8.3f %8s%n" seed (:absorbed r)
                           (double (:settled r)) (:coexist r))
                   (flush) r))
               SEEDS)]
  (printf "%ncoexisting %d/%d   absorbed %d/%d%n"
          (count (filter :coexist rs)) (count rs)
          (count (filter :absorbed rs)) (count rs)))
