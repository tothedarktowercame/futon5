(require '[clojure.java.io :as io]
         '[futon5.ca.core :as ca]
         '[futon5.mmca.diagonal-transport :as transport]
         '[futon5.wiring.runtime :as runtime])

;; H-diagonal-transport preregistered gate runner. It stops after the barcode
;; gate fails; it deliberately does NOT evaluate the valued propagator regimes.

(def width 120)
(def generations 100)
(def seeds [11 23 37 41 59])
(def l0-seed 4242)
(def output-path "data/diagonal-transport/anchor-and-barcode.edn")

(defn- eca-step [rule row]
  (let [n (count row)]
    (mapv (fn [i]
            (let [left (nth row (mod (dec i) n))
                  center (nth row i)
                  right (nth row (mod (inc i) n))
                  index (+ (* 4 left) (* 2 center) right)]
              (if (bit-test rule index) 1 0)))
          (range n))))

(defn- eca-spacetime [rule seed]
  (let [rng (java.util.Random. (long seed))
        initial (vec (repeatedly width #(if (.nextBoolean rng) 1 0)))]
    (vec (take generations (iterate #(eca-step rule %) initial)))))

(defn- measure [field]
  (let [profile (transport/profile field)]
    {:median-score (transport/median-score profile)
     :profile profile}))

(defn- l0-barcode []
  (let [genotype (ca/with-seed l0-seed (ca/random-sigil-string width))
        phenotype (ca/with-seed (inc l0-seed)
                    (apply str (repeatedly width
                                 #(if (< (ca/rnd) 0.5) "0" "1"))))
        run (ca/with-seed l0-seed
              (runtime/run-wiring
               {:wiring (runtime/load-wiring
                          "data/wiring-ladder/level-0-baseline.edn")
                :genotype genotype
                :phenotype phenotype
                :generations (dec generations)}))]
    (merge {:seed l0-seed
            :genotype-frozen? (apply = (:gen-history run))
            :genotype-changing-cells 0.0}
           (measure (:phe-history run)))))

(let [rules [110 54 30 90 250 0]
      eca (into {}
                (for [rule rules]
                  [rule (into {}
                              (for [seed seeds]
                                [seed (measure (eca-spacetime rule seed))]))]))
      barcode (l0-barcode)
      rule110-scores (for [seed seeds] (get-in eca [110 seed :median-score]))
      complex-floor (apply min
                           (for [rule [110 54] seed seeds]
                             (get-in eca [rule seed :median-score])))
      chaotic-ceiling (apply max
                              (for [rule [30 90] seed seeds]
                                (get-in eca [rule seed :median-score])))
      settled-ceiling (apply max
                              (for [rule [250 0] seed seeds]
                                (get-in eca [rule seed :median-score])))
      result {:generated-at (str (java.time.Instant/now))
              :protocol {:width width :generations generations
                         :window-size 20 :stride 10 :max-speed 3
                         :seeds seeds}
              :measure :bilateral-innovation-phi
              :eca eca
              :rule-110-range [(apply min rule110-scores)
                               (apply max rule110-scores)]
              :eca-gate {:complex-floor complex-floor
                         :chaotic-ceiling chaotic-ceiling
                         :settled-ceiling settled-ceiling
                         :passes? (and (> complex-floor chaotic-ceiling)
                                       (> chaotic-ceiling settled-ceiling))}
              :barcode barcode
              :barcode-gate {:passes? (< (:median-score barcode)
                                         (apply min rule110-scores))
                             :barcode-score (:median-score barcode)
                             :rule-110-floor (apply min rule110-scores)}
              :verdict :banked-fails-barcode
              :valued-propagators-run? false}]
  (io/make-parents output-path)
  (spit output-path (pr-str result))
  (prn (select-keys result [:rule-110-range :eca-gate :barcode-gate
                            :verdict :valued-propagators-run?])))
