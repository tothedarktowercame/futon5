;; Spacetime dump for the boundary-invasion figure (fable, 2026-08-08).
;; Re-runs the four rescue arms at the anchor seed (2026102000) with identical
;; dynamics to boundary_invasion_rescue.clj, dumping per-row phenotype and
;; settled bits.  Output: figs/binv-<arm>-{phe,set}.txt (one row per line).
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
(def DWELL-LO 15)
(def DWELL-HI 25)
(def SEED 2026102000)
(def kinds (vec grid/exotype-kinds))

(defn dwell-plan [seed]
  {:dwell (vec (for [i (range WIDTH)]
                 (ca/with-mixed-seed (+ seed (* 7919 i))
                   (+ DWELL-LO (ca/rnd-int (inc (- DWELL-HI DWELL-LO)))))))
   :offset (vec (for [i (range WIDTH)]
                  (ca/with-mixed-seed (+ seed 13 (* 104729 i))
                    (ca/rnd-int DWELL-HI))))})

(defn run-dump [arm seed]
  (let [{:keys [dwell offset]} (dwell-plan seed)
        rng (java.util.Random. (ca/mix-seed (+ seed (hash arm))))]
    (with-open [pw (io/writer (str "figs/binv-" (name arm) "-phe.txt"))
                sw (io/writer (str "figs/binv-" (name arm) "-set.txt"))
                ow (io/writer (str "figs/binv-" (name arm) "-ovr.txt"))]
      (loop [state (metaca-state GAMMA KAPPA seed)
             t 0
             run-len (vec (repeat WIDTH 1))]
        (when (< t STEPS)
          (let [prev-phe (:phenotype state)
                advanced (metaca-step state)
                phe (:phenotype advanced)
                changed (mapv not= prev-phe phe)
                run-len' (mapv (fn [c r] (if c 1 (inc r))) changed run-len)
                settled (mapv #(> % W) run-len')
                pure? (fn [i] (let [a (nth settled (mod (dec i) WIDTH))
                                    b (nth settled i)
                                    c (nth settled (mod (inc i) WIDTH))]
                                (= a b c)))
                mixed? (fn [i] (not (pure? i)))
                due? (fn [i] (zero? (mod (- t (nth offset i)) (nth dwell i))))
                fire (when (and (>= t START) (not= arm :policy))
                       (vec (filter #(and (due? %) (pure? %)) (range WIDTH))))
                exo (:exotypes advanced)
                [exo' fired]
                (case arm
                  :policy [exo []]
                  :invade-adopt
                  (reduce (fn [[e fs] i]
                            (let [l (mod (dec i) WIDTH) r (mod (inc i) WIDTH)
                                  cands (filterv mixed? [l r])]
                              (if (seq cands)
                                [(assoc e i (nth exo (nth cands (.nextInt rng (count cands))))) (conj fs i)]
                                [e fs])))
                          [exo []] fire)
                  :invade-mutate
                  (reduce (fn [[e fs] i]
                            [(assoc e i (nth kinds (.nextInt rng (count kinds)))) (conj fs i)])
                          [exo []] fire)
                  :yoked
                  (reduce (fn [[e fs] _]
                            (let [i (.nextInt rng WIDTH)]
                              [(assoc e i (nth kinds (.nextInt rng (count kinds)))) (conj fs i)]))
                          [exo []] fire))]
            (.write pw (str phe "\n"))
            (.write sw (str (apply str (map #(if % \1 \0) settled)) "\n"))
            (.write ow (str (str/join " " fired) "\n"))
            (recur (assoc advanced :exotypes exo') (inc t) run-len')))))
    (println "dumped" (name arm))
    (flush)))

(doseq [arm [:policy :invade-adopt :invade-mutate :yoked]]
  (run-dump arm SEED))
(println "DUMP_DONE")
