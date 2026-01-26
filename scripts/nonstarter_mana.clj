(ns scripts.nonstarter-mana
  "CLI for Nonstarter mana donations."
  (:require [clojure.string :as str]
            [nonstarter.db :as db]
            [nonstarter.schema :as schema]))

(defn- usage []
  (str "usage: bb -m scripts.nonstarter-mana <command> [opts]\n\n"
       "Commands:\n"
       "  donate --db PATH --amount N [--donor TEXT] [--note TEXT] [--format edn|text]\n"
       "  pool   --db PATH [--format edn|text]\n"))

(defn- parse-args [args]
  (loop [opts {:args []
               :format "edn"}
         remaining args]
    (if (empty? remaining)
      opts
      (case (first remaining)
        "--db" (recur (assoc opts :db (second remaining)) (nnext remaining))
        "--amount" (recur (assoc opts :amount (Double/parseDouble (second remaining))) (nnext remaining))
        "--donor" (recur (assoc opts :donor (second remaining)) (nnext remaining))
        "--note" (recur (assoc opts :note (second remaining)) (nnext remaining))
        "--format" (recur (assoc opts :format (second remaining)) (nnext remaining))
        (recur (update opts :args conj (first remaining)) (next remaining))))))

(defn- ensure-db [db]
  (when (str/blank? (str db))
    (binding [*out* *err*]
      (println "--db is required")
      (println (usage)))
    (System/exit 2)))

(defn- format-mana [value]
  (if (number? value)
    (if (== value (Math/floor (double value)))
      (format "%.0f" (double value))
      (format "%.2f" (double value)))
    "0"))

(defn -main [& args]
  (let [{:keys [args db amount donor note] :as opts} (parse-args args)
        output-format (:format opts)
        cmd (first args)]
    (ensure-db db)
    (case cmd
      "donate"
      (do
        (when (not (number? amount))
          (binding [*out* *err*]
            (println "donate requires --amount")
            (println (usage)))
          (System/exit 2))
        (let [ds (schema/connect! db)
              record (db/donate! ds amount :donor donor :note note)]
          (if (= "text" output-format)
            (do
              (println (format "donation: 🔮 %s (%s)" (format-mana amount) (or donor "anonymous")))
              (when (and note (not (str/blank? note)))
                (println (format "note: %s" note))))
            (prn record))))

      "pool"
      (let [ds (schema/connect! db)
            stats (db/pool-stats ds)]
        (if (= "text" output-format)
          (println (format "pool: 🔮 %s (donated %s, funded %s)"
                           (format-mana (:balance stats))
                           (format-mana (:total-donated stats))
                           (format-mana (:total-funded stats))))
          (prn stats)))

      (do
        (binding [*out* *err*]
          (println (usage)))
        (System/exit 2)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
