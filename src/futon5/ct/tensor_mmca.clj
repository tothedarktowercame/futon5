(ns futon5.ct.tensor-mmca
  "MMCA-style evaluation helpers for executable tensor runs."
  (:require [futon5.ca.core :as ca]
            [futon5.ct.tensor-exec :as tensor-exec]
            [futon5.mmca.metrics :as metrics]))

(defn history->metrics-history
  "Build per-generation metrics with the same shape produced by MMCA runtime."
  [gen-history]
  (let [history (vec (or gen-history []))]
    (mapv (fn [idx genotype]
            (let [genotype (or genotype "")
                  prev (when (pos? idx) (nth history (dec idx)))
                  len (count genotype)
                  counts (frequencies (seq genotype))
                  change (when (and prev (pos? len))
                           (/ (double (ca/hamming-distance prev genotype))
                              (double len)))]
              {:generation idx
               :length len
               :unique-sigils (count counts)
               :sigil-counts counts
               :entropy (metrics/shannon-entropy genotype)
               :change-rate change}))
          (range (count history))
          history)))

(defn run->mmca-metrics
  "Attach MMCA metric views to any run containing :gen-history.

   If :phe-history is absent and :phenotype is present, a stationary phenotype
   history is synthesized for summary compatibility."
  [run]
  (let [gen-history (vec (or (:gen-history run) []))
        phe-history (or (:phe-history run)
                        (when-let [phenotype (:phenotype run)]
                          (vec (repeat (count gen-history) phenotype))))
        metrics-history (history->metrics-history gen-history)
        base {:gen-history gen-history
              :phe-history phe-history
              :metrics-history metrics-history
              :seed (:seed run)
              :lesion (:lesion run)}
        summary (metrics/summarize-run base)
        episode-summary (metrics/episode-summary base)]
    (merge run
           base
           {:summary summary
            :episode-summary episode-summary})))

(defn run-tensor-mmca
  "Run tensor evolution and evaluate it through MMCA summary paths.

   Accepts the same options as `futon5.ct.tensor-exec/run-tensor-ca` plus
   optional :seed (passed through for deterministic triangle tie-breaking)."
  [opts]
  (-> (tensor-exec/run-tensor-ca opts)
      (assoc :seed (:seed opts)
             :backend :tensor)
      run->mmca-metrics))
