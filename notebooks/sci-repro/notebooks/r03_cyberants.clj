(ns notebooks.r03-cyberants
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [scicloj.kindly.v4.kind :as kind]))

;; # R03: CyberAnts controlled replay
;;
;; This is a self-contained view of the reviewed futon2 statistical replay.
;; The source simulation is unseeded: each cell below summarizes 30 runs of
;; 300 ticks, and intervals are two-sided 95% t intervals.

;; ## Pinned source

(def resource-root (io/file "resources" "replays" "cyberants"))
(def replay-summary
  (edn/read-string (slurp (io/file resource-root "summary.edn"))))
(def reviewed-claims
  (slurp (io/file resource-root "CLAIMS.md")))

;; futon2 harness SHA: `73ec130a5befdc321ae1a04d51ef59d6bbabbc44`
;;
;; `src/ants/compare.clj` blob: `412a87b7f14467a49f069b7b30d24a139179e5ee`

(def scenarios [:patchy :sparse :snowdrift])
(def arms [:l5 :sigil-gradient :random-wiring :shuffled-parameter])

(defn fmt [value]
  (format "%.3f" (double value)))

(defn result-cell [{:keys [mean ci95]}]
  (str (fmt mean) " [" (fmt (first ci95)) ", "
       (fmt (second ci95)) "]"))

(defn starvation-cell [{:keys [starvation-fraction starvation-count n]}]
  (str (fmt starvation-fraction) " (" starvation-count "/" n ")"))

(defn html-table [headers rows]
  (str "<table border='1' style='border-collapse:collapse'>"
       "<thead><tr>"
       (apply str (map #(str "<th style='padding:6px'>" % "</th>") headers))
       "</tr></thead><tbody>"
       (apply str
              (for [row rows]
                (str "<tr>"
                     (apply str
                            (map #(str "<td style='padding:6px'>" % "</td>") row))
                     "</tr>")))
       "</tbody></table>"))

;; ## Arm summaries

(kind/html
 (html-table
  ["Scenario" "Arm" "Mean [95% CI]" "Starvation"]
  (for [scenario scenarios
        arm arms
        :let [result (get-in replay-summary [:results scenario arm])]]
    [(name scenario) (name arm) (result-cell result) (starvation-cell result)])))

;; Patchy and sparse show an apparent L5 advantage over sigil-gradient only
;; because sigil-gradient scores 0.000 and starves in 30/30 runs. Both control
;; arms match L5: their difference intervals include zero in every scenario.

;; ## Claims and controls

(kind/html
 (html-table
  ["Scenario" "Comparison" "Expected" "Delta [95% CI]" "Supported?"]
  (for [scenario scenarios
        [comparison label expected direction]
        [[:l5-vs-sigil "L5 - sigil-gradient" (if (= scenario :snowdrift)
                                                "L5 < sigil"
                                                "L5 > sigil")
          (if (= scenario :snowdrift) :less :greater)]
         [:l5-vs-random-wiring "L5 - random-wiring" "L5 > control" :greater]
         [:l5-vs-shuffled-parameter "L5 - shuffled-parameter" "L5 > control" :greater]]
        :let [{:keys [delta ci95]} (get-in replay-summary
                                           [:comparisons scenario comparison])
              supported? (case direction
                           :greater (pos? (first ci95))
                           :less (neg? (second ci95)))]]
    [(name scenario)
     label
     expected
     (str (fmt delta) " [" (fmt (first ci95)) ", " (fmt (second ci95)) "]")
     (if supported? "yes" "no")])))

;; **Finding:** L5-creative beats sigil-gradient on patchy and sparse only
;; because sigil-gradient fully starves. The random-wiring and
;; shuffled-parameter controls match L5, so this replay does not establish a
;; wiring-specific advantage. Snowdrift shows no supported difference.

;; ## Interpretation constraint
;;
;; The current futon2 external-config adapter applies numeric precision to
;; live AIF state, while retaining policy, pattern-sense, and adaptation
;; wiring mainly as provenance. The random-wiring control is therefore a
;; valid structural permutation but may be operationally equivalent to L5 in
;; this harness. The shuffled-parameter control includes a precision
;; permutation and can be operationally distinct. Any null control result is
;; evidence about this executed boundary, not evidence that arbitrary wiring
;; is generally equivalent.
;;
;; The copied `CLAIMS.md` is loaded above alongside `summary.edn`; its presence
;; keeps the reviewed prose and the machine-readable numbers together in the
;; published notebook resource bundle.

(kind/html
 (str "<p><small>Reviewed claims source loaded: "
      (count reviewed-claims) " characters.</small></p>"))
