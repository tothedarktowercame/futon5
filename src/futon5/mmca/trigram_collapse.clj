(ns futon5.mmca.trigram-collapse
  "Two-half (early/late) scoring with collapse penalties and exotype attribution."
  (:require [futon5.mmca.metrics :as metrics]))

(def ^:private default-opts
  {:entropy-min 0.2
   :change-min 0.05
   :unique-min 0.05
   :autocorr-max 0.9
   :early-penalty 0.5
   :late-penalty 0.1})

(defn- split-halves [series]
  (let [series (vec (or series []))
        n (count series)
        mid (quot n 2)]
    [(subvec series 0 mid)
     (subvec series mid n)]))

(defn- summarize [series]
  (when (seq series)
    (metrics/summarize-series series)))

(defn- collapse-reasons [summary {:keys [entropy-min change-min unique-min autocorr-max]}]
  (cond-> []
    (< (double (or (:avg-entropy-n summary) 0.0)) entropy-min) (conj :low-entropy)
    (< (double (or (:avg-change summary) 0.0)) change-min) (conj :low-change)
    (< (double (or (:avg-unique summary) 0.0)) unique-min) (conj :low-unique)
    (> (double (or (:temporal-autocorr summary) 0.0)) autocorr-max) (conj :high-autocorr)))

(defn- collapse? [summary opts]
  (let [reasons (collapse-reasons summary opts)]
    {:collapse? (seq reasons)
     :reasons reasons}))

(defn- merge-collapse [a b]
  (let [a (or a {:collapse? false :reasons []})
        b (or b {:collapse? false :reasons []})
        reasons (vec (distinct (concat (:reasons a) (:reasons b))))]
    {:collapse? (or (:collapse? a) (:collapse? b))
     :reasons reasons}))

(defn- half-score [gen-summary phe-summary]
  (let [scores (remove nil? [(when gen-summary (:composite-score gen-summary))
                             (when phe-summary (:composite-score phe-summary))])]
    (if (seq scores)
      (/ (reduce + 0.0 scores) (double (count scores)))
      0.0)))

(defn- score-epoch
  [gen-series phe-series opts]
  (let [[gen-early gen-late] (split-halves gen-series)
        [phe-early phe-late] (split-halves phe-series)
        gen-early-summary (summarize gen-early)
        gen-late-summary (summarize gen-late)
        phe-early-summary (summarize phe-early)
        phe-late-summary (summarize phe-late)
        early-coll-gen (when gen-early-summary (collapse? gen-early-summary opts))
        early-coll-phe (when phe-early-summary (collapse? phe-early-summary opts))
        late-coll-gen (when gen-late-summary (collapse? gen-late-summary opts))
        late-coll-phe (when phe-late-summary (collapse? phe-late-summary opts))
        early-coll (merge-collapse early-coll-gen early-coll-phe)
        late-coll (merge-collapse late-coll-gen late-coll-phe)
        early-collapse? (:collapse? early-coll)
        late-collapse? (:collapse? late-coll)
        early-score (half-score gen-early-summary phe-early-summary)
        late-score (half-score gen-late-summary phe-late-summary)
        base (/ (+ early-score late-score) 2.0)
        score (cond-> base
                early-collapse? (* (:early-penalty opts))
                late-collapse? (* (:late-penalty opts)))]
    {:score score
     :early {:score early-score
             :gen gen-early-summary
             :phe phe-early-summary
             :collapse early-coll}
     :late {:score late-score
            :gen gen-late-summary
            :phe phe-late-summary
            :collapse late-coll}
     :early-collapse? early-collapse?
     :late-collapse? late-collapse?}))

(defn- epochs [gen-history mutations]
  (let [n (count (or gen-history []))
        ticks (->> (map :tick (or mutations []))
                   (filter #(and (number? %) (<= 0 % n)))
                   distinct
                   sort
                   vec)
        bounds (vec (concat [0] ticks [n]))]
    (mapv (fn [[a b]] {:start a :end b})
          (partition 2 1 bounds))))

(defn score-run
  "Score a run with trigram-like collapse penalties and exotype attribution.
  Returns {:score .. :epochs [...]}, where epochs align to exotype mutation ticks."
  [run {:keys [opts]}]
  (let [opts (merge default-opts (or opts {}))
        gen-history (:gen-history run)
        phe-history (:phe-history run)
        muts (:exotype-mutations run)
        epochs (epochs gen-history muts)
        epoch-summaries
        (mapv (fn [{:keys [start end]}]
                (let [gen (subvec (vec (or gen-history [])) start end)
                      phe (subvec (vec (or phe-history [])) start end)
                      scored (score-epoch gen phe opts)
                      length (max 1 (- end start))]
                  (merge {:tick-start start
                          :tick-end end
                          :length length}
                         scored)))
              epochs)
        total-len (reduce + 0 (map :length epoch-summaries))
        overall (if (pos? total-len)
                  (/ (reduce + 0.0 (map (fn [e] (* (:score e) (:length e))) epoch-summaries))
                     (double total-len))
                  0.0)]
    {:score overall
     :epochs epoch-summaries
     :mutations muts
     :opts opts}))
