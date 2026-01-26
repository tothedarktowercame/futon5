#!/usr/bin/env clj -M
;; Generate full workup for passing hexagram wirings.
;; Usage:
;;   clj -M -e '(load-file "scripts/hexagram_wiring_pass_workup.clj")'

(ns hexagram-wiring-pass-workup
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [futon5.hexagram.lines :as lines]
            [futon5.mmca.render :as render]
            [futon5.wiring.hexagram :as hex-wiring]
            [futon5.wiring.runtime :as runtime]
            [futon5.xenotype.mermaid :as mermaid]))

(def ^:private default-seed 352362012)
(def ^:private default-generations 50)
(def ^:private default-width 80)
(def ^:private default-out-dir "reports/hexagram-wiring-pass-workup")

(defn- usage []
  (str "Usage:\n"
       "  clj -M -e '(load-file \"scripts/hexagram_wiring_pass_workup.clj\")'\n\n"
       "Options (via env vars):\n"
       "  HEX_SEED          RNG seed (default 352362012)\n"
       "  HEX_GENERATIONS   Generations (default 50)\n"
       "  HEX_WIDTH         Width (default 80)\n"
       "  HEX_OUT_DIR       Output dir (default reports/hexagram-wiring-pass-workup)\n"))

(defn- env-int [k fallback]
  (try
    (Integer/parseInt (str (or (System/getenv k) fallback)))
    (catch Throwable _ fallback)))

(defn- ensure-dir! [path]
  (.mkdirs (io/file path))
  path)

(defn- lines->symbols [hex-lines]
  (apply str (map #(if (= % :yang) "⚊" "⚋") hex-lines)))

(defn- command-exists? [cmd]
  (zero? (:exit (shell/sh "bash" "-lc" (str "command -v " cmd " >/dev/null 2>&1")))))

(defn- convert-ppm->png! [ppm-path png-path]
  (if (command-exists? "convert")
    (let [{:keys [exit err]} (shell/sh "convert" ppm-path png-path)]
      (when-not (zero? exit)
        (println "Warning: convert failed" err)))
    (println "Warning: convert not available; leaving" ppm-path)))

(defn- render-mermaid! [mmd-path png-path]
  (if (and (command-exists? "aa-exec") (command-exists? "mmdc"))
    (let [{:keys [exit err]} (shell/sh "aa-exec" "-p" "chrome" "--"
                                      "mmdc" "-i" mmd-path "-o" png-path)]
      (when-not (zero? exit)
        (println "Warning: mmdc failed" err)))
    (println "Warning: aa-exec or mmdc not available; leaving" mmd-path)))

(defn- run-hexagram [n seed generations width]
  (let [wiring (hex-wiring/hexagram->simple-wiring n)
        genotype (runtime/random-genotype width seed)
        phenotype (apply str (repeat width "0"))
        run (runtime/run-wiring {:wiring wiring
                                 :genotype genotype
                                 :phenotype phenotype
                                 :generations generations
                                 :collect-metrics? true})
        history (take 50 (:gen-history run))
        failures (runtime/detect-failures history)
        metrics (last (:metrics-history run))
        hex-info (lines/hexagram-number->hexagram n)]
    {:n n
     :wiring wiring
     :hex-info hex-info
     :metrics metrics
     :failures failures
     :run run}))

(defn- render-pass! [pass out-dir]
  (let [{:keys [n wiring run metrics failures hex-info]} pass
        images-dir (str out-dir "/images")
        mermaid-dir (str out-dir "/mermaid")
        base (format "hex-%02d" n)
        triptych-ppm (str images-dir "/" base "-triptych.ppm")
        triptych-png (str images-dir "/" base "-triptych.png")
        wiring-mmd (str mermaid-dir "/" base ".mmd")
        wiring-png (str images-dir "/" base "-wiring.png")]
    (ensure-dir! images-dir)
    (ensure-dir! mermaid-dir)

    ;; Triptych render
    (render/render-run->file! run triptych-ppm {:exotype? true})
    (convert-ppm->png! triptych-ppm triptych-png)

    ;; Wiring mermaid
    (mermaid/save-mermaid (:diagram wiring) wiring-mmd {:title (str "Hexagram " n)})
    (render-mermaid! wiring-mmd wiring-png)

    (assoc pass
           :triptych-ppm triptych-ppm
           :triptych-png triptych-png
           :wiring-mmd wiring-mmd
           :wiring-png wiring-png)))

(defn- write-report! [passes out-dir opts]
  (let [report-path (str out-dir "/hexagram-wiring-pass-workup.md")
        sb (StringBuilder.)
        timestamp (java.time.LocalDateTime/now)
        {:keys [seed generations width]} opts]
    (.append sb "# Hexagram Wiring Pass Workup\n\n")
    (.append sb (str "*Generated: " timestamp "*\n\n"))
    (.append sb (format "Seed: %d | Generations: %d | Width: %d\n\n" seed generations width))
    (.append sb (format "Passing hexagrams: %d\n\n" (count passes)))

    (doseq [pass passes]
      (let [{:keys [n wiring hex-info metrics failures triptych-png wiring-png wiring-mmd]} pass
            lines-str (lines->symbols (:lines hex-info))
            formula (get-in wiring [:meta :formula])
            entropy (or (:entropy-n metrics) 0.0)
            unique (or (:unique-sigils metrics) 0)
            barcode (get-in failures [:barcode] 0.0)
            candycane (get-in failures [:candycane] 0.0)]
        (.append sb (format "## #%d %s\n\n" n (or (:name hex-info) "?")))
        (.append sb (format "- Lines: %s\n" lines-str))
        (.append sb (format "- Formula: `%s`\n" (or formula "?")))
        (.append sb (format "- Metrics: entropy-n=%.2f, unique=%d\n" entropy unique))
        (.append sb (format "- Failure scores: barcode=%.2f, candycane=%.2f\n\n" barcode candycane))

        (when (and triptych-png (.exists (io/file triptych-png)))
          (.append sb (format "![Triptych](images/%s)\n\n" (.getName (io/file triptych-png)))))

        (cond
          (and wiring-png (.exists (io/file wiring-png)))
          (.append sb (format "![Wiring](images/%s)\n\n" (.getName (io/file wiring-png))))

          (and wiring-mmd (.exists (io/file wiring-mmd)))
          (do
            (.append sb "```mermaid\n")
            (.append sb (slurp wiring-mmd))
            (.append sb "\n```\n\n"))

          :else
          (.append sb "(No wiring diagram available.)\n\n"))))

    (spit report-path (str sb))
    report-path))

(defn -main []
  (let [seed (env-int "HEX_SEED" default-seed)
        generations (env-int "HEX_GENERATIONS" default-generations)
        width (env-int "HEX_WIDTH" default-width)
        out-dir (or (System/getenv "HEX_OUT_DIR") default-out-dir)
        _ (ensure-dir! out-dir)
        results (mapv #(run-hexagram % seed generations width) (range 1 65))
        passes (->> results
                    (filter #(not (get-in % [:failures :fails?])))
                    (map #(render-pass! % out-dir))
                    vec)
        report (write-report! passes out-dir {:seed seed :generations generations :width width})]
    (println "Pass workup complete:")
    (println "  Report:" report)
    (println "  Images:" (str out-dir "/images"))
    (println "  Mermaid:" (str out-dir "/mermaid"))))

(-main)

