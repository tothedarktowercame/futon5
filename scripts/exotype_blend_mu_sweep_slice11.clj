(ns exotype-blend-mu-sweep-slice11
  "Slice 11: two-parameter sweep of neighbour-agreement blend strength against
   mutation rate, on the baseline arm.

   Rationale (TN-metaca-baldwin-micro-pilots, micro-pilot 7): the exotype
   objective cannot be repaired from inside — chaos's claims are CONFIRMED at
   every active observation, so no accuracy-based correction displaces it. The
   only lever is the observation distribution. Blend and mu both act on it, so
   their interaction is the first two-parameter question in this study that is
   justified rather than speculative."
  (:require [clojure.java.shell :as sh]
            [clojure.string :as str]
            [futon5.exotype.pattern-eig :as pattern]
            [futon5.exotype.slice-harness :as harness]))

(def blend-strengths [0.0 0.05 0.1 0.2 0.35 0.5 0.7 1.0])
(def mus [0.0 0.03 0.1 0.3 1.0])

(def config
  {:seed-base 20260803 :seeds 8 :width 80 :steps 6000 :workers 24
   :lambda 0.55 :tau 0.3 :prevalence-radius 1
   :damage-steps 59 :checkpoints [0 120 600 1200 3000 6000]})

(defn cell-config [beta mu]
  (assoc config :blend-strength beta :mu mu))

(defn figure-path [beta mu]
  (str "reports/figures/slice11-beta" beta "-mu" mu "-triptych.png"))

(defn render-cell! [beta mu]
  (let [cfg (cell-config beta mu)
        state (harness/initial-state cfg :baseline (:seed-base cfg))
        states (take (inc (:steps cfg)) (iterate pattern/step-compact state))
        path (figure-path beta mu)]
    (harness/render-pixels! (harness/triptych-pixels states) path
                            (str "baseline blend beta=" beta " mu=" mu))
    path))

(defn ratchet [path]
  (let [{:keys [out err]} (sh/sh "python3" "analysis/ratchet_check.py" path "80" "right")
        text (str out err)
        grab (fn [re] (some-> (re-find re text) second))]
    {:verdict (cond (str/includes? text "PROMOTED") "PROMOTED"
                    (str/includes? text "CANDIDATE") "CANDIDATE"
                    (str/includes? text "REGRESSION") "REGRESSION"
                    :else "?")
     :dominant (grab #"dominant kind share\s+([0-9.]+)")
     :kinds (grab #"kinds above 15%\s+(\d+)")
     :domains (grab #"domain rows\s+([0-9.]+)")
     :confetti (grab #"confetti rows\s+([0-9.]+)")}))

(defn cell [beta mu]
  (let [cfg (cell-config beta mu)
        runs (into {} (for [i (range (:seeds cfg))
                            :let [s (+ (:seed-base cfg) i)]]
                        [s (harness/seed-run cfg :baseline s)]))
        dmg (fn [layer] (harness/mean (mapv #(get-in % [:damage layer]) (vals runs))))
        path (render-cell! beta mu)]
    (merge {:beta beta :mu mu
            :P (dmg :phenotype) :G (dmg :genotype) :X (dmg :exotype)
            :figure path}
           (ratchet path))))

(defn -main [& _]
  (println (format "SLICE 11 — blend x mu, baseline arm, %d cells, %d seeds each"
                   (* (count blend-strengths) (count mus)) (:seeds config)))
  (let [rows (doall (for [mu mus beta blend-strengths]
                      (let [r (cell beta mu)]
                        (println (format "  beta=%-5s mu=%-5s %-10s dom=%-6s kinds=%-2s dom-rows=%-6s  P=%.2f G=%.2f X=%.2f"
                                         beta mu (:verdict r) (:dominant r) (:kinds r)
                                         (:domains r) (:P r) (:G r) (:X r)))
                        (flush)
                        r)))]
    (spit "reports/exotype-blend-mu-slice11.md"
          (str "# Slice 11 — blend strength x mutation rate (baseline arm)\n\n"
               "Config: " (pr-str (dissoc config :seed-base)) "\n\n"
               "| beta | mu | verdict | dominant | kinds>15% | domain rows | confetti | P dmg | G dmg | X dmg |\n"
               "|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|\n"
               (str/join "\n"
                         (for [r rows]
                           (format "| %s | %s | %s | %s | %s | %s | %s | %.3f | %.3f | %.3f |"
                                   (:beta r) (:mu r) (:verdict r) (:dominant r) (:kinds r)
                                   (:domains r) (:confetti r) (:P r) (:G r) (:X r))))
               "\n"))
    (spit "reports/exotype-blend-mu-slice11.raw.edn" (pr-str rows))
    (println "\nwrote reports/exotype-blend-mu-slice11.md")
    (let [good (filter #(#{"PROMOTED" "CANDIDATE"} (:verdict %)) rows)]
      (println (format "PROMOTED/CANDIDATE cells: %d of %d" (count good) (count rows)))
      (doseq [r (sort-by :dominant good)]
        (println (format "  %s  beta=%s mu=%s  dominant=%s kinds=%s domain-rows=%s"
                         (:verdict r) (:beta r) (:mu r) (:dominant r) (:kinds r) (:domains r)))))))
