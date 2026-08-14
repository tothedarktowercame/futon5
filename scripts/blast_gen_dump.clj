;; Genotype+phenotype dump of the four boundary-blast arms at the pinned seed
;; (fable, 2026-08-08) -- for standard-colour inspection of whether the blast
;; states are any good.  Same dynamics as boundary_blast.clj, exactly.
;; Writes figs/blastg-<arm>-{phe,gen}.txt (gen: space-separated sigils per row,
;; the exotype_sheet.clj format).
(require '[clojure.string :as str] '[clojure.java.io :as io])
(let [src (slurp "scripts/local_compressibility_grid.clj")
      driver "(apply -main *command-line-args*)"]
  (load-string (str/replace src driver "")))
(require '[futon5.ca.core :as ca] '[futon5.exotype.grid :as grid])

(def objective-ns (find-ns 'local-compressibility-grid))
(def metaca-state @(ns-resolve objective-ns 'metaca-state))
(def metaca-step @(ns-resolve objective-ns 'checked-metaca-step))

(def WIDTH 250) (def STEPS 3000) (def W 15) (def GAMMA 16.0) (def KAPPA 0.1)
(def START 40) (def SEED 2026102000)
(def kinds (vec grid/exotype-kinds))

(defn dwell-plan [seed lo hi]
  {:dwell (vec (for [i (range WIDTH)]
                 (ca/with-mixed-seed (+ seed (* 7919 i))
                   (+ lo (ca/rnd-int (inc (- hi lo)))))))
   :offset (vec (for [i (range WIDTH)]
                  (ca/with-mixed-seed (+ seed 13 (* 104729 i)) (ca/rnd-int hi))))})

(defn run-dump [arm]
  (let [fast? (contains? #{:blast-fast :yoked-fast} arm)
        yoked? (contains? #{:yoked-blast :yoked-fast} arm)
        {:keys [dwell offset]} (dwell-plan SEED (if fast? 5 15) (if fast? 10 25))
        rng (java.util.Random. (ca/mix-seed (+ SEED (hash arm))))]
    (with-open [pw (io/writer (str "figs/blastg-" (name arm) "-phe.txt"))
                gw (io/writer (str "figs/blastg-" (name arm) "-gen.txt"))]
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
                mixed? (fn [i] (let [a (nth settled (mod (dec i) WIDTH))
                                     b (nth settled i)
                                     c (nth settled (mod (inc i) WIDTH))]
                                 (not (= a b c))))
                due? (fn [i] (zero? (mod (- t (nth offset i)) (nth dwell i))))
                fire (when (>= t START)
                       (vec (filter #(and (due? %) (mixed? %)) (range WIDTH))))
                exo (:exotypes advanced)
                exo' (reduce (fn [e i]
                               (let [target (if yoked? (.nextInt rng WIDTH) i)]
                                 (assoc e target (nth kinds (.nextInt rng (count kinds))))))
                             exo (or fire []))]
            (.write pw (str phe "\n"))
            (.write gw (str (str/join " " (:genotype advanced)) "\n"))
            (recur (assoc advanced :exotypes exo') (inc t) run-len')))))
    (println "dumped" (name arm)) (flush)))

(doall (pmap run-dump [:blast :yoked-blast :blast-fast :yoked-fast]))
(println "GEN_DUMP_DONE")
