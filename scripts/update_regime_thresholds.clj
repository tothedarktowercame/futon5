(ns update-regime-thresholds
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- usage []
  (str/join
   "\n"
   ["Update regime thresholds from HIT judgements."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/update_regime_thresholds.clj [options]"
    ""
    "Options:"
    "  --judgements PATH  Judgements EDN lines (default /tmp/mmca-judgements.edn)."
    "  --out PATH         Output thresholds EDN (default futon5/resources/regime-thresholds.edn)."
    "  --min N            Minimum count per label to include (default 3)."
    "  --help             Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--judgements" flag)
          (recur (rest more) (assoc opts :judgements (first more)))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          (= "--min" flag)
          (recur (rest more) (assoc opts :min (parse-int (first more))))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- read-edn-lines [path]
  (->> (str/split-lines (slurp path))
       (remove str/blank?)
       (mapv edn/read-string)))

(defn- quantile [xs q]
  (let [xs (sort xs)
        n (count xs)
        idx (int (Math/floor (* (max 0.0 (min 1.0 q)) (dec n))))]
    (nth xs idx)))

(defn- extract [entries key]
  (->> entries
       (map #(get-in % [:summary key]))
       (filter number?)
       vec))

(defn- thresholds-from
  [entries]
  (let [change (extract entries :avg-change)
        entropy (extract entries :avg-entropy-n)
        autocorr (extract entries :temporal-autocorr)]
    (when (and (seq change) (seq entropy) (seq autocorr))
      {:activity-low (quantile change 0.1)
       :activity-high (quantile change 0.9)
       :struct-low (quantile autocorr 0.1)
       :static-struct (quantile autocorr 0.85)
       :static-activity (quantile change 0.25)
       :chaos-change (quantile change 0.85)
       :chaos-autocorr (quantile autocorr 0.25)
       :magma-entropy (quantile entropy 0.9)
       :magma-change (quantile change 0.8)})))

(defn -main [& args]
  (let [{:keys [help unknown judgements out min]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      :else
      (let [judgements (read-edn-lines (or judgements "/tmp/mmca-judgements.edn"))
            min (long (or min 3))
            by-label (group-by :label judgements)
            eoc (get by-label :eoc)
            not-eoc (get by-label :not-eoc)
            base (or eoc judgements)
            counts {:eoc (count eoc) :not-eoc (count not-eoc) :all (count judgements)}
            use (if (and eoc (>= (count eoc) min)) eoc base)
            thresholds (thresholds-from use)
            out (or out "futon5/resources/regime-thresholds.edn")]
        (when-not thresholds
          (throw (ex-info "Insufficient data to compute thresholds."
                          {:counts counts})))
        (spit out (pr-str (assoc thresholds :source :hit
                                            :counts counts
                                            :min-count min)))
        (println "Wrote" out)
        (println "Counts" counts)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
