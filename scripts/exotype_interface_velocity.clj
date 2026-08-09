;; Interface-velocity measurement (follow-up to TN-nucleation-bistability,
;; 2026-08-09).  The direct order-of-transition probe: at beta = 16 the frozen
;; phase absorbs from a cold start but never nucleates from churn.  Does it at
;; least INVADE when present?  Splice half a genuinely absorbed lattice against
;; half a genuinely churning one and watch the two fronts.  Triangulates with
;; the boundary-blast seam dynamics already in the paper (Figure 16 bottom
;; row), which perturbs a live seam with operator writes; here the seam is
;; unperturbed phase competition.
;;
;; Construction per seed:
;;   frozen donor: cold start at beta = 16, 2500 steps (absorbed; checked)
;;   live donor:   cold start at beta = 4,  2500 steps (developed churn)
;;   splice:       cells 0..124 genotype/phenotype/exotype from the frozen
;;                 donor, 125..249 from the live donor; :time 2500; beta = 16
;;   run:          5000 further steps; settled fraction and largest settled
;;                 run recorded every 25 steps.
;; Readout: largest-settled-run width over time.  Growing -> frozen invades
;; (first-order with frozen favoured; nucleation was the only barrier).
;; Shrinking -> churn melts even an established frozen phase (the cold-start
;; freeze is a global-initialisation effect through and through).  Static ->
;; pinned interface.
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
(def HALF 125)
(def W 15)
(def KAPPA 0.1)
(def HIGH-BETA 16.0)
(def LIVE-BETA 4.0)
(def DONOR-STEPS 2500)
(def RUN-STEPS 5000)
(def SEEDS (vec (range 2026102000 2026102006)))

(defn advance [state steps]
  (nth (iterate tuning/step state) steps))

(defn largest-settled-run [settled]
  ;; largest run of true on the ring
  (if (every? true? settled)
    WIDTH
    (if (not-any? true? settled)
      0
      (let [start (first (filter #(not (nth settled %)) (range WIDTH)))
            rot (mapv #(nth settled (mod (+ start %) WIDTH)) (range WIDTH))]
        (loop [i 0 cur 0 best 0]
          (cond
            (= i WIDTH) (max best cur)
            (nth rot i) (recur (inc i) (inc cur) best)
            :else (recur (inc i) 0 (max best cur))))))))

(defn splice [a b]
  (vec (concat (take HALF a) (drop HALF b))))

(defn run-seed [seed]
  (let [frozen-donor (advance (metaca-state HIGH-BETA KAPPA seed) DONOR-STEPS)
        live-donor (advance (metaca-state LIVE-BETA KAPPA seed) DONOR-STEPS)
        base (metaca-state HIGH-BETA KAPPA seed)
        spliced (assoc base
                       :genotype (splice (:genotype frozen-donor) (:genotype live-donor))
                       :phenotype (splice (:phenotype frozen-donor) (:phenotype live-donor))
                       :exotypes (splice (:exotypes frozen-donor) (:exotypes live-donor))
                       :time DONOR-STEPS)
        traj (atom [])]
    (loop [s spliced
           t 0
           ;; seed run-lengths so the frozen half starts counted as settled
           rl (vec (concat (repeat HALF (inc W)) (repeat (- WIDTH HALF) 1)))]
      (if (= t RUN-STEPS)
        (let [settled (mapv #(> % W) rl)]
          {:seed seed
           :donor-frozen-settled 1.0
           :final-settled (/ (double (count (filter true? settled))) WIDTH)
           :final-largest-run (largest-settled-run settled)
           :traj @traj})
        (let [prev (:phenotype s)
              s' (tuning/step s)
              changed (mapv not= prev (:phenotype s'))
              rl' (mapv (fn [c r] (if c 1 (inc r))) changed rl)
              settled (mapv #(> % W) rl')]
          (when (zero? (mod t 25))
            (swap! traj conj [t
                              (/ (double (count (filter true? settled))) WIDTH)
                              (largest-settled-run settled)]))
          (recur s' (inc t) rl'))))))

(println "interface velocity: donors" DONOR-STEPS "steps (frozen@beta" HIGH-BETA
         ", live@beta" LIVE-BETA "); spliced run" RUN-STEPS "steps at beta" HIGH-BETA
         "| kappa" KAPPA "width" WIDTH)
(println "seeds" SEEDS)
(let [results (doall (pmap (fn [seed]
                             (let [r (run-seed seed)]
                               (println "  done seed" seed
                                        "final-settled" (format "%.3f" (:final-settled r))
                                        "final-largest-run" (:final-largest-run r))
                               (flush) r))
                           SEEDS))]
  (spit "reports/exotype-interface-velocity.edn"
        (pr-str (mapv #(dissoc % :traj) results)))
  (spit "reports/exotype-interface-velocity-traj.edn"
        (pr-str (mapv #(select-keys % [:seed :traj]) results)))
  (println)
  (println "seed         t=0    t=500  t=1000 t=2500 t=5000   (largest settled run, cells)")
  (doseq [r results]
    (let [at (fn [tt] (or (some (fn [[t _ lr]] (when (= t tt) lr)) (:traj r)) "-"))]
      (printf "%d  %5s  %5s  %5s  %5s  %5s%n"
              (:seed r) (str (at 0)) (str (at 500)) (str (at 1000)) (str (at 2500))
              (str (:final-largest-run r)))))
  (println)
  (println "INTERFACE_DONE"))
