(ns futon5.llm.relay
  "Lightweight CLI inspired by aob-chatgpt.el for relaying prompts to an LLM.
   Reads system prompts from files, assembles a Chat Completions payload, and
   (optionally) POSTs it to the OpenAI API."
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.time ZonedDateTime)
           (java.time.format DateTimeFormatter)))

(def default-model "gpt-4o-mini")
(def api-url "https://api.openai.com/v1/chat/completions")
(def log-env-var "FUTON5_LLM_LOG")

(def stanza-delimiter "\n\n---SYSTEM-STANZA---\n\n")

(defn slurp-when [path]
  (when path
    (when (.exists (io/file path))
      (slurp path))))

(defn split-prompts [raw]
  (->> (str/split (or raw "") #",")
       (map str/trim)
       (remove str/blank?)
       vec))

(defn load-prompts!
  [paths]
  (let [missing (remove #(-> (io/file %) .exists) paths)]
    (when (seq missing)
      (throw (ex-info (str "Prompt file(s) not found: " (str/join ", " missing))
                      {:missing missing})))
    (->> paths
         (map slurp-when)
         (remove nil?)
         vec)))

(defn build-messages
  [{:keys [system user]}]
  (cond-> []
    (not (str/blank? system)) (conj {:role "system" :content system})
    (not (str/blank? user)) (conj {:role "user" :content user})))

(defn load-messages!
  [path]
  (when-not (.exists (io/file path))
    (throw (ex-info (str "Messages file not found: " path) {:path path})))
  (let [data (json/parse-string (slurp path) true)]
    (when-not (sequential? data)
      (throw (ex-info "Messages JSON must be an array of role/content maps" {:path path})))
    (vec data)))

(defn chat-request
  [{:keys [model system user]}]
  {:model (or model default-model)
   :messages (build-messages {:system system :user user})})

(defn- read-openai-key-file []
  (let [path (io/file (System/getProperty "user.home") ".openai-key")]
    (when (.exists path)
      (-> (slurp path) str/trim not-empty))))

(defn- openai-key-info []
  (let [file-key (read-openai-key-file)]
    (when file-key
      {:key file-key :source "file"})))

(defn- now-iso []
  (.format DateTimeFormatter/ISO_OFFSET_DATE_TIME (ZonedDateTime/now)))

(defn- key-fingerprint [key]
  (when (and key (not (str/blank? key)))
    (let [len (count key)
          tail (subs key (max 0 (- len 6)))]
      (format "len=%d tail=%s" len tail))))

(defn- log-event!
  [event]
  (let [path (some-> (System/getenv log-env-var) str/trim not-empty)]
    (when path
      (spit path (str (json/encode event) "\n") :append true))))

(defn call-openai!
  [payload]
  (let [{:keys [key source]} (or (openai-key-info)
                                 (throw (ex-info "~/.openai-key missing or unreadable" {})))
        fingerprint (key-fingerprint key)]
    (try
      (let [resp (http/post api-url
                            {:headers {"Authorization" (str "Bearer " key)}
                             :content-type :json
                             :accept :json
                             :as :json
                             :body (json/encode payload)})]
        (log-event!
         {:ts (now-iso)
          :event "openai.response"
          :model (:model payload)
          :status (:status resp)
          :request_id (get-in resp [:headers "x-request-id"])
          :usage (get-in resp [:body "usage"])
          :key_source source
          :key_fingerprint fingerprint})
        resp)
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (log-event!
           {:ts (now-iso)
            :event "openai.error"
            :model (:model payload)
            :status (:status data)
            :request_id (get-in data [:headers "x-request-id"])
            :error (or (get data :reason-phrase) (.getMessage e))
            :key_source source
            :key_fingerprint fingerprint}))
        (throw e)))))

(defn print-response
  [resp]
  (if-let [choices (get-in resp [:body "choices"])]
    (doseq [choice choices]
      (when-let [text (get-in choice ["message" "content"])]
        (println text)))
    (println (json/encode (:body resp)))))

(defn usage []
  (println "Usage: clj -M -m futon5.llm.relay --prompt PROMPT[,PROMPT...] --input FILE [--model MODEL] [--dry-run]")
  (println "       clj -M -m futon5.llm.relay --messages FILE [--model MODEL] [--dry-run]")
  (println "Reads PROMPT (system) + FILE (user) or a JSON messages file, then either prints")
  (println "the JSON payload (--dry-run) or posts it to the OpenAI chat/completions API."))

(defn parse-args
  [args]
  (loop [opts {} remaining args]
    (if (empty? remaining)
      opts
      (let [[flag & more] remaining]
        (case flag
          "--prompt" (recur (update opts :prompts (fnil into []) (split-prompts (first more)))
                            (rest more))
          "--input" (recur (assoc opts :input (first more)) (rest more))
          "--model" (recur (assoc opts :model (first more)) (rest more))
          "--messages" (recur (assoc opts :messages (first more)) (rest more))
          "--dry-run" (recur (assoc opts :dry-run true) more)
          "--help" (assoc opts :help true)
          (recur (update opts :unknown (fnil conj []) flag) more))))))

(defn -main
  [& args]
  (let [{:keys [prompts input model dry-run unknown messages]} (parse-args args)]
    (cond
      (:unknown unknown) (do (usage) (System/exit 1))
      (and (nil? messages) (empty? prompts))
      (do (println "Either --prompt or --messages is required") (usage) (System/exit 1))
      :else
      (let [payload (if messages
                      {:model (or model default-model)
                       :messages (load-messages! messages)}
                      (let [system (->> (load-prompts! prompts)
                                        (str/join stanza-delimiter))
                            user (or (slurp-when input)
                                     (slurp *in*))]
                        (chat-request {:model model
                                       :system system
                                       :user user})))]
        (if dry-run
          (println (json/encode payload {:pretty true}))
          (print-response (call-openai! payload)))))))
