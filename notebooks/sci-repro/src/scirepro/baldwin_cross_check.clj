(ns scirepro.baldwin-cross-check
  "Cross-check the Clojure Baldwin dynamic against 256ca.el.

   Deterministic route (per the lab standard): we shadow the elisp `random`
   source so that BOTH engines consume the SAME explicit random stream.

   The elisp variant under test is `evolve-sigil-with-blending-baldwin`
   (256ca.el:634–686), driven contextually by `co-evolve-phenotype-and-
   genotype` (256ca.el:~1190) via `evolve-sigil-string-contextually`.

   RNG consumption order per generation (cell order 0..width-1):
     - Head cell (i=0): evolve-sigil-with-blending-baldwin called with 3
       args → context=nil → (and nil ...) short-circuits → NO random draw.
     - Middle cells (i=1..width-2): called with 4 args → context non-nil →
       draws (random 3) once.  If < 1 (i.e. == 0): draws (mutations+2) ×
       (random 8) via mutate-rule-n.
     - Tail cell (i=width-1): same as head → NO random draw.

   Because the gate decision and mutations count depend on the context
   (which is derived from phenotype state), the exact number of random
   draws per generation is grid-state-dependent.  We therefore run the
   Clojure engine first with a recording rng-fn that generates valid values
   (0..2 for random-3 calls, 0..7 for random-8 calls) and captures the full
   consumed stream.  That recorded stream is then injected into the elisp
   shadow-random.  If both sides have identical dynamics, they consume the
   same number of values in the same order → grid-identity."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [scirepro.engine :as engine]
            [scirepro.baldwin :as baldwin]
            [scirepro.cross-check :as xcheck]))

;; ---------------------------------------------------------------------------
;; Recording RNG: generates values on-the-fly and records the full stream.
;; ---------------------------------------------------------------------------

(defn- make-recording-rng
  "Create an rng-fn that draws from a seeded java.util.Random.  Each call
   receives a LIMIT (3 for the gate draw, 8 for mutate-rule-n positions).
   Returns [rng-fn consumed-atom].  The rng-fn returns (.nextInt rng limit)
   and appends the value to consumed-atom."
  [seed]
  (let [^java.util.Random rng (java.util.Random. (long seed))
        consumed (atom [])
        rng-fn (fn rng-fn
                 [limit]
                 (let [val (.nextInt rng (int limit))]
                   (swap! consumed conj val)
                   val))]
    [rng-fn consumed]))

(defn- baldwin-step-labeled
  "One co-evolution generation under the Baldwin dynamic, using a LABELED
   rng-fn that receives the limit (3 for the gate, 8 for mutate-rule-n).

   This is the same logic as baldwin/baldwin-step but passes the limit
   argument to rng-fn so the recording RNG can generate valid values."
  [{:keys [genotype phenotype]} rng-fn]
  (let [width (count genotype)
        new-phenotype (engine/phenotype-step genotype phenotype)
        contexts (baldwin/build-context-quadruples phenotype new-phenotype)
        new-genotype
        (mapv (fn [i center-rule]
                (let [left-rule (if (zero? i) 0 (nth genotype (dec i)))
                      right-rule (if (= i (dec width)) 0 (nth genotype (inc i)))
                      context-str (if (or (zero? i) (= i (dec width)))
                                    nil
                                    (nth contexts (dec i)))
                      output-rule (engine/blend-cell left-rule center-rule right-rule)]
                  (if (and context-str (< (rng-fn 3) 1))
                    (let [mutations (baldwin/count-context-matches context-str)
                          n (+ mutations 2)]
                      (loop [j 0 rule output-rule]
                        (if (>= j n)
                          rule
                          (let [pos (rng-fn 8)]
                            (recur (inc j) (engine/flip-bit rule pos))))))
                    output-rule)))
              (range)
              genotype)]
    {:genotype new-genotype
     :phenotype new-phenotype}))

