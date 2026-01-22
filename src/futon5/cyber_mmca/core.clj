(ns futon5.cyber-mmca.core
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.runtime :as runtime]
            [futon5.xenotype.interpret :as interpret]
            [futon5.xenotype.wiring :as wiring]))

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

(defn- safe-kernel
  [candidate fallback]
  (try
    (when candidate
      (ca/kernel-fn candidate)
      candidate)
    (catch Exception _ fallback)))

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

(defn choose-actions-hex
  [{:keys [regime pressure selectivity structure]}]
  (if (nil? regime)
    [:hold]
    (let [p (double (or pressure 0.0))
          s (double (or selectivity 0.0))
          t (double (or structure 0.0))]
      (cond
        (= regime :freeze) [:pressure-up]
        (= regime :magma) [:pressure-down :selectivity-up]
        (and (ok-regime? regime) (< t 0.4) (< s 0.4)) [:selectivity-up]
        (and (ok-regime? regime) (> p 0.7) (> s 0.7)) [:pressure-down]
        :else [:hold]))))

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

(defn choose-actions-sigil
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

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(defn- apply-actions
  [params-before actions]
  (let [params-after (if (seq (remove #{:hold} actions))
                       (adjust-params params-before actions)
                       params-before)
        delta-update (when (and (:update-prob params-after) (:update-prob params-before))
                       (- (double (:update-prob params-after))
                          (double (:update-prob params-before))))
        delta-match (when (and (:match-threshold params-after) (:match-threshold params-before))
                      (- (double (:match-threshold params-after))
                         (double (:match-threshold params-before))))
        applied? (or (and (number? delta-update) (not (zero? delta-update)))
                     (and (number? delta-match) (not (zero? delta-match))))]
    {:params params-after
     :delta-update delta-update
     :delta-match delta-match
     :applied? applied?}))

(defn- window-features
  [metrics-history gen-history phe-history W S]
  (let [windows (metrics/windowed-macro-features
                 {:metrics-history metrics-history
                  :gen-history gen-history
                  :phe-history phe-history}
                 {:W W :S S})]
    (or (last windows) {})))

(defn run-controller
  "Run a controller over multiple windows and return per-window records.

  opts:
  - :controller (:null, :hex, :sigil, :wiring)
  - :seed
  - :windows
  - :W, :S
  - :kernel, :length
  - :sigil, :sigil-count
  - :wiring-diagram, :wiring-path, :wiring-index, :wiring-actions
  - :phenotype, :phenotype-length
  - :genotype-gate, :genotype-gate-signal
  - :freeze-genotype
  - :exotype-mode
  - :operators (default [])"
  [{:keys [controller seed windows W S kernel length sigil sigil-count
           wiring-diagram wiring-path wiring-index wiring-actions
           phenotype phenotype-length
           genotype-gate genotype-gate-signal freeze-genotype exotype-mode
           operators]}]
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
        phenotype (or phenotype
                      (when phenotype-length
                        (ca/random-phenotype-string phenotype-length)))
        base-exotype (exotype/resolve-exotype {:sigil sigil :tier :super})
        sigils (when (= controller :sigil)
                 (sigils-for-count (or sigil-count 16)))
        wiring-diagram (when (= controller :wiring)
                         (-> (resolve-wiring-diagram {:wiring-diagram wiring-diagram
                                                      :wiring-path wiring-path
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
        windows-out
        (let [result (runtime/run-mmca {:genotype (:genotype state)
                                        :phenotype (:phenotype state)
                                        :generations W
                                        :kernel (:kernel state)
                                        :kernel-fn (:kernel-fn state)
                                        :lock-kernel false
                                        :operators (or operators [])
                                        :genotype-gate (boolean genotype-gate)
                                        :genotype-gate-signal (or genotype-gate-signal \1)
                                        :freeze-genotype (boolean freeze-genotype)
                                        :exotype (:exotype state)
                                        :exotype-mode (or exotype-mode :inline)
                                        :seed (when seed (+ seed idx))})
              metrics-history (into (:metrics-history state) (:metrics-history result))
              gen-history (into (:gen-history state) (:gen-history result))
              phe-history (into (:phe-history state) (:phe-history result))
              window (window-features metrics-history gen-history phe-history W S)
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
              {:keys [params delta-update delta-match applied?]} (apply-actions params-before actions)
              exotype' (assoc (:exotype state) :params params)
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
                      :update-prob (:update-prob params)
                      :match-threshold (:match-threshold params)
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

(defn run-stress-controller
  "Run a controller with optional stress overrides on exotype params.

  opts keys in addition to run-controller:
  - :stress-windows (set or seq of window indexes)
  - :stress-params (map merged into exotype :params during stress windows)"
  [{:keys [controller seed windows W S kernel length sigil sigil-count
           wiring-diagram wiring-path wiring-index wiring-actions
           phenotype phenotype-length
           genotype-gate genotype-gate-signal freeze-genotype exotype-mode
           operators stress-windows stress-params]}]
  (let [rng (when seed (java.util.Random. (long seed)))
        length (max 1 (int (or length 32)))
        kernel (or kernel :mutating-template)
        sigil (or sigil ca/default-sigil)
        windows (max 1 (int (or windows 10)))
        W (max 1 (int (or W 10)))
        S (max 1 (int (or S W)))
        stress-windows (set (or stress-windows []))
        stress-params (or stress-params {})
        genotype (if rng
                   (rng-sigil-string rng length)
                   (ca/random-sigil-string length))
        phenotype (or phenotype
                      (when phenotype-length
                        (ca/random-phenotype-string phenotype-length)))
        base-exotype (exotype/resolve-exotype {:sigil sigil :tier :super})
        sigils (when (= controller :sigil)
                 (sigils-for-count (or sigil-count 16)))
        wiring-diagram (when (= controller :wiring)
                         (-> (resolve-wiring-diagram {:wiring-diagram wiring-diagram
                                                      :wiring-path wiring-path
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
        windows-out
        (let [result (runtime/run-mmca {:genotype (:genotype state)
                                        :phenotype (:phenotype state)
                                        :generations W
                                        :kernel (:kernel state)
                                        :kernel-fn (:kernel-fn state)
                                        :lock-kernel false
                                        :operators (or operators [])
                                        :genotype-gate (boolean genotype-gate)
                                        :genotype-gate-signal (or genotype-gate-signal \1)
                                        :freeze-genotype (boolean freeze-genotype)
                                        :exotype (:exotype state)
                                        :exotype-mode (or exotype-mode :inline)
                                        :seed (when seed (+ seed idx))})
              metrics-history (into (:metrics-history state) (:metrics-history result))
              gen-history (into (:gen-history state) (:gen-history result))
              phe-history (into (:phe-history state) (:phe-history result))
              window (window-features metrics-history gen-history phe-history W S)
              stress? (contains? stress-windows idx)
              {:keys [actions chosen-sigil wiring-score wiring-pass]} (cond
                                                                        stress? {:actions [:stress]}
                                                                        (= controller :null) {:actions [:hold]}
                                                                        (= controller :sigil)
                                                                        (let [{:keys [sigil actions]} (choose-actions-sigil sigils window)]
                                                                          {:actions actions :chosen-sigil sigil})
                                                                        (= controller :wiring)
                                                                        (choose-actions-wiring wiring-diagram window
                                                                                               {:actions wiring-actions})
                                                                        :else {:actions (choose-actions-hex window)})
              params-before (get-in state [:exotype :params])
              params-before (if stress?
                              (merge params-before stress-params)
                              params-before)
              {:keys [params delta-update delta-match applied?]} (apply-actions params-before actions)
              params (if stress? params-before params)
              exotype' (assoc (:exotype state) :params params)
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
                      :update-prob (:update-prob params)
                      :match-threshold (:match-threshold params)
                      :delta-update delta-update
                      :delta-match delta-match
                      :applied? applied?
                      :stress? stress?
                      :stress-update (:update-prob stress-params)
                      :stress-match (:match-threshold stress-params)
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

(defn- segment-lengths [pred xs]
  (->> xs
       (partition-by pred)
       (map (fn [seg]
              (let [v (first seg)]
                (when (pred v) (count seg)))))
       (remove nil?)))

(defn stats-for-controller
  [windows]
  (let [regimes (map :regime windows)
        total (count regimes)
        freeze? #(= % :freeze)
        magma? #(= % :magma)
        ok? ok-regime?
        transitions (count (remove #(apply = %) (partition 2 1 regimes)))
        freeze-count (count (filter freeze? regimes))
        magma-count (count (filter magma? regimes))
        ok-count (count (filter ok? regimes))
        freeze-segs (segment-lengths freeze? regimes)
        magma-segs (segment-lengths magma? regimes)
        ok-segs (segment-lengths ok? regimes)]
    {:fraction-freeze (when (pos? total) (/ freeze-count total))
     :fraction-magma (when (pos? total) (/ magma-count total))
     :fraction-ok (when (pos? total) (/ ok-count total))
     :regime-transitions transitions
     :avg-freeze-exit (when (seq freeze-segs) (/ (reduce + freeze-segs) (double (count freeze-segs))))
     :avg-magma-exit (when (seq magma-segs) (/ (reduce + magma-segs) (double (count magma-segs))))
     :avg-ok-streak (when (seq ok-segs) (/ (reduce + ok-segs) (double (count ok-segs))))
     :regime-classes (count (set regimes))}))
