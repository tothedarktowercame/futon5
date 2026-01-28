#!/usr/bin/env bb
(ns ct-candidate-score
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.runtime :as mmca]))

(defn- usage []
  (str/join
   "\n"
   ["Score CT candidates with CA envelope scores."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/ct_candidate_score.clj --candidates PATH [options]"
    ""
    "Options:"
    "  --candidates PATH      EDN with :candidates vector (required)."
    "  --out PATH             Output EDN (default /tmp/ct-candidate-scores.edn)."
    "  --seeds CSV            Comma-separated seeds (default 4242,1111,2222)."
    "  --length N             Genotype/phenotype length (default 100)."
    "  --generations N        Generations per run (default 100)."
    "  --entropy-center P     Envelope entropy center (default 0.6)."
    "  --entropy-width P      Envelope entropy width (default 0.25)."
    "  --change-center P      Envelope change center (default 0.2)."
    "  --change-width P       Envelope change width (default 0.15)."
    "  --no-change            Ignore avg-change in envelope score."
    "  --help                 Show this message."]))

(defn- parse-double [s]
  (try (Double/parseDouble s) (catch Exception _ nil)))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-seeds [s]
  (->> (str/split (or s "") #",")
       (map str/trim)
       (remove str/blank?)
       (map parse-int)
       (remove nil?)
       vec))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (# {"--help" "-h"} flag) (recur more (assoc opts :help true))
          (= "--candidates" flag) (recur (rest more) (assoc opts :candidates (first more)))
          (= "--out" flag) (recur (rest more) (assoc opts :out (first more)))
          (= "--seeds" flag) (recur (rest more) (assoc opts :seeds (parse-seeds (first more))))
          (= "--length" flag) (recur (rest more) (assoc opts :length (parse-int (first more))))
          (= "--generations" flag) (recur (rest more) (assoc opts :generations (parse-int (first more))))
          (= "--entropy-center" flag) (recur (rest more) (assoc opts :entropy-center (parse-double (first more))))
          (= "--entropy-width" flag) (recur (rest more) (assoc opts :entropy-width (parse-double (first more))))
          (= "--change-center" flag) (recur (rest more) (assoc opts :change-center (parse-double (first more))))
          (= "--change-width" flag) (recur (rest more) (assoc opts :change-width (parse-double (first more))))
          (= "--no-change" flag) (recur more (assoc opts :include-change? false))
          :else (recur more (assoc opts :unknown flag))))
      opts)))

(defn- band-score [x center width]
  (let [x (double (or x 0.0))
        center (double (or center 0.0))
        width (double (or width 1.0))]
    (if (pos? width)
      (max 0.0 (- 1.0 (/ (Math/abs (- x center)) width)))
      0.0)))

