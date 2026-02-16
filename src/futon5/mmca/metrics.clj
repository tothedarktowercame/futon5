(ns futon5.mmca.metrics
  "Metric helpers for MMCA runs."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [futon5.ca.core :as ca]
            [futon5.mmca.band-analysis :as band]
            [futon5.mmca.triangle-analysis :as tri]))

(defn avg [xs]
  (when (seq xs)
    (/ (reduce + 0.0 xs) (double (count xs)))))

(defn shannon-entropy [s]
  (let [chars (seq (or s ""))
        total (double (count chars))]
    (if (pos? total)
      (- (reduce (fn [acc [_ cnt]]
                   (let [p (/ cnt total)]
                     (if (pos? p)
                       (+ acc (* p (/ (Math/log p) (Math/log 2.0))))
                       acc)))
                 0.0
                 (frequencies chars)))
      0.0)))

(defn hamming-rate [a b]
  (let [len (count a)]
    (when (and a b (pos? len))
      (/ (double (reduce + (map (fn [x y] (if (= x y) 0 1)) a b))) len))))

(defn lz78-token-count [s]
  (let [chars (vec (seq (or s "")))]
    (loop [idx 0
           dict #{}
           tokens 0]
      (let [n (count chars)]
        (if (>= idx n)
          tokens
          (let [[next-idx next-dict]
                (loop [j (inc idx)]
                  (if (> j n)
                    [n (conj dict (apply str (subvec chars idx n)))]
                    (let [substr (apply str (subvec chars idx j))]
                      (if (contains? dict substr)
                        (recur (inc j))
                        [(+ idx (count substr)) (conj dict substr)]))))]
            (recur next-idx next-dict (inc tokens))))))))

(defn compressibility-metrics [history]
  (let [entries (remove nil? history)
        lengths (map count entries)
        tokens (map lz78-token-count entries)
        ratios (map (fn [t len]
                      (when (pos? len)
                        (/ (double t) (double len))))
                    tokens lengths)]
    {:lz78-tokens (avg tokens)
     :lz78-ratio (avg ratios)}))

(defn spatial-autocorr [s]
  (let [chars (vec (seq (or s "")))
        pairs (partition 2 1 chars)
        total (count pairs)]
    (when (pos? total)
      (/ (double (count (filter (fn [[a b]] (= a b)) pairs)))
         total))))

(defn- row->vec [row]
  (cond
    (vector? row) row
    (string? row) (vec row)
    (sequential? row) (vec row)
    :else (vec (seq (str row)))))

(defn- diag-autocorr-pair [row-a row-b shift]
  (let [a (row->vec row-a)
        b (row->vec row-b)
        len (min (count a) (count b))
        shift (int shift)
        offset (Math/abs shift)
        n (- len offset)
        start-a (if (neg? shift) offset 0)
        start-b (if (pos? shift) offset 0)]
    (when (pos? n)
      (loop [idx 0
             matches 0]
        (if (>= idx n)
          (/ (double matches) (double n))
          (recur (inc idx)
                 (if (= (nth a (+ start-a idx))
                        (nth b (+ start-b idx)))
                   (inc matches)
                   matches)))))))

(defn- diag-autocorr [history shift]
  (let [pairs (partition 2 1 history)
        diag (keep (fn [[a b]] (diag-autocorr-pair a b shift)) pairs)]
    (avg diag)))

(defn autocorr-metrics [history]
  (let [entries (remove nil? history)
        spatial (keep spatial-autocorr entries)
        pairs (partition 2 1 entries)
        temporal (keep (fn [[a b]]
                         (when-let [rate (hamming-rate a b)]
                           (- 1.0 rate)))
                       pairs)
        diag-left (diag-autocorr entries -1)
        diag-right (diag-autocorr entries 1)
        diag-max (when (or diag-left diag-right)
                   (apply max (remove nil? [diag-left diag-right])))]
    {:spatial-autocorr (avg spatial)
     :temporal-autocorr (avg temporal)
     :diag-autocorr-left diag-left
     :diag-autocorr-right diag-right
     :diag-autocorr diag-max}))

