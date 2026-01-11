(ns futon5.sigils
  "CLI utility for inspecting sigil allocations and metapattern bindings."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- load-resource [path]
  (some-> path io/resource slurp edn/read-string))

(defn- sigil-base []
  (or (load-resource "futon5/sigils.edn") []))

(defn- sigil-patterns []
  (or (load-resource "futon5/sigil_patterns.edn") []))

(defn entries []
  (let [patterns (into {}
                       (map (fn [{:keys [sigil] :as entry}]
                              [sigil entry])
                            (sigil-patterns)))]
    (map (fn [{:keys [sigil bits translation color]}]
           (let [{:keys [role pattern description]} (get patterns sigil)]
             {:sigil sigil
              :bits bits
              :translation translation
              :color color
              :role role
              :pattern pattern
              :description description}))
         (sigil-base))))

(defn reserved-entries []
  (filter :role (entries)))

(defn free-entries []
  (remove :role (entries)))

(defn- pad [s width]
  (let [s (str s)]
    (if (>= (count s) width)
      s
      (str s (apply str (repeat (- width (count s)) " "))))))

(defn- print-table [rows]
  (let [header ["Sigil" "Bits" "Role" "Pattern" "Translation"]
        widths (map #(apply max (map count %))
                    (map (fn [idx]
                           (map #(nth % idx)
                                (cons header rows)))
                         (range (count header))))]
    (println (apply str (interpose "  " (map pad header widths))))
    (doseq [[sig bits role pattern translation] rows]
      (println (apply str (interpose "  "
                                     (map pad [sig bits (or role "-")
                                               (or pattern "-") translation]
                                          widths)))))))

(defn -main
  [& _]
  (let [reserved (reserved-entries)
        total (count (entries))
        reserved-count (count reserved)
        rows (map (fn [{:keys [sigil bits role pattern translation]}]
                    [sigil bits (or role "") (or pattern "") (or translation "")])
                  reserved)]
    (if (seq rows)
      (do
        (println "Reserved sigils (" reserved-count "of" total ")")
        (print-table rows))
      (println "No metapatterns registered."))
    (println)
    (println "Free slots:" (- total reserved-count))))
