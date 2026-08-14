;; :none coexistence-label check (fable, 2026-08-08).
;;
;; The four-arm gate (interface_adoption_gate.clj) reported :none finishing at
;; S ~= 0.49 -- three times the top of the prospective grid's mixed-regime range
;; [0.0984, 0.1617].  TN-fable-strict-locality.md S0 asks the deciding question:
;; does the :none arm satisfy the coexistence label (never absorbs AND settled
;; fraction in [0.02, 0.98])?
;;
;;   - :none PASSES the label  -> the detector's no-overlap S-range does not
;;     transfer across ICs/widths (external-validity caveat for the detector);
;;   - :none FAILS the label   -> a high-S state that is not the regime; direct
;;     counterexample to "the maximum of S is coexistence."
;;
;; Exact re-run of the gate's :none arm (same seeds 101-124, same derivation of
;; ICs from seed), with per-seed label output.  No adoption occurs in :none, so
;; the shared decision stream is irrelevant here.
(require '[futon5.ca.core :as ca] '[futon5.exotype.grid :as grid])

(def WIDTH 120)
(def STEPS 1200)
(def SETTLE 15)
(def FROZEN-LEANING [:collapser :even1 :fix2 :even8])
(def UNIFORM-FRACTION 0.92)

(defn seeded-phenotype [width]
  (apply str (repeatedly width #(if (< (ca/rnd) UNIFORM-FRACTION) 0 1))))

(defn settled? [phes t i]
  (and (>= t SETTLE)
       (every? #(= (get-in phes [% i]) (get-in phes [(dec %) i]))
               (range (- t (dec SETTLE)) (inc t)))))

(defn iface-at [phes t i w]
  (let [me (settled? phes t i)]
    (if (or (zero? i) (= i (dec w))) 0
      (if (or (not= me (settled? phes t (mod (dec i) w)))
              (not= me (settled? phes t (mod (inc i) w)))) 1 0))))

(defn run-none [seed]
  (let [st (ca/with-seed seed
             {:arm :heterogeneous-fixed :seed seed :time 0
              :exotypes (vec (repeatedly WIDTH #(ca/rnd-nth FROZEN-LEANING)))
              :genotype (vec (ca/random-sigil-string WIDTH))
              :phenotype (seeded-phenotype WIDTH)})]
    (loop [s st t 0 phes []]
      (if (= t STEPS)
        phes
        (recur (grid/step s) (inc t) (conj phes (vec (:phenotype s))))))))

(defn summarise [phe]
  (let [n (count phe)
        settled-frac (fn [t] (/ (double (count (filter #(settled? phe t %) (range WIDTH)))) WIDTH))
        iface-frac (fn [t] (/ (double (reduce + (map #(iface-at phe t % WIDTH) (range WIDTH)))) WIDTH))
        late (range (- n 200) n)
        absorbed? (every? #(= (nth phe %) (nth phe (dec %))) (range (- n 100) n))
        S (/ (reduce + (map iface-frac late)) (count late))
        settled (/ (reduce + (map settled-frac late)) (count late))]
    {:S S :settled settled :absorbed absorbed?
     :coexist (and (not absorbed?) (<= 0.02 settled 0.98))}))

(println "seed        S    settled  absorbed  coexist-label")
(let [rs (vec (for [seed (range 101 125)]
                (let [r (summarise (run-none seed))]
                  (printf "%-5d %8.4f %8.3f %9s %10s%n"
                          seed (:S r) (:settled r) (:absorbed r) (:coexist r))
                  (flush)
                  r)))]
  (println)
  (printf "mean S %.4f   mean settled %.3f   coexisting %d/%d   absorbed %d/%d%n"
          (/ (reduce + (map :S rs)) (count rs))
          (/ (reduce + (map :settled rs)) (count rs))
          (count (filter :coexist rs)) (count rs)
          (count (filter :absorbed rs)) (count rs)))
