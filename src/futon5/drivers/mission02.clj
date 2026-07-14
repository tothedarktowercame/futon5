(ns futon5.drivers.mission02
  "Mission 2: Naive evolution collapses — baseline + null.
   Runs >=30 seeded runs of run-mmca with no exotype (naive evolution),
   plus a null model (random genotype per generation, no evolution)."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [futon5.ca.core :as ca]
            [futon5.mmca.runtime :as mmca]
            [futon5.mmca.metrics :as metrics]))

(def out-dir (io/file "data/mission-02-runs"))
(def length 50)
(def generations 80)
(def num-seeds 30)

(defn rng-sigil-string [^java.util.Random rng n]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly n #(nth sigils (.nextInt rng (count sigils)))))))

(defn run-baseline [seed]
  (let [rng (java.util.Random. (long seed))
        genotype (rng-sigil-string rng length)
        result (mmca/run-mmca {:genotype genotype
                               :generations generations
                               :kernel :mutating-template
                               :operators []
                               :seed seed})
        summary (metrics/summarize-run result)]
    {:arm :baseline
     :seed seed
     :length length
     :generations generations
     :gen-history (:gen-history result)
     :phe-history (:phe-history result)
     :summary summary
     :composite-score (:composite-score summary)
     :gen-avg-entropy-n (:gen/avg-entropy-n summary)
     :gen-avg-change (:gen/avg-change summary)
     :gen-stasis-step (:gen/stasis-step summary)
     :final-unique-sigils (count (set (last (:gen-history result))))}))

(defn ^:no-doc run-null [seed]
  ;; Null model: random genotype each generation (no evolution).
  (let [rng (java.util.Random. (long seed))
        genotype (rng-sigil-string rng length)
        gen-history (vec
                     (cons genotype
                           (for [_ (range generations)]
                             (rng-sigil-string rng length))))
        fake-result {:gen-history gen-history :phe-history [] :metrics-history [] :seed seed}
        summary (metrics/summarize-run fake-result)]
    {:arm :null
     :seed seed
     :length length
     :generations generations
     :gen-history gen-history
     :phe-history []
     :summary summary
     :composite-score (:composite-score summary)
     :gen-avg-entropy-n (:gen/avg-entropy-n summary)
     :gen-avg-change (:gen/avg-change summary)
     :gen-stasis-step (:gen/stasis-step summary)
     :final-unique-sigils (count (set (last gen-history)))}))

(defn artifact-path [arm seed]
  (io/file out-dir (str (name arm) "-seed-" seed ".edn")))

(defn completed? [path]
  (when (.exists path)
    (try
      (= :complete (:status (read-string (slurp path))))
      (catch Exception _ false))))

(defn -main [& _args]
  (.mkdirs out-dir)
  (doseq [arm [:baseline :null]]
    (println (str "Arm: " arm))
    (doseq [seed (range 42 (+ 42 num-seeds))]
      (let [path (artifact-path arm seed)]
        (if (completed? path)
          (println (str "  seed " seed ": SKIPPED"))
          (do
            (print (str "  seed " seed ": running..."))
            (flush)
            (let [result (assoc (if (= arm :baseline) (run-baseline seed) (run-null seed))
                               :status :complete)]
              (spit path (pr-str result))
              (println " DONE")))))
    (println "")))
  ;; Print summary statistics
  (letfn [(load-arm [arm]
            (for [seed (range 42 (+ 42 num-seeds))
                  :let [path (artifact-path arm seed)]
                  :when (.exists path)]
              (read-string (slurp path))))
          (mean [xs] (if (empty? xs) 0.0 (/ (reduce + 0.0 xs) (count xs))))
          (ci95 [xs] (let [m (mean xs) n (count xs)]
                       (if (< n 2) 0.0
                           (* 1.96 (Math/sqrt (/ (reduce + 0.0 (map #(Math/pow (- % m) 2) xs)) n))
                              (/ (Math/sqrt n))))))]
    (println "")
    (println "=== Summary ===")
    (doseq [arm [:baseline :null]]
      (let [runs (load-arm arm)
            scores (map :composite-score runs)
            ents (map :gen-avg-entropy-n runs)
            chgs (map :gen-avg-change runs)
            uniqs (map :final-unique-sigils runs)]
        (println (str arm ":"))
        (println (str "  n=" (count runs)))
        (println (str "  composite-score: " (format "%.4f" (mean scores)) " ± " (format "%.4f" (ci95 scores))))
        (println (str "  avg-entropy-n: " (format "%.4f" (mean ents)) " ± " (format "%.4f" (ci95 ents))))
        (println (str "  avg-change: " (format "%.4f" (mean chgs)) " ± " (format "%.4f" (ci95 chgs))))
        (println (str "  final-unique-sigils: " (format "%.1f" (mean uniqs)) " ± " (format "%.1f" (ci95 uniqs)))))))
  (println "")
  (println "MISSION-02 DRIVER COMPLETE"))
