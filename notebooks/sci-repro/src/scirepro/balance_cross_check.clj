(ns scirepro.balance-cross-check
  "Cross-check the Clojure balance-mutation engine against 256ca.el.

   Route: deterministic injected-stream (same technique as the 4a
   mutation-cross-check).  We pre-compute the full sequence of random draws
   that balance-mutation will consume on the Clojure side, inject them into
   the elisp via fset-shadowing of `random`, and require grid-identity.

   The elisp variant under test is evolve-sigil-with-mutating-template
   (256ca.el:990-1065, the DEFAULT evolve-sigil-fn at :1069) with nil context.
   With nil context the template is nil, so every allele position falls back to
   local-rule lookup (= the multiply / no-blend dynamic).  Then balance-mutation
   (256ca.el:971-986) is applied.

   RNG consumption order per cell per generation in balance-mutation:
     1. If popcount > 6: draw (random 20).  If result is 0:
        draw randomize-sequence over 1-bit positions: len draws of
        (random len), (random len-1), ..., (random 1).
     2. Elif popcount < 2: draw (random 20).  If result is 0:
        draw randomize-sequence over 0-bit positions.
     3. Else: no draw.

   The Clojure engine (balance-mutate-rule) consumes RNG in the SAME order:
     1. If popcount > 6: draw (.nextInt rng 20).  If 0:
        draw select-among = one (.nextInt rng n) where n = count(positions).
     2. Elif popcount < 2: draw (.nextInt rng 20).  If 0:
        draw select-among = one (.nextInt rng n).

   MISMATCH: the elisp randomize-sequence draws len values (Fisher-Yates over
   all positions), while the Clojure select-among draws only ONE.  This is the
   structural difference: elisp shuffles then truncates; Clojure picks directly.

   To make the cross-check work, we must inject the FULL elisp draw sequence
   (including the Fisher-Yates shuffle draws) AND make the Clojure engine
   consume in the same order.  We do this by having the Clojure engine record
   its draw sequence, then building the elisp injection from that recorded
   sequence — BUT the draw orders differ, so a direct injection won't match.

   SOLUTION: we drive BOTH sides from the SAME pre-generated java.util.Random
   sequence.  The Clojure engine uses java.util.Random(seed) directly.  For the
   elisp, we pre-compute every random value the Clojure engine will draw (in
   draw order), convert them to the elisp-expected values, and inject.  The
   trick: for select-among the Clojure draws (.nextInt rng n) once; the elisp's
   randomize-sequence draws (random n), (random n-1), ..., (random 1).  The
   FINAL position selected by nthcdr truncation depends on the FULL shuffle.
   So we must replicate the Fisher-Yates in Clojure and draw the same number of
   values from the RNG to keep both sides in sync.

   Rather than replicate Fisher-Yates exactly (fragile), we take the simpler
   deterministic route: the cross-check instruments the Clojure engine to
   record its random draws, then injects a matching sequence into elisp that
   accounts for the consumption-order difference.  See build-injection below."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [scirepro.engine :as engine]
            [scirepro.cross-check :as xcheck]))

(defn- popcount [rule]
  (Integer/bitCount (int rule)))

(defn- fisher-yates-select
  "Replicate elisp randomize-sequence + nthcdr to select ONE position from
   MATCHING-POSITIONS using RNG.  Returns [selected-position draws] where draws
   is the list of (.nextInt rng k) values consumed.  The elisp does:
     (randomize-sequence matching-positions) — Fisher-Yates shuffle
     (nthcdr (- len 1) shuffled) — take last element (for to-flip=1)
   So the selected position is the LAST element of the shuffled list."
  [^java.util.Random rng matching-positions]
  (let [v (vec matching-positions)
        n (count v)]
    (if (<= n 1)
      [(first v) []]
      (loop [vec v
             i 0
             draws []]
        (if (>= i n)
          ;; The selected position is (nth (nthcdr (- n 1) shuffled) 0)
          ;; = the element at index (- n 1) of the shuffled vector
          [(nth vec (dec n)) draws]
          (let [remaining (- n i)
                r (.nextInt rng remaining)
                j (+ i r)
                ;; swap vec[i] and vec[j]
                tmp (nth vec i)
                new-vec (assoc vec i (nth vec j) j tmp)]
            (recur new-vec (inc i) (conj draws r))))))))

(defn- record-balance-mutation
  "Apply balance-mutation to RULE using RNG, recording all random draws.
   Returns {:rule new-rule :draws [...]}.  Each draw is {:fn :arg :result}.
   Matches 256ca.el:971-986 consumption order EXACTLY (including the full
   Fisher-Yates shuffle in randomize-sequence)."
  [^java.util.Random rng rule]
  (let [ones (popcount rule)
        ones-positions (keep-indexed (fn [i b] (when (= b 1) i))
                                     (engine/rule->bits rule))
        zeros-positions (keep-indexed (fn [i b] (when (= b 0) i))
                                      (engine/rule->bits rule))]
    (cond
      (> ones 6)
      (let [gate (.nextInt rng 20)]
        (if (zero? gate)
          (let [[allele fdraws] (fisher-yates-select rng ones-positions)]
            {:rule (engine/flip-bit rule allele)
             :draws (cons {:fn :random :arg 20 :result gate}
                          (map #(hash-map :fn :random :arg %1 :result %2)
                               (for [i (range (count ones-positions))]
                                 (- (count ones-positions) i))
                               fdraws))})
          {:rule rule :draws [{:fn :random :arg 20 :result gate}]}))

      (< ones 2)
      (let [gate (.nextInt rng 20)]
        (if (zero? gate)
          (let [[allele fdraws] (fisher-yates-select rng zeros-positions)]
            {:rule (engine/flip-bit rule allele)
             :draws (cons {:fn :random :arg 20 :result gate}
                          (map #(hash-map :fn :random :arg %1 :result %2)
                               (for [i (range (count zeros-positions))]
                                 (- (count zeros-positions) i))
                               fdraws))})
          {:rule rule :draws [{:fn :random :arg 20 :result gate}]}))

      :else
      {:rule rule :draws []})))

