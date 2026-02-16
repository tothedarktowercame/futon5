(ns futon5.mmca.triangle-analysis
  "Detect Rule-90 style triangular clearings (stunted trees)."
  (:require [futon5.ca.core :as ca]))

(defn- row->bits [row]
  (cond
    (vector? row) (mapv #(if (zero? %) 0 1) row)
    (string? row) (mapv (fn [c] (if (= c \1) 1 0)) row)
    (sequential? row) (mapv #(if (zero? %) 0 1) row)
    :else (mapv (fn [c] (if (= c \1) 1 0)) (seq (str row)))))

(defn- sigil-row->bitplanes
  "Convert a row of sigils into 8 bitplanes (vectors of 0/1)."
  [row]
  (let [sigils (cond
                 (string? row) (seq row)
                 (sequential? row) row
                 :else (seq (str row)))]
    (reduce (fn [planes sigil]
              (let [sigil-str (if (char? sigil) (str sigil) (str sigil))
                    bits (try (ca/bits-for sigil-str)
                              (catch Exception _ "00000000"))]
                (mapv (fn [plane idx]
                        (conj plane (if (= (nth bits idx) \1) 1 0)))
                      planes
                      (range 8))))
            (vec (repeat 8 []))
            sigils)))

(defn- genotype-bitplane-histories
  "Return 8 histories, one per bitplane."
  [history]
  (reduce (fn [acc row]
            (mapv conj acc (sigil-row->bitplanes row)))
          (vec (repeat 8 []))
          history))

(defn- tie-breaker-fn
  [tie-breaker seed]
  (case tie-breaker
    :random (fn [_ _ _ _] (rand-int 2))
    :zero (fn [_ _ _ _] 0)
    :one (fn [_ _ _ _] 1)
    :hash (fn [row col sigil _]
            (bit-and 1 (hash (str sigil ":" row ":" col ":" seed))))
    (fn [row col _ _]
      (bit-and 1 (+ (long seed) (* 31 row) (* 131 col))))))

(defn- vote-bit
  [bits tie-fn row col sigil]
  (let [ones (count (filter #(= % \1) bits))
        zeros (- (count bits) ones)]
    (cond
      (> ones zeros) 1
      (< ones zeros) 0
      :else (tie-fn row col sigil bits))))

(defn project-genotype-history
  "Project genotype history into a 0/1 history by majority vote over sigil bits.
   tie-breaker: :seeded (default), :hash, :zero, :one, :random."
  [history {:keys [seed tie-breaker] :or {seed 0 tie-breaker :seeded}}]
  (let [tie-fn (tie-breaker-fn tie-breaker seed)]
    (mapv (fn [[row-idx row]]
            (let [sigils (cond
                           (string? row) (seq row)
                           (sequential? row) row
                           :else (seq (str row)))]
              (mapv (fn [col sigil]
                      (let [sigil-str (if (char? sigil) (str sigil) (str sigil))
                            bits (try (ca/bits-for sigil-str)
                                      (catch Exception _ "00000000"))]
                        (vote-bit bits tie-fn row-idx col sigil-str)))
                    (range (count sigils))
                    sigils)))
          (map-indexed vector history))))

(defn- zero-runs
  "Return contiguous zero runs as {:start i :end j :len L} (end exclusive)."
  [row]
  (let [row (row->bits row)
        n (count row)]
    (loop [idx 0
           runs []]
      (if (>= idx n)
        runs
        (if (zero? (nth row idx))
          (let [start idx
                end (loop [j idx]
                      (if (and (< j n) (zero? (nth row j)))
                        (recur (inc j))
                        j))]
            (recur end (conj runs {:start start :end end :len (- end start)})))
          (recur (inc idx) runs))))))

(defn- all-zero?
  [row start end]
  (let [row (row->bits row)
        n (count row)]
    (when (and (<= 0 start) (<= end n) (< start end))
      (every? zero? (subvec row start end)))))

(defn- parent-run?
  "Return true if the previous row has the immediate parent run
  that would expand this run by 1 on both sides."
  [prev {:keys [start end]}]
  (let [p-start (dec start)
        p-end (inc end)]
    (when (<= 0 p-start)
      (all-zero? prev p-start p-end))))

(defn- triangle-height
  "Return the number of shrinking steps from a base run."
  [history row-idx {:keys [start end]}]
  (let [n (count history)]
    (loop [step 1]
      (let [next-row (+ row-idx step)
            next-start (+ start step)
            next-end (- end step)]
        (if (or (>= next-row n) (>= next-start next-end))
          (dec step)
          (if (all-zero? (nth history next-row) next-start next-end)
            (recur (inc step))
            (dec step)))))))

(defn detect-clearings
  "Detect triangular clearings in a history. Options:
  {:min-base 6 :min-height 3}."
  [history {:keys [min-base min-height] :or {min-base 6 min-height 3}}]
  (let [rows (vec history)
        row-count (count rows)]
    (loop [row-idx 0
           clearings []]
      (if (>= row-idx row-count)
        clearings
        (let [row (nth rows row-idx)
              prev (when (pos? row-idx) (nth rows (dec row-idx)))
              runs (filter #(>= (:len %) min-base) (zero-runs row))
              bases (if prev
                      (remove #(parent-run? prev %) runs)
                      runs)
              hits (for [run bases
                         :let [height (triangle-height rows row-idx run)]
                         :when (>= height min-height)]
                     (assoc run
                            :row row-idx
                            :height height
                            :tip-len (- (:len run) (* 2 height))))]
          (recur (inc row-idx) (into clearings hits)))))))

(defn analyze-history
  "Return triangle clearing summary."
  ([history] (analyze-history history {}))
  ([history opts]
   (when (seq history)
     (let [clearings (detect-clearings history opts)
           heights (map :height clearings)
           bases (map :len clearings)
           clearing-count (count clearings)
           generations (count history)
           avg-height (when (seq heights) (/ (reduce + heights) (double clearing-count)))
           avg-base (when (seq bases) (/ (reduce + bases) (double clearing-count)))
           max-height (when (seq heights) (apply max heights))
           density (if (pos? generations) (/ (double clearing-count) generations) 0.0)
           height-score (if (and avg-height (pos? avg-height))
                          (min 1.0 (/ avg-height 10.0))
                          0.0)]
       {:triangle-count clearing-count
        :triangle-avg-height avg-height
        :triangle-max-height max-height
       :triangle-avg-base avg-base
       :triangle-density density
       :triangle-score (* density height-score)}))))

(defn analyze-genotype
  "Analyze genotype history by projecting each sigil to 8 bitplanes."
  ([history] (analyze-genotype history {}))
  ([history opts]
   (when (seq history)
     (let [histories (genotype-bitplane-histories history)
           per-plane (mapv (fn [idx plane]
                             (assoc (analyze-history plane opts) :plane idx))
                           (range 8)
                           histories)
           score-key (fn [m] (or (:triangle-score m) 0.0))
           best (apply max-key score-key per-plane)]
       {:triangle-bitplanes per-plane
        :triangle-best-plane (:plane best)
        :triangle-score-max (:triangle-score best)
        :triangle-count-max (apply max (map :triangle-count per-plane))
        :triangle-density-max (apply max (map :triangle-density per-plane))
        :triangle-avg-height-max (apply max (map :triangle-avg-height per-plane))}))))

(defn analyze-genotype-vote
  "Analyze genotype history after majority-vote projection to 0/1."
  ([history] (analyze-genotype-vote history {}))
  ([history {:keys [seed tie-breaker] :or {seed 0 tie-breaker :seeded} :as opts}]
   (when (seq history)
     (let [projected (project-genotype-history history opts)
           summary (analyze-history projected)]
       (assoc summary
              :triangle-projection :vote
              :triangle-vote-tie-breaker tie-breaker
              :triangle-vote-seed seed)))))
