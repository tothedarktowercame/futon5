#!/usr/bin/env clj -M
;; Run L5-creative and sample hexagrams from the result
;;
;; Usage: clj -M scripts/run_and_sample_hexagrams.clj

(ns run-and-sample-hexagrams
  (:require [clojure.edn :as edn]
            [futon5.mmca.runtime :as mmca]
            [futon5.mmca.exotype :as exotype]
            [futon5.hexagram.lift :as hex-lift]
            [futon5.ca.core :as ca]))

(defn random-genotype [len seed]
  (let [rng (java.util.Random. seed)
        sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly len #(nth sigils (.nextInt rng (count sigils)))))))

(defn sample-hexagrams-from-run [run num-samples sample-seed]
  (let [history (:genotype-history run)
        phe-history (:phenotype-history run)
        rng (java.util.Random. sample-seed)]
    (for [_ (range num-samples)
          :let [ctx (exotype/sample-context history phe-history rng)]
          :when ctx]
      (let [physics (exotype/context->physics-family ctx)]
        {:hexagram-id (:hexagram-id physics)
         :hexagram-name (:hexagram-name physics)
         :coord (:coord ctx)}))))

(defn hexagram-distribution [samples]
  (let [ids (keep :hexagram-id samples)
        freqs (frequencies ids)
        total (count ids)]
    {:total total
     :unique (count freqs)
     :distribution (into (sorted-map) freqs)
     :mode (when (seq freqs) (key (apply max-key val freqs)))
     :top-10 (take 10 (sort-by (comp - val) freqs))}))

(defn -main [& args]
  (println "Loading L5-creative wiring...")
  (let [wiring-path "data/wiring-ladder/level-5-creative.edn"
        wiring (edn/read-string (slurp wiring-path))
        seed 352362012

        _ (println "Generating initial genotype (seed" seed ")...")
        genotype (random-genotype 100 seed)
        phenotype (apply str (repeat 100 "0"))

        _ (println "Running MMCA (100 generations)...")
        run (mmca/run-mmca {:genotype genotype
                           :phenotype phenotype
                           :generations 100
                           :seed seed
                           :wiring wiring})

        _ (println "Sampling 500 hexagrams...")
        samples (sample-hexagrams-from-run run 500 42)
        dist (hexagram-distribution samples)]

    (println "\n=== Hexagram Distribution for L5-creative ===")
    (println "Total samples:" (:total dist))
    (println "Unique hexagrams:" (:unique dist))
    (println "Mode (most common): #" (:mode dist))

    (println "\n=== Top 10 Hexagrams ===")
    (doseq [[hex-id cnt] (:top-10 dist)]
      (let [pct (* 100.0 (/ cnt (:total dist)))]
        (println (format "  #%2d: %3d (%.1f%%)" hex-id cnt pct))))

    ;; Rule 90 signature check
    (let [h63 (get (:distribution dist) 63 0)
          h64 (get (:distribution dist) 64 0)
          total (:total dist)
          rule90-pct (* 100.0 (/ (+ h63 h64) (max 1 total)))]
      (println "\n=== Rule 90 Signature ===")
      (println "Hexagram 63 (既濟 Ji Ji - Completion):" h63)
      (println "Hexagram 64 (未濟 Wei Ji - Incompletion):" h64)
      (println (format "Combined: %.1f%%" rule90-pct))
      (when (> rule90-pct 10)
        (println "*** RULE 90 SIGNATURE DETECTED ***")))))

(-main)
