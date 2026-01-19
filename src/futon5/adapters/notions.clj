(ns futon5.adapters.notions
  "Reuse futon3a pattern search + flexiarg parsing, with optional CT enrichment."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon.notions :as notions]))

(defn search
  "Pass-through search for patterns, using futon3a's notions index."
  [query & {:keys [method top-k] :or {method :auto top-k 5}}]
  (notions/search query :method method :top-k top-k))

(defn get-pattern-details
  "Fetch flexiarg details for a pattern id via futon3a's parser."
  [pattern-id]
  (notions/get-pattern-details pattern-id))

(defn- parse-section
  "Parse a flexiarg section as EDN. Returns a map or {:raw ...} on failure."
  [section-text]
  (let [wrapped (str "[" section-text "\n]")]
    (try
      (let [forms (edn/read-string wrapped)]
        (if (even? (count forms))
          (apply hash-map forms)
          {:raw forms}))
      (catch Exception _
        {:raw section-text}))))

(defn- read-section
  "Read a named flexiarg section (e.g. :ct-interpretation) from a file path."
  [path section-tag]
  (when (and path (.exists (io/file path)))
    (let [lines (str/split-lines (slurp path))
          tag (str "@" (name section-tag))
          idx (->> lines
                   (map-indexed vector)
                   (filter (fn [[_ line]]
                             (str/starts-with? (str/trim line) tag)))
                   ffirst)]
      (when (number? idx)
        (let [body (->> lines
                        (drop (inc idx))
                        (take-while (fn [line]
                                      (not (str/starts-with? (str/trim line) "@"))))
                        (str/join "\n"))]
          (when-not (str/blank? (str/trim body))
            (parse-section body)))))))

(defn- read-interpretations
  "Read interpretation blocks from a flexiarg file path."
  [path]
  {:ct-interpretation (read-section path :ct-interpretation)
   :ant-interpretation (read-section path :ant-interpretation)
   :mmca-interpretation (read-section path :mmca-interpretation)})

(defn pattern-by-id
  "Load a pattern by id with flexiarg details and interpretation blocks."
  [pattern-id]
  (when pattern-id
    (let [details (get-pattern-details pattern-id)
          interpretations (read-interpretations (:path details))]
      (cond-> details
        (:ct-interpretation interpretations) (assoc :ct-interpretation (:ct-interpretation interpretations))
        (:ant-interpretation interpretations) (assoc :ant-interpretation (:ant-interpretation interpretations))
        (:mmca-interpretation interpretations) (assoc :mmca-interpretation (:mmca-interpretation interpretations))))))

(defn enrich-results
  "Enrich search results with TSV + flexiarg details, plus interpretation blocks."
  [results]
  (let [base (notions/enrich-results results)]
    (mapv (fn [r]
            (let [interpretations (read-interpretations (:path r))]
              (cond-> r
                (:ct-interpretation interpretations)
                (assoc :ct-interpretation (:ct-interpretation interpretations))
                (:ant-interpretation interpretations)
                (assoc :ant-interpretation (:ant-interpretation interpretations))
                (:mmca-interpretation interpretations)
                (assoc :mmca-interpretation (:mmca-interpretation interpretations)))))
          base)))

(defn enrich-search
  "Search and return enriched patterns (including :ct-interpretation when present)."
  [query & {:keys [method top-k] :or {method :auto top-k 5}}]
  (-> (search query :method method :top-k top-k)
      enrich-results))
