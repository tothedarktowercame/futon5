(ns futon5.ct.tensor-closed-loop
  "Deterministic tensor-first closed-loop experiment runner.

   Loop:
   tensor run -> transfer pack -> policy update -> next tensor run"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.ct.tensor :as tensor]
            [futon5.ct.tensor-mmca :as tensor-mmca]
            [futon5.ct.tensor-transfer :as transfer]
            [futon5.mmca.meta-lift :as meta-lift]
            [futon5.mmca.payload :as payload]))

(def ^:private default-seed 42)
(def ^:private default-runs 8)
(def ^:private default-length 24)
(def ^:private default-generations 48)
(def ^:private default-feedback-top 16)
(def ^:private default-leaderboard-size 16)
(def ^:private default-rule-sigil "手")
(def ^:private default-step-opts {:wrap? false :boundary-bit 0 :backend :clj})

(def ^:private sigils
  (mapv :sigil (ca/sigil-entries)))

(defn- parse-int [s]
  (try
    (Long/parseLong s)
    (catch Exception _ nil)))

(defn- parse-float [s]
  (try
    (Double/parseDouble s)
    (catch Exception _ nil)))

(defn- usage []
  (str/join
   "\n"
   ["Tensor closed-loop runner (deterministic, parity-checked)"
    ""
    "Usage:"
    "  clj -M -m futon5.ct.tensor-closed-loop [options]"
    ""
    "Options:"
    "  --seed N                RNG seed (default 42)."
    "  --runs N                Number of closed-loop iterations (default 8)."
    "  --length N              Initial genotype length when random (default 24)."
    "  --generations N         Steps per run (default 48)."
    "  --init-genotype STRING  Initial genotype (default deterministic random by seed)."
    "  --init-rule-sigil SIGIL Initial tensor rule sigil (default 手)."
    "  --phenotype STRING      Fixed phenotype gate for all runs."
    "  --with-phenotype        Use deterministic random fixed phenotype."
    "  --feedback-top N        Keep top-N transfer sigils in loop policy (default 16)."
    "  --init-feedback-sigils CSV  Seed initial feedback sigils (comma-separated)."
    "  --explore-rate P        Exploration rate for rule updates, 0..1 (default 0)."
    "  --leaderboard-size N    Leaderboard size for report/feedback (default 16)."
    "  --backend NAME          Tensor backend: clj or jax (default clj)."
    "  --wrap                  Use wrapped boundary mode (default open boundary)."
    "  --boundary-bit N        Boundary bit for open mode (default 0)."
    "  --strict-parity         Fail immediately on parity mismatch (default)."
    "  --no-strict-parity      Continue and report parity mismatches."
    "  --out PATH              Write full loop result (EDN)."
    "  --report PATH           Write report payload (EDN)."
    "  --feedback PATH         Write feedback payload (EDN)."
    "  --help                  Show this message."]))

