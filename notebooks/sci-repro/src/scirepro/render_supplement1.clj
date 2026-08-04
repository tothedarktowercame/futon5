(ns scirepro.render-supplement1
  (:gen-class)
  (:require [clojure.java.io :as io]
            [scicloj.clay.v2.api :as clay]))

(def target "out/notebooks.mmca_supplement1.html")

(defn render! []
  (clay/make! {:source-path ["notebooks/mmca_supplement1.clj"]
               :base-source-path "."
               :base-target-path "out"
               :format [:html]
               :show false})
  {:path target
   :bytes (.length (io/file target))})

(defn -main [& _]
  (println "CLAY RENDER OK" (pr-str (render!)))
  (System/exit 0))
