(ns futon5.mmca.judge-cli
  "Interactive labeling tool for MMCA runs."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.java.shell :as shell]
            [futon5.mmca.render :as render]
            [futon5.hexagram.metrics :as hex-metrics]
            [futon5.mmca.metrics :as mmca-metrics]
            [futon5.mmca.register-shift :as register-shift]
            [futon5.mmca.trigram :as trigram]
            [futon5.mmca.filament :as filament]))

(defn- usage []
  (str/join
   "\n"
   ["MMCA judgement tool"
    ""
    "Usage:"
    "  bb -cp src:resources -m futon5.mmca.judge-cli [options] --inputs PATH"
    ""
    "Options:"
    "  --inputs PATH         Text file with one EDN run path per line."
    "  --out PATH            Output EDN lines file (default /tmp/mmca-judgements.edn)."
    "  --render-dir PATH     Write PPM renders for each run (optional)."
    "  --exotype             Render genotype/phenotype/exotype triptych."
    "  --scale PCT           Resize renders by percent (e.g. 250)."
    "  --shuffle             Shuffle input order."
    "  --help                Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--inputs" flag)
          (recur (rest more) (assoc opts :inputs (first more)))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          (= "--render-dir" flag)
          (recur (rest more) (assoc opts :render-dir (first more)))

          (= "--exotype" flag)
          (recur more (assoc opts :exotype true))

          (= "--scale" flag)
          (recur (rest more) (assoc opts :scale (parse-int (first more))))

          (= "--shuffle" flag)
          (recur more (assoc opts :shuffle true))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- now [] (.toString (java.time.Instant/now)))

(defn- load-run [path]
  (edn/read-string (slurp path)))

(defn- read-inputs [path]
  (->> (slurp path)
       str/split-lines
       (map str/trim)
       (remove str/blank?)
       vec))

(defn- prompt-label []
  (println "Label? (e=EoC, n=not, b=borderline, s=skip, q=quit)")
  (print "> ")
  (flush)
  (some-> (read-line) str/trim str/lower-case))

(defn- label->kw [s]
  (case s
    "e" :eoc
    "n" :not-eoc
    "b" :borderline
    "s" :skip
    "q" :quit
    nil))

(defn- summarize [run]
  (let [summary (mmca-metrics/summarize-run run)
        trans (hex-metrics/run->transition-matrix run)
        sig (hex-metrics/transition-matrix->signature trans)
        cls (hex-metrics/signature->hexagram-class sig)]
    {:summary summary
     :hexagram {:class cls
                :signature sig}}))

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

(defn- mask-genotype [gen phenotype keep-char]
  (apply str
         (keep (fn [[g p]]
                 (when (= p keep-char) g))
               (map vector (seq (or gen "")) (seq (or phenotype ""))))))

(defn- diversity-count [xs]
  (count (set xs)))

(defn- geno-phe-diversity [gen-history phe-history]
  (let [pairs (map vector (or gen-history []) (or phe-history []))
        masked-ones (map (fn [[g p]] (mask-genotype g p \1)) pairs)
        masked-zeros (map (fn [[g p]] (mask-genotype g p \0)) pairs)
        geno-div-1 (diversity-count masked-ones)
        geno-div-0 (diversity-count masked-zeros)
        phe-div (diversity-count phe-history)
        product (* geno-div-0 geno-div-1)]
    {:geno-div-0 geno-div-0
     :geno-div-1 geno-div-1
     :phe-div phe-div
     :geno-div-product product
     :geno-phe-div (* phe-div product)}))

(defn- score-run [run summary hexagram]
  (let [hex-score (* 100.0 (hex-metrics/hexagram-fitness (:class hexagram)))
        shift (register-shift/register-shift-summary run)
        trigram (trigram/score-early-late (:phe-history run))
        filament-score (double (or (:score (filament/analyze-run (phe->frames (:phe-history run)) {})) 0.0))
        {:keys [avg-change avg-entropy-n]} summary
        dead? (and (<= (double (or avg-change 0.0)) 0.05)
                   (<= (double (or avg-entropy-n 0.0)) 0.2))
        confetti? (and (>= (double (or avg-change 0.0)) 0.45)
                       (>= (double (or avg-entropy-n 0.0)) 0.8))
        diversity (geno-phe-diversity (:gen-history run) (:phe-history run))]
    {:short (double (or (:composite-score summary) 0.0))
     :envelope (envelope-score summary)
     :triad (triad-score summary hex-score)
     :shift (double (or (:shift/composite shift) 0.0))
     :trigram (double (or (:score trigram) 0.0))
     :filament filament-score
     :hex hex-score
     :diagnostics {:dead? dead?
                   :confetti? confetti?
                   :diversity diversity}}))

(defn- render-run!
  [render-dir path run exotype? scale]
  (when render-dir
    (let [base (-> path io/file .getName (str/replace #"\.edn$" ""))
          out (io/file render-dir (str base ".ppm"))]
      (.mkdirs (.getParentFile out))
      (render/render-run->file! run (.getPath out) {:exotype? exotype?})
      (when (and scale (pos? (long scale)))
        (shell/sh "mogrify" "-resize" (str scale "%") (.getPath out)))
      (.getPath out))))

(defn- open-image!
  [path]
  (when path
    (try
      (let [pb (ProcessBuilder. ["display" path])]
        (.start pb))
      (catch Exception _ nil))))

(defn- close-image!
  [^Process proc]
  (when proc
    (try
      (when (.isAlive proc)
        (.destroy proc)
        (Thread/sleep 50)
        (when (.isAlive proc)
          (.destroyForcibly proc)))
      (catch Exception _ nil))))

(defn- append-entry!
  [path entry]
  (spit path (str (pr-str entry) "\n") :append true))

(defn -main [& args]
  (let [{:keys [help unknown inputs out render-dir exotype scale shuffle]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do
                (println "Unknown option:" unknown)
                (println)
                (println (usage)))
      (nil? inputs) (do
                      (println "Missing --inputs PATH")
                      (println)
                      (println (usage)))
      :else
      (let [out (or out "/tmp/mmca-judgements.edn")
            paths (read-inputs inputs)
            paths (if shuffle (vec (shuffle paths)) paths)]
        (println (format "Loaded %d runs." (count paths)))
        (append-entry! out {:event :bundle
                            :timestamp (now)
                            :cwd (.getAbsolutePath (io/file "."))
                            :argv args
                            :inputs inputs
                            :render-dir render-dir
                            :exotype exotype
                            :scale scale
                            :shuffle shuffle})
        (doseq [path paths]
          (let [run (load-run path)
                render-path (render-run! render-dir path run exotype scale)
                proc (open-image! render-path)
                {:keys [summary hexagram]} (summarize run)
                scores (score-run run summary hexagram)
                {:keys [avg-change avg-entropy-n avg-unique temporal-autocorr]} summary]
            (println)
            (println "Run:" path)
            (when render-path
              (println "Render:" render-path))
            (println (format "Metrics: change %.3f | entropy-n %.3f | unique %.3f | autocorr %.3f"
                             (double (or avg-change 0.0))
                             (double (or avg-entropy-n 0.0))
                             (double (or avg-unique 0.0))
                             (double (or temporal-autocorr 0.0))))
            (println (format "Hex: %s | alpha %.3f | gap %.3f | rank %.2f"
                             (name (:class hexagram))
                             (double (or (get-in hexagram [:signature :alpha-estimate]) 0.0))
                             (double (or (get-in hexagram [:signature :spectral-gap]) 0.0))
                             (double (or (get-in hexagram [:signature :projection-rank]) 0.0))))
            (try
              (loop []
                (let [resp (prompt-label)
                      label (label->kw resp)]
                  (cond
                    (= label :quit) (do (println "Stopping.") (System/exit 0))
                    (= label :skip) (println "Skipped.")
                    (keyword? label)
                    (do
                      (append-entry! out {:event :judgement
                                          :timestamp (now)
                                          :path path
                                          :run/id (:run/id run)
                                          :seed (:seed run)
                                          :label label
                                          :summary summary
                                          :hexagram hexagram
                                          :scores scores})
                      (println "Saved.")))
                  (when-not (keyword? label)
                    (println "Unknown response; try again.")
                    (recur))))
              (finally
                (close-image! proc)))))
        (println "Done.")))))
