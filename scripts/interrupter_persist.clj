(ns interrupter-persist
  "Coupled-chain persistence table X_persist for TN-interrupter-fable-answer.md 1.5.

   For each ordered pair (A,B) of vocabulary kinds: run the two byte chains under
   shared draws (baseline applies A forever, twin applies B forever, same k
   stream, same uniform start byte b0). Record P(b_t != b'_t) at checkpoints.
   Monte Carlo: all 256 start bytes x 40 shared k-streams per pair.

   Internal check: P(diverged at t=1) must reproduce X_pair (it is its exact
   byte-averaged one-step probability).

   Run: clojure -Sdeps '{:paths [\"src\" \"resources\"]}' -M scripts/interrupter_persist.clj"
  (:require [clojure.pprint :as pprint]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.policy-epistemic :as pe]
            [futon5.xenotype.generator :as gen]))

(def kinds grid/exotype-kinds)
(def T 60)
(def checkpoints #{1 5 10 20 40 60})
(def streams 40)

(def positional
  (into {} (for [k kinds] [k (vec (gen/sigma-positional (get grid/propagators k)))])))

(defn- step-byte ^long [^long b sigma ^long k]
  (let [bit-k (bit-and (bit-shift-right b k) 1)
        target (long (nth sigma k))
        nb (bit-and (bit-xor bit-k 1) 1)]
    (bit-or (bit-and b (bit-not (bit-shift-left 1 target)))
            (bit-shift-left nb target))))

(defn pair-curve
  "Map t -> P(diverged at t), plus :ever and :healed-at-60."
  [a b]
  (let [sa (positional a) sb (positional b)
        rng (java.util.Random. 20260805)
        n (* 256 streams)
        counts (long-array (inc T))
        ever (atom 0) healed (atom 0)]
    (dotimes [s streams]
      (let [ks (long-array T)]
        (dotimes [t T] (aset ks t (long (.nextInt rng 8))))
        (dotimes [b0 256]
          (loop [x (long b0) y (long b0) t 0 was-div false]
            (if (= t T)
              (do (when was-div (swap! ever inc))
                  (when (and was-div (== x y)) (swap! healed inc)))
              (let [k (aget ks t)
                    x' (step-byte x sa k)
                    y' (step-byte y sb k)
                    div (not= x' y')]
                (when div (aset counts (inc t) (inc (aget counts (inc t)))))
                (recur x' y' (inc t) (or was-div div))))))))
    {:curve (into (sorted-map)
                  (for [t (sort checkpoints)]
                    [t (/ (aget counts t) (double n))]))
     :ever (/ @ever (double n))
     :healed-at-60 (/ @healed (double n))}))

(defn- corr [xs ys]
  (let [n (count xs)
        mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
        dx (map #(- % mx) xs) dy (map #(- % my) ys)
        sxy (reduce + (map * dx dy))
        sx (Math/sqrt (reduce + (map #(* % %) dx)))
        sy (Math/sqrt (reduce + (map #(* % %) dy)))]
    (/ sxy (* sx sy))))

(def all-even #{:collapser :even1 :even4 :even8})

(defn -main []
  (let [rows (vec (for [a kinds b kinds :when (not= a b)]
                    (let [{:keys [curve ever healed-at-60]} (pair-curve a b)]
                      {:a a :b b :x-pair (pe/pair-value a b)
                       :curve curve :ever ever :healed healed-at-60})))]
    (spit "analysis/interrupter-persist-rows.edn"
          (with-out-str (pprint/pprint rows)))
    (println "== calibration: P(div at t=1) vs X_pair over 132 ordered pairs ==")
    (println (format "  r = %.4f  max |diff| = %.4f"
                     (corr (map :x-pair rows) (map #(get-in % [:curve 1]) rows))
                     (apply max (map #(Math/abs (- (double (:x-pair %))
                                                   (double (get-in % [:curve 1])))) rows))))
    (println "\n== X_persist(60) vs X_pair ==")
    (println (format "  all pairs:            r = %.4f" (corr (map :x-pair rows) (map #(get-in % [:curve 60]) rows))))
    (let [halting (filter #(and (all-even (:a %)) (all-even (:b %))) rows)
          nonh (remove #(and (all-even (:a %)) (all-even (:b %))) rows)]
      (println (format "  all-even x all-even:  r = %.4f (n=%d)"
                       (corr (map :x-pair halting) (map #(get-in % [:curve 60]) halting))
                       (count halting)))
      (println (format "  rest:                 r = %.4f (n=%d)"
                       (corr (map :x-pair nonh) (map #(get-in % [:curve 60]) nonh))
                       (count nonh))))
    (println "\n== divergence level: mean curve over pairs, grouped ==")
    (doseq [[label group] [["all-even x all-even" (filter #(and (all-even (:a %)) (all-even (:b %))) rows)]
                           ["mixed / odd pairs  " (remove #(and (all-even (:a %)) (all-even (:b %))) rows)]]]
      (println (format "  %s  t=1 %.3f t=5 %.3f t=10 %.3f t=20 %.3f t=40 %.3f t=60 %.3f  ever %.3f healed %.3f"
                       label
                       (/ (reduce + (map #(get-in % [:curve 1]) group)) (count group))
                       (/ (reduce + (map #(get-in % [:curve 5]) group)) (count group))
                       (/ (reduce + (map #(get-in % [:curve 10]) group)) (count group))
                       (/ (reduce + (map #(get-in % [:curve 20]) group)) (count group))
                       (/ (reduce + (map #(get-in % [:curve 40]) group)) (count group))
                       (/ (reduce + (map #(get-in % [:curve 60]) group)) (count group))
                       (/ (reduce + (map :ever group)) (count group))
                       (/ (reduce + (map :healed group)) (count group)))))
    (println "\n== extreme pairs: |X_persist(60) - X_pair| largest ==")
    (doseq [r (take 8 (sort-by #(- (Math/abs (- (double (get-in % [:curve 60]))
                                                (double (:x-pair %))))) rows))]
      (println (format "  %-10s -> %-10s X_pair %.3f persist60 %.3f ever %.3f healed %.3f"
                       (name (:a r)) (name (:b r)) (:x-pair r)
                       (get-in r [:curve 60]) (:ever r) (:healed r))))))

(-main)
