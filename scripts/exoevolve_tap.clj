(ns exoevolve-tap
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.mmca.exoevolve :as exoevolve]))

(defn- usage []
  (str/join
   "\n"
   ["Run exoevolve with tap> logging."
    ""
    "Usage:"
    "  bb -cp src:resources scripts/exoevolve_tap.clj [options]"
    ""
    "Options:"
    "  --tap-out PATH   File to append tap> events (default resources/exoevolve-tap.edn)"
    ""
    "All other args are passed through to futon5.mmca.exoevolve/-main."
    "Note: this wrapper forces --tap on."]))

(defn- parse-args [args]
  (loop [args args
         opts {:tap-out "resources/exoevolve-tap.edn"}
         passthrough []]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true) passthrough)

          (= "--tap-out" flag)
          (recur (rest more) (assoc opts :tap-out (first more)) passthrough)

          :else
          (recur more opts (conj passthrough flag))))
      {:opts opts
       :passthrough passthrough})))

(defn- append-tap! [path value]
  (with-open [w (io/writer path :append true)]
    (.write w (str (pr-str value) "\n"))))

(defn -main [& args]
  (let [{:keys [opts passthrough]} (parse-args args)
        {:keys [help tap-out]} opts]
    (if help
      (println (usage))
      (do
        (add-tap #(append-tap! tap-out %))
        (let [args' (if (some #{"--tap"} passthrough)
                      passthrough
                      (conj (vec passthrough) "--tap"))]
          (apply exoevolve/-main args'))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
