(ns futon5.mmca.hex-batch
  "Summarize hexagram class distributions over long exoevolve batches."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(defn- usage []
  (str/join
   "\n"
   ["Hexagram batch summary"
    ""
    "Usage:"
    "  bb -cp src:resources -m futon5.mmca.hex-batch --log PATH [options]"
    ""
    "Options:"
    "  --log PATH           Exoevolve log path (EDN lines)."
    "  --update-every N     Window size used in exoevolve (default 100)."
    "  --out PATH           Output path (default /tmp/hex-batch.csv)."
    "  --format FMT         csv or edn (default csv)."
    "  --help               Show this message."]))

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

          (= "--update-every" flag)
          (recur (rest more) (assoc opts :update-every (parse-int (first more))))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          (= "--format" flag)
          (recur (rest more) (assoc opts :format (some-> (first more) str/lower-case)))

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

(defn- window-index [run-id update-every]
  (quot (dec (long run-id)) (long update-every)))

(defn- summarize [entries update-every]
  (let [runs (filter #(= :run (:event %)) entries)]
    (reduce (fn [acc entry]
              (let [run-id (:run/id entry)
                    window (window-index run-id update-every)
                    cls (get-in entry [:hexagram :class] :unknown)]
                (update-in acc [window cls] (fnil inc 0))))
            {}
            runs)))

(defn- expand-summary [summary]
  (->> summary
       (mapcat (fn [[window class-map]]
                 (let [total (reduce + 0 (vals class-map))]
                   (map (fn [[cls cnt]]
                          {:window window
                           :class cls
                           :count cnt
                           :total total
                           :pct (if (pos? total) (/ (double cnt) total) 0.0)})
                        class-map))))
       (sort-by (juxt :window :class))))

(defn- csv-line [{:keys [window class count total pct]}]
  (format "%d,%s,%d,%d,%.6f"
          (long window) (name class) (long count) (long total) (double pct)))

(defn- write-csv! [path rows]
  (let [lines (cons "window,class,count,total,pct" (map csv-line rows))]
    (spit path (str/join "\n" lines))))

(defn -main [& args]
  (let [{:keys [help unknown log update-every out format]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do
                (println "Unknown option:" unknown)
                (println)
                (println (usage)))
      (nil? log) (do
                   (println "Missing --log PATH")
                   (println)
                   (println (usage)))
      :else
      (let [update-every (long (or update-every 100))
            out (or out "/tmp/hex-batch.csv")
            format (or format "csv")
            entries (read-lines log)
            summary (summarize entries update-every)
            rows (expand-summary summary)]
        (case format
          "edn" (spit out (pr-str rows))
          (write-csv! out rows))
        (println "Wrote" out)))))
