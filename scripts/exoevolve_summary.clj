(ns exoevolve-summary
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- usage []
  (str/join
   "\n"
   ["Summarize exoevolve EDN log"
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/exoevolve_summary.clj --in PATH --out PATH [--org PATH] [--hit PATH]"
    ""
    "Options:"
    "  --in PATH    Exoevolve EDN log (required)."
    "  --out PATH   Output EDN summary (required)."
    "  --org PATH   Optional Org summary."
    "  --hit PATH   Optional HIT judgements EDN lines (judge-cli output)."]))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag) (recur more (assoc opts :help true))
          (= "--in" flag) (recur (rest more) (assoc opts :in (first more)))
          (= "--out" flag) (recur (rest more) (assoc opts :out (first more)))
          (= "--org" flag) (recur (rest more) (assoc opts :org (first more)))
          (= "--hit" flag) (recur (rest more) (assoc opts :hit (first more)))
          :else (recur more (assoc opts :unknown flag))))
      opts)))

(defn- parse-edn-lines [path]
  (with-open [r (io/reader path)]
    (->> (line-seq r)
         (map str/trim)
         (remove empty?)
         (map (fn [line]
                (try
                  (edn/read-string line)
                  (catch Exception _
                    nil))))
         (remove nil?))))

(def label->num {:not-eoc 0 :borderline 1 :eoc 2})

(defn- rank-map [values]
  (let [indexed (map-indexed vector values)
        sorted (vec (sort-by second indexed))
        groups (partition-by second sorted)]
    (loop [groups groups
           offset 0
           acc {}]
      (if (empty? groups)
        acc
        (let [group (first groups)
              idxs (map first group)
              start (inc offset)
              end (+ offset (count group))
              rank (/ (+ start end) 2.0)
              acc (reduce (fn [m idx] (assoc m idx rank)) acc idxs)]
          (recur (rest groups) end acc))))))

(defn- spearman [xs ys]
  (let [rx (rank-map xs)
        ry (rank-map ys)
        pairs (map (fn [idx] [(rx idx) (ry idx)]) (range (count xs)))
        xs (map first pairs)
        ys (map second pairs)
        mean (fn [vals] (/ (reduce + 0.0 vals) (double (count vals))))
        mx (mean xs)
        my (mean ys)
        cov (reduce + 0.0 (map (fn [x y] (* (- x mx) (- y my))) xs ys))
        sx (Math/sqrt (reduce + 0.0 (map (fn [x] (let [d (- x mx)] (* d d))) xs)))
        sy (Math/sqrt (reduce + 0.0 (map (fn [y] (let [d (- y my)] (* d d))) ys)))]
    (if (and (pos? sx) (pos? sy)) (/ cov (* sx sy)) 0.0)))

(defn- mean [xs]
  (when (seq xs)
    (/ (reduce + 0.0 xs) (double (count xs)))))

(defn- label-by-tertiles [scores]
  (let [n (count scores)
        top (int (Math/ceil (/ n 3.0)))
        bottom top
        ranked (->> scores
                    (map-indexed vector)
                    (sort-by second >))
        idx->label (reduce (fn [m [pos [idx _]]]
                             (cond
                               (< pos top) (assoc m idx :eoc)
                               (>= pos (- n bottom)) (assoc m idx :not-eoc)
                               :else (assoc m idx :borderline)))
                           {}
                           (map-indexed vector ranked))]
    idx->label))

(defn- ->double [s]
  (try (Double/parseDouble (str s)) (catch Exception _ 0.0)))

(defn- scorer-metrics [labels scores]
  (let [idx->pred (label-by-tertiles scores)
        exact (mean (map-indexed (fn [idx label]
                                   (if (= label (idx->pred idx)) 1.0 0.0))
                                 labels))
        ordinal (mean (map-indexed (fn [idx label]
                                     (let [pred (idx->pred idx)
                                           diff (Math/abs (- (label->num label) (label->num pred)))]
                                       (- 1.0 (/ diff 2.0))))
                                   labels))
        strong-disagree (mean (map-indexed (fn [idx label]
                                             (let [pred (idx->pred idx)
                                                   extreme? (or (= label :eoc) (= label :not-eoc))
                                                   opposite? (or (and (= label :eoc) (= pred :not-eoc))
                                                                 (and (= label :not-eoc) (= pred :eoc)))]
                                               (if (and extreme? opposite?) 1.0 0.0)))
                                           labels))
        rho (spearman scores (map label->num labels))]
    {:exact exact
     :ordinal ordinal
     :spearman rho
     :strong-disagree strong-disagree}))