(defn phenotype-metrics [phe-history]
  (let [pairs (partition 2 1 phe-history)
        changes (keep (fn [[a b]] (hamming-rate a b)) pairs)
        entropies (map shannon-entropy phe-history)
        lengths (map count phe-history)
        max-entropies (map (fn [len]
                             (when (pos? len)
                               (/ (Math/log len) (Math/log 2.0))))
                           lengths)
        entropy-norms (map (fn [entropy max-e]
                             (when (and entropy max-e (pos? max-e))
                               (/ (double entropy) (double max-e))))
                           entropies max-entropies)]
    {:phe-entropy (avg entropies)
     :phe-change (avg changes)
     :phe-entropy-n (avg entropy-norms)}))

(defn interestingness
  "Heuristic: higher entropy, higher change-rate, and higher diversity."
  [metrics-history phe-history]
  (let [usable (remove nil? metrics-history)
        entropies (keep :entropy usable)
        changes (keep :change-rate usable)
        lengths (keep :length usable)
        uniques (keep :unique-sigils usable)
        max-entropies (map (fn [len]
                             (when (and len (pos? len))
                               (/ (Math/log len) (Math/log 2.0))))
                           lengths)
        entropy-norms (map (fn [entropy max-e]
                             (when (and entropy max-e (pos? max-e))
                               (/ entropy max-e)))
                           entropies max-entropies)
        unique-ratios (map (fn [unique len]
                             (when (and unique len (pos? len))
                               (/ (double unique) (double len))))
                           uniques lengths)
        avg-entropy (avg entropies)
        avg-change (avg changes)
        avg-unique (avg unique-ratios)
        avg-entropy-n (avg entropy-norms)
        {:keys [phe-entropy phe-change phe-entropy-n]}
        (when (seq phe-history) (phenotype-metrics phe-history))
        entropy-n (if (and phe-entropy-n avg-entropy-n)
                    (/ (+ phe-entropy-n avg-entropy-n) 2.0)
                    (or phe-entropy-n avg-entropy-n))
        change (if (and phe-change avg-change)
                 (+ (* 0.7 avg-change) (* 0.3 phe-change))
                 (or phe-change avg-change))
        diversity (or avg-unique 0.1)
        score (when (and entropy-n change diversity)
                (* 100.0 (max 0.05 entropy-n)
                   (max 0.05 change)
                   (max 0.05 diversity)))]
    {:score score
     :avg-entropy avg-entropy
     :avg-entropy-n avg-entropy-n
     :avg-change avg-change
     :avg-unique avg-unique
     :phe-entropy phe-entropy
     :phe-change phe-change}))

(defn centered-score [x]
  (when (number? x)
    (max 0.0 (- 1.0 (* 2.0 (Math/abs (- x 0.5)))))))

(defn- band-score [x center width]
  (when (and (number? x) (pos? (double width)))
    (max 0.0 (- 1.0 (/ (Math/abs (- x center)) (double width))))))

(defn structured-chaos-score
  "Heuristic: diagonal coherence + mid-band spatial structure + high activity."
  [{:keys [avg-entropy-n avg-change]} {:keys [spatial-autocorr diag-autocorr]}]
  (let [diag (or diag-autocorr 0.0)
        spatial-score (or (band-score spatial-autocorr 0.5 0.35) 0.0)
        change-score (or (band-score avg-change 0.8 0.45) 0.0)
        entropy-score (or (band-score avg-entropy-n 0.7 0.4) 0.0)
        chaos (max change-score entropy-score)]
    (* diag spatial-score chaos)))

