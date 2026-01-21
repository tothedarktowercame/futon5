(ns futon5.hexagram.logging
  "PSR/PUR logging helpers for hexagram evaluation."
  (:require [clojure.java.io :as io]))

(defn- now []
  (.toString (java.time.Instant/now)))

(defn psr-entry
  [{:keys [sigil exotype-params predicted-hexagram reason generation seed]}]
  {:event :psr
   :timestamp (now)
   :sigil sigil
   :exotype-params exotype-params
   :predicted-hexagram predicted-hexagram
   :reason reason
   :generation generation
   :seed seed})

(defn pur-entry
  [{:keys [sigil actual-dynamics actual-hexagram match? generation seed]}]
  {:event :pur
   :timestamp (now)
   :sigil sigil
   :actual-dynamics actual-dynamics
   :actual-hexagram actual-hexagram
   :match? (boolean match?)
   :generation generation
   :seed seed})

(defn append-entry!
  [path entry]
  (when path
    (let [file (io/file path)]
      (when-let [parent (.getParentFile file)]
        (.mkdirs parent))
      (spit file (str (pr-str entry) "\n") :append true))))
