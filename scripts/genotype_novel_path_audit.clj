(ns genotype-novel-path-audit
  (:require [clojure.set :as set]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(def width 256)
(def steps 40)
(def seeds [2026080601 2026080602 2026080603 2026080604])

(defn- permutation [seed]
  (let [xs (java.util.ArrayList. (mapv :sigil @ca/sigils))]
    (java.util.Collections/shuffle xs (java.util.Random. seed))
    (vec xs)))

(defn- initial-state [seed]
  (ca/with-seed seed
    (let [genotype (permutation seed)]
      {:arm :efe-full
       :seed seed
       :time 0
       :self-tuning-arm :fixed-0.55
       :lambda-step-size 0.0
       :hunger-target (:hunger efe/preferences)
       :lambdas (vec (repeat width 0.55))
       :genotype genotype
       :previous-genotype genotype
       :phenotype (apply str (repeatedly width #(if (< (ca/rnd) 0.5) \0 \1)))
       :exotypes (grid/initial-grid :heterogeneous-fixed width)
       :blend-action? true
       :policy-precision 16.0
       :blend-strength 0.0
       :epistemic-coefficient 0.2
       :apply-probability 1.0})))

(defn- path-for [decision]
  (if (= :blend (get-in decision [:winner :policy]))
    :policy-blend
    :exotype-transition))

(defn- step-counts [state advanced]
  (let [present (set (:genotype state))
        events (mapv (fn [sigil decision]
                       {:sigil sigil
                        :path (path-for decision)
                        :novel? (not (contains? present sigil))})
                     (:genotype advanced)
                     (:self-tuning-decisions advanced))
        novel (filterv :novel? events)
        paths-by-sigil (reduce (fn [m {:keys [sigil path]}]
                                 (update m sigil (fnil conj #{}) path))
                               {} novel)]
    {:events (frequencies (map :path novel))
     :novel-values (count paths-by-sigil)
     :value-attribution (frequencies
                         (map (fn [[_ paths]]
                                (if (= 1 (count paths)) (first paths) :mixed))
                              paths-by-sigil))
     :selected (frequencies (map path-for (:self-tuning-decisions advanced)))}))

(defn- merge-counts [a b]
  (merge-with + (or a {}) (or b {})))

(defn- run-seed [seed]
  (loop [state (initial-state seed)
         t 0
         totals {:events {} :value-attribution {} :selected {}}
         recurrence-steps 0]
    (if (= t steps)
      (assoc totals :seed seed :recurrence-steps recurrence-steps)
      (let [advanced (tuning/step state)
            counts (step-counts state advanced)
            recurrent? (pos? (reduce + 0 (vals (:events counts))))]
        (recur advanced (inc t)
               (-> totals
                   (update :events merge-counts (:events counts))
                   (update :value-attribution merge-counts
                           (:value-attribution counts))
                   (update :selected merge-counts (:selected counts)))
               (+ recurrence-steps (if recurrent? 1 0)))))))

(defn- all-byte-strings []
  (set (for [n (range 256)]
         (let [s (Integer/toBinaryString n)]
           (str (apply str (repeat (- 8 (count s)) \0)) s)))))

(defn -main [& _]
  (let [runs (mapv run-seed seeds)
        aggregate (reduce (fn [a r]
                            (-> a
                                (update :events merge-counts (:events r))
                                (update :value-attribution merge-counts
                                        (:value-attribution r))
                                (update :selected merge-counts (:selected r))))
                          {:events {} :value-attribution {} :selected {}}
                          runs)
        table-bits (set (map :bits @ca/sigils))]
    (prn {:configuration {:width width :steps steps :seeds seeds
                          :policy-precision 16.0 :blend-strength 0.0
                          :epistemic-coefficient 0.2 :apply-probability 1.0
                          :self-tuning-arm :fixed-0.55 :blend-action? true}
          :runs runs
          :aggregate aggregate
          :sigil-table {:entries (count @ca/sigils)
                        :unique-bits (count table-bits)
                        :missing-byte-strings
                        (count (set/difference (all-byte-strings) table-bits))}})))

(apply -main *command-line-args*)
