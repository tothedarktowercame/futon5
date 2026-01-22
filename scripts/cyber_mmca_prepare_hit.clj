(ns cyber-mmca-prepare-hit
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.render :as render]
            [futon5.mmca.runtime :as runtime]
            [futon5.xenotype.interpret :as interpret]
            [futon5.xenotype.wiring :as wiring]))

(defn- usage []
  (str/join
   "\n"
   ["Prepare Cyber-MMCA runs as HIT inputs (full EDN histories)."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_prepare_hit.clj [options]"
    ""
    "Options:"
    "  --seeds LIST        Comma-separated seeds (default 4242,1111,2222,3333)."
    "  --controllers LIST  Comma-separated controllers (null,hex,sigil,wiring)."
    "  --windows N         Number of control windows (default 12)."
    "  --W N               Window length in generations (default 10)."
    "  --S N               Window stride (default 10)."
    "  --length N          Genotype length (default 32)."
    "  --phenotype-length N Phenotype length (default = length)."
    "  --no-phenotype      Disable phenotype generation."
    "  --kernel KW         Kernel keyword (default :mutating-template)."
    "  --sigil STR         Base exotype sigil (default ca/default-sigil)."
    "  --sigil-count N     Control sigil count (default 16)."
    "  --genotype-gate     Enable genotype gate."
    "  --genotype-gate-signal CH  Gate signal (default \\1)."
    "  --freeze-genotype   Freeze genotype updates."
    "  --allow-kernel-switch Allow operators to switch kernels (default false)."
    "  --exotype-mode KW   Exotype mode (default :inline)."
    "  --wiring-path PATH  Wiring diagram EDN path (optional)."
    "  --wiring-index N    Wiring candidate index when EDN has :candidates (default 0)."
    "  --wiring-actions EDN Action vector for wiring controller (default [:pressure-up :selectivity-up :selectivity-down :pressure-down])."
    "  --out-dir PATH      Output directory for run EDN files (default /tmp/cyber-mmca-hit)."
    "  --inputs PATH       Output inputs list (default /tmp/cyber-mmca-hit-inputs.txt)."
    "  --label PREFIX      Filename prefix (default cyber-mmca-hit)."
    "  --render-dir PATH   Render PPM images for each run (optional)."
    "  --exotype           Render genotype/phenotype/exotype triptych."
    "  --scale PCT         Resize renders by percent (optional)."
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

(defn- parse-controllers [s]
  (->> (str/split (or s "") #",")
       (map str/trim)
       (remove str/blank?)
       (map keyword)
       (remove nil?)
       vec))

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

          (= "--controllers" flag)
          (recur (rest more) (assoc opts :controllers (parse-controllers (first more))))

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

          (= "--sigil-count" flag)
          (recur (rest more) (assoc opts :sigil-count (parse-int (first more))))

          (= "--genotype-gate" flag)
          (recur more (assoc opts :genotype-gate true))

          (= "--genotype-gate-signal" flag)
          (recur (rest more) (assoc opts :genotype-gate-signal (first more)))

          (= "--freeze-genotype" flag)
          (recur more (assoc opts :freeze-genotype true))

          (= "--allow-kernel-switch" flag)
          (recur more (assoc opts :allow-kernel-switch true))

          (= "--exotype-mode" flag)
          (recur (rest more) (assoc opts :exotype-mode (keyword (first more))))

          (= "--wiring-path" flag)
          (recur (rest more) (assoc opts :wiring-path (first more)))

          (= "--wiring-index" flag)
          (recur (rest more) (assoc opts :wiring-index (parse-int (first more))))

          (= "--wiring-actions" flag)
          (recur (rest more) (assoc opts :wiring-actions (parse-edn (first more))))

          (= "--out-dir" flag)
          (recur (rest more) (assoc opts :out-dir (first more)))

          (= "--inputs" flag)
          (recur (rest more) (assoc opts :inputs (first more)))

          (= "--label" flag)
          (recur (rest more) (assoc opts :label (first more)))

          (= "--render-dir" flag)
          (recur (rest more) (assoc opts :render-dir (first more)))

          (= "--exotype" flag)
          (recur more (assoc opts :exotype true))

          (= "--scale" flag)
          (recur (rest more) (assoc opts :scale (parse-int (first more))))

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

(defn- gate-signal
  [s]
  (cond
    (char? s) s
    (string? s) (first s)
    :else \1))

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

(defn- sigils-for-count [n]
  (->> (ca/sigil-entries)
       (map :sigil)
       (take (max 1 (int n)))
       vec))

(defn- bit-at [bits idx]
  (if (and bits (<= 0 idx) (< idx (count bits)))
    (nth bits idx)
    \0))

(defn- sigil->actions [sigil]
  (let [bits (ca/bits-for sigil)
        b0 (bit-at bits 0)
        b1 (bit-at bits 1)
        b4 (bit-at bits 4)
        b5 (bit-at bits 5)
        actions (cond-> []
                  (= b0 \1) (conj :pressure-up)
                  (= b1 \1) (conj :pressure-down)
                  (= b4 \1) (conj :selectivity-up)
                  (= b5 \1) (conj :selectivity-down))]
    (if (seq actions) actions [:hold])))

(defn- choose-actions-sigil
  [sigils {:keys [pressure selectivity]}]
  (let [p (double (or pressure 0.0))
        s (double (or selectivity 0.0))
        bins 4
        idx (+ (* (min (dec bins) (int (* p bins))) bins)
               (min (dec bins) (int (* s bins))))
        idx (mod idx (count sigils))
        sigil (nth sigils idx)]
    {:sigil sigil
     :actions (sigil->actions sigil)}))

(defn- read-edn-file
  [path]
  (when path
    (edn/read-string (slurp path))))

(defn- pick-candidate
  [candidates idx]
  (let [idx (int (or idx 0))
        max-idx (max 0 (dec (count candidates)))
        idx (int (clamp idx 0 max-idx))]
    (nth candidates idx)))

(defn- resolve-wiring-diagram
  [{:keys [wiring-diagram wiring-path wiring-index]}]
  (cond
    (map? wiring-diagram) wiring-diagram
    (and wiring-path (seq wiring-path))
    (let [data (read-edn-file wiring-path)]
      (cond
        (and (map? data) (:nodes data)) data
        (and (map? data) (:diagram data)) (:diagram data)
        (and (map? data) (:candidates data)) (pick-candidate (:candidates data) wiring-index)
        :else data))
    :else (wiring/example-diagram)))

(defn- validate-wiring-diagram
  [diagram]
  (let [lib (wiring/load-components)
        validation (wiring/validate-diagram lib diagram)]
    (when-not (:ok? validation)
      (throw (ex-info "Invalid wiring diagram." {:errors (:errors validation)})))
    diagram))

(defn- normalize-wiring-score
  [score]
  (when (number? score)
    (let [score (double score)
          scaled (cond
                   (<= score 1.0) score
                   (> score 1.0) (/ score 100.0)
                   :else score)]
      (clamp scaled 0.0 1.0))))

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
     :wiring-pass pass?
     :wiring-score score}))

(defn- safe-kernel
  [candidate fallback]
  (try
    (when candidate
      (ca/kernel-fn candidate)
      candidate)
    (catch Exception _ fallback)))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(defn- run-controller-full
  [{:keys [controller seed windows W S kernel length sigil sigil-count
           wiring-path wiring-index wiring-actions
           phenotype-length genotype-gate genotype-gate-signal freeze-genotype allow-kernel-switch exotype-mode]}]
  (let [rng (when seed (java.util.Random. (long seed)))
        length (max 1 (int (or length 32)))
        kernel (or kernel :mutating-template)
        sigil (or sigil ca/default-sigil)
        windows (max 1 (int (or windows 10)))
        W (max 1 (int (or W 10)))
        S (max 1 (int (or S W)))
        genotype (if rng
                   (rng-sigil-string rng length)
                   (ca/random-sigil-string length))
        phenotype (when phenotype-length
                    (ca/random-phenotype-string (max 1 (int phenotype-length))))
        base-exotype (exotype/resolve-exotype {:sigil sigil :tier :super})
        sigils (when (= controller :sigil)
                 (sigils-for-count (or sigil-count 16)))
        wiring-diagram (when (= controller :wiring)
                         (-> (resolve-wiring-diagram {:wiring-path wiring-path
                                                      :wiring-index wiring-index})
                             (validate-wiring-diagram)))
        wiring-actions (or wiring-actions
                           [:pressure-up :selectivity-up :selectivity-down :pressure-down])]
    (loop [idx 0
           state {:genotype genotype
                  :phenotype phenotype
                  :kernel kernel
                  :kernel-fn nil
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
                                        :kernel-fn (:kernel-fn state)
                                        :lock-kernel (not (true? allow-kernel-switch))
                                        :genotype-gate (boolean genotype-gate)
                                        :genotype-gate-signal (gate-signal genotype-gate-signal)
                                        :freeze-genotype (boolean freeze-genotype)
                                        :exotype (:exotype state)
                                        :exotype-mode (or exotype-mode :inline)
                                        :seed (when seed (+ seed idx))})
              metrics-history (into (:metrics-history state) (:metrics-history result))
              gen-history (into (:gen-history state) (:gen-history result))
              phe-history (into (:phe-history state) (:phe-history result))
              window (last (metrics/windowed-macro-features
                            {:metrics-history metrics-history
                             :gen-history gen-history
                             :phe-history phe-history}
                            {:W W :S S}))
              {:keys [actions chosen-sigil wiring-score wiring-pass]} (cond
                                                                        (= controller :null) {:actions [:hold]}
                                                                        (= controller :sigil)
                                                                        (let [{:keys [sigil actions]} (choose-actions-sigil sigils window)]
                                                                          {:actions actions :chosen-sigil sigil})
                                                                        (= controller :wiring)
                                                                        (choose-actions-wiring wiring-diagram window
                                                                                               {:actions wiring-actions})
                                                                        :else {:actions (choose-actions-hex window)})
              params-before (get-in state [:exotype :params])
              params-after (if (seq (remove #{:hold} actions))
                             (adjust-params params-before actions)
                             params-before)
              delta-update (when (and (:update-prob params-after) (:update-prob params-before))
                             (- (double (:update-prob params-after))
                                (double (:update-prob params-before))))
              delta-match (when (and (:match-threshold params-after) (:match-threshold params-before))
                            (- (double (:match-threshold params-after))
                               (double (:match-threshold params-before))))
              applied? (or (and (number? delta-update) (not (zero? delta-update)))
                           (and (number? delta-match) (not (zero? delta-match))))
              exotype' (assoc (:exotype state) :params params-after)
              record {:controller controller
                      :seed seed
                      :window idx
                      :w-start (:w-start window)
                      :w-end (:w-end window)
                      :regime (:regime window)
                      :pressure (:pressure window)
                      :selectivity (:selectivity window)
                      :structure (:structure window)
                      :activity (:activity window)
                      :actions actions
                      :sigil chosen-sigil
                      :wiring-score wiring-score
                      :wiring-pass wiring-pass
                      :update-prob (:update-prob params-after)
                      :match-threshold (:match-threshold params-after)
                      :delta-update delta-update
                      :delta-match delta-match
                      :applied? applied?
                      :genotype-gate (boolean genotype-gate)
                      :freeze-genotype (boolean freeze-genotype)
                      :exotype-mode (or exotype-mode :inline)}]
          (recur (inc idx)
                 {:genotype (or (last (:gen-history result)) (:genotype state))
                  :phenotype (or (last (:phe-history result)) (:phenotype state))
                  :kernel (safe-kernel (:kernel result) (:kernel state))
                  :kernel-fn (or (:kernel-fn result) (:kernel-fn state))
                  :exotype exotype'
                  :metrics-history metrics-history
                  :gen-history gen-history
                  :phe-history phe-history}
                 (conj windows-out record)))))))

