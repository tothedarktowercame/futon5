(ns futon5.mmca.cli
  "Terminal runner for MetaMetaCA simulations."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.runtime :as mmca]))

(def ^:private default-length 32)
(def ^:private default-generations 32)

(defn- usage []
  (str/join
   "\n"
   ["MetaMetaCA terminal runner"
    ""
    "Usage:"
    "  clj -M -m futon5.mmca.cli [options]"
    ""
    "Options:"
    "  --genotype STR        Starting sigil string (required if no --length)."
    "  --length N            Random sigil string length (default 32)."
    "  --generations N       Generations to run (default 32)."
    "  --kernel KW           Kernel keyword, e.g. :mutating-template."
    "  --mode KW             :god or :classic (default :god)."
    "  --pattern-sigils STR  Comma-separated sigils to activate operators."
    "  --phenotype STR       Starting phenotype bit string."
    "  --phenotype-length N  Random phenotype length."
    "  --sleep-ms N          Delay between generations (for live demo)."
    "  --tty                 Write output to /dev/tty (forces streaming)."
    "  --raw                 Write via FileOutputStream (bypass buffering)."
    "  --global-rule N       Use rule N (0-255) for every sigil in genotype."
    "  --mutation MODE       Mutation mode: none (classic), or omit."
    "  --no-operators        Disable MMCA operators (pure CA run)."
    "  --lock-kernel         Ignore operator kernel changes (fixed kernel)."
    "  --freeze-genotype     Keep genotype fixed (classic CA behavior)."
    "  --genotype-gate       Gate genotype changes by phenotype bits."
    "  --gate-signal BIT     Phenotype bit used as keep-signal (default 1)."
    "  --lesion              Apply mid-run lesion to half the field."
    "  --lesion-tick N        Tick to apply lesion (default mid-run)."
    "  --lesion-target T      lesion target: phenotype, genotype, or both."
    "  --lesion-half H        lesion half: left or right."
    "  --lesion-mode M        lesion mode: zero or default."
    "  --seed N              Seed for deterministic init."
    "  --help                Show this message."]))

(defn- parse-int [s]
  (try
    (Long/parseLong s)
    (catch Exception _
      nil)))

