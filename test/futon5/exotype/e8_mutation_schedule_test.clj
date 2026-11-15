(ns futon5.exotype.e8-mutation-schedule-test
  "P-008b §7 item 5, step 5b-A: the mutation hit schedule (no operators yet).

  Rulings 2026-11-15 (exotype 557c393): mutation stream keyed
  mix-seed(mix-seed(seed XOR tag) + t*width + i); one (ca/rnd-int 10000) per
  cell per step, always drawn when mutation is on; hit iff draw <
  round(10000*mu); mutation off draws nothing.

  Tests a and b load the SHEET engine (scripts/local_compressibility_grid.clj)
  the way scripts/p008/s8smoke.clj does, so they must run with the worktree
  root as the working directory. Test c/d/e call mutation-hit? / the keyed
  draws directly over a 250 x 3000 grid -- no engine stepping."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as st]))

(defn- load-engine!
  "Load the July SHEET engine into this JVM (s8smoke.clj's method), and
  install the pooled S8 predictor the way scripts/p008/g1b_one.clj does
  (an S8 vector is scored as its 12-kind image through the conditional
  model; vectors outside the 12 kinds throw, as in the G1 protocol)."
  []
  (let [src (slurp "scripts/local_compressibility_grid.clj")]
    (load-string (str/replace src "(apply -main *command-line-args*)" "")))
  (let [s8->kind (into {} (for [k grid/exotype-kinds]
                             [(grid/kind->s8 k) k]))
        predict @(ns-resolve (find-ns 'futon5.exotype.efe) 'predict)
        predictor-atom @(ns-resolve (find-ns 'futon5.exotype.efe) 's8-predictor)]
    (reset! predictor-atom
            (fn [s8-exotype _observation]
              (let [kind (or (get s8->kind s8-exotype)
                             (throw (ex-info "s8 vector is not one of the 12 kinds"
                                             {:exotype s8-exotype})))]
                (select-keys (predict kind _observation :derived)
                             [:activity :diversity :hunger])))))
  nil)

(defn- sha [s]
  (let [d (.digest (java.security.MessageDigest/getInstance "SHA-256")
                   (.getBytes ^String s "UTF-8"))]
    (apply str (map #(format "%02x" %) (take 8 d)))))

(def ^:private recorded-g1 "957e93e21609fb79")

(defn- kw-s8-hashes [metaca-state kind->s8 s8->kind step T]
  (letfn [(run [st0]
            (nth (iterate step st0) T))
          (h [final s8?]
            (sha (pr-str [(vec (map str (:genotype final)))
                          (str (apply str (:phenotype final)))
                          (vec (if s8? (mapv s8->kind (:exotypes final))
                                   (:exotypes final)))])))]
    (let [kw (run (metaca-state 10.0 0.1 2026102000))
          s8 (run (update (metaca-state 10.0 0.1 2026102000)
                          :exotypes #(mapv kind->s8 %)))]
      {:kw (h kw false) :s8 (h s8 true)})))

(deftest off-is-byte-identical-g1
  (load-engine!)
  (let [ns* (find-ns 'local-compressibility-grid)
        metaca-state @(ns-resolve ns* 'metaca-state)
        step @(ns-resolve ns* 'checked-metaca-step)
        kind->s8 grid/kind->s8
        s8->kind (into {} (for [k grid/exotype-kinds] [(kind->s8 k) k]))
        base (kw-s8-hashes metaca-state kind->s8 s8->kind step 1500)
        nil-run (kw-s8-hashes (fn [b k s] (assoc (metaca-state b k s) :mutation nil))
                              kind->s8 s8->kind step 1500)]
    (testing ":mutation absent: the G1 hash is the recorded one"
      (is (= recorded-g1 (:s8 base)))
      (is (= (:kw base) (:s8 base)) "kw and s8 arms still match"))
    (testing ":mutation nil: same hashes, no schedule"
      (is (= base nil-run)))))

(deftest hit-schedule-unchanged-from-step-5b-a
  (testing "step 5b-B's rate draw is step 5b-A's hit schedule exactly (first 200 steps)"
    (let [seed 2026102600 mu 0.001 w 250 T 200
          state (fn [t] {:seed seed :time t :mutation :s8-transposition
                         :mu mu :exotypes (vec (repeat w :identity))})
          ;; the NEW schedule: truthy mutation-draw (a keyword winner is
          ;; skipped, but the rate draw still decides hit vs no hit)
          new-schedule (into #{}
                             (for [t (range T) i (range w)
                                   :when (st/mutation-draw (state t) i :identity)]
                               [t i]))
          ;; part A's schedule, computed directly: ONE (ca/rnd-int 10000)
          ;; under the same key
          old-schedule (into #{}
                             (for [t (range T) i (range w)
                                   :when (ca/with-seed
                                           (st/mutation-draw-seed seed t w i)
                                           (< (ca/rnd-int 10000) 10))]
                               [t i]))]
      (is (= old-schedule new-schedule))
      (is (<= 30 (count new-schedule) 70)
          (str "expected ~50 hits (mean 50, sd 7) in 200x250 at mu 1e-3; got "
               (count new-schedule))))))

;; --- the schedule itself, computed directly (no engine stepping) -----------

(def ^:private width 250)
(def ^:private T 3000)

(defn- hit-set
  "Set of [t i] hits for SEED at rate MU, computed with mutation-draw on a
  minimal state (the rate draw depends only on seed/time/width/index/mu).
  The winner is a keyword, so a hit yields the :skipped-keyword record --
  truthy exactly when the rate draw hits."
  [seed mu]
  (into #{}
        (for [t (range T)
              :let [state {:seed seed :time t :mutation :s8-transposition
                           :mu mu :exotypes (vec (repeat width :identity))}]
              i (range width)
              :when (st/mutation-draw state i :identity)]
          [t i])))

(deftest rate-within-binomial-interval
  (testing "mu 1e-3: mean 750, sd 27.4, 99.9% interval [660, 840]"
    (let [n (count (hit-set 2026102600 0.001))]
      (is (<= 660 n 840) (str "hits " n))))
  (testing "mu 1e-4: mean 75, sd 8.66, 99.9% interval [47, 103]"
    (let [n (count (hit-set 2026102600 0.0001))]
      (is (<= 47 n 103) (str "hits " n)))))

(defn- shifted-overlap
  "Fraction of SEED-A's hits (t, i) for which seed B has a hit at (t, i-1)
  or (t, i)."
  [a b]
  (let [sa (hit-set a 0.001)
        sb (hit-set b 0.001)]
    (/ (count (filter (fn [[t i]] (or (sb [t (dec i)]) (sb [t i]))) sa))
       (double (count sa)))))

(defn- per-step-counts [seed]
  (let [hits (hit-set seed 0.001)]
    (mapv #(count (filter (fn [[t _]] (= t %)) hits)) (range T))))

(defn- correlation [xs ys]
  (let [n (count xs)
        mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
        dx (mapv #(- % mx) xs) dy (mapv #(- % my) ys)
        sxy (reduce + (map * dx dy))
        sxx (reduce + (map * dx dx)) syy (reduce + (map * dy dy))]
    (if (or (zero? sxx) (zero? syy))
      0.0
      (/ sxy (Math/sqrt (* sxx syy))))))

(defn- adjacent-seed-check [a b]
  (let [overlap (shifted-overlap a b)
        r (correlation (per-step-counts a) (per-step-counts b))
        ;; SE of a correlation over T=3000 points ~ 1/sqrt(3000) = 0.0183
        se 0.0183]
    {:overlap overlap :r r :within-4se (< (Math/abs r) (* 4 se))}))

(deftest adjacent-seeds-are-unrelated
  (doseq [[a b] [[2026102600 2026102601] [2026102600 2026102850]]]
    (let [{:keys [overlap r within-4se]} (adjacent-seed-check a b)]
      (testing (str "seeds " a " and " b)
        (is (< overlap 0.02) (str "shifted overlap " overlap))
        (is within-4se (str "per-step count correlation " r " outside 4 SE"))
        (is (not= (hit-set a 0.001) (hit-set b 0.001)) "hit sets differ")))))

(deftest wrong-keying-fails-the-overlap-criterion
  (testing "seed + time*width + index (test-local) repeats the schedule shifted"
    (let [bad-key (fn [seed t w i]
                    (ca/mix-seed (unchecked-add (long seed)
                                                (unchecked-add
                                                 (unchecked-multiply (long t) (long w))
                                                 (long i)))))
          hits (fn [seed]
                 (into #{}
                       (for [t (range T) i (range width)
                             :when (ca/with-seed (bad-key seed t width i)
                                     (< (ca/rnd-int 10000) 10))]
                         [t i])))
          sa (hits 2026102600) sb (hits 2026102601)
          overlap (/ (count (filter (fn [[t i]] (or (sb [t (dec i)]) (sb [t i]))) sa))
                     (double (count sa)))]
      ;; under the bad keying, seed s+1 at (t, i) repeats seed s at (t, i+1):
      ;; every hit of s should appear in s+1 shifted by one cell, so the
      ;; (t, i-1) overlap is near 1, failing the < 0.02 criterion.
      (is (> overlap 0.95) (str "bad-keying overlap " overlap))
      (is (>= overlap 0.02) "the bad keying must FAIL the criterion"))))

(deftest non-integral-threshold-throws
  (testing "mu 5e-5: 10000*mu = 0.5 is not integral"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #"10000\*mu"
         (st/mutation-draw {:seed 1 :time 0 :mutation :e8-point :mu 5e-5
                            :exotypes (vec (repeat 8 :identity))} 0 :identity))))
  (testing ":mutation set but :mu missing throws"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #":mu is missing"
         (st/mutation-draw {:seed 1 :time 0 :mutation :e8-point
                            :exotypes (vec (repeat 8 :identity))} 0 :identity)))))
