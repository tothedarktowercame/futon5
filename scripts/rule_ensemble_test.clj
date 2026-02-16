#!/usr/bin/env clj -M
;; Test ensemble of elementary CA rules as MMCA wirings
;;
;; Usage: clj -M -e '(load-file "scripts/rule_ensemble_test.clj")'

(ns rule-ensemble-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [futon5.mmca.runtime :as mmca]
            [futon5.mmca.exotype :as exotype]
            [futon5.ca.core :as ca]))

(defn random-genotype [len seed]
  (let [rng (java.util.Random. seed)
        sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly len #(nth sigils (.nextInt rng (count sigils)))))))

(defn load-wiring [path]
  (edn/read-string (slurp path)))

(defn run-rule [wiring-path seed]
  (let [wiring (load-wiring wiring-path)
        rule-id (get-in wiring [:meta :id])
        genotype (random-genotype 100 seed)
        phenotype (apply str (repeat 100 "0"))]
    (println "  Running" rule-id "...")
    (try
      (let [run (mmca/run-mmca {:genotype genotype
                                :phenotype phenotype
                                :generations 100
                                :seed seed
                                :wiring wiring})
            gen-history (:gen-history run)
            phe-history (:phe-history run)]

        ;; Basic metrics
        (let [final-gen (last gen-history)
              first-gen (first gen-history)

              ;; Change rate between consecutive generations
              change-rates (for [i (range (dec (count gen-history)))]
                             (let [g1 (nth gen-history i)
                                   g2 (nth gen-history (inc i))
                                   diffs (count (filter identity
                                                        (map not= (seq g1) (seq g2))))]
                               (/ diffs (count g1))))
              avg-change (/ (reduce + change-rates) (count change-rates))

              ;; Frozen cells (same as initial)
              frozen-count (count (filter identity
                                          (map = (seq first-gen) (seq final-gen))))
              frozen-ratio (/ frozen-count (count first-gen))

              ;; Unique sigils in final generation
              unique-final (count (set (seq final-gen)))

              ;; Sample hexagrams for behavioral signature
              hex-rng (java.util.Random. 42)
              hex-samples (for [_ (range 100)
                                :let [ctx (exotype/sample-context gen-history phe-history hex-rng)]
                                :when ctx
                                :let [physics (exotype/context->physics-family ctx)]
                                :when physics]
                            (:hexagram-id physics))
              hex-freqs (frequencies hex-samples)
              hex-mode (when (seq hex-freqs) (key (apply max-key val hex-freqs)))
              hex-unique (count hex-freqs)
              ;; Check for notable hexagrams
              hex-63 (get hex-freqs 63 0)
              hex-64 (get hex-freqs 64 0)
              hex-11 (get hex-freqs 11 0)]

          {:rule-id rule-id
           :path wiring-path
           :seed seed
           :generations (count gen-history)
           :width (count first-gen)
           :avg-change-rate (double avg-change)
           :frozen-ratio (double frozen-ratio)
           :unique-sigils-final unique-final
           :hex-mode hex-mode
           :hex-unique hex-unique
           :hex-63 hex-63
           :hex-64 hex-64
           :hex-11 hex-11
           :final-gen-sample (subs (str final-gen) 0 (min 30 (count final-gen)))}))
      (catch Exception e
        {:rule-id rule-id
         :path wiring-path
         :seed seed
         :error (.getMessage e)}))))

(defn run-ensemble []
  (let [rule-files (sort (.listFiles (io/file "data/wiring-rules")))
        seeds [42 352362012 238310129]
        results (for [f rule-files
                      :when (.endsWith (.getName f) ".edn")
                      seed seeds]
                  (run-rule (.getPath f) seed))]

    (println "\n=== Rule Ensemble Results ===\n")
    (println (format "%-12s %-12s %7s %6s %5s %5s %5s %5s"
                     "Rule" "Seed" "Change" "Uniq" "HexM" "H-63" "H-64" "H-11"))
    (println (apply str (repeat 75 "-")))

    (doseq [r (sort-by (juxt :rule-id :seed) results)]
      (if (:error r)
        (println (format "%-12s %-12s ERROR: %s"
                         (:rule-id r) (:seed r) (:error r)))
        (println (format "%-12s %-12d %7.3f %6d %5s %5d %5d %5d"
                         (:rule-id r)
                         (:seed r)
                         (:avg-change-rate r)
                         (:unique-sigils-final r)
                         (str "#" (:hex-mode r))
                         (:hex-63 r)
                         (:hex-64 r)
                         (:hex-11 r)))))

    ;; Summary by rule
    (println "\n=== Summary by Rule ===\n")
    (let [by-rule (group-by :rule-id (remove :error results))]
      (doseq [[rule-id runs] (sort-by first by-rule)]
        (let [avg-change (double (/ (reduce + (map :avg-change-rate runs)) (count runs)))
              avg-unique (double (/ (reduce + (map :unique-sigils-final runs)) (count runs)))
              total-63 (reduce + (map :hex-63 runs))
              total-64 (reduce + (map :hex-64 runs))
              total-11 (reduce + (map :hex-11 runs))
              hex-modes (frequencies (map :hex-mode runs))]
          (println (format "%-12s  change=%.3f  unique=%.1f  hex-63/64/11=%d/%d/%d  modes=%s"
                           rule-id avg-change avg-unique total-63 total-64 total-11
                           (pr-str hex-modes))))))

    results))

(run-ensemble)
