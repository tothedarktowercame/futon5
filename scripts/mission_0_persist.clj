(ns mission-0-persist
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]))

(defn- usage []
  (str/join
   "\n"
   ["Persist top exotypes into futon3/library/iiching."
    ""
    "Usage:"
    "  bb -cp src:resources scripts/mission_0_persist.clj --log PATH [options]"
    ""
    "Options:"
    "  --log PATH            Exoevolve log path (EDN lines)."
    "  --update-every N      Window size used in exoevolve (default 100)."
    "  --top-k N             Top-K per window (default 6)."
    "  --out-dir PATH        iiching directory (default /home/joe/code/futon3/library/iiching)."
    "  --window-start N      First window index to persist."
    "  --window-end N        Last window index to persist."
    "  --help                Show this message."]))

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

          (= "--out-dir" flag)
          (recur (rest more) (assoc opts :out-dir (first more)))

          (= "--window-start" flag)
          (recur (rest more) (assoc opts :window-start (parse-int (first more))))

          (= "--window-end" flag)
          (recur (rest more) (assoc opts :window-end (parse-int (first more))))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- now [] (.toString (java.time.Instant/now)))

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

(defn- top-entries [entries k]
  (->> entries
       (sort-by pick-score >)
       (take k)))

(defn- exotype-number [sigil]
  (Integer/parseInt (ca/bits-for sigil) 2))

(defn- hex-str [n]
  (format "0x%02X" (int n)))

(defn- hamming-weight [bits]
  (count (filter #(= \1 %) bits)))

(defn- template-path [out-dir]
  (str (io/file out-dir "TEMPLATE.flexiarg")))

(defn- exotype-path [out-dir n]
  (str (io/file out-dir (format "exotype-%03d.flexiarg" (int n)))))

(defn- ensure-exotype-file!
  [out-dir sigil]
  (let [n (exotype-number sigil)
        path (exotype-path out-dir n)
        file (io/file path)]
    (when-not (.exists file)
      (let [tmpl (slurp (template-path out-dir))
            bits (ca/bits-for sigil)
            title (format "Exotype %d (%s)" n (hex-str n))
            exo (exotype/resolve-exotype {:sigil sigil :tier :super})
            params (:params exo)
            content (-> tmpl
                        (str/replace "@flexiarg iiching/exotype-XYZ"
                                     (format "@flexiarg iiching/exotype-%03d" n))
                        (str/replace "@title Exotype XYZ (0xYY)"
                                     (format "@title %s" title))
                        (str/replace "@bits 00000000" (format "@bits %s" bits))
                        (str/replace "@number 0" (format "@number %d" n))
                        (str/replace "@hex 0x00" (format "@hex %s" (hex-str n)))
                        (str/replace "@hamming-weight 0"
                                     (format "@hamming-weight %d" (hamming-weight bits)))
                        (str/replace ":rotation nil" (format ":rotation %s" (:rotation params)))
                        (str/replace ":match-threshold nil" (format ":match-threshold %s" (:match-threshold params)))
                        (str/replace ":invert-on-phenotype? nil"
                                     (format ":invert-on-phenotype? %s" (:invert-on-phenotype? params)))
                        (str/replace ":update-prob nil" (format ":update-prob %s" (:update-prob params)))
                        (str/replace ":mix-mode nil" (format ":mix-mode %s" (:mix-mode params)))
                        (str/replace ":mix-shift nil" (format ":mix-shift %s" (:mix-shift params)))
                        (str/replace ":notes \"blocked-by[futon5-exotypes]\""
                                     ":notes \"From futon5/mmca/exotype params.\""))]
        (spit path content)))
    path))

(defn- update-evidence
  [content entry-str]
  (let [list-pattern (re-pattern "(?s):evidence \\[(.*?)\\]")
        nil-pattern (re-pattern ":evidence nil")
        m (re-find list-pattern content)]
    (cond
      m
      (let [body (str/trim (second m))
            new-body (if (str/blank? body)
                       (str "\n  " entry-str "\n")
                       (str "\n  " body "\n  " entry-str "\n"))]
        (str/replace content list-pattern (str ":evidence [" new-body "]")))

      (re-find nil-pattern content)
      (str/replace content nil-pattern (str ":evidence [\n  " entry-str "\n]"))

      :else
      content)))

(defn- persist-entry!
  [out-dir log-path update-every window entry]
  (let [sigil (get-in entry [:exotype :sigil])
        path (ensure-exotype-file! out-dir sigil)
        content (slurp path)
        evidence (pr-str {:source :mission-0
                          :timestamp (now)
                          :window window
                          :update-every update-every
                          :run-id (:run/id entry)
                          :seed (:seed entry)
                          :score (select-keys (:score entry) [:short :final])
                          :hex-class (get-in entry [:hexagram :class])
                          :log log-path})
        updated (update-evidence content evidence)]
    (when-not (= content updated)
      (spit path updated))))

(defn -main [& args]
  (let [{:keys [help unknown log update-every top-k out-dir window-start window-end]}
        (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      (nil? log) (do (println "Missing --log PATH") (println) (println (usage)))
      :else
      (let [update-every (long (or update-every 100))
            top-k (long (or top-k 6))
            out-dir (or out-dir "/home/joe/code/futon3/library/iiching")
            entries (read-lines log)
            runs (filter #(= :run (:event %)) entries)
            by-window (group-by #(window-index (:run/id %) update-every) runs)
            windows (->> (keys by-window) sort vec)
            windows (cond-> windows
                      window-start (filter #(>= % window-start))
                      window-end (filter #(<= % window-end)))]
        (doseq [window windows]
          (let [entries (top-entries (get by-window window) top-k)]
            (doseq [entry entries]
              (persist-entry! out-dir log update-every window entry))))
        (println "Persisted to" out-dir)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
