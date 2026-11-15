;; P-008 initial-draw frequency check (claude-1 packet 2026-11-15, discovery
;; only, SHEET reference). No engine steps, no fitting: only the initial kind
;; draw of tools/calib_derive.clj (branch c1/calib-pb-anchor, lines 81-84 and
;; :106) is reproduced:
;;   (ca/with-seed seed (vec (repeatedly 60 #(ca/rnd-nth vocab))))
;; with (a) the 14-entry literal vocabulary and (b) a 15-entry vocabulary
;; (the 14 plus one target entry; only the count 15 matters for index
;; statistics). ca/rnd-nth over a vector of size n is java.util.Random
;; .nextInt(n) (futon5 src/futon5/ca/core.clj:105-110).
;;
;; NOTE: calib_derive.clj has no separate "genotype-kind" vector; the initial
;; genotype there is a sigil string (random-sigil-string, nextInt over the
;; 256 sigil entries), not a kind draw. The kind counts below are for the
;; initial EXOTYPE vector, which is the only draw whose vocabulary size is
;; 14 vs 15.
;;
;; Seeds: consecutive 2026104000-014 (item 1-3) and non-consecutive
;; 2026104000 + 7919*j, j = 0..14 (item 4).
(require '[futon5.ca.core :as ca])

(def width 60)
(def vocab14
  "calib_derive.clj july-vocab-literal, verbatim."
  [:identity :even4 :builder :odd53 :even44 :fix6 :even1 :chaos :even8 :fix4
   :odd332 :collapser :fix3 :fix2])
(def vocab15 (conj vocab14 :target-15th))

(defn draw
  "Initial kind-index vector exactly as calib_derive does it; returns indices."
  [n seed]
  (ca/with-seed seed
    (vec (repeatedly width #(.nextInt ^java.util.Random ca/*rng* (int n))))))

(defn counts [indices n]
  (vec (for [i (range n)] (count (filter #{i} indices)))))

(defn mean [xs] (double (/ (reduce + xs) (count xs))))
(defn sample-sd [xs]
  (let [m (mean xs) k (count xs)]
    (Math/sqrt (/ (reduce + (map #(let [d (- % m)] (* d d)) xs)) (dec k)))))

(defn stats-table [label seeds n]
  (let [vocab (if (= n 14) vocab14 vocab15)
        per-seed (mapv #(draw n %) seeds)
        per-seed-counts (mapv #(counts % n) per-seed)
        expected (double (/ width n))
        binom-sd (Math/sqrt (* width (/ 1.0 n) (- 1 (/ 1.0 n))))
        binom-se (/ binom-sd (Math/sqrt (count seeds)))]
    (println)
    (println "=== " label " vocab-size" n "seeds" (first seeds) "..."
             (last seeds) "===")
    (println "expected count per kind =" expected
             " binomial sd (per seed) =" (format "%.3f" binom-sd)
             " binomial SE of 15-seed mean =" (format "%.3f" binom-se))
    (println "seed      " (apply str (for [i (range n)] (format "%5d" i))))
    (doseq [[s cs] (map vector seeds per-seed-counts)]
      (println (format "%9d " s) (apply str (for [c cs] (format "%5d" c)))))
    (println "kind          mean    exp  between-seed-sd  binomial-sd  |z| of mean (mean-exp)/SE")
    (let [zs (vec (for [i (range n)]
                    (let [cs (mapv #(nth % i) per-seed-counts)
                          m (mean cs)]
                      [i m (sample-sd cs)])))]
      (doseq [[i m sd] zs]
        (println (format "%4d %9.3f %6.2f %16.3f %12.3f %12.3f"
                         i m (double expected) sd binom-sd
                         (/ (- m expected) binom-se))))
      {:label label :n n :zs zs :binom-sd binom-sd :expected expected
       :binom-se binom-se})))

(defn index-matrix [label seeds n]
  (println)
    (println "--- first 10 cells, kind index per seed (" label ", vocab" n ") ---")
  (println "seed      " (apply str (for [c (range 10)] (format "%4d" c))))
  (doseq [s seeds]
    (let [v (draw n s)]
      (println (format "%9d " s) (apply str (for [c (range 10)] (format "%4d" (nth v c))))))))

(def seeds-consec (range 2026104000 2026104015))
(def seeds-spread (for [j (range 15)] (+ 2026104000 (* 7919 j))))

;; items 2 (consecutive) and 4 (non-consecutive): per-seed counts + stats
(def stats [(stats-table "consecutive" seeds-consec 14)
            (stats-table "consecutive" seeds-consec 15)
            (stats-table "spread" seeds-spread 14)
            (stats-table "spread" seeds-spread 15)])

;; items 3 and 4: index matrices
(index-matrix "consecutive" seeds-consec 14)
(index-matrix "consecutive" seeds-consec 15)
(index-matrix "spread" seeds-spread 14)
(index-matrix "spread" seeds-spread 15)

;; item 5: one line per construction
(doseq [{:keys [label n zs binom-sd expected binom-se]} stats]
  (let [exceed (filter (fn [[i m sd]] (> (Math/abs (double (/ (- m expected) binom-se))) 3)) zs)
        below (filter (fn [[i m sd]] (< sd binom-sd)) zs)]
    (println)
    (println "ITEM5" label "vocab" n ":"
             "kinds with |mean-exp| > 3 binomial SE:"
             (if (seq exceed) (mapv first exceed) "none")
             "; between-seed sd below binomial sd for"
             (count below) "of" n "kinds")))

(shutdown-agents)
