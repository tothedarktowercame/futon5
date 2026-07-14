(ns scirepro.mutating-template-context-cross-check
  "Cross-check the CONTEXTUAL Clojure port (scirepro.mutating-template) of
  `evolve-sigil-with-mutating-template` (256ca.el:990-1065, the CONTEXT != nil
  branch) against 256ca.el via the deterministic shadow-`random` injection
  route.

  This is R-repro-5b: the contextual / phenotype-driven mutating template.  The
  elisp driver under test is `evolve-sigil-string-contextually`
  (256ca.el:1109) driven by `co-evolve-phenotype-and-genotype`
  (256ca.el:1192) iterated for N generations (run-for-generations-3,
  256ca.el:1217).  The Clojure mirror is
  scirepro.mutating-template/coupled-contextual-evolve-recorded.

  Route (same technique as 4a/4b/5): we run the Clojure engine with
  coupled-contextual-evolve-recorded to capture the exact RNG draw sequence, inject
  that sequence into the elisp via fset-shadowing of `random`, and require
  grid-identity on BOTH the genotype and phenotype layers.

  THE CRUX (data-dependent RNG draws + context threading):
  (a) CONTEXT quadruples must be built EXACTLY as evolve-sigil-string-contextually
      does: old landscape wrapped [0, old..., 0], new landscape wrapped
      [0, new..., 0], quad[k] = [old-w[k], old-w[k+1], old-w[k+2], new-w[k]]
      for middle cells 1..L-2; head (cell 0) and tail (cell L-1) get NO context.
  (b) The per-bit template-match-then-local-rule fallback must match elisp
      bit-for-bit (template = [actual, bitflip, bitflip[1], bitflip[0 2 3]];
      match on first-3; fallback to center genotype's local-rule bit).
  (c) balance-mutation's (random 20) gate is data-dependent (only drawn at
      ones-count extremes >6 or <2) plus randomize-sequence draws on an actual
      flip.  The cell processing order is head -> middle[1..L-2] -> tail, and
      BOTH sides derive draws from identical state, so lockstep holds IFF the
      port is correct — that self-consistency is the test."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [scirepro.engine :as engine]
            [scirepro.mutating-template :as mt]
            [scirepro.cross-check :as xcheck]))

(defn- contextual-elisp-program
  "Elisp program: coupled genotype/phenotype evolution using
  co-evolve-phenotype-and-genotype (which calls evolve-sigil-string-contextually,
  the default evolve-sigil-fn already aliased by 256ca.el:1069) with random
  shadowed to return values from INJECTION in order."
  [genotype-ic phenotype-ic steps injection]
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
       "  (defun sci-row-to-sigils (row)\n"
       "    (apply #'concat (mapcar #'sci-rule-to-sigil row)))\n"
       "  (defun sci-sigils-to-rules (s)\n"
       "    (vconcat (mapcar (lambda (ch)\n"
       "              (string-to-number (first (get-genotype-from-sigil (char-to-string ch))) 2))\n"
       "            (string-to-list s))))\n"
       "  (defun sci-bits-to-string (row)\n"
       "    (apply #'concat (mapcar #'number-to-string row)))\n"
       "  (defun sci-string-to-bits (s)\n"
       "    (vconcat (mapcar (lambda (ch) (string-to-number (char-to-string ch)))\n"
       "                     (string-to-list s))))\n"
       "  (let* ((steps " steps ")\n"
       "         (gen (sci-row-to-sigils '" (pr-str genotype-ic) "))\n"
       "         (phe (sci-bits-to-string '" (pr-str phenotype-ic) "))\n"
       "         (gen-rows (list gen))\n"
       "         (phe-rows (list phe)))\n"
       "    (dotimes (_ steps)\n"
       "      (let ((result (co-evolve-phenotype-and-genotype gen phe)))\n"
       "        (setq gen (first result))\n"
       "        (setq phe (second result))\n"
       "        (setq gen-rows (cons gen gen-rows))\n"
       "        (setq phe-rows (cons phe phe-rows))))\n"
       "    (princ \"\\nSCI_REPRO_EDN \")\n"
       "    (princ (prin1-to-string (vector (vconcat (mapcar #'sci-sigils-to-rules\n"
       "                                                (reverse gen-rows)))\n"
       "                                  (vconcat (mapcar #'sci-string-to-bits\n"
       "                                                (reverse phe-rows))))))))\n"))

(defn emacs-contextual-grid
  "Run the shadowed-random elisp and return {:genotype [...] :phenotype [...]}."
  [genotype-ic phenotype-ic steps injection]
  (let [program (contextual-elisp-program genotype-ic phenotype-ic steps injection)
        file (java.io.File/createTempFile "sci-repro-mt-ctx-xcheck" ".el")]
    (try
      (spit file program)
      (let [{:keys [exit out err]} (sh/sh "emacs" "--batch" "-l" (.getPath file))]
        (when-not (zero? exit)
          (throw (ex-info "emacs contextual cross-check failed"
                          {:exit exit :out out :err err})))
        (let [[gen phe] (or (xcheck/normalize-elisp-output out)
                            (throw (ex-info "could not parse emacs contextual output"
                                            {:out out :err err})))]
          {:genotype gen :phenotype phe}))
      (finally
        (.delete file)))))

