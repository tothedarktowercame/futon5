;; Boundary-invasion rescue experiment (fable, 2026-08-08).
;;
;; Arena: the SHEET system (local_compressibility_grid geometry, width 250,
;; per-cell EFE operator selection) at beta = 16, kappa = 0.1 -- the deep-freeze
;; arm of the Part III bisection, which absorbs (paper: t = 1722 at the pinned
;; seed; "outcome seed-robust, timing not").  This is the one arena in the
;; project where the uncontrolled system actually LEAVES the mixed regime, so a
;; find/hold claim has headroom (the gate arena coexists uncontrolled 24/24;
;; see none_label_check.clj).
;;
;; Joe's hypothesis: a strictly local rule that notices phase boundaries and
;; provokes rule-mutations that invade from them can hold coexistence where the
;; policy alone freezes.  Under the strict-locality audit this is the
;; indicator-type family: the ONLY calibration-free interior preference --
;; "locale mixed" = at least one settled and one unsettled cell in radius 1 --
;; a predicate with structural edges, satiating, unable to Goodhart by ratchet.
;; The L*/tau_c measurement (lstar_tau_measure.clj) killed ESTIMATION-based
;; local steering (P(rank) <= 0.571 at every window, tau_c ~ 6); it does not
;; bind this rule, which uses the predicate to decide WHERE to act, not to
;; rank operators.
;;
;; Arms (shared substrate seeds; overrides drawn from per-arm tagged streams
;; that do not touch the policy's own seeded streams):
;;   :policy        unmodified EFE policy (baseline; expected to absorb)
;;   :invade-adopt  a due cell whose radius-1 locale is PURE (all-settled or
;;                  all-unsettled) copies the exotype of a neighbour whose
;;                  locale is MIXED, if one exists; else nothing.  Invasion
;;                  strictly from the boundary, one cell per dwell.  Uses the
;;                  predicate for WHERE and the neighbour's operator for WHAT.
;;   :invade-mutate a due pure-locale cell takes a uniform random vocabulary
;;                  draw.  WHERE only; WHAT is noise.
;;   :yoked         same COUNT of overrides as the predicate would fire in this
;;                  arm's own lattice, at uniformly random cells, random kinds.
;;                  Neither WHERE nor WHAT.  If this matches the invade arms,
;;                  placement carries nothing and the mechanism is churn.
;;
;; An override is one post-step exotype write: the written operator gets
;; exactly one application at that cell on the next step (genotype-step reads
;; the state's exotypes), after which the policy stream resumes.  Persistence
;; is through the rewritten genotype byte, i.e. it is a provoked MUTATION, not
;; a sustained occupation.
;;
;; Dwell discipline: per-cell dwell ~ uniform[15,25], random phase (gate
;; convention; jitter is load-bearing against breather entrainment).
;; Overrides begin at t = 40 (settled bits need w+1 = 16 rows).
;;
;; Outcomes per run: absorbed? (no phenotype change in the last 100 steps),
;; mean settled fraction + interface density S over the last 200, coexistence
;; label, settled-domain WIDTH distribution (sampled every 10 of last 500
;; steps; Goodhart gate: median and fraction <= 2), settled-interval LIFETIME
;; mass in [w, 2w] (breather gate, duration-weighted), overrides/step, frozen
;; trajectory every 25 steps.
(require '[clojure.string :as str] '[clojure.java.io :as io])

(let [src (slurp "scripts/local_compressibility_grid.clj")
      driver "(apply -main *command-line-args*)"]
  (when-not (str/includes? src driver)
    (throw (ex-info "driver form not found" {})))
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
(def DWELL-LO 15)
(def DWELL-HI 25)
(def SEEDS (vec (range 2026102000 2026102006)))
(def ARMS [:policy :invade-adopt :invade-mutate :yoked])
(def kinds (vec grid/exotype-kinds))

(defn dwell-plan [seed]
  {:dwell (vec (for [i (range WIDTH)]
                 (ca/with-mixed-seed (+ seed (* 7919 i))
                   (+ DWELL-LO (ca/rnd-int (inc (- DWELL-HI DWELL-LO)))))))
   :offset (vec (for [i (range WIDTH)]
                  (ca/with-mixed-seed (+ seed 13 (* 104729 i))
                    (ca/rnd-int DWELL-HI))))})

(defn run-arm [arm seed]
  (let [{:keys [dwell offset]} (dwell-plan seed)
        rng (java.util.Random. (ca/mix-seed (+ seed (hash arm))))
        widths (atom [])          ; settled-run widths, sampled late
        lifetimes (atom [])       ; completed settled-interval durations
        frozen-traj (atom [])
        override-count (atom 0)]
    (loop [state (metaca-state GAMMA KAPPA seed)
           t 0
           run-len (vec (repeat WIDTH 1))     ; constant-block row count
           settled-since (vec (repeat WIDTH nil))
           last-change 0
           late-S [] late-settled []]
      (if (= t STEPS)
        (let [absorbed (<= last-change (- STEPS 100))
              settled-now (mapv #(> % W) run-len)]
          ;; close open settled intervals
          (doseq [i (range WIDTH)]
            (when-let [s (nth settled-since i)]
              (swap! lifetimes conj (- t s))))
          {:arm arm :seed seed :absorbed absorbed
           :last-change last-change
           :S (/ (reduce + late-S) (max 1 (count late-S)))
           :settled (/ (reduce + late-settled) (max 1 (count late-settled)))
           :coexist (and (not absorbed)
                         (<= 0.02 (/ (reduce + late-settled)
                                     (max 1 (count late-settled))) 0.98))
           :overrides-per-step (/ (double @override-count) STEPS)
           :widths @widths :lifetimes @lifetimes
           :frozen-traj @frozen-traj
           :final-settled-frac (/ (double (count (filter true? settled-now))) WIDTH)})
        (let [prev-phe (:phenotype state)
              advanced (metaca-step state)
              phe (:phenotype advanced)
              changed (mapv not= prev-phe phe)
              run-len' (mapv (fn [c r] (if c 1 (inc r))) changed run-len)
              settled (mapv #(> % W) run-len')
              ;; settled-interval bookkeeping
              settled-since'
              (vec (for [i (range WIDTH)]
                     (let [s (nth settled-since i) now (nth settled i)]
                       (cond (and now (nil? s)) t
                             (and (not now) s) (do (swap! lifetimes conj (- t s)) nil)
                             :else s))))
              last-change' (if (some true? changed) (inc t) last-change)
              pure? (fn [i] (let [a (nth settled (mod (dec i) WIDTH))
                                  b (nth settled i)
                                  c (nth settled (mod (inc i) WIDTH))]
                              (= a b c)))
              mixed? (fn [i] (not (pure? i)))
              due? (fn [i] (zero? (mod (- t (nth offset i)) (nth dwell i))))
              fire (when (and (>= t START) (not= arm :policy))
                     (vec (filter #(and (due? %) (pure? %)) (range WIDTH))))
              exo (:exotypes advanced)
              exo' (case arm
                     :policy exo
                     :invade-adopt
                     (reduce (fn [e i]
                               (let [l (mod (dec i) WIDTH) r (mod (inc i) WIDTH)
                                     cands (filterv mixed? [l r])]
                                 (if (seq cands)
                                   (do (swap! override-count inc)
                                       (assoc e i (nth exo (nth cands (.nextInt rng (count cands))))))
                                   e)))
                             exo fire)
                     :invade-mutate
                     (reduce (fn [e i]
                               (swap! override-count inc)
                               (assoc e i (nth kinds (.nextInt rng (count kinds)))))
                             exo fire)
                     :yoked
                     (reduce (fn [e _]
                               (swap! override-count inc)
                               (assoc e (.nextInt rng WIDTH)
                                      (nth kinds (.nextInt rng (count kinds)))))
                             exo fire))
              ;; late-window statistics
              S-now (when (>= t (- STEPS 200))
                      (/ (double (reduce + (for [i (range WIDTH)]
                                             (if (mixed? i) 1 0))))
                         WIDTH))
              settled-frac (/ (double (count (filter true? settled))) WIDTH)]
          (when (zero? (mod t 25))
            (swap! frozen-traj conj [t settled-frac]))
          (when (and (>= t (- STEPS 500)) (zero? (mod t 10)))
            ;; settled-run widths on the ring
            (let [bits settled]
              (when (some true? bits)
                (if (every? true? bits)
                  (swap! widths conj WIDTH)
                  ;; rotate to start at an unsettled cell, then scan runs
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
                 (if (>= t (- STEPS 200)) (conj late-settled settled-frac) late-settled)))))))

(defn summarise-widths [ws]
  (if (empty? ws) {:median nil :frac<=2 nil}
    (let [s (vec (sort ws))]
      {:median (nth s (quot (count s) 2))
       :frac<=2 (/ (double (count (filter #(<= % 2) ws))) (count ws))})))

(defn lifetime-breather-mass [ls]
  (if (empty? ls) nil
    (let [tot (reduce + ls)
          band (reduce + (filter #(<= W % (* 2 W)) ls))]
      (when (pos? tot) (/ (double band) tot)))))

(println "boundary-invasion rescue: beta" GAMMA "kappa" KAPPA
         "width" WIDTH "steps" STEPS)
(println "arms" ARMS "seeds" SEEDS)
(let [specs (for [arm ARMS seed SEEDS] [arm seed])
      results (doall (pmap (fn [[arm seed]]
                             (let [r (run-arm arm seed)]
                               (println "  done" arm seed
                                        "absorbed" (:absorbed r)
                                        "settled" (format "%.3f" (double (:settled r)))
                                        "S" (format "%.3f" (double (:S r))))
                               (flush)
                               r))
                           specs))]
  (spit "reports/boundary-invasion-rescue.edn"
        (pr-str (mapv #(dissoc % :frozen-traj) results)))
  (spit "reports/boundary-invasion-rescue-traj.edn"
        (pr-str (mapv #(select-keys % [:arm :seed :frozen-traj]) results)))
  (println)
  (println "arm            coexist  absorbed  mean-S  mean-settled  ovr/step  width-med  width<=2  breather-mass")
  (doseq [arm ARMS]
    (let [rs (filter #(= arm (:arm %)) results)
          co (count (filter :coexist rs))
          ab (count (filter :absorbed rs))
          ms (/ (reduce + (map (comp double :S) rs)) (count rs))
          mset (/ (reduce + (map (comp double :settled) rs)) (count rs))
          ov (/ (reduce + (map :overrides-per-step rs)) (count rs))
          allw (mapcat :widths rs)
          {:keys [median frac<=2]} (summarise-widths allw)
          bm (lifetime-breather-mass (mapcat :lifetimes rs))]
      (printf "%-14s %4d/%d %6d/%d %8.3f %10.3f %11.2f %8s %9s %10s%n"
              (name arm) co (count rs) ab (count rs) ms mset ov
              (str median)
              (if frac<=2 (format "%.2f" frac<=2) "-")
              (if bm (format "%.2f" bm) "-"))))
  (println)
  (println "per-seed coexistence by arm:")
  (doseq [seed SEEDS]
    (printf "  seed %d: %s%n" seed
            (str/join "  " (for [arm ARMS]
                             (let [r (first (filter #(and (= arm (:arm %)) (= seed (:seed %))) results))]
                               (str (name arm) "=" (if (:coexist r) "Y" "n")))))))
  (println)
  (println "RESCUE_DONE"))
