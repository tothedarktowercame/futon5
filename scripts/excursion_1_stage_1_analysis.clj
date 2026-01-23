(ns excursion-1-stage-1-analysis
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(defn- usage []
  (str/join
   "\n"
   ["Excursion 1 Stage 1 analysis"
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/excursion_1_stage_1_analysis.clj [options]"
    ""
    "Options:"
    "  --scores PATH   Scores CSV from Stage 1 (required)."
    "  --out PATH      Output EDN analysis (default <scores-dir>/analysis.edn)."
    "  --help          Show this message."]))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag) (recur more (assoc opts :help true))
          (= "--scores" flag) (recur (rest more) (assoc opts :scores (first more)))
          (= "--out" flag) (recur (rest more) (assoc opts :out (first more)))
          :else (recur more (assoc opts :unknown flag))))
      opts)))

(defn- ->double [s]
  (try (Double/parseDouble (str s)) (catch Exception _ 0.0)))

(defn- mean [xs]
  (when (seq xs)
    (/ (reduce + 0.0 xs) (double (count xs)))))

(defn- stddev [xs]
  (when (seq xs)
    (let [m (mean xs)
          var (/ (reduce + 0.0 (map (fn [x]
                                      (let [d (- (double x) (double m))]
                                        (* d d)))
                                    xs))
                 (double (count xs)))]
      (Math/sqrt var))))

(defn- normal-cdf [z]
  ;; Abramowitz-Stegun approximation for Phi(z)
  (let [t (/ 1.0 (+ 1.0 (* 0.2316419 (Math/abs z))))
        d (* 0.3989423 (Math/exp (* -0.5 z z)))
        poly (+ (* 0.3193815 t)
                (* -0.3565638 (Math/pow t 2))
                (* 1.781478 (Math/pow t 3))
                (* -1.821256 (Math/pow t 4))
                (* 1.330274 (Math/pow t 5)))
        p (- 1.0 (* d poly))]
    (if (neg? z) (- 1.0 p) p)))

(defn- t-test-approx [diffs]
  (let [n (count diffs)
        m (mean diffs)
        sd (stddev diffs)
        t (if (and sd (pos? sd)) (/ m (/ sd (Math/sqrt n))) 0.0)
        ;; normal approx for p-value (two-tailed)
        p (if (pos? n)
            (* 2.0 (- 1.0 (normal-cdf (Math/abs t))))
            1.0)]
    {:n n :mean-diff m :std-diff sd :t t :p-approx p}))

(defn- cohen-d [diffs]
  (let [m (mean diffs)
        sd (stddev diffs)]
    (if (and sd (pos? sd)) (/ m sd) 0.0)))

(defn- parse-csv [path]
  (let [lines (->> (str/split-lines (slurp path))
                   (remove str/blank?))
        header (map keyword (str/split (first lines) #","))]
    (mapv (fn [line]
            (zipmap header (str/split line #",")))
          (rest lines))))

(defn- build-index [rows]
  (reduce (fn [acc row]
            (let [seed (Long/parseLong (row :seed))
                  arm (keyword (row :arm))]
              (assoc-in acc [seed arm] row)))
          {}
          rows))

(defn- diffs-for [index arm-a arm-b metric]
  (let [seeds (sort (keys index))]
    (keep (fn [seed]
            (let [a (get-in index [seed arm-a])
                  b (get-in index [seed arm-b])]
              (when (and a b)
                (- (->double (a metric)) (->double (b metric))))))
          seeds)))

(defn -main [& args]
  (let [{:keys [help unknown scores out]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      (nil? scores) (println (usage))
      :else
      (let [rows (parse-csv scores)
            index (build-index rows)
            metrics [:filament :envelope :triad :shift :short]
            comparisons [[:tai-constrained :unconstrained]
                         [:tai-constrained :baseline]]
            results (into {}
                          (for [metric metrics]
                            [metric
                             (into {}
                                   (for [[a b] comparisons]
                                     (let [diffs (vec (diffs-for index a b metric))
                                           stats (t-test-approx diffs)]
                                       [[a b]
                                        (assoc stats
                                               :cohen-d (cohen-d diffs)
                                               :diffs diffs)])))]))
            out (or out (str (io/file (.getParentFile (io/file scores)) "analysis.edn")))
            summary {:scores scores
                     :comparisons comparisons
                     :metrics metrics
                     :results results}]
        (spit out (pr-str summary))
        (println "Wrote" out)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
