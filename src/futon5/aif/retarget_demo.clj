(ns futon5.aif.retarget-demo
  "Re-targeting demo for the MetaCA tokamak — validates CLAIM (A) ONLY.

   claude-4's decomposition (verified): 'the tokamak holds the CA at
   edge-of-chaos' is a CONJUNCTION of two independent claims:

     (A) COMPETENT CONFINEMENT — the loop drives the CA's macro-state toward
         a target C, holds it there, beats a no-control baseline, and
         RE-TARGETS when C moves.  This is control theory.  It needs NO EoC
         discriminator, so it is validatable NOW.
     (B) THE HELD REGION IS EDGE-OF-CHAOS — requires a SeparatesEoC-valid
         discriminator.  That is the OPEN frontier (EVALUATOR-SPEC banks the
         fails: AIS / nn-TE / distance-TE all rank chaos > complex).

   This demo validates (A) and makes NO claim about (B).  The targets below
   are deliberately arbitrary control set-points — they are NOT claimed to be
   edge-of-chaos.  (The historical 工/泰 'EoC' anchor is contaminated: run-health
   measures that same config as :barcode / 74-93% frozen, so it cannot serve as
   an EoC ground truth.)

   The FALSIFIABLE core is re-targeting: a controller that collapses to the
   same place regardless of C is dying, not confining."
  (:require [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.aif.controller :as ctrl]
            [futon5.aif.preference :as pref]
            [futon5.aif.forward :as forward]))

(def numeric-channels
  "Scored over the two genuinely independent macro-feature dimensions.
   See futon5.aif.preference/numeric-channels: :activity is a literal duplicate
   of :pressure and :structure is its complement (1 - avg-change) / nil, so
   scoring them double-counted change-rate and a missing value."
  pref/numeric-channels)

;; Two DISTINCT control set-points.  Not EoC claims — just different places to
;; be asked to go, so that "does it follow C?" is answerable.
;;
;; *** RETRACTED (2026-07-15).  The set-points below were VACUOUS. ***
;;
;; The previous note here read: "the REACHABLE macro-feature set under the two
;; knobs is pressure/activity in [0.22, 0.72] and selectivity in [0.53, 0.81]
;; ... So targets below stay INSIDE the reachable band."  That band was measured
;; by a 1-seed-per-config probe, and it is NOT the reachable band — it is the
;; plant's SEED-TO-SEED VARIANCE.  The knobs it referred to (:update-prob,
;; :match-threshold) move the plant by EXACTLY 0.000 (paired, 12 seeds).  The
;; probe measured noise and called it control authority.
;;
;; Measured properly (paired, over the live Z/8 actuator, 8 seeds averaged), the
;; true reachable set is pressure 0.076..0.279 and selectivity 0.604..0.896.  So
;; the old target-A (pressure 0.30) and target-B (pressure 0.65) were BOTH
;; unreachable — B by nearly 2.5x the plant's maximum.  Worse, both had the SAME
;; nearest reachable point (r=6), so the two arms were identical BY
;; CONSTRUCTION: a competent controller necessarily does the same thing in both,
;; and this demo's own falsifiable core ("a controller that collapses to the same
;; place regardless of C is dying, not confining") would have scored CORRECT
;; behaviour as death.  The experiment could not have produced evidence either
;; way.
;;
;; Fix: derive the set-points from the genotype's OWN measured reachable set, so
;; both properties hold BY CONSTRUCTION rather than by assumption:
;;   - reachable    — each target IS an enumerated reachable point;
;;   - discriminable — A and B are DIFFERENT reachable points, so a competent
;;                     controller must behave differently in the two arms.
;; This is necessary because the reachable set is GENOTYPE-DEPENDENT (a second
;; genotype measures pressure 0.102..0.206, a different band), and each trial
;; draws a fresh genotype — so no fixed pair of numbers can be reachable for all
;; of them.  Verify with futon5.aif.design-gates/report before running.

(def ^:private rotation-space
  "The actuator: r ∈ Z/8 as (:mix-mode :rotate-left, :mix-shift r).  See the
   measured-actuator note in futon5.aif.forward — rotate-left k ≡ rotate-right
   (8-k) to 1e-9, so this enumerates the WHOLE reachable set with no redundancy."
  (range 8))

