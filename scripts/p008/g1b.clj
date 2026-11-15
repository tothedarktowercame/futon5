;; G1b (DESIGN-P008 §6/§10.1): give the 12 July kinds to the S8 vector path
;; with efe/s8-predictor set to a per-kind predictor that returns exactly the
;; keyword path's conditional-model row, and check byte-identity with the
;; keyword path over the G1 runs (beta 10/12, kappa 0.1, seeds 2026102000-017,
;; T = 1500).
;;
;; Run from a futon5 checkout on branch p008-s8-vocabulary, own JVM:
;;   nice -n 10 clojure -J-XX:ActiveProcessorCount=4 -M scripts/p008/g1b.clj
;;
;; Exotypes are mapped back to kind names before hashing: each vector in the
;; final grid is looked up in {(kind->s8 kind) kind} over the 12 kinds; an
;; unmapped vector is a hard error (adoption only ever copies a neighbour's
;; exotype, so the 12 kinds' vectors are closed under the step).
;; Tie-break note (§10.1), CORRECTED 2026-11-15: there is no separate seeded
;; tie-break in the S8 path, and at these betas the winner is NOT the argmin.
;; The G1b states carry :policy-precision gamma (scripts/
;; local_compressibility_grid.clj:128), so metaca-step -> tuning/step
;; (self_tuning.clj:285) -> transmit (:223) -> cell-decision (:137) takes the
;; softmax branch: softmax-probabilities (self_tuning.clj:119) over
;; -gamma*total, then a seeded sample-policy draw (:202). efe/cell-decision's
;; (first (sort-by :total ...)) is a different function and is not in this
;; call chain. Equal totals get equal probability; the candidate order
;; (hold, left, right, blend) only lays out the CDF inside sample-policy.
;; So G1b still compares like with like (one shared decision rule), and the
;; tie counts below measure degeneracy of :total, not tie-break sensitivity.
;; Recount: scripts/p008/g1b_ties.clj.
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

;; The per-kind S8 predictor: exactly the keyword path's derived-model row.
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

(defn run-pair
  "Run keyword and S8-vector trajectories side by side. Returns
  {:match boolean :first-divergence (step, field) | nil :ties long}."
  [beta kappa seed T]
  (loop [t 0
         kw (metaca-state beta kappa seed)
         s8 (update (metaca-state beta kappa seed)
                    :exotypes #(mapv grid/kind->s8 %))
         ties 0
         divergence nil]
    (if (= t T)
      (let [h-kw (sha (pr-str [(vec (map str (:genotype kw)))
                               (str (apply str (:phenotype kw)))
                               (vec (:exotypes kw))]))
            h-s8 (sha (pr-str [(vec (map str (:genotype s8)))
                               (str (apply str (:phenotype s8)))
                               (vec (s8->kinds (:exotypes s8)))]))]
        {:match (and (nil? divergence) (= h-kw h-s8))
         :hash h-s8 :kw-hash h-kw :ties ties :first-divergence divergence})
      (let [kw' (metaca-step kw)
            s8' (metaca-step s8)
            tie-count (count (filter
                              (fn [d]
                                (let [w (:total (:winner d))]
                                  (some #(= w (:total %)) (rest (sort-by :total (:candidates d))))))
                              (:efe-decisions s8')))
            div (or divergence
                    (when (or (not= (:genotype kw') (:genotype s8'))
                              (not= (:phenotype kw') (:phenotype s8'))
                              (not= (:exotypes kw') (s8->kinds (:exotypes s8'))))
                      [t (cond (not= (:genotype kw') (:genotype s8')) :genotype
                               (not= (:phenotype kw') (:phenotype s8')) :phenotype
                               :else :exotypes)]))]
        (recur (inc t) kw' s8' (+ ties tie-count) div)))))

(def T 1500)
(def refs
  (into {}
        (mapcat (fn [[beta file]]
                  (for [line (str/split-lines (slurp file))]
                    (let [[_ b k s _ h] (str/split line #"\s+")]
                      [[(Double/parseDouble b) (Long/parseLong s)] h])))
                {10.0 "/tmp/g1-p008-10.0.txt"
                 12.0 "/tmp/g1-p008-12.0.txt"})))

(def results
  (for [beta [10.0 12.0]
        seed (range 2026102000 2026102018)]
    (let [r (run-pair beta 0.1 seed T)
          ref (get refs [beta seed])]
      (println "G1B" beta 0.1 seed T
               (:hash r) "kw" (:kw-hash r)
               "ref" ref
               (if (and (:match r) (= (:hash r) ref)) "MATCH" "DIFFER")
               "ties" (:ties r)
               "div" (:first-divergence r))
      (flush)
      [beta seed r])))

(let [matches (count (filter (fn [[b s r]] (and (:match r)
                                                (= (:hash r) (get refs [b s]))))
                             results))
      total-ties (reduce + (map (comp :ties second) (map (fn [[b s r]] [b r]) results)))]
  (println "G1B-SUMMARY" matches "/ 36 identical;"
           "exact-tie decisions in the 36 runs:" total-ties))
(shutdown-agents)
