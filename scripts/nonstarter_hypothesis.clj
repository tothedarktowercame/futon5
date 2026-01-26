(ns scripts.nonstarter-hypothesis
  "CLI for Nonstarter hypotheses."
  (:require [clojure.string :as str]
            [nonstarter.db :as db]
            [nonstarter.schema :as schema]))

(defn- usage []
  (str "usage: clj -M -m scripts.nonstarter-hypothesis <command> [opts]\n\n"
       "Commands:\n"
       "  register --db PATH --title TEXT --statement TEXT [--context TEXT] [--status STATUS] [--print-id]\n"
       "  update   --db PATH --id ID --status STATUS\n"
       "  list     --db PATH [--status STATUS]\n"))

(defn- parse-args [args]
  (loop [opts {:args []}
         remaining args]
    (if (empty? remaining)
      opts
      (case (first remaining)
        "--db" (recur (assoc opts :db (second remaining)) (nnext remaining))
        "--id" (recur (assoc opts :id (second remaining)) (nnext remaining))
        "--title" (recur (assoc opts :title (second remaining)) (nnext remaining))
        "--statement" (recur (assoc opts :statement (second remaining)) (nnext remaining))
        "--context" (recur (assoc opts :context (second remaining)) (nnext remaining))
        "--status" (recur (assoc opts :status (second remaining)) (nnext remaining))
        "--print-id" (recur (assoc opts :print-id true) (next remaining))
        (recur (update opts :args conj (first remaining)) (next remaining))))))

(defn- ensure-db [db]
  (when (str/blank? (str db))
    (binding [*out* *err*]
      (println "--db is required")
      (println (usage)))
    (System/exit 2)))

(defn -main [& args]
  (let [{:keys [args db id title statement context status print-id]} (parse-args args)
        cmd (first args)]
    (ensure-db db)
    (case cmd
      "register"
      (do
        (when (or (str/blank? (str title)) (str/blank? (str statement)))
          (binding [*out* *err*]
            (println "register requires --title and --statement")
            (println (usage)))
          (System/exit 2))
        (let [ds (schema/connect! db)
              record (db/create-hypothesis! ds {:title title
                                                :statement statement
                                                :context context
                                                :status status})]
          (if print-id
            (println (:id record))
            (prn record))))

      "update"
      (do
        (when (or (str/blank? (str id)) (str/blank? (str status)))
          (binding [*out* *err*]
            (println "update requires --id and --status")
            (println (usage)))
          (System/exit 2))
        (let [ds (schema/connect! db)
              record (db/update-hypothesis-status! ds id status)]
          (prn record)))

      "list"
      (let [ds (schema/connect! db)
            records (if (str/blank? (str status))
                      (db/list-hypotheses ds)
                      (db/list-hypotheses ds status))]
        (doseq [r records]
          (prn r)))

      (do
        (binding [*out* *err*]
          (println (usage)))
        (System/exit 2)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
