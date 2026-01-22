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
    "  --lift PATH           Lift registry for CT templates (default futon5/resources/exotype-xenotype-lift.edn)."
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

          (= "--lift" flag)
          (recur (rest more) (assoc opts :lift (first more)))

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

(defn- manifest-data
  [path]
  (let [entries (edn/read-string (slurp path))
        idx-map (reduce (fn [m [idx entry]]
                          (assoc m [(:sigil entry) (:tier entry)] idx))
                        {}
                        (map-indexed vector entries))]
    {:entries entries
     :index idx-map}))

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

(defn- lift-info
  [lift]
  (reduce (fn [m {:keys [sigil ct-template pattern-id source]}]
            (if sigil
              (-> m
                  (update-in [sigil :pattern-ids] (fnil conj []) pattern-id)
                  (update-in [sigil :pattern-evidence] (fnil conj []) {:pattern-id pattern-id
                                                                       :source source
                                                                       :source-path "futon5/resources/exotype-xenotype-lift.edn"})
                  (update-in [sigil :ct-template] (fn [curr]
                                                    (or curr ct-template))))
              m))
          {}
          (:patterns lift)))

(defn- evidence-from-run
  [log-path row ct-template]
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
       :ct-template ct-template
       :ct-source (when ct-template :lift-registry)
       :final-score score
       :exotype (select-keys exotype [:sigil :tier :params])
       :gate gate
       :at (now)})))

(defn- select-evidence
  [logs min-delta lift]
  (let [min-delta (double (or min-delta 0.3))]
    (->> logs
         (mapcat (fn [path]
                   (->> (read-edn-lines path)
                        (filter #(= (:event %) :run))
                        (keep (fn [row]
                                (let [sigil (get-in row [:exotype :sigil])
                                      ct-template (get-in lift [sigil :ct-template])]
                                  (evidence-from-run path row ct-template)))))))
         (filter #(and (number? (:delta-weighted %))
                       (> (:delta-weighted %) min-delta)))
         vec)))

