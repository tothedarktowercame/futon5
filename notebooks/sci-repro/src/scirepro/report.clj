(ns scirepro.report
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [scirepro.engine :as engine]))

(def figure-seeds [150200130 150200131 150200132 150200133 150200134 150200135])
(def stasis-seeds (range 150200130 150200160))
(def baseline-rules [0 23 30 84 90 110 128 184])
(def width 80)
(def figure-steps 120)
(def stasis-steps 300)
(def c2-steps 500)
(def c3-steps 160)
(def band-window 8)
(def c3-region-window 8)

(defn ic-path [seed]
  (io/file "resources/ics" (str "seed-" seed ".edn")))

(defn phenotype-ic-path [seed]
  (io/file "resources/phenotype-ics" (str "seed-" seed ".edn")))

(defn ensure-ic! [seed]
  (let [path (ic-path seed)]
    (when-not (.exists path)
      (engine/save-ic! path seed width))
    path))

(defn ensure-phenotype-ic! [seed]
  (let [path (phenotype-ic-path seed)]
    (when-not (.exists path)
      (engine/save-phenotype-ic! path seed width))
    path))

(defn ic-for-seed [seed]
  (let [path (ensure-ic! seed)
        {:keys [ic] :as meta} (engine/read-ic-meta path)]
    ;; Loud guard: a persisted IC at the wrong width silently corrupts the
    ;; cohort (slice-2 defect: three width-64 cross-check leftovers were
    ;; reused inside the width-80 paired measurement). Never reuse silently.
    (when (not= (:width meta) width)
      (throw (ex-info "persisted IC width mismatch — regenerate or move it"
                      {:seed seed :path (str path)
                       :expected width :actual (:width meta)})))
    ic))

(defn phenotype-ic-for-seed [seed]
  (let [path (ensure-phenotype-ic! seed)
        {:keys [ic] :as meta} (engine/read-ic-meta path)]
    (when (not= (:width meta) width)
      (throw (ex-info "persisted phenotype IC width mismatch — regenerate or move it"
                      {:seed seed :path (str path)
                       :expected width :actual (:width meta)})))
    ic))

(defn ensure-all-ics! []
  (doseq [seed (sort (set (concat figure-seeds stasis-seeds)))]
    (ensure-ic! seed)
    (ensure-phenotype-ic! seed)))

(defn no-blend-runs []
  (ensure-all-ics!)
  (mapv (fn [seed]
          {:seed seed
           :ic (ic-for-seed seed)
           :rows (engine/evolve (ic-for-seed seed) figure-steps :multiply)})
        figure-seeds))

(defn blend-runs []
  (ensure-all-ics!)
  (mapv (fn [seed]
          {:seed seed
           :ic (ic-for-seed seed)
           :rows (engine/evolve (ic-for-seed seed) figure-steps :blend)})
        figure-seeds))

(defn dynamic-times
  ([dynamic steps] (dynamic-times dynamic steps stasis-seeds))
  ([dynamic steps seeds]
   (ensure-all-ics!)
   (mapv (fn [seed]
           (let [ic (ic-for-seed seed)
                 rows (engine/evolve ic steps dynamic)]
             {:seed seed
              :time-to-stasis (engine/first-stasis-time rows)
              :time-to-band (engine/first-band-time rows band-window)}))
         seeds)))

(defn stasis-times []
  (dynamic-times :multiply stasis-steps))

(defn paired-c2-times []
  (let [multiply (zipmap stasis-seeds (dynamic-times :multiply c2-steps))
        blend (zipmap stasis-seeds (dynamic-times :blend c2-steps))
        censored-time (inc c2-steps)]
    (mapv (fn [seed]
            (let [m (get-in multiply [seed :time-to-stasis])
                  b (get-in blend [seed :time-to-stasis])
                  mb (get-in multiply [seed :time-to-band])
                  bb (get-in blend [seed :time-to-band])
                  m* (or m censored-time)
                  b* (or b censored-time)]
              {:seed seed
               :multiply-stasis m
               :blend-stasis b
               :multiply-band mb
               :blend-band bb
               :delta (- b* m*)
               :blend-longer? (> b* m*)
               :same? (= b* m*)}))
          stasis-seeds)))

