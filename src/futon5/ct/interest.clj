(ns futon5.ct.interest
  "Adaptive definitions for what counts as interesting tensor/CA behavior.")

(def ^:private descriptor-keys
  [:change :entropy-n :temporal :unique :coherence])

(defn- clamp01 [x]
  (let [x (double (or x 0.0))]
    (cond
      (< x 0.0) 0.0
      (> x 1.0) 1.0
      :else x)))

(defn- band-score
  [x center width]
  (let [x (clamp01 x)
        center (clamp01 center)
        width (max 1.0e-9 (double width))]
    (max 0.0 (- 1.0 (/ (Math/abs (- x center)) width)))))

(defn- normalize-weights
  [weights]
  (let [weights (merge {:quality 0.6 :novelty 0.25 :surprise 0.15}
                       (or weights {}))
        weights (into {}
                      (map (fn [[k v]]
                             [k (max 0.01 (double (or v 0.0)))]))
                      weights)
        total (reduce + 0.0 (vals weights))]
    (if (pos? total)
      (into {} (map (fn [[k v]] [k (/ (double v) total)]) weights))
      {:quality 0.6 :novelty 0.25 :surprise 0.15})))

(defn initial-definition
  []
  {:version 1
   :weights (normalize-weights nil)
   :targets {:change 0.48
             :entropy-n 0.70
             :temporal 0.55
             :unique 0.55
             :coherence 0.45}
   :widths {:change 0.35
            :entropy-n 0.35
            :temporal 0.35
            :unique 0.35
            :coherence 0.45}
   :stale-count 0
   :notes []})

(defn descriptor
  "Build a behavior descriptor from summary/episode maps."
  [{:keys [generation summary episode]}]
  {:generation (long (or generation 0))
   :regime (:regime episode)
   :change (clamp01 (:avg-change summary))
   :entropy-n (clamp01 (:avg-entropy-n summary))
   :temporal (clamp01 (:temporal-autocorr summary))
   :unique (clamp01 (:avg-unique summary))
   :coherence (clamp01 (:coherence summary))
   :composite (double (or (:composite-score summary) 0.0))})

(defn quality-score
  [definition d]
  (let [targets (or (:targets definition) {})
        widths (or (:widths definition) {})]
    (->> descriptor-keys
         (map (fn [k]
                (band-score (get d k)
                            (get targets k 0.5)
                            (get widths k 0.4))))
         (reduce + 0.0)
         (#(/ % (double (count descriptor-keys))))
         clamp01)))

(defn descriptor-distance
  [a b]
  (->> descriptor-keys
       (map (fn [k]
              (Math/abs (- (double (or (get a k) 0.0))
                           (double (or (get b k) 0.0))))))
       (reduce + 0.0)
       (#(/ % (double (count descriptor-keys))))
       clamp01))

(defn novelty-score
  [archive d]
  (if (seq archive)
    (let [nearest (apply min (map #(descriptor-distance % d) archive))]
      (clamp01 (* 3.0 nearest)))
    1.0))

(defn surprise-score
  [prev d]
  (if prev
    (let [delta (descriptor-distance prev d)]
      (clamp01 (* 2.0 delta)))
    0.5))

(defn evaluate
  "Score a descriptor against current definition and behavior archive."
  [definition archive prev d]
  (let [q (quality-score definition d)
        n (novelty-score archive d)
        s (surprise-score prev d)
        weights (normalize-weights (:weights definition))
        wq (double (or (:quality weights) 0.0))
        wn (double (or (:novelty weights) 0.0))
        ws (double (or (:surprise weights) 0.0))
        total (clamp01 (+ (* wq q)
                          (* wn n)
                          (* ws s)))]
    {:quality q
     :novelty n
     :surprise s
     :weights weights
     :total total}))

(defn- shift-target
  [definition k delta]
  (update-in definition [:targets k]
             (fn [v]
               (clamp01 (+ (double (or v 0.5))
                           (double delta))))))

(defn- smooth-targets-toward
  [definition d alpha]
  (reduce (fn [acc k]
            (let [t (double (or (get-in acc [:targets k]) 0.5))
                  x (double (or (get d k) t))
                  blended (+ (* (- 1.0 alpha) t) (* alpha x))]
              (assoc-in acc [:targets k] (clamp01 blended))))
          definition
          descriptor-keys))

(defn- adjust-weights
  [definition f]
  (update definition :weights (fn [w] (normalize-weights (f (or w {}))))))

(defn adapt-definition
  "Evolve the interestingness definition based on new evidence.

   Returns {:definition ... :notes [...]}."
  [definition eval d]
  (let [definition (or definition (initial-definition))
        stale? (< (double (:novelty eval)) 0.22)
        stale-count (if stale?
                      (inc (long (or (:stale-count definition) 0)))
                      0)
        definition (assoc definition :stale-count stale-count)
        notes (cond-> []
                (= :freeze (:regime d))
                (conj "regime freeze: push target change up")

                (#{:chaos :magma} (:regime d))
                (conj "regime chaos/magma: pull target change down")

                (> (double (:quality eval)) 0.78)
                (conj "high quality: pull targets toward observed descriptor")

                (and (>= stale-count 2) (zero? (mod stale-count 3)))
                (conj "stale novelty: boost novelty weight")

                (> (double (:surprise eval)) 0.72)
                (conj "high surprise: slightly increase surprise weight"))
        definition (cond
                     (= :freeze (:regime d))
                     (-> definition
                         (shift-target :change 0.04)
                         (shift-target :entropy-n 0.02))

                     (#{:chaos :magma} (:regime d))
                     (-> definition
                         (shift-target :change -0.04)
                         (shift-target :temporal 0.03))

                     :else definition)
        definition (if (> (double (:quality eval)) 0.78)
                     (smooth-targets-toward definition d 0.15)
                     definition)
        definition (if (and (>= stale-count 2) (zero? (mod stale-count 3)))
                     (adjust-weights definition
                                     (fn [w]
                                       (-> w
                                           (update :novelty (fnil + 0.0) 0.08)
                                           (update :quality (fnil - 0.0) 0.05))))
                     definition)
        definition (if (> (double (:surprise eval)) 0.72)
                     (adjust-weights definition
                                     (fn [w]
                                       (-> w
                                           (update :surprise (fnil + 0.0) 0.03)
                                           (update :quality (fnil - 0.0) 0.02))))
                     definition)]
    {:definition (-> definition
                     (update :version (fnil inc 0))
                     (update :notes #(vec (take-last 12 (concat (or % []) notes)))))
     :notes notes}))
