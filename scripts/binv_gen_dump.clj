;; Genotype+phenotype dump of the four boundary-invasion-rescue arms at the
;; pinned seed (fable, 2026-08-08) -- the full-colour companion to the paper's
;; "Local intervention at the freezing arm" figure.  Dynamics mirror
;; boundary_invasion_rescue.clj exactly (same dwell-plan, same rng seeding,
;; same arm logic).  Writes figs/binvg-<arm>-{phe,gen}.txt.
(require '[clojure.string :as str] '[clojure.java.io :as io])
(let [src (slurp "scripts/local_compressibility_grid.clj")
      driver "(apply -main *command-line-args*)"]
  (load-string (str/replace src driver "")))
(require '[futon5.ca.core :as ca] '[futon5.exotype.grid :as grid])

(def objective-ns (find-ns 'local-compressibility-grid))
(def metaca-state @(ns-resolve objective-ns 'metaca-state))
(def metaca-step @(ns-resolve objective-ns 'checked-metaca-step))

(def WIDTH 250) (def STEPS 3000) (def W 15) (def GAMMA 16.0) (def KAPPA 0.1)
(def START 40) (def DWELL-LO 15) (def DWELL-HI 25) (def SEED 2026102000)
(def kinds (vec grid/exotype-kinds))

(defn dwell-plan [seed]
  {:dwell (vec (for [i (range WIDTH)]
                 (ca/with-mixed-seed (+ seed (* 7919 i))
                   (+ DWELL-LO (ca/rnd-int (inc (- DWELL-HI DWELL-LO)))))))
   :offset (vec (for [i (range WIDTH)]
                  (ca/with-mixed-seed (+ seed 13 (* 104729 i))
                    (ca/rnd-int DWELL-HI))))})

(defn run-dump [arm]
  (let [{:keys [dwell offset]} (dwell-plan SEED)
        rng (java.util.Random. (ca/mix-seed (+ SEED (hash arm))))]
    (with-open [pw (io/writer (str "figs/binvg-" (name arm) "-phe.txt"))
                gw (io/writer (str "figs/binvg-" (name arm) "-gen.txt"))]
      (loop [state (metaca-state GAMMA KAPPA SEED)
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
                exo' (case arm
                       :policy exo
                       :invade-adopt
                       (reduce (fn [e i]
                                 (let [l (mod (dec i) WIDTH) r (mod (inc i) WIDTH)
                                       cands (filterv mixed? [l r])]
                                   (if (seq cands)
                                     (assoc e i (nth exo (nth cands (.nextInt rng (count cands)))))
                                     e)))
                               exo fire)
                       :invade-mutate
                       (reduce (fn [e i]
                                 (assoc e i (nth kinds (.nextInt rng (count kinds)))))
                               exo fire)
                       :yoked
                       (reduce (fn [e _]
                                 (assoc e (.nextInt rng WIDTH)
                                        (nth kinds (.nextInt rng (count kinds)))))
                               exo fire))]
            (.write pw (str phe "\n"))
            (.write gw (str (str/join " " (:genotype advanced)) "\n"))
            (recur (assoc advanced :exotypes exo') (inc t) run-len')))))
    (println "dumped" (name arm)) (flush)))

(doall (pmap run-dump [:policy :invade-adopt :invade-mutate :yoked]))
(println "BINV_GEN_DUMP_DONE")
(shutdown-agents)
