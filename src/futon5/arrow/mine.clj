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

(defn test-arrow
  "Test a term from a starting regime using a context set."
  [base term contexts tau]
  (let [regimes (mapv (fn [seed]
                        (normalize-regime (run-with-term base term seed)))
                      contexts)
        counts (frequencies (map :sig regimes))
        [top-sig top-count] (first (sort-by val > counts))
        total (count regimes)
        prob (if (pos? total) (/ top-count (double total)) 0.0)
        top (first (filter #(= (:sig %) top-sig) regimes))]
    (when (>= prob tau)
      {:to top
       :robustness {:tau prob
                    :contexts total}
       :evidence {:seed-set contexts
                  :regimes (mapv :sig regimes)}})))

(defn mine-arrows
  [{:keys [contexts k tau seed]}]
  (let [rng (java.util.Random. (long seed))
        base (run-config rng)
        sigils (mapv :sigil (ca/sigil-entries))
        candidates (term/generate-candidates {:sigils (take 6 sigils)} k)
        seeds (vec (repeatedly contexts #(rng-int rng Integer/MAX_VALUE)))]
    (loop [remaining candidates
           arrows []]
      (if (empty? remaining)
        arrows
        (let [term (first remaining)
              test (test-arrow base term seeds tau)
              arrows' (if test
                        (conj arrows {:from {:regime :unknown
                                             :sig :unknown}
                                      :witness {:term term
                                                :cost (term/cost term {})}
                                      :to (:to test)
                                      :robustness (:robustness test)
                                      :evidence (:evidence test)})
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
            arrows (mine-arrows {:contexts contexts :k k :tau tau :seed seed})
            payload {:event :arrow-pilot
                     :config {:contexts contexts :k k :tau tau :seed seed}
                     :count (count arrows)
                     :arrows arrows}]
        (spit out (pr-str payload))
        (println "Wrote" out "with" (count arrows) "arrows")))))
