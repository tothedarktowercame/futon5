(ns scirepro.render
  (:gen-class)
  (:require [clojure.java.io :as io]
            [scicloj.clay.v2.api :as clay]
            [scirepro.report :as report]))

(def clay-target "out/notebooks.nb01_metaca_core.html")
(def report-target "out/nb01_metaca_core.html")

(defn render! []
  (clay/make! {:source-path "notebooks/nb01_metaca_core.clj"
               :base-source-path "."
               :base-target-path "out"
               :format [:html]
               :show false})
  (report/write-html! report-target)
  {:clay clay-target
   :report report-target
   :clay-bytes (.length (io/file clay-target))
   :report-bytes (.length (io/file report-target))})

(defn -main [& _]
  (let [{:keys [clay report clay-bytes report-bytes]} (render!)]
    (println (format "CLAY RENDER OK %s bytes=%d; report=%s bytes=%d"
                     clay clay-bytes report report-bytes))
    (System/exit 0)))
