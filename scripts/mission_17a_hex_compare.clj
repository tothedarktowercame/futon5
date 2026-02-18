(ns mission-17a-hex-compare
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.hexagram.metrics :as hex]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.runtime :as mmca]
            [futon5.scripts.output :as out]))

(defn- usage []
  (str/join
   "\n"
   ["Compare hexagram lift: whole-run vs dominant window class (Mission 17a seeds)."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/mission_17a_hex_compare.clj [options]"
    ""
    "Options:"
    "  --window N     Window size (default 10)."
    "  --seed N       RNG seed for selection (default 4242)."
    "  --out PATH     Output org table (default /tmp/mission-17a-hex-compare-<ts>.org)."
    "  --help         Show this message."]))

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

          (= "--window" flag)
          (recur (rest more) (assoc opts :window (parse-int (first more))))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(defn- rng-phenotype-string [^java.util.Random rng length]
  (apply str (repeatedly length #(rng-int rng 2))))

(defn- read-seeds []
  (let [lines (->> (slurp "/tmp/mission-17a-compare-1768955825167.org")
                   str/split-lines
                   (remove str/blank?))
        rows (drop 2 lines)]
    (mapv (fn [line]
            (Long/parseLong (str/trim (second (str/split line #"\|")))))
          rows)))

(defn- hex-class [gen-history]
  (let [transition (hex/run->transition-matrix {:gen-history gen-history})
        signature (hex/transition-matrix->signature transition)
        clazz (hex/signature->hexagram-class signature)]
    {:class clazz
     :fitness (* 100.0 (hex/hexagram-fitness clazz))}))

(defn- windows [xs n]
  (when (pos? n)
    (loop [idx 0
           out []]
      (if (>= idx (count xs))
        out
        (let [end (min (count xs) (+ idx n))]
          (recur end (conj out (subvec xs idx end))))))))

(defn- dominant-class [gen-history window]
  (let [wins (windows (vec gen-history) window)
        classes (map (fn [w] (:class (hex-class w))) wins)
        freqs (frequencies classes)]
    (when (seq freqs)
      (let [[clazz cnt] (apply max-key val freqs)]
        {:class clazz
         :fitness (* 100.0 (hex/hexagram-fitness clazz))
         :counts freqs
         :total (count classes)}))))

(defn- run-pair [seed]
  (let [baseline-cfg (:config (edn/read-string (slurp "futon5/resources/mission-17a-refine-baseline.edn")))
        exotic-cfg (:config (edn/read-string (slurp "futon5/resources/mission-17a-refine-exotic.edn")))
        gen-len (count (:genotype baseline-cfg))
        phe-len (count (:phenotype baseline-cfg))
        grng (java.util.Random. (long seed))
        genotype (rng-sigil-string grng gen-len)
        phenotype (rng-phenotype-string grng phe-len)
        baseline (mmca/run-mmca (assoc baseline-cfg
                                       :genotype genotype
                                       :phenotype phenotype
                                       :seed seed))
        exotic (mmca/run-mmca (assoc exotic-cfg
                                     :genotype genotype
                                     :phenotype phenotype
                                     :seed seed
                                     :exotype (exotype/resolve-exotype (:exotype exotic-cfg))))]
    {:baseline baseline
     :exotic exotic}))

(defn -main [& args]
  (let [{:keys [help unknown window seed out]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      :else
      (let [window (int (or window 10))
            seed (long (or seed 4242))
            seeds (read-seeds)
            rows (mapv (fn [s]
                         (let [{:keys [baseline exotic]} (run-pair s)
                               base-whole (hex-class (:gen-history baseline))
                               exo-whole (hex-class (:gen-history exotic))
                               base-dom (dominant-class (:gen-history baseline) window)
                               exo-dom (dominant-class (:gen-history exotic) window)]
                           {:seed s
                            :base-whole base-whole
                            :exo-whole exo-whole
                            :base-dom base-dom
                            :exo-dom exo-dom}))
                       seeds)
            out (or out (format "/tmp/mission-17a-hex-compare-%d.org" (System/currentTimeMillis)))
            header "| seed | base whole | base fitness | base dom | base dom fitness | exo whole | exo fitness | exo dom | exo dom fitness |"
            sep "|-"
            lines (map (fn [{:keys [seed base-whole exo-whole base-dom exo-dom]}]
                         (format "| %d | %s | %.1f | %s | %.1f | %s | %.1f | %s | %.1f |"
                                 seed
                                 (name (:class base-whole))
                                 (double (:fitness base-whole))
                                 (name (:class base-dom))
                                 (double (:fitness base-dom))
                                 (name (:class exo-whole))
                                 (double (:fitness exo-whole))
                                 (name (:class exo-dom))
                                 (double (:fitness exo-dom))))
                       rows)
            table (str/join "\n" (concat [header sep] lines))]
        (out/spit-text! out table)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
