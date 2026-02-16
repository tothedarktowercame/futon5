(ns phenotype-ca-periodicity-smoke
  (:require [clojure.string :as str]
            [futon5.mmca.band-analysis :as band]))

(def ^:private neighborhoods ["111" "110" "101" "100" "011" "010" "001" "000"])

(defn- rule-map [rule]
  (into {}
        (map-indexed
         (fn [idx nb]
           [nb (if (bit-test rule (- 7 idx)) 1 0)])
         neighborhoods)))

(defn- step-row
  [row rule]
  (let [len (count row)]
    (apply str
           (for [i (range len)]
             (let [l (nth row (mod (dec i) len))
                   c (nth row i)
                   r (nth row (mod (inc i) len))
                   nb (str l c r)]
               (if (= 1 (get rule nb)) \1 \0))))))

(defn- random-row [len seed]
  (let [rng (java.util.Random. (long seed))]
    (apply str (repeatedly len #(if (.nextBoolean rng) \1 \0)))))

(defn- pad-or-trim [s len]
  (let [s (or s "")]
    (cond
      (= (count s) len) s
      (> (count s) len) (subs s 0 len)
      :else (str s (apply str (repeat (- len (count s)) \0))))))

(defn- init-row [len init seed]
  (cond
    (and init (re-matches #"[01]+" init)) (pad-or-trim init len)
    (= init "random") (random-row len seed)
    :else (let [mid (quot len 2)]
            (apply str (concat (repeat mid \0) [\1] (repeat (max 0 (dec (- len mid))) \0))))))

(defn- usage []
  (str/join
   "\n"
   ["Generate a phenotype-only CA and report band periodicity."
    ""
    "Usage:"
    "  bb -cp src:resources:scripts scripts/phenotype_ca_periodicity_smoke.clj [options]"
    ""
    "Options:"
    "  --rule N         Elementary CA rule number (default 15)."
    "  --length N       Row length (default 16)."
    "  --generations N  Number of generations (default 40)."
    "  --seed N         RNG seed (default 4242)."
    "  --init STR       Initial row: 'random', 'single' (default), or 01 string."
    "  --show N         Print first N rows (default 10)."
    "  --help           Show this message."]))

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

          (= "--rule" flag)
          (recur (rest more) (assoc opts :rule (parse-int (first more))))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--generations" flag)
          (recur (rest more) (assoc opts :generations (parse-int (first more))))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

          (= "--init" flag)
          (recur (rest more) (assoc opts :init (first more)))

          (= "--show" flag)
          (recur (rest more) (assoc opts :show (parse-int (first more))))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn -main [& args]
  (let [{:keys [help unknown] :as opts} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      :else
      (let [rule (long (or (:rule opts) 15))
            length (long (or (:length opts) 16))
            generations (long (or (:generations opts) 40))
            seed (long (or (:seed opts) 4242))
            init (or (:init opts) "single")
            show (long (or (:show opts) 10))
            rule (rule-map rule)
            row0 (init-row length init seed)
            history (vec (take generations (iterate #(step-row % rule) row0)))
            analysis (band/analyze-history history)]
        (println (format "Rule %d | length=%d | generations=%d | init=%s" (:rule opts 15) length generations init))
        (doseq [row (take show history)]
          (println row))
        (println)
        (println (format "Band score: %.3f | moderate %.1f%% | chaotic %.1f%% | frozen %.1f%%"
                         (double (or (:band-score analysis) 0.0))
                         (* 100.0 (double (or (:moderate-ratio analysis) 0.0)))
                         (* 100.0 (double (or (:chaotic-ratio analysis) 0.0)))
                         (* 100.0 (double (or (:frozen-ratio analysis) 0.0)))))
        (println (format "Row periodicity: %s"
                         (if (:row-periodic? analysis)
                           (format "YES (period=%d, strength=%.2f)"
                                   (long (or (:row-period analysis) 0))
                                   (double (or (:row-period-strength analysis) 0.0)))
                           "no")))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
