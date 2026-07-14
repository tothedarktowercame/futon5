(ns scirepro.runstore
  "Incremental keyed run-artifact store (M-lab-standard Long-run rule).

  Each run is keyed by (dynamic, seed, config-hash) — a stable key that
  identifies a unique computational run. The store supports:

  - Incremental appends: grid rows are appended every K generations so a
    killed run leaves partial progress on disk, not nothing.
  - Completion markers: a finished run is marked :complete.
  - Read-through API: given a key, return :complete (with the artifact),
    :partial (with rows-so-far + resume point), or :absent — so a driver
    can skip completed seeds and resume partial ones.

  Storage layout (under a configurable root directory):

    <root>/
      <key>/
        meta.edn      — run metadata (key, config, status, timestamps)
        rows.edn      — accumulated grid rows (appended incrementally)

  The meta.edn file tracks :status (:partial | :complete), :rows-written
  (count of rows flushed to rows.edn), and :last-updated."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :as pp]))

;; ---------------------------------------------------------------------------
;; Key construction
;; ---------------------------------------------------------------------------

(defn config-hash
  "Compute a stable hash for a run configuration map.
  Returns a short hex string suitable for use in directory names."
  [config]
  (format "%08x"
          (hash (into (sorted-map)
                      (for [[k v] config]
                        [k (if (map? v)
                             (into (sorted-map) v)
                             v)])))))

(defn run-key
  "Build the stable run key string from (dynamic, seed, config).

  Dynamic is a keyword (e.g. :blend, :mutation, :standard).
  Seed is a long.
  Config is a map of run parameters (e.g. {:width 128 :steps 500}).

  Returns a string like 'blend-150200140-a1b2c3d4'."
  [dynamic seed config]
  (let [dyn (if (keyword? dynamic) (name dynamic) (str dynamic))
        h (config-hash config)]
    (format "%s-%d-%s" dyn seed h)))

;; ---------------------------------------------------------------------------
;; Path management
;; ---------------------------------------------------------------------------

(defn- run-dir
  "Return the directory path for a given run key under ROOT."
  [root key-str]
  (io/file root key-str))

(defn- meta-path
  "Return the meta.edn path for a run."
  [root key-str]
  (io/file (run-dir root key-str) "meta.edn"))

(defn- rows-path
  "Return the rows.edn path for a run."
  [root key-str]
  (io/file (run-dir root key-str) "rows.edn"))

;; ---------------------------------------------------------------------------
;; Meta read / write
;; ---------------------------------------------------------------------------

(defn- read-meta
  "Read the metadata for a run. Returns nil if not found."
  [root key-str]
  (let [f (meta-path root key-str)]
    (when (.exists f)
      (edn/read-string (slurp f)))))

(defn- write-meta!
  "Write metadata for a run (creates the directory if needed)."
  [root key-str meta]
  (let [dir (run-dir root key-str)]
    (.mkdirs dir)
    (spit (meta-path root key-str)
          (with-out-str (pp/pprint meta)))))

;; ---------------------------------------------------------------------------
;; Row append / read
;; ---------------------------------------------------------------------------

(defn- append-rows!
  "Append ROWS (a sequence of grid rows) to the run's rows.edn file.
  Each row is written as a separate EDN form on its own line, so partial
  reads are safe (a truncated last line is simply skipped)."
  [root key-str rows]
  (let [f (rows-path root key-str)]
    (.mkdirs (.getParentFile f))
    (with-open [w (io/writer f :append true)]
      (doseq [row rows]
        (.write w (pr-str row))
        (.write w "\n")))))

(defn- read-rows
  "Read all complete rows from the run's rows.edn file.
  Returns a vector of rows. Truncated/incomplete trailing lines are skipped."
  [root key-str]
  (let [f (rows-path root key-str)]
    (if-not (.exists f)
      []
      (with-open [r (io/reader f)]
        (->> (line-seq r)
             (filter #(not (str/blank? %)))
             (keep (fn [line]
                     (try (edn/read-string {:eof nil} line)
                          (catch Exception _ nil))))
             vec)))))

;; ---------------------------------------------------------------------------
;; Public API
;; ---------------------------------------------------------------------------

(def default-root
  "Default store root directory. Relative to the sci-repro project."
  "out/runstore")

(defn init-run!
  "Initialize a new run in the store. Creates the directory and writes
  initial metadata with :status :partial and :rows-written 0.

  Options:
    :root       — store root directory (default: out/runstore)
    :dynamic    — dynamic keyword (e.g. :blend)
    :seed       — seed (long)
    :config     — config map
    :steps      — total planned steps
    :flush-every — generations between flushes (default: 50)

  Returns the run key string."
  [{:keys [root dynamic seed config steps flush-every]
    :or {root default-root
         flush-every 50}}]
  (let [key-str (run-key dynamic seed config)
        meta {:key key-str
              :dynamic dynamic
              :seed seed
              :config config
              :status :partial
              :rows-written 0
              :steps-planned steps
              :flush-every flush-every
              :created-at (str (java.time.Instant/now))
              :last-updated (str (java.time.Instant/now))}]
    (write-meta! root key-str meta)
    key-str))

(defn flush-rows!
  "Append the accumulated ROWS (grid rows since the last flush) to the
  run's artifact and update the metadata. ROWS-COUNT is the total number
  of rows written so far (including these), used to update :rows-written.

  This is the incremental checkpoint: call it every K generations so a
  killed run leaves partial progress on disk."
  ([root key-str rows rows-count]
   (let [meta (or (read-meta root key-str)
                  (throw (ex-info "runstore: no run found for key"
                                  {:key key-str :root root})))]
     (append-rows! root key-str rows)
     (write-meta! root key-str
                  (assoc meta
                         :rows-written rows-count
                         :last-updated (str (java.time.Instant/now))))))
  ([key-str rows rows-count]
   (flush-rows! default-root key-str rows rows-count)))

(defn mark-complete!
  "Mark a run as complete and optionally append final rows.
  Sets :status :complete and records the completion timestamp."
  ([root key-str]
   (let [meta (or (read-meta root key-str)
                  (throw (ex-info "runstore: no run found for key"
                                  {:key key-str :root root})))]
     (write-meta! root key-str
                  (assoc meta
                         :status :complete
                         :completed-at (str (java.time.Instant/now))
                         :last-updated (str (java.time.Instant/now))))))
  ([root key-str final-rows rows-count]
   (when (seq final-rows)
     (flush-rows! root key-str final-rows rows-count))
   (mark-complete! root key-str))
  ([key-str]
   (mark-complete! default-root key-str)))

(defn lookup
  "Read-through API: given a key, return the run's status and artifact.

  Returns one of:
    {:status :complete  :rows [...]  :meta {...}}   — run finished, full grid
    {:status :partial   :rows [...]  :meta {...}    — run in progress, rows-so-far
    {:status :absent}                                — no run found for this key

  For :partial runs, :rows contains all rows flushed so far. The resume point
  is (:rows-written meta), which lookup reconciles to the actual rows on disk
  — so a crash between the row append and the meta write cannot make a
  resuming driver re-append already-persisted rows."
  ([root key-str]
   (if-let [meta (read-meta root key-str)]
     (let [rows (read-rows root key-str)
           ;; Authoritative resume point = rows actually on disk. meta's
           ;; :rows-written can lag if a crash struck between the row append
           ;; and the meta write inside flush-rows!; the file is the truth, so
           ;; a resuming driver never re-appends already-persisted rows.
           meta* (assoc meta :rows-written (count rows))]
       {:status (:status meta)
        :rows rows
        :meta meta*})
     {:status :absent}))
  ([key-str]
   (lookup default-root key-str)))

(defn list-runs
  "List all runs in the store. Returns a vector of meta maps.
  Optional FILTER-FN is applied to each meta map."
  ([root]
   (let [dir (io/file root)]
     (if-not (.exists dir)
       []
       (->> (.listFiles dir)
            (filter #(.isDirectory %))
            (keep #(read-meta root (.getName %)))
            (sort-by :created-at)
            vec))))
  ([]
   (list-runs default-root)))

(defn cleanup!
  "Remove a run's artifacts from the store. Use with care."
  ([root key-str]
   (let [dir (run-dir root key-str)]
     (when (.exists dir)
       (doseq [f (reverse (file-seq dir))]
         (.delete f)))))
  ([key-str]
   (cleanup! default-root key-str)))
