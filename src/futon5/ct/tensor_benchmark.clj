(ns futon5.ct.tensor-benchmark
  "Fixed-seed benchmark comparing tensor execution vs meta-lift execution."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.ct.tensor :as tensor]
            [futon5.ct.tensor-mmca :as tensor-mmca]
            [futon5.ct.tensor-transfer :as transfer]
            [futon5.mmca.payload :as payload]
            [futon5.mmca.meta-lift :as meta-lift]))

(defn- parse-int [s]
  (try
    (Long/parseLong s)
    (catch Exception _ nil)))

(defn- usage []
  (str/join
   "\n"
   ["Tensor benchmark (fixed-seed)"
    ""
    "Usage:"
    "  clj -M -m futon5.ct.tensor-benchmark [options]"
    ""
    "Options:"
    "  --seed N             RNG seed for deterministic case generation (default 42)."
    "  --cases N            Number of generated cases (default 8)."
    "  --length N           Genotype length for each case (default 24)."
    "  --generations N      Steps per case (default 48)."
    "  --rule-sigil SIGIL   Fixed rule sigil (default random per case)."
    "  --with-phenotype     Include a fixed phenotype gate (generated from seed)."
    "  --out PATH           Optional EDN output path."
    "  --report PATH        Write metaevolve-style report payload (EDN)."
    "  --feedback PATH      Write metaevolve-style feedback payload (EDN)."
    "  --leaderboard-size N Leaderboard size for report/feedback (default 16)."
    "  --help               Show this message."]))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

          (= "--cases" flag)
          (recur (rest more) (assoc opts :cases (parse-int (first more))))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--generations" flag)
          (recur (rest more) (assoc opts :generations (parse-int (first more))))

          (= "--rule-sigil" flag)
          (recur (rest more) (assoc opts :rule-sigil (first more)))

          (= "--with-phenotype" flag)
          (recur more (assoc opts :with-phenotype true))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          (= "--report" flag)
          (recur (rest more) (assoc opts :report (first more)))

          (= "--feedback" flag)
          (recur (rest more) (assoc opts :feedback (first more)))

          (= "--leaderboard-size" flag)
          (recur (rest more) (assoc opts :leaderboard-size (parse-int (first more))))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(def ^:private sigils
  (mapv :sigil (ca/sigil-entries)))

(defn- random-sigil [^java.util.Random rng]
  (nth sigils (rng-int rng (count sigils))))

