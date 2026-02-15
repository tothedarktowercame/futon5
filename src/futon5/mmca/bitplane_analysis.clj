(ns futon5.mmca.bitplane-analysis
  "Multi-scale bitplane analysis for MMCA histories.

   Sigil-based CAs (256 symbols) encode 8 independent bitplanes. When the
   wiring operates per-bit (bit-and, bit-or, bit-xor), each bitplane evolves
   as an independent 1D elementary CA. When the wiring or kernel creates
   cross-bit interactions, the bitplanes become coupled — and it's precisely
   this coupling that can generate computational dynamics beyond what any
   single elementary CA could achieve.

   This module provides three layers of analysis:

   1. **Bitplane decomposition** — convert sigil history into 8 binary histories
   2. **Per-bitplane metrics** — domain detection, compression variance on each
   3. **Cross-bitplane coupling** — pairwise mutual information, coupling
      profile over time, spatial coupling structure

   The coupling spectrum measures 'how coupled' the bitplanes are:
   - Independence (coupling ~ 0): 8 parallel elementary CAs
   - Weak coupling: mostly independent with local interactions
   - Strong structured coupling: genuine multi-bit computation
   - Uniform coupling: noise/chaos across all bits

   The most interesting systems are expected to show intermediate, spatially
   structured coupling — like L5-creative's boundary-guardian that mixes
   legacy kernel and XOR-creative paths based on local diversity."
  (:require [futon5.ca.core :as ca]
            [futon5.mmca.domain-analysis :as domain]
            [futon5.mmca.metrics :as metrics]))

;; =============================================================================
;; Bitplane Decomposition
;; =============================================================================

