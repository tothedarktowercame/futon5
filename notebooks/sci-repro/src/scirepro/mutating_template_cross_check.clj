(ns scirepro.mutating-template-cross-check
  "Cross-check the Clojure port of the DEFAULT elisp dynamic
  (`evolve-sigil-with-mutating-template`, 256ca.el:990-1065, aliased
  `evolve-sigil-fn` at :1069) against 256ca.el.

  This is R-repro-5: the NON-CONTEXTUAL default only.  With context=nil the
  template is nil, so every cell falls to the local-rule branch (= the
  standard genotype-CA :multiply step already in engine.clj) followed by
  `balance-mutation` (256ca.el:971-986) on each new cell's genotype byte.
  No phenotype blending.

  Route: deterministic shadow-random injection (same technique as the 4a
  mutation-cross-check and the 4b balance-cross-check).  We run the Clojure
  engine with `evolve-with-mutating-template-recorded` to capture the exact
  RNG draw sequence, inject that sequence into the elisp via fset-shadowing
  of `random`, and require grid-identity.

  THE CRUX (data-dependent RNG draws):
  `balance-mutation`'s RNG draws are DATA-DEPENDENT.  `(random 20)` is only
  drawn when popcount > 6 or popcount < 2 (elisp `and` short-circuits), and
  `randomize-sequence` draws MORE randoms only when the gate passes.  The
  Clojure engine's `balance-mutate-rule-recorded` consumes RNG in the exact
  same grid-state-dependent order as elisp.  Because both engines compute
  popcount from identical grid state, the draw positions stay in lockstep
  IFF the port is correct — that self-consistency is the whole test.

  The engine function `randomize-and-select-last` faithfully replicates the
  elisp `randomize-sequence` Fisher-Yates (len draws of sizes len, len-1,
  ..., 1) plus `nthcdr (- len 1)` truncation.  This is the single source of
  truth: the cross-check records its draws and injects them verbatim."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [scirepro.engine :as engine]
            [scirepro.cross-check :as xcheck]))

(defn- mutating-template-elisp-program
  "Elisp program: genotype-only evolution using evolve-sigil-with-mutating-template
  (the default evolve-sigil-fn, already aliased by 256ca.el:1069) with nil
  context, random shadowed to return values from INJECTION in order."
  [ic steps injection]
  (str "(require 'cl)\n"
       "(provide 'hexrgb)\n"
       "(defalias 'string-to-int 'string-to-number)\n"
       "(load-file " (pr-str xcheck/elisp-path) ")\n"
       ;; evolve-sigil-fn is already aliased to evolve-sigil-with-mutating-template
       ;; by 256ca.el:1069.  We don't rebind it.
       "(let ((sci-injection '" (pr-str (vec injection)) ")\n"
       "      (sci-sentinel 0))\n"
       "  (fset 'random\n"
       "    (lambda (limit)\n"
       "      (let ((val (if (< sci-sentinel (length sci-injection))\n"
       "                     (prog1 (aref sci-injection sci-sentinel)\n"
       "                       (setq sci-sentinel (1+ sci-sentinel)))\n"
       "                     0)))\n"
       "        val)))\n"
       "  (defun sci-binary8 (n)\n"
       "    (let ((s \"\"))\n"
       "      (dotimes (i 8 s)\n"
       "        (setq s (concat s (if (= 0 (logand n (lsh 1 (- 7 i)))) \"0\" \"1\"))))))\n"
       "  (defun sci-rule-to-sigil (n)\n"
       "    (second (get-genotype-from-rule (sci-binary8 n))))\n"
       "  (defun sci-evolve-cell (row i)\n"
       "    (let* ((n (length row))\n"
       "           (left (if (= i 0) 0 (aref row (- i 1))))\n"
       "           (center (aref row i))\n"
       "           (right (if (= i (- n 1)) 0 (aref row (+ i 1))))\n"
       "           (result (first (funcall 'evolve-sigil-fn\n"
       "                           (sci-rule-to-sigil center)\n"
       "                           (sci-rule-to-sigil left)\n"
       "                           (sci-rule-to-sigil right)))))\n"
       "      (string-to-number result 2)))\n"
       "  (defun sci-evolve-row (row)\n"
       "    (let ((v (vconcat row)) (out nil))\n"
       "      (dotimes (i (length v) (vconcat (nreverse out)))\n"
       "        (setq out (cons (sci-evolve-cell v i) out)))))\n"
       "  (let* ((steps " steps ")\n"
       "         (row '" (pr-str ic) ")\n"
       "         (rows (list row)))\n"
       "    (dotimes (_ steps)\n"
       "      (setq row (sci-evolve-row row))\n"
       "      (setq rows (cons row rows)))\n"
       "    (princ \"\\nSCI_REPRO_EDN \")\n"
       "    (princ (prin1-to-string (vconcat (reverse rows))))))\n"))

