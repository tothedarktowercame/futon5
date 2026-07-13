(ns scirepro.report
  (:require [clojure.java.io :as io]
            [scirepro.engine :as engine]))

(def figure-seeds [150200130 150200131 150200132 150200133 150200134 150200135])
(def stasis-seeds (range 150200130 150200160))
(def baseline-rules [0 23 30 84 90 110 128 184])
(def width 80)
(def figure-steps 120)
(def stasis-steps 300)

(defn no-blend-runs []
  (mapv (fn [seed]
          {:seed seed
           :ic (engine/seeded-ic seed width)
           :rows (engine/evolve (engine/seeded-ic seed width) figure-steps :multiply)})
        figure-seeds))

(defn stasis-times []
  (mapv (fn [seed]
          (let [ic (engine/seeded-ic seed width)
                rows (engine/evolve ic stasis-steps :multiply)]
            {:seed seed
             :time-to-stasis (engine/first-stasis-time rows)}))
        stasis-seeds))

(defn baseline-stasis []
  (mapv (fn [rule]
          (let [times (mapv (fn [seed]
                              (let [ic (engine/seeded-ic seed width)
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

(defn write-html! [path]
  (let [f (io/file path)]
    (.mkdirs (.getParentFile f))
    (spit f (html-report))
    (.getPath f)))

(defn -main [& _]
  (let [path (write-html! "out/nb01_metaca_core.html")
        summary (:stasis-summary (report))]
    (println (format "REPORT OK %s stasis observed=%d/%d median=%s"
                     path (:observed summary) (:n summary) (:median summary)))
    (System/exit 0)))
