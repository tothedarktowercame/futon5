(ns nonstarter.sql
  "SQL helpers for Babashka execution."
  (:require [clojure.walk :as walk]
            [pod.babashka.go-sqlite3 :as sqlite]))

(defn datasource
  "Return the DB path; babashka sqlite uses file paths directly."
  [path]
  path)

(defn- normalize-row [row]
  (if (map? row)
    (walk/keywordize-keys row)
    row))

(defn execute!
  "Execute a SQL command."
  [db sqlvec]
  (sqlite/execute! db sqlvec))

(defn query
  "Execute a SQL query returning rows."
  [db sqlvec]
  (mapv normalize-row (sqlite/query db sqlvec)))

(defn execute-one!
  "Execute a SQL query returning a single row."
  [db sqlvec]
  (first (query db sqlvec)))