(defn- parse-args [args]
  (loop [args args
         opts {:strict-parity true}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

          (= "--runs" flag)
          (recur (rest more) (assoc opts :runs (parse-int (first more))))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--generations" flag)
          (recur (rest more) (assoc opts :generations (parse-int (first more))))

          (= "--init-genotype" flag)
          (recur (rest more) (assoc opts :init-genotype (first more)))

          (= "--init-rule-sigil" flag)
          (recur (rest more) (assoc opts :init-rule-sigil (first more)))

          (= "--phenotype" flag)
          (recur (rest more) (assoc opts :phenotype (first more)))

          (= "--with-phenotype" flag)
          (recur more (assoc opts :with-phenotype true))

          (= "--feedback-top" flag)
          (recur (rest more) (assoc opts :feedback-top (parse-int (first more))))

          (= "--init-feedback-sigils" flag)
          (recur (rest more)
                 (assoc opts :init-feedback-sigils
                        (->> (str/split (or (first more) "") #",")
                             (map str/trim)
                             (remove empty?)
                             vec)))

          (= "--explore-rate" flag)
          (recur (rest more) (assoc opts :explore-rate (parse-float (first more))))

          (= "--leaderboard-size" flag)
          (recur (rest more) (assoc opts :leaderboard-size (parse-int (first more))))

          (= "--backend" flag)
          (recur (rest more) (assoc opts :backend (keyword (first more))))

          (= "--wrap" flag)
          (recur more (assoc opts :wrap? true))

          (= "--boundary-bit" flag)
          (recur (rest more) (assoc opts :boundary-bit (parse-int (first more))))

          (= "--strict-parity" flag)
          (recur more (assoc opts :strict-parity true))

          (= "--no-strict-parity" flag)
          (recur more (assoc opts :strict-parity false))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          (= "--report" flag)
          (recur (rest more) (assoc opts :report (first more)))

          (= "--feedback" flag)
          (recur (rest more) (assoc opts :feedback (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- random-sigil [^java.util.Random rng]
  (nth sigils (.nextInt rng (count sigils))))

(defn- random-genotype [^java.util.Random rng length]
  (apply str (repeatedly (long length) #(random-sigil rng))))

(defn- random-phenotype [^java.util.Random rng length]
  (apply str (repeatedly (long length) #(if (zero? (.nextInt rng 2)) \0 \1))))

(defn- run-meta-lift-ca
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

(defn- trim-feedback [sigils feedback-top]
  (let [limit (max 1 (long (or feedback-top default-feedback-top)))]
    (->> sigils
         (map str)
         (remove empty?)
         distinct
         (take limit)
         vec)))

(defn- update-policy
  [policy transfer-pack feedback-top ^java.util.Random rng explore-rate]
  (let [feedback-sigils (trim-feedback
                         (concat (:feedback-sigils policy)
                                 (:top-sigils transfer-pack))
                         feedback-top)
        top-sigils (vec (or (:top-sigils transfer-pack) []))
        exploit-rule (or (first top-sigils)
                         (:rule-sigil policy)
                         default-rule-sigil)
        p (double (max 0.0 (min 1.0 (or explore-rate 0.0))))
        explore? (and (pos? p) (< (.nextDouble rng) p))
        explore-pool (if (seq top-sigils) top-sigils feedback-sigils)
        explored-rule (when (and explore? (seq explore-pool))
                        (nth explore-pool (.nextInt rng (count explore-pool))))
        next-rule-sigil (or explored-rule exploit-rule)]
    {:rule-sigil next-rule-sigil
     :feedback-sigils feedback-sigils}))

(defn- leaderboard
  ([ranked] (leaderboard ranked default-leaderboard-size))
  ([ranked leaderboard-size]
   (let [size (max 1 (long (or leaderboard-size default-leaderboard-size)))]
     (->> ranked
          (map (fn [{:keys [summary rule seed]}]
                 {:score (double (or (:composite-score summary) 0.0))
                  :rule rule
                  :seed seed}))
          (take size)
          vec))))

(defn run-tensor-closed-loop
  "Run tensor-first closed-loop experiments.

   Options:
   - :seed :runs :length :generations
   - :init-genotype :init-rule-sigil :phenotype :with-phenotype
   - :step-opts map (defaults to open-boundary :clj backend)
   - :backend, :wrap?, :boundary-bit (merged into :step-opts)
   - :feedback-top :init-feedback-sigils :explore-rate :strict-parity"
  [{:keys [seed runs length generations init-genotype init-rule-sigil phenotype
           with-phenotype step-opts backend wrap? boundary-bit feedback-top
           init-feedback-sigils explore-rate leaderboard-size strict-parity]
    :or {strict-parity true}}]
  (let [seed (long (or seed default-seed))
        runs (long (or runs default-runs))
        length (long (or length default-length))
        generations (long (or generations default-generations))
        rng (java.util.Random. seed)
        policy-rng (java.util.Random. (long (+ seed 4242)))
        init-genotype (or init-genotype (random-genotype rng length))
        init-phenotype (or phenotype
                          (when with-phenotype
                            (random-phenotype rng (count init-genotype))))
        init-rule-sigil (or init-rule-sigil default-rule-sigil)
        step-opts (merge default-step-opts
                         step-opts
                         (cond-> {}
                           backend (assoc :backend backend)
                           (contains? #{true false} wrap?) (assoc :wrap? wrap?)
                           (some? boundary-bit) (assoc :boundary-bit boundary-bit)))
        loop-result
        (loop [idx 0
               genotype init-genotype
               policy {:rule-sigil init-rule-sigil
                       :feedback-sigils (trim-feedback init-feedback-sigils feedback-top)}
               rows []]
          (if (= idx runs)
            rows
            (let [run-seed (+ seed 1000 idx)
                  rule-sigil (:rule-sigil policy)
                  rule {:engine :tensor
                        :rule-sigil rule-sigil
                        :backend (or (:backend step-opts) :clj)
                        :closed-loop true}
                  run-input {:genotype genotype
                             :rule-sigil rule-sigil
                             :phenotype init-phenotype
                             :generations generations
                             :seed run-seed
                             :step-opts step-opts}
                  tensor-run (tensor-mmca/run-tensor-mmca run-input)
                  meta-run (-> (run-meta-lift-ca run-input)
                               tensor-mmca/run->mmca-metrics)
                  final-parity? (= (last (:gen-history tensor-run))
                                   (last (:gen-history meta-run)))
                  history-parity? (= (:gen-history tensor-run)
                                     (:gen-history meta-run))
                  _ (when (and strict-parity (not history-parity?))
                      (throw (ex-info "Tensor/meta-lift parity mismatch in strict mode"
                                      {:run-index idx
                                       :seed run-seed
                                       :rule-sigil rule-sigil
                                       :tensor-final (last (:gen-history tensor-run))
                                       :meta-final (last (:gen-history meta-run))})))
                  transfer-pack (transfer/run-transfer-pack {:gen-history (:gen-history tensor-run)
                                                             :summary (:summary tensor-run)
                                                             :seed run-seed
                                                             :run-index idx
                                                             :policy :tensor-closed-loop
                                                             :rule rule})
                  next-policy (update-policy policy transfer-pack feedback-top policy-rng explore-rate)
                  row {:run-id (inc idx)
                       :seed run-seed
                       :input (select-keys run-input [:genotype :rule-sigil :phenotype :generations])
                       :policy {:before policy :after next-policy}
                       :parity? final-parity?
                       :history-parity? history-parity?
                       :tensor {:summary (:summary tensor-run)
                                :run tensor-run}
                       :meta-lift {:summary (:summary meta-run)
                                   :run meta-run}
                       :transfer transfer-pack
                       :ranked {:summary (:summary tensor-run)
                                :rule rule
                                :seed run-seed
                                :policy :tensor-closed-loop
                                :meta-lift {:top-sigils (:top-sigils transfer-pack)
                                            :sigil-counts (:sigil-counts transfer-pack)}
                                :tensor-transfer {:cyber-ant (:cyber-ant transfer-pack)
                                                  :aif (:aif transfer-pack)}
                                :run-result (select-keys tensor-run [:gen-history :phe-history :metrics-history])}}]
              (recur (inc idx)
                     (last (:gen-history tensor-run))
                     next-policy
                     (conj rows row)))))
        ranked (->> loop-result
                    (map :ranked)
                    (sort-by (comp :composite-score :summary) >)
                    vec)
        leaderboard-size (long (or leaderboard-size default-leaderboard-size))
        lb (leaderboard ranked leaderboard-size)
        feedback-sigils (->> loop-result
                             (mapcat #(get-in % [:transfer :top-sigils]))
                             distinct
                             vec)
        parity-count (count (filter :parity? loop-result))
        history-parity-count (count (filter :history-parity? loop-result))]
    {:experiment :tensor-closed-loop
     :config {:seed seed
              :runs runs
              :length length
              :generations generations
              :init-rule-sigil init-rule-sigil
              :feedback-top (long (or feedback-top default-feedback-top))
              :explore-rate (double (max 0.0 (min 1.0 (or explore-rate 0.0))))
              :leaderboard-size leaderboard-size
              :strict-parity (boolean strict-parity)
              :step-opts step-opts}
     :initial {:genotype init-genotype
               :phenotype init-phenotype}
     :results loop-result
     :ranked ranked
     :leaderboard lb
     :feedback-sigils feedback-sigils
     :summary {:runs runs
               :parity-count parity-count
               :parity-rate (if (pos? runs) (/ (double parity-count) (double runs)) 0.0)
               :history-parity-count history-parity-count
               :history-parity-rate (if (pos? runs) (/ (double history-parity-count) (double runs)) 0.0)}}))

(defn feedback-payload
  "Build metaevolve-compatible feedback payload."
  ([loop-result] (feedback-payload loop-result {}))
  ([loop-result {:keys [leaderboard-size]}]
   (let [cfg (:config loop-result)
         ranked (:ranked loop-result)
         lb (leaderboard ranked (or leaderboard-size default-leaderboard-size))]
     (payload/feedback-payload
      {:meta {:seed (:seed cfg)
              :runs (:runs cfg)
              :length (:length cfg)
              :generations (:generations cfg)
              :engine :tensor
              :closed-loop true}
       :feedback-sigils (:feedback-sigils loop-result)
       :learned-specs {}
       :leaderboard lb
       :runs-completed (:runs cfg)}))))

(defn- run-detail-entry
  [row]
  {:schema/version 1
   :experiment/id :tensor-closed-loop
   :event :run
   :run/id (:run-id row)
   :seed (:seed row)
   :engine :tensor
   :rule-sigil (get-in row [:input :rule-sigil])
   :parity {:final (:parity? row)
            :history (:history-parity? row)}
   :summary (get-in row [:tensor :summary])
   :meta-lift {:top-sigils (get-in row [:transfer :top-sigils])
               :sigil-counts (get-in row [:transfer :sigil-counts])}
   :tensor-transfer {:aif (get-in row [:transfer :aif])
                     :cyber-ant (get-in row [:transfer :cyber-ant])}})

(defn report-payload
  "Build metaevolve-compatible report payload."
  ([loop-result] (report-payload loop-result {}))
  ([loop-result {:keys [leaderboard-size]}]
   (let [cfg (:config loop-result)
         ranked (:ranked loop-result)
         lb (leaderboard ranked (or leaderboard-size default-leaderboard-size))
         runs-detail (mapv run-detail-entry (:results loop-result))
         top-k (max 1 (long (or leaderboard-size default-leaderboard-size)))]
     (payload/report-payload
      {:meta {:seed (:seed cfg)
              :runs (:runs cfg)
              :length (:length cfg)
              :generations (:generations cfg)
              :engine :tensor
              :closed-loop true
              :strict-parity (:strict-parity cfg)
              :step-opts (:step-opts cfg)}
       :ranked ranked
       :learned-specs {}
       :leaderboard lb
       :runs-detail runs-detail
       :top-runs (mapv #(select-keys % [:summary :rule :seed :policy :run-result])
                       (take top-k ranked))}))))

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
      unknown (do
                (println "Unknown option:" unknown)
                (println)
                (println (usage)))
      :else
      (let [result (run-tensor-closed-loop opts)
            leaderboard-size (or leaderboard-size default-leaderboard-size)]
        (println (str "Runs: " (get-in result [:summary :runs])
                      " | parity: " (format "%.2f" (* 100.0 (or (get-in result [:summary :parity-rate]) 0.0))) "%"
                      " | history parity: " (format "%.2f" (* 100.0 (or (get-in result [:summary :history-parity-rate]) 0.0))) "%"))
        (when out
          (println "Wrote:" (write-edn! out result)))
        (when report
          (println "Wrote report:" (write-edn! report (report-payload result {:leaderboard-size leaderboard-size}))))
        (when feedback
          (println "Wrote feedback:" (write-edn! feedback (feedback-payload result {:leaderboard-size leaderboard-size}))))
        (when-not out
          (println (pr-str {:summary (:summary result)})))))))
