(ns xenotype-harness-render
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [babashka.process :as p]
            [futon5.mmca.render :as render]
            [futon5.xenotype.mermaid :as mermaid]))

(defn- usage []
  (str/join
   "\n"
   ["Render CA triptychs + wiring diagrams from harness run EDNs."
    ""
    "Usage:"
    "  bb -cp src:resources:data scripts/xenotype_harness_render.clj [options]"
    ""
    "Options:"
    "  --runs-dir PATH     Directory containing run EDN files."
    "  --out-dir PATH      Output directory (default = runs-dir)."
    "  --mmdc-cmd CMD      Mermaid command prefix (default: aa-exec -p chrome -- mmdc)."
    "  --help              Show this message."])))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{{"--help" "-h"}} flag)
          (recur more (assoc opts :help true))

          (= "--runs-dir" flag)
          (recur (rest more) (assoc opts :runs-dir (first more)))

          (= "--out-dir" flag)
          (recur (rest more) (assoc opts :out-dir (first more)))

          (= "--mmdc-cmd" flag)
          (recur (rest more) (assoc opts :mmdc-cmd (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- edn-files [dir]
  (->> (.listFiles (io/file dir))
       (filter #(.isFile %))
       (filter #(str/ends-with? (.getName %) ".edn"))
       (sort-by #(.getName %))))

(defn- base-name [path]
  (-> path
      (str/replace #"\.edn$" "")
      (str/replace #"^.*/" "")))

(defn- load-diagram [path]
  (when path
    (let [data (edn/read-string (slurp path))]
      (cond
        (and (map? data) (:diagram data)) (:diagram data)
        (map? data) data
        :else nil))))

(defn- render-triptych! [run out-dir label]
  (let [ppm (str (io/file out-dir (str label ".ppm")))
        png (str (io/file out-dir (str label ".png")))]
    (render/render-run->file! run ppm {:exotype? true})
    (p/process ["convert" ppm png] {:inherit true})
    {:ppm ppm :png png}))

(defn- render-wiring! [diagram out-dir label mmdc-cmd]
  (when diagram
    (let [mmd (str (io/file out-dir (str label ".wiring.mmd")))
          png (str (io/file out-dir (str label ".wiring.png")))
          cmd (or mmdc-cmd "aa-exec -p chrome -- mmdc")]
      (mermaid/save-mermaid diagram mmd {:direction :LR})
      (try
        (let [parts (str/split cmd #"\s+")]
          (p/process (vec (concat parts ["-i" mmd "-o" png])) {:inherit true}))
        (catch Exception _ nil))
      {:mmd mmd :png png})))

(defn -main [& args]
  (let [{:keys [help unknown runs-dir out-dir mmdc-cmd]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      :else
      (let [runs-dir (or runs-dir "")
            out-dir (or out-dir runs-dir)]
        (when (str/blank? runs-dir)
          (println "--runs-dir is required")
          (System/exit 1))
        (.mkdirs (io/file out-dir))
        (doseq [file (edn-files runs-dir)]
          (let [run (edn/read-string (slurp file))
                label (base-name (.getName file))
                wiring (get-in run [:meta :wiring])
                diagram (load-diagram wiring)]
            (render-triptych! run out-dir label)
            (render-wiring! diagram out-dir label mmdc-cmd)
            (println "Rendered" label)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
