(ns scirepro.cross-check
  (:gen-class)
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [scirepro.engine :as engine]))

(def project-root
  (.getCanonicalFile (io/file "../..")))

(def elisp-path
  (.getPath (io/file project-root "256ca.el")))

(def default-ics
  ;; Cross-check ICs live under their own xcheck- prefix: the seed-*.edn files
  ;; belong to the measurement cohort (width 80) and must stay homogeneous.
  ;; Reusing them here at width 64 is what caused the slice-2 mixed-width
  ;; cohort defect (see mission checkpoint, slice-2 review).
  [{:name "xcheck-150200130" :seed 150200130 :width 64}
   {:name "xcheck-150200131" :seed 150200131 :width 64}
   {:name "xcheck-150200132" :seed 150200132 :width 64}])

(defn ensure-default-ics! []
  (doseq [{:keys [name seed width]} default-ics]
    (let [path (io/file "resources/ics" (str name ".edn"))]
      (when-not (.exists path)
        (engine/save-ic! path seed width)))
    (let [path (io/file "resources/phenotype-ics" (str name ".edn"))]
      (when-not (.exists path)
        (engine/save-phenotype-ic! path seed width)))))

(defn ic-files []
  (ensure-default-ics!)
  (->> (file-seq (io/file "resources/ics"))
       (filter #(.isFile %))
       (filter #(str/starts-with? (.getName %) "xcheck-"))
       (filter #(str/ends-with? (.getName %) ".edn"))
       (sort-by #(.getName %))
       vec))

(defn- elisp-fn-name [dynamic]
  (case dynamic
    :multiply "evolve-sigil"
    :blend "evolve-sigil-with-blending"))

(defn- elisp-program [ic steps dynamic]
  (str "(require 'cl)\n"
       "(provide 'hexrgb)\n"
       "(defalias 'string-to-int 'string-to-number)\n"
       "(load-file " (pr-str elisp-path) ")\n"
       "(setq sci-evolve-fn '" (elisp-fn-name dynamic) ")\n"
       "(defun sci-binary8 (n)\n"
       "  (let ((s \"\"))\n"
       "    (dotimes (i 8 s)\n"
       "      (setq s (concat s (if (= 0 (logand n (lsh 1 (- 7 i)))) \"0\" \"1\"))))))\n"
       "(defun sci-rule-to-sigil (n)\n"
       "  (second (get-genotype-from-rule (sci-binary8 n))))\n"
       "(defun sci-evolve-cell (row i)\n"
       "  (let* ((n (length row))\n"
       "         (left (if (= i 0) 0 (aref row (- i 1))))\n"
       "         (center (aref row i))\n"
       "         (right (if (= i (- n 1)) 0 (aref row (+ i 1))))\n"
       "         (result (first (funcall sci-evolve-fn (sci-rule-to-sigil center)\n"
       "                                 (sci-rule-to-sigil left)\n"
       "                                 (sci-rule-to-sigil right)))))\n"
       "    (string-to-number result 2)))\n"
       "(defun sci-evolve-row (row)\n"
       "  (let ((v (vconcat row)) (out nil))\n"
       "    (dotimes (i (length v) (vconcat (nreverse out)))\n"
       "      (setq out (cons (sci-evolve-cell v i) out)))))\n"
       "(let* ((steps " steps ")\n"
       "       (row '" (pr-str ic) ")\n"
       "       (rows (list row)))\n"
       "  (dotimes (_ steps)\n"
       "    (setq row (sci-evolve-row row))\n"
       "    (setq rows (cons row rows)))\n"
       "  (princ \"\\nSCI_REPRO_EDN \")\n"
       "  (princ (prin1-to-string (vconcat (reverse rows)))))\n"))

(defn normalize-elisp-output [out]
  (when-let [line (some #(when (str/starts-with? % "SCI_REPRO_EDN ")
                          (subs % (count "SCI_REPRO_EDN ")))
                        (str/split-lines out))]
    (edn/read-string
     (-> line
         (str/replace "(" "[")
         (str/replace ")" "]")))))

(defn- coupled-elisp-program [genotype phenotype steps]
  (str "(require 'cl)\n"
       "(provide 'hexrgb)\n"
       "(defalias 'string-to-int 'string-to-number)\n"
       "(load-file " (pr-str elisp-path) ")\n"
       "(fset 'evolve-sigil-fn 'evolve-sigil-with-blending)\n"
       "(defun sci-binary8 (n)\n"
       "  (let ((s \"\"))\n"
       "    (dotimes (i 8 s)\n"
       "      (setq s (concat s (if (= 0 (logand n (lsh 1 (- 7 i)))) \"0\" \"1\"))))))\n"
       "(defun sci-rule-to-sigil (n)\n"
       "  (second (get-genotype-from-rule (sci-binary8 n))))\n"
       "(defun sci-row-to-sigils (row)\n"
       "  (apply #'concat (mapcar #'sci-rule-to-sigil row)))\n"
       "(defun sci-sigils-to-rules (s)\n"
       "  (vconcat (mapcar (lambda (ch)\n"
       "            (string-to-number (first (get-genotype-from-sigil (char-to-string ch))) 2))\n"
       "          (string-to-list s))))\n"
       "(defun sci-bits-to-string (row)\n"
       "  (apply #'concat (mapcar #'number-to-string row)))\n"
       "(defun sci-string-to-bits (s)\n"
       "  (vconcat (mapcar (lambda (ch) (string-to-number (char-to-string ch)))\n"
       "                   (string-to-list s))))\n"
       "(let* ((steps " steps ")\n"
       "       (gen (sci-row-to-sigils '" (pr-str genotype) "))\n"
       "       (phe (sci-bits-to-string '" (pr-str phenotype) "))\n"
       "       (gen-rows (list (sci-sigils-to-rules gen)))\n"
       "       (phe-rows (list (sci-string-to-bits phe))))\n"
       "  (dotimes (_ steps)\n"
       "    (let ((result (co-evolve-phenotype-and-genotype gen phe)))\n"
       "      (setq gen (first result))\n"
       "      (setq phe (second result))\n"
       "      (setq gen-rows (cons (sci-sigils-to-rules gen) gen-rows))\n"
       "      (setq phe-rows (cons (sci-string-to-bits phe) phe-rows))))\n"
       "  (princ \"\\nSCI_REPRO_EDN \")\n"
       "  (princ (prin1-to-string (vector (vconcat (reverse gen-rows))\n"
       "                                  (vconcat (reverse phe-rows))))))\n"))

(defn emacs-grid [ic steps dynamic]
  (let [program (elisp-program ic steps dynamic)
        file (java.io.File/createTempFile "sci-repro-cross-check" ".el")]
    (try
      (spit file program)
      (let [{:keys [exit out err]} (sh/sh "emacs" "--batch" "-l" (.getPath file))]
        (when-not (zero? exit)
          (throw (ex-info "emacs cross-check failed" {:exit exit :out out :err err})))
        (or (normalize-elisp-output out)
            (throw (ex-info "could not parse emacs cross-check output" {:out out :err err}))))
      (finally
        (.delete file)))))

(defn emacs-coupled-grid [genotype phenotype steps]
  (let [program (coupled-elisp-program genotype phenotype steps)
        file (java.io.File/createTempFile "sci-repro-coupled-cross-check" ".el")]
    (try
      (spit file program)
      (let [{:keys [exit out err]} (sh/sh "emacs" "--batch" "-l" (.getPath file))]
        (when-not (zero? exit)
          (throw (ex-info "emacs coupled cross-check failed" {:exit exit :out out :err err})))
        (let [[gen phe] (or (normalize-elisp-output out)
                            (throw (ex-info "could not parse emacs coupled output"
                                            {:out out :err err})))]
          {:genotype gen :phenotype phe}))
      (finally
        (.delete file)))))

(defn compare-ic [file steps dynamic]
  (let [ic (engine/read-ic file)
        clj-grid (engine/evolve ic steps dynamic)
        elisp-grid (emacs-grid ic steps dynamic)
        identical? (= clj-grid elisp-grid)]
    {:file (.getPath file)
     :dynamic dynamic
     :steps steps
     :rows (count clj-grid)
     :width (count ic)
     :identical? identical?
     :first-diff (when-not identical?
                   (first (keep-indexed (fn [idx [a b]]
                                          (when-not (= a b)
                                            {:row idx :clojure a :elisp b}))
                                        (map vector clj-grid elisp-grid))))}))

(defn compare-coupled-ic [file steps]
  (let [phenotype-file (io/file "resources/phenotype-ics" (.getName file))
        genotype (engine/read-ic file)
        phenotype (engine/read-ic phenotype-file)
        clj-grid (engine/coupled-evolve genotype phenotype steps)
        elisp-grid (emacs-coupled-grid genotype phenotype steps)
        genotype-identical? (= (:genotype clj-grid) (:genotype elisp-grid))
        phenotype-identical? (= (:phenotype clj-grid) (:phenotype elisp-grid))]
    {:file (.getPath file)
     :phenotype-file (.getPath phenotype-file)
     :dynamic :coupled
     :steps steps
     :rows (count (:genotype clj-grid))
     :width (count genotype)
     :genotype-identical? genotype-identical?
     :phenotype-identical? phenotype-identical?
     :identical? (and genotype-identical? phenotype-identical?)
     :first-diff (when-not (and genotype-identical? phenotype-identical?)
                   (first
                    (keep-indexed
                     (fn [idx [[cg cp] [eg ep]]]
                       (cond
                         (not= cg eg) {:layer :genotype :row idx :clojure cg :elisp eg}
                         (not= cp ep) {:layer :phenotype :row idx :clojure cp :elisp ep}))
                     (map vector
                          (map vector (:genotype clj-grid) (:phenotype clj-grid))
                          (map vector (:genotype elisp-grid) (:phenotype elisp-grid))))))}))

(defn run-cross-check
  ([] (run-cross-check 120))
  ([steps]
   (let [files (take 3 (ic-files))
         results {:multiply (mapv #(compare-ic % steps :multiply) files)
                  :blend (mapv #(compare-ic % steps :blend) files)
                  :coupled (mapv #(compare-coupled-ic % steps) files)}
         report {:dynamics [:multiply :blend :coupled]
                 :engine :scirepro.engine
                 :ground-truth elisp-path
                 :results results
                 :ok? (every? :identical? (mapcat val results))}
         out-file (io/file "out/cross-check.edn")]
     (.mkdirs (.getParentFile out-file))
     (spit out-file (with-out-str (prn report)))
     report)))

(defn -main [& args]
  (let [steps (if-let [arg (first args)] (parse-long arg) 120)
        report (run-cross-check steps)]
    (println (format "CROSS-CHECK %s dynamics=%s %d ICs x %d steps; report=%s"
                     (if (:ok? report) "OK" "FAIL")
                     (pr-str (:dynamics report))
                     (count (get-in report [:results :multiply]))
                     steps
                     "out/cross-check.edn"))
    (when-not (:ok? report)
      (doseq [result (mapcat val (:results report))]
        (when-not (:identical? result)
          (println (:dynamic result) (:file result) (:first-diff result)))))
    (System/exit (if (:ok? report) 0 1))))
