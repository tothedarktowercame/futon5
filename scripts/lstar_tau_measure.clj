;; L* and tau_c -- the two quantities that decide the strictly-local steering
;; family in advance (fable, 2026-08-08; TN-fable-strict-locality.md S3-S4).
;;
;;   L*   : the smallest window L such that a windowed local-interface sample
;;          ranks two operators by their true conditional effect with
;;          probability >= 1/2 + delta.  Measured as P(correct pairwise
;;          ranking) vs L, two ways:
;;            - :distant  random site pairs carrying different operators
;;                        (population-quality estimate, context randomised);
;;            - :adjacent neighbouring site pairs (what a CELL can actually
;;                        compare; context shared but operator-context
;;                        confounding present).
;;   tau_c: the stationarity time of the site-level context -- decorrelation
;;          time of the settled field at a site under held-fixed operators.
;;
;; Substrate: the het-fixed harness context of the SS8 action-contrast
;; measurement -- full 12-operator vocabulary, random spatial assignment,
;; operators never change, generic random initialisation (the substrate's
;; default basin, which coexists; see none_label_check.clj for the biased-IC
;; case).  Geometry matches the gate: width 120, w = 15.
;;
;; If L* > tau_c: evidence at a site decays faster than it accumulates, at
;; every decision rate, and the surviving controller family is dead in advance.
(require '[futon5.ca.core :as ca] '[futon5.exotype.grid :as grid])

(def WIDTH 120)
(def STEPS 3000)
(def SETTLE 15)
(def BURN 100)
(def WINDOWS [5 10 20 40 80 160 320 640])
(def PAIR-SAMPLES 4000)      ; ranking draws per window length per kind
(def SEEDS (range 301 309))  ; 8 seeds
(def kinds (vec (keys grid/propagators)))

