(ns futon5.mmca.filament
  "Detect and score filamentary edge-of-chaos structure in CA-like frames."
  (:require [clojure.string :as str]
            [clojure.test :as t]))

;; -----------------------------------------------------------------------------
;; Grid helpers

(defn dims [grid]
  [(count grid) (count (or (first grid) []))])

(defn in-bounds?
  [[rows cols] [y x]]
  (and (<= 0 y) (< y rows) (<= 0 x) (< x cols)))

(defn get-cell
  ([grid [y x]] (get-cell grid [y x] 0))
  ([grid [y x] default]
   (if (in-bounds? (dims grid) [y x])
     (let [row (nth grid y)]
       (if (and row (<= 0 x) (< x (count row)))
         (nth row x)
         default))
     default)))

(defn set-cell
  [grid [y x] v]
  (assoc-in grid [y x] v))

(defn neighbours8
  [[y x]]
  [[(dec y) x] [(dec y) (inc x)] [y (inc x)] [(inc y) (inc x)]
   [(inc y) x] [(inc y) (dec x)] [y (dec x)] [(dec y) (dec x)]])

(defn count-neigh
  [grid coord]
  (reduce (fn [acc n]
            (+ acc (if (pos? (get-cell grid n 0)) 1 0)))
          0
          (neighbours8 coord)))

(defn on-coords
  [grid]
  (let [[rows cols] (dims grid)]
    (reduce (fn [acc y]
              (reduce (fn [acc2 x]
                        (if (pos? (get-cell grid [y x] 0))
                          (conj acc2 [y x])
                          acc2))
                      acc
                      (range cols)))
            []
            (range rows))))

(defn count-on [grid]
  (count (on-coords grid)))

;; -----------------------------------------------------------------------------
;; Zhang-Suen thinning

(defn- ordered-neigh
  "Return p2..p9 (north, NE, E, SE, S, SW, W, NW)."
  [grid [y x]]
  (mapv #(if (pos? (get-cell grid % 0)) 1 0)
        [[(dec y) x] [(dec y) (inc x)] [y (inc x)] [(inc y) (inc x)]
         [(inc y) x] [(inc y) (dec x)] [y (dec x)] [(dec y) (dec x)]]))

(defn- transitions-0-1
  "A(p): number of 0->1 transitions in ordered neighbours p2..p9..p2."
  [neigh]
  (let [cycle (conj neigh (first neigh))]
    (reduce (fn [acc [a b]]
              (+ acc (if (and (zero? a) (= 1 b)) 1 0)))
            0
            (partition 2 1 cycle))))

(defn- step-delete?
  "Check Zhang-Suen delete conditions for step 1 or step 2."
  [grid coord step]
  (let [p (ordered-neigh grid coord)
        b (reduce + p)
        a (transitions-0-1 p)
        [p2 _p3 p4 _p5 p6 _p7 p8 _p9] p
        c1 (and (<= 2 b) (<= b 6))
        c2 (= a 1)]
    (and c1 c2
         (case step
           1 (and (zero? (* p2 p4 p6))
                  (zero? (* p4 p6 p8)))
           2 (and (zero? (* p2 p4 p8))
                  (zero? (* p2 p6 p8)))
           false))))

