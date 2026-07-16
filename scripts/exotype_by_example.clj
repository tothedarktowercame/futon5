(ns exotype-by-example
  "A compositional exotype, by example (M-propagators / futon5 geno->exo layering).

  THE QUESTION (Joe, 2026-07-16). If propagators are a basis for the global physics
  of a MetaCA, then named physics like 'baldwin' or 'blend' should THEMSELVES be
  compositions of propagators. An EXOTYPE is such a composition: choose propagator A
  under one local condition, propagator B under another. A XENOTYPE is where exotypes
  get evolved under outside selection pressure (e.g. ants). This script builds the
  simplest non-trivial exotype and MEASURES whether conditional composition yields a
  genuinely new operator -- one no single propagator reproduces.

  THE LAYERS, made concrete:
    GENOTYPE  a cell's 8-bit rule sigil.
    a PROPAGATOR (level below the exotype) rewrites that rule:
        rule-permute: pick a neighbourhood at random, copy the INVERSE of its
        response into another neighbourhood's response. sigma = identity is a random
        walk (no attractor); a non-trivial sigma COUPLES responses and has a fixed
        point that selects the rule. (We reuse the exact private fn, not a copy.)
    an EXOTYPE  = switch(local-condition, propagatorA, propagatorB). This script's
        object of study.
    (XENOTYPE = evolving the switch itself; out of scope here, but the switch is
        written so its condition/branches are data, i.e. evolvable.)

  THE SHARP TEST -- is 'baldwin' a composition? Baldwin's rule (measured live in
  H-baldwin-repro) is 'bored -> mutate, interesting -> hold'. That is EXACTLY
  switch(local-condition, a-propagator, no-op). So we instantiate a baldwin-FLAVOURED
  exotype and ask two things with numbers:
    (Q1) Does the exotype's per-step mutation rate TRACK local state -- high when the
         field is boring, falling as structure emerges -- where the constant policies
         cannot (explore-always is pinned near 1, hold-always at 0)? A state-dependent
         churn rate is the signature of a genuinely new operator.
    (Q2) Is the exotype's genotype-diversity trajectory REACHABLE by neither
         constituent (explore-always collapses or churns; hold-always freezes)? If the
         exotype sits where neither constant policy can, the exo layer is real, not
         nominal relabelling of a geno-level knob.

  HONEST SCOPE. This does not claim the 2014 baldwin fn IS this exotype bit-for-bit
  (baldwin mutates (1- context-matches) times, a graded count, not a binary switch).
  It claims baldwin's STRUCTURE -- conditional propagator application -- is
  reconstructible from propagator + condition, and measures what that buys."
  (:require [futon5.ca.core :as ca]
            [futon5.xenotype.generator :as gen]))

