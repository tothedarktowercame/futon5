(ns futon5.aif.forward
  "Pure forward kernel for the MetaCA tokamak (R4).

   Wraps the EXISTING deterministic CA step `futon5.mmca.runtime/run-mmca` —
   no new CA dynamics are introduced.  The forward model is the controller's
   predict seam: given a macro-state (genotype + phenotype + exotype + history)
   and a candidate action (pressure/selectivity knob), it runs the CA forward
   and projects the result to the macro-feature observation ABI.

   `forward-predict` calls the SAME kernel and returns a distribution (mean +
   per-channel variance) suitable for EFE scoring in later slices.

   Contract (mirrors the ant port's `ants.aif.forward/forward-predict`):
     {:mean     predicted-next-macro-state  (deterministic kernel output)
      :variance {channel sigma2}             (per-channel prediction variance)}

   Faithfulness tag: FEP-derived (R4 — one pure forward kernel shared by
   live-step and prediction).  The MetaCA's structural advantage over the ant
   port is that `run-mmca` is already a real generative kernel — R4 wraps it,
   it does not have to be factored out of a mutating world."
  (:require [futon5.ca.core :as ca]
            [futon5.mmca.runtime :as runtime]
            [futon5.mmca.metrics :as metrics]))

;; --------------------------------------------------------------------------- ;;
;; Action → exotype-param translation.  This is the MetaCA action vocabulary
;; (R6): {:rotate-up :rotate-down :hold}.
;;
;; MEASURED ACTUATOR (2026-07-15).  The previous vocabulary was
;; {:pressure-up :pressure-down :selectivity-up :selectivity-down :hold},
;; actuating :update-prob and :match-threshold.  BOTH are inert — measured
;; paired (same seed, knob varied), 12 seeds, every sigil, both exotype modes:
;; effect EXACTLY 0.000, trajectory byte-identical.  Mechanism:
;;   - :update-prob     — exotype.clj:748-750 takes the context's :mutation-bias
;;                        whenever physics-params exist; the :update-prob read is
;;                        an else-branch that never fires.
;;   - :match-threshold — exotype.clj:756 puts it in ctx, but
;;                        ca/mutate-kernel-spec-contextual ignores it: the output
;;                        kernel spec is byte-identical at 0.0 / 0.5 / 1.0.
;; Only :mix-mode/:mix-shift propagate into the kernel spec (exotype.clj:767-771),
;; and they are LIVE: paired SNR 3.05, moves the trajectory 12/12 seeds.
;;
;; The live actuator is exactly the cyclic group Z/8 acting on the 8-bit sigil.
;; Verified to 1e-9 on all pairs: rotate-left k ≡ rotate-right (8-k).  So the
;; honest parameterisation is ONE signed rotation r ∈ Z/8, realised as
;; (:mix-mode :rotate-left, :mix-shift r); r=0 is the identity.  All 8 rotations
;; give distinct (pressure, selectivity), so this covers the whole reachable set
;; with no redundant dimension.
;;
;; Actions are named for what they DO, not for an intended effect.  A 1-D
;; rotation cannot independently raise pressure — pressure and selectivity are
;; both functions of the same scalar r — so an action named :pressure-up would
;; be writing a promise the plant cannot keep.  The forward model below is what
;; discovers which rotation achieves which macro-state.
;;
;; NOTE :mix-mode is only live when :mix-shift ≠ 0 (rotate-by-zero IS identity),
;; so r=0 is a degenerate corner where the mode knob has no authority.  Forcing
;; :mix-mode :rotate-left here means a :none-mode exotype (e.g. the 一 sigil,
;; which derives :mix-mode :none) is still controllable.
;; --------------------------------------------------------------------------- ;;

(def rotation-modulus
  "The sigil is 8 bits, so rotations live in Z/8.  Wrapping (not clamping) is
   the true geometry of the actuator: clamping would invent a boundary the
   plant does not have."
  8)

(defn- rotate-by
  "Step the exotype's rotation by `delta` within Z/8, forcing the mix-mode that
   makes the rotation live."
  [p delta]
  (assoc p
         :mix-mode :rotate-left
         :mix-shift (mod (+ (long (or (:mix-shift p) 0)) delta) rotation-modulus)))

(defn apply-action-to-params
  "Translate a candidate action (or sequence of actions) into exotype param
   deltas.  The forward model and the live controller share these action
   semantics, so this is the single definition of what the tokamak can DO."
  [params action]
  (let [actions (if (sequential? action) action [action])
        apply-one (fn [p a]
                    (case a
                      :rotate-up   (rotate-by p 1)
                      :rotate-down (rotate-by p -1)
                      ;; :hold and any unknown action → no change
                      p))]
    (reduce apply-one (or params {}) actions)))

;; --------------------------------------------------------------------------- ;;
;; Macro-state: the controller's per-tick state, projected to what the forward
;; model needs.  This is the R1 belief substrate (to be enriched in Slice 2+).
;; --------------------------------------------------------------------------- ;;

(defn macro-state
  "Build a macro-state snapshot from the controller's loop state.
   A macro-state captures everything `run-mmca` needs to advance the CA:
   :genotype :phenotype :kernel :exotype :metrics-history :gen-history
   :phe-history."
  [{:keys [genotype phenotype kernel exotype
           metrics-history gen-history phe-history]}]
  {:genotype genotype
   :phenotype phenotype
   :kernel kernel
   :exotype exotype
   :metrics-history metrics-history
   :gen-history gen-history
   :phe-history phe-history})

;; --------------------------------------------------------------------------- ;;
;; Forward-predict: the R4 seam.
;; --------------------------------------------------------------------------- ;;

(def ^:private default-forward-generations
  "Window length for a single forward prediction step."
  10)

(def ^:private default-predict-noise
  "Per-channel noise stdev for forward-predict v1 (Gaussian band).
   These are principled approximations of observation uncertainty — adequate
   for v1; replaced by tracked posterior variance in Slice 3 (R7)."
  {:pressure 0.05
   :selectivity 0.05
   :structure 0.05
   :activity 0.05})

(defn forward-predict
  "Predict the next macro-feature distribution by calling the real CA kernel
   (`run-mmca`) and projecting to macro-features via `windowed-macro-features`.

   Args:
     state     — a macro-state map (see `macro-state`).
     action    — a candidate action keyword or vector of actions
                 (from R6: :rotate-up, :rotate-down, :hold).
     opts      — optional map:
       :generations  window length (default 10)
       :seed         RNG seed for the CA step (default 42)
       :W            windowed-macro-features window size (default :generations)
       :S            windowed-macro-features stride (default :W)
       :noise        override per-channel noise stdev map
       :lesion       optional lesion map passed to run-mmca

   Returns:
     {:mean     predicted-next-macro-state
                 (the deterministic macro-feature map from windowed-macro-features:
                  {:pressure :selectivity :structure :activity :regime ...})
      :variance {channel sigma2}  (per-channel prediction variance)
      :next-state macro-state for the subsequent tick
                  (genotype/phenotype/exotype/history advanced by the step)}

   The mean equals the deterministic kernel next-macro-state exactly.  The
   variance is a simple per-channel Gaussian noise model — adequate for v1."
  ([state action]
   (forward-predict state action {}))
  ([state action {:keys [generations seed W S noise lesion]
                  :or {generations default-forward-generations
                       seed 42}}]
   (let [;; Apply the action to the exotype params (the control input).
         exotype (:exotype state)
         params-before (get-in exotype [:params])
         params-after (apply-action-to-params params-before action)
         exotype' (if params-after
                    (assoc exotype :params params-after)
                    exotype)
         ;; Run the real CA kernel forward.
         ;;
         ;; ca/with-seed is what makes R4 ("one PURE forward kernel") true.
         ;; Without it this function is not pure: run-mmca's :seed only drives
         ;; the exotype rng, while the CA dynamics draw from the GLOBAL rand, so
         ;; the result depends on how much RNG the process happened to consume
         ;; first.  That silently confounds the experiment it is used in — the
         ;; :aif arm burns global RNG running rollouts before every choice and
         ;; the :null arm does not, so the two arms' trajectories diverge for
         ;; reasons unrelated to control.  Observed before this fix: a trial in
         ;; which :aif chose :hold for ALL 12 windows (an action-for-action
         ;; identical arm to :null) still reported a different distance from
         ;; null — the two arms were not comparable at all.
         ;;
         ;; Seeding here makes forward-predict a pure function of
         ;; (state, action, seed), so identical action sequences give identical
         ;; trajectories and aif-vs-null is a PAIRED comparison.
         result (ca/with-seed seed
                  (runtime/run-mmca
                   {:genotype (:genotype state)
                  :phenotype (:phenotype state)
                  :generations generations
                  :kernel (:kernel state)
                  :lock-kernel false
                  ;; SCOPE DECISION (2026-07-15): the tokamak runs on the
                  ;; operator-free plant.  Without this, `resolve-operators`
                  ;; derives operators FROM THE GENOTYPE, and BlendHand
                  ;; (operators.clj:257) sets :kernel :blend-hand — a kernel
                  ;; ca/kernel-spec-for does not know.  That throw is swallowed
                  ;; at runtime.clj:540, kernel-spec goes nil, and the whole
                  ;; exotype path short-circuits: the actuator is DEAD and the
                  ;; run still reports success.  Measured: 0 exotype mutations
                  ;; with genotype-derived operators, 105 with :operators [].
                  ;;
                  ;; So the exotype actuator and kernel-overwriting operators are
                  ;; INCOMPATIBLE, and this is a real scope limit, not a tidy-up:
                  ;; the tokamak currently cannot steer a plant whose operators
                  ;; rewrite the kernel.  The principled fix is to give such
                  ;; kernels a spec (ca/kernel-specs) so the exotype can act on
                  ;; them; until then the experiment is honestly scoped to the
                  ;; operator-free plant rather than silently running with no
                  ;; actuator.
                  :operators []
                  :exotype exotype'
                  ;; :inline is marked "(deprecated)" in runtime.clj, but it is
                  ;; the CORRECT mode here and the only one where this actuator
                  ;; acts: :local-physics SKIPS the exotype entirely
                  ;; (runtime.clj:750-754 routes to local-physics/advance-state,
                  ;; which never receives it).
                  :exotype-mode :inline
                  :seed seed
                  :lesion lesion}))
         ;; Project to macro-features (the R2 observation ABI).
         metrics-history' (into (or (:metrics-history state) [])
                                (:metrics-history result))
         gen-history' (into (or (:gen-history state) [])
                            (:gen-history result))
         phe-history' (into (or (:phe-history state) [])
                            (:phe-history result))
         window-size (or W generations)
         window-stride (or S window-size)
         windows (metrics/windowed-macro-features
                  {:metrics-history metrics-history'
                   :gen-history gen-history'
                   :phe-history phe-history'}
                  {:W window-size :S window-stride})
         predicted-mean (or (last windows)
                            {:pressure nil
                             :selectivity nil
                             :structure nil
                             :activity nil
                             :regime nil})
         ;; Per-channel variance (v1: fixed noise band).
         noise-map (or noise default-predict-noise)
         variance (into {}
                        (for [[ch sd] noise-map]
                          [ch (* (double sd) (double sd))]))
         ;; Build the next macro-state (for chained predictions / rollout).
         next-state {:genotype (or (last (:gen-history result)) (:genotype state))
                     :phenotype (or (last (:phe-history result)) (:phenotype state))
                     :kernel (:kernel state)
                     :exotype exotype'
                     :metrics-history metrics-history'
                     :gen-history gen-history'
                     :phe-history phe-history'}]
     {:mean predicted-mean
      :variance variance
      :next-state next-state
      :run-result result})))
