#!/usr/bin/env bb
;; Informed sigil assignment: bridge 8-bit exotypes → truth-table-8 hanzi → tokizh emoji
;;
;; Chains the pattern→exotype bridge (learned from MiniLM + hexagram skeleton)
;; with the truth-table-8 (8-bit → hanzi) to produce sigil assignments that
;; are grounded in pattern semantics rather than heuristic/random.
;;
;; Where the bridge-assigned hanzi also appears in the tokizh mapping, the
;; corresponding emoji is included — producing a complete validated sigil
;; (emoji/hanzi) with both components semantically grounded.
;;
;; Also produces 4-hanzi xenotype sigils from the 36-bit section encodings:
;;   IF(8) / HOWEVER(8) / THEN(8) / BECAUSE(8)
;;
;; Usage:
;;   cd /home/joe/code/futon5
;;   bb -cp src:resources scripts/sigil_assignment.clj
;;
;; Output:
;;   resources/sigil-assignments.edn    — full assignment data
;;   stdout                             — summary report

(require '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clojure.java.io :as io])

;; =============================================================================
;; TRUTH TABLE
;; =============================================================================

(defn parse-truth-table
  "Parse truth-table-8.el (Elisp format) into {8-bit-string hanzi}."
  [path]
  (let [text (slurp path)
        ;; Match: ("00000000" "一" "#000000")
        pattern #"\(\"([01]{8})\"\s+\"(.)\"\s+\"#[0-9a-fA-F]{6}\"\)"
        matches (re-seq pattern text)]
    (into {} (map (fn [[_ bits hanzi]] [bits hanzi]) matches))))

(defn parse-truth-table-reverse
  "Parse truth-table-8.el into {hanzi 8-bit-string}."
  [path]
  (let [text (slurp path)
        pattern #"\(\"([01]{8})\"\s+\"(.)\"\s+\"#[0-9a-fA-F]{6}\"\)"
        matches (re-seq pattern text)]
    (into {} (map (fn [[_ bits hanzi]] [hanzi bits]) matches))))

;; =============================================================================
;; TOKIZH (emoji ↔ hanzi mapping)
;; =============================================================================

(defn parse-tokizh
  "Parse tokizh.org (org-table format) into {hanzi {:emoji emoji :tokipona word}}.
   Where multiple emoji map to the same hanzi, last one wins."
  [path]
  (let [text (slurp path)
        lines (str/split-lines text)
        ;; Skip header and separator lines
        data-lines (filter (fn [line]
                             (and (str/starts-with? (str/trim line) "|")
                                  (not (str/includes? line "---"))
                                  (not (str/includes? line "emoji"))))
                           lines)]
    (into {}
      (for [line data-lines
            :let [cols (mapv str/trim (str/split line #"\|"))
                  ;; cols: ["" emoji tokipona shortcode english hanzi notes ""]
                  emoji (nth cols 1 nil)
                  tokipona (nth cols 2 nil)
                  hanzi (nth cols 5 nil)]
            :when (and emoji hanzi
                       (not (str/blank? emoji))
                       (not (str/blank? hanzi))
                       (= 1 (count hanzi)))]
        [hanzi {:emoji emoji :tokipona tokipona}]))))

;; =============================================================================
;; BRIDGE DATA
;; =============================================================================

(defn load-bridge
  "Load the pattern-exotype-bridge.edn."
  [path]
  (edn/read-string (slurp path)))

;; =============================================================================
;; EXISTING SIGILS
;; =============================================================================

(defn extract-existing-sigils
  "Scan flexiarg files for @sigils lines. Returns {pattern-id [sigil-strings]}."
  [library-root]
  (let [files (file-seq (io/file library-root))
        flexiarg-files (filter #(str/ends-with? (.getName %) ".flexiarg") files)]
    (into {}
      (for [f flexiarg-files
            :let [text (slurp f)
                  ;; Extract pattern-id from path: library/foo/bar.flexiarg → foo/bar
                  rel-path (str/replace (.getPath f) (str library-root "/") "")
                  pattern-id (-> rel-path
                                 (str/replace #"\.flexiarg$" "")
                                 (str/replace #"^library/" ""))
                  ;; Find @sigils line
                  sigil-match (re-find #"@sigils\s+\[([^\]]+)\]" text)]
            :when sigil-match]
        [pattern-id (str/split (str/trim (second sigil-match)) #"\s+")]))))

;; =============================================================================
;; ASSIGNMENT
;; =============================================================================

(defn assign-sigils
  "For each bridge pattern, look up the hanzi for its 8-bit exotype,
   then look up the emoji from tokizh if available.
   Also produce section-level hanzi/emoji from the 36-bit xenotype."
  [bridge-data bits->hanzi hanzi->tokizh]
  (let [hanzi->sigil (fn [hanzi]
                       (when hanzi
                         (let [tok (get hanzi->tokizh hanzi)]
                           {:hanzi hanzi
                            :emoji (:emoji tok)
                            :tokipona (:tokipona tok)
                            :sigil (if (:emoji tok)
                                     (str (:emoji tok) "/" hanzi)
                                     hanzi)})))]
    (mapv (fn [pattern]
            (let [exo-bits (:exotype-8bit pattern)
                  exo-hanzi (get bits->hanzi exo-bits)
                  exo-sigil (hanzi->sigil exo-hanzi)

                  ;; Parse 36-bit xenotype into 4 bytes + nibble
                  xeno-str (:xenotype-36bit pattern)
                  xeno-parts (when xeno-str (str/split (str/trim xeno-str) #"\s+"))
                  if-bits (nth xeno-parts 0 nil)
                  however-bits (nth xeno-parts 1 nil)
                  then-bits (nth xeno-parts 2 nil)
                  because-bits (nth xeno-parts 3 nil)

                  section-sigils {:if (hanzi->sigil (when if-bits (get bits->hanzi if-bits)))
                                  :however (hanzi->sigil (when however-bits (get bits->hanzi however-bits)))
                                  :then (hanzi->sigil (when then-bits (get bits->hanzi then-bits)))
                                  :because (hanzi->sigil (when because-bits (get bits->hanzi because-bits)))}]
              (assoc pattern
                     :exotype-hanzi (:hanzi exo-sigil)
                     :exotype-emoji (:emoji exo-sigil)
                     :exotype-tokipona (:tokipona exo-sigil)
                     :exotype-sigil (:sigil exo-sigil)
                     :xenotype-hanzi {:if (get-in section-sigils [:if :hanzi])
                                      :however (get-in section-sigils [:however :hanzi])
                                      :then (get-in section-sigils [:then :hanzi])
                                      :because (get-in section-sigils [:because :hanzi])}
                     :xenotype-sigils section-sigils)))
          (:patterns bridge-data))))

;; =============================================================================
;; COMPARISON WITH EXISTING
;; =============================================================================

(defn compare-assignments
  "Compare bridge-derived hanzi with existing @sigils hanzi."
  [assigned existing-sigils hanzi->bits]
  (let [comparisons
        (for [p assigned
              :let [pid (:pattern-id p)
                    existing (get existing-sigils pid)
                    bridge-hanzi (:exotype-hanzi p)
                    bridge-bits (:exotype-8bit p)]
              :when existing]
          (let [;; Extract hanzi from existing sigils (right side of emoji/hanzi)
                existing-hanzi (mapv (fn [s]
                                      (let [parts (str/split s #"/")]
                                        (when (= 2 (count parts))
                                          (second parts))))
                                    existing)
                existing-bits (mapv #(get hanzi->bits %) existing-hanzi)
                ;; Hamming distance between bridge and first existing
                first-existing-bits (first existing-bits)
                hamming (when (and bridge-bits first-existing-bits
                                  (= (count bridge-bits) (count first-existing-bits)))
                          (count (filter true?
                                   (map not= bridge-bits first-existing-bits))))]
            {:pattern-id pid
             :bridge-hanzi bridge-hanzi
             :bridge-bits bridge-bits
             :existing-sigils existing
             :existing-hanzi existing-hanzi
             :existing-bits existing-bits
             :hamming hamming
             :match? (= bridge-hanzi (first existing-hanzi))}))]
    (vec comparisons)))

;; =============================================================================
;; MAIN
;; =============================================================================

(let [;; Paths
      tt-path "/home/joe/code/futon3/resources/truth-table-8/truth-table-8.el"
      tokizh-path "/home/joe/code/futon3/resources/tokizh/tokizh.org"
      bridge-path "resources/pattern-exotype-bridge.edn"
      library-root "/home/joe/code/futon3/library"
      output-path "resources/sigil-assignments.edn"

      ;; Load data
      _ (println "Loading truth-table-8...")
      bits->hanzi (parse-truth-table tt-path)
      hanzi->bits (parse-truth-table-reverse tt-path)
      _ (println (str "  " (count bits->hanzi) " entries"))

      _ (println "Loading tokizh...")
      hanzi->tokizh (parse-tokizh tokizh-path)
      _ (println (str "  " (count hanzi->tokizh) " hanzi with emoji skins"))
      _ (println (str "  Coverage: " (count hanzi->tokizh) "/256 = "
                      (format "%.0f%%" (* 100.0 (/ (count hanzi->tokizh) 256.0)))))

      _ (println "Loading bridge data...")
      bridge (load-bridge bridge-path)
      _ (println (str "  " (count (:patterns bridge)) " patterns"))

      _ (println "Scanning existing @sigils...")
      existing (extract-existing-sigils library-root)
      _ (println (str "  " (count existing) " patterns with existing sigils"))
      _ (println)

      ;; Assign
      assigned (assign-sigils bridge bits->hanzi hanzi->tokizh)

      ;; Compare
      comparisons (compare-assignments assigned existing hanzi->bits)
      n-compared (count comparisons)
      n-match (count (filter :match? comparisons))
      hammings (keep :hamming comparisons)
      mean-hamming (when (seq hammings)
                     (/ (reduce + 0.0 hammings) (count hammings)))]

  ;; Emoji coverage
  (let [with-emoji (count (filter :exotype-emoji assigned))
        without-emoji (- (count assigned) with-emoji)]

    ;; Report
    (println "=== Sigil Assignment Report ===")
    (println)
    (println (str "Patterns assigned:     " (count assigned)))
    (println (str "  Complete sigils:     " with-emoji
                  " (emoji/hanzi, " (format "%.0f%%" (* 100.0 (/ (double with-emoji) (count assigned)))) ")"))
    (println (str "  Hanzi-only sigils:   " without-emoji
                  " (hanzi not in tokizh)"))
    (println (str "With existing sigils:  " n-compared))
    (println (str "Exact hanzi match:     " n-match "/" n-compared
                  " (" (when (pos? n-compared)
                         (format "%.1f%%" (* 100.0 (/ (double n-match) n-compared))))
                  ")"))
    (when mean-hamming
      (printf "Mean hamming distance:  %.2f bits (between bridge and existing)%n" mean-hamming))
    (println))

  ;; Show some examples
  (println "--- Sample Assignments (first 30) ---")
  (println)
  (printf "  %-40s %-10s %-8s %-10s  %s%n" "Pattern" "Bits" "Sigil" "Toki Pona" "Xenotype (IF/HOW/THEN/BEC)")
  (println (apply str (repeat 115 "-")))
  (doseq [p (take 30 assigned)]
    (let [xs (:xenotype-sigils p)
          xsig (str (get-in xs [:if :sigil] "?") "/"
                     (get-in xs [:however :sigil] "?") "/"
                     (get-in xs [:then :sigil] "?") "/"
                     (get-in xs [:because :sigil] "?"))]
      (printf "  %-40s %-10s %-8s %-10s  %s%n"
              (:pattern-id p)
              (:exotype-8bit p)
              (or (:exotype-sigil p) "?")
              (or (:exotype-tokipona p) "")
              xsig)))
  (println)

  ;; Show comparison examples
  (when (seq comparisons)
    (println "--- Bridge vs Existing Sigils (sample) ---")
    (println)
    (printf "  %-40s %-8s %-8s %-8s %s%n"
            "Pattern" "Bridge" "Existing" "Hamming" "Match?")
    (println (apply str (repeat 85 "-")))
    (doseq [c (take 30 (sort-by :hamming comparisons))]
      (printf "  %-40s %-8s %-8s %-8s %s%n"
              (:pattern-id c)
              (or (:bridge-hanzi c) "?")
              (str/join "," (:existing-hanzi c))
              (or (:hamming c) "?")
              (if (:match? c) "YES" "")))
    (println))

  ;; Hamming distribution
  (when (seq hammings)
    (println "--- Hamming Distance Distribution ---")
    (let [dist (frequencies hammings)]
      (doseq [h (sort (keys dist))]
        (printf "  %d bits: %d patterns %s%n"
                h (get dist h)
                (apply str (repeat (get dist h) "#")))))
    (println))

  ;; Save full assignments
  (spit output-path
        (pr-str {:meta {:generated (str (java.time.LocalDate/now))
                        :truth-table "futon3/resources/truth-table-8/truth-table-8.el"
                        :tokizh "futon3/resources/tokizh/tokizh.org"
                        :bridge "resources/pattern-exotype-bridge.edn"
                        :n-patterns (count assigned)
                        :n-complete-sigils (count (filter :exotype-emoji assigned))
                        :n-existing-compared n-compared
                        :mean-hamming mean-hamming}
                 :assignments
                 (mapv (fn [p]
                         {:pattern-id (:pattern-id p)
                          :title (:title p)
                          :exotype-8bit (:exotype-8bit p)
                          :exotype-hanzi (:exotype-hanzi p)
                          :exotype-emoji (:exotype-emoji p)
                          :exotype-tokipona (:exotype-tokipona p)
                          :exotype-sigil (:exotype-sigil p)
                          :xenotype-36bit (:xenotype-36bit p)
                          :xenotype-hanzi (:xenotype-hanzi p)
                          :xenotype-sigils (into {}
                                            (map (fn [[k v]]
                                                   [k (select-keys v [:hanzi :emoji :tokipona :sigil])])
                                                 (:xenotype-sigils p)))
                          :confidence (:confidence p)
                          :is-anchor (:is-anchor p)})
                       assigned)}))
  (println "Saved to:" output-path))
