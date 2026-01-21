#!/usr/bin/env bb
(ns update-iiching-ratchet-evidence
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- usage []
  (str/join
   "\n"
   ["Update iiching flexiarg files with ratchet evidence from exoevolve logs."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/update_iiching_ratchet_evidence.clj [options]"
    ""
    "Options:"
    "  --logs CSV            Comma-separated exoevolve log paths."
    "  --iiching-root PATH   iiching library root (default futon3/library/iiching)."
    "  --manifest PATH       Exotype manifest (default futon5/resources/exotype-program-manifest.edn)."
    "  --min-delta X         Minimum weighted delta (default 0.3)."
    "  --max-best N          Keep best N evidence entries (default 10)."
    "  --max-recent N        Keep most recent N entries (default 10)."
    "  --dry-run             Do not write files, just report."
    "  --help                Show this message."]))

(defn- parse-double [s]
  (try (Double/parseDouble s) (catch Exception _ nil)))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-csv [s]
  (->> (str/split (or s "") #",")
       (map str/trim)
       (remove str/blank?)
       vec))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--logs" flag)
          (recur (rest more) (assoc opts :logs (parse-csv (first more))))

          (= "--iiching-root" flag)
          (recur (rest more) (assoc opts :iiching-root (first more)))

          (= "--manifest" flag)
          (recur (rest more) (assoc opts :manifest (first more)))

          (= "--min-delta" flag)
          (recur (rest more) (assoc opts :min-delta (parse-double (first more))))

          (= "--max-best" flag)
          (recur (rest more) (assoc opts :max-best (parse-int (first more))))

          (= "--max-recent" flag)
          (recur (rest more) (assoc opts :max-recent (parse-int (first more))))

          (= "--dry-run" flag)
          (recur more (assoc opts :dry-run true))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- read-edn-lines [path]
  (->> (slurp path)
       str/split-lines
       (map str/trim)
       (remove str/blank?)
       (map edn/read-string)))

(defn- now []
  (.toString (java.time.Instant/now)))

(defn- manifest->index
  [path]
  (let [entries (edn/read-string (slurp path))]
    (reduce (fn [m [idx entry]]
              (assoc m [(:sigil entry) (:tier entry)] idx))
            {}
            (map-indexed vector entries))))

(defn- choose-index
  [sigil tier idx-map]
  (or (get idx-map [sigil tier])
      (get idx-map [sigil :super])
      (get idx-map [sigil :local])))

(defn- pad3 [n]
  (format "%03d" (int n)))

(defn- flexiarg-path
  [root idx]
  (str (io/file root (str "exotype-" (pad3 idx) ".flexiarg"))))

(defn- evidence-from-run
  [log-path row]
  (let [ratchet (:ratchet row)
        exotype (:exotype row)
        delta-weighted (:delta-weighted ratchet)
        delta-score (:delta-score ratchet)
        gate (:gate ratchet)
        score (get-in row [:score :final])]
    (when (and (map? ratchet)
               (number? delta-weighted)
               (number? delta-score)
               (not (= gate :blocked)))
      {:log log-path
       :run-id (:run/id row)
       :seed (:seed row)
       :window (get-in ratchet [:curriculum :window])
       :delta-weighted delta-weighted
       :delta-score delta-score
       :delta-mean-n (:delta-mean-n ratchet)
       :delta-q50-n (:delta-q50-n ratchet)
       :scale (:scale ratchet)
       :threshold (get-in ratchet [:curriculum :threshold])
       :final-score score
       :exotype (select-keys exotype [:sigil :tier :params])
       :gate gate
       :at (now)})))

