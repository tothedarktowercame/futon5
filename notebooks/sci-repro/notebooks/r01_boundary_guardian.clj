(ns notebooks.r01-boundary-guardian
  (:require [scicloj.kindly.v4.kind :as kind]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [scirepro.exo :as exo]))

;; # R01: Boundary-Guardian — the L5-creative wiring replay
;;
;; **Tier-2 replay #2** from the replay ledger. The boundary-guardian (L5-creative)
;; wiring is the only genotype-layer edge-of-chaos run ever recorded in the futon5
;; repo, scoring 0.176 on the standard verifier despite strong EoC diagnostics.
;;
;; This notebook measures four claims (BG1–BG4) against two null models.
;;
;; **Ground truth:** `data/wiring-ladder/level-5-creative.edn`, executed via
;; `futon5.wiring.runtime` (headless from the futon5 project).
;;
;; **Cross-check:** `scirepro.exo-cross-check` — creative path grid-identical
;; (18K+ checks, 0 errors); legacy path statistical (unseeded `rand` at
;; `ca/core.clj:416`, see B1 correction).
;;
;; **Findings:** B1 (wiring semantics + rand correction), B2 (measurement
;; reconciliation), plus any new entries.

;; ## Setup

(def runs-dir (io/file "resources/runs"))
(def seeds (range 42 72))
(defn load-artifact [arm seed]
  (let [path (io/file runs-dir (str "r01-" (name arm) "-seed-" seed ".edn"))]
    (when (.exists path)
      (edn/read-string (slurp path)))))

