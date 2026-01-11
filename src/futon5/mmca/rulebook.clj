(ns futon5.mmca.rulebook
  "Programmatic access to the core 64-rule catalog derived from the I Ching."
  (:require [clojure.string :as str]
            [futon5.mmca.iching :as iching]))

(def ^:private stage-cycle
  [:init :observe :decide :act :reflect :adapt :meta])

(defn- annotate-stages [lines]
  (->> lines
       (map vector (cycle stage-cycle) lines)
       (map (fn [[stage {:keys [label text] :as entry}]]
              (assoc entry :stage stage
                           :summary (format "%s — %s" label text))))
       vec))

(defn- summarize-rule [entry]
  (let [judgement (:judgement entry "")
        synopsis (->> (:lines entry)
                      (map :text)
                      (take 2)
                      (str/join " / "))]
    (cond
      (seq judgement) judgement
      (seq synopsis) synopsis
      :else (format "Hexagram %s" (:name entry)))))

(defn- hexagram->rule [entry]
  {:rule/id (format "iching/%02d" (:index entry))
   :rule/name (:name entry)
   :rule/header (:header entry)
   :rule/judgement (:judgement entry)
   :rule/source :iching
   :rule/summary (summarize-rule entry)
   :rule/stages (annotate-stages (:lines entry))
   :rule/commentary (:commentary entry)
   :rule/notes (:notes entry)})

(def iching-rules
  "Vector of 64 canonical MMCA rule descriptors derived from the hexagrams."
  (delay (mapv hexagram->rule @iching/core-hexagrams)))

(defn all-rules [] @iching-rules)

(defn rule-by-id [id]
  (some (fn [entry] (when (= (:rule/id entry) id) entry)) @iching-rules))

(defn rule-by-name [name]
  (some (fn [entry] (when (= (:rule/name entry) name) entry)) @iching-rules))
