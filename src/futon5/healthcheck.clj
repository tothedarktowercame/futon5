(ns futon5.healthcheck
  "Healthcheck CLI for keeping futon5 \"settled\".

  This is intentionally conservative: it checks for required files and local
  deps, then runs a tiny deterministic MMCA run (no operators) as a smoke test.
  Core MMCA behavior is not modified."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [futon5.mmca.runtime :as runtime]))

(def ^:private required-resource-files
  ["resources/futon5/sigils.edn"
   "resources/futon5/sigil_patterns.edn"
   "resources/futon5/pattern-lifts.edn"])

(def ^:private expected-local-deps
  ;; Relative to futon5 repo root (directory containing deps.edn).
  ["../futon3a"
   "../futon1/apps/common"])

(defn- file-exists?
  [^java.io.File f]
  (and f (.exists f)))

(defn- find-repo-root
  "Walk up from user.dir until we find deps.edn. Returns a File or nil."
  []
  (loop [dir (io/file (System/getProperty "user.dir"))]
    (when dir
      (if (file-exists? (io/file dir "deps.edn"))
        dir
        (recur (.getParentFile dir))))))

(defn check-required-files
  "Return {:ok? bool :missing [paths...] :present [paths...] :root <abs>}."
  ([] (check-required-files (find-repo-root)))
  ([root]
   (let [root (or root (io/file (System/getProperty "user.dir")))
         present (->> required-resource-files
                      (filter (fn [p] (file-exists? (io/file root p))))
                      vec)
         missing (->> required-resource-files
                      (remove (fn [p] (file-exists? (io/file root p))))
                      vec)]
     {:ok? (empty? missing)
      :root (.getAbsolutePath root)
      :present present
      :missing missing})))

(defn check-local-deps
  "Return {:ok? bool :missing [{:path .. :hint ..}] :present [paths...] :root <abs>}."
  ([] (check-local-deps (find-repo-root)))
  ([root]
   (let [root (or root (io/file (System/getProperty "user.dir")))
         missing (->> expected-local-deps
                      (keep (fn [p]
                              (let [f (io/file root p)]
                                (when-not (file-exists? f)
                                  {:path p
                                   :abs (.getAbsolutePath f)
                                   :hint (str "Expected checkout at " (.getAbsolutePath f))}))))
                      vec)
         present (->> expected-local-deps
                      (filter (fn [p] (file-exists? (io/file root p))))
                      vec)]
     {:ok? (empty? missing)
      :root (.getAbsolutePath root)
      :present present
      :missing missing})))

(defn check-deps-edn-declares-local-deps
  "Verify that deps.edn contains :local/root entries for expected local deps.
  This is a ratchet check against accidental drift."
  ([] (check-deps-edn-declares-local-deps (find-repo-root)))
  ([root]
   (let [root (or root (io/file (System/getProperty "user.dir")))
         deps-path (io/file root "deps.edn")
         deps-data (when (file-exists? deps-path)
                     (edn/read-string (slurp deps-path)))
         local-roots (->> (vals (:deps deps-data))
                          (keep :local/root)
                          set)
         missing (->> expected-local-deps
                      (remove local-roots)
                      vec)]
     {:ok? (empty? missing)
      :root (.getAbsolutePath root)
      :declared (sort local-roots)
      :missing missing})))

(defn check-imagemagick-convert
  "Best-effort check. Returns {:ok? bool :warning <string>|nil}."
  []
  (try
    (let [{:keys [exit]} (sh/sh "bash" "-lc" "command -v convert >/dev/null 2>&1")]
      (if (zero? exit)
        {:ok? true}
        {:ok? false
         :warning "ImageMagick `convert` not found in PATH (PPM->PNG/PDF conversions will be unavailable)."}))
    (catch Exception e
      {:ok? false
       :warning (str "Unable to check for ImageMagick `convert`: " (.getMessage e))})))

(defn- resolve-tpg-python
  "Resolve Python interpreter for TPG tools."
  [root]
  (let [env-python (some-> (System/getenv "FUTON5_TPG_PYTHON") str/trim)
        venv-python (io/file root ".venv-tpg/bin/python3")]
    (cond
      (seq env-python) {:path env-python :source :env}
      (file-exists? venv-python) {:path (.getAbsolutePath venv-python) :source :venv}
      :else {:path "python3" :source :path})))

(defn check-tpg-python-toolchain
  "Best-effort TPG toolchain check (non-fatal).
   Verifies Python interpreter can import jax and z3."
  ([] (check-tpg-python-toolchain (find-repo-root)))
  ([root]
   (let [root (or root (io/file (System/getProperty "user.dir")))
         {:keys [path source]} (resolve-tpg-python root)]
     (try
       (let [{:keys [exit err]} (sh/sh path "-c" "import jax, z3; print('ok')")]
         (if (zero? exit)
           {:ok? true :path path :source source}
           {:ok? false
            :path path
            :source source
            :warning (str "TPG Python toolchain not ready via " path
                          " (" (name source) "): "
                          (if (str/blank? err)
                            "missing jax and/or z3-solver"
                            (str/trim err))
                          ". Run scripts/setup_tpg_python.sh or set FUTON5_TPG_PYTHON.")}))
       (catch Throwable t
         {:ok? false
          :path path
          :source source
          :warning (str "Unable to launch TPG Python interpreter " path
                        " (" (name source) "): " (.getMessage t)
                        ". Run scripts/setup_tpg_python.sh or set FUTON5_TPG_PYTHON.")})))))

(defn run-mmca-smoke
  "Run a tiny deterministic MMCA run (no operators, deterministic kernel).
  Returns {:ok? bool :result <map>|nil :error <string>|nil}."
  []
  (try
    ;; Avoid any kernel/operator randomness: multiplication kernel, no operators.
    ;; Genotype uses the default sigil table entry \"一\" (must exist in sigils.edn).
    (let [genotype (apply str (repeat 16 "一"))
          result (runtime/run-mmca {:genotype genotype
                                   :generations 8
                                   :mode :classic
                                   :kernel :multiplication
                                   :operators []})]
      {:ok? true :result (select-keys result [:gen-history :metrics-history])})
    (catch Throwable t
      {:ok? false
       :error (or (some-> t ex-data :cause)
                  (.getMessage t)
                  (str t))})))

(defn- println-err [& xs]
  (binding [*out* *err*]
    (apply println xs)))

(defn -main
  [& _args]
  (let [root (or (find-repo-root)
                 (io/file (System/getProperty "user.dir")))
        req (check-required-files root)
        deps (check-local-deps root)
        declared (check-deps-edn-declares-local-deps root)
        convert (check-imagemagick-convert)
        tpg-python (check-tpg-python-toolchain root)
        mmca (run-mmca-smoke)
        fatal-missing? (or (not (:ok? req))
                           (not (:ok? deps))
                           (not (:ok? declared))
                           (not (:ok? mmca)))]
    (println "futon5.healthcheck")
    (println "Root:" (:root req))
    (println)

    (println "Required files:")
    (doseq [p required-resource-files]
      (let [f (io/file root p)]
        (if (file-exists? f)
          (println "  OK " p)
          (println "  MISSING" p "->" (.getAbsolutePath f)))))
    (println)

    (println "Local deps (filesystem):")
    (doseq [p expected-local-deps]
      (let [f (io/file root p)]
        (if (file-exists? f)
          (println "  OK " p "->" (.getAbsolutePath f))
          (println "  MISSING" p "->" (.getAbsolutePath f)))))
    (println)

    (println "Local deps (deps.edn declarations):")
    (if (:ok? declared)
      (println "  OK  deps.edn declares expected :local/root entries")
      (do
        (println "  MISSING deps.edn :local/root entries:")
        (doseq [p (:missing declared)]
          (println "   -" p))))
    (println)

    (when-not (:ok? convert)
      (println-err "WARN:" (:warning convert))
      (println-err))

    (println "TPG Python toolchain (optional, needed for SMT/JAX TPG runs):")
    (if (:ok? tpg-python)
      (println "  OK " (:path tpg-python) "(" (name (:source tpg-python)) ") imports jax,z3")
      (println "  WARN" (:warning tpg-python)))
    (println)

    (println "MMCA smoke:")
    (if (:ok? mmca)
      (let [hist (get-in mmca [:result :gen-history])]
        (println "  OK  deterministic run completed"
                 "(genotypes:" (count (or hist [])) ")"))
      (do
        (println "  FAIL" (:error mmca))
        (println)))

    (when fatal-missing?
      (println-err "Healthcheck failed.")
      (System/exit 2))
    (println "Healthcheck OK.")))