(defn paired-c2-summary [pairs]
  (let [deltas (sort (map :delta pairs))
        multiply-observed (keep :multiply-stasis pairs)
        blend-observed (keep :blend-stasis pairs)
        multiply-band-observed (keep :multiply-band pairs)
        blend-band-observed (keep :blend-band pairs)]
    {:n (count pairs)
     :horizon c2-steps
     :censored-time (inc c2-steps)
     :multiply-observed (count multiply-observed)
     :blend-observed (count blend-observed)
     :multiply-censored (- (count pairs) (count multiply-observed))
     :blend-censored (- (count pairs) (count blend-observed))
     :multiply-median (when (seq multiply-observed)
                        (nth (vec (sort multiply-observed))
                             (quot (count multiply-observed) 2)))
     :blend-median (when (seq blend-observed)
                     (nth (vec (sort blend-observed))
                          (quot (count blend-observed) 2)))
     :multiply-band-observed (count multiply-band-observed)
     :blend-band-observed (count blend-band-observed)
     :multiply-band-median (when (seq multiply-band-observed)
                             (nth (vec (sort multiply-band-observed))
                                  (quot (count multiply-band-observed) 2)))
     :blend-band-median (when (seq blend-band-observed)
                          (nth (vec (sort blend-band-observed))
                               (quot (count blend-band-observed) 2)))
     :delta-min (first deltas)
     :delta-median (nth (vec deltas) (quot (count deltas) 2))
     :delta-max (last deltas)
     :sign-test-blend-longer (count (filter :blend-longer? pairs))
     :sign-test-multiply-longer (count (filter #(neg? (:delta %)) pairs))
     :sign-test-ties (count (filter :same? pairs))}))

(defn mean [xs]
  (if (seq xs)
    (/ (reduce + xs) (double (count xs)))
    0.0))

(defn curve-for
  [dynamic steps seeds metric]
  (let [runs (mapv #(engine/evolve (ic-for-seed %) steps dynamic) seeds)]
    (mapv (fn [t]
            (let [values (case metric
                           :entropy (map #(engine/shannon-entropy (nth % t)) runs)
                           :change-rate (if (zero? t)
                                          (repeat (count runs) 0.0)
                                          (map #(engine/change-rate (nth % (dec t)) (nth % t)) runs)))]
              {:t t :value (mean values)}))
          (range (inc steps)))))

(defn c2-curves []
  (ensure-all-ics!)
  {:multiply-entropy (curve-for :multiply c2-steps stasis-seeds :entropy)
   :blend-entropy (curve-for :blend c2-steps stasis-seeds :entropy)
   :multiply-change-rate (curve-for :multiply c2-steps stasis-seeds :change-rate)
   :blend-change-rate (curve-for :blend c2-steps stasis-seeds :change-rate)})

(defn baseline-stasis []
  (mapv (fn [rule]
          (let [times (mapv (fn [seed]
                              (let [ic (ic-for-seed seed)
                                    bits (engine/genotype->initial-bits ic)
                                    rows (engine/eca-evolve rule bits stasis-steps)]
                                (engine/first-stasis-time rows)))
                            stasis-seeds)]
            {:rule rule
             :stasis-count (count (filter some? times))
             :median-time (when-let [xs (seq (sort (filter some? times)))]
                            (nth (vec xs) (quot (count xs) 2)))
             :times times}))
        baseline-rules))

(defn stasis-summary [times]
  (let [observed (sort (keep :time-to-stasis times))]
    {:n (count times)
     :observed (count observed)
     :censored (- (count times) (count observed))
     :min (first observed)
     :median (when (seq observed) (nth (vec observed) (quot (count observed) 2)))
     :max (last observed)}))

(defn report []
  (let [times (stasis-times)]
    {:figure-runs (no-blend-runs)
     :stasis-times times
     :stasis-summary (stasis-summary times)
     :baselines (baseline-stasis)
     :c7 (engine/blending-censored-rule-23-proof)
     :ambiguities
     {:a1 "Rules are byte strings in 256ca.el truth-table-3 order 000,001,010,100,011,101,110,111."
      :a2 "The string evolution uses fixed zero boundary cells, represented by sigil 一."
      :a3 "Blending copies agreed left/right alleles; only disagreements use the central local rule."}}))

(defn blend-report []
  (let [pairs (paired-c2-times)]
    {:figure-runs (blend-runs)
     :pairs pairs
     :summary (paired-c2-summary pairs)
     :curves (c2-curves)}))

(defn coupled-runs []
  (ensure-all-ics!)
  (mapv (fn [seed]
          (let [genotype (ic-for-seed seed)
                phenotype (phenotype-ic-for-seed seed)
                rows (engine/coupled-evolve genotype phenotype figure-steps)]
            {:seed seed
             :genotype (:genotype rows)
             :phenotype (:phenotype rows)}))
        figure-seeds))

(defn c3-coupled-runs []
  (ensure-all-ics!)
  (mapv (fn [seed]
          (let [genotype (ic-for-seed seed)
                phenotype (phenotype-ic-for-seed seed)
                rows (engine/coupled-evolve genotype phenotype c3-steps)]
            {:seed seed
             :genotype (:genotype rows)
             :phenotype (:phenotype rows)
             :frozen-phenotype (engine/phenotype-evolve-under-genotype genotype phenotype c3-steps)}))
        stasis-seeds))

(defn- frozen-segments-at [genotype-rows t window]
  (let [row (nth genotype-rows t)
        width (count row)
        stable? (fn [idx rule]
                  (every? #(= rule (nth (nth genotype-rows %) idx))
                          (range t (+ t window))))]
    (loop [idx 0 segments []]
      (if (>= idx width)
        segments
        (let [rule (nth row idx)]
          (if-not (stable? idx rule)
            (recur (inc idx) segments)
            (let [end (loop [j (inc idx)]
                        (if (and (< j width)
                                 (= rule (nth row j))
                                 (stable? j rule))
                          (recur (inc j))
                          j))]
              (recur end (conj segments {:t t :start idx :end end :rule rule})))))))))

(defn region-conformance [genotype-rows phenotype-rows]
  (let [window c3-region-window
        segments (mapcat #(frozen-segments-at genotype-rows % window)
                         (range 0 (- (count genotype-rows) window)))
        comparisons
        (for [{:keys [t start end rule]} segments
              :let [len (- end start)]
              :when (>= len (inc (* 2 window)))
              k (range 1 window)
              i (range (+ start k) (- end k))
              :let [segment-p0 (subvec (vec (nth phenotype-rows t)) start end)
                    pure-rows (engine/eca-evolve rule segment-p0 k)
                    predicted (nth (nth pure-rows k) (- i start))
                    actual (nth (nth phenotype-rows (+ t k)) i)]]
          (= predicted actual))]
    {:segments (count segments)
     :comparisons (count comparisons)
     :matches (count (filter true? comparisons))
     :fraction (if (seq comparisons)
                 (/ (count (filter true? comparisons))
                    (double (count comparisons)))
                 0.0)}))

(defn mi-series [runs layer-key]
  (mapv (fn [t]
          (let [actual (map #(engine/mutual-information
                              (nth (:genotype %) t)
                              (nth (layer-key %) t))
                            runs)
                null (map-indexed
                      (fn [idx run]
                        (engine/mutual-information
                         (nth (:genotype run) t)
                         (engine/rotate-row (nth (layer-key run) t) (+ 17 idx t))))
                      runs)]
            {:t t :mi (mean actual) :null (mean null)}))
        (range (inc c3-steps))))

(defn c3-report []
  (let [runs (c3-coupled-runs)
        conformances (mapv #(region-conformance (:genotype %) (:phenotype %)) runs)
        totals {:comparisons (reduce + (map :comparisons conformances))
                :matches (reduce + (map :matches conformances))
                :segments (reduce + (map :segments conformances))}
        mi (mi-series runs :phenotype)
        frozen-mi (mi-series (mapv (fn [run]
                                     (assoc run :phenotype (:frozen-phenotype run)))
                                   runs)
                             :phenotype)
        avg-mi (mean (map :mi mi))
        avg-null (mean (map :null mi))]
    {:figure-runs (coupled-runs)
     :conformance (assoc totals
                         :fraction (if (pos? (:comparisons totals))
                                     (/ (:matches totals)
                                        (double (:comparisons totals)))
                                     0.0))
     :per-seed-conformance conformances
     :mi mi
     :frozen-mi frozen-mi
     :summary {:steps c3-steps
               :region-window c3-region-window
               :mi-mean avg-mi
               :mi-null-mean avg-null
               :mi-lift (- avg-mi avg-null)
               :frozen-random-genotype-mi-mean (mean (map :mi frozen-mi))}}))

(defn binary-palette [v]
  (if (zero? v) "#f7f7f7" "#111111"))

(defn- table [headers rows]
  (str "<table><thead><tr>"
       (apply str (map #(str "<th>" % "</th>") headers))
       "</tr></thead><tbody>"
       (apply str
              (for [row rows]
                (str "<tr>" (apply str (map #(str "<td>" % "</td>") row)) "</tr>")))
       "</tbody></table>"))

(defn- fmt [x]
  (cond
    (nil? x) "censored"
    (float? x) (format "%.3f" x)
    (double? x) (format "%.3f" x)
    :else (str x)))

(defn chart-svg
  [series {:keys [width height y-max title]
           :or {width 900 height 260}}]
  (let [all-points (mapcat :points series)
        max-x (double (apply max 1 (map :t all-points)))
        max-y (double (or y-max (apply max 1.0 (map :value all-points))))
        pad 32
        plot-w (- width (* 2 pad))
        plot-h (- height (* 2 pad))
        x-scale (fn [t] (+ pad (* plot-w (/ t max-x))))
        y-scale (fn [v] (- height pad (* plot-h (/ v max-y))))
        path-for (fn [points]
                   (->> points
                        (map-indexed
                         (fn [idx {:keys [t value]}]
                           (str (if (zero? idx) "M" "L")
                                (format "%.2f,%.2f" (x-scale t) (y-scale value)))))
                        (str/join " ")))]
    (str "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " width " " height
         "\" width=\"" width "\" height=\"" height "\" role=\"img\">"
         "<rect x=\"0\" y=\"0\" width=\"" width "\" height=\"" height "\" fill=\"#fff\"/>"
         "<text x=\"" pad "\" y=\"20\" font-size=\"14\" font-family=\"sans-serif\">" title "</text>"
         "<line x1=\"" pad "\" y1=\"" (- height pad) "\" x2=\"" (- width pad) "\" y2=\"" (- height pad) "\" stroke=\"#555\"/>"
         "<line x1=\"" pad "\" y1=\"" pad "\" x2=\"" pad "\" y2=\"" (- height pad) "\" stroke=\"#555\"/>"
         (apply str
                (for [[idx {:keys [label color points]}] (map-indexed vector series)]
                  (str "<path d=\"" (path-for points) "\" fill=\"none\" stroke=\"" color
                       "\" stroke-width=\"2\"/>"
                       "<text x=\"" (- width 160) "\" y=\"" (+ 44 (* 18 idx))
                       "\" fill=\"" color "\" font-size=\"12\" font-family=\"sans-serif\">" label "</text>")))
         "</svg>")))

(defn html-report []
  (let [{:keys [figure-runs stasis-summary baselines c7 ambiguities]} (report)]
    (str "<!doctype html><html><head><meta charset=\"utf-8\">"
         "<title>nb01 MetaCA core reproduction</title>"
         "<style>body{font-family:system-ui,sans-serif;max-width:1100px;margin:32px auto;line-height:1.45}"
         "figure{margin:0 0 24px 0} .grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}"
         "svg{max-width:100%;height:auto;border:1px solid #ccc;image-rendering:pixelated}"
         "table{border-collapse:collapse;margin:16px 0} th,td{border:1px solid #ccc;padding:4px 8px;text-align:right}"
         "th{text-align:left;background:#f1f1f1} code{background:#eee;padding:1px 4px}</style></head><body>"
         "<h1>nb01 MetaCA core reproduction</h1>"
         "<p>Standalone reproduction of arXiv:1502.00130v1 §3.1 no-blending MetaCA dynamics, with fixed-rule ECA baselines and the §5.3 blending/censored-Rule-23 check.</p>"
         "<h2>Figure 2-style no-blend random IC runs</h2><div class=\"grid\">"
         (apply str
                (for [{:keys [seed rows]} figure-runs]
                  (str "<figure><figcaption>seed " seed "</figcaption>"
                       (engine/grid->svg rows {:cell 3})
                       "</figure>")))
         "</div>"
         "<h2>C1 time-to-stasis</h2>"
         "<p>Stasis is the first row identical to its predecessor, measured over "
         (:n stasis-summary) " seeded ICs, width " width ", censoring at " stasis-steps " steps.</p>"
         (table ["n" "observed" "censored" "min" "median" "max"]
                [[(:n stasis-summary) (:observed stasis-summary) (:censored stasis-summary)
                  (:min stasis-summary) (:median stasis-summary) (:max stasis-summary)]])
         "<h2>Fixed-rule ECA baselines</h2>"
         "<p>Baselines use the same saved genotype ICs projected to bit position 0.</p>"
         (table ["rule" "stasis-count" "median-time"]
                (map (juxt :rule :stasis-count :median-time) baselines))
         "<h2>C7 exhaustive blending check</h2>"
         "<p>Cases: " (:cases c7) "; passed: " (:passed c7) "; ok: " (:ok? c7) ".</p>"
         "<h2>A1-A3 findings</h2><ul>"
         (apply str (for [[k v] ambiguities] (str "<li><code>" (name k) "</code>: " v "</li>")))
         "</ul></body></html>")))

(defn blend-html-report []
  (let [{:keys [figure-runs pairs summary curves]} (blend-report)]
    (str "<!doctype html><html><head><meta charset=\"utf-8\">"
         "<title>nb02 MetaCA blending reproduction</title>"
         "<style>body{font-family:system-ui,sans-serif;max-width:1120px;margin:32px auto;line-height:1.45}"
         "figure{margin:0 0 24px 0}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}"
         "svg{max-width:100%;height:auto;border:1px solid #ccc;image-rendering:pixelated}"
         "table{border-collapse:collapse;margin:16px 0;font-size:13px}th,td{border:1px solid #ccc;padding:4px 8px;text-align:right}"
         "th{text-align:left;background:#f1f1f1}code{background:#eee;padding:1px 4px}</style></head><body>"
         "<h1>nb02 MetaCA blending reproduction</h1>"
         "<p>Reproduction of arXiv:1502.00130v1 §3.2 blending dynamics and measured C2: blend transient length versus no-blend on matched ICs.</p>"
         "<h2>Figure 1-style blend runs</h2><div class=\"grid\">"
         (apply str
                (for [{:keys [seed rows]} figure-runs]
                  (str "<figure><figcaption>seed " seed "</figcaption>"
                       (engine/grid->svg rows {:cell 3})
                       "</figure>")))
         "</div>"
         "<h2>C2 paired stasis result</h2>"
         (table ["n" "horizon" "multiply observed" "blend observed" "multiply median" "blend median"
                 "blend>multiply" "multiply>blend" "ties" "delta median"]
                [[(:n summary) (:horizon summary) (:multiply-observed summary) (:blend-observed summary)
                  (:multiply-median summary) (:blend-median summary)
                  (:sign-test-blend-longer summary) (:sign-test-multiply-longer summary)
                  (:sign-test-ties summary) (:delta-median summary)]])
         "<h2>Time-to-band</h2>"
         "<p>Band time is first row that remains unchanged for " band-window " consecutive rows.</p>"
         (table ["multiply band observed" "blend band observed" "multiply band median" "blend band median"]
                [[(:multiply-band-observed summary) (:blend-band-observed summary)
                  (:multiply-band-median summary) (:blend-band-median summary)]])
         "<h2>Entropy and change-rate curves</h2>"
         (chart-svg [{:label "multiply entropy" :color "#444" :points (:multiply-entropy curves)}
                     {:label "blend entropy" :color "#b23b3b" :points (:blend-entropy curves)}]
                    {:title "Mean row entropy" :y-max 7.0})
         (chart-svg [{:label "multiply change" :color "#444" :points (:multiply-change-rate curves)}
                     {:label "blend change" :color "#1f6fb2" :points (:blend-change-rate curves)}]
                    {:title "Mean row change-rate" :y-max 1.0})
         "<h2>Per-seed paired deltas</h2>"
         (table ["seed" "multiply stasis" "blend stasis" "delta" "multiply band" "blend band"]
                (map (fn [{:keys [seed multiply-stasis blend-stasis delta multiply-band blend-band]}]
                       [seed (fmt multiply-stasis) (fmt blend-stasis) delta (fmt multiply-band) (fmt blend-band)])
                     pairs))
         "</body></html>")))

(defn coupled-panel-html [{:keys [seed genotype phenotype]}]
  (str "<figure><figcaption>seed " seed " genotype</figcaption>"
       (engine/grid->svg genotype {:cell 3})
       "</figure>"
       "<figure><figcaption>seed " seed " phenotype</figcaption>"
       (engine/grid->svg phenotype {:cell 3 :palette binary-palette})
       "</figure>"))

(defn c3-html-report []
  (let [{:keys [figure-runs conformance mi frozen-mi summary]} (c3-report)
        mi-points (mapv (fn [{:keys [t mi]}] {:t t :value mi}) mi)
        null-points (mapv (fn [{:keys [t null]}] {:t t :value null}) mi)
        frozen-points (mapv (fn [{:keys [t mi]}] {:t t :value mi}) frozen-mi)]
    (str "<!doctype html><html><head><meta charset=\"utf-8\">"
         "<title>nb03 MetaCA phenotype coupling reproduction</title>"
         "<style>body{font-family:system-ui,sans-serif;max-width:1120px;margin:32px auto;line-height:1.45}"
         ".grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}"
         "figure{margin:0 0 20px 0}svg{max-width:100%;height:auto;border:1px solid #ccc;image-rendering:pixelated}"
         "table{border-collapse:collapse;margin:16px 0;font-size:13px}th,td{border:1px solid #ccc;padding:4px 8px;text-align:right}"
         "th{text-align:left;background:#f1f1f1}code{background:#eee;padding:1px 4px}</style></head><body>"
         "<h1>nb03 MetaCA phenotype coupling reproduction</h1>"
         "<p>Figure-4-style deterministic coupled genotype+phenotype dynamics, with phenotype driven by local genotype rules.</p>"
         "<h2>Figure 4-style coupled panels</h2><div class=\"grid\">"
         (apply str (map coupled-panel-html figure-runs))
         "</div>"
         "<h2>C3 region conformance</h2>"
         (table ["segments" "comparisons" "matches" "fraction"]
                [[(:segments conformance) (:comparisons conformance)
                  (:matches conformance) (fmt (:fraction conformance))]])
         "<h2>Mutual information</h2>"
         (table ["steps" "MI mean" "shuffled null mean" "lift" "frozen-random-genotype MI mean"]
                [[(:steps summary) (fmt (:mi-mean summary)) (fmt (:mi-null-mean summary))
                  (fmt (:mi-lift summary)) (fmt (:frozen-random-genotype-mi-mean summary))]])
         (chart-svg [{:label "coupled MI" :color "#b23b3b" :points mi-points}
                     {:label "shuffled null" :color "#555" :points null-points}
                     {:label "frozen genotype baseline" :color "#1f6fb2" :points frozen-points}]
                    {:title "Mean genotype/phenotype mutual information"})
         "<h2>A5 phenotype semantics</h2>"
         "<p>Phenotype updates first from old genotype and old phenotype using each cell's own current genotype rule on phenotype neighbors with fixed-zero boundaries; genotype then takes the S3.2 blend step from old genotype.</p>"
         "<h2>How to reproduce</h2>"
         "<pre>cd /home/joe/code/futon5/notebooks/sci-repro\nclojure -X:test\nclojure -M -m scirepro.cross-check 120\nclojure -M -m scirepro.render</pre>"
         "</body></html>")))

(defn write-html! [path]
  (let [f (io/file path)]
    (.mkdirs (.getParentFile f))
    (spit f (html-report))
    (.getPath f)))

(defn write-blend-html! [path]
  (let [f (io/file path)]
    (.mkdirs (.getParentFile f))
    (spit f (blend-html-report))
    (.getPath f)))

(defn write-c3-html! [path]
  (let [f (io/file path)]
    (.mkdirs (.getParentFile f))
    (spit f (c3-html-report))
    (.getPath f)))

(defn -main [& _]
  (let [path (write-html! "out/nb01_metaca_core.html")
        summary (:stasis-summary (report))]
    (println (format "REPORT OK %s stasis observed=%d/%d median=%s"
                     path (:observed summary) (:n summary) (:median summary)))
    (System/exit 0)))

;;; ---------------------------------------------------------------------------
;;; C4-C6 mutation report functions (slice 4b)
;;;

(def c4-seeds (range 150200130 150200140))  ; 10 seeds
(def c4-steps 200)
(def c4-width width)

(defn- ic-for-c4 [seed]
  (let [path (ensure-ic! seed)
        {:keys [ic] :as meta} (engine/read-ic-meta path)]
    (when (not= (:width meta) width)
      (throw (ex-info "persisted IC width mismatch" {:seed seed :expected width :actual (:width meta)})))
    ic))

(defn mutation-runs [mode rate steps seeds]
  (mapv (fn [seed]
          (let [ic (ic-for-c4 seed)]
            {:seed seed
             :ic ic
             :rows (case mode
                     :uniform (engine/evolve-with-mutation ic steps
                               (engine/generate-mutation-stream seed (count ic) steps rate :uniform))
                     :first-bit (engine/evolve-with-mutation ic steps
                                  (engine/generate-mutation-stream seed (count ic) steps rate :first-bit))
                     :balance-blend (engine/evolve-with-balance-mutation ic steps seed :blend)
                     :balance-multiply (engine/evolve-with-balance-mutation ic steps seed :multiply)
                     :random-replace (engine/evolve-with-random-replacement ic steps seed rate :blend)
                     :no-mutation (engine/evolve ic steps :blend))}))
        seeds))

(defn- entropy-curve [runs steps]
  (mapv (fn [t]
          {:t t :value (mean (map #(engine/shannon-entropy (nth (:rows %) t)) runs))})
        (range (inc steps))))

(defn- change-rate-curve [runs steps]
  (mapv (fn [t]
          (if (zero? t)
            {:t t :value 0.0}
            {:t t :value (mean (map #(engine/change-rate (nth (:rows %) (dec t)) (nth (:rows %) t)) runs))}))
        (range (inc steps))))

(defn c4-report []
  (let [rates [1.0 0.1 0.01 0.001 0.0]
        uniform-sweep (into {}
                            (for [rate rates]
                              [rate (mutation-runs :uniform rate c4-steps c4-seeds)]))
        balance-blend-runs (mutation-runs :balance-blend nil c4-steps c4-seeds)
        no-mutation-runs (mutation-runs :no-mutation nil c4-steps c4-seeds)
        random-replace-runs (mutation-runs :random-replace 0.05 c4-steps c4-seeds)]
    {:rates rates
     :uniform-sweep uniform-sweep
     :balance-blend-runs balance-blend-runs
     :no-mutation-runs no-mutation-runs
     :random-replace-runs random-replace-runs
     :curves
     {:uniform-rate-1.0-entropy (entropy-curve (get uniform-sweep 1.0) c4-steps)
      :uniform-rate-0.1-entropy (entropy-curve (get uniform-sweep 0.1) c4-steps)
      :uniform-rate-0.01-entropy (entropy-curve (get uniform-sweep 0.01) c4-steps)
      :uniform-rate-0.001-entropy (entropy-curve (get uniform-sweep 0.001) c4-steps)
      :balance-entropy (entropy-curve balance-blend-runs c4-steps)
      :no-mutation-entropy (entropy-curve no-mutation-runs c4-steps)
      :random-replace-entropy (entropy-curve random-replace-runs c4-steps)
      :uniform-rate-1.0-change-rate (change-rate-curve (get uniform-sweep 1.0) c4-steps)
      :balance-change-rate (change-rate-curve balance-blend-runs c4-steps)
      :no-mutation-change-rate (change-rate-curve no-mutation-runs c4-steps)
      :random-replace-change-rate (change-rate-curve random-replace-runs c4-steps)}
     :summary
     {:seeds (count c4-seeds)
      :steps c4-steps
      :width c4-width
      :rate-1.0-final-entropy (:value (last (:uniform-rate-1.0-entropy {:dummy []})))
      :balance-final-entropy (:value (last (entropy-curve balance-blend-runs c4-steps)))
      :no-mutation-final-entropy (:value (last (entropy-curve no-mutation-runs c4-steps)))
      :random-replace-final-entropy (:value (last (entropy-curve random-replace-runs c4-steps)))}}))

;;; C5: popcount-class frequencies and flagged-rule patch lifetimes

(def flagged-rules
  "Rules the paper highlights: 110, 30, 90, 184 and their bit-reversals/inverses."
  #{110 30 90 184
    200 57 165 18    ; bit-reversals of 110, 30, 90, 184 (approx — Wolfram convention)
    145 225 74})     ; inverses of 110, 30, 184 (165 already in set)

(defn- popcount-class [rule]
  (Integer/bitCount (int rule)))

(defn popcount-histogram [row]
  (let [counts (vec (repeat 9 0))]
    (reduce (fn [acc rule]
              (assoc acc (popcount-class rule) (inc (nth acc (popcount-class rule)))))
            counts
            row)))

(defn popcount-histogram-series [runs steps]
  (mapv (fn [t]
          (let [hists (map #(popcount-histogram (nth (:rows %) t)) runs)
                n (double (count hists))
                mean-hist (mapv #(if (pos? n) (/ % n) 0.0)
                                (apply map + hists))]
            {:t t :hist mean-hist}))
        (range 0 (inc steps) (max 1 (quot steps 20)))))

(defn- patch-lifetimes [runs rule-set _steps]
  (for [seed-run runs
        :let [rows (:rows seed-run)]
        i (range (count (first rows)))
        :let [lifetime (loop [t 0 best 0]
                         (if (>= t (count rows))
                           best
                           (let [rule (nth (nth rows t) i)]
                             (if (contains? rule-set rule)
                               (recur (inc t) (inc best))
                               (recur (inc t) best)))))]]
    lifetime))

(defn c5-report []
  (let [runs (mutation-runs :uniform 0.1 c4-steps c4-seeds)
        balance-runs (mutation-runs :balance-blend nil c4-steps c4-seeds)
        uniform-hists (popcount-histogram-series runs c4-steps)
        balance-hists (popcount-histogram-series balance-runs c4-steps)
        patch-lifetimes-uniform (patch-lifetimes runs flagged-rules c4-steps)
        patch-lifetimes-balance (patch-lifetimes balance-runs flagged-rules c4-steps)
        non-zero-lifetimes (filter pos? patch-lifetimes-uniform)
        sorted-lifetimes (sort non-zero-lifetimes)]
    {:uniform-popcount-hists uniform-hists
     :balance-popcount-hists balance-hists
     :flagged-rules (sort flagged-rules)
     :patch-lifetimes {:uniform
                       {:total-patches (count non-zero-lifetimes)
                        :median (when (seq sorted-lifetimes)
                                  (nth (vec sorted-lifetimes) (quot (count sorted-lifetimes) 2)))
                        :max (when (seq sorted-lifetimes) (last sorted-lifetimes))}
                       :balance
                       (let [bz (sort (filter pos? patch-lifetimes-balance))]
                         {:total-patches (count bz)
                          :median (when (seq bz) (nth (vec bz) (quot (count bz) 2)))
                          :max (when (seq bz) (last bz))})}}))

;;; C6: first-bit-only variant on coupled runs

(defn c6-report []
  (let [runs (mapv (fn [seed]
                     (let [genotype (ic-for-c4 seed)
                           phenotype (phenotype-ic-for-seed seed)
                           stream (engine/generate-mutation-stream
                                   seed (count genotype) c3-steps 0.1 :first-bit)
                           rows (engine/coupled-evolve-with-mutation genotype phenotype c3-steps stream)]
                       {:seed seed
                        :genotype (:genotype rows)
                        :phenotype (:phenotype rows)}))
                   (take 6 c4-seeds))
        occupancy (mapv (fn [t]
                          (let [gen-rows (map #(nth (:genotype %) t) runs)
                                total (* (count gen-rows) (count (first gen-rows)))
                                count-0 (count (filter zero? (apply concat gen-rows)))
                                count-128 (count (filter #(= 128 %) (apply concat gen-rows)))]
                            {:t t
                             :rule-0-frac (/ count-0 (double total))
                             :rule-128-frac (/ count-128 (double total))
                             :rule-0-or-128-frac (/ (+ count-0 count-128) (double total))}))
                        (range (inc c3-steps)))
        pheno-entropy (mapv (fn [t]
                              {:t t
                               :value (mean (map #(engine/shannon-entropy
                                                   (nth (:phenotype %) t)) runs))})
                            (range (inc c3-steps)))
        pheno-change-rate (mapv (fn [t]
                                  (if (zero? t)
                                    {:t t :value 0.0}
                                    {:t t
                                     :value (mean (map #(engine/change-rate
                                                         (nth (:phenotype %) (dec t))
                                                         (nth (:phenotype %) t)) runs))}))
                                (range (inc c3-steps)))]
    {:runs runs
     :occupancy occupancy
     :pheno-entropy pheno-entropy
     :pheno-change-rate pheno-change-rate
     :summary {:steps c3-steps
               :seeds (count runs)
               :final-0-or-128-frac (:rule-0-or-128-frac (last occupancy))
               :mean-0-or-128-frac (mean (map :rule-0-or-128-frac occupancy))}}))