(defn- section-indices
  [lines]
  (keep-indexed (fn [idx line]
                  (when (re-find #"^@[^ \t]+" line)
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
  [{:keys [evidence notes ct-template]}]
  (let [lines [(str "@exotype-ratchet")
               (str "  :ct-template " (pr-str ct-template))
               (str "  :evidence " (pr-str (or evidence [])))]
        lines (if notes
                (conj lines (str "  :notes " (pr-str notes)))
                lines)]
    lines))

(defn- strip-section
  [lines section]
  (let [markers (section-indices lines)
        ranges (->> markers
                    (map-indexed (fn [idx [start label]]
                                   (when (= label section)
                                     (let [end (if-let [[next _] (nth markers (inc idx) nil)]
                                                 next
                                                 (count lines))]
                                       [start end]))))
                    (remove nil?))
        drop-idx (into #{} (mapcat (fn [[s e]] (range s e)) ranges))]
    (vec (map second
              (filter (fn [[idx _]] (not (contains? drop-idx idx)))
                      (map-indexed vector lines))))))

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
        existing-map (when ratchet
                       (parse-section-map lines (:start ratchet) (:end ratchet)))
        existing (or (:evidence existing-map) [])
        existing-ct (:ct-template existing-map)
        merged (merge-evidence (or existing []) new-evidence opts)
        ct-template (or existing-ct (some :ct-template merged))
        section-lines (render-section {:evidence merged
                                       :ct-template ct-template})
        cleaned (strip-section lines "@exotype-ratchet")
        program' (find-section cleaned "@exotype-program")
        lines' (cond
                 program'
                 (vec (concat (subvec (vec cleaned) 0 (:start program'))
                              section-lines
                              (subvec (vec cleaned) (:start program'))))
                 :else
                 (vec (concat cleaned section-lines)))]
    {:updated-lines lines'
     :existing-count (count (or existing []))
     :merged-count (count merged)}))

(defn- write-flexiarg!
  [path lines]
  (spit path (str/join "\n" lines) :append false))

(defn- bits->string
  [bits]
  (cond
    (string? bits) bits
    (number? bits) (let [bin (Integer/toBinaryString (int bits))]
                     (-> (format "%8s" bin)
                         (str/replace " " "0")))
    :else "00000000"))

(defn- bits->vec
  [bits]
  (mapv (fn [ch] (if (= ch \1) 1 0))
        (seq (bits->string bits))))

(defn- bootstrap-flexiarg
  [idx entry lift-info evidence]
  (let [sigil (:sigil entry)
        tier (:tier entry)
        bits-str (bits->string (:bits entry))
        bits-vec (bits->vec bits-str)
        ct-template (or (:ct-template lift-info) (some :ct-template evidence))
        program (:program entry)
        program-id (:template program)
        program-kind (:semantics program)
        program-name (some-> program-id name)
        header [(str "@flexiarg iiching/exotype-" (pad3 idx))
                (str "@title Exotype " (pad3 idx) " (0x" (format "%02X" (int idx)) ")")
                (str "@sigils [" sigil "]")
                (str "@bits " bits-str)
                (str "@number " (int idx))
                (str "@hex 0x" (format "%02X" (int idx)))
                "@audience exotype implementers, mmca users"
                "@tone reference"
                "@style pattern"
                "@futon5-ref futon5/resources/exotype-program-manifest.edn; futon5/resources/exotype-xenotype-lift.edn"
                "@futon5-manifest-path futon5/resources/exotype-program-manifest.edn"
                "@futon5-lift-path futon5/resources/exotype-xenotype-lift.edn"
                ""
                "! conclusion: Auto-generated by ratchet updater; program/params from manifest; lift registry attached."
                "  + context: Bootstrap file for ratchet evidence ingestion."
                "  + THEN:"
                "    Use iiching_sync_exotype for full canonical metadata if needed."
                ""]
        encoding [(str "@exotype-encoding")
                  "  :bit-order :msb->lsb"
                  (str "  :bits-b7..b0 " (pr-str bits-vec))
                  ""]
        ratchet (render-section {:evidence evidence
                                 :ct-template ct-template})
        program-lines [(str "@exotype-program")
                       (str "  :program-id " program-id)
                       (str "  :program-name " (pr-str program-name))
                       (str "  :program-kind " program-kind)
                       (str "  :tier " tier)
                       (str "  :scope " (:scope entry))
                       (str "  :inputs " (pr-str (:inputs entry)))
                       (str "  :outputs " (pr-str (:outputs entry)))
                       (str "  :invariants " (pr-str (:invariants entry)))
                       (str "  :word-variation " (:word-variation entry))
                       "  :notes \"From futon5/resources/exotype-program-manifest.edn.\""
                       ""]
        lift-lines [(str "@exotype-lift")
                    (str "  :pattern-ids " (pr-str (vec (distinct (:pattern-ids lift-info)))))
                    (str "  :ct-template " (pr-str (:ct-template lift-info)))
                    "  :lift-rules nil"
                    (str "  :evidence " (pr-str (vec (distinct (:pattern-evidence lift-info)))))
                    "  :notes \"Lift registry evidence from futon5/resources/exotype-xenotype-lift.edn.\""
                    ""]
        params-lines [(str "@exotype-params")
                      (str "  :rotation " (get-in entry [:params :rotation]))
                      (str "  :match-threshold " (get-in entry [:params :match-threshold]))
                      (str "  :invert-on-phenotype? " (get-in entry [:params :invert-on-phenotype?]))
                      (str "  :update-prob " (get-in entry [:params :update-prob]))
                      (str "  :mix-mode " (get-in entry [:params :mix-mode]))
                      (str "  :mix-shift " (get-in entry [:params :mix-shift]))
                      "  :notes \"From futon5/resources/exotype-program-manifest.edn.\""
                      ""]]
    (vec (concat header encoding ratchet [""]
                 program-lines lift-lines params-lines))))

(defn -main [& args]
  (let [{:keys [help unknown logs iiching-root manifest lift min-delta max-best max-recent dry-run]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println (usage)))
      (or (empty? logs) (nil? manifest)) (do
                                           (println "Missing --logs or --manifest.")
                                           (println (usage)))
      :else
      (let [iiching-root (or iiching-root "futon3/library/iiching")
            lift-path (or lift "futon5/resources/exotype-xenotype-lift.edn")
            lift-info (when lift-path
                        (lift-info (edn/read-string (slurp lift-path))))
            {:keys [entries index]} (manifest-data manifest)
            manifest-entries entries
            evidence (select-evidence logs min-delta lift-info)
            grouped (group-by (fn [e]
                                (let [sigil (get-in e [:exotype :sigil])
                                      tier (get-in e [:exotype :tier])]
                                  (choose-index sigil tier index)))
                              evidence)
            grouped (dissoc grouped nil)]
        (println "Evidence entries:" (count evidence))
        (println "Targets:" (count grouped))
        (doseq [[idx evidence-list] grouped]
          (let [path (flexiarg-path iiching-root idx)
                file (io/file path)
                entry (nth manifest-entries idx nil)
                lift-sigil (get-in entry [:sigil])
                lift-meta (get lift-info lift-sigil)]
            (cond
              (nil? entry)
              (println "Missing manifest entry for idx:" idx)

              (not (.exists file))
              (let [lines (bootstrap-flexiarg idx entry lift-meta evidence-list)]
                (println "Creating flexiarg:" path)
                (when-not dry-run
                  (.mkdirs (io/file iiching-root))
                  (write-flexiarg! path lines))
                (println (format "%s: %d -> %d evidence" path 0 (count evidence-list))))
              :else
              (let [{:keys [updated-lines existing-count merged-count]} (update-flexiarg path evidence-list
                                                                                         {:max-best max-best
                                                                                          :max-recent max-recent})]
                (println (format "%s: %d -> %d evidence" path existing-count merged-count))
                (when-not dry-run
                  (write-flexiarg! path updated-lines))))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