(defn- point->target
  "Turn a measured reachable point into a C-vector target.  `structure` is pinned
   at 0.5 so its (missing) channel contributes ~0 to the distance rather than
   silently dominating it; `activity` mirrors pressure because it IS pressure
   (README-cyber-mmca: ':activity - currently same as pressure')."
  [{:keys [pressure selectivity]}]
  {:pressure    {:mean pressure    :sd 0.15}
   :selectivity {:mean selectivity :sd 0.15}
   :structure   {:mean 0.50        :sd 0.15}
   :activity    {:mean pressure    :sd 0.15}})

(defn distance-to-c
  "Euclidean distance from an observed macro-feature map to a target C's means.
   Lower = closer to the set-point."
  [obs target]
  (Math/sqrt
   (double
    (reduce + (for [ch numeric-channels]
                (let [o (double (or (get obs ch) 0.5))
                      t (double (:mean (get target ch)))
                      d (- o t)]
                  (* d d)))))))

(defn- rng-sigil-string
  [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (.nextInt rng (count sigils)))))))

(def control-sigil
  "工.  NOT ca/default-sigil (一), which was used here until 2026-07-15: 一 is
   00000000 and derives :mix-mode :none, and :none makes the rotation actuator
   inert — the exotype would be steering with a mode that ignores the shift.
   Any sigil whose params the actuator overwrites would do; 工 is the one the L5
   wiring already pins (level-5-creative.edn:84-89)."
  "工")

(defn initial-state
  [seed length]
  (let [rng (java.util.Random. (long seed))]
    {:genotype (rng-sigil-string rng length)
     :phenotype nil
     :kernel :mutating-template
     ;; Start at r=0 (identity).  The actuator forces :mix-mode :rotate-left on
     ;; every rotation, so the start's mode does not pin the run.
     :exotype (-> (exotype/resolve-exotype {:sigil control-sigil :tier :super})
                  (assoc-in [:params :mix-shift] 0))
     :metrics-history []
     :gen-history []
     :phe-history []}))

(defn reachable-set
  "Enumerate THIS genotype's reachable macro-states by sweeping the actuator
   over Z/8.  8 forward runs; the honest ground truth for what the controller
   can be asked to do.

   Returns [{:r k :point {:pressure p :selectivity s}}] ordered by r."
  [state {:keys [seed generations] :or {seed 42 generations 10}}]
  (vec (for [r rotation-space]
         (let [st (assoc-in state [:exotype :params]
                            (assoc (get-in state [:exotype :params])
                                   :mix-mode :rotate-left :mix-shift r))
               fp (forward/forward-predict st :hold {:seed seed :generations generations
                                                     :W generations})]
           {:r r :point (select-keys (:mean fp) numeric-channels)}))))