(defn load-all [arm]
  (remove nil? (map #(load-artifact arm %) seeds)))

(defn mean [xs] (if (empty? xs) 0.0 (/ (reduce + 0.0 xs) (count xs))))
(defn stddev [xs]
  (let [m (mean xs) n (count xs)]
    (if (< n 2) 0.0 (Math/sqrt (/ (reduce + 0.0 (map #(Math/pow (- % m) 2) xs)) n)))))
(defn ci95 [xs]
  (let [s (stddev xs) n (count xs)]
    (if (< n 2) 0.0 (* 1.96 s (/ (Math/sqrt n))))))
(defn round4 [x] (/ (Math/round (* x 10000.0)) 10000.0))

;; ## BG1: Edge-of-chaos reproduction
;;
;; **Claim:** The L5-creative wiring produces genotype-layer EoC: high entropy,
;; high change-rate, low autocorrelation — the diagnostics recorded in
;; `reports/what-makes-a-good-run.md` §3.
;;
;; **Recorded values:** H=0.946, Δ=0.985, ρ=0.015, σ=0.828, φ=0.149, λ=0.990
;;
;; **Baselines:** L0-baseline (frozen, Δ≈0.000) and Rule-30 (generic chaos).

(defn extract-diag [arm key]
  (let [runs (load-all arm)
        vals (map #(get-in % [:late-window key]) runs)]
    (remove nil? vals)))

(defn diag-summary [arm key]
  (let [vals (extract-diag arm key)]
    {:mean (round4 (mean vals))
     :ci95 (round4 (ci95 vals))
     :n (count vals)}))

(kind/html
 (let [keys [:mean-entropy :mean-change-rate :mean-autocorr :mean-diversity]
       labels ["H (entropy)" "Δ (change)" "ρ (autocorr)" "σ (diversity)"]
       arms [:l5-creative :l0-baseline :rule-30]
       arm-labels ["L5-creative" "L0-baseline" "Rule-30"]]
   (str "<table border='1' style='border-collapse:collapse'>"
        "<tr><th>Metric</th>" (apply str (for [al arm-labels] (str "<th>" al "</th>"))) "</tr>"
        (apply str
               (for [[k lbl] (zipmap keys labels)]
                 (str "<tr><td>" lbl "</td>"
                      (apply str
                             (for [arm arms]
                               (let [s (diag-summary arm k)]
                                 (str "<td>" (:mean s) " ± " (:ci95 s) " (n=" (:n s) ")</td>"))))
                      "</tr>")))
        "</table>")))

;; **BG1 verdict:** (filled by the rendered data — check which recorded numbers
;; reproduce within CI and which do not.)

;; ## BG2: Verifier blind spot
;;
;; **Claim:** The standard verifier (tai-zone spec: H∈[0.25,0.95], Δ∈[0.0,0.4],
;; ρ∈[0.3,0.9], σ∈[0.1,0.7]) scores L5-creative at ~0.176 despite the EoC.
;;
;; The verifier penalizes the extreme change-rate (Δ≈0.985, far above the
;; [0.0,0.4] band) and near-zero autocorrelation (ρ≈0.015, far below [0.3,0.9]).

(defn verifier-summary [arm]
  (let [runs (load-all arm)
        scores (map :verifier-score runs)]
    {:mean (round4 (mean scores))
     :ci95 (round4 (ci95 scores))
     :n (count scores)}))

(kind/html
 (str "<table border='1' style='border-collapse:collapse'>"
      "<tr><th>Arm</th><th>Verifier score (mean ± 95% CI)</th><th>n</th></tr>"
      (apply str
             (for [[arm label] [[:l5-creative "L5-creative"]
                                [:l0-baseline "L0-baseline"]
                                [:rule-30 "Rule-30"]]]
               (let [s (verifier-summary arm)]
                 (str "<tr><td>" label "</td><td>" (:mean s) " ± " (:ci95 s) "</td><td>" (:n s) "</td></tr>"))))
      "</table>"))

;; **BG2 verdict:** (the recorded ~0.176 verifier score — does the distribution
;; confirm or refute?)

;; ## BG3: Measurement reconciliation
;;
;; **Recorded disagreement:** "Codex saw 99.5% chaotic, local run saw settling
;; to 0.10–0.14." The likely explanation: two different metrics.
;;
;; - "99.5% chaotic" = fraction of generations with change-rate > 0.5 (BG3a)
;; - "settling to 0.10–0.14" = instantaneous change-rate at late window (BG3b)

(defn metric-comparison [arm]
  (let [frac-above (extract-diag arm :fraction-above-0.5)
        inst-change (extract-diag arm :instantaneous-change)]
    {:frac-above-0.5 {:mean (round4 (mean frac-above)) :ci95 (round4 (ci95 frac-above))}
     :inst-late-change {:mean (round4 (mean inst-change)) :ci95 (round4 (ci95 inst-change))}}))

(kind/html
 (let [bg3 (metric-comparison :l5-creative)]
   (str "<h3>BG3: Two metrics for L5-creative</h3>"
        "<p><b>Fraction of run with Δ > 0.5:</b> " (get-in bg3 [:frac-above-0.5 :mean])
        " ± " (get-in bg3 [:frac-above-0.5 :ci95]) "</p>"
        "<p><b>Instantaneous late-window Δ:</b> " (get-in bg3 [:inst-late-change :mean])
        " ± " (get-in bg3 [:inst-late-change :ci95]) "</p>"
        "<p><b>Reconciliation:</b> The '99.5% chaotic' claim corresponds to the"
        " fraction-above-threshold metric; the 'settling to 0.10–0.14' claim"
        " would require a different windowing or metric definition.</p>")))

;; ## BG4: Discriminators
;;
;; Do bitplane MI, diagonal autocorrelation, or triangle density separate
;; L5-creative's "structured genotype chaos" from generic Rule-30 chaos?
;;
;; **Null models:** Rule-30 (generic chaos) and true random noise.
;; This is the notebook's novel contribution.

(defn hamming-distance [a b]
  (count (filter true? (map not= a b))))

(defn bitplane-signature [genotype]
  ;; Extract 8-bit planes
  (let [chars (seq genotype)
        planes (for [bit (range 8)]
                 (mapv (fn [c]
                         (let [bits (exo/sigil->bits (str c))]
                           (if (= \1 (nth bits bit)) 1 0)))
                       chars))
        entropies (for [plane planes]
                    (let [ones (count (filter #{1} plane))
                          total (count plane)
                          p (/ (double ones) total)]
                      (if (or (<= p 0.01) (>= p 0.99))
                        0.0
                        (- (* p (/ (Math/log p) (Math/log 2.0)))
                           (* (- 1 p) (/ (Math/log (- 1 p)) (Math/log 2.0)))))))]
    entropies))

(defn bitplane-mi [history]
  ;; Mean mutual information between adjacent bitplanes
  (let [late (take-last 20 history)
        sigs (map seq late)
        planes-per-gen (map bitplane-signature sigs)]
    (if (< (count planes-per-gen) 2)
      0.0
      (let [pairs (partition 2 1 planes-per-gen)
            mis (for [[g1 g2] pairs]
                  (let [agreements (count (filter true? (map = g1 g2)))]
                    (/ (double agreements) 8)))]
        (mean mis)))))

(defn diag-autocorr [history]
  ;; Diagonal autocorrelation
  (let [late (drop-while nil? (take-last 20 history))
        rows (vec late)]
    (if (< (count rows) 5)
      0.0
      (let [base (nth rows 0)
            corrs (for [offset (range 1 (min 5 (count rows)))]
                    (let [target (nth rows offset)
                          shifted (str (subs target offset) (subs target 0 offset))
                          matches (count (filter true? (map = base shifted)))]
                      (/ (double matches) (count base))))]
        (mean corrs)))))

(defn discriminator-summary [arm]
  (let [runs (load-all arm)
        bp-mis (for [r runs] (bitplane-mi (:gen-history r)))
        diags (for [r runs] (diag-autocorr (:gen-history r)))]
    {:bitplane-mi {:mean (round4 (mean bp-mis)) :ci95 (round4 (ci95 bp-mis))}
     :diag-autocorr {:mean (round4 (mean diags)) :ci95 (round4 (ci95 diags))}}))

(kind/html
 (str "<h3>BG4: Discriminator separation</h3>"
      "<table border='1' style='border-collapse:collapse'>"
      "<tr><th>Discriminator</th><th>L5-creative</th><th>Rule-30</th><th>L0-baseline</th></tr>"
      (let [bp-l5 (discriminator-summary :l5-creative)
            bp-r30 (discriminator-summary :rule-30)
            bp-l0 (discriminator-summary :l0-baseline)]
        (str
         "<tr><td>Bitplane MI</td>"
         "<td>" (get-in bp-l5 [:bitplane-mi :mean]) " ± " (get-in bp-l5 [:bitplane-mi :ci95]) "</td>"
         "<td>" (get-in bp-r30 [:bitplane-mi :mean]) " ± " (get-in bp-r30 [:bitplane-mi :ci95]) "</td>"
         "<td>" (get-in bp-l0 [:bitplane-mi :mean]) " ± " (get-in bp-l0 [:bitplane-mi :ci95]) "</td></tr>"
         "<tr><td>Diag autocorr</td>"
         "<td>" (get-in bp-l5 [:diag-autocorr :mean]) " ± " (get-in bp-l5 [:diag-autocorr :ci95]) "</td>"
         "<td>" (get-in bp-r30 [:diag-autocorr :mean]) " ± " (get-in bp-r30 [:diag-autocorr :ci95]) "</td>"
         "<td>" (get-in bp-l0 [:diag-autocorr :mean]) " ± " (get-in bp-l0 [:diag-autocorr :ci95]) "</td></tr>"))
      "</table>"))

;; **BG4 verdict:** (do the discriminators separate structured chaos from
;; generic chaos? A negative answer is a valid finding.)

;; ## Spacetime panels

(defn sigil-color [s]
  (let [idx (exo/sigil->int s)
        r (mod idx 16)
        g (mod (quot idx 16) 16)
        b (mod (quot idx 32) 16)]
    (str "#" (format "%X" r) (format "%X" g) (format "%X" b))))

(defn render-spacetime-svg [history title max-rows]
  (let [rows (take max-rows history)
        height (count rows)
        width (count (first rows))
        cell-size 4
        chars (map seq rows)]
    (str "<svg xmlns='http://www.w3.org/2000/svg' width='" (* width cell-size) "' height='" (* height cell-size) "'>"
         "<text x='0' y='-2' font-size='10'>" title "</text>"
         (apply str
                (for [y (range height)
                      x (range width)]
                  (let [c (nth (nth chars y) x)
                        color (sigil-color (str c))]
                    (str "<rect x='" (* x cell-size) "' y='" (* y cell-size)
                         "' width='" cell-size "' height='" cell-size
                         "' fill='" color "'/>"))))
         "</svg>")))

(kind/html
 (let [long-run (load-artifact :l5-creative-long 42)]
   (when long-run
     (render-spacetime-svg (:gen-history long-run)
                           "L5-creative seed 42 (500 gen, first 120 rows)"
                           120))))

(kind/html
 (let [short-runs (load-all :l5-creative)]
   (str "<h3>L5-creative spacetime panels</h3>"
        (apply str
               (for [r (take 6 short-runs)]
                 (render-spacetime-svg (:gen-history r)
                                       (str "L5-creative seed " (:seed r))
                                       60))))))

(kind/html
 (let [l0-runs (load-all :l0-baseline)]
   (when (seq l0-runs)
     (render-spacetime-svg (:gen-history (first l0-runs))
                           "L0-baseline seed 42 (frozen)"
                           80))))

(kind/html
 (let [r30-runs (load-all :rule-30)]
   (when (seq r30-runs)
     (render-spacetime-svg (:gen-history (first r30-runs))
                           "Rule-30 chaos seed 42"
                           80))))

;; ## How to reproduce
;;
;; 1. Run the driver: `clojure -M -m notebooks.r01-driver`
;;    (launches headless futon5 for all arms × 30 seeds + 1 long run;
;;     per-seed artifacts under `resources/runs/`)
;; 2. Run the cross-check: `clojure -M -m scirepro.exo-cross-check`
;; 3. Render this notebook: `clojure -M -m scirepro.render-r01`
;;
;; **Ground truth:** futon5 `data/wiring-ladder/level-5-creative.edn`
;; **Cross-check:** creative path grid-identical; legacy path statistical
;; (unseeded `rand` at `ca/core.clj:416`, see B1 correction).
;;
;; **Pinned deps:** futon5 SHA ≥ `7ed2ad7`, sci-repro project `deps.edn`.
