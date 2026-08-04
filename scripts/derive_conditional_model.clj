(ns derive-conditional-model
  "Derive P(next local observation | exotype, current local observation) by
   MEASURING the substrate, and write it to a resource.

   The legacy `efe/fixed-model` is keyed by exotype alone -- three hand-typed
   numbers per kind -- and `predict` fakes the conditional dependence afterwards
   with a hardcoded 50/50 blend. Measured against held-out seeds, that model is
   WORSE THAN PREDICTING A CONSTANT on every channel (TN-baldwin-reboot.md 28).

   Exotype assignment is random at initialisation and fixed under
   `:heterogeneous-fixed`, so conditioning on the exotype a cell actually carried
   is unconfounded -- no counterfactual re-simulation is needed.

   Bins use INTEGER counts, not the doubles `local-observation` returns: activity
   is k/3 for k in 0..2 and diversity is k/3 for k in 1..3, so the numerator is
   the honest key and floating-point equality never enters a lookup.

   Regenerate:  clojure -M scripts/derive_conditional_model.clj
   Determinism and coverage are enforced by futon5.exotype.invariants-test."
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]))

(def config
  "Frozen. Changing any of this changes the derived model, so it is recorded in
   the resource and pinned by test.

   S0b (TN-baldwin-reboot.md 42.1): :vocabulary was widened from the default
   :declared (the four exotype-kinds) to :all (all 12 propagators). This is a
   BEHAVIOUR CHANGE: the mixture the rows are derived under changes, so even the
   four declared kinds' rows may move. The 4-kind resource is retained at
   /tmp/conditional-model-4kind.edn for A/B comparison.

   :seeds was expanded from [11 22 33] to twelve to maintain per-bin density:
   12 kinds x up to 9 observation bins = 108 bins, and at 3 seeds x 60 cells x
   160 steps = 28800 transitions that is ~267/bin on average -- adequate, but
   sparse bins (rare activity/diversity pairs for uncommon kinds) benefit from
   the fourfold increase."
  {:seeds [11 22 33 44 55 66 77 88 99 111 222 333]
   :width 60 :steps 160 :channels [:activity :diversity :hunger]
   :vocabulary :all})

(defn- vocabulary-kinds
  "Which exotypes to draw from for the derivation grid. :declared is the original
   four (exotype-kinds); :all is every key in propagators. The deriver must NOT
   call grid/initial-grid, because that hardcodes exotype-kinds and would ignore
   the widening -- it builds the grid directly."
  []
  (case (:vocabulary config)
    :declared grid/exotype-kinds
    :all (keys grid/propagators)))

(defn- transitions [seed]
  (let [w (:width config)
        kinds (vocabulary-kinds)
        st (ca/with-seed seed
             {:arm :heterogeneous-fixed :seed seed :time 0
              :exotypes (vec (repeatedly w #(ca/rnd-nth kinds)))
              :genotype (vec (ca/random-sigil-string w))
              :phenotype (ca/random-phenotype-string w)})]
    (loop [s (grid/step st) t 1 acc []]
      (if (= t (:steps config))
        acc
        (let [nx (grid/step s)]
          (recur nx (inc t)
                 (into acc
                       (for [i (range w)]
                         (let [o (efe/local-observation s i)
                               o' (efe/local-observation nx i)]
                           [(efe/observation-bin (nth (:exotypes s) i) o)
                            {:activity (:activity o')
                             :diversity (:diversity o')
                             :hunger (if (:hungry? o') 1.0 0.0)}])))))))))

(def schema-version
  "Bump when the shape of the emitted map changes, so a stale artifact is
   detectable from the artifact alone (codex-12 #5). Bumped to 2 at S0b: the
   config now carries :vocabulary, and the resource spans 12 kinds not 4."
  2)

(defn- source-fingerprint
  "SHA-256 over the three files that determine the output: this generator, the
   substrate it measures, and the observation code it calls. The config alone
   establishes reproducibility only if the implementation is pinned too -- an
   artifact should say which code produced it."
  []
  (let [digest (java.security.MessageDigest/getInstance "SHA-256")]
    (doseq [path ["scripts/derive_conditional_model.clj"
                  "src/futon5/exotype/grid.clj"
                  "src/futon5/exotype/efe.clj"]]
      (.update digest (.getBytes (slurp path) "UTF-8")))
    (apply str (map #(format "%02x" %) (take 8 (.digest digest))))))

(defn derive-model []
  (let [rows (mapcat transitions (:seeds config))]
    {:schema-version schema-version
     :source-fingerprint (source-fingerprint)
     :config config
     :sample-count (count rows)
     :global (into {} (for [ch (:channels config)]
                        [ch (/ (reduce + (map #(get (second %) ch) rows))
                               (double (count rows)))]))
     :bins (into (sorted-map)
                 (for [[k g] (group-by first rows)]
                   [k (assoc (into {} (for [ch (:channels config)]
                                        [ch (/ (reduce + (map #(get (second %) ch) g))
                                               (double (count g)))]))
                             :n (count g))]))}))

(defn -main [& _]
  (let [model (derive-model)
        path "resources/futon5/exotype/conditional-model.edn"]
    (io/make-parents path)
    (spit path (with-out-str (pp/pprint model)))
    (println (format "wrote %s" path))
    (println (format "  vocabulary: %s (%d kinds)" (:vocabulary config) (count (vocabulary-kinds))))
    (println (format "  %d transitions -> %d bins" (:sample-count model) (count (:bins model))))
    (println (format "  smallest bin: n=%d" (apply min (map :n (vals (:bins model))))))))

(-main)
