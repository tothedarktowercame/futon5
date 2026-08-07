;; Matched-window remeasurement (claude-14, 2026-08-06).
;;
;; The bridge-test figure of held-out R2 = 0.5414 is a LOWER BOUND: the observables
;; came from the earlier surface run (rows 100--300) while the objective sheet is
;; rows 0--250.  Same seeds and parameters, so the same trajectories -- but different
;; measurement windows.  The mismatch adds noise and therefore biases the test toward
;; failure, which is why the pass was trustworthy; it also means the NUMBER is not.
;;
;; This drives codex-3's `aligned-episode`, which measures halting, change and the
;; objective all on rows 0--250, across the same 35-cell grid.  One JVM, not 140.
;;
;; Emits the same column names as the legacy CSVs so the existing bridge test runs
;; against it unchanged: gamma,kappa,seed,halting,change,mid_range,sd_active,mean_ratio

(require '[clojure.java.io :as io])
;; Two wrinkles in loading the controller script:
;;  1. it self-executes its CLI at the bottom (`(apply -main *command-line-args*)`),
;;     so a plain load-file runs the CLI and exits -- strip that trailing form;
;;  2. it declares `(ns intrinsic-objective-controller ...)`, so its defs land THERE
;;     and not in `user` -- hence the fully-qualified call below.
(let [src (slurp "scripts/intrinsic_objective_controller.clj")
      cut (.lastIndexOf src "(try\n  (apply -main")]
  (when (neg? cut)
    (throw (ex-info "could not find the trailing CLI invocation to strip" {})))
  (load-string (subs src 0 cut)))
(when-not (resolve 'intrinsic-objective-controller/aligned-episode)
  (throw (ex-info "aligned-episode did not load" {})))

(def gammas [1.0 2.0 4.0 8.0 16.0 32.0 64.0])
(def kappas [0.0 0.1 0.2 0.5 1.0])
(def seed-base 2026102000)
(def seed-count 4)

(defn -main [& args]
  (let [out (or (first args) "/tmp/matched-surface.csv")
        total (* (count gammas) (count kappas) seed-count)]
    (with-open [w (io/writer out)]
      (.write w "gamma,kappa,seed,halting,change,mid_range,sd_active,mean_ratio\n")
      (doseq [[i [gamma kappa seed]]
              (map-indexed vector
                           (for [gamma gammas kappa kappas
                                 seed (range seed-base (+ seed-base seed-count))]
                             [gamma kappa seed]))]
        (let [r (intrinsic-objective-controller/aligned-episode gamma kappa seed)]
          (.write w (format "%.6f,%.6f,%d,%.6f,%.6f,%.6f,%.6f,%.6f%n"
                            gamma kappa seed
                            (:halting r) (:change r)
                            (double (:mid-range r))
                            (double (or (:sd-active r) 0.0))
                            (double (:mean-ratio r))))
          (.flush w)
          (when (zero? (mod (inc i) seed-count))
            (println (format "cell %d/%d done: gamma=%.1f kappa=%.2f  mid=%.4f halt=%.4f chg=%.4f"
                             (quot (inc i) seed-count) (quot total seed-count)
                             gamma kappa (double (:mid-range r))
                             (:halting r) (:change r)))
          (flush)))))
    (println "MATCHED_DONE" out)))

(apply -main *command-line-args*)
