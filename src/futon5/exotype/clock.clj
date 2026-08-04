(ns futon5.exotype.clock
  "N2b — the endogenous clock. Each cell carries (rule, tau); tau integrates
   STRESS rather than chronological time, and a cell whose tau crosses a threshold
   undergoes apoptosis and is replaced from its exotype.

   Design and invariants: TN-baldwin-reboot.md 14, 36.3. Joe's framing:

     Einstein  -- every rule has its own proper time. Two identical rules may have
                  different ages because they have had different histories. No
                  global generation counter is needed.
     Apoptosis -- a rule that cannot maintain itself is not DEFEATED by another; it
                  reaches the end of its viable lifetime.

   THE THREE INVARIANTS, fixed before this was built:

   (a) tau modulates the RATE of variation, never its DIRECTION. The clock decides
       *when* a replacement happens; the exotype decides *what* it is. Letting tau
       choose the successor would be transcription in disguise and would collapse
       the Lamarck/Baldwin contrast -- the same shape as the two void designs.

   (b) Apoptosis must introduce NOVELTY. Replacing an expired rule with a copy of a
       neighbour is copy-only over a non-growing set, which is exactly the slice-12
       void.

       *** THIS INVARIANT IS CURRENTLY VIOLATED. DO NOT CITE IT AS HONOURED. ***
       (codex-12 #2, codex-14 Q4, both confirmed by direct measurement.)
       `successor` applies the propagator to the CORPSE'S OWN sigil, which is an
       exotype-conditioned orbit of the dead rule rather than a draw from the
       exotype's induced distribution. Measured: one `apply-exotype` yields 3
       distinct results over 500 draw-seeds; 8 applications cover 22 of 256 bytes;
       and the successor CAN EQUAL THE CORPSE, making apoptosis a no-op -- the
       all-zero sigil under :identity at seed 45 returns its own input.
       A correct implementation needs a source-independent start plus a test for
       diversity and explicit non-copying. Held for Joe: it changes the mechanism's
       semantics and this note exists to stop hotfixing.

   (c) The consequences require the N2 seeding fix. Before it, every cell past
       threshold would have fired together.

   WHY THIS MIGHT MATTER BEYOND VARIATION (36.3). The EoC peak in blend is
   invisible to every local observable the substrate computes -- divergence,
   hunger and activity are all monotone in blend while damage peaks at 0.5.
   Criticality is a COUNTERFACTUAL property and a single run contains no
   counterfactual. Apoptosis is an endogenous perturbation source: an expiring
   cell is a live single-cell flip, and the field's response to it is precisely
   the quantity no passive observable reports. Whether that makes criticality
   *sensible* to the system is the open question, and `local-statistics-are-monotone?`
   is its falsifier."
  (:require [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]))

(def default-theta
  "Apoptosis threshold. At the increments below a wholly static cell ages by +1 per
   step, so this is roughly 'twenty steps of accomplishing nothing'."
  20.0)

(def default-draws
  "Propagator applications used to generate a successor. More draws sample the
   exotype's induced distribution more fully; one draw would be a single bit flip
   of the corpse, which is closer to mutation-of-the-dead than to generation."
  8)

(defn stress
  "Per-step increment to a cell's tau.

     +1            ageing floor: doing nothing still costs
     + activity    local mismatch -- a discordant neighbourhood is work
     - 2 * changed successful self-maintenance repays more than the floor

   So a cell that keeps changing in a quiet neighbourhood runs its clock BACKWARDS
   (net -1) and effectively does not age; a frozen cell ages at +1; a cell in a
   discordant neighbourhood that cannot change ages fastest. That is the Einstein
   property: proper time is a function of history, not of the global step count."
  [{:keys [activity]} changed?]
  (+ 1.0 (double activity) (if changed? -2.0 0.0)))

(defn successor
  "A new rule for an expired cell. Not a function of tau, so invariant (a) holds.

   INVARIANT (b) IS NOT SATISFIED -- see the namespace docstring. This is an orbit
   of the corpse under the exotype, not a draw from the exotype, and it can return
   the corpse unchanged. Kept as-is pending a design decision."
  [sigil exotype seed draws]
  (reduce (fn [s i] (grid/apply-exotype s exotype (+ (long seed) (* 7919 i))))
          sigil
          (range draws)))

(defn advance
  "Integrate tau over one step and fire apoptosis where it crosses theta.

   Consumes the state AFTER `grid/step` has produced the new genotype, so
   `previous-genotype` is available and `changed?` is well defined -- which is why
   carrying that field (25) was a prerequisite for this namespace."
  [{:keys [genotype previous-genotype exotypes taus seed time theta draws] :as state}]
  (let [width (count genotype)
        theta (double (or theta default-theta))
        draws (long (or draws default-draws))
        taus (or taus (vec (repeat width 0.0)))
        stepped
        (mapv (fn [index]
                (let [observation (efe/local-observation state index)
                      changed? (not (:static? observation))
                      tau (+ (double (nth taus index))
                             (stress observation changed?))]
                  (max 0.0 tau)))
              (range width))
        expired (set (filter #(> (nth stepped %) theta) (range width)))]
    {:genotype (mapv (fn [index sigil]
                       (if (expired index)
                         (successor sigil (nth exotypes index)
                                    (+ (long (or seed 0))
                                       (* (long (or time 0)) width) index)
                                    draws)
                         sigil))
                     (range width) genotype)
     :taus (mapv (fn [index tau] (if (expired index) 0.0 tau))
                 (range width) stepped)
     :apoptoses (count expired)
     :previous-genotype (or previous-genotype genotype)}))

(defn step
  "`grid/step` followed by the clock. Returns a state carrying `:taus` and the
   apoptosis count for the step."
  [state]
  (let [advanced (grid/step state)
        clocked (advance (assoc advanced :taus (:taus state)
                                :theta (:theta state) :draws (:draws state)))]
    (assoc advanced
           :genotype (:genotype clocked)
           :taus (:taus clocked)
           :apoptoses (:apoptoses clocked)
           :theta (:theta state)
           :draws (:draws state))))

(defn run-steps [state steps]
  (nth (iterate step state) steps))
