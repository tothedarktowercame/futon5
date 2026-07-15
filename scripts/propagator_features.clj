(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.pprint :as pprint]
         '[clojure.string :as str])
(import '[java.awt BasicStroke Color Font RenderingHints]
        '[java.awt.image BufferedImage]
        '[java.util Random]
        '[java.util.zip GZIPInputStream]
        '[javax.imageio ImageIO]
        '[org.apache.commons.math3.linear Array2DRowRealMatrix
          EigenDecomposition])

(def fingerprint "ac2ff1681eae5b85")
(def expected-orbits 20256)
(def artifact-dir (io/file "data/propagator-index/artifacts" fingerprint))
(def output-dir (io/file "holes/labs/M-aif-tokamak/propagator-clusters"))
(def width 60.0)

(def feature-names
  [:death-mean :death-range :terminal-rules-mean :phenotype-activity-per-cell
   :entropy-early :entropy-middle :entropy-late :entropy-terminal
   :top1-terminal :top4-terminal :active-rules-terminal :terminal-rule-flux
   :class4-early :class4-middle :class4-late :class4-terminal :class4-peak
   :entropy-delta])

(defn mean [xs] (if (seq xs) (/ (double (reduce + xs)) (count xs)) 0.0))
(defn sq [x] (* x x))
(defn euclidean [a b] (Math/sqrt (reduce + (map #(sq (- %1 %2)) a b))))
(defn window [xs lo hi] (subvec (vec xs) lo (min hi (count xs))))

(defn read-gzip-edn [file]
  (with-open [in (GZIPInputStream. (io/input-stream file))
              reader (java.io.PushbackReader. (io/reader in))]
    (edn/read reader)))

(defn distribution [counts]
  (let [total (double (reduce + counts))]
    (if (pos? total) (mapv #(/ % total) counts) (vec (repeat 256 0.0)))))

(defn entropy [counts]
  (let [ps (distribution counts)]
    (/ (- (reduce + (for [p ps :when (pos? p)] (* p (Math/log p)))))
       (Math/log 256.0))))

(defn top-mass [counts k]
  (reduce + (take k (sort > (distribution counts)))))

(defn active-rules [counts] (count (filter pos? counts)))

(defn total-variation [a b]
  (* 0.5 (reduce + (map #(Math/abs (- %1 %2))
                        (distribution a) (distribution b)))))

(defn run-features [{:keys [death rules activity class-4 census]}]
  (let [entropies (mapv entropy census)
        terminal (peek census)]
    {:death death
     :rules rules
     :activity-per-cell (/ activity (* 120.0 width))
     :entropy-early (mean (window entropies 0 40))
     :entropy-middle (mean (window entropies 40 80))
     :entropy-late (mean (window entropies 80 121))
     :entropy-terminal (peek entropies)
     :top1-terminal (top-mass terminal 1)
     :top4-terminal (top-mass terminal 4)
     :active-rules-terminal (active-rules terminal)
     :terminal-rule-flux (total-variation (nth census (- (count census) 2)) terminal)
     :class4-early (/ (mean (window class-4 0 40)) width)
     :class4-middle (/ (mean (window class-4 40 80)) width)
     :class4-late (/ (mean (window class-4 80 121)) width)
     :class4-terminal (/ (double (peek class-4)) width)
     :class4-peak (/ (double (apply max class-4)) width)}))

(defn artifact-row [file]
  (let [{:keys [status sigma runs] :as artifact} (read-gzip-edn file)
        artifact-fingerprint (:fingerprint artifact)]
    (when-not (and (= :complete status) (= fingerprint artifact-fingerprint))
      (throw (ex-info "artifact identity mismatch"
                      {:file (str file) :status status :fingerprint artifact-fingerprint})))
    (let [rf (mapv run-features runs)
          deaths (mapv :death rf)
          early (mean (map :entropy-early rf))
          late (mean (map :entropy-late rf))
          features {:death-mean (mean deaths)
                    :death-range (- (apply max deaths) (apply min deaths))
                    :terminal-rules-mean (mean (map :rules rf))
                    :phenotype-activity-per-cell (mean (map :activity-per-cell rf))
                    :entropy-early early
                    :entropy-middle (mean (map :entropy-middle rf))
                    :entropy-late late
                    :entropy-terminal (mean (map :entropy-terminal rf))
                    :top1-terminal (mean (map :top1-terminal rf))
                    :top4-terminal (mean (map :top4-terminal rf))
                    :active-rules-terminal (mean (map :active-rules-terminal rf))
                    :terminal-rule-flux (mean (map :terminal-rule-flux rf))
                    :class4-early (mean (map :class4-early rf))
                    :class4-middle (mean (map :class4-middle rf))
                    :class4-late (mean (map :class4-late rf))
                    :class4-terminal (mean (map :class4-terminal rf))
                    :class4-peak (mean (map :class4-peak rf))
                    :entropy-delta (- late early)}]
      {:sigma sigma
       :source (.getName file)
       :deaths deaths
       :terminal-rules (mapv :rules rf)
       :features features})))

(defn atomic-edn! [file value]
  (io/make-parents file)
  (let [tmp (io/file (.getParentFile file) (str "." (.getName file) ".tmp"))]
    (spit tmp (with-out-str (pprint/pprint value)))
    (java.nio.file.Files/move (.toPath tmp) (.toPath file)
                              (into-array java.nio.file.StandardCopyOption
                                          [java.nio.file.StandardCopyOption/REPLACE_EXISTING
                                           java.nio.file.StandardCopyOption/ATOMIC_MOVE]))))

(defn feature-csv [rows]
  (let [header (concat ["sigma" "death-seeds" "terminal-rules-seeds" "cluster" "pc1" "pc2"]
                       (map name feature-names))]
    (str (str/join "," header) "\n"
         (str/join
          "\n"
          (for [{:keys [sigma deaths terminal-rules cluster pc features]} rows]
            (str/join ","
                      (concat [(apply str sigma)
                               (str/join "|" deaths)
                               (str/join "|" terminal-rules)
                               cluster
                               (format "%.9g" (double (first pc)))
                               (format "%.9g" (double (second pc)))]
                              (map #(format "%.9g" (double (get features %)))
                                   feature-names)))))
         "\n")))

(defn atomic-text! [file text]
  (io/make-parents file)
  (let [tmp (io/file (.getParentFile file) (str "." (.getName file) ".tmp"))]
    (spit tmp text)
    (java.nio.file.Files/move (.toPath tmp) (.toPath file)
                              (into-array java.nio.file.StandardCopyOption
                                          [java.nio.file.StandardCopyOption/REPLACE_EXISTING
                                           java.nio.file.StandardCopyOption/ATOMIC_MOVE]))))

(defn extract-rows! [files checkpoint]
  (loop [remaining files rows []]
    (if-let [file (first remaining)]
      (let [rows' (conj rows (artifact-row file))]
        (when (zero? (mod (count rows') 25))
          (atomic-edn! checkpoint {:status :extracting :coverage (count rows') :rows rows'})
          (println "features" (count rows') "/" (count files))
          (flush))
        (recur (next remaining) rows'))
      rows)))

(defn standardize [rows]
  (let [matrix (mapv #(mapv (fn [k] (double (get-in % [:features k]))) feature-names) rows)
        columns (apply mapv vector matrix)
        means (mapv mean columns)
        sds (mapv (fn [xs mu]
                    (let [sd (Math/sqrt (mean (map #(sq (- % mu)) xs)))]
                      (if (< sd 1.0e-12) 1.0 sd))) columns means)]
    {:points (mapv (fn [row] (mapv #(/ (- %1 %2) %3) row means sds)) matrix)
     :means means :sds sds}))

(defn nearest-center [point centers]
  (first (apply min-key second
                (map-indexed (fn [idx center] [idx (euclidean point center)]) centers))))

(defn choose-centers [points k seed]
  (let [rng (Random. seed)]
    (loop [centers [(nth points (.nextInt rng (count points)))]]
      (if (= k (count centers)) centers
          (let [weights (mapv (fn [p] (apply min (map #(sq (euclidean p %)) centers))) points)
                total (reduce + weights)
                target (* (.nextDouble rng) total)
                idx (loop [i 0 acc 0.0]
                      (if (or (= i (dec (count points))) (>= (+ acc (nth weights i)) target))
                        i (recur (inc i) (+ acc (nth weights i)))))]
            (recur (conj centers (nth points idx))))))))

(defn kmeans [points k seed]
  (loop [centers (choose-centers points k seed) iteration 0]
    (let [assignments (mapv #(nearest-center % centers) points)
          new-centers (mapv (fn [cluster]
                              (let [members (keep-indexed #(when (= cluster %2) (nth points %1)) assignments)]
                                (if (seq members) (mapv mean (apply map vector members))
                                    (nth centers cluster))))
                            (range k))]
      (if (or (= centers new-centers) (= iteration 100))
        {:centers new-centers :assignments assignments
         :inertia (reduce + (map #(sq (euclidean %1 (nth new-centers %2))) points assignments))}
        (recur new-centers (inc iteration))))))

(defn silhouette [points assignments k]
  (mean
   (for [i (range (count points))
         :let [own (nth assignments i)
               distances (for [j (range (count points)) :when (not= i j)]
                           [(nth assignments j) (euclidean (nth points i) (nth points j))])
               a (mean (map second (filter #(= own (first %)) distances)))
               bs (for [cluster (range k) :when (not= own cluster)
                        :let [ds (map second (filter #(= cluster (first %)) distances))]
                        :when (seq ds)] (mean ds))
               b (if (seq bs) (apply min bs) 0.0)]]
     (if (and (pos? a) (pos? b)) (/ (- b a) (max a b)) 0.0))))

(defn adjusted-rand [a b]
  (let [n (count a) choose2 #(/ (* % (dec %)) 2.0)
        cells (vals (frequencies (map vector a b)))
        as (vals (frequencies a)) bs (vals (frequencies b))
        nij (reduce + (map choose2 cells))
        ai (reduce + (map choose2 as)) bj (reduce + (map choose2 bs))
        total (choose2 n) expected (/ (* ai bj) total)
        denom (- (* 0.5 (+ ai bj)) expected)]
    (if (zero? denom) 1.0 (/ (- nij expected) denom))))

(defn silhouette-sample [points assignments k]
  (let [n (count points) sample-size (min 1000 n)
        indexes (mapv #(min (dec n) (long (Math/floor (* % (/ n (double sample-size))))))
                      (range sample-size))]
    {:sample-size sample-size
     :value (silhouette (mapv #(nth points %) indexes)
                        (mapv #(nth assignments %) indexes) k)}))

(defn select-clusters [points]
  (let [max-k (min 10 (dec (count points)))
        curve (mapv (fn [k]
                      (let [fits (mapv #(kmeans points k (+ 4100 (* 97 k) %)) (range 5))
                            best (apply min-key :inertia fits)
                            sampled (silhouette-sample points (:assignments best) k)]
                        {:k k :silhouette (:value sampled)
                         :silhouette-sample-size (:sample-size sampled)
                         :inertia (:inertia best)
                         :stability (mean (map #(adjusted-rand (:assignments best) (:assignments %)) fits))
                         :fit best}))
                    (range 2 (inc max-k)))
        chosen (last (sort-by (juxt :silhouette (comp - :k)) curve))]
    {:curve (mapv #(dissoc % :fit) curve)
     :chosen-k (:k chosen) :fit (:fit chosen)
     :strength (cond (>= (:silhouette chosen) 0.5) :strong
                     (>= (:silhouette chosen) 0.25) :weak
                     :else :unseparated)}))

(defn pca2 [points]
  (let [n (count points) d (count (first points))
        covariance (vec (for [i (range d)]
                          (vec (for [j (range d)]
                                 (/ (reduce + (map #(* (nth % i) (nth % j)) points))
                                    (max 1 (dec n)))))))
        eig (EigenDecomposition. (Array2DRowRealMatrix. (into-array (map double-array covariance))))
        order (take 2 (sort-by #(- (.getRealEigenvalue eig %)) (range d)))
        axes (mapv #(vec (.toArray (.getEigenvector eig %))) order)]
    {:eigenvalues (mapv #(.getRealEigenvalue eig %) order)
     :coordinates (mapv (fn [p] (mapv #(reduce + (map * p %)) axes)) points)}))

(defn cluster-medoid [points indexes]
  (apply min-key (fn [i] (reduce + (map #(euclidean (nth points i) (nth points %)) indexes))) indexes))

(defn exemplars [points coords assignments k]
  (let [base (mapcat (fn [cluster]
                       (let [idxs (vec (keep-indexed #(when (= cluster %2) %1) assignments))
                             medoid (cluster-medoid points idxs)
                             extremes (for [axis (range 2) direction [min-key max-key]]
                                        (apply direction #(nth (nth coords %) axis) idxs))]
                         (distinct (cons medoid extremes))))
                     (range k))]
    (vec (take 40 (distinct base)))))

(def cluster-colors
  [(Color. 31 119 180) (Color. 255 127 14) (Color. 44 160 44)
   (Color. 214 39 40) (Color. 148 103 189) (Color. 140 86 75)
   (Color. 227 119 194) (Color. 127 127 127) (Color. 188 189 34)
   (Color. 23 190 207)])

(defn render-map! [file coords assignments coverage chosen-k strength]
  (let [w 1200 h 850 margin 80 image (BufferedImage. w h BufferedImage/TYPE_INT_RGB)
        g (.createGraphics image) xs (map first coords) ys (map second coords)
        scale (fn [v lo hi a b] (+ a (* (/ (- v lo) (max 1.0e-9 (- hi lo))) (- b a))))]
    (.setColor g Color/WHITE) (.fillRect g 0 0 w h)
    (.setRenderingHint g RenderingHints/KEY_ANTIALIASING RenderingHints/VALUE_ANTIALIAS_ON)
    (.setColor g Color/BLACK) (.setFont g (Font. Font/SANS_SERIF Font/BOLD 20))
    (.drawString g (format "Propagator census feature map — %d / %,d orbits" coverage expected-orbits) margin 32)
    (.setFont g (Font. Font/SANS_SERIF Font/PLAIN 14))
    (.drawString g (format "PCA projection; k=%d selected by silhouette; separation=%s; no EoC labels" chosen-k (name strength)) margin 56)
    (.setStroke g (BasicStroke. 1.0))
    (doseq [[idx [x y]] (map-indexed vector coords)]
      (.setColor g (nth cluster-colors (nth assignments idx)))
      (.fillOval g (int (- (scale x (apply min xs) (apply max xs) margin (- w margin)) 3))
                 (int (- (scale y (apply min ys) (apply max ys) (- h margin) margin) 3)) 6 6))
    (.setColor g Color/BLACK)
    (.drawString g "PC1" (- w margin 20) (- h 28)) (.drawString g "PC2" 20 margin)
    (.dispose g) (io/make-parents file) (ImageIO/write image "png" (io/file file))))

(let [files (->> (.listFiles artifact-dir)
                 (filter #(str/ends-with? (.getName %) ".edn.gz"))
                 (sort-by #(.getName %)) vec)
      coverage (count files)
      checkpoint (io/file output-dir "features.partial.edn")]
  (when (< coverage 3) (throw (ex-info "too few completed artifacts" {:coverage coverage})))
  (println "snapshot coverage" coverage "/" expected-orbits "fingerprint" fingerprint) (flush)
  (let [rows (extract-rows! files checkpoint)
        {:keys [points means sds]} (standardize rows)
        {:keys [curve chosen-k fit strength]} (select-clusters points)
        {:keys [coordinates eigenvalues]} (pca2 points)
        assignments (:assignments fit)
        sizes (frequencies assignments)
        chosen (exemplars points coordinates assignments chosen-k)
        rows' (mapv (fn [idx row]
                      (assoc row :cluster (nth assignments idx)
                             :pc (nth coordinates idx))) (range coverage) rows)
        selection (mapv (fn [idx]
                          {:sigma (:sigma (nth rows idx))
                           :cluster (nth assignments idx)
                           :cluster-size (get sizes (nth assignments idx))
                           :role (if (= idx (cluster-medoid points
                                                           (vec (keep-indexed #(when (= (nth assignments idx) %2) %1) assignments))))
                                   :medoid :pc-extreme)}) chosen)
        metadata {:fingerprint fingerprint :coverage coverage :expected expected-orbits
                  :feature-names feature-names :standardization {:means means :sds sds}
                  :cluster-selection {:chosen-k chosen-k :strength strength :curve curve}
                  :pca-eigenvalues eigenvalues}]
    (atomic-text! (io/file output-dir "features.csv") (feature-csv rows'))
    (atomic-edn! (io/file output-dir "cluster-selection.edn")
                 {:metadata metadata :cluster-sizes (into (sorted-map) sizes)
                  :render-selection selection
                  :render-status :blocked-no-spatial-histories})
    (render-map! (io/file output-dir "cluster-map.png") coordinates assignments coverage chosen-k strength)
    (.delete checkpoint)
    (println "selected k" chosen-k "strength" strength "curve"
             (mapv #(select-keys % [:k :silhouette :stability]) curve))
    (println "wrote" coverage "rows and" (count selection) "render selections")
    (flush)))
