(ns futon5.mmca.info-dynamics
  "Information-theoretic dynamics for MMCA histories.

   Computes local transfer entropy (TE) and active information storage (AIS)
   at each spacetime point (x, t). These are the key local measures of
   information processing in cellular automata (Lizier 2010).

   Transfer entropy detects directed information flow between cells.
   In a Class IV CA, gliders show up as high-TE streaks against a low-TE
   background. Active information storage detects computation at
   interaction sites.

   Implemented from scratch using empirical frequency tables — no external
   dependencies (JIDT not required).")

(defn- log2 [x]
  (if (pos? x)
    (/ (Math/log (double x)) (Math/log 2.0))
    0.0))

(defn- cell-at [row x]
  (nth row (mod x (count row))))

;; ---------------------------------------------------------------------------
;; Frequency tables
;; ---------------------------------------------------------------------------

(defn- build-joint-frequencies
  "Build empirical joint frequency table for (source-past, target-past, target-present).
   With k=1 (default), source-past and target-past are single values.
   Returns a map of [source-past target-past target-present] -> count."
  [history x-source x-target k]
  (let [n (count history)]
    (reduce (fn [freq-map t]
              (when (>= t k)
                (let [target-present (cell-at (nth history t) x-target)
                      target-past (mapv #(cell-at (nth history (- t % 1)) x-target)
                                        (range k))
                      source-past (mapv #(cell-at (nth history (- t % 1)) x-source)
                                        (range k))
                      key [source-past target-past target-present]]
                  (update freq-map key (fnil inc 0)))))
            {}
            (range k n))))

(defn- marginalize
  "Marginalize joint distribution by summing over specified indices.
   keep-indices: set of positions to keep in the key."
  [joint-freq keep-indices]
  (reduce-kv
   (fn [acc key cnt]
     (let [marginal-key (mapv #(nth key %) (sort keep-indices))]
       (update acc marginal-key (fnil + 0) cnt)))
   {}
   joint-freq))

(defn- conditional-entropy-from-joint
  "Compute H(X|Y) from joint frequencies p(x, y).
   H(X|Y) = H(X,Y) - H(Y)
   where x-idx and y-idx identify which positions in the key tuple are X and Y."
  [joint-freq x-indices y-indices]
  (let [total (double (reduce + (vals joint-freq)))
        ;; H(X,Y)
        h-xy (- (reduce-kv
                  (fn [acc _ cnt]
                    (let [p (/ (double cnt) total)]
                      (+ acc (* p (log2 p)))))
                  0.0
                  joint-freq))
        ;; H(Y) via marginalization
        marginal-y (marginalize joint-freq (set y-indices))
        h-y (- (reduce-kv
                 (fn [acc _ cnt]
                   (let [p (/ (double cnt) total)]
                     (+ acc (* p (log2 p)))))
                 0.0
                 marginal-y))]
    (max 0.0 (- h-xy h-y))))

;; ---------------------------------------------------------------------------
;; Transfer entropy
;; ---------------------------------------------------------------------------

(defn transfer-entropy-cell
  "Compute transfer entropy from cell x-source to cell x-target
   over the full history, with embedding length k.

   TE(source → target) = H(target_t | target_past) - H(target_t | target_past, source_past)

   This measures how much knowing the source's past reduces uncertainty
   about the target's future, beyond what the target's own past provides.

   Returns a double in [0, +inf). Higher values indicate more information transfer."
  [history x-source x-target & [{:keys [k] :or {k 1}}]]
  (let [;; Build joint table: [source-past target-past target-present]
        joint (build-joint-frequencies history x-source x-target k)
        ;; Indices: 0=source-past, 1=target-past, 2=target-present
        ;; H(target_t | target_past) = H(target_t, target_past) - H(target_past)
        h-target-given-past (conditional-entropy-from-joint
                              joint #{2} #{1})
        ;; H(target_t | target_past, source_past) = H(all) - H(source_past, target_past)
        h-target-given-both (conditional-entropy-from-joint
                              joint #{2} #{0 1})]
    (max 0.0 (- h-target-given-past h-target-given-both))))

(defn local-transfer-entropy
  "Compute local (pointwise) transfer entropy at each (x, t).
   Returns a 2D vector [t][x] of doubles.

   For each cell x at time t, computes the average TE from its neighbors
   (within radius) to x. High TE = information being transferred to this cell.

   opts:
     :k      - embedding length (default 1)
     :radius - neighborhood radius for sources (default 1)"
  [history & [{:keys [k radius] :or {k 1 radius 1}}]]
  (let [n (count history)
        width (count (first history))]
    (if (< n (+ k 2))
      (vec (repeat n (vec (repeat width 0.0))))
      (let [;; Pre-compute per-cell TE for all source-target pairs within radius
            ;; This is O(width * 2*radius * n) which is manageable for small grids
            te-grid (vec (for [t (range n)]
                           (vec (for [x (range width)]
                                  (if (< t k)
                                    0.0
                                    ;; Local TE at (x, t): average over source neighbors
                                    (let [sources (for [dx (range (- radius) (inc radius))
                                                        :when (not (zero? dx))]
                                                    (mod (+ x dx) width))
                                          ;; For efficiency, compute a local TE estimate
                                          ;; using only the data near this point
                                          local-te
                                          (let [;; Look at a window of recent history
                                                window-start (max 0 (- t 20))
                                                window (subvec (vec history) window-start (inc t))
                                                t-local (- t window-start)]
                                            (if (< t-local k)
                                              0.0
                                              (/ (reduce + 0.0
                                                         (map #(transfer-entropy-cell
                                                                 window % x {:k k})
                                                              sources))
                                                 (max 1 (count sources)))))]
                                      local-te))))))]
        te-grid))))

;; ---------------------------------------------------------------------------
;; Active information storage
;; ---------------------------------------------------------------------------

(defn active-information-storage
  "Compute active information storage (AIS) for each cell.
   AIS measures how much a cell's past predicts its future:
   AIS(x) = MI(x_past ; x_present)

   Returns a map {:mean double :max double :per-cell [double ...]}."
  [history & [{:keys [k] :or {k 1}}]]
  (let [n (count history)
        width (count (first history))]
    (if (< n (+ k 1))
      {:mean 0.0 :max 0.0 :per-cell (vec (repeat width 0.0))}
      (let [per-cell
            (mapv (fn [x]
                    ;; Build joint (past, present) distribution
                    (let [joint (reduce (fn [freq t]
                                          (when (>= t k)
                                            (let [present (cell-at (nth history t) x)
                                                  past (mapv #(cell-at (nth history (- t % 1)) x)
                                                             (range k))
                                                  key [past present]]
                                              (update freq key (fnil inc 0)))))
                                        {}
                                        (range k n))
                          total (double (reduce + (vals joint)))
                          ;; MI = H(present) + H(past) - H(past, present)
                          marginal-present (marginalize joint #{1})
                          marginal-past (marginalize joint #{0})
                          h-joint (- (reduce-kv (fn [a _ c]
                                                  (let [p (/ (double c) total)]
                                                    (+ a (* p (log2 p)))))
                                                0.0 joint))
                          h-present (- (reduce-kv (fn [a _ c]
                                                    (let [p (/ (double c) total)]
                                                      (+ a (* p (log2 p)))))
                                                  0.0 marginal-present))
                          h-past (- (reduce-kv (fn [a _ c]
                                                 (let [p (/ (double c) total)]
                                                   (+ a (* p (log2 p)))))
                                               0.0 marginal-past))]
                      (max 0.0 (- (+ h-present h-past) h-joint))))
                  (range width))]
        {:mean (/ (reduce + 0.0 per-cell) (max 1 (count per-cell)))
         :max (if (seq per-cell) (apply max per-cell) 0.0)
         :per-cell per-cell}))))

;; ---------------------------------------------------------------------------
;; Summary and analysis
;; ---------------------------------------------------------------------------

(defn te-summary
  "Summarize transfer entropy across the full spatiotemporal field.
   Returns:
   {:mean-te double          -- average TE across all cells
    :max-te double           -- maximum TE observed
    :te-variance double      -- variance of per-cell average TE
    :high-te-fraction double -- fraction of cells with TE > 2*mean (normalized)
    :information-transport-score double -- composite [0,1] score}"
  [history & [opts]]
  (let [width (count (first history))
        ;; Compute average TE per target cell (from both neighbors)
        per-cell-te
        (mapv (fn [x]
                (let [left (mod (dec x) width)
                      right (mod (inc x) width)
                      te-left (transfer-entropy-cell history left x (or opts {}))
                      te-right (transfer-entropy-cell history right x (or opts {}))]
                  (/ (+ te-left te-right) 2.0)))
              (range width))
        mean-te (/ (reduce + 0.0 per-cell-te) (max 1 width))
        max-te (if (seq per-cell-te) (apply max per-cell-te) 0.0)
        variance (/ (reduce + 0.0 (map #(let [d (- % mean-te)] (* d d)) per-cell-te))
                    (max 1 (dec width)))
        threshold (* 2.0 mean-te)
        high-count (count (filter #(> % threshold) per-cell-te))
        high-frac (/ (double high-count) (max 1 width))
        ;; Composite: high variance in TE + some high-TE cells = information transport
        transport-score (Math/tanh (* 3.0 (+ (* 0.5 (Math/tanh (* 5.0 (Math/sqrt variance))))
                                             (* 0.5 high-frac))))]
    {:mean-te mean-te
     :max-te max-te
     :te-variance variance
     :high-te-fraction high-frac
     :information-transport-score transport-score}))

(defn analyze-info-dynamics
  "Full information dynamics analysis. Returns:
   {:transfer-entropy {:mean-te :max-te :te-variance :high-te-fraction :information-transport-score}
    :active-info-storage {:mean :max :per-cell}
    :information-transport-score double  -- the key normalized [0,1] composite}"
  [history & [opts]]
  (let [te (te-summary history opts)
        ais (active-information-storage history opts)]
    {:transfer-entropy te
     :active-info-storage ais
     :information-transport-score (:information-transport-score te)}))