(defn- baldwin-evolve-labeled
  "Evolve with the labeled rng-fn.  Returns {:grids ... :stream ...}."
  [genotype phenotype steps seed]
  (let [[rng-fn consumed-atom] (make-recording-rng seed)
        states (vec (take (inc steps)
                          (iterate #(baldwin-step-labeled % rng-fn)
                                   {:genotype (vec genotype)
                                    :phenotype (vec phenotype)})))
        stream @consumed-atom]
    {:genotype (mapv :genotype states)
     :phenotype (mapv :phenotype states)
     :stream stream}))

;; ---------------------------------------------------------------------------
;; Elisp program: drive co-evolution with Baldwin variant + shadow random.
;; ---------------------------------------------------------------------------

(defn- baldwin-elisp-program
  "Elisp program that runs co-evolve-phenotype-and-genotype with the Baldwin
   variant as evolve-sigil-fn, and `random` shadowed to return values from
   INJECTION in order."
  [genotype phenotype steps injection]
  (str "(require 'cl)\n"
       "(provide 'hexrgb)\n"
       "(defalias 'string-to-int 'string-to-number)\n"
       "(load-file " (pr-str xcheck/elisp-path) ")\n"
       ;; Set the Baldwin variant as the dynamic.
       "(fset 'evolve-sigil-fn 'evolve-sigil-with-blending-baldwin)\n"
       ;; Shadow `random` to pop from a fixed list.
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
       "         (gen (sci-row-to-sigils '" (pr-str genotype) "))\n"
       "         (phe (sci-bits-to-string '" (pr-str phenotype) "))\n"
       "         (gen-rows (list (sci-sigils-to-rules gen)))\n"
       "         (phe-rows (list (sci-string-to-bits phe))))\n"
       "    (dotimes (_ steps)\n"
       "      (let ((result (co-evolve-phenotype-and-genotype gen phe)))\n"
       "        (setq gen (first result))\n"
       "        (setq phe (second result))\n"
       "        (setq gen-rows (cons (sci-sigils-to-rules gen) gen-rows))\n"
       "        (setq phe-rows (cons (sci-string-to-bits phe) phe-rows))))\n"
       "    (princ \"\\nSCI_REPRO_EDN \")\n"
       "    (princ (prin1-to-string (vector (vconcat (reverse gen-rows))\n"
       "                                    (vconcat (reverse phe-rows)))))))\n"))

(defn- emacs-baldwin-grid
  "Run the shadowed-random elisp and return {:genotype ... :phenotype ...}."
  [genotype phenotype steps injection]
  (let [program (baldwin-elisp-program genotype phenotype steps injection)
        file (java.io.File/createTempFile "sci-repro-baldwin-xcheck" ".el")]
    (try
      (spit file program)
      (let [{:keys [exit out err]} (sh/sh "emacs" "--batch" "-l" (.getPath file))]
        (when-not (zero? exit)
          (throw (ex-info "emacs baldwin cross-check failed"
                          {:exit exit :out out :err err})))
        (let [[gen phe] (or (xcheck/normalize-elisp-output out)
                            (throw (ex-info "could not parse emacs baldwin output"
                                            {:out out :err err})))]
          {:genotype gen :phenotype phe}))
      (finally
        (.delete file)))))

;; ---------------------------------------------------------------------------
;; Cross-check driver.
;; ---------------------------------------------------------------------------

(def baldwin-xcheck-ics
  "Dedicated contextual ICs for the Baldwin cross-check (width 32 to keep
   emacs batch fast).  Each IC has a genotype (rule bytes) and a phenotype
   (binary)."
  [{:name "xcheck-baldwin-150200150" :seed 150200150 :width 32}
   {:name "xcheck-baldwin-150200151" :seed 150200151 :width 32}
   {:name "xcheck-baldwin-150200152" :seed 150200152 :width 32}])

(defn ensure-baldwin-ics! []
  (doseq [{:keys [name seed width]} baldwin-xcheck-ics]
    (let [gen-path (io/file "resources/ics" (str name ".edn"))
          phe-path (io/file "resources/phenotype-ics" (str name ".edn"))]
      (when-not (.exists gen-path)
        (engine/save-ic! gen-path seed width))
      (when-not (.exists phe-path)
        (engine/save-phenotype-ic! phe-path seed width)))))

