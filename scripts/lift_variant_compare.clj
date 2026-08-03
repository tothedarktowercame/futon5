(ns lift-variant-compare
  "Deterministic comparison of exotype situation-key lifts against MetaCA behaviour."
  (:require [clojure.java.io :as io]
            [futon5.ca.core :as ca]
            [futon5.hexagram.lift-variants :as lift-variants]))

(def default-config {:seed 20260803 :samples 200 :width 80 :steps 120})

(defn random-bits [^java.util.Random rng]
  (vec (repeatedly 36 #(.nextInt rng 2))))

(defn- bits->sigil [bits]
  (ca/sigil-for (apply str bits)))

(defn initial-state
  "Interpret LEFT/EGO/RIGHT/NEXT as four initial rule sigils, repeated across
   the ring, and repeat the four phenotype-family bits as the phenotype row."
  [bits width]
  (let [rules (mapv bits->sigil (partition 8 (take 32 bits)))
        phe-bits (drop 32 bits)]
    {:genotype (apply str (take width (cycle rules)))
     :phenotype (apply str (take width (cycle phe-bits)))}))

(defn- changed-fraction [a b]
  (/ (double (count (filter true? (map not= a b)))) (count a)))

(defn behavioral-signature [bits width steps behavior-seed]
  (ca/with-seed behavior-seed
    (let [{:keys [genotype phenotype]} (initial-state bits width)
          [gen-history phe-history]
          (ca/run-for-generations-3 genotype phenotype steps)
          diversity (mapv #(double (count (distinct %))) (rest gen-history))
          activity (mapv changed-fraction phe-history (rest phe-history))
          mutation (mapv changed-fraction gen-history (rest gen-history))]
      (vec (mapcat identity [diversity activity mutation])))))

(defn- column-stats [signatures]
  (mapv (fn [values]
          (let [n (count values)
                mean (/ (reduce + values) (double n))
                variance (/ (reduce + (map #(let [d (- % mean)] (* d d)) values))
                            (double n))]
            [mean (Math/sqrt variance)]))
        (apply mapv vector signatures)))

(defn normalise-signatures
  "Z-score each time/readout coordinate across sampled neighbourhoods; constant
   coordinates map to zero. This gives diversity, activity, and mutation equal
   coordinate-level scale before RMS Euclidean distance."
  [signatures]
  (let [stats (column-stats signatures)]
    (mapv (fn [signature]
            (mapv (fn [x [mean sd]]
                    (if (zero? sd) 0.0 (/ (- x mean) sd)))
                  signature stats))
          signatures)))

(defn- rms-distance [a b]
  (Math/sqrt (/ (reduce + (map #(let [d (- %1 %2)] (* d d)) a b))
                (double (count a)))))

(defn- mean [xs]
  (if (seq xs) (/ (reduce + xs) (double (count xs))) 0.0))

(defn separation [classes signatures]
  (let [pairs (for [i (range (count classes)) j (range (inc i) (count classes))]
                [(= (nth classes i) (nth classes j))
                 (rms-distance (nth signatures i) (nth signatures j))])
        within (mean (map second (filter first pairs)))
        between (mean (map second (remove first pairs)))]
    {:within within :between between
     :ratio (if (pos? within) (/ between within) ##Inf)}))

(defn flip-locality [variant samples random-seed]
  (mean
   (for [bits samples bit-index (range 36)
         :let [flipped (update bits bit-index bit-xor 1)
               a (:lines (lift-variants/exotype->hexagram variant bits random-seed))
               b (:lines (lift-variants/exotype->hexagram variant flipped random-seed))]]
     (count (filter true? (map not= a b))))))

;; --- controls on the MEASUREMENT, not on the lift (added on review, claude-11) ---
;;
;; The first run of this script reported ratio ~1.0 for every variant INCLUDING the
;; random lift. That table alone cannot distinguish "no lift groups behaviour" from
;; "this signature/distance separates nothing, so 1.0 is what it always returns".
;; Two controls settle it, and both belong in the artifact rather than in a reviewer's
;; scratch buffer:
;;
;;   ORACLE -- partition the samples by a coordinate of the signature ITSELF. A measure
;;   that cannot separate this cannot separate anything, so a ratio at ~1.0 here would
;;   condemn the apparatus rather than the lifts. Measured 2026-08-03: 1.2326.
;;
;;   MATCHED-GRANULARITY NULLS -- random partitions into k classes, for each k the real
;;   variants actually reach, AVERAGED over `null-draws` draws. The null is not exactly
;;   1.0, so each variant must be read against the null AT ITS OWN k rather than against
;;   1.0; occupancy here spans 5..60.
;;
;;   A first version of this used a SINGLE draw per k and reported that the null "falls
;;   with k" (0.9967 at k=2 down to 0.9748 at k=60). That was an artifact of one seed:
;;   a second seed gave 1.0084 at k=32 where the first gave 0.9844. The per-draw spread
;;   is ~0.02, which is the size of the effects being measured, and it flipped
;;   eigen-sign's excess from +0.002 to -0.022. Hence averaging, and hence `:null-sd`
;;   and `:excess-sd-units` in the output: an excess under about 2 sd is not
;;   distinguishable from a lucky partition and must not be read as one.

(defn oracle-classes
  "Median split on each sample's mean raw signature -- a partition built from the
   behaviour itself, so it MUST separate if the measure works."
  [raw-signatures]
  (let [means (mapv #(/ (reduce + %) (double (count %))) raw-signatures)
        median (nth (vec (sort means)) (quot (count means) 2))]
    (mapv #(if (< % median) 0 1) means)))

(defn null-classes
  "A random partition into k classes, drawn from a fixed seed so the null is part of
   the deterministic artifact rather than re-rolled per reader."
  [k samples null-seed]
  (let [rng (java.util.Random. (long null-seed))]
    (vec (repeatedly samples #(.nextInt rng (int k))))))

(def null-draws
  "How many random partitions to average the null over.

   ONE draw is not enough: at k=32 two different seeds gave nulls of 0.9844 and
   1.0084, a spread of 0.024 -- the same size as the variant effects being measured,
   so a single-draw null can flip a variant's excess from positive to negative. That
   happened on the first corrected run and is why this constant exists."
  20)

(defn null-ratio
  "Mean and sd of the separation ratio over `null-draws` random partitions into k
   classes. The sd is reported so a reader can see whether an excess clears the noise
   rather than having to trust that it does."
  [k samples signatures base-null-seed]
  (let [rs (mapv (fn [d]
                   (:ratio (separation (null-classes k samples (+ base-null-seed d))
                                       signatures)))
                 (range null-draws))
        n (count rs)
        m (/ (reduce + rs) (double n))
        sd (Math/sqrt (/ (reduce + (map #(let [x (- % m)] (* x x)) rs)) (double n)))]
    {:mean m :sd sd}))

(defn compare-variants [{:keys [seed samples width steps] :as config}]
  (let [rng (java.util.Random. (long seed))
        neighbourhoods (vec (repeatedly samples #(random-bits rng)))
        raw-signatures (mapv (fn [idx bits]
                               (behavioral-signature bits width steps (+ seed idx)))
                             (range samples) neighbourhoods)
        signatures (normalise-signatures raw-signatures)]
    {:kind :lift-variant-comparison
     :schema 2
     :config config
     :normalisation :per-coordinate-z-score
     :distance :root-mean-square-euclidean
     ;; The measure's own positive control. If this is not clearly above 1.0 the
     ;; variant rows below say nothing, because the apparatus cannot detect grouping.
     :oracle (let [o (separation (oracle-classes raw-signatures) signatures)
                   n (null-ratio 2 samples signatures (+ seed 424242))]
               (assoc o :null-ratio-at-k (:mean n) :null-sd (:sd n)
                        :excess-over-null (- (:ratio o) (:mean n))))
     :null-draws null-draws
     ;; The null is k-dependent, so every variant row carries its OWN baseline and the
     ;; excess over it. Raw ratios are not comparable across differing occupancy.
     :rows
     (mapv (fn [variant]
             (let [classes (mapv #(get-in (lift-variants/exotype->hexagram
                                           variant % seed) [:number])
                                 neighbourhoods)
                   k (count (distinct classes))
                   sep (separation classes signatures)
                   null (null-ratio k samples signatures (+ seed 424242))
                   excess (- (:ratio sep) (:mean null))]
               (merge {:variant variant
                       :occupancy k
                       :flip-locality (flip-locality variant neighbourhoods seed)
                       :null-ratio-at-k (:mean null)
                       :null-sd (:sd null)
                       :excess-over-null excess
                       ;; excess in units of the null's own spread. Anything under
                       ;; about 2 is not distinguishable from a lucky partition.
                       :excess-sd-units (if (pos? (:sd null)) (/ excess (:sd null)) 0.0)}
                      sep)))
           lift-variants/variants)}))

(defn -main [& [output-path]]
  (let [output-path (or output-path "reports/lift-variant-comparison.edn")
        result (compare-variants default-config)]
    (io/make-parents output-path)
    (spit output-path (str (pr-str result) "\n"))
    (let [o (:oracle result)]
      (println (format "oracle (measure positive control): ratio %.6f  null %.6f+-%.6f  excess %+.6f"
                       (:ratio o) (:null-ratio-at-k o) (:null-sd o) (:excess-over-null o))))
    (println (format "nulls averaged over %d random partitions per k\n" (:null-draws result)))
    (println (format "%-16s %4s %9s %9s %9s %9s %9s %9s"
                     "variant" "occ" "flip-loc" "ratio" "null@k" "null-sd" "excess" "sd-units"))
    (doseq [{:keys [variant occupancy flip-locality ratio null-ratio-at-k null-sd
                    excess-over-null excess-sd-units]}
            (:rows result)]
      (println (format "%-16s %4d %9.6f %9.6f %9.6f %9.6f %+9.6f %+9.2f"
                       (name variant) occupancy flip-locality ratio
                       null-ratio-at-k null-sd excess-over-null excess-sd-units)))))

(apply -main *command-line-args*)
