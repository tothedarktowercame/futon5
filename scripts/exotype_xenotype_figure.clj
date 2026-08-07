(ns exotype-xenotype-figure
  "XENOTYPE-LAYER spacetime: the population's trajectory through RULE SPACE,
   not through the ring. Joe, 2026-08-05: `we need a spacetime diagram in the
   xenotype layer, showing how the pheno-geno-exo layers are being evolved over
   time and whether they are indeed reaching a healthy state`.

   Ring-space diagrams answer `is this example nice?`. They cannot answer `how
   is the search working?`, because the ring axis is not the space being
   searched. The manifold is.

   Two panels, both time on X:
     (a) LANGTON occupancy. Y = Langton's lambda for a rule (fraction of 1s in
         the 8-bit table, 9 possible values). This is the classical
         order/chaos coordinate for CA rule space, so a population drifting
         toward or away from the critical band is directly visible.
     (b) RULE-INDEX occupancy. Y = the rule byte 0-255. Coarser to read, but it
         shows whether the population is CONCENTRATING (few rules, thick bands)
         or DISPERSING (many rules, uniform haze) -- diversity in the manifold
         rather than diversity counted.
   Intensity = number of cells holding that coordinate at that time.

   usage: clojure -M scripts/exotype_xenotype_figure.clj <out-prefix>"
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]
            [futon5.mmca.render :as render]))

(def W 250) (def STEPS 250) (def SEED 2026085200)

(defn- state []
  (ca/with-seed SEED
    (let [g (vec (ca/random-sigil-string W))]
      {:arm :efe-full :seed SEED :time 0 :hunger-target 0.1676
       :lambdas (vec (repeat W 0.55)) :genotype g :previous-genotype g
       :phenotype (ca/random-phenotype-string W)
       :exotypes (grid/initial-grid :heterogeneous-fixed W)
       :blend-action? true :blend-strength 0.0 :epistemic-coefficient 0.2
       :apply-probability 1.0 :self-tuning-arm :hunger-coupled
       :lambda-step-size 0.001})))

(defn- rule-byte [sigil]
  (Integer/parseInt (ca/bits-for (str sigil)) 2))

(defn- langton [b] (Integer/bitCount b))          ; 0..8 ones in the table

(defn- heat-row
  "One time column -> a vector of RGB triples of height ROWS, log-scaled."
  [counts rows]
  (let [mx (max 1 (apply max 1 (vals counts)))]
    (mapv (fn [i]
            (let [c (get counts i 0)
                  v (if (zero? c) 0
                        (int (+ 40 (* 215 (/ (Math/log (inc c)) (Math/log (inc mx)))))))]
              [v v v]))
          (range rows))))

(defn- transpose-to-image [cols rows]
  ;; cols is a seq of columns (each a vector of rows RGB); image wants rows of columns
  (mapv (fn [r] (mapv (fn [col] (nth col r)) cols)) (range rows)))

(defn -main [& [prefix]]
  (let [hs (reductions (fn [s _] (tuning/step s)) (state) (range STEPS))
        bytes-per-step (mapv (fn [s] (mapv rule-byte (:genotype s))) hs)
        lang-cols (mapv (fn [bs] (heat-row (frequencies (map langton bs)) 9)) bytes-per-step)
        rule-cols (mapv (fn [bs] (heat-row (frequencies bs) 256)) bytes-per-step)
        ;; Langton panel is only 9 rows tall; stretch x12 so it is legible
        lang-img (mapcat (fn [row] (repeat 12 row))
                         (transpose-to-image lang-cols 9))]
    (render/write-image! (str prefix "-langton.png.ppm") (vec lang-img))
    (render/write-image! (str prefix "-rulespace.png.ppm")
                         (transpose-to-image rule-cols 256))
    (println "wrote" prefix "-langton / -rulespace")
    (println "  Langton distribution at t=0  :"
             (pr-str (into (sorted-map) (frequencies (map langton (first bytes-per-step))))))
    (println "  Langton distribution at t=250:"
             (pr-str (into (sorted-map) (frequencies (map langton (last bytes-per-step))))))
    (println "  distinct rules t=0 / t=250   :"
             (count (distinct (first bytes-per-step))) "/"
             (count (distinct (last bytes-per-step))))))

(apply -main *command-line-args*)
