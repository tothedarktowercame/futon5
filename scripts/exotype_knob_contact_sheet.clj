(ns exotype-knob-contact-sheet
  "KNOB SWEEP CONTACT SHEET -- one row of space-time diagrams per knob.

   Joe, 2026-08-05, on being offered a leverage heatmap: `this doesn't quite
   sound like a HIT`. Correct. A 4x6 matrix of numbers is a table with colours;
   it is not the perceptual read he does on space-time diagrams, where regime
   and texture are visible directly.

   So: hold everything fixed, sweep ONE knob, render the space-time diagram at
   each setting, and put them side by side with the same seed. If turning the
   knob changes the texture, the knob has leverage on something that matters to
   the eye. If every panel looks the same, it does not -- and that is the
   controller-design question answered perceptually rather than statistically.

   This is deliberately the raw phenomenon, not a derived statistic. It pairs
   with scripts/exotype_knob_census.clj: the census gives the number, this gives
   the read, and DISAGREEMENT BETWEEN THEM IS DIAGNOSTIC -- it has fired in both
   directions repeatedly in this work (a chaotic configuration that looked like
   the edge; a diversity effect no preregistered measure caught).

   usage: clojure -M scripts/exotype_knob_contact_sheet.clj <out-dir>"
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]
            [futon5.mmca.render :as render]))

(def W 200)
(def STEPS 200)
(def SEED 2026085400)
(def GAP 6)

(defn- base []
  (ca/with-seed SEED
    (let [g (vec (ca/random-sigil-string W))]
      {:arm :efe-full :seed SEED :time 0 :hunger-target (:hunger efe/preferences)
       :lambdas (vec (repeat W 0.55)) :genotype g :previous-genotype g
       :phenotype (apply str (repeatedly W #(if (< (ca/rnd) 0.5) \0 \1)))
       :exotypes (grid/initial-grid :heterogeneous-fixed W)
       :blend-action? true :blend-strength 0.0 :apply-probability 1.0
       :epistemic-coefficient 0.2 :self-tuning-arm :hunger-coupled
       :lambda-step-size 0.0 :policy-precision 4.0})))

(defn- with-knob [s knob v]
  (case knob
    :gamma  (assoc s :policy-precision v)
    :kappa  (assoc s :epistemic-coefficient v)
    :lambda (assoc s :lambdas (vec (repeat W v)))
    :p      (assoc s :apply-probability v)))

(def SWEEPS {:gamma  [1.0 4.0 16.0 64.0]
             :kappa  [0.0 0.2 0.478 1.0]
             :lambda [0.1 0.55 1.0]
             :p      [0.3 0.6 1.0]})

(defn- panel
  "Genotype space-time for one knob setting, as rows of RGB."
  [knob v]
  (let [hs (reductions (fn [s _] (tuning/step s)) (with-knob (base) knob v) (range STEPS))]
    (render/render-history (mapv #(apply str (map str (:genotype %))) hs))))

(defn- hjoin [panels]
  (let [h (count (first panels))
        white (vec (repeat GAP [255 255 255]))]
    (mapv (fn [r] (vec (mapcat (fn [p] (concat (nth p r) white)) panels))) (range h))))

(defn -main [& [dir]]
  (doseq [[knob vals] SWEEPS]
    (let [img (hjoin (mapv #(panel knob %) vals))
          out (format "%s/knob-sweep-%s.png.ppm" (or dir "reports/figures") (name knob))]
      (render/write-image! out img)
      (println (format "  %-7s %s  panels left->right: %s"
                       (name knob) out (pr-str vals))))))

(apply -main *command-line-args*)
