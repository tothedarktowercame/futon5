(ns mission-17a-compare
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.hexagram.metrics :as hex-metrics]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.filament :as filament]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.register-shift :as register-shift]
            [futon5.mmca.render :as render]
            [futon5.mmca.runtime :as mmca]))

(def ^:private default-scale 250)

(defn- usage []
  (str/join
   "\n"
   ["Generate Mission 17a baseline/exotic side-by-side examples with scores."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/mission_17a_compare.clj [options]"
    ""
    "Options:"
    "  --n N            Number of examples (default 20)."
    "  --seed N         RNG seed for example selection (default 4242)."
    "  --run-seed N     Force a specific run seed for the first example."
    "  --sigil S        Override exotype sigil."
    "  --update-prob P  Override exotype update-prob."
    "  --match-threshold P Override exotype match-threshold."
    "  --out-dir PATH   Output dir for images (default futon5/resources/figures)."
    "  --out-table PATH Output org table (default /tmp/mission-17a-compare-<ts>.org)."
    "  --scale PCT      Resize PNGs by percent (default 250)."
    "  --help           Show this message."]))

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

          (= "--n" flag)
          (recur (rest more) (assoc opts :n (parse-int (first more))))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

          (= "--run-seed" flag)
          (recur (rest more) (assoc opts :run-seed (parse-int (first more))))

          (= "--sigil" flag)
          (recur (rest more) (assoc opts :sigil (first more)))

          (= "--update-prob" flag)
          (recur (rest more) (assoc opts :update-prob (parse-double (first more))))

          (= "--match-threshold" flag)
          (recur (rest more) (assoc opts :match-threshold (parse-double (first more))))

          (= "--out-dir" flag)
          (recur (rest more) (assoc opts :out-dir (first more)))

          (= "--out-table" flag)
          (recur (rest more) (assoc opts :out-table (first more)))

          (= "--scale" flag)
          (recur (rest more) (assoc opts :scale (parse-int (first more))))

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

(defn- band-score [x center width]
  (let [x (double (or x 0.0))
        center (double (or center 0.0))
        width (double (or width 1.0))]
    (if (pos? width)
      (max 0.0 (- 1.0 (/ (Math/abs (- x center)) width)))
      0.0)))

(defn- envelope-score [summary]
  (let [ent (band-score (:avg-entropy-n summary) 0.6 0.25)
        chg (band-score (:avg-change summary) 0.2 0.15)]
    (* 100.0 (/ (+ ent chg) 2.0))))

(defn- triad-score [summary hex-score]
  (let [gen-score (double (or (:gen/composite-score summary) (:composite-score summary) 0.0))
        phe-score (double (or (:phe/composite-score summary) (:composite-score summary) 0.0))
        exo-score (double (or hex-score 0.0))]
    (/ (+ gen-score phe-score exo-score) 3.0)))

(defn- phe->grid [s]
  [(mapv (fn [ch] (if (= ch \1) 1 0)) (seq (or s "")))])

(defn- phe->frames [phe-history]
  (mapv phe->grid phe-history))

(defn- score-run [run]
  (let [summary (metrics/summarize-run run)
        hex-transition (hex-metrics/run->transition-matrix run)
        hex-signature (hex-metrics/transition-matrix->signature hex-transition)
        hex-class (hex-metrics/signature->hexagram-class hex-signature)
        hex-score (* 100.0 (hex-metrics/hexagram-fitness hex-class))
        triad (triad-score summary hex-score)
        shift (register-shift/register-shift-summary run)
        filament-score (double (or (:score (filament/analyze-run (phe->frames (:phe-history run)) {})) 0.0))]
    {:short (double (or (:composite-score summary) 0.0))
     :envelope (envelope-score summary)
     :triad triad
     :shift (:shift/composite shift)
     :filament filament-score
     :hex {:class hex-class :score hex-score}}))

(defn- render-run! [run path]
  (render/render-run->file! run path {:exotype? true}))

(defn- write-png! [ppm png]
  (shell/sh "convert" ppm png))

(defn- scale-png! [png scale]
  (when (and scale (pos? scale))
    (shell/sh "mogrify" "-resize" (str scale "%") png)))

