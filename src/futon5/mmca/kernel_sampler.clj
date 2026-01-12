(ns futon5.mmca.kernel-sampler
  "Generate a visual sampler for each available CA kernel."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.render :as render]))

(def ^:private default-length 64)
(def ^:private default-generations 64)
(def ^:private default-samples 1)
(def ^:private default-kernel-order
  [:multiplication
   :blending
   :blending-3
   :blending-flip
   :blending-mutation
   :blending-baldwin
   :ad-hoc-template
   :collection-template
   :mutating-template])

(defn- usage []
  (str/join
   "\n"
   ["MMCA kernel sampler"
    ""
    "Usage:"
    "  bb -cp src:resources -m futon5.mmca.kernel-sampler [options]"
    ""
    "Options:"
    "  --genotype STR        Starting sigil string (optional)."
    "  --length N            Random sigil length (default 64)."
    "  --phenotype STR       Starting phenotype bit string."
    "  --phenotype-length N  Random phenotype length (default length)."
    "  --no-phenotype        Skip phenotype evolution."
    "  --generations N       Generations to run (default 64)."
    "  --samples N           Samples per kernel (default 1)."
    "  --seed N              Seed for deterministic init."
    "  --kernels LIST        Comma-separated kernel keywords."
    "  --out-dir PATH        Output directory (default ./mmca_kernel_sampler)."
    "  --out-pdf PATH        Render images into a PDF (optional)."
    "  --summary PATH        Write EDN summary (optional)."
    "  --help                Show this message."]))

(defn- parse-int [s]
  (try
    (Long/parseLong s)
    (catch Exception _ nil)))

(defn- parse-kernels [s]
  (let [tokens (->> (str/split (or s "") #",")
                    (map str/trim)
                    (remove str/blank?))]
    (when (seq tokens)
      (mapv (fn [token]
              (let [token (if (str/starts-with? token ":")
                            (subs token 1)
                            token)]
                (keyword token)))
            tokens))))

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

          (= "--no-phenotype" flag)
          (recur more (assoc opts :no-phenotype true))

          (= "--generations" flag)
          (recur (rest more) (assoc opts :generations (parse-int (first more))))

          (= "--samples" flag)
          (recur (rest more) (assoc opts :samples (parse-int (first more))))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

          (= "--kernels" flag)
          (recur (rest more) (assoc opts :kernels (parse-kernels (first more))))

          (= "--out-dir" flag)
          (recur (rest more) (assoc opts :out-dir (first more)))

          (= "--out-pdf" flag)
          (recur (rest more) (assoc opts :out-pdf (first more)))

          (= "--summary" flag)
          (recur (rest more) (assoc opts :summary (first more)))

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

(defn- ensure-dir! [path]
  (let [f (io/file path)]
    (.mkdirs f)
    path))

(defn- safe-name [s]
  (-> s str (str/replace #"[^a-zA-Z0-9._-]" "_")))

(defn- summarize-history [gen-history phe-history]
  (let [unique-states (count (distinct gen-history))
        unique-sigils (count (set (apply str gen-history)))
        avg-hamming (ca/average-hamming gen-history)
        stasis (ca/first-stasis-step gen-history)
        zero-step (when phe-history (ca/zero-phenotype-step phe-history))]
    {:unique-states unique-states
     :unique-sigils unique-sigils
     :average-hamming avg-hamming
     :stasis-step stasis
     :phenotype-zero-step zero-step}))

(defn- render-pdf! [image-paths out]
  (when (seq image-paths)
    (apply shell/sh
           (concat ["convert"]
                   image-paths
                   [out]))))

(defn -main [& args]
  (let [{:keys [help unknown genotype length phenotype phenotype-length no-phenotype
                generations samples seed kernels out-dir out-pdf summary] :as opts}
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
      (let [length (or length default-length)
            generations (or generations default-generations)
            samples (max 1 (long (or samples default-samples)))
            rng (when seed (java.util.Random. (long seed)))
            kernel-order (or (seq kernels)
                             (let [base default-kernel-order
                                   extras (->> (keys ca/kernels)
                                               (remove (set base))
                                               sort)]
                               (vec (concat base extras))))
            missing (seq (remove ca/kernels kernel-order))
            next-genotype (fn []
                            (or genotype
                                (when rng (rng-sigil-string rng length))
                                (ca/random-sigil-string length)))
            next-phenotype (fn []
                             (when-not no-phenotype
                               (or phenotype
                                   (let [phe-len (or phenotype-length length)]
                                     (if rng
                                       (rng-phenotype-string rng phe-len)
                                       (ca/random-phenotype-string phe-len))))))
            sample-inits (mapv (fn [idx]
                                 {:sample (inc idx)
                                  :genotype (next-genotype)
                                  :phenotype (next-phenotype)})
                               (range samples))]
        (when missing
          (throw (ex-info "Unknown kernels requested"
                          {:kernels missing
                           :available (sort (keys ca/kernels))})))
        (let [out-dir (ensure-dir! (or out-dir "./mmca_kernel_sampler"))
              runs (->> kernel-order
                        (mapcat (fn [kernel]
                                  (map (fn [{:keys [sample genotype phenotype]}]
                                         (let [result (ca/simulate-kernel {:kernel kernel
                                                                          :genotype genotype
                                                                          :phenotype phenotype
                                                                          :generations generations})
                                               base (safe-name (format "s%02d_%s" sample (name kernel)))
                                               img-path (str out-dir "/" base ".ppm")
                                               summary (summarize-history (:gen-history result)
                                                                          (:phe-history result))]
                                           (render/render-run->file! result img-path)
                                           (println (format "s%02d %-22s -> %s"
                                                            sample (name kernel) img-path))
                                           (merge {:kernel kernel
                                                   :sample sample
                                                   :image img-path
                                                   :generations generations
                                                   :final-genotype (last (:gen-history result))}
                                                  summary)))
                                       sample-inits)))
                        vec)
              images (mapv :image runs)]
          (when summary
            (spit summary
                  (pr-str {:samples sample-inits
                           :generations generations
                           :length length
                           :kernels kernel-order
                           :runs runs}))
            (println "Wrote summary" summary))
          (when out-pdf
            (render-pdf! images out-pdf)
            (println "Wrote PDF" out-pdf)))))))
