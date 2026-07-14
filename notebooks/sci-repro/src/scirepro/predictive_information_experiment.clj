(ns scirepro.predictive-information-experiment
  "Matched statistical comparison of mutating-template, Baldwin, and their
   executable combine/mutate blend using bitplane predictive information."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [futon5.ca.core :as ca]
            [futon5.mmca.predictive-information :as pi]
            [scirepro.baldwin :as baldwin]
            [scirepro.engine :as engine]
            [scirepro.mutating-template :as mutating-template])
  (:import [java.awt.image BufferedImage]
           [javax.imageio ImageIO]))

(def width 128)
(def steps 256)
(def seeds (vec (range 42 62)))
(def past-window 8)
(def burn-in 64)
(def dynamics [:mutating-template :baldwin :blend-template+baldwin-mutate])

(defn bounded-rng [seed]
  (let [rng (java.util.Random. (long seed))]
    (fn [limit] (.nextInt rng (int limit)))))

(defn context-string->bits [context]
  (when context
    (mapv (comp parse-long str) context)))

(defn blend-step
  "One coupled step using mutating-template COMBINE and Baldwin MUTATE."
  [state rng-fn]
  (let [{:keys [genotype phenotype]} state
        new-phenotype (engine/phenotype-step genotype phenotype)
        contexts (baldwin/build-context-quadruples phenotype new-phenotype)
        last-index (dec (count genotype))
        new-genotype
        (mapv (fn [i center]
                (let [left (if (zero? i) 0 (nth genotype (dec i)))
                      right (if (= i last-index) 0 (nth genotype (inc i)))
                      context (when (and (pos? i) (< i last-index))
                                (nth contexts (dec i)))
                      combined (mutating-template/combine-with-template
                                left center right (context-string->bits context))]
                  (baldwin/mutate-combined-rule combined context rng-fn)))
              (range)
              genotype)]
    {:genotype new-genotype :phenotype new-phenotype}))

(defn blend-evolve [genotype phenotype generations seed]
  (let [rng-fn (bounded-rng seed)
        states (vec (take (inc generations)
                          (iterate #(blend-step % rng-fn)
                                   {:genotype (vec genotype)
                                    :phenotype (vec phenotype)})))]
    {:genotype (mapv :genotype states)
     :phenotype (mapv :phenotype states)}))

(defn run-dynamic [dynamic seed]
  (let [genotype (engine/seeded-ic seed width)
        phenotype (engine/seeded-phenotype-ic (+ 100000 seed) width)
        dynamic-seed (+ 200000 seed)]
    (case dynamic
      :mutating-template
      (mutating-template/coupled-contextual-evolve genotype phenotype steps dynamic-seed)

      :baldwin
      (baldwin/baldwin-evolve genotype phenotype steps (bounded-rng dynamic-seed))

      :blend-template+baldwin-mutate
      (blend-evolve genotype phenotype steps dynamic-seed))))