(defn- select-evidence
  [logs min-delta]
  (let [min-delta (double (or min-delta 0.3))]
    (->> logs
         (mapcat (fn [path]
                   (->> (read-edn-lines path)
                        (filter #(= (:event %) :run))
                        (keep #(evidence-from-run path %)))))
         (filter #(and (number? (:delta-weighted %))
                       (> (:delta-weighted %) min-delta)))
         vec)))

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
  [lines start end]
  (let [body (->> (subvec (vec lines) (inc start) end)
                  (map str/trim)
                  (remove str/blank?)
                  (remove #(str/starts-with? % ";;"))
                  (str/join "\n"))]
    (when (seq body)
      (edn/read-string (str "{" body "}")))))

(defn- render-section
  [{:keys [evidence notes]}]
  (let [lines [(str "@exotype-ratchet")
               (str "  :evidence " (pr-str (or evidence [])))]
        lines (if notes
                (conj lines (str "  :notes " (pr-str notes)))
                lines)]
    lines))

(defn- merge-evidence
  [existing incoming {:keys [max-best max-recent]}]
  (let [max-best (max 0 (int (or max-best 10)))
        max-recent (max 0 (int (or max-recent 10)))
        keyfn (fn [e] [(:log e) (:run-id e) (:seed e) (:window e) (get-in e [:exotype :sigil]) (get-in e [:exotype :tier])])
        merged (->> (concat existing incoming)
                    (reduce (fn [m e] (assoc m (keyfn e) e)) {})
                    vals)
        best (->> merged
                  (sort-by (fn [e] (double (or (:delta-weighted e) -1e9))) >)
                  (take max-best))
        recent (->> merged
                    (sort-by (fn [e] (or (:run-id e) 0)) >)
                    (take max-recent))]
    (vec (distinct (concat best recent)))))

(defn- update-flexiarg
  [path new-evidence opts]
  (let [lines (str/split-lines (slurp path))
        ratchet (find-section lines "@exotype-ratchet")
        program (find-section lines "@exotype-program")
        existing (when ratchet
                   (get (parse-section-map lines (:start ratchet) (:end ratchet)) :evidence))
        merged (merge-evidence (or existing []) new-evidence opts)
        section-lines (render-section {:evidence merged})
        lines' (cond
                 ratchet
                 (vec (concat (subvec (vec lines) 0 (:start ratchet))
                              section-lines
                              (subvec (vec lines) (:end ratchet))))
                 program
                 (vec (concat (subvec (vec lines) 0 (:start program))
                              section-lines
                              (subvec (vec lines) (:start program))))
                 :else
                 (vec (concat lines section-lines)))]
    {:updated-lines lines'
     :existing-count (count (or existing []))
     :merged-count (count merged)}))

(defn- write-flexiarg!
  [path lines]
  (spit path (str/join "\n" lines) :append false))

(defn -main [& args]
  (let [{:keys [help unknown logs iiching-root manifest min-delta max-best max-recent dry-run]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println (usage)))
      (or (empty? logs) (nil? manifest)) (do
                                           (println "Missing --logs or --manifest.")
                                           (println (usage)))
      :else
      (let [iiching-root (or iiching-root "futon3/library/iiching")
            idx-map (manifest->index manifest)
            evidence (select-evidence logs min-delta)
            grouped (group-by (fn [e]
                                (let [sigil (get-in e [:exotype :sigil])
                                      tier (get-in e [:exotype :tier])]
                                  (choose-index sigil tier idx-map)))
                              evidence)
            grouped (dissoc grouped nil)]
        (println "Evidence entries:" (count evidence))
        (println "Targets:" (count grouped))
        (doseq [[idx entries] grouped]
          (let [path (flexiarg-path iiching-root idx)]
            (if-not (.exists (io/file path))
              (println "Missing flexiarg:" path)
              (let [{:keys [updated-lines existing-count merged-count]} (update-flexiarg path entries
                                                                                         {:max-best max-best
                                                                                          :max-recent max-recent})]
                (println (format "%s: %d -> %d evidence" path existing-count merged-count))
                (when-not dry-run
                  (write-flexiarg! path updated-lines))))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
