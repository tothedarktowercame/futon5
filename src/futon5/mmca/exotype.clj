(ns futon5.mmca.exotype
  "Exotypes: kernel-rewrite operators derived from sigils."
  (:require [futon5.ca.core :as ca]))

(def ^:private mix-modes
  [:none :rotate-left :rotate-right :reverse :xor-neighbor :majority :swap-halves :scramble])

(defn- normalize-sigil [sigil]
  (cond
    (nil? sigil) nil
    (string? sigil) sigil
    (keyword? sigil) (name sigil)
    (char? sigil) (str sigil)
    :else (str sigil)))

(defn- bits->int [bits]
  (reduce (fn [acc ch]
            (+ (* acc 2) (if (= ch \1) 1 0)))
          0
          bits))

(defn- sigil-bits [sigil]
  (ca/bits-for (normalize-sigil sigil)))

(defn- sigil->params [sigil]
  (let [bits (sigil-bits sigil)
        rotation (mod (bits->int (subs bits 0 2)) 4)
        threshold-idx (bits->int (subs bits 2 5))
        match-threshold (/ (double (inc threshold-idx)) 9.0)
        invert? (= \1 (nth bits 5))
        update-prob (nth [0.25 0.5 0.75 1.0] (bits->int (subs bits 6 8)))
        idx (bits->int bits)
        mix-mode (nth mix-modes (mod idx (count mix-modes)))
        mix-shift (mod (quot idx 3) 4)]
    {:rotation rotation
     :match-threshold match-threshold
     :invert-on-phenotype? invert?
     :update-prob update-prob
     :mix-mode mix-mode
     :mix-shift mix-shift}))

(defn lift
  "Lift a sigil into its local-rule exotype."
  [sigil]
  (let [sigil (normalize-sigil sigil)]
    {:sigil sigil
     :tier :local
     :params (sigil->params sigil)}))

(defn promote
  "Promote a sigil into its super exotype."
  [sigil]
  (let [sigil (normalize-sigil sigil)]
    {:sigil sigil
     :tier :super
     :params (sigil->params sigil)}))

(defn resolve-exotype
  "Accepts a sigil, keyword, or exotype map and returns a normalized exotype."
  [exotype]
  (cond
    (nil? exotype) nil
    (map? exotype) (let [sigil (normalize-sigil (:sigil exotype))]
                     (cond-> (assoc exotype
                                    :sigil sigil
                                    :tier (or (:tier exotype) :local))
                       (nil? (:params exotype)) (assoc :params (sigil->params sigil))))
    :else (lift exotype)))

(defn all-exotypes []
  (mapv (fn [{:keys [sigil]}]
          {:sigil sigil
           :lift (lift sigil)
           :super (promote sigil)})
        (ca/sigil-entries)))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- sigil-at [s idx]
  (if (and s (<= 0 idx) (< idx (count s)))
    (str (nth s idx))
    ca/default-sigil))

(defn- bit-at [s idx]
  (if (and s (<= 0 idx) (< idx (count s)))
    (nth s idx)
    \0))

(defn sample-context
  "Pick a single local context from the run history."
  [gen-history phe-history ^java.util.Random rng]
  (let [rows (count gen-history)
        cols (count (or (first gen-history) ""))]
    (when (and (> rows 1) (pos? cols))
      (let [t (rng-int rng (dec rows))
            x (rng-int rng cols)
            row (nth gen-history t)
            next-row (nth gen-history (inc t))
            pred (sigil-at row (dec x))
            self (sigil-at row x)
            next (sigil-at row (inc x))
            out (sigil-at next-row x)
            phe (when (and phe-history (> (count phe-history) (inc t)))
                  (let [phe-row (nth phe-history t)
                        phe-next (nth phe-history (inc t))]
                    (str (bit-at phe-row (dec x))
                         (bit-at phe-row x)
                         (bit-at phe-row (inc x))
                         (bit-at phe-next x))))]
        {:context-sigils [pred self next out]
         :phenotype-context phe
         :rotation (rng-int rng 4)}))))

(defn apply-exotype
  "Rewrite a kernel spec using an exotype and a sampled context."
  [kernel exotype context ^java.util.Random rng]
  (when (and kernel exotype context)
    (let [{:keys [tier params]} (resolve-exotype exotype)
          params (or params {})
          update-prob (double (or (:update-prob params) 1.0))
          update? (< (.nextDouble rng) update-prob)
          ctx (cond-> context
                (contains? params :match-threshold) (assoc :match-threshold (:match-threshold params))
                (contains? params :invert-on-phenotype?) (assoc :invert-on-phenotype? (:invert-on-phenotype? params))
                (contains? params :rotation) (assoc :rotation (:rotation params)))
          spec (ca/kernel-spec-for kernel)
          spec (if update?
                 (ca/mutate-kernel-spec-contextual spec ctx)
                 spec)
          spec (if (= :super tier)
                 (assoc spec
                        :mix-mode (:mix-mode params)
                        :mix-shift (:mix-shift params))
                 spec)]
      (-> (ca/normalize-kernel-spec spec)
          (assoc :label nil)))))
