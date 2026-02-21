(ns scripts.tensor-legacy-adapter-report
  "Adapt legacy best-of-class seeds into tensor tokamak runs and render a visual report."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.render :as render]))

(def ^:private legacy-cases
  [{:id :champion-352362012
    :label "Champion seed 352362012"
    :seed 352362012
    :generations 80
    :genotype "下为八尤午火大勿个土叫心五丸石扔巨刊认毛占术世日巴节土帅史忆击二亏风六力双毛厂无劝友风乎令兄心艺北刊白尤无劝只且飞日凡风布尸午田风及仓弓厂火书父车已飞一且付丸仅打从艺仪凡用电元井升犬刊刊支火不丸手白心二仗上乌由见元于刀允仔平一扔叮叫从大北印"
    :phenotype "000100000000101101110100111011111111111010101110000010000100011001110011110100000010110111010111000001011000110001000011"}
   {:id :baseline-4242
    :label "Baseline seed 4242"
    :seed 4242
    :generations 80
    :genotype "下为八尤午火大勿个土叫心五丸石扔巨刊认毛占术世日巴节土帅史忆击二亏风六力双毛厂无劝友风乎令兄心艺北刊白尤无劝只且飞日凡风布尸午田风及仓弓厂火书父车已飞一且付丸仅打从艺仪凡用电元井升犬刊刊支火不丸手白心二仗上乌由见元于刀允仔平一扔叮叫从大北印"
    :phenotype "000100000000101101110100111011111111111010101110000010000100011001110011110100000010110111010111000001011000110001000011"}
   {:id :m07a-top2
   :label "Mission 7A top #2"
    :seed 1701878710
    :length 50
    :generations 40
    :genotype "下为八尤午火大勿个土叫心五丸石扔巨刊认毛占术世日巴节土帅史忆击二亏风六力双毛厂无劝友风乎令兄心"
    :phenotype "00010000000010110111010011101111111111101010111000"}
   {:id :m07b-top1
   :label "Mission 7B top #1 (legacy 兄 super exotype)"
    :seed 1695261645
    :length 50
    :generations 40
    :genotype "下为八尤午火大勿个土叫心五丸石扔巨刊认毛占术世日巴节土帅史忆击二亏风六力双毛厂无劝友风乎令兄心"
    :phenotype "00010000000010110111010011101111111111101010111000"
    :legacy-exotype-note "legacy exotype not directly mapped; tensor exotype program used"}
   {:id :arrow-ding-super
   :label "Arrow pilot 丁 super"
    :seed 1188240613
    :length 32
    :generations 40
    :genotype "下为八尤午火大勿个土叫心五丸石扔巨刊认毛占术世日巴节土帅史忆击二"
    :phenotype "01101000101111101110011100011001"
    :legacy-exotype-note "legacy exotype not directly mapped; tensor exotype program used"}])