(defn- run->edn!
  [{:keys [state windows meta]} out-dir label idx]
  (let [seed (or (:seed (first windows)) 0)
        controller (or (:controller (first windows)) :unknown)
        metrics-history (:metrics-history state)
        gen-history (:gen-history state)
        phe-history (:phe-history state)
        generations (or (:generations meta)
                        (count (or gen-history metrics-history)))
        base (format "%s-%s-%02d-seed-%d" label (name controller) idx seed)
        path (str (io/file out-dir (str base ".edn")))
        run {:metrics-history metrics-history
             :gen-history gen-history
             :phe-history phe-history
             :genotype (last gen-history)
             :phenotype (last phe-history)
             :kernel (:kernel state)
             :exotype (:exotype state)
             :cyber-mmca/windows windows
             :cyber-mmca/meta (merge {:controller controller
                                      :seed seed
                                      :windows (count windows)
                                      :generations generations}
                                     meta)
             :hit/meta {:label base
                        :seed seed
                        :generations generations
                        :controller controller
                        :note "replayed from Cyber-MMCA controller loop"}}]
    (spit path (pr-str run))
    path))

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

(defn- avg-or-nil [xs]
  (when (seq xs)
    (/ (reduce + 0.0 xs) (double (count xs)))))

(defn- progress-line
  [{:keys [idx total controller seed windows elapsed-seconds]}]
  (let [last-win (last windows)
        u (:update-prob last-win)
        m (:match-threshold last-win)
        pass-values (keep (fn [v] (when (boolean? v) (if v 1.0 0.0)))
                          (map :wiring-pass windows))
        score-values (keep #(when (number? %) (double %)) (map :wiring-score windows))
        pass (or (avg-or-nil pass-values) 0.0)
        score (or (avg-or-nil score-values) 0.0)]
    (format "progress %d/%d controller=%s seed=%s u=%.2f m=%.2f pass=%.3f rank=%.3f elapsed=%.1fs"
            idx
            total
            (name (or controller :unknown))
            (or seed "-")
            (double (or u 0.0))
            (double (or m 0.0))
            (double pass)
            (double score)
            (double elapsed-seconds))))

(defn -main [& args]
  (let [{:keys [help unknown seeds controllers windows W S length phenotype-length no-phenotype kernel sigil
                sigil-count genotype-gate genotype-gate-signal freeze-genotype allow-kernel-switch exotype-mode
                wiring-path wiring-index wiring-actions out-dir inputs label render-dir exotype scale]}
        (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      :else
      (let [_ (ensure-resources!)
            _ (println "Progress fields: u/m are last-window update-prob/match-threshold; pass/rank are wiring averages; elapsed is seconds since start.")
            seeds (seq (or seeds [4242 1111 2222 3333]))
            controllers (seq (or controllers [:null :hex :sigil]))
            windows (max 1 (int (or windows 12)))
            W (max 1 (int (or W 10)))
            S (max 1 (int (or S W)))
            length (max 1 (int (or length 32)))
            phenotype-length (when-not no-phenotype
                               (or (when (number? phenotype-length) phenotype-length) length))
            kernel (or kernel :mutating-template)
            sigil (or sigil ca/default-sigil)
            sigil-count (max 4 (int (or sigil-count 16)))
            out-dir (or out-dir "/tmp/cyber-mmca-hit")
            inputs (or inputs "/tmp/cyber-mmca-hit-inputs.txt")
            label (or label "cyber-mmca-hit")
            wiring-index (or wiring-index 0)]
        (.mkdirs (io/file out-dir))
        (when render-dir
          (.mkdirs (io/file render-dir)))
        (let [run-plan (vec (for [controller controllers
                                  seed seeds]
                              {:controller controller
                               :seed seed}))
              total (count run-plan)
              start (System/nanoTime)
              paths (loop [idx 0
                           plans run-plan
                           paths []]
                      (if (empty? plans)
                        paths
                        (let [{:keys [controller seed]} (first plans)
                              result (run-controller-full
                                      {:controller controller
                                       :seed seed
                                       :windows windows
                                       :W W
                                       :S S
                                       :length length
                                       :phenotype-length phenotype-length
                                       :kernel kernel
                                       :sigil sigil
                                       :sigil-count sigil-count
                                       :genotype-gate genotype-gate
                                       :genotype-gate-signal genotype-gate-signal
                                       :freeze-genotype freeze-genotype
                                       :exotype-mode exotype-mode
                                       :wiring-path wiring-path
                                       :wiring-index wiring-index
                                       :wiring-actions wiring-actions})
                              path (run->edn! (assoc result
                                                    :meta {:controller controller
                                                           :seed seed
                                                           :windows windows
                                                           :W W
                                                           :S S
                                                           :length length
                                                           :generations (* windows W)
                                                           :kernel kernel
                                                           :sigil sigil
                                                           :sigil-count sigil-count
                                                           :genotype-gate (boolean genotype-gate)
                                                           :freeze-genotype (boolean freeze-genotype)
                                                           :exotype-mode (or exotype-mode :inline)
                                                           :wiring-path wiring-path
                                                           :wiring-index wiring-index
                                                           :wiring-actions wiring-actions})
                                                out-dir
                                                label
                                                (inc idx))
                              run (edn/read-string (slurp path))
                              elapsed (/ (- (System/nanoTime) start) 1e9)]
                          (render-run! render-dir path run exotype scale)
                          (println (progress-line {:idx (inc idx)
                                                   :total total
                                                   :controller controller
                                                   :seed seed
                                                   :windows (:windows result)
                                                   :elapsed-seconds elapsed}))
                          (recur (inc idx) (rest plans) (conj paths path)))))]
          (spit inputs (str/join "\n" paths))
          (println "Wrote" inputs)
          (println "Runs saved to" out-dir)
          (when render-dir
            (println "Renders saved to" render-dir)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
