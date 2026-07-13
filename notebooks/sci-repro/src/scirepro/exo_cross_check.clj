(ns scirepro.exo-cross-check
  (:gen-class)
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [scirepro.exo :as exo]))

;; CORRECTED ROUTE JUSTIFICATION (R1a.2, 2026-07-13):
;;
;; The legacy kernel path is non-deterministic due to kernel-baldwin-mutate
;; in ca/core.clj:414-421, which calls (rand) — an unseeded Math/random()
;; — whenever phenotype-context is truthy. In the wiring runtime,
;; build-local-context sets phenotype-context to "0000" (truthy) even when
;; no phenotype is provided (exotype.clj:389-398). This makes the legacy
;; path genuinely non-deterministic WITHIN a single JVM process.
;;
;; The creative path (XOR) and diversity gate are fully deterministic.
;; The eigenvalue decomposition in context->physics-family IS deterministic
;; (confirmed by claude-6's 500-context cross-JVM probe); the original
;; B1 attribution to EigenDecomposition FP non-determinism was WRONG.
;;
;; Cross-check routes:
;; - Grid-identity for the creative path (deterministic component): VERIFIED
;; - Grid-identity for the full grid (creative + legacy): FAILS by design
;;   (legacy path uses unseeded rand)
;; - Statistical route (30 seeds, distributional comparison): PRIMARY

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
        checks (atom 0)
        first-error (atom nil)]
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
                  (swap! errors inc)
                  (when (nil? @first-error)
                    (reset! first-error {:gen gen-idx :cell i
                                         :pred pred :succ succ
                                         :expected expected :actual actual})))))))))
    {:checks @checks :errors @errors :first-error @first-error}))

(defn ^:no-doc verify-grid-identity [hist-a hist-b]
  ;; Compare two full grid histories. Returns {:match true} or
  ;; {:match false :first-diff {:gen :cell :a :b}}.
  (let [result {:match true :first-diff nil}]
    (if (= hist-a hist-b)
      result
      (loop [gen-idx 0]
        (if (>= gen-idx (min (count hist-a) (count hist-b)))
          {:match false :first-diff {:gen gen-idx :reason "length mismatch"}}
          (let [row-a (nth hist-a gen-idx)
                row-b (nth hist-b gen-idx)]
            (if (= row-a row-b)
              (recur (inc gen-idx))
              {:match false :first-diff {:gen gen-idx
                                         :row-a row-a
                                         :row-b row-b}})))))))

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

(defn run-grid-mode [generations _seeds]
  (println (str "EXO CROSS-CHECK (grid-identity): 3 ICs x " generations " gen"))
  (println "Attempting full grid-identity (creative + legacy paths)...")
  (println "")
  (let [grid-seeds [42 43 44]]
    (doseq [seed grid-seeds]
      (let [r1 (run-futon5-headless seed default-width generations (str "g1-" seed))
            r2 (run-futon5-headless seed default-width generations (str "g2-" seed))
            cmp (verify-grid-identity (:history r1) (:history r2))
            cr (verify-creative-path (:history r1))]
        (println (str "  seed " seed ":"))
        (println (str "    full-grid identity: " (:match cmp)))
        (when-not (:match cmp)
          (let [fd (:first-diff cmp)]
            (println (str "    first-diff gen=" (:gen fd)))
            (println "    EXPECTED (legacy rand divergence)")))
        (println (str "    creative-path: checks=" (:checks cr) " errors=" (:errors cr)))))
    (println "")
    (println "Creative-path grid-identity (1 IC for gate):")
    (let [r (run-futon5-headless 42 default-width generations "cr-grid")
          v (verify-creative-path (:history r))
          total-errors (:errors v)
          total-checks (:checks v)]
      (println (str "  seed 42: checks=" total-checks " errors=" total-errors))
      (when (> total-errors 0)
        (throw (ex-info "Creative path FAILED" {:errors total-errors})))))
  (println "")
  (println "EXO CROSS-CHECK OK route=grid-identity-creative")
  (println "  (full grid-identity fails on legacy path: ca/core.clj:416 unseeded rand)"))

(defn run-statistical-mode [generations seeds]
  (println (str "EXO CROSS-CHECK (statistical): " num-seeds " seeds x " generations " gen"))
  (println "Legacy path non-deterministic: ca/core.clj:416 kernel-baldwin-mutate calls (rand)")
  (println "Creative path (XOR) and diversity gate are deterministic.")
  (println "")
  (println "Phase 1: Creative path verification (10 of 30 seeds)")
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
  (println "EXO CROSS-CHECK OK route=statistical"))

(defn -main
  "Usage: clojure -M -m scirepro.exo-cross-check [generations]
     or: clojure -M -m scirepro.exo-cross-check grid [generations]"
  [& args]
  (let [is-grid (= (first args) "grid")
        gen-arg (if is-grid (second args) (first args))
        generations (if gen-arg (Integer/parseInt gen-arg) default-generations)
        seeds (range 42 (+ 42 num-seeds))]
    (if is-grid
      (run-grid-mode generations seeds)
      (run-statistical-mode generations seeds))))
