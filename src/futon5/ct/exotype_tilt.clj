(ns futon5.ct.exotype-tilt
  "Contextual exotype tilt DSL over local LEFT/EGO/RIGHT/NEXT/PHENO contexts."
  (:require [futon5.ca.core :as ca]
            [futon5.mmca.metrics :as metrics]))

(def default-patterns
  {:anti-freeze {:priority 100
                 :guard {:zero-min 0.72
                         :stasis? true}
                 :op {:kind :zero->one
                      :bit-flips 1
                      :prob 0.9}}
   :anti-magma {:priority 95
                :guard {:zero-max 0.35
                        :entropy-min 0.92}
                :op {:kind :toward-ego
                     :bit-flips 2
                     :prob 0.8}}
   :edge-preserve {:priority 80
                   :guard {:edge? true
                           :pheno-min 1}
                   :op {:kind :toward-ego
                        :bit-flips 1
                        :prob 0.7}}
   :symmetry-break {:priority 70
                    :guard {:symmetry? true
                            :stasis? true}
                    :op {:kind :random-flip
                         :bit-flips 1
                         :prob 0.6}}
   :boundary-soften {:priority 60
                     :guard {:edge? true
                             :entropy-min 0.7}
                     :op {:kind :toward-neighbor
                          :bit-flips 1
                          :prob 0.55}}})

(def default-program
  {:pattern-weights {:anti-freeze 0.24
                     :anti-magma 0.22
                     :edge-preserve 0.20
                     :symmetry-break 0.18
                     :boundary-soften 0.16}
   :global-strength 0.35
   :max-cells-ratio 0.20})

(defn- clamp01 [x]
  (let [x (double (or x 0.0))]
    (cond
      (< x 0.0) 0.0
      (> x 1.0) 1.0
      :else x)))

(defn- bit-at
  [s idx]
  (let [n (count (or s ""))]
    (if (and (>= idx 0) (< idx n))
      (if (= \1 (nth s idx)) 1 0)
      0)))

(defn- sigil-bits
  [sigil]
  (-> sigil ca/bits-for ca/bits->ints vec))

(defn- ints->sigil
  [bits]
  (ca/sigil-for (ca/ints->bits bits)))

(defn- guard-match?
  [ctx guard]
  (and
   (if-let [x (:zero-min guard)] (>= (:zero-ratio ctx) (double x)) true)
   (if-let [x (:zero-max guard)] (<= (:zero-ratio ctx) (double x)) true)
   (if-let [x (:entropy-min guard)] (>= (:entropy-n ctx) (double x)) true)
   (if-let [x (:entropy-max guard)] (<= (:entropy-n ctx) (double x)) true)
   (if-let [x (:stasis? guard)] (= (:stasis? ctx) (boolean x)) true)
   (if-let [x (:symmetry? guard)] (= (:symmetry? ctx) (boolean x)) true)
   (if-let [x (:edge? guard)] (= (:edge? ctx) (boolean x)) true)
   (if-let [x (:pheno-min guard)] (>= (:pheno-ones ctx) (long x)) true)))

(defn- local-context
  [row-before row-next phenotype idx]
  (let [n (count row-before)
        left-idx (max 0 (dec idx))
        right-idx (min (dec n) (inc idx))
        left (nth row-before left-idx)
        ego (nth row-before idx)
        right (nth row-before right-idx)
        next (nth row-next idx)
        left-bits (sigil-bits left)
        ego-bits (sigil-bits ego)
        right-bits (sigil-bits right)
        next-bits (sigil-bits next)
        phe (or phenotype "")
        p-left (bit-at phe left-idx)
        p-center (bit-at phe idx)
        p-right (bit-at phe right-idx)
        p-majority (if (>= (+ p-left p-center p-right) 2) 1 0)
        p-window [p-left p-center p-right p-majority]
        bits (vec (concat left-bits ego-bits right-bits next-bits p-window))
        zero-ratio (if (seq bits)
                     (/ (double (count (filter zero? bits)))
                        (double (count bits)))
                     0.0)
        entropy-n (clamp01
                   (let [ent (metrics/shannon-entropy (apply str bits))
                         max-ent (if (seq bits)
                                   (/ (Math/log (count bits)) (Math/log 2.0))
                                   1.0)]
                     (if (pos? max-ent) (/ ent max-ent) 0.0)))]
    {:idx idx
     :left left
     :ego ego
     :right right
     :next next
     :left-bits left-bits
     :ego-bits ego-bits
     :right-bits right-bits
     :next-bits next-bits
     :pheno-window p-window
     :pheno-ones (reduce + 0 p-window)
     :zero-ratio zero-ratio
     :entropy-n entropy-n
     :stasis? (= ego next)
     :symmetry? (= left right)
     :edge? (or (zero? idx) (= idx (dec n)))}))

