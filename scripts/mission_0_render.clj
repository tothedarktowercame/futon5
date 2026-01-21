(ns mission-0-render
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.render :as render]
            [futon5.mmca.runtime :as mmca]))

(defn- usage []
  (str/join
   "\n"
   ["Render Mission 0 snapshots from an exoevolve log."
    ""
    "Usage:"
    "  bb -cp src:resources futon5/scripts/mission_0_render.clj --log PATH [options]"
    ""
    "Options:"
    "  --log PATH         Exoevolve log path (EDN lines)."
    "  --out-dir PATH     Output directory (default futon5/resources/figures)."
   "  --top-k N          Number of top runs to render (default 3)."
   "  --mid-k N          Number of mid runs to render (default 3)."
    "  --seed LIST        Comma-separated seeds to render (optional)."
    "  --label PREFIX     Label prefix (default mission-0-modest)."
    "  --help             Show this message."]))

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

          (= "--log" flag)
          (recur (rest more) (assoc opts :log (first more)))

          (= "--out-dir" flag)
          (recur (rest more) (assoc opts :out-dir (first more)))

          (= "--top-k" flag)
          (recur (rest more) (assoc opts :top-k (parse-int (first more))))

          (= "--mid-k" flag)
          (recur (rest more) (assoc opts :mid-k (parse-int (first more))))

          (= "--label" flag)
          (recur (rest more) (assoc opts :label (first more)))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- read-lines [path]
  (->> (slurp path)
       str/split-lines
       (map str/trim)
       (remove str/blank?)
       (map edn/read-string)
       vec))

(defn- pick-score [entry]
  (or (get-in entry [:score :final])
      (get-in entry [:score :short])
      0.0))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(defn- rng-phenotype-string [^java.util.Random rng length]
  (apply str (repeatedly length #(rng-int rng 2))))

(defn- render-entry!
  [entry out-dir label idx]
  (let [seed (long (:seed entry))
        length (int (:length entry))
        generations (int (:generations entry))
        rng (java.util.Random. seed)
        genotype (rng-sigil-string rng length)
        phenotype (rng-phenotype-string rng length)
        exo (exotype/resolve-exotype (:exotype entry))
        run (mmca/run-mmca {:genotype genotype
                            :phenotype phenotype
                            :generations generations
                            :kernel :mutating-template
                            :exotype exo
                            :seed seed})
        base (format "%s-%02d-seed-%d" label idx seed)
        ppm (str (io/file out-dir (str base ".ppm")))
        png (str (io/file out-dir (str base ".png")))
        edn-path (str (io/file out-dir (str base ".edn")))]
    (spit edn-path (pr-str {:label base
                            :seed seed
                            :length length
                            :generations generations
                            :exotype (:exotype entry)
                            :note "rendered from exoevolve log; genotype/phenotype regenerated from seed"}))
    (render/render-run->file! run ppm {:exotype? true})
    (shell/sh "convert" ppm png)
    {:label base :ppm ppm :png png}))

(defn- parse-seeds [s]
  (when (seq s)
    (->> (str/split s #",")
         (map str/trim)
         (remove str/blank?)
         (map parse-int)
         (remove nil?)
         vec)))

(defn -main [& args]
  (let [{:keys [help unknown log out-dir top-k mid-k label seed]} (parse-args args)
        seeds (parse-seeds seed)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      (nil? log) (do (println "Missing --log PATH") (println) (println (usage)))
      :else
      (let [out-dir (or out-dir "futon5/resources/figures")
            top-k (long (or top-k 3))
            mid-k (long (or mid-k 3))
            label (or label "mission-0-modest")
            entries (->> (read-lines log) (filter #(= :run (:event %))) vec)
            sorted (vec (sort-by pick-score > entries))
            mid-start (max 0 (quot (- (count sorted) mid-k) 2))
            top (take top-k sorted)
            mid (take mid-k (drop mid-start sorted))
            by-seed (when (seq seeds)
                      (filter (fn [entry] (some #{(:seed entry)} seeds)) entries))]
        (.mkdirs (io/file out-dir))
        (cond
          (seq by-seed)
          (doseq [[idx entry] (map-indexed vector by-seed)]
            (render-entry! entry out-dir label (inc idx)))

          :else
          (do
            (doseq [[idx entry] (map-indexed vector top)]
              (render-entry! entry out-dir (str label "-top") (inc idx)))
            (doseq [[idx entry] (map-indexed vector mid)]
              (render-entry! entry out-dir (str label "-mid") (inc idx)))))
        (println "Rendered to" out-dir)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
