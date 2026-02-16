#!/usr/bin/env bb
(ns composition-survey
  "Survey pairwise mission composition across data/missions/*.edn."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ct.mission :as ct]))

(def ^:private default-missions-dir "data/missions")

(defn- usage []
  (str/join
   "\n"
   ["Survey mission composition properties."
    ""
    "Usage:"
    "  bb -cp src:resources scripts/composition_survey.clj [options]"
    ""
    "Options:"
    "  --missions-dir PATH   Mission directory (default data/missions)."
    "  --fail-on-invalid     Exit non-zero when any mission/composition is invalid."
    "  --help                Show this message."]))

(defn- parse-args [args]
  (loop [args args
         opts {:missions-dir default-missions-dir
               :fail-on-invalid false}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag) (assoc opts :help true)
          (= "--missions-dir" flag) (recur (rest more) (assoc opts :missions-dir (first more)))
          (= "--fail-on-invalid" flag) (recur more (assoc opts :fail-on-invalid true))
          :else (recur more (assoc opts :unknown (str "Unknown option: " flag)))))
      opts)))

(defn- mission-files [dir]
  (->> (file-seq (io/file dir))
       (filter #(.isFile ^java.io.File %))
       (filter #(str/ends-with? (.getName ^java.io.File %) ".edn"))
       (sort-by #(.getName ^java.io.File %))
       vec))

(defn- load-mission [^java.io.File file]
  (let [spec (edn/read-string (slurp file))
        diagram (ct/mission-diagram spec)
        validation (ct/validate diagram)
        summary (ct/summary diagram)]
    {:file (.getPath file)
     :id (:mission/id diagram)
     :diagram diagram
     :valid? (:all-valid validation)
     :failed-checks (:failed-checks summary)
     :summary summary}))

(defn- ordered-pairs [xs]
  (for [a xs
        b xs
        :when (not= (:id a) (:id b))]
    [a b]))

(defn- compose-report [[a b]]
  (let [a-diagram (:diagram a)
        b-diagram (:diagram b)
        matches (ct/composable? a-diagram b-diagram)
        serial (when (seq matches) (ct/compose-missions a-diagram b-diagram))
        serial-valid? (when serial (:all-valid (ct/validate serial)))
        parallel (ct/compose-parallel a-diagram b-diagram)
        parallel-valid? (when parallel (:all-valid (ct/validate parallel)))]
    {:from (:id a)
     :to (:id b)
     :match-count (count matches)
     :serial? (boolean serial)
     :serial-valid? (boolean serial-valid?)
     :parallel? (boolean parallel)
     :parallel-valid? (boolean parallel-valid?)}))

(defn- print-mission-table [missions]
  (println "Mission validation:")
  (doseq [{:keys [id file valid? failed-checks]} missions]
    (println (format "  %-28s valid=%-5s file=%s"
                     (name id) valid? file))
    (when (seq failed-checks)
      (println "    failed:" (str/join ", " (map name failed-checks))))))

(defn- print-summary [missions compositions]
  (let [mission-total (count missions)
        mission-valid (count (filter :valid? missions))
        serial-total (count (filter :serial? compositions))
        serial-valid (count (filter :serial-valid? compositions))
        parallel-total (count (filter :parallel? compositions))
        parallel-valid (count (filter :parallel-valid? compositions))]
    (println)
    (println "Composition survey summary:")
    (println (format "  Missions: %d total, %d valid, %d invalid"
                     mission-total mission-valid (- mission-total mission-valid)))
    (println (format "  Serial compositions: %d possible, %d valid, %d invalid"
                     serial-total serial-valid (- serial-total serial-valid)))
    (println (format "  Parallel compositions: %d possible, %d valid, %d invalid"
                     parallel-total parallel-valid (- parallel-total parallel-valid)))))

(defn -main [& args]
  (let [{:keys [help unknown missions-dir fail-on-invalid]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do
                (binding [*out* *err*]
                  (println unknown)
                  (println)
                  (println (usage)))
                (System/exit 2))
      :else
      (let [files (mission-files missions-dir)]
        (when (empty? files)
          (binding [*out* *err*]
            (println "No mission files found in" missions-dir))
          (System/exit 2))
        (let [missions (mapv load-mission files)
              compositions (mapv compose-report (ordered-pairs missions))
              mission-invalid (count (remove :valid? missions))
              serial-invalid (count (filter (fn [{:keys [serial? serial-valid?]}]
                                              (and serial? (not serial-valid?)))
                                            compositions))
              parallel-invalid (count (filter (fn [{:keys [parallel? parallel-valid?]}]
                                                (and parallel? (not parallel-valid?)))
                                              compositions))]
          (print-mission-table missions)
          (print-summary missions compositions)
          (when (and fail-on-invalid
                     (pos? (+ mission-invalid serial-invalid parallel-invalid)))
            (binding [*out* *err*]
              (println)
              (println "Invalid results found and --fail-on-invalid was set."))
            (System/exit 1)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))