(defn coherence-score
  [{:keys [avg-entropy-n avg-change]} {:keys [spatial-autocorr temporal-autocorr]}]
  (let [entropy-score (or (band-score avg-entropy-n 0.6 0.35) 0.0)
        change-score (or (band-score avg-change 0.2 0.18) 0.0)
        spatial-score (or (band-score spatial-autocorr 0.55 0.3) 0.0)
        temporal-score (or (band-score temporal-autocorr 0.6 0.3) 0.0)]
    (* entropy-score change-score spatial-score temporal-score)))

(defn- lesion-penalty
  [lesion-summary]
  (if-let [discrepancy (:lesion/half-discrepancy lesion-summary)]
    (/ 1.0 (+ 1.0 (/ (double discrepancy) 10.0)))
    1.0))

(defn composite-score
  "Blend interestingness with compressibility, autocorr balance, and coherence."
  [{:keys [score]} {:keys [lz78-ratio]} {:keys [temporal-autocorr]} lesion-summary coherence]
  (let [i (or score 0.0)
        i-n (/ i 100.0)
        c (or (centered-score lz78-ratio) 0.0)
        a (or (centered-score temporal-autocorr) 0.0)
        coh (or coherence 0.0)
        base (+ (* 0.45 i-n) (* 0.2 c) (* 0.2 a) (* 0.15 coh))
        penalty (lesion-penalty lesion-summary)]
    (* 100.0 base penalty)))

(defn- series-metrics-history [series]
  (let [entries (vec (remove nil? series))]
    (mapv (fn [idx s]
            (let [prev (when (pos? idx) (nth entries (dec idx)))
                  len (count s)
                  change (when (and prev (pos? len))
                           (hamming-rate prev s))
                  counts (frequencies s)]
              {:entropy (shannon-entropy s)
               :change-rate change
               :unique-sigils (count counts)
               :length len}))
          (range (count entries))
          entries)))

(defn- prefixed-summary
  [prefix summary]
  (let [k (fn [suffix] (keyword prefix suffix))]
    {(k "composite-score") (:composite-score summary)
     (k "coherence") (:coherence summary)
     (k "structured-chaos-score") (:structured-chaos-score summary)
     (k "score") (:score summary)
     (k "avg-change") (:avg-change summary)
     (k "avg-entropy-n") (:avg-entropy-n summary)
     (k "avg-unique") (:avg-unique summary)
     (k "lz78-ratio") (:lz78-ratio summary)
     (k "temporal-autocorr") (:temporal-autocorr summary)
     (k "spatial-autocorr") (:spatial-autocorr summary)
     (k "diag-autocorr") (:diag-autocorr summary)}))

(defn summarize-series [series]
  (let [metrics-history (series-metrics-history series)
        interesting (interestingness metrics-history nil)
        compress (compressibility-metrics series)
        autocorr (autocorr-metrics series)
        coherence (coherence-score interesting autocorr)
        structured (structured-chaos-score interesting autocorr)
        composite (composite-score interesting compress autocorr nil coherence)]
    (merge {:composite-score composite
            :coherence coherence
            :structured-chaos-score structured}
           interesting
           compress
           autocorr)))

(defn- half-diff [s]
  (let [s (or s "")
        len (count s)
        mid (quot len 2)]
    (when (and (pos? len) (> mid 0))
      (hamming-rate (subs s 0 mid) (subs s mid)))))

(defn- lesion-metrics [series tick]
  (let [pre (take (inc tick) series)
        post (drop tick series)]
    {:lesion/pre-half-diff (avg (keep half-diff pre))
     :lesion/post-half-diff (avg (keep half-diff post))
     :lesion/post-entropy (avg (map shannon-entropy post))}))

(defn- split-halves [series]
  (map (fn [s]
         (let [len (count s)
               mid (quot len 2)]
           [(subs s 0 mid) (subs s mid)]))
       series))

