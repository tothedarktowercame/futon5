(ns scirepro.weighted-blend-experiment
  "Statistical sweep of per-cell stochastic mixtures of mutating-template and
   Baldwin dynamics, scored with both AIS and nearest-neighbor TE."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.mmca.predictive-information :as pi]
            [scirepro.baldwin :as baldwin]
            [scirepro.engine :as engine]
            [scirepro.mutating-template :as mutating-template]
            [scirepro.predictive-information-experiment :as experiment]))

(def weights [1.0 0.9 0.7 0.5 0.3 0.1 0.0])

(defn weighted-step
  "One hybrid step. Each cell independently selects the full template or
   Baldwin cell dynamic; the selected branch performs both its combine and its
   mutation stages. Endpoints are handled by weighted-evolve, not this function,
   so pure-parent runs consume exactly the parent RNG streams."
  [state ^java.util.Random rng weight]
  (let [{:keys [genotype phenotype]} state
        new-phenotype (engine/phenotype-step genotype phenotype)
        contexts (baldwin/build-context-quadruples phenotype new-phenotype)
        last-index (dec (count genotype))
        bounded-rng (fn [limit] (.nextInt rng (int limit)))
        new-genotype
        (mapv
         (fn [i center]
           (let [left (if (zero? i) 0 (nth genotype (dec i)))
                 right (if (= i last-index) 0 (nth genotype (inc i)))
                 context (when (and (pos? i) (< i last-index))
                           (nth contexts (dec i)))]
             (if (< (.nextDouble rng) weight)
               (mutating-template/evolve-cell
                rng left center right (experiment/context-string->bits context))
               (baldwin/mutate-combined-rule
                (engine/blend-cell left center right) context bounded-rng))))
         (range)
         genotype)]
    {:genotype new-genotype :phenotype new-phenotype}))

(defn weighted-evolve [genotype phenotype generations seed weight]
  (when-not (<= 0.0 weight 1.0)
    (throw (ex-info "weight must be in [0,1]" {:weight weight})))
  (cond
    (= 1.0 weight)
    (mutating-template/coupled-contextual-evolve genotype phenotype generations seed)

    (= 0.0 weight)
    (baldwin/baldwin-evolve genotype phenotype generations
                            (experiment/bounded-rng seed))

    :else
    (let [rng (java.util.Random. (long seed))
          states (vec (take (inc generations)
                            (iterate #(weighted-step % rng weight)
                                     {:genotype (vec genotype)
                                      :phenotype (vec phenotype)})))]
      {:genotype (mapv :genotype states)
       :phenotype (mapv :phenotype states)})))

(defn run-weight [weight seed]
  (let [genotype (engine/seeded-ic seed experiment/width)
        phenotype (engine/seeded-phenotype-ic (+ 100000 seed) experiment/width)
        dynamic-seed (+ 200000 seed)]
    (weighted-evolve genotype phenotype experiment/steps dynamic-seed weight)))

(defn score-run [weight seed]
  (let [result (run-weight weight seed)
        history (experiment/rule-history->sigil-history (:genotype result))
        ais (pi/score-metaca-history
             history {:k experiment/past-window :burn-in experiment/burn-in})
        te (pi/score-metaca-transfer-entropy
            history {:k experiment/past-window :burn-in experiment/burn-in})]
    {:weight weight
     :seed seed
     :mean-ais-corrected (:mean-ais-corrected ais)
     :mean-te-corrected (:mean-te-corrected te)
     :ais-per-plane (mapv #(select-keys % [:plane :ais-corrected])
                          (:per-plane ais))
     :te-per-plane (mapv #(select-keys % [:plane :te-corrected])
                         (:per-plane te))}))

(defn metric-summary [runs key]
  (let [values (mapv key runs)]
    {:n (count values)
     :mean (experiment/mean values)
     :ci95 (experiment/ci95 values)}))

(defn summarize [runs]
  (into (sorted-map)
        (for [weight weights
              :let [weight-runs (filter #(= weight (:weight %)) runs)]]
          [weight {:ais (metric-summary weight-runs :mean-ais-corrected)
                   :te (metric-summary weight-runs :mean-te-corrected)}])))

(defn maxima [summary]
  (let [best (fn [metric]
               (apply max-key #(get-in (val %) [metric :mean]) summary))
        [ais-weight ais] (best :ais)
        [te-weight te] (best :te)]
    {:ais {:weight ais-weight :summary (:ais ais)}
     :te {:weight te-weight :summary (:te te)}}))

(defn weight-label [weight]
  (str/replace (format "%.1f" (double weight)) "." "p"))

(def diagram-weights [1.0 0.9 0.5 0.0])

(defn write-diagrams! [out-dir seed]
  (let [dir (io/file out-dir)]
    (.mkdirs dir)
    (into (sorted-map)
          (for [weight diagram-weights
                :let [path (io/file dir (str "weighted-w-" (weight-label weight)
                                             "-seed-" seed ".png"))
                      result (run-weight weight seed)]]
            (do
              (experiment/write-png! (:genotype result) path)
              [weight (str "data/weighted-predictive-information-diagrams/"
                           (.getName path))])))))

(defn experiment-result []
  (let [runs (mapv (fn [[weight seed]] (score-run weight seed))
                   (for [weight weights seed experiment/seeds] [weight seed]))
        summary (summarize runs)
        pure-template (get summary 1.0)
        hybrids (dissoc summary 1.0 0.0)
        best-hybrid-ais (apply max-key #(get-in (val %) [:ais :mean]) hybrids)
        best-hybrid-te (apply max-key #(get-in (val %) [:te :mean]) hybrids)]
    {:schema/version 1
     :experiment/id :weighted-predictive-information-sweep
     :replay :statistical
     :protocol {:weights weights
                :width experiment/width
                :steps experiment/steps
                :seeds experiment/seeds
                :mixture :per-cell-update
                :template-probability :weight
                :bitplanes 8
                :past-window experiment/past-window
                :burn-in experiment/burn-in}
     :runs runs
     :summary summary
     :maxima (maxima summary)
     :hybrid-vs-template
     {:best-hybrid-ais-weight (key best-hybrid-ais)
      :best-hybrid-ais-mean (get-in (val best-hybrid-ais) [:ais :mean])
      :template-ais-mean (get-in pure-template [:ais :mean])
      :best-hybrid-te-weight (key best-hybrid-te)
      :best-hybrid-te-mean (get-in (val best-hybrid-te) [:te :mean])
      :template-te-mean (get-in pure-template [:te :mean])}}))

(defn -main [& _]
  (let [root (experiment/repo-root)
        path (io/file root "data/weighted-predictive-information-sweep.edn")
        diagrams (write-diagrams!
                  (io/file root "data/weighted-predictive-information-diagrams") 42)
        result (assoc (experiment-result) :diagrams diagrams)]
    (.mkdirs (.getParentFile path))
    (spit path (str (pr-str result) "\n"))
    (prn (select-keys result [:summary :maxima :hybrid-vs-template :diagrams]))))
