(ns futon5.mmca.metrics-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [futon5.mmca.metrics :as metrics]))

(def ^:private log-files
  ["resources/exotic-mode-demo.log"
   "resources/mission-16-pilot1-baseline.log"
   "resources/mission-16-pilot1-exotic.log"])

(def ^:private compare-log "resources/mission-17a-refine-r50.log")

(defn- read-run-summaries [path]
  (when (.exists (io/file path))
    (with-open [r (io/reader path)]
      (->> (line-seq r)
           (keep (fn [line]
                   (try
                     (let [entry (edn/read-string line)]
                       (:summary entry))
                     (catch Exception _ nil))))
           (remove nil?)
           vec))))

(defn- load-summary-samples []
  (->> log-files
       (mapcat read-run-summaries)
       (remove nil?)
       vec))

(defn- quantile [values q]
  (let [sorted (vec (sort values))
        n (count sorted)]
    (when (pos? n)
      (let [idx (int (Math/floor (* q (dec n))))]
        (nth sorted (max 0 (min (dec n) idx)))))))

(defn- samples->features [summaries]
  (->> summaries
       (keep (fn [summary]
               (let [pressure (:avg-change summary)
                     selectivity (when-let [unique (:avg-unique summary)]
                                   (- 1.0 unique))
                     structure (:temporal-autocorr summary)]
                 (when (and pressure selectivity structure)
                   {:pressure pressure
                    :selectivity selectivity
                    :structure structure}))))
       vec))

(defn- summary->metrics-history
  "Build a synthetic metrics-history window from a run summary."
  [{:keys [avg-change avg-unique avg-entropy-n temporal-autocorr]}]
  (let [length 32
        unique-sigils (max 1 (int (Math/round (* (double (or avg-unique 0.0)) length))))
        max-entropy (/ (Math/log length) (Math/log 2.0))
        entropy (* (double (or avg-entropy-n 0.0)) max-entropy)]
    (vec (repeat 10 {:change-rate avg-change
                     :unique-sigils unique-sigils
                     :length length
                     :entropy entropy
                     :temporal-autocorr temporal-autocorr}))))

(defn- synthetic-metrics-history [n change-rate unique-sigils length]
  (mapv (fn [_]
          {:change-rate change-rate
           :unique-sigils unique-sigils
           :length length})
        (range n)))

(defn- alternating-history [n s1 s2]
  (mapv (fn [idx]
          (if (even? idx) s1 s2))
        (range n)))

(deftest qian-like-synthetic-calibration
  (let [summaries (load-summary-samples)
        features (samples->features summaries)]
    (is (seq features) "Expected summary samples from logs")
    (let [pressures (map :pressure features)
          selectivities (map :selectivity features)
          p80 (quantile pressures 0.8)
          s20 (quantile selectivities 0.2)
          s1 "abcdefghijklmnopqrstuvwxyz012345"
          s2 "543210zyxwvutsrqponmlkjihgfedcba"
          metrics-history (synthetic-metrics-history 20 0.9 32 32)
          gen-history (alternating-history 20 s1 s2)
          summary (metrics/episode-summary {:metrics-history metrics-history
                                            :gen-history gen-history})]
      (is (>= (:pressure-avg summary) (or p80 0.0)))
      (is (<= (:selectivity-avg summary) (or s20 1.0)))
      (is (= :magma (:regime summary)))
      (is (some #{:pressure-up} (apply concat (:macro-trace summary))))
      (is (some #{:selectivity-down} (apply concat (:macro-trace summary)))))))

(deftest freeze-gate-calibration
  (let [s "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        metrics-history (synthetic-metrics-history 20 0.02 1 32)
        gen-history (vec (repeat 20 s))
        summary (metrics/episode-summary {:metrics-history metrics-history
                                          :gen-history gen-history})]
    (is (= :freeze (:regime summary)))))

(deftest windowed-freeze-anchor
  (let [metrics-history (synthetic-metrics-history 30 0.02 1 32)
        windows (metrics/windowed-macro-features metrics-history {:W 10 :S 5})
        regimes (map :regime windows)
        freeze-count (count (filter #{:freeze} regimes))]
    (is (>= freeze-count (Math/ceil (* 0.6 (count regimes)))))))

(deftest windowed-magma-anchor
  (let [metrics-history (mapv (fn [_]
                                {:change-rate 0.9
                                 :unique-sigils 32
                                 :length 32
                                 :temporal-autocorr 0.1})
                              (range 30))
        windows (metrics/windowed-macro-features metrics-history {:W 10 :S 5})
        regimes (map :regime windows)
        magma-count (count (filter #{:magma} regimes))]
    (is (>= magma-count (Math/ceil (* 0.6 (count regimes)))))))

(deftest windowed-non-degenerate-anchor
  (let [summaries (read-run-summaries compare-log)
        histories (map summary->metrics-history summaries)
        regimes (->> histories
                     (mapcat #(metrics/windowed-macro-features % {:W 10 :S 10}))
                     (map :regime)
                     (remove nil?))
        classes (set regimes)]
    (is (seq summaries) "Expected mission-17a compare summaries")
    (is (> (count classes) 2))))

(deftest windowed-normalization-contract
  (let [summaries (read-run-summaries compare-log)
        histories (map summary->metrics-history summaries)
        windows (mapcat #(metrics/windowed-macro-features % {:W 10 :S 10}) histories)
        ok? (fn [v] (or (nil? v) (and (number? v) (<= 0.0 v 1.0) (not (Double/isNaN (double v))))))]
    (is (seq summaries) "Expected mission-17a compare summaries")
    (is (every? #(ok? (:pressure %)) windows))
    (is (every? #(ok? (:selectivity %)) windows))
    (is (every? #(ok? (:structure %)) windows))
    (is (every? #(ok? (:activity %)) windows))))