(defn- thin-step
  [grid step]
  (let [coords (on-coords grid)
        deletions (filter #(step-delete? grid % step) coords)]
    (reduce (fn [g coord] (set-cell g coord 0)) grid deletions)))

(defn thin
  "Zhang-Suen thinning on a binary grid. Returns a skeleton grid."
  [grid]
  (loop [g grid]
    (let [g1 (thin-step g 1)
          g2 (thin-step g1 2)]
      (if (= g2 g)
        g2
        (recur g2)))))

;; -----------------------------------------------------------------------------
;; Skeleton metrics

(defn- degree
  [grid coord]
  (count-neigh grid coord))

(defn- component-sizes
  [grid]
  (let [coords (set (on-coords grid))]
    (loop [remaining coords
           sizes []]
      (if (empty? remaining)
        sizes
        (let [start (first remaining)
              comp (loop [stack [start]
                          seen #{start}]
                     (if (empty? stack)
                       seen
                       (let [c (peek stack)
                             stack (pop stack)
                             ns (filter coords (neighbours8 c))
                             new (remove seen ns)]
                         (recur (into stack new) (into seen new)))))]
          (recur (reduce disj remaining comp)
                 (conj sizes (count comp))))))))

(defn- count-edges
  "Count undirected adjacency edges between on pixels (8-connected)."
  [grid]
  (let [[rows cols] (dims grid)
        offsets [[0 1] [1 0] [1 1] [1 -1]]]
    (reduce (fn [acc y]
              (reduce (fn [acc2 x]
                        (if (pos? (get-cell grid [y x] 0))
                          (+ acc2
                             (reduce (fn [e [dy dx]]
                                       (let [ny (+ y dy) nx (+ x dx)]
                                         (if (and (<= 0 ny) (< ny rows)
                                                  (<= 0 nx) (< nx cols)
                                                  (pos? (get-cell grid [ny nx] 0)))
                                           (inc e)
                                           e)))
                                     0
                                     offsets))
                          acc2))
                      acc
                      (range cols)))
            0
            (range rows))))

(defn skeleton-metrics
  "Compute skeleton graph metrics for a binary grid (already thinned)."
  [grid]
  (let [coords (on-coords grid)
        length (count coords)
        degs (map #(degree grid %) coords)
        endpoints (count (filter #(= 1 %) degs))
        branchpoints (count (filter #(>= % 3) degs))
        comps (component-sizes grid)
        comp-count (count comps)
        max-comp (if (seq comps) (apply max comps) 0)
        giant-frac (if (pos? length) (/ max-comp (double length)) 0.0)
        edges (count-edges grid)
        cycles (max 0 (+ (- edges length) comp-count))]
    {:skeleton-length length
     :endpoint-count endpoints
     :endpoint-density (if (pos? length) (/ endpoints (double length)) 0.0)
     :branchpoint-count branchpoints
     :branchpoint-density (if (pos? length) (/ branchpoints (double length)) 0.0)
     :components comp-count
     :giant-component-frac giant-frac
     :edges edges
     :cycles cycles}))

;; -----------------------------------------------------------------------------
;; Temporal persistence

(defn persistence
  "Overlap of skeletons allowing Chebyshev radius r."
  [skel-a skel-b {:keys [r] :or {r 1}}]
  (let [coords-a (on-coords skel-a)
        coords-b (set (on-coords skel-b))
        r (int r)
        matched (reduce (fn [acc [y x]]
                          (let [found? (some (fn [dy]
                                               (some (fn [dx]
                                                       (contains? coords-b [(+ y dy) (+ x dx)]))
                                                     (range (- r) (inc r))))
                                             (range (- r) (inc r)))]
                            (if found? (inc acc) acc)))
                        0
                        coords-a)]
    (if (seq coords-a)
      (/ matched (double (count coords-a)))
      0.0)))

;; -----------------------------------------------------------------------------
;; Scoring

(defn- safe-log1p [x]
  (Math/log1p (max 0.0 (double x))))

(defn filament-score
  "Compute a filament score from metrics."
  [metrics {:keys [length-weight endpoint-eps]
            :or {length-weight 0.3 endpoint-eps 1.0}}]
  (let [{:keys [skeleton-length endpoint-density branchpoint-density
                giant-component-frac cycles persistence]} metrics
        length-factor (Math/pow (safe-log1p skeleton-length) length-weight)
        endpoint-penalty (+ endpoint-density endpoint-eps)
        branch-boost (+ 0.5 branchpoint-density)
        cycle-boost (+ 0.5 (safe-log1p cycles))
        coherence (* (+ 0.5 giant-component-frac) (+ 0.5 persistence))]
    (* length-factor branch-boost cycle-boost coherence (/ 1.0 endpoint-penalty))))

(defn analyze-run
  "Analyze a run as a sequence of binary grids.
  Returns per-frame metrics, aggregates, and final score."
  [frames {:keys [r score-opts] :or {r 1 score-opts {}}}]
  (let [skels (mapv thin frames)
        metrics (mapv skeleton-metrics skels)
        persistence-series (mapv (fn [[a b]]
                                   (persistence a b {:r r}))
                                 (partition 2 1 skels))
        metrics (mapv (fn [m idx]
                        (assoc m :persistence
                               (if (zero? idx) 0.0 (nth persistence-series (dec idx)))))
                      metrics
                      (range))
        avg (fn [k] (if (seq metrics)
                      (/ (reduce + 0.0 (map k metrics)) (double (count metrics)))
                      0.0))
        agg {:skeleton-length (avg :skeleton-length)
             :endpoint-density (avg :endpoint-density)
             :branchpoint-density (avg :branchpoint-density)
             :giant-component-frac (avg :giant-component-frac)
             :cycles (avg :cycles)
             :persistence (if (seq persistence-series)
                            (/ (reduce + 0.0 persistence-series) (double (count persistence-series)))
                            0.0)}
        score (filament-score agg score-opts)]
    {:frames metrics
     :aggregate agg
     :score score}))

(defn topk
  "Return top-k runs by score."
  [runs k]
  (->> runs
       (sort-by :score >)
       (take k)))

;; -----------------------------------------------------------------------------
;; Demo grids

(def plus-grid
  [[0 0 0 0 0]
   [0 0 1 0 0]
   [0 1 1 1 0]
   [0 0 1 0 0]
   [0 0 0 0 0]])

(def noise-grid
  [[0 1 0 1 0]
   [1 0 1 0 1]
   [0 1 0 1 0]
   [1 0 1 0 1]
   [0 1 0 1 0]])

(def lattice-grid
  [[1 0 1 0 1]
   [0 1 0 1 0]
   [1 0 1 0 1]
   [0 1 0 1 0]
   [1 0 1 0 1]])

(defn demo []
  (doseq [[label grid] [["plus" plus-grid] ["noise" noise-grid] ["lattice" lattice-grid]]]
    (let [skel (thin grid)
          metrics (skeleton-metrics skel)]
      (println (format "%s metrics: %s" label (pr-str metrics))))))

;; -----------------------------------------------------------------------------
;; Tests

(t/deftest thinning-does-not-increase
  (let [grid plus-grid
        skel (thin grid)]
    (t/is (<= (count-on skel) (count-on grid)))))

(t/deftest cycles-nonnegative
  (let [metrics (skeleton-metrics (thin lattice-grid))]
    (t/is (>= (:cycles metrics) 0))))

(t/deftest persistence-identical
  (let [grid plus-grid
        skel (thin grid)]
    (t/is (= 1.0 (persistence skel skel {:r 1})))))

(defn -main [& args]
  (if (some #{"test"} args)
    (t/run-tests 'futon5.mmca.filament)
    (demo)))