(defn baldwin-ic-files []
  (ensure-baldwin-ics!)
  (->> (file-seq (io/file "resources/ics"))
       (filter #(.isFile %))
       (filter #(str/starts-with? (.getName %) "xcheck-baldwin-"))
       (filter #(str/ends-with? (.getName %) ".edn"))
       (sort-by #(.getName %))
       vec))

(defn compare-baldwin-ic
  "Cross-check one IC: run Clojure Baldwin with a recording RNG, then feed
   the recorded stream to elisp, require grid-identity."
  [gen-file phe-file steps seed]
  (let [genotype (engine/read-ic gen-file)
        phenotype (engine/read-ic phe-file)
        ;; Run Clojure with labeled recording RNG.
        clj-result (baldwin-evolve-labeled genotype phenotype steps seed)
        clj-gen (:genotype clj-result)
        clj-phe (:phenotype clj-result)
        stream (:stream clj-result)
        ;; Run elisp with the same injection stream.
        elisp-result (emacs-baldwin-grid genotype phenotype steps stream)
        elisp-gen (:genotype elisp-result)
        elisp-phe (:phenotype elisp-result)
        gen-identical? (= clj-gen elisp-gen)
        phe-identical? (= clj-phe elisp-phe)
        identical? (and gen-identical? phe-identical?)]
    {:file (.getPath gen-file)
     :phenotype-file (.getPath phe-file)
     :dynamic :baldwin
     :steps steps
     :seed seed
     :rows (count clj-gen)
     :width (count genotype)
     :stream-length (count stream)
     :genotype-identical? gen-identical?
     :phenotype-identical? phe-identical?
     :identical? identical?
     :first-diff (when-not identical?
                   (first
                    (keep-indexed
                     (fn [idx [[cg cp] [eg ep]]]
                       (cond
                         (not= cg eg) {:layer :genotype :row idx :clojure cg :elisp eg}
                         (not= cp ep) {:layer :phenotype :row idx :clojure cp :elisp ep}))
                     (map vector
                          (map vector clj-gen clj-phe)
                          (map vector elisp-gen elisp-phe)))))}))

(defn run-baldwin-cross-check
  ([] (run-baldwin-cross-check 120))
  ([steps]
   (let [gen-files (baldwin-ic-files)
         phe-files (mapv #(io/file "resources/phenotype-ics" (.getName %))
                         gen-files)
         results (mapv (fn [{:keys [seed]} gen-file phe-file]
                         (compare-baldwin-ic gen-file phe-file steps seed))
                       baldwin-xcheck-ics
                       gen-files
                       phe-files)
         report {:dynamic :baldwin
                 :engine :scirepro.baldwin
                 :variant :evolve-sigil-with-blending-baldwin
                 :driver :evolve-sigil-string-contextually
                 :ground-truth xcheck/elisp-path
                 :route :injected-stream
                 :results results
                 :ok? (every? :identical? results)}
         out-file (io/file "out/baldwin-cross-check.edn")]
     (.mkdirs (.getParentFile out-file))
     (spit out-file (with-out-str (prn report)))
     report)))

(defn -main [& args]
  (let [steps (if-let [arg (first args)] (parse-long arg) 120)
        report (run-baldwin-cross-check steps)]
    (println (format "BALDWIN CROSS-CHECK %s route=%s variant=%s %d ICs x %d steps; report=%s"
                     (if (:ok? report) "OK" "FAIL")
                     (name (:route report))
                     (name (:variant report))
                     (count (:results report))
                     steps
                     "out/baldwin-cross-check.edn"))
    (when-not (:ok? report)
      (doseq [result (:results report)]
        (when-not (:identical? result)
          (println (:dynamic result) (:file result) (:first-diff result)))))
    (System/exit (if (:ok? report) 0 1))))
