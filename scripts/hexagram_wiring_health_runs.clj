#!/usr/bin/env clj -M
;; Generate run + wiring EDN files for hexagram wirings (for health + learning loop).
;; Usage: clj -M -e '(load-file "scripts/hexagram_wiring_health_runs.clj")'

(ns hexagram-wiring-health-runs
  (:require [clojure.java.io :as io]
            [futon5.wiring.hexagram :as hex-wiring]
            [futon5.wiring.runtime :as runtime]))

(def ^:private default-seed 352362012)
(def ^:private default-generations 50)
(def ^:private default-width 80)
(def ^:private default-run-dir "/tmp/hexagram-wiring-runs")
(def ^:private default-wiring-dir "/tmp/hexagram-wiring-wirings")

(defn- env-int [k fallback]
  (try
    (Integer/parseInt (str (or (System/getenv k) fallback)))
    (catch Throwable _ fallback)))

(defn- ensure-dir! [path]
  (.mkdirs (io/file path))
  path)

(defn- write-edn! [path data]
  (spit path (pr-str data))
  path)

(defn- run-hexagram! [n seed generations width run-dir wiring-dir]
  (let [wiring (hex-wiring/hexagram->simple-wiring n)
        genotype (runtime/random-genotype width seed)
        phenotype (apply str (repeat width "0"))
        run (runtime/run-wiring {:wiring wiring
                                 :genotype genotype
                                 :phenotype phenotype
                                 :generations generations
                                 :collect-metrics? true})
        run (assoc run
                   :seed seed
                   :label (format "hex-%02d" n)
                   :wiring-id (get-in wiring [:meta :id])
                   :hexagram-number n)
        run-path (format "%s/hex-%02d-run.edn" run-dir n)
        wiring-path (format "%s/hex-%02d-wiring.edn" wiring-dir n)]
    (write-edn! run-path run)
    (write-edn! wiring-path wiring)
    {:n n :run-path run-path :wiring-path wiring-path}))

(defn -main []
  (let [seed (env-int "HEX_SEED" default-seed)
        generations (env-int "HEX_GENERATIONS" default-generations)
        width (env-int "HEX_WIDTH" default-width)
        run-dir (or (System/getenv "HEX_RUN_DIR") default-run-dir)
        wiring-dir (or (System/getenv "HEX_WIRING_DIR") default-wiring-dir)
        _ (ensure-dir! run-dir)
        _ (ensure-dir! wiring-dir)]
    (println "Generating hexagram runs...")
    (doseq [n (range 1 65)]
      (when (zero? (mod n 8))
        (println "  processing" n "..."))
      (run-hexagram! n seed generations width run-dir wiring-dir))
    (println "Done.")
    (println "  runs:" run-dir)
    (println "  wirings:" wiring-dir)))

(-main)
