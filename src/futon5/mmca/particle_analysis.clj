(ns futon5.mmca.particle-analysis
  "Particle/glider detection in MMCA histories.

   Given a periodic background domain (from domain-analysis), finds cells
   that deviate from the background ('defects'), groups them into connected
   components, and tracks these components across time to identify particles.

   A particle is a connected defect region that persists and moves at a
   consistent velocity. A catalog of particle species is a strong signal
   of Wolfram Class IV dynamics."
  (:require [clojure.set :as set]
            [futon5.mmca.domain-analysis :as domain]))

;; ---------------------------------------------------------------------------
;; Defect detection
;; ---------------------------------------------------------------------------

(defn defect-mask
  "Compute boolean mask of cells that deviate from the domain tile.
   Returns a vector of vectors of booleans (true = defect, false = background).
   If no domain result is provided, runs domain analysis first."
  [history domain-result]
  (if (and (:tile domain-result) (:px domain-result))
    (let [bg-mask (domain/domain-mask history domain-result)]
      ;; domain-mask returns true for matches; we want true for defects
      (mapv (fn [row] (mapv not row)) bg-mask))
    ;; No domain → everything is a defect
    (let [width (count (first history))]
      (vec (repeat (count history) (vec (repeat width true)))))))

;; ---------------------------------------------------------------------------
;; Connected components (1D per-generation)
;; ---------------------------------------------------------------------------

