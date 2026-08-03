(ns exotype-pattern-slice6
  "Slice 6: pattern NEXT claims and local epistemic value. Measurements only."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.pattern-eig :as pattern]
            [futon5.mmca.render :as render]))

(def config
  {:seed-base 20260803 :seeds 60 :width 80 :steps 6000 :workers 12
   :lambda 0.55 :mu 0.1 :tau 0.3 :prevalence-radius 1
   :damage-steps 59 :checkpoints [0 120 600 1200 3000 6000]
   :arms pattern/arms})

(def raw-path "reports/exotype-pattern-slice6.raw.edn")
(def edn-path "reports/exotype-pattern-slice6.edn")
(def md-path "reports/exotype-pattern-slice6.md")

(defn initial-state [arm seed]
  (ca/with-seed seed
    (let [width (:width config)
          genotype (vec (ca/random-sigil-string width))]
      {:arm :efe-full :pattern-arm arm :seed seed :time 0
       :lambda (:lambda config) :mu (:mu config) :tau (:tau config)
       :prevalence-radius (:prevalence-radius config)
       :genotype genotype :previous-genotype genotype
       :phenotype (apply str (repeatedly width #(if (< (ca/rnd) 0.5) \0 \1)))
       :exotypes (grid/initial-grid :heterogeneous-fixed width)})))

(defn- mean [xs] (/ (reduce + 0.0 xs) (double (count xs))))
(defn- sd [xs]
  (if (< (count xs) 2) 0.0
      (let [m (mean xs)]
        (Math/sqrt (/ (reduce + 0.0 (map #(let [d (- (double %) m)] (* d d)) xs))
                      (double (dec (count xs))))))))
(defn- summary [xs]
  (let [xs (vec xs) s (sd xs)]
    {:mean (mean xs) :sd s :sem (/ s (Math/sqrt (double (count xs))))
     :n (count xs)}))
(defn- median [xs]
  (let [v (vec (sort xs)) n (count v) i (quot n 2)]
    (if (odd? n) (double (nth v i))
        (/ (+ (double (nth v (dec i))) (double (nth v i))) 2.0))))
(defn- difference [a b] (count (filter true? (map not= a b))))
(defn- counts [xs]
  (let [f (frequencies xs)]
    (into (sorted-map) (for [k grid/exotype-kinds] [k (get f k 0)]))))
(defn- entropy [cs]
  (let [n (double (reduce + (vals cs)))]
    (- (reduce + 0.0 (for [x (vals cs) :when (pos? x) :let [p (/ x n)]]
                         (* p (Math/log p)))))))
(defn- spatial [xs]
  (let [n (double (count xs))
        agree (/ (count (filter true? (map = xs (concat (rest xs) [(first xs)])))) n)
        expected (reduce + 0.0 (map #(let [p (/ % n)] (* p p))
                                    (vals (frequencies xs))))]
    (if (< expected 1.0) (/ (- agree expected) (- 1.0 expected)) 0.0)))
(defn- checkpoint [state]
  (let [cs (counts (:exotypes state))]
    {:counts cs :kind-count (count (filter pos? (vals cs)))
     :entropy (entropy cs) :spatial-autocorrelation (spatial (:exotypes state))}))

(defn stop-line
  "Within-decision ranges, never pooled spread. A term discriminates a decision
   iff at least two compared policies receive different values."
  []
  (let [states (for [seed (range (:seed-base config) (+ (:seed-base config) 8))]
                 (initial-state :next-C-plus-eig seed))
        decisions (mapcat (fn [state]
                            (map #(pattern/cell-decision :next-C-plus-eig state %)
                                 (range (:width config)))) states)]
    (into (sorted-map)
          (for [term [:risk :ambiguity :conatus :eig]
                :let [ranges (map (fn [decision]
                                    (let [values (map term (:candidates decision))]
                                      (- (apply max values) (apply min values))))
                                  decisions)
                      all-values (mapcat #(map term (:candidates %)) decisions)]]
            [term {:decisions (count decisions)
                   :fraction-discriminating
                   (/ (count (filter #(> % 1.0e-12) ranges))
                      (double (count ranges)))
                   :within-decision-range (summary ranges)
                   :candidate-median (median all-values)
                   :candidate-max (apply max all-values)
                   :median-below-max? (< (median all-values) (apply max all-values))}]))))

(defn- advance [state n] (nth (iterate pattern/step-compact state) n))
(defn- flip-phenotype [state site]
  (update state :phenotype #(apply str (assoc (vec %) site
                                              (if (= \0 (nth % site)) \1 \0)))))
(defn- flip-genotype [state site]
  (update-in state [:genotype site]
             #(let [bits (vec (ca/bits-for (str %)))]
                (ca/sigil-for (apply str (update bits 0 (fn [b] (if (= \0 b) \1 \0))))))))
(defn- flip-exotype [state site]
  (update-in state [:exotypes site]
             #(nth grid/exotype-kinds
                   (mod (inc (.indexOf grid/exotype-kinds %))
                        (count grid/exotype-kinds)))))
(defn- damage [state]
  (let [site (quot (:width config) 2) control (advance state (:damage-steps config))]
    (into (sorted-map)
          (for [[layer perturb key] [[:phenotype flip-phenotype :phenotype]
                                     [:genotype flip-genotype :genotype]
                                     [:exotype flip-exotype :exotypes]]]
            [layer (difference (key control)
                               (key (advance (perturb state site)
                                             (:damage-steps config))))]))))

(defn seed-run [arm seed]
  (let [wanted (set (:checkpoints config)) initial (initial-state arm seed)]
    (loop [state initial time 0 cps (sorted-map 0 (checkpoint initial))
           changed-steps 0 changed-cells 0 phenotype-changes 0]
      (if (= time (:steps config))
        {:checkpoints cps :changed-steps changed-steps :changed-cells changed-cells
         :phenotype-activity (/ phenotype-changes
                                (double (* (:width config) (:steps config))))
         :genotype-rule-count (count (distinct (:genotype state)))
         :damage (damage state)}
        (let [next (pattern/step-compact state)
              changed (difference (:exotypes state) (:exotypes next))]
          (recur next (inc time)
                 (if (wanted (inc time)) (assoc cps (inc time) (checkpoint next)) cps)
                 (+ changed-steps (if (pos? changed) 1 0)) (+ changed-cells changed)
                 (+ phenotype-changes
                    (difference (:phenotype state) (:phenotype next)))))))))

(defn- save-raw! [raw] (spit raw-path (str (pr-str raw) "\n")))
(defn- load-raw []
  (if (.exists (io/file raw-path)) (edn/read-string (slurp raw-path)) (sorted-map)))
(defn- run-arm! [raw arm seeds]
  (let [existing (get raw arm (sorted-map))
        missing (remove #(contains? existing %) seeds)
        pool (java.util.concurrent.Executors/newFixedThreadPool (:workers config))]
    (try
      (let [tasks (mapv (fn [seed] [seed (.submit pool ^java.util.concurrent.Callable
                                                   #(seed-run arm seed))]) missing)]
        (reduce (fn [r [seed future]]
                  (let [next (assoc-in r [arm seed]
                                       (.get ^java.util.concurrent.Future future))]
                    (save-raw! next) (println :completed arm seed) (flush) next))
                raw tasks))
      (finally (.shutdown pool)))))

(defn- checkpoint-summary [runs t]
  (let [rows (map #(get-in % [:checkpoints t]) runs)]
    {:kind-count (summary (map :kind-count rows))
     :entropy (summary (map :entropy rows))
     :spatial-autocorrelation (summary (map :spatial-autocorrelation rows))
     :counts (into (sorted-map)
                   (for [k grid/exotype-kinds]
                     [k (summary (map #(get-in % [:counts k]) rows))]))}))
(defn- arm-summary [runs]
  {:trajectory (into (sorted-map) (for [t (:checkpoints config)]
                                    [t (checkpoint-summary runs t)]))
   :changed-steps (summary (map :changed-steps runs))
   :changed-cells (summary (map :changed-cells runs))
   :phenotype-activity (summary (map :phenotype-activity runs))
   :genotype-rule-count (summary (map :genotype-rule-count runs))
   :damage (into (sorted-map) (for [k [:phenotype :genotype :exotype]]
                                [k (summary (map #(get-in % [:damage k]) runs))]))})

(def exotype-colours {:builder [54 162 235] :collapser [245 166 35]
                      :chaos [220 55 65] :identity [55 190 105]})
(defn- genotype-colour [x]
  (let [h (bit-and 255 (hash x))] [h (bit-and 255 (* 3 h)) (bit-and 255 (* 7 h))]))
(defn- render-arm! [arm]
  (let [states (take (inc (:steps config))
                     (iterate pattern/step-compact
                              (initial-state arm (:seed-base config))))
        pixels (mapv (fn [s]
                       (vec (concat (map genotype-colour (:genotype s)) [[255 255 255]]
                                    (map #(if (= % \1) [245 245 245] [15 15 15])
                                         (:phenotype s)) [[255 255 255]]
                                    (map exotype-colours (:exotypes s))))) states)
        path (str "reports/figures/slice6-" (name arm) "-triptych.png")
        ppm (str path ".ppm")]
    (io/make-parents path) (render/write-ppm! ppm pixels :comment (name arm))
    (let [{:keys [exit err]} (sh/sh "convert" ppm "-filter" "point"
                                    "-resize" "1200x1200!" "-strip" path)]
      (when-not (zero? exit) (throw (ex-info "convert failed" {:error err}))))
    (.delete (io/file ppm)) path))

(defn- determinism []
  (let [left (pr-str (seed-run :next-C-plus-eig (:seed-base config)))
        right (pr-str (seed-run :next-C-plus-eig (:seed-base config)))]
    {:byte-identical? (= left right)
     :hash (format "%08x" (bit-and 0xffffffff (hash left)))}))
(defn- fmt [x] (format "%.4f (sd %.4f; sem %.4f)" (:mean x) (:sd x) (:sem x)))
(defn markdown [result]
  (str "# Exotype patterns and local EIG — Slice 6\n\n"
       "Measurements only. N=60, width=80, horizon=6000; lambda=0.55, mu=0.1, tau=0.3.\n\n"
       "## Within-decision stop-line\n\n```clojure\n" (pr-str (:stop-line result)) "\n```\n\n"
       "## Endpoint and activity\n\n| arm | kinds | entropy | spatial | changed steps | changed cells | phenotype activity | genotype rules | P damage | G damage | X damage |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n"
       (apply str (for [[arm row] (:arms result) :let [end (get-in row [:trajectory 6000])]]
                    (format "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n"
                            (name arm) (fmt (:kind-count end)) (fmt (:entropy end))
                            (fmt (:spatial-autocorrelation end)) (fmt (:changed-steps row))
                            (fmt (:changed-cells row)) (fmt (:phenotype-activity row))
                            (fmt (:genotype-rule-count row)) (fmt (get-in row [:damage :phenotype]))
                            (fmt (get-in row [:damage :genotype])) (fmt (get-in row [:damage :exotype])))))
       "\n## Trajectories\n\n```clojure\n"
       (pr-str (into (sorted-map) (for [[arm row] (:arms result)] [arm (:trajectory row)])))
       "\n```\n\n## Modelling choices and determinism\n\n```clojure\n"
       (pr-str (select-keys result [:modelling-choices :determinism :figures]))
       "\n```\n\nNo scientific verdict is made here.\n"))

(defn -main [& _]
  (let [probe (stop-line)]
    (when-not (pos? (get-in probe [:eig :fraction-discriminating]))
      (throw (ex-info "STOP-LINE: EIG does not discriminate within decisions" probe)))
    (let [seeds (range (:seed-base config) (+ (:seed-base config) (:seeds config)))
          raw (reduce #(run-arm! %1 %2 seeds) (load-raw) (:arms config))
          result {:kind :exotype-pattern-slice6 :schema 1 :config config
                  :stop-line probe
                  :modelling-choices
                  {:next :existing-four-channel-probability-vector
                   :claim-test {:statistic :mean-channel-log-likelihood
                                :floor :log-one-half}
                   :eig :entropy-of-current-local-holder-confirmations
                   :no-cell-memory true :eig-coefficient 1.0
                   :risk :sum-bernoulli-kl-prediction-to-candidate-next
                   :eig-only-retains-conatus true
                   :forbidden-inputs [:damage :reach :band :entropy :kind-count
                                      :global-statistic]}
                  :arms (into (sorted-map) (for [arm (:arms config)]
                                             [arm (arm-summary (vals (get raw arm)))]))
                  :determinism (determinism)
                  :figures (into (sorted-map) (for [arm (:arms config)]
                                                [arm (render-arm! arm)]))}]
      (spit edn-path (str (pr-str result) "\n"))
      (spit md-path (markdown result))
      (println :wrote edn-path md-path))))

(apply -main *command-line-args*)
(shutdown-agents)
