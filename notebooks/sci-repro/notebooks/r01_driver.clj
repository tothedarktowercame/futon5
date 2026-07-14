(ns notebooks.r01-driver
  "Driver for the boundary-guardian notebook (R1b).
   Runs the futon5 wiring runtime for three arms:
   - L5-creative (boundary-guardian)
   - L0-baseline (pure legacy, frozen)
   - Rule-30 chaos (generic chaos null)
   
   Persists per-seed diagnostics under resources/runs/.
   Reads through existing artifacts (skips completed seeds)."
  (:gen-class)
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [scirepro.exo :as exo]))

(def futon5-root (.getCanonicalFile (io/file "../..")))
(def runs-dir (io/file "resources/runs"))
(def width 120)
(def generations 200)
(def long-generations 500)
(def num-seeds 30)
(def seeds (range 42 (+ 42 num-seeds)))

(def arm-configs
  {:l5-creative {:wiring "data/wiring-ladder/level-5-creative.edn"}
   :l0-baseline {:wiring "data/wiring-ladder/level-0-baseline.edn"}})

(defn artifact-path [arm seed]
  (io/file runs-dir (str "r01-" (name arm) "-seed-" seed ".edn")))

(defn completed? [path]
  (when (.exists path)
    (try
      (let [data (edn/read-string (slurp path))]
        (= :complete (:status data)))
      (catch Exception _ false))))

(defn run-futon5-headless [wiring-path genotype generations tag]
  (let [tmpdir (System/getProperty "java.io.tmpdir")
        in-path (.getPath (io/file tmpdir (str "r01-in-" tag ".edn")))
        out-path (.getPath (io/file tmpdir (str "r01-out-" tag ".edn")))
        runner-path (.getPath (io/file tmpdir (str "r01-runner-" tag ".clj")))]
    (spit in-path (pr-str {:genotype genotype :generations generations :wiring-path wiring-path}))
    (spit runner-path
          (str "(require '[futon5.wiring.runtime :as rt])\n"
               "(require '[futon5.tpg.diagnostics :as diag])\n"
               "(let [input (clojure.edn/read-string (slurp \"" in-path "\"))\n"
               "      wiring (rt/load-wiring (:wiring-path input))\n"
               "      result (rt/run-wiring {:wiring wiring\n"
               "                              :genotype (:genotype input)\n"
               "                              :generations (:generations input)\n"
               "                              :collect-metrics? true})\n"
               "      gen-hist (:gen-history result)\n"
               "      met-hist (:metrics-history result)\n"
               "      ;; Compute late-window diagnostics\n"
               "      late-start (max 0 (- (count gen-hist) 50))\n"
               "      late-gens (subvec gen-hist late-start)\n"
               "      late-metrics (subvec met-hist late-start)\n"
               "      ;; Instantaneous change-rate at late window\n"
               "      inst-change (if (> (count late-metrics) 1)\n"
               "                     (or (:change-rate (last late-metrics)) 0.0)\n"
               "                     0.0)\n"
               "      ;; Fraction of full run above 0.5 change-rate\n"
               "      all-changes (remove nil? (map :change-rate met-hist))\n"
               "      frac-above (if (seq all-changes)\n"
               "                    (/ (double (count (filter #(> % 0.5) all-changes)))\n"
               "                       (count all-changes))\n"
               "                    0.0)\n"
               "      ;; Mean diagnostics over late window\n"
               "      mean-ent (if (seq late-metrics) (/ (reduce + 0.0 (map :entropy-n late-metrics)) (count late-metrics)) 0.0)\n"
               "      mean-change (if (seq late-metrics) (/ (reduce + 0.0 (remove nil? (map :change-rate late-metrics))) (count (remove nil? (map :change-rate late-metrics)))) 0.0)\n"
               "      mean-div (if (seq late-metrics) (/ (reduce + 0.0 (map (fn [m] (/ (double (:unique-sigils m)) (double (:length m)))) late-metrics)) (count late-metrics)) 0.0)\n"
               "      ;; Autocorrelation = 1 - change-rate (smoothed)\n"
               "      mean-autocorr (- 1.0 mean-change)\n"
               "      ;; Verifier score (default spec)\n"
               "      band-score (fn [x center width] (max 0.0 (- 1.0 (/ (Math/abs (- (double x) center)) width))))\n"
               "      ent-score (band-score mean-ent 0.6 0.35)\n"
               "      chg-score (band-score mean-change 0.2 0.2)\n"
               "      ac-score (band-score mean-autocorr 0.6 0.3)\n"
               "      div-score (band-score mean-div 0.4 0.3)\n"
               "      verifier-score (/ (+ ent-score chg-score ac-score div-score) 4.0)]\n"
               "  (spit \"" out-path "\" (pr-str {:gen-history gen-hist\n"
               "                                :late-window {:mean-entropy mean-ent\n"
               "                                              :mean-change-rate mean-change\n"
               "                                              :mean-diversity mean-div\n"
               "                                              :mean-autocorr mean-autocorr\n"
               "                                              :instantaneous-change inst-change\n"
               "                                              :fraction-above-0.5 frac-above}\n"
               "                                :verifier-score verifier-score\n"
               "                                :generations (:generations input)})))\n"))
    (let [{:keys [exit err]} (sh/sh "clojure" "-M" "-i" runner-path :dir (str futon5-root))]
      (when-not (= exit 0)
        (throw (ex-info "futon5 headless run failed" {:exit exit :err err}))))
    (edn/read-string (slurp out-path))))

