(ns futon5.drivers.mission04-06
  "Statistical replay of Mission 4 (unguarded exotype evolution) against
   Mission 6 (the same evolution with a slow xenotype guard)."
  (:gen-class)
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.mmca.exoevolve :as exoevolve]))

(def default-out-dir "data/mission-04-06-runs")
(def default-xeno-spec "data/mission-04-06-xeno.edn")
(def default-seed-start 42)
(def default-num-seeds 30)
(def default-runs 100)
(def default-length 30)
(def default-generations 10)
(def default-pop 16)
(def default-update-every 100)
(def default-xeno-weight 0.5)

(def dead-change 0.05)
(def dead-entropy 0.2)
(def confetti-change 0.45)
(def confetti-entropy 0.8)

(defn mean [xs]
  (when (seq xs)
    (/ (reduce + 0.0 xs) (double (count xs)))))

(defn ci95 [xs]
  (let [xs (vec xs)
        n (count xs)]
    (when (> n 1)
      (let [m (mean xs)
            variance (/ (reduce + 0.0
                                (map (fn [x]
                                       (let [d (- (double x) m)]
                                         (* d d)))
                                     xs))
                        (double (dec n)))]
        (* 1.96 (Math/sqrt (/ variance n)))))))

(defn exotype-id [exotype]
  (select-keys exotype [:sigil :tier]))

(defn dead-run? [entry]
  (let [summary (:summary entry)]
    (and (<= (double (or (:avg-change summary) 0.0)) dead-change)
         (<= (double (or (:avg-entropy-n summary) 0.0)) dead-entropy))))

(defn confetti-run? [entry]
  (let [summary (:summary entry)]
    (and (>= (double (or (:avg-change summary) 0.0)) confetti-change)
         (>= (double (or (:avg-entropy-n summary) 0.0)) confetti-entropy))))