(defn summarize-run [result]
  (let [metrics-history (:metrics-history result)
        gen-history (:gen-history result)
        phe-history (:phe-history result)
        stasis-step (ca/first-stasis-step gen-history)
        interesting (interestingness metrics-history phe-history)
        gen-interesting (interestingness metrics-history nil)
        gen-compress (compressibility-metrics gen-history)
        gen-autocorr (autocorr-metrics gen-history)
        gen-coherence (coherence-score gen-interesting gen-autocorr)
        gen-structured (structured-chaos-score gen-interesting gen-autocorr)
        gen-composite (composite-score gen-interesting gen-compress gen-autocorr nil gen-coherence)
        gen-summary (prefixed-summary "gen"
                                      (merge gen-interesting
                                             gen-compress
                                             gen-autocorr
                                             {:coherence gen-coherence
                                              :structured-chaos-score gen-structured
                                              :composite-score gen-composite}))
        phe-series-summary (when (seq phe-history) (summarize-series phe-history))
        phe-summary (when phe-series-summary (prefixed-summary "phe" phe-series-summary))
        basis (if (seq phe-history) phe-history gen-history)
        band-analysis (band/analyze-run result)
        band-summary (when band-analysis
                       {:band-score (:band-score band-analysis)
                        :band-moderate-ratio (:moderate-ratio band-analysis)
                        :band-chaotic-ratio (:chaotic-ratio band-analysis)
                        :band-frozen-ratio (:frozen-ratio band-analysis)
                        :band-interpretation (:interpretation band-analysis)
                        :band-active-bands (:active-bands band-analysis)
                        :band-widest-band (:widest-band band-analysis)
                        :band-history-type (:history-type band-analysis)
                        :band-row-periodic? (:row-periodic? band-analysis)
                        :band-row-period (:row-period band-analysis)
                        :band-row-period-strength (:row-period-strength band-analysis)})
        gen-triangle (when (seq gen-history) (tri/analyze-genotype gen-history))
        gen-triangle-summary (when gen-triangle
                               {:gen/triangle-best-plane (:triangle-best-plane gen-triangle)
                                :gen/triangle-score-max (:triangle-score-max gen-triangle)
                                :gen/triangle-count-max (:triangle-count-max gen-triangle)
                                :gen/triangle-density-max (:triangle-density-max gen-triangle)
                                :gen/triangle-avg-height-max (:triangle-avg-height-max gen-triangle)})
        gen-triangle-vote (when (seq gen-history)
                            (tri/analyze-genotype-vote gen-history
                                                       {:seed (:seed result)
                                                        :tie-breaker :seeded}))
        gen-triangle-vote-summary (when gen-triangle-vote
                                    {:gen/triangle-vote-score (:triangle-score gen-triangle-vote)
                                     :gen/triangle-vote-count (:triangle-count gen-triangle-vote)
                                     :gen/triangle-vote-density (:triangle-density gen-triangle-vote)
                                     :gen/triangle-vote-avg-height (:triangle-avg-height gen-triangle-vote)
                                     :gen/triangle-vote-tie-breaker (:triangle-vote-tie-breaker gen-triangle-vote)})
        gen-band (when (seq gen-history) (band/analyze-history gen-history {:row->values vec}))
        gen-band-summary (when gen-band
                           {:gen/band-score (:band-score gen-band)
                            :gen/band-moderate-ratio (:moderate-ratio gen-band)
                            :gen/band-chaotic-ratio (:chaotic-ratio gen-band)
                            :gen/band-frozen-ratio (:frozen-ratio gen-band)
                            :gen/band-interpretation (:interpretation gen-band)
                            :gen/band-active-bands (:active-bands gen-band)
                            :gen/band-widest-band (:widest-band gen-band)
                            :gen/band-row-periodic? (:row-periodic? gen-band)
                            :gen/band-row-period (:row-period gen-band)
                            :gen/band-row-period-strength (:row-period-strength gen-band)})
        phe-band (when (seq phe-history) (band/analyze-history phe-history))
        phe-band-summary (when phe-band
                           {:phe/band-score (:band-score phe-band)
                            :phe/band-moderate-ratio (:moderate-ratio phe-band)
                            :phe/band-chaotic-ratio (:chaotic-ratio phe-band)
                            :phe/band-frozen-ratio (:frozen-ratio phe-band)
                            :phe/band-interpretation (:interpretation phe-band)
                            :phe/band-active-bands (:active-bands phe-band)
                            :phe/band-widest-band (:widest-band phe-band)
                            :phe/band-row-periodic? (:row-periodic? phe-band)
                            :phe/band-row-period (:row-period phe-band)
                            :phe/band-row-period-strength (:row-period-strength phe-band)})
        triangle (when (seq phe-history) (tri/analyze-history phe-history))
        triangle-summary (when triangle (assoc triangle :triangle-history-type :phenotype))
        compress (compressibility-metrics basis)
        autocorr (autocorr-metrics basis)
        coherence (coherence-score interesting autocorr)
        structured (structured-chaos-score interesting autocorr)
        lesion (:lesion result)
        lesion-target (:target lesion)
        lesion-series (cond
                        (and (= lesion-target :phenotype) (seq phe-history)) phe-history
                        (seq phe-history) phe-history
                        :else gen-history)
        lesion-summary (when (and lesion (seq lesion-series))
                         (lesion-metrics lesion-series (:tick lesion)))
        lesion-post (when (and lesion (seq lesion-series))
                      (drop (:tick lesion) lesion-series))
        [left-series right-series] (when (seq lesion-post)
                                     [(map first (split-halves lesion-post))
                                      (map second (split-halves lesion-post))])
        left-summary (when (seq left-series) (summarize-series left-series))
        right-summary (when (seq right-series) (summarize-series right-series))
        discrepancy (when (and left-summary right-summary)
                      (Math/abs (- (double (:composite-score left-summary))
                                   (double (:composite-score right-summary)))))
        lesion-summary (cond-> lesion-summary
                         discrepancy (assoc :lesion/half-discrepancy discrepancy))
        base-composite (composite-score interesting compress autocorr lesion-summary coherence)
        composite (if (seq phe-history)
                    (+ (* 0.65 base-composite) (* 0.35 gen-composite))
                    base-composite)]
    (merge {:composite-score composite
            :coherence coherence
            :structured-chaos-score structured}
           interesting
           compress
           autocorr
           {:first-stasis-step stasis-step}
           band-summary
           gen-triangle-summary
           gen-triangle-vote-summary
           triangle-summary
           gen-band-summary
           phe-band-summary
           gen-summary
           phe-summary
           lesion-summary
           (when (and lesion-summary left-summary right-summary)
             {:lesion/half (:half lesion)
              :lesion/left left-summary
              :lesion/right right-summary}))))

