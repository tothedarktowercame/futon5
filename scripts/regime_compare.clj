(ns regime-compare
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
   ["Run a fixed-seed regime set with comparable settings and triptych renders."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/regime_compare.clj [options]"
    ""
    "Options:"
    "  --regimes PATH     Regime EDN (default futon5/resources/regimes-typology.edn)."
    "  --seed N           RNG seed (default 4242)."
    "  --length N         Genotype length (default 50)."
    "  --generations N    Generations (default 60)."
    "  --out-dir PATH     Output directory (default futon5/resources/figures)."
    "  --label PREFIX     Filename prefix (default regime-compare)."
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

          (= "--regimes" flag)
          (recur (rest more) (assoc opts :regimes (first more)))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--generations" flag)
          (recur (rest more) (assoc opts :generations (parse-int (first more))))

          (= "--out-dir" flag)
          (recur (rest more) (assoc opts :out-dir (first more)))

          (= "--label" flag)
          (recur (rest more) (assoc opts :label (first more)))

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

(defn- sanitize-label [s]
  (-> s
      (str/replace #"\s+" "-")
      (str/replace #"[^a-zA-Z0-9._-]" "")))

(defn- resolve-exotype
  [exo]
  (when exo
    (exotype/resolve-exotype exo)))

(defn- render-run!
  [run out-dir label]
  (let [ppm (str (io/file out-dir (str label ".ppm")))
        png (str (io/file out-dir (str label ".png")))]
    (render/render-run->file! run ppm {:exotype? true})
    (shell/sh "convert" ppm png)
    {:ppm ppm :png png}))

(defn -main [& args]
  (let [{:keys [help unknown regimes seed length generations out-dir label]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      :else
      (let [regimes-path (or regimes "futon5/resources/regimes-typology.edn")
            regimes (get (edn/read-string (slurp regimes-path)) :regimes)
            seed (long (or seed 4242))
            length (int (or length 50))
            generations (int (or generations 60))
            out-dir (or out-dir "futon5/resources/figures")
            label (or label "regime-compare")
            rng (java.util.Random. seed)
            base-genotype (rng-sigil-string rng length)
            base-phenotype (rng-phenotype-string rng length)]
        (.mkdirs (io/file out-dir))
        (doseq [{:keys [name label exotype genotype phenotype generations] :as regime} regimes]
          (let [genotype (or genotype base-genotype)
                phenotype (or phenotype base-phenotype)
                generations (int (or generations generations))
                length (count (str genotype))
                run (mmca/run-mmca {:genotype genotype
                                    :phenotype phenotype
                                    :generations generations
                                    :kernel :mutating-template
                                    :operators []
                                    :exotype (resolve-exotype exotype)
                                    :seed seed})
                base (sanitize-label (str label "-" (or name label)))
                rendered (render-run! run out-dir base)
                meta-path (str (io/file out-dir (str base ".edn")))]
            (spit meta-path
                  (pr-str {:label base
                           :seed seed
                           :length length
                           :generations generations
                           :exotype exotype
                           :regime regime
                           :note "fixed-seed regime compare"}))
            (println "Rendered" (get rendered :png))))
        (println "Done.")))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
