(ns local-compressibility-grid
  "Measure the phenotype local-compressibility distribution on a MetaCA grid.

   The measurement geometry is fixed and shared by every grid cell and ECA
   anchor: width 250, zero discarded burn-in rows, then 250 recorded rows.  Each sheet is
   tiled into 100x100 patches at stride 50 (16 patches, 1250 packed bytes each)
   and compressed independently with zlib level 9.

   No command runs by default.  The expensive grid requires the explicit
   `grid` command; `validate-eca` runs only rules 54, 110, 204, 90, and 30.

   Examples:
     clojure -M scripts/local_compressibility_grid.clj validate-eca --seeds 4

     clojure -M scripts/local_compressibility_grid.clj grid
       --gammas 1,2,4,8,16,32,64 --kappas 0,0.1,0.2,0.5,1.0
       --seeds 4 --out /tmp/local-compressibility.csv"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning])
  (:import (java.io ByteArrayOutputStream)
           (java.util.zip Deflater DeflaterOutputStream)))

(def ^:private width 250)
(def ^:private burn-in 0)
(def ^:private sheet-rows 250)
(def ^:private patch-size 100)
(def ^:private patch-stride 50)
(def ^:private default-gammas [1.0 2.0 4.0 8.0 16.0 32.0 64.0])
(def ^:private default-kappas [0.0 0.1 0.2 0.5 1.0])
(def ^:private default-seed-base 2026102000)
(def ^:private eca-rules [54 110 204 90 30])
;; The acceptance bar is SEPARATION, not point targets.  The original targets
;; (54 -> 0.94, 110 -> 0.88) came from a reference implementation that packed each
;; 100-bit row to 13 bytes, appending four zero bits per row -- a regular period-13
;; artifact zlib exploits.  codex-4 caught that on review; under correct contiguous
;; packing rule 110 measures ~0.67-0.75, so the 0.88 target was contaminated and the
;; script was right to refuse it.  Encoding a corrected point target would repeat the
;; mistake at a new value.  What the instrument must actually do is put the class-IV
;; rules high and everything else at zero.
(def ^:private eca-class-iv #{54 110})
(def ^:private eca-non-class-iv #{204 90 30})
(def ^:private class-iv-floor 0.50)
(def ^:private non-class-iv-ceiling 0.05)

(defn- usage []
  (str
   "Usage:\n"
   "  clojure -M scripts/local_compressibility_grid.clj validate-eca [options]\n"
   "  clojure -M scripts/local_compressibility_grid.clj grid [options]\n\n"
   "Options:\n"
   "  --gammas CSV    gamma grid (default 1,2,4,8,16,32,64)\n"
   "  --kappas CSV    kappa grid (default 0,0.1,0.2,0.5,1.0)\n"
   "  --seeds N       common seed count, at least 4 (default 4)\n"
   "  --seed-base N   first seed (default 2026102000)\n"
   "  --out PATH      grid CSV path, or - for stdout (default -)\n\n"
   "Geometry is deliberately not configurable: width=250, burn-in=0, "
   "rows=250, patch=100, stride=50.\n"))

(defn- parse-long-option [option value]
  (try
    (Long/parseLong value)
    (catch NumberFormatException _
      (throw (ex-info (str option " requires an integer")
                      {:option option :value value})))))

(defn- parse-double-list [option value]
  (let [pieces (remove str/blank? (str/split value #","))]
    (when (empty? pieces)
      (throw (ex-info (str option " requires a non-empty comma-separated list")
                      {:option option :value value})))
    (try
      (mapv #(Double/parseDouble %) pieces)
      (catch NumberFormatException _
        (throw (ex-info (str option " contains a non-number")
                        {:option option :value value}))))))

(defn- parse-options [args]
  (loop [remaining args
         options {:gammas default-gammas
                  :kappas default-kappas
                  :seed-count 4
                  :seed-base default-seed-base
                  :out "-"}]
    (if (empty? remaining)
      (do
        (when (< (:seed-count options) 4)
          (throw (ex-info "--seeds must be at least 4"
                          {:seed-count (:seed-count options)})))
        options)
      (let [[option value & more] remaining]
        (when (or (nil? value) (str/starts-with? value "--"))
          (throw (ex-info (str option " requires a value") {:option option})))
        (recur
         more
         (case option
           "--gammas" (assoc options :gammas (parse-double-list option value))
           "--kappas" (assoc options :kappas (parse-double-list option value))
           "--seeds" (assoc options :seed-count (parse-long-option option value))
           "--seed-base" (assoc options :seed-base (parse-long-option option value))
           "--out" (assoc options :out value)
           (throw (ex-info (str "unknown option " option) {:option option}))))))))

(defn- initial-components [seed]
  (ca/with-seed seed
    {:genotype (vec (ca/random-sigil-string width))
     :phenotype (ca/random-phenotype-string width)
     :exotypes (grid/initial-grid :heterogeneous-fixed width)}))

(defn- metaca-state [gamma kappa seed]
  (let [{:keys [genotype phenotype exotypes]} (initial-components seed)]
    {:arm :efe-full
     :seed seed
     :time 0
     :self-tuning-arm :fixed-0.55
     :lambda-step-size 0.0
     :lambdas (vec (repeat width 0.55))
     :genotype genotype
     :previous-genotype genotype
     :phenotype phenotype
     :exotypes exotypes
     :blend-action? true
     :epistemic-coefficient kappa
     :blend-strength 0.0
     :apply-probability 1.0
     :policy-precision gamma}))

(defn- checked-metaca-step [state]
  (let [advanced (tuning/step state)]
    ;; self-tuning/step reconstructs its output map.  These checks make loss of
    ;; either grid coordinate an immediate error instead of a silent trajectory
    ;; change after the first step.
    (doseq [key [:policy-precision :epistemic-coefficient]]
      (when-not (= (double (key state)) (double (key advanced)))
        (throw (ex-info "MetaCA step failed to preserve a grid coordinate"
                        {:key key :before (key state) :after (key advanced)}))))
    (when-not (= :efe-full (:arm advanced))
      (throw (ex-info "MetaCA step changed the EFE arm"
                      {:before (:arm state) :after (:arm advanced)})))
    advanced))

(defn- sheet-after-burn [step project-row initial]
  (let [burned (nth (iterate step initial) burn-in)]
    (loop [state burned
           rows []]
      (if (= sheet-rows (count rows))
        rows
        (let [advanced (step state)]
          (recur advanced (conj rows (project-row advanced))))))))

(defn- metaca-sheet [gamma kappa seed]
  (sheet-after-burn checked-metaca-step :phenotype
                    (metaca-state gamma kappa seed)))

(defn- eca-step [rule row]
  (apply str
         (for [index (range width)]
           (let [left (Character/digit ^char (nth row (mod (dec index) width)) 2)
                 self (Character/digit ^char (nth row index) 2)
                 right (Character/digit ^char (nth row (mod (inc index) width)) 2)
                 neighbourhood (+ (* 4 left) (* 2 self) right)]
             (if (bit-test rule neighbourhood) \1 \0)))))

(defn- eca-sheet [rule seed]
  (let [initial (:phenotype (initial-components seed))]
    (sheet-after-burn #(eca-step rule %) identity initial)))

(defn- patch-starts [extent]
  (range 0 (inc (- extent patch-size)) patch-stride))

(defn- packed-patch [sheet row-start column-start]
  (let [bit-count (* patch-size patch-size)
        packed (byte-array (quot bit-count 8))]
    (doseq [row-offset (range patch-size)
            column-offset (range patch-size)]
      (let [bit-index (+ (* row-offset patch-size) column-offset)
            byte-index (quot bit-index 8)
            bit-in-byte (- 7 (mod bit-index 8))
            cell (nth (nth sheet (+ row-start row-offset))
                      (+ column-start column-offset))]
        (when-not (or (= cell \0) (= cell \1))
          (throw (ex-info "phenotype sheet contains a non-binary cell"
                          {:row (+ row-start row-offset)
                           :column (+ column-start column-offset)
                           :cell cell})))
        (when (= cell \1)
          (aset-byte packed byte-index
                     (unchecked-byte
                      (bit-set (bit-and 0xff (aget packed byte-index))
                               bit-in-byte))))))
    packed))

(defn- zlib-length [^bytes input]
  (let [sink (ByteArrayOutputStream.)
        compressor (Deflater. Deflater/BEST_COMPRESSION)]
    (try
      (with-open [stream (DeflaterOutputStream. sink compressor)]
        (.write stream input 0 (alength input)))
      (.size sink)
      (finally
        (.end compressor)))))

(defn- patch-ratios [sheet]
  (when-not (and (= sheet-rows (count sheet))
                 (every? #(= width (count %)) sheet))
    (throw (ex-info "sheet geometry differs from the fixed measurement geometry"
                    {:expected [sheet-rows width]
                     :actual [(count sheet) (mapv count (take 3 sheet))]})))
  (mapv (fn [[row-start column-start]]
          (let [packed (packed-patch sheet row-start column-start)]
            (/ (double (zlib-length packed)) (alength packed))))
        (for [row-start (patch-starts sheet-rows)
              column-start (patch-starts width)]
          [row-start column-start])))

(defn- mean [values]
  (/ (reduce + 0.0 values) (double (count values))))

(defn- population-sd [values]
  (when (seq values)
    (let [average (mean values)]
      (Math/sqrt
       (mean (map #(let [delta (- (double %) average)]
                     (* delta delta))
                  values))))))

(defn- standard-error [values]
  (let [values (vec (remove nil? values))
        n (count values)]
    (cond
      (zero? n) nil
      (= 1 n) 0.0
      :else
      (let [average (mean values)
            sample-variance (/ (reduce + 0.0
                                       (map #(let [delta (- (double %) average)]
                                               (* delta delta))
                                            values))
                               (double (dec n)))]
        (/ (Math/sqrt sample-variance) (Math/sqrt n))))))

(defn- measure-sheet [sheet]
  (let [ratios (patch-ratios sheet)
        active (filterv #(< 0.5 %) ratios)]
    {:mid-range (/ (double (count (filter #(<= 0.3 % 0.9) ratios)))
                   (count ratios))
     :sd-active (population-sd active)
     :mean-ratio (mean ratios)
     :ceiling (apply max ratios)
     :patch-count (count ratios)}))

(defn- seeds [{:keys [seed-base seed-count]}]
  (range seed-base (+ seed-base seed-count)))

(defn- format-number [value]
  (if (nil? value) "" (format "%.9f" (double value))))

(defn- csv-row [{:keys [gamma kappa seed mid-range sd-active mean-ratio]}]
  (str/join "," [(format-number gamma)
                  (format-number kappa)
                  seed
                  (format-number mid-range)
                  (format-number sd-active)
                  (format-number mean-ratio)]))

(defn- summary-field [rows key]
  (let [values (mapv key rows)]
    [(when (every? some? values) (mean values))
     (when (every? some? values) (standard-error values))]))

(defn- print-cell-summary! [label rows]
  (let [[mid mid-se] (summary-field rows :mid-range)
        [active active-se] (summary-field rows :sd-active)
        [ratio ratio-se] (summary-field rows :mean-ratio)]
    (binding [*out* *err*]
      (println
       (format (str "%s  mid-range %.2f%% +/- %.2f%% SE; "
                    "SD-active %s; mean-ratio %.6f +/- %.6f SE")
               label (* 100.0 mid) (* 100.0 mid-se)
               (if active
                 (format "%.6f +/- %.6f SE" active active-se)
                 "NA")
               ratio ratio-se)))))

(defn- open-output [path]
  (if (= path "-")
    {:writer *out* :close? false}
    {:writer (io/writer path) :close? true}))

(defn- run-grid! [options]
  (let [{:keys [writer close?]} (open-output (:out options))]
    (try
      (.write writer "gamma,kappa,seed,mid_range,sd_active,mean_ratio\n")
      (doseq [gamma (:gammas options)
              kappa (:kappas options)]
        (let [rows (mapv (fn [seed]
                           (merge {:gamma gamma :kappa kappa :seed seed}
                                  (measure-sheet (metaca-sheet gamma kappa seed))))
                         (seeds options))]
          (doseq [row rows]
            (.write writer (str (csv-row row) "\n")))
          (.flush writer)
          (print-cell-summary! (format "gamma=%g kappa=%g" gamma kappa) rows)))
      (finally
        (when close? (.close writer))))))

(defn- run-eca-validation! [options]
  (binding [*out* *err*]
    (println (format (str "ECA validation: width=%d burn-in=%d rows=%d, "
                          "patch=%d stride=%d, %d packed bytes/patch, "
                          "%d patches/sheet, %d seeds")
                     width burn-in sheet-rows patch-size patch-stride
                     (quot (* patch-size patch-size) 8)
                     (* (count (patch-starts sheet-rows))
                        (count (patch-starts width)))
                     (:seed-count options))))
  (let [by-rule
        (mapv (fn [rule]
                (let [rows (mapv (fn [seed]
                                   (assoc (measure-sheet (eca-sheet rule seed))
                                          :rule rule :seed seed))
                                 (seeds options))]
                  (print-cell-summary! (format "rule=%d" rule) rows)
                  [rule rows]))
              eca-rules)
        all-rows (mapcat second by-rule)
        ceiling (apply max (map :ceiling all-rows))]
    (binding [*out* *err*]
      (println (format "Observed compression ceiling: %.9f" ceiling)))
    (let [failures
          (keep (fn [[rule rows]]
                  (let [observed (mean (map :mid-range rows))]
                    (cond
                      (and (eca-class-iv rule) (< observed class-iv-floor))
                      {:rule rule :expected (str ">= " class-iv-floor)
                       :observed observed :class :class-iv}
                      (and (eca-non-class-iv rule) (> observed non-class-iv-ceiling))
                      {:rule rule :expected (str "<= " non-class-iv-ceiling)
                       :observed observed :class :non-class-iv})))
                by-rule)]
      (when (seq failures)
        (throw (ex-info
                "ECA anchor validation failed; do not run the MetaCA grid"
                {:validation-failed? true :failures (vec failures)}))))
    all-rows))

(defn -main [& args]
  (let [[command & option-args] args]
    (try
      (case command
        "grid" (run-grid! (parse-options option-args))
        "validate-eca" (run-eca-validation! (parse-options option-args))
        (throw (ex-info "an explicit command is required" {:command command})))
      (catch clojure.lang.ExceptionInfo error
        (binding [*out* *err*]
          (println "ERROR:" (ex-message error))
          (if (:validation-failed? (ex-data error))
            (println "Failures:" (:failures (ex-data error)))
            (println (usage))))
        (System/exit 2)))))

(apply -main *command-line-args*)