(defn- clamp01 [x]
  (cond
    (nil? x) 0.0
    (< x 0.0) 0.0
    (> x 1.0) 1.0
    :else (double x)))

(defn- unique-ratio [{:keys [unique-sigils length]}]
  (when (and unique-sigils length (pos? length))
    (/ (double unique-sigils) (double length))))

(defn- macro-action-step
  "Infer a coarse macro-action set from per-tick metrics.
  Draft heuristic: change-rate drives pressure, diversity drives selectivity,
  temporal autocorr and change-rate hint at structure-preserve/disrupt."
  [{:keys [change-rate temporal-autocorr] :as metrics}]
  (let [pressure (clamp01 change-rate)
        diversity (clamp01 (unique-ratio metrics))
        selectivity (clamp01 (- 1.0 (or diversity 0.0)))
        structure-preserve? (and (number? temporal-autocorr)
                                 (>= temporal-autocorr 0.6)
                                 (or (nil? change-rate) (< change-rate 0.25)))
        pressure-tag (cond
                       (>= pressure 0.5) :pressure-up
                       (< pressure 0.2) :pressure-down)
        selectivity-tag (cond
                          (>= selectivity 0.6) :selectivity-up
                          (< selectivity 0.4) :selectivity-down)
        structure-tag (if structure-preserve?
                        :structure-preserve
                        :structure-disrupt)]
    (vec (remove nil? [pressure-tag selectivity-tag structure-tag]))))

