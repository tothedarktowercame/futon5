(ns futon5.aif.design-gates
  "Design-level gates for control experiments.

   Component verification does not imply design verification.  On 2026-07-15 the
   MetaCA tokamak passed every component check it had — g-efe unit-pure (R5), the
   R-contract S1–S4, four repair-flipping Lean theorems in MetaCATokamakExample,
   Cμ validated against the ECA anchor — while the EXPERIMENT was meaningless.
   Three independent defects were present, EACH sufficient on its own to make the
   controller indistinguishable from :null:

     1. the action space actuated :update-prob and :match-threshold, both inert
        (paired effect exactly 0.000, every seed, every sigil, both modes);
     2. a silent (catch Exception _ nil) at runtime.clj:540 disabled the entire
        exotype path whenever a genotype-derived operator set a kernel with no
        spec (BlendHand → :blend-hand), which is the demo's default config;
     3. the target C sat OUTSIDE the reachable set on both channels, which pins
        argmin to a constant and emits one action forever.

   No component test asks any of the questions that would have caught these.
   They are questions about the DESIGN:

     - can the actuator move the plant at all?          (gate: actuator-authority)
     - can the plant reach the target?                  (gate: target-reachable?)
     - do the arms of the experiment differ?            (gate: targets-discriminable?)

   The deeper point, and the reason these are gates rather than analyses: a
   DEAD KNOB MAKES AN ABLATION PASS FOR FREE.  'Scramble the wiring → results
   unchanged' is the signature of a disconnected actuator, and it is equally well
   explained by 'the wiring never mattered' (refuting the hypothesis) and by 'the
   wiring was never read' (refuting the apparatus).  The null result cannot tell
   them apart.  So authority is a PRECONDITION of the experiment, established
   before any ablation, not a conclusion drawn after one.

   These gates are deliberately generic — `run-fn` is any (seed, params) →
   {channel value} — so the same checks apply across the cohort, not just to the
   tokamak.")

;; --------------------------------------------------------------------------- ;;
;; helpers
;; --------------------------------------------------------------------------- ;;

(defn- mean [xs] (if (seq xs) (/ (reduce + xs) (double (count xs))) 0.0))

(defn- sd [xs]
  (let [m (mean xs)]
    (Math/sqrt (mean (map #(let [e (- % m)] (* e e)) xs)))))

(defn- channel-means
  "Average each channel of `run-fn` over `seeds`."
  [run-fn params seeds channels]
  (let [rs (map #(run-fn % params) seeds)]
    (into {} (for [c channels] [c (mean (map #(double (get % c 0.0)) rs))]))))

(defn- euclidean [a b channels]
  (Math/sqrt (reduce + (for [c channels]
                         (let [d (- (double (get a c 0.0)) (double (get b c 0.0)))]
                           (* d d))))))

;; --------------------------------------------------------------------------- ;;
;; GATE 1 — actuator authority
;; --------------------------------------------------------------------------- ;;

(defn actuator-authority
  "Does each action actually move the plant?

   PAIRED by construction: for each seed we run `base-params` and
   `(apply-action base-params action)` under the SAME seed, so the plant's own
   stochasticity cancels within the pair and what survives is the actuator's
   authority.  This distinction is not cosmetic — the unpaired version of this
   test reported 'signal 0.070, noise 0.123, SNR 0.57' for a knob whose true
   effect is EXACTLY zero.  Unpaired, you measure the plant's variance and call
   it a weak actuator.

   Returns a map per action: {:moved n :of n :mean-d d :snr s :live? bool}.
   An action is LIVE iff it changes the plant on at least one seed."
  [{:keys [run-fn apply-action base-params actions seeds channel]}]
  (into {}
        (for [a actions]
          (let [ds (vec (for [s seeds]
                          (let [before (double (get (run-fn s base-params) channel 0.0))
                                after  (double (get (run-fn s (apply-action base-params a)) channel 0.0))]
                            (- after before))))
                nz (count (remove #(< (Math/abs %) 1e-9) ds))
                m (mean ds)
                sdv (sd ds)]
            [a {:moved nz :of (count ds) :mean-d m
                :snr (/ (Math/abs m) (max sdv 1e-9))
                :live? (pos? nz)}]))))

;; --------------------------------------------------------------------------- ;;
;; GATE 2 — the reachable set
;; --------------------------------------------------------------------------- ;;

(defn reachable-set
  "Enumerate what the actuator can actually reach.

   `param-space` is the seq of param maps the actuator can realise (for the
   tokamak: the 8 rotations of Z/8).  Returns [{:params p :point {chan val}}].

   Enumerating this is the single highest-value thing to do before running a
   control experiment: it converts 'the controller should steer to C' from an
   assumption into a checkable claim."
  [{:keys [run-fn param-space seeds channels]}]
  (vec (for [p param-space]
         {:params p :point (channel-means run-fn p seeds channels)})))

(defn reachable-ranges
  "Per-channel [min max] over the reachable set."
  [reachable channels]
  (into {} (for [c channels]
             (let [vs (map #(double (get (:point %) c 0.0)) reachable)]
               [c [(apply min vs) (apply max vs)]]))))

(defn target-reachable?
  "Is `target` inside the reachable set's per-channel range?

   This is a NECESSARY condition, not a sufficient one: the reachable set is a
   finite scatter of points, not a box, so being inside every channel's range
   does not guarantee the exact point is hit.  But failing it is decisive — a
   target outside the range on any channel is unreachable on that channel, which
   pins argmin to a constant and makes the controller a constant policy.

   `target` is {channel {:mean m}} (the C-vector shape) or {channel v}."
  [reachable target channels]
  (let [ranges (reachable-ranges reachable channels)
        per (into {} (for [c channels]
                       (let [t (let [v (get target c)] (double (if (map? v) (:mean v) v)))
                             [lo hi] (get ranges c)]
                         [c {:target t :range [lo hi] :inside? (<= lo t hi)}])))]
    {:per-channel per
     :reachable? (every? :inside? (vals per))}))

;; --------------------------------------------------------------------------- ;;
;; GATE 4 — non-stationarity (is the target still reachable later?)
;; --------------------------------------------------------------------------- ;;

(defn target-stable?
  "Is the target STILL reachable once the plant has evolved?

   Reachability is not a property of the experiment, it is a property of the
   plant AT A MOMENT.  The MetaCA's reachable set is a function of the genotype,
   and the genotype evolves as the run proceeds — measured 2026-07-15: pressure
   reachable 0.125..0.375 at t=0 and 0.067..0.357 by t=6, so a target derived at
   t=0 (0.375) is UNREACHABLE by t=6.  The controller is then chasing a
   set-point that moved out from under it, and 'failure to hold C' says nothing
   about the controller.

   This makes the tokamak's problem NON-STATIONARY REGULATION rather than
   fixed-set-point confinement — a substantially harder claim than the one the
   demo is written to test.  Pass `reachable-t0` and `reachable-tn` from
   `reachable-set` at two times."
  [reachable-t0 reachable-tn target channels]
  (let [at-0 (target-reachable? reachable-t0 target channels)
        at-n (target-reachable? reachable-tn target channels)]
    {:reachable-at-t0 (:reachable? at-0)
     :reachable-at-tn (:reachable? at-n)
     :drifted? (not= (map :point reachable-t0) (map :point reachable-tn))
     :stable? (and (:reachable? at-0) (:reachable? at-n))
     :per-channel-t0 (:per-channel at-0)
     :per-channel-tn (:per-channel at-n)}))

;; --------------------------------------------------------------------------- ;;
;; GATE 3 — discriminability of the experiment's arms
;; --------------------------------------------------------------------------- ;;

(defn targets-discriminable?
  "Do two targets actually induce DIFFERENT optimal behaviour?

   If the nearest reachable point to A is also the nearest to B, then a
   competent controller does the SAME thing in both arms, and 'A vs B' is a
   null result by construction — the experiment cannot discriminate no matter
   how good the controller is.  This is the same vacuity as a dead actuator,
   one level up: the arms are identical, so the comparison is uninformative.

   Checks the argmin over the reachable set, which is what a competent
   controller converges to."
  [reachable target-a target-b channels]
  (let [pt (fn [t] (into {} (for [c channels]
                              [c (let [v (get t c)] (double (if (map? v) (:mean v) v)))])))
        best (fn [t] (apply min-key #(euclidean (:point %) (pt t) channels) reachable))
        ba (best target-a)
        bb (best target-b)]
    {:best-for-a (:point ba) :params-a (:params ba)
     :best-for-b (:point bb) :params-b (:params bb)
     :discriminable? (not= (:params ba) (:params bb))}))

;; --------------------------------------------------------------------------- ;;
;; report
;; --------------------------------------------------------------------------- ;;

(defn report
  "Run every gate and print a verdict.  Returns {:pass? bool :gates {...}}."
  [{:keys [run-fn apply-action base-params actions seeds channel channels
           param-space target-a target-b]}]
  (let [auth (actuator-authority {:run-fn run-fn :apply-action apply-action
                                  :base-params base-params :actions actions
                                  :seeds seeds :channel channel})
        reach (reachable-set {:run-fn run-fn :param-space param-space
                              :seeds seeds :channels channels})
        ra (target-reachable? reach target-a channels)
        rb (target-reachable? reach target-b channels)
        disc (targets-discriminable? reach target-a target-b channels)
        ;; :hold is expected to be inert — that is its job.
        actuating (remove #{:hold} actions)
        auth-pass? (every? #(:live? (get auth %)) actuating)]
    (println "=== GATE 1: ACTUATOR AUTHORITY (paired; does each action move the plant?) ===")
    (doseq [a actions]
      (let [{:keys [moved of mean-d snr live?]} (get auth a)]
        (println (format "  %-14s moved %2d/%-2d | mean d %+.4f | SNR %5.2f | %s"
                         (name a) moved of mean-d snr
                         (cond (= a :hold) (if live? "LIVE — BUG: :hold must be inert" "inert (correct)")
                               live? "LIVE"
                               :else "DEAD — this action is a no-op wearing a name")))))
    (println (format "  => gate 1 %s" (if auth-pass? "PASS" "FAIL")))
    (println)
    (println "=== GATE 2: TARGET REACHABILITY (can the plant reach C at all?) ===")
    (println (format "  reachable set: %d distinct points over %d actuator settings"
                     (count (distinct (map :point reach))) (count reach)))
    (doseq [[label r] [["target-a" ra] ["target-b" rb]]]
      (println (format "  %s:" label))
      (doseq [[c {:keys [target range inside?]}] (:per-channel r)]
        (println (format "    %-12s target %.4f | reachable %.4f .. %.4f | %s"
                         (name c) target (first range) (second range)
                         (if inside? "inside" "OUTSIDE -> argmin is CONSTANT -> policy is constant")))))
    (println (format "  => gate 2 %s" (if (and (:reachable? ra) (:reachable? rb)) "PASS" "FAIL")))
    (println)
    (println "=== GATE 3: ARM DISCRIMINABILITY (do A and B induce different behaviour?) ===")
    (println (format "  nearest reachable to A: %s  via %s" (pr-str (:best-for-a disc)) (pr-str (:params-a disc))))
    (println (format "  nearest reachable to B: %s  via %s" (pr-str (:best-for-b disc)) (pr-str (:params-b disc))))
    (println (format "  => gate 3 %s%s" (if (:discriminable? disc) "PASS" "FAIL")
                     (if (:discriminable? disc) ""
                         " — A and B share an optimum; the arms are identical BY CONSTRUCTION")))
    (println)
    (let [pass? (and auth-pass? (:reachable? ra) (:reachable? rb) (:discriminable? disc))]
      (println (str "=== DESIGN VERDICT: " (if pass? "PASS — the experiment can produce a signal"
                                               "FAIL — this experiment is VACUOUS; a null result would mean nothing")
                    " ==="))
      (when-not pass?
        (println "  Fix the design before running. A null from a vacuous design is")
        (println "  indistinguishable from a null that refutes the hypothesis."))
      {:pass? pass? :gates {:authority auth :reachable-a ra :reachable-b rb
                            :discriminable disc :reachable-set reach}})))
