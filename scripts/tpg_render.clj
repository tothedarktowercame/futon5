#!/usr/bin/env bb
;; Render CA spacetime diagrams from TPG-controlled MMCA runs.
;;
;; Usage:
;;   bb -cp src:resources scripts/tpg_render.clj
;;
;; Produces PPM+PNG spacetime diagrams in out/tpg-runs/

(require '[futon5.ca.core :as ca]
         '[futon5.mmca.render :as render]
         '[futon5.tpg.core :as tpg]
         '[futon5.tpg.runner :as runner]
         '[clojure.string :as str])

(def out-dir "out/tpg-runs")

(defn ensure-dir! [dir]
  (let [f (java.io.File. dir)]
    (when-not (.exists f)
      (.mkdirs f))))

(defn convert-ppm->png! [ppm-path png-path & {:keys [scale] :or {scale 4}}]
  (let [result (clojure.java.shell/sh
                "convert" ppm-path
                "-scale" (str scale "00%")
                "-interpolate" "Nearest"
                "-filter" "point"
                png-path)]
    (when (zero? (:exit result))
      (println "  →" png-path))))

(defn run-and-render!
  "Run a TPG-controlled MMCA simulation and render the spacetime diagram."
  [{:keys [name tpg genotype phenotype generations seed scale]
    :or {generations 80 seed 42 scale 4}}]
  (println (str "\n=== " name " ==="))
  (println (str "  TPG: " (:tpg/id tpg)))
  (println (str "  Genotype: " (subs genotype 0 (min 20 (count genotype)))
                (when (> (count genotype) 20) "...")))
  (println (str "  Generations: " generations " | Seed: " seed))

  (let [result (runner/run-tpg
                {:genotype genotype
                 :phenotype phenotype
                 :generations generations
                 :tpg tpg
                 :seed seed})
        base (str out-dir "/" name)
        ppm-path (str base ".ppm")
        png-path (str base ".png")]

    ;; Print routing summary
    (let [summary (runner/summarize-routing (:routing-trace result))]
      (printf "  Operators used: %d | Entropy: %.2f bits | Mean depth: %.1f\n"
              (count (:operator-frequency summary))
              (:operator-entropy summary)
              (:mean-depth summary))
      (doseq [[op cnt] (sort-by (comp - val) (:operator-frequency summary))]
        (printf "    %-20s %d (%.0f%%)\n"
                (clojure.core/name op) cnt
                (* 100.0 (/ (double cnt) (double (:total-decisions summary)))))))

    ;; Print verifier satisfaction
    (let [vr (:verifier-result result)]
      (printf "  Overall satisfaction: %.3f\n" (double (:overall-satisfaction vr)))
      (doseq [[k v] (sort-by key (:satisfaction-vector vr))]
        (printf "    %-20s %.3f\n" (clojure.core/name k) (double v))))

    ;; Render
    (let [pixels (render/render-run result)]
      (render/write-ppm! ppm-path pixels :comment name)
      (println (str "  PPM: " ppm-path " (" (count pixels) " rows × "
                    (count (first pixels)) " cols)"))
      (convert-ppm->png! ppm-path png-path :scale scale))

    ;; Also render with phenotype if available
    (when phenotype
      (let [phe-ppm (str base "-phe.ppm")
            phe-png (str base "-phe.png")
            phe-pixels (render/render-run result {:exotype? false})]
        (render/write-ppm! phe-ppm phe-pixels :comment (str name "-phenotype"))
        (convert-ppm->png! phe-ppm phe-png :scale scale)))

    result))

;; =============================================================================
;; GENERATE DIAGRAMS
;; =============================================================================

(ensure-dir! out-dir)

(println "\n╔══════════════════════════════════════════════════════════╗")
(println "║  TPG-Controlled MMCA Spacetime Diagrams                 ║")
(println "╚══════════════════════════════════════════════════════════╝")

;; Get some sigils for genotypes
(def sigils (mapv :sigil (ca/sigil-entries)))
(defn random-genotype [rng len]
  (apply str (repeatedly len #(nth sigils (.nextInt ^java.util.Random rng (count sigils))))))

(def rng (java.util.Random. 42))

;; 1. Simple TPG — short genotype
(def gen32 (random-genotype rng 32))
(run-and-render!
 {:name "tpg-simple-32"
  :tpg (tpg/seed-tpg-simple)
  :genotype gen32
  :generations 80
  :seed 42})

;; 2. Hierarchical TPG — same genotype for comparison
(run-and-render!
 {:name "tpg-hierarchical-32"
  :tpg (tpg/seed-tpg-hierarchical)
  :genotype gen32
  :generations 80
  :seed 42})

;; 3. Simple TPG — longer genotype (64 sigils)
(def gen64 (random-genotype rng 64))
(run-and-render!
 {:name "tpg-simple-64"
  :tpg (tpg/seed-tpg-simple)
  :genotype gen64
  :generations 120
  :seed 42})

;; 4. Hierarchical TPG — longer genotype
(run-and-render!
 {:name "tpg-hierarchical-64"
  :tpg (tpg/seed-tpg-hierarchical)
  :genotype gen64
  :generations 120
  :seed 42})

;; 5. With phenotype
(def phe32 (apply str (repeatedly 32 #(if (< (.nextDouble rng) 0.5) \1 \0))))
(run-and-render!
 {:name "tpg-simple-phenotype"
  :tpg (tpg/seed-tpg-simple)
  :genotype gen32
  :phenotype phe32
  :generations 80
  :seed 42})

;; 6. Hierarchical with phenotype
(run-and-render!
 {:name "tpg-hierarchical-phenotype"
  :tpg (tpg/seed-tpg-hierarchical)
  :genotype gen32
  :phenotype phe32
  :generations 80
  :seed 42})

(println "\n══════════════════════════════════════════════════════════")
(println "Done! Diagrams in" out-dir)
(println "══════════════════════════════════════════════════════════")
