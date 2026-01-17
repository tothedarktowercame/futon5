(ns futon5.arrow.mine
  "Minimal arrow mining loop for Mission 9.5 MVP."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.runtime :as mmca]
            [futon5.mmca.exotype :as exotype]
            [futon5.arrow.term :as term]
            [futon5.arrow.normalize :as norm]))

(def ^:private default-length 32)
(def ^:private default-generations 32)
(def ^:private default-k 2)
(def ^:private default-tau 0.7)
(def ^:private default-contexts 20)

(defn- avg [xs]
  (when (seq xs)
    (/ (reduce + 0.0 xs) (double (count xs)))))

(defn- stddev [xs]
  (let [xs (seq xs)]
    (when xs
      (let [mean (avg xs)]
        (Math/sqrt
         (/ (reduce + 0.0 (map (fn [x] (let [d (- (double x) mean)] (* d d))) xs))
            (double (count xs))))))))

(defn- usage []
  (str/join
   "\n"
   ["Arrow discovery (M9.5 MVP)"
    ""
    "Usage:"
    "  bb -cp src:resources -m futon5.arrow.mine [options]"
    ""
    "Options:"
    "  --contexts N     Seeds per test (default 20)."
    "  --k N            Max term cost (default 2)."
    "  --tau X          Robustness threshold (default 0.7)."
    "  --out PATH       Output EDN log (default resources/arrow-pilot.edn)."
    "  --log-tests PATH Append per-term test summaries (optional)."
    "  --min-delta X    Minimum effect size to accept arrow (default 0.0)."
    "  --seed N         RNG seed (default 4242)."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-double [s]
  (try (Double/parseDouble s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--contexts" flag)
          (recur (rest more) (assoc opts :contexts (parse-int (first more))))

          (= "--k" flag)
          (recur (rest more) (assoc opts :k (parse-int (first more))))

          (= "--tau" flag)
          (recur (rest more) (assoc opts :tau (parse-double (first more))))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          (= "--log-tests" flag)
          (recur (rest more) (assoc opts :log-tests (first more)))

          (= "--min-delta" flag)
          (recur (rest more) (assoc opts :min-delta (parse-double (first more))))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

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

(defn- run-config [^java.util.Random rng]
  {:genotype (rng-sigil-string rng default-length)
   :phenotype (rng-phenotype-string rng default-length)
   :generations default-generations
   :kernel :mutating-template
   :operators []})

(defn- run-with-term [base term seed]
  (let [cfg (assoc base :seed seed)
        cfg (term/apply-term cfg term)
        cfg (update cfg :exotype exotype/resolve-exotype)]
    (mmca/run-mmca cfg)))

(defn- normalize-regime [result]
  (let [descriptor (norm/normalize result)]
    (assoc descriptor :sig (norm/regime-sig descriptor))))

(defn- delay-step [summary generations]
  (let [step (:first-stasis-step summary)]
    (double (or step generations))))

(defn test-arrow
  "Test a term from a starting regime using a context set."
  [base baseline-regimes term contexts generations]
  (let [regimes (mapv (fn [seed]
                        (normalize-regime (run-with-term base term seed)))
                      contexts)
        counts (frequencies (map :sig regimes))
        [top-sig top-count] (first (sort-by val > counts))
        total (count regimes)
        prob (if (pos? total) (/ top-count (double total)) 0.0)
        top (first (filter #(= (:sig %) top-sig) regimes))
        baseline-macro (mapv :macro-vec baseline-regimes)
        target-macro (mapv :macro-vec regimes)
        mean-baseline (when (seq baseline-macro)
                        (mapv avg (apply map vector baseline-macro)))
        mean-target (when (seq target-macro)
                      (mapv avg (apply map vector target-macro)))
        delta (when (and mean-baseline mean-target)
                (norm/macro-distance mean-baseline mean-target))
        delays (mapv (fn [r]
                       (delay-step (:summary r) generations))
                     regimes)]
    {:to top
     :robustness {:tau prob
                  :contexts total}
     :effect {:delta delta
              :delay-mean (avg delays)
              :delay-std (stddev delays)}
     :evidence {:seed-set contexts
                :regime-sigs (mapv :sig regimes)
                :word-classes (mapv :word-class regimes)}}))

(defn mine-arrows
  [{:keys [contexts k tau seed log-tests min-delta]}]
  (let [rng (java.util.Random. (long seed))
        base (run-config rng)
        sigils (mapv :sigil (ca/sigil-entries))
        candidates (term/generate-candidates {:sigils (take 6 sigils)} k)
        seeds (vec (repeatedly contexts #(rng-int rng Integer/MAX_VALUE)))
        baseline-regimes (mapv (fn [seed]
                                 (normalize-regime (run-with-term base [:noop] seed)))
                               seeds)
        from-sig (->> baseline-regimes (map :sig) frequencies (sort-by val >) ffirst)
        from (first (filter #(= (:sig %) from-sig) baseline-regimes))
        from-node {:regime (:word-class from)
                   :sig (:sig from)}]
    (loop [remaining candidates
           arrows []]
      (if (empty? remaining)
        arrows
        (let [term (first remaining)
              test (test-arrow base baseline-regimes term seeds default-generations)
              entry {:from from-node
                     :witness {:term (term/canonical-term term)
                               :cost (term/cost term {})}
                     :to (:to test)
                     :robustness (:robustness test)
                     :effect (:effect test)
                     :evidence (:evidence test)}
              delta (get-in test [:effect :delta])
              accept? (and (>= (get-in test [:robustness :tau] 0.0) tau)
                           (not= from-sig (get-in test [:to :sig]))
                           (or (nil? min-delta)
                               (and (number? delta) (>= delta (double min-delta)))))
              _ (when log-tests
                  (spit log-tests (str (pr-str entry) "\n") :append true))
              arrows' (if accept?
                        (conj arrows entry)
                        arrows)]
          (recur (rest remaining) arrows'))))))

(defn -main [& args]
  (let [{:keys [help unknown] :as opts} (parse-args args)]
    (cond
      help
      (println (usage))

      unknown
      (do
        (println "Unknown option:" unknown)
        (println)
        (println (usage)))

      :else
      (let [contexts (or (:contexts opts) default-contexts)
            k (or (:k opts) default-k)
            tau (or (:tau opts) default-tau)
            seed (or (:seed opts) 4242)
            out (or (:out opts) "resources/arrow-pilot.edn")
            log-tests (:log-tests opts)
            min-delta (or (:min-delta opts) 0.0)
            arrows (mine-arrows {:contexts contexts
                                 :k k
                                 :tau tau
                                 :seed seed
                                 :log-tests log-tests
                                 :min-delta min-delta})
            payload {:event :arrow-pilot
                     :config {:contexts contexts :k k :tau tau :seed seed
                              :log-tests log-tests
                              :min-delta min-delta}
                     :count (count arrows)
                     :arrows arrows}]
        (spit out (pr-str payload))
        (println "Wrote" out "with" (count arrows) "arrows")))))
