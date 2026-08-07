(ns srch-artifact-analysis
  "M1 for TN-search-control-fable-answer.md. Pure re-analysis of the nine
   preregistered cells in reports-remote/srch/ -- no simulation.

   Questions answered here:
   1. G(t) per arm with per-seed paired SE, including the FROZEN arm's own
      G(t) collapse (the mixing confound of section 1.5a, measured).
   2. frozen-vs-random-walk per-seed damage identity (is the walk inert?).
   3. hunger-coupled mean-lambda residual against the exact open-loop ramp
      clip(0.55 + 0.0003 t) per checkpoint (H1's trajectory-level test).
   4. random-walk lambda-SD against the nominal sqrt(t) diffusion law.

   usage: clojure -Sdeps '{:paths [\"src\" \"resources\"]}' -M analysis/srch_artifact_analysis.clj"
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(def dir "reports-remote/srch")
(def starts [:ordered :chaotic :random])
(def arms [:fixed-0.55 :random-walk :hunger-coupled])
(def step-size 0.0003)
(def initial-lambda 0.55)

(defn cell [start arm]
  (edn/read-string (slurp (format "%s/%s-%s.edn" dir (name start) (name arm)))))

(def cells
  (into {} (for [s starts a arms] [[s a] (cell s a)])))

(def checkpoints (get-in (first (vals cells)) [:design :checkpoints]))
(def seeds (get-in (first (vals cells)) [:design :seeds]))

(defn metric-by-seed [c t k]
  (mapv #(get-in % [:trajectory t k]) (:runs c)))

(defn mean [xs] (/ (reduce + 0.0 xs) (count xs)))
(defn sd [xs]
  (let [m (mean xs)]
    (Math/sqrt (/ (reduce + 0.0 (map #(let [d (- (double %) m)] (* d d)) xs))
                  (max 1 (dec (count xs)))))))
(defn se [xs] (/ (sd xs) (Math/sqrt (count xs))))

(println "== 1. G(t) per arm: paired per-seed ordered-minus-chaotic damage ==")
(println "arm | t | G(t)=|mean diff| | SE(paired diff) | mean ord | mean cha")
(doseq [a arms]
  (doseq [t checkpoints]
    (let [o (metric-by-seed (cells [:ordered a]) t :damage-reach)
          c (metric-by-seed (cells [:chaotic a]) t :damage-reach)
          d (mapv - o c)]
      (println (format "%-14s t=%3d  G=%7.3f  SE=%6.3f  ord=%6.2f  cha=%6.2f"
                       (name a) t (Math/abs (mean d)) (se d)
                       (mean o) (mean c))))))

(println)
(println "== 2. Is the random walk inert? frozen vs walk, all starts ==")
(doseq [s starts]
  (let [f (cells [s :fixed-0.55]) w (cells [s :random-walk])
        pairs (for [t checkpoints
                    [fr wr] (map vector (:runs f) (:runs w))]
                [(get-in fr [:trajectory t :damage-reach])
                 (get-in wr [:trajectory t :damage-reach])])
        n (count pairs)
        equal (count (filter (fn [[x y]] (= x y)) pairs))
        ;; also compare EVERY checkpoint metric, not just damage
        all-equal?
        (every? (fn [t]
                  (every? (fn [[fr wr]]
                            (= (dissoc (get-in fr [:trajectory t]) :mean-lambda :lambda-sd)
                               (dissoc (get-in wr [:trajectory t]) :mean-lambda :lambda-sd)))
                          (map vector (:runs f) (:runs w))))
                checkpoints)]
    (println (format "%-8s damage equal: %d/%d checkpoints-x-seeds; all non-lambda metrics identical: %s"
                     (name s) equal n all-equal?))))

(println)
(println "== 3. Coupled arm vs the exact open-loop ramp clip(0.55 + s t) ==")
(println "start | t | mean-lambda (mean over seeds) | ramp | residual | max |resid| over seeds")
(doseq [s starts]
  (doseq [t checkpoints]
    (let [ml (metric-by-seed (cells [s :hunger-coupled]) t :mean-lambda)
          ramp (min 1.0 (+ initial-lambda (* step-size t)))
          resid (mapv #(- (double %) ramp) ml)]
      (println (format "%-8s t=%3d  lam=%8.5f  ramp=%8.5f  mean-resid=%+9.6f  max|resid|=%9.6f"
                       (name s) t (mean ml) ramp (mean resid)
                       (apply max (map #(Math/abs (double %)) resid)))))))

(println)
(println "== 3b. Implied negative-sign budget in the coupled arm ==")
;; between consecutive checkpoints, deficit from full-rate rise = 2s * (neg cell-steps)/width
(doseq [s starts]
  (let [c (cells [s :hunger-coupled])]
    (doseq [[t0 t1] (partition 2 1 checkpoints)]
      (let [rise (mean (mapv (fn [r] (- (get-in r [:trajectory t1 :mean-lambda])
                                        (get-in r [:trajectory t0 :mean-lambda])))
                             (:runs c)))
            full (* step-size (- t1 t0))
            ;; neg fraction f solves rise = full*(1-2f)  (ignoring clipping, none interior)
            f (/ (- full rise) (* 2.0 full))]
        (println (format "%-8s t=%3d->%3d  rise=%8.5f  full=%8.5f  implied neg-sign frac=%7.4f"
                         (name s) t0 t1 rise full f))))))

(println)
(println "== 4. Walk lambda-SD vs nominal sqrt-t diffusion ==")
(println "start | t | lambda-SD (mean over seeds) | s*sqrt(t) | ratio")
(doseq [s starts]
  (doseq [t checkpoints]
    (let [sds (metric-by-seed (cells [s :random-walk]) t :lambda-sd)
          nominal (* step-size (Math/sqrt t))]
      (println (format "%-8s t=%3d  sd=%9.6f  nominal=%9.6f  ratio=%s"
                       (name s) t (mean sds) nominal
                       (if (pos? nominal)
                         (format "%6.3f" (/ (mean sds) nominal)) "  --"))))))

(println)
(println "== 5. Context: damage means per start/arm at t=0 vs t=800, and random start ==")
(doseq [s starts, a arms]
  (let [c (cells [s a])
        d0 (metric-by-seed c 0 :damage-reach)
        d800 (metric-by-seed c 800 :damage-reach)]
    (println (format "%-8s %-14s damage t=0: %6.2f +- %5.2f   t=800: %6.2f +- %5.2f"
                     (name s) (name a) (mean d0) (sd d0) (mean d800) (sd d800)))))
