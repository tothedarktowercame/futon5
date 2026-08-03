(ns exotype-prevalence-slice3
  "Slice 3: neighbourhood-prevalence E prior at the Slice-2b critical lambda."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.prevalence :as prevalence]
            [futon5.mmca.render :as render]))

(def config
  {:seed-base 20260803
   :seeds 100
   :width 80
   :steps 120
   :lambda 0.55
   :prevalence-radius 1
   :taus [0.0 0.01 0.03 0.1 0.3 1.0 3.0 10.0]})

(defn- initial-state [seed tau]
  (ca/with-seed seed
    (let [genotype (vec (ca/random-sigil-string (:width config)))]
      {:arm :efe-full :seed seed :time 0
       :lambda (:lambda config) :tau tau
       :prevalence-radius (:prevalence-radius config)
       :genotype genotype :previous-genotype genotype
       :phenotype (apply str
                         (repeatedly (:width config)
                                     #(if (< (ca/rnd) 0.5) \0 \1)))
       :exotypes (grid/initial-grid :heterogeneous-fixed (:width config))})))

(defn- trajectory [seed tau]
  (vec (take (inc (:steps config))
             (iterate prevalence/step (initial-state seed tau)))))

(defn- mean [values]
  (/ (reduce + 0.0 values) (double (count values))))

(defn- sample-sd [values]
  (if (< (count values) 2)
    0.0
    (let [average (mean values)]
      (Math/sqrt
       (/ (reduce + 0.0
                  (map #(let [delta (- (double %) average)] (* delta delta))
                       values))
          (double (dec (count values))))))))

(defn- summary [values]
  (let [n (count values)
        sd (sample-sd values)]
    {:mean (mean values) :sd sd :sem (/ sd (Math/sqrt (double n))) :n n}))

(defn- difference [left right]
  (count (filter true? (map not= left right))))

(defn- grid-activity [states]
  (let [changes (mapv #(difference (:exotypes %1) (:exotypes %2))
                      states (rest states))]
    {:changed-steps (count (filter pos? changes))
     :changed-cells (reduce + 0 changes)}))

(defn- phenotype-activity [states]
  (/ (reduce + 0.0
             (map #(difference (:phenotype %1) (:phenotype %2))
                  states (rest states)))
     (double (* (:width config) (:steps config)))))

(defn- exotype-counts [state]
  (let [counts (frequencies (:exotypes state))]
    (into (sorted-map)
          (map #(vector % (get counts % 0)) grid/exotype-kinds))))

(defn- shannon-entropy [counts]
  (let [total (double (reduce + (vals counts)))]
    (- (reduce + 0.0
               (for [n (vals counts) :when (pos? n)
                     :let [p (/ n total)]]
                 (* p (Math/log p)))))))

(defn- seed-run [tau seed]
  (let [states (trajectory seed tau)
        final-state (peek states)
        counts (exotype-counts final-state)
        activity (grid-activity states)]
    {:final-exotypes counts
     :kind-count (count (filter pos? (vals counts)))
     :entropy (shannon-entropy counts)
     :changed-steps (:changed-steps activity)
     :changed-cells (:changed-cells activity)
     :phenotype-activity (phenotype-activity states)
     :genotype-rule-count (count (distinct (:genotype final-state)))}))

(defn- tau-summary [runs]
  {:final-exotypes
   (into (sorted-map)
         (for [kind grid/exotype-kinds]
           [kind (summary (map #(get-in % [:final-exotypes kind]) runs))]))
   :kind-count (summary (map :kind-count runs))
   :entropy (summary (map :entropy runs))
   :changed-steps (summary (map :changed-steps runs))
   :changed-cells (summary (map :changed-cells runs))
   :phenotype-activity (summary (map :phenotype-activity runs))
   :genotype-rule-count (summary (map :genotype-rule-count runs))})

(defn- legacy-view [state]
  (select-keys state [:arm :seed :time :phenotype :genotype
                      :previous-genotype :exotypes :lambda]))

(defn- legacy-trajectory [seed]
  (let [initial (dissoc (initial-state seed 0.0) :tau :prevalence-radius)]
    (mapv legacy-view
          (take (inc (:steps config)) (iterate efe/step initial)))))

(defn- zero-temperature-check [seeds]
  (let [checks (mapv (fn [seed]
                       (= (pr-str (legacy-trajectory seed))
                          (pr-str (mapv legacy-view (trajectory seed 0.0)))))
                     seeds)]
    {:comparison :legacy-state-abi-pr-str
     :seeds-checked (count seeds)
     :all-byte-identical? (every? true? checks)
     :mismatches (vec (keep-indexed #(when-not %2 %1) checks))}))

(defn- determinism-check [seed tau]
  (let [left (pr-str (trajectory seed tau))
        right (pr-str (trajectory seed tau))]
    {:seed seed :tau tau :comparison :full-trajectory-pr-str
     :byte-identical? (= left right)
     :hash (format "%08x" (bit-and 0xffffffff (hash left)))}))

(def ^:private exotype-colours
  {:builder [54 162 235]
   :collapser [245 166 35]
   :chaos [220 55 65]
   :identity [55 190 105]})

(defn- render-tau! [label tau]
  (let [states (trajectory (:seed-base config) tau)
        pixels (mapv (fn [state]
                       (mapv exotype-colours (:exotypes state)))
                     states)
        path (str "reports/figures/slice3-" label "-tau-"
                  (str/replace (format "%.2f" tau) "." "p")
                  ".png")
        ppm (str path ".ppm")]
    (io/make-parents path)
    (render/write-ppm! ppm pixels :comment (str "slice3 exotypes tau=" tau))
    (let [{:keys [exit err]} (sh/sh "convert" ppm "-filter" "point"
                                    "-resize" "800x1210!" "-strip" path)]
      (when-not (zero? exit)
        (throw (ex-info "ImageMagick conversion failed"
                        {:tau tau :error err}))))
    (.delete (io/file ppm))
    path))

(defn experiment []
  (let [seeds (mapv #(+ (:seed-base config) %) (range (:seeds config)))
        raw (into (sorted-map)
                  (for [tau (:taus config)]
                    [tau (mapv #(seed-run tau %) seeds)]))
        summaries (into (sorted-map)
                        (map (fn [[tau runs]] [tau (tau-summary runs)]) raw))
        peak (apply max-key #(get-in summaries [% :entropy :mean])
                    (:taus config))
        render-taus (distinct [0.0 peak (last (:taus config))])
        figures (into (sorted-map)
                      (for [tau render-taus]
                        [tau (render-tau! (cond
                                           (= tau 0.0) "fit-extreme"
                                           (= tau (last (:taus config)))
                                           "conformity-extreme"
                                           :else "entropy-maximum")
                                          tau)]))]
    {:kind :exotype-prevalence-slice3
     :schema 1
     :config config
     :seeds seeds
     :taus summaries
     :entropy-maximum {:criterion :mean-shannon-entropy :tau peak
                       :mean (get-in summaries [peak :entropy :mean])}
     :legacy-endpoint (zero-temperature-check seeds)
     :determinism (determinism-check (:seed-base config) 0.3)
     :figures figures
     :modelling-choices
     {:prior-source :current-exotype-grid-only
      :cell-memory :none
      :neighbourhood {:topology :circular :radius (:prevalence-radius config)
                      :includes-self true}
      :policy-space [:hold :adopt-left :adopt-right]
      :duplicate-candidate-treatment
      "Each source policy receives the prevalence of its candidate exotype; policies remain distinct even when they yield the same exotype."
      :zero-temperature :legacy-argmin-g
      :positive-temperature :sample-q
      :sampling :stateless-java-util-random-by-seed-time-index
      :entropy {:log-base :natural :zero-counts :omitted}
      :render {:seed (:seed-base config) :content :exotype-grid}}}))

(defn- fmt [{:keys [mean sd sem]}]
  (format "%.4f (sd %.4f; sem %.4f)" mean sd sem))

(defn markdown [result]
  (str "# Exotype prevalence Slice 3\n\n"
       "Fixed tau grid `" (pr-str (get-in result [:config :taus]))
       "`; lambda fixed at `" (get-in result [:config :lambda])
       "`; N=`" (count (:seeds result)) "` seeds per tau.\n\n"
       "| tau | kinds present | Shannon entropy | changed steps | changed cells | phenotype activity | genotype rules |\n"
       "|---:|---:|---:|---:|---:|---:|---:|\n"
       (apply str
              (for [[tau row] (:taus result)]
                (format "| %.2f | %s | %s | %s | %s | %s | %s |\n"
                        tau (fmt (:kind-count row)) (fmt (:entropy row))
                        (fmt (:changed-steps row)) (fmt (:changed-cells row))
                        (fmt (:phenotype-activity row))
                        (fmt (:genotype-rule-count row)))))
       "\n## Final exotype distributions\n\n```clojure\n"
       (pr-str (into (sorted-map)
                     (map (fn [[tau row]] [tau (:final-exotypes row)]))
                     (:taus result)))
       "\n```\n\n## Apparatus checks\n\n```clojure\n"
       (pr-str (select-keys result [:legacy-endpoint :determinism]))
       "\n```\n\n## Modelling choices\n\n```clojure\n"
       (pr-str (:modelling-choices result))
       "\n```\n\n## Spacetime panels\n\n"
       (apply str (for [[tau path] (:figures result)]
                    (str "- tau `" tau "`: `" path "`\n")))
       "\nThis report records measurements only; interpretation is reserved for review.\n"))

(defn -main [& _]
  (let [result (experiment)]
    (spit "reports/exotype-prevalence-slice3.edn"
          (str (pr-str result) "\n"))
    (spit "reports/exotype-prevalence-slice3.md" (markdown result))
    (println :legacy-endpoint (:legacy-endpoint result))
    (println :determinism (:determinism result))
    (doseq [[tau row] (:taus result)]
      (println tau :kinds (get-in row [:kind-count :mean])
               :entropy (get-in row [:entropy :mean])))
    (println :wrote "reports/exotype-prevalence-slice3.edn"
             "reports/exotype-prevalence-slice3.md")))