(defn compare-contextual-ic
  "Cross-check one coupled IC: run the Clojure contextual engine recording RNG
  draws, inject the recorded draw sequence into the elisp shadow-random, require
  grid-identity on both genotype and phenotype layers."
  [geno-file phe-file steps seed]
  (let [genotype-ic (engine/read-ic geno-file)
        phenotype-ic (engine/read-ic phe-file)
        {:keys [genotype phenotype all-draws]}
        (mt/coupled-contextual-evolve-recorded genotype-ic phenotype-ic steps seed)
        injection (vec all-draws)
        elisp-grid (emacs-contextual-grid genotype-ic phenotype-ic steps injection)
        genotype-identical? (= genotype (:genotype elisp-grid))
        phenotype-identical? (= phenotype (:phenotype elisp-grid))
        identical? (and genotype-identical? phenotype-identical?)]
    {:file (.getPath geno-file)
     :phenotype-file (.getPath phe-file)
     :dynamic :mutating-template-contextual
     :steps steps
     :seed seed
     :rows (count genotype)
     :width (count genotype-ic)
     :genotype-identical? genotype-identical?
     :phenotype-identical? phenotype-identical?
     :identical? identical?
     :first-diff (when-not identical?
                   (first
                    (keep-indexed
                     (fn [idx [cg eg]]
                       (when-not (= cg eg)
                         {:layer :genotype :row idx :clojure cg :elisp eg}))
                     (map vector genotype (:genotype elisp-grid)))))}))

(def contextual-xcheck-ics
  "Dedicated coupled IC seeds for the contextual mutating-template cross-check.
  Width 32 to keep the emacs batch fast while exercising the full template +
  balance-mutation RNG path.  These use DISTINCT seeds from the R-repro-5
  non-contextual ICs (xcheck-mt-) and the balance ICs (xcheck-bal-) to avoid
  any file-level collision."
  [{:name "xcheck-mt-ctx-150200170" :seed 150200170 :width 32}
   {:name "xcheck-mt-ctx-150200171" :seed 150200171 :width 32}
   {:name "xcheck-mt-ctx-150200172" :seed 150200172 :width 32}])

(defn ensure-contextual-ics! []
  (doseq [{:keys [name seed width]} contextual-xcheck-ics]
    (let [geno-path (io/file "resources/ics" (str name ".edn"))
          phe-path (io/file "resources/phenotype-ics" (str name ".edn"))]
      (when-not (.exists geno-path)
        (engine/save-ic! geno-path seed width))
      (when-not (.exists phe-path)
        (engine/save-phenotype-ic! phe-path seed width)))))

(defn contextual-ic-files []
  (ensure-contextual-ics!)
  (->> (file-seq (io/file "resources/ics"))
       (filter #(.isFile %))
       (filter #(str/starts-with? (.getName %) "xcheck-mt-ctx-"))
       (filter #(str/ends-with? (.getName %) ".edn"))
       (sort-by #(.getName %))
       vec))

(defn run-contextual-cross-check
  ([] (run-contextual-cross-check 120))
  ([steps]
   (let [geno-files (contextual-ic-files)
         results (mapv (fn [{:keys [seed]} geno-file]
                         (let [phe-file (io/file "resources/phenotype-ics"
                                                 (.getName geno-file))]
                           (compare-contextual-ic geno-file phe-file steps seed)))
                       contextual-xcheck-ics
                       geno-files)
         report {:dynamic :mutating-template-contextual
                 :engine :scirepro.mutating-template
                 :variant :evolve-sigil-with-mutating-template
                 :context :phenotype-quadruples
                 :ground-truth xcheck/elisp-path
                 :route :injected-stream
                 :description "CONTEXTUAL elisp dynamic (evolve-sigil-fn alias) with phenotype context quadruples: template + per-bit match/fallback + balance-mutation. Coupled pheno->geno driver (co-evolve-phenotype-and-genotype)."
                 :results results
                 :ok? (every? :identical? results)}
         out-file (io/file "out/mutating-template-context-cross-check.edn")]
     (.mkdirs (.getParentFile out-file))
     (spit out-file (with-out-str (prn report)))
     report)))

(defn -main [& args]
  (let [steps (if-let [arg (first args)] (parse-long arg) 120)
        report (run-contextual-cross-check steps)]
    (println (format "MUTATING-TEMPLATE-CONTEXT CROSS-CHECK %s route=%s variant=%s context=phenotype-quadruples %d ICs x %d steps; report=%s"
                     (if (:ok? report) "OK" "FAIL")
                     (name (:route report))
                     (name (:variant report))
                     (count (:results report))
                     steps
                     "out/mutating-template-context-cross-check.edn"))
    (when-not (:ok? report)
      (doseq [result (:results report)]
        (when-not (:identical? result)
          (println (:dynamic result) (:file result) (:first-diff result)))))
    (System/exit (if (:ok? report) 0 1))))
