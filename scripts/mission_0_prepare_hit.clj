(ns mission-0-prepare-hit
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.runtime :as mmca]
            [futon5.scripts.output :as out]))

(defn- usage []
  (str/join
   "\n"
   ["Prepare HIT inputs by replaying runs and saving full EDN histories."
    ""
    "Usage:"
    "  bb -cp src:resources futon5/scripts/mission_0_prepare_hit.clj --log PATH [options]"
    ""
    "Options:"
    "  --log PATH         Exoevolve log path (EDN lines)."
    "  --out-dir PATH     Output directory for run EDN (default /tmp/mmca-hit)."
    "  --inputs PATH      Output inputs list (default /tmp/mmca-hit-inputs.txt)."
    "  --top-k N          Number of top runs to include (default 5)."
    "  --mid-k N          Number of mid runs to include (default 5)."
    "  --rand-k N         Number of random runs to include (default 5)."
    "  --seed N           RNG seed for random selection (default 4242)."
    "  --label PREFIX     Filename prefix (default mission-0-hit)."
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

          (= "--log" flag)
          (recur (rest more) (assoc opts :log (first more)))

          (= "--out-dir" flag)
          (recur (rest more) (assoc opts :out-dir (first more)))

          (= "--inputs" flag)
          (recur (rest more) (assoc opts :inputs (first more)))

          (= "--top-k" flag)
          (recur (rest more) (assoc opts :top-k (parse-int (first more))))

          (= "--mid-k" flag)
          (recur (rest more) (assoc opts :mid-k (parse-int (first more))))

          (= "--rand-k" flag)
          (recur (rest more) (assoc opts :rand-k (parse-int (first more))))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

          (= "--label" flag)
          (recur (rest more) (assoc opts :label (first more)))

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

(defn- pick-score [entry]
  (or (get-in entry [:score :final])
      (get-in entry [:score :short])
      0.0))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(defn- rng-phenotype-string [^java.util.Random rng length]
  (apply str (repeatedly length #(rng-int rng 2))))

(defn- select-entries
  [entries top-k mid-k rand-k seed]
  (let [sorted (vec (sort-by pick-score > entries))
        mid-start (max 0 (quot (- (count sorted) mid-k) 2))
        top (take top-k sorted)
        mid (take mid-k (drop mid-start sorted))
        rng (java.util.Random. (long seed))
        shuffled (loop [xs (vec sorted)
                        i (dec (count sorted))]
                   (if (<= i 0)
                     xs
                     (let [j (.nextInt rng (inc i))
                           xi (nth xs i)
                           xj (nth xs j)
                           xs (assoc xs i xj j xi)]
                       (recur xs (dec i)))))
        rand (take rand-k shuffled)]
    (vec (distinct (concat top mid rand)))))

(defn- run->edn!
  [entry out-dir label idx]
  (let [seed (long (:seed entry))
        length (int (:length entry))
        generations (int (:generations entry))
        rng (java.util.Random. seed)
        genotype (rng-sigil-string rng length)
        phenotype (rng-phenotype-string rng length)
        exo (exotype/resolve-exotype (:exotype entry))
        run (mmca/run-mmca {:genotype genotype
                            :phenotype phenotype
                            :generations generations
                            :kernel :mutating-template
                            :exotype exo
                            :seed seed})
        base (format "%s-%02d-seed-%d" label idx seed)
        path (str (io/file out-dir (str base ".edn")))]
    (out/warn-overwrite-file! path)
    (spit path (pr-str (assoc run
                              :hit/meta {:label base
                                         :seed seed
                                         :length length
                                         :generations generations
                                         :exotype (:exotype entry)
                                         :note "replayed from exoevolve log for HIT labeling"})))
    path))

(defn -main [& args]
  (let [{:keys [help unknown log out-dir inputs top-k mid-k rand-k seed label]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      (nil? log) (do (println "Missing --log PATH") (println) (println (usage)))
      :else
      (let [out-dir (or out-dir "/tmp/mmca-hit")
            inputs (or inputs "/tmp/mmca-hit-inputs.txt")
            top-k (long (or top-k 5))
            mid-k (long (or mid-k 5))
            rand-k (long (or rand-k 5))
            seed (long (or seed 4242))
            label (or label "mission-0-hit")
            entries (->> (read-lines log) (filter #(= :run (:event %))) vec)
            selected (select-entries entries top-k mid-k rand-k seed)]
        (out/warn-overwrite-dir! out-dir)
        (.mkdirs (io/file out-dir))
        (let [paths (mapv (fn [[idx entry]]
                            (run->edn! entry out-dir label (inc idx)))
                          (map-indexed vector selected))]
          (out/spit-text! inputs (str/join "\n" paths))
          (println "Runs saved to" (out/abs-path out-dir)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
