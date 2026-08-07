(ns exotype-kappa-4action
  "kappa sweep on the 3- vs 4-action policy space.

   WHY THIS EXISTS (TN-baldwin-reboot.md 73, 74).

   Two findings meet here. (1) At the shipped kappa = 0.478 the epistemic term
   carries 2-11x the authority of the decision margins it is supposed to inform;
   adoption saturates near 1.0 and one-bit decision sensitivity collapses 100x.
   A term larger than the gaps overwrites the comparison instead of grading it.
   (2) A fourth, rule-writing action (`:blend`) now exists, and it is the only
   way rule CONTENT can cross cells -- with three sigma-writing actions that is
   structurally impossible for ANY score term (Proposition 1, 0 witnesses in
   1206 events; 453 witnesses once the fourth action is on).

   The fourth action scores identically to `hold` on risk and ambiguity -- same
   exotype, since blending does not change sigma. Its ONLY advantage is its
   epistemic value. So kappa is now bounded from BOTH sides for the first time:
   too small and blend is never selectable, too large and the decision layer
   goes deaf. That is what this sweep measures.

   PREREGISTERED, before the run:

   P1 blend win-rate rises monotonically with kappa. (Manipulation check: kappa
      is the only thing pricing the blend action. If this fails the wiring is
      wrong, not the hypothesis.)
   P2 interruption -- rule-layer damage cone from a sigma perturbation -- is
      NON-MONOTONE in kappa on the 4-action arm: ~0 at kappa=0 (blend never
      selected), rising, then falling as kappa enters the authority regime.
   P3 one-bit decision sensitivity falls monotonically with kappa on BOTH arms.
      (This is the authority effect and should not need the fourth action.)
   P4 the 4-action arm exceeds the 3-action arm on interruption at intermediate
      kappa, and the two coincide at kappa = 0.

   FALSIFIER. If the rule-damage cone is monotone in kappa (no interior peak),
   OR if 4-action and 3-action are indistinguishable at every kappa, the fourth
   action buys nothing and the content channel is not worth its complexity.

   NOT MEASURED, deliberately: dominant-share. Retired at 71.3 -- both bonus
   arms lock into a period-2 alternation that pins it near 0.5, so it is an
   artifact detector rather than a diversity metric. Partner diversity (kinds
   holding >= 5% of the ring) replaces it.

   usage:
     clojure -M scripts/exotype_kappa_4action.clj run <kappa> <actions|3|4> <out.edn>
     clojure -M scripts/exotype_kappa_4action.clj report <out.md> <in.edn>..."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(def design
  {:schema :exotype-kappa-4action
   :width 80
   :burn-in 200
   :damage-horizon 40
   :kappas [0.0 0.05 0.1 0.2 0.478 1.0]
   :seeds (vec (range 2026085000 2026085024))
   :apply-probability 1.0
   :diversity-threshold 0.05})

