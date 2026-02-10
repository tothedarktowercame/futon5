(ns futon5.scripts.output
  "Small helpers for scripts that write results to /tmp by default.

  Goals:
  - Always print what paths were written.
  - Warn when overwriting existing files/dirs (without changing behavior)."
  (:require [clojure.java.io :as io]))

(defn abs-path
  [path]
  (.getAbsolutePath (io/file path)))

(defn- exists?
  [path]
  (.exists (io/file path)))

(defn- nonempty-dir?
  [path]
  (let [f (io/file path)]
    (and (.exists f)
         (.isDirectory f)
         (seq (.listFiles f)))))

(defn warn-overwrite-file!
  [path]
  (when (exists? path)
    (println "NOTE: overwriting existing file:" (abs-path path))))

(defn warn-append-file!
  [path]
  (when (exists? path)
    (println "NOTE: appending to existing file:" (abs-path path))))

(defn warn-overwrite-dir!
  [path]
  (when (nonempty-dir? path)
    (println "NOTE: writing into existing non-empty directory:" (abs-path path))))

(defn ensure-parent-dirs!
  [path]
  (let [f (io/file path)
        parent (.getParentFile f)]
    (when parent
      (.mkdirs parent)))
  path)

(defn spit-text!
  "Write text to path, warn on overwrite, ensure parent dirs, and print the final path."
  [path text]
  (warn-overwrite-file! path)
  (ensure-parent-dirs! path)
  (spit path text)
  (println "Wrote" (abs-path path))
  path)

(defn announce-wrote!
  "Print a standardized 'Wrote' line for a path."
  [path]
  (println "Wrote" (abs-path path))
  path)

(defn announce-outputs!
  "Print a standardized list of output paths."
  [label->path]
  (doseq [[label path] label->path]
    (when path
      (println (format "Wrote %s %s" (name label) (abs-path path))))))

