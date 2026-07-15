(ns futon5.mmca.diagonal-transport
  "Windowed bilateral transport probe for binary spacetime fields.

   The probe operates on temporal innovations (cell changes), not cell values.
   For each window it measures the strongest lag-one phi correlation carried
   left and right at speeds 1..MAX-SPEED, then takes their minimum.  Requiring
   both directions rejects one-way correlation such as Rule 30's dominant
   characteristic.  Phi normalization makes the statistic independent of the
   number of sampled cells.  This is an observational probe, not an EoC label:
   H-diagonal-transport shows that it fails the frozen-barcode gate.")

(defn- binary-cell [x]
  (cond
    (or (= x 0) (= x \0)) 0
    (or (= x 1) (= x \1)) 1
    :else (throw (ex-info "spacetime cells must be binary" {:cell x}))))

(defn- rectangular-binary-grid [grid]
  (let [rows (mapv (fn [row] (mapv binary-cell row)) grid)
        widths (set (map count rows))]
    (when (or (< (count rows) 3)
              (not= 1 (count widths))
              (zero? (or (first widths) 0)))
      (throw (ex-info "spacetime must have >=3 non-empty equal-width rows"
                      {:rows (count rows) :widths widths})))
    rows))

(defn innovation-field
  "Return D[t,x] = X[t+1,x] XOR X[t,x]. A frozen field maps to all zeroes."
  [grid]
  (let [rows (rectangular-binary-grid grid)]
    (mapv (fn [a b] (mapv bit-xor a b)) rows (rest rows))))

(defn- phi
  "Phi coefficient for binary PAIRS; zero when either margin has no variance."
  [pairs]
  (let [counts (frequencies pairs)
        a (double (get counts [1 1] 0))
        b (double (get counts [1 0] 0))
        c (double (get counts [0 1] 0))
        d (double (get counts [0 0] 0))
        denominator (Math/sqrt (* (+ a b) (+ c d) (+ a c) (+ b d)))]
    (if (pos? denominator)
      (/ (- (* a d) (* b c)) denominator)
      0.0)))

(defn- shifted-correlation [innovations shift]
  (let [width (count (first innovations))
        last-t (dec (count innovations))]
    (phi (for [t (range last-t)
               x (range width)]
           [(get-in innovations [t x])
            (get-in innovations [(inc t) (mod (+ x shift) width)])]))))

(defn window-score
  "Measure bilateral innovation transport in one binary spacetime WINDOW.

   Returns the absolute correlations by velocity, the strongest left/right
   values, their minimum as :score, and innovation density."
  ([window] (window-score window {}))
  ([window {:keys [max-speed] :or {max-speed 3}}]
   (when-not (pos-int? max-speed)
     (throw (ex-info "max-speed must be a positive integer"
                     {:max-speed max-speed})))
   (let [innovations (innovation-field window)
         velocities (vec (concat (range (- max-speed) 0)
                                 (range 1 (inc max-speed))))
         correlations (into (sorted-map)
                            (map (fn [velocity]
                                   [velocity (Math/abs
                                              (shifted-correlation innovations
                                                                   velocity))]))
                            velocities)
         left (apply max (map correlations (range (- max-speed) 0)))
         right (apply max (map correlations (range 1 (inc max-speed))))
         cells (mapcat identity innovations)]
     {:score (min left right)
      :left left
      :right right
      :correlations correlations
      :innovation-density (/ (double (reduce + cells)) (count cells))})))

(defn profile
  "Return a sliding-window transport profile for GRID.

   Options: :window-size (20), :stride (10), :max-speed (3). Windows are
   reported as half-open [:t-start :t-end] coordinates in the input grid."
  ([grid] (profile grid {}))
  ([grid {:keys [window-size stride max-speed]
          :or {window-size 20 stride 10 max-speed 3}}]
   (let [grid (rectangular-binary-grid grid)
         n (count grid)]
     (when (or (< window-size 3) (> window-size n) (not (pos-int? stride)))
       (throw (ex-info "invalid transport window"
                       {:rows n :window-size window-size :stride stride})))
     (mapv (fn [start]
             (merge {:t-start start :t-end (+ start window-size)}
                    (window-score (subvec grid start (+ start window-size))
                                  {:max-speed max-speed})))
           (range 0 (inc (- n window-size)) stride)))))

(defn median-score
  "Median :score of a non-empty transport PROFILE."
  [transport-profile]
  (let [scores (vec (sort (map :score transport-profile)))
        n (count scores)]
    (when (zero? n)
      (throw (ex-info "transport profile is empty" {})))
    (if (odd? n)
      (nth scores (quot n 2))
      (/ (+ (nth scores (dec (quot n 2))) (nth scores (quot n 2))) 2.0))))

(defn- genotype-byte [cell]
  (let [bits (cond
               (string? cell) cell
               (sequential? cell) (apply str cell)
               :else nil)]
    (when-not (and (= 8 (count bits)) (every? #{\0 \1} bits))
      (throw (ex-info "genotype cells must be eight-bit rule strings"
                      {:cell cell})))
    bits))

(defn genotype-bitplanes
  "Split an eight-bit-rule genotype spacetime into eight binary fields.

   Each input cell is the canonical eight-character rule string returned by
   MetaCA's `get-genotype-from-sigil`.  Keeping the truth-table bits separate
   avoids collapsing distinct rule changes into a single changed/not-changed
   category."
  [grid]
  (let [rows (mapv (fn [row] (mapv genotype-byte row)) grid)
        widths (set (map count rows))]
    (when (or (< (count rows) 3)
              (not= 1 (count widths))
              (zero? (or (first widths) 0)))
      (throw (ex-info "genotype spacetime must have >=3 non-empty equal-width rows"
                      {:rows (count rows) :widths widths})))
    (mapv (fn [bit-index]
            (mapv (fn [row]
                    (mapv #(binary-cell (nth % bit-index)) row))
                  rows))
          (range 8))))

(defn genotype-profile
  "Return windowed transport for an eight-bit-rule genotype spacetime.

   The binary probe is applied independently to each canonical truth-table
   bit-plane.  A window's genotype :score is the arithmetic mean of its eight
   bit-plane bilateral scores; :innovation-density is likewise the mean and is
   therefore the mean per-bit Hamming change.  Per-plane values remain in the
   result so this declared aggregation is auditable."
  ([grid] (genotype-profile grid {}))
  ([grid opts]
   (let [plane-profiles (mapv #(profile % opts) (genotype-bitplanes grid))]
     (apply mapv
            (fn [& windows]
              (let [scores (mapv :score windows)
                    densities (mapv :innovation-density windows)]
                {:t-start (:t-start (first windows))
                 :t-end (:t-end (first windows))
                 :score (/ (reduce + scores) 8.0)
                 :innovation-density (/ (reduce + densities) 8.0)
                 :bit-plane-scores scores
                 :bit-plane-innovation-densities densities}))
            plane-profiles))))
