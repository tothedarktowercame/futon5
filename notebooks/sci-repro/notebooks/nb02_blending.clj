(ns notebooks.nb02-blending
  (:require [scicloj.kindly.v4.kind :as kind]
            [scirepro.engine :as engine]
            [scirepro.report :as report]))

;; # MetaCA blending reproduction
;;
;; This notebook measures the S3.2 blending dynamic from
;; arXiv:1502.00130v1 and turns C2 into a paired comparison: does
;; blending extend the transient before stable bands, on the same
;; explicit ICs used by the no-blend reproduction?

(def findings (report/blend-report))

;; ## Figure 1-style blend runs
;;
;; These six panels reuse the same saved seeds as `nb01_metaca_core`.

(kind/html
 (str "<div style=\"display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px\">"
      (apply str
             (for [{:keys [seed rows]} (:figure-runs findings)]
               (str "<figure style=\"margin:0\"><figcaption>seed " seed "</figcaption>"
                    (engine/grid->svg rows {:cell 3})
                    "</figure>")))
      "</div>"))

;; ## C2: paired time-to-stasis
;;
;; Stasis is the first row identical to its predecessor. Censored
;; values are scored as horizon+1 for the paired sign count.

(:summary findings)

;; ## Paired seed deltas
;;
;; Positive delta means the blend dynamic lasted longer before stasis
;; than the no-blend dynamic on the same IC.

(mapv #(select-keys % [:seed :multiply-stasis :blend-stasis :delta
                       :multiply-band :blend-band])
      (:pairs findings))

;; ## Entropy and change-rate curves
;;
;; Means are over the same 30 persisted ICs.

(kind/html
 (report/chart-svg [{:label "multiply entropy"
                     :color "#444"
                     :points (get-in findings [:curves :multiply-entropy])}
                    {:label "blend entropy"
                     :color "#b23b3b"
                     :points (get-in findings [:curves :blend-entropy])}]
                   {:title "Mean row entropy"
                    :y-max 7.0}))

(kind/html
 (report/chart-svg [{:label "multiply change"
                     :color "#444"
                     :points (get-in findings [:curves :multiply-change-rate])}
                    {:label "blend change"
                     :color "#1f6fb2"
                     :points (get-in findings [:curves :blend-change-rate])}]
                   {:title "Mean row change-rate"
                    :y-max 1.0}))

