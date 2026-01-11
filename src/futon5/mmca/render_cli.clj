(ns futon5.mmca.render-cli
  "Render MMCA runs into image formats."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.render :as render]
            [futon5.mmca.runtime :as mmca]))

(def ^:private default-length 64)
(def ^:private default-generations 64)

(defn- usage []
  (str/join
   "\n"
   ["MMCA render helper"
    ""
    "Usage:"
    "  bb -cp src:resources -m futon5.mmca.render-cli [options]"
    ""
    "Options:"
    "  --genotype STR        Starting sigil string (required if no --length)."
    "  --length N            Random sigil string length (default 64)."
    "  --phenotype STR       Starting phenotype bit string."
    "  --phenotype-length N  Random phenotype length."
    "  --generations N       Generations to run (default 64)."
    "  --kernel KW           Kernel keyword, e.g. :mutating-template."
    "  --mode KW             :god or :classic (default :god)."
    "  --pattern-sigils STR  Comma-separated sigils to activate operators."
    "  --no-operators        Disable MMCA operators."
    "  --lock-kernel         Ignore operator kernel changes."
    "  --freeze-genotype     Keep genotype fixed."
    "  --genotype-gate       Gate genotype changes by phenotype bits."
    "  --gate-signal BIT     Phenotype bit used as keep-signal (default 1)."
    "  --lesion              Apply mid-run lesion to half the field."
    "  --lesion-tick N        Tick to apply lesion (default mid-run)."
    "  --lesion-target T      lesion target: phenotype, genotype, or both."
    "  --lesion-half H        lesion half: left or right."
    "  --lesion-mode M        lesion mode: zero or default."
    "  --seed N              Seed for deterministic init."
    "  --input PATH          Render from an EDN run result instead of running."
    "  --out PATH            Output image path (PPM only; default mmca.ppm)."
    "  --save-run PATH       Write the run result to EDN (optional)."
    "  --help                Show this message."]))

(defn- parse-int [s]
  (try
    (Long/parseLong s)
    (catch Exception _ nil)))

(defn- parse-sigils [s]
  (let [tokens (->> (str/split (or s "") #",")
                    (map str/trim)
                    (remove str/blank?))]
    (when (seq tokens)
      (vec tokens))))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--genotype" flag)
          (recur (rest more) (assoc opts :genotype (first more)))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--phenotype" flag)
          (recur (rest more) (assoc opts :phenotype (first more)))

          (= "--phenotype-length" flag)
          (recur (rest more) (assoc opts :phenotype-length (parse-int (first more))))

          (= "--generations" flag)
          (recur (rest more) (assoc opts :generations (parse-int (first more))))

          (= "--kernel" flag)
          (recur (rest more) (assoc opts :kernel (some-> (first more) keyword)))

          (= "--mode" flag)
          (recur (rest more) (assoc opts :mode (some-> (first more) keyword)))

          (= "--pattern-sigils" flag)
          (recur (rest more) (assoc opts :pattern-sigils (parse-sigils (first more))))

          (= "--no-operators" flag)
          (recur more (assoc opts :no-operators true))

          (= "--lock-kernel" flag)
          (recur more (assoc opts :lock-kernel true))

          (= "--freeze-genotype" flag)
          (recur more (assoc opts :freeze-genotype true))

          (= "--genotype-gate" flag)
          (recur more (assoc opts :genotype-gate true))

          (= "--gate-signal" flag)
          (recur (rest more) (assoc opts :gate-signal (first more)))

          (= "--lesion" flag)
          (recur more (assoc opts :lesion true))

          (= "--lesion-tick" flag)
          (recur (rest more) (assoc opts :lesion-tick (parse-int (first more))))

          (= "--lesion-target" flag)
          (recur (rest more) (assoc opts :lesion-target (keyword (first more))))

          (= "--lesion-half" flag)
          (recur (rest more) (assoc opts :lesion-half (keyword (first more))))

          (= "--lesion-mode" flag)
          (recur (rest more) (assoc opts :lesion-mode (keyword (first more))))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

          (= "--input" flag)
          (recur (rest more) (assoc opts :input (first more)))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          (= "--save-run" flag)
          (recur (rest more) (assoc opts :save-run (first more)))

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

(defn- lesion-map [{:keys [lesion lesion-tick lesion-target lesion-half lesion-mode]}]
  (when (or lesion lesion-tick lesion-target lesion-half lesion-mode)
    (cond-> {}
      lesion-tick (assoc :tick lesion-tick)
      lesion-target (assoc :target lesion-target)
      lesion-half (assoc :half lesion-half)
      lesion-mode (assoc :mode lesion-mode))))

(defn- load-run [path]
  (edn/read-string (slurp path)))

(defn -main [& args]
  (let [{:keys [help unknown genotype length phenotype phenotype-length generations kernel mode
                pattern-sigils no-operators lock-kernel freeze-genotype genotype-gate
                gate-signal seed input out save-run] :as opts}
        (parse-args args)]
    (cond
      help
      (println (usage))

      unknown
      (do
        (println "Unknown option:" unknown)
        (println)
        (println (usage)))

      :else
      (let [out (or out "mmca.ppm")
            {:keys [result fresh?]}
            (if input
              {:result (load-run input)
               :fresh? false}
              (let [length (or length default-length)
                         generations (or generations default-generations)
                         rng (when seed (java.util.Random. (long seed)))
                         genotype (or genotype
                                      (when rng (rng-sigil-string rng length))
                                      (ca/random-sigil-string length))
                         phenotype (or phenotype
                                       (when phenotype-length
                                         (let [phe-len (or phenotype-length length)]
                                           (if rng
                                             (rng-phenotype-string rng phe-len)
                                             (ca/random-phenotype-string phe-len)))))
                         base {:genotype genotype
                               :generations generations
                               :mode (or mode :god)}
                         base (cond-> base
                                kernel (assoc :kernel kernel)
                                phenotype (assoc :phenotype phenotype)
                                lock-kernel (assoc :lock-kernel true)
                                freeze-genotype (assoc :freeze-genotype true)
                                genotype-gate (assoc :genotype-gate true)
                                gate-signal (assoc :genotype-gate-signal (first gate-signal))
                                (seq pattern-sigils) (assoc :pattern-sigils pattern-sigils)
                                (lesion-map opts) (assoc :lesion (lesion-map opts)))
                         opts (cond-> base
                                no-operators (assoc :operators []))]
                     {:result (mmca/run-mmca opts)
                      :fresh? true})))]
        (render/render-run->file! result out)
        (when (and save-run (or fresh? input))
          (spit save-run (pr-str result))
          (println "Saved run" save-run))
        (println "Wrote" out)))))
