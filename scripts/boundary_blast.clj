;; Boundary BLAST experiment (fable, 2026-08-08; Joe's proposal).
;;
;; The rescue experiment (boundary_invasion_rescue.clj) put random operator
;; writes at PURE locales and left boundaries alone; its yoke matched it, so
;; placement carried nothing.  Joe's counter-proposal, motivated by the
;; natural boundary crossings visible at gamma=1-2, kappa=0.1 (live filaments
;; tunnelling through frozen bands under low-precision policy churn): put the
;; random writes AT the boundary -- mixed-locale cells -- to blast crossings
;; through frozen zones deliberately.  WHERE-only (a predicate), so admissible
;; under strict locality and not bound by the L*/tau_c kill condition; and
;; unlike :invade-adopt it cannot be conscripted, because random draws are not
;; the freeze front's own operators.
;;
;; Arms (beta = 16, kappa = 0.1, width 250, 3000 steps, seeds 2026102000-05,
;; same substrate streams as the rescue run; :policy baseline already measured
;; there -- absorbs 6/6):
;;   :blast        due mixed-locale cells get a uniform random vocabulary
;;                 write; dwell ~ uniform[15,25] (comparable intensity to the
;;                 rescue arms)
;;   :yoked-blast  same COUNT, uniformly random cells
;;   :blast-fast   dwell ~ uniform[5,10] -- the breach mechanism is a race
;;                 against re-settling, so hit the frontier harder
;;   :yoked-fast   same COUNT, uniformly random cells
;;
;; At the pinned seed 2026102000 each arm also dumps its phenotype sheet to
;; figs/blast-<arm>-phe.txt for figures.
(require '[clojure.string :as str] '[clojure.java.io :as io])
(let [src (slurp "scripts/local_compressibility_grid.clj")
      driver "(apply -main *command-line-args*)"]
  (load-string (str/replace src driver "")))
(require '[futon5.ca.core :as ca] '[futon5.exotype.grid :as grid])

(def objective-ns (find-ns 'local-compressibility-grid))
(def metaca-state @(ns-resolve objective-ns 'metaca-state))
(def metaca-step @(ns-resolve objective-ns 'checked-metaca-step))

(def WIDTH 250)
(def STEPS 3000)
(def W 15)
(def GAMMA 16.0)
(def KAPPA 0.1)
(def START 40)
(def SEEDS (vec (range 2026102000 2026102006)))
(def PIN 2026102000)
(def ARMS [:blast :yoked-blast :blast-fast :yoked-fast])
(def kinds (vec grid/exotype-kinds))

(defn dwell-plan [seed lo hi]
  {:dwell (vec (for [i (range WIDTH)]
                 (ca/with-mixed-seed (+ seed (* 7919 i))
                   (+ lo (ca/rnd-int (inc (- hi lo)))))))
   :offset (vec (for [i (range WIDTH)]
                  (ca/with-mixed-seed (+ seed 13 (* 104729 i)) (ca/rnd-int hi))))})

(defn run-arm [arm seed]
  (let [fast? (contains? #{:blast-fast :yoked-fast} arm)
        yoked? (contains? #{:yoked-blast :yoked-fast} arm)
        {:keys [dwell offset]} (dwell-plan seed (if fast? 5 15) (if fast? 10 25))
        rng (java.util.Random. (ca/mix-seed (+ seed (hash arm))))
        dump? (= seed PIN)
        pw (when dump? (io/writer (str "figs/blast-" (name arm) "-phe.txt")))
        widths (atom [])
        lifetimes (atom [])
        frozen-traj (atom [])
        override-count (atom 0)]
    (try
      (loop [state (metaca-state GAMMA KAPPA seed)
             t 0
             run-len (vec (repeat WIDTH 1))
             settled-since (vec (repeat WIDTH nil))
             last-change 0
             late-S [] late-settled []]
        (if (= t STEPS)
          (let [absorbed (<= last-change (- STEPS 100))]
            (doseq [i (range WIDTH)]
              (when-let [s (nth settled-since i)]
                (swap! lifetimes conj (- t s))))
            {:arm arm :seed seed :absorbed absorbed :last-change last-change
             :S (/ (reduce + late-S) (max 1 (count late-S)))
             :settled (/ (reduce + late-settled) (max 1 (count late-settled)))
             :coexist (and (not absorbed)
                           (<= 0.02 (/ (reduce + late-settled)
                                       (max 1 (count late-settled))) 0.98))
             :overrides-per-step (/ (double @override-count) STEPS)
             :widths @widths :lifetimes @lifetimes :frozen-traj @frozen-traj})
          (let [prev-phe (:phenotype state)
                advanced (metaca-step state)
                phe (:phenotype advanced)
                _ (when pw (.write pw (str phe "\n")))
                changed (mapv not= prev-phe phe)
                run-len' (mapv (fn [c r] (if c 1 (inc r))) changed run-len)
                settled (mapv #(> % W) run-len')
                settled-since'
                (vec (for [i (range WIDTH)]
                       (let [s (nth settled-since i) now (nth settled i)]
                         (cond (and now (nil? s)) t
                               (and (not now) s) (do (swap! lifetimes conj (- t s)) nil)
                               :else s))))
                last-change' (if (some true? changed) (inc t) last-change)
                mixed? (fn [i] (let [a (nth settled (mod (dec i) WIDTH))
                                     b (nth settled i)
                                     c (nth settled (mod (inc i) WIDTH))]
                                 (not (= a b c))))
                due? (fn [i] (zero? (mod (- t (nth offset i)) (nth dwell i))))
                fire (when (>= t START)
                       (vec (filter #(and (due? %) (mixed? %)) (range WIDTH))))
                exo (:exotypes advanced)
                exo' (reduce (fn [e i]
                               (swap! override-count inc)
                               (let [target (if yoked? (.nextInt rng WIDTH) i)]
                                 (assoc e target (nth kinds (.nextInt rng (count kinds))))))
                             exo (or fire []))
                S-now (when (>= t (- STEPS 200))
                        (/ (double (count (filter mixed? (range WIDTH)))) WIDTH))
                settled-frac (/ (double (count (filter true? settled))) WIDTH)]
            (when (zero? (mod t 25))
              (swap! frozen-traj conj [t settled-frac]))
            (when (and (>= t (- STEPS 500)) (zero? (mod t 10)))
              (let [bits settled]
                (when (some true? bits)
                  (if (every? true? bits)
                    (swap! widths conj WIDTH)
                    (let [start (first (filter #(not (nth bits %)) (range WIDTH)))
                          rot (mapv #(nth bits (mod (+ start %) WIDTH)) (range WIDTH))]
                      (loop [i 0 cur 0]
                        (cond
                          (= i WIDTH) (when (pos? cur) (swap! widths conj cur))
                          (nth rot i) (recur (inc i) (inc cur))
                          :else (do (when (pos? cur) (swap! widths conj cur))
                                    (recur (inc i) 0)))))))))
            (recur (assoc advanced :exotypes exo')
                   (inc t) run-len' settled-since' last-change'
                   (if S-now (conj late-S S-now) late-S)
                   (if (>= t (- STEPS 200)) (conj late-settled settled-frac) late-settled)))))
      (finally (when pw (.close pw))))))

(defn summarise-widths [ws]
  (if (empty? ws) {:median nil :frac<=2 nil :max nil}
    (let [s (vec (sort ws))]
      {:median (nth s (quot (count s) 2))
       :frac<=2 (/ (double (count (filter #(<= % 2) ws))) (count ws))
       :max (peek s)})))

(println "boundary blast: beta" GAMMA "kappa" KAPPA "arms" ARMS "seeds" SEEDS)
(let [specs (for [arm ARMS seed SEEDS] [arm seed])
      results (doall (pmap (fn [[arm seed]]
                             (let [r (run-arm arm seed)]
                               (println "  done" arm seed
                                        "absorbed" (:absorbed r)
                                        "settled" (format "%.3f" (double (:settled r)))
                                        "ovr/step" (format "%.2f" (:overrides-per-step r)))
                               (flush) r))
                           specs))]
  (spit "reports/boundary-blast.edn" (pr-str (mapv #(dissoc % :frozen-traj) results)))
  (spit "reports/boundary-blast-traj.edn"
        (pr-str (mapv #(select-keys % [:arm :seed :frozen-traj]) results)))
  (println)
  (println "arm          coexist  absorbed  mean-S  mean-settled  ovr/step  width-med  width<=2  width-max")
  (doseq [arm ARMS]
    (let [rs (filter #(= arm (:arm %)) results)
          co (count (filter :coexist rs))
          ab (count (filter :absorbed rs))
          ms (/ (reduce + (map (comp double :S) rs)) (count rs))
          mset (/ (reduce + (map (comp double :settled) rs)) (count rs))
          ov (/ (reduce + (map :overrides-per-step rs)) (count rs))
          {:keys [median frac<=2 max]} (summarise-widths (mapcat :widths rs))]
      (printf "%-12s %4d/%d %6d/%d %8.3f %10.3f %11.2f %8s %9s %8s%n"
              (name arm) co (count rs) ab (count rs) ms mset ov
              (str median) (if frac<=2 (format "%.2f" frac<=2) "-") (str max))))
  (println)
  (println "BLAST_DONE"))
