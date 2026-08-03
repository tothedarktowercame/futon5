(ns lift-lambda-compare
  "Tape-averaged damage comparison for dynamically grounded situation keys."
  (:require [clojure.java.io :as io]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.hexagram.lambda-lift :as lambda-lift]
            [futon5.hexagram.lift-variants :as lift-variants]))

(defn- env-long [name fallback]
  (if-let [value (System/getenv name)]
    (Long/parseLong value)
    fallback))

(def config
  {:seed 20260803
   :samples (env-long "LIFT_LAMBDA_SAMPLES" 200)
   :tapes (env-long "LIFT_LAMBDA_TAPES" 8)
   :ceiling-replicate-tapes (env-long "LIFT_LAMBDA_CEILING_TAPES" 8)
   :width 80
   :burn-in 60
   :damage-steps 59
   :site 40
   ;; The active three-grid apparatus used by the critical-point damage
   ;; protocol. This is conatus weight, not Langton's lambda.
   :efe-conatus-weight 0.55
   :null-draws 32})

(def variants [:lambda-grounded :eigen-sign :random])

(defn- random-bits [^java.util.Random rng]
  (vec (repeatedly 36 #(.nextInt rng 2))))

(defn- bits->sigil [bits]
  (ca/sigil-for (apply str bits)))

(defn- context-state [bits tape-seed]
  (ca/with-seed tape-seed
    (let [{:keys [width efe-conatus-weight]} config
          rules (mapv bits->sigil (partition 8 (take 32 bits)))
          phenotype-family (drop 32 bits)
          genotype (vec (take width (cycle rules)))
          phenotype (apply str (take width (cycle phenotype-family)))]
      {:arm :efe-full
       :seed tape-seed
       :time 0
       :lambda efe-conatus-weight
       :genotype genotype
       :previous-genotype genotype
       :phenotype phenotype
       :exotypes (grid/initial-grid :heterogeneous-fixed width)})))

(defn- difference [left right]
  (count (filter true? (map not= left right))))

(defn- flip-phenotype [state site]
  (update state :phenotype
          #(apply str (assoc (vec %) site (if (= \0 (nth % site)) \1 \0)))))

(defn- flip-genotype [state site]
  (update-in state [:genotype site]
             (fn [sigil]
               (let [rule (vec (ca/bits-for (str sigil)))]
                 (ca/sigil-for
                  (apply str (update rule 0 #(if (= \0 %) \1 \0))))))))

(defn- flip-exotype [state site]
  (update-in state [:exotypes site]
             (fn [value]
               (nth grid/exotype-kinds
                    (mod (inc (.indexOf grid/exotype-kinds value))
                         (count grid/exotype-kinds))))))

(defn damage-signature
  "Return [phenotype genotype exotype] causal reach for one context/tape.
   The tape fixes the initial exotype field and all subsequent rewrite draws."
  [bits tape-seed]
  (let [{:keys [width burn-in damage-steps site]} config
        burned (efe/run-steps (context-state bits tape-seed) burn-in)
        control (efe/run-steps burned damage-steps)]
    (mapv
     (fn [[perturb state-key]]
       (let [treated (efe/run-steps (perturb burned site) damage-steps)]
         (/ (double (difference (state-key control) (state-key treated))) width)))
     [[flip-phenotype :phenotype]
      [flip-genotype :genotype]
      [flip-exotype :exotypes]])))

(defn- mean [values]
  (if (seq values)
    (/ (reduce + 0.0 values) (double (count values)))
    0.0))

(defn- population-sd [values]
  (if (seq values)
    (let [average (mean values)]
      (Math/sqrt
       (/ (reduce + 0.0
                  (map #(let [delta (- (double %) average)] (* delta delta))
                       values))
          (double (count values)))))
    0.0))

(defn- average-signatures [signatures]
  (mapv mean (apply mapv vector signatures)))

(defn- column-stats [signatures]
  (mapv (fn [values] [(mean values) (population-sd values)])
        (apply mapv vector signatures)))

(defn normalise-signatures
  "Z-score each damage layer across the supplied population."
  [signatures]
  (let [stats (column-stats signatures)]
    (mapv (fn [signature]
            (mapv (fn [value [average sd]]
                    (if (zero? sd) 0.0 (/ (- value average) sd)))
                  signature stats))
          signatures)))

(defn- rms-distance [left right]
  (Math/sqrt
   (/ (reduce + 0.0
              (map #(let [delta (- (double %1) (double %2))]
                      (* delta delta))
                   left right))
      (double (count left)))))

(defn separation [classes signatures]
  (let [totals
        (reduce
         (fn [acc [i j]]
           (let [same? (= (nth classes i) (nth classes j))
                 bucket (if same? :within :between)
                 distance (rms-distance (nth signatures i) (nth signatures j))]
             (-> acc
                 (update-in [bucket :sum] + distance)
                 (update-in [bucket :n] inc))))
         {:within {:sum 0.0 :n 0} :between {:sum 0.0 :n 0}}
         (for [i (range (count classes))
               j (range (inc i) (count classes))]
           [i j]))
        within (if (pos? (get-in totals [:within :n]))
                 (/ (get-in totals [:within :sum])
                    (double (get-in totals [:within :n])))
                 0.0)
        between (if (pos? (get-in totals [:between :n]))
                  (/ (get-in totals [:between :sum])
                     (double (get-in totals [:between :n])))
                  0.0)]
    {:within within
     :between between
     :ratio (if (pos? within) (/ between within) ##Inf)}))

(defn- class-for [variant bits random-seed]
  (case variant
    :lambda-grounded (:number (lambda-lift/context->hexagram bits))
    (:eigen-sign :random)
    (:number (lift-variants/exotype->hexagram variant bits random-seed))
    (throw (ex-info "unknown comparison variant"
                    {:variant variant :available variants}))))

(defn- lift-lines [variant bits random-seed]
  (case variant
    :lambda-grounded (lambda-lift/context->lines bits)
    (:eigen-sign :random) (lift-variants/exotype->lines variant bits random-seed)))

(defn- flip-locality [variant neighbourhoods random-seed]
  (mean
   (for [bits neighbourhoods
         index (range 36)
         :let [flipped (update bits index bit-xor 1)
               before (lift-lines variant bits random-seed)
               after (lift-lines variant flipped random-seed)]]
     (difference before after))))

(defn- oracle-classes [raw-average-signatures]
  (let [scores (mapv mean raw-average-signatures)
        median (nth (vec (sort scores)) (quot (count scores) 2))]
    (mapv #(if (< % median) 0 1) scores)))

(defn- null-classes [k samples seed]
  (let [rng (java.util.Random. (long seed))]
    (vec (repeatedly samples #(.nextInt rng (int k))))))

(defn- null-ratio [k samples signatures seed draws]
  (let [ratios (mapv (fn [draw]
                       (:ratio
                        (separation (null-classes k samples (+ seed draw))
                                    signatures)))
                     (range draws))]
    {:mean (mean ratios) :sd (population-sd ratios) :draws draws}))

(defn- raw-tape-repeatability [per-neighbourhood-tapes]
  (let [indexed (vec
                 (mapcat (fn [neighbourhood signatures]
                           (map-indexed (fn [tape signature]
                                          {:neighbourhood neighbourhood
                                           :tape tape
                                           :signature signature})
                                        signatures))
                         (range) per-neighbourhood-tapes))
        normalised (normalise-signatures (mapv :signature indexed))
        totals
        (reduce
         (fn [acc [i j]]
           (let [bucket (if (= (:neighbourhood (nth indexed i))
                               (:neighbourhood (nth indexed j)))
                          :within :between)
                 d (rms-distance (nth normalised i) (nth normalised j))]
             (-> acc
                 (update-in [bucket :sum] + d)
                 (update-in [bucket :n] inc))))
         {:within {:sum 0.0 :n 0} :between {:sum 0.0 :n 0}}
         (for [i (range (count indexed))
               j (range (inc i) (count indexed))]
           [i j]))
        within (/ (get-in totals [:within :sum])
                  (double (get-in totals [:within :n])))
        between (/ (get-in totals [:between :sum])
                   (double (get-in totals [:between :n])))]
    {:within within :between between :ratio (/ between within)
     :within-pairs (get-in totals [:within :n])
     :between-pairs (get-in totals [:between :n])}))

(defn- tape-averaged-ceiling [primary-averages replicate-averages]
  (let [samples (count primary-averages)
        all-signatures (vec (concat primary-averages replicate-averages))
        normalised (normalise-signatures all-signatures)
        primary (subvec normalised 0 samples)
        replicate (subvec normalised samples (* 2 samples))
        within-distances (mapv #(rms-distance (nth primary %) (nth replicate %))
                               (range samples))
        between-distances
        (for [i (range samples) j (range samples) :when (not= i j)]
          (rms-distance (nth primary i) (nth replicate j)))
        within (mean within-distances)
        between (mean between-distances)]
    {:within within :between between :ratio (/ between within)
     :within-pairs (count within-distances)
     :between-pairs (* samples (dec samples))
     :estimate-tapes (:tapes config)}))

(defn experiment []
  (let [{:keys [seed samples tapes ceiling-replicate-tapes null-draws]} config
        rng (java.util.Random. (long seed))
        neighbourhoods (vec (repeatedly samples #(random-bits rng)))
        primary-tape-seeds (mapv #(+ seed 100000 %) (range tapes))
        replicate-tape-seeds
        (mapv #(+ seed 200000 %) (range ceiling-replicate-tapes))
        per-neighbourhood
        (vec
         (pmap (fn [bits]
                 {:primary (mapv #(damage-signature bits %) primary-tape-seeds)
                  :replicate (mapv #(damage-signature bits %)
                                   replicate-tape-seeds)})
               neighbourhoods))
        primary-tapes (mapv :primary per-neighbourhood)
        replicate-tapes (mapv :replicate per-neighbourhood)
        raw-averages (mapv average-signatures primary-tapes)
        replicate-averages (mapv average-signatures replicate-tapes)
        signatures (normalise-signatures raw-averages)
        control-seed (+ seed 424242)
        oracle-classes* (oracle-classes raw-averages)
        oracle-k (count (distinct oracle-classes*))
        oracle-separation (separation oracle-classes* signatures)
        oracle-null (null-ratio oracle-k samples signatures control-seed null-draws)]
    {:kind :lift-lambda-comparison
     :schema 1
     :config config
     :tape-seeds {:primary primary-tape-seeds
                  :ceiling-replicate replicate-tape-seeds}
     :signature {:layers [:phenotype :genotype :exotype]
                 :aggregation :mean-over-fixed-tapes
                 :normalisation :per-layer-z-score-after-tape-average
                 :distance :root-mean-square-euclidean
                 :protocol {:burn-in (:burn-in config)
                            :damage-steps (:damage-steps config)
                            :site (:site config)}}
     :packing {:lines-0-to-3 :current-left-ego-right-next-lambda-gte-half
               :line-4 :left-ego-right-not-all-identical
               :line-5 :phenotype-family-density-gte-half}
     :ceiling {:tape-averaged
               (tape-averaged-ceiling raw-averages replicate-averages)
               :raw-primary-tape-repeatability
               (raw-tape-repeatability primary-tapes)}
     :oracle (merge oracle-separation
                    {:occupancy oracle-k
                     :null-ratio-at-k (:mean oracle-null)
                     :null-sd (:sd oracle-null)
                     :excess-over-null (- (:ratio oracle-separation)
                                          (:mean oracle-null))
                     :excess-sd-units
                     (if (pos? (:sd oracle-null))
                       (/ (- (:ratio oracle-separation) (:mean oracle-null))
                          (:sd oracle-null))
                       0.0)})
     :rows
     (mapv
      (fn [variant]
        (let [classes (mapv #(class-for variant % seed) neighbourhoods)
              occupancy (count (distinct classes))
              measured (separation classes signatures)
              null (null-ratio occupancy samples signatures control-seed null-draws)
              excess (- (:ratio measured) (:mean null))]
          (merge {:variant variant
                  :occupancy occupancy
                  :flip-locality (flip-locality variant neighbourhoods seed)
                  :null-ratio-at-k (:mean null)
                  :null-sd (:sd null)
                  :excess-over-null excess
                  :excess-sd-units (if (pos? (:sd null))
                                     (/ excess (:sd null)) 0.0)}
                 measured)))
      variants)}))

(defn- fmt-row [{:keys [variant occupancy flip-locality within between ratio
                         null-ratio-at-k null-sd excess-over-null
                         excess-sd-units]}]
  (format "| %s | %d | %.6f | %.6f | %.6f | %.6f | %.6f | %.6f | %+.6f | %+.2f |\n"
          (name variant) occupancy flip-locality within between ratio
          null-ratio-at-k null-sd excess-over-null excess-sd-units))

(defn markdown [result]
  (let [{:keys [samples tapes ceiling-replicate-tapes width burn-in damage-steps
                seed null-draws efe-conatus-weight]} (:config result)
        ceiling (get-in result [:ceiling :tape-averaged])
        raw-repeatability (get-in result [:ceiling :raw-primary-tape-repeatability])
        oracle (:oracle result)]
    (str "# Dynamically grounded lift comparison\n\n"
         "Fixed seed `" seed "`; N=`" samples "` neighbourhoods; T=`" tapes
         "` fixed primary tapes plus `" ceiling-replicate-tapes
         "` independent ceiling tapes; width `" width "`; t*=`" burn-in "`; dt=`"
         damage-steps "`. The active three-grid apparatus uses EFE conatus weight `"
         efe-conatus-weight "`.\n\n"
         "| variant | occupancy | flip locality | within | between | ratio | null@k | null sd | excess | excess sd |\n"
         "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n"
         (apply str (map fmt-row (:rows result)))
         "\n## Damage ceiling and controls\n\n"
         (format "Damage ceiling: within-neighbourhood tape distance `%.6f` (%d pairs), between-neighbourhood distance `%.6f` (%d pairs), ratio `%.6f`.\n\n"
                 (:within ceiling) (:within-pairs ceiling)
                 (:between ceiling) (:between-pairs ceiling) (:ratio ceiling))
         (format "For comparison, unaveraged primary-tape repeatability is within `%.6f`, between `%.6f`, ratio `%.6f`; this is not used as the T=8 ceiling.\n\n"
                 (:within raw-repeatability) (:between raw-repeatability)
                 (:ratio raw-repeatability))
         (format "Oracle: occupancy `%d`, ratio `%.6f`, matched null `%.6f ± %.6f`, excess `%+.6f` (`%+.2f` null SD).\n\n"
                 (:occupancy oracle) (:ratio oracle) (:null-ratio-at-k oracle)
                 (:null-sd oracle) (:excess-over-null oracle)
                 (:excess-sd-units oracle))
         "Matched-granularity nulls use `" null-draws
         "` deterministic random partitions per occupancy.\n\n"
         "## Method and packing\n\n"
         "Each tape initializes the same 36-bit neighbourhood as four repeating current rule sigils plus its repeating phenotype family. Only the heterogeneous exotype field and subsequent rewrite tape vary. After 60 burn-in steps, independent midpoint perturbations are propagated for 59 steps and phenotype, genotype, and exotype Hamming reach are divided by width. The three reaches are averaged over the eight fixed primary tapes, then each layer is z-scored across neighbourhoods before RMS Euclidean distance. The ceiling compares that T=8 estimate against an independent T=8 replicate estimate for the same neighbourhood, versus different neighbourhoods; it therefore uses the same averaging grain as the arm signatures.\n\n"
         "The lambda-grounded key packs six bottom-to-top lines: four lookup-table Langton-lambda bits for the CURRENT LEFT/EGO/RIGHT/NEXT rules (`lambda >= 0.5`); one bit saying LEFT/EGO/RIGHT are not all identical; and one bit saying at least two of the four phenotype-family bits are one. Thus evaluation is local in both space and time and never retains initial sigils. The half-inclusive tie rules are fixed, not fitted. `eigen-sign` calls the incumbent lift unchanged; `random` is the seeded-hash control.\n\n"
         "The old `2.4344` ceiling used a different signature and is not reused here.\n")))

(defn -main [& [edn-path md-path]]
  (let [edn-path (or edn-path "reports/lift-lambda-comparison.edn")
        md-path (or md-path "reports/lift-lambda-comparison.md")
        result (experiment)]
    (io/make-parents edn-path)
    (spit edn-path (str (pr-str result) "\n"))
    (spit md-path (markdown result))
    (println (format "ceiling %.6f oracle %.6f"
                     (get-in result [:ceiling :tape-averaged :ratio])
                     (get-in result [:oracle :ratio])))
    (doseq [row (:rows result)]
      (println (format "%-16s occ=%2d ratio=%.6f null=%.6f+-%.6f excess-sd=%+.2f"
                       (name (:variant row)) (:occupancy row) (:ratio row)
                       (:null-ratio-at-k row) (:null-sd row)
                       (:excess-sd-units row))))))

(apply -main *command-line-args*)
(shutdown-agents)
