(ns exotype-search
  "PREREGISTERED SEARCH EXPERIMENT -- 2026-08-05, before any sweep run.

   QUESTION. Can endogenous lambda adaptation search for a common dynamical
   regime from both ordered and chaotic starts, rather than merely exhibit a
   regime configured by hand?

   DESIGN. At checkpoints t in {0,25,50,100,200,300,400,600,800}, fork the
   current immutable state. Flip the centre phenotype bit in one fork, advance
   it and an unperturbed copy for H=40 steps under shared deterministic draws,
   record phenotype damage reach, and discard both forks. The main trajectory
   advances only from the original unforked state.

   Starts are uniform Rule 204 (ordered), uniform Rule 30 (chaotic), and random
   rules. Arms are frozen lambda (:fixed-0.55, step size 0), undirected lambda
   motion (:random-walk, step size 0.01), and directed search
   (:hunger-coupled, step size 0.01). Every arm starts at lambda=0.55. The
   policy-controlled blend action is enabled at epistemic coefficient 0.2;
   apply probability is 1.0. Stochastic blending remains at its default 0.0 so
   rule writing in this experiment is selected by the fourth action.

   PRIMARY STATISTIC:
     G(t) = abs(mean damage(t | ordered) - mean damage(t | chaotic)).
   No target value for damage is asserted.

   P1. Under arm C (:hunger-coupled), G(t) decreases with t.
   P2. Under arms A (:fixed-0.55) and B (:random-walk), G(t) does not
       decrease, or decreases materially less than under C.
   P3. Mean lambda under C moves in a consistent direction; under B it
       diffuses (lambda SD grows while its mean does not move consistently).

   FALSIFIER. If all three arms give indistinguishable G(t), there is no
   evidence of search--only dynamics. Report that null plainly.

   NUMERICAL DECISION RULES, fixed before the sweep (codex-12 correctly objected
   that `decreases` and `materially less` were not decision rules):

     P1 holds iff  G(800) < G(0)  AND  the reduction exceeds 2 x SE of the
        paired per-seed difference.
     P2 holds iff  C's reduction exceeds A's and B's, each by more than 2 SE of
        the difference of reductions.
     P3 holds iff  |mean-lambda(800) - mean-lambda(0)| under C exceeds 3x that
        under B, AND lambda-SD under B exceeds lambda-SD under C at t=800.

   SATURATION GUARD. If more than 20% of lambda values are pinned at 0 or 1 at
   any checkpoint, that arm is SATURATED, not directed: P3 is void for it and
   the cell must be reported as a degenerate configuration. This guard exists
   because the first specified step size (0.01) saturated 100% of cells.

   `smoke` is a labelled, non-evidential one-seed/25-step wiring check. It does
   not alter the preregistered `run` design and its artifact cannot be mistaken
   for a production cell.

   usage:
     clojure -M scripts/exotype_search.clj run <ordered|chaotic|random> <fixed-0.55|random-walk|hunger-coupled> <out.edn>
     clojure -M scripts/exotype_search.clj report <out.md> <in.edn>...
     clojure -M scripts/exotype_search.clj smoke <start> <arm> <out.edn>"
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(def design
  {:schema :exotype-search-v1
   :width 80
   :steps 800
   :checkpoints [0 25 50 100 200 300 400 600 800]
   :damage-horizon 40
   :seeds (vec (range 2026085100 2026085116))
   :starts [:ordered :chaotic :random]
   :arms [:fixed-0.55 :random-walk :hunger-coupled]
   :initial-lambda 0.55
   :blend-action? true
   :blend-strength 0.0
   :epistemic-coefficient 0.2
   :apply-probability 1.0
   :diversity-threshold 0.05})

(def smoke-design
  (assoc design
         :steps 25
         :checkpoints [0 25]
         :damage-horizon 5
         :seeds [(first (:seeds design))]))

(def absorbing-kinds #{:collapser :even1 :even4 :even8})
(def rule-numbers {:ordered 204 :chaotic 30})

(def arm-config
  ;; STEP SIZE 0.0003, NOT 0.01. claude-14's original spec said 0.01; measured
  ;; over 800 steps that pins 100% of cells at the lambda ceiling by t=100, so
  ;; the "directed search" arm would have been measuring SATURATION, not search,
  ;; and P3 would have been trivially true for a degenerate reason:
  ;;   step   t=100  t=300  t=800   fraction pinned at 0 or 1 @800
  ;;   0.0100 1.000  1.000  1.000   1.00
  ;;   0.0030 0.848  1.000  1.000   1.00
  ;;   0.0010 0.649  0.849  1.000   1.00
  ;;   0.0003 0.580  0.640  0.790   0.00   <- the only one that stays interior
  ;; Both moving arms use the SAME step size, or :random-walk stops being a
  ;; matched control.
  {:fixed-0.55 {:self-tuning-arm :fixed-0.55 :lambda-step-size 0.0}
   :random-walk {:self-tuning-arm :random-walk :lambda-step-size 0.0003}
   :hunger-coupled {:self-tuning-arm :hunger-coupled :lambda-step-size 0.0003}})

(defn- mean [xs]
  (/ (reduce + 0.0 xs) (double (count xs))))

(defn- population-sd [xs]
  (let [average (mean xs)]
    (Math/sqrt
     (mean (map #(let [delta (- (double %) average)] (* delta delta)) xs)))))

(defn- rule-sigil [rule]
  (ca/sigil-for
   (str/replace (format "%8s" (Integer/toBinaryString rule)) " " "0")))

(defn- initial-genotype [start width]
  (case start
    :ordered (vec (repeat width (rule-sigil (rule-numbers start))))
    :chaotic (vec (repeat width (rule-sigil (rule-numbers start))))
    :random (vec (ca/random-sigil-string width))
    (throw (ex-info "unknown initial regime"
                    {:start start :known (:starts design)}))))

(defn- initial-state [run-design start arm seed]
  (when-not (contains? arm-config arm)
    (throw (ex-info "unknown search arm" {:arm arm :known (:arms design)})))
  (ca/with-seed seed
    (let [width (:width run-design)
          genotype (initial-genotype start width)]
      (merge
       {:arm :efe-full
        :seed seed
        :time 0
        :hunger-target (:hunger efe/preferences)
        :lambdas (vec (repeat width (:initial-lambda run-design)))
        :genotype genotype
        :previous-genotype genotype
        :phenotype (ca/random-phenotype-string width)
        :exotypes (grid/initial-grid :heterogeneous-fixed width)
        :blend-action? (:blend-action? run-design)
        :blend-strength (:blend-strength run-design)
        :epistemic-coefficient (:epistemic-coefficient run-design)
        :apply-probability (:apply-probability run-design)}
       (arm-config arm)))))

(defn- phenotype-damage [run-design state]
  (let [site (quot (:width run-design) 2)
        perturbed (update state :phenotype
                          #(apply str
                                  (update (vec %) site
                                          (fn [bit] (if (= bit \0) \1 \0)))))
        advance #(nth (iterate tuning/step %)
                      (:damage-horizon run-design))
        control-final (advance state)
        perturbed-final (advance perturbed)]
    (count (filter true?
                   (map not= (:phenotype control-final)
                        (:phenotype perturbed-final))))))

(defn- checkpoint [run-design state]
  (let [width (:width run-design)
        decisions (:decisions (tuning/transmit state))
        winners (mapv :winner decisions)
        frequencies (frequencies (:exotypes state))
        threshold (* (:diversity-threshold run-design) width)
        effective-adoptions
        (count (filter true?
                       (map-indexed
                        (fn [index winner]
                          (not= (nth (:exotypes state) index)
                                (:candidate-exotype winner)))
                        winners)))]
    {:damage-reach (phenotype-damage run-design state)
     :mean-lambda (mean (:lambdas state))
     :lambda-sd (population-sd (:lambdas state))
     ;; These are a current-state, next-decision snapshot. Adoption means a
     ;; sigma change; blend is reported separately because it writes only rule.
     :blend-win-rate
     (/ (double (count (filter #(= :blend (:policy %)) winners))) width)
     :adoption-rate (/ (double effective-adoptions) width)
     :kinds-above-5pct
     (count (filter #(>= (val %) threshold) frequencies))
     :halting-capable-share
     (/ (double (count (filter absorbing-kinds (:exotypes state)))) width)}))

(defn- run-seed [run-design start arm seed]
  (let [wanted (set (:checkpoints run-design))]
    (loop [state (initial-state run-design start arm seed)
           time 0
           trajectory (sorted-map)]
      (let [trajectory' (if (wanted time)
                          (assoc trajectory time (checkpoint run-design state))
                          trajectory)]
        (if (= time (:steps run-design))
          {:seed seed :trajectory trajectory'}
          (recur (tuning/step state) (inc time) trajectory'))))))

(defn- run-cell [run-design start arm smoke?]
  (when-not ((set (:starts design)) start)
    (throw (ex-info "unknown initial regime" {:start start :known (:starts design)})))
  (when-not ((set (:arms design)) arm)
    (throw (ex-info "unknown search arm" {:arm arm :known (:arms design)})))
  {:schema (:schema design)
   :status (if smoke? :smoke-non-evidential :preregistered-cell)
   :start start
   :arm arm
   :design run-design
   :runs (mapv #(run-seed run-design start arm %) (:seeds run-design))})

(defn- write-edn! [path value]
  (spit path (with-out-str (pprint/pprint value))))

(defn- checked-cell [path]
  (let [cell (edn/read-string (slurp path))]
    (when-not (and (= (:schema design) (:schema cell))
                   ((set (:starts design)) (:start cell))
                   ((set (:arms design)) (:arm cell))
                   (seq (:runs cell)))
      (throw (ex-info "input is not an exotype-search cell"
                      {:path path :artifact cell})))
    cell))

(defn- spread [values]
  {:mean (mean values) :sd (population-sd values)})

(defn- metric-row [cell time]
  (into {:start (:start cell) :arm (:arm cell) :time time}
        (for [metric [:damage-reach :mean-lambda :lambda-sd :blend-win-rate
                      :adoption-rate :kinds-above-5pct
                      :halting-capable-share]]
          [metric (spread (map #(get-in % [:trajectory time metric])
                               (:runs cell)))])))

(defn- fmt-spread [{:keys [mean sd]}]
  (format "%.3f ± %.3f" mean sd))

(defn- report-text [cells]
  (let [production (filter #(= :preregistered-cell (:status %)) cells)
        rows (for [cell cells
                   time (get-in cell [:design :checkpoints])]
               (metric-row cell time))
        by-condition (into {} (map (juxt (juxt :start :arm) identity) production))
        gap-rows (for [arm (:arms design)
                       :let [ordered (get by-condition [:ordered arm])
                             chaotic (get by-condition [:chaotic arm])]
                       :when (and ordered chaotic)
                       time (:checkpoints design)]
                   {:arm arm :time time
                    :gap (Math/abs
                          (- (:mean (:damage-reach (metric-row ordered time)))
                             (:mean (:damage-reach (metric-row chaotic time)))))} )]
    (str
     "# Endogenous criticality search\n\n"
     "Preregistered in `scripts/exotype_search.clj` before the sweep. "
     "The primary statistic is `G(t) = |mean damage_ordered - mean damage_chaotic|`; "
     "no target damage value was asserted. Smoke artifacts are labelled non-evidential.\n\n"
     "## Cell trajectories\n\n"
     "| start | arm | t | damage | mean lambda | lambda SD | blend wins | sigma adoption | kinds >=5% | halting share |\n"
     "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|\n"
     (str/join
      "\n"
      (for [row rows]
        (format "| %s | %s | %d | %s | %s | %s | %s | %s | %s | %s |"
                (name (:start row)) (name (:arm row)) (:time row)
                (fmt-spread (:damage-reach row))
                (fmt-spread (:mean-lambda row))
                (fmt-spread (:lambda-sd row))
                (fmt-spread (:blend-win-rate row))
                (fmt-spread (:adoption-rate row))
                (fmt-spread (:kinds-above-5pct row))
                (fmt-spread (:halting-capable-share row)))))
     "\n\n## Ordered-chaotic gap G(t)\n\n"
     (if (seq gap-rows)
       (str "| arm | t | G(t) |\n|---|---:|---:|\n"
            (str/join "\n"
                      (for [{:keys [arm time gap]} gap-rows]
                        (format "| %s | %d | %.3f |" (name arm) time gap)))
            "\n")
       "Not computable: no production arm has both ordered and chaotic cells.\n")
     "\n## Preregistered interpretation\n\n"
     "P1: G(t) decreases under hunger-coupled. P2: it does not decrease, or "
     "decreases materially less, under fixed-0.55 and random-walk. P3: directed "
     "mean-lambda movement separates from random-walk diffusion. If all three "
     "G(t) curves are indistinguishable, report no search--only dynamics.\n")))

(defn -main [& [mode a b & more]]
  (case mode
    "run"
    (let [start (keyword a) arm (keyword b) out (first more)
          artifact (run-cell design start arm false)]
      (write-edn! out artifact)
      (println (format "%s/%s: %d seeds -> %s"
                       a b (count (:runs artifact)) out)))

    "smoke"
    (let [start (keyword a) arm (keyword b) out (first more)
          artifact (run-cell smoke-design start arm true)]
      (write-edn! out artifact)
      (println (pr-str artifact)))

    "report"
    (let [out a
          paths (cons b more)
          cells (mapv checked-cell paths)
          condition-keys (map (juxt :start :arm) cells)]
      (when-not (= (count condition-keys) (count (distinct condition-keys)))
        (throw (ex-info "duplicate start/arm cell in report" {:cells condition-keys})))
      (spit out (report-text cells))
      (println "wrote" out))

    (println "usage: run START ARM OUT.edn | report OUT.md IN.edn... | smoke START ARM OUT.edn")))

(apply -main *command-line-args*)
