(ns cyber-mmca-demo
  (:require [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.runtime :as runtime]))

(defn- usage []
  (str/join
   "\n"
   ["Cyber-MMCA demo loop (rule-based controller)."
    ""
    "Usage:"
    "  bb -cp futon5/src futon5/scripts/cyber_mmca_demo.clj [options]"
    ""
    "Options:"
    "  --windows N          Number of control windows (default 8)."
    "  --W N                Window length in generations (default 10)."
    "  --S N                Window stride for features (default 10)."
    "  --seed N             RNG seed for exotype context sampling."
    "  --genotype STR       Starting sigil string (overrides --length)."
    "  --length N           Random sigil string length (default 32)."
    "  --phenotype STR      Starting phenotype bit string."
    "  --phenotype-length N Random phenotype length."
    "  --kernel KW          Kernel keyword (default :mutating-template)."
    "  --sigil STR          Exotype sigil (default ca/default-sigil)."
    "  --out PATH           Output CSV (default /tmp/cyber-mmca-demo.csv)."
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

          (= "--windows" flag)
          (recur (rest more) (assoc opts :windows (parse-int (first more))))

          (= "--W" flag)
          (recur (rest more) (assoc opts :W (parse-int (first more))))

          (= "--S" flag)
          (recur (rest more) (assoc opts :S (parse-int (first more))))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

          (= "--genotype" flag)
          (recur (rest more) (assoc opts :genotype (first more)))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--phenotype" flag)
          (recur (rest more) (assoc opts :phenotype (first more)))

          (= "--phenotype-length" flag)
          (recur (rest more) (assoc opts :phenotype-length (parse-int (first more))))

          (= "--kernel" flag)
          (recur (rest more) (assoc opts :kernel (keyword (first more))))

          (= "--sigil" flag)
          (recur (rest more) (assoc opts :sigil (first more)))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- clamp [x lo hi]
  (max lo (min hi x)))

(defn- adjust-params [params actions]
  (let [params (or params {})
        pressure-step 0.1
        select-step 0.1
        apply-action (fn [p action]
                       (case action
                         :pressure-up (update p :update-prob
                                              #(clamp (+ (double (or % 1.0)) pressure-step) 0.05 1.0))
                         :pressure-down (update p :update-prob
                                                #(clamp (- (double (or % 1.0)) pressure-step) 0.05 1.0))
                         :selectivity-up (update p :match-threshold
                                                 #(clamp (+ (double (or % 0.5)) select-step) 0.0 1.0))
                         :selectivity-down (update p :match-threshold
                                                   #(clamp (- (double (or % 0.5)) select-step) 0.0 1.0))
                         p))]
    (reduce apply-action params actions)))

(defn- pick-actions [{:keys [regime structure]}]
  (cond
    (= regime :freeze) [:pressure-up]
    (= regime :magma) [:pressure-down :selectivity-up]
    (and (= regime :eoc) (number? structure) (< structure 0.4)) [:selectivity-up]
    :else [:hold]))

(defn- format-num [v]
  (when (number? v)
    (format "%.4f" (double v))))

(defn- rows->csv [rows header out-path]
  (spit out-path
        (str (str/join "," header)
             "\n"
             (str/join "\n" (map #(str/join "," %) rows)))))

(defn -main [& args]
  (let [{:keys [help unknown windows W S seed genotype length phenotype phenotype-length kernel sigil out]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      :else
      (let [windows (max 1 (int (or windows 8)))
            W (max 1 (int (or W 10)))
            S (max 1 (int (or S W)))
            length (max 1 (int (or length 32)))
            kernel (or kernel :mutating-template)
            sigil (or sigil ca/default-sigil)
            out (or out "/tmp/cyber-mmca-demo.csv")
            genotype (or genotype (ca/random-sigil-string length))
            phenotype (or phenotype
                          (when phenotype-length
                            (ca/random-phenotype-string phenotype-length)))
            base-exotype (exotype/resolve-exotype {:sigil sigil :tier :super})]
        (loop [idx 0
               genotype genotype
               phenotype phenotype
               kernel kernel
               exotype base-exotype
               metrics-history []
               gen-history []
               phe-history []
               rows []]
          (if (>= idx windows)
            (do
              (rows->csv rows
                         ["window" "w_start" "w_end" "regime" "pressure"
                          "selectivity" "structure" "activity" "actions"
                          "update_prob" "match_threshold" "kernel"]
                         out)
              (println "Wrote" out))
            (let [result (runtime/run-mmca {:genotype genotype
                                            :phenotype phenotype
                                            :generations W
                                            :kernel kernel
                                            :lock-kernel false
                                            :exotype exotype
                                            :exotype-mode :inline
                                            :seed (when seed (+ seed idx))})
                  metrics-history' (into metrics-history (:metrics-history result))
                  gen-history' (into gen-history (:gen-history result))
                  phe-history' (into phe-history (:phe-history result))
                  windows' (metrics/windowed-macro-features
                            {:metrics-history metrics-history'
                             :gen-history gen-history'
                             :phe-history phe-history'}
                            {:W W :S S})
                  window (last windows')
                  actions (pick-actions window)
                  params-before (get exotype :params)
                  params-after (if (seq (remove #{:hold} actions))
                                 (adjust-params params-before actions)
                                 params-before)
                  exotype' (assoc exotype :params params-after)
                  row {:window idx
                       :w-start (:w-start window)
                       :w-end (:w-end window)
                       :regime (some-> (:regime window) name)
                       :pressure (:pressure window)
                       :selectivity (:selectivity window)
                       :structure (:structure window)
                       :activity (:activity window)
                       :actions (str/join "+" (map name actions))
                       :update-prob (:update-prob params-after)
                       :match-threshold (:match-threshold params-after)
                       :kernel (name (or (:kernel result) kernel))}
                  last-gen (last (:gen-history result))
                  last-phe (last (:phe-history result))
                  kernel' (or (:kernel result) kernel)]
              (println (format "W%02d | %s | P=%s S=%s T=%s | %s | u=%.2f m=%.2f"
                               idx
                               (or (:regime window) :unknown)
                               (format-num (:pressure window))
                               (format-num (:selectivity window))
                               (format-num (:structure window))
                               (str/join "+" (map name actions))
                               (double (or (:update-prob params-after) 0.0))
                               (double (or (:match-threshold params-after) 0.0))))
              (recur (inc idx)
                     (or last-gen genotype)
                     (or last-phe phenotype)
                     kernel'
                     exotype'
                     metrics-history'
                     gen-history'
                     phe-history'
                     (conj rows [(str (:window row))
                                 (str (:w-start row))
                                 (str (:w-end row))
                                 (or (:regime row) "")
                                 (or (format-num (:pressure row)) "")
                                 (or (format-num (:selectivity row)) "")
                                 (or (format-num (:structure row)) "")
                                 (or (format-num (:activity row)) "")
                                 (:actions row)
                                 (format-num (:update-prob row))
                                 (format-num (:match-threshold row))
                                 (:kernel row)])))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
