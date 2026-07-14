(ns notebooks.r03-cyberants
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [scicloj.kindly.v4.kind :as kind]))

;; # R03: CyberAnts controlled replay
;;
;; ## What this replay asked
;;
;; The archived result: an **L5-creative** ant wiring beat a **sigil-gradient**
;; heuristic 20/20 on patchy food, 10/10 on sparse, and lost 16/20 on snowdrift.
;; The implicit reading was that *the L5-creative wiring design* is what won.
;;
;; This replay re-runs that comparison with two controls the original lacked —
;; a **random wiring** and a **shuffled-parameter** config — and reports
;; starvation explicitly, over 30 unseeded runs/arm × 300 ticks, 95% t intervals.
;;
;; ## What it found, in one line
;;
;; **The direction reproduces, but the win cannot be credited to the L5-creative
;; wiring** — for two independent reasons below. (This is *not* a refutation of
;; L5-creative; see the caveat for why we stop at "not isolated.")

;; ## Pinned source

(def resource-root (io/file "resources" "replays" "cyberants"))
(def replay-summary
  (edn/read-string (slurp (io/file resource-root "summary.edn"))))
(def reviewed-claims
  (slurp (io/file resource-root "CLAIMS.md")))

;; futon2 harness SHA `73ec130`; `src/ants/compare.clj` blob `412a87b`.

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

;; ## The picture at a glance
;;
;; One bar per arm, per scenario. Read it directly: on **patchy** and **sparse**
;; the sigil-gradient bar is flat on the floor (it starves to 0), while
;; **L5-creative, random-wiring, and shuffled-parameter are indistinguishable**.
;; On **snowdrift** all four are the same. Nowhere does L5-creative stand out.

(def arm-colors
  {:l5 "#33aa77" :sigil-gradient "#cc4444"
   :random-wiring "#3388cc" :shuffled-parameter "#aa66cc"})

;; Bars are normalized within each scenario (the scenario's best arm = full
;; width), since absolute scores differ ~10x between patchy/sparse and snowdrift.

(kind/html
 (apply str
   (for [s scenarios
         :let [means (into {} (for [a arms]
                                [a (double (:mean (get-in replay-summary
                                                          [:results s a])))]))
               mx (apply max 0.001 (vals means))]]
     (str "<div style='margin:10px 0'><b>" (name s) "</b>"
          "<table style='border-collapse:collapse;font-size:90%'>"
          (apply str
            (for [a arms :let [m (means a) pct (* 100.0 (/ m mx))]]
              (str "<tr>"
                   "<td style='padding:2px 8px;text-align:right;white-space:nowrap'>"
                   (name a) "</td>"
                   "<td style='padding:2px'>"
                   "<div style='width:220px;background:#eee;border:1px solid #ccc'>"
                   "<div style='height:14px;width:" (format "%.1f" pct)
                   "%;background:" (arm-colors a) "'></div></div></td>"
                   "<td style='padding:2px 8px;font-variant-numeric:tabular-nums'>"
                   (format "%.3f" m) "</td></tr>")))
          "</table></div>"))))

;; ## Finding 1 — the baseline it beat is degenerate
;;
;; On patchy and sparse, **sigil-gradient scores 0.000 — it starves in 30/30
;; runs.** So "L5 beats sigil-gradient" here means only "L5 isn't dead." That is
;; a floor any surviving ant clears; on its own it says nothing about the wiring.

(kind/html
 (html-table
  ["Scenario" "Arm" "Mean [95% CI]" "Starvation"]
  (for [scenario scenarios
        arm arms
        :let [result (get-in replay-summary [:results scenario arm])]]
    [(name scenario) (name arm) (result-cell result) (starvation-cell result)])))

;; ## Finding 2 — random and shuffled wiring do just as well as L5-creative
;;
;; Each row is an L5 − control (or L5 − sigil) difference with its 95% interval.
;; "Supported?" is *yes* only when the interval excludes zero in the
;; pre-registered direction. L5 beats **sigil-gradient** on patchy/sparse (but
;; that's beating a corpse, per Finding 1). Against the **controls**, every
;; interval includes zero: scrambling the wiring changes nothing, so the
;; advantage is **not specific to the L5-creative wiring**. Snowdrift: no
;; supported difference between any arms.

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

;; ## What this means
;;
;; The archived "20/20" result reproduces as a **direction** but not as an
;; **interpretation**. The credit it implicitly gave to the L5-creative *design*
;; is not earned here: a random wiring matches it, over a baseline that simply
;; dies. This does not say L5-creative is worthless — it says this comparison
;; **does not isolate any wiring-specific advantage**.

;; ## Caveat — why "not isolated," not "refuted"
;;
;; In this futon2 harness, the external-config adapter applies the wiring's
;; **numeric precision** to live AIF state but keeps policy / pattern-sense /
;; adaptation wiring mainly as **provenance**. So a random wiring may be
;; *operationally equivalent* to L5-creative simply because the harness barely
;; reads the wiring — meaning the null control is evidence about **this executed
;; boundary**, not proof that arbitrary wiring is generally as good. Crucially,
;; the original 20/20 claim was made on this same boundary. Honest verdict: **as
;; executed, the comparison cannot support "the L5-creative wiring is the
;; reason."** Distinguishing wiring from precision would need an adapter that
;; actually exercises the policy/pattern-sense wiring.

(kind/html
 (str "<p><small>Self-contained: reviewed <code>CLAIMS.md</code> ("
      (count reviewed-claims) " chars) and <code>summary.edn</code> are bundled "
      "with this notebook; numbers above are read from <code>summary.edn</code>.</small></p>"))