(defn read-run-entries [path]
  (->> (str/split-lines (slurp path))
       (remove str/blank?)
       (map edn/read-string)
       (filter #(= :run (:event %)))
       vec))

(defn survivor-behaviors
  "Describe the selected half of evolve-exotypes' final population using only
   evaluations from the final selection window. Duplicate survivors remain
   duplicated: the metric describes the surviving distribution, not merely its
   distinct identities. Throws rather than inventing behavior for an unevaluated
   survivor."
  [population entries pop update-every]
  (let [survivors (vec (take (max 1 (quot pop 2)) population))
        final-window (vec (take-last update-every entries))
        by-id (group-by (comp exotype-id :exotype) final-window)]
    (mapv (fn [survivor]
            (let [id (exotype-id survivor)
                  evaluations (vec (get by-id id))]
              (when-not (seq evaluations)
                (throw (ex-info "Selected survivor has no final-window evidence"
                                {:survivor id
                                 :final-window-size (count final-window)})))
              {:exotype id
               :evaluations (count evaluations)
               :confetti-rate (mean (map #(if (confetti-run? %) 1.0 0.0)
                                         evaluations))
               :dead-rate (mean (map #(if (dead-run? %) 1.0 0.0)
                                     evaluations))
               :mean-change (mean (map #(get-in % [:summary :avg-change])
                                       evaluations))
               :mean-entropy-n (mean (map #(get-in % [:summary :avg-entropy-n])
                                          evaluations))
               :mean-short-score (mean (map #(get-in % [:score :short])
                                            evaluations))
               :mean-xeno-score (mean (keep #(get-in % [:score :xeno])
                                            evaluations))}))
          survivors)))

(defn run-metrics [behaviors]
  (let [n (count behaviors)
        frequencies (frequencies (map :exotype behaviors))]
    {:selected-survivors n
     :survivor-confetti-rate (mean (map :confetti-rate behaviors))
     :survivor-dead-rate (mean (map :dead-rate behaviors))
     :survivor-distinct-identities (count frequencies)
     :survivor-identity-diversity (when (pos? n)
                                    (/ (count frequencies) (double n)))
     :survivor-max-identity-share (when (pos? n)
                                    (/ (apply max (vals frequencies)) (double n)))
     :survivor-mean-change (mean (map :mean-change behaviors))
     :survivor-mean-entropy-n (mean (map :mean-entropy-n behaviors))}))

(defn arm-options [opts arm seed log-path]
  (cond-> {:runs (:runs opts)
           :length (:length opts)
           :generations (:generations opts)
           :pop (:pop opts)
           :update-every (:update-every opts)
           :tier :both
           :seed seed
           :log log-path
           :iiching-manifest "resources/exotype-program-manifest.edn"
           :on-error :fail}
    (= arm :guarded)
    (assoc :xeno-spec (:xeno-spec opts)
           :xeno-weight (:xeno-weight opts))))

(defn run-arm [opts arm seed]
  (let [tmp (java.io.File/createTempFile
             (str "mission-04-06-" (name arm) "-" seed "-") ".edn")]
    (try
      (let [result (exoevolve/evolve-exotypes
                    (arm-options opts arm seed (.getPath tmp)))
            entries (read-run-entries tmp)
            behaviors (survivor-behaviors (:population result) entries
                                          (:pop opts) (:update-every opts))]
        {:schema/version 1
         :experiment/id :mission-04-06-evaluator-guard
         :status :complete
         :replay :statistical
         :arm arm
         :seed seed
         :protocol (select-keys opts [:runs :length :generations :pop
                                     :update-every :xeno-weight])
         :xeno-spec (when (= arm :guarded) (edn/read-string (slurp (:xeno-spec opts))))
         :selected-survivors behaviors
         :metrics (run-metrics behaviors)})
      (finally
        (.delete tmp)))))

(defn artifact-path [out-dir arm seed]
  (io/file out-dir (str (name arm) "-seed-" seed ".edn")))

(defn complete-artifact? [path]
  (when (.exists path)
    (try
      (= :complete (:status (edn/read-string (slurp path))))
      (catch Exception _ false))))

(defn parse-long* [s]
  (Long/parseLong s))

(defn parse-double* [s]
  (Double/parseDouble s))

(defn parse-args [args]
  (loop [opts {:out-dir default-out-dir
               :xeno-spec default-xeno-spec
               :seed-start default-seed-start
               :num-seeds default-num-seeds
               :runs default-runs
               :length default-length
               :generations default-generations
               :pop default-pop
               :update-every default-update-every
               :xeno-weight default-xeno-weight
               :force? false}
         args args]
    (if-let [flag (first args)]
      (case flag
        "--out-dir" (recur (assoc opts :out-dir (second args)) (nnext args))
        "--xeno-spec" (recur (assoc opts :xeno-spec (second args)) (nnext args))
        "--seed-start" (recur (assoc opts :seed-start (parse-long* (second args))) (nnext args))
        "--num-seeds" (recur (assoc opts :num-seeds (parse-long* (second args))) (nnext args))
        "--runs" (recur (assoc opts :runs (parse-long* (second args))) (nnext args))
        "--length" (recur (assoc opts :length (parse-long* (second args))) (nnext args))
        "--generations" (recur (assoc opts :generations (parse-long* (second args))) (nnext args))
        "--pop" (recur (assoc opts :pop (parse-long* (second args))) (nnext args))
        "--update-every" (recur (assoc opts :update-every (parse-long* (second args))) (nnext args))
        "--xeno-weight" (recur (assoc opts :xeno-weight (parse-double* (second args))) (nnext args))
        "--force" (recur (assoc opts :force? true) (next args))
        (throw (ex-info "Unknown option" {:option flag})))
      opts)))

(def headline-metrics
  [:survivor-confetti-rate :survivor-dead-rate :survivor-identity-diversity
   :survivor-max-identity-share :survivor-mean-change :survivor-mean-entropy-n])

(defn summarize-artifacts [opts]
  (into {}
        (for [arm [:unguarded :guarded]
              :let [runs (mapv (fn [seed]
                                 (edn/read-string
                                  (slurp (artifact-path (:out-dir opts) arm seed))))
                               (range (:seed-start opts)
                                      (+ (:seed-start opts) (:num-seeds opts))))]]
          [arm
           {:n (count runs)
            :metrics
            (into {}
                  (for [metric headline-metrics
                        :let [values (mapv #(get-in % [:metrics metric]) runs)]]
                    [metric {:mean (mean values)
                             :ci95 (ci95 values)}]))}])))

(defn -main [& args]
  (let [opts (parse-args args)
        out-dir (io/file (:out-dir opts))]
    (when (or (odd? (:pop opts))
              (not (zero? (mod (:runs opts) (:update-every opts)))))
      (throw (ex-info "Protocol requires even pop and complete final selection window"
                      (select-keys opts [:runs :pop :update-every]))))
    (when-not (.exists (io/file (:xeno-spec opts)))
      (throw (ex-info "Xenotype spec does not exist" {:path (:xeno-spec opts)})))
    (.mkdirs out-dir)
    (doseq [arm [:unguarded :guarded]
            seed (range (:seed-start opts)
                        (+ (:seed-start opts) (:num-seeds opts)))]
      (let [path (artifact-path out-dir arm seed)]
        (if (and (not (:force? opts)) (complete-artifact? path))
          (println arm seed "SKIPPED")
          (do
            (println arm seed "RUNNING")
            (spit path (pr-str (run-arm opts arm seed)))
            (println arm seed "DONE")))))
    (prn (summarize-artifacts opts))))
