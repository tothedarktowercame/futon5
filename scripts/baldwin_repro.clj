(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.string :as str])
(import '[java.awt Color Font RenderingHints]
        '[java.awt.image BufferedImage]
        '[javax.imageio ImageIO])

(def width 60)
(def steps 120)
(def seeds (vec (range 15)))
(def output-root "data/baldwin-repro")
(def lab-root "holes/labs/M-aif-tokamak/baldwin-repro")

(def arms
  [{:id :baldwin-2014 :version "2014" :arm "baldwin"
    :source "vendor/metaca/256ca-2014-12-29-BUGGY.el"}
   {:id :blending-mutation-2014 :version "2014" :arm "blending-mutation"
    :source "vendor/metaca/256ca-2014-12-29-BUGGY.el"}
   {:id :baldwin-2015 :version "2015" :arm "baldwin"
    :source "vendor/metaca/256ca-2015-04-12.el"}])

(defn- run-file [id seed]
  (io/file output-root "runs" (format "%s-seed-%02d.edn" (name id) seed)))

(defn- task-edn [{:keys [id arm]} seed]
  (str "(:arm " (pr-str arm) " :seed " seed
       " :width " width " :steps " steps
       " :path " (pr-str (.getAbsolutePath (run-file id seed))) ")"))

(defn- command! [args]
  (let [p (-> (ProcessBuilder. ^java.util.List (mapv str args))
              (.inheritIO) (.start))
        exit (.waitFor p)]
    (when-not (zero? exit)
      (throw (ex-info "command failed" {:args args :exit exit})))))

(defn- run-version! [version source version-arms]
  (let [missing (vec (for [arm version-arms seed seeds
                           :when (not (.isFile (run-file (:id arm) seed)))]
                       [arm seed]))]
    (println version "cached" (- (* (count version-arms) (count seeds))
                                  (count missing))
             "missing" (count missing))
    (when (seq missing)
      (let [task-file (java.io.File/createTempFile "baldwin-repro-" ".el")]
        (try
          (spit task-file
                (str "(" (str/join "\n" (map (fn [[arm seed]]
                                                (task-edn arm seed))
                                              missing)) ")\n"))
          (command! ["emacs" "--batch" "-Q"
                     "-l" "scripts/elisp-harness/clcompat.el"
                     "-l" "scripts/baldwin_repro_worker.el"
                     "--eval" (str "(baldwin-repro-run-batch "
                                   (pr-str version) " "
                                   (pr-str (.getAbsolutePath (io/file source))) " "
                                   (pr-str (.getAbsolutePath task-file)) ")")])
          (finally (.delete task-file)))))))

(defn- plist-map [x] (apply hash-map x))

(defn- read-run [arm seed]
  (let [x (-> (run-file (:id arm) seed) slurp edn/read-string plist-map)]
    (-> x
        (update :protocol plist-map)
        (update :genotype #(mapv vec %))
        (update :phenotype vec)
        (update :phenotype-activity vec)
        (update :genotype-activity vec)
        (update :requested-mutation-hist
                #(into (sorted-map) (map (fn [[k v]] [k v]) %)))
        (update :written-position-hist
                #(into (sorted-map) (map (fn [[k v]] [k v]) %)))
        (assoc :id (:id arm)))))

(defn- last-active [activity]
  (or (last (keep-indexed (fn [i n] (when (pos? n) (inc i))) activity)) 0))

(defn- mean [xs] (/ (double (reduce + xs)) (count xs)))

(defn- run-summary [run]
  (let [tail-rules (set (mapcat identity (take-last 20 (:genotype run))))
        phe-activity (:phenotype-activity run)
        total-cells (* width steps)]
    {:id (:id run) :seed (:seed run)
     :phenotype-death (last-active phe-activity)
     :phenotype-terminal-zero? (every? zero? (take-last 20 phe-activity))
     :tail-rule-set (vec (sort tail-rules))
     :tail-42-170-only? (= #{"00101010" "10101010"} tail-rules)
     :terminal-rule-set
     (vec (sort (map #(Integer/parseInt % 2) (distinct (last (:genotype run))))))
     :terminal-rule-count (count (distinct (last (:genotype run))))
     :terminal-genotype-activity (last (:genotype-activity run))
     :requested-mutation-hist (:requested-mutation-hist run)
     :written-position-hist (:written-position-hist run)
     :mutation-call-fraction (/ (:mutation-calls run) (double total-cells))
     :positive-mutation-call-fraction
     (/ (:positive-mutation-calls run) (double total-cells))
     :changed-mutation-call-fraction
     (/ (:changed-mutation-calls run) (double total-cells))
     :mutation-steps-per-cell (/ (:mutation-steps run) (double total-cells))}))

(defn- merge-hists [maps]
  (apply merge-with + maps))

(defn- arm-summary [id summaries]
  (let [rs (filter #(= id (:id %)) summaries)]
    {:seeds (mapv :seed rs)
     :figure8-pair-count (count (filter :tail-42-170-only? rs))
     :terminal-zero-count (count (filter :phenotype-terminal-zero? rs))
     :phenotype-deaths (mapv :phenotype-death rs)
     :terminal-rule-sets (mapv :terminal-rule-set rs)
     :terminal-rule-counts (mapv :terminal-rule-count rs)
     :terminal-genotype-activities (mapv :terminal-genotype-activity rs)
     :requested-mutation-hist (merge-hists (map :requested-mutation-hist rs))
     :written-position-hist (merge-hists (map :written-position-hist rs))
     :mean-mutation-call-fraction (mean (map :mutation-call-fraction rs))
     :mean-positive-mutation-call-fraction
     (mean (map :positive-mutation-call-fraction rs))
     :mean-changed-mutation-call-fraction
     (mean (map :changed-mutation-call-fraction rs))
     :mean-mutation-steps-per-cell (mean (map :mutation-steps-per-cell rs))}))

(defn- grayscale [bits]
  (let [v (Integer/parseInt bits 2)] (Color. v v v)))

(defn- render-arm! [arm runs]
  (let [scale 2
        panel-w (* scale width)
        panel-h (* scale (inc steps))
        header 42 gap 22 footer 18
        image (BufferedImage. (* panel-w (count runs))
                              (+ header panel-h gap panel-h footer)
                              BufferedImage/TYPE_INT_RGB)
        g (.createGraphics image)
        path (str lab-root "/" (name (:id arm)) "-genotype-phenotype.png")]
    (.setColor g Color/WHITE)
    (.fillRect g 0 0 (.getWidth image) (.getHeight image))
    (.setFont g (Font. Font/MONOSPACED Font/PLAIN 12))
    (.setRenderingHint g RenderingHints/KEY_ANTIALIASING
                       RenderingHints/VALUE_ANTIALIAS_ON)
    (doseq [[column run] (map-indexed vector runs)]
      (let [x0 (* column panel-w)]
        (.setColor g Color/BLACK)
        (.drawString g (format "seed %02d" (:seed run)) (+ x0 4) 16)
        (.drawString g "GEN" (+ x0 4) 34)
        (doseq [t (range (inc steps)) x (range width)]
          (.setColor g (grayscale (get-in run [:genotype t x])))
          (.fillRect g (+ x0 (* scale x)) (+ header (* scale t)) scale scale))
        (.setColor g Color/BLACK)
        (.drawString g "PHE" (+ x0 4) (+ header panel-h 16))
        (doseq [t (range (inc steps)) x (range width)]
          (.setColor g (if (= \1 (nth (nth (:phenotype run) t) x))
                         Color/BLACK Color/WHITE))
          (.fillRect g (+ x0 (* scale x))
                     (+ header panel-h gap (* scale t)) scale scale))))
    (.dispose g)
    (io/make-parents path)
    (ImageIO/write image "png" (io/file path))
    path))

(doseq [[version version-arms] (group-by :version arms)]
  (run-version! version (:source (first version-arms)) version-arms))

(let [runs (vec (for [arm arms seed seeds] (read-run arm seed)))
      summaries (mapv run-summary runs)
      result {:generated-at (str (java.time.Instant/now))
              :protocol {:width width :steps steps :recorded-rows (inc steps)
                         :seeds seeds
                         :co-evolution-order :phenotype-then-contextual-genotype}
              :arms (into {} (map (fn [arm]
                                    [(:id arm) (arm-summary (:id arm) summaries)])
                                  arms))
              :runs summaries}]
  (io/make-parents (str output-root "/summary.edn"))
  (spit (str output-root "/summary.edn") (pr-str result))
  (doseq [arm arms]
    (println "rendered" (render-arm! arm (filterv #(= (:id arm) (:id %)) runs))))
  (prn (:arms result)))
