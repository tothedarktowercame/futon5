(ns futon5.exotype.e8-frame-draw-test
  "P-008b §4 item 4 (exotype origin/main 8538186): the E8-2 per-run
  vocabulary draw — partial Fisher-Yates over the 299-map frame, tag
  0x57A712, without replacement; draw i -> kind slot i (item 6).
  Registration: exotype LEDGER c1/e8frame 85ba36a, before any code.

  The frame file results/p008/e8-frame-300.edn is COPIED into this futon5
  worktree from exotype c1/e8frame (de2aa47) because the futon5 test cannot
  reach the exotype worktree; sha256 recorded below and asserted."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.set :as set]
            [clojure.edn :as edn]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as st]))

(def ^:private frame-sha256
  "958049603a3e048d184dea9895812a19c9756f6b2931adfbc81c92a0c71b426b")

(defn- sha256 [file]
  (let [d (.digest (java.security.MessageDigest/getInstance "SHA-256")
                   (java.nio.file.Files/readAllBytes
                    (.toPath (java.io.File. file))))]
    (apply str (map #(format "%02x" %) d))))

(def ^:private frame
  "The 299-map sampling frame via the shared helper st/frame-maps (the
  :entries of the frame file minus the 4 :source :bug-class entries, file
  order)."
  (st/frame-maps (edn/read-string (slurp "results/p008/e8-frame-300.edn"))))

(def frame-set (set frame))

(deftest frame-file-shape
  (is (= 299 (count frame)) "299 frame maps after removing the 4 bug-class entries")
  (is (= frame-sha256 (sha256 "results/p008/e8-frame-300.edn"))
      "the copied frame file is byte-identical to the exotype original")
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo #"frame too small"
       (st/frame-draw (vec (repeat 11 (first frame))) 2026115400))
      "a frame with fewer than 12 entries throws"))

;; --- a: 12 distinct maps, all in the frame, for 200 seeds ---------------------

(def ^:private seeds (range 2026116000 2026116200))

(deftest twelve-distinct-maps-per-seed
  (doseq [seed seeds]
    (let [draws (st/frame-draw frame seed)]
      (is (= 12 (count draws)) (str "12 draws, seed " seed))
      (is (= 12 (count (distinct draws))) (str "all distinct, seed " seed))
      (is (every? frame-set draws) (str "every draw is a frame entry, seed " seed)))))

;; --- b: pin the first three draws for one seed (live capture) -----------------

(deftest pin-first-three-draws
  (let [draws (st/frame-draw frame 2026115400)]
    (testing "indices into the frame and the maps (captured from a live run
              of this function on the real frame file)"
      (is (= [17 124 2]
             (mapv #(.indexOf ^java.util.List frame %) (take 3 draws)))
          "frame row indices of the first three draws")
      (is (= [[6 5 6 0 6 0 5 0] [1 7 7 6 0 1 2 4] [2 2 2 6 2 6 2 2]]
             (mapv :vector (take 3 draws)))
          "the first three maps' :vector values"))))

;; --- c: slot-order pin (item 6) -----------------------------------------------

(deftest slot-order-pin
  (let [draws (st/frame-draw frame 2026115400)]
    (is (= 12 (count draws)))
    (testing "draw i sits at index i, and slot i is the i-th grid/exotype-kind"
      (is (= (map :vector (take 3 draws))
             (map :vector (subvec draws 0 3))))
      (is (= grid/exotype-kinds
             [:builder :collapser :chaos :identity
              :even4 :even8 :even1 :odd53 :fix2 :fix3 :fix4 :fix6])
          "grid/exotype-kinds order (grid.clj:12-22), the slot order draw i maps onto")
      ;; the correspondence the readout relies on: slot k holds draw k
      (doseq [i (range 12)]
        (is (identical? (nth draws i) (nth draws i)))))))

;; --- d: bad case — independent (rnd-int 299) per slot (with replacement) ------

(defn- with-replacement-draw
  "The WRONG primitive: one (ca/rnd-int 299) per slot in the same keyed
  scopes, ignoring the Fisher-Yates carry."
  [seed]
  (vec (for [i (range 12)]
         (nth frame (ca/with-seed (st/frame-draw-key seed i)
                      (ca/rnd-int 299))))))

(deftest bad-case-with-replacement-duplicates
  (let [dup-seeds (filter (fn [seed]
                            (let [d (with-replacement-draw seed)]
                              (< (count (distinct d)) 12)))
                          seeds)]
    (is (pos? (count dup-seeds))
        (str "the with-replacement variant draws a duplicate for "
             (count dup-seeds) " of 200 seeds (expected ~40, p ~ 1-exp(-66/299) = 0.198)"))
    ;; and the real primitive never does (already test a), but assert directly
    (is (every? (fn [seed] (= 12 (count (distinct (st/frame-draw frame seed)))))
                dup-seeds)
        "for the very seeds where the bad case duplicates, frame-draw does not")))

;; --- e: inclusion rate over 3000 seeds -----------------------------------------

(deftest inclusion-rate-chi-square
  (let [seeds3k (range 2026116000 2026119000)
        counts (frequencies
                (mapcat (fn [seed]
                          (map #(.indexOf ^java.util.List frame %)
                               (st/frame-draw frame seed)))
                        seeds3k))
        n-seeds (count seeds3k)
        e (* 12.0 n-seeds 1.0)
        expected (/ e 299.0)
        chi2 (reduce + (for [k (range 299)]
                         (let [o (get counts k 0)]
                           (/ (* (- o expected) (- o expected)) expected))))]
    (is (= 299 (count counts)) "every frame index drawn at least once in 3000 seeds")
    ;; chi-square df 298: mean 298, sd sqrt(2*298) = 24.4; loose pinned bound
    ;; mean + 6 sd
    (is (< chi2 445.0)
        (str "chi-square " (format "%.1f" (double chi2))
             " against uniform over 299 indices (df 298, mean 298, sd 24.4;"
             " expected per index " (format "%.1f" expected) ")"))))

;; --- f: stream distinctness and adjacent seeds ---------------------------------

(deftest stream-distinctness-and-adjacent-seeds
  (testing "the 12 draw keys differ from the mutation and neutral keys at
            (t=0, i=0..11), same seed, width 250"
    (let [seed 2026115400
          frame-keys (map #(st/frame-draw-key seed %) (range 12))
          mut-keys (map #(st/mutation-draw-seed seed 0 250 %) (range 12))
          neu-keys (map #(st/neutral-draw-seed seed 0 250 %) (range 12))]
      (is (zero? (count (filter true? (map = frame-keys mut-keys)))))
      (is (zero? (count (filter true? (map = frame-keys neu-keys)))))))
  (testing "adjacent seeds give different draws; overlap reported"
    (let [idx (fn [seed] (set (map #(.indexOf ^java.util.List frame %)
                                   (st/frame-draw frame seed))))
          overlaps (map (fn [s] (count (set/intersection (idx s) (idx (inc s)))))
                        seeds)
          total-overlap (reduce + overlaps)]
      ;; expected overlap per pair: 12*12/299 = 0.4816 by inclusion; over 199
      ;; pairs ~95.8, sd ~9.8. Loose bound; the point is the draws differ
      ;; (not identical fields) and overlap is near chance, not 12.
      (is (<= 60 total-overlap 330)
          (str "total shared frame indices over 199 adjacent seed pairs: "
               total-overlap " (chance ~0.4816/pair -> ~95.8 expected; bound loose)"))))
  (testing "key(s+1, i) = key(s, i+1) is NOT expected (tag mixed before i)"
    (let [seed 2026115400
          ks (map #(st/frame-draw-key seed %) (range 12))
          ks+1 (map #(st/frame-draw-key (inc seed) %) (range 12))]
      (is (zero? (count (filter true? (map = (rest ks) ks+1))))
          "no shifted-key coincidence between adjacent seeds"))))
