(ns futon5.mmca.band-analysis)

(defn- char->bit [c]
  (case c \0 0 \1 1 (if (< (int c) 128) 0 1)))

(defn- string->bits [s]
  (mapv char->bit s))

(defn column-values
  "Extract values for column idx across all generations."
  [history idx]
  (mapv #(nth (string->bits %) idx) history))

(defn- shannon-entropy
  "Compute Shannon entropy of a sequence of values."
  [values]
  (let [n (count values)
        freqs (frequencies values)]
    (if (< n 2)
      0.0
      (->> freqs
           vals
           (map #(let [p (/ % n)]
                   (if (pos? p)
                     (* -1.0 p (Math/log p))
                     0.0)))
           (reduce +)))))

(defn column-entropy
  "Entropy of a single column over time."
  [history idx]
  (shannon-entropy (column-values history idx)))

(defn column-change-rate
  "Fraction of generations where column idx changes value."
  [history idx]
  (let [col (column-values history idx)
        pairs (partition 2 1 col)
        changes (count (filter (fn [[a b]] (not= a b)) pairs))]
    (if (< (count col) 2)
      0.0
      (double (/ changes (dec (count col)))))))

(defn column-run-lengths
  "Compute run lengths (consecutive same values) for a column.
   Returns map with :mean-run-length and :max-run-length."
  [history idx]
  (let [col (column-values history idx)
        runs (partition-by identity col)
        lengths (map count runs)]
    {:mean-run-length (if (seq lengths) (double (/ (reduce + lengths) (count lengths))) 0.0)
     :max-run-length (if (seq lengths) (apply max lengths) 0)}))

(defn analyze-column
  "Full analysis of a single column."
  [history idx]
  (let [entropy (column-entropy history idx)
        change-rate (column-change-rate history idx)
        runs (column-run-lengths history idx)]
    {:column idx
     :entropy entropy
     :change-rate change-rate
     :mean-run-length (:mean-run-length runs)
     :max-run-length (:max-run-length runs)
     :classification (cond
                       (< change-rate 0.05) :frozen
                       (> change-rate 0.45) :chaotic
                       (< 0.15 change-rate 0.45) :moderate
                       :else :low-activity)}))

(defn analyze-all-columns
  "Analyze all columns in a history."
  [history]
  (let [len (count (first history))]
    (mapv #(analyze-column history %) (range len))))

(defn band-summary
  "Summarize vertical band characteristics for a run."
  [column-analyses]
  (let [n (count column-analyses)
        by-class (group-by :classification column-analyses)
        frozen-count (count (:frozen by-class))
        chaotic-count (count (:chaotic by-class))
        moderate-count (count (:moderate by-class))
        low-count (count (:low-activity by-class))
        entropies (map :entropy column-analyses)
        change-rates (map :change-rate column-analyses)]
    {:total-columns n
     :frozen-columns frozen-count
     :chaotic-columns chaotic-count
     :moderate-columns moderate-count
     :low-activity-columns low-count
     :frozen-ratio (double (/ frozen-count n))
     :moderate-ratio (double (/ moderate-count n))
     :chaotic-ratio (double (/ chaotic-count n))
     :mean-entropy (/ (reduce + entropies) n)
     :mean-change-rate (/ (reduce + change-rates) n)
     :band-score (double (/ moderate-count n))
     :interpretation (cond
                       (> frozen-count (* 0.7 n)) :mostly-frozen
                       (> chaotic-count (* 0.5 n)) :mostly-chaotic
                       (> moderate-count (* 0.3 n)) :has-active-bands
                       :else :sparse-activity)}))

(defn find-active-bands
  "Find contiguous regions of moderate-activity columns."
  [column-analyses]
  (let [moderate? #(= :moderate (:classification %))
        indexed (map-indexed vector column-analyses)
        moderate-indices (map first (filter #(moderate? (second %)) indexed))]
    (when (seq moderate-indices)
      (reduce (fn [bands idx]
                (if (and (seq bands)
                         (= idx (inc (last (last bands)))))
                  (update bands (dec (count bands)) conj idx)
                  (conj bands [idx])))
              []
              moderate-indices))))

(defn analyze-history
  "Analyze a raw history vector of strings."
  [history]
  (when (seq history)
    (let [col-analyses (analyze-all-columns history)
          summary (band-summary col-analyses)
          bands (find-active-bands col-analyses)]
      (assoc summary
             :generations (count history)
             :active-bands (count bands)
             :band-widths (map count bands)
             :widest-band (if (seq bands) (apply max (map count bands)) 0)))))

(defn analyze-run
  "Analyze a run map with :phe-history or :gen-history."
  [run]
  (let [phe-hist (:phe-history run)
        gen-hist (:gen-history run)
        history (if (seq phe-hist) phe-hist gen-hist)
        history-type (if (seq phe-hist) :phenotype :genotype)]
    (when (seq history)
      (assoc (analyze-history history)
             :history-type history-type))))
