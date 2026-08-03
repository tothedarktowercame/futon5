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

(defn compare-variants [{:keys [seed samples width steps] :as config}]
  (let [rng (java.util.Random. (long seed))
        neighbourhoods (vec (repeatedly samples #(random-bits rng)))
        signatures
        (normalise-signatures
         (mapv (fn [idx bits]
                 (behavioral-signature bits width steps (+ seed idx)))
               (range samples) neighbourhoods))]
    {:kind :lift-variant-comparison
     :schema 1
     :config config
     :normalisation :per-coordinate-z-score
     :distance :root-mean-square-euclidean
     :rows
     (mapv (fn [variant]
             (let [classes (mapv #(get-in (lift-variants/exotype->hexagram
                                           variant % seed) [:number])
                                 neighbourhoods)]
               (merge {:variant variant
                       :occupancy (count (distinct classes))
                       :flip-locality (flip-locality variant neighbourhoods seed)}
                      (separation classes signatures))))
           lift-variants/variants)}))

(defn -main [& [output-path]]
  (let [output-path (or output-path "reports/lift-variant-comparison.edn")
        result (compare-variants default-config)]
    (io/make-parents output-path)
    (spit output-path (str (pr-str result) "\n"))
    (doseq [{:keys [variant occupancy flip-locality within between ratio]}
            (:rows result)]
      (println (format "%-16s %2d %.6f %.6f %.6f %.6f"
                       (name variant) occupancy flip-locality within between ratio)))))

(apply -main *command-line-args*)