(defn- random-idxs
  [^java.util.Random rng n k]
  (loop [acc #{}]
    (if (= (count acc) k)
      acc
      (recur (conj acc (.nextInt rng n))))))

(defn- op-apply
  [ctx op global-strength ^java.util.Random rng]
  (let [next-bits (vec (:next-bits ctx))
        ego-bits (vec (:ego-bits ctx))
        left-bits (vec (:left-bits ctx))
        right-bits (vec (:right-bits ctx))
        k (max 1 (long (or (:bit-flips op) 1)))
        p (clamp01 (* global-strength (double (or (:prob op) 1.0))))
        apply? (< (.nextDouble rng) p)]
    (if-not apply?
      {:bits next-bits :changed? false}
      (let [n (count next-bits)
            bits
            (case (:kind op)
              :zero->one
              (let [idxs (vec (keep-indexed (fn [i b] (when (zero? b) i)) next-bits))
                    k (min k (count idxs))
                    picks (if (pos? k)
                            (loop [picked #{}]
                              (if (= (count picked) k)
                                picked
                                (recur (conj picked (nth idxs (.nextInt rng (count idxs)))))))
                            #{})]
                (reduce (fn [acc i] (assoc acc i 1)) next-bits picks))

              :one->zero
              (let [idxs (vec (keep-indexed (fn [i b] (when (= 1 b) i)) next-bits))
                    k (min k (count idxs))
                    picks (if (pos? k)
                            (loop [picked #{}]
                              (if (= (count picked) k)
                                picked
                                (recur (conj picked (nth idxs (.nextInt rng (count idxs)))))))
                            #{})]
                (reduce (fn [acc i] (assoc acc i 0)) next-bits picks))

              :toward-ego
              (let [idxs (vec (keep-indexed (fn [i b] (when (not= b (nth ego-bits i)) i)) next-bits))
                    k (min k (count idxs))
                    picks (if (pos? k) (random-idxs rng (count idxs) k) #{})]
                (reduce (fn [acc j]
                          (let [i (nth idxs j)]
                            (assoc acc i (nth ego-bits i))))
                        next-bits
                        picks))

              :toward-neighbor
              (let [neighbor (if (:edge? ctx)
                               (if (zero? (:idx ctx)) right-bits left-bits)
                               (if (< (.nextDouble rng) 0.5) left-bits right-bits))
                    idxs (vec (keep-indexed (fn [i b] (when (not= b (nth neighbor i)) i)) next-bits))
                    k (min k (count idxs))
                    picks (if (pos? k) (random-idxs rng (count idxs) k) #{})]
                (reduce (fn [acc j]
                          (let [i (nth idxs j)]
                            (assoc acc i (nth neighbor i))))
                        next-bits
                        picks))

              :random-flip
              (reduce (fn [acc i]
                        (update acc i (fn [b] (if (= 1 b) 0 1))))
                      next-bits
                      (random-idxs rng n (min k n)))

              next-bits)]
        {:bits bits
         :changed? (not= bits next-bits)}))))

(defn apply-tilts
  "Apply contextual local tilts to base-next row.

   Returns {:row :changed-cells :hits :triggered :max-cells :program}."
  [row-before base-next phenotype program ^java.util.Random rng]
  (let [row-before (mapv str (seq (str (or row-before ""))))
        row-next0 (mapv str (seq (str (or base-next ""))))
        n (count row-next0)
        program (merge default-program (or program {}))
        weights (or (:pattern-weights program) {})
        max-cells (min n (max 0 (long (Math/round (* n (double (:max-cells-ratio program)))))))
        global-strength (double (or (:global-strength program) 0.35))]
    (loop [idx 0
           row row-next0
           changed 0
           hits {}
           triggered {}]
      (if (>= idx n)
        {:row (apply str row)
         :changed-cells changed
         :hits hits
         :triggered triggered
         :max-cells max-cells
         :program program}
        (let [ctx (local-context row-before row phenotype idx)
              applicable
              (->> default-patterns
                   (keep (fn [[pid {:keys [guard priority] :as spec}]]
                           (let [w (double (or (get weights pid) 0.0))]
                             (when (and (> w 0.0) (guard-match? ctx guard))
                               (assoc spec
                                      :id pid
                                      :priority priority
                                      :score (+ (* 0.7 w)
                                                (* 0.3 (/ (double priority) 100.0))))))))
                   (sort-by :score >)
                   vec)
              triggered (if-let [pid (:id (first applicable))]
                          (update triggered pid (fnil inc 0))
                          triggered)]
          (if (or (>= changed max-cells) (empty? applicable))
            (recur (inc idx) row changed hits triggered)
            (let [{:keys [id op]} (first applicable)
                  {:keys [bits changed?]} (op-apply (assoc ctx :next-bits (sigil-bits (nth row idx)))
                                                    op
                                                    global-strength
                                                    rng)
                  sigil (ints->sigil bits)
                  row' (if changed? (assoc row idx sigil) row)
                  changed' (if changed? (inc changed) changed)
                  hits' (if changed? (update hits id (fnil inc 0)) hits)]
              (recur (inc idx) row' changed' hits' triggered))))))))
