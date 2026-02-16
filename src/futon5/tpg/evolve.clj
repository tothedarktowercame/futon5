(ns futon5.tpg.evolve
  "TPG evolution via Pareto selection on verifier constraint satisfaction.

   Evolves a population of TPGs using (μ+λ) with Pareto ranking.
   No scalar fitness — selection pressure comes from multi-objective
   constraint satisfaction over verifier specs.

   Mutation operators at three levels:
   - Program: weight perturbation, bias shift, action reassignment
   - Team: program addition, deletion, replacement
   - Graph: team addition, deletion, edge rewiring

   All mutations maintain structural invariants (reachability, size bounds)."
  (:require [clojure.set :as set]
            [futon5.ca.core :as ca]
            [futon5.tpg.core :as tpg]
            [futon5.tpg.diagnostics :as diag]
            [futon5.tpg.runner :as runner]
            [futon5.tpg.verifiers :as verifiers]))

;; =============================================================================
;; CONFIGURATION
;; =============================================================================

(def default-config
  {:mu 8               ; parent population size
   :lambda 8           ; offspring per generation
   :eval-runs 5        ; MMCA runs per TPG evaluation
   :eval-generations 50 ; generations per MMCA run
   :genotype-length 32
   :evo-generations 20  ; number of evolutionary generations
   :verifier-spec verifiers/default-spec
   :max-teams 8
   :max-programs-per-team 6
   :weight-sigma 0.15   ; std dev for weight perturbation
   :bias-sigma 0.1
   :verbose-eval? false       ; print per-candidate evaluation progress
   :verbose-eval-runs? false  ; print progress inside each candidate run
   :verbose-eval-gen-every 10 ; generation heartbeat interval for inner progress
   :verbose-eval-phases? false}) ; print inner generation phase boundaries

;; =============================================================================
;; RNG HELPERS
;; =============================================================================

(defn- rng-double [^java.util.Random rng]
  (.nextDouble rng))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng (max 1 (int n))))

(defn- rng-gaussian [^java.util.Random rng]
  (.nextGaussian rng))

(defn- rng-choice [^java.util.Random rng coll]
  (nth (vec coll) (rng-int rng (count coll))))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

;; =============================================================================
;; PROGRAM-LEVEL MUTATION
;; =============================================================================

(defn- perturb-weights
  "Perturb program weights with Gaussian noise."
  [^java.util.Random rng weights sigma]
  (mapv (fn [w]
          (+ (double w) (* sigma (rng-gaussian rng))))
        weights))

(defn- perturb-bias
  "Perturb program bias with Gaussian noise."
  [^java.util.Random rng bias sigma]
  (+ (double bias) (* sigma (rng-gaussian rng))))

(defn- random-action
  "Generate a random action: route to a team or execute an operator.
   Includes wiring operator IDs from config if present."
  [^java.util.Random rng team-ids config]
  (let [exo-ids (mapv :operator-id tpg/operator-table)
        wiring-ids (vec (keys (:wiring-operators config)))
        all-ids (into exo-ids wiring-ids)]
    (if (and (seq team-ids) (< (rng-double rng) 0.3))
      {:type :team :target (rng-choice rng team-ids)}
      {:type :operator :target (rng-choice rng all-ids)})))

(defn- mutate-program
  "Mutate a single program. Mutation type chosen randomly:
   0: weight perturbation
   1: bias shift
   2: action reassignment
   3: weight perturbation + bias shift"
  [^java.util.Random rng program team-ids config]
  (let [mutation-type (rng-int rng 4)]
    (case mutation-type
      0 (assoc program :weights (perturb-weights rng (:weights program) (:weight-sigma config)))
      1 (assoc program :bias (perturb-bias rng (:bias program) (:bias-sigma config)))
      2 (assoc program :action (random-action rng team-ids config))
      3 (-> program
            (assoc :weights (perturb-weights rng (:weights program) (:weight-sigma config)))
            (assoc :bias (perturb-bias rng (:bias program) (:bias-sigma config)))))))

;; =============================================================================
;; TEAM-LEVEL MUTATION
;; =============================================================================

