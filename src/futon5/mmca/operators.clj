(ns futon5.mmca.operators
  "Executable MetaMetaCA operator hooks for key lifted patterns."
  (:require [clojure.string :as str]
            [futon5.ca.core :as ca]))

(defn- clamp [x lo hi]
  (max lo (min hi x)))

(defn- parse-number [value]
  (try
    (cond
      (number? value) (double value)
      (string? value) (Double/parseDouble value)
      :else nil)
    (catch Exception _ nil)))

(defn- number-param [params k default]
  (or (parse-number (get params k)) (double default)))

(defn- int-param [params k default]
  (max 1 (int (Math/round (number-param params k default)))))

(defn- ratio-param [params k default]
  (let [n (number-param params k default)]
    (cond
      (< n 0.0) 0.0
      (> n 1.0) 1.0
      :else n)))

(defn- choose-child [value default]
  (cond
    (map? value) value
    (keyword? value) {:kernel value}
    (string? value)
    (let [v (str/lower-case value)]
      (cond
        (str/includes? v "flip") {:kernel :blending-flip}
        (str/includes? v "mutate") {:kernel :blending-mutation}
        (str/includes? v "blend") {:kernel :blending}
        :else default))
    :else default))

(defn- select-blend-mode [params]
  (let [raw (get params :blend_mode)
        v (cond
            (keyword? raw) raw
            (string? raw) (cond
                           (re-find #"soft" (str/lower-case raw)) :soft-blend
                           (re-find #"switch" (str/lower-case raw)) :hard-switch
                           :else nil)
            :else nil)]
    (or v :hard-switch)))

(defn- select-selector [params]
  (let [raw (get params :selector)
        v (cond
            (keyword? raw) raw
            (string? raw)
            (let [s (str/lower-case raw)]
              (cond
                (str/includes? s "entropy") :entropy
                (str/includes? s "change") :change-rate
                (str/includes? s "motif") :motif-density
                :else nil))
            :else nil)]
    (or v :entropy)))

(defn- string-chars [s]
  (vec (seq (or s ""))))

(defn- random-char []
  (first (ca/random-sigil)))

(defn- reinforce-genotype [genotype targets rate]
  (let [chars (string-chars genotype)
        len (count chars)
        steps (max 1 (int (* len (max rate 0.0))))]
    (if (or (zero? len) (empty? targets))
      genotype
      (apply str
             (loop [idx steps
                    acc chars]
               (if (zero? idx)
                 acc
                 (let [pos (rand-int len)
                       replacement (rand-nth targets)]
                   (recur (dec idx) (assoc acc pos replacement)))))))))

;; -----------------------------------------------------------------------------
;; Baldwin Lift

(defn baldwin-lift
  [{:keys [lift]}]
  (let [params (:parameters lift)
        history-window (int-param params :history_window 8)
        persistence-thresh (int-param params :persistence_thresh 3)
        learning-rate (ratio-param params :learning_rate 0.2)
        update-history (fn [history genotype]
                         (->> (conj (vec history) genotype)
                              (take-last history-window)
                              vec))
        persistent-sigils (fn [history]
                            (->> history
                                 (mapcat seq)
                                 frequencies))]
    {:pattern (:role lift)
     :impl :baldwin-lift
     :hooks
     {:init (fn [& _]
              {:meta {:history []
                      :persistence {}
                      :reinforce []}})
      :observe
      (fn [& {:keys [world meta]}]
        (let [history (update-history (:history meta) (:genotype world))
              counts (persistent-sigils history)]
          {:meta (assoc meta :history history :persistence counts)
           :metrics {:baldwin/persistence counts}}))
      :decide
      (fn [& {:keys [meta]}]
        (let [persist (->> (:persistence meta)
                           (filter (fn [[_ cnt]] (>= cnt persistence-thresh)))
                           (map first)
                           vec)]
          {:meta (assoc meta :reinforce persist)}))
      :act
      (fn [& {:keys [world meta]}]
        (let [targets (:reinforce meta)]
          (if (seq targets)
            {:grid (fn [genotype]
                     (reinforce-genotype genotype targets learning-rate))
             :metrics {:baldwin/targets (count targets)}}
            {:metrics {:baldwin/targets 0}})))}
     :parameters params}))

;; -----------------------------------------------------------------------------
;; Entropy Pulse

(def ^:private pulse-kernels
  [:blending-mutation :blending-3 :mutating-template :ad-hoc-template])

(defn- flip-sigils [genotype fraction]
  (let [chars (string-chars genotype)
        len (count chars)]
    (if (zero? len)
      genotype
      (loop [remaining (max 1 (int (* len (max 0.0 fraction))))
             acc chars]
        (if (zero? remaining)
          (apply str acc)
          (let [idx (rand-int len)
                replacement (random-char)]
            (recur (dec remaining) (assoc acc idx replacement))))))))

(defn entropy-pulse
  [{:keys [lift]}]
  (let [params (:parameters lift)
        pulse-period (int-param params :pulse_period 12)
        entropy-low (number-param params :entropy_low 0.8)
        entropy-high (number-param params :entropy_high 3.0)
        flip-fraction (ratio-param params :flip_fraction 0.15)
        switch-prob (ratio-param params :rule_switch_prob 0.35)]
     {:pattern (:role lift)
     :impl :entropy-pulse
     :hooks
     {:init (fn [& _]
              {:meta {:step 0
                      :last-entropy nil
                      :pulse? false}})
      :observe
      (fn [& {:keys [world meta]}]
        (let [entropy (get-in world [:metrics :entropy] 0.0)
              step (inc (:step meta 0))
              pulse? (or (>= step pulse-period)
                         (< entropy entropy-low)
                         (> entropy entropy-high))]
          {:meta (assoc meta
                        :step (if pulse? 0 step)
                        :last-entropy entropy
                        :pulse? pulse?)
           :metrics {:entropy-pulse/entropy entropy
                     :entropy-pulse/pulse pulse?}}))
      :decide (fn [& _] {})
      :act
      (fn [& {:keys [meta]}]
        (if (:pulse? meta)
          (let [new-kernel (when (< (rand) switch-prob)
                             (rand-nth pulse-kernels))]
            {:grid (fn [genotype]
                     (flip-sigils genotype flip-fraction))
             :rule (when new-kernel {:kernel new-kernel})
             :metrics {:entropy-pulse/fired true}})
          {:metrics {:entropy-pulse/fired false}}))}
     :parameters params}))

;; -----------------------------------------------------------------------------
;; Blend Hand

(defn- selector-alpha [selector world]
  (let [metrics (:metrics world)]
    (case selector
      :change-rate (double (or (:change-rate metrics) 0.0))
      :motif-density
      (let [counts (or (:motif_counts metrics) {})
            total (double (reduce + 0 (vals counts)))
            variety (double (count counts))]
        (if (pos? variety)
          (min 1.0 (/ total (* variety 4.0)))
          0.0))
      :entropy
      (let [entropy (double (or (:entropy metrics) 0.0))
            denominator (Math/log (max 2 (count (:genotype world))))]
        (if (pos? denominator)
          (min 1.0 (/ entropy denominator))
          0.0))
      :random (rand)
      0.5)))

(def default-child-a {:kernel :blending})
(def default-child-b {:kernel :mutating-template})

(defn blend-hand
  [{:keys [lift]}]
  (let [params (:parameters lift)
        selector (select-selector params)
        mode (select-blend-mode params)
        child-a (choose-child (:childA params) default-child-a)
        child-b (choose-child (:childB params) default-child-b)
        child-kernel (fn [{:keys [kernel kernel-fn]}]
                       (cond
                         kernel-fn {:kernel :custom :kernel-fn kernel-fn}
                         kernel {:kernel kernel}
                         :else {:kernel :mutating-template}))]
     {:pattern (:role lift)
     :impl :blend-hand
     :hooks
     {:init (fn [& _] {:meta {:alpha 0.5}})
      :observe
      (fn [& {:keys [world meta]}]
        (let [alpha (clamp (selector-alpha selector world) 0.0 1.0)]
          {:meta (assoc meta :alpha alpha)
           :metrics {:blend-hand/alpha alpha}}))
      :decide (fn [& _] {})
      :act
      (fn [& {:keys [meta]}]
        (let [alpha (:alpha meta 0.5)]
          (if (= mode :soft-blend)
            (let [kernel-a (ca/kernel-fn (:kernel child-a :blending))
                  kernel-b (ca/kernel-fn (:kernel child-b :mutating-template))
                  blended (fn [sigil pred next context]
                            (if (< (rand) alpha)
                              (kernel-a sigil pred next context)
                              (kernel-b sigil pred next context)))]
              {:rule {:kernel :blend-hand
                      :kernel-fn blended}
               :metrics {:blend-hand/mode :soft
                         :blend-hand/alpha alpha}})
            (let [chosen (if (>= alpha 0.5) child-a child-b)]
              {:rule (child-kernel chosen)
               :metrics {:blend-hand/mode :hard
                         :blend-hand/alpha alpha
                         :blend-hand/choice (if (>= alpha 0.5) :childA :childB)}}))))}
     :parameters params}))

;; -----------------------------------------------------------------------------
;; Uplift Operator

(defn- motif-windows [s window]
  (let [chars (string-chars s)
        len (count chars)]
    (if (or (zero? window) (> window len))
      []
      (map (fn [idx]
             (apply str (subvec chars idx (+ idx window))))
           (range (inc (- len window)))))))

(defn- decay-counts [counts]
  (into {} (map (fn [[motif count]] [motif (* 0.9 (double count))]) counts)))

(defn- reinforce-motifs [genotype motifs learning-rate]
  (reduce (fn [g motif]
            (if (< (rand) learning-rate)
              (let [chars (string-chars g)
                    motif-chars (string-chars motif)
                    len (count chars)
                    mlen (count motif-chars)]
                (cond
                  (zero? len) motif
                  (zero? mlen) g
                  (> mlen len)
                  (apply str motif-chars)
                  :else
                  (let [start (rand-int (inc (- len mlen)))
                        updated (reduce (fn [acc [offset ch]]
                                          (assoc acc (+ start offset) ch))
                                        chars
                                        (map-indexed vector motif-chars))]
                    (apply str updated))))
              g))
          genotype
          motifs))

(defn uplift-operator
  [{:keys [lift]}]
  (let [params (:parameters lift)
        window (int-param params :window_size 4)
        threshold (int-param params :freq_thresh 3)
        learning-rate (ratio-param params :learning_rate 0.25)
        max-motifs 48]
    {:pattern (:role lift)
     :impl :uplift-operator
     :hooks
     {:init (fn [& _]
              {:meta {:motif-counts {}
                      :operator-set #{}}})
      :observe
      (fn [& {:keys [world meta]}]
        (let [motifs (motif-windows (:genotype world) window)
              decayed (decay-counts (:motif-counts meta))
              counts (reduce (fn [acc motif]
                               (update acc motif (fnil inc 0)))
                             decayed
                             motifs)
              trimmed (into {} (take max-motifs (sort-by val > counts)))]
          {:meta (assoc meta :motif-counts trimmed)
           :metrics {:motif_counts trimmed}}))
      :decide
      (fn [& {:keys [meta]}]
        (let [promoted (->> (:motif-counts meta)
                             (filter (fn [[_ cnt]] (>= cnt threshold)))
                             (map first)
                             set)]
          {:meta (-> meta
                     (assoc :promoted promoted
                            :operator-set (set (concat (:operator-set meta) promoted))))
           :metrics {:uplift/operators (count promoted)}}))
      :act
      (fn [& {:keys [meta world]}]
        (let [motifs (seq (:promoted meta))]
          (if (seq motifs)
            {:grid (fn [genotype]
                     (reinforce-motifs genotype motifs learning-rate))
             :metrics {:uplift/promoted (count motifs)
                       :uplift/operator-set (:operator-set meta)}}
            {:metrics {:uplift/promoted 0}})))}
     :parameters params}))

;; -----------------------------------------------------------------------------
;; Learned Sigil Operator

(defn- evolve-with-rule-sigil [genotype rule-sigil]
  (let [letters (string-chars genotype)
        len (count letters)
        rule (ca/safe-sigil rule-sigil)]
    (cond
      (zero? len) ""
      (= 1 len) (:sigil (ca/evolve-sigil rule ca/default-sigil ca/default-sigil nil))
      :else
      (let [head (:sigil (ca/evolve-sigil rule ca/default-sigil (letters 1) nil))
            tail (:sigil (ca/evolve-sigil rule (letters (- len 2)) ca/default-sigil nil))
            mids (map (fn [idx]
                        (:sigil (ca/evolve-sigil rule
                                                 (letters (dec idx))
                                                 (letters (inc idx))
                                                 nil)))
                      (range 1 (dec len)))]
        (apply str (concat [head] mids [tail]))))))

(defn- blend-genotype [genotype evolved rate]
  (if (and (string? genotype)
           (string? evolved)
           (= (count genotype) (count evolved)))
    (apply str
           (map (fn [old new]
                  (if (< (rand) rate) new old))
                genotype
                evolved))
    evolved))

(defn learned-sigil-op
  [{:keys [sigil parameters source]}]
  (let [params (merge {:pulse_period 6
                       :apply_rate 0.35}
                      parameters)
        period (int-param params :pulse_period 6)
        apply-rate (ratio-param params :apply_rate 0.35)
        rule (ca/safe-sigil sigil)]
    {:sigil sigil
     :pattern "learned-sigil"
     :impl :learned-sigil
     :context {:source source}
     :parameters params
     :hooks
     {:init (fn [& _]
              {:meta {:step 0
                      :fire? false}})
      :observe
      (fn [& {:keys [meta]}]
        (let [step (inc (:step meta 0))
              fire? (>= step period)]
          {:meta (assoc meta :step (if fire? 0 step) :fire? fire?)
           :metrics {:learned-sigil/period period
                     :learned-sigil/fire fire?}}))
      :decide (fn [& _] {})
      :act
      (fn [& {:keys [world meta]}]
        (if (:fire? meta)
          (let [genotype (:genotype world)
                evolved (evolve-with-rule-sigil genotype rule)
                blended (blend-genotype genotype evolved apply-rate)]
            {:grid blended
             :metrics {:learned-sigil/applied true
                       :learned-sigil/rate apply-rate}})
          {:metrics {:learned-sigil/applied false}}))}}))
