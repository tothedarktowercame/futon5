(ns futon5.xenotype.scorer
  "Implementations for scorer wiring components.

   These evaluate CA runs and compute fitness scores."
  (:require [clojure.set :as set]
            [futon5.mmca.filament :as filament]))

;;; ============================================================
;;; INPUT: Extract from run data
;;; ============================================================

(defn run-frames [{:keys [run]}]
  {:frames (:frames run)})

(defn run-genotype [{:keys [run]}]
  {:genotype (vec (:genotype run))})

(defn run-metadata [{:keys [run]}]
  {:meta (dissoc run :frames :genotype)})

(defn frames-count [{:keys [frames]}]
  {:count (count frames)})

(defn frame-at [{:keys [frames index]}]
  {:frame (nth frames (min index (dec (count frames))))})

(defn frames-slice [{:keys [frames start end]}]
  {:frames (vec (take (- end start) (drop start frames)))})

;;; ============================================================
;;; SKELETON: Thinning and structure extraction
;;; ============================================================

(defn thin-frame [{:keys [frame]}]
  {:skeleton (filament/thin frame)})

(defn thin-all [{:keys [frames]}]
  {:skeletons (mapv filament/thin frames)})

;;; ============================================================
;;; METRICS: Per-frame measurements
;;; ============================================================

(defn skeleton-length [{:keys [skeleton]}]
  {:length (count (filament/on-coords skeleton))})

(defn endpoint-density [{:keys [skeleton]}]
  (let [metrics (filament/skeleton-metrics skeleton)]
    {:density (:endpoint-density metrics)}))

(defn branchpoint-density [{:keys [skeleton]}]
  (let [metrics (filament/skeleton-metrics skeleton)]
    {:density (:branchpoint-density metrics)}))

(defn component-count [{:keys [skeleton]}]
  (let [metrics (filament/skeleton-metrics skeleton)]
    {:count (:components metrics)}))

(defn giant-component-frac [{:keys [skeleton]}]
  (let [metrics (filament/skeleton-metrics skeleton)]
    {:frac (:giant-component-frac metrics)}))

(defn cycle-count [{:keys [skeleton]}]
  (let [metrics (filament/skeleton-metrics skeleton)]
    {:cycles (:cycles metrics)}))

(defn skeleton-metrics-comp [{:keys [skeleton]}]
  {:metrics (filament/skeleton-metrics skeleton)})

;;; ============================================================
;;; TEMPORAL: Cross-frame measurements
;;; ============================================================

(defn persistence [{:keys [skel-a skel-b radius] :or {radius 1}}]
  {:overlap (filament/persistence skel-a skel-b {:r radius})})

(defn persistence-series [{:keys [skeletons radius] :or {radius 1}}]
  {:series (mapv (fn [[a b]] (filament/persistence a b {:r radius}))
                 (partition 2 1 skeletons))})

(defn change-rate [{:keys [frame-a frame-b]}]
  (let [coords-a (clojure.core/set (filament/on-coords frame-a))
        coords-b (clojure.core/set (filament/on-coords frame-b))
        union (set/union coords-a coords-b)
        diff (set/difference
              (set/union coords-a coords-b)
              (set/intersection coords-a coords-b))]
    {:rate (if (empty? union)
             0.0
             (/ (count diff) (double (count union))))}))

(defn activity-series [{:keys [frames]}]
  {:series (mapv (fn [[a b]]
                   (:rate (change-rate {:frame-a a :frame-b b})))
                 (partition 2 1 frames))})

;;; ============================================================
;;; AGGREGATION: Combine measurements
;;; ============================================================

(defn mean-agg [{:keys [values]}]
  {:avg (if (empty? values)
          0.0
          (/ (reduce + 0.0 values) (count values)))})

(defn median-agg [{:keys [values]}]
  {:med (if (empty? values)
          0.0
          (let [sorted (sort values)
                n (count sorted)
                mid (quot n 2)]
            (if (odd? n)
              (nth sorted mid)
              (/ (+ (nth sorted (dec mid)) (nth sorted mid)) 2.0))))})

