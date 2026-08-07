(ns futon5.exotype.policy-epistemic
  "Policy-specific expected injection of one-step rule divergence.

   `pair-value` compares the cell's current propagator with a candidate
   propagator under the common draw used by the twin trajectories. It is a
   property of the ordered propagator pair, not of the current lattice state.
   The complete 12 x 12 selectable table is computed once at load time and
   every score-time lookup is O(1).

   Scope: this quantity predicts injection of new dynamics over the immediate
   effective horizon (roughly 5--20 steps). It is not a predictor of the
   magnitude of long-horizon damage, where chaotic mixing erases the signal."
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.xenotype.generator :as gen]))

(defn- pattern-weights
  "Local frequency of each three-cell phenotype pattern over [i-5,i+5]."
  [phenotype index]
  (let [width (count phenotype)
        positions (for [offset (range -5 6)]
                    (mod (+ index offset) width))
        triples (for [k positions]
                  (str (nth phenotype (mod (dec k) width))
                       (nth phenotype k)
                       (nth phenotype (mod (inc k) width))))
        counts (frequencies triples)]
    (into {}
          (for [pattern ca/truth-table-3]
            [pattern (/ (double (get counts pattern 0)) 11.0)]))))

(defn blend-value
  "Phenotype-frequency-weighted fraction of inputs on which the complete
   neighbour blend changes cell INDEX's rule output."
  [state index]
  (let [genotype (:genotype state)
        width (count genotype)
        rule-sigil (nth genotype index)
        left (nth genotype (mod (dec index) width))
        right (nth genotype (mod (inc index) width))
        blended (grid/blend-rule left rule-sigil right)
        rule-table (ca/local-rule-table (str rule-sigil))
        blend-table (ca/local-rule-table (str blended))
        weights (pattern-weights (:phenotype state) index)]
    ;; The mathematical weighted sum is in [0,1]. Clamp the occasional
    ;; 1.0000000000000002 produced by binary addition back to that contract.
    (-> (reduce +
                (for [pattern ca/truth-table-3]
                  (* (get weights pattern 0.0)
                     (if (not= (get rule-table pattern)
                               (get blend-table pattern))
                       1.0
                       0.0))))
        (max 0.0)
        (min 1.0)
        double)))

(defn- exact-pair-value [own candidate]
  (let [a (gen/sigma-positional (get grid/propagators own))
        b (gen/sigma-positional (get grid/propagators candidate))]
    (/ (reduce +
               (map-indexed
                (fn [k ak]
                  (let [bk (nth b k)]
                    (cond
                      (= ak bk) 0.0
                      (or (= ak k) (= bk k)) 1.0
                      :else 0.75)))
                a))
       8.0)))

(def pair-table
  "The precomputed 12 x 12 X_pair table, keyed by [own candidate]."
  (into {}
        (for [own grid/exotype-kinds
              candidate grid/exotype-kinds]
          [[own candidate] (exact-pair-value own candidate)])))

(defn pair-value
  "Expected fraction of the eight common draws for which CANDIDATE injects a
   rule divergence relative to OWN, averaged over all 256 rule bytes."
  [own candidate]
  (or (get pair-table [own candidate])
      (throw (ex-info "policy epistemic pair is outside the selectable vocabulary"
                      {:own own :candidate candidate
                       :selectable grid/exotype-kinds}))))
