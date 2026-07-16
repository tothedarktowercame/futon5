(ns propagator-compositions
  "COMPOSITIONS OF PROPAGATORS — pictures.

   Joe, 2026-07-17: 'we haven't yet tried compositions of the propagators... let's
   see if we can make some examples to see whether we get any interesting pictures.'

   What exotype_by_example.clj actually ran was switch(cond, propagator, NO-OP):
   `choose-n` returns 1 or 0 -- how many times to fire ONE sigma. This script
   switches WHICH sigma: switch(cond, sigmaA, sigmaB), both live.

   THE PREDICTION, from set arithmetic, made BEFORE the run:
     image(switch) = image(A) union image(B),  so  FREE(switch) = FREE(A) cap FREE(B).
   The clamped-shift family s_j(k) = max(k-j, 0) has |FREE(s_j)| = j and NESTED free
   sets, so it composes without losing its scaffold. Permutations (rot2) have
   FREE = {} and annihilate any scaffold they meet.
     switch(c, s_1, s_2) -> FREE {7}      switch(c, s_1, rot2) -> FREE {}
   s_1 IS the 2014 Emacs bug. Baldwin-as-built used rot2 -- a permutation -- so the
   measured self-annealing happened with NO scaffold at all.

   Arithmetic predicts WHICH POSITIONS CAN BE WRITTEN. It says nothing about what
   the composition DOES. That is what the pictures are for."
  (:require [futon5.ca.core :as ca]
            [futon5.xenotype.generator :as gen]
            [clojure.set :as set]))

