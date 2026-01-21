(ns futon5.mmca.hex-eval
  "Evaluate hexagram predictions against labelled judgements."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(defn- usage []
  (str/join
   "\n"
   ["Hexagram evaluation"
    ""
    "Usage:"
    "  bb -cp src:resources -m futon5.mmca.hex-eval --labels PATH"
    ""
    "Options:"
    "  --labels PATH       EDN lines file from judge-cli."
    "  --positive KW       Label treated as positive (default :eoc)."
    "  --help              Show this message."]))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--labels" flag)
          (recur (rest more) (assoc opts :labels (first more)))

          (= "--positive" flag)
          (recur (rest more) (assoc opts :positive (some-> (first more) keyword)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- read-lines [path]
  (->> (slurp path)
       str/split-lines
       (map str/trim)
       (remove str/blank?)
       (map edn/read-string)
       vec))

(defn- classifier->label [hex-class positive]
  (if (= hex-class :tai) positive :not-eoc))

(defn- metrics [counts]
  (let [{:keys [tp fp tn fn]} counts
        tp (double tp) fp (double fp) tn (double tn) fn (double fn)
        total (+ tp fp tn fn)
        accuracy (if (pos? total) (/ (+ tp tn) total) 0.0)
        precision (if (pos? (+ tp fp)) (/ tp (+ tp fp)) 0.0)
        recall (if (pos? (+ tp fn)) (/ tp (+ tp fn)) 0.0)
        f1 (if (pos? (+ precision recall))
             (/ (* 2.0 precision recall) (+ precision recall))
             0.0)]
    {:accuracy accuracy :precision precision :recall recall :f1 f1}))

(defn -main [& args]
  (let [{:keys [help unknown labels positive]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do
                (println "Unknown option:" unknown)
                (println)
                (println (usage)))
      (nil? labels) (do
                      (println "Missing --labels PATH")
                      (println)
                      (println (usage)))
      :else
      (let [positive (or positive :eoc)
            rows (read-lines labels)
            rows (filter #(= :judgement (:event %)) rows)
            counts (reduce (fn [acc {:keys [label hexagram]}]
                             (let [pred (classifier->label (get-in hexagram [:class]) positive)
                                   actual label]
                               (cond
                                 (and (= pred positive) (= actual positive)) (update acc :tp inc)
                                 (and (= pred positive) (not= actual positive)) (update acc :fp inc)
                                 (and (not= pred positive) (= actual positive)) (update acc :fn inc)
                                 :else (update acc :tn inc))))
                           {:tp 0 :fp 0 :tn 0 :fn 0}
                           rows)
            report (metrics counts)]
        (println "Counts:" counts)
        (println (format "Accuracy %.3f | Precision %.3f | Recall %.3f | F1 %.3f"
                         (double (:accuracy report))
                         (double (:precision report))
                         (double (:recall report))
                         (double (:f1 report))))))))