(defn targets-for
  "Derive a reachable, discriminable A/B pair from a genotype's reachable set:
   the min-pressure and max-pressure corners.  Both are enumerated reachable
   points, and they are distinct points, so a competent controller MUST behave
   differently in the two arms.

   Returns {:target-a C :target-b C :r-a k :r-b k :separation d}, or nil if the
   actuator cannot separate the corners for this genotype (a degenerate plant —
   which is itself a design FAIL worth reporting, not routing around)."
  [reachable]
  (let [lo (apply min-key #(double (get-in % [:point :pressure] 0.5)) reachable)
        hi (apply max-key #(double (get-in % [:point :pressure] 0.5)) reachable)
        sep (Math/abs (- (double (get-in hi [:point :pressure] 0.5))
                         (double (get-in lo [:point :pressure] 0.5))))]
    (when (> sep 1e-9)
      {:target-a (point->target (:point lo))
       :target-b (point->target (:point hi))
       :r-a (:r lo) :r-b (:r hi)
       :separation sep})))

(defn run-arm
  "Drive the CA for `windows`, choosing each window's action by `mode`:
     :aif  -> the tokamak controller, scored against the CURRENT target C
     :null -> no control (:hold every window)

   `c-schedule` is (fn [window-idx] -> target-C), which is what enables the
   re-targeting test (move C mid-run and see if the CA follows).

   The world is advanced with forward-predict's :next-state — i.e. the SAME
   run-mmca kernel the controller predicts with (R4)."
  [{:keys [mode windows seed length c-schedule generations]
    :or {windows 24 seed 42 length 32 generations 10}}]
  (loop [i 0
         state (initial-state seed length)
         p-state nil
         out []]
    (if (>= i windows)
      out
      (let [target (c-schedule i)
            opts {:seed (+ seed i) :generations generations :W generations}
            result (when (= mode :aif)
                     (ctrl/choose-actions-aif
                      state nil (assoc opts
                                       :target-c target
                                       :precision-state p-state)))
            action (if (= mode :null) :hold (first (:actions result)))
            fp (forward/forward-predict state action opts)
            obs (:mean fp)]
        (recur (inc i)
               (:next-state fp)
               (:precision-state result)
               (conj out {:window i
                          :action action
                          :obs (select-keys obs numeric-channels)
                          :distance (distance-to-c obs target)}))))))

(defn- mean [xs]
  (if (seq xs) (/ (reduce + xs) (count xs)) 0.0))

(defn retarget-trial
  "One seeded trial: run `windows` with target-A for the first half, then
   SWITCH to target-B for the second half.  Returns per-arm phase distances.

   The targets are derived from THIS genotype's measured reachable set (see
   `targets-for`), so both are reachable and the two arms are guaranteed to have
   different optima.  Returns {:skipped :degenerate-plant} when the actuator
   cannot separate the corners for this genotype — reported, never silently
   dropped, because a plant with no separation is a design fact about the
   experiment, not a seed to quietly discard."
  [{:keys [seed windows generations length]
    :or {seed 42 windows 24 generations 10 length 32}}]
  (let [half (quot windows 2)
        st0 (initial-state seed length)
        reach (reachable-set st0 {:seed seed :generations generations})
        tg (targets-for reach)]
    (if-not tg
      {:seed seed :skipped :degenerate-plant}
      (let [schedule (fn [i] (if (< i half) (:target-a tg) (:target-b tg)))
            run (fn [mode] (run-arm {:mode mode :windows windows :seed seed
                                     :length length :generations generations
                                     :c-schedule schedule}))
            aif (run :aif)
            null (run :null)
            phase (fn [rows lo hi] (mean (map :distance (subvec (vec rows) lo hi))))]
        {:seed seed
         :r-a (:r-a tg) :r-b (:r-b tg) :separation (:separation tg)
         :aif-phase1 (phase aif 0 half)      ; distance to A while targeting A
         :aif-phase2 (phase aif half windows) ; distance to B while targeting B
         :null-phase1 (phase null 0 half)
         :null-phase2 (phase null half windows)
         :aif-actions (frequencies (map :action aif))}))))

(defn report
  "Run N seeded trials and print the claim-(A) evidence."
  [{:keys [trials windows generations length]
    :or {trials 5 windows 24 generations 10 length 32}}]
  (let [all (mapv #(retarget-trial {:seed (+ 1000 (* 7 %)) :windows windows
                                    :generations generations :length length})
                  (range trials))
        rs (remove :skipped all)
        skipped (filter :skipped all)
        m (fn [k] (mean (map k rs)))]
    (println "=== TOKAMAK RE-TARGETING DEMO — claim (A): competent confinement ===")
    (println "trials:" trials " windows:" windows " (C switches A->B at half)")
    ;; No silent caps: a dropped trial is stated, never absorbed into the mean.
    (when (seq skipped)
      (println (format "  SKIPPED %d/%d trials (degenerate plant: actuator cannot"
                       (count skipped) trials))
      (println "          separate the reachable corners for that genotype)"))
    (if (empty? rs)
      (do (println "  NO usable trials — every genotype was degenerate.")
          (println "  This is a DESIGN failure, not a result.")
          all)
      (do
        (println (format "  targets are per-genotype reachable corners; mean corner separation %.4f"
                         (mean (map :separation rs))))
        (println)
        (println "  phase 1 (target A)   :aif dist =" (format "%.4f" (m :aif-phase1))
                 " | :null dist =" (format "%.4f" (m :null-phase1)))
        (println "  phase 2 (target B)   :aif dist =" (format "%.4f" (m :aif-phase2))
                 " | :null dist =" (format "%.4f" (m :null-phase2)))
        (println)
        (println "  BEATS-NULL phase1:" (< (m :aif-phase1) (m :null-phase1))
                 " phase2:" (< (m :aif-phase2) (m :null-phase2)))
        (println "  actions taken by :aif (trial 0):" (:aif-actions (first rs)))
        (println)
        (println "NOTE: targets are arbitrary control set-points. This demo claims")
        (println "      CONFINEMENT/RE-TARGETING only — NOT that any target is EoC.")
        (println "      The EoC band itself is measurably UNREACHABLE by this")
        (println "      actuator — see futon5.aif.preference/eoc-targets.")
        rs))))
