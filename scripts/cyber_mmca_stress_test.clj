(ns cyber-mmca-stress-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.cyber-mmca.core :as core]))

(defn- usage []
  (str/join
   "\n"
   ["Cyber-MMCA terminal stress test (extreme params + recovery)."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_stress_test.clj [options]"
    ""
    "Options:"
    "  --seeds LIST        Comma-separated seeds (default 4242,1111,2222,3333)."
    "  --controllers LIST  Comma-separated controllers (null,hex,sigil)."
    "  --windows N         Number of control windows (default 12)."
    "  --W N               Window length in generations (default 10)."
    "  --S N               Window stride (default 10)."
    "  --length N          Genotype length (default 32)."
    "  --kernel KW         Kernel keyword (default :mutating-template)."
    "  --sigil STR         Base exotype sigil (default ca/default-sigil)."
    "  --sigil-count N     Control sigil count (default 16)."
    "  --stress-window N   Window index to stress (default 2)."
    "  --stress-update V   Override update-prob during stress (default 1.0)."
    "  --stress-match V    Override match-threshold during stress (default 0.0)."
    "  --out PATH          Output CSV (default /tmp/cyber-mmca-stress.csv)."
    "  --help              Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-double* [s]
  (try (Double/parseDouble s) (catch Exception _ nil)))

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

          (= "--kernel" flag)
          (recur (rest more) (assoc opts :kernel (keyword (first more))))

          (= "--sigil" flag)
          (recur (rest more) (assoc opts :sigil (first more)))

          (= "--sigil-count" flag)
          (recur (rest more) (assoc opts :sigil-count (parse-int (first more))))

          (= "--stress-window" flag)
          (recur (rest more) (assoc opts :stress-window (parse-int (first more))))

          (= "--stress-update" flag)
          (recur (rest more) (assoc opts :stress-update (parse-double* (first more))))

          (= "--stress-match" flag)
          (recur (rest more) (assoc opts :stress-match (parse-double* (first more))))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- rows->csv [rows header out-path]
  (spit out-path
        (str (str/join "," header)
             "\n"
             (str/join "\n" (map #(str/join "," %) rows)))))

(defn- ensure-resources! []
  (when-not (io/resource "futon5/sigils.edn")
    (when-let [add-cp (resolve 'babashka.classpath/add-classpath)]
      (add-cp "futon5/resources"))
    (when-not (io/resource "futon5/sigils.edn")
      (throw (ex-info "Missing futon5/resources on classpath."
                      {:hint "Use: bb -cp futon5/src:futon5/resources ..."})))))

(defn -main [& args]
  (let [{:keys [help unknown seeds controllers windows W S length kernel sigil
                sigil-count out stress-window stress-update stress-match]} (parse-args args)]
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
                       :kernel (or kernel :mutating-template)
                       :sigil (or sigil ca/default-sigil)
                       :sigil-count (max 4 (int (or sigil-count 16)))}
            stress-window (or stress-window 2)
            stress-params {:update-prob (or stress-update 1.0)
                           :match-threshold (or stress-match 0.0)}
            all-windows (mapcat (fn [seed]
                                  (mapcat (fn [controller]
                                            (core/run-stress-controller
                                             (merge base-opts
                                                    {:controller controller
                                                     :seed seed
                                                     :stress-windows [stress-window]
                                                     :stress-params stress-params})))
                                          controllers))
                                seeds)
            grouped (group-by :controller all-windows)
            rows (map (fn [{:keys [controller seed window w-start w-end regime pressure selectivity structure
                                   activity actions sigil delta-update delta-match applied? stress?]}]
                        [(name controller)
                         seed
                         window
                         w-start
                         w-end
                         (or (some-> regime name) "")
                         (when (number? pressure) (format "%.4f" (double pressure)))
                         (when (number? selectivity) (format "%.4f" (double selectivity)))
                         (when (number? structure) (format "%.4f" (double structure)))
                         (when (number? activity) (format "%.4f" (double activity)))
                         (str/join "+" (map name actions))
                         (or sigil "")
                         (when (number? delta-update) (format "%.4f" (double delta-update)))
                         (when (number? delta-match) (format "%.4f" (double delta-match)))
                         (if applied? "true" "false")
                         (if stress? "true" "false")])
                      all-windows)]
        (rows->csv rows
                   ["controller" "seed" "window" "w_start" "w_end" "regime"
                    "pressure" "selectivity" "structure" "activity" "actions"
                    "sigil" "delta_update" "delta_match" "applied" "stress"]
                   (or out "/tmp/cyber-mmca-stress.csv"))
        (doseq [[controller windows] grouped]
          (println (format "%s stats: %s" (name controller) (core/stats-for-controller windows))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
