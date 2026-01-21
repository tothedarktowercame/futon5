(ns mission-0-envelope-summary
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(defn- usage []
  (str/join
   "\n"
   ["Summarize envelope scores from exoevolve logs."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/mission_0_envelope_summary.clj --log PATH"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/mission_0_envelope_summary.clj --dir /tmp/mission-0"
    ""
    "Options:"
    "  --log PATH   Single log path."
    "  --dir PATH   Directory to scan for mission-0 env logs."
    "  --k N        Top/bottom K entries (default 3)."]))

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

          (= "--log" flag)
          (recur (rest more) (assoc opts :log (first more)))

          (= "--dir" flag)
          (recur (rest more) (assoc opts :dir (first more)))

          (= "--k" flag)
          (recur (rest more) (assoc opts :k (parse-int (first more))))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- read-lines [path]
  (->> (slurp path)
       str/split-lines
       (map str/trim)
       (remove str/blank?)
       (map edn/read-string)
       (filter #(= :run (:event %)))
       vec))

(defn- summarize-log [path k]
  (let [entries (read-lines path)
        scored (map (fn [entry]
                      (let [score (double (or (get-in entry [:score :envelope]) 0.0))]
                        (assoc entry :envelope-score score)))
                    entries)
        sorted (vec (sort-by :envelope-score > scored))
        top (take k sorted)
        bottom (take k (reverse sorted))
        scores (map :envelope-score sorted)
        avg (if (seq scores) (/ (reduce + 0.0 scores) (double (count scores))) 0.0)
        minv (if (seq scores) (apply min scores) 0.0)
        maxv (if (seq scores) (apply max scores) 0.0)]
    {:path path
     :count (count entries)
     :avg avg
     :min minv
     :max maxv
     :top top
     :bottom bottom}))

(defn- print-entry [entry]
  (let [seed (:seed entry)
        score (:envelope-score entry)
        entropy (double (or (get-in entry [:summary :avg-entropy-n]) 0.0))
        change (double (or (get-in entry [:summary :avg-change]) 0.0))]
    (println (format "seed %d | envelope %.2f | entropy %.3f | change %.3f"
                     (long (or seed 0)) score entropy change))))

(defn -main [& args]
  (let [{:keys [help unknown log dir k]} (parse-args args)
        k (long (or k 3))]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      (and (nil? log) (nil? dir)) (do (println "Need --log or --dir") (println) (println (usage)))
      :else
      (let [paths (if log
                    [log]
                    (->> (file-seq (clojure.java.io/file dir))
                         (filter #(.isFile %))
                         (map #(.getPath %))
                         (filter #(re-find #"mission-0-env-.*edn$" %))
                         sort))]
        (doseq [path paths]
          (let [{:keys [count avg min max top bottom]} (summarize-log path k)]
            (println)
            (println (format "Log %s | n %d | avg %.2f | min %.2f | max %.2f"
                             path count avg min max))
            (println "Top:")
            (doseq [entry top] (print-entry entry))
            (println "Bottom:")
            (doseq [entry bottom] (print-entry entry))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
