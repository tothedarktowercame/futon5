(ns scirepro.render
  (:gen-class)
  (:require [clojure.java.io :as io]
            [scicloj.clay.v2.api :as clay]
            [scirepro.report :as report]))

(def clay-targets
  ["out/notebooks.nb01_metaca_core.html"
   "out/notebooks.nb02_blending.html"])

(def report-targets
  {:nb01 "out/nb01_metaca_core.html"
   :nb02 "out/nb02_blending.html"})

(defn render! []
  (clay/make! {:source-path ["notebooks/nb01_metaca_core.clj"
                             "notebooks/nb02_blending.clj"]
               :base-source-path "."
               :base-target-path "out"
               :format [:html]
               :show false})
  (report/write-html! (:nb01 report-targets))
  (report/write-blend-html! (:nb02 report-targets))
  {:clay (mapv (fn [path] {:path path :bytes (.length (io/file path))})
               clay-targets)
   :report (mapv (fn [[k path]] {:id k :path path :bytes (.length (io/file path))})
                 report-targets)})

(defn -main [& _]
  (let [{:keys [clay report]} (render!)]
    (println (format "CLAY RENDER OK %s; reports=%s"
                     (pr-str clay)
                     (pr-str report)))
    (System/exit 0)))
