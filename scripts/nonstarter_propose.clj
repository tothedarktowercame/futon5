(ns scripts.nonstarter-propose
  "Create a nonstarter proposal with optional mana estimation."
  (:require [clojure.edn :as edn]
            [nonstarter.db :as db]
            [nonstarter.estimate :as estimate]
            [nonstarter.schema :as schema]))

(defn- usage []
  (str "usage: clj -M -m scripts.nonstarter-propose \\n"
       "  --db PATH --title TEXT [--ask N] [--estimate EDN] \\n"
       "  [--description TEXT] [--sigil TEXT] [--proposer NAME]\n\n"
       "Examples:\n"
       "  clj -M -m scripts.nonstarter-propose --db $HOME/code/storage/futon5/nonstarter.db \\\n"
       "    --title \"Prototype 5 of futon2\" --ask 500\n"
       "  clj -M -m scripts.nonstarter-propose --db $HOME/code/storage/futon5/nonstarter.db \\\n"
       "    --title \"Prototype 5 of futon2\" --estimate '{:size :large :complexity :high :risk :med}'\n"))

(defn- parse-int [value fallback]
  (try
    (Long/parseLong (str value))
    (catch Throwable _ fallback)))

(defn- parse-args [args]
  (loop [opts {}
         remaining args]
    (if (empty? remaining)
      opts
      (case (first remaining)
        "--db" (recur (assoc opts :db (second remaining)) (nnext remaining))
        "--title" (recur (assoc opts :title (second remaining)) (nnext remaining))
        "--ask" (recur (assoc opts :ask (parse-int (second remaining) nil)) (nnext remaining))
        "--estimate" (recur (assoc opts :estimate (edn/read-string (second remaining))) (nnext remaining))
        "--description" (recur (assoc opts :description (second remaining)) (nnext remaining))
        "--sigil" (recur (assoc opts :sigil (second remaining)) (nnext remaining))
        "--proposer" (recur (assoc opts :proposer (second remaining)) (nnext remaining))
        (recur opts (next remaining))))))

(defn- ensure-required [{:keys [db title ask estimate]}]
  (when-not (and db title (or ask estimate))
    (binding [*out* *err*]
      (println (usage)))
    (System/exit 2)))

(defn -main [& args]
  (let [{:keys [db title ask estimate description sigil proposer]} (parse-args args)
        _ (ensure-required {:db db :title title :ask ask :estimate estimate})
        estimate-result (when estimate (estimate/estimate-cost estimate))
        ask* (or ask (:mana estimate-result))
        ds (schema/connect! db)
        record (db/propose! ds {:title title
                                 :ask ask*
                                 :description description
                                 :sigil sigil
                                 :proposer proposer})]
    (if estimate-result
      (prn {:proposal record
            :estimate estimate-result})
      (prn {:proposal record}))))
