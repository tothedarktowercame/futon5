(ns scripts.nonstarter-hypothesis
  "CLI for Nonstarter hypotheses."
  (:require [clojure.string :as str]
            [nonstarter.db :as db]
            [nonstarter.schema :as schema]))

(defn- usage []
  (str "usage: clj -M -m scripts.nonstarter-hypothesis <command> [opts]\n\n"
       "Commands:\n"
       "  register --db PATH --title TEXT --statement TEXT [--context TEXT] [--status STATUS]\n"
       "           [--priority N] [--mana N] [--print-id] [--format edn|text]\n"
       "  update   --db PATH --id ID [--status STATUS] [--priority N] [--mana N] [--format edn|text]\n"
       "  list     --db PATH [--status STATUS] [--format edn|text]\n"))

(defn- parse-args [args]
  (loop [opts {:args []
               :format "edn"}
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
        "--priority" (recur (assoc opts :priority (Long/parseLong (second remaining))) (nnext remaining))
        "--mana" (recur (assoc opts :mana-estimate (Double/parseDouble (second remaining))) (nnext remaining))
        "--format" (recur (assoc opts :format (second remaining)) (nnext remaining))
        "--print-id" (recur (assoc opts :print-id true) (next remaining))
        (recur (update opts :args conj (first remaining)) (next remaining))))))

(defn- ensure-db [db]
  (when (str/blank? (str db))
    (binding [*out* *err*]
      (println "--db is required")
      (println (usage)))
    (System/exit 2)))

(defn- print-record-text [record]
  (println (format "- %s [%s]" (or (:title record) "?") (or (:status record) "?")))
  (let [priority (or (:priority record) "n/a")
        mana (or (:mana_estimate record) (:mana-estimate record) "n/a")
        created (or (:created_at record) (:created-at record))]
    (println (format "  priority: %s" priority))
    (println (format "  mana: %s" mana))
    (when created
      (println (format "  created: %s" created))))
  (when-let [statement (:statement record)]
    (println (format "  statement: %s" statement)))
  (when-let [context (:context record)]
    (println (format "  context: %s" context)))
  (println))

(defn -main [& args]
  (let [{:keys [args db id title statement context status priority mana-estimate print-id format]} (parse-args args)
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
                                                :status status
                                                :priority priority
                                                :mana-estimate mana-estimate})]
          (cond
            print-id (println (:id record))
            (= "text" format) (print-record-text record)
            :else (prn record))))

      "update"
      (do
        (when (str/blank? (str id))
          (binding [*out* *err*]
            (println "update requires --id")
            (println (usage)))
          (System/exit 2))
        (let [ds (schema/connect! db)
              record (db/update-hypothesis! ds id {:status status
                                                   :priority priority
                                                   :mana-estimate mana-estimate})]
          (if (= "text" format)
            (print-record-text record)
            (prn record))))

      "list"
      (let [ds (schema/connect! db)
            records (if (str/blank? (str status))
                      (db/list-hypotheses ds)
                      (db/list-hypotheses ds status))]
        (if (= "text" format)
          (do
            (println "Order: priority desc (nulls last), created_at desc")
            (println)
            (doseq [r records] (print-record-text r)))
          (doseq [r records] (prn r))))

      (do
        (binding [*out* *err*]
          (println (usage)))
        (System/exit 2)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
