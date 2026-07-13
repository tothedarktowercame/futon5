(ns scirepro.mutation-cross-check
  "Cross-check the Clojure mutation engine against 256ca.el.

   Deterministic route (preferred per the lab standard): we shadow the elisp
   `random` source via cl-letf so that BOTH engines consume the SAME explicit
   flip events.  The elisp variant under test is
   `evolve-sigil-with-blending-mutation` (256ca.el:595-627), which performs the
   blend step then calls `mutate-rule-n` with mutation=1 — exactly one
   (random 8) bit flip per cell per generation (256ca.el:571-591).

   To drive this deterministically we inject a cl-letf around `random` that
   returns, in order:
     1. For each cell each generation: position = the allele index from the
        pre-generated event stream (or a no-op if no event for that cell/gen).
   Because mutate-rule-n always calls (random 8) exactly once per invocation
   (line 573), and evolve-sigil-with-blending-mutation always calls
   mutate-rule-n with mutation=1 (line 596,627), the RNG call sequence is:
   one (random 8) per cell per generation, in cell order, in generation order.
   We build an injection list matching that exact call sequence."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [scirepro.engine :as engine]
            [scirepro.cross-check :as xcheck]))

(defn- build-random-injection
  "Build the flat sequence of (random 8) return values that mutate-rule-n
   will consume, given the mutation event stream for WIDTH cells over STEPS
   generations.  mutate-rule-n is called once per cell per generation (always
   with n=1), drawing one (random 8) each time (256ca.el:573).  Cells that have
   no mutation event still get a draw (which is a no-op because the byte is
   flipped then flipped back — but actually NO: evolve-sigil-with-blending-mutation
   ALWAYS calls mutate-rule-n with mutation=1 regardless. So EVERY cell gets one
   flip per generation in the elisp.)

   This means the elisp's blending-mutation variant flips EVERY cell EVERY
   generation — there is no rate gate in that variant.  The mutation=1 on line
   596 is unconditional.  To cross-check grid-identity we must therefore use a
   rate of 1.0 (every cell flips) and provide the allele for every cell/gen."
  [width steps event-map]
  (for [gen (range 1 (inc steps))
        cell (range width)]
    (if-let [alleles (get-in event-map [gen cell])]
      (first alleles)  ; mutate-rule-n with n=1 draws one position
      0)))             ; no event → still consumes one (random 8); the flip
                       ; still happens in elisp, so rate-1.0 is required

(defn- mutation-elisp-program
  "Elisp program that runs evolve-sigil-with-blending-mutation with `random`
   shadowed to return values from INJECTION in order.  This forces the elisp
   to consume exactly the allele positions we pre-generated."
  [ic steps injection]
  (str "(require 'cl)\n"
       "(provide 'hexrgb)\n"
       "(defalias 'string-to-int 'string-to-number)\n"
       "(load-file " (pr-str xcheck/elisp-path) ")\n"
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
       "  (defun sci-evolve-cell (row i)\n"
       "    (let* ((n (length row))\n"
       "           (left (if (= i 0) 0 (aref row (- i 1))))\n"
       "           (center (aref row i))\n"
       "           (right (if (= i (- n 1)) 0 (aref row (+ i 1))))\n"
       "           (result (first (evolve-sigil-with-blending-mutation\n"
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

(defn emacs-mutation-grid
  "Run the shadowed-random elisp and return the grid."
  [ic steps injection]
  (let [program (mutation-elisp-program ic steps injection)
        file (java.io.File/createTempFile "sci-repro-mut-xcheck" ".el")]
    (try
      (spit file program)
      (let [{:keys [exit out err]} (sh/sh "emacs" "--batch" "-l" (.getPath file))]
        (when-not (zero? exit)
          (throw (ex-info "emacs mutation cross-check failed"
                          {:exit exit :out out :err err})))
        (or (xcheck/normalize-elisp-output out)
            (throw (ex-info "could not parse emacs mutation output"
                            {:out out :err err}))))
      (finally
        (.delete file)))))

(defn compare-mutation-ic
  "Cross-check one IC: generate a rate-1.0 mutation stream (every cell flips
   once per generation), inject it into both engines, require grid-identity."
  [file steps seed]
  (let [ic (engine/read-ic file)
        width (count ic)
        ;; Rate 1.0 because evolve-sigil-with-blending-mutation unconditionally
        ;; calls mutate-rule-n with mutation=1 for EVERY cell (256ca.el:596).
        stream (engine/generate-mutation-stream seed width steps 1.0 :uniform)
        event-map (engine/stream->event-map stream)
        clj-grid (engine/evolve-with-mutation ic steps stream)
        injection (build-random-injection width steps event-map)
        elisp-grid (emacs-mutation-grid ic steps injection)
        identical? (= clj-grid elisp-grid)]
    {:file (.getPath file)
     :dynamic :mutation
     :steps steps
     :seed seed
     :rows (count clj-grid)
     :width width
     :identical? identical?
     :first-diff (when-not identical?
                   (first (keep-indexed (fn [idx [a b]]
                                          (when-not (= a b)
                                            {:row idx :clojure a :elisp b}))
                                        (map vector clj-grid elisp-grid))))}))

(def mutation-xcheck-ics
  "Dedicated IC seeds for the mutation cross-check (rate-1.0, width 32 to keep
   the emacs batch fast — every cell flips every step)."
  [{:name "xcheck-mut-150200140" :seed 150200140 :width 32}
   {:name "xcheck-mut-150200141" :seed 150200141 :width 32}
   {:name "xcheck-mut-150200142" :seed 150200142 :width 32}])

(defn ensure-mutation-ics! []
  (doseq [{:keys [name seed width]} mutation-xcheck-ics]
    (let [path (io/file "resources/ics" (str name ".edn"))]
      (when-not (.exists path)
        (engine/save-ic! path seed width)))))

(defn mutation-ic-files []
  (ensure-mutation-ics!)
  (->> (file-seq (io/file "resources/ics"))
       (filter #(.isFile %))
       (filter #(str/starts-with? (.getName %) "xcheck-mut-"))
       (filter #(str/ends-with? (.getName %) ".edn"))
       (sort-by #(.getName %))
       vec))

(defn run-mutation-cross-check
  ([] (run-mutation-cross-check 120))
  ([steps]
   (let [files (mutation-ic-files)
         results (mapv (fn [{:keys [seed]} file]
                         (compare-mutation-ic file steps seed))
                       mutation-xcheck-ics
                       files)
         report {:dynamic :mutation
                 :engine :scirepro.engine
                 :variant :evolve-sigil-with-blending-mutation
                 :ground-truth xcheck/elisp-path
                 :route :injected-stream
                 :results results
                 :ok? (every? :identical? results)}
         out-file (io/file "out/mutation-cross-check.edn")]
     (.mkdirs (.getParentFile out-file))
     (spit out-file (with-out-str (prn report)))
     report)))

(defn -main [& args]
  (let [steps (if-let [arg (first args)] (parse-long arg) 120)
        report (run-mutation-cross-check steps)]
    (println (format "MUTATION CROSS-CHECK %s route=%s variant=%s %d ICs x %d steps; report=%s"
                     (if (:ok? report) "OK" "FAIL")
                     (name (:route report))
                     (name (:variant report))
                     (count (:results report))
                     steps
                     "out/mutation-cross-check.edn"))
    (when-not (:ok? report)
      (doseq [result (:results report)]
        (when-not (:identical? result)
          (println (:dynamic result) (:file result) (:first-diff result)))))
    (System/exit (if (:ok? report) 0 1))))