(defn- random-program
  "Generate a new random program."
  [^java.util.Random rng team-ids config]
  (let [weights (vec (repeatedly diag/diagnostic-dim #(* 2.0 (- (rng-double rng) 0.5))))
        bias (* 0.5 (- (rng-double rng) 0.5))
        action (random-action rng team-ids config)]
    (tpg/make-program (keyword (str "p-" (rng-int rng 10000)))
                      weights bias
                      (:type action)
                      (:target action))))

(defn- mutate-team
  "Mutate a team. Mutation type chosen randomly:
   0: mutate one program (weight/bias/action)
   1: add a program (if under limit)
   2: delete a program (if more than 2)
   3: replace a program"
  [^java.util.Random rng team team-ids config]
  (let [programs (:programs team)
        mutation-type (rng-int rng 4)
        max-programs (:max-programs-per-team config)]
    (case mutation-type
      ;; Mutate one program
      0 (let [idx (rng-int rng (count programs))
              mutated (mutate-program rng (nth programs idx) team-ids config)]
          (assoc team :programs (assoc (vec programs) idx mutated)))

      ;; Add a program
      1 (if (< (count programs) max-programs)
          (let [new-prog (random-program rng team-ids config)]
            (assoc team :programs (conj (vec programs) new-prog)))
          ;; At limit, mutate instead
          (let [idx (rng-int rng (count programs))
                mutated (mutate-program rng (nth programs idx) team-ids config)]
            (assoc team :programs (assoc (vec programs) idx mutated))))

      ;; Delete a program (keep at least 2)
      2 (if (> (count programs) 2)
          (let [idx (rng-int rng (count programs))]
            (assoc team :programs (vec (concat (subvec (vec programs) 0 idx)
                                               (subvec (vec programs) (inc idx))))))
          ;; Too few, mutate instead
          (let [idx (rng-int rng (count programs))
                mutated (mutate-program rng (nth programs idx) team-ids config)]
            (assoc team :programs (assoc (vec programs) idx mutated))))

      ;; Replace a program
      3 (let [idx (rng-int rng (count programs))
              new-prog (random-program rng team-ids config)]
          (assoc team :programs (assoc (vec programs) idx new-prog))))))

;; =============================================================================
;; GRAPH-LEVEL MUTATION
;; =============================================================================

(defn- fresh-team-id
  "Generate a unique team ID."
  [^java.util.Random rng existing-ids]
  (loop [attempts 0]
    (let [candidate (keyword (str "team-" (rng-int rng 10000)))]
      (if (or (contains? (set existing-ids) candidate) (> attempts 100))
        (keyword (str "team-" (System/nanoTime)))
        (if (contains? (set existing-ids) candidate)
          (recur (inc attempts))
          candidate)))))

(defn- mutate-graph-add-team
  "Add a new team to the TPG (if under max-teams)."
  [^java.util.Random rng tpg-graph config]
  (let [teams (:teams tpg-graph)
        team-ids (mapv :team/id teams)]
    (if (< (count teams) (:max-teams config))
      (let [new-id (fresh-team-id rng team-ids)
            ;; New team has 2-3 programs routing to operators
            n-programs (+ 2 (rng-int rng 2))
            programs (vec (repeatedly n-programs #(random-program rng team-ids config)))
            new-team (tpg/make-team new-id programs)
            ;; Also modify a random existing program to route to the new team
            target-team-idx (rng-int rng (count teams))
            target-team (nth teams target-team-idx)
            target-prog-idx (rng-int rng (count (:programs target-team)))
            updated-prog (assoc-in (nth (:programs target-team) target-prog-idx)
                                   [:action] {:type :team :target new-id})
            updated-team (assoc-in target-team [:programs target-prog-idx] updated-prog)
            updated-teams (assoc (vec teams) target-team-idx updated-team)]
        (assoc tpg-graph :teams (conj updated-teams new-team)))
      ;; At team limit, mutate a team instead
      (let [idx (rng-int rng (count teams))
            team-ids (mapv :team/id teams)
            mutated (mutate-team rng (nth teams idx) team-ids config)]
        (assoc tpg-graph :teams (assoc (vec teams) idx mutated))))))

(defn- mutate-graph-delete-team
  "Delete a non-root team (if more than 2 teams).
   Rewires any references to the deleted team to operator actions."
  [^java.util.Random rng tpg-graph]
  (let [teams (:teams tpg-graph)
        root-id (or (get-in tpg-graph [:config :root-team])
                    (:team/id (first teams)))]
    (if (> (count teams) 2)
      (let [;; Pick a non-root team to delete
            non-root (filterv #(not= (:team/id %) root-id) teams)
            victim-id (:team/id (rng-choice rng non-root))
            ;; Remove the victim team
            remaining (filterv #(not= (:team/id %) victim-id) teams)
            ;; Rewire any program that referenced the victim
            operator-ids (mapv :operator-id tpg/operator-table)
            rewire-action (fn [action]
                            (if (and (= :team (:type action))
                                     (= victim-id (:target action)))
                              {:type :operator :target (rng-choice rng operator-ids)}
                              action))
            rewired (mapv (fn [team]
                            (update team :programs
                                    (fn [progs]
                                      (mapv #(update % :action rewire-action) progs))))
                          remaining)]
        (assoc tpg-graph :teams rewired))
      ;; Too few teams, no-op
      tpg-graph)))

;; =============================================================================
;; TEMPORAL SCHEDULE MUTATION
;; =============================================================================

(defn- all-operator-ids
  "All operator IDs (exotype + wiring) available in config."
  [config]
  (into (mapv :operator-id tpg/operator-table)
        (keys (:wiring-operators config))))

(defn- random-schedule
  "Generate a random temporal schedule with 2-5 entries."
  [^java.util.Random rng config]
  (let [n (+ 2 (rng-int rng 4))
        ids (all-operator-ids config)]
    (vec (repeatedly n
           (fn []
             {:operator (rng-choice rng ids)
              :steps (+ 1 (rng-int rng 8))})))))

(defn- mutate-schedule
  "Mutate a temporal schedule.
   Operations: change operator, change steps, add entry, remove entry, swap."
  [^java.util.Random rng schedule config]
  (let [n (count schedule)
        op (rng-int rng 5)
        ids (all-operator-ids config)]
    (case (int op)
      ;; change one operator
      0 (let [idx (rng-int rng n)]
          (assoc-in (vec schedule) [idx :operator] (rng-choice rng ids)))
      ;; change one step count
      1 (let [idx (rng-int rng n)]
          (assoc-in (vec schedule) [idx :steps] (+ 1 (rng-int rng 8))))
      ;; add entry (max 8)
      2 (if (< n 8)
          (conj (vec schedule) {:operator (rng-choice rng ids)
                                :steps (+ 1 (rng-int rng 8))})
          schedule)
      ;; remove entry (min 2)
      3 (if (> n 2)
          (let [idx (rng-int rng n)]
            (vec (concat (subvec (vec schedule) 0 idx)
                         (subvec (vec schedule) (inc idx)))))
          schedule)
      ;; swap adjacent entries
      4 (if (> n 1)
          (let [idx (rng-int rng (dec n))
                s (vec schedule)]
            (assoc s idx (nth s (inc idx))
                     (inc idx) (nth s idx)))
          schedule))))

;; =============================================================================
;; TOP-LEVEL TPG MUTATION
;; =============================================================================

(defn mutate-tpg
  "Mutate a TPG. Selects mutation level randomly:
   55% program/team, 15% add team, 8% delete team,
   12% schedule mutation, 10% remove schedule.

   Always validates the result; retries on invalid mutations."
  [^java.util.Random rng tpg-graph config]
  (let [max-retries 5]
    (loop [attempt 0]
      (if (>= attempt max-retries)
        tpg-graph ;; give up, return unchanged
        (let [team-ids (mapv :team/id (:teams tpg-graph))
              r (rng-double rng)
              candidate
              (cond
                ;; 55%: program/team mutation
                (< r 0.55)
                (let [team-idx (rng-int rng (count (:teams tpg-graph)))
                      mutated-team (mutate-team rng
                                                (nth (:teams tpg-graph) team-idx)
                                                team-ids config)]
                  (assoc tpg-graph :teams
                         (assoc (vec (:teams tpg-graph)) team-idx mutated-team)))

                ;; 15%: add team
                (< r 0.70)
                (mutate-graph-add-team rng tpg-graph config)

                ;; 8%: delete team
                (< r 0.78)
                (mutate-graph-delete-team rng tpg-graph)

                ;; 12%: mutate or add temporal schedule
                (< r 0.90)
                (if (:temporal-schedule tpg-graph)
                  (assoc tpg-graph :temporal-schedule
                         (mutate-schedule rng (:temporal-schedule tpg-graph) config))
                  (assoc tpg-graph :temporal-schedule
                         (random-schedule rng config)))

                ;; 10%: remove schedule (force routing mode)
                :else
                (dissoc tpg-graph :temporal-schedule))

              extra-ops (set (keys (:wiring-operators config)))
              validation (tpg/validate-tpg candidate {:extra-operator-ids extra-ops})]
          (if (:valid? validation)
            candidate
            (recur (inc attempt))))))))

;; =============================================================================
;; CROSSOVER
;; =============================================================================

(defn crossover-tpg
  "Crossover two TPGs by transplanting programs between shared teams.

   Strategy: find teams with the same ID in both parents, then swap
   individual programs between them. This preserves graph structure
   (no dangling team references) while mixing program weights/actions.

   If no shared teams exist, copy program weights from parent-b's
   root into parent-a's root programs."
  [^java.util.Random rng parent-a parent-b]
  (let [teams-a (:teams parent-a)
        teams-b (:teams parent-b)
        ids-a (set (map :team/id teams-a))
        ids-b (set (map :team/id teams-b))
        shared (seq (set/intersection ids-a ids-b))]
    (let [;; Graph crossover
          child
          (if shared
            ;; Swap programs within a shared team
            (let [swap-id (rng-choice rng shared)
                  team-a (first (filter #(= swap-id (:team/id %)) teams-a))
                  team-b (first (filter #(= swap-id (:team/id %)) teams-b))
                  donor-prog (rng-choice rng (:programs team-b))
                  safe-action (let [{:keys [type target]} (:action donor-prog)]
                                (if (and (= type :team) (not (ids-a target)))
                                  {:type :operator :target (rng-choice rng (mapv :operator-id tpg/operator-table))}
                                  (:action donor-prog)))
                  safe-donor (assoc donor-prog :action safe-action)
                  replace-idx (rng-int rng (count (:programs team-a)))
                  updated-progs (assoc (vec (:programs team-a)) replace-idx safe-donor)
                  updated-team (assoc team-a :programs updated-progs)
                  updated-teams (mapv (fn [t]
                                        (if (= swap-id (:team/id t))
                                          updated-team t))
                                      teams-a)]
              (assoc parent-a :teams updated-teams))
            ;; No shared teams: transplant weights
            (let [root-a (first teams-a)
                  root-b (first teams-b)
                  progs-a (:programs root-a)
                  progs-b (:programs root-b)]
              (if (and (seq progs-a) (seq progs-b))
                (let [target-idx (rng-int rng (count progs-a))
                      source-prog (rng-choice rng progs-b)
                      updated-prog (assoc (nth progs-a target-idx)
                                          :weights (:weights source-prog)
                                          :bias (:bias source-prog))
                      updated-root (assoc root-a :programs
                                          (assoc (vec progs-a) target-idx updated-prog))
                      updated-teams (assoc (vec teams-a) 0 updated-root)]
                  (assoc parent-a :teams updated-teams))
                parent-a)))

          ;; Schedule crossover
          sched-a (:temporal-schedule parent-a)
          sched-b (:temporal-schedule parent-b)]
      (cond
        ;; Both have schedules: segment crossover
        (and sched-a sched-b)
        (let [cut-a (rng-int rng (count sched-a))
              cut-b (rng-int rng (count sched-b))
              new-sched (vec (concat (take (inc cut-a) sched-a)
                                     (drop cut-b sched-b)))]
          (assoc child :temporal-schedule
                 (if (and (seq new-sched) (<= (count new-sched) 8))
                   new-sched
                   sched-a)))
        ;; One has schedule: 50% inherit
        sched-a (if (< (rng-double rng) 0.5)
                  (assoc child :temporal-schedule sched-a)
                  child)
        sched-b (if (< (rng-double rng) 0.5)
                  (assoc child :temporal-schedule sched-b)
                  child)
        :else child))))

;; =============================================================================
;; EVALUATION
;; =============================================================================

(defn evaluate-tpg
  "Evaluate a TPG by running multiple MMCA simulations.

   Returns the TPG augmented with :satisfaction-vector and :overall-satisfaction."
  [tpg-graph config ^java.util.Random rng]
  (let [{:keys [eval-runs eval-generations genotype-length verifier-spec
                wiring-operators spatial-coupling? coupling-stability?
                verbose-eval-runs? verbose-eval-gen-every verbose-eval-phases?]} config
        progress-fn (when verbose-eval-runs?
                      (fn [{:keys [event run n-runs generation generations elapsed-s overall] :as evt}]
                        (case event
                          :run-start
                          (do (printf "      [run %d/%d] start%n" run n-runs)
                              (flush))
                          :generation
                          (do (printf "      [run %d/%d] gen %d/%d%n"
                                      run n-runs generation generations)
                              (flush))
                          :phase
                          (when verbose-eval-phases?
                            (do (printf "      [run %d/%d] phase %s gen=%d%s%n"
                                        run n-runs (name (:phase evt))
                                        (int (or generation 0))
                                        (if-let [op (:operator-id evt)]
                                          (str " op=" (name op))
                                          ""))
                                (flush)))
                          :run-end
                          (do (printf "      [run %d/%d] done in %.1fs (overall=%.3f)%n"
                                      run n-runs elapsed-s (double (or overall 0.0)))
                              (flush))
                          nil)))
        batch-result (runner/run-tpg-batch
                      (cond-> {:tpg tpg-graph
                               :n-runs eval-runs
                               :generations eval-generations
                               :verifier-spec verifier-spec
                               :base-seed (rng-int rng Integer/MAX_VALUE)}
                        wiring-operators (assoc :wiring-operators wiring-operators)
                        spatial-coupling? (assoc :spatial-coupling? true)
                        coupling-stability? (assoc :coupling-stability? true)
                        progress-fn (assoc :progress-fn progress-fn)
                        progress-fn (assoc :progress-phases? verbose-eval-phases?)
                        progress-fn (assoc :progress-every verbose-eval-gen-every)))
        mean-sat (:mean-satisfaction batch-result)
        overall (:overall-mean batch-result)]
    (assoc tpg-graph
           :satisfaction-vector mean-sat
           :overall-satisfaction overall
           :eval-runs eval-runs)))

;; =============================================================================
;; SELECTION
;; =============================================================================

(defn select-survivors
  "Select μ survivors from a combined (parents + offspring) population
   using Pareto ranking.

   Within the same Pareto front, prefer higher overall satisfaction."
  [population mu]
  (let [sat-vecs (mapv :satisfaction-vector population)
        ranked (verifiers/pareto-rank sat-vecs)
        ;; Annotate population with rank
        annotated (mapv (fn [individual rank-entry]
                          (assoc individual
                                 :pareto-rank (:rank rank-entry)))
                        population ranked)
        ;; Sort by Pareto rank (lower is better), then by overall satisfaction (higher is better)
        sorted (sort-by (juxt :pareto-rank (comp - :overall-satisfaction))
                        annotated)
        survivors (vec (take mu sorted))]
    survivors))

;; =============================================================================
;; INITIAL POPULATION
;; =============================================================================

(defn- random-tpg
  "Generate a random TPG with 1-3 teams.
   Guarantees reachability by ensuring each team has at least one
   program routing to an operator."
  [^java.util.Random rng config]
  (let [n-teams (+ 1 (rng-int rng 3))
        team-ids (mapv (fn [i] (keyword (str "team-" i))) (range n-teams))
        exo-ids (mapv :operator-id tpg/operator-table)
        wiring-ids (vec (keys (:wiring-operators config)))
        all-operator-ids (into exo-ids wiring-ids)
        teams (mapv (fn [tid]
                      (let [n-progs (+ 2 (rng-int rng 3))
                            ;; First program always routes to an operator (reachability)
                            anchor (let [w (vec (repeatedly diag/diagnostic-dim
                                                            #(* 2.0 (- (rng-double rng) 0.5))))
                                         b (* 0.5 (- (rng-double rng) 0.5))]
                                     (tpg/make-program
                                      (keyword (str "p-anchor-" (rng-int rng 10000)))
                                      w b :operator (rng-choice rng all-operator-ids)))
                            ;; Remaining programs can route to teams or operators
                            rest-progs (vec (repeatedly (dec n-progs)
                                                        #(random-program rng team-ids config)))]
                        (tpg/make-team tid (into [anchor] rest-progs))))
                    team-ids)]
    (cond-> (tpg/make-tpg (str "random-" (rng-int rng 10000))
                          teams
                          {:root-team (first team-ids)})
      ;; 30% chance of starting with a random temporal schedule
      (< (rng-double rng) 0.3)
      (assoc :temporal-schedule (random-schedule rng config)))))

(defn initial-population
  "Generate initial population: mix of seed TPGs and random TPGs."
  [^java.util.Random rng config]
  (let [mu (:mu config)
        ;; Always include both seed TPGs
        seeds [(tpg/seed-tpg-simple)
               (tpg/seed-tpg-hierarchical)]
        n-random (max 0 (- mu (count seeds)))
        randoms (loop [acc []
                       attempts 0]
                  (if (or (>= (count acc) n-random) (> attempts (* 3 n-random)))
                    (vec (take n-random acc))
                    (let [candidate (random-tpg rng config)
                          extra-ops (set (keys (:wiring-operators config)))
                          valid? (:valid? (tpg/validate-tpg candidate {:extra-operator-ids extra-ops}))]
                      (recur (if valid? (conj acc candidate) acc)
                             (inc attempts)))))]
    (vec (concat seeds randoms))))

;; =============================================================================
;; MAIN EVOLUTION LOOP
;; =============================================================================

(defn evolve-one-generation
  "Produce the next generation from current population.

   Generates lambda offspring via mutation/crossover, evaluates them,
   and selects mu survivors from the combined pool.

   Returns {:population survivors :gen-record {...}}"
  [population gen config ^java.util.Random rng]
  (let [{:keys [mu lambda]} config
        verbose-eval? (boolean (:verbose-eval? config))
        verbose-eval-runs? (boolean (:verbose-eval-runs? config))
        ;; Generate offspring
        offspring
        (loop [acc []
               attempts 0]
          (if (or (>= (count acc) lambda) (> attempts (* 3 lambda)))
            (vec (take lambda acc))
            (let [parent (rng-choice rng population)
                  child (if (< (rng-double rng) 0.8)
                          (mutate-tpg rng parent config)
                          (let [other (rng-choice rng population)
                                crossed (crossover-tpg rng parent other)]
                            (mutate-tpg rng crossed config)))
                  extra-ops (set (keys (:wiring-operators config)))
                  valid? (:valid? (tpg/validate-tpg child {:extra-operator-ids extra-ops}))]
              (recur (if valid? (conj acc child) acc)
                     (inc attempts)))))

        ;; Evaluate offspring (optionally with per-candidate progress)
        evaluated-offspring
        (mapv (fn [idx child]
                (let [_ (when verbose-eval?
                          (if verbose-eval-runs?
                            (printf "  [gen %d] offspring %d/%d%n"
                                    gen (inc idx) (count offspring))
                            (printf "  [gen %d] offspring %d/%d ... "
                                    gen (inc idx) (count offspring)))
                          (flush))
                      t0 (System/currentTimeMillis)
                      evaluated (evaluate-tpg child config rng)
                      elapsed (/ (- (System/currentTimeMillis) t0) 1000.0)]
                  (when verbose-eval?
                    (if verbose-eval-runs?
                      (printf "  [gen %d] offspring %d/%d done in %.1fs%n"
                              gen (inc idx) (count offspring) elapsed)
                      (printf "done in %.1fs%n" elapsed))
                    (flush))
                  evaluated))
              (range (count offspring))
              offspring)

        ;; Select survivors from parents + offspring
        combined (vec (concat population evaluated-offspring))
        survivors (select-survivors combined mu)

        ;; Record history
        best (first (sort-by (comp - :overall-satisfaction) survivors))
        mean-overall (/ (reduce + 0.0 (map :overall-satisfaction survivors))
                        (double (count survivors)))
        front-size (count (filter #(= 0 (:pareto-rank %)) survivors))
        gen-record {:generation gen
                    :best-overall (:overall-satisfaction best)
                    :best-satisfaction (:satisfaction-vector best)
                    :best-id (:tpg/id best)
                    :mean-overall mean-overall
                    :front-size front-size
                    :pop-size (count survivors)}]
    {:population survivors :gen-record gen-record}))

(defn evolve
  "Run TPG evolution.

   config keys (all optional, with defaults):
   - :mu — parent population size (default 8)
   - :lambda — offspring per generation (default 8)
   - :eval-runs — MMCA runs per TPG evaluation (default 5)
   - :eval-generations — generations per MMCA run (default 50)
   - :genotype-length — length of test genotypes (default 32)
   - :evo-generations — evolutionary generations (default 20)
   - :verifier-spec — verifier target bands
   - :seed — RNG seed
   - :verbose? — print progress (default true)

   Returns:
   {:population [tpg ...]  — final population (ranked)
    :history [{:generation :best :mean-overall :front-size} ...]
    :best tpg              — best TPG by overall satisfaction
    :config config}"
  [config]
  (let [config (merge default-config config)
        {:keys [mu lambda evo-generations seed verbose? verbose-eval? verbose-eval-runs?]} config
        verbose? (if (some? verbose?) verbose? true)
        rng (java.util.Random. (long (or seed 42)))

        ;; Initialize population
        pop (initial-population rng config)
        _ (when verbose?
            (println (str "\n=== TPG Evolution ==="))
            (println (str "Population: " mu " parents + " lambda " offspring"))
            (println (str "Eval: " (:eval-runs config) " runs × "
                          (:eval-generations config) " generations"))
            (println (str "Evolution: " evo-generations " generations"))
            (when verbose-eval?
              (println "Per-candidate eval progress: enabled"))
            (println))

        ;; Evaluate initial population
        _ (when verbose?
            (if verbose-eval?
              (println "Evaluating initial population:")
              (print "Evaluating initial population... "))
            (flush))
        evaluated-pop
        (mapv (fn [idx individual]
                (let [_ (when (and verbose? verbose-eval?)
                          (if verbose-eval-runs?
                            (printf "  [init] %d/%d%n" (inc idx) (count pop))
                            (printf "  [init] %d/%d ... " (inc idx) (count pop)))
                          (flush))
                      t0 (System/currentTimeMillis)
                      evaluated (evaluate-tpg individual config rng)
                      elapsed (/ (- (System/currentTimeMillis) t0) 1000.0)]
                  (when (and verbose? verbose-eval?)
                    (if verbose-eval-runs?
                      (printf "  [init] %d/%d done in %.1fs%n" (inc idx) (count pop) elapsed)
                      (printf "done in %.1fs%n" elapsed))
                    (flush))
                  evaluated))
              (range (count pop))
              pop)
        _ (when (and verbose? (not verbose-eval?)) (println "done."))

        ;; Evolution loop
        result
        (loop [population evaluated-pop
               gen 0
               history []]
          (if (>= gen evo-generations)
            {:population population :history history}

            (let [{:keys [population gen-record]}
                  (evolve-one-generation population gen config rng)]

              (when verbose?
                (printf "Gen %2d | best %.3f | mean %.3f | front %d | teams %d\n"
                        gen
                        (double (:best-overall gen-record))
                        (double (:mean-overall gen-record))
                        (:front-size gen-record)
                        (count (:teams (first (sort-by (comp - :overall-satisfaction) population)))))
                (flush))

              (recur population (inc gen) (conj history gen-record)))))]

    (let [best (first (sort-by (comp - :overall-satisfaction) (:population result)))]
      (when verbose?
        (println)
        (println "=== Evolution Complete ===")
        (printf "Best overall satisfaction: %.3f\n" (double (:overall-satisfaction best)))
        (println "Satisfaction vector:" (:satisfaction-vector best))
        (println "Teams:" (count (:teams best))
                 "| Programs:" (reduce + (map #(count (:programs %)) (:teams best))))
        (println))
      (assoc result
             :best best
             :config config))))

;; =============================================================================
;; CLI
;; =============================================================================

(defn -main [& args]
  (let [config (merge default-config
                      (when (seq args)
                        (try
                          (read-string (first args))
                          (catch Exception _ {}))))]
    (evolve config)))
