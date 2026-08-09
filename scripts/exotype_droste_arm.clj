;; The Droste arm (T4 follow-up; contribution.edn + TN-nucleation-bistability,
;; 2026-08-09).
;;
;; Droste, Do & Gross 2013: local self-organisation to criticality succeeds
;; with an ASYMMETRIC rule -- tentative while no information, decisive on
;; one-sided evidence -- because only one phase carries information about the
;; global state.  The closure's negative covers symmetric, estimator-shaped
;; statistics only; this arm tests the one-sided design class it left open.
;;
;; The certificate, from the nucleation triad: a long-settled cell is
;; unilateral evidence of the frozen phase (live cells occur in both
;; macrostates and say nothing).  Transient islands under churn are <= ~7
;; cells and dissolve; established domains invade.  So: act on age.
;;
;; Rule (strictly local, per cell): if own phenotype has been unchanged for
;; W* steps, take one uniform random vocabulary write at that cell (decisive);
;; otherwise do nothing (tentative).  Refractory W steps after firing so a
;; write that fails to unfreeze is not hammered.
;;
;; Arms (cold start, beta = 16, kappa = 0.1 -- the absorbing regime):
;;   :policy       baseline (absorbs; known)
;;   :droste-30    W* = 30  (eager: fires on anything twice the settled window)
;;   :droste-100   W* = 100 (patient: fires only on old, established structure)
;;   :yoked-30/-100  same per-step write COUNT as the matching droste arm's own
;;                 lattice, at uniformly random cells (the paper's yoke
;;                 discipline).  If yoke == droste, placement carries nothing
;;                 even with a one-sided trigger; if droste holds coexistence
;;                 where its yoke does not (or at lower dose than undirected
;;                 noise's ~1 write per cell-dwell), placement DOES carry
;;                 something once the trigger is one-sided.
;;
;; Readouts per run (3000 steps, matching the interventions horizon): absorbed?,
;; late frozen fraction, late settled-run WIDTH distribution (median, frac <= 2:
;; the foam-vs-regions gate), writes/step, frozen trajectory every 25.
(require '[clojure.string :as str])

(let [src (slurp "scripts/local_compressibility_grid.clj")
      driver "(apply -main *command-line-args*)"]
  (when-not (str/includes? src driver)
    (throw (ex-info "driver form not found" {})))
  (load-string (str/replace src driver "")))
(require '[futon5.ca.core :as ca] '[futon5.exotype.grid :as grid]
         '[futon5.exotype.self-tuning :as tuning])

(def objective-ns (find-ns 'local-compressibility-grid))
(def metaca-state @(ns-resolve objective-ns 'metaca-state))

(def WIDTH 250)
(def STEPS 3000)
(def W 15)
(def BETA 16.0)
(def KAPPA 0.1)
(def SEEDS (vec (range 2026102000 2026102006)))
(def ARMS [:policy :droste-30 :droste-100 :yoked-30 :yoked-100])
(def kinds (vec grid/exotype-kinds))

(defn wstar [arm] (case arm (:droste-30 :yoked-30) 30 (:droste-100 :yoked-100) 100 nil))
(defn droste? [arm] (str/starts-with? (name arm) "droste"))
(defn yoked? [arm] (str/starts-with? (name arm) "yoked"))

(defn run-arm [arm seed]
  (let [rng (java.util.Random. (ca/mix-seed (+ seed (hash arm))))
        frozen-traj (atom [])
        widths (atom [])
        write-count (atom 0)]
    (loop [state (metaca-state BETA KAPPA seed)
           t 0
           run-len (vec (repeat WIDTH 1))
           refractory (vec (repeat WIDTH 0))
           last-change 0
           late-settled []]
      (if (= t STEPS)
        {:arm arm :seed seed
         :absorbed (<= last-change (- STEPS 100))
         :last-change last-change
         :settled (/ (reduce + late-settled) (max 1 (count late-settled)))
         :coexist (and (> last-change (- STEPS 100))
                       (<= 0.02 (/ (reduce + late-settled) (max 1 (count late-settled))) 0.98))
         :writes-per-step (/ (double @write-count) STEPS)
         :widths @widths
         :frozen-traj @frozen-traj}
        (let [prev-phe (:phenotype state)
              advanced (tuning/step state)
              phe (:phenotype advanced)
              changed (mapv not= prev-phe phe)
              run-len' (mapv (fn [c r] (if c 1 (inc r))) changed run-len)
              settled (mapv #(> % W) run-len')
              refractory' (mapv #(max 0 (dec %)) refractory)
              w* (wstar arm)
              fire (cond
                     (droste? arm)
                     (vec (filter #(and (> (nth run-len' %) w*)
                                        (zero? (nth refractory' %)))
                                  (range WIDTH)))
                     (yoked? arm)
                     ;; same count as this arm's own droste trigger would give,
                     ;; evaluated on THIS lattice, but placed at random cells
                     (let [n (count (filter #(and (> (nth run-len' %) w*)
                                                  (zero? (nth refractory' %)))
                                            (range WIDTH)))]
                       (vec (repeatedly n #(.nextInt rng WIDTH))))
                     :else [])
              exo' (reduce (fn [e i]
                             (swap! write-count inc)
                             (assoc e i (nth kinds (.nextInt rng (count kinds)))))
                           (:exotypes advanced) fire)
              refractory'' (if (droste? arm)
                             (reduce (fn [r i] (assoc r i W)) refractory' fire)
                             ;; yoke arms carry refractory on the TRIGGER cells so
                             ;; the count stays matched to the droste cadence
                             (reduce (fn [r i] (assoc r i W)) refractory'
                                     (filter #(and (> (nth run-len' %) w*)
                                                   (zero? (nth refractory' %)))
                                             (when w* (range WIDTH)))))
              last-change' (if (some true? changed) (inc t) last-change)
              settled-frac (/ (double (count (filter true? settled))) WIDTH)]
          (when (zero? (mod t 25))
            (swap! frozen-traj conj [t settled-frac]))
          (when (and (>= t (- STEPS 500)) (zero? (mod t 10)) (some true? settled))
            (if (every? true? settled)
              (swap! widths conj WIDTH)
              (let [start (first (filter #(not (nth settled %)) (range WIDTH)))
                    rot (mapv #(nth settled (mod (+ start %) WIDTH)) (range WIDTH))]
                (loop [i 0 cur 0]
                  (cond
                    (= i WIDTH) (when (pos? cur) (swap! widths conj cur))
                    (nth rot i) (recur (inc i) (inc cur))
                    :else (do (when (pos? cur) (swap! widths conj cur))
                              (recur (inc i) 0)))))))
          (recur (assoc advanced :exotypes exo')
                 (inc t) run-len' refractory'' last-change'
                 (if (>= t (- STEPS 200)) (conj late-settled settled-frac) late-settled)))))))

(defn width-summary [ws]
  (if (empty? ws) {:median nil :frac<=2 nil}
      (let [s (vec (sort ws))]
        {:median (nth s (quot (count s) 2))
         :frac<=2 (/ (double (count (filter #(<= % 2) ws))) (count ws))})))

(println "droste arm: beta" BETA "kappa" KAPPA "width" WIDTH "steps" STEPS)
(println "arms" ARMS "seeds" SEEDS)
(let [specs (for [arm ARMS seed SEEDS] [arm seed])
      results (doall (pmap (fn [[arm seed]]
                             (let [r (run-arm arm seed)]
                               (println "  done" arm seed
                                        "absorbed" (:absorbed r)
                                        "settled" (format "%.3f" (double (:settled r)))
                                        "writes/step" (format "%.2f" (:writes-per-step r)))
                               (flush) r))
                           specs))]
  (spit "reports/exotype-droste-arm.edn"
        (pr-str (mapv #(dissoc % :frozen-traj) results)))
  (spit "reports/exotype-droste-arm-traj.edn"
        (pr-str (mapv #(select-keys % [:arm :seed :frozen-traj]) results)))
  (println)
  (println "arm          coexist absorbed mean-settled writes/step width-med width<=2")
  (doseq [arm ARMS]
    (let [rs (filter #(= arm (:arm %)) results)
          co (count (filter :coexist rs))
          ab (count (filter :absorbed rs))
          ms (/ (reduce + (map (comp double :settled) rs)) (count rs))
          wr (/ (reduce + (map :writes-per-step rs)) (count rs))
          {:keys [median frac<=2]} (width-summary (mapcat :widths rs))]
      (printf "%-12s %4d/%d %6d/%d %10.3f %11.2f %9s %8s%n"
              (name arm) co (count rs) ab (count rs) ms wr
              (str median) (if frac<=2 (format "%.2f" frac<=2) "-"))))
  (println)
  (println "DROSTE_DONE"))
