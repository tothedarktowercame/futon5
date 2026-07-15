(require '[futon5.ca.core :as ca] '[futon5.wiring.runtime :as rt]
         '[futon5.mmca.particle-detection :as pd] '[clojure.data.json :as json]
         '[clojure.java.io :as io] '[clojure.edn :as edn] '[clojure.string :as str])

;; THE SUITE RERUN — every wiring, one runtime, one convention, with phenotypes.
;;
;; RESUMABLE: each (wiring, seed) is its own artefact on disk, keyed by a
;; fingerprint of everything that determines it. A timeout loses at most ONE
;; artefact; re-running picks up where it stopped. (The first draft of this
;; script was a single 8-minute block that SIGTERMed on its timeout and lost
;; every result — hence this.)
;;
;; Protocol is the ladder's own canonical one (data/wiring-ladder/README.md:56-58,
;; = level-5-creative.edn :reproducibility :standard-protocol):
;;   seeds [4242 238310129 352362012] | width 120 | generations 100
;;
;; WHY THE KEY, and not just r01_driver's :status :complete check:
;; r01_driver.clj:30-36 treats an artefact as done if it says :complete, with no
;; record of WHAT PRODUCED IT. Its 91 cached artefacts in
;; notebooks/sci-repro/resources/runs/ were built before ca/core's neighbourhood
;; table was standardised on 2026-07-15 (it agreed with Wolfram on 16/256 rules;
;; now 256/256), so every one of them is stale — and `completed?` cannot tell.
;; A cache without an input fingerprint silently serves results from code that no
;; longer exists. So each artefact here records the sha of its wiring file, of the
;; engine sources that determine the dynamics, and of the protocol; a mismatch
;; rebuilds instead of lying. Build state is carried through to the page.
;;
;; Usage:  clojure -M -e '(load-file "scripts/suite_rerun.clj")'   ; resumes
;;         SUITE_FORCE=1 clojure -M -e '(load-file "scripts/suite_rerun.clj")'

(def SEEDS [4242 238310129 352362012])
(def WIDTH 120)
(def GENS 100)
(def CACHE "resources/suite-runs")
(def FORCE (boolean (System/getenv "SUITE_FORCE")))

(def suite
  [[:nb01-metaca-core "notebooks/sci-repro/data/wirings/nb01-metaca-core.edn"]
   [:nb02-blending    "notebooks/sci-repro/data/wirings/nb02-blending.edn"]
   [:nb03-phenotype   "notebooks/sci-repro/data/wirings/nb03-phenotype.edn"]
   [:nb04-mutation    "notebooks/sci-repro/data/wirings/nb04-mutation.edn"]
   [:l0-baseline  "data/wiring-ladder/level-0-baseline.edn"]
   [:l1-legacy    "data/wiring-ladder/level-1-legacy.edn"]
   [:l2-context   "data/wiring-ladder/level-2-context.edn"]
   [:l3-diversity "data/wiring-ladder/level-3-diversity.edn"]
   [:l4-gate      "data/wiring-ladder/level-4-gate.edn"]
   [:l5-creative  "data/wiring-ladder/level-5-creative.edn"]
   [:xun-wind     "data/wiring-ladder/巽-wind.edn"]])

;; --------------------------------------------------------------------------- ;;
;; freshness key
;; --------------------------------------------------------------------------- ;;

