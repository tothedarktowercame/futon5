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
  [{:name "seed-150200130" :seed 150200130 :width 64}
   {:name "seed-150200131" :seed 150200131 :width 64}
   {:name "seed-150200132" :seed 150200132 :width 64}])

(defn ensure-default-ics! []
  (doseq [{:keys [name seed width]} default-ics]
    (let [path (io/file "resources/ics" (str name ".edn"))]
      (when-not (.exists path)
        (engine/save-ic! path seed width)))))

(defn ic-files []
  (ensure-default-ics!)
  (->> (file-seq (io/file "resources/ics"))
       (filter #(.isFile %))
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

(defn- normalize-elisp-output [out]
  (when-let [line (some #(when (str/starts-with? % "SCI_REPRO_EDN ")
                          (subs % (count "SCI_REPRO_EDN ")))
                        (str/split-lines out))]
    (edn/read-string
     (-> line
         (str/replace "(" "[")
         (str/replace ")" "]")))))

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

(defn run-cross-check
  ([] (run-cross-check 120))
  ([steps]
   (let [files (take 3 (ic-files))
         results {:multiply (mapv #(compare-ic % steps :multiply) files)
                  :blend (mapv #(compare-ic % steps :blend) files)}
         report {:dynamics [:multiply :blend]
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
