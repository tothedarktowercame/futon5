(ns nonstarter.sql
  "SQL helpers for JVM execution."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private query-opts
  {:builder-fn rs/as-unqualified-maps})

(defn datasource
  "Create a datasource for a SQLite DB path."
  [path]
  (jdbc/get-datasource {:dbtype "sqlite"
                        :dbname path}))

(defn execute!
  "Execute a SQL command."
  [ds sqlvec]
  (jdbc/execute! ds sqlvec))

(defn query
  "Execute a SQL query returning rows."
  [ds sqlvec]
  (jdbc/execute! ds sqlvec query-opts))

(defn execute-one!
  "Execute a SQL query returning a single row."
  [ds sqlvec]
  (jdbc/execute-one! ds sqlvec query-opts))
