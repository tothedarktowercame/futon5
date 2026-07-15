(require '[futon5.ca.core :as ca]
         '[futon5.aif.retarget-demo :as d]
         '[futon5.aif.forward :as forward])

;; PAIRED SNR — the honest re-test of "the plant is uncontrollable".
;;
;; The earlier probe was UNPAIRED: it compared pressure at knob-lo vs knob-hi
;; across DIFFERENT seeds, so the CA's own variance (sd 0.123) sat in the
;; denominator and swamped the knob's effect (0.070) -> SNR 0.57 -> "no
;; controller can steer this". That design was forced by the missing seeding:
;; there was no way to hold the noise fixed.
;;
;; Now ca/with-seed makes the dynamics reproducible, so we can do the test that
;; actually answers the control question: SAME seed, vary ONLY the knob. The
;; CA's stochasticity cancels within each pair, and what survives is the
;; actuator's authority.
;;
;;   paired d_s = pressure(hi, seed s) - pressure(lo, seed s)
;;   paired SNR = |mean d| / sd d
;;
;; If paired SNR >> 1 the knobs DO have authority and my negative was an
;; artifact of an under-powered design. If it is still ~0, the plant really is
;; uncontrollable through these knobs and the verdict stands.

(defn- stats [xs]
  (let [n (count xs), m (/ (reduce + xs) (double n))
        v (/ (reduce + (map #(let [e (- % m)] (* e e)) xs)) (double n))]
    {:mean m :sd (Math/sqrt v)}))

(defn pressure-at
  "Run one window at a given knob setting, with BOTH the dynamics RNG and the
   run's :seed pinned, so the only difference between arms is the knob."
  [seed up]
  (ca/with-seed seed
    (let [st0 (d/initial-state 1000 48)
          st (assoc-in st0 [:exotype :params]
                       (assoc (get-in st0 [:exotype :params])
                              :update-prob up :match-threshold 0.5))
          fp (forward/forward-predict st :hold {:seed seed :generations 8 :W 8})]
      (double (or (:pressure (:mean fp)) 0.5)))))

(def seeds (range 3000 3016))

(println "=== PAIRED SNR — same seed, knob varied (the control question) ===")
(println)
(def pairs
  (doall (for [s seeds]
           (let [lo (pressure-at s 0.05)
                 hi (pressure-at s 1.0)]
             {:seed s :lo lo :hi hi :d (- hi lo)}))))

(println (format "%-8s %-9s %-9s %s" "seed" "lo(0.05)" "hi(1.00)" "paired d"))
(doseq [p (take 8 pairs)]
  (println (format "%-8d %-9.3f %-9.3f %+.3f" (:seed p) (:lo p) (:hi p) (:d p))))
(println "  ... (16 pairs total)")
(println)

(let [ds (mapv :d pairs)
      {:keys [mean sd]} (stats ds)
      snr (/ (Math/abs mean) (max sd 1e-9))
      nonzero (count (remove #(< (Math/abs %) 1e-9) ds))
      same-sign (max (count (filter pos? ds)) (count (filter neg? ds)))]
  (println (format "  paired mean d = %+.4f   sd(d) = %.4f" mean sd))
  (println (format "  PAIRED SNR = %.2f" snr))
  (println (format "  pairs where the knob changed anything: %d/%d" nonzero (count ds)))
  (println (format "  pairs agreeing on direction: %d/%d" same-sign (count ds)))
  (println)
  (println "  -- for comparison, the UNPAIRED probe gave: signal 0.070, noise 0.123, SNR 0.57")
  (println)
  (cond
    (< nonzero 1)
    (println "  VERDICT: the knob changes NOTHING even with noise cancelled ->"
             "\n           the actuator has no authority. The negative STANDS, and is now decisive.")
    (> snr 1.0)
    (println "  VERDICT: paired SNR > 1 -> the knobs DO have authority once the CA's own"
             "\n           variance is cancelled. My earlier 'uncontrollable plant' verdict was"
             "\n           an artifact of the unpaired design. The tokamak is back in play.")
    :else
    (println "  VERDICT: even paired, the knob's effect is within its own variability ->"
             "\n           weak authority. Negative largely stands.")))
