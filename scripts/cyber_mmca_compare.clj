(ns cyber-mmca-compare
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.runtime :as runtime]))

(defn- usage []
  (str/join
   "\n"
   ["Compare Cyber-MMCA controllers on shared observation ABI."
    ""
    "Usage:"
    "  bb -cp futon5/src futon5/scripts/cyber_mmca_compare.clj [options]"
    ""
    "Options:"
    "  --seeds LIST        Comma-separated seeds (default 4242,1111,2222,3333)."
    "  --windows N         Number of control windows (default 12)."
    "  --W N               Window length in generations (default 10)."
    "  --S N               Window stride (default 10)."
    "  --length N          Genotype length (default 32)."
    "  --kernel KW         Kernel keyword (default :mutating-template)."
    "  --sigil STR         Base exotype sigil (default ca/default-sigil)."
    "  --sigil-count N     Control sigil count for controller/sigil (default 16)."
    "  --kick-window N     Apply a lesion at window N (0-based)."
    "  --kick-target T     Lesion target: phenotype, genotype, or both."
    "  --kick-half H       Lesion half: left or right."
    "  --out PATH          Output CSV (default /tmp/cyber-mmca-compare.csv)."
    "  --help              Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-seeds [s]
  (->> (str/split (or s "") #",")
       (map str/trim)
       (remove str/blank?)
       (map parse-int)
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

          (= "--windows" flag)
          (recur (rest more) (assoc opts :windows (parse-int (first more))))

          (= "--W" flag)
          (recur (rest more) (assoc opts :W (parse-int (first more))))

          (= "--S" flag)
          (recur (rest more) (assoc opts :S (parse-int (first more))))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--kernel" flag)
          (recur (rest more) (assoc opts :kernel (keyword (first more))))

          (= "--sigil" flag)
          (recur (rest more) (assoc opts :sigil (first more)))

          (= "--sigil-count" flag)
          (recur (rest more) (assoc opts :sigil-count (parse-int (first more))))

          (= "--kick-window" flag)
          (recur (rest more) (assoc opts :kick-window (parse-int (first more))))

          (= "--kick-target" flag)
          (recur (rest more) (assoc opts :kick-target (keyword (first more))))

          (= "--kick-half" flag)
          (recur (rest more) (assoc opts :kick-half (keyword (first more))))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- clamp [x lo hi]
  (max lo (min hi x)))

(defn- adjust-params [params actions]
  (let [params (or params {})
        pressure-step 0.1
        select-step 0.1
        apply-action (fn [p action]
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
    (reduce apply-action params actions)))

(defn- ok-regime? [regime]
  (and regime (not (#{:freeze :magma} regime))))

(defn- choose-actions-hex [{:keys [regime pressure selectivity structure]}]
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

(defn- sigil->actions [sigil]
  (let [bits (ca/bits-for sigil)
        b0 (nth bits 0)
        b1 (nth bits 1)
        b4 (nth bits 4)
        b5 (nth bits 5)
        actions (cond-> []
                  (= b0 \1) (conj :pressure-up)
                  (= b1 \1) (conj :pressure-down)
                  (= b4 \1) (conj :selectivity-up)
                  (= b5 \1) (conj :selectivity-down))]
    (if (seq actions) actions [:hold])))

(defn- choose-actions-sigil [sigils {:keys [pressure selectivity structure]}]
  (let [p (double (or pressure 0.0))
        s (double (or selectivity 0.0))
        t (double (or structure 0.0))
        bins 4
        idx (+ (* (min (dec bins) (int (* p bins))) bins)
               (min (dec bins) (int (* s bins))))
        idx (mod idx (count sigils))
        sigil (nth sigils idx)]
    {:sigil sigil
     :actions (sigil->actions sigil)}))

(defn- next-window
  [{:keys [genotype phenotype kernel exotype metrics-history gen-history phe-history]} opts]
  (let [{:keys [W S seed lesion]} opts
        result (runtime/run-mmca {:genotype genotype
                                  :phenotype phenotype
                                  :generations W
                                  :kernel kernel
                                  :lock-kernel false
                                  :exotype exotype
                                  :exotype-mode :inline
                                  :seed seed
                                  :lesion lesion})
        metrics-history' (into metrics-history (:metrics-history result))
        gen-history' (into gen-history (:gen-history result))
        phe-history' (into phe-history (:phe-history result))
        windows (metrics/windowed-macro-features
                 {:metrics-history metrics-history'
                  :gen-history gen-history'
                  :phe-history phe-history'}
                 {:W W :S (or S W)})
        window (last windows)]
    {:state {:genotype (or (last (:gen-history result)) genotype)
             :phenotype (or (last (:phe-history result)) phenotype)
             :kernel kernel
             :exotype exotype
             :metrics-history metrics-history'
             :gen-history gen-history'
             :phe-history phe-history'}
     :window window}))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(defn- run-controller
  [controller-id {:keys [seed windows W S kernel length sigil sigil-count kick-window kick-target kick-half]}]
  (let [rng (when seed (java.util.Random. (long seed)))
        genotype (if rng
                   (rng-sigil-string rng length)
                   (ca/random-sigil-string length))
        base-exotype (exotype/resolve-exotype {:sigil sigil :tier :super})
        sigils (when (= controller-id :sigil)
                 (sigils-for-count sigil-count))]
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
        (let [lesion (when (and (number? kick-window) (= idx kick-window))
                       (cond-> {:tick (quot W 2) :mode :zero}
                         kick-target (assoc :target kick-target)
                         kick-half (assoc :half kick-half)))
              {:keys [state window]} (next-window state {:W W :S S :seed (when seed (+ seed idx)) :lesion lesion})
              {:keys [actions chosen-sigil]} (cond
                                               (= controller-id :sigil)
                                               (let [{:keys [sigil actions]} (choose-actions-sigil sigils window)]
                                                 {:actions actions :chosen-sigil sigil})
                                               (= controller-id :null)
                                               {:actions [:hold]}
                                               :else
                                               {:actions (choose-actions-hex window)})
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
              exotype' (assoc (:exotype state) :params params-after)]
          (recur (inc idx)
                 (assoc state :exotype exotype')
                 (conj windows-out (assoc window
                                           :controller controller-id
                                           :seed seed
                                           :actions actions
                                           :sigil chosen-sigil
                                           :update-prob (:update-prob params-after)
                                           :match-threshold (:match-threshold params-after)
                                           :delta-update delta-update
                                           :delta-match delta-match
                                           :applied? applied?
                                           :kernel (:kernel state)))))))))

(defn- segment-lengths [pred xs]
  (->> xs
       (partition-by pred)
       (map (fn [seg]
              (let [v (first seg)]
                (when (pred v) (count seg)))))
       (remove nil?)))

(defn- stats-for-controller [windows]
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

(defn- rows->csv [rows header out-path]
  (spit out-path
        (str (str/join "," header)
             "\n"
             (str/join "\n" (map #(str/join "," %) rows)))))

(defn -main [& args]
  (let [{:keys [help unknown seeds windows W S length kernel sigil sigil-count out
                kick-window kick-target kick-half]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      :else
      (let [seeds (seq (or seeds [4242 1111 2222 3333]))
            windows (max 1 (int (or windows 12)))
            W (max 1 (int (or W 10)))
            S (max 1 (int (or S W)))
            length (max 1 (int (or length 32)))
            kernel (or kernel :mutating-template)
            sigil (or sigil ca/default-sigil)
            sigil-count (max 4 (int (or sigil-count 16)))
            out (or out "/tmp/cyber-mmca-compare.csv")
            all-windows (mapcat (fn [seed]
                                  (concat (run-controller :null {:seed seed
                                                                 :windows windows
                                                                 :W W
                                                                 :S S
                                                                 :kernel kernel
                                                                 :length length
                                                                 :sigil sigil
                                                                 :sigil-count sigil-count
                                                                 :kick-window kick-window
                                                                 :kick-target kick-target
                                                                 :kick-half kick-half})
                                          (run-controller :hex {:seed seed
                                                                :windows windows
                                                                :W W
                                                                :S S
                                                                :kernel kernel
                                                                :length length
                                                                :sigil sigil
                                                                :sigil-count sigil-count
                                                                :kick-window kick-window
                                                                :kick-target kick-target
                                                                :kick-half kick-half})
                                          (run-controller :sigil {:seed seed
                                                                  :windows windows
                                                                  :W W
                                                                  :S S
                                                                  :kernel kernel
                                                                  :length length
                                                                  :sigil sigil
                                                                  :sigil-count sigil-count
                                                                  :kick-window kick-window
                                                                  :kick-target kick-target
                                                                  :kick-half kick-half})))
                                seeds)
            grouped (group-by :controller all-windows)
            rows (map (fn [{:keys [controller seed w-start w-end regime pressure selectivity structure activity actions sigil delta-update delta-match applied?]}]
                        [(name controller)
                         seed
                         w-start
                         w-end
                         (or (some-> regime name) "")
                         (when (number? pressure) (format "%.4f" (double pressure)))
                         (when (number? selectivity) (format "%.4f" (double selectivity)))
                         (when (number? structure) (format "%.4f" (double structure)))
                         (when (number? activity) (format "%.4f" (double activity)))
                         (str/join "+" (map name actions))
                         (or sigil "")
                         (when (number? delta-update) (format "%.4f" (double delta-update)))
                         (when (number? delta-match) (format "%.4f" (double delta-match)))
                         (if applied? "true" "false")])
                      all-windows)]
        (rows->csv rows
                   ["controller" "seed" "w_start" "w_end" "regime" "pressure"
                    "selectivity" "structure" "activity" "actions" "sigil"
                    "delta_update" "delta_match" "applied"]
                   out)
        (doseq [[controller windows] grouped]
          (println (format "%s stats: %s" (name controller) (stats-for-controller windows))))
        (println "Wrote" out)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