(defn sigil->bits
  "Convert a single sigil character to its 8-bit vector [b7 b6 ... b0]."
  [sigil]
  (let [bits-str (ca/bits-for (str sigil))]
    (mapv #(if (= % \1) 1 0) bits-str)))

(defn decompose-row
  "Decompose a sigil row (string) into 8 binary rows (vectors of 0/1)."
  [row]
  (let [bit-vecs (mapv sigil->bits (seq row))]
    ;; Transpose: from [cell][bit] to [bit][cell]
    (mapv (fn [plane-idx]
            (mapv #(nth % plane-idx) bit-vecs))
          (range 8))))

(defn decompose-history
  "Decompose a sigil history into 8 bitplane histories.
   Returns a vector of 8 histories, where each history is a vector of
   binary rows (vectors of 0/1)."
  [history]
  (let [n (count history)
        width (count (first history))
        ;; Pre-decompose all rows
        decomposed (mapv decompose-row history)]
    ;; Collect per-bitplane: [plane][time][cell]
    (mapv (fn [plane-idx]
            (mapv #(nth % plane-idx) decomposed))
          (range 8))))

;; =============================================================================
;; Per-Bitplane Metrics
;; =============================================================================

(defn bitplane-domain
  "Run domain analysis on a single binary bitplane history.
   The binary rows need to be converted to strings for the domain analyzer."
  [bitplane-history]
  (let [;; Convert binary vectors to strings for domain analysis
        str-history (mapv #(apply str (map str %)) bitplane-history)]
    (domain/analyze-domain str-history)))

(defn bitplane-compression-variance
  "Compute compression variance on a single binary bitplane."
  [bitplane-history]
  (let [str-history (mapv #(apply str (map str %)) bitplane-history)]
    (metrics/compression-variance str-history)))

(defn analyze-bitplane
  "Full analysis of a single bitplane."
  [bitplane-history plane-idx]
  (let [domain-result (bitplane-domain bitplane-history)
        cv-result (bitplane-compression-variance bitplane-history)
        ;; Basic activity measures
        n (count bitplane-history)
        change-rates (for [t (range 1 n)]
                       (let [row-a (nth bitplane-history (dec t))
                             row-b (nth bitplane-history t)
                             diffs (count (filter (fn [[a b]] (not= a b))
                                                  (map vector row-a row-b)))]
                         (/ (double diffs) (count row-a))))
        mean-change (if (seq change-rates)
                      (/ (reduce + 0.0 change-rates) (count change-rates))
                      0.0)]
    {:plane plane-idx
     :domain-fraction (:domain-fraction domain-result)
     :domain-px (:px domain-result)
     :domain-pt (:pt domain-result)
     :class-iv-candidate? (:class-iv-candidate? domain-result)
     :compression-cv (:cv cv-result)
     :mean-change mean-change}))

(defn analyze-all-bitplanes
  "Analyze all 8 bitplanes independently."
  [history]
  (let [bitplanes (decompose-history history)]
    (mapv #(analyze-bitplane (nth bitplanes %) %) (range 8))))

(defn best-bitplane-domain
  "Find the bitplane with the highest domain fraction.
   This is the primary bitplane-level signal for Class IV detection."
  [bitplane-analyses]
  (apply max-key :domain-fraction bitplane-analyses))

(defn aggregate-bitplane-metrics
  "Aggregate bitplane analyses into summary metrics.
   Returns metrics that can feed into the Wolfram class estimator."
  [bitplane-analyses]
  (let [best (best-bitplane-domain bitplane-analyses)
        domain-fracs (mapv :domain-fraction bitplane-analyses)
        cvs (mapv :compression-cv bitplane-analyses)
        changes (mapv :mean-change bitplane-analyses)
        mean-domain (/ (reduce + 0.0 domain-fracs) 8.0)
        max-domain (apply max domain-fracs)
        std-domain (let [m mean-domain]
                     (Math/sqrt (/ (reduce + 0.0
                                           (map #(let [d (- % m)] (* d d))
                                                domain-fracs))
                                   8.0)))
        ;; How many bitplanes show Class IV candidate domain fraction?
        class-iv-planes (count (filter :class-iv-candidate? bitplane-analyses))
        ;; Bitplane diversity: are all bitplanes similar or different?
        activity-spread (let [m (/ (reduce + 0.0 changes) 8.0)]
                          (Math/sqrt (/ (reduce + 0.0
                                                 (map #(let [d (- % m)] (* d d))
                                                      changes))
                                        8.0)))]
    {:best-plane (:plane best)
     :best-domain-fraction (:domain-fraction best)
     :best-domain-px (:domain-px best)
     :best-domain-pt (:domain-pt best)
     :mean-domain-fraction mean-domain
     :max-domain-fraction max-domain
     :domain-fraction-std std-domain
     :best-compression-cv (:compression-cv best)
     :class-iv-plane-count class-iv-planes
     :activity-spread activity-spread
     :per-plane bitplane-analyses}))

;; =============================================================================
;; Cross-Bitplane Coupling
;; =============================================================================

(defn- log2 [x]
  (if (pos? x) (/ (Math/log (double x)) (Math/log 2.0)) 0.0))

(defn pairwise-mi
  "Compute mutual information between two binary bitplane histories.
   MI(A;B) = H(A) + H(B) - H(A,B) averaged over all spacetime points.
   Returns MI in bits."
  [bitplane-a bitplane-b]
  (let [n (count bitplane-a)
        width (count (first bitplane-a))
        ;; Count joint occurrences across all spacetime points
        joint-freq (reduce
                    (fn [freq t]
                      (reduce
                       (fn [freq x]
                         (let [a (nth (nth bitplane-a t) x)
                               b (nth (nth bitplane-b t) x)
                               key [a b]]
                           (update freq key (fnil inc 0))))
                       freq
                       (range width)))
                    {}
                    (range n))
        total (double (reduce + (vals joint-freq)))
        ;; Marginals
        margin-a (reduce-kv (fn [acc [a _] c] (update acc a (fnil + 0) c)) {} joint-freq)
        margin-b (reduce-kv (fn [acc [_ b] c] (update acc b (fnil + 0) c)) {} joint-freq)
        ;; H(A), H(B), H(A,B)
        h-fn (fn [freq]
               (- (reduce-kv (fn [acc _ c]
                               (let [p (/ (double c) total)]
                                 (+ acc (* p (log2 p)))))
                             0.0 freq)))
        h-a (h-fn margin-a)
        h-b (h-fn margin-b)
        h-ab (h-fn joint-freq)]
    (max 0.0 (- (+ h-a h-b) h-ab))))

(defn coupling-matrix
  "Compute pairwise MI between all 8 bitplanes.
   Returns an 8x8 matrix where [i][j] = MI(bitplane_i, bitplane_j)."
  [bitplanes]
  (let [n 8]
    (mapv (fn [i]
            (mapv (fn [j]
                    (if (= i j)
                      1.0  ;; Self-information (H(X))
                      (pairwise-mi (nth bitplanes i) (nth bitplanes j))))
                  (range n)))
          (range n))))

(defn temporal-coupling-profile
  "Measure how coupling evolves over time.
   Computes pairwise MI in sliding windows across the history.
   Returns a vector of {:t int :mean-coupling double :max-coupling double}."
  [bitplanes {:keys [window-size stride]
              :or {window-size 20 stride 10}}]
  (let [n (count (first bitplanes))
        width (count (first (first bitplanes)))]
    (loop [t 0
           profile []]
      (if (> (+ t window-size) n)
        profile
        (let [;; Extract window for each bitplane
              windowed (mapv (fn [bp] (subvec (vec bp) t (+ t window-size)))
                             bitplanes)
              ;; Compute mean pairwise MI (upper triangle only)
              pairs (for [i (range 8)
                          j (range (inc i) 8)]
                      (pairwise-mi (nth windowed i) (nth windowed j)))
              mean-mi (if (seq pairs)
                        (/ (reduce + 0.0 pairs) (count pairs))
                        0.0)
              max-mi (if (seq pairs) (apply max pairs) 0.0)]
          (recur (+ t stride)
                 (conj profile {:t (+ t (quot window-size 2))
                                :mean-coupling mean-mi
                                :max-coupling max-mi})))))))

(defn spatial-coupling-profile
  "Measure where in the lattice bitplane coupling is strongest.
   Computes per-cell pairwise MI across all bitplane pairs.
   Returns {:per-cell [double] :mean double :max double :hotspot-fraction double}."
  [bitplanes & [{:keys [sample-pairs] :or {sample-pairs 10}}]]
  (let [n (count (first bitplanes))
        width (count (first (first bitplanes)))
        ;; Select bitplane pairs to measure (all 28 pairs for 8 bits)
        pairs (for [i (range 8) j (range (inc i) 8)] [i j])
        ;; For each cell x, compute mean MI across pairs using the full time series
        per-cell
        (mapv (fn [x]
                (let [pair-mis
                      (mapv (fn [[i j]]
                              (let [;; Extract column x from each bitplane across time
                                    col-i (mapv #(nth (nth (nth bitplanes i) %) x) (range n))
                                    col-j (mapv #(nth (nth (nth bitplanes j) %) x) (range n))
                                    ;; Joint frequencies for this column pair
                                    joint (frequencies (map vector col-i col-j))
                                    total (double n)
                                    margin-a (frequencies col-i)
                                    margin-b (frequencies col-j)
                                    h-fn (fn [freq]
                                           (- (reduce-kv
                                               (fn [a _ c]
                                                 (let [p (/ (double c) total)]
                                                   (+ a (* p (log2 p)))))
                                               0.0 freq)))]
                                (max 0.0 (- (+ (h-fn margin-a) (h-fn margin-b))
                                            (h-fn joint)))))
                            pairs)]
                  (/ (reduce + 0.0 pair-mis) (max 1 (count pair-mis)))))
              (range width))
        mean-coupling (/ (reduce + 0.0 per-cell) (max 1 width))
        max-coupling (if (seq per-cell) (apply max per-cell) 0.0)
        ;; Hotspots: cells with coupling > 2× mean
        threshold (* 2.0 mean-coupling)
        hotspot-count (count (filter #(> % threshold) per-cell))]
    {:per-cell per-cell
     :mean mean-coupling
     :max max-coupling
     :hotspot-fraction (/ (double hotspot-count) (max 1 width))}))

;; =============================================================================
;; Coupling Spectrum
;; =============================================================================

(defn coupling-spectrum
  "Compute the full coupling spectrum for a sigil history.
   This is the key multi-scale diagnostic.

   Returns:
   {:independence-score double  -- [0,1] 1.0 = fully independent bitplanes
    :mean-coupling double       -- mean pairwise MI across all 28 pairs
    :max-coupling double        -- maximum pairwise MI
    :coupling-cv double         -- coefficient of variation of pairwise MIs
    :structured-coupling double -- [0,1] high when coupling is spatially localized
    :temporal-trend :stable/:increasing/:decreasing/:oscillating
    :summary :independent/:weakly-coupled/:structured/:uniformly-coupled}"
  [history & [{:keys [temporal? spatial?]
               :or {temporal? true spatial? true}}]]
  (let [bitplanes (decompose-history history)
        ;; Global coupling matrix
        matrix (coupling-matrix bitplanes)
        ;; Extract upper triangle (28 pairs)
        pair-mis (for [i (range 8) j (range (inc i) 8)]
                   (nth (nth matrix i) j))
        mean-mi (/ (reduce + 0.0 pair-mis) (max 1 (count pair-mis)))
        max-mi (if (seq pair-mis) (apply max pair-mis) 0.0)
        variance (/ (reduce + 0.0 (map #(let [d (- % mean-mi)] (* d d)) pair-mis))
                    (max 1 (dec (count pair-mis))))
        std-mi (Math/sqrt variance)
        coupling-cv (if (pos? mean-mi) (/ std-mi mean-mi) 0.0)

        ;; Independence score: 1 - normalized mean MI
        ;; Binary MI maxes at 1.0 (for two perfectly correlated binary variables)
        independence-score (max 0.0 (- 1.0 (* 2.0 mean-mi)))

        ;; Spatial coupling (if requested)
        spatial (when spatial?
                  (spatial-coupling-profile bitplanes))
        structured-coupling (if spatial
                              (:hotspot-fraction spatial)
                              0.0)

        ;; Temporal coupling profile (if requested)
        temporal (when temporal?
                   (temporal-coupling-profile bitplanes {}))
        temporal-trend (when (and temporal (>= (count temporal) 3))
                         (let [couplings (mapv :mean-coupling temporal)
                               first-third (/ (reduce + 0.0 (take (quot (count couplings) 3) couplings))
                                              (max 1 (quot (count couplings) 3)))
                               last-third (/ (reduce + 0.0 (take-last (quot (count couplings) 3) couplings))
                                             (max 1 (quot (count couplings) 3)))
                               diff (- last-third first-third)
                               mid-couplings (subvec couplings
                                                     (quot (count couplings) 3)
                                                     (* 2 (quot (count couplings) 3)))
                               mid-mean (/ (reduce + 0.0 mid-couplings)
                                           (max 1 (count mid-couplings)))]
                           (cond
                             (> diff (* 0.2 first-third)) :increasing
                             (< diff (* -0.2 first-third)) :decreasing
                             (and (> mid-mean (* 1.2 first-third))
                                  (> mid-mean (* 1.2 last-third))) :oscillating
                             :else :stable)))

        ;; Overall summary classification
        summary (cond
                  (< mean-mi 0.01)  :independent
                  (< mean-mi 0.05)  :weakly-coupled
                  (> structured-coupling 0.1) :structured
                  :else :uniformly-coupled)]

    (cond-> {:independence-score independence-score
             :mean-coupling mean-mi
             :max-coupling max-mi
             :coupling-cv coupling-cv
             :structured-coupling structured-coupling
             :temporal-trend (or temporal-trend :unknown)
             :summary summary
             :coupling-matrix matrix}
      spatial  (assoc :spatial-profile spatial)
      temporal (assoc :temporal-profile temporal))))

;; =============================================================================
;; Full Multi-Scale Analysis
;; =============================================================================

(defn analyze-multiscale
  "Complete multi-scale analysis of a sigil history.
   Combines bitplane-level metrics, sigil-level metrics, and coupling spectrum.

   Returns:
   {:bitplane-summary  -- aggregated per-bitplane domain/compression
    :coupling          -- coupling spectrum
    :multiscale-class-iv-score  -- [0,1] composite Class IV signal}"
  [history & [opts]]
  (let [bitplane-analyses (analyze-all-bitplanes history)
        bp-summary (aggregate-bitplane-metrics bitplane-analyses)
        coupling (coupling-spectrum history (or opts {}))
        ;; Multi-scale Class IV composite:
        ;; - Bitplane-level domain fraction (does any bitplane show ether?)
        ;; - Coupling structure (are bitplanes interacting in interesting ways?)
        ;; - Compression variance across bitplanes
        best-df (:best-domain-fraction bp-summary)
        iv-planes (:class-iv-plane-count bp-summary)
        coupling-interesting? (and (> (:mean-coupling coupling) 0.02)
                                   (< (:mean-coupling coupling) 0.4))
        structured? (> (:structured-coupling coupling) 0.05)
        ;; Score: high when bitplane-level structure + interesting coupling
        bp-domain-signal (cond
                           (and (>= best-df 0.5) (<= best-df 0.95)) 1.0
                           (>= best-df 0.3) 0.5
                           :else 0.0)
        coupling-signal (cond
                          structured? 0.8
                          coupling-interesting? 0.5
                          :else 0.0)
        composite (* 0.5 (+ (* 0.6 bp-domain-signal)
                             (* 0.4 coupling-signal)))]
    {:bitplane-summary bp-summary
     :coupling coupling
     :multiscale-class-iv-score composite}))
