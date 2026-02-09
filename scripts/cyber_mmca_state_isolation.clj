(ns cyber-mmca-state-isolation
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.cyber-mmca.core :as core]
            [futon5.scripts.output :as out]))

(defn- usage []
  (str/join
   "\n"
   ["Cyber-MMCA genotype-only vs phenotype-only isolation."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_state_isolation.clj [options]"
    ""
    "Options:"
    "  --seeds LIST        Comma-separated seeds (default 4242,1111,2222,3333)."
    "  --controllers LIST  Comma-separated controllers (null,hex,sigil,wiring)."
    "  --windows N         Number of control windows (default 12)."
    "  --W N               Window length in generations (default 10)."
    "  --S N               Window stride (default 10)."
    "  --length N          Genotype length (default 32)."
    "  --phenotype-length N Phenotype length (default 32)."
    "  --kernel KW         Kernel keyword (default :mutating-template)."
    "  --sigil STR         Base exotype sigil (default ca/default-sigil)."
    "  --sigil-count N     Control sigil count (default 16)."
    "  --wiring-path PATH  Wiring diagram EDN path (optional)."
    "  --wiring-index N    Wiring candidate index when EDN has :candidates (default 0)."
    "  --wiring-actions EDN Action vector for wiring controller (default [:pressure-up :selectivity-up :selectivity-down :pressure-down])."
    "  --out PATH          Output CSV (default /tmp/cyber-mmca-state-isolation.csv)."
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

          (= "--windows" flag)
          (recur (rest more) (assoc opts :windows (parse-int (first more))))

          (= "--W" flag)
          (recur (rest more) (assoc opts :W (parse-int (first more))))

          (= "--S" flag)
          (recur (rest more) (assoc opts :S (parse-int (first more))))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--phenotype-length" flag)
          (recur (rest more) (assoc opts :phenotype-length (parse-int (first more))))

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
  (out/spit-text!
   out-path
   (str (str/join "," header)
        "\n"
        (str/join "\n" (map #(str/join "," %) rows)))))

(defn -main [& args]
  (let [{:keys [help unknown seeds controllers windows W S length phenotype-length
                kernel sigil sigil-count wiring-path wiring-index wiring-actions out]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      :else
      (let [_ (ensure-resources!)
            seeds (seq (or seeds [4242 1111 2222 3333]))
            controllers (seq (or controllers [:null :hex :sigil]))
            base-opts {:windows (max 1 (int (or windows 12)))
                       :W (max 1 (int (or W 10)))
                       :S (max 1 (int (or S 10)))
                       :length (max 1 (int (or length 32)))
                       :phenotype-length (max 1 (int (or phenotype-length 32)))
                       :kernel (or kernel :mutating-template)
                       :sigil (or sigil ca/default-sigil)
                       :sigil-count (max 4 (int (or sigil-count 16)))
                       :wiring-path wiring-path
                       :wiring-index (or wiring-index 0)
                       :wiring-actions wiring-actions}
            variants [{:mode "genotype" :freeze-genotype false :phenotype-length nil}
                      {:mode "phenotype" :freeze-genotype true}]
            rows (for [{:keys [mode] :as variant} variants
                       controller controllers
                       :let [windows (mapcat (fn [seed]
                                               (core/run-controller
                                                (merge base-opts
                                                       variant
                                                       {:controller controller
                                                        :seed seed})))
                                             seeds)
                             stats (core/stats-for-controller windows)]]
                   [mode
                    (name controller)
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
                   ["mode" "controller" "window_count" "freeze_frac" "magma_frac"
                    "ok_frac" "avg_freeze_exit" "avg_magma_exit" "avg_ok_streak"
                    "regime_transitions" "regime_classes"]
                   (or out "/tmp/cyber-mmca-state-isolation.csv"))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
