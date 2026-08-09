;; Adiabatic hysteresis sweep (T8 discriminator; contribution.edn, 2026-08-09).
;;
;; Question (Gross & Blasius 2006 reading): when the substrate co-evolves with
;; the state, a continuous absorbing transition classically becomes first order,
;; with bistability and hysteresis.  A first-order transition ALSO presents as
;; "no critical point" in a finite-size scan -- so the paper's broad-crossover
;; claim is under-determined until an up/down sweep either shows a loop
;; (bistability: freeze point != melt point) or does not.
;;
;; Protocol: one trajectory per seed at kappa = 0.1 (the freezing arm of the
;; Part III bisection).  beta is laddered up through the freezing regime and
;; back down, 400 steps per plateau; :policy-precision is assoc'd between steps
;; (the inner-loop controller pattern, exotype_inner_loop.clj:307-318), so no
;; re-initialisation and no RNG discontinuity.  Per plateau we record the mean
;; settled fraction over the last 200 steps and whether the phenotype changed
;; at all in the last 100 (plateau-absorbed).  Hysteresis = the down-branch
;; settled fraction differing from the up-branch at matching beta; the
;; interesting sub-question is whether a lattice frozen solid at high beta
;; re-melts when beta returns to values where fresh runs stay live.
;;
;; Frozen/settled definition matches the paper (w = 15), lifted from
;; boundary_invasion_rescue.clj:120-122.
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
(def DWELL 2500)
(def BETAS-UP [4.0 8.0 16.0 32.0])
(def BETAS-DOWN (vec (reverse (butlast BETAS-UP))))
(def LADDER (vec (concat (map vector BETAS-UP (repeat :up))
                         (map vector BETAS-DOWN (repeat :down)))))
(def SEEDS (vec (range 2026102000 2026102006)))

(defn checked-step [state]
  (let [s' (tuning/step state)]
    (assert (= (:epistemic-coefficient s') (:epistemic-coefficient state)) "kappa dropped")
    (assert (= (:arm s') (:arm state)) "arm dropped")
    s'))

(defn run-seed [seed]
  (let [frozen-traj (atom [])
        plateaus (atom [])]
    (loop [state (metaca-state (first BETAS-UP) KAPPA seed)
           ladder LADDER
           t 0
           run-len (vec (repeat WIDTH 1))]
      (if (empty? ladder)
        {:seed seed :plateaus @plateaus :frozen-traj @frozen-traj}
        (let [[beta dir] (first ladder)
              ;; run one plateau of DWELL steps at this beta (2500 > cold-start
              ;; freezing time ~1722, so each plateau can express its phase)
              [state' t' run-len' tail-settled tail-changed?]
              (loop [s (assoc state :policy-precision beta)
                     tt t rl run-len
                     tail-settled [] tail-changed? false k 0]
                (if (= k DWELL)
                  [s tt rl tail-settled tail-changed?]
                  (let [prev-phe (:phenotype s)
                        s' (checked-step s)
                        changed (mapv not= prev-phe (:phenotype s'))
                        rl' (mapv (fn [c r] (if c 1 (inc r))) changed rl)
                        settled-frac (/ (double (count (filter #(> % W) rl'))) WIDTH)
                        late? (>= k (- DWELL 200))
                        last100? (>= k (- DWELL 100))]
                    (when (zero? (mod tt 25))
                      (swap! frozen-traj conj [tt beta (name dir) settled-frac]))
                    (recur s' (inc tt) rl'
                           (if late? (conj tail-settled settled-frac) tail-settled)
                           (or tail-changed? (and last100? (some true? changed) true))
                           (inc k)))))
              mean-settled (/ (reduce + tail-settled) (max 1 (count tail-settled)))]
          (swap! plateaus conj {:beta beta :dir dir
                                :settled (double mean-settled)
                                :plateau-absorbed (not tail-changed?)})
          (recur state' (rest ladder) t' run-len'))))))

(println "hysteresis sweep: kappa" KAPPA "width" WIDTH
         "dwell" DWELL "ladder" (mapv first LADDER))
(println "seeds" SEEDS)
(let [results (doall (pmap (fn [seed]
                             (let [r (run-seed seed)]
                               (println "  done seed" seed)
                               (flush) r))
                           SEEDS))]
  (spit "reports/exotype-hysteresis-sweep.edn"
        (pr-str (mapv #(dissoc % :frozen-traj) results)))
  (spit "reports/exotype-hysteresis-sweep-traj.edn"
        (pr-str (mapv #(select-keys % [:seed :frozen-traj]) results)))
  (println)
  (println "beta     up-settled (per seed)                    down-settled (per seed)               up-abs down-abs")
  (doseq [beta BETAS-UP]
    (let [pick (fn [dir] (for [r results
                               p (:plateaus r)
                               :when (and (= beta (:beta p)) (= dir (:dir p)))]
                           p))
          ups (pick :up) downs (pick :down)
          fmt (fn [ps] (if (empty? ps) "-"
                           (str/join " " (map #(format "%.2f" (:settled %)) ps))))]
      (printf "%-8s %-42s %-40s %d/%d   %s%n"
              (str beta) (fmt ups) (fmt downs)
              (count (filter :plateau-absorbed ups)) (max 1 (count ups))
              (if (empty? downs) "-"
                  (str (count (filter :plateau-absorbed downs)) "/" (count downs))))))
  (println)
  (println "HYSTERESIS_DONE"))
