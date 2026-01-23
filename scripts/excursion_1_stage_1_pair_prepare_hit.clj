(ns excursion-1-stage-1-pair-prepare-hit
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.runtime :as mmca]
            [futon5.mmca.render :as render]))

(defn- usage []
  (str/join
   "\n"
   ["Prepare paired HIT set for tai vs baseline (mutation-free)."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/excursion_1_stage_1_pair_prepare_hit.clj [options]"
    ""
    "Options:"
    "  --seeds PATH       EDN vector of seeds (required)."
    "  --count N          Number of mutation-free pairs (default 20)."
    "  --length N         Genotype/phenotype length (default 80)."
    "  --generations N    Generations (default 100)."
    "  --out-dir PATH     Output directory (default /tmp/excursion-1-stage-1-pairs)."
    "  --inputs PATH      Output inputs list for pair-judge (default <out-dir>/inputs.txt)."
    "  --scale PCT        Resize renders by percent (default 250)."
    "  --reject-kernel-mutation   Skip runs with kernel changes from exotype (default false)."
    "  --help             Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag) (recur more (assoc opts :help true))
          (= "--seeds" flag) (recur (rest more) (assoc opts :seeds (first more)))
          (= "--count" flag) (recur (rest more) (assoc opts :count (parse-int (first more))))
          (= "--length" flag) (recur (rest more) (assoc opts :length (parse-int (first more))))
          (= "--generations" flag) (recur (rest more) (assoc opts :generations (parse-int (first more))))
          (= "--out-dir" flag) (recur (rest more) (assoc opts :out-dir (first more)))
          (= "--inputs" flag) (recur (rest more) (assoc opts :inputs (first more)))
          (= "--scale" flag) (recur (rest more) (assoc opts :scale (parse-int (first more))))
          (= "--reject-kernel-mutation" flag) (recur more (assoc opts :reject-mutations true))
          :else (recur more (assoc opts :unknown flag))))
      opts)))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(defn- rng-phenotype-string [^java.util.Random rng length]
  (apply str (repeatedly length #(rng-int rng 2))))

(defn- sample-range [^java.util.Random rng [lo hi]]
  (+ (double lo) (* (.nextDouble rng) (- (double hi) (double lo)))))

(defn- build-exotype [sigil update-prob match-threshold]
  (let [base (exotype/lift sigil)
        params (assoc (:params base)
                      :update-prob update-prob
                      :match-threshold match-threshold)]
    (assoc base :params params)))

(defn- render-run! [run ppm-path png-path scale]
  (render/render-run->file! run ppm-path {:exotype? true})
  (shell/sh "convert" ppm-path png-path)
  (when (and scale (pos? scale))
    (shell/sh "mogrify" "-resize" (str scale "%") png-path)))

(defn- pair-image! [left right out]
  (shell/sh "convert" left right "+append" out))

(defn- run-pair [seed length generations]
  (let [rng (java.util.Random. (long seed))
        genotype (rng-sigil-string rng length)
        phenotype (rng-phenotype-string rng length)
        update-prob (sample-range rng [0.3 0.7])
        match-threshold (sample-range rng [0.3 0.7])
        tai (build-exotype "工" update-prob match-threshold)
        baseline-run (mmca/run-mmca {:genotype genotype
                                     :phenotype phenotype
                                     :generations generations
                                     :kernel :mutating-template
                                     :operators []
                                     :seed seed})
        tai-run (mmca/run-mmca {:genotype genotype
                                :phenotype phenotype
                                :generations generations
                                :kernel :mutating-template
                                :operators []
                                :exotype tai
                                :seed seed})]
    {:seed seed
     :tai tai
     :baseline baseline-run
     :tai-run tai-run
     :mutations (seq (:exotype-mutations tai-run))}))

(defn -main [& args]
  (let [{:keys [help unknown seeds length generations out-dir inputs scale]
         :as opts} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      (nil? seeds) (println (usage))
      :else
      (let [target-count (int (or (:count opts) 20))
            length (int (or length 80))
            generations (int (or generations 100))
            scale (int (or scale 250))
            reject-mutations? (true? (:reject-mutations opts))
            seeds (edn/read-string (slurp seeds))
            out-dir (or out-dir "/tmp/excursion-1-stage-1-pairs")
            inputs (or inputs (str (io/file out-dir "inputs.txt")))
            pairs-path (str (io/file out-dir "pairs.edn"))]
        (.mkdirs (io/file out-dir))
        (loop [remaining seeds
               acc []
               processed 0]
          (if (or (empty? remaining) (>= (count acc) target-count))
            (do
              (spit inputs (str/join "\n" (map :image acc)))
              (spit pairs-path (str/join "\n" (map pr-str acc)))
              (println "Wrote" inputs)
              (println "Wrote" pairs-path)
              (println "Pairs" (count acc)))
            (let [seed (first remaining)
                  result (run-pair seed length generations)]
              (println (format "seed %d processed=%d accepted=%d mutations=%d"
                               (long seed)
                               (inc processed)
                               (count acc)
                               (count (:mutations result))))
              (if (and reject-mutations? (seq (:mutations result)))
                (recur (rest remaining) acc (inc processed))
                (let [base (format "excursion-1-stage-1-pair-seed-%d" (long seed))
                      tai-edn (str (io/file out-dir (str base "-tai.edn")))
                      base-edn (str (io/file out-dir (str base "-baseline.edn")))
                      tai-ppm (str (io/file out-dir (str base "-tai.ppm")))
                      base-ppm (str (io/file out-dir (str base "-baseline.ppm")))
                      tai-png (str (io/file out-dir (str base "-tai.png")))
                      base-png (str (io/file out-dir (str base "-baseline.png")))
                      pair-png (str (io/file out-dir (str base "-pair.png")))]
                  (spit tai-edn (pr-str (:tai-run result)))
                  (spit base-edn (pr-str (:baseline result)))
                  (render-run! (:tai-run result) tai-ppm tai-png scale)
                  (render-run! (:baseline result) base-ppm base-png scale)
                  (pair-image! tai-png base-png pair-png)
                  (recur (rest remaining)
                         (conj acc {:seed seed
                                    :left {:arm :tai :run tai-edn :image tai-png :params (:tai result)}
                                    :right {:arm :baseline :run base-edn :image base-png}
                                    :image pair-png})
                         (inc processed))))))))))
      )

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
