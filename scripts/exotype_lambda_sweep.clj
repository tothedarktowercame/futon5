(ns exotype-lambda-sweep
  "S-item: turn DOWN the drive toward stasis until the behaviour flips (Joe, 2026-08-04).

   WHY. With the vocabulary widened to 12, the dynamics still converges to a
   monoculture that freezes -- `:even1` rather than `:collapser`, but frozen either
   way (TN-baldwin-reboot.md 43.2). `:odd53`, the only kind with zero absorbing
   bytes, is a co-winner in the pure-function argmin and loses in dynamics. The
   diagnosis was that the CONATUS channel drives toward low hunger, i.e. toward
   stasis, and absorbing kinds are how stasis is achieved.

   If that diagnosis is right, weakening conatus should flip which kind wins.
   Lambda weights conatus in `score-policy`, so lambda is the knob.

   DESIGN. Lambda is normally SELF-TUNED per cell (`:self-tuning-arm
   :hunger-coupled`, step size 0.01, starting at 0.5), so it is not a free
   parameter -- it adapts. This sweep PINS it by setting the step size to zero and
   sweeps the pinned value, so the contrast is interpretable. Pinning is verified
   at runtime rather than assumed: `:lambda-drift` reports the largest deviation
   from the pinned value over the whole trajectory, and it must be 0.

   PREREGISTERED. Prediction: as lambda falls, the dominant exotype shifts away
   from absorbing kinds toward `:odd53`, and the frozen fraction falls.
   FALSIFIER: if NO lambda -- including 0, which removes conatus entirely --
   moves the dominant kind off the absorbing kinds, then conatus is not what
   selects freezing and the 43.2 diagnosis is wrong. That would point at the risk
   term instead, which targets a rule-change rate of 0.15 that NO propagator in
   S8 can reach (the minimum is 0.5), so every candidate is on the same side of it.

   Partitioned by LAMBDA per futon0/README-bare-metal.md 5.

     clojure -M scripts/exotype_lambda_sweep.clj run <lambda> <out.edn>
     clojure -M scripts/exotype_lambda_sweep.clj report <out.md> <in.edn>..."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as st]))

(def config
  {:lambdas [0.0 0.1 0.25 0.5 0.75 1.0]
   :seeds 40 :seed-base 20260804 :width 80 :steps 300
   :blend 0.5 :damage-steps 100})

(def absorbing
  "Absorbing-byte count per kind -- the coordinate that decides whether a kind can
   freeze at all (TN 30.3). Zero means the kind cannot freeze."
  {:odd53 0 :chaos 0 :builder 0 :identity 0 :fix2 0 :fix3 0 :fix4 0 :fix6 0
   :even1 2 :collapser 4 :even8 8 :even4 16})

(defn- init-state [seed lambda]
  (let [w (:width config)]
    (assoc {:arm :efe-full :seed seed :time 0
            :exotypes (grid/initial-grid :heterogeneous-fixed w)
            :genotype (vec (ca/random-sigil-string w))
            :phenotype (ca/random-phenotype-string w)
            :self-tuning-arm :hunger-coupled
            :hunger-target (:hunger efe/preferences)}
           :lambdas (vec (repeat w lambda))
           :lambda-step-size 0.0          ; pin it; verified below
           :blend-strength (:blend config))))

(defn- run-trajectory [seed lambda]
  (let [w (:width config)
        final (loop [s (init-state seed lambda) t 0 drift 0.0]
                (if (= t (:steps config))
                  (assoc s :lambda-drift drift)
                  (let [nx (st/step s)]
                    (recur nx (inc t)
                           (max drift (reduce max 0.0 (map #(Math/abs (- (double %) lambda))
                                                           (:lambdas nx))))))))
        kinds (frequencies (:exotypes final))
        dominant (key (apply max-key val kinds))]
    {:seed seed :lambda lambda
     :dominant dominant
     :dominant-share (/ (double (get kinds dominant)) w)
     :absorbing-share (/ (reduce + (map (fn [[k n]] (if (pos? (get absorbing k 0)) n 0)) kinds))
                         (double w))
     :odd53-share (/ (double (get kinds :odd53 0)) w)
     :distinct-kinds (count kinds)
     :frozen-fraction (/ (count (filter true? (map = (:genotype final)
                                                   (:previous-genotype final))))
                         (double w))
     :genotype-diversity (/ (double (count (distinct (:genotype final)))) w)
     :lambda-drift (:lambda-drift final)}))

(defn run-lambda [lambda]
  (let [seeds (range (:seed-base config) (+ (:seed-base config) (:seeds config)))
        rs (mapv #(run-trajectory % lambda) seeds)
        mean (fn [f] (/ (reduce + (map f rs)) (double (count rs))))]
    {:lambda lambda
     :dominant-kinds (frequencies (map :dominant rs))
     :absorbing-share (mean :absorbing-share)
     :odd53-share (mean :odd53-share)
     :frozen-fraction (mean :frozen-fraction)
     :genotype-diversity (mean :genotype-diversity)
     :distinct-kinds (mean :distinct-kinds)
     :max-lambda-drift (reduce max 0.0 (map :lambda-drift rs))
     :config config}))

(defn -main [& [mode a & more]]
  (case mode
    "run" (let [lambda (Double/parseDouble a)
                r (run-lambda lambda)]
            (spit (first more) (pr-str r))
            (println (format "lambda=%.2f drift=%.4f absorbing-share=%.3f odd53=%.3f frozen=%.3f  %s"
                             lambda (:max-lambda-drift r) (:absorbing-share r)
                             (:odd53-share r) (:frozen-fraction r)
                             (pr-str (:dominant-kinds r)))))
    "report"
    (let [rs (sort-by :lambda (map (comp edn/read-string slurp) more))]
      (spit a
            (str "# Lambda sweep — turning down the drive toward stasis\n\n"
                 (format "%d seeds, width %d, %d steps, blend %.2f, arm `:efe-full`, "
                         (:seeds config) (:width config) (:steps config) (:blend config))
                 "lambda PINNED (step size 0).\n\n"
                 "`absorbing share` is the fraction of cells holding a kind that CAN freeze; "
                 "`odd53 share` the fraction holding the only kind that cannot. "
                 "`drift` must be 0 or the pin failed.\n\n"
                 "| lambda | absorbing share | odd53 share | frozen | genotype diversity | distinct kinds | drift | dominant kinds |\n"
                 "|---:|---:|---:|---:|---:|---:|---:|---|\n"
                 (str/join "\n"
                   (for [r rs]
                     (format "| %.2f | %.3f | %.3f | %.3f | %.3f | %.2f | %.4f | %s |"
                             (:lambda r) (:absorbing-share r) (:odd53-share r)
                             (:frozen-fraction r) (:genotype-diversity r)
                             (:distinct-kinds r) (:max-lambda-drift r)
                             (pr-str (:dominant-kinds r)))))
                 "\n"))
      (println "wrote" a))
    (println "usage: run <lambda> <out.edn> | report <out.md> <in.edn>...")))

(apply -main *command-line-args*)