(def absorbing-kinds #{:collapser :even1 :even4 :even8})

(defn- initial-state [seed actions kappa]
  (ca/with-seed seed
    (let [width (:width design)
          genotype (vec (ca/random-sigil-string width))]
      (cond-> {:arm :efe-full :seed seed :time 0
               :self-tuning-arm :hunger-coupled
               :lambda-step-size 0.0
               :hunger-target (:hunger efe/preferences)
               :lambdas (vec (repeat width 0.55))
               :genotype genotype :previous-genotype genotype
               :phenotype (apply str (repeatedly width #(if (< (ca/rnd) 0.5) \0 \1)))
               :exotypes (grid/initial-grid :heterogeneous-fixed width)
               :apply-probability (:apply-probability design)
               :epistemic-coefficient kappa}
        (= 4 actions) (assoc :blend-action? true)))))

(defn- rule-damage
  "Rule-layer cone from a SIGMA perturbation: the interruption measure. Twins
   share every draw, so the only difference is the exotype at the centre cell."
  [state]
  (let [width (:width design)
        site (quot width 2)
        other (nth grid/exotype-kinds
                   (mod (inc (.indexOf ^java.util.List (vec grid/exotype-kinds)
                                       (nth (:exotypes state) site)))
                        (count grid/exotype-kinds)))
        twin (update state :exotypes assoc site other)
        advance #(nth (iterate tuning/step %) (:damage-horizon design))
        a (advance state) b (advance twin)]
    {:rule-cone (count (filter true? (map not= (map str (:genotype a)) (map str (:genotype b)))))
     :sigma-cone (count (filter true? (map not= (:exotypes a) (:exotypes b))))
     ;; witnesses: rule diverged where sigma never did -- only possible with a
     ;; rule-writing action in the policy set.
     :content-witnesses
     (count (for [i (range width)
                  :when (and (not= i site)
                             (not= (str (nth (:genotype a) i)) (str (nth (:genotype b) i)))
                             (= (nth (:exotypes a) i) (nth (:exotypes b) i)))]
              i))}))

(defn- decision-sensitivity
  "Fraction of cells whose argmin flips when one observation bit is perturbed.
   A decision layer dominated by a state-independent term is deaf: this goes to 0."
  [state]
  (let [width (:width design)
        flips (for [i (range width)]
                (let [base (get-in (efe/cell-decision :efe-full state i) [:winner :policy])
                      bumped (update state :phenotype
                                     #(apply str (update (vec %) i (fn [b] (if (= b \0) \1 \0)))))
                      alt (get-in (efe/cell-decision :efe-full bumped i) [:winner :policy])]
                  (if (= base alt) 0 1)))]
    (/ (double (reduce + flips)) width)))

(defn- run-seed [seed actions kappa]
  (let [burned (nth (iterate tuning/step (initial-state seed actions kappa))
                    (:burn-in design))
        width (:width design)
        freqs (frequencies (:exotypes burned))
        thresh (* (:diversity-threshold design) width)
        wins (frequencies (map #(get-in % [:winner :policy]) (:efe-decisions burned)))]
    (merge {:seed seed :actions actions :kappa kappa
            ;; partner diversity, replacing dominant-share (71.3)
            :kinds-above-5pct (count (filter #(>= (val %) thresh) freqs))
            :distinct-kinds (count freqs)
            :blend-win-rate (/ (double (get wins :blend 0)) width)
            :adoption-rate (/ (double (+ (get wins :adopt-left 0) (get wins :adopt-right 0)
                                         (get wins :blend 0))) width)
            :halting-capable-share
            (/ (double (count (filter absorbing-kinds (:exotypes burned)))) width)
            :decision-sensitivity (decision-sensitivity burned)}
           (rule-damage burned))))

(defn- mean [xs] (if (seq xs) (/ (reduce + xs) (double (count xs))) 0.0))
(defn- sd [xs]
  (let [m (mean xs)]
    (Math/sqrt (/ (reduce + (map #(let [d (- % m)] (* d d)) xs)) (max 1 (dec (count xs)))))))

(defn -main [& [mode a b c]]
  (case mode
    "run"
    (let [kappa (Double/parseDouble a)
          actions (Integer/parseInt b)
          runs (mapv #(run-seed % actions kappa) (:seeds design))]
      (spit c (pr-str {:schema (:schema design) :design design
                       :kappa kappa :actions actions :runs runs}))
      (println (format "%s-action kappa=%.3f -> %s  rule-cone %.1f  blend-wins %.3f  sens %.4f"
                       b kappa c
                       (mean (map :rule-cone runs))
                       (mean (map :blend-win-rate runs))
                       (mean (map :decision-sensitivity runs)))))

    "report"
    (let [cells (->> (rest (cons b (list* b c (drop 3 *command-line-args*))))
                     distinct (filter #(.exists (java.io.File. (str %))))
                     (map #(edn/read-string (slurp %))))
          cells (sort-by (juxt :actions :kappa) cells)]
      (spit a
            (str "# kappa sweep on the 3- vs 4-action policy space\n\n"
                 "Preregistered in the script docstring before the run. "
                 "Dominant-share deliberately absent (retired, TN 71.3).\n\n"
                 "| actions | kappa | rule cone | content witnesses | blend wins | kinds>=5% | decision sens | halting |\n"
                 "|---:|---:|---:|---:|---:|---:|---:|---:|\n"
                 (str/join "\n"
                           (for [{:keys [actions kappa runs]} cells]
                             (format "| %d | %.3f | %.1f ± %.1f | %.2f | %.3f | %.2f | %.4f | %.3f |"
                                     actions kappa
                                     (mean (map :rule-cone runs)) (sd (map :rule-cone runs))
                                     (mean (map :content-witnesses runs))
                                     (mean (map :blend-win-rate runs))
                                     (mean (map :kinds-above-5pct runs))
                                     (mean (map :decision-sensitivity runs))
                                     (mean (map :halting-capable-share runs)))))
                 "\n"))
      (println "wrote" a))

    (println "usage: run <kappa> <3|4> <out.edn> | report <out.md> <in.edn>...")))

(apply -main *command-line-args*)
