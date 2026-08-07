(ns exotype-local-damage
  "BUILD PACKET E2a — per-cell local damage measurement.

  Measures how many phenotype cells differ at t=horizon between a baseline
  trajectory and one where the GENOTYPE at INDEX is perturbed (first bit flipped).
  Both trajectories use grid/step from the same seed; the only difference is
  the perturbed byte.

  clojure -M scripts/exotype_local_damage.clj"
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]))

(defn local-damage
  "Number of phenotype cells that differ at t=horizon between the trajectory
  from STATE and the trajectory from STATE with the genotype at INDEX perturbed.

  Perturbs by flipping the first bit of the sigil at INDEX. Both trajectories
  start from the same state map (same :seed); the only difference is the
  perturbed byte."
  [state index horizon]
  (let [
        sigil (nth (:genotype state) index)
        bits (ca/bits-for (str sigil))
        flipped (ca/sigil-for (str (if (= \0 (first bits)) \1 \0)
                                   (subs bits 1)))
        perturbed (assoc (:genotype state) index flipped)
        base-st (assoc state :genotype (:genotype state))
        pert-st (assoc state :genotype perturbed)]
    (loop [b base-st p pert-st t 0]
      (if (= t horizon)
        (count (filter true? (map not= (:phenotype b) (:phenotype p))))
        (recur (grid/step b) (grid/step p) (inc t))))))

(defn- init-state [kind seed]
  (let [w 60]
    (ca/with-seed seed
      {:arm :heterogeneous-fixed :seed seed :time 0
       :exotypes (vec (repeat w kind))
       :genotype (vec (ca/random-sigil-string w))
       :phenotype (ca/random-phenotype-string w)})))

(defn- run-arm [kind seeds horizon]
  (let [w 60
        all (for [seed seeds
                  idx (range w)]
              (local-damage (init-state kind seed) idx horizon))
        mean (/ (reduce + all) (double (count all)))
        sd (let [m mean] (Math/sqrt (/ (reduce + (map #(* (- % m) (- % m)) all))
                                       (double (count all)))))
        zeros (count (filter zero? all))]
    {:kind kind :n (count all)
     :mean mean :sd sd
     :min (apply min all) :max (apply max all)
     :zero-fraction (/ (double zeros) (count all))}))

(defn -main [& _]
  (let [seeds [11 22 33 44 55 66 77 88]
        horizon 60
        results (for [kind [:odd53 :even4]]
                  (run-arm kind seeds horizon))]
    (println "| kind | n | mean | sd | min | max | zero-frac |")
    (println "|---|---:|---:|---:|---:|---:|---:|")
    (doseq [r results]
      (println (format "| %s | %d | %.4f | %.4f | %d | %d | %.4f |"
                       (name (:kind r)) (:n r)
                       (:mean r) (:sd r) (:min r) (:max r)
                       (:zero-fraction r))))
    (let [odd53-mean (:mean (first results))
          even4-mean (:mean (second results))]
      (println)
      (println (format "SANITY CHECK: odd53 mean (%.4f) %s even4 mean (%.4f)"
                       odd53-mean
                       (if (> odd53-mean even4-mean) ">" "<=")
                       even4-mean)))))

(apply -main *command-line-args*)