(defn macro-trace
  "Return a per-tick vector of macro-action sets."
  [metrics-history]
  (->> (remove nil? metrics-history)
       (mapv macro-action-step)))

(declare classify-regime)

(def ^:private default-regime-thresholds
  {:activity-low 0.1
   :activity-high 0.7
   :struct-low 0.4
   :static-struct 0.8
   :static-activity 0.35
   :chaos-change 0.55
   :chaos-autocorr 0.35
   :magma-entropy 0.85
   :magma-change 0.45})

(def ^:dynamic *regime-thresholds-path*
  "futon5/resources/regime-thresholds.edn")

(defonce ^:private regime-thresholds-cache (atom nil))

(defn- load-regime-thresholds []
  (let [path *regime-thresholds-path*]
    (when (and path (.exists (io/file path)))
      (edn/read-string (slurp path)))))

(defn regime-thresholds
  "Return regime thresholds, merging defaults with any on-disk overrides."
  []
  (or @regime-thresholds-cache
      (let [loaded (load-regime-thresholds)
            merged (merge default-regime-thresholds (or loaded {}))]
        (reset! regime-thresholds-cache merged)
        merged)))

(defn reload-regime-thresholds!
  "Reload regime thresholds from disk."
  []
  (reset! regime-thresholds-cache
          (merge default-regime-thresholds (or (load-regime-thresholds) {}))))

(defn- entropy-n
  [{:keys [entropy length]}]
  (let [len (or length 0)
        max-entropy (when (pos? len)
                      (/ (Math/log len) (Math/log 2.0)))]
    (when (and (number? entropy) (number? max-entropy) (pos? max-entropy))
      (/ (double entropy) (double max-entropy)))))

(defn- avg-or-nil [xs]
  (when (seq xs)
    (avg xs)))

(defn- nan? [x]
  (and (number? x) (Double/isNaN (double x))))

(defn- ensure-normalized!
  [k v]
  (when (number? v)
    (when (or (nan? v) (< v 0.0) (> v 1.0))
      (throw (ex-info "Windowed metric violates normalization contract."
                      {:metric k :value v})))))

(defn- normalize-window
  [k v]
  (when (number? v)
    (let [v (double v)]
      (ensure-normalized! k v)
      v)))

(defn- temporal-autocorr-window [series]
  (let [pairs (partition 2 1 series)
        temporal (keep (fn [[a b]]
                         (when-let [rate (hamming-rate a b)]
                           (- 1.0 rate)))
                       pairs)]
    (avg-or-nil temporal)))

(defn- window-summary-from-metrics [entries]
  (let [changes (keep :change-rate entries)
        uniques (keep unique-ratio entries)
        entropies (keep entropy-n entries)
        temporal (keep :temporal-autocorr entries)]
    {:avg-change (avg-or-nil changes)
     :avg-unique (avg-or-nil uniques)
     :avg-entropy-n (avg-or-nil entropies)
     :temporal-autocorr (avg-or-nil temporal)}))

(defn- window-summary-from-gen-history [series]
  (let [changes (keep (fn [[a b]] (hamming-rate a b)) (partition 2 1 series))
        uniques (keep (fn [s]
                        (when (seq s)
                          (/ (double (count (frequencies s)))
                             (double (count s)))))
                      series)
        entropies (keep shannon-entropy series)
        lengths (map count series)
        max-entropies (map (fn [len] (when (pos? len) 1.0)) lengths)
        entropy-norms (map (fn [entropy max-e]
                             (when (and entropy max-e (pos? max-e))
                               (/ entropy max-e)))
                           entropies max-entropies)]
    {:avg-change (avg-or-nil changes)
     :avg-unique (avg-or-nil uniques)
     :avg-entropy-n (avg-or-nil entropy-norms)
     :temporal-autocorr (temporal-autocorr-window series)}))

