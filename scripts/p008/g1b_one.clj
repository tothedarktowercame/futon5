;; G1b single-seed recheck (P-008b §7 item 1 packet, test 4): rerun the G1b
;; reproduction for ONE seed, beta 10, seed 2026102000, T = 1500, and compare
;; against the recorded G1 hash 957e93e21609fb79 from /tmp/g1-p008-10.0.txt
;; (the G1 run record; same protocol as scripts/p008/g1b.clj). Purpose: show
;; the item-1 representation additions (perm?, e8->sigma, unwired) leave the
;; G1 path byte-identical.
(require '[clojure.string :as str]
         '[futon5.exotype.efe :as efe]
         '[futon5.exotype.grid :as grid])

(let [src (slurp "scripts/local_compressibility_grid.clj")]
  (load-string (str/replace src "(apply -main *command-line-args*)" "")))
(def ns* (find-ns 'local-compressibility-grid))
(def metaca-state @(ns-resolve ns* 'metaca-state))
(def metaca-step @(ns-resolve ns* 'checked-metaca-step))

(def kinds grid/exotype-kinds)
(def s8->kind (into {} (for [k kinds] [(grid/kind->s8 k) k])))

(reset! efe/s8-predictor
        (fn [s8-exotype observation]
          (let [kind (or (get s8->kind s8-exotype)
                         (throw (ex-info "s8 vector is not one of the 12 kinds"
                                         {:exotype s8-exotype})))]
            (select-keys (efe/predict kind observation :derived)
                         [:activity :diversity :hunger]))))

(defn sha [s]
  (let [d (.digest (java.security.MessageDigest/getInstance "SHA-256")
                   (.getBytes ^String s "UTF-8"))]
    (apply str (map #(format "%02x" %) (take 8 d)))))

(defn s8->kinds [exotypes] (mapv #(get s8->kind %) exotypes))

;; identical to g1b.clj's run-pair, one seed
(defn run-pair [beta kappa seed T]
  (loop [t 0
         kw (metaca-state beta kappa seed)
         s8 (update (metaca-state beta kappa seed)
                    :exotypes #(mapv grid/kind->s8 %))
         divergence nil]
    (if (= t T)
      (let [h-kw (sha (pr-str [(vec (map str (:genotype kw)))
                               (str (apply str (:phenotype kw)))
                               (vec (:exotypes kw))]))
            h-s8 (sha (pr-str [(vec (map str (:genotype s8)))
                               (str (apply str (:phenotype s8)))
                               (vec (s8->kinds (:exotypes s8)))]))]
        {:match (and (nil? divergence) (= h-kw h-s8)) :hash h-s8 :kw-hash h-kw
         :first-divergence divergence})
      (let [kw' (metaca-step kw)
            s8' (metaca-step s8)
            div (or divergence
                    (when (or (not= (:genotype kw') (:genotype s8'))
                              (not= (:phenotype kw') (:phenotype s8'))
                              (not= (:exotypes kw') (s8->kinds (:exotypes s8'))))
                      [t (cond (not= (:genotype kw') (:genotype s8')) :genotype
                               (not= (:phenotype kw') (:phenotype s8')) :phenotype
                               :else :exotype)]))]
        (recur (inc t) kw' s8' div)))))

(let [recorded "957e93e21609fb79"
      r (run-pair 10.0 0.1 2026102000 1500)]
  (println "G1B-ONE 10.0 0.1 2026102000 1500"
           (:hash r) "kw" (:kw-hash r)
           "recorded" recorded
           (if (and (:match r) (= (:hash r) recorded)) "MATCH" "DIFFER")
           "div" (:first-divergence r)))

(shutdown-agents)