(defn- random-genotype [^java.util.Random rng length]
  (apply str (repeatedly length #(random-sigil rng))))

(defn- random-phenotype [^java.util.Random rng length]
  (apply str (repeatedly length #(if (zero? (rng-int rng 2)) \0 \1))))

(defn generate-cases
  "Generate deterministic benchmark cases."
  [{:keys [seed cases length generations rule-sigil with-phenotype]
    :or {seed 42
         cases 8
         length 24
         generations 48}}]
  (let [rng (java.util.Random. (long seed))]
    (mapv (fn [idx]
            (let [genotype (random-genotype rng length)
                  fixed-rule (or rule-sigil (random-sigil rng))
                  phenotype (when with-phenotype (random-phenotype rng length))
                  case-seed (+ (long seed) (long idx))]
              {:id idx
               :seed case-seed
               :genotype genotype
               :rule-sigil fixed-rule
               :phenotype phenotype
               :generations generations}))
          (range cases))))

(defn run-meta-lift-ca
  "Run historical meta-lift stepping with optional phenotype gate."
  [{:keys [genotype rule-sigil phenotype generations]}]
  (loop [step 0
         current genotype
         history [genotype]]
    (if (>= step generations)
      {:gen-history history
       :rule-sigil rule-sigil
       :phenotype phenotype
       :generations generations
       :backend :meta-lift}
      (let [next-row (meta-lift/lift-sigil-string current rule-sigil)
            gated-row (if phenotype
                        (tensor/gate-sigil-row-by-phenotype current next-row phenotype)
                        next-row)]
        (recur (inc step) gated-row (conj history gated-row))))))

(defn- elapsed-ms [start-ns end-ns]
  (/ (double (- end-ns start-ns)) 1000000.0))

(defn run-case
  "Run one benchmark case across tensor and meta-lift paths."
  [{:keys [id seed genotype rule-sigil phenotype generations] :as case-spec}]
  (let [tensor-start (System/nanoTime)
        tensor-run (tensor-mmca/run-tensor-mmca {:genotype genotype
                                                 :rule-sigil rule-sigil
                                                 :phenotype phenotype
                                                 :generations generations
                                                 :seed seed
                                                 :step-opts {:wrap? false :boundary-bit 0}})
        tensor-end (System/nanoTime)
        meta-start (System/nanoTime)
        meta-run (-> (run-meta-lift-ca case-spec)
                     tensor-mmca/run->mmca-metrics)
        meta-end (System/nanoTime)
        transfer-pack (transfer/run-transfer-pack {:gen-history (:gen-history tensor-run)
                                                   :summary (:summary tensor-run)
                                                   :seed (or seed id)
                                                   :run-index id})
        tensor-final (last (:gen-history tensor-run))
        meta-final (last (:gen-history meta-run))
        tensor-score (double (or (get-in tensor-run [:summary :composite-score]) 0.0))
        meta-score (double (or (get-in meta-run [:summary :composite-score]) 0.0))]
    {:id id
     :seed seed
     :input {:genotype genotype
             :rule-sigil rule-sigil
             :phenotype phenotype
             :generations generations}
     :parity? (= tensor-final meta-final)
     :history-parity? (= (:gen-history tensor-run) (:gen-history meta-run))
     :tensor {:final tensor-final
              :ms (elapsed-ms tensor-start tensor-end)
              :score tensor-score
              :summary (:summary tensor-run)
              :run tensor-run}
     :meta-lift {:final meta-final
                 :ms (elapsed-ms meta-start meta-end)
                 :score meta-score
                 :summary (:summary meta-run)
                 :run meta-run}
     :transfer transfer-pack
     :score-delta (- tensor-score meta-score)}))

(defn- avg [xs]
  (when (seq xs)
    (/ (reduce + 0.0 xs) (double (count xs)))))

(defn summarize-results
  "Aggregate benchmark result rows."
  [rows]
  (let [n (count rows)
        tensor-ms (map (comp :ms :tensor) rows)
        meta-ms (map (comp :ms :meta-lift) rows)
        parity (count (filter :parity? rows))
        history-parity (count (filter :history-parity? rows))
        total-tensor (reduce + 0.0 tensor-ms)
        total-meta (reduce + 0.0 meta-ms)]
    {:cases n
     :parity-count parity
     :parity-rate (if (pos? n) (/ (double parity) (double n)) 0.0)
     :history-parity-count history-parity
     :history-parity-rate (if (pos? n) (/ (double history-parity) (double n)) 0.0)
     :tensor-ms-total total-tensor
     :tensor-ms-avg (avg tensor-ms)
     :meta-lift-ms-total total-meta
     :meta-lift-ms-avg (avg meta-ms)
     :speedup-meta-over-tensor (when (pos? total-tensor)
                                 (/ total-meta total-tensor))
     :score-delta-avg (avg (map :score-delta rows))}))

(defn run-fixed-seed-benchmark
  "Run deterministic tensor-vs-meta-lift comparisons."
  [{:keys [seed cases length generations rule-sigil with-phenotype] :as opts}]
  (let [case-specs (generate-cases {:seed (or seed 42)
                                    :cases (or cases 8)
                                    :length (or length 24)
                                    :generations (or generations 48)
                                    :rule-sigil rule-sigil
                                    :with-phenotype with-phenotype})
        rows (mapv run-case case-specs)]
    {:benchmark :tensor-vs-meta-lift
     :config (select-keys opts [:seed :cases :length :generations :rule-sigil :with-phenotype])
     :results rows
     :summary (summarize-results rows)}))

(defn ranked-entries
  "Create metaevolve-compatible ranked entries from benchmark rows."
  [benchmark-result]
  (->> (:results benchmark-result)
       (map (fn [row]
              (let [input (:input row)
                    transfer (:transfer row)
                    tensor-run (get-in row [:tensor :run])
                    base-summary (get-in row [:tensor :summary])
                    aif (or (:aif transfer) {})
                    summary (merge base-summary
                                   {:aif-score (:aif/score aif)}
                                   aif)
                    rule {:engine :tensor
                          :rule-sigil (:rule-sigil input)
                          :phenotype? (boolean (:phenotype input))
                          :diagram :sigil-step}
                    run-result (select-keys tensor-run [:gen-history :phe-history :metrics-history])]
                {:summary summary
                 :rule rule
                 :seed (:seed row)
                 :policy :tensor-benchmark
                 :meta-lift {:top-sigils (:top-sigils transfer)
                             :sigil-counts (:sigil-counts transfer)}
                 :tensor-transfer {:cyber-ant (:cyber-ant transfer)
                                   :aif aif}
                 :run-result run-result})))
       (sort-by (comp :composite-score :summary) >)
       vec))

(defn leaderboard
  "Build metaevolve-style leaderboard entries."
  ([benchmark-result] (leaderboard benchmark-result 16))
  ([benchmark-result leaderboard-size]
   (let [size (long (or leaderboard-size 16))]
     (->> (ranked-entries benchmark-result)
          (map (fn [{:keys [summary rule seed]}]
                 {:score (double (or (:composite-score summary) 0.0))
                  :rule rule
                  :seed seed}))
          (take (max 1 size))
          vec))))

(defn feedback-payload
  "Create metaevolve-compatible feedback payload."
  [benchmark-result {:keys [leaderboard-size] :or {leaderboard-size 16}}]
  (let [ranked (ranked-entries benchmark-result)
        lb (leaderboard benchmark-result leaderboard-size)
        feedback-sigils (->> ranked
                             (mapcat (fn [entry]
                                       (get-in entry [:meta-lift :top-sigils])))
                             distinct
                             vec)
        cfg (:config benchmark-result)]
    (payload/feedback-payload {:meta {:seed (:seed cfg)
                                      :runs (:cases cfg)
                                      :length (:length cfg)
                                      :generations (:generations cfg)
                                      :engine :tensor}
                               :feedback-sigils feedback-sigils
                               :learned-specs {}
                               :leaderboard lb
                               :runs-completed (:cases cfg)})))

(defn- run-detail-entry [idx row]
  (let [transfer (:transfer row)
        input (:input row)
        tensor-summary (get-in row [:tensor :summary])]
    {:schema/version 1
     :experiment/id :tensor-benchmark
     :event :run
     :run/id (inc idx)
     :seed (:seed row)
     :engine :tensor
     :rule-sigil (:rule-sigil input)
     :score {:short (get-in row [:tensor :score])
             :final (get-in row [:tensor :score])
             :meta-lift (get-in row [:meta-lift :score])}
     :summary tensor-summary
     :meta-lift {:top-sigils (:top-sigils transfer)
                 :sigil-counts (:sigil-counts transfer)}
     :tensor-transfer {:aif (:aif transfer)
                       :cyber-ant (:cyber-ant transfer)}}))

(defn report-payload
  "Create metaevolve-compatible report payload."
  [benchmark-result {:keys [leaderboard-size] :or {leaderboard-size 16}}]
  (let [ranked (ranked-entries benchmark-result)
        lb (leaderboard benchmark-result leaderboard-size)
        cfg (:config benchmark-result)
        runs-detail (mapv run-detail-entry (range) (:results benchmark-result))]
    (payload/report-payload {:meta {:seed (:seed cfg)
                                    :runs (:cases cfg)
                                    :length (:length cfg)
                                    :generations (:generations cfg)
                                    :engine :tensor}
                             :ranked ranked
                             :learned-specs {}
                             :leaderboard lb
                             :runs-detail runs-detail
                             :top-runs (mapv #(select-keys % [:summary :rule :seed :policy :run-result])
                                             (take (long (or leaderboard-size 16)) ranked))})))

(defn- write-edn! [path data]
  (let [f (io/file path)]
    (when-let [parent (.getParentFile f)]
      (.mkdirs parent))
    (spit f (pr-str data))
    path))

(defn -main [& args]
  (let [{:keys [help unknown out report feedback leaderboard-size] :as opts} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      :else
      (let [result (run-fixed-seed-benchmark opts)
            leaderboard-size (or leaderboard-size 16)]
        (println (str "Cases: " (get-in result [:summary :cases])
                      " | parity: " (format "%.2f" (* 100.0 (or (get-in result [:summary :parity-rate]) 0.0))) "%"
                      " | history parity: " (format "%.2f" (* 100.0 (or (get-in result [:summary :history-parity-rate]) 0.0))) "%"))
        (println (str "Tensor total ms: " (format "%.3f" (or (get-in result [:summary :tensor-ms-total]) 0.0))
                      " | Meta total ms: " (format "%.3f" (or (get-in result [:summary :meta-lift-ms-total]) 0.0))))
        (when out
          (println "Wrote:" (write-edn! out result)))
        (when report
          (println "Wrote report:" (write-edn! report (report-payload result {:leaderboard-size leaderboard-size}))))
        (when feedback
          (println "Wrote feedback:" (write-edn! feedback (feedback-payload result {:leaderboard-size leaderboard-size}))))
        (when-not out
          (println (pr-str {:summary (:summary result)})))))))
