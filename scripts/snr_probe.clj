(require '[futon5.aif.retarget-demo :as d]
         '[futon5.aif.forward :as forward])

;; SIGNAL-TO-NOISE PROBE — the decisive question for claim (A).
;;
;; SIGNAL = how far the control knobs move the macro-features (from the
;;          authority probe: pressure spans ~0.50 across knob extremes).
;; NOISE  = how much the macro-features vary at a FIXED knob setting, purely
;;          from the CA's own stochasticity (different seeds).
;;
;; If NOISE >= SIGNAL, the controller CANNOT steer no matter how good it is:
;; the plant's intrinsic variance swamps the actuator's authority. That is a
;; property of the PLANT, not a bug in the controller.

(defn- stats [xs]
  (let [n (count xs)
        m (/ (reduce + xs) (double n))
        var (/ (reduce + (map #(let [d (- % m)] (* d d)) xs)) (double n))]
    {:mean m :sd (Math/sqrt var) :min (apply min xs) :max (apply max xs)}))

(defn- probe-at [up mt seeds]
  (let [vals (for [s seeds]
               (let [st0 (d/initial-state 1000 32)
                     st (assoc-in st0 [:exotype :params]
                                  (assoc (get-in st0 [:exotype :params])
                                         :update-prob up :match-threshold mt))
                     fp (forward/forward-predict st :hold {:seed s :generations 8 :W 8})]
                 (double (or (:pressure (:mean fp)) 0.5))))]
    (stats (vec vals))))

(println "=== SIGNAL-TO-NOISE PROBE (pressure channel) ===")
(println "NOISE: spread of :pressure at a FIXED knob setting, across seeds")
(println)
(def seeds (range 2000 2012))

(def lo (probe-at 0.05 0.5 seeds))
(def hi (probe-at 1.0 0.5 seeds))

(println (format "  knobs (update-prob=0.05): mean %.3f  sd %.3f  range %.3f..%.3f"
                 (:mean lo) (:sd lo) (:min lo) (:max lo)))
(println (format "  knobs (update-prob=1.00): mean %.3f  sd %.3f  range %.3f..%.3f"
                 (:mean hi) (:sd hi) (:min hi) (:max hi)))
(println)
(def signal (Math/abs (- (:mean hi) (:mean lo))))
(def noise (/ (+ (:sd lo) (:sd hi)) 2.0))
(println (format "  SIGNAL (mean shift from knob extreme->extreme): %.3f" signal))
(println (format "  NOISE  (avg sd at a FIXED knob setting):        %.3f" noise))
(println (format "  SNR    (signal / noise):                        %.2f" (/ signal (max noise 1e-9))))
(println)
(println "SNR << 1  => the plant's own variance swamps the actuator; NO controller")
(println "             can steer it. Claim (A) fails for a PLANT reason, not a")
(println "             controller bug.")
(println "SNR >> 1  => the knobs do dominate; a failure to confine is then the")
(println "             controller's fault and is fixable.")
