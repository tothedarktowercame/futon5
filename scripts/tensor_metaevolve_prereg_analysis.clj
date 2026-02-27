(ns scripts.tensor-metaevolve-prereg-analysis
  "Evaluate prereg decision rules for tensor metaevolve lock-in study."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- usage []
  (str "usage: bb -m scripts.tensor-metaevolve-prereg-analysis \\\n"
       "  --control PATH --treatment PATH [--out PATH]\n"))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (case flag
          "--control" (recur (rest more) (assoc opts :control (first more)))
          "--treatment" (recur (rest more) (assoc opts :treatment (first more)))
          "--out" (recur (rest more) (assoc opts :out (first more)))
          "--help" (recur more (assoc opts :help true))
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- max-streak
  [xs]
  (loop [xs (seq xs)
         prev nil
         streak 0
         best 0]
    (if-let [x (first xs)]
      (if (= x prev)
        (let [s (inc streak)]
          (recur (next xs) x s (max best s)))
        (recur (next xs) x 1 (max best 1)))
      best)))

(defn- parity-rate
  [runs-detail]
  (let [n (count runs-detail)
        ok (count (filter #(true? (get-in % [:parity :history])) runs-detail))]
    (if (pos? n)
      (/ (double ok) (double n))
      0.0)))

(defn- arm-metrics
  [report]
  (let [runs-detail (vec (or (:runs-detail report) []))
        sigils (mapv :rule-sigil runs-detail)
        ranked (vec (or (:ranked report) []))
        top-composite (double (or (get-in (first ranked) [:summary :composite-score]) 0.0))]
    {:runs (count runs-detail)
     :rule-diversity (count (set sigils))
     :max-lockin-streak (max-streak sigils)
     :history-parity-rate (parity-rate runs-detail)
     :top-composite top-composite
     :rule-sequence-preview (vec (take 16 sigils))}))

(defn- decision-rules
  [control treatment]
  (let [d1 (>= (:rule-diversity treatment)
               (+ (:rule-diversity control) 2))
        d2 (<= (:max-lockin-streak treatment)
               (* 0.7 (double (max 1 (:max-lockin-streak control)))))
        d3 (and (>= (:history-parity-rate control) 0.99)
                (>= (:history-parity-rate treatment) 0.99))
        d4 (>= (:top-composite treatment)
               (* 0.8 (:top-composite control)))]
    {:d1-rule-diversity-improves d1
     :d2-lockin-streak-reduced d2
     :d3-parity-constraint d3
     :d4-non-inferiority-top-score d4
     :overall (and d1 d2 d3 d4)}))

(defn -main [& args]
  (let [{:keys [help unknown control treatment out]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do
                (binding [*out* *err*]
                  (println "Unknown option:" unknown))
                (println (usage))
                (System/exit 2))
      (or (str/blank? (str control))
          (str/blank? (str treatment)))
      (do
        (binding [*out* *err*]
          (println "Missing required --control/--treatment"))
        (println (usage))
        (System/exit 2))
      :else
      (let [control-report (edn/read-string (slurp control))
            treatment-report (edn/read-string (slurp treatment))
            control-m (arm-metrics control-report)
            treatment-m (arm-metrics treatment-report)
            decisions (decision-rules control-m treatment-m)
            result {:study/id "tensor-metaevolve-lockin-2026-02-21"
                    :inputs {:control control :treatment treatment}
                    :control control-m
                    :treatment treatment-m
                    :decisions decisions}]
        (if (str/blank? (str out))
          (prn result)
          (let [f (io/file out)]
            (when-let [parent (.getParentFile f)]
              (.mkdirs parent))
            (spit f (pr-str result))
            (println "Wrote" out)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
