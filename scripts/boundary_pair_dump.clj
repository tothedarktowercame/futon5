;; Dump GENOTYPE and PHENOTYPE spacetime for named (gamma, kappa) cells.
;; (claude-14, 2026-08-06 — for the phase-boundary pair gamma=32, kappa 0.1 vs 0.2.)
;;
;; Joe's point: reading only the phenotype, and only through a compressor, has been
;; hiding what the system is doing.  gzip detects REPETITION, not organisation; a
;; sheet can be highly organised without repeating, and the measure would call it
;; incompressible.  So: look at genotype and phenotype together, directly.
;;
;; Same geometry as the objective grid (width 250, rows 0--250, burn-in 0) so these
;; sheets are directly comparable to everything else measured today.

(require '[clojure.java.io :as io])

;; The controller script self-executes its CLI at the bottom and declares its own ns;
;; strip the trailing form and call through the namespace.  (See matched_window_surface.)
(let [src (slurp "scripts/intrinsic_objective_controller.clj")
      cut (.lastIndexOf src "(try\n  (apply -main")]
  (when (neg? cut)
    (throw (ex-info "could not find the trailing CLI invocation to strip" {})))
  (load-string (subs src 0 cut)))

(def rows 250)

(defn dump-cell [gamma kappa seed out-prefix]
  (let [{:keys [initial step]}
        (intrinsic-objective-controller/assert-objective-geometry!
         (intrinsic-objective-controller/load-objective-api!))]
    (with-open [gw (io/writer (str out-prefix "-gen.txt"))
                pw (io/writer (str out-prefix "-phe.txt"))]
      (loop [state (initial gamma kappa seed) row 0]
        (when (< row rows)
          (.write pw (str (apply str (map #(if (or (true? %) (= 1 %)) \1 \0)
                                          (:phenotype state))) "\n"))
          (.write gw (str (clojure.string/join " " (map #(hash %) (:genotype state))) "\n"))
          (recur (step state) (inc row)))))
    (println "wrote" out-prefix "-gen.txt /" out-prefix "-phe.txt")))

(let [seed 2026102000]
  (dump-cell 32.0 0.10 seed "/tmp/bnd-g32-k010")
  (dump-cell 32.0 0.20 seed "/tmp/bnd-g32-k020")
  (println "BOUNDARY_DUMP_DONE"))
