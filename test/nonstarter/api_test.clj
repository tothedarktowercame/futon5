(ns nonstarter.api-test
  "Smoke test for the nonstarter HTTP API.
   Starts a real server against a temp DB, exercises bid/clear/get round trip."
  (:require [clojure.test :refer [deftest is testing]]
            [cheshire.core :as json]
            [nonstarter.api :as api]
            [clj-http.client :as http]))

(defn- with-temp-server [f]
  (let [tmp (java.io.File/createTempFile "nonstarter-test-" ".db")
        path (.getAbsolutePath tmp)
        ;; Use a random high port to avoid conflicts
        port (+ 49152 (rand-int 10000))
        srv (api/start! {:port port :db path})]
    (try
      (f port)
      (finally
        (api/stop! srv)
        (.delete tmp)))))

(deftest health-check-server-starts
  (testing "Server starts and responds to GET /api/pool"
    (with-temp-server
      (fn [port]
        (let [resp (http/get (str "http://localhost:" port "/api/pool")
                             {:as :json})
              body (:body resp)]
          (is (= 200 (:status resp)))
          (is (= 0.0 (:balance body))))))))

(deftest bid-clear-round-trip
  (testing "POST bid → GET heartbeat → POST clear → GET heartbeat round trip"
    (with-temp-server
      (fn [port]
        (let [base (str "http://localhost:" port)
              week-id "2026-W09"
              bid-payload {:week-id week-id
                           :bids [{:action "work-on" :mission "M-foo" :effort "hard"}
                                  {:action "review" :mission nil :effort "easy"}]
                           :mode-prediction "BUILD"}]
          ;; 1. POST bid
          (let [resp (http/post (str base "/api/heartbeat/bid")
                                {:body (json/generate-string bid-payload)
                                 :content-type :json
                                 :as :json})]
            (is (= 200 (:status resp)))
            (is (true? (get-in resp [:body :ok]))))

          ;; 2. GET the heartbeat back
          (let [resp (http/get (str base "/api/heartbeat")
                               {:query-params {"week-id" week-id}
                                :as :json})
                hb (:body resp)]
            (is (= 200 (:status resp)))
            (is (= week-id (:week_id hb)))
            (is (= 2 (count (:bids hb)))))

          ;; 3. POST clear
          (let [clear-payload {:week-id week-id
                               :clears [{:action "work-on" :mission "M-foo"
                                         :effort "hard" :outcome "complete"}]
                               :mode-observed "BUILD"}
                resp (http/post (str base "/api/heartbeat/clear")
                                {:body (json/generate-string clear-payload)
                                 :content-type :json
                                 :as :json})]
            (is (= 200 (:status resp)))
            (is (true? (get-in resp [:body :ok]))))

          ;; 4. GET again — now has both bids and clears
          (let [resp (http/get (str base "/api/heartbeat")
                               {:query-params {"week-id" week-id}
                                :as :json})
                hb (:body resp)]
            (is (= 200 (:status resp)))
            (is (= 2 (count (:bids hb))))
            (is (= 1 (count (:clears hb))))
            (is (= "BUILD" (str (:mode_observed hb)))))

          ;; 5. List heartbeats
          (let [resp (http/get (str base "/api/heartbeats")
                               {:query-params {"n" "5"}
                                :as :json})]
            (is (= 200 (:status resp)))
            (is (= 1 (count (:body resp))))))))))

(deftest nonexistent-heartbeat-returns-404
  (testing "GET heartbeat for missing week returns 404"
    (with-temp-server
      (fn [port]
        (let [resp (http/get (str "http://localhost:" port "/api/heartbeat")
                             {:query-params {"week-id" "2099-W01"}
                              :throw-exceptions false
                              :as :json})]
          (is (= 404 (:status resp))))))))