(defn run-hetfixed [seed]
  (let [st (ca/with-seed seed
             {:arm :heterogeneous-fixed :seed seed :time 0
              :exotypes (vec (repeatedly WIDTH #(ca/rnd-nth kinds)))
              :genotype (vec (ca/random-sigil-string WIDTH))
              :phenotype (apply str (repeatedly WIDTH #(ca/rnd-int 2)))})]
    (loop [s st t 0 phes (transient [])]
      (if (= t STEPS)
        {:phe (persistent! phes) :exo (:exotypes st)}
        (recur (grid/step s) (inc t) (conj! phes (vec (:phenotype s))))))))

;; settled bit per (t,i), computed once by streaming a per-site quiescence
;; counter over the phenotype rows: settled at t iff the value was unchanged
;; over [t-w+1, t] (matches interface_adoption_gate.clj's settled?).
(defn settled-grid [phe]
  (let [n (count phe)]
    (loop [t 1 run (vec (repeat WIDTH 1)) out [(vec (repeat WIDTH false))]]
      (if (= t n)
        out
        (let [run' (vec (for [i (range WIDTH)]
                          (if (= (get-in phe [t i]) (get-in phe [(dec t) i]))
                            (inc (nth run i)) 1)))]
          ;; gate's settled? = unchanged over [t-w, t] (w comparisons), i.e.
          ;; a constant block of w+1 rows -> counter > SETTLE
          (recur (inc t) run' (conj out (vec (map #(> % SETTLE) run')))))))))

;; local interface bit of cell i at time t: settled-bit XOR against either
;; neighbour (ring).  This is the cell's own two interface bits averaged --
;; the design-2 arm (e) observable.
(defn iface-grid [sett]
  (vec (for [row sett]
         (vec (for [i (range WIDTH)]
                (let [me (nth row i)
                      l (nth row (mod (dec i) WIDTH))
                      r (nth row (mod (inc i) WIDTH))]
                  (/ (+ (if (not= me l) 1 0) (if (not= me r) 1 0)) 2.0)))))))

;; prefix sums down the time axis for O(1) window means per site
(defn prefix-by-site [g]
  (let [n (count g)]
    (loop [t 0 acc (vec (repeat WIDTH 0.0)) out []]
      (if (= t n)
        out
        (let [acc' (vec (map + acc (nth g t)))]
          (recur (inc t) acc' (conj out acc')))))))

(defn window-mean [pfx t L i]  ; mean over [t, t+L)
  (let [hi (nth (nth pfx (+ t L -1)) i)
        lo (if (zero? t) 0.0 (nth (nth pfx (dec t)) i))]
    (/ (- hi lo) L)))

(def TAU-CAP 400)  ; censoring cap on the lag search

(defn autocorr-tau [series]
  ;; smallest lag at which autocorrelation of the (0/1) settled series drops
  ;; below 1/e; nil for zero-variance sites; TAU-CAP if censored.
  ;; lag schedule 1..49 step 1 then 50..TAU-CAP step 5, to bound cost.
  (let [n (count series)
        xs (map #(if % 1.0 0.0) series)
        m (/ (reduce + xs) n)
        xv (vec (map #(- % m) xs))
        v0 (/ (reduce + (map #(* % %) xv)) n)
        lags (concat (range 1 50) (range 50 (inc TAU-CAP) 5))
        ac (fn [lag]
             (/ (/ (reduce + (map * (subvec xv 0 (- n lag)) (subvec xv lag n)))
                   (- n lag)) v0))]
    (when (> v0 1e-9)
      (or (first (filter #(< (ac %) 0.3678794411714423) lags))
          TAU-CAP))))

(println "running" (count SEEDS) "het-fixed seeds,"
         STEPS "steps, width" WIDTH ", vocabulary" (count kinds) "operators")
(let [runs (vec (for [seed SEEDS]
                  (let [{:keys [phe exo]} (run-hetfixed seed)
                        sett (settled-grid phe)
                        ifg (iface-grid sett)]
                    (println "  seed" seed "done")
                    (flush)
                    {:seed seed :exo exo :sett sett :pfx (prefix-by-site ifg)})))
      ;; ground truth: per-operator mean local interface across all sites
      ;; carrying it, all post-burn-in times, all seeds (random assignment =>
      ;; unconfounded population estimate)
      truth (let [sums (atom {})]
              (doseq [{:keys [exo pfx]} runs
                      i (range WIDTH)]
                (let [k (nth exo i)
                      tot (- (window-mean pfx BURN (- STEPS BURN) i) 0.0)]
                  (swap! sums update k (fnil (fn [[s c]] [(+ s tot) (inc c)]) [0.0 0]))))
              (into {} (map (fn [[k [s c]]] [k (/ s c)]) @sums)))
      _ (do (println)
            (println "per-operator true mean local interface (population, all seeds):")
            (doseq [[k v] (sort-by val truth)]
              (printf "  %-12s %.4f%n" (name k) v))
            (println))
      rank-p (fn [mode L]
               ;; sample pairs; correct iff window ordering matches truth ordering
               (let [tmax (- STEPS L 1)
                     draws (ca/with-seed (+ 7000 L (if (= mode :adjacent) 1 0))
                             (vec (for [_ (range PAIR-SAMPLES)]
                                    [(ca/rnd-int (count runs))
                                     (ca/rnd-int WIDTH)
                                     (ca/rnd-int WIDTH)
                                     (+ BURN (ca/rnd-int (- tmax BURN)))])))
                     results
                     (keep (fn [[ri i j0 t]]
                             (let [{:keys [exo pfx]} (nth runs ri)
                                   j (if (= mode :adjacent) (mod (inc i) WIDTH) j0)
                                   ka (nth exo i) kb (nth exo j)
                                   ta (get truth ka) tb (get truth kb)]
                               (when (and (not= ka kb) (> (Math/abs (- ta tb)) 1e-6))
                                 (let [wa (window-mean pfx t L i)
                                       wb (window-mean pfx t L j)]
                                   (when (not= wa wb)
                                     (= (> wa wb) (> ta tb)))))))
                           draws)]
                 {:p (when (seq results)
                       (/ (double (count (filter true? results))) (count results)))
                  :n (count results)}))]
  (println "P(correct pairwise ranking) vs window L   [ties and same-operator pairs excluded]")
  (println "L        distant-pair (n)      adjacent-pair (n)")
  (doseq [L WINDOWS]
    (let [d (rank-p :distant L) a (rank-p :adjacent L)]
      (printf "%-6d   %.4f (%d)      %.4f (%d)%n" L
              (or (:p d) -1.0) (:n d) (or (:p a) -1.0) (:n a))
      (flush)))
  (println)
  ;; tau_c across sites and seeds
  (let [taus (for [{:keys [sett]} runs
                   i (range WIDTH)
                   :let [series (mapv #(nth % i) (drop BURN sett))
                         tau (autocorr-tau series)]
                   :when tau]
               tau)
        sorted (vec (sort taus))
        q (fn [p] (nth sorted (int (* p (dec (count sorted))))))]
    (printf "tau_c (settled-field site decorrelation, %d live sites of %d):%n"
            (count sorted) (* (count runs) WIDTH))
    (printf "  median %d   q25 %d   q75 %d   q90 %d   max %d%n"
            (q 0.5) (q 0.25) (q 0.75) (q 0.9) (peek sorted))))