(def rule-permute #'gen/rule-permute)
(def elisp-table ["000" "001" "010" "100" "011" "101" "110" "111"])
(defn nb [positional] (gen/positional-sigma->neighbourhood-sigma positional elisp-table))

;; --- the shape maps, as POSITIONAL sigmas ------------------------------------
(defn shift-j [j] (vec (for [k (range 8)] (max (- k j) 0))))
(def s1   (shift-j 1))            ; the 2014 Emacs bug.  FREE {7}
(def s2   (shift-j 2))            ; FREE {6 7}
(def s3   (shift-j 3))            ; FREE {5 6 7}
(def rot2 [2 3 4 5 6 7 0 1])      ; Baldwin's propagator: a PERMUTATION. FREE {}

(defn free-of [pos] (vec (sort (remove (set pos) (range 8)))))
(defn free-switch [a b] (vec (sort (set/intersection (set (free-of a)) (set (free-of b))))))

(def W 80) (def STEPS 120)
(defn apply-prop [sigil sigma n]
  (ca/sigil-for (reduce (fn [bits _] (rule-permute bits sigma)) (ca/bits-for (str sigil)) (range n))))
(defn boring? [phe i]
  (let [b (fn [j] (nth phe (mod j (count phe))))] (= (b (dec i)) (b i) (b (inc i)))))

;; --- the arms: each is switch(boring?, A, B) ---------------------------------
(def arms
  [{:id :s1-alone     :a s1   :b nil  :label "s_1 alone (the 2014 bug)"}
   {:id :rot2-alone   :a rot2 :b nil  :label "rot2 alone (permutation)"}
   {:id :baldwin-rot2 :a rot2 :b nil  :label "switch(bored, rot2, no-op) = Baldwin as built"}
   {:id :baldwin-s1   :a s1   :b nil  :label "switch(bored, s_1, no-op) = Baldwin WITH a scaffold"}
   {:id :s1-x-s2      :a s1   :b s2   :label "switch(bored, s_1, s_2) = TWO live propagators"}
   {:id :s1-x-rot2    :a s1   :b rot2 :label "switch(bored, s_1, rot2) = scaffold vs permutation"}])

(defn step [geno phe {:keys [a b id]}]
  (let [w (count geno)
        phe' (apply str (for [i (range w)]
                          (ca/evolve-digits-by-rule
                           (str (nth phe (mod (dec i) w))) (str (nth phe i))
                           (str (nth phe (mod (inc i) w))) (ca/bits-for (str (nth geno i))))))
        muts (atom 0)
        geno' (vec (for [i (range w)]
                     (let [bored (boring? phe i)
                           sigma (cond (= id :s1-alone) a
                                       (= id :rot2-alone) a
                                       bored a
                                       :else b)          ; nil b = no-op (hold)
                           g' (if sigma (apply-prop (nth geno i) (nb sigma) 1) (nth geno i))]
                       (when (not= g' (nth geno i)) (swap! muts inc))
                       g')))]
    {:geno geno' :phe phe' :mutations @muts}))

(defn run-arm [arm seed]
  (ca/with-seed seed
    (let [g0 (vec (ca/random-sigil-string W))
          p0 (apply str (repeatedly W #(if (< (ca/rnd) 0.5) "1" "0")))]
      (loop [geno g0 phe p0 t 0 G [g0] P [p0] rates []]
        (if (>= t STEPS)
          {:seed seed :genotype G :phenotype P :rates rates
           :diversity (count (distinct geno))
           :terminal (frequencies (map #(Integer/parseInt (ca/bits-for (str %)) 2) geno))}
          (let [{:keys [geno phe mutations]} (step geno phe arm)]
            (recur geno phe (inc t) (conj G geno) (conj P phe)
                   (conj rates (/ (double mutations) W)))))))))

;; --- render -----------------------------------------------------------------
(import '[java.awt Color Font RenderingHints] '[java.awt.image BufferedImage] '[javax.imageio ImageIO])

(def OUT "holes/labs/M-aif-tokamak/compositions")
(.mkdirs (java.io.File. OUT))

(defn grayscale [sigil]
  (let [v (Integer/parseInt (ca/bits-for (str sigil)) 2)] (Color. v v v)))
(defn bw [c] (if (= \1 c) Color/BLACK Color/WHITE))

(defn render! [arm runs]
  (let [scale 2 pw (* scale W) ph (* scale (inc STEPS))
        header 46 gap 24 footer 34
        img (BufferedImage. (* pw (count runs)) (+ header ph gap ph footer) BufferedImage/TYPE_INT_RGB)
        g (.createGraphics img)
        path (str OUT "/" (name (:id arm)) ".png")]
    (.setColor g Color/WHITE) (.fillRect g 0 0 (.getWidth img) (.getHeight img))
    (.setFont g (Font. Font/MONOSPACED Font/PLAIN 11))
    (.setRenderingHint g RenderingHints/KEY_ANTIALIASING RenderingHints/VALUE_ANTIALIAS_ON)
    (.setColor g Color/BLACK)
    (.drawString g (str (:label arm)) 4 14)
    (.drawString g (str "predicted FREE = " (if (:b arm) (free-switch (:a arm) (:b arm)) (free-of (:a arm)))) 4 28)
    (doseq [[col run] (map-indexed vector runs)]
      (let [x0 (* col pw)]
        (.setColor g Color/BLACK)
        (.drawString g (format "seed %d  GENOTYPE" (:seed run)) (+ x0 4) 42)
        (doseq [t (range (inc STEPS)) x (range W)]
          (.setColor g (grayscale (get-in run [:genotype t x])))
          (.fillRect g (+ x0 (* scale x)) (+ header (* scale t)) scale scale))
        (.setColor g Color/BLACK)
        (.drawString g "PHENOTYPE" (+ x0 4) (+ header ph 16))
        (doseq [t (range (inc STEPS)) x (range W)]
          (.setColor g (bw (nth (get-in run [:phenotype t]) x)))
          (.fillRect g (+ x0 (* scale x)) (+ header ph gap (* scale t)) scale scale))
        (.setColor g Color/BLACK)
        (.drawString g (format "diversity %d  mut-rate %.3f" (:diversity run)
                               (/ (reduce + (:rates run)) (count (:rates run))))
                     (+ x0 4) (+ header ph gap ph 14))))
    (.dispose g) (ImageIO/write img "png" (java.io.File. path))
    path))

(println "PREDICTED SCAFFOLDS (set arithmetic, registered BEFORE the run):")
(doseq [{:keys [id a b label]} arms]
  (println (format "  %-14s FREE = %-8s  %s" (name id) (if b (free-switch a b) (free-of a)) label)))
(println "\nRUNNING (3 seeds x 120 steps x width 80):")
(doseq [arm arms]
  (let [runs (mapv #(run-arm arm %) [1 2 3])
        path (render! arm runs)
        divs (mapv :diversity runs)
        rate (/ (reduce + (mapv #(/ (reduce + (:rates %)) (count (:rates %))) runs)) 3.0)]
    (println (format "  %-14s diversity %-14s mean mut-rate %.4f  -> %s"
                     (name (:id arm)) (str divs) rate path))))
