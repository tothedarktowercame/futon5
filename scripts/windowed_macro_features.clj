(ns windowed-macro-features
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.mmca.metrics :as metrics]))

(defn- usage []
  (str/join
   "\n"
   ["Windowed macro-feature scan for MMCA logs."
    ""
    "Usage:"
    "  bb -cp futon5/src futon5/scripts/windowed_macro_features.clj [options]"
    ""
    "Options:"
    "  --log PATH      Input log file (default resources/mission-17a-refine-r50.log)."
    "  --out PATH      Output CSV (default /tmp/mission-17a-hex-compare-v0.csv)."
    "  --W N           Window size (default 10)."
    "  --S N           Window stride (default 5)."
    "  --limit N       Max runs to process (default: no limit)."
    "  --help          Show this message."]))

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

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          (= "--W" flag)
          (recur (rest more) (assoc opts :W (parse-int (first more))))

          (= "--S" flag)
          (recur (rest more) (assoc opts :S (parse-int (first more))))

          (= "--limit" flag)
          (recur (rest more) (assoc opts :limit (parse-int (first more))))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- summary->metrics-history
  "Build a synthetic metrics-history window from a run summary."
  [{:keys [avg-change avg-unique avg-entropy-n temporal-autocorr]}]
  (let [length 32
        unique-sigils (max 1 (int (Math/round (* (double (or avg-unique 0.0)) length))))
        max-entropy (/ (Math/log length) (Math/log 2.0))
        entropy (* (double (or avg-entropy-n 0.0)) max-entropy)]
    (vec (repeat 10 {:change-rate avg-change
                     :unique-sigils unique-sigils
                     :length length
                     :entropy entropy
                     :temporal-autocorr temporal-autocorr}))))

(defn- run->history
  [{:keys [metrics-history gen-history summary] :as entry}]
  (cond
    (seq metrics-history) metrics-history
    (seq gen-history) {:gen-history gen-history}
    summary (summary->metrics-history summary)
    :else nil))

(defn- rows->csv [rows header out-path]
  (spit out-path
        (str (str/join "," header)
             "\n"
             (str/join "\n" (map #(str/join "," %) rows)))))

(defn -main [& args]
  (let [{:keys [help unknown log out W S limit]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      :else
      (let [log-path (or log "resources/mission-17a-refine-r50.log")
            out-path (or out "/tmp/mission-17a-hex-compare-v0.csv")
            W (max 1 (int (or W 10)))
            S (max 1 (int (or S 5)))
            limit (when limit (max 1 (int limit)))
            entries (with-open [r (io/reader log-path)]
                      (->> (line-seq r)
                           (map edn/read-string)
                           (take (or limit Long/MAX_VALUE))
                           vec))
            windows (mapcat (fn [entry]
                              (when-let [history (run->history entry)]
                                (let [run-id (or (:run/id entry) (:run-id entry))
                                      seed (:seed entry)
                                      win (metrics/windowed-macro-features history {:W W :S S})]
                                  (map (fn [w]
                                         (assoc w
                                                :run-id run-id
                                                :seed seed))
                                       win))))
                            entries)
            rows (map (fn [{:keys [run-id seed w-start w-end pressure selectivity structure activity regime]}]
                        [(or run-id "-")
                         (or seed "-")
                         w-start
                         w-end
                         (or (when pressure (format "%.4f" (double pressure))) "")
                         (or (when selectivity (format "%.4f" (double selectivity))) "")
                         (or (when structure (format "%.4f" (double structure))) "")
                         (or (when activity (format "%.4f" (double activity))) "")
                         (or (when regime (name regime)) "")])
                      windows)
            counts (frequencies (keep :regime windows))]
        (rows->csv rows
                   ["run_id" "seed" "w_start" "w_end" "pressure" "selectivity"
                    "structure" "activity" "regime"]
                   out-path)
        (println "Regime counts:" counts)
        (println "Wrote" out-path)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
