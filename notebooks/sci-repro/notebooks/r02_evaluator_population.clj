(ns notebooks.r02-evaluator-population
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [scicloj.kindly.v4.kind :as kind]))

;; # R02: Evaluator populations - collapse and guard
;;
;; This statistical replay joins two reviewed, 30-run experiments. Arm 1
;; measures naive genotype evolution against a random null. Arm 2 measures
;; evaluator populations with and without the xenotype guard.
;;
;; The engines are not deterministic per seed. The committed EDN files are
;; sample runs, and every interval below describes an arm distribution rather
;; than per-seed reproducibility.

;; ## Setup

(def seeds (range 42 72))
(def data-root (io/file "../.." "data"))

(defn load-artifact [directory arm seed]
  (let [path (io/file data-root directory
                      (str (name arm) "-seed-" seed ".edn"))]
    (edn/read-string (slurp path))))

(defn load-all [directory arm]
  (mapv #(load-artifact directory arm %) seeds))

(defn mean [xs]
  (/ (reduce + 0.0 xs) (count xs)))

(defn stddev [xs]
  (let [m (mean xs)]
    (Math/sqrt
     (/ (reduce + 0.0 (map #(Math/pow (- % m) 2) xs))
        (count xs)))))

(defn ci95 [xs]
  (* 1.96 (stddev xs) (/ (Math/sqrt (count xs)))))

(defn summary [runs path]
  (let [values (map #(get-in % path) runs)]
    {:mean (mean values)
     :ci95 (ci95 values)
     :n (count values)}))

(defn fmt [digits value]
  (format (str "%." digits "f") (double value)))

(defn bar-chart [title series max-value]
  (let [width 680
        label-width 185
        plot-width 430
        row-height 42
        height (+ 58 (* row-height (count series)))]
    (str "<svg xmlns='http://www.w3.org/2000/svg' role='img' aria-label='" title
         "' viewBox='0 0 " width " " height "' style='max-width:680px;width:100%'>"
         "<text x='0' y='22' font-size='17' font-weight='600'>" title "</text>"
         (apply str
                (map-indexed
                 (fn [index {:keys [label value color display]}]
                   (let [y (+ 43 (* index row-height))
                         bar-width (* plot-width (/ value max-value))]
                     (str "<text x='0' y='" (+ y 18) "' font-size='14'>" label "</text>"
                          "<rect x='" label-width "' y='" y "' width='" bar-width
                          "' height='25' fill='" color "'/>"
                          "<text x='" (+ label-width bar-width 8) "' y='" (+ y 18)
                          "' font-size='14'>" display "</text>")))
                 series))
         "</svg>")))

(def baseline-runs (delay (load-all "mission-02-runs" :baseline)))
(def null-runs (delay (load-all "mission-02-runs" :null)))
(def unguarded-runs (delay (load-all "mission-04-06-runs" :unguarded)))
(def guarded-runs (delay (load-all "mission-04-06-runs" :guarded)))

;; ## Arm 1: Naive evolution collapses
;;
;; | Claim | Metric | Baseline (mean +/- 95% CI, n=30) | Null (mean +/- 95% CI, n=30) | Verdict |
;; |---|---|---:|---:|---|
;; | C2.1 | Composite score | 22.45 +/- 0.47 | 3.15 +/- 0.05 | Evolution produces higher structure than random |
;; | C2.2 | Avg entropy (normalized) | 0.572 +/- 0.016 | N/A (no metrics) | Moderate entropy - not maximal chaos |
;; | C2.3 | Avg change rate | 0.112 +/- 0.004 | N/A (no metrics) | Low change - evolution settles, not chaotic |
;; | C2.4 | Final unique sigils | 17.4 +/- 0.9 | 45.0 +/- 0.6 | Evolution collapses diversity (17 vs 45) |

(kind/html
 (let [baseline (summary @baseline-runs [:final-unique-sigils])
       random-null (summary @null-runs [:final-unique-sigils])]
   (bar-chart
    "Final unique sigils"
    [{:label "Naive evolution"
      :value (:mean baseline)
      :display (str (fmt 1 (:mean baseline)) " +/- " (fmt 1 (:ci95 baseline)))
      :color "#277da1"}
     {:label "Random null"
      :value (:mean random-null)
      :display (str (fmt 1 (:mean random-null)) " +/- " (fmt 1 (:ci95 random-null)))
      :color "#6c757d"}]
    50.0)))

;; **Measured proposition (verbatim from `data/mission-02-claims.md`):**
;;
;; **"Naive evolution collapses":** Under local evolution with no exotype
;; (kernel=:mutating-template, no exotype steering), the genotype diversity
;; collapses from the initial ~45 unique sigils to ~17 (a 62% reduction),
;; with a low change rate (0.112 ± 0.004) and moderate entropy (0.572 ± 0.016).
;; The composite score (22.45) is well above the null (3.15), confirming that
;; evolution produces *some* structure — but the collapse in diversity shows
;; it converges to a narrow attractor rather than maintaining exploratory
;; dynamics.
;;
;; The null model (random genotype per generation) maintains full diversity
;; (45 unique sigils) but has near-zero composite score (3.15), confirming
;; that diversity without evolution is just noise.

;; ## Arm 2: Xenotype guard
;;
;; | Metric | Unguarded (Mission 4) | Guarded (Mission 6) | Verdict |
;; |---|---:|---:|---|
;; | survivor-confetti-rate | 0.927 +/- 0.013 | 0.631 +/- 0.038 | Guard **reduces** degenerate survivors (CIs disjoint) |
;; | survivor-mean-change | 0.853 +/- 0.006 | 0.776 +/- 0.012 | Guard lowers churn (CIs disjoint) |
;; | survivor-mean-entropy-n | 0.870 +/- 0.003 | 0.810 +/- 0.011 | Guard lowers noise (CIs disjoint) |
;; | survivor-identity-diversity | 0.979 +/- 0.020 | 0.992 +/- 0.011 | No significant difference (CIs overlap; both near-max) |
;; | survivor-dead-rate | 0.000 | 0.000 | No dead survivors in either arm |

(kind/html
 (let [unguarded (summary @unguarded-runs [:metrics :survivor-confetti-rate])
       guarded (summary @guarded-runs [:metrics :survivor-confetti-rate])]
   (bar-chart
    "Survivor confetti rate"
    [{:label "Unguarded"
      :value (:mean unguarded)
      :display (str (fmt 3 (:mean unguarded)) " +/- " (fmt 3 (:ci95 unguarded)))
      :color "#d1495b"}
     {:label "Guarded"
      :value (:mean guarded)
      :display (str (fmt 3 (:mean guarded)) " +/- " (fmt 3 (:ci95 guarded)))
      :color "#2a9d8f"}]
    1.0)))

;; **Measured proposition (verbatim from `data/mission-04-06-claims.md`):**
;;
;; **"Xenotype guards change the surviving evaluator set (Goodhart guard)":** a slow
;; xenotype guard (update every ~100 exotype evals) shifts the surviving exotype
;; population toward **less degenerate** evaluators — confetti-rate 0.927 → 0.631,
;; mean-change 0.853 → 0.776, entropy 0.870 → 0.810 (all with **disjoint** 95% CIs,
;; n=30). The guard **reduces but does not eliminate** degeneracy (63% of guarded
;; survivors are still confetti). Identity-diversity is unchanged (both arms
;; near-maximal). So the guard demonstrably reshapes the surviving evaluator set on
;; the degeneracy axis — the honest, statistical form of Mission 6's "xenotypes
;; demonstrably change the surviving evaluator set."
;;
;; Together with the Mission-2 genotype-collapse baseline, this is the second arm of
;; the **r02 evaluator-population replay**.

;; ## Statistical framing
;;
;; Both arms use 30 committed sample runs (seeds 42-71). Their mutation and
;; exo/xeno engines are not fully deterministic per seed, so this notebook
;; reports population means and 95% CIs. It does not claim grid identity or
;; reproducible per-seed trajectories.
