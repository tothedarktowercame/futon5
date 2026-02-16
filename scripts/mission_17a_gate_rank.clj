(ns mission-17a-gate-rank
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.scoring :as scoring]
            [futon5.scripts.output :as out]))

(defn- usage []
  (str/join
   "\n"
   ["Apply gate+rank to Mission 17a compare table."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/mission_17a_gate_rank.clj --table PATH --weights PATH [options]"
    ""
    "Options:"
    "  --table PATH     Compare table org file."
    "  --weights PATH   Weights EDN from learn_pref_from_compare."
    "  --out PATH       Output org table (default /tmp/mission-17a-gate-rank-<ts>.org)."
    "  --eps P          Tie margin for rank score (default 0.1)."
    "  --help           Show this message."]))

(defn- parse-double [s]
  (try (Double/parseDouble s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--table" flag)
          (recur (rest more) (assoc opts :table (first more)))

          (= "--weights" flag)
          (recur (rest more) (assoc opts :weights (first more)))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          (= "--eps" flag)
          (recur (rest more) (assoc opts :eps (parse-double (first more))))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- parse-table [path]
  (let [lines (->> (slurp path) str/split-lines (map str/trim) (remove str/blank?))
        rows (drop 2 lines)]
    (mapv (fn [line]
            (let [parts (map str/trim (str/split (subs line 1 (dec (count line))) #"\|"))
                  seed (Long/parseLong (nth parts 0))
                  vals (mapv #(Double/parseDouble %) (subvec (vec parts) 1))]
              {:seed seed
               :short-b (nth vals 0) :short-e (nth vals 1)
               :env-b (nth vals 2) :env-e (nth vals 3)
               :triad-b (nth vals 4) :triad-e (nth vals 5)
               :shift-b (nth vals 6) :shift-e (nth vals 7)
               :fil-b (nth vals 8) :fil-e (nth vals 9)}))
          rows)))

(defn- deltas [row]
  {:delta-short (- (:short-e row) (:short-b row))
   :delta-envelope (- (:env-e row) (:env-b row))
   :delta-triad (- (:triad-e row) (:triad-b row))
   :delta-shift (- (:shift-e row) (:shift-b row))
   :delta-filament (- (:fil-e row) (:fil-b row))
   :envelope (:env-e row)})

(defn -main [& args]
  (let [{:keys [help unknown table weights out eps]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      (or (nil? table) (nil? weights)) (do (println "Missing --table or --weights") (println) (println (usage)))
      :else
      (let [rows (parse-table table)
            weights-data (edn/read-string (slurp weights))
            weight-vec (:w (:model weights-data))
            weight-map {:delta-short (nth weight-vec 0)
                        :delta-envelope (nth weight-vec 1)
                        :delta-triad (nth weight-vec 2)
                        :delta-shift (nth weight-vec 3)
                        :delta-filament (nth weight-vec 4)}
            eps (double (or eps 0.1))
            out (or out (format "/tmp/mission-17a-gate-rank-%d.org" (System/currentTimeMillis)))
            lines (map (fn [row]
                         (let [raw (deltas row)
                               result (scoring/gate-rank-score {:summary nil
                                                                :raw raw
                                                                :weights weight-map})
                               score (get-in result [:rank :score])
                               pred (cond
                                      (< (Math/abs score) eps) "tie"
                                      (pos? score) "exotic"
                                      :else "baseline")
                               gate (if (get-in result [:gate :passed?]) "pass" "fail")]
                           (format "| %d | %.2f | %s | %s |"
                                   (:seed row) (double score) pred gate)))
                       rows)
            header "| seed | rank score | pred | gate |"
            sep "|-"
            table (str/join "\n" (concat [header sep] lines))]
        (out/spit-text! out table))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
