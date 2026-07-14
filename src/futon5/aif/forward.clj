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
  (:require [futon5.mmca.runtime :as runtime]
            [futon5.mmca.metrics :as metrics]))

;; --------------------------------------------------------------------------- ;;
;; Action → exotype-param translation (mirrors cyber_mmca_compare/adjust-params).
;; This is the MetaCA action vocabulary (R6): {:pressure-up :pressure-down
;; :selectivity-up :selectivity-down :hold}.
;; --------------------------------------------------------------------------- ;;

(def ^:private pressure-step 0.1)
(def ^:private select-step 0.1)

(defn- clamp
  [x lo hi]
  (max lo (min hi x)))

(defn apply-action-to-params
  "Translate a candidate action (or sequence of actions) into exotype param
   deltas.  Mirrors `cyber-mmca-compare/adjust-params` exactly so the forward
   model and the live controller share the same action semantics."
  [params action]
  (let [actions (if (sequential? action) action [action])
        apply-one (fn [p a]
                    (case a
                      :pressure-up
                      (update p :update-prob
                              #(clamp (+ (double (or % 1.0)) pressure-step) 0.05 1.0))
                      :pressure-down
                      (update p :update-prob
                              #(clamp (- (double (or % 1.0)) pressure-step) 0.05 1.0))
                      :selectivity-up
                      (update p :match-threshold
                              #(clamp (+ (double (or % 0.5)) select-step) 0.0 1.0))
                      :selectivity-down
                      (update p :match-threshold
                              #(clamp (- (double (or % 0.5)) select-step) 0.0 1.0))
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
                 (from R6: :pressure-up/-down, :selectivity-up/-down, :hold).
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
         result (runtime/run-mmca
                 {:genotype (:genotype state)
                  :phenotype (:phenotype state)
                  :generations generations
                  :kernel (:kernel state)
                  :lock-kernel false
                  :exotype exotype'
                  :exotype-mode :inline
                  :seed seed
                  :lesion lesion})
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
