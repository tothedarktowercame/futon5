(ns nonstarter.api
  "HTTP API for nonstarter — portfolio heartbeats and mana queries.

   Endpoints:
     GET  /api/heartbeat?week-id=YYYY-Www     — get heartbeat for a week
     GET  /api/heartbeats?n=10                 — list recent heartbeats
     POST /api/heartbeat/bid                   — record intended actions
     POST /api/heartbeat/clear                 — record actual actions
     GET  /api/mana?session-id=...             — mana summary
     GET  /api/pool                            — pool stats

   Start: (start! {:port 7071 :db \"path/to/nonstarter.db\"})"
  (:require [cheshire.core :as json]
            [nonstarter.db :as db]
            [nonstarter.schema :as schema]
            [ring.adapter.jetty :as jetty])
  (:import [java.time LocalDate]
           [java.time.temporal WeekFields]
           [java.util Locale]))

(defn- current-week-id []
  (let [now (LocalDate/now)
        wf (WeekFields/of Locale/US)
        week (.get now (.weekOfWeekBasedYear wf))
        year (.get now (.weekBasedYear wf))]
    (format "%d-W%02d" year week)))

(defn- json-response [status body]
  {:status status
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string body)})

(defn- parse-body [request]
  (when-let [body (:body request)]
    (json/parse-string (slurp body) true)))

(defn- query-param [request k]
  (get (:query-params request) k))

(defn- wrap-query-params [handler]
  (fn [request]
    (let [qs (:query-string request)
          params (when qs
                   (into {}
                     (for [pair (.split qs "&")
                           :let [[k v] (.split pair "=" 2)]]
                       [k (java.net.URLDecoder/decode (or v "") "UTF-8")])))]
      (handler (assoc request :query-params params)))))

(defn make-handler [ds]
  (fn [{:keys [request-method uri] :as request}]
    (try
      (case [request-method uri]
        ;; GET heartbeat for a specific week
        [:get "/api/heartbeat"]
        (let [week-id (or (query-param request "week-id") (current-week-id))]
          (if-let [hb (db/get-heartbeat ds week-id)]
            (json-response 200 hb)
            (json-response 404 {:error "no heartbeat" :week-id week-id})))

        ;; GET recent heartbeats
        [:get "/api/heartbeats"]
        (let [n (parse-long (or (query-param request "n") "10"))]
          (json-response 200 (db/list-heartbeats ds n)))

        ;; POST bid
        [:post "/api/heartbeat/bid"]
        (let [body (parse-body request)]
          (db/upsert-heartbeat-bids! ds body)
          (json-response 200 {:ok true :week-id (:week-id body)}))

        ;; POST clear
        [:post "/api/heartbeat/clear"]
        (let [body (parse-body request)]
          (db/upsert-heartbeat-clears! ds body)
          (json-response 200 {:ok true :week-id (:week-id body)}))

        ;; GET mana summary
        [:get "/api/mana"]
        (if-let [sid (query-param request "session-id")]
          (json-response 200 (db/mana-summary ds sid))
          (json-response 400 {:error "session-id required"}))

        ;; GET pool stats
        [:get "/api/pool"]
        (json-response 200 (db/pool-stats ds))

        ;; Fallback
        (json-response 404 {:error "not found" :uri uri}))
      (catch Exception e
        (json-response 500 {:error (.getMessage e)})))))

(defn start!
  "Start the nonstarter HTTP API server.
   opts: {:port 7071 :db \"path/to/nonstarter.db\"}"
  [{:keys [port db] :or {port 7071 db "data/nonstarter.db"}}]
  (let [ds (schema/connect! db)
        handler (wrap-query-params (make-handler ds))]
    (println (str "[nonstarter] API on http://localhost:" port))
    (println (str "[nonstarter] DB: " db))
    {:server (jetty/run-jetty handler {:port port :join? false})
     :ds ds}))

(defn stop! [{:keys [server]}]
  (when server (.stop server)))

(defn -main [& args]
  (let [port (if (seq args) (parse-long (first args)) 7071)
        db (or (System/getenv "NONSTARTER_DB") "data/nonstarter.db")]
    (start! {:port port :db db})
    @(promise)))  ;; block forever
