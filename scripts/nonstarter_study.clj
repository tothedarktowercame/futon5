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
       "           [--design EDN] [--metrics EDN] [--seeds EDN] [--status STATUS] [--results EDN] [--notes TEXT]\n"
       "           [--priority N] [--mana N] [--print-id] [--format edn|text]\n"
       "  update   --db PATH --id ID [--status STATUS] [--results EDN] [--notes TEXT] [--priority N] [--mana N] [--format edn|text]\n"
       "  list     --db PATH [--hypothesis-id ID] [--format edn|text]\n"))

(defn- parse-args [args]
  (loop [opts {:args []
               :format "edn"}
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
  (println (format "- %s [%s]" (or (:study-name record) "?") (or (:status record) "?")))
  (let [priority (or (:priority record) "n/a")
        mana (or (:mana_estimate record) (:mana-estimate record) "n/a")
        created (or (:created_at record) (:created-at record))]
    (println (format "  priority: %s" priority))
    (println (format "  mana: %s" mana))
    (when created
      (println (format "  created: %s" created))))
  (println (format "  hypothesis: %s"
                   (or (:hypothesis-title record)
                       (:hypothesis-id record)
                       "?")))
  (when-let [design (:design record)]
    (println (format "  design: %s" (pr-str design))))
  (when-let [metrics (:metrics record)]
    (println (format "  metrics: %s" (pr-str metrics))))
  (when-let [seeds (:seeds record)]
    (println (format "  seeds: %s" (pr-str seeds))))
  (when-let [results (:results record)]
    (println (format "  results: %s" (pr-str results))))
  (when-let [notes (:notes record)]
    (println (format "  notes: %s" notes)))
  (println))

(defn- format-mana [value]
  (if (number? value)
    (if (== value (Math/floor (double value)))
      (format "%.0f" value)
      (format "%.2f" value))
    "0"))

(defn- split-funded [records donated]
  (loop [funded []
         unfunded []
         remaining records
         running 0.0]
    (if (empty? remaining)
      {:funded funded
       :unfunded unfunded}
      (let [record (first remaining)
            mana (:mana_estimate record)]
        (if (number? mana)
          (let [next-total (+ running mana)]
            (if (<= next-total donated)
              (recur (conj funded record) unfunded (rest remaining) next-total)
              (recur funded (conj unfunded record) (rest remaining) running)))
          (recur funded (conj unfunded record) (rest remaining) running))))))

(defn -main [& args]
  (let [{:keys [args db id hypothesis-id study-name design metrics seeds status results notes priority mana-estimate print-id]
         :as opts} (parse-args args)
        output-format (:format opts)
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
                                             :notes notes
                                             :priority priority
                                             :mana-estimate mana-estimate})]
          (cond
            print-id (println (:id record))
            (= "text" output-format) (print-record-text record)
            :else (prn record))))

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
                                                      :notes notes
                                                      :priority priority
                                                      :mana-estimate mana-estimate})]
          (if (= "text" output-format)
            (print-record-text record)
            (prn record))))

      "list"
      (let [ds (schema/connect! db)
            records (if (str/blank? (str hypothesis-id))
                      (db/list-studies ds)
                      (db/list-studies ds hypothesis-id))]
        (if (= "text" output-format)
          (let [donated (or (:total-donated (db/pool-stats ds)) 0.0)
                {:keys [funded unfunded]} (split-funded records donated)
                titles (->> (db/list-hypotheses ds)
                            (map (juxt :id :title))
                            (into {}))
                annotate (fn [record]
                           (assoc record :hypothesis-title (get titles (:hypothesis-id record))))]
            (println "Order: priority desc (nulls last), created_at desc")
            (println)
            (doseq [r funded] (print-record-text (annotate r)))
            (println (format "-------------------- 🔮 %s --------------------" (format-mana donated)))
            (println)
            (doseq [r unfunded] (print-record-text (annotate r))))
          (doseq [r records] (prn r))))

      (do
        (binding [*out* *err*]
          (println (usage)))
        (System/exit 2)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
