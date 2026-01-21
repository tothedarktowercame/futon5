(ns futon5.exotic.ratchet-library
  "Read ratchet evidence/CT interpretations from iiching flexiarg files."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- parse-edn [path]
  (edn/read-string (slurp path)))

(defn- manifest-index
  [manifest]
  (reduce (fn [m [idx entry]]
            (assoc m [(:sigil entry) (:tier entry)] idx))
          {}
          (map-indexed vector manifest)))

(defn- resolve-index
  [idx-map sigil tier]
  (or (get idx-map [sigil tier])
      (get idx-map [sigil :super])
      (get idx-map [sigil :local])))

(defn- pad3 [n]
  (format "%03d" (int n)))

(defn- flexiarg-path
  [root idx]
  (str (io/file root (str "exotype-" (pad3 idx) ".flexiarg"))))

(defn- section-indices
  [lines]
  (keep-indexed (fn [idx line]
                  (when (re-find #"^@\\S+" line)
                    [idx line]))
                lines))

(defn- find-section
  [lines section]
  (let [markers (section-indices lines)
        start (some (fn [[idx line]]
                      (when (= line section) idx))
                    markers)]
    (when start
      (let [end (->> markers
                     (map first)
                     (filter #(> % start))
                     sort
                     first)]
        {:start start
         :end (or end (count lines))}))))

(defn- parse-section-map
  [lines section]
  (when-let [{:keys [start end]} (find-section lines section)]
    (let [body (->> (subvec (vec lines) (inc start) end)
                    (map str/trim)
                    (remove str/blank?)
                    (remove #(str/starts-with? % ";;"))
                    (str/join "\n"))]
      (when (seq body)
        (edn/read-string (str "{" body "}"))))))

(defn- read-flexiarg
  [path]
  (let [lines (str/split-lines (slurp path))]
    {:ratchet (parse-section-map lines "@exotype-ratchet")
     :lift (parse-section-map lines "@exotype-lift")}))

(defonce ^:private cache (atom {}))

(defn evidence-for
  "Return ratchet evidence + CT template (if any) for a sigil+tier pair.
  opts: {:iiching-root PATH :manifest PATH}"
  [{:keys [iiching-root manifest]} sigil tier]
  (let [key [iiching-root manifest sigil tier]]
    (if-let [cached (get @cache key)]
      cached
      (let [root (or iiching-root "futon3/library/iiching")
            manifest (parse-edn (or manifest "futon5/resources/exotype-program-manifest.edn"))
            idx-map (manifest-index manifest)
            idx (resolve-index idx-map sigil tier)
            path (when idx (flexiarg-path root idx))
            data (when (and path (.exists (io/file path)))
                   (read-flexiarg path))
            ratchet (:ratchet data)
            lift (:lift data)
            evidence (vec (or (:evidence ratchet) []))
            ct-template (or (:ct-template ratchet)
                            (:ct-template lift)
                            (some :ct-template evidence))
            result (when data
                     {:path path
                      :evidence evidence
                      :evidence-count (count evidence)
                      :ct-template ct-template})]
        (swap! cache assoc key result)
        result))))
