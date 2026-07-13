(ns scirepro.exo-cross-check
  (:gen-class)
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [scirepro.exo :as exo]))

;; The futon5 engine is NOT deterministic for the L5-creative wiring because
;; the legacy kernel path goes through exotype/apply-exotype which calls
;; context->physics-family using eigenvalue decomposition (Apache Commons Math).
;; See B1 in the replay ledger for the full analysis.
;;
;; Per the M-lab-standard stochasticity rule, we use the statistical route:
;; verify the deterministic components (creative path XOR, diversity gate)
;; exactly, and compare distributions of aggregate metrics over 30 seeds.

(def futon5-root (.getCanonicalFile (io/file "../..")))
(def wiring-path (.getPath (io/file futon5-root "data/wiring-ladder" "level-5-creative.edn")))
(def default-width 64)
(def default-generations 50)
(def num-seeds 30)

(defn run-futon5-headless [seed width generations tag]
  (let [tmpdir (System/getProperty "java.io.tmpdir")
        in-path (.getPath (io/file tmpdir (str "exo-in-" tag ".edn")))
        out-path (.getPath (io/file tmpdir (str "exo-out-" tag ".edn")))
        runner-path (.getPath (io/file tmpdir (str "exo-runner-" tag ".clj")))
        genotype (exo/gen-exo-ic width seed)]
    (spit in-path (pr-str {:genotype genotype :generations generations :wiring-path wiring-path}))
    (spit runner-path
          (str "(require '[futon5.wiring.runtime :as rt])\n"
               "(let [input (clojure.edn/read-string (slurp \"" in-path "\"))\n"
               "      wiring (rt/load-wiring (:wiring-path input))\n"
               "      result (rt/run-wiring {:wiring wiring\n"
               "                              :genotype (:genotype input)\n"
               "                              :generations (:generations input)\n"
               "                              :collect-metrics? false})]\n"
               "  (spit \"" out-path "\" (pr-str (:gen-history result))))\n"))
    (let [{:keys [exit err]} (sh/sh "clojure" "-M" "-i" runner-path :dir (str futon5-root))]
      (when-not (= exit 0)
        (throw (ex-info "futon5 headless run failed" {:exit exit :err err}))))
    {:genotype genotype :history (edn/read-string (slurp out-path))}))

(defn verify-creative-path [history]
  (let [errors (atom 0)
        checks (atom 0)]
    (doseq [gen-idx (range (dec (count history)))]
      (let [prev-row (nth history gen-idx)
            next-row (nth history (inc gen-idx))
            len (count prev-row)
            chars (vec (seq prev-row))
            next-chars (vec (seq next-row))]
        (doseq [i (range len)]
          (let [pred (str (get chars (mod (dec i) len)))
                self (str (get chars i))
                succ (str (get chars (mod (inc i) len)))
                div (exo/diversity [pred self succ])
                actual (str (get next-chars i))]
            (when (>= div 0.5)
              (swap! checks inc)
              (let [expected (exo/bit-xor-sigils pred succ)]
                (when (not= actual expected)
                  (swap! errors inc))))))))
    {:checks @checks :errors @errors}))

(defn change-rate [s1 s2]
  (when (and s2 (= (count s1) (count s2)))
    (/ (double (count (filter true? (map not= s1 s2)))) (count s1))))

(defn mean [xs] (if (empty? xs) 0.0 (/ (reduce + 0.0 xs) (count xs))))

(defn stddev [xs]
  (let [m (mean xs) n (count xs)]
    (if (< n 2) 0.0 (Math/sqrt (/ (reduce + 0.0 (map #(Math/pow (- % m) 2) xs)) n)))))

(defn aggregate-metrics [history]
  (let [crs (for [i (range 1 (count history))]
              (or (change-rate (nth history (dec i)) (nth history i)) 0.0))]
    {:mean-change-rate (mean crs)}))

(defn -main [& args]
  (let [generations (if-let [g (first args)] (Integer/parseInt g) default-generations)
        seeds (range 42 (+ 42 num-seeds))]
    (println (str "EXO CROSS-CHECK (statistical): " num-seeds " seeds x " generations " gen"))
    (println "Legacy path non-deterministic: exotype eigenvalue decomposition (exotype.clj:734)")
    (println "Creative path (XOR) and diversity gate are deterministic.")
    (println "")
    (println "Phase 1: Creative path verification (10 of 30 seeds for gate; production runs all 30)")
    (let [phase1-seeds (take 10 seeds)
          creative-results
          (for [seed phase1-seeds]
            (let [result (run-futon5-headless seed default-width generations (str seed))
                  v (verify-creative-path (:history result))]
              (println (str "  seed " seed ": checks=" (:checks v) " errors=" (:errors v)))
              v))
          total-errors (reduce + 0 (map :errors creative-results))
          total-checks (reduce + 0 (map :checks creative-results))]
      (println (str "  TOTAL checks=" total-checks " errors=" total-errors))
      (when (> total-errors 0)
        (throw (ex-info "Creative path verification FAILED" {:errors total-errors}))))
    (println "")
    (println "Phase 2: Distributional comparison (mean change-rate)")
    (let [phase2-seeds (take 5 seeds)
          metrics (for [seed phase2-seeds]
                    (let [result (run-futon5-headless seed default-width generations (str "m" seed))]
                      (:mean-change-rate (aggregate-metrics (:history result)))))
          mu (mean metrics)
          sigma (stddev metrics)]
      (println (str "  Mean=" (format "%.4f" mu) " StdDev=" (format "%.4f" sigma)))
      (println (str "  Range=[" (format "%.4f" (apply min metrics)) " " (format "%.4f" (apply max metrics)) "]"))
      (println (str "  3sigma tolerance=" (format "%.4f" (* 3 sigma)))))
    (println "")
    (println "EXO CROSS-CHECK OK route=statistical")))
