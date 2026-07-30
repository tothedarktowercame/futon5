(ns futon5.mmca.exotype-score
  "Adapter from heritable exotypes to the aligned causal-reach protocol.

   Rule fields are vectors of sigils with history and the t*=60 phenotype
   snapshot in metadata.  All stochasticity originates in the vectors drawn
   by `causal-score`: gain and update decisions use disjoint gate coins, while
   each local kernel receives a RNG seeded by its pre-drawn source value."
  (:require [futon5.ca.core :as ca]
            [futon5.mmca.causal-score :as causal-score]
            [futon5.mmca.exotype :as exotype]))

(def ^:private lattice-width 80)
(def ^:private snapshot-transition-time 59)

(defn- circular-at [xs index]
  (nth xs (mod index (count xs))))

(defn- bit [phenotype index]
  (Character/digit ^char (circular-at phenotype index) 2))

(defn- majority-bit [bits]
  (if (<= (* 2 (reduce + bits)) (count bits)) \0 \1))

(defn- rule-output [sigil phenotype index]
  (let [neighborhood (str (bit phenotype (dec index))
                          (bit phenotype index)
                          (bit phenotype (inc index)))
        rule (ca/local-rule-table sigil)]
    (char (+ (int \0) (get rule neighborhood)))))

(defn- phenotype-step [field phenotype]
  (apply str
         (map-indexed (fn [index sigil]
                        (rule-output sigil phenotype index))
                      field)))

(defn- phenotype-family
  "Compress the whole centred WIDTH-neighbourhood into the existing
   three-parent-plus-child exotype context.  The left and right parent bits are
   majorities of their respective half-neighbourhoods; width 3 therefore
   agrees exactly with the original [left self right child] context."
  [field phenotype index width]
  (let [half (quot width 2)
        left (map #(bit phenotype (+ index %)) (range (- half) 0))
        right (map #(bit phenotype (+ index %)) (range 1 (inc half)))]
    (str (majority-bit left)
         (circular-at phenotype index)
         (majority-bit right)
         (rule-output (nth field index) phenotype index))))

(defn- source-rng [source-a source-b]
  ;; `draw-count` supplies two source values alongside the two gate decisions.
  ;; Fold both into the isolated per-cell kernel tape; no supplied source value
  ;; is skipped or replaced by global randomness.
  (java.util.Random.
   (bit-xor (long source-a)
            (bit-shift-left (long source-b) 31))))

(defn- initial-field [genotype]
  (with-meta (mapv str genotype)
    {:previous-genotype (mapv str genotype)}))

(defn- validate-exotype [candidate]
  (let [resolved (exotype/resolve-exotype candidate)
        genotype (:initial-genotype resolved)
        {:keys [gain width update-prob]} (:params resolved)]
    (when-not (and (string? genotype)
                   (= lattice-width (count genotype))
                   (number? gain)
                   (<= 0.0 (double gain) 1.0)
                   (contains? (set exotype/width-levels) width)
                   (number? update-prob)
                   (<= 0.0 (double update-prob) 1.0))
      (throw (ex-info "Invalid evolvable exotype for causal scoring"
                      {:initial-genotype-length (some-> genotype count)
                       :gain gain
                       :width width
                       :update-prob update-prob})))
    resolved))

(defn configuration-for
  "Build the aligned `causal-score/reach` configuration for EXOTYPE.

   `:on-field-step`, when supplied, receives an audit map after every field
   update.  It is observational only and is useful for tape-alignment tests."
  ([candidate]
   (configuration-for candidate {}))
  ([candidate {:keys [on-field-step]}]
   (let [{:keys [params]} (validate-exotype candidate)
         {:keys [gain width update-prob]} params
         gain (double gain)
         update-prob (double update-prob)]
     {:phenotype-step phenotype-step
      ;; Two independent gate decisions per cell are drawn unconditionally.
      :draw-count (* 2 lattice-width)
      :source-limit Integer/MAX_VALUE
      :field-step
      (fn [{:keys [field phenotype time source-draws gate-coins]}]
        (let [previous (or (:previous-genotype (meta field)) field)
              snapshot (or (:frozen-phenotype (meta field))
                           (when (= time snapshot-transition-time)
                             (phenotype-step field phenotype)))
              gain-coins (subvec gate-coins 0 lattice-width)
              update-coins (subvec gate-coins lattice-width)
              cell-sources (subvec source-draws 0 lattice-width)
              cell-sources-2 (subvec source-draws lattice-width)
              next-field
              (mapv
               (fn [index ego]
                 (let [live? (< (nth gain-coins index) gain)
                       viewed (if (or live? (nil? snapshot))
                                phenotype
                                snapshot)
                       pred (circular-at field (dec index))
                       succ (circular-at field (inc index))
                       prev (nth previous index)
                       family (phenotype-family field viewed index width)
                       update? (< (nth update-coins index) update-prob)]
                   (if update?
                     (binding [ca/*rng*
                               (source-rng (nth cell-sources index)
                                           (nth cell-sources-2 index))]
                       (:sigil
                        (exotype/evolve-sigil-local
                         ego pred succ prev family {})))
                     ego)))
               (range lattice-width)
               field)
              next-field (with-meta next-field
                           {:previous-genotype field
                            :frozen-phenotype snapshot})]
          (when on-field-step
            (on-field-step {:time time
                            :phenotype phenotype
                            :source-draws source-draws
                            :gate-coins gate-coins
                            :gain-decisions (mapv #(< % gain) gain-coins)
                            :update-decisions
                            (mapv #(< % update-prob) update-coins)}))
          next-field))})))

(defn reach-for
  "Measure causal reach from EXOTYPE's inherited initial genotype.

   OPTS are forwarded to `causal-score/reach`; `:on-field-step` is consumed by
   this adapter.  The result intentionally exposes the scoring contract only."
  ([candidate]
   (reach-for candidate {}))
  ([candidate opts]
   (let [resolved (validate-exotype candidate)
         cfg (configuration-for resolved opts)
         field (initial-field (:initial-genotype resolved))
         reach-opts (dissoc opts :on-field-step)]
     (select-keys (causal-score/reach field cfg reach-opts)
                  [:mean :sd :n]))))
