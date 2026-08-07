(ns futon5.exotype.chain-risk
  "Exact discounted risk along each propagator's 256-byte Markov chain."
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.xenotype.generator :as gen]))

(def horizon 12)
(def discount 0.7)
(def risk-target 0.15)

(def ^:private epsilon 1.0e-9)

(defn- bernoulli-kl [q p]
  (let [clamp #(-> (double %) (max epsilon) (min (- 1.0 epsilon)))
        q (clamp q)
        p (clamp p)]
    (+ (* q (Math/log (/ q p)))
       (* (- 1.0 q) (Math/log (/ (- 1.0 q) (- 1.0 p)))))))

(defn byte-of
  "Convert a rule sigil to the integer convention used by the chain table."
  [sigil]
  (let [bits (ca/bits-for (str sigil))]
    (reduce (fn [byte position]
              (if (= \1 (nth bits position))
                (bit-or byte (bit-shift-left 1 position))
                byte))
            0
            (range 8))))

(defn- positional [kind]
  (gen/sigma-positional (get grid/propagators kind)))

(defn- successor [positions byte draw]
  (let [source-bit (bit-and (bit-shift-right byte draw) 1)
        destination (long (nth positions draw))
        new-bit (if (zero? source-bit) 1 0)]
    (-> byte
        (bit-and (bit-not (bit-shift-left 1 destination)))
        (bit-or (bit-shift-left new-bit destination)))))

(defn- kind-column [kind]
  (let [positions (positional kind)
        successors (vec (for [byte (range 256)]
                          (mapv #(successor positions byte %) (range 8))))
        realized-change
        (mapv (fn [byte]
                (/ (count (filter #(not= byte %)
                                  (nth successors byte)))
                   8.0))
              (range 256))
        initial (mapv #(bernoulli-kl % risk-target) realized-change)
        markov-step
        (fn [values]
          (mapv (fn [byte]
                  (/ (reduce + (map values (nth successors byte))) 8.0))
                (range 256)))
        depths (take horizon (iterate markov-step initial))
        weights (mapv #(Math/pow discount %) (range horizon))
        normalizer (reduce + weights)]
    (mapv (fn [byte]
            (/ (reduce +
                       (map (fn [weight values]
                              (* weight (nth values byte)))
                            weights depths))
               normalizer))
          (range 256))))

(def table
  "Discount-normalized chain risk keyed by [exotype-kind byte]."
  (into {}
        (for [kind grid/exotype-kinds
              :let [column (kind-column kind)]
              byte (range 256)]
          [[kind byte] (nth column byte)])))

(defn risk
  "Look up chain risk for KIND at integer BYTE."
  [kind byte]
  (or (get table [kind byte])
      (throw (ex-info "chain-risk lookup outside the cached table"
                      {:kind kind :byte byte}))))
