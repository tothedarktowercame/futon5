(ns nonstarter.config
  "Shared config loader for nonstarter CLI tools."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn load-config
  "Load an EDN config file. Returns nil if path is blank."
  [path]
  (when (and path (not (str/blank? (str path))))
    (let [file (io/file path)]
      (when-not (.exists file)
        (throw (ex-info (str "Config not found: " path) {:path path})))
      (edn/read-string (slurp file)))))

(defn apply-config
  "Merge config defaults into opts (CLI opts override defaults)."
  [opts]
  (if-let [cfg (load-config (:config opts))]
    (let [defaults (or (:defaults cfg) {})
          merge-defaults (fn [m]
                           (reduce-kv (fn [acc k v]
                                        (if (nil? (get acc k))
                                          (assoc acc k v)
                                          acc))
                                      m
                                      defaults))
          opts* (merge-defaults opts)
          db* (:db cfg)]
      (if (and db* (str/blank? (str (:db opts*))))
        (assoc opts* :db db*)
        opts*))
    opts))
