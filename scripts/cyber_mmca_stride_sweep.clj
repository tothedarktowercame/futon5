(ns cyber-mmca-stride-sweep
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.cyber-mmca.core :as core]))

(defn- usage []
  (str/join
   "\n"
   ["Cyber-MMCA window/stride sweep (W/S grid)."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_stride_sweep.clj [options]"
    ""
    "Options:"
    "  --seeds LIST        Comma-separated seeds (default 4242,1111,2222,3333)."
    "  --controllers LIST  Comma-separated controllers (null,hex,sigil,wiring)."
    "  --Ws LIST           Comma-separated W values (default 10,20,30)."
    "  --Ss LIST           Comma-separated S values (default 1,5,10)."
    "  --windows N         Number of control windows (default 12)."
    "  --length N          Genotype length (default 32)."
    "  --phenotype-length N Phenotype length (default = length)."
    "  --no-phenotype      Disable phenotype generation."
    "  --kernel KW         Kernel keyword (default :mutating-template)."
    "  --sigil STR         Base exotype sigil (default ca/default-sigil)."
    "  --sigil-count N     Control sigil count (default 16)."
    "  --wiring-path PATH  Wiring diagram EDN path (optional)."
    "  --wiring-index N    Wiring candidate index when EDN has :candidates (default 0)."
    "  --wiring-actions EDN Action vector for wiring controller (default [:pressure-up :selectivity-up :selectivity-down :pressure-down])."
    "  --out PATH          Output CSV (default /tmp/cyber-mmca-stride-sweep.csv)."
    "  --help              Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-ints [s]
  (->> (str/split (or s "") #",")
       (map str/trim)
       (remove str/blank?)
       (map parse-int)
       (remove nil?)
       vec))

(defn- parse-seeds [s]
  (parse-ints s))

(defn- parse-controllers [s]
  (->> (str/split (or s "") #",")
       (map str/trim)
       (remove str/blank?)
       (map keyword)
       (remove nil?)
       vec))

(defn- parse-edn [s]
  (try (edn/read-string s) (catch Exception _ nil)))

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

          (= "--controllers" flag)
          (recur (rest more) (assoc opts :controllers (parse-controllers (first more))))

          (= "--Ws" flag)
          (recur (rest more) (assoc opts :Ws (parse-ints (first more))))

          (= "--Ss" flag)
          (recur (rest more) (assoc opts :Ss (parse-ints (first more))))

          (= "--windows" flag)
          (recur (rest more) (assoc opts :windows (parse-int (first more))))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--phenotype-length" flag)
          (recur (rest more) (assoc opts :phenotype-length (parse-int (first more))))

          (= "--no-phenotype" flag)
          (recur more (assoc opts :no-phenotype true))

          (= "--kernel" flag)
          (recur (rest more) (assoc opts :kernel (keyword (first more))))

          (= "--sigil" flag)
          (recur (rest more) (assoc opts :sigil (first more)))

          (= "--sigil-count" flag)
          (recur (rest more) (assoc opts :sigil-count (parse-int (first more))))

          (= "--wiring-path" flag)
          (recur (rest more) (assoc opts :wiring-path (first more)))

          (= "--wiring-index" flag)
          (recur (rest more) (assoc opts :wiring-index (parse-int (first more))))

          (= "--wiring-actions" flag)
          (recur (rest more) (assoc opts :wiring-actions (parse-edn (first more))))

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

(defn -main [& args]
  (let [{:keys [help unknown seeds controllers Ws Ss windows length phenotype-length no-phenotype kernel sigil
                sigil-count wiring-path wiring-index wiring-actions out]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      :else
      (let [_ (ensure-resources!)
            seeds (seq (or seeds [4242 1111 2222 3333]))
            controllers (seq (or controllers [:null :hex :sigil]))
            Ws (seq (or Ws [10 20 30]))
            Ss (seq (or Ss [1 5 10]))
            length (max 1 (int (or length 32)))
            phenotype-length (when-not no-phenotype
                               (or (when (number? phenotype-length) phenotype-length) length))
            base-opts {:windows (max 1 (int (or windows 12)))
                       :length length
                       :phenotype-length phenotype-length
                       :kernel (or kernel :mutating-template)
                       :sigil (or sigil ca/default-sigil)
                       :sigil-count (max 4 (int (or sigil-count 16)))
                       :wiring-path wiring-path
                       :wiring-index (or wiring-index 0)
                       :wiring-actions wiring-actions}
            rows (for [W Ws
                       S Ss
                       controller controllers
                       :let [windows (mapcat (fn [seed]
                                               (core/run-controller
                                                (merge base-opts
                                                       {:controller controller
                                                        :seed seed
                                                        :W W
                                                        :S S})))
                                             seeds)
                             stats (core/stats-for-controller windows)]]
                   [(name controller)
                    W
                    S
                    (count windows)
                    (or (:fraction-freeze stats) 0.0)
                    (or (:fraction-magma stats) 0.0)
                    (or (:fraction-ok stats) 0.0)
                    (or (:avg-freeze-exit stats) "")
                    (or (:avg-magma-exit stats) "")
                    (or (:avg-ok-streak stats) "")
                    (or (:regime-transitions stats) 0)
                    (or (:regime-classes stats) 0)])]
        (rows->csv rows
                   ["controller" "W" "S" "window_count" "freeze_frac" "magma_frac"
                    "ok_frac" "avg_freeze_exit" "avg_magma_exit" "avg_ok_streak"
                    "regime_transitions" "regime_classes"]
                   (or out "/tmp/cyber-mmca-stride-sweep.csv"))
        (println "Wrote" (or out "/tmp/cyber-mmca-stride-sweep.csv"))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
