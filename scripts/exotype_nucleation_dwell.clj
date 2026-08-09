;; Extended-dwell nucleation test (follow-up to the T8 hysteresis sweep,
;; contribution.edn, 2026-08-09).
;;
;; The sweep found: cold start at beta = 16 freezes solid (last change t = 1723
;; at the pinned seed, matching the paper's 1722), but a lattice arriving at
;; beta = 16-32 from developed live churn shows settled fraction 0.00 for the
;; whole 2500-step plateau, 6/6 seeds.  Question here: does the frozen phase
;; EVER nucleate from churn, given 4x the cold-start absorption time?
;;
;; Protocol: 2500 steps at beta = 4 (develop churn), then 10,000 steps at
;; beta = 16, kappa = 0.1, width 250, six seeds.  Record settled fraction every
;; 25 steps, absorption (no phenotype change in trailing 100 steps of the run),
;; and the maximum settled fraction ever reached during the high-beta dwell.
(require '[clojure.string :as str])

(let [src (slurp "scripts/local_compressibility_grid.clj")
      driver "(apply -main *command-line-args*)"]
  (when-not (str/includes? src driver)
    (throw (ex-info "driver form not found" {})))
  (load-string (str/replace src driver "")))
(require '[futon5.exotype.self-tuning :as tuning])

(def objective-ns (find-ns 'local-compressibility-grid))
(def metaca-state @(ns-resolve objective-ns 'metaca-state))

(def WIDTH 250)
(def W 15)
(def KAPPA 0.1)
(def PREP-BETA 4.0)
(def PREP 2500)
(def HIGH-BETA 16.0)
(def DWELL 10000)
(def SEEDS (vec (range 2026102000 2026102006)))

(defn run-seed [seed]
  (let [traj (atom [])]
    (loop [s (metaca-state PREP-BETA KAPPA seed)
           t 0
           rl (vec (repeat WIDTH 1))
           last-change 0
           max-settled 0.0]
      (if (= t (+ PREP DWELL))
        {:seed seed
         :final-settled (/ (double (count (filter #(> % W) rl))) WIDTH)
         :max-settled-high-beta max-settled
         :last-change last-change
         :absorbed (<= last-change (- (+ PREP DWELL) 100))
         :traj @traj}
        (let [s (if (= t PREP) (assoc s :policy-precision HIGH-BETA) s)
              prev (:phenotype s)
              s' (tuning/step s)
              changed (mapv not= prev (:phenotype s'))
              rl' (mapv (fn [c r] (if c 1 (inc r))) changed rl)
              sf (/ (double (count (filter #(> % W) rl'))) WIDTH)]
          (when (zero? (mod t 25))
            (swap! traj conj [t sf]))
          (recur s' (inc t) rl'
                 (if (some true? changed) (inc t) last-change)
                 (if (>= t PREP) (max max-settled sf) max-settled)))))))

(println "nucleation dwell: prep" PREP "steps at beta" PREP-BETA
         "then" DWELL "steps at beta" HIGH-BETA "| kappa" KAPPA "width" WIDTH)
(println "seeds" SEEDS)
(let [results (doall (pmap (fn [seed]
                             (let [r (run-seed seed)]
                               (println "  done seed" seed
                                        "final" (format "%.3f" (:final-settled r))
                                        "max@high-beta" (format "%.3f" (:max-settled-high-beta r))
                                        "absorbed" (:absorbed r)
                                        "last-change" (:last-change r))
                               (flush) r))
                           SEEDS))]
  (spit "reports/exotype-nucleation-dwell.edn"
        (pr-str (mapv #(dissoc % :traj) results)))
  (spit "reports/exotype-nucleation-dwell-traj.edn"
        (pr-str (mapv #(select-keys % [:seed :traj]) results)))
  (println)
  (println "summary: absorbed" (count (filter :absorbed results)) "/" (count results)
           "| max settled at high beta:"
           (str/join " " (map #(format "%.3f" (:max-settled-high-beta %)) results)))
  (println "NUCLEATION_DONE"))
