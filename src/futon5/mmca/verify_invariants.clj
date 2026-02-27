(ns futon5.mmca.verify-invariants
  "Verify the exotype invariants from Mission-0-CHECKPOINT-invariants.md.

   Run this before ANY compute to ensure the system is working correctly.

   Usage:
     bb -cp src:resources:test -m futon5.mmca.verify-invariants

   Or in REPL:
     (require '[futon5.mmca.verify-invariants :as verify])
     (verify/run-all)

   Expected output:
     All invariants should PASS. If any FAIL, do NOT proceed with
     scoring, evaluation, or xenoevolve until the issue is resolved."
  (:require [clojure.edn :as edn]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.runtime :as mmca]
            [futon5.hexagram.lift :as hex-lift]
            [futon5.xenotype.category :as wiring-cat]))

(defn- check [name test-fn]
  (print (str "  " name "... "))
  (flush)
  (try
    (let [result (test-fn)]
      (if result
        (do (println "PASS") true)
        (do (println "FAIL") false)))
    (catch Exception e
      (println (str "ERROR: " (.getMessage e)))
      false)))

(defn verify-invariant-1
  "Invariant 1: Exotypes Are Real

   Each cell MUST compute its local 36-bit context and apply
   the corresponding physics rule."
  []
  (println "\n=== INVARIANT 1: Exotypes Are Real ===")

  (and
   ;; Test 1: 36-bit context structure
   (check "36-bit context has all components"
          (fn []
            (let [ctx (exotype/build-local-context "一" "二" "三" "四" "1010")]
              (and (= 4 (count (:context-sigils ctx)))
                   (= "1010" (:phenotype-context ctx))))))

   ;; Test 2: Context produces physics rule
   (check "Context produces valid physics rule"
          (fn []
            (let [ctx (exotype/build-local-context "一" "二" "三" "四" "1010")
                  physics (hex-lift/context->physics-rule ctx)]
              (and (map? physics)
                   (<= 0 (:rule physics) 255)
                   (contains? physics :hexagram)
                   (contains? physics :energy)))))

   ;; Test 3: Different contexts produce different rules
   (check "Different contexts produce different rules"
          (fn []
            (let [contexts [(exotype/build-local-context "一" "一" "一" "一" "0000")
                            (exotype/build-local-context "二" "二" "二" "二" "0000")
                            (exotype/build-local-context "三" "三" "三" "三" "0000")
                            (exotype/build-local-context "万" "万" "万" "万" "1111")]
                  rules (mapv #(:rule (hex-lift/context->physics-rule %)) contexts)]
              (> (count (set rules)) 1))))

   ;; Test 4: evolve-string-local produces per-cell rules
   (check "evolve-string-local tracks per-cell rules"
          (fn []
            (let [kernels {:blending (fn [s _ _ _] {:sigil s})
                           :multiplication (fn [s _ _ _] {:sigil s})
                           :ad-hoc-template (fn [s _ _ _] {:sigil s})
                           :blending-mutation (fn [s _ _ _] {:sigil s})
                           :blending-baldwin (fn [s _ _ _] {:sigil s})
                           :collection-template (fn [s _ _ _] {:sigil s})
                           :mutating-template (fn [s _ _ _] {:sigil s})}
                  genotype "一二三四五六七八"
                  result (exotype/evolve-string-local genotype nil genotype kernels)]
              (and (= (count genotype) (count (:rules result)))
                   (every? #(<= 0 % 255) (:rules result))
                   ;; With varied input, expect some rule variety
                   (> (count (set (:rules result))) 1)))))))

(defn verify-invariant-2
  "Invariant 2: Xenotypes Evolve Real Physics

   Xenotype evolution MUST search over the 256-rule space."
  []
  (println "\n=== INVARIANT 2: 256 Physics Rules ===")

  (and
   ;; Test 1: 256 = 64 hexagrams × 4 energies
   (check "256 rules = 64 hexagrams × 4 energies"
          (fn []
            (every? (fn [rule]
                      (let [{:keys [hexagram energy]} (hex-lift/rule->hexagram+energy rule)]
                        (and (<= 1 hexagram 64)
                             (contains? #{:peng :lu :ji :an} (:key energy)))))
                    (range 256))))

   ;; Test 2: Roundtrip encoding
   (check "hexagram+energy -> rule roundtrips correctly"
          (fn []
            (every? (fn [[hexagram energy-id]]
                      (let [rule (hex-lift/hexagram+energy->rule (dec hexagram) energy-id)
                            decoded (hex-lift/rule->hexagram+energy rule)]
                        (and (= hexagram (:hexagram decoded))
                             (= energy-id (get-in decoded [:energy :id])))))
                    (for [h (range 1 65) e (range 4)] [h e]))))

   ;; Test 3: Each rule maps to a kernel
   (check "Each rule maps to a valid kernel spec"
          (fn []
            (every? (fn [rule]
                      (let [spec (exotype/rule->kernel-spec rule)]
                        (and (map? spec)
                             (keyword? (:kernel spec))
                             (map? (:params spec)))))
                    (range 0 256 8))))))

(defn verify-invariant-3
  "Invariant 3: No Mixing Old and New

   Runtime MUST NOT accidentally use old code paths."
  []
  (println "\n=== INVARIANT 3: No Mixing Old and New ===")

  (and
   ;; Test 1: *exotype-system* dynamic var exists
   (check "*exotype-system* dynamic var exists"
          (fn []
            (var? #'exotype/*exotype-system*)))

   ;; Test 2: with-local-physics sets the var
   (check "with-local-physics binds correctly"
          (fn []
            (exotype/with-local-physics
              (= exotype/*exotype-system* :local-physics))))

   ;; Test 3: assert-local-physics! works
   (check "assert-local-physics! enforces local physics"
          (fn []
            (and
             ;; Should throw outside of with-local-physics
             (try
               (exotype/assert-local-physics!)
               false
               (catch Exception _ true))
             ;; Should not throw inside with-local-physics
             (exotype/with-local-physics
               (exotype/assert-local-physics!)
               true))))

   ;; Test 4: run-metadata includes system info
   (check "run-metadata includes exotype-system"
          (fn []
            (let [meta (exotype/run-metadata)]
              (contains? meta :exotype-system))))))

(defn verify-invariant-4
  "Invariant 4: Scoring Is Against Real Runs

   Any scoring MUST operate on runs produced by the new system."
  []
  (println "\n=== INVARIANT 4: Local Physics Mode ===")

  (and
   ;; Test 1: Run with :local-physics mode
   (check "run-mmca supports :exotype-mode :local-physics"
          (fn []
            (let [result (mmca/run-mmca {:genotype "一二三四五六七八"
                                         :generations 3
                                         :exotype-mode :local-physics})]
              (= :local-physics (:exotype-system result)))))

   ;; Test 2: Result includes exotype metadata
   (check "Result includes exotype-metadata"
          (fn []
            (let [result (mmca/run-mmca {:genotype "一二三四五六七八"
                                         :generations 3
                                         :exotype-mode :local-physics})]
              (and (contains? result :exotype-metadata)
                   (= :local-physics (get-in result [:exotype-metadata :exotype-system]))))))

   ;; Test 3: Result tracks local physics runs
   (check "Result tracks local-physics-runs"
          (fn []
            (let [result (mmca/run-mmca {:genotype "一二三四五六七八"
                                         :generations 5
                                         :exotype-mode :local-physics})]
              (seq (:local-physics-runs result)))))))

;;; ============================================================
;;; XENOTYPE INVARIANTS (Part II)
;;; These should FAIL until xenotype wiring is implemented
;;; ============================================================

(defn verify-invariant-5
  "Invariant 5: Xenotypes Have Wiring Definitions

   Each of the 256 xenotypes MUST have an explicit wiring definition
   that specifies which primitives compose and how."
  []
  (println "\n=== INVARIANT 5: Xenotype Wirings Exist ===")

  (and
   ;; Test 1: Generator components file exists
   (check "Generator components file exists"
          (fn []
            (.exists (java.io.File. "resources/xenotype-generator-components.edn"))))

   ;; Test 2: Components can be loaded
   (check "Generator components can be loaded"
          (fn []
            (require '[futon5.xenotype.wiring :as wiring])
            (let [lib ((resolve 'futon5.xenotype.wiring/load-components))]
              (and (map? lib)
                   (> (count (:components lib)) 40)))))

   ;; Test 3: All 8 prototype wirings exist
   (check "All 8 prototype wirings exist (one per hexagram family)"
          (fn []
            (let [dir (java.io.File. "resources/xenotype-wirings")
                  files (when (.exists dir) (.listFiles dir))
                  edn-files (filter #(.endsWith (.getName %) ".edn") files)]
              (>= (count edn-files) 8))))))

(defn verify-invariant-6
  "Invariant 6: Xenotypes Produce Wiring Diagrams

   Each xenotype MUST be renderable as a wiring diagram (mermaid)."
  []
  (println "\n=== INVARIANT 6: Wiring Diagrams Render ===")

  (and
   ;; Test 1: Wiring-to-mermaid function exists
   (check "wiring->mermaid function exists"
          (fn []
            (require 'futon5.xenotype.mermaid)
            (some? (resolve 'futon5.xenotype.mermaid/wiring->mermaid))))

   ;; Test 2: Sample wiring produces valid mermaid
   (check "Sample wiring produces valid mermaid output"
          (fn []
            (require 'futon5.xenotype.mermaid)
            (let [wiring->mermaid (resolve 'futon5.xenotype.mermaid/wiring->mermaid)
                  wiring (edn/read-string (slurp "resources/xenotype-wirings/prototype-001-creative-peng.edn"))
                  mmd (wiring->mermaid (:diagram wiring))]
              (and (string? mmd)
                   (.contains mmd "graph")
                   (.contains mmd "-->")))))

   ;; Test 3: Wiring diagram reflects composition structure
   (check "Diagram contains expected nodes"
          (fn []
            (require 'futon5.xenotype.mermaid)
            (let [wiring->mermaid (resolve 'futon5.xenotype.mermaid/wiring->mermaid)
                  wiring (edn/read-string (slurp "resources/xenotype-wirings/prototype-001-creative-peng.edn"))
                  mmd (wiring->mermaid (:diagram wiring))]
              (and (.contains mmd "diversity")
                   (.contains mmd "threshold-sigil")))))))

(defn verify-invariant-7
  "Invariant 7: Xenotype Wirings Are CT-Valid

   Wirings must satisfy category theory laws (composition, identity)."
  []
  (println "\n=== INVARIANT 7: CT Validity ===")

  (and
   ;; Test 1: Wiring category can be constructed
   (check "Wiring category can be constructed"
          (fn []
            (let [summary (wiring-cat/category-summary)]
              (and (> (:object-count summary) 0)
                   (> (:morphism-count summary) 0)))))

   ;; Test 2: Identity laws hold
   (check "Identity laws: id_A ∘ f = f = f ∘ id_B"
          (fn []
            (let [result (wiring-cat/verify-identity-laws)]
              (:passed result))))

   ;; Test 3: Associativity holds
   (check "Associativity: (f ∘ g) ∘ h = f ∘ (g ∘ h)"
          (fn []
            (let [result (wiring-cat/verify-associativity)]
              (:passed result))))

   ;; Test 4: Prototype wirings have valid types
   (check "Prototype wirings have valid type edges"
          (fn []
            (let [wiring-dir (java.io.File. "resources/xenotype-wirings")
                  files (when (.exists wiring-dir)
                          (filter #(.endsWith (.getName %) ".edn")
                                  (.listFiles wiring-dir)))
                  results (map #(wiring-cat/validate-wiring-file (.getPath %)) files)]
              (every? :valid results))))))

(defn verify-invariant-8
  "Invariant 8: Scorer Wirings Are Explicit

   Scorers MUST be expressed as compositions of evaluation primitives,
   not hardcoded functions."
  []
  (println "\n=== INVARIANT 8: Scorer Wirings ===")

  (and
   ;; Test 1: Scorer components file exists
   (check "Scorer components file exists"
          (fn []
            (.exists (java.io.File. "resources/xenotype-scorer-components.edn"))))

   ;; Test 2: Scorer registry exists and has implementations
   (check "Scorer registry exists with implementations"
          (fn []
            (require 'futon5.xenotype.scorer)
            (let [registry @(resolve 'futon5.xenotype.scorer/scorer-registry)]
              (and (map? registry)
                   (> (count registry) 20)
                   (contains? registry :entropy-score)
                   (contains? registry :persistence)))))

   ;; Test 3: 4 prototype scorer wirings exist (one per energy)
   (check "4 prototype scorer wirings exist (one per energy)"
          (fn []
            (let [dir (java.io.File. "resources/xenotype-scorer-wirings")
                  files (when (.exists dir) (.listFiles dir))
                  edn-files (filter #(.endsWith (.getName %) ".edn") files)]
              (>= (count edn-files) 4))))

   ;; Test 4: EOC scorer is wiring-based (ji-eoc)
   (check "EOC scorer exists as primitive composition"
          (fn []
            (let [path "resources/xenotype-scorer-wirings/scorer-ji-eoc.edn"]
              (when (.exists (java.io.File. path))
                (let [wiring (edn/read-string (slurp path))]
                  (and (contains? wiring :diagram)
                       (seq (get-in wiring [:diagram :nodes]))
                       (seq (get-in wiring [:diagram :edges]))))))))

   ;; Test 5: Scorer wirings can be rendered as mermaid
   (check "Scorer wirings produce mermaid diagrams"
          (fn []
            (require 'futon5.xenotype.mermaid)
            (let [wiring->mermaid (resolve 'futon5.xenotype.mermaid/wiring->mermaid)
                  wiring (edn/read-string (slurp "resources/xenotype-scorer-wirings/scorer-peng-diversity.edn"))
                  mmd (wiring->mermaid (:diagram wiring))]
              (and (string? mmd)
                   (.contains mmd "graph")
                   (.contains mmd "-->")))))))

(defn run-all
  "Run all invariant checks. Returns true if all pass."
  []
  (println "\n" (apply str (repeat 60 "=")) "\n")
  (println "  EXOTYPE + XENOTYPE INVARIANT VERIFICATION")
  (println "  Mission-0 Part II")
  (println "  " (java.util.Date.))
  (println "\n" (apply str (repeat 60 "=")) "\n")

  (let [exotype-results [(verify-invariant-1)
                         (verify-invariant-2)
                         (verify-invariant-3)
                         (verify-invariant-4)]
        xenotype-results [(verify-invariant-5)
                          (verify-invariant-6)
                          (verify-invariant-7)
                          (verify-invariant-8)]
        exotype-pass (every? true? exotype-results)
        xenotype-pass (every? true? xenotype-results)
        all-pass (and exotype-pass xenotype-pass)]

    (println "\n" (apply str (repeat 60 "=")) "\n")
    (println "  EXOTYPE INVARIANTS: " (if exotype-pass "PASS" "FAIL"))
    (println "  XENOTYPE INVARIANTS: " (if xenotype-pass "PASS" "FAIL"))
    (println)
    (if exotype-pass
      (println "  Exotype system is ready for local physics runs.")
      (println "  Fix exotype issues before running compute."))
    (if xenotype-pass
      (println "  Xenotype wirings are defined and CT-valid.")
      (println "  Xenotype wirings need implementation (Part II work)."))
    (println "\n" (apply str (repeat 60 "=")) "\n")

    {:exotype-pass exotype-pass
     :xenotype-pass xenotype-pass
     :all-pass all-pass}))

(defn -main [& args]
  (let [result (run-all)
        mode (first args)]
    (cond
      ;; If --exotype-only, only check exotype invariants pass
      (= mode "--exotype-only")
      (System/exit (if (:exotype-pass result) 0 1))

      ;; If --strict, require all invariants (will fail until Part II done)
      (= mode "--strict")
      (System/exit (if (:all-pass result) 0 1))

      ;; Default: only require exotype invariants (Part I)
      :else
      (System/exit (if (:exotype-pass result) 0 1)))))