;; the exact private propagator, reused (no reimplementation -> no divergence)
(def rule-permute #'gen/rule-permute)
(def elisp-table ["000" "001" "010" "100" "011" "101" "110" "111"])
(defn nb [positional] (gen/positional-sigma->neighbourhood-sigma positional elisp-table))
(def explore (nb [2 3 4 5 6 7 0 1]))   ; rotate+2: measured live, ~31 rules, structured

(def W 80) (def STEPS 120)

;; ---- one propagator application to a cell's rule, n times ----
(defn apply-prop [rule-sigil sigma n]
  (ca/sigil-for (reduce (fn [bits _] (rule-permute bits sigma)) (ca/bits-for (str rule-sigil)) (range n))))

;; ---- the local condition: is this cell's phenotype neighbourhood BORING? ----
;; boring = the 3 phenotype bits [prev self next] are all equal (no local activity).
;; This is the switch's discriminator -- baldwin counts context matches; we threshold.
(defn boring? [phe i]
  (let [b (fn [j] (nth phe (mod j (count phe))))]
    (= (b (dec i)) (b i) (b (inc i)))))

;; ---- the three arms, as ONE parameterised policy ----
;; :explore  always fire the propagator            (the constituent, unconditional)
;; :hold     never fire it                          (the other constituent = frozen geno)
;; :exotype  fire ONLY where boring  (baldwin-flavoured switch: bored->mutate, else hold)
(defn choose-n [policy phe i]
  (case policy
    :explore 1
    :hold    0
    :exotype (if (boring? phe i) 1 0)))

(defn step [geno phe policy]
  (let [w (count geno)
        ;; phenotype evolves under each cell's genotype rule (MetaCA co-evolution)
        phe' (apply str (for [i (range w)]
                          (ca/evolve-digits-by-rule
                           (str (nth phe (mod (dec i) w))) (str (nth phe i))
                           (str (nth phe (mod (inc i) w))) (ca/bits-for (str (nth geno i))))))
        ;; genotype evolves under the policy; count how many cells actually mutated
        mutations (atom 0)
        geno' (vec (for [i (range w)]
                     (let [n (choose-n policy phe i)
                           g' (apply-prop (nth geno i) explore n)]
                       (when (and (pos? n) (not= g' (nth geno i))) (swap! mutations inc))
                       g')))]
    {:geno geno' :phe phe' :mutations @mutations}))

(defn run-arm [policy seed]
  (ca/with-seed seed
    (let [geno0 (vec (ca/random-sigil-string W))
          phe0 (apply str (repeatedly W #(if (< (ca/rnd) 0.5) "1" "0")))]
      (loop [geno geno0 phe phe0 t 0 diversity [] mut-rate [] phe-act []]
        (if (>= t STEPS)
          {:policy policy :diversity diversity :mut-rate mut-rate :phe-activity phe-act}
          (let [{:keys [geno phe mutations]} (step geno phe policy)
                prev-phe (if (empty? phe-act) phe phe)   ; activity vs previous handled below
                d (count (distinct (map #(ca/bits-for (str %)) geno)))]
            (recur geno phe (inc t)
                   (conj diversity d)
                   (conj mut-rate (/ (double mutations) W))
                   (conj phe-act phe))))))))

(defn phe-activity-series [phe-rows]
  ;; fraction of cells whose phenotype bit changed, per step
  (mapv (fn [a b] (/ (double (count (filter true? (map not= a b)))) (count a)))
        phe-rows (rest phe-rows)))

(defn mean [xs] (if (seq xs) (/ (reduce + xs) (double (count xs))) 0.0))

(println "=== A COMPOSITIONAL EXOTYPE, BY EXAMPLE ===")
(println (format "width %d, %d steps, propagator = rotate+2 (measured live)\n" W STEPS))
(def results (doall (for [p [:explore :hold :exotype]] (run-arm p 0))))

(println (format "%-10s %-28s %-24s %s" "policy" "mut-rate (per step)" "genotype diversity" "phe activity"))
(doseq [{:keys [policy diversity mut-rate phe-activity]} results]
  (let [act (phe-activity-series phe-activity)]
    (println (format "%-10s start %.3f  end %.3f  mean %.3f    start %2d  end %2d      mean %.3f"
                     (name policy) (first mut-rate) (last mut-rate) (mean mut-rate)
                     (first diversity) (last diversity) (mean act)))))

;; Q1: does the exotype's mutation rate TRACK state (fall as structure emerges)?
(let [ex (first (filter #(= (:policy %) :exotype) results))
      mr (:mut-rate ex)
      first-third (mean (take (quot STEPS 3) mr))
      last-third (mean (take-last (quot STEPS 3) mr))]
  (println (format "\nQ1  exotype mutation rate: first-third %.3f -> last-third %.3f  (%s)"
                   first-third last-third
                   (if (< last-third (* 0.75 first-third)) "FALLS as structure emerges -- state-tracking"
                       "does not clearly fall"))))

;; Q2: is the exotype's diversity trajectory reachable by neither constituent?
(let [by (into {} (map (juxt :policy identity) results))
      ex (mean (:diversity (:exotype by)))
      exl (last (:diversity (:exotype by)))
      exp (mean (:diversity (:explore by)))
      hol (mean (:diversity (:hold by)))]
  (println (format "Q2  mean diversity: explore %.1f | hold %.1f | exotype %.1f (end %d)"
                   exp hol ex exl))
  (println (format "    exotype strictly between the constituents? %s"
                   (boolean (or (< hol ex exp) (< exp ex hol))))))

(require '[clojure.data.json :as json])
(spit "/tmp/exotype.json" (json/write-str results))
(println "\nwrote /tmp/exotype.json (per-step series for all three arms)")