(defn -main [& args]
  (let [{:keys [help unknown n seed run-seed sigil update-prob match-threshold out-dir out-table scale]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      :else
      (let [n (int (or n 20))
            seed (long (or seed 4242))
            scale (int (or scale default-scale))
            out-dir (or out-dir "futon5/resources/figures")
            ts (System/currentTimeMillis)
            out-table (or out-table (format "/tmp/mission-17a-compare-%d.org" ts))
            baseline-cfg (:config (edn/read-string (slurp "futon5/resources/mission-17a-refine-baseline.edn")))
            raw-exotic-cfg (:config (edn/read-string (slurp "futon5/resources/mission-17a-refine-exotic.edn")))
            exo-params (cond-> (:params (:exotype raw-exotic-cfg))
                         (some? update-prob) (assoc :update-prob update-prob)
                         (some? match-threshold) (assoc :match-threshold match-threshold))
            exotype-override (cond-> (:exotype raw-exotic-cfg)
                               (some? sigil) (assoc :sigil sigil)
                               (or (some? update-prob) (some? match-threshold)) (assoc :params exo-params))
            exotic-cfg (assoc raw-exotic-cfg :exotype exotype-override)
            gen-len (count (:genotype baseline-cfg))
            phe-len (count (:phenotype baseline-cfg))
            generations (int (:generations baseline-cfg))
            rng (java.util.Random. seed)
            label-prefix (if (or (some? update-prob) (some? match-threshold))
                           (format "mission-17a-compare-pt-u%.2f-m%.2f"
                                   (double (or update-prob (:update-prob exo-params)))
                                   (double (or match-threshold (:match-threshold exo-params))))
                           "mission-17a-compare")]
        (.mkdirs (io/file out-dir))
        (let [rows
              (mapv
               (fn [idx]
                 (let [run-seed (long (or (when (and (= idx 0) (some? run-seed)) run-seed)
                                          (rng-int rng Integer/MAX_VALUE)))
                       grng (java.util.Random. run-seed)
                       genotype (rng-sigil-string grng gen-len)
                       phenotype (rng-phenotype-string grng phe-len)
                       baseline-run (mmca/run-mmca (assoc baseline-cfg
                                                          :genotype genotype
                                                          :phenotype phenotype
                                                          :seed run-seed))
                       exotic-run (mmca/run-mmca (assoc exotic-cfg
                                                        :genotype genotype
                                                        :phenotype phenotype
                                                        :seed run-seed
                                                        :exotype (exotype/resolve-exotype (:exotype exotic-cfg))))
                       base-label (format "%s-%02d-seed-%d" label-prefix (inc idx) run-seed)
                       baseline-ppm (str (io/file out-dir (str base-label "-baseline.ppm")))
                       baseline-png (str (io/file out-dir (str base-label "-baseline.png")))
                       exotic-ppm (str (io/file out-dir (str base-label "-exotic.ppm")))
                       exotic-png (str (io/file out-dir (str base-label "-exotic.png")))
                       baseline-score (score-run baseline-run)
                       exotic-score (score-run exotic-run)]
                   (render-run! baseline-run baseline-ppm)
                   (write-png! baseline-ppm baseline-png)
                   (scale-png! baseline-png scale)
                   (render-run! exotic-run exotic-ppm)
                   (write-png! exotic-ppm exotic-png)
                   (scale-png! exotic-png scale)
                   {:seed run-seed
                    :baseline baseline-score
                    :exotic exotic-score
                    :baseline-image baseline-png
                    :exotic-image exotic-png}))
               (range n))]
          (let [header "| seed | short b | short e | env b | env e | triad b | triad e | shift b | shift e | filament b | filament e |"
                sep "|-"
                lines (map (fn [{:keys [seed baseline exotic]}]
                             (format "| %d | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f | %.3f | %.3f |"
                                     seed
                                     (double (or (:short baseline) 0.0))
                                     (double (or (:short exotic) 0.0))
                                     (double (or (:envelope baseline) 0.0))
                                     (double (or (:envelope exotic) 0.0))
                                     (double (or (:triad baseline) 0.0))
                                     (double (or (:triad exotic) 0.0))
                                     (double (or (:shift baseline) 0.0))
                                     (double (or (:shift exotic) 0.0))
                                     (double (or (:filament baseline) 0.0))
                                     (double (or (:filament exotic) 0.0))))
                           rows)
                table (str/join "\n" (concat [header sep] lines))]
            (spit out-table table)
            (println "Wrote" out-table)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
