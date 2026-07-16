(ns exotype-demo
  "Compositional exotypes, by example (M-propagators, 2026-07-16).

  Joe's conjecture: in the pheno->geno->EXO->xeno hierarchy, an EXOTYPE is a
  'global physics' composed from propagators -- one propagator chosen on one local
  condition, another on another. And known apparatus (baldwin, blend) are probably
  themselves such compositions. This demo builds the general form
  (:rule-permute-switch) and shows three exotypes, EACH measured against its own two
  branches run solo.

  THE CLAIM UNDER TEST (stated so it can fail): a switch settles where NEITHER of its
  branches settles alone. If switch(A,B) just tracks A or just tracks B, composition
  bought nothing and the conjecture is not yet demonstrated. The solo columns are the
  nulls; the switch must escape the interval between them.

  Propagator vocabulary is drawn from the 20,256-orbit census (features.partial.edn),
  each sigma chosen for a measured SOLO role, then converted to futon5's neighbourhood
  convention THROUGH the elisp truth table (the port the census was measured in).
  Sigmas are legacy-positional; passing them raw to futon5 would be the silent-port
  bug that positional-sigma->neighbourhood-sigma exists to prevent."
  (:require [futon5.ca.core :as ca] [futon5.wiring.runtime :as rt]
            [futon5.xenotype.generator :as gen] [futon5.mmca.render :as render]
            [clojure.java.shell :as sh]))

;; census/elisp neighbourhood order -- NOT ca/truth-table-3
(def elisp-table ["000" "001" "010" "100" "011" "101" "110" "111"])
(defn nb [positional] (gen/positional-sigma->neighbourhood-sigma positional elisp-table))
(defn perm [s] (mapv #(Character/digit ^char % 10) (seq s)))

;; --- vocabulary: measured SOLO role -> sigma (from the census mining) ---
(def PROPS
  {:builder   (nb (perm "51034267"))  ; survives, ~48 terminal rules, entropy .68
   :collapser (nb (perm "10345672"))  ; -> 1 rule, dies ~gen 50 (Figure-8-like, quiet)
   :chaos     (nb (perm "13407265"))  ; survives, phenotype activity .57 (turbulent)
   :identity  (nb [0 1 2 3 4 5 6 7])}) ; hold

(defn solo-diagram [sigma]
  {:nodes [{:id :p :component :context-pred} {:id :s :component :context-self}
           {:id :c :component :context-succ} {:id :b :component :blend-cell}
           {:id :m :component :rule-permute :params {:sigma sigma}}
           {:id :o :component :output-sigil}]
   :edges [{:from :p :from-port :sigil :to :b :to-port :pred}
           {:from :s :from-port :sigil :to :b :to-port :self}
           {:from :c :from-port :sigil :to :b :to-port :succ}
           {:from :b :from-port :result :to :m :to-port :rule}
           {:from :m :from-port :result :to :o :to-port :sigil}]
   :output :o})

(defn switch-diagram [sigma-a sigma-b condition]
  {:nodes [{:id :p :component :context-pred} {:id :s :component :context-self}
           {:id :c :component :context-succ} {:id :b :component :blend-cell}
           {:id :m :component :rule-permute-switch
            :params {:sigma-a sigma-a :sigma-b sigma-b :condition condition}}
           {:id :o :component :output-sigil}]
   :edges [{:from :p :from-port :sigil :to :b :to-port :pred}
           {:from :s :from-port :sigil :to :b :to-port :self}
           {:from :c :from-port :sigil :to :b :to-port :succ}
           {:from :b :from-port :result :to :m :to-port :rule}
           {:from :m :from-port :result :to :o :to-port :sigil}]
   :output :o})

(def W 60) (def STEPS 120) (def SEEDS [0 1 2])

(defn run [diagram seed]
  (ca/with-seed seed
    (let [g0 (ca/random-sigil-string W)
          p0 (apply str (repeatedly W #(if (< (ca/rnd) 0.5) "0" "1")))]
      (rt/run-wiring {:wiring {:diagram diagram} :genotype g0 :phenotype p0 :generations STEPS}))))

(defn terminal-rules [r]
  (count (distinct (seq (last (:gen-history r))))))
(defn phe-activity [r]
  (let [ph (:phe-history r)]
    (/ (reduce + (map (fn [a b] (count (filter true? (map not= a b)))) ph (rest ph)))
       (double (* W (dec (count ph)))))))
(defn summary [diagram]
  (let [rs (map #(run diagram %) SEEDS)]
    {:rules (/ (reduce + (map terminal-rules rs)) 3.0)
     :act (/ (reduce + (map phe-activity rs)) 3.0)
     :ex (first rs)}))

;; --- the three exotypes: (name, sigma-a, sigma-b, condition, the story) ---
(def EXOTYPES
  [{:name "baldwin-reconstructed" :a :builder :b :identity :cond :boredom
    :story "bored -> build, interesting -> hold. Baldwin's control structure, in propagators."}
   {:name "thermostat" :a :collapser :b :builder :cond :active
    :story "local active -> collapse, local quiet -> build. A diversity set-point?"}
   {:name "annealer" :a :chaos :b :collapser :cond :active
    :story "hot (active) -> stir, cool (quiet) -> freeze. Settle onto structure?"}])

(println "=== COMPOSITIONAL EXOTYPES (M-propagators, by example) ===")
(println (format "  width %d, %d gens, seeds %s. terminal-rules / phenotype-activity.\n" W STEPS SEEDS))
(println (format "  %-24s %10s %10s %10s   %s" "" "branch-A" "branch-B" "SWITCH" "escapes A-B interval?"))

(def out (atom []))
(doseq [{:keys [name a b cond story]} EXOTYPES]
  (let [sa (PROPS a) sb (PROPS b)
        ra (summary (solo-diagram sa)) rb (summary (solo-diagram sb))
        rs (summary (switch-diagram sa sb cond))
        lo (min (:rules ra) (:rules rb)) hi (max (:rules ra) (:rules rb))
        escapes (or (< (:rules rs) (- lo 0.5)) (> (:rules rs) (+ hi 0.5)))]
    (println (format "  %-24s %10s %10s %10s   %s" name
                     (format "%.1f" (:rules ra)) (format "%.1f" (:rules rb))
                     (format "%.1f" (:rules rs))
                     (if escapes "YES — emergent" "no — tracks a branch")))
    (println (format "  %-24s %10s %10s %10s   (%s=A  %s=B  cond=%s)"
                     (str "  activity:") (format "%.2f" (:act ra)) (format "%.2f" (:act rb))
                     (format "%.2f" (:act rs)) (clojure.core/name a) (clojure.core/name b) (clojure.core/name cond)))
    (println (format "  %-24s %s\n" "" story))
    ;; render the switch exotype's evolution (256-colour genotype | b/w phenotype)
    (let [ppm (str "/tmp/exo-" name ".ppm")]
      (render/write-ppm! ppm (render/render-history-phenotype
                              (:gen-history (:ex rs)) (:phe-history (:ex rs)))
                         :comment name)
      (sh/sh "convert" ppm (str "holes/labs/M-aif-tokamak/exotype-" name ".png")))
    (swap! out conj {:name name :branch-a (:rules ra) :branch-b (:rules rb)
                     :switch (:rules rs) :escapes escapes})))

(println "panels: holes/labs/M-aif-tokamak/exotype-*.png")
(flush)
