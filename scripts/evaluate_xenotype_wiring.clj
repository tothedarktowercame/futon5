(ns evaluate-xenotype-wiring
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.xenotype.interpret :as interpret]))

(defn- usage []
  (str/join
   "\n"
   ["Evaluate wiring diagrams against MMCA run EDN inputs."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/evaluate_xenotype_wiring.clj [options]"
    ""
    "Options:"
    "  --inputs PATH       Text file with one EDN run path per line."
    "  --diagram PATH      Wiring diagram EDN (or synth output with :candidates)."
    "  --index N           Candidate index when diagram has :candidates (default 0)."
    "  --components PATH   Component library EDN (default futon5/resources/xenotype-evaluation-components.edn)."
    "  --W N               Window length (default 10)."
    "  --S N               Window stride (default 10)."
    "  --out PATH          Output EDN lines file (default /tmp/xenotype-eval.edn)."
    "  --help              Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--inputs" flag)
          (recur (rest more) (assoc opts :inputs (first more)))

          (= "--diagram" flag)
          (recur (rest more) (assoc opts :diagram (first more)))

          (= "--index" flag)
          (recur (rest more) (assoc opts :index (parse-int (first more))))

          (= "--components" flag)
          (recur (rest more) (assoc opts :components (first more)))

          (= "--W" flag)
          (recur (rest more) (assoc opts :W (parse-int (first more))))

          (= "--S" flag)
          (recur (rest more) (assoc opts :S (parse-int (first more))))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- read-lines [path]
  (->> (slurp path)
       str/split-lines
       (map str/trim)
       (remove str/blank?)
       vec))

(defn- resolve-diagram [path idx]
  (let [data (edn/read-string (slurp path))
        idx (int (or idx 0))]
    (cond
      (and (map? data) (:nodes data)) data
      (and (map? data) (:diagram data)) (:diagram data)
      (and (map? data) (:candidates data))
      (let [cands (:candidates data)
            max-idx (max 0 (dec (count cands)))
            idx (max 0 (min idx max-idx))]
        (nth cands idx))
      :else data)))

(defn- append-entry! [path entry]
  (spit path (str (pr-str entry) "\n") :append true))

(defn -main [& args]
  (let [{:keys [help unknown inputs diagram index components W S out]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      (or (nil? inputs) (nil? diagram)) (do
                                          (println "Missing --inputs or --diagram")
                                          (println)
                                          (println (usage)))
      :else
      (let [out (or out "/tmp/xenotype-eval.edn")
            W (int (or W 10))
            S (int (or S 10))
            components (or components "futon5/resources/xenotype-evaluation-components.edn")
            diagram (resolve-diagram diagram index)
            paths (read-lines inputs)]
        (doseq [path paths]
          (let [run (edn/read-string (slurp path))
                {:keys [output node-values]} (interpret/evaluate-run-diagram
                                              diagram
                                              run
                                              {:W W :S S :components-path components})
                entry {:event :xenotype-eval
                       :path path
                       :score (:score output)
                       :pass? (:pass? output)
                       :output output
                       :node-values node-values}]
            (append-entry! out entry)))
        (println "Wrote" out)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
