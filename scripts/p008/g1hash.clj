;; G1: hash of (genotype, phenotype, exotypes) at step T for beta kappa seed. clojure -M g1hash.clj T beta kappa seed...
(require '[clojure.string :as str])
(let [src (slurp "scripts/local_compressibility_grid.clj")]
  (load-string (str/replace src "(apply -main *command-line-args*)" "")))
(def ns* (find-ns 'local-compressibility-grid))
(def metaca-state @(ns-resolve ns* 'metaca-state))
(def metaca-step @(ns-resolve ns* 'checked-metaca-step))
(defn sha [s] (let [d (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes ^String s "UTF-8"))] (apply str (map #(format "%02x" %) (take 8 d)))))
(let [[T b k & seeds] *command-line-args*]
  (doseq [s seeds]
    (let [st (nth (iterate metaca-step (metaca-state (Double/parseDouble b) (Double/parseDouble k) (Long/parseLong s))) (Long/parseLong T))]
      (println "G1" b k s T (sha (pr-str [(vec (map str (:genotype st))) (str (apply str (:phenotype st))) (vec (:exotypes st))]))))))
(shutdown-agents)
