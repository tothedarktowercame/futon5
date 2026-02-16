(ns cyber-mmca-disagreement-hit
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.runtime :as runtime]
            [futon5.scripts.output :as out]
            [futon5.xenotype.interpret :as interpret]
            [futon5.xenotype.wiring :as wiring]))

(defn- usage []
  (str/join
   "\n"
   ["Run wiring vs hex controllers and export disagreement-focused HIT inputs."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_disagreement_hit.clj [options]"
    ""
    "Options:"
    "  --seeds LIST        Comma-separated seeds (default 4242,1111,2222,3333,4444,5555,6666,7777)."
    "  --windows N         Number of control windows (default 16)."
    "  --W N               Window length in generations (default 10)."
    "  --S N               Window stride (default 10)."
    "  --length N          Genotype length (default 32)."
    "  --phenotype-length N Phenotype length (default = length)."
    "  --no-phenotype      Disable phenotype generation."
    "  --kernel KW         Kernel keyword (default :mutating-template)."
    "  --sigil STR         Base exotype sigil (default ca/default-sigil)."
    "  --wiring-path PATH  Wiring diagram EDN path."
    "  --wiring-index N    Wiring candidate index when EDN has :candidates (default 0)."
    "  --wiring-actions EDN Action vector for wiring controller (default [:pressure-up :selectivity-up :selectivity-down :pressure-down])."
    "  --allow-kernel-switch Allow operators to switch kernels (default false)."
    "  --top-k N           Number of seeds to keep by disagreement (default 4)."
    "  --out-dir PATH      Output directory for run EDN files (default /tmp/cyber-mmca-disagree-<ts>)."
    "  --inputs PATH       Output inputs list (default /tmp/cyber-mmca-disagree-<ts>-inputs.txt)."
    "  --label PREFIX      Filename prefix (default cyber-mmca-disagree)."
    "  --help              Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-edn [s]
  (try (edn/read-string s) (catch Exception _ nil)))

(defn- parse-seeds [s]
  (->> (str/split (or s "") #",")
       (map str/trim)
       (remove str/blank?)
       (map parse-int)
       (remove nil?)
       vec))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--seeds" flag)
          (recur (rest more) (assoc opts :seeds (parse-seeds (first more))))

          (= "--windows" flag)
          (recur (rest more) (assoc opts :windows (parse-int (first more))))

          (= "--W" flag)
          (recur (rest more) (assoc opts :W (parse-int (first more))))

          (= "--S" flag)
          (recur (rest more) (assoc opts :S (parse-int (first more))))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--phenotype-length" flag)
          (recur (rest more) (assoc opts :phenotype-length (parse-int (first more))))

          (= "--no-phenotype" flag)
          (recur more (assoc opts :no-phenotype true))

          (= "--kernel" flag)
          (recur (rest more) (assoc opts :kernel (keyword (first more))))

          (= "--sigil" flag)
          (recur (rest more) (assoc opts :sigil (first more)))

          (= "--wiring-path" flag)
          (recur (rest more) (assoc opts :wiring-path (first more)))

          (= "--wiring-index" flag)
          (recur (rest more) (assoc opts :wiring-index (parse-int (first more))))

          (= "--wiring-actions" flag)
          (recur (rest more) (assoc opts :wiring-actions (parse-edn (first more))))

          (= "--allow-kernel-switch" flag)
          (recur more (assoc opts :allow-kernel-switch true))

          (= "--top-k" flag)
          (recur (rest more) (assoc opts :top-k (parse-int (first more))))

          (= "--out-dir" flag)
          (recur (rest more) (assoc opts :out-dir (first more)))

          (= "--inputs" flag)
          (recur (rest more) (assoc opts :inputs (first more)))

          (= "--label" flag)
          (recur (rest more) (assoc opts :label (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- clamp [x lo hi]
  (max lo (min hi x)))

(defn- adjust-params [params actions]
  (let [params (or params {})
        pressure-step 0.1
        select-step 0.1
        update-default (or (:update-prob params) 0.5)
        match-default (or (:match-threshold params) 0.5)
        apply-action (fn [p action]
                       (case action
                         :pressure-up (update p :update-prob
                                              #(clamp (+ (double (or % update-default)) pressure-step) 0.05 1.0))
                         :pressure-down (update p :update-prob
                                                #(clamp (- (double (or % update-default)) pressure-step) 0.05 1.0))
                         :selectivity-up (update p :match-threshold
                                                 #(clamp (+ (double (or % match-default)) select-step) 0.0 1.0))
                         :selectivity-down (update p :match-threshold
                                                   #(clamp (- (double (or % match-default)) select-step) 0.0 1.0))
                         p))]
    (reduce apply-action params actions)))

(defn- ok-regime? [regime]
  (and regime (not (#{:freeze :magma} regime))))

(defn- choose-actions-hex
  [{:keys [regime pressure selectivity structure]}]
  (let [p (double (or pressure 0.0))
        s (double (or selectivity 0.0))
        t (double (or structure 0.0))]
    (cond
      (= regime :freeze) [:pressure-up]
      (= regime :magma) [:pressure-down :selectivity-up]
      (and (ok-regime? regime) (< t 0.4) (< s 0.4)) [:selectivity-up]
      (and (ok-regime? regime) (> p 0.7) (> s 0.7)) [:pressure-down]
      :else [:hold])))

(defn- normalize-wiring-score
  [score]
  (when (number? score)
    (let [score (double score)
          scaled (cond
                   (<= score 1.0) score
                   (> score 1.0) (/ score 100.0)
                   :else score)]
      (clamp scaled 0.0 1.0))))

(defn- resolve-wiring-diagram
  [{:keys [wiring-path wiring-index]}]
  (cond
    wiring-path
    (let [data (edn/read-string (slurp wiring-path))
          wiring-index (or wiring-index 0)
          pick-candidate (fn [candidates idx]
                           (nth candidates (min (max 0 idx) (dec (count candidates)))))]
      (cond
        (and (map? data) (:diagram data)) (:diagram data)
        (and (map? data) (:candidates data)) (pick-candidate (:candidates data) wiring-index)
        :else data))
    :else (wiring/example-diagram)))

(defn- validate-wiring-diagram [diagram]
  (let [lib (wiring/load-components)
        validation (wiring/validate-diagram lib diagram)]
    (when-not (:ok? validation)
      (throw (ex-info "Invalid wiring diagram." {:errors (:errors validation)})))
    diagram))

(defn- choose-actions-wiring
  [diagram window {:keys [actions] :or {actions [:pressure-up :selectivity-up :selectivity-down :pressure-down]}}]
  (let [context {:summary (:summary window)}
        {:keys [output]} (interpret/evaluate-diagram diagram context)
        pass? (:pass? output)
        score (normalize-wiring-score (:score output))
        action-list (vec (or actions [:hold]))
        action (cond
                 (and (boolean? pass?) (= (count action-list) 2))
                 (if pass? (first action-list) (second action-list))

                 (and (number? score) (pos? (count action-list)))
                 (let [idx (min (dec (count action-list))
                                (int (Math/floor (* score (count action-list)))))]
                   (nth action-list idx))

                 :else
                 :hold)]
    {:actions [(or action :hold)]
     :wiring-score score
     :wiring-pass pass?}))

(defn- run-controller
  [{:keys [seed windows W S kernel length phenotype-length sigil wiring-diagram wiring-actions allow-kernel-switch controller]}]
  (let [rng (java.util.Random. (long seed))
        length (max 1 (int (or length 32)))
        kernel (or kernel :mutating-template)
        sigil (or sigil ca/default-sigil)
        windows (max 1 (int (or windows 10)))
        W (max 1 (int (or W 10)))
        S (max 1 (int (or S W)))
        genotype (rng-sigil-string rng length)
        phenotype (when phenotype-length
                    (ca/random-phenotype-string (max 1 (int phenotype-length))))
        base-exotype (exotype/resolve-exotype {:sigil sigil :tier :super})
        wiring-actions (or wiring-actions
                           [:pressure-up :selectivity-up :selectivity-down :pressure-down])]
    (loop [idx 0
           state {:genotype genotype
                  :phenotype phenotype
                  :kernel kernel
                  :exotype base-exotype
                  :metrics-history []
                  :gen-history []
                  :phe-history []}
           windows-out []]
      (if (>= idx windows)
        {:state state
         :windows windows-out}
        (let [result (runtime/run-mmca {:genotype (:genotype state)
                                        :phenotype (:phenotype state)
                                        :generations W
                                        :kernel (:kernel state)
                                        :lock-kernel (not (true? allow-kernel-switch))
                                        :exotype (:exotype state)
                                        :seed (+ seed idx)})
              metrics-history (into (:metrics-history state) (:metrics-history result))
              gen-history (into (:gen-history state) (:gen-history result))
              phe-history (into (:phe-history state) (:phe-history result))
              window (last (metrics/windowed-macro-features
                            {:metrics-history metrics-history
                             :gen-history gen-history
                             :phe-history phe-history}
                            {:W W :S S}))
              {:keys [actions wiring-score wiring-pass]} (if (= controller :wiring)
                                                           (choose-actions-wiring wiring-diagram window {:actions wiring-actions})
                                                           {:actions (choose-actions-hex window)})
              params-before (get-in state [:exotype :params])
              params-after (if (seq (remove #{:hold} actions))
                             (adjust-params params-before actions)
                             params-before)
              exotype' (assoc (:exotype state) :params params-after)
              record (merge window
                            {:controller controller
                             :seed seed
                             :window idx
                             :actions actions
                             :wiring-score wiring-score
                             :wiring-pass wiring-pass
                             :update-prob (:update-prob params-after)
                             :match-threshold (:match-threshold params-after)})]
          (recur (inc idx)
                 {:genotype (or (last (:gen-history result)) (:genotype state))
                  :phenotype (or (last (:phe-history result)) (:phenotype state))
                  :kernel (or (:kernel result) (:kernel state))
                  :exotype exotype'
                  :metrics-history metrics-history
                  :gen-history gen-history
                  :phe-history phe-history}
                 (conj windows-out record)))))))

(defn- disagreement-rate
  [hex-windows wiring-windows]
  (let [pairs (map vector hex-windows wiring-windows)
        disagree? (fn [[h w]]
                    (not= (set (:actions h)) (set (:actions w))))]
    (if (seq pairs)
      (/ (count (filter disagree? pairs)) (double (count pairs)))
      0.0)))

(defn- run->edn!
  [{:keys [state windows controller seed] :as data} out-dir label]
  (let [generations (count (:metrics-history state))
        base (format "%s-%s-seed-%d" label (name controller) seed)
        path (str (io/file out-dir (str base ".edn")))
        run {:metrics-history (:metrics-history state)
             :gen-history (:gen-history state)
             :phe-history (:phe-history state)
             :genotype (last (:gen-history state))
             :phenotype (last (:phe-history state))
             :kernel (:kernel state)
             :exotype (:exotype state)
             :cyber-mmca/windows windows
             :cyber-mmca/meta {:controller controller
                              :seed seed
                              :windows (count windows)
                              :generations generations}
             :hit/meta {:label base
                        :seed seed
                        :controller controller
                        :note "wiring vs hex disagreement run"}}]
    (out/warn-overwrite-file! path)
    (spit path (pr-str run))
    path))

(defn -main [& args]
  (let [{:keys [help unknown seeds windows W S length phenotype-length no-phenotype kernel sigil wiring-path wiring-index wiring-actions
                allow-kernel-switch top-k out-dir inputs label]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      :else
      (let [seeds (or seeds [4242 1111 2222 3333 4444 5555 6666 7777])
            windows (int (or windows 16))
            W (int (or W 10))
            S (int (or S 10))
            length (int (or length 32))
            phenotype-length (when-not no-phenotype
                               (or (when (number? phenotype-length) phenotype-length) length))
            kernel (or kernel :mutating-template)
            sigil (or sigil ca/default-sigil)
            wiring-diagram (-> (resolve-wiring-diagram {:wiring-path wiring-path
                                                        :wiring-index wiring-index})
                               (validate-wiring-diagram))
            top-k (int (or top-k 4))
            ts (System/currentTimeMillis)
            out-dir (or out-dir (format "/tmp/cyber-mmca-disagree-%d" ts))
            inputs (or inputs (format "/tmp/cyber-mmca-disagree-%d-inputs.txt" ts))
            label (or label "cyber-mmca-disagree")]
        (out/warn-overwrite-dir! out-dir)
        (.mkdirs (io/file out-dir))
        (let [results
              (mapv (fn [seed]
                      (let [hex (run-controller {:seed seed
                                                 :controller :hex
                                                 :windows windows :W W :S S
                                                 :kernel kernel
                                                 :length length
                                                 :phenotype-length phenotype-length
                                                 :sigil sigil
                                                 :allow-kernel-switch allow-kernel-switch})
                            wiring (run-controller {:seed seed
                                                    :controller :wiring
                                                    :windows windows :W W :S S
                                                    :kernel kernel
                                                    :length length
                                                    :phenotype-length phenotype-length
                                                    :sigil sigil
                                                    :wiring-diagram wiring-diagram
                                                    :wiring-actions wiring-actions
                                                    :allow-kernel-switch allow-kernel-switch})
                            rate (disagreement-rate (:windows hex) (:windows wiring))]
                        {:seed seed
                         :hex hex
                         :wiring wiring
                         :rate rate}))
                    seeds)
              ranked (->> results (sort-by :rate >) (take (min top-k (count results))))
              paths (mapcat (fn [{:keys [seed hex wiring]}]
                              [(run->edn! (assoc hex :controller :hex :seed seed) out-dir label)
                               (run->edn! (assoc wiring :controller :wiring :seed seed) out-dir label)])
                            ranked)]
          (out/spit-text! inputs (str/join "\n" paths))
          (doseq [{:keys [seed rate]} ranked]
            (println (format "seed %d disagreement %.3f" seed rate)))
          (println "Runs saved to" (out/abs-path out-dir)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
