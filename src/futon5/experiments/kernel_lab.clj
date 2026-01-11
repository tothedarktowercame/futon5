(ns futon5.experiments.kernel-lab
  "Ad-hoc experiments comparing legacy kernel functors via the in-repo CT DSL.
  The initial study tracks the prevalence of the sigil 牛 (ox) in genotype
  histories and lets each kernel propose follow-up rules and learning
  hypotheses."
  (:require [clojure.pprint :as pprint]
            [futon5.ca.core :as ca]
            [futon5.ct.dsl :as ct]))

(def target-sigil "牛")

(defonce ^:private setup-registry!
  (delay
    (ct/register-category! ct/aif-stage-category)
    (ct/register-category! ct/meta-kernel-category)
    (ct/register-functor! ct/observation->kernel-functor)
    (ct/register-natural-transformation! ct/local-rule-balancer)))

(defn sigil-prevalence
  [gen-history sigil]
  (let [chars (mapcat seq gen-history)
        total (count chars)
        hits (count (filter #(= (str %) sigil) chars))]
    (if (pos? total)
      (/ hits total)
      0.0)))

(defn- mutation-path []
  (when-let [cat (ct/category :futon5/meta-kernel)]
    (ct/compose cat [:template :mutate :apply])))

(defn propose-next-rule
  [{:keys [kernel prevalence]}]
  (let [path (mutation-path)
        pct (float prevalence)]
    (cond
      (> prevalence 0.4)
      {:kernel kernel
       :type :diversify
       :description (format "牛 dominates (%.2f) – encourage contextual templates to flip 牛"
                             pct)
       :path path}

      (< prevalence 0.05)
      {:kernel kernel
       :type :stabilize
       :description (format "牛 nearly vanished (%.2f) – bias lifts towards 牛 contexts" pct)
       :path path}

      :else
      {:kernel kernel
       :type :monitor
       :description (format "牛 prevalence balanced (%.2f) – no rule change" pct)
       :path path})))

(defn composite-score
  [{:keys [prevalence average-hamming stasis-step phenotype-zero-step]}]
  (let [h (or average-hamming 0.0)
        stasis (or stasis-step 100)
        zero (or phenotype-zero-step 100)
        diversity-score h
        stability-penalty (/ (+ stasis zero) 200.0)]
    (+ (* 0.5 prevalence)
       (* 0.4 diversity-score)
       (* 0.1 (- 1.0 stability-penalty)))))

(defn propose-mca-physics
  [{:keys [kernel prevalence average-hamming stasis-step]}]
  (cond
    (> prevalence 0.45)
    {:kernel kernel
     :action :template-swap
     :description "Introduce local template inversions whenever 牛 appears twice in a neighborhood."
     :local-rule {:template {:bias :invert-牛}}}

    (< prevalence 0.08)
    {:kernel kernel
     :action :context-lift
     :description "Augment :lift to seed 牛 contexts when phenotype tension drops."
     :local-rule {:lift {:seed target-sigil}}}

    (< (or average-hamming 0.0) 0.05)
    {:kernel kernel
     :action :mutation-jitter
     :description "Allow contextual templates to nudge mutation rate up when local variety collapses."
     :local-rule {:mutate {:jitter 0.1}}}

    (and stasis-step (< stasis-step 4))
    {:kernel kernel
     :action :delay-template
     :description "Insert a delay before applying :template so short loops can escape early stasis."
     :local-rule {:template {:delay 1}}}

    :else
    {:kernel kernel
     :action :observe
     :description "No global change. Continue monitoring local patterns."}))

(defn generate-hypotheses
  [{:keys [kernel prevalence average-hamming]}]
  (let [baseline {:kernel kernel}
        add (fn [acc entry] (conj acc (merge baseline entry)))]
    (cond-> []
      (> prevalence 0.4)
      (add {:id :anti-牛-motif
            :description "Test: inject anti-牛 mutation templates into a sparse genotype."})

      (< prevalence 0.1)
      (add {:id :seed-牛
            :description "Test: pre-seed alternating 牛 contexts and measure recovery speed."})

      (> (or average-hamming 0.0) 0.3)
      (add {:id :lockdown
            :description "Test: temporarily clamp mutation to see if complex structures persist."}))))

(defn analyze-kernel
  [summary]
  (let [prevalence (sigil-prevalence (:gen-history summary) target-sigil)
        proposal (propose-next-rule {:kernel (:kernel summary)
                                     :prevalence prevalence})
        physics (propose-mca-physics (assoc summary :prevalence prevalence))
        hypotheses (generate-hypotheses (assoc summary :prevalence prevalence))
        score (composite-score (assoc summary :prevalence prevalence))]
    (assoc summary
           :target-sigil target-sigil
           :prevalence prevalence
           :proposal proposal
           :physics physics
           :hypotheses hypotheses
           :composite-score score)))

(def default-experiment
  {:genotype "九几了乃刀"
   :phenotype "01010"
   :generations 16})

(defn run-sigil-prevalence-study
  ([] (run-sigil-prevalence-study default-experiment))
  ([opts]
   @setup-registry!
   (->> (ca/survey-kernels opts)
        (map analyze-kernel)
        (sort-by (comp - :composite-score))
        vec)))

(defn print-learning-loop
  ([] (print-learning-loop default-experiment))
  ([opts]
   (doseq [{:keys [kernel prevalence composite-score proposal physics hypotheses]}
           (run-sigil-prevalence-study opts)]
     (println (format "%-22s prevalence=%.2f score=%.3f" kernel (float prevalence) composite-score))
     (println "  rule:" (:description proposal))
     (println "  physics:" (:description physics))
     (doseq [{:keys [id description]} hypotheses]
       (println (format "   hypothesis[%s]: %s" (name id) description)))
     (println))))

(comment
  (print-learning-loop))
