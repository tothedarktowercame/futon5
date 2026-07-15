(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[futon5.ca.core :as ca]
         '[futon5.mmca.diagonal-transport :as transport])
(import '[java.security MessageDigest])

;; Execution of genotype_transport_PREREG.md.  Definitions in this file are
;; committed before the identity null is observed; a failed decisive gate is a
;; terminal scientific result, not a parameter-search branch.

(def width 60)
(def steps 120)
(def seeds [0 1 2 3 4])
(def profile-options {:window-size 20 :stride 10 :max-speed 3})
(def l0-seed 4242)
(def cache-root "data/genotype-transport")
(def output-path (str cache-root "/gates.edn"))

(def regimes
  {:identity {:perm [0 1 2 3 4 5 6 7] :kind :busy-null}
   :rotate+2 {:perm [2 3 4 5 6 7 0 1] :kind :live}
   :sigma-51276043 {:perm [5 1 2 7 6 0 4 3] :kind :live}
   :three-plus-five {:perm [1 2 0 4 5 6 7 3] :kind :live}
   :rotate+1 {:perm [1 2 3 4 5 6 7 0] :kind :dead}
   :two-four-cycles {:perm [1 2 3 0 5 6 7 4] :kind :dead}})

(def live-labels [:rotate+2 :sigma-51276043 :three-plus-five])
(def dead-labels [:rotate+1 :two-four-cycles])

(defn- sha256 [s]
  (let [d (MessageDigest/getInstance "SHA-256")]
    (apply str (map #(format "%02x" (bit-and 0xff %))
                    (.digest d (.getBytes (str s) "UTF-8"))))))

(defn- source-files []
  ["scripts/genotype_transport.clj"
   "scripts/genotype_transport_worker.el"
   "scripts/elisp-harness/run.el"
   "scripts/elisp-harness/clcompat.el"
   "src/futon5/mmca/diagonal_transport.clj"
   "vendor/metaca/256ca-2014-12-29-BUGGY.el"])

(def protocol
  {:width width :steps steps :recorded-rows (inc steps) :invert true
   :seeds seeds :l0-seed l0-seed :profile profile-options
   :representation :canonical-eight-bit-planes
   :aggregation :arithmetic-mean-of-eight-bilateral-plane-scores
   :run-summary :median-window-score})

(defn- fingerprint []
  (subs
   (sha256
    (pr-str {:protocol protocol
             :inputs (mapv (fn [path]
                             [path (sha256 (slurp path))])
                           (source-files))}))
   0 16))

(defn- perm-id [perm] (str/join "" perm))

(defn- artefact-file [fp label seed]
  (io/file cache-root "runs" fp
           (format "%s-sigma-%s-seed-%d.edn"
                   (name label) (perm-id (get-in regimes [label :perm])) seed)))

(defn- load-artefact [fp label seed]
  (let [f (artefact-file fp label seed)
        expected (get-in regimes [label :perm])]
    (when (.exists f)
      (try
        (let [x (edn/read-string (slurp f))]
          (when (and (= :complete (:status x))
                     (= fp (:fingerprint x))
                     (= (name label) (:label x))
                     (= expected (:perm x))
                     (= seed (:seed x))
                     (= {:width width :steps steps :invert true} (:protocol x)))
            x))
        (catch Exception _ nil)))))

(defn- elisp-task [fp label seed]
  (let [perm (get-in regimes [label :perm])]
    (str "(:label " (pr-str (name label))
         " :perm [" (str/join " " perm) "]"
         " :seed " seed " :width " width " :steps " steps
         " :fingerprint " (pr-str fp)
         " :path " (pr-str (.getAbsolutePath (artefact-file fp label seed))) ")")))

(defn- run-command! [args]
  (let [process (-> (ProcessBuilder. ^java.util.List (mapv str args))
                    (.inheritIO)
                    (.start))
        exit (.waitFor process)]
    (when-not (zero? exit)
      (throw (ex-info "External command failed" {:exit exit :args args})))))

(defn- run-labels! [fp labels]
  (let [missing (vec (for [label labels seed seeds
                           :when (nil? (load-artefact fp label seed))]
                       [label seed]))]
    (println (format "gate batch %s: %d cached, %d to run"
                     (str/join "," (map name labels))
                     (- (* (count labels) (count seeds)) (count missing))
                     (count missing)))
    (when (seq missing)
      (let [task-file (java.io.File/createTempFile "genotype-transport-" ".el")]
        (try
          (spit task-file
                (str "(" (str/join "\n"
                                    (map (fn [[label seed]]
                                           (elisp-task fp label seed))
                                         missing)) ")\n"))
          (run-command! ["emacs" "--batch" "-Q"
                         "-l" "scripts/elisp-harness/run.el"
                         "-l" "scripts/genotype_transport_worker.el"
                         "--eval" (str "(genotype-transport-run-batch "
                                       (pr-str (.getAbsolutePath task-file)) ")")])
          (finally (.delete task-file)))))))

(defn- measured-run [fp label seed]
  (let [artefact (or (load-artefact fp label seed)
                     (throw (ex-info "Missing completed genotype artefact"
                                     {:label label :seed seed})))
        measured (:measured artefact)
        profile (transport/genotype-profile (:genotype measured) profile-options)]
    {:label label :kind (get-in regimes [label :kind])
     :perm (get-in regimes [label :perm]) :seed seed
     :death (:death measured) :rules (:rules measured)
     :phenotype-activity (:activity measured)
     :median-score (transport/median-score profile)
     :profile profile}))

(defn- frozen-l0-check []
  (let [initial (ca/with-seed l0-seed (ca/random-sigil-string width))
        row (mapv (comp ca/bits-for str) initial)
        profile (transport/genotype-profile
                 (vec (repeat (inc steps) row)) profile-options)]
    {:source :l0-baseline-initial-genotype
     :seed l0-seed :heterogeneous-rules (count (distinct row))
     :rows (inc steps) :changing-cells 0
     :median-score (transport/median-score profile)
     :profile profile
     :passes? (and (every? zero? (map :score profile))
                   (every? zero? (map :innovation-density profile)))}))

(defn- mean [xs] (/ (double (reduce + xs)) (count xs)))

(defn- regime-summary [runs]
  (into {}
        (for [[label rs] (group-by :label runs)]
          [label {:kind (:kind (first rs))
                  :perm (:perm (first rs))
                  :median-scores (mapv :median-score (sort-by :seed rs))
                  :mean-median-score (mean (map :median-score rs))
                  :innovation-density-by-window
                  (mapv (fn [run]
                          {:seed (:seed run)
                           :densities (mapv :innovation-density (:profile run))})
                        (sort-by :seed rs))}])))

(defn- live-gate [identity-runs live-runs]
  (let [identity-by-seed (into {} (map (juxt :seed :median-score) identity-runs))
        matched (mapv (fn [run]
                        {:label (:label run) :seed (:seed run)
                         :live (:median-score run)
                         :identity (identity-by-seed (:seed run))
                         :passes? (> (:median-score run)
                                     (identity-by-seed (:seed run)))})
                      live-runs)
        live-floor (apply min (map :median-score live-runs))
        identity-ceiling (apply max (map :median-score identity-runs))]
    {:matched-seed-comparisons matched
     :all-matched-pass? (every? :passes? matched)
     :live-floor live-floor :identity-ceiling identity-ceiling
     :global-separation-pass? (> live-floor identity-ceiling)
     :passes? (and (every? :passes? matched)
                   (> live-floor identity-ceiling))}))

(let [fp (fingerprint)
      frozen (frozen-l0-check)]
  (when-not (:passes? frozen)
    (throw (ex-info "Gate A failed: a frozen genotype was not exactly zero"
                    frozen)))
  ;; Gate B is executed and persisted before any live candidate is launched.
  (run-labels! fp [:identity])
  (let [identity-runs (mapv #(measured-run fp :identity %) seeds)]
    ;; Gate C uses the already-fixed identity distribution and strict ordering.
    (run-labels! fp live-labels)
    (let [live-runs (mapv (fn [label seed] (measured-run fp label seed))
                          (mapcat #(repeat (count seeds) %) live-labels)
                          (cycle seeds))
          decisive (live-gate identity-runs live-runs)
          ;; Gate D is descriptive and is not executed when the decisive gate
          ;; has already banked the statistic.
          dead-runs (when (:passes? decisive)
                      (run-labels! fp dead-labels)
                      (mapv (fn [label seed] (measured-run fp label seed))
                            (mapcat #(repeat (count seeds) %) dead-labels)
                            (cycle seeds)))
          all-runs (vec (concat identity-runs live-runs dead-runs))
          result {:generated-at (str (java.time.Instant/now))
                  :fingerprint fp :protocol protocol
                  :headline-limitation :rejection-probe-no-positive-ground-truth
                  :gate-a-frozen-l0 frozen
                  :gate-b-identity {:runs identity-runs}
                  :gate-c-live {:runs live-runs :verdict decisive}
                  :gate-d-dead (if dead-runs
                                 {:status :measured :runs dead-runs}
                                 {:status :not-run-decisive-gate-failed})
                  :regime-summary (regime-summary all-runs)
                  :verdict (if (:passes? decisive)
                             :separates-registered-nulls-not-eoc-certification
                             :banked-fails-identity-null)}]
      (io/make-parents output-path)
      (spit output-path (pr-str result))
      (prn {:fingerprint fp
            :gate-a (:passes? frozen)
            :identity (get-in result [:regime-summary :identity :median-scores])
            :live (select-keys (:regime-summary result) live-labels)
            :gate-c decisive :gate-d (get-in result [:gate-d-dead :status])
            :verdict (:verdict result)}))))