(defn rule-history->sigil-history [history]
  (mapv (fn [row]
          (apply str (map #(ca/sigil-for (engine/rule->bit-string %)) row)))
        history))

(defn score-run [dynamic seed]
  (let [result (run-dynamic dynamic seed)
        score (pi/score-metaca-history
               (rule-history->sigil-history (:genotype result))
               {:k past-window :burn-in burn-in})]
    {:dynamic dynamic
     :seed seed
     :mean-ais-corrected (:mean-ais-corrected score)
     :max-ais-corrected (:max-ais-corrected score)
     :per-plane (mapv #(select-keys % [:plane :ais-corrected :ais-plugin
                                      :samples-per-cell])
                      (:per-plane score))}))

(defn mean [xs]
  (/ (reduce + 0.0 xs) (double (count xs))))

(defn ci95 [xs]
  (let [xs (vec xs)
        n (count xs)
        m (mean xs)
        variance (/ (reduce + 0.0
                            (map (fn [x]
                                   (let [d (- (double x) m)] (* d d)))
                                 xs))
                    (double (dec n)))]
    (* 1.96 (Math/sqrt (/ variance n)))))

(defn summarize [runs]
  (into {}
        (for [dynamic dynamics
              :let [values (mapv :mean-ais-corrected
                                 (filter #(= dynamic (:dynamic %)) runs))]]
          [dynamic {:n (count values) :mean (mean values) :ci95 (ci95 values)}])))

(defn comparison [summary]
  (let [a (:mutating-template summary)
        b (:baldwin summary)
        blend (:blend-template+baldwin-mutate summary)
        parent-low (min (- (:mean a) (:ci95 a)) (- (:mean b) (:ci95 b)))
        parent-high (max (+ (:mean a) (:ci95 a)) (+ (:mean b) (:ci95 b)))
        blend-low (- (:mean blend) (:ci95 blend))
        blend-high (+ (:mean blend) (:ci95 blend))]
    {:parent-ci-envelope [parent-low parent-high]
     :blend-ci [blend-low blend-high]
     :position (cond
                 (> blend-low parent-high) :above-both-parents
                 (< blend-high parent-low) :below-both-parents
                 :else :inside-or-overlapping-parent-envelope)}))

(defn write-png! [rows path]
  (let [scale 2
        height (count rows)
        width (count (first rows))
        image (BufferedImage. (* scale width) (* scale height)
                              BufferedImage/TYPE_INT_RGB)]
    (doseq [[y row] (map-indexed vector rows)
            [x value] (map-indexed vector row)
            dy (range scale)
            dx (range scale)]
      (let [v (int value)
            rgb (bit-or (bit-shift-left v 16) (bit-shift-left v 8) v)]
        (.setRGB image (+ (* scale x) dx) (+ (* scale y) dy) rgb)))
    (ImageIO/write image "png" (io/file path))))

(defn write-diagrams! [out-dir seed]
  (let [dir (io/file out-dir)
        results (into {} (for [dynamic dynamics]
                           [dynamic (run-dynamic dynamic seed)]))
        blend-grid (get-in results [:blend-template+baldwin-mutate :genotype])
        novelty {:different-from-mutating-template?
                 (not= blend-grid (get-in results [:mutating-template :genotype]))
                 :different-from-baldwin?
                 (not= blend-grid (get-in results [:baldwin :genotype]))}]
    (when-not (every? true? (vals novelty))
      (throw (ex-info "Blend grid duplicates a parent" novelty)))
    (.mkdirs dir)
    {:paths
     (into {}
           (for [dynamic dynamics
                 :let [path (io/file dir (str (name dynamic) "-seed-" seed ".png"))]]
             (do
               (write-png! (get-in results [dynamic :genotype]) path)
               [dynamic (str "data/predictive-information-diagrams/"
                             (.getName path))])))
     :novelty novelty}))

(defn repo-root []
  (cond
    (.exists (io/file "notebooks/sci-repro/deps.edn")) (io/file ".")
    (.exists (io/file "../../src/futon5")) (io/file "../..")
    :else (throw (ex-info "Run from futon5 root or notebooks/sci-repro" {}))))

(defn experiment-result []
  (let [runs (mapv (fn [[dynamic seed]] (score-run dynamic seed))
                   (for [dynamic dynamics seed seeds] [dynamic seed]))
        summary (summarize runs)]
    {:schema/version 1
     :experiment/id :predictive-information-parent-blend
     :replay :statistical
     :protocol {:width width :steps steps :seeds seeds
                :bitplanes 8 :past-window past-window :burn-in burn-in
                :initial-condition :matched-per-seed
                :blend {:combine :mutating-template
                        :mutate :baldwin-context-gated}}
     :runs runs
     :summary summary
     :comparison (comparison summary)}))

(defn -main [& _]
  (let [root (repo-root)
        data-path (io/file root "data/predictive-information-dynamics.edn")
        diagrams (write-diagrams! (io/file root "data/predictive-information-diagrams") 42)
        result (assoc (experiment-result) :diagrams diagrams)]
    (.mkdirs (.getParentFile data-path))
    (spit data-path (str (pr-str result) "\n"))
    (prn (select-keys result [:summary :comparison :diagrams]))))
