(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.string :as str])
(import '[java.io FileInputStream FileOutputStream BufferedOutputStream]
        '[java.security MessageDigest]
        '[java.util.zip GZIPOutputStream])

;; Orbit-complete S8 index.  The original 2014 Elisp remains the dynamics
;; authority; this driver plans mirror orbits, fingerprints every determining
;; input, launches independent batch-Emacs workers, and builds the embedding
;; table from compact per-representative manifests.

(def width 60)
(def steps 120)
(def seeds [0 1 2])
(def mirror [0 3 2 1 6 5 4 7])
(def legacy-to-standard [0 1 2 4 3 5 6 7])
(def class-4-rules [110 124 137 193 54 147 106 120 169 225 41 97])
(def strategy-version 1)
(def root "data/propagator-index")

(def anchors
  [{:name :rotate+2 :sigma [2 3 4 5 6 7 0 1] :expect :live}
   {:name :rotate+1 :sigma [1 2 3 4 5 6 7 0] :expect :collapsed}
   {:name :two-4-cycles :sigma [1 2 3 0 5 6 7 4] :expect :collapsed}
   {:name :three-five :sigma [1 2 0 4 5 6 7 3] :expect :live}])

(def protocol
  {:width width :steps steps :seeds seeds :invert true
   :census-shape [3 121 256] :rule-numbering :standard-wolfram
   :orbit-action {:mirror mirror} :strategy-version strategy-version})

(defn sha256-bytes [bytes]
  (let [d (MessageDigest/getInstance "SHA-256")]
    (apply str (map #(format "%02x" (bit-and 0xff %)) (.digest d bytes)))))

(defn sha256-string [s] (sha256-bytes (.getBytes (str s) "UTF-8")))

(defn sha256-file [path]
  (with-open [in (FileInputStream. (io/file path))]
    (let [d (MessageDigest/getInstance "SHA-256")
          buf (byte-array 65536)]
      (loop []
        (let [n (.read in buf)]
          (when (pos? n) (.update d buf 0 n) (recur))))
      (apply str (map #(format "%02x" (bit-and 0xff %)) (.digest d))))))

(defn source-files []
  (let [vendor (->> (file-seq (io/file "vendor/metaca"))
                    (filter #(.isFile %))
                    (remove #(str/includes? (.getPath %) "/.git/"))
                    (map #(.getPath %)))]
    (sort (concat ["scripts/propagator_index.clj"
                   "scripts/propagator_index_worker.el"
                   "scripts/propagator_orbit_probe.el"
                   "scripts/elisp-harness/run.el"
                   "scripts/elisp-harness/clcompat.el"
                   "data/propagator-index/orbit-proof.edn"]
                  vendor))))

(defn fingerprint []
  (let [inputs (mapv (fn [path]
                       (when-not (.exists (io/file path))
                         (throw (ex-info "Fingerprint input missing" {:path path})))
                       [path (sha256-file path)])
                     (source-files))]
    (subs (sha256-string (pr-str {:protocol protocol :inputs inputs})) 0 16)))

(defn permutations [xs]
  (if (empty? xs)
    [[]]
    (mapcat (fn [x]
              (map #(into [x] %) (permutations (remove #{x} xs)))) xs)))

(def all-sigmas (delay (mapv vec (permutations (range 8)))))

(defn conjugate [sigma action]
  (mapv (fn [k] (nth action (nth sigma (nth action k)))) (range 8)))

(defn lex-min [a b] (if (neg? (compare a b)) a b))
(defn orbit-key [sigma] (lex-min sigma (conjugate sigma mirror)))

(def preferred-representatives
  (into {} (map (fn [{:keys [sigma]}] [(orbit-key sigma) sigma]) anchors)))

(defn representative [sigma]
  (let [k (orbit-key sigma)] (get preferred-representatives k k)))

(defn representatives
  "Orbit representatives, anchors first, then DETERMINISTICALLY SHUFFLED.

  The shuffle matters and is not cosmetic. Sorted order made any partial build a
  lexicographic prefix of sigma-space -- a biased sample, so clustering or any
  population claim computed over an unfinished build would be biased too, silently.
  Shuffled, EVERY PREFIX IS A UNIFORM RANDOM SAMPLE of the 20,256 orbits, so a build
  that dies at 60% still supports honest population statistics over the whole space.

  Seeded (42) so the order is reproducible across JVMs and resumes: the same build
  visits the same representatives in the same sequence. Per-representative manifests
  key off sigma, not position, so re-ordering never invalidates cached work -- the 352
  already complete stay complete."
  []
  (let [reps (->> @all-sigmas (map representative) distinct sort vec)
        priority (mapv :sigma anchors)
        shuffled (let [al (java.util.ArrayList. ^java.util.Collection reps)]
                   (java.util.Collections/shuffle al (java.util.Random. 42))
                   (vec al))
        ordered (vec (distinct (concat priority shuffled)))]
    (when-not (= 20256 (count ordered))
      (throw (ex-info "Mirror orbit count mismatch" {:got (count ordered)})))
    ordered))

(defn perm-id [sigma] (str/join "" sigma))
(defn artifact-file [fp sigma]
  (io/file root "artifacts" fp (str "sigma-" (perm-id sigma) ".edn.gz")))
(defn manifest-file [fp sigma]
  (io/file root "manifests" fp (str "sigma-" (perm-id sigma) ".edn")))

(defn load-manifest [fp sigma]
  (let [mf (manifest-file fp sigma) af (artifact-file fp sigma)]
    (when (and (.exists mf) (.exists af))
      (try
        (let [m (edn/read-string (slurp mf))]
          (when (and (= :complete (:status m))
                     (= fp (:fingerprint m))
                     (= sigma (:sigma m))
                     (= 3 (count (:runs m)))
                     (= (:artifact-bytes m) (.length af))
                     (= (:artifact-sha256 m) (sha256-file af)))
            m))
        (catch Exception _ nil)))))

(defn elisp-task [fp sigma]
  (str "(:perm [" (str/join " " sigma) "]"
       " :fingerprint " (pr-str fp)
       " :width " width " :steps " steps
       " :seeds [" (str/join " " seeds) "]"
       " :artifact " (pr-str (.getAbsolutePath (artifact-file fp sigma)))
       " :manifest " (pr-str (.getAbsolutePath (manifest-file fp sigma))) ")"))

(defn run-worker! [worker-id task-file]
  (let [args ["emacs" "--batch" "-Q"
              "-l" "scripts/elisp-harness/run.el"
              "-l" "scripts/propagator_index_worker.el"
              "--eval" (str "(propagator-index-run-batch "
                             (pr-str (.getAbsolutePath task-file)) ")")]
        pb (doto (ProcessBuilder. ^java.util.List args)
             (.inheritIO))]
    (.put (.environment pb) "PROPAGATOR_WORKER_ID" (str worker-id))
    (let [exit (.waitFor (.start pb))]
      (when-not (zero? exit)
        (throw (ex-info "Propagator index worker failed"
                        {:worker worker-id :exit exit}))))))

(defn run-parallel! [fp sigmas workers]
  (let [missing (vec (remove #(load-manifest fp %) sigmas))
        chunks (->> missing
                    (map-indexed vector)
                    (group-by #(mod (first %) workers))
                    (sort-by key)
                    (mapv (fn [[worker pairs]]
                            [worker (mapv second pairs)])))]
    (println (format "representatives: %d selected, %d cached, %d missing"
                     (count sigmas) (- (count sigmas) (count missing))
                     (count missing)))
    (when (seq missing)
      (let [jobs
            (mapv
             (fn [[worker xs]]
               (let [task-file (java.io.File/createTempFile
                                (str "propagator-index-" worker "-") ".el")]
                 (spit task-file
                       (str "(" (str/join "\n" (map #(elisp-task fp %) xs)) ")\n"))
                 (future
                   (try (run-worker! worker task-file)
                        (finally (.delete task-file))))))
             chunks)]
        (doseq [job jobs] @job)))))

(defn standard-rule-mirror [rule]
  (let [m [0 4 2 6 1 5 3 7]]
    (reduce + (for [k (range 8) :when (bit-test rule (nth m k))]
                (bit-shift-left 1 k)))))

(def standard-mirror-map (mapv standard-rule-mirror (range 256)))

(defn gzip-spit! [path contents]
  (.mkdirs (.getParentFile (io/file path)))
  (let [tmp (java.io.File/createTempFile ".partial-" ".gz"
                                         (.getParentFile (io/file path)))]
    (try
      (with-open [out (GZIPOutputStream.
                       (BufferedOutputStream. (FileOutputStream. tmp)))]
        (.write out (.getBytes contents "UTF-8")))
      (java.nio.file.Files/move
       (.toPath tmp) (.toPath (io/file path))
       (into-array java.nio.file.CopyOption
                   [java.nio.file.StandardCopyOption/REPLACE_EXISTING
                    java.nio.file.StandardCopyOption/ATOMIC_MOVE]))
      (finally (when (.exists tmp) (.delete tmp))))))

(defn mean [xs] (/ (double (reduce + xs)) (count xs)))

(defn anchor-verdict [manifest expect]
  (let [runs (:runs manifest)
        deaths (mapv :death runs)
        rules (mapv :rules runs)
        rules-mean (mean rules)
        pass? (case expect
                :live (and (every? #(= steps %) deaths)
                           (<= 20 rules-mean 40))
                :collapsed (and (<= rules-mean 2.0)
                                (some #(< % steps) deaths)))]
    {:death deaths :rules rules :rules-mean rules-mean :pass? pass?}))

(defn write-report! [fp status coverage anchors]
  (let [complete? (= :complete status)
        path "holes/labs/M-aif-tokamak/propagator_index_REPORT.md"
        rows (apply str
                    (for [{:keys [name sigma representative available verdict]} anchors]
                      (str "| `" (clojure.core/name name) "` | `" sigma "` | `" representative
                           "` | " available " | " (get verdict :death) " | "
                           (get verdict :rules) " | " (get verdict :pass?) " |\n")))]
    (spit path
          (str "# S8 propagator composition index\n\n"
               "**Status: " (if complete? "COMPLETE" "PARTIAL") ". Coverage: "
               (:sigmas-represented coverage) " / 40,320 σ through "
               (:representatives-complete coverage) " / 20,256 proven mirror-orbit "
               "representatives.**\n\n"
               "The index records three seeded trajectories per representative at width 60 "
               "for 120 updates. Each trajectory is a dense `121 × 256` census in standard "
               "Wolfram rule numbering, accompanied by survival, terminal rule count, total "
               "phenotype activity, and the supplied class-4 population over time. No "
               "embedding or post-hoc regime labels are applied.\n\n"
               "## Orbit reduction\n\n"
               "Left–right reflection was proven pathwise on the original 2014 engine; "
               "0↔1 complementation was rejected because its fixed-zero boundaries break "
               "the symmetry. Burnside reduction therefore yields exactly 20,256 orbits. "
               "The machine index enumerates all 40,320 σ and gives each representative, "
               "artifact path, and standard-rule mirror-axis transform.\n\n"
               "## Anchors\n\n"
               "| anchor | sigma | representative | available | death | terminal rules | pass |\n"
               "|---|---|---|---|---|---|---|\n" rows "\n"
               "## Storage and provenance\n\n"
               "Fingerprint `" fp "` covers this driver, its lexical-binding Elisp worker, "
               "the orbit witness, harness, protocol, and every vendored MetaCA input. Each "
               "compressed census has an atomic manifest containing its SHA-256 and compact "
               "measures; resume rejects absent, stale, or checksum-mismatched files.\n\n"
               "- Full machine table: `data/propagator-index/index-" fp ".edn.gz`\n"
               "- Compact build state: `data/propagator-index/coverage.edn`\n"
               "- Census artifacts: `data/propagator-index/artifacts/" fp "/`\n"
               "- Per-representative manifests: `data/propagator-index/manifests/" fp "/`\n\n"
               "Reproduce or resume with:\n\n"
               "```sh\nPROPAGATOR_INDEX_WORKERS=8 clojure -M -e "
               "'(load-file \"scripts/propagator_index.clj\")'\n```\n"))))

(defn build-index! [fp reps]
  (let [available (into {} (keep (fn [sigma]
                                   (when-let [m (load-manifest fp sigma)]
                                     [sigma m])) reps))
        entries
        (mapv (fn [sigma]
                (let [rep (representative sigma)
                      transform (if (= sigma rep) :identity :mirror)]
                  {:sigma sigma :representative rep :transform transform
                   :rule-axis transform
                   :available (contains? available rep)
                   :artifact (str "artifacts/" fp "/sigma-"
                                  (perm-id rep) ".edn.gz")}))
              @all-sigmas)
        completed (count available)
        represented (count (filter :available entries))
        complete? (= 20256 completed)
        anchor-results
        (mapv (fn [{:keys [name sigma expect]}]
                (let [rep (representative sigma)
                      m (get available rep)]
                  {:name name :sigma sigma :representative rep
                   :available (boolean m)
                   :verdict (when m (anchor-verdict m expect))})) anchors)
        index {:status (if complete? :complete :partial)
               :fingerprint fp :protocol protocol
               :coverage {:representatives-complete completed
                          :representatives-total 20256
                          :sigmas-represented represented
                          :sigmas-total 40320}
               :orbit-proof {:action :left-right-mirror
                             :legacy-index-map mirror
                             :fixed-sigmas 192 :orbit-count 20256
                             :complement-rejected true}
               :census {:shape-per-artifact [3 121 256]
                        :rule-numbering :standard-wolfram
                        :legacy-to-standard legacy-to-standard
                        :class-4-rules class-4-rules
                        :mirror-standard-rule-map standard-mirror-map}
               :anchors anchor-results :entries entries}
        index-path (str root "/index-" fp ".edn.gz")
        coverage-path (str root "/coverage.edn")]
    (gzip-spit! index-path (pr-str index))
    (spit coverage-path
          (pr-str (select-keys index [:status :fingerprint :protocol :coverage
                                     :orbit-proof :census :anchors])))
    (write-report! fp (:status index) (:coverage index) anchor-results)
    (println "coverage" (:coverage index))
    (doseq [a anchor-results] (println "anchor" a))
    (when (and complete?
               (not-every? #(get-in % [:verdict :pass?]) anchor-results))
      (throw (ex-info "Completed index failed anchor gate"
                      {:anchors anchor-results})))
    index))

(defn run-index! []
  (let [fp (fingerprint)
        reps (representatives)
        workers (max 1 (Integer/parseInt
                        (or (System/getenv "PROPAGATOR_INDEX_WORKERS") "8")))
        limit-env (System/getenv "PROPAGATOR_INDEX_LIMIT")
        limit (when limit-env (Integer/parseInt limit-env))
        selected (if limit (vec (take limit reps)) reps)]
    (println "fingerprint" fp)
    (println "mirror orbits" (count reps) "workers" workers
             "requested this invocation" (count selected))
    (run-parallel! fp selected workers)
    (build-index! fp reps)))

(do (run-index!) nil)
