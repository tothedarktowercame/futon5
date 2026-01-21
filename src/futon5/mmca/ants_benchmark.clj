(ns futon5.mmca.ants-benchmark
  "Post-run ants benchmark for exoevolve logs."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]))

(defn- usage []
  (str/join
   "\n"
   ["Ants benchmark (post-run)"
    ""
    "Usage:"
    "  bb -cp src:resources -m futon5.mmca.ants-benchmark --log PATH --baseline-sigils SIGILS [options]"
    ""
    "Options:"
    "  --log PATH             Exoevolve log path (EDN lines)."
    "  --update-every N       Window size used in exoevolve (default 100)."
    "  --top-k N              Top-K sigils per window (default 6)."
    "  --runs N               Ant runs per benchmark (default 20)."
    "  --ticks N              Ticks per ant run (default 200)."
    "  --baseline-sigils CSV  Comma-separated baseline sigils."
    "  --out-dir PATH         Output directory (default /tmp/mission-0/ants-bench)."
    "  --include-aif          Include AIF as third population."
    "  --no-termination       Disable early termination."
    "  --window-start N       First window index to process."
    "  --window-end N         Last window index to process."
    "  --help                 Show this message."]))

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

          (= "--top-k" flag)
          (recur (rest more) (assoc opts :top-k (parse-int (first more))))

          (= "--runs" flag)
          (recur (rest more) (assoc opts :runs (parse-int (first more))))

          (= "--ticks" flag)
          (recur (rest more) (assoc opts :ticks (parse-int (first more))))

          (= "--baseline-sigils" flag)
          (recur (rest more) (assoc opts :baseline-sigils (first more)))

          (= "--out-dir" flag)
          (recur (rest more) (assoc opts :out-dir (first more)))

          (= "--include-aif" flag)
          (recur more (assoc opts :include-aif true))

          (= "--no-termination" flag)
          (recur more (assoc opts :no-termination true))

          (= "--window-start" flag)
          (recur (rest more) (assoc opts :window-start (parse-int (first more))))

          (= "--window-end" flag)
          (recur (rest more) (assoc opts :window-end (parse-int (first more))))

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

(defn- pick-score [entry]
  (or (get-in entry [:score :final])
      (get-in entry [:score :short])
      0.0))

(defn- top-sigils [entries k]
  (->> entries
       (sort-by pick-score >)
       (map (comp :sigil :exotype))
       (remove nil?)
       distinct
       (take k)))

(defn- parse-sigils [csv]
  (->> (str/split (or csv "") #",")
       (map str/trim)
       (remove str/blank?)
       vec))

(defn- run-cmd
  [{:keys [cmd dir]}]
  (let [{:keys [exit out err]} (apply shell/sh (concat cmd (when dir [:dir dir])))]
    (when-not (zero? exit)
      (throw (ex-info "Command failed" {:cmd cmd :dir dir :out out :err err})))
    {:out out :err err}))

(defn- write-cyberants!
  [sigils out-path]
  (run-cmd {:cmd (concat ["clj" "-M" "-m" "futon5.adapters.cyberant-cli"]
                         (mapcat (fn [sigil] ["--sigil" sigil]) sigils)
                         ["--out" out-path])
            :dir "/home/joe/code/futon5"}))

(defn- run-ants-compare!
  [{:keys [hex-path sigil-path runs ticks out include-aif? no-termination?]}]
  (run-cmd {:cmd (concat ["clj" "-M" "-m" "ants.compare"
                          "--hex" hex-path
                          "--sigil" sigil-path
                          "--runs" (str runs)
                          "--ticks" (str ticks)
                          "--out" out]
                         (when include-aif? ["--include-aif"])
                         (when no-termination? ["--no-termination"]))
            :dir "/home/joe/code/futon2"}))

(defn -main [& args]
  (let [{:keys [help unknown log update-every top-k runs ticks baseline-sigils
                out-dir include-aif no-termination window-start window-end]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      (nil? log) (do (println "Missing --log PATH") (println) (println (usage)))
      (str/blank? baseline-sigils) (do (println "Missing --baseline-sigils CSV") (println) (println (usage)))
      :else
      (let [update-every (long (or update-every 100))
            top-k (long (or top-k 6))
            runs (long (or runs 20))
            ticks (long (or ticks 200))
            out-dir (or out-dir "/tmp/mission-0/ants-bench")
            baseline (parse-sigils baseline-sigils)
            entries (read-lines log)
            runs-entries (filter #(= :run (:event %)) entries)
            by-window (group-by #(window-index (:run/id %) update-every) runs-entries)
            windows (->> (keys by-window) sort vec)
            windows (cond-> windows
                      window-start (filter #(>= % window-start))
                      window-end (filter #(<= % window-end)))]
        (.mkdirs (io/file out-dir))
        (let [baseline-path (str out-dir "/baseline-sigils.edn")]
          (write-cyberants! baseline baseline-path)
          (doseq [window windows]
            (let [sigils (top-sigils (get by-window window) top-k)
                  hex-path (format "%s/window-%03d-hex.edn" out-dir window)
                  out-path (format "%s/window-%03d-ants.edn" out-dir window)]
              (println (format "Window %d: sigils %s" window (str/join "," sigils)))
              (write-cyberants! sigils hex-path)
              (run-ants-compare! {:hex-path hex-path
                                  :sigil-path baseline-path
                                  :runs runs
                                  :ticks ticks
                                  :out out-path
                                  :include-aif? include-aif
                                  :no-termination? no-termination}))))
        (println "Done.")))))
