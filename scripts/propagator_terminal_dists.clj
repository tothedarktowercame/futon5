(ns propagator-terminal-dists
  "Extract each propagator's TERMINAL rule distribution from the census.

  WHY. The census artifact is 3 seeds x 121 generations x 256 rule COUNTS summing to
  60 -- i.e. each generation is a probability distribution over rule space, and a
  propagator is a TRAJECTORY IN THE 256-SIMPLEX. features.csv's 18 columns (entropy,
  top1, class4, ...) are lossy scalar summaries of those distributions, and we
  clustered them under EUCLIDEAN distance. So the 'no natural joints' result
  (silhouette monotone-decreasing 0.441 -> 0.267, k=2 weak) is CONDITIONAL on that
  feature choice and that geometry. This dumps the distributions themselves so the
  question can be re-asked under the information metric (Fisher-Rao), where the
  simplex's own geometry decides rather than my choice of summary statistic.

  WHAT IT WRITES (little-endian float64, for numpy):
    data/propagator-metric/terminal-dists.f64  20256 x 256 row-major, each row the
                                               pooled terminal rule counts over the 3
                                               seeds (sums to 180 = 3 x width 60)
    data/propagator-metric/sigmas.txt          one sigma per line, SAME ORDER as rows
  Pooling the 3 seeds is a legitimate pooled estimate of the terminal rule population;
  it is stated here because it is a choice, not a law.

  Not a fingerprint input, so it cannot disturb an index build."
  (:require [clojure.edn :as edn] [clojure.java.io :as io] [clojure.string :as str]))

(def fingerprint "ac2ff1681eae5b85")
(def artifact-dir (io/file "data/propagator-index/artifacts" fingerprint))
(def out-dir (io/file "data/propagator-metric"))

(defn terminal-counts
  "Pooled terminal rule counts (256 longs) across this artifact's seeds."
  [file]
  (with-open [in (java.util.zip.GZIPInputStream. (io/input-stream file))]
    (let [m (edn/read-string (slurp in))
          rows (map (fn [run] (last (:census run))) (:runs m))]
      {:sigma (apply str (:sigma m))
       :counts (apply mapv + rows)})))

(defn -main [& _]
  (.mkdirs out-dir)
  (let [files (->> (.listFiles artifact-dir)
                   (filter #(str/ends-with? (.getName %) ".edn.gz"))
                   (sort-by #(.getName %)) vec)
        n (count files)
        done (java.util.concurrent.atomic.AtomicInteger. 0)
        _ (println "artifacts:" n)
        rows (vec (pmap (fn [f]
                          (let [r (terminal-counts f)
                                k (.incrementAndGet done)]
                            (when (zero? (mod k 2000)) (println "  " k "/" n) (flush))
                            r))
                        files))]
    ;; sanity: every row must sum to 180 (3 seeds x width 60). A silent short row
    ;; would corrupt the geometry, so fail loudly rather than normalise it away.
    (let [bad (remove #(= 180 (reduce + (:counts %))) rows)]
      (when (seq bad)
        (throw (ex-info "terminal counts do not sum to 180" {:n (count bad)
                                                             :first (first bad)}))))
    (with-open [os (java.io.DataOutputStream.
                    (java.io.BufferedOutputStream. (io/output-stream (io/file out-dir "terminal-dists.f64"))))]
      (doseq [r rows, c (:counts r)]
        ;; little-endian float64 for numpy
        (.writeLong os (Long/reverseBytes (Double/doubleToLongBits (double c))))))
    (spit (io/file out-dir "sigmas.txt") (str/join "\n" (map :sigma rows)))
    (println "wrote" (count rows) "x 256 ->" (str (io/file out-dir "terminal-dists.f64")))
    (flush)))

(-main)
