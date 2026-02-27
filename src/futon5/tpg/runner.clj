(ns futon5.tpg.runner
  "TPG-controlled MMCA runner.

   Replaces the flat xenotype controller with a TPG that routes among
   hexagram-aligned operators based on generation-level diagnostics.

   Per D1: routing happens once per generation (not per cell).
   Per D2: operators are 1:1 with hexagram families.

   Usage:
     (require '[futon5.tpg.runner :as tpg-runner])
     (require '[futon5.tpg.core :as tpg])

     (tpg-runner/run-tpg
       {:genotype \"一二三四五六七八\"
        :generations 50
        :tpg (tpg/seed-tpg-hierarchical)
        :seed 42})"
  (:require [futon5.ca.core :as ca]
            [futon5.mmca.bitplane-analysis :as bitplane]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.local-physics :as local-physics]
            [futon5.tpg.core :as tpg]
            [futon5.tpg.diagnostics :as diag]
            [futon5.tpg.verifiers :as verifiers]
            [futon5.wiring.runtime :as wrt]))

;; =============================================================================
;; GENERATION STEP
;; =============================================================================

(defn- advance-generation
  "Advance one generation using a global rule and bend mode from TPG routing.

   Takes the current state and returns the next state with updated
   genotype, phenotype, and history."
  [state global-rule bend-mode]
  (local-physics/advance-state state global-rule bend-mode))

;; =============================================================================
;; WIRING-DIAGRAM EVOLUTION
;; =============================================================================

(defn- advance-generation-wiring
  "Advance one generation using a wiring diagram."
  [state diagram]
  (let [genotype (:genotype state)
        phenotype (:phenotype state)
        prev-genotype (when (> (count (get-in state [:history :genotypes])) 1)
                        (nth (get-in state [:history :genotypes])
                             (- (count (get-in state [:history :genotypes])) 2)))
        next-gen (wrt/evolve-genotype diagram genotype prev-genotype)
        next-phe (when phenotype
                   (ca/evolve-phenotype-against-genotype genotype phenotype))]
    (-> state
        (assoc :generation (inc (:generation state))
               :genotype next-gen)
        (cond-> next-phe (assoc :phenotype next-phe))
        (update-in [:history :genotypes] conj next-gen)
        (cond-> next-phe
          (update-in [:history :phenotypes]
                     (fn [hist] (if hist (conj hist next-phe) [next-phe])))))))

;; =============================================================================
;; TEMPORAL SCHEDULE
;; =============================================================================

(defn- schedule-operator
  "Get operator from temporal schedule at generation t."
  [schedule t]
  (let [cycle-len (reduce + (map :steps schedule))
        pos (mod t cycle-len)]
    (loop [remaining pos
           entries schedule]
      (let [{:keys [operator steps]} (first entries)]
        (if (< remaining steps)
          operator
          (recur (- remaining steps) (rest entries)))))))

;; =============================================================================
;; WIRING OPERATOR LOADING
;; =============================================================================

(defn load-wiring-operators
  "Load wiring diagrams from file paths into operator map.

   path-map: {operator-id file-path}
   Returns: {operator-id {:diagram ... :meta ...}}"
  [path-map]
  (into {} (map (fn [[k path]]
                  [k (wrt/load-wiring path)])
                path-map)))

;; =============================================================================
;; MAIN RUNNER
;; =============================================================================

(defn run-tpg
  "Run an MMCA simulation with TPG controller.

   opts:
   - :genotype (required) — starting genotype string
   - :phenotype (optional) — starting phenotype string
   - :generations (default 50) — number of generations
   - :tpg (required) — TPG structure (from tpg/core)
   - :verifier-spec (optional) — verifier target bands (default: tai-zone)
   - :seed (optional) — RNG seed for reproducibility
   - :progress-fn (optional) — callback for coarse progress events
   - :progress-every (optional, default 10) — emit generation progress every N generations

   Returns:
   {:gen-history [string ...]
    :phe-history [string ...] or nil
    :diagnostics-trace [diagnostic-map ...]
    :routing-trace [{:generation :operator-id :route :depth :fallback?} ...]
    :verifier-result {:satisfaction-vector :per-generation :overall-satisfaction}
    :generations int
    :seed long
    :tpg-id string
    :config map}"
  [{:keys [genotype phenotype generations tpg verifier-spec seed
           progress-fn progress-every progress-phases?] :as opts}]
  (when-not (seq genotype)
    (throw (ex-info "TPG runner requires a starting genotype" {:opts (keys opts)})))
  (when-not tpg
    (throw (ex-info "TPG runner requires a TPG" {:opts (keys opts)})))
  (let [generations (or generations 50)
        progress-every (max 1 (int (or progress-every 10)))
        seed (or seed (System/nanoTime))
        rng (java.util.Random. (long seed))
        verifier-spec (or verifier-spec verifiers/default-spec)

        ;; Validate TPG before running
        extra-ops (set (keys (:wiring-operators opts)))
        validation (tpg/validate-tpg tpg {:extra-operator-ids extra-ops})
        _ (when-not (:valid? validation)
            (throw (ex-info "TPG validation failed"
                            {:errors (:errors validation)})))

        ;; Initialize state
        initial-state {:generation 0
                       :genotype genotype
                       :phenotype phenotype
                       :history {:genotypes [genotype]
                                 :phenotypes (when phenotype [phenotype])}}

        ;; Generation loop
        result
        (binding [exotype/*exotype-system* :local-physics]
          (loop [state initial-state
                 diagnostics-trace []
                 routing-trace []
                 gen 0]
            (if (>= gen generations)
              {:state state
               :diagnostics-trace diagnostics-trace
               :routing-trace routing-trace}

              ;; 1. Compute diagnostic from current state
              (let [gen-history (get-in state [:history :genotypes])
                    prev-genotype (when (> (count gen-history) 1)
                                    (nth gen-history (- (count gen-history) 2)))
                    _ (when (and progress-fn progress-phases?)
                        (progress-fn {:event :phase
                                      :generation (inc gen)
                                      :phase :diagnostic-start}))
                    ;; Recent history for damage spread (up to 3 generations)
                    recent-start (max 0 (- (count gen-history) 3))
                    recent-history (subvec (vec gen-history) recent-start)
                    diagnostic (diag/compute-diagnostic
                                (:genotype state)
                                prev-genotype
                                (:phenotype state)
                                recent-history)
                    _ (when (and progress-fn progress-phases?)
                        (progress-fn {:event :phase
                                      :generation (inc gen)
                                      :phase :routing-start}))

                    ;; 2. Route through TPG (or temporal schedule)
                    ;; Priority: TPG's evolved schedule > opts override > routing
                    routing (tpg/route tpg (:vector diagnostic))
                    operator-id (cond
                                  (:temporal-schedule tpg)
                                  (schedule-operator (:temporal-schedule tpg) gen)
                                  (:temporal-schedule opts)
                                  (schedule-operator (:temporal-schedule opts) gen)
                                  :else
                                  (:operator-id routing))
                    routing (assoc routing :operator-id operator-id)
                    _ (when (and progress-fn progress-phases?)
                        (progress-fn {:event :phase
                                      :generation (inc gen)
                                      :phase :advance-start
                                      :operator-id operator-id}))

                    ;; 3. Check if this is a wiring operator
                    wiring-ops (:wiring-operators opts)
                    wiring-entry (get wiring-ops operator-id)
                    wiring-diagram (when wiring-entry (:diagram wiring-entry))

                    ;; 4. Advance the world
                    next-state (if wiring-diagram
                                 (advance-generation-wiring state wiring-diagram)
                                 (let [global-rule (tpg/operator->global-rule operator-id)
                                       bend-mode (tpg/operator->bend-mode operator-id)]
                                   (advance-generation state global-rule bend-mode)))
                    _ (when (and progress-fn progress-phases?)
                        (progress-fn {:event :phase
                                      :generation (inc gen)
                                      :phase :advance-done
                                      :operator-id operator-id}))

                    ;; Track
                    routing-entry (assoc routing :generation gen)
                    diagnostics-trace' (conj diagnostics-trace diagnostic)
                    routing-trace' (conj routing-trace routing-entry)
                    next-gen (inc gen)
                    _ (when (and progress-fn
                                 (or (= next-gen generations)
                                     (zero? (mod next-gen progress-every))))
                        (progress-fn {:event :generation
                                      :generation next-gen
                                      :generations generations}))]

                (recur next-state
                       diagnostics-trace'
                       routing-trace'
                       next-gen)))))

        ;; 5. Compute final diagnostic for the last generation
        final-state (:state result)
        final-gen-history (get-in final-state [:history :genotypes])
        final-prev (when (> (count final-gen-history) 1)
                     (nth final-gen-history (- (count final-gen-history) 2)))
        final-recent-start (max 0 (- (count final-gen-history) 3))
        final-recent (subvec (vec final-gen-history) final-recent-start)
        final-diagnostic (diag/compute-diagnostic
                          (:genotype final-state) final-prev
                          (:phenotype final-state) final-recent)
        all-diagnostics (conj (:diagnostics-trace result) final-diagnostic)

        ;; 6. Compute extended diagnostics and attach to all diagnostics
        run-result {:gen-history final-gen-history}
        extended (diag/compute-extended-diagnostic run-result
                   {:spatial? (boolean (:spatial-coupling? opts))})
        all-diagnostics-ext (mapv #(assoc % :extended extended) all-diagnostics)

        ;; 7. Evaluate verifiers over entire trace (with extended diagnostics)
        verifier-result (verifiers/evaluate-run all-diagnostics-ext verifier-spec)]

    {:gen-history (get-in final-state [:history :genotypes])
     :phe-history (get-in final-state [:history :phenotypes])
     :diagnostics-trace all-diagnostics-ext
     :routing-trace (:routing-trace result)
     :verifier-result verifier-result
     :generations generations
     :seed seed
     :tpg-id (:tpg/id tpg)
     :config {:verifier-spec verifier-spec
              :routing-frequency :per-generation}}))

;; =============================================================================
;; BATCH RUNNER (for evolution)
;; =============================================================================

(defn run-tpg-batch
  "Run multiple MMCA simulations with the same TPG but different seeds.

   Returns a vector of run results, plus aggregate statistics."
  [{:keys [tpg genotypes generations verifier-spec base-seed n-runs
           wiring-operators temporal-schedule spatial-coupling?
           coupling-stability? progress-fn progress-every progress-phases?]}]
  (let [n-runs (or n-runs (count genotypes))
        base-seed (or base-seed 42)
        genotypes (or genotypes (repeat n-runs (ca/random-sigil-string 32)))
        generations (or generations 50)
        verifier-spec (or verifier-spec verifiers/default-spec)

        runs (mapv (fn [i genotype]
                     (let [run-idx (inc i)
                           _ (when progress-fn
                               (progress-fn {:event :run-start
                                             :run run-idx
                                             :n-runs n-runs}))
                           t0 (System/currentTimeMillis)
                           run-progress (when progress-fn
                                          (fn [m]
                                            (progress-fn (merge {:run run-idx
                                                                 :n-runs n-runs}
                                                                m))))
                           run (run-tpg (cond-> {:genotype genotype
                                                 :generations generations
                                                 :tpg tpg
                                                 :verifier-spec verifier-spec
                                                 :seed (+ base-seed i)}
                                          wiring-operators (assoc :wiring-operators wiring-operators)
                                          temporal-schedule (assoc :temporal-schedule temporal-schedule)
                                          spatial-coupling? (assoc :spatial-coupling? spatial-coupling?)
                                          run-progress (assoc :progress-fn run-progress)
                                          progress-phases? (assoc :progress-phases? progress-phases?)
                                          progress-every (assoc :progress-every progress-every)))
                           elapsed (/ (- (System/currentTimeMillis) t0) 1000.0)
                           _ (when progress-fn
                               (progress-fn {:event :run-end
                                             :run run-idx
                                             :n-runs n-runs
                                             :elapsed-s elapsed
                                             :overall (get-in run [:verifier-result :overall-satisfaction])}))]
                       run))
                   (range n-runs)
                   genotypes)

        ;; Aggregate satisfaction vectors
        sat-vecs (mapv #(get-in % [:verifier-result :satisfaction-vector]) runs)
        verifier-keys (keys verifier-spec)
        mean-satisfaction
        (into {} (map (fn [vk]
                        (let [vals (keep #(get % vk) sat-vecs)
                              n (count vals)]
                          [vk (if (pos? n)
                                (/ (reduce + 0.0 vals) (double n))
                                0.0)]))
                      verifier-keys))
        overall-mean (let [vals (map #(get-in % [:verifier-result :overall-satisfaction]) runs)]
                       (/ (reduce + 0.0 vals) (double (count vals))))

        ;; Optional: cross-run coupling stability
        coupling-stability
        (when (and coupling-stability? (> n-runs 1))
          (let [couplings (mapv (fn [run]
                                  (bitplane/coupling-spectrum
                                   (:gen-history run)
                                   {:spatial? false :temporal? false}))
                                runs)
                mis (mapv :mean-coupling couplings)
                mi-mean (/ (reduce + 0.0 mis) (double (count mis)))
                mi-var (/ (reduce + 0.0 (map #(let [d (- (double %) mi-mean)]
                                                 (* d d)) mis))
                          (double (max 1 (dec (count mis)))))
                mi-cv (if (pos? mi-mean) (/ (Math/sqrt mi-var) mi-mean) 0.0)]
            {:coupling-stability (- 1.0 (min 1.0 mi-cv))
             :coupling-mi-mean mi-mean
             :coupling-mi-cv mi-cv}))

        ;; Merge coupling stability into mean satisfaction if present
        mean-satisfaction (if coupling-stability
                           (assoc mean-satisfaction
                                  :coupling-stability (:coupling-stability coupling-stability))
                           mean-satisfaction)]
    {:runs runs
     :n-runs n-runs
     :mean-satisfaction mean-satisfaction
     :overall-mean overall-mean
     :satisfaction-vectors sat-vecs
     :coupling-stability coupling-stability}))

;; =============================================================================
;; SUMMARY / INSPECTION
;; =============================================================================

(defn summarize-routing
  "Summarize routing decisions across a run.

   Returns operator usage frequencies, fallback rate, and route depth stats."
  [routing-trace]
  (let [n (count routing-trace)
        operators (map :operator-id routing-trace)
        operator-freq (frequencies operators)
        fallback-count (count (filter :fallback? routing-trace))
        depths (map :depth routing-trace)
        routes (map :route routing-trace)]
    {:operator-frequency operator-freq
     :operator-entropy (let [total (double n)
                             probs (map #(/ (double %) total) (vals operator-freq))]
                         (- (reduce + 0.0 (map #(if (pos? %) (* % (/ (Math/log %) (Math/log 2.0))) 0.0) probs))))
     :fallback-rate (if (pos? n) (/ (double fallback-count) (double n)) 0.0)
     :mean-depth (if (pos? n) (/ (reduce + 0.0 depths) (double n)) 0.0)
     :max-depth (if (seq depths) (apply max depths) 0)
     :unique-routes (count (set routes))
     :total-decisions n}))

(defn print-run-summary
  "Print a human-readable summary of a TPG run."
  [{:keys [verifier-result routing-trace generations tpg-id seed] :as run}]
  (let [routing-summary (summarize-routing routing-trace)]
    (println "\n=== TPG Run Summary ===")
    (println "TPG:" tpg-id "| Generations:" generations "| Seed:" seed)
    (println)
    (println "--- Verifier Satisfaction ---")
    (doseq [[k v] (sort-by key (:satisfaction-vector verifier-result))]
      (printf "  %-20s %.3f%n" (name k) (double v)))
    (printf "  %-20s %.3f%n" "OVERALL" (double (:overall-satisfaction verifier-result)))
    (println)
    (println "--- Routing ---")
    (printf "  Operator entropy:  %.2f bits%n" (:operator-entropy routing-summary))
    (printf "  Fallback rate:     %.3f%n" (:fallback-rate routing-summary))
    (printf "  Mean route depth:  %.1f%n" (:mean-depth routing-summary))
    (printf "  Unique routes:     %d%n" (:unique-routes routing-summary))
    (println "  Operator usage:")
    (doseq [[op count] (sort-by (comp - val) (:operator-frequency routing-summary))]
      (printf "    %-20s %d (%.0f%%)%n"
              (name op) count
              (* 100.0 (/ (double count) (double (:total-decisions routing-summary))))))
    (println)))
