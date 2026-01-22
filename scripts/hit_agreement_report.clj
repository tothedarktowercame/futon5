(ns hit-agreement-report
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(defn- usage []
  (str/join
   "\n"
   ["Summarize HIT judgements and agreement with scorers/controllers."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/hit_agreement_report.clj [options]"
    ""
    "Options:"
    "  --inputs PATH      HIT inputs list (optional)."
    "  --judgements PATH  Judgements EDN lines (default /tmp/mmca-judgements.edn)."
    "  --scores PATH      Scores CSV (optional; mission_0_regime_mix format)."
    "  --out PATH         Output report path (default /tmp/hit-agreement-report.txt)."
    "  --help             Show this message."]))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--inputs" flag)
          (recur (rest more) (assoc opts :inputs (first more)))

          (= "--judgements" flag)
          (recur (rest more) (assoc opts :judgements (first more)))

          (= "--scores" flag)
          (recur (rest more) (assoc opts :scores (first more)))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- read-edn-lines [path]
  (->> (str/split-lines (slurp path))
       (remove str/blank?)
       (mapv edn/read-string)))

(defn- parse-csv [path]
  (let [lines (->> (str/split-lines (slurp path))
                   (remove str/blank?))
        header (str/split (first lines) #",")
        rows (map #(str/split % #",") (rest lines))]
    (mapv (fn [row]
            (zipmap (map keyword header) row))
          rows)))

(def label->num {:not-eoc 0 :borderline 1 :eoc 2})

(defn- ->double [s]
  (try (Double/parseDouble (str s)) (catch Exception _ 0.0)))

(defn- mean [xs]
  (when (seq xs) (/ (reduce + 0.0 xs) (double (count xs)))))

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

(defn- score-scorer [scorer labeled run-id->scores]
  (let [rows (map (fn [entry]
                    (let [row (run-id->scores (:run/id entry))
                          score (->double (row (keyword (name scorer))))]
                      {:label (:label entry)
                       :score score}))
                  labeled)
        scores (map :score rows)
        labels (map :label rows)
        idx->pred (label-by-tertiles scores)
        accuracy (mean (map-indexed (fn [idx label]
                                      (if (= label (idx->pred idx)) 1.0 0.0))
                                    labels))
        ord-agree (mean (map-indexed (fn [idx label]
                                       (let [pred (idx->pred idx)
                                             diff (Math/abs (- (label->num label) (label->num pred)))]
                                         (- 1.0 (/ diff 2.0))))
                                     labels))
        rho (spearman scores (map label->num labels))]
    {:scorer scorer
     :accuracy accuracy
     :ordinal ord-agree
     :spearman rho}))

(defn- load-controller [path]
  (let [run (edn/read-string (slurp path))]
    (get-in run [:cyber-mmca/meta :controller])))

(defn -main [& args]
  (let [{:keys [help unknown inputs judgements scores out]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      :else
      (let [judgements (read-edn-lines (or judgements "/tmp/mmca-judgements.edn"))
            inputs (when inputs
                     (->> (str/split-lines (slurp inputs))
                          (remove str/blank?)
                          set))
            judgements (if inputs
                         (filter #(contains? inputs (:path %)) judgements)
                         judgements)
            labeled (mapv (fn [j]
                            (let [controller (load-controller (:path j))]
                              (assoc j :controller controller)))
                          judgements)
            out (or out "/tmp/hit-agreement-report.txt")
            labels (frequencies (map :label labeled))
            by-controller (frequencies (map (juxt :controller :label) labeled))
            scores-table (when scores
                           (let [rows (parse-csv scores)
                                 run-id->scores (into {}
                                                      (map (fn [row]
                                                             [(Long/parseLong (row :run_id)) row])
                                                           rows))
                                 labeled (keep (fn [j]
                                                 (when-let [run-id (:run/id j)] j))
                                               labeled)
                                 scorers [:short :envelope :triad :shift :filament]
                                 results (map #(score-scorer % labeled run-id->scores) scorers)]
                             results))]
        (spit out
              (str "count " (count labeled) "\n"
                   "labels " labels "\n"
                   "by-controller " by-controller "\n"
                   (when scores-table
                     (str "\n| scorer | exact | ordinal | spearman |\n"
                          "|--------+-------+---------+----------|\n"
                          (str/join "\n"
                                    (map (fn [{:keys [scorer accuracy ordinal spearman]}]
                                           (format "| %s | %.3f | %.3f | %.3f |"
                                                   (name scorer) accuracy ordinal spearman))
                                         scores-table))
                          "\n"))))
        (println "Wrote" out)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
