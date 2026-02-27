(ns nonstarter.paths
  "Default filesystem locations for nonstarter persistence."
  (:require [clojure.java.io :as io]))

(defn default-storage-root
  "Canonical storage root for futon repos."
  []
  (str (System/getProperty "user.home") "/code/storage"))

(defn default-db-path
  "Canonical nonstarter DB path for the given repo key (e.g. \"futon5\")."
  [repo-key]
  (-> (io/file (default-storage-root) repo-key "nonstarter.db")
      .getPath))