(defn- parse-sigils [s]
  (let [tokens (->> (str/split (or s "") #",")
                    (map str/trim)
                    (remove str/blank?))]
    (when (seq tokens)
      (vec tokens))))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--tty" flag)
          (recur more (assoc opts :tty true))

          (= "--raw" flag)
          (recur more (assoc opts :raw true))

          (= "--no-operators" flag)
          (recur more (assoc opts :no-operators true))

          (= "--lock-kernel" flag)
          (recur more (assoc opts :lock-kernel true))

          (= "--freeze-genotype" flag)
          (recur more (assoc opts :freeze-genotype true))

          (= "--genotype-gate" flag)
          (recur more (assoc opts :genotype-gate true))

          (= "--lesion" flag)
          (recur more (assoc opts :lesion true))

          (= "--genotype" flag)
          (recur (rest more) (assoc opts :genotype (first more)))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--generations" flag)
          (recur (rest more) (assoc opts :generations (parse-int (first more))))

          (= "--kernel" flag)
          (recur (rest more) (assoc opts :kernel (some-> (first more) keyword)))

          (= "--mode" flag)
          (recur (rest more) (assoc opts :mode (some-> (first more) keyword)))

          (= "--pattern-sigils" flag)
          (recur (rest more) (assoc opts :pattern-sigils (parse-sigils (first more))))

          (= "--phenotype" flag)
          (recur (rest more) (assoc opts :phenotype (first more)))

          (= "--phenotype-length" flag)
          (recur (rest more) (assoc opts :phenotype-length (parse-int (first more))))

          (= "--sleep-ms" flag)
          (recur (rest more) (assoc opts :sleep-ms (parse-int (first more))))

          (= "--global-rule" flag)
          (recur (rest more) (assoc opts :global-rule (parse-int (first more))))

          (= "--mutation" flag)
          (recur (rest more) (assoc opts :mutation (first more)))

          (= "--gate-signal" flag)
          (recur (rest more) (assoc opts :gate-signal (first more)))

          (= "--lesion-tick" flag)
          (recur (rest more) (assoc opts :lesion-tick (parse-int (first more))))

          (= "--lesion-target" flag)
          (recur (rest more) (assoc opts :lesion-target (keyword (first more))))

          (= "--lesion-half" flag)
          (recur (rest more) (assoc opts :lesion-half (keyword (first more))))

          (= "--lesion-mode" flag)
          (recur (rest more) (assoc opts :lesion-mode (keyword (first more))))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- rule->bits [n]
  (let [bits (Integer/toBinaryString (int n))
        padded (format "%8s" bits)]
    (str/replace padded #" " "0")))

(defn- rule->sigil [n]
  (let [bits (rule->bits n)]
    (ca/sigil-for bits)))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(defn- rng-phenotype-string [^java.util.Random rng length]
  (apply str (repeatedly length #(rng-int rng 2))))

(defn- format-change [change-rate]
  (if (number? change-rate)
    (format "%.3f" (double change-rate))
    "-"))

(defn- format-entropy [entropy]
  (if (number? entropy)
    (format "%.3f" (double entropy))
    "-"))

(def ^:private ansi-red "\u001b[31m")
(def ^:private ansi-reset "\u001b[0m")

(defn- highlight-rule-110 [s]
  (when s
    (str/replace s "手" (str ansi-red "手" ansi-reset))))

(defn- pad-right [s width]
  (let [s (str s)]
    (if (>= (count s) width)
      s
      (str s (apply str (repeat (- width (count s)) " "))))))

(defn- print-header [{:keys [kernel mode generations operators]}]
  (println "MMCA run"
           "| kernel" (name kernel)
           "| mode" (name mode)
           "| generations" generations)
  (when-let [ops (seq operators)]
    (println "Operators:" (str/join " " (map :sigil ops))))
  (println "----"))

(defn- print-step [{:keys [state]}]
  (let [{:keys [generation genotype phenotype metrics]} state
        {:keys [entropy change-rate unique-sigils length]} metrics
        g-width (max 1 (count (or genotype "")))
        g (pad-right (highlight-rule-110 genotype) g-width)
        p (or phenotype "-")]
    (println (format "%03d | H=%s Δ=%s U=%d L=%d | G %s | P %s"
                     generation
                     (format-entropy entropy)
                     (format-change change-rate)
                     (long (or unique-sigils 0))
                     (long (or length 0))
                     g
                     p))))

(defn- print-proposals [result]
  (when-let [proposals (seq (:proposals result))]
    (println "----")
    (println "Proposals:" (count proposals))))

(defn- print-interestingness [metrics-history gen-history phe-history lesion-info]
  (let [summary (metrics/summarize-run {:metrics-history metrics-history
                                        :gen-history gen-history
                                        :phe-history phe-history
                                        :lesion lesion-info})
        {:keys [score avg-entropy avg-change avg-unique phe-entropy phe-change
                lz78-tokens lz78-ratio spatial-autocorr temporal-autocorr]}
        summary
        lesion-pre (:lesion/pre-half-diff summary)
        lesion-post (:lesion/post-half-diff summary)
        lesion-entropy (:lesion/post-entropy summary)
        lesion-half (:lesion/half summary)
        left-summary (:lesion/left summary)
        right-summary (:lesion/right summary)]
    (when score
      (println "----")
      (println (format "Interestingness: %.1f | entropy %.3f | change %.3f | diversity %.3f | phe-entropy %.3f | phe-change %.3f"
                       score
                       (double avg-entropy)
                       (double avg-change)
                       (double avg-unique)
                       (double (or phe-entropy 0.0))
                       (double (or phe-change 0.0))))
      (println (format "Compressibility: tokens %.2f | ratio %.3f"
                       (double (or lz78-tokens 0.0))
                       (double (or lz78-ratio 0.0))))
      (println (format "Autocorr: spatial %.3f | temporal %.3f"
                       (double (or spatial-autocorr 0.0))
                       (double (or temporal-autocorr 0.0))))
      (when (or lesion-pre lesion-post)
        (println (format "Lesion: pre-half %.3f | post-half %.3f | post-entropy %.3f"
                         (double (or lesion-pre 0.0))
                         (double (or lesion-post 0.0))
                         (double (or lesion-entropy 0.0)))))
      (when (and left-summary right-summary)
        (println (format "Lesion halves (%s): left score %.2f (I %.2f LZ %.3f T %.3f) | right score %.2f (I %.2f LZ %.3f T %.3f)"
                         (or (name lesion-half) "-")
                         (double (or (:composite-score left-summary) 0.0))
                         (double (or (:score left-summary) 0.0))
                         (double (or (:lz78-ratio left-summary) 0.0))
                         (double (or (:temporal-autocorr left-summary) 0.0))
                         (double (or (:composite-score right-summary) 0.0))
                         (double (or (:score right-summary) 0.0))
                         (double (or (:lz78-ratio right-summary) 0.0))
                         (double (or (:temporal-autocorr right-summary) 0.0))))))))

(defn- interestingness-line [metrics-history gen-history phe-history lesion-info]
  (let [summary (metrics/summarize-run {:metrics-history metrics-history
                                        :gen-history gen-history
                                        :phe-history phe-history
                                        :lesion lesion-info})
        {:keys [score avg-entropy avg-change avg-unique phe-entropy phe-change
                lz78-tokens lz78-ratio spatial-autocorr temporal-autocorr]}
        summary
        lesion-pre (:lesion/pre-half-diff summary)
        lesion-post (:lesion/post-half-diff summary)
        lesion-entropy (:lesion/post-entropy summary)
        lesion-half (:lesion/half summary)
        left-summary (:lesion/left summary)
        right-summary (:lesion/right summary)]
    (when score
      (str
       (format "Interestingness: %.1f | entropy %.3f | change %.3f | diversity %.3f | phe-entropy %.3f | phe-change %.3f"
               score
               (double avg-entropy)
               (double avg-change)
               (double avg-unique)
               (double (or phe-entropy 0.0))
               (double (or phe-change 0.0)))
       "\n"
       (format "Compressibility: tokens %.2f | ratio %.3f"
               (double (or lz78-tokens 0.0))
               (double (or lz78-ratio 0.0)))
       "\n"
       (format "Autocorr: spatial %.3f | temporal %.3f"
               (double (or spatial-autocorr 0.0))
               (double (or temporal-autocorr 0.0)))
       (when (or lesion-pre lesion-post)
         (str "\n"
              (format "Lesion: pre-half %.3f | post-half %.3f | post-entropy %.3f"
                      (double (or lesion-pre 0.0))
                      (double (or lesion-post 0.0))
                      (double (or lesion-entropy 0.0)))))
       (when (and left-summary right-summary)
         (str "\n"
              (format "Lesion halves (%s): left score %.2f (I %.2f LZ %.3f T %.3f) | right score %.2f (I %.2f LZ %.3f T %.3f)"
                      (or (name lesion-half) "-")
                      (double (or (:composite-score left-summary) 0.0))
                      (double (or (:score left-summary) 0.0))
                      (double (or (:lz78-ratio left-summary) 0.0))
                      (double (or (:temporal-autocorr left-summary) 0.0))
                      (double (or (:composite-score right-summary) 0.0))
                      (double (or (:score right-summary) 0.0))
                      (double (or (:lz78-ratio right-summary) 0.0))
                      (double (or (:temporal-autocorr right-summary) 0.0)))))))))

(defn -main
  [& args]
  (let [{:keys [help unknown genotype length generations kernel mode pattern-sigils
                phenotype phenotype-length sleep-ms tty raw global-rule mutation
                no-operators lock-kernel freeze-genotype genotype-gate gate-signal
                lesion lesion-tick lesion-target lesion-half lesion-mode
                seed]}
        (parse-args args)]
    (cond
      help
      (println (usage))

      unknown
      (do
        (println "Unknown option:" unknown)
        (println)
        (println (usage)))

      :else
      (let [length (or length default-length)
            global-rule (when (and global-rule (<= 0 global-rule 255))
                          global-rule)
            rng (when seed (java.util.Random. (long seed)))
            genotype (cond
                       global-rule
                       (apply str (repeat length (rule->sigil global-rule)))
                       genotype
                       genotype
                       :else
                       (if rng
                         (rng-sigil-string rng length)
                         (ca/random-sigil-string length)))
            phenotype (or phenotype
                          (when phenotype-length
                            (if rng
                              (rng-phenotype-string rng phenotype-length)
                              (ca/random-phenotype-string phenotype-length))))
            generations (or generations default-generations)
            mode (or mode :god)
            mutation (some-> mutation str/lower-case)
            kernel (or (when (= mutation "none") :multiplication) kernel)
            lesion-map (when (or lesion lesion-tick lesion-target lesion-half lesion-mode)
                         (cond-> {}
                           lesion-tick (assoc :tick lesion-tick)
                           lesion-target (assoc :target lesion-target)
                           lesion-half (assoc :half lesion-half)
                           lesion-mode (assoc :mode lesion-mode)))
            opts (cond-> {:genotype genotype
                          :generations generations
                          :mode mode}
                   phenotype (assoc :phenotype phenotype)
                   kernel (assoc :kernel kernel)
                   lock-kernel (assoc :lock-kernel true)
                   freeze-genotype (assoc :freeze-genotype true)
                   genotype-gate (assoc :genotype-gate true)
                   gate-signal (assoc :genotype-gate-signal (first gate-signal))
                   lesion-map (assoc :lesion lesion-map)
                   (or no-operators
                       (and (= mutation "none") (not (seq pattern-sigils))))
                   (assoc :operators [])
                   (seq pattern-sigils) (assoc :pattern-sigils pattern-sigils))
            printed? (atom false)]
        (let [writer (if tty
                       (try
                         (java.io.PrintWriter. (io/writer "/dev/tty") true)
                         (catch Exception _
                           (java.io.PrintWriter. System/err true)))
                       (java.io.PrintWriter. System/err true))
              stream (when raw
                       (try
                         (if tty
                           (java.io.FileOutputStream. "/dev/tty")
                           System/err)
                         (catch Exception _
                           System/err)))
              write-raw (fn [s]
                          (when stream
                            (.write ^java.io.OutputStream stream
                                    (.getBytes (str s "\n") "UTF-8"))
                            (.flush ^java.io.OutputStream stream)))]
          (with-open [w writer]
            (binding [*out* w]
              (let [result (mmca/run-mmca-stream
                            opts
                            (fn [{:keys [state operators] :as step}]
                              (when-not @printed?
                                (if raw
                                  (write-raw (str "MMCA run | kernel "
                                                  (name (:kernel state))
                                                  " | mode " (name mode)
                                                  " | generations " generations))
                                  (print-header {:kernel (:kernel state)
                                                 :mode mode
                                                 :generations generations
                                                 :operators operators}))
                                (when (and (not raw) operators)
                                  nil)
                                (reset! printed? true))
                              (if raw
                                (write-raw (with-out-str (print-step step)))
                                (do
                                  (print-step step)
                                  (.flush ^java.io.PrintWriter *out*)))
                              (when (and sleep-ms (pos? sleep-ms))
                                (Thread/sleep sleep-ms))))]
                (if raw
                  (do
                    (when-let [line (interestingness-line (:metrics-history result)
                                                         (:gen-history result)
                                                         (:phe-history result)
                                                         (:lesion result))]
                      (write-raw "----")
                      (write-raw line))
                    (when-let [proposals (:proposals result)]
                      (write-raw (str "----\nProposals: " (count proposals)))))
                  (do
                    (print-interestingness (:metrics-history result)
                                           (:gen-history result)
                                           (:phe-history result)
                                           (:lesion result))
                    (print-proposals result)))))))))))