(defn- usage []
  (str/join
   "\n"
   ["usage: bb -m scripts.tensor-legacy-adapter-report [options]"
    ""
    "Options:"
    "  --out-dir PATH      Output directory (default out/tokamak/legacy-adapt-v1)."
    "  --rule SIGIL        Initial tensor rule sigil (default 手)."
    "  --gens N            Override generations for all cases."
    "  --help              Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (case flag
          "--out-dir" (recur (rest more) (assoc opts :out-dir (first more)))
          "--rule" (recur (rest more) (assoc opts :rule (first more)))
          "--gens" (recur (rest more) (assoc opts :gens (parse-int (first more))))
          "--help" (recur more (assoc opts :help true))
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- ensure-dir! [path]
  (doto (io/file path) .mkdirs)
  path)

(defn- has-convert? []
  (zero? (:exit (shell/sh "bash" "-lc" "command -v convert >/dev/null 2>&1"))))

(defn- ppm->png! [ppm png]
  (let [res (shell/sh "convert" ppm png)]
    (when-not (zero? (:exit res))
      (throw (ex-info "convert failed" {:ppm ppm :png png :err (:err res) :out (:out res)})))
    png))

(defn- short-f [x]
  (format "%.3f" (double (or x 0.0))))

(defn- random-sigil [^java.util.Random rng sigils]
  (nth sigils (.nextInt rng (count sigils))))

(defn- seeded-initial-state [seed length]
  (let [rng (java.util.Random. (long seed))
        sigils (mapv :sigil (ca/sigil-entries))
        genotype (apply str (repeatedly (long length) #(random-sigil rng sigils)))
        phenotype (apply str (repeatedly (long length) #(if (< (.nextDouble rng) 0.5) \1 \0)))]
    {:genotype genotype
     :phenotype phenotype
     :source :seeded-reconstruction}))

(defn- resolve-initial-state [case]
  (let [g (:genotype case)
        p (:phenotype case)
        length (:length case)]
    (cond
      (and (seq g) (seq p) (= (count g) (count p)))
      {:genotype g
       :phenotype p
       :source :legacy-explicit}

      (and (number? length) (number? (:seed case)))
      (let [seeded (seeded-initial-state (:seed case) length)]
        (assoc seeded
               :source :seeded-reconstruction
               :note (when (and (seq g) (seq p) (not= (count g) (count p)))
                       (str "legacy genotype/phenotype width mismatch ("
                            (count g) " vs " (count p)
                            "); used deterministic seed+length reconstruction"))))

      :else
      (throw (ex-info "cannot resolve initial state"
                      {:id (:id case)
                       :has-genotype? (boolean (seq g))
                       :has-phenotype? (boolean (seq p))
                       :length length
                       :seed (:seed case)})))))

(defn- rel-path [base-file target]
  (let [base (-> (io/file base-file) .getParentFile .toPath .toAbsolutePath .normalize)
        target (-> (io/file target) .toPath .toAbsolutePath .normalize)]
    (-> (str (.relativize base target))
        (str/replace "\\" "/"))))

(defn- exo-stats [run]
  (let [ledger (vec (or (:ledger run) []))]
    {:attempts (count (filter #(get-in % [:mutation :exotype :attempted?]) ledger))
     :selected (count (filter #(get-in % [:mutation :exotype :selected?]) ledger))
     :cells (reduce + 0 (map #(long (or (get-in % [:mutation :exotype :tilt :changed-cells]) 0))
                             ledger))}))

(defn- run-case!
  [case {:keys [out-dir rule gens use-png?]}]
  (let [id-name (name (:id case))
        {:keys [genotype phenotype source note]} (resolve-initial-state case)
        generations (long (or gens (:generations case) 40))
        length (count genotype)
        out-run-dir (str (io/file out-dir "runs"))
        out-img-dir (str (io/file out-dir "images"))
        out-edn (str (io/file out-run-dir (str id-name ".edn")))
        out-md (str (io/file out-run-dir (str id-name ".md")))
        _ (ensure-dir! out-run-dir)
        _ (ensure-dir! out-img-dir)
        cmd (cond-> ["bb" "-m" "scripts.tensor-tokamak"
                     "--seed" (str (:seed case))
                     "--length" (str length)
                     "--generations" (str generations)
                     "--init-genotype" genotype
                     "--init-rule-sigil" (or rule "手")
                     "--out" out-edn
                     "--report" out-md]
              (seq phenotype)
              (into ["--phenotype" phenotype]))
        res (apply shell/sh cmd)]
    (when-not (zero? (:exit res))
      (throw (ex-info "tensor tokamak run failed"
                      {:id (:id case)
                       :cmd cmd
                       :exit (:exit res)
                       :err (:err res)
                       :out (:out res)})))
    (let [run (edn/read-string (slurp out-edn))
          img-ppm (str (io/file out-img-dir (str id-name ".ppm")))
          img-png (str (io/file out-img-dir (str id-name ".png")))
          _ (render/render-run->file! {:gen-history (get-in run [:final :gen-history])
                                       :phe-history (get-in run [:final :phe-history])}
                                      img-ppm
                                      {:exotype? true})
          img-path (if use-png? (ppm->png! img-ppm img-png) img-ppm)
          exo (exo-stats run)
          summary (or (:summary run) {})]
      {:id (:id case)
       :label (:label case)
       :seed (:seed case)
       :init-source source
       :init-note note
       :legacy-exotype-note (:legacy-exotype-note case)
       :generations generations
       :length length
       :best-total (double (or (:best-total summary) 0.0))
       :final-total (double (or (:final-total summary) 0.0))
       :final-regime (:final-regime summary)
       :rewinds (long (or (:rewinds summary) 0))
       :attempts (:attempts exo)
       :selected (:selected exo)
       :cells (:cells exo)
       :run-edn out-edn
       :run-md out-md
       :img-path img-path})))

(defn- write-report! [out-md rows]
  (let [lines (concat
               ["# Tensor Legacy Adapter Report"
                ""
                "- Goal: replay legacy best-of-class initial conditions in tensor tokamak."
                "- Mapping used: legacy `genotype/phenotype` are ported directly; legacy exotype kernels are not directly portable and are approximated by tensor exotype program dynamics."
                "- Images are triptychs: genotype | phenotype | exotype."
                ""
                "| Case | Seed | Len | Gens | Best | Final | Regime | Rewinds | Exo sel/att | Cells |"
                "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |"]
               (map (fn [r]
                      (format "| %s | %d | %d | %d | %s | %s | %s | %d | %d/%d | %d |"
                              (name (:id r))
                              (long (:seed r))
                              (long (:length r))
                              (long (:generations r))
                              (short-f (:best-total r))
                              (short-f (:final-total r))
                              (name (or (:final-regime r) :unknown))
                              (long (:rewinds r))
                              (long (:selected r))
                              (long (:attempts r))
                              (long (:cells r))))
                    rows)
               [""]
               (mapcat (fn [r]
                         [(str "## " (:label r))
                          ""
                          (format "- seed `%d`, final `%s`, regime `%s`, rewinds `%d`"
                                  (long (:seed r))
                                  (short-f (:final-total r))
                                  (name (or (:final-regime r) :unknown))
                                  (long (:rewinds r)))
                          (format "- exotype selected/attempts `%d/%d`, changed-cells `%d`"
                                  (long (:selected r))
                                  (long (:attempts r))
                                  (long (:cells r)))
                          (str "- init-source: `" (name (:init-source r)) "`")
                          (when-let [n (:init-note r)]
                            (str "- init-note: " n))
                          (str "- run artifacts: [" (rel-path out-md (:run-md r)) "](" (rel-path out-md (:run-md r))
                               "), [" (rel-path out-md (:run-edn r)) "](" (rel-path out-md (:run-edn r)) ")")
                          (when-let [n (:legacy-exotype-note r)]
                            (str "- note: " n))
                          ""
                          (str "![](" (rel-path out-md (:img-path r)) ")")
                          ""])
                       rows))]
    (spit out-md (str/join "\n" (remove nil? lines)))
    out-md))

(defn -main [& args]
  (let [{:keys [help unknown out-dir rule gens]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do
                (println "Unknown option:" unknown)
                (println)
                (println (usage))
                (System/exit 2))
      :else
      (let [out-dir (or out-dir "out/tokamak/legacy-adapt-v1")
            use-png? (has-convert?)
            rows (mapv #(run-case! % {:out-dir out-dir
                                       :rule (or rule "手")
                                       :gens gens
                                       :use-png? use-png?})
                       legacy-cases)
            out-md (str (io/file out-dir "README.md"))]
        (ensure-dir! out-dir)
        (write-report! out-md rows)
        (println "Wrote" out-md)
        (println "Runs:" (count rows))
        (println "PNG conversion:" (if use-png? "enabled" "disabled"))))))
