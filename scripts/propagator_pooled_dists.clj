(ns propagator-pooled-dists
  "Pool each propagator's rule distribution over the LAST K generations, not just the
   terminal row. (M-propagators 2b — testing the sparsity hypothesis.)

  WHY. Fisher-Rao on the terminal distributions came back FLAT (0.07-0.08, barely above a
  structureless blob), and I attributed that to SATURATION: the terminal row is 3 seeds x
  width 60 = 180 samples spread over 256 rule bins, so any two propagators overlap barely
  by chance and nearly every pair looks orthogonal (mean pairwise FR 2.1-2.5 of a maximum
  pi). That is a claim about SAMPLING, not about the space -- and it is cheaply testable
  with data already on disk: pool the last K generations and the same distributions get
  K times denser at zero extra simulation.

    terminal only : 3 x 60          =   180 samples / 256 bins   (sparse)
    last 20 gens  : 3 x 20 x 60     = 3,600 samples / 256 bins   (20x denser)

  If FR stops saturating, sparsity was the cause and the no-joints negative was partly an
  artifact of my choice of 'the point'. If it stays flat, the negative is about the space
  and a wider-grid JAX census would be an expensive way to confirm what we already know.

  STATIONARITY CAVEAT, stated not hidden: pooling over time assumes the last K
  generations sample one distribution. The live regimes still churn at the horizon
  (terminal-rule-flux ~0.6), so this is a TIME-AVERAGED distribution, not a snapshot of a
  settled one. That is arguably the better object anyway -- but it is a different object,
  and it is the reason this is a separate script rather than a tweak.

  Writes data/propagator-metric/pooled-dists-K<k>.f64  (20256 x 256, little-endian f64)"
  (:require [clojure.edn :as edn] [clojure.java.io :as io] [clojure.string :as str]))

(def fingerprint "ac2ff1681eae5b85")
(def artifact-dir (io/file "data/propagator-index/artifacts" fingerprint))
(def out-dir (io/file "data/propagator-metric"))

(defn pooled-counts
  "Sum the census rows over the last K generations, across all seeds."
  [file k]
  (with-open [in (java.util.zip.GZIPInputStream. (io/input-stream file))]
    (let [m (edn/read-string (slurp in))
          rows (mapcat (fn [run] (take-last k (:census run))) (:runs m))]
      {:sigma (apply str (:sigma m))
       :counts (apply mapv + rows)})))

(defn -main [& args]
  (let [k (Integer/parseInt (or (first args) "20"))
        _ (.mkdirs out-dir)
        files (->> (.listFiles artifact-dir)
                   (filter #(str/ends-with? (.getName %) ".edn.gz"))
                   (sort-by #(.getName %)) vec)
        n (count files)
        done (java.util.concurrent.atomic.AtomicInteger. 0)
        _ (println (format "pooling last %d generations over %d artifacts" k n))
        rows (vec (pmap (fn [f]
                          (let [r (pooled-counts f k)
                                c (.incrementAndGet done)]
                            (when (zero? (mod c 4000)) (println "  " c "/" n) (flush))
                            r))
                        files))
        expect (* 3 k 60)]
    ;; fail loudly rather than normalise a short row away
    (let [bad (remove #(= expect (reduce + (:counts %))) rows)]
      (when (seq bad)
        (throw (ex-info "pooled counts wrong" {:expect expect :n-bad (count bad)
                                               :got (reduce + (:counts (first bad)))}))))
    (let [out (io/file out-dir (str "pooled-dists-K" k ".f64"))]
      (with-open [os (java.io.DataOutputStream.
                      (java.io.BufferedOutputStream. (io/output-stream out)))]
        (doseq [r rows, c (:counts r)]
          (.writeLong os (Long/reverseBytes (Double/doubleToLongBits (double c))))))
      (println (format "wrote %d x 256 (each row sums to %d) -> %s"
                       (count rows) expect (str out))))
    (flush)))

(apply -main *command-line-args*)
