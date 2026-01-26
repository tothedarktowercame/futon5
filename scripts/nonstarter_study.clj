(ns scripts.nonstarter-study
  "CLI for Nonstarter study preregistrations." 
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [nonstarter.db :as db]
            [nonstarter.schema :as schema]))

(defn- usage []
  (str "usage: clj -M -m scripts.nonstarter-study <command> [opts]\n\n"
       "Commands:\n"
       "  register --db PATH --hypothesis-id ID --study-name TEXT\n"
       "           [--design EDN] [--metrics EDN] [--seeds EDN] [--status STATUS] [--results EDN] [--notes TEXT] [--print-id]\n"
       "  update   --db PATH --id ID [--status STATUS] [--results EDN] [--notes TEXT]\n"
       "  list     --db PATH [--hypothesis-id ID]\n"))

(defn- parse-args [args]
  (loop [opts {:args []}
         remaining args]
    (if (empty? remaining)
      opts
      (case (first remaining)
        "--db" (recur (assoc opts :db (second remaining)) (nnext remaining))
        "--id" (recur (assoc opts :id (second remaining)) (nnext remaining))
        "--hypothesis-id" (recur (assoc opts :hypothesis-id (second remaining)) (nnext remaining))
        "--study-name" (recur (assoc opts :study-name (second remaining)) (nnext remaining))
        "--design" (recur (assoc opts :design (edn/read-string (second remaining))) (nnext remaining))
        "--metrics" (recur (assoc opts :metrics (edn/read-string (second remaining))) (nnext remaining))
        "--seeds" (recur (assoc opts :seeds (edn/read-string (second remaining))) (nnext remaining))
        "--status" (recur (assoc opts :status (second remaining)) (nnext remaining))
        "--results" (recur (assoc opts :results (edn/read-string (second remaining))) (nnext remaining))
        "--notes" (recur (assoc opts :notes (second remaining)) (nnext remaining))
        "--print-id" (recur (assoc opts :print-id true) (next remaining))
        (recur (update opts :args conj (first remaining)) (next remaining))))))

(defn- ensure-db [db]
  (when (str/blank? (str db))
    (binding [*out* *err*]
      (println "--db is required")
      (println (usage)))
    (System/exit 2)))

(defn -main [& args]
  (let [{:keys [args db id hypothesis-id study-name design metrics seeds status results notes print-id]} (parse-args args)
        cmd (first args)]
    (ensure-db db)
    (case cmd
      "register"
      (do
        (when (or (str/blank? (str hypothesis-id)) (str/blank? (str study-name)))
          (binding [*out* *err*]
            (println "register requires --hypothesis-id and --study-name")
            (println (usage)))
          (System/exit 2))
        (let [ds (schema/connect! db)
              record (db/register-study! ds {:hypothesis-id hypothesis-id
                                             :study-name study-name
                                             :design design
                                             :metrics metrics
                                             :seeds seeds
                                             :status status
                                             :results results
                                             :notes notes})]
          (if print-id
            (println (:id record))
            (prn record))))

      "update"
      (do
        (when (str/blank? (str id))
          (binding [*out* *err*]
            (println "update requires --id")
            (println (usage)))
          (System/exit 2))
        (let [ds (schema/connect! db)
              record (db/update-study-results! ds id {:results results
                                                      :status status
                                                      :notes notes})]
          (prn record)))

      "list"
      (let [ds (schema/connect! db)
            records (if (str/blank? (str hypothesis-id))
                      (db/list-studies ds)
                      (db/list-studies ds hypothesis-id))]
        (doseq [r records]
          (prn r)))

      (do
        (binding [*out* *err*]
          (println (usage)))
        (System/exit 2)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))

