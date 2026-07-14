(ns futon5.aif.confinement-demo-test
  "Confinement demo for M-aif-tokamak Slice 2: :aif vs :null time-at-eoc."
  (:require [clojure.test :refer [deftest testing]]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.runtime :as runtime]
            [futon5.aif.controller :as aif-ctrl]
            [clojure.string :as str]))

(defn- rng-sigil-string [rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(.get sigils (.nextInt rng (count sigils)))))))

(defn- clamp [x lo hi] (max lo (min hi x)))
(def ^:private pressure-step 0.1)
(def ^:private select-step 0.1)

(defn- adjust-params [params actions]
  (let [apply-action (fn [p action]
                       (case action
                         :pressure-up (update p :update-prob
                                              #(clamp (+ (double (or % 1.0)) pressure-step) 0.05 1.0))
                         :pressure-down (update p :update-prob
                                                #(clamp (- (double (or % 1.0)) pressure-step) 0.05 1.0))
                         :selectivity-up (update p :match-threshold
                                                 #(clamp (+ (double (or % 0.5)) select-step) 0.0 1.0))
                         :selectivity-down (update p :match-threshold
                                                   #(clamp (- (double (or % 0.5)) select-step) 0.0 1.0))
                         p))]
    (reduce apply-action (or params {}) (if (sequential? actions) actions [actions]))))

(defn- next-window [state opts]
  (let [{:keys [W S seed lesion]} opts
        result (runtime/run-mmca {:genotype (:genotype state)
                                  :phenotype (:phenotype state)
                                  :generations W
                                  :kernel (:kernel state)
                                  :lock-kernel false
                                  :exotype (:exotype state)
                                  :exotype-mode :inline
                                  :seed seed
                                  :lesion lesion})
        metrics-hist (into (:metrics-history state) (:metrics-history result))
        gen-hist (into (:gen-history state) (:gen-history result))
        phe-hist (into (:phe-history state) (:phe-history result))
        windows (metrics/windowed-macro-features
                 {:metrics-history metrics-hist
                  :gen-history gen-hist
                  :phe-history phe-hist}
                 {:W W :S (or S W)})]
    {:state {:genotype (or (last (:gen-history result)) (:genotype state))
             :phenotype (or (last (:phe-history result)) (:phenotype state))
             :kernel (:kernel state)
             :exotype (:exotype state)
             :metrics-history metrics-hist
             :gen-history gen-hist
             :phe-history phe-hist}
     :window (last windows)}))

(defn- run-ctrl
  ([controller-id seed windows W S]
   (run-ctrl controller-id seed windows W S nil))
  ([controller-id seed windows W S start-params]
   (let [rng (java.util.Random. (long seed))
         genotype (rng-sigil-string rng 32)
         base-exotype (exotype/resolve-exotype {:sigil ca/default-sigil :tier :super})
         base-exotype (if start-params
                        (assoc base-exotype :params
                                 (merge (:params base-exotype) start-params))
                        base-exotype)]
     (loop [idx 0
            state {:genotype genotype
                   :phenotype nil
                   :kernel :mutating-template
                   :exotype base-exotype
                   :metrics-history []
                   :gen-history []
                   :phe-history []}
            out []]
      (if (>= idx windows)
        out
        (let [{:keys [state window]} (next-window state {:W W :S S :seed (+ seed idx)})
              actions (cond
                        (= controller-id :null) [:hold]
                        (= controller-id :aif)
                        (:actions (aif-ctrl/choose-actions-aif state window
                                                               {:seed (+ seed idx) :W W :S S})))
              params-after (adjust-params (get-in state [:exotype :params]) actions)
              exotype' (assoc (:exotype state) :params params-after)]
          (recur (inc idx)
                 (assoc state :exotype exotype')
                 (conj out (assoc window :actions actions)))))))))

(deftest confinement-demo
  (testing ":aif keeps more ticks at :eoc than :null on seeds where :null drifts"
    (let [seeds [42 100 200 500]
          windows 12
          W 10
          S 10
          ;; Start from a drifting exotype: very low update-prob → freeze tendency
          drift-params {:update-prob 0.05 :match-threshold 0.9}]
      (println "=== CONFINEMENT DEMO: :aif vs :null ===")
      (println (format "Seeds: %s, %d windows, start params: %s"
                       (str/join "," (map str seeds)) windows drift-params))
      (doseq [seed seeds]
        (let [null-out (run-ctrl :null seed windows W S drift-params)
              aif-out (run-ctrl :aif seed windows W S drift-params)
              count-r (fn [r ws] (count (filter #(= (:regime %) r) ws)))
              null-eoc (count-r :eoc null-out)
              aif-eoc (count-r :eoc aif-out)
              null-freeze (count-r :freeze null-out)
              aif-freeze (count-r :freeze aif-out)
              null-magma (count-r :magma null-out)
              aif-magma (count-r :magma aif-out)]
          (println "")
          (println (format "Seed %d:" seed))
          (println (format "  :null  eoc=%d/%d  freeze=%d  magma=%d  | regimes: %s"
                           null-eoc windows null-freeze null-magma
                           (str/join " " (map :regime null-out))))
          (println (format "  :aif   eoc=%d/%d  freeze=%d  magma=%d  | regimes: %s"
                           aif-eoc windows aif-freeze aif-magma
                           (str/join " " (map :regime aif-out))))
          (println (format "  delta: eoc %+d" (- aif-eoc null-eoc))))))))
