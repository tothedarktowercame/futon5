(ns nonstarter.demo
  "Mission 1 demo trace: portal candidates -> policy recommendation -> ledger update."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [nonstarter.adapter :as adapter]
            [nonstarter.db :as db]
            [nonstarter.policy :as policy]
            [nonstarter.schema :as schema]
            [nonstarter.sidecar :as sidecar]))

(defn- usage []
  (str "usage: clj -M -m nonstarter.demo \\n"
       "  --db PATH \\n"
       "  --session-id ID \\n"
       "  --turn N \\n"
       "  --intent TEXT \\n"
       "  [--aif EDN] \\n"
       "  [--candidates PATH] \\n"
       "  [--out PATH]\n"))

(defn- read-edn-file [path]
  (with-open [r (io/reader path)]
    (edn/read {:eof nil} r)))

(defn- parse-int [value fallback]
  (try
    (Integer/parseInt (str value))
    (catch Throwable _ fallback)))

(defn- parse-args [args]
  (loop [opts {:turn 1}
         remaining args]
    (if (empty? remaining)
      opts
      (case (first remaining)
        "--db" (recur (assoc opts :db (second remaining)) (nnext remaining))
        "--session-id" (recur (assoc opts :session-id (second remaining)) (nnext remaining))
        "--turn" (recur (assoc opts :turn (parse-int (second remaining) (:turn opts)))
                        (nnext remaining))
        "--intent" (recur (assoc opts :intent (second remaining)) (nnext remaining))
        "--aif" (recur (assoc opts :aif (edn/read-string (second remaining))) (nnext remaining))
        "--candidates" (recur (assoc opts :candidates (second remaining)) (nnext remaining))
        "--out" (recur (assoc opts :out (second remaining)) (nnext remaining))
        (recur opts (next remaining))))))

(def ^:private sample-candidates
  [{:id "futon3a/P0"
    :title "Portal Query Layer"
    :namespace "futon3a"
    :score 0.55
    :blocks {:context "You want pattern-guided agent work"
             :if "Patterns live in futon3/library"
             :however "Agents need a query interface"
             :then "Portal provides Drawbridge-based eval"
             :because "Pattern retrieval must be fast"}}
   {:id "futon3a/P1"
    :title "Sidecar Audit Trail"
    :namespace "futon3a"
    :score 0.72
    :blocks {:context "You need machine-checkable evidence"
             :if "Agents run and produce artifacts"
             :however "Without structured logging, outcomes aren't traceable"
             :then "Sidecar appends PSR/PUR and evidence"
             :because "PSR/PUR is the instrument for validation"}
    :psr-example "Selected sidecar for audit logging"
    :pur-template "Used sidecar in {{session-id}}"}])

(defn- ensure-required [{:keys [db session-id intent]}]
  (when-not (and db session-id intent)
    (binding [*out* *err*]
      (println (usage)))
    (System/exit 2)))

(defn- write-output [path payload]
  (if path
    (spit path (with-out-str (prn payload)))
    (prn payload)))

(defn -main [& args]
  (let [{:keys [db session-id turn intent aif candidates out] :as opts}
        (parse-args args)
        _ (ensure-required opts)
        raw-candidates (if candidates
                         (read-edn-file candidates)
                         sample-candidates)
        ds (schema/connect! db)
        input (adapter/policy-input {:session-id session-id
                                     :turn turn
                                     :intent intent
                                     :aif aif
                                     :candidates raw-candidates})
        recommendation (policy/recommend input)
        _ (db/record-mana! ds {:session-id session-id
                               :turn turn
                               :delta 1
                               :reason :policy-simulate
                               :note "Mission 1 demo credit"})
        sidecar-event (sidecar/event-envelope
                       {:session-id session-id
                        :turn turn
                        :event-type :meme/proposal
                        :source {:system :nonstarter :method "demo"}
                        :payload {:proposal/id (str "prop-" session-id "-" turn)
                                  :proposal/kind :pattern
                                  :proposal/target-id (:chosen recommendation)
                                  :proposal/score (:confidence recommendation)
                                  :proposal/method "demo"
                                  :proposal/evidence {:intent intent}}})
        _ (db/record-sidecar-event! ds (assoc sidecar-event
                                              :event-type (:event/type sidecar-event)
                                              :session-id (:session/id sidecar-event)
                                              :turn (:turn sidecar-event)
                                              :payload sidecar-event))
        output {:policy/input input
                :policy/recommendation recommendation
                :mana (db/mana-summary ds session-id)
                :sidecar sidecar-event}]
    (write-output out output)))

