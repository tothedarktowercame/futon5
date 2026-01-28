(ns scripts.nonstarter-mana
  "CLI for Nonstarter mana donations."
  (:require [clojure.string :as str]
            [nonstarter.config :as config]
            [nonstarter.db :as db]
            [nonstarter.schema :as schema]))

(defn- usage []
  (str "usage: bb -m scripts.nonstarter-mana <command> [opts]\n\n"
       "Commands:\n"
       "  donate  --db PATH --amount N [--donor TEXT] [--note TEXT] [--format edn|text]\n"
       "  sospeso --db PATH --action TEXT --confidence P --cost C [--session ID] [--format edn|text]\n"
       "          Gives sospeso (1-p)*C to pool (pure dana)\n"
       "  pool    --db PATH [--format edn|text]\n"))

(defn- parse-args [args]
  (loop [opts {:args []
               :format nil}
         remaining args]
    (if (empty? remaining)
      opts
      (case (first remaining)
        "--db" (recur (assoc opts :db (second remaining)) (nnext remaining))
        "--amount" (recur (assoc opts :amount (Double/parseDouble (second remaining))) (nnext remaining))
        "--donor" (recur (assoc opts :donor (second remaining)) (nnext remaining))
        "--note" (recur (assoc opts :note (second remaining)) (nnext remaining))
        "--config" (recur (assoc opts :config (second remaining)) (nnext remaining))
        "--format" (recur (assoc opts :format (second remaining)) (nnext remaining))
        "--action" (recur (assoc opts :action (second remaining)) (nnext remaining))
        "--confidence" (recur (assoc opts :confidence (Double/parseDouble (second remaining))) (nnext remaining))
        "--cost" (recur (assoc opts :cost (Double/parseDouble (second remaining))) (nnext remaining))
        "--session" (recur (assoc opts :session (second remaining)) (nnext remaining))
        (recur (update opts :args conj (first remaining)) (next remaining))))))

(defn- ensure-db [db]
  (when (str/blank? (str db))
    (binding [*out* *err*]
      (println "--db is required (or provide --config with :db)")
      (println (usage)))
    (System/exit 2)))

(defn- format-mana [value]
  (if (number? value)
    (if (== value (Math/floor (double value)))
      (format "%.0f" (double value))
      (format "%.2f" (double value)))
    "0"))

(defn -main [& args]
  (let [opts (config/apply-config (parse-args args))
        {:keys [args db amount donor note]} opts
        output-format (or (:format opts) "edn")
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

      "sospeso"
      (let [{:keys [action confidence cost session note]} (parse-args args)]
        (when (or (str/blank? action) (nil? confidence) (nil? cost))
          (binding [*out* *err*]
            (println "sospeso requires --action, --confidence, and --cost")
            (println (usage)))
          (System/exit 2))
        (when-not (#{0.3 0.6 0.8 0.95} confidence)
          (binding [*out* *err*]
            (println "confidence must be one of: 0.3, 0.6, 0.8, 0.95"))
          (System/exit 2))
        (let [gift (* (- 1.0 confidence) cost)
              ds (schema/connect! db)
              sospeso-note (str "sospeso [p=" confidence ", C=" cost "]: " action)
              record (db/donate! ds gift :donor "sospeso" :note sospeso-note)]
          ;; Also log as mana_event if session provided
          (when session
            (db/record-mana! ds {:session-id session
                                 :turn nil
                                 :delta gift
                                 :reason :sospeso
                                 :note sospeso-note}))
          (if (= "text" format)
            (do
              (println (format "sospeso: 🔮 %s (p=%s, C=%s)" (format-mana gift) confidence cost))
              (println (format "action: %s" action)))
            (prn (assoc record :gift gift :confidence confidence :cost cost)))))

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