(defn windowed-macro-features
  "Return a sequence of per-window macro features.

  Accepts either a run map (with :metrics-history and/or :gen-history) or a
  raw metrics-history sequence. opts expects {:W N :S N} for window/stride."
  [history {:keys [W S] :or {W 10 S 5}}]
  (let [metrics-history (cond
                          (map? history) (:metrics-history history)
                          (sequential? history) history
                          :else nil)
        gen-history (when (map? history) (:gen-history history))
        metric-count (count (or metrics-history []))
        gen-count (count (or gen-history []))
        base-count (if (seq metrics-history) metric-count gen-count)
        n (max 0 (int base-count))
        w (max 1 (int W))
        s (max 1 (int S))]
    (loop [start 0
           acc []]
      (if (>= start n)
        acc
        (let [end (min n (+ start w))
              metric-window (when (and metrics-history (< start metric-count))
                              (subvec (vec metrics-history) start (min metric-count end)))
              gen-window (when (and gen-history (< start gen-count))
                           (subvec (vec gen-history) start (min gen-count end)))
              summary (cond
                        (seq metric-window) (window-summary-from-metrics metric-window)
                        (seq gen-window) (window-summary-from-gen-history gen-window)
                        :else {})
              pressure (normalize-window :pressure (:avg-change summary))
              selectivity (when-let [unique (:avg-unique summary)]
                            (normalize-window :selectivity (- 1.0 unique)))
              structure (normalize-window :structure (:temporal-autocorr summary))
              activity (normalize-window :activity (:avg-change summary))
              regime (when (or pressure selectivity structure)
                       (classify-regime summary {:temporal-autocorr (:temporal-autocorr summary)}))]
          (recur (+ start s)
                 (conj acc {:w-start start
                            :w-end (dec end)
                            :summary summary
                            :pressure pressure
                            :selectivity selectivity
                            :structure structure
                            :activity activity
                            :regime regime})))))))

(defn- classify-regime
  "Draft regime classifier based on summary metrics."
  [{:keys [avg-change avg-entropy-n]} {:keys [temporal-autocorr]}]
  (let [{:keys [activity-low activity-high struct-low static-struct static-activity
                chaos-change chaos-autocorr magma-entropy magma-change]}
        (regime-thresholds)
        activity (when (number? avg-change) avg-change)
        structure (when (number? temporal-autocorr) temporal-autocorr)]
    (cond
      (and activity (< activity activity-low))
      :freeze

      (and activity (> activity activity-high)
           structure (< structure struct-low))
      :magma

      (and structure (>= structure static-struct)
           activity (>= activity activity-low) (< activity static-activity))
      :static

      (and (number? avg-change) (>= avg-change chaos-change)
           (number? temporal-autocorr) (< temporal-autocorr chaos-autocorr))
      :chaos

      (and (number? avg-entropy-n) (>= avg-entropy-n magma-entropy)
           (number? avg-change) (>= avg-change magma-change))
      :magma

      :else
      :eoc)))

(defn episode-summary
  "Return a canonical summary map for a run result."
  [{:keys [metrics-history gen-history phe-history] :as result}]
  (let [summary (summarize-run {:metrics-history metrics-history
                                :gen-history gen-history
                                :phe-history phe-history
                                :lesion (:lesion result)})
        autocorr (select-keys summary [:spatial-autocorr :temporal-autocorr])
        regime (classify-regime summary autocorr)
        pressure-avg (clamp01 (:avg-change summary))
        selectivity-avg (clamp01 (- 1.0 (or (:avg-unique summary) 0.0)))
        metastability (clamp01 (:coherence summary))]
    {:regime regime
     :macro-trace (macro-trace metrics-history)
     :pressure-avg pressure-avg
     :selectivity-avg selectivity-avg
     :metastability metastability}))
