(ns futon5.cyber-mmca.core
  (:require [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.runtime :as runtime]))

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
  - :controller (:null, :hex, :sigil)
  - :seed
  - :windows
  - :W, :S
  - :kernel, :length
  - :sigil, :sigil-count
  - :genotype-gate, :genotype-gate-signal
  - :freeze-genotype
  - :exotype-mode
  - :operators (default [])"
  [{:keys [controller seed windows W S kernel length sigil sigil-count
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
        base-exotype (exotype/resolve-exotype {:sigil sigil :tier :super})
        sigils (when (= controller :sigil)
                 (sigils-for-count (or sigil-count 16)))]
    (loop [idx 0
           state {:genotype genotype
                  :phenotype nil
                  :kernel kernel
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
              {:keys [actions chosen-sigil]} (cond
                                               (= controller :null) {:actions [:hold]}
                                               (= controller :sigil)
                                               (let [{:keys [sigil actions]} (choose-actions-sigil sigils window)]
                                                 {:actions actions :chosen-sigil sigil})
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
                  :kernel (or (:kernel result) (:kernel state))
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
