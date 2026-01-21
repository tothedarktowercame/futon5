(ns cyber-mmca-ab-compare
  (:require [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.cyber-mmca.core :as core]))

(defn- usage []
  (str/join
   "\n"
   ["Cyber-MMCA fixed-seed A/B toggle comparison."
    ""
    "Usage:"
    "  bb -cp futon5/src futon5/scripts/cyber_mmca_ab_compare.clj [options]"
    ""
    "Options:"
    "  --seeds LIST        Comma-separated seeds (default 4242,1111,2222,3333)."
    "  --controllers LIST  Comma-separated controllers (null,hex,sigil)."
    "  --toggle KEY        Toggle key: genotype-gate or exotype-mode (default genotype-gate)."
    "  --a VALUE           A value (default false for genotype-gate, inline for exotype-mode)."
    "  --b VALUE           B value (default true for genotype-gate, nominate for exotype-mode)."
    "  --windows N         Number of control windows (default 12)."
    "  --W N               Window length in generations (default 10)."
    "  --S N               Window stride (default 10)."
    "  --length N          Genotype length (default 32)."
    "  --kernel KW         Kernel keyword (default :mutating-template)."
    "  --sigil STR         Base exotype sigil (default ca/default-sigil)."
    "  --sigil-count N     Control sigil count (default 16)."
    "  --out PATH          Output CSV (default /tmp/cyber-mmca-ab.csv)."
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

(defn- parse-bool [s]
  (case (str/lower-case (str s))
    "true" true
    "false" false
    nil))

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

          (= "--toggle" flag)
          (recur (rest more) (assoc opts :toggle (keyword (first more))))

          (= "--a" flag)
          (recur (rest more) (assoc opts :a (first more)))

          (= "--b" flag)
          (recur (rest more) (assoc opts :b (first more)))

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

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- toggle-values [{:keys [toggle a b]}]
  (case toggle
    :exotype-mode {:a (keyword (or a "inline"))
                   :b (keyword (or b "nominate"))}
    :genotype-gate {:a (or (parse-bool a) false)
                    :b (or (parse-bool b) true)}
    {:a (or (parse-bool a) false)
     :b (or (parse-bool b) true)}))

(defn- rows->csv [rows header out-path]
  (spit out-path
        (str (str/join "," header)
             "\n"
             (str/join "\n" (map #(str/join "," %) rows)))))

(defn -main [& args]
  (let [{:keys [help unknown seeds controllers toggle windows W S length kernel sigil
                sigil-count out a b]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      :else
      (let [toggle (or toggle :genotype-gate)
            {:keys [a b]} (toggle-values {:toggle toggle :a a :b b})
            seeds (seq (or seeds [4242 1111 2222 3333]))
            controllers (seq (or controllers [:hex]))
            base-opts {:windows (max 1 (int (or windows 12)))
                       :W (max 1 (int (or W 10)))
                       :S (max 1 (int (or S 10)))
                       :length (max 1 (int (or length 32)))
                       :kernel (or kernel :mutating-template)
                       :sigil (or sigil ca/default-sigil)
                       :sigil-count (max 4 (int (or sigil-count 16)))}
            variants [{:variant "A" toggle a}
                      {:variant "B" toggle b}]
            all-windows (mapcat (fn [seed]
                                  (mapcat (fn [controller]
                                            (mapcat (fn [variant]
                                                      (let [opts (merge base-opts
                                                                        variant
                                                                        {:controller controller
                                                                         :seed seed})
                                                            windows (core/run-controller opts)]
                                                        (map #(assoc % :variant (:variant variant)) windows)))
                                                    variants))
                                          controllers))
                                seeds)
            grouped (group-by (fn [row] [(:variant row) (:controller row)]) all-windows)
            rows (map (fn [{:keys [variant controller seed window w-start w-end regime
                                   pressure selectivity structure activity actions sigil
                                   delta-update delta-match applied? genotype-gate exotype-mode
                                   freeze-genotype]}]
                        [variant
                         (name controller)
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
                         (if genotype-gate "true" "false")
                         (if freeze-genotype "true" "false")
                         (name (or exotype-mode :inline))])
                      all-windows)]
        (rows->csv rows
                   ["variant" "controller" "seed" "window" "w_start" "w_end"
                    "regime" "pressure" "selectivity" "structure" "activity"
                    "actions" "sigil" "delta_update" "delta_match" "applied"
                    "genotype_gate" "freeze_genotype" "exotype_mode"]
                   (or out "/tmp/cyber-mmca-ab.csv"))
        (doseq [[[variant controller] windows] grouped]
          (println (format "%s/%s stats: %s"
                           variant
                           (name controller)
                           (core/stats-for-controller windows))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
