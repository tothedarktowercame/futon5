(ns exotype-grid-slice1
  "Slice-1 paired three-layer damage experiment and anchor calibration."
  (:require [clojure.java.io :as io]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]))

(def config {:seed-base 20260803 :seeds 24 :width 80
             :burn-in 60 :damage-steps 59 :site 40})

(defn- initial-state [arm seed width exotype-override]
  (ca/with-seed seed
    {:arm arm
     :seed seed
     :time 0
     :genotype (vec (ca/random-sigil-string width))
     :phenotype (apply str (repeatedly width #(if (< (ca/rnd) 0.5) \0 \1)))
     :exotypes (if exotype-override
                 (vec (repeat width exotype-override))
                 (grid/initial-grid arm width))}))

(defn- flip-phenotype [state site]
  (update state :phenotype
          #(apply str (assoc (vec %) site (if (= \0 (nth % site)) \1 \0)))))

(defn- flip-genotype [state site]
  (update-in state [:genotype site]
             (fn [sigil]
               (let [bits (vec (ca/bits-for (str sigil)))]
                 (ca/sigil-for
                  (apply str (update bits 0 #(if (= \0 %) \1 \0))))))))

(defn- flip-exotype [state site]
  (update-in state [:exotypes site]
             (fn [value]
               (nth grid/exotype-kinds
                    (mod (inc (.indexOf grid/exotype-kinds value))
                         (count grid/exotype-kinds))))))

(defn- difference [a b]
  (count (filter true? (map not= a b))))

(defn damage-run
  ([arm seed] (damage-run arm seed nil))
  ([arm seed exotype-override]
   (let [{:keys [width burn-in damage-steps site]} config
         burned (grid/run-steps (initial-state arm seed width exotype-override)
                                burn-in)]
     (into (sorted-map)
           (for [[layer perturb key]
                 [[:phenotype flip-phenotype :phenotype]
                  [:genotype flip-genotype :genotype]
                  [:exotype flip-exotype :exotypes]]]
             (let [control (grid/run-steps burned damage-steps)
                   treated (grid/run-steps (perturb burned site) damage-steps)]
               [layer (difference (key control) (key treated))]))))))

(defn- mean [values]
  (/ (reduce + 0.0 values) (double (count values))))

(defn- sample-sd [values]
  (if (< (count values) 2) 0.0
      (let [m (mean values)]
        (Math/sqrt
         (/ (reduce + (map #(let [d (- (double %) m)] (* d d)) values))
            (double (dec (count values))))))))

(defn- summary [values]
  {:mean (mean values) :sd (sample-sd values) :n (count values)})

(defn- calibrate-band [ordered turbulent]
  (cond
    (= ordered turbulent) {:status :unbanded :reason :anchors-equal
                           :ordered ordered :turbulent turbulent}
    (> ordered turbulent) {:status :unbanded :reason :anchors-inverted
                           :ordered ordered :turbulent turbulent}
    :else {:status :banded :low ordered :high turbulent}))

(defn- in-band? [band value]
  (and (= :banded (:status band))
       (<= (:low band) value (:high band))))

(defn- paired-contrast [rows left right]
  (into (sorted-map)
        (for [layer [:phenotype :genotype :exotype]
              :let [deltas (mapv #(- (get-in rows [right % layer])
                                     (get-in rows [left % layer]))
                                 (range (:seeds config)))]]
          [layer (assoc (summary deltas) :direction :right-minus-left)])))

(defn experiment []
  (let [seeds (mapv #(+ (:seed-base config) %) (range (:seeds config)))
        anchors
        (into (sorted-map)
              (for [anchor [:collapser :chaos]]
                [anchor (mapv #(damage-run :uniform-fixed % anchor) seeds)]))
        bands
        (into (sorted-map)
              (for [layer [:phenotype :genotype :exotype]]
                [layer (calibrate-band
                        (mean (map layer (:collapser anchors)))
                        (mean (map layer (:chaos anchors))))]))
        raw (into (sorted-map)
                  (for [arm grid/arms]
                    [arm (mapv #(damage-run arm %) seeds)]))
        arms
        (into (sorted-map)
              (for [[arm runs] raw]
                [arm
                 {:damage (into (sorted-map)
                                (for [layer [:phenotype :genotype :exotype]]
                                  [layer (summary (map layer runs))]))
                  :band-fraction
                  (into (sorted-map)
                        (for [layer [:phenotype :genotype :exotype]]
                          [layer (when (= :banded (get-in bands [layer :status]))
                                   (/ (count (filter #(in-band? (bands layer) (layer %)) runs))
                                      (double (count runs))))]))
                  :joint-fraction
                  (when (every? #(= :banded (get-in bands [% :status]))
                                [:phenotype :genotype :exotype])
                    (/ (count (filter #(every? (fn [layer]
                                                 (in-band? (bands layer) (layer %)))
                                               [:phenotype :genotype :exotype]) runs))
                       (double (count runs))))}]))]
    {:kind :exotype-grid-slice1 :schema 1 :config config
     :seeds seeds
     :anchors (into (sorted-map)
                    (for [[anchor runs] anchors]
                      [anchor (into (sorted-map)
                                    (for [layer [:phenotype :genotype :exotype]]
                                      [layer (summary (map layer runs))]))]))
     :bands bands :arms arms
     :contrasts
     {:variation (paired-contrast raw :uniform-fixed :heterogeneous-fixed)
      :transmission
      {:conformist (paired-contrast raw :heterogeneous-fixed :conformist)
       :boring-triggered
       (paired-contrast raw :heterogeneous-fixed :boring-triggered)}}}))

(defn- fmt-summary [{:keys [mean sd]}]
  (format "%.3f ± %.3f" mean sd))

(defn markdown [result]
  (str "# Exotype-grid Slice 1\n\n"
       "Fixed seeds `" (first (:seeds result)) "`–`" (last (:seeds result))
       "`; N=`" (count (:seeds result)) "` per arm; width 80; t*=60; dt=59.\n\n"
       "The conformist policy takes a strict majority over left/self/right and retains self on a three-way tie. The boring-triggered policy copies the left neighbour when `boring?` is true; left is the fixed, locally available tie-break direction. All three grids update synchronously. Damage flips the midpoint phenotype bit, the first truth-table bit of the midpoint genotype rule, or advances the midpoint exotype to the next vocabulary entry, respectively.\n\n"
       "| arm | phenotype damage | genotype damage | exotype damage | pheno band | geno band | exo band | joint |\n"
       "|---|---:|---:|---:|---:|---:|---:|---:|\n"
       (apply str
              (for [[arm row] (:arms result)]
                (format "| %s | %s | %s | %s | %s | %s | %s | %s |\n"
                        (name arm)
                        (fmt-summary (get-in row [:damage :phenotype]))
                        (fmt-summary (get-in row [:damage :genotype]))
                        (fmt-summary (get-in row [:damage :exotype]))
                        (pr-str (get-in row [:band-fraction :phenotype]))
                        (pr-str (get-in row [:band-fraction :genotype]))
                        (pr-str (get-in row [:band-fraction :exotype]))
                        (pr-str (:joint-fraction row)))))
       "\n## Measured anchors and bands\n\n```clojure\n"
       (pr-str {:anchors (:anchors result) :bands (:bands result)})
       "\n```\n\nThe band is the inclusive interval from the measured collapser mean to the measured chaos mean. Equal or inverted anchors leave that layer explicitly unbanded. Because at least one layer is unbanded, `joint` is reported as `nil`, not imputed.\n\n"
       "## Paired contrasts\n\n```clojure\n" (pr-str (:contrasts result)) "\n```\n"))

(defn -main [& [edn-path md-path]]
  (let [edn-path (or edn-path "reports/exotype-grid-slice1.edn")
        md-path (or md-path "reports/exotype-grid-slice1.md")
        result (experiment)]
    (io/make-parents edn-path)
    (spit edn-path (str (pr-str result) "\n"))
    (spit md-path (markdown result))
    (doseq [[arm row] (:arms result)]
      (println (name arm)
               (into {} (map (fn [[layer stats]] [layer (:mean stats)]))
                     (:damage row))
               :joint (:joint-fraction row)))))

(apply -main *command-line-args*)