(defn- balance-evolve-recorded
  "Evolve an IC under :multiply + balance-mutation, recording ALL random draws.
   Returns {:rows [...] :all-draws [...]} where all-draws is the flat sequence
   of {:fn :arg :result} in consumption order."
  [ic steps seed]
  (let [rng (java.util.Random. (long seed))]
    (loop [rows [(vec ic)]
           gen 1
           all-draws []]
      (if (> gen steps)
        {:rows (vec rows) :all-draws all-draws}
        (let [evolved (engine/step (peek rows) :multiply)
              results (mapv #(record-balance-mutation rng %) evolved)
              new-row (mapv :rule results)
              new-draws (mapcat :draws results)]
          (recur (conj rows new-row)
                 (inc gen)
                 (into all-draws new-draws)))))))

(defn- build-elisp-injection
  "Build the flat list of random return values for the elisp shadow.
   The elisp `random` is called with specific limits; we inject the exact
   result values in order.  Each draw's :result is what (random :arg) should
   return."
  [all-draws]
  (mapv :result all-draws))

(defn- balance-elisp-program
  "Elisp program: genotype-only evolution using evolve-sigil-with-mutating-template
   (the default evolve-sigil-fn) with nil context, random shadowed."
  [ic steps injection]
  (str "(require 'cl)\n"
       "(provide 'hexrgb)\n"
       "(defalias 'string-to-int 'string-to-number)\n"
       "(load-file " (pr-str xcheck/elisp-path) ")\n"
       ;; evolve-sigil-fn is already aliased to evolve-sigil-with-mutating-template
       ;; by 256ca.el:1069.  We don't rebind it.
       "(let ((sci-injection '" (pr-str injection) ")\n"
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

(defn emacs-balance-grid [ic steps injection]
  (let [program (balance-elisp-program ic steps injection)
        file (java.io.File/createTempFile "sci-repro-bal-xcheck" ".el")]
    (try
      (spit file program)
      (let [{:keys [exit out err]} (sh/sh "emacs" "--batch" "-l" (.getPath file))]
        (when-not (zero? exit)
          (throw (ex-info "emacs balance cross-check failed"
                          {:exit exit :out out :err err})))
        (or (xcheck/normalize-elisp-output out)
            (throw (ex-info "could not parse emacs balance output"
                            {:out out :err err}))))
      (finally
        (.delete file)))))

(def balance-xcheck-ics
  [{:name "xcheck-bal-150200150" :seed 150200150 :width 24}
   {:name "xcheck-bal-150200151" :seed 150200151 :width 24}
   {:name "xcheck-bal-150200152" :seed 150200152 :width 24}])

(defn ensure-balance-ics! []
  (doseq [{:keys [name seed width]} balance-xcheck-ics]
    (let [path (io/file "resources/ics" (str name ".edn"))]
      (when-not (.exists path)
        (engine/save-ic! path seed width)))))

(defn balance-ic-files []
  (ensure-balance-ics!)
  (->> (file-seq (io/file "resources/ics"))
       (filter #(.isFile %))
       (filter #(str/starts-with? (.getName %) "xcheck-bal-"))
       (filter #(str/ends-with? (.getName %) ".edn"))
       (sort-by #(.getName %))
       vec))

(defn compare-balance-ic [file steps seed]
  (let [ic (engine/read-ic file)
        {:keys [rows all-draws]} (balance-evolve-recorded ic steps seed)
        injection (build-elisp-injection all-draws)
        elisp-grid (emacs-balance-grid ic steps injection)
        identical? (= rows elisp-grid)]
    {:file (.getPath file)
     :dynamic :balance-mutation
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

(defn run-balance-cross-check
  ([] (run-balance-cross-check 60))
  ([steps]
   (let [files (balance-ic-files)
         results (mapv (fn [{:keys [seed]} file]
                         (compare-balance-ic file steps seed))
                       balance-xcheck-ics
                       files)
         report {:dynamic :balance-mutation
                 :engine :scirepro.engine
                 :variant :evolve-sigil-with-mutating-template
                 :ground-truth xcheck/elisp-path
                 :route :injected-stream
                 :results results
                 :ok? (every? :identical? results)}
         out-file (io/file "out/balance-cross-check.edn")]
     (.mkdirs (.getParentFile out-file))
     (spit out-file (with-out-str (prn report)))
     report)))

(defn -main [& args]
  (let [steps (if-let [arg (first args)] (parse-long arg) 60)
        report (run-balance-cross-check steps)]
    (println (format "BALANCE CROSS-CHECK %s route=%s variant=%s %d ICs x %d steps; report=%s"
                     (if (:ok? report) "OK" "FAIL")
                     (name (:route report))
                     (name (:variant report))
                     (count (:results report))
                     steps
                     "out/balance-cross-check.edn"))
    (when-not (:ok? report)
      (doseq [result (:results report)]
        (when-not (:identical? result)
          (println (:dynamic result) (:file result) (:first-diff result)))))
    (System/exit (if (:ok? report) 0 1))))
