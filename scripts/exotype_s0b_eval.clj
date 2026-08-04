(ns exotype-s0b-eval
  "S0b evaluation: run the EFE-driven dynamics under the 12-kind conditional model
  and measure where the system actually goes.

  This is NOT a pure-function argmin (that was done locally in TN-baldwin-reboot.md
  42.2). This runs the real trajectory: cell-decision selects exotypes step by step,
  the propagator acts, the phenotype advances. The question is whether the 12-kind
  model produces sustained dynamics or freezes, and how that compares across blend.

  Partitioned by blend per futon0/README-bare-metal.md 5. Run on zone-joe for speed.

    clojure -M scripts/exotype_s0b_eval.clj run <blend> <out.edn>
    clojure -M scripts/exotype_s0b_eval.clj report <out.md> <in.edn>..."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as st]))

(def config
  {:seeds 40
   :seed-base 20260804
   :width 80
   :steps 300
   :blends [0.0 0.1 0.25 0.5 0.75]
   ;; damage reach: single-cell phenotype flip, measure Hamming distance at t=100
   :damage-steps 100})

(defn- diffcount [a b]
  (count (filter true? (map not= a b))))

(defn- init-state [seed blend]
  (let [w (:width config)]
    (cond-> {:arm :efe-full
             :seed seed :time 0
             :exotypes (grid/initial-grid :heterogeneous-fixed w)
             :genotype (vec (ca/random-sigil-string w))
             :phenotype (ca/random-phenotype-string w)
             :lambdas (vec (repeat w 0.5))
             :self-tuning-arm :hunger-coupled
             :lambda-step-size 0.01
             :hunger-target (:hunger efe/preferences)}
      (pos? blend) (assoc :blend-strength blend))))

(defn- run-trajectory [seed blend]
  (let [st (init-state seed blend)
        final (st/run-steps st (:steps config))
        w (:width config)]
    {:seed seed :blend blend
     :final-time (:time final)
     :final-exotypes (frequencies (:exotypes final))
     :distinct-exotypes (count (distinct (:exotypes final)))
     :genotype-rule-count (count (distinct (:genotype final)))
     ;; activity: fraction of phenotype cells that differ from self's neighbours
     :phenotype-activity (/ (count (filter (fn [i]
                                              (not= (nth (:phenotype final) i)
                                                    (nth (:phenotype final) (mod (inc i) w))))
                                           (range w)))
                            (double w))
     ;; genotype diversity: distinct rules / width
     :genotype-diversity (/ (double (count (distinct (:genotype final)))) w)
     ;; frozen: fraction of genotype cells that haven't changed in the last step
     :frozen-fraction (/ (count (filter true? (map = (:genotype final)
                                                    (:previous-genotype final))))
                         (double w))}))

(defn- damage-reach
  "Single-cell phenotype flip at t=0, measure Hamming distance at t=100."
  [seed blend]
  (let [w (:width config)
        mid (quot w 2)
        base (init-state seed blend)
        pert (update base :phenotype
                     #(apply str (update (vec %) mid (fn [c] (if (= \0 c) \1 \0)))))]
    (loop [a base b pert t 0]
      (if (= t (:damage-steps config))
        (diffcount (:phenotype a) (:phenotype b))
        (recur (st/step a) (st/step b) (inc t))))))

(defn- mean [xs] (/ (reduce + xs) (double (count xs))))
(defn- sd [xs] (let [m (mean xs)] (Math/sqrt (mean (map #(* (- % m) (- % m)) xs)))))

(defn run-blend [blend]
  (let [seeds (range (:seed-base config) (+ (:seed-base config) (:seeds config)))
        trajectories (mapv #(run-trajectory % blend) seeds)
        reaches (mapv #(damage-reach % blend) seeds)]
    {:blend blend
     :seeds (:seeds config)
     :phenotype-activity (mean (map :phenotype-activity trajectories))
     :genotype-diversity (mean (map :genotype-diversity trajectories))
     :genotype-rule-count (mean (map :genotype-rule-count trajectories))
     :frozen-fraction (mean (map :frozen-fraction trajectories))
     :distinct-exotypes (mean (map :distinct-exotypes trajectories))
     :exotype-distribution (into (sorted-map)
                                 (merge-with +
                                             (map :final-exotypes trajectories)))
     :damage-reach (mean reaches)
     :damage-sd (sd reaches)}))

(defn -main [& [mode a & more]]
  (case mode
    "run" (let [blend (Double/parseDouble a)
                r (run-blend blend)]
            (spit (first more) (pr-str r))
            (println (format "blend %.2f -> %s  activity=%.3f diversity=%.3f damage=%.1f"
                             blend (first more)
                             (:phenotype-activity r)
                             (:genotype-diversity r)
                             (:damage-reach r))))
    "report"
    (let [rs (map (comp edn/read-string slurp) more)
          out a
          rows (for [r (sort-by :blend rs)]
                 (format "| %.2f | %.4f | %.4f | %.1f | %.4f | %.1f | %.1f | %.1f |"
                         (:blend r) (:phenotype-activity r)
                         (:genotype-diversity r)
                         (:genotype-rule-count r)
                         (:frozen-fraction r)
                         (:distinct-exotypes r)
                         (:damage-reach r) (:damage-sd r)))
          dists (for [r (sort-by :blend rs)]
                  (format "- blend %.2f: %s" (:blend r) (:exotype-distribution r)))
          content (str
                    "# S0b trajectory evaluation -- 12-kind conditional model\n\n"
                    (format "%d seeds, width %d, %d steps.\n\n"
                            (:seeds config) (:width config) (:steps config))
                    "EFE-driven dynamics (:efe-full arm, hunger-tracking self-tuning).\n"
                    "Damage reach = Hamming distance at t=100 after single-cell flip.\n\n"
                    "| blend | activity | geno-div | geno-rules | frozen | exo-kinds | damage | sd |\n"
                    "|---:|---:|---:|---:|---:|---:|---:|---:|\n"
                    (str/join "\n" rows)
                    "\n\n## Exotype distributions (summed over seeds)\n\n"
                    (str/join "\n" dists)
                    "\n")]
      (spit out content)
      (println "wrote" out))
    (println "usage: run <blend> <out.edn> | report <out.md> <in.edn>...")))

(apply -main *command-line-args*)