(defn- mean-score [score-map keys]
  (let [vals (map (fn [k] (->double (get score-map k))) keys)
        vals (remove nil? vals)]
    (if (seq vals)
      (/ (reduce + 0.0 vals) (double (count vals)))
      0.0)))

(defn- summarize-hit [hit-path]
  (let [entries (parse-edn-lines hit-path)
        judgements (filter #(= :judgement (:event %)) entries)
        labeled (filter (fn [j] (and (:label j) (:scores j))) judgements)
        labels (map :label labeled)
        scorers [:short :envelope :triad :shift :filament]
        scorer-summaries (into {}
                               (map (fn [scorer]
                                      (let [scores (map (fn [j]
                                                          (->double (get-in j [:scores scorer])))
                                                        labeled)]
                                        [scorer (scorer-metrics labels scores)]))
                                    scorers))
        ensemble-scores (map (fn [j] (mean-score (:scores j) scorers)) labeled)
        ensemble (scorer-metrics labels ensemble-scores)
        thresholds {:exact 0.55 :ordinal 0.70 :spearman 0.40 :strong-disagree 0.15}
        pass? (and (>= (:exact ensemble) (:exact thresholds))
                   (>= (:ordinal ensemble) (:ordinal thresholds))
                   (>= (:spearman ensemble) (:spearman thresholds))
                   (<= (:strong-disagree ensemble) (:strong-disagree thresholds)))]
    {:count (count labeled)
     :thresholds thresholds
     :ensemble ensemble
     :scorers scorer-summaries
     :pass? pass?}))

(defn- avg [xs]
  (when (seq xs)
    (/ (reduce + 0.0 xs) (double (count xs)))))

(defn- percentile [xs q]
  (when (seq xs)
    (let [sorted (vec (sort xs))
          n (count sorted)
          idx (int (Math/floor (* (max 0.0 (min 1.0 q)) (dec n))))]
      (nth sorted idx))))

(defn- collect-runs [entries]
  (filter #(= :run (:event %)) entries))

(defn- collect-windows [entries]
  (filter #(= :window (:event %)) entries))

(defn- collect-errors [entries]
  (filter #(= :error (:event %)) entries))

(defn- collect-checkpoints [entries]
  (filter #(= :checkpoint (:event %)) entries))

(defn- summarize-runs [runs]
  (let [finals (mapv (comp :final :score) runs)
        summaries (map :summary runs)
        dead-count (count (filter #(<= (double (or (:avg-change %) 0.0)) 0.05) summaries))
        confetti-count (count (filter #(>= (double (or (:avg-change %) 0.0)) 0.45) summaries))
        total (count runs)]
    {:count total
     :mean (avg finals)
     :q50 (percentile finals 0.5)
     :q90 (percentile finals 0.9)
     :best (when (seq finals) (apply max finals))
     :dead-rate (if (pos? total) (/ dead-count (double total)) 0.0)
     :confetti-rate (if (pos? total) (/ confetti-count (double total)) 0.0)}))

(defn- summarize-windows [windows]
  (let [stats (map :stats windows)
        means (map :mean stats)
        q50s (map :q50 stats)
        bests (map :best stats)]
    {:count (count windows)
     :mean-mean (avg means)
     :mean-q50 (avg q50s)
     :mean-best (avg bests)
     :last (last windows)}))

(defn- write-org [path summary]
  (let [lines [(str "* Exoevolve Summary")
               ""
               "** Meta"
               (pr-str (:meta summary))
               ""
               "** Runs"
               (pr-str (:runs summary))
               ""
               "** Windows"
               (pr-str (:windows summary))
               ""
               "** Errors"
               (pr-str (:errors summary))
               ""
               "** Checkpoints"
               (pr-str (:checkpoints summary))]]
    (spit path (str/join "\n" lines))))

(defn -main [& args]
  (let [{:keys [help unknown in out org hit]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      (or (nil? in) (nil? out)) (println (usage))
      :else
      (let [entries (vec (parse-edn-lines in))
            meta-entry (first (filter #(= :meta (:event %)) entries))
            runs (vec (collect-runs entries))
            windows (vec (collect-windows entries))
            errors (vec (collect-errors entries))
            checkpoints (vec (collect-checkpoints entries))
            hit-summary (when hit (summarize-hit hit))
            summary {:meta meta-entry
                     :runs (summarize-runs runs)
                     :windows (summarize-windows windows)
                     :errors {:count (count errors)
                              :samples (take 5 errors)}
                     :checkpoints {:count (count checkpoints)
                                   :last (last checkpoints)}
                     :hit hit-summary
                     :events {:total (count entries)}}]
        (spit out (pr-str summary))
        (when org
          (write-org org summary))
        (println "Wrote" out)
        (when org
          (println "Wrote" org))))))
