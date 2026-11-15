;; G1b tie-count follow-up (claude-1, 2026-11-15). Read-only recount on 4 of
;; the 36 G1b runs (beta 10 and 12, seeds 2026102000 and 2026102001, kappa
;; 0.1, T = 1500), plus a correction to g1b.clj's tie-break note.
;;
;; CORRECTION (see g1b.clj header, now fixed): at these betas the winner is
;; NOT (first (sort-by :total ...)) of efe/cell-decision. The G1b states carry
;; :policy-precision gamma (scripts/local_compressibility_grid.clj:128), so
;; metaca-step -> tuning/step (self_tuning.clj:285) -> transmit (:223) ->
;; cell-decision (:137) takes the softmax branch: softmax-probabilities
;; (self_tuning.clj:119) over -gamma*total, then sample-policy (:129, called at
;; :202) with a seeded draw. Equal totals get equal probability; the candidate
;; order (hold, left, right, blend) only fixes the CDF interval layout inside
;; sample-policy, it is not an argmin tie-break.
;;
;; Counting rule (registered by the packet text itself; claude-1's bell of
;; 2026-11-15, edge invoke-1794722599460-20803-76db1e1d):
;;   (a) decisions where some non-winner candidate's :total equals the
;;       winner's :total (same definition as the G1b tie counter);
;;   (b) of (a), those whose tied set (all candidates at the winner's total)
;;       names at least two distinct :candidate-exotype values;
;;   (c) of (b), those where the first-of-sort argmin under candidate order
;;       [hold left right blend] differs from the right-first order
;;       [hold right left blend].
;; Additional measured counts (labelled): (d) drawn winner differs from the
;; left-first argmin, overall and within (b) -- this is what the softmax draw
;; actually does to the trajectory.
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

(def left-order  {:hold 0 :adopt-left 1 :adopt-right 2 :blend 3})
(def right-order {:hold 0 :adopt-right 1 :adopt-left 2 :blend 3})

(defn argmin [candidates order]
  (:candidate-exotype
   (first (sort-by (juxt :total (comp order :policy)) candidates))))

(defn classify [decision]
  (let [w (:total (:winner decision))
        tied (filter #(= w (:total %)) (:candidates decision))
        tie? (< 1 (count tied))
        distinct-exotypes? (and tie? (< 1 (count (set (map :candidate-exotype tied)))))
        c (and distinct-exotypes?
               (not= (argmin (:candidates decision) left-order)
                     (argmin (:candidates decision) right-order)))
        drawn-differs (not= (:candidate-exotype (:winner decision))
                            (argmin (:candidates decision) left-order))]
    {:a tie?
     :b distinct-exotypes?
     :c c
     :d drawn-differs
     :d-in-b (and distinct-exotypes? drawn-differs)}))

(def T 1500)

(defn run-counts [beta seed]
  (loop [t 0
         s8 (update (metaca-state beta 0.1 seed)
                    :exotypes #(mapv grid/kind->s8 %))
         acc {:a 0 :b 0 :c 0 :d 0 :d-in-b 0 :steps 0 :cells 0}]
    (if (= t T)
      acc
      (let [s8' (metaca-step s8)
            cls (map classify (:efe-decisions s8'))]
        (recur (inc t) s8'
               (-> acc
                   (update :steps inc)
                   (update :cells + (count cls))
                   (as-> m (reduce (fn [m c]
                                     (reduce (fn [m k] (if (get c k) (update m k inc) m))
                                             m [:a :b :c :d :d-in-b]))
                                   m cls))))))))

(doseq [beta [10.0 12.0]
        seed [2026102000 2026102001]]
  (let [r (run-counts beta seed)]
    (println "G1B-TIES" beta 0.1 seed T
             "cells" (:cells r)
             "a-all-ties" (:a r)
             "b-distinct-exotype" (:b r)
             "c-argmin-order-flips" (:c r)
             "d-drawn!=left-argmin" (:d r)
             "d-within-b" (:d-in-b r))
    (flush)))

(shutdown-agents)
