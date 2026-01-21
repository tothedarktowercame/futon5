(ns cyber-mmca-macro-trace-report
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.runtime :as runtime]))

(defn- usage []
  (str/join
   "\n"
   ["Macro-trace sanity report for MMCA runs."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_macro_trace_report.clj [options]"
    ""
    "Options:"
    "  --seeds LIST        Comma-separated seeds (default 4242,1111,2222,3333)."
    "  --windows N         Number of windows (default 12)."
    "  --W N               Window length in generations (default 10)."
    "  --S N               Window stride (default 10)."
    "  --length N          Genotype length (default 32)."
    "  --kernel KW         Kernel keyword (default :mutating-template)."
    "  --out PATH          Output CSV (default /tmp/cyber-mmca-macro-trace.csv)."
    "  --help              Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-seeds [s]
  (->> (str/split (or s "") #",")
       (map str/trim)
       (remove str/blank?)
       (map parse-int)
       (remove nil?)
       vec))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--seeds" flag)
          (recur (rest more) (assoc opts :seeds (parse-seeds (first more))))

          (= "--windows" flag)
          (recur (rest more) (assoc opts :windows (parse-int (first more))))

          (= "--W" flag)
          (recur (rest more) (assoc opts :W (parse-int (first more))))

          (= "--S" flag)
          (recur (rest more) (assoc opts :S (parse-int (first more))))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--kernel" flag)
          (recur (rest more) (assoc opts :kernel (keyword (first more))))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- ensure-resources! []
  (when-not (io/resource "futon5/sigils.edn")
    (when-let [add-cp (resolve 'babashka.classpath/add-classpath)]
      (add-cp "futon5/resources"))
    (when-not (io/resource "futon5/sigils.edn")
      (throw (ex-info "Missing futon5/resources on classpath."
                      {:hint "Use: bb -cp futon5/src:futon5/resources ..."})))) )

(defn- rows->csv [rows header out-path]
  (spit out-path
        (str (str/join "," header)
             "\n"
             (str/join "\n" (map #(str/join "," %) rows)))))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(def macro-tags
  [:pressure-up :pressure-down :selectivity-up :selectivity-down :structure-preserve :structure-disrupt])

(defn- window-counts [trace start end]
  (let [window (subvec trace start end)
        flattened (mapcat identity window)
        counts (frequencies flattened)
        empty-count (count (filter empty? window))]
    (merge {:empty empty-count}
           (into {} (map (fn [k] [k (get counts k 0)]) macro-tags)))))

(defn -main [& args]
  (let [{:keys [help unknown seeds windows W S length kernel out]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      :else
      (let [_ (ensure-resources!)
            seeds (seq (or seeds [4242 1111 2222 3333]))
            windows (max 1 (int (or windows 12)))
            W (max 1 (int (or W 10)))
            S (max 1 (int (or S W)))
            length (max 1 (int (or length 32)))
            kernel (or kernel :mutating-template)
            rows (mapcat (fn [seed]
                           (let [rng (java.util.Random. (long seed))
                                 genotype (rng-sigil-string rng length)
                                 result (runtime/run-mmca {:genotype genotype
                                                           :generations (* windows W)
                                                           :kernel kernel
                                                           :lock-kernel false})
                                 trace (metrics/macro-trace (:metrics-history result))
                                 max-idx (count trace)]
                             (map (fn [idx start]
                                    (let [end (min max-idx (+ start W))
                                          counts (window-counts trace start end)]
                                      [(long seed)
                                       idx
                                       start
                                       (dec end)
                                       (:empty counts)
                                       (:pressure-up counts)
                                       (:pressure-down counts)
                                       (:selectivity-up counts)
                                       (:selectivity-down counts)
                                       (:structure-preserve counts)
                                       (:structure-disrupt counts)]))
                                  (range windows)
                                  (take windows (iterate #(+ % S) 0)))))
                         seeds)]
        (rows->csv rows
                   ["seed" "window" "w_start" "w_end" "empty"
                    "pressure_up" "pressure_down" "selectivity_up" "selectivity_down"
                    "structure_preserve" "structure_disrupt"]
                   (or out "/tmp/cyber-mmca-macro-trace.csv"))
        (println "Wrote" (or out "/tmp/cyber-mmca-macro-trace.csv"))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