(defn- envelope-score
  [summary {:keys [entropy-center entropy-width change-center change-width include-change?]}]
  (let [entropy (band-score (:avg-entropy-n summary) entropy-center entropy-width)
        change (band-score (:avg-change summary) change-center change-width)
        parts (if include-change? [entropy change] [entropy])
        avg (if (seq parts) (/ (reduce + 0.0 parts) (double (count parts))) 0.0)]
    (* 100.0 avg)))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(defn- rng-phenotype-string [^java.util.Random rng length]
  (apply str (repeatedly length #(rng-int rng 2))))

(defn- template-map [ct-template]
  (cond
    (and (seq? ct-template) (= 'category (first ct-template))) (second ct-template)
    (map? ct-template) ct-template
    :else nil))

(defn- wrap-template [m]
  (when (map? m)
    (list 'category m)))

(defn- merge-maps
  [a b]
  (-> (merge a b)
      (update :objects (fn [o] (vec (distinct (concat (or o []) (or (:objects b) []))))))))

(defn- hybrid-section-weighted
  [a b {:keys [weight-a] :or {weight-a 0.6}}]
  (let [ma (template-map a)
        mb (template-map b)
        morphs (merge (:morphisms mb) (:morphisms ma))
        merged (-> ma
                   (merge mb)
                   (assoc :morphisms morphs)
                   (update :objects (fn [o] (vec (distinct (concat (or o []) (or (:objects mb) []))))))
                   (assoc :notes {:hybrid :section-weighted :weight-a weight-a}))]
    (wrap-template merged)))

(defn- hybrid-trigram-split
  [upper lower]
  (let [mu (template-map upper)
        ml (template-map lower)
        rename-keys (fn [prefix m]
                      (into {} (map (fn [[k v]] [(keyword (str (name prefix) "-" (name k))) v]) m)))
        morphs (merge (rename-keys :upper (:morphisms mu))
                      (rename-keys :lower (:morphisms ml)))
        objs (vec (distinct (concat (or (:objects mu) []) (or (:objects ml) []))))
        merged {:name (or (:name mu) (:name ml))
                :objects objs
                :morphisms morphs
                :compose {}
                :notes {:hybrid :trigram-split}}]
    (wrap-template merged)))

(defn- resolve-ct-template
  [candidate id->template]
  (if-let [ct (:ct-template candidate)]
    ct
    (when-let [hybrid (:hybrid candidate)]
      (case (:type hybrid)
        :section-weighted (hybrid-section-weighted (get id->template (:a hybrid))
                                                   (get id->template (:b hybrid))
                                                   hybrid)
        :trigram-split (hybrid-trigram-split (get id->template (:upper hybrid))
                                             (get id->template (:lower hybrid)))
        nil))))

(defn- apply-exotype-overrides
  [exotype {:keys [update-prob match-threshold]}]
  (let [params (cond-> (:params exotype)
                 (some? update-prob) (assoc :update-prob update-prob)
                 (some? match-threshold) (assoc :match-threshold match-threshold))]
    (assoc exotype :params params)))

(defn- score-candidate
  [candidate seeds length generations envelope-opts]
  (let [exotype (-> (select-keys candidate [:sigil :tier])
                    exotype/resolve-exotype
                    (apply-exotype-overrides (:exotype-override candidate)))]
    (reduce (fn [acc seed]
              (let [rng (java.util.Random. (long seed))
                    genotype (rng-sigil-string rng length)
                    phenotype (rng-phenotype-string rng length)
                    result (mmca/run-mmca {:genotype genotype
                                           :phenotype phenotype
                                           :generations generations
                                           :kernel :mutating-template
                                           :operators []
                                           :exotype exotype
                                           :seed seed})
                    summary (metrics/summarize-run result)
                    score (envelope-score summary envelope-opts)]
                (-> acc
                    (update :per-seed assoc seed score)
                    (update :scores conj score))))
            {:per-seed {} :scores []}
            seeds)))

(defn -main [& args]
  (let [{:keys [help unknown candidates out seeds length generations
                entropy-center entropy-width change-center change-width include-change?]}
        (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println (usage)))
      (nil? candidates) (do (println "Missing --candidates PATH") (println (usage)))
      :else
      (let [data (edn/read-string (slurp candidates))
            candidates (vec (:candidates data))
            seeds (or (seq seeds) [4242 1111 2222])
            length (long (or length 100))
            generations (long (or generations 100))
            envelope-opts {:entropy-center (or entropy-center 0.6)
                           :entropy-width (or entropy-width 0.25)
                           :change-center (or change-center 0.2)
                           :change-width (or change-width 0.15)
                           :include-change? (if (contains? #{true false} include-change?) include-change? true)}
            id->template (into {} (keep (fn [c]
                                          (when-let [ct (:ct-template c)]
                                            [(:id c) ct]))
                                        candidates))
            scored (mapv (fn [c]
                           (let [ct (resolve-ct-template c id->template)
                                 id (:id c)
                                 stats (score-candidate c seeds length generations envelope-opts)
                                 scores (:scores stats)
                                 mean (if (seq scores) (/ (reduce + 0.0 scores) (double (count scores))) 0.0)
                                 best (if (seq scores) (apply max scores) 0.0)]
                             (assoc c
                                    :ct-template ct
                                    :envelope {:mean mean
                                               :best best
                                               :per-seed (:per-seed stats)})))
                         candidates)
            out (or out "/tmp/ct-candidate-scores.edn")
            payload {:meta {:seeds (vec seeds)
                            :length length
                            :generations generations
                            :envelope envelope-opts}
                     :candidates scored}]
        (spit out (pr-str payload))
        (println "Wrote" out)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
