(ns exotype-bestof-search
  "Best-of search over everything we now know matters, scored by the criterion the
   paper actually defines (TN-baldwin-reboot.md 33, 36).

   SCORE: phenotype damage reach at t=100 after a single-cell flip, against an ECA
   scale measured in the same harness:

     rule 204 (frozen)  1.0     rule 54   32.8
     rule 90            8.0     rule 110  37.1
                                rule 30   65.1  (chaotic)

   Higher is not automatically better: the target is the class-IV band, not the
   maximum. Rule 30 scores highest and is chaos.

   DIMENSIONS, each established as load-bearing by measurement rather than guess:
     arm    -- :heterogeneous-fixed (loop open) vs :boring-triggered (phenotype
               feedback, worth +15 reach) vs :conformist (exotype-only, worth ~0)
     vocab  -- the exotype menu. The objective can only choose what it is offered,
               and with the default four it picks :collapser, which freezes (36.1).
     blend  -- lateral coupling. Genotype damage peaks near 0.5 and dies by 0.75
               (35), and no local observable tracks that peak (36.2).

   Partitioned by ARM per futon0/README-bare-metal.md 5.

     clojure -M scripts/exotype_bestof_search.clj run <arm> <out.edn>
     clojure -M scripts/exotype_bestof_search.clj report <out.md> <in.edn>..."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]))

(def config
  {:arms [:heterogeneous-fixed :boring-triggered :conformist]
   :vocabs {:default4 [:builder :collapser :chaos :identity]
            :odd-only [:odd53]
            :no-absorbing [:odd53 :chaos :fix2 :fix3 :fix4 :builder :fix6 :identity]
            :rate-span [:odd53 :fix2 :fix4 :fix6]
            :absorbing-mix [:odd53 :even1 :even8 :even4]
            :all12 [:builder :collapser :chaos :identity :odd53 :even1 :even8 :even4
                    :fix2 :fix3 :fix4 :fix6]}
   :blends [0.0 0.25 0.5 0.75]
   :seeds 24 :width 201 :steps 100})

(def eca-scale
  "Measured in this same harness, for reference in the report."
  {204 1.0, 90 8.0, 54 32.8, 110 37.1, 30 65.1})

(defn- diffcount [a b] (count (filter true? (map not= a b))))

(defn- damage [arm vocab beta seed]
  (let [w (:width config)
        mid (quot w 2)
        base (ca/with-seed seed
               (cond-> {:arm arm :seed seed :time 0
                        :exotypes (vec (repeatedly w #(ca/rnd-nth vocab)))
                        :genotype (vec (ca/random-sigil-string w))
                        :phenotype (ca/random-phenotype-string w)}
                 (pos? beta) (assoc :blend-strength beta)))
        pert (update base :phenotype
                     #(apply str (update (vec %) mid (fn [c] (if (= \0 c) \1 \0)))))]
    (loop [a base b pert t 0]
      (if (= t (:steps config))
        (diffcount (:phenotype a) (:phenotype b))
        (recur (grid/step a) (grid/step b) (inc t))))))

(defn run-arm [arm]
  (let [seeds (range 20260804 (+ 20260804 (:seeds config)))
        mean (fn [xs] (/ (reduce + xs) (double (count xs))))
        sd (fn [xs] (let [m (mean xs)] (Math/sqrt (mean (map #(* (- % m) (- % m)) xs)))))]
    {:arm arm
     :cells (vec (for [[vname vocab] (:vocabs config)
                       beta (:blends config)]
                   (let [ds (mapv #(damage arm vocab beta %) seeds)]
                     {:vocab vname :blend beta
                      :reach (mean ds) :sd (sd ds)})))
     :config config}))

(defn -main [& [mode a & more]]
  (case mode
    "run" (let [arm (keyword a)
                r (run-arm arm)]
            (spit (first more) (pr-str r))
            (println (format "%s -> %s  best cell: %s"
                             a (first more)
                             (pr-str (apply max-key :reach (:cells r))))))
    "report"
    (let [rs (map (comp edn/read-string slurp) more)
          all (mapcat (fn [r] (map #(assoc % :arm (:arm r)) (:cells r))) rs)
          out a]
      (spit out
            (str "# Best-of search — damage reach on the ECA scale\n\n"
                 (format "%d seeds, width %d, t=%d, single-cell phenotype flip.\n\n"
                         (:seeds config) (:width config) (:steps config))
                 "ECA scale measured in this harness: **204 frozen 1.0 · 90 → 8.0 · "
                 "54 → 32.8 · 110 → 37.1 · 30 chaotic → 65.1**. The target is the "
                 "class-IV band, not the maximum — rule 30 scores highest and is chaos.\n\n"
                 "| rank | arm | vocab | blend | reach | ± |\n|---:|---|---|---:|---:|---:|\n"
                 (str/join "\n"
                   (map-indexed
                    (fn [i c] (format "| %d | `%s` | `%s` | %.2f | **%.1f** | %.1f |"
                                      (inc i) (name (:arm c)) (name (:vocab c))
                                      (:blend c) (:reach c) (:sd c)))
                    (take 20 (sort-by :reach > all))))
                 "\n"))
      (println "wrote" out))
    (println "usage: run <arm> <out.edn> | report <out.md> <in.edn>...")))

(apply -main *command-line-args*)
