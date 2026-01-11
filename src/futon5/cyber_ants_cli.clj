(ns futon5.cyber-ants-cli
  "CLI to generate cyber-ant configs from meta-evolve reports."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.cyber-ants :as cyber]))

(def ^:private default-count 5)

(defn- usage []
  (str/join
   "\n"
   ["Cyber-ant generator"
    ""
    "Usage:"
    "  bb -cp src:resources -m futon5.cyber-ants-cli [options]"
    ""
    "Options:"
    "  --report PATH   Meta-evolve EDN report (required)."
    "  --out PATH      Write generated EDN bundle to file."
    "  --count N       Number of top runs to convert (default 5)."
    "  --base ID       Base pattern id (default :cyber/baseline)."
    "  --flexiarg-dir PATH  Write flexiarg files into this directory."
    "  --help          Show this message."]))

(defn- parse-int [s]
  (try
    (Long/parseLong s)
    (catch Exception _ nil)))

(defn- parse-keyword [s]
  (when s
    (keyword (str/replace s #"^:" ""))))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--report" flag)
          (recur (rest more) (assoc opts :report (first more)))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          (= "--count" flag)
          (recur (rest more) (assoc opts :count (parse-int (first more))))

          (= "--base" flag)
          (recur (rest more) (assoc opts :base (parse-keyword (first more))))

          (= "--flexiarg-dir" flag)
          (recur (rest more) (assoc opts :flexiarg-dir (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- load-report [path]
  (edn/read-string (slurp path)))

(defn- generate-patterns [report base-id count]
  (let [ranked (:ranked report)
        base-id (or base-id cyber/default-pattern-id)]
    (->> (or ranked [])
         (take (or count default-count))
         (map-indexed
          (fn [idx entry]
            (cyber/propose-cyber-ant
             (get-in entry [:meta-lift :top-sigils])
             {:base-id base-id
              :seed (:seed entry)
              :run-index (inc idx)
              :policy (:policy entry)
              :rule (:rule entry)})))
         vec)))

(defn- build-output [report-path report base-id count]
  {:generated-at (.toString (java.time.Instant/now))
   :source {:report report-path
            :meta (select-keys report [:seed :runs :length :generations])}
   :base base-id
   :patterns (generate-patterns report base-id count)})

(defn -main [& args]
  (let [{:keys [report out count base flexiarg-dir help unknown]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println (str "Unknown flag: " unknown))
                  (println (usage)))
      (not report) (do (println "Missing --report.")
                       (println (usage)))
      :else
      (let [report-data (load-report report)
            output (build-output report report-data base count)
            rendered (pr-str output)]
        (when flexiarg-dir
          (doseq [pattern (:patterns output)]
            (cyber/write-flexiarg! pattern flexiarg-dir)))
        (if out
          (spit out rendered)
          (println rendered))))))