(defn emacs-mutating-template-grid
  "Run the shadowed-random elisp and return the grid."
  [ic steps injection]
  (let [program (mutating-template-elisp-program ic steps injection)
        file (java.io.File/createTempFile "sci-repro-mt-xcheck" ".el")]
    (try
      (spit file program)
      (let [{:keys [exit out err]} (sh/sh "emacs" "--batch" "-l" (.getPath file))]
        (when-not (zero? exit)
          (throw (ex-info "emacs mutating-template cross-check failed"
                          {:exit exit :out out :err err})))
        (or (xcheck/normalize-elisp-output out)
            (throw (ex-info "could not parse emacs mutating-template output"
                            {:out out :err err}))))
      (finally
        (.delete file)))))

(defn compare-mutating-template-ic
  "Cross-check one IC: run the Clojure engine recording RNG draws, inject
  the recorded draw sequence into the elisp shadow-random, require
  grid-identity."
  [file steps seed]
  (let [ic (engine/read-ic file)
        {:keys [rows all-draws]} (engine/evolve-with-mutating-template-recorded
                                  ic steps seed)
        injection (vec all-draws)
        elisp-grid (emacs-mutating-template-grid ic steps injection)
        identical? (= rows elisp-grid)]
    {:file (.getPath file)
     :dynamic :mutating-template
     :steps steps
     :seed seed
     :rows (count rows)
     :width (count ic)
     :identical? identical?
     :first-diff (when-not identical?
                   (first (keep-indexed (fn [idx [a b]]
                                          (when-not (= a b)
                                            {:row idx :clojure a :elisp b}))
                                        (map vector rows elisp-grid))))}))

(def mutating-template-xcheck-ics
  "Dedicated IC seeds for the mutating-template cross-check (width 32 to keep
  the emacs batch fast while exercising the full balance-mutation RNG path)."
  [{:name "xcheck-mt-150200160" :seed 150200160 :width 32}
   {:name "xcheck-mt-150200161" :seed 150200161 :width 32}
   {:name "xcheck-mt-150200162" :seed 150200162 :width 32}])

(defn ensure-mutating-template-ics! []
  (doseq [{:keys [name seed width]} mutating-template-xcheck-ics]
    (let [path (io/file "resources/ics" (str name ".edn"))]
      (when-not (.exists path)
        (engine/save-ic! path seed width)))))

(defn mutating-template-ic-files []
  (ensure-mutating-template-ics!)
  (->> (file-seq (io/file "resources/ics"))
       (filter #(.isFile %))
       (filter #(str/starts-with? (.getName %) "xcheck-mt-"))
       (filter #(str/ends-with? (.getName %) ".edn"))
       (sort-by #(.getName %))
       vec))

(defn run-mutating-template-cross-check
  ([] (run-mutating-template-cross-check 120))
  ([steps]
   (let [files (mutating-template-ic-files)
         results (mapv (fn [{:keys [seed]} file]
                         (compare-mutating-template-ic file steps seed))
                       mutating-template-xcheck-ics
                       files)
         report {:dynamic :mutating-template
                 :engine :scirepro.engine
                 :variant :evolve-sigil-with-mutating-template
                 :context :nil
                 :ground-truth xcheck/elisp-path
                 :route :injected-stream
                 :description "DEFAULT elisp dynamic (evolve-sigil-fn alias) with nil context: local-rule step + balance-mutation. Non-contextual path only."
                 :results results
                 :ok? (every? :identical? results)}
         out-file (io/file "out/mutating-template-cross-check.edn")]
     (.mkdirs (.getParentFile out-file))
     (spit out-file (with-out-str (prn report)))
     report)))

(defn -main [& args]
  (let [steps (if-let [arg (first args)] (parse-long arg) 120)
        report (run-mutating-template-cross-check steps)]
    (println (format "MUTATING-TEMPLATE CROSS-CHECK %s route=%s variant=%s context=nil %d ICs x %d steps; report=%s"
                     (if (:ok? report) "OK" "FAIL")
                     (name (:route report))
                     (name (:variant report))
                     (count (:results report))
                     steps
                     "out/mutating-template-cross-check.edn"))
    (when-not (:ok? report)
      (doseq [result (:results report)]
        (when-not (:identical? result)
          (println (:dynamic result) (:file result) (:first-diff result)))))
    (System/exit (if (:ok? report) 0 1))))