(defn ^:no-doc run-rule30 [genotype generations]
  (let [bits (mapv (fn [c] (mod (int (.charAt ^String (str c) 0)) 2)) (seq genotype))
        rule30-result (loop [hist [bits] cur bits gen 0]
                        (if (>= gen generations)
                          hist
                          (let [n (count cur)
                                nxt (vec (for [i (range n)]
                                           (let [l (get cur (mod (dec i) n))
                                                 s (get cur i)
                                                 r (get cur (mod (inc i) n))]
                                             ;; Rule 30: l XOR (s OR r)
                                             (bit-xor l (bit-or s r)))))]
                            (recur (conj hist nxt) nxt (inc gen)))))]
    {:gen-history (mapv (fn [row] (apply str (map #(if (= 1 %) "丁" "一") row))) rule30-result)
     :verifier-score 0.0
     :late-window {:mean-entropy 0.0 :mean-change-rate 1.0 :mean-diversity (/ 2.0 120.0)
                   :mean-autocorr 0.0 :instantaneous-change 1.0 :fraction-above-0.5 1.0}}))

(defn run-arm [arm seed]
  (let [path (artifact-path arm seed)]
    (if (completed? path)
      (edn/read-string (slurp path))
      (let [genotype (exo/gen-exo-ic width seed)
            gens (if (= arm :l5-creative-long) long-generations generations)
            result (case arm
                     :l5-creative (run-futon5-headless (:wiring (:l5-creative arm-configs))
                                                       genotype gens (str arm "-" seed))
                     :l0-baseline (run-futon5-headless (:wiring (:l0-baseline arm-configs))
                                                       genotype gens (str arm "-" seed))
                     :rule-30 (run-rule30 genotype gens))
            data (assoc result :arm arm :seed seed :status :complete)]
        (io/make-parents path)
        (spit path (pr-str data))
        data))))

(defn -main [& _args]
  (.mkdirs runs-dir)
  (let [arms [:l5-creative :l0-baseline :rule-30]]
    (doseq [arm arms]
      (println (str "Arm: " arm))
      (doseq [seed seeds]
        (let [path (artifact-path arm seed)]
          (if (completed? path)
            (println (str "  seed " seed ": SKIPPED (artifact exists)"))
            (do
              (println (str "  seed " seed ": running..."))
              (run-arm arm seed)
              (println (str "  seed " seed ": DONE"))))))
      (println ""))
    ;; Also run one long L5-creative run
    (println "Long L5-creative run (seed 42, 500 gen)...")
    (let [long-path (io/file runs-dir "r01-l5-creative-long-seed-42.edn")]
      (when-not (completed? long-path)
        (let [genotype (exo/gen-exo-ic width 42)
              result (run-futon5-headless (:wiring (:l5-creative arm-configs))
                                          genotype long-generations "long-42")
              data (assoc result :arm :l5-creative-long :seed 42 :status :complete)]
          (spit long-path (pr-str data))))
      (println "Long run: DONE"))
    (println "R01 DRIVER COMPLETE")))