(defn find-defect-components
  "Find connected components of defects in a single generation's defect row.
   Adjacent defect cells (within distance 1) are grouped.
   Returns a vector of {:cells #{x-positions} :centroid double :width int :min-x int :max-x int}."
  [defect-row]
  (let [n (count defect-row)]
    (loop [x 0
           current nil
           components []]
      (if (>= x n)
        ;; Finish last component
        (if current
          (let [cells (:cells current)
                min-x (apply min cells)
                max-x (apply max cells)
                centroid (/ (reduce + 0.0 cells) (count cells))]
            (conj components (assoc current
                                    :centroid centroid
                                    :width (inc (- max-x min-x))
                                    :min-x min-x
                                    :max-x max-x)))
          components)
        (if (nth defect-row x)
          ;; Defect cell
          (if current
            (recur (inc x)
                   (update current :cells conj x)
                   components)
            (recur (inc x)
                   {:cells #{x}}
                   components))
          ;; Background cell — close current component if any
          (if current
            (let [cells (:cells current)
                  min-x (apply min cells)
                  max-x (apply max cells)
                  centroid (/ (reduce + 0.0 cells) (count cells))]
              (recur (inc x)
                     nil
                     (conj components (assoc current
                                             :centroid centroid
                                             :width (inc (- max-x min-x))
                                             :min-x min-x
                                             :max-x max-x))))
            (recur (inc x) nil components)))))))

;; ---------------------------------------------------------------------------
;; Particle tracking
;; ---------------------------------------------------------------------------

(defn- shifted-overlap
  "Compute Jaccard overlap between component A's cells shifted by `shift` and
   component B's cells."
  [comp-a comp-b shift]
  (let [shifted-a (set (map #(+ % shift) (:cells comp-a)))
        cells-b (:cells comp-b)
        intersection (count (set/intersection shifted-a cells-b))
        union (count (set/union shifted-a cells-b))]
    (if (pos? union)
      (/ (double intersection) (double union))
      0.0)))

(defn- best-match
  "Find the best matching component in next-components for the given component,
   trying shifts from -max-shift to +max-shift.
   Returns {:component comp :shift int :overlap double} or nil."
  [comp next-components max-shift]
  (let [candidates
        (for [shift (range (- max-shift) (inc max-shift))
              next-comp next-components
              :let [overlap (shifted-overlap comp next-comp shift)]
              :when (> overlap 0.05)]
          {:component next-comp :shift shift :overlap overlap})]
    (when (seq candidates)
      (apply max-key :overlap candidates))))

(defn track-particles
  "Track defect components across consecutive generations.
   Returns a vector of particle tracks, each:
   {:id int
    :positions [{:t int :centroid double :width int :cells #{...}} ...]
    :velocity double  -- mean spatial shift per generation
    :lifetime int     -- number of generations the particle persists}"
  [history domain-result & [{:keys [max-shift min-lifetime]
                              :or {max-shift 3 min-lifetime 3}}]]
  (let [mask (defect-mask history domain-result)
        n (count mask)
        ;; Get components per generation
        per-gen (mapv find-defect-components mask)
        ;; Track across generations
        tracks (atom [])
        active (atom {}) ;; component-id → track-so-far
        next-id (atom 0)]
    ;; Process each generation
    (doseq [t (range n)]
      (let [components (nth per-gen t)
            ;; Try to match each active track to a component
            matched-comps (atom #{})
            new-active (atom {})]
        ;; For each active track, find best match
        (doseq [[track-id track] @active]
          (let [last-pos (peek (:positions track))
                last-comp {:cells (:cells last-pos)
                           :centroid (:centroid last-pos)}
                match (best-match last-comp
                                  (remove #(contains? @matched-comps %) components)
                                  max-shift)]
            (if match
              (do
                (swap! matched-comps conj (:component match))
                (swap! new-active assoc track-id
                       (update track :positions conj
                               (assoc (:component match) :t t :shift (:shift match)))))
              ;; Track ended — save if long enough
              (when (>= (count (:positions track)) min-lifetime)
                (swap! tracks conj track)))))
        ;; Start new tracks for unmatched components
        (doseq [comp components
                :when (not (contains? @matched-comps comp))]
          (let [id (swap! next-id inc)]
            (swap! new-active assoc id
                   {:id id
                    :positions [(assoc comp :t t)]})))
        (reset! active @new-active)))
    ;; Finalize remaining active tracks
    (doseq [[_ track] @active]
      (when (>= (count (:positions track)) min-lifetime)
        (swap! tracks conj track)))
    ;; Compute velocities
    (mapv (fn [track]
            (let [positions (:positions track)
                  shifts (keep :shift (rest positions))
                  velocity (if (seq shifts)
                             (/ (reduce + 0.0 shifts) (count shifts))
                             0.0)]
              (assoc track
                     :velocity velocity
                     :lifetime (count positions))))
          @tracks)))

;; ---------------------------------------------------------------------------
;; Species classification
;; ---------------------------------------------------------------------------

(defn- velocity-bucket
  "Bucket velocity into discrete classes for species grouping."
  [v]
  (cond
    (< (Math/abs v) 0.2) :stationary
    (< v -0.5) :left-fast
    (< v 0.0) :left-slow
    (< v 0.5) :right-slow
    :else :right-fast))

(defn particle-catalog
  "Build a catalog of particle species from tracks.
   Groups by velocity bucket and typical width.
   Returns a vector of species descriptors."
  [tracks]
  (let [grouped (group-by (fn [t]
                            [(velocity-bucket (:velocity t))
                             (let [widths (map :width (:positions t))]
                               (when (seq widths)
                                 (int (Math/round (double (/ (reduce + 0.0 widths)
                                                             (count widths)))))))])
                          tracks)]
    (mapv (fn [[[vel-class avg-width] species-tracks]]
            {:velocity-class vel-class
             :avg-width avg-width
             :count (count species-tracks)
             :avg-velocity (/ (reduce + 0.0 (map :velocity species-tracks))
                              (count species-tracks))
             :avg-lifetime (/ (reduce + 0.0 (map :lifetime species-tracks))
                              (count species-tracks))})
          grouped)))

;; ---------------------------------------------------------------------------
;; Interaction detection
;; ---------------------------------------------------------------------------

(defn- detect-interactions
  "Count apparent collision events: when two particles are close at time t
   and fewer particles exist at time t+1 in that region, or vice versa."
  [tracks]
  (let [;; Simple heuristic: count tracks that end within 3 cells of another
        ;; track's start (suggesting a collision created/destroyed particles)
        track-ends (for [t tracks
                         :let [last-pos (peek (:positions t))]]
                     {:t (:t last-pos) :centroid (:centroid last-pos)})
        track-starts (for [t tracks
                           :let [first-pos (first (:positions t))]]
                       {:t (:t first-pos) :centroid (:centroid first-pos)})]
    (count (for [e track-ends
                 s track-starts
                 :when (and (= (:t e) (:t s))
                            (< (Math/abs (- (:centroid e) (:centroid s))) 3.0))]
             :interaction))))

;; ---------------------------------------------------------------------------
;; Main analysis
;; ---------------------------------------------------------------------------

(defn analyze-particles
  "Full particle analysis. Returns:
   {:particle-count int         -- total particles detected
    :species-count int          -- number of distinct species
    :max-lifetime int           -- longest-lived particle
    :mean-velocity double       -- average absolute particle speed
    :catalog [...]              -- species catalog
    :interaction-count int      -- number of apparent collision events
    :tracks [...]               -- raw track data (without :cells for brevity)}"
  [history & [opts]]
  (let [domain-result (domain/analyze-domain history)
        tracks (track-particles history domain-result opts)
        catalog (particle-catalog tracks)
        interactions (detect-interactions tracks)]
    {:particle-count (count tracks)
     :species-count (count catalog)
     :max-lifetime (if (seq tracks)
                     (apply max (map :lifetime tracks))
                     0)
     :mean-velocity (if (seq tracks)
                      (/ (reduce + 0.0 (map #(Math/abs (:velocity %)) tracks))
                         (count tracks))
                      0.0)
     :catalog catalog
     :interaction-count interactions
     :domain-fraction (:domain-fraction domain-result)
     :tracks (mapv #(dissoc % :positions) tracks)}))