(defn- sha [^String s]
  (let [d (java.security.MessageDigest/getInstance "SHA-256")]
    (apply str (take 12 (map #(format "%02x" %) (.digest d (.getBytes s "UTF-8")))))))

(def engine-files
  "Every source whose contents change the numbers. If you touch the dynamics or
   add a component, add the file here — otherwise the cache serves stale results
   and reports them as fresh, which is the exact failure this key exists to stop.

   THIS SCRIPT IS IN THE LIST. The measurement code determines the measurement
   just as much as the engine does: without it, editing `measure` below would
   leave every artefact matching its key while holding numbers no current code
   produces — the same silent-stale failure the key exists to prevent, one level
   up. (Caught 2026-07-15 by asking what would happen if I optimised `measure`.)"
  ["scripts/suite_rerun.clj"
   "src/futon5/ca/core.clj"
   "src/futon5/wiring/runtime.clj"
   "src/futon5/xenotype/generator.clj"
   "src/futon5/xenotype/interpret.clj"
   "src/futon5/mmca/particle_detection.clj"
   "src/futon5/mmca/local_causal_states.clj"
   "resources/xenotype-generator-components.edn"
   "resources/futon5/sigils.edn"])

(def engine-sha
  (delay (sha (str/join " " (map #(if (.exists (io/file %)) (slurp %) "") engine-files)))))

(defn artefact-key [wiring-path]
  {:wiring-sha (sha (slurp wiring-path))
   :engine-sha @engine-sha
   :protocol {:seeds SEEDS :width WIDTH :generations GENS}
   :key-version 1})

(defn artefact-path [id seed]
  (io/file CACHE (str "suite-" (name id) "-seed-" seed ".edn")))

(defn load-artefact
  "The cached artefact iff COMPLETE and its key matches what the current inputs
   would produce; else nil (missing or stale)."
  [id seed k]
  (let [f (artefact-path id seed)]
    (when (and (.exists f) (not FORCE))
      (try (let [d (edn/read-string (slurp f))]
             (when (and (= :complete (:status d)) (= k (:key d))) d))
           (catch Exception _ nil)))))

(defn artefact-state
  "Why we are (or are not) reusing — so the page can show build status honestly."
  [id seed k]
  (let [f (artefact-path id seed)]
    (cond FORCE :forced
          (not (.exists f)) :missing
          :else (try (let [d (edn/read-string (slurp f))]
                       (cond (not= :complete (:status d)) :incomplete
                             (not= k (:key d)) :stale
                             :else :cached))
                     (catch Exception _ :unreadable)))))

;; --------------------------------------------------------------------------- ;;
;; measurement
;; --------------------------------------------------------------------------- ;;

(defn- mean [xs] (if (seq xs) (/ (double (reduce + xs)) (count xs)) 0.0))
(defn bits-row [s] (mapv #(Character/digit ^char % 2) (seq s)))
(defn gen-ints [s] (mapv #(Integer/parseInt (ca/bits-for (str %)) 2) (seq s)))
(defn- cmu-of [g] (:model-states (pd/observe g {:past-depth 2 :future-depth 1
                                                :alpha 0.01 :min-support 20})))

(defn run-one [path seed]
  (ca/with-seed seed
    (let [geno (ca/with-seed seed (ca/random-sigil-string WIDTH))
          phe (ca/with-seed (+ seed 1) (apply str (repeatedly WIDTH #(if (< (ca/rnd) 0.5) "0" "1"))))]
      (rt/run-wiring {:wiring (rt/load-wiring path) :genotype geno
                      :phenotype phe :generations (dec GENS)}))))

(defn death-time
  "First t after which the phenotype NEVER changes again, or nil if it stays
   alive. 'It died at t=60' is a fact a run-level mean cannot express."
  [rows]
  (let [n (count rows)
        changed? (fn [t] (not= (nth rows t) (nth rows (dec t))))]
    (first (for [t (range 1 n) :when (not-any? changed? (range t n))] t))))

(defn cmu-profile
  "Cmu over the whole run, over the LIVE window, and swept in sliding windows.

   WHY, and this is a real defect in the first draft of this suite: every number
   it produced was a mean over all 100 generations. A run that is edge-of-chaos
   for 40 steps and then freezes scores as FROZEN — the transient is averaged
   away. Transient EoC is exactly what a search for EoC should be finding (Joe,
   2026-07-15, on the paper's Figure 8: 'definitely was [EoC] (until it died)').
   So the run-level mean is kept, but it is no longer the only number: the peak
   over a sliding window is what would catch a run that lived and then died.

   Cmu costs ~0.5s against a ~26s run, so the sweep is nearly free."
  [p]
  (let [n (count p)
        dt (death-time p)
        live (subvec (vec p) 0 (max 12 (or dt n)))
        wsize 40 stride 20
        starts (range 0 (max 1 (- n wsize -1)) stride)
        wins (vec (for [s starts]
                    {:t s :cmu (cmu-of (subvec (vec p) s (min n (+ s wsize))))}))]
    {:cmu (cmu-of (vec p))                    ; run-level: kept, no longer alone
     :cmu-live (cmu-of live)
     :cmu-peak (if (seq wins) (apply max (map :cmu wins)) 0)
     :cmu-windows wins
     :death-t dt}))

(defn measure
  "One artefact's worth of measurement. Deliberately ONE run: a 120x100 run costs
   ~26s (the wiring runtime evaluates the whole diagram per cell per generation —
   11,880 graph walks), while the Cmu on it costs ~0.5s. So the run is the entire
   budget, and anything that runs the wiring twice doubles the suite. The
   reproducibility check is therefore done ONCE PER WIRING (see `reproducible?`),
   not once per seed: it is a property of the wiring and engine, not of the seed."
  [path seed]
  (let [r (run-one path seed)
        g (:gen-history r) p (mapv bits-row (:phe-history r))
        n (count g)]
    (merge
     (cmu-profile p)
     {:churn (mean (for [t (range 1 n)]
                    (count (remove true? (map = (seq (nth g t)) (seq (nth g (dec t))))))))
     :div0 (count (distinct (seq (first g))))
     :div-end (count (distinct (seq (last g))))
     :g-frozen-pct (* 100.0 (/ (count (filter #(= (nth g %) (nth g (dec %))) (range 1 n))) (dec n)))
     :p-alive (count (remove true? (map = (nth p (- n 2)) (nth p (dec n)))))
     :p-density (let [c (apply concat p)] (/ (double (reduce + c)) (count c)))
      :p-frozen-pct (* 100.0 (/ (count (filter #(= (nth p %) (nth p (dec %))) (range 1 n))) (dec n)))})))

(defn reproducible?
  "Same seed twice -> byte-identical genotype history. Once per WIRING (a
   property of the wiring + engine, not of the seed), at a short horizon: this
   costs two more full runs, and at ~26s each that is the whole budget."
  [path]
  (let [g (ca/with-seed 4242 (ca/random-sigil-string WIDTH))
        ph (ca/with-seed 4243 (apply str (repeatedly WIDTH #(if (< (ca/rnd) 0.5) "0" "1"))))
        run #(ca/with-seed 4242 (:gen-history (rt/run-wiring
                                               {:wiring (rt/load-wiring path) :genotype g
                                                :phenotype ph :generations 20})))]
    (= (run) (run))))

(defn phe-influences?
  "Does the phenotype actually reach this diagram's genotype dynamics? Until
   2026-07-15 the runtime never put :phe in the cell context, so the answer was
   NO for every wiring regardless of what its diagram declared."
  [path]
  (let [g (ca/with-seed 4242 (ca/random-sigil-string WIDTH))
        mk (fn [s] (ca/with-seed s (apply str (repeatedly WIDTH #(if (< (ca/rnd) 0.5) "0" "1")))))
        run (fn [ph] (ca/with-seed 4242 (:gen-history (rt/run-wiring
                                                       {:wiring (rt/load-wiring path) :genotype g
                                                        :phenotype ph :generations 12}))))]
    (not= (run (mk 7)) (run (mk 9)))))

(defn build! [id path seed]
  (let [k (artefact-key path)
        t0 (System/currentTimeMillis)
        m (measure path seed)
        d {:status :complete :key k :id (name id) :seed seed
           :built-at (str (java.time.Instant/now))
           :build-ms (- (System/currentTimeMillis) t0)
           :measured m}]
    (.mkdirs (io/file CACHE))
    (spit (artefact-path id seed) (pr-str d))
    d))

;; --------------------------------------------------------------------------- ;;
;; drive
;; --------------------------------------------------------------------------- ;;

(println "=== SUITE RERUN (resumable) — 11 wirings x 3 seeds, 120x100, with phenotypes ===")
(println (format "  engine fingerprint: %s | cache: %s%s" @engine-sha CACHE (if FORCE " | FORCE" "")))
(println)
(flush)

(def artefacts
  (doall
   (for [[id path] suite, seed SEEDS]
     (let [k (artefact-key path)
           state (artefact-state id seed k)
           cached (load-artefact id seed k)
           d (or cached (build! id path seed))]
       (println (format "  %-18s seed %-10d %-14s %6dms  Cmu=%2s alive=%s"
                        (name id) seed
                        (case state
                          :cached "CACHED"
                          :stale "STALE->rebuilt"
                          :missing "built"
                          :forced "FORCED"
                          (name state))
                        (:build-ms d 0)
                        (get-in d [:measured :cmu]) (get-in d [:measured :p-alive])))
       (flush)
       (assoc d :build-state state)))))

(println "\n=== assembling ===")
(def by-id (group-by :id artefacts))
(def results
  (vec (for [[id path] suite]
         (let [as (get by-id (name id))
               ms (map :measured as)
               w (rt/load-wiring path)]
           {:id (name id)
            :label (get-in w [:meta :label] (name id))
            :level (get-in w [:meta :level])
            :cohort (if (str/starts-with? (name id) "nb") "sci-repro" "ladder")
            :description (get-in w [:meta :description])
            :diagram (:diagram w)
            :reproducible (reproducible? path)
            :phe-influences (phe-influences? path)
            :churn (mean (map :churn ms))
            :div0 (mean (map :div0 ms)) :div-end (mean (map :div-end ms))
            :g-frozen-pct (mean (map :g-frozen-pct ms))
            :p-alive (mean (map :p-alive ms)) :p-density (mean (map :p-density ms))
            :p-frozen-pct (mean (map :p-frozen-pct ms))
            :cmu (mean (map :cmu ms))
            :cmu-per-seed (mapv :cmu ms)
            ;; build provenance, surfaced on the page
            :build {:states (mapv (comp name :build-state) as)
                    :built-at (mapv :built-at as)
                    :engine-sha @engine-sha
                    :wiring-sha (:wiring-sha (artefact-key path))
                    :all-cached (every? #(= :cached (:build-state %)) as)}}))))

(defn eca-grid [rule seed]
  (let [rng (java.util.Random. (long seed))
        step (fn [row] (let [n (count row)]
                         (vec (for [i (range n)]
                                (let [l (nth row (mod (dec i) n)) c (nth row i) r (nth row (mod (inc i) n))]
                                  (if (bit-test rule (+ (* 4 l) (* 2 c) r)) 1 0))))))]
    (vec (take GENS (iterate step (vec (repeatedly WIDTH #(if (.nextBoolean rng) 1 0))))))))

(println "\n=== ECA calibration (same protocol; the only EoC ground truth we have) ===")
(def eca-ref
  (vec (for [[rule label] [[110 "class 4 COMPLEX"] [54 "class 4 complex"]
                           [90 "class 3 chaotic (XOR)"] [30 "class 3 chaotic"] [0 "class 1 frozen"]]]
         (let [gs (mapv #(eca-grid rule %) SEEDS)
               f (fn [g] (let [n (count g)]
                           {:p-alive (count (remove true? (map = (nth g (- n 2)) (nth g (dec n)))))
                            :p-density (let [c (apply concat g)] (/ (double (reduce + c)) (count c)))
                            :p-frozen-pct (* 100.0 (/ (count (filter #(= (nth g %) (nth g (dec %))) (range 1 n))) (dec n)))
                            :cmu (:model-states (pd/observe g {:past-depth 2 :future-depth 1
                                                               :alpha 0.01 :min-support 20}))}))
               ms (mapv f gs)]
           (println (format "  ECA %-4d %-24s Cmu=%.1f" rule label (mean (map :cmu ms))))
           {:id (str "ECA " rule) :label label :rule rule
            :p-alive (mean (map :p-alive ms)) :p-density (mean (map :p-density ms))
            :p-frozen-pct (mean (map :p-frozen-pct ms)) :cmu (mean (map :cmu ms))}))))

(def panels
  (into {} (for [[id path] suite]
             (let [r (run-one path (first SEEDS))]
               [(name id) {:geno (mapv gen-ints (:gen-history r))
                           :phe (mapv bits-row (:phe-history r))}]))))
(def eca-panels
  (into {} (for [rule [110 90 30 0]] [(str "eca" rule) {:phe (eca-grid rule (first SEEDS))}])))

(spit "/tmp/suite_results.json"
      (json/write-str {:results results :eca eca-ref :panels panels :ecaPanels eca-panels
                       :protocol {:seeds SEEDS :width WIDTH :generations GENS}
                       :engineSha @engine-sha
                       :builtAt (str (java.time.Instant/now))}))
(println (format "\nartefacts: %s" (pr-str (frequencies (map :build-state artefacts)))))
(println "wrote /tmp/suite_results.json")