(defn variance-agg [{:keys [values]}]
  {:var (if (< (count values) 2)
          0.0
          (let [mu (/ (reduce + 0.0 values) (count values))
                sq-diffs (map #(Math/pow (- % mu) 2) values)]
            (/ (reduce + 0.0 sq-diffs) (count values))))})

(defn min-val [{:keys [values]}]
  {:min (if (empty? values) 0.0 (apply min values))})

(defn max-val [{:keys [values]}]
  {:max (if (empty? values) 0.0 (apply max values))})

(defn sum-agg [{:keys [values]}]
  {:total (reduce + 0.0 values)})

(defn normalize [{:keys [value min max]}]
  {:normalized (if (= min max)
                 0.5
                 (/ (- value min) (- max min)))})

;;; ============================================================
;;; ARITHMETIC: Scalar operations
;;; ============================================================

(defn add-op [{:keys [a b]}]
  {:result (+ (double a) (double b))})

(defn multiply-op [{:keys [a b]}]
  {:result (* (double a) (double b))})

(defn divide-op [{:keys [a b]}]
  {:result (if (zero? b) 0.0 (/ (double a) (double b)))})

(defn log1p-op [{:keys [x]}]
  {:result (Math/log1p (max 0.0 (double x)))})

(defn pow-op [{:keys [base exp]}]
  {:result (Math/pow (double base) (double exp))})

(defn clamp-op [{:keys [x lo hi]}]
  {:result (max (double lo) (min (double hi) (double x)))})

(defn invert-op [{:keys [x]}]
  {:result (if (zero? x) 0.0 (/ 1.0 (double x)))})

;;; ============================================================
;;; CONTROL: Conditional and composition
;;; ============================================================

(defn threshold-op [{:keys [score threshold]}]
  {:above (>= score threshold)})

(defn if-then-else [{:keys [cond then else]}]
  {:result (if cond then else)})

(defn weighted-sum [{:keys [values weights]}]
  {:result (reduce + 0.0
                   (map * values (concat weights (repeat 0.0))))})

(defn product-all [{:keys [values]}]
  {:result (reduce * 1.0 values)})

;;; ============================================================
;;; EDGE-OF-CHAOS: Specialized EOC metrics
;;; ============================================================

(defn entropy-score [{:keys [frames]}]
  ;; Simple spatial entropy: fraction of on cells
  (let [entropies (map (fn [frame]
                         (let [total (* (count frame)
                                        (count (first frame)))
                               on (count (filament/on-coords frame))]
                           (if (zero? total)
                             0.0
                             (let [p (/ on (double total))]
                               (if (or (zero? p) (= p 1.0))
                                 0.0
                                 (- (- (* p (Math/log p))
                                       (* (- 1 p) (Math/log (- 1 p))))))))))
                       frames)]
    {:score (if (empty? entropies)
              0.0
              (/ (reduce + 0.0 entropies) (count entropies)))}))

(defn lambda-estimate [{:keys [frames]}]
  ;; Langton's lambda approximation: fraction of active cells
  (let [lambdas (map (fn [frame]
                       (let [total (* (count frame)
                                      (count (first frame)))
                             on (count (filament/on-coords frame))]
                         (if (zero? total) 0.0 (/ on (double total)))))
                     frames)]
    {:lambda (if (empty? lambdas)
               0.0
               (/ (reduce + 0.0 lambdas) (count lambdas)))}))

(defn eoc-distance [{:keys [lambda]}]
  ;; Distance from edge of chaos (lambda = 0.5)
  {:dist (Math/abs (- lambda 0.5))})

;;; ============================================================
;;; OUTPUT: Final score
;;; ============================================================

(defn output-score [{:keys [score]}]
  {:out score})

(defn output-with-breakdown [{:keys [score components]}]
  {:out score :breakdown components})

;;; ============================================================
;;; REGISTRY
;;; ============================================================

(def scorer-registry
  "Registry of all scorer component implementations."
  {;; Input
   :run-frames run-frames
   :run-genotype run-genotype
   :run-metadata run-metadata
   :frames-count frames-count
   :frame-at frame-at
   :frames-slice frames-slice

   ;; Skeleton
   :thin-frame thin-frame
   :thin-all thin-all

   ;; Metrics
   :skeleton-length skeleton-length
   :endpoint-density endpoint-density
   :branchpoint-density branchpoint-density
   :component-count component-count
   :giant-component-frac giant-component-frac
   :cycle-count cycle-count
   :skeleton-metrics skeleton-metrics-comp

   ;; Temporal
   :persistence persistence
   :persistence-series persistence-series
   :change-rate change-rate
   :activity-series activity-series

   ;; Aggregation
   :mean mean-agg
   :median median-agg
   :variance variance-agg
   :min-val min-val
   :max-val max-val
   :sum sum-agg
   :normalize normalize

   ;; Arithmetic
   :add add-op
   :multiply multiply-op
   :divide divide-op
   :log1p log1p-op
   :pow pow-op
   :clamp clamp-op
   :invert invert-op

   ;; Control
   :threshold threshold-op
   :if-then-else if-then-else
   :weighted-sum weighted-sum
   :product-all product-all

   ;; Edge of Chaos
   :entropy-score entropy-score
   :lambda-estimate lambda-estimate
   :eoc-distance eoc-distance

   ;; Output
   :output-score output-score
   :output-with-breakdown output-with-breakdown})
