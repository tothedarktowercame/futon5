(ns exotype-immune-axis-p2
  "P2 (TN-baldwin-reboot.md 30.5, 31): does the ABSORBING-BYTE count govern
   freezing, on an axis the generative model cannot see?

   PREREGISTERED CLAIM. For a permutation propagator,

       rate = 0.5 + fix(sigma)/16

   so `:rule-change` is a function of the fixed-point count alone. Every all-even
   cycle type has fix = 0 and therefore rate EXACTLY 0.5000, while its absorbing
   byte count is 2^(#cycles). The five arms below span absorbing counts
   0, 2, 4, 8, 16 at a single, identical value of the only coordinate the model
   represents.

     arm         cycle type   rate     absorbing bytes
     :odd53      (5,3)        0.5000    0   <- control, odd cycles
     :even1      (8)          0.5000    2
     :collapser  (6,2)        0.5000    4   <- the one already in the vocabulary
     :even8      (4,2,2)      0.5000    8
     :even4      (2,2,2,2)    0.5000   16

   PREDICTION: time-to-freeze decreases monotonically in absorbing count, and
   `:odd53` never freezes.
   FALSIFIER: arms do not separate, or separate without ordering by absorbing
   count -- either kills the claim that this axis governs the ordered regime.
   CONTROL: the per-application change rate must be ~0.5 in EVERY arm. If it is
   not, the arms differ on the visible coordinate too and the design is void.

   NB the control is measured on RANDOM BYTES, not on the running grid. Measured
   in-run it is confounded by the very effect under test: once an arm freezes,
   nothing changes and its apparent rate collapses toward zero (`:even4` reads
   0.0066 in-run). A control contaminated by the treatment is not a control.

   Baseline configuration only (blend 0, transfer 0): an absorbing byte is
   absorbing precisely because nothing else writes the genotype.

   Partitioned by CONDITION per futon0/README-bare-metal.md 5 -- one process per
   arm, one file each, merged afterwards.

     clojure -M scripts/exotype_immune_axis_p2.clj run <arm> <out.edn>
     clojure -M scripts/exotype_immune_axis_p2.clj report <out.md> <in.edn>..."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.xenotype.generator :as gen]))

(def config
  {:arms [:odd53 :even1 :collapser :even8 :even4]
   :seeds 400 :seed-base 20260804 :width 80 :steps 300
   :checkpoints [0 1 2 3 5 10 15 20 30 40 60 100 200 300]
   :control-bytes 20000})

(defn absorbing-bytes
  "Bytes the propagator cannot change: bit[sigma(k)] != bit[k] for every k."
  [kind]
  (let [pos (gen/sigma-positional (get grid/propagators kind))
        bit (fn [b i] (bit-and (bit-shift-right b (- 7 i)) 1))]
    (set (for [b (range 256)
               :when (every? #(not= (bit b (nth pos %)) (bit b %)) (range 8))]
           (str (ca/sigil-for (str/replace (format "%8s" (Integer/toBinaryString b)) " " "0")))))))

(defn control-rate
  "P(one application changes the byte) over RANDOM bytes -- the visible
   coordinate, measured free of the dynamics. Must be ~0.5 in every arm."
  [kind]
  (let [n (:control-bytes config)
        hits (count (filter true?
                            (for [i (range n)]
                              (let [b (mod (* i 2654435761) 256)
                                    s (ca/sigil-for (str/replace (format "%8s" (Integer/toBinaryString b)) " " "0"))]
                                (not= (str s) (str (grid/apply-exotype s kind (+ 7777 i))))))))]
    (/ (double hits) n)))

(defn- run-seed [kind seed]
  (let [w (:width config)
        absorbing (absorbing-bytes kind)
        st (ca/with-seed seed
             {:arm :heterogeneous-fixed :seed seed :time 0
              :exotypes (vec (repeat w kind))
              :genotype (vec (ca/random-sigil-string w))
              :phenotype (ca/random-phenotype-string w)})
        cps (set (:checkpoints config))]
    (loop [s st t 0 frozen {} changes 0 n 0]
      (if (> t (:steps config))
        {:frozen frozen :change-rate (/ (double changes) (max n 1))}
        (let [nx (grid/step s)
              f (/ (count (filter #(absorbing (str %)) (:genotype s))) (double w))
              ;; realized per-application change rate -- the rate-match control
              d (count (filter true? (map not= (map str (:genotype s))
                                          (map str (:genotype nx)))))]
          (recur nx (inc t)
                 (if (cps t) (assoc frozen t f) frozen)
                 (+ changes d) (+ n w)))))))

(defn run-arm [kind]
  (let [seeds (range (:seed-base config) (+ (:seed-base config) (:seeds config)))
        rs (mapv #(run-seed kind %) seeds)
        at (fn [t] (mapv #(get-in % [:frozen t]) rs))
        mean (fn [xs] (/ (reduce + xs) (double (count xs))))
        sd (fn [xs] (let [m (mean xs)]
                      (Math/sqrt (mean (map #(* (- % m) (- % m)) xs)))))
        ;; first checkpoint at which a seed is at least half frozen
        half (mapv (fn [r] (or (first (filter #(>= (get-in r [:frozen %] 0.0) 0.5)
                                              (:checkpoints config)))
                               -1))
                   rs)]
    {:arm kind
     :absorbing (count (absorbing-bytes kind))
     :derived-rate (gen/rule-change-rate (get grid/propagators kind))
     :control-rate (control-rate kind)
     :in-run-change-rate (mean (map :change-rate rs))
     :frozen (into (sorted-map) (for [t (:checkpoints config)]
                                  [t {:mean (mean (at t)) :sd (sd (at t))}]))
     :half-freeze-steps {:never (count (filter neg? half))
                         :median (let [ok (sort (remove neg? half))]
                                   (if (seq ok) (nth ok (quot (count ok) 2)) nil))}
     :config config}))

(defn -main [& [mode a b & more]]
  (case mode
    "run" (let [kind (keyword a)
                result (run-arm kind)]
            (spit b (pr-str result))
            (println (format "%s -> %s  absorbing=%d control-rate=%.4f in-run=%.4f"
                             a b (:absorbing result) (:control-rate result)
                             (:in-run-change-rate result))))
    "report"
    (let [rs (sort-by :absorbing (map (comp edn/read-string slurp) (cons b more)))
          cps (:checkpoints config)]
      (spit a
            (str "# P2 — the absorbing-byte axis\n\n"
                 "All arms have `rate = 0.5000` exactly: the coordinate the generative model\n"
                 "represents is IDENTICAL across every row below.\n\n"
                 (format "%d seeds, width %d, %d steps, blend 0, transfer 0.\n\n"
                         (:seeds config) (:width config) (:steps config))
                 "| arm | absorbing | derived rate | control rate | "
                 (str/join " | " (map #(str "t=" %) cps)) " | median t½ | never |\n"
                 "|---|---:|---:|---:|" (str/join "" (repeat (count cps) "---:|")) "---:|---:|\n"
                 (str/join "\n"
                   (for [r rs]
                     (format "| `%s` | %d | %.4f | %.4f | %s | %s | %d |"
                             (name (:arm r)) (:absorbing r) (:derived-rate r)
                             (:control-rate r)
                             (str/join " | " (for [t cps]
                                               (format "%.2f" (get-in r [:frozen t :mean]))))
                             (str (get-in r [:half-freeze-steps :median]))
                             (get-in r [:half-freeze-steps :never]))))
                 "\n"))
      (println "wrote" a))
    (println "usage: run <arm> <out.edn> | report <out.md> <in.edn>...")))

(apply -main *command-line-args*)
