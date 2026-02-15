(ns futon5.mmca.domain-analysis
  "Spatiotemporal domain detection for MMCA histories.

   Finds periodic background tiles (the 'ether' or 'domain') and measures
   what fraction of cells match the pattern. This is the key signal for
   Wolfram Class IV detection:

     domain-fraction ~1.0  →  Class I/II (fully periodic)
     domain-fraction ~0.0  →  Class III (no repeating structure)
     domain-fraction 0.5-0.95  →  Class IV candidate (periodic background with defects)

   The periodic tile has spatial period P_x and temporal period P_t.
   A 'defect' is any cell that deviates from the tiled background —
   connected defect regions are candidate particles/gliders.")

(defn- cell-at
  "Get the value at position x in row. Works for strings and vectors."
  [row x]
  (nth row (mod x (count row))))

(defn extract-tile
  "Extract a candidate tile of size [px pt] starting at position [x0 t0].
   history: vector of rows (strings or vectors).
   Returns a 2D vector of values [pt rows][px cols]."
  [history x0 t0 px pt]
  (let [n (count history)]
    (mapv (fn [dt]
            (let [t (+ t0 dt)]
              (when (< t n)
                (mapv (fn [dx] (cell-at (nth history t) (+ x0 dx)))
                      (range px)))))
          (range pt))))

(defn- tile-cell
  "Look up a value in the tile at position (x, t) relative to tile origin."
  [tile px pt x t]
  (let [tx (mod x px)
        tt (mod t pt)]
    (nth (nth tile tt) tx)))

(defn tile-match-fraction
  "Fraction of cells in the region [t-start, t-end) that match the tile
   when tiled periodically from origin (x-offset, t-offset).
   Samples up to sample-limit cells for efficiency."
  [history tile px pt x-offset t-offset t-start t-end
   & [{:keys [sample-limit] :or {sample-limit 5000}}]]
  (let [width (count (first history))
        total-cells (* width (- t-end t-start))]
    (if (zero? total-cells)
      0.0
      (let [;; If total cells is small enough, check all; otherwise sample
            check-all? (<= total-cells sample-limit)
            cells (if check-all?
                    (for [t (range t-start t-end)
                          x (range width)]
                      [x t])
                    ;; Deterministic sampling via stride
                    (let [stride (max 1 (quot total-cells sample-limit))]
                      (for [i (range 0 total-cells stride)]
                        (let [t (+ t-start (quot i width))
                              x (mod i width)]
                          [x t]))))
            n-checked (count cells)
            matches (reduce (fn [acc [x t]]
                              (if (= (cell-at (nth history t) x)
                                     (tile-cell tile px pt
                                                (- x x-offset)
                                                (- t t-offset)))
                                (inc acc)
                                acc))
                            0
                            cells)]
        (/ (double matches) (double n-checked))))))

(defn find-best-tile
  "Search for the best periodic tile in the latter portion of history.
   Searches spatial periods 1..max-px and temporal periods 1..max-pt.

   Returns {:tile 2D-vec :px int :pt int :fraction double :origin [x0 t0]}
   or nil if history is too short."
  [history {:keys [max-px max-pt] :or {max-px 16 max-pt 16}}]
  (let [n (count history)
        width (count (first history))]
    (when (and (> n 4) (> width 2))
      (let [;; Use latter half to skip transients
            t-start (quot n 2)
            t-end n
            ;; Try extracting tiles from a stable region
            t-sample (+ t-start (quot (- t-end t-start) 3))
            x-sample 0
            best (atom {:fraction 0.0})]
        (doseq [pt (range 1 (inc (min max-pt (- t-end t-start))))
                px (range 1 (inc (min max-px width)))
                :when (<= (+ t-sample pt) n)]
          (let [tile (extract-tile history x-sample t-sample px pt)]
            (when (every? some? tile)
              (let [frac (tile-match-fraction
                          history tile px pt
                          x-sample t-sample
                          t-start t-end)]
                (when (> frac (:fraction @best))
                  (reset! best {:tile tile :px px :pt pt
                                :fraction frac
                                :origin [x-sample t-sample]}))
                ;; Early exit if near-perfect match
                (when (> frac 0.98)
                  (reduced nil))))))
        (when (> (:fraction @best) 0.0)
          @best)))))

(defn domain-mask
  "Return a 2D boolean vector: true where cell matches domain tile.
   Used by particle analysis to identify defects (non-background cells)."
  [history {:keys [tile px pt origin]}]
  (let [[x-off t-off] origin
        n (count history)
        width (count (first history))]
    (mapv (fn [t]
            (mapv (fn [x]
                    (= (cell-at (nth history t) x)
                       (tile-cell tile px pt (- x x-off) (- t t-off))))
                  (range width)))
          (range n))))

(defn domain-fraction
  "Compute the fraction of cells in the latter half that match the best
   periodic tile. This is the primary Class IV signal."
  [history & [opts]]
  (if-let [best (find-best-tile history (or opts {}))]
    (:fraction best)
    0.0))

(defn analyze-domain
  "Full domain analysis. Returns:
   {:domain-fraction double  -- fraction of cells matching background
    :px int                  -- spatial period of background
    :pt int                  -- temporal period of background
    :tile 2D-vec             -- the background tile
    :origin [x0 t0]          -- tile extraction origin
    :class-iv-candidate? boolean -- domain-fraction in [0.5, 0.95]}"
  [history & [opts]]
  (if-let [best (find-best-tile history (or opts {}))]
    (let [frac (:fraction best)]
      (assoc best :domain-fraction frac
                  :class-iv-candidate? (and (>= frac 0.5) (<= frac 0.95))))
    {:domain-fraction 0.0
     :px 0 :pt 0 :tile nil :origin nil
     :class-iv-candidate? false}))
