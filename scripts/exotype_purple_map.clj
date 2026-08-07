(ns exotype-purple-map
  "RED/BLUE/PURPLE co-occurrence map -- does blend activity coincide with criticality?

   Joe, 2026-08-05: `Presumably we could Red/Blue tint the diagrams and look for Purple?`

   The design question this answers. We are about to re-pair the controller to
   the knob-sensor pair kappa <-> realized blend share, because that pair has
   measured LEVERAGE where lambda <-> hunger had none. But leverage is only half
   of it: the sensor must also be VALID -- it must track the thing we actually
   want, which is criticality. Leverage without validity regulates the wrong
   quantity, confidently.

   The test, per cell per timestep, running a system and its one-bit-perturbed
   twin in lockstep:
     RED   = this cell took the BLEND action at (t,i)          -- what we can move
     BLUE  = this cell is DAMAGED at (t,i), i.e. differs from  -- what we want
             the twin: the actual damage cone, per cell
     PURPLE= both at once                                       -- co-occurrence

   If blend share tracks criticality, blend events should live ON the damage
   front and the picture is purple. If they are unrelated, red and blue occupy
   different regions and the picture separates. Reported alongside the image:
   the phi coefficient for the 2x2 table, so the eye and the number are checked
   against each other rather than one standing in for the other.

   usage: clojure -M scripts/exotype_purple_map.clj <kappa> <out-prefix>"
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]
            [futon5.mmca.render :as render]))

(def W 250)
(def STEPS 250)
(def SEED 2026085400)

(defn- state [kappa]
  (ca/with-seed SEED
    (let [g (vec (ca/random-sigil-string W))]
      {:arm :efe-full :seed SEED :time 0 :hunger-target (:hunger efe/preferences)
       :lambdas (vec (repeat W 0.55)) :genotype g :previous-genotype g
       :phenotype (apply str (repeatedly W #(if (< (ca/rnd) 0.5) \0 \1)))
       :exotypes (grid/initial-grid :heterogeneous-fixed W)
       :blend-action? true :blend-strength 0.0 :apply-probability 1.0
       :epistemic-coefficient kappa :self-tuning-arm :hunger-coupled
       :lambda-step-size 0.0 :policy-precision 4.0})))

(defn- blend-mask [s]
  (mapv #(= :blend (get-in % [:winner :policy])) (or (:efe-decisions s) [])))

(defn -main [& [k out]]
  (let [kappa (Double/parseDouble (or k "0.2"))
        s0 (state kappa)
        s1 (assoc s0 :phenotype (apply str (update (vec (:phenotype s0)) (quot W 2)
                                                   #(if (= % \0) \1 \0))))
        rows (loop [a s0 b s1 t 0 acc []]
               (if (= t STEPS) acc
                   (let [a' (tuning/step a) b' (tuning/step b)
                         red (blend-mask a')
                         blue (mapv not= (:phenotype a') (:phenotype b'))]
                     (recur a' b' (inc t) (conj acc [red blue])))))
        img (mapv (fn [[red blue]]
                    (mapv (fn [i]
                            (let [r (if (get red i false) 235 25)
                                  bl (if (get blue i false) 235 25)]
                              [r 20 bl]))
                          (range W)))
                  rows)
        cells (for [[red blue] rows i (range W)] [(get red i false) (get blue i false)])
        n11 (count (filter (fn [[r b]] (and r b)) cells))
        n10 (count (filter (fn [[r b]] (and r (not b))) cells))
        n01 (count (filter (fn [[r b]] (and (not r) b)) cells))
        n00 (count (filter (fn [[r b]] (and (not r) (not b))) cells))
        den (Math/sqrt (* (double (+ n11 n10)) (+ n01 n00) (+ n11 n01) (+ n10 n00)))
        phi (if (pos? den) (/ (- (* (double n11) n00) (* (double n10) n01)) den) 0.0)]
    (render/write-image! (str out ".png.ppm") img)
    (println (format "  kappa %.3f -> %s" kappa out))
    (println (format "    RED  (blend)          %6.3f of cells" (/ (double (+ n11 n10)) (count cells))))
    (println (format "    BLUE (damaged)        %6.3f of cells" (/ (double (+ n11 n01)) (count cells))))
    (println (format "    PURPLE (both)         %6.3f of cells" (/ (double n11) (count cells))))
    (println (format "    expected if INDEPENDENT %6.3f"
                     (* (/ (double (+ n11 n10)) (count cells)) (/ (double (+ n11 n01)) (count cells)))))
    (println (format "    phi coefficient       %+6.4f   %s" phi
                     (cond (> (Math/abs phi) 0.2) "ASSOCIATED"
                           (> (Math/abs phi) 0.05) "weak"
                           :else "INDEPENDENT -- blend does not track damage")))))

(apply -main *command-line-args*)
