(ns exotype-target-sweep
  "CLOSED-LOOP target sweep: where does lambda actually settle, per hunger target?

   WHY (TN-baldwin-reboot.md 84). Two targets have now been tried and both gave a
   RECTIFIED RAMP -- lambda pinned at a boundary, no interior fixed point:
     target 0.05   (below the clamped range) -> lambda ramps UP   to 1.0
     target 0.1676 (inside the clamped range) -> lambda ramps DOWN to ~0.00
   The second was chosen from the OPEN-LOOP response curve h-bar(lambda), measured
   with lambda clamped UNIFORMLY. In closed loop lambda is HETEROGENEOUS, and that
   curve does not govern it. So the open-loop method for picking a target is dead;
   this sweeps the target in CLOSED loop instead and asks the question directly.

   PREREGISTERED:
     P1 there EXISTS a target whose settled lambda is interior (0.01 < lambda < 0.99
        at t=2000, and not still drifting: |lambda(2000)-lambda(1600)| < 0.02).
     P2 settled lambda is MONOTONE DECREASING in the target (a higher hunger target
        means less pressure to raise lambda).
     FALSIFIER for the whole search programme via this parameter: if EVERY target
     settles at a boundary, the controller has no interior fixed point at all and
     search through lambda is closed. That is a real result -- report it plainly.

   usage: run <target> <out.edn> | report <out.md> <in.edn>..."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(def design
  {:schema :exotype-target-sweep
   :width 80 :steps 2000 :lambda0 0.55 :seeds (vec (range 2026085300 2026085316))
   :checkpoints [0 200 400 800 1200 1600 2000]
   :targets [0.05 0.10 0.14 0.16 0.17 0.18 0.20 0.25 0.35 0.50]})

(defn- initial-state [target seed]
  (ca/with-seed seed
    (let [w (:width design) g (vec (ca/random-sigil-string w))]
      {:arm :efe-full :seed seed :time 0 :hunger-target target
       :lambdas (vec (repeat w (:lambda0 design)))
       :genotype g :previous-genotype g
       :phenotype (ca/random-phenotype-string w)
       :exotypes (grid/initial-grid :heterogeneous-fixed w)
       :blend-action? true :blend-strength 0.0 :epistemic-coefficient 0.2
       :apply-probability 1.0 :self-tuning-arm :hunger-coupled
       :lambda-step-size 0.001})))

(defn- mean [xs] (/ (reduce + 0.0 xs) (double (count xs))))

(defn- run-seed [target seed]
  (let [wanted (set (:checkpoints design))]
    (loop [s (initial-state target seed) t 0 acc (sorted-map)]
      (let [acc' (if (wanted t)
                   (assoc acc t {:mean-lambda (mean (:lambdas s))
                                 :lambda-sd (let [m (mean (:lambdas s))]
                                              (Math/sqrt (mean (map #(let [d (- % m)] (* d d))
                                                                    (:lambdas s)))))
                                 :realized-hunger
                                 (mean (map #(get-in % [:winner :prediction :hunger])
                                            (or (:efe-decisions s) [])))})
                   acc)]
        (if (= t (:steps design)) {:seed seed :trajectory acc'}
            (recur (tuning/step s) (inc t) acc'))))))

(defn -main [& [mode a & more]]
  (case mode
    "run" (let [tgt (Double/parseDouble a)
                runs (mapv #(run-seed tgt %) (:seeds design))
                fin (mean (map #(get-in % [:trajectory 2000 :mean-lambda]) runs))
                pre (mean (map #(get-in % [:trajectory 1600 :mean-lambda]) runs))]
            (spit (first more) (pr-str {:schema (:schema design) :target tgt :runs runs}))
            (println (format "target %.3f -> settled lambda %.4f  (drift 1600->2000 %+.4f)  %s"
                             tgt fin (- fin pre)
                             (if (< 0.01 fin 0.99) "INTERIOR" "at boundary"))))
    "report"
    (let [cs (sort-by :target (map #(edn/read-string (slurp %))
                                   (filter #(.exists (java.io.File. (str %))) (cons a more))))]
      (spit (first (filter #(str/ends-with? % ".md") [a])) "")
      (println "| target | settled lambda | drift 1600->2000 | realized hunger | interior? |")
      (println "|---:|---:|---:|---:|---|")
      (doseq [{:keys [target runs]} cs]
        (let [f (mean (map #(get-in % [:trajectory 2000 :mean-lambda]) runs))
              p (mean (map #(get-in % [:trajectory 1600 :mean-lambda]) runs))
              h (mean (map #(get-in % [:trajectory 2000 :realized-hunger]) runs))]
          (println (format "| %.3f | %.4f | %+.4f | %.4f | %s |" target f (- f p) h
                           (if (< 0.01 f 0.99) "**INTERIOR**" "boundary"))))))
    (println "usage: run <target> <out.edn> | report <out.md> <in.edn>...")))

(apply -main *command-line-args*)
