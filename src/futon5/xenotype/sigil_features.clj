(ns futon5.xenotype.sigil-features
  (:require [futon5.ca.core :as ca]))

(def ^:private mix-mode->value
  {:none 0.0
   :mix-shift 0.2
   :rotate-left 0.4
   :rotate-right 0.6
   :xor 0.8
   :and 0.1
   :or 0.9})

(defn- clamp-01 [x]
  (cond
    (not (number? x)) 0.0
    (< x 0.0) 0.0
    (> x 1.0) 1.0
    :else (double x)))

(defn- hamming-weight [bits]
  (count (filter #(= \1 %) (seq (or bits "")))))

(defn- sigil->bits [sigil]
  (when sigil
    (ca/bits-for sigil)))

(defn- sigil->int [bits]
  (try
    (Integer/parseInt (or bits "0") 2)
    (catch Exception _ 0)))

(defn- normalize [x denom]
  (if (and (number? x) (number? denom) (pos? denom))
    (clamp-01 (/ (double x) (double denom)))
    0.0))

(defn feature-vector
  "Return a fixed-order feature vector derived from the run exotype."
  [run]
  (let [{:keys [sigil tier params]} (:exotype run)
        bits (sigil->bits sigil)
        sigil-int (sigil->int bits)
        sigil-weight (normalize (hamming-weight bits) 8.0)
        update-prob (clamp-01 (:update-prob params))
        match-threshold (clamp-01 (:match-threshold params))
        rotation (normalize (:rotation params) 3.0)
        mix-shift (normalize (:mix-shift params) 3.0)
        invert (if (:invert-on-phenotype? params) 1.0 0.0)
        tier-val (case tier :super 1.0 :local 0.0 0.0)
        mix-mode (get mix-mode->value (:mix-mode params) 0.0)
        sigil-int-n (normalize sigil-int 255.0)]
    [sigil-int-n sigil-weight update-prob match-threshold rotation mix-shift invert tier-val mix-mode]))

(defn feature-map
  "Return a labeled map of sigil features."
  [run]
  (let [{:keys [sigil tier params]} (:exotype run)
        bits (sigil->bits sigil)
        sigil-int (sigil->int bits)]
    {:sigil sigil
     :sigil-bits bits
     :sigil-int sigil-int
     :sigil-weight (hamming-weight bits)
     :update-prob (:update-prob params)
     :match-threshold (:match-threshold params)
     :rotation (:rotation params)
     :mix-shift (:mix-shift params)
     :invert-on-phenotype? (:invert-on-phenotype? params)
     :tier tier
     :mix-mode (:mix-mode params)}))
