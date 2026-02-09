(ns exotype-behavior-demo
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.render :as render]
            [futon5.mmca.runtime :as runtime]
            [futon5.scripts.output :as out]))

(defn- usage []
  (str/join
   "\n"
   ["Exotype behavior demo: generate runs + renders for three behaviors."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/exotype_behavior_demo.clj [options]"
    ""
    "Options:"
    "  --out-dir PATH         Output directory (default /tmp/futon5-exotype-demo)"
    "  --runs N               Runs per behavior (default 10)"
    "  --generations N        Generations per run (default 12)"
    "  --length N             Genotype length (default 12)"
    "  --phenotype-length N   Phenotype length (default = length)"
    "  --seed-base N          Base seed (default 4242)"
    "  --combined             Include combined run (inline + bending + strictness)"
    "  --mutation-rate P      Combined: override mutation-rate (default 0.05)"
    "  --template-strictness P Combined: template strictness (default 0.5)"
    "  --no-phenotype         Disable phenotype generation"
    "  --no-render            Skip PPM renders"
    "  --help                 Show this message"]))

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

          (= "--out-dir" flag)
          (recur (rest more) (assoc opts :out-dir (first more)))

          (= "--runs" flag)
          (recur (rest more) (assoc opts :runs (parse-int (first more))))

          (= "--generations" flag)
          (recur (rest more) (assoc opts :generations (parse-int (first more))))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--phenotype-length" flag)
          (recur (rest more) (assoc opts :phenotype-length (parse-int (first more))))

          (= "--seed-base" flag)
          (recur (rest more) (assoc opts :seed-base (parse-int (first more))))

          (= "--combined" flag)
          (recur more (assoc opts :combined true))

          (= "--mutation-rate" flag)
          (recur (rest more) (assoc opts :mutation-rate (parse-double (first more))))

          (= "--template-strictness" flag)
          (recur (rest more) (assoc opts :template-strictness (parse-double (first more))))

          (= "--no-phenotype" flag)
          (recur more (assoc opts :no-phenotype true))

          (= "--no-render" flag)
          (recur more (assoc opts :no-render true))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- ensure-resources! []
  (when-not (io/resource "futon5/sigils.edn")
    (when-let [add-cp (resolve 'babashka.classpath/add-classpath)]
      (add-cp "futon5/resources"))
    (when-not (io/resource "futon5/sigils.edn")
      (throw (ex-info "Missing futon5/resources on classpath."
                      {:hint "Use: bb -cp futon5/src:futon5/resources ..."})))))

(defn- rng
  [seed]
  (java.util.Random. (long seed)))

(defn- rand-nth*
  [^java.util.Random rng coll]
  (nth coll (.nextInt rng (count coll))))

(defn- random-sigil-string
  [^java.util.Random rng sigils len]
  (apply str (repeatedly len #(rand-nth* rng sigils))))

(defn- random-phenotype-string
  [^java.util.Random rng len]
  (apply str (repeatedly len #(if (< (.nextDouble rng) 0.5) \0 \1))))

(defn- write-run!
  [{:keys [out-dir group name run render?]}]
  (let [dir (io/file out-dir group)
        _ (.mkdirs dir)
        edn-path (io/file dir (str name ".edn"))
        ppm-path (io/file dir (str name ".ppm"))]
    (out/warn-overwrite-file! (.getPath edn-path))
    (spit edn-path (pr-str run))
    (when render?
      (out/warn-overwrite-file! (.getPath ppm-path))
      (render/render-run->file! run (.getPath ppm-path) {:exotype? true}))
    {:edn (.getPath edn-path)
     :ppm (when render? (.getPath ppm-path))}))

(defn- run-inline
  [{:keys [seed length phenotype-length generations kernel sigil]}]
  (let [genotype (random-sigil-string (rng seed) (map :sigil (ca/sigil-entries)) length)
        phenotype (when phenotype-length
                    (random-phenotype-string (rng (+ seed 99)) phenotype-length))
        exo (exotype/resolve-exotype {:sigil sigil :tier :super})]
    (assoc (runtime/run-mmca {:genotype genotype
                              :phenotype phenotype
                              :generations generations
                              :kernel kernel
                              :exotype exo
                              :seed seed
                              :exotype-context-depth 1
                              :exotype-mode :inline})
           :demo {:behavior :inline
                  :seed seed
                  :sigil sigil})))

(defn- evolve-bent
  [{:keys [seed length phenotype-length generations global-rule bend-mode]}]
  (let [sigils (map :sigil (ca/sigil-entries))
        rng* (rng seed)
        genotype (random-sigil-string rng* sigils length)
        phenotype (when phenotype-length
                    (random-phenotype-string rng* phenotype-length))
        kernels ca/kernels]
    (loop [step 0
           prev genotype
           gen genotype
           phe phenotype
           gen-history [genotype]
           phe-history (when phenotype [phenotype])]
      (if (>= step generations)
        {:gen-history gen-history
         :phe-history phe-history
         :genotype (last gen-history)
         :phenotype (last phe-history)
         :kernel :bent
         :demo {:behavior :bent
                :seed seed
                :global-rule global-rule
                :bend-mode bend-mode}}
        (let [next-phe (when phe (ca/evolve-phenotype-against-genotype gen phe))
              next-gen (exotype/evolve-with-global-exotype gen phe prev global-rule bend-mode kernels rng*)
              gen-history' (conj gen-history next-gen)
              phe-history' (when phe-history (conj phe-history next-phe))]
          (recur (inc step) gen next-gen next-phe gen-history' phe-history'))))))

(defn- evolve-local-with-strictness
  [{:keys [seed length phenotype-length generations strictness]}]
  (let [sigils (map :sigil (ca/sigil-entries))
        rng* (rng seed)
        genotype (random-sigil-string rng* sigils length)
        phenotype (when phenotype-length
                    (random-phenotype-string rng* phenotype-length))
        kernels ca/kernels
        evolve-once (fn [gen phe prev]
                      (let [len (count gen)
                            letters (vec (map str (seq gen)))
                            phe-chars (vec (seq (or phe (apply str (repeat len "0")))))
                            prev-letters (vec (map str (seq (or prev gen))))
                            default ca/default-sigil
                            results
                            (mapv (fn [idx]
                                    (let [pred (get letters (dec idx) default)
                                          ego (get letters idx)
                                          succ (get letters (inc idx) default)
                                          prev* (get prev-letters idx ego)
                                          phe (str (get phe-chars idx \0)
                                                   (get phe-chars (inc idx) \0)
                                                   (get phe-chars (+ idx 2) \0)
                                                   (get phe-chars (+ idx 3) \0))
                                          context (exotype/build-local-context pred ego succ prev* phe)
                                          {:keys [kernel params rule]} (exotype/context->kernel-spec context)
                                          kernel-fn (get kernels kernel (get kernels :mutating-template))
                                          ctx (merge context params {:template-strictness strictness})
                                          result (kernel-fn ego pred succ ctx)]
                                      {:sigil (:sigil result)
                                       :rule rule
                                       :kernel kernel}))
                                  (range len))]
                        {:genotype (apply str (map :sigil results))
                         :rules (mapv :rule results)
                         :kernels (mapv :kernel results)}))]
    (loop [step 0
           prev genotype
           gen genotype
           phe phenotype
           gen-history [genotype]
           phe-history (when phenotype [phenotype])]
      (if (>= step generations)
        {:gen-history gen-history
         :phe-history phe-history
         :genotype (last gen-history)
         :phenotype (last phe-history)
         :kernel :local
         :demo {:behavior :template-strictness
                :strictness strictness
                :seed seed}}
        (let [next-phe (when phe (ca/evolve-phenotype-against-genotype gen phe))
              next-gen (:genotype (evolve-once gen phe prev))
              gen-history' (conj gen-history next-gen)
              phe-history' (when phe-history (conj phe-history next-phe))]
          (recur (inc step) gen next-gen next-phe gen-history' phe-history'))))))

(defn- kernel-overrides
  [kernel-spec]
  (select-keys (ca/normalize-kernel-spec kernel-spec)
               [:blend-mode :flip? :template-mode :mutation :balance :mix-mode :mix-shift]))

(defn- kernel-fn-map-with-overrides
  [overrides]
  (into {}
        (for [k (keys ca/kernels)]
          (let [base (ca/normalize-kernel-spec (ca/kernel-spec-for k))
                spec (merge base overrides)]
            [k (ca/kernel-spec->fn spec)]))))

(defn- wrap-kernel-fns
  [kernel-fn-map ctx-overrides]
  (into {}
        (map (fn [[k f]]
               [k (fn [sigil pred next context]
                    (f sigil pred next (merge context ctx-overrides)))])
             kernel-fn-map)))

(defn- evolve-combined
  [{:keys [seed length phenotype-length generations strictness mutation-rate global-rule bend-mode sigil]}]
  (let [sigils (map :sigil (ca/sigil-entries))
        rng* (rng seed)
        genotype (random-sigil-string rng* sigils length)
        phenotype (when phenotype-length
                    (random-phenotype-string rng* phenotype-length))
        exo (exotype/resolve-exotype {:sigil sigil :tier :super})
        kernel-spec (ca/kernel-spec-for :mutating-template)]
    (loop [step 0
           prev genotype
           gen genotype
           phe phenotype
           kernel-spec kernel-spec
           gen-history [genotype]
           phe-history (when phenotype [phenotype])]
      (if (>= step generations)
        {:gen-history gen-history
         :phe-history phe-history
         :genotype (last gen-history)
         :phenotype (last phe-history)
         :kernel :combined
         :demo {:behavior :combined
                :seed seed
                :sigil sigil
                :global-rule global-rule
                :bend-mode bend-mode
                :template-strictness strictness
                :mutation-rate mutation-rate}}
        (let [ctx (exotype/sample-context gen-history phe-history rng*)
              kernel-spec' (or (and ctx (exotype/apply-exotype kernel-spec exo ctx rng*))
                               kernel-spec)
              overrides (kernel-overrides kernel-spec')
              kernel-fn-map (kernel-fn-map-with-overrides overrides)
              kernel-fn-map (wrap-kernel-fns kernel-fn-map {:template-strictness strictness
                                                            :mutation-rate mutation-rate})
              next-phe (when phe (ca/evolve-phenotype-against-genotype gen phe))
              next-gen (exotype/evolve-with-global-exotype gen phe prev global-rule bend-mode kernel-fn-map rng*)
              gen-history' (conj gen-history next-gen)
              phe-history' (when phe-history (conj phe-history next-phe))]
          (recur (inc step) gen next-gen next-phe kernel-spec' gen-history' phe-history'))))))

(defn -main [& args]
  (let [{:keys [help unknown out-dir runs generations length phenotype-length seed-base
                no-phenotype no-render combined mutation-rate template-strictness]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      :else
      (do
        (ensure-resources!)
        (let [out-dir (or out-dir "/tmp/futon5-exotype-demo")
              runs (max 1 (int (or runs 10)))
              generations (max 1 (int (or generations 12)))
              length (max 1 (int (or length 12)))
              phenotype-length (when-not no-phenotype
                                 (max 1 (int (or phenotype-length length))))
              seed-base (long (or seed-base 4242))
              sigils (map :sigil (ca/sigil-entries))
              inline-group "A_inline"
              bend-group "B_bend"
              strict-group "C_strictness"
              combined-group "D_combined"
              global-rules [:blending :conservative :adaptive :transformative]
              bend-mode :matrix
              mutation-rate (double (or mutation-rate 0.05))
              template-strictness (double (or template-strictness 0.5))
              manifest (atom [])]
          (out/warn-overwrite-dir! out-dir)
          (println "Generating A inline runs...")
          (dotimes [i runs]
            (let [seed (+ seed-base i)
                  sigil (nth sigils (mod (+ i 3) (count sigils)))
                  name (format "inline_%02d" i)
                  run (run-inline {:seed seed
                                   :length length
                                   :phenotype-length phenotype-length
                                   :generations generations
                                   :kernel :mutating-template
                                   :sigil sigil})
                  files (write-run! {:out-dir out-dir
                                     :group inline-group
                                     :name name
                                     :run run
                                     :render? (not no-render)})]
              (swap! manifest conj (merge {:behavior :inline
                                           :seed seed
                                           :name name
                                           :group inline-group}
                                          files))))
          (println "Generating B bending runs...")
          (dotimes [i runs]
            (let [seed (+ seed-base 100 i)
                  global-rule (nth global-rules (mod i (count global-rules)))
                  name (format "bend_%02d" i)
                  run (evolve-bent {:seed seed
                                    :length length
                                    :phenotype-length phenotype-length
                                    :generations generations
                                    :global-rule global-rule
                                    :bend-mode bend-mode})
                  files (write-run! {:out-dir out-dir
                                     :group bend-group
                                     :name name
                                     :run run
                                     :render? (not no-render)})]
              (swap! manifest conj (merge {:behavior :bent
                                           :seed seed
                                           :name name
                                           :group bend-group
                                           :global-rule global-rule
                                           :bend-mode bend-mode}
                                          files))))
          (println "Generating C strictness runs (hi/lo pairs)...")
          (dotimes [i runs]
            (let [seed (+ seed-base 200 i)
                  name-hi (format "strict_hi_%02d" i)
                  name-lo (format "strict_lo_%02d" i)
                  run-hi (evolve-local-with-strictness {:seed seed
                                                        :length length
                                                        :phenotype-length phenotype-length
                                                        :generations generations
                                                        :strictness 1.0})
                  run-lo (evolve-local-with-strictness {:seed seed
                                                        :length length
                                                        :phenotype-length phenotype-length
                                                        :generations generations
                                                        :strictness 0.0})
                  files-hi (write-run! {:out-dir out-dir
                                        :group strict-group
                                        :name name-hi
                                        :run run-hi
                                        :render? (not no-render)})
                  files-lo (write-run! {:out-dir out-dir
                                        :group strict-group
                                        :name name-lo
                                        :run run-lo
                                        :render? (not no-render)})]
              (swap! manifest into [(merge {:behavior :template-strictness
                                             :strictness 1.0
                                             :seed seed
                                             :name name-hi
                                             :group strict-group}
                                            files-hi)
                                     (merge {:behavior :template-strictness
                                             :strictness 0.0
                                             :seed seed
                                             :name name-lo
                                             :group strict-group}
                                            files-lo)])))
          (when combined
            (println "Generating D combined runs (low mutation)...")
            (dotimes [i runs]
              (let [seed (+ seed-base 300 i)
                    sigil (nth sigils (mod (+ i 5) (count sigils)))
                    global-rule (nth global-rules (mod i (count global-rules)))
                    name (format "combined_%02d" i)
                    run (evolve-combined {:seed seed
                                          :length length
                                          :phenotype-length phenotype-length
                                          :generations generations
                                          :strictness template-strictness
                                          :mutation-rate mutation-rate
                                          :global-rule global-rule
                                          :bend-mode bend-mode
                                          :sigil sigil})
                    files (write-run! {:out-dir out-dir
                                       :group combined-group
                                       :name name
                                       :run run
                                       :render? (not no-render)})]
                (swap! manifest conj (merge {:behavior :combined
                                             :seed seed
                                             :name name
                                             :group combined-group
                                             :global-rule global-rule
                                             :bend-mode bend-mode
                                             :template-strictness template-strictness
                                             :mutation-rate mutation-rate}
                                            files)))))
          (let [manifest-path (io/file out-dir "manifest.edn")]
            (.mkdirs (.getParentFile manifest-path))
            (out/warn-overwrite-file! (.getPath manifest-path))
            (spit manifest-path (pr-str @manifest))
            (println "Wrote" (count @manifest) "entries to" (out/abs-path (.getPath manifest-path)))
            (println "Outputs saved to" (out/abs-path out-dir))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
