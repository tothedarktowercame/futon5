(ns futon5.tpg.core
  "Tangled Program Graph: graph-structured modular control for MMCA.

   A TPG is a directed graph of teams and actions:
   - Teams contain programs that compete via bid values
   - Programs are linear classifiers over diagnostic feature vectors
   - Actions correspond to hexagram-family operators (D2: 1:1 alignment)
   - Routing is per-generation (D1), not per-cell

   Execution: diagnostic vector → root team → winning program →
              follow edge to next team or action → repeat until action reached.

   See docs/technote-verifier-guided-tpg.md for full formalization."
  (:require [futon5.tpg.diagnostics :as diag]))

;; =============================================================================
;; OPERATORS (D2: hexagram-aligned)
;; =============================================================================

(def operator-table
  "The 8 operators, one per hexagram family. D2: 1:1 alignment.

   Each operator maps to a hexagram family (8 hexagrams) and a default energy.
   The operator-id is used as the action target in TPG programs."
  [{:operator-id :expansion      :family 0 :energy :peng :hexagrams [1 8]
    :description "Creative mixing — high mutation, low structure"}
   {:operator-id :conservation   :family 1 :energy :lu   :hexagrams [9 16]
    :description "Taming — low mutation, high structure"}
   {:operator-id :adaptation     :family 2 :energy :ji   :hexagrams [17 24]
    :description "Following — balanced mutation/structure, selective"}
   {:operator-id :momentum       :family 3 :energy :peng :hexagrams [25 32]
    :description "Forward motion — moderate-high mutation, blending"}
   {:operator-id :conditional    :family 4 :energy :an   :hexagrams [33 40]
    :description "Phenotype-conditional — Baldwin effect"}
   {:operator-id :differentiation :family 5 :energy :ji  :hexagrams [41 48]
    :description "Differentiating — collection templates"}
   {:operator-id :transformation :family 6 :energy :peng :hexagrams [49 56]
    :description "Revolution — high mutation, low structure"}
   {:operator-id :consolidation  :family 7 :energy :lu   :hexagrams [57 64]
    :description "Gentle consolidation — low mutation, high structure"}])

(def ^:private operator-ids
  (set (map :operator-id operator-table)))

(def ^:private operator-by-id
  (into {} (map (juxt :operator-id identity) operator-table)))

;; =============================================================================
;; PROGRAM EXECUTION
;; =============================================================================

(defn program-bid
  "Compute a program's bid given a diagnostic vector.

   program: {:weights [w0 w1 ... w5] :bias b :action {...}}
   diagnostic: double array of length 6

   bid = w · D + b"
  [{:keys [weights bias]} ^doubles diagnostic]
  (let [n (min (count weights) (alength diagnostic))]
    (loop [i 0
           sum (double (or bias 0.0))]
      (if (>= i n)
        sum
        (recur (inc i)
               (+ sum (* (double (nth weights i))
                         (aget diagnostic i))))))))

(defn execute-program
  "Execute a program: compute bid and return action + bid.

   Returns {:action {:type :team/:operator :target keyword} :bid double}"
  [program diagnostic]
  {:action (:action program)
   :bid (program-bid program diagnostic)})

;; =============================================================================
;; TEAM EXECUTION
;; =============================================================================

(defn execute-team
  "Execute a team: all programs compete, highest bid wins.

   team: {:team/id keyword :programs [program ...]}
   diagnostic: double array

   Returns the action of the winning program."
  [{:keys [programs]} diagnostic]
  (when (seq programs)
    (let [results (mapv #(execute-program % diagnostic) programs)
          winner (apply max-key :bid results)]
      (:action winner))))

;; =============================================================================
;; TPG GRAPH ROUTING
;; =============================================================================

(def ^:private max-routing-depth 4)

(defn- team-by-id
  "Look up a team by id in a TPG."
  [tpg team-id]
  (first (filter #(= (:team/id %) team-id) (:teams tpg))))

(defn route
  "Route through a TPG from the root team to an action.

   tpg: {:teams [...] :operators {...} :config {...}}
   diagnostic: double array of length 6

   Returns:
   {:operator-id keyword       — which operator was selected
    :route [team-id ...]       — path taken through the graph
    :depth int                 — routing depth
    :fallback? boolean}        — true if depth limit forced a fallback"
  [tpg diagnostic]
  (let [max-depth (or (get-in tpg [:config :max-depth]) max-routing-depth)
        root-id (or (get-in tpg [:config :root-team])
                    (:team/id (first (:teams tpg)))
                    :root)]
    (loop [current-id root-id
           route-path [root-id]
           depth 0]
      (if (>= depth max-depth)
        ;; Depth limit reached: fall back to first operator in the last team
        (let [team (team-by-id tpg current-id)
              ;; Find any program in this team that routes to an operator
              operator-program (first (filter #(= :operator (get-in % [:action :type]))
                                              (:programs team)))
              fallback-op (or (get-in operator-program [:action :target])
                              :adaptation)] ;; absolute fallback: balanced operator
          {:operator-id fallback-op
           :route route-path
           :depth depth
           :fallback? true})

        (let [team (team-by-id tpg current-id)]
          (if-not team
            ;; Team not found: fallback
            {:operator-id :adaptation
             :route route-path
             :depth depth
             :fallback? true}

            (let [action (execute-team team diagnostic)]
              (case (:type action)
                ;; Route to another team
                :team
                (recur (:target action)
                       (conj route-path (:target action))
                       (inc depth))

                ;; Terminal: execute operator
                :operator
                {:operator-id (:target action)
                 :route route-path
                 :depth depth
                 :fallback? false}

                ;; Unknown action type: fallback
                {:operator-id :adaptation
                 :route route-path
                 :depth depth
                 :fallback? true}))))))))

;; =============================================================================
;; TPG CONSTRUCTION
;; =============================================================================

(defn make-program
  "Create a program with given weights, bias, and action."
  [program-id weights bias action-type action-target]
  {:program/id program-id
   :weights (vec weights)
   :bias (double bias)
   :action {:type action-type :target action-target}})

(defn make-team
  "Create a team with given programs."
  [team-id programs]
  {:team/id team-id
   :programs (vec programs)})

(defn make-tpg
  "Create a TPG from teams, operators, and config."
  [tpg-id teams config]
  {:tpg/id tpg-id
   :tpg/version 1
   :teams (vec teams)
   :operators operator-table
   :config (merge {:max-depth max-routing-depth
                   :routing-frequency :per-generation
                   :diagnostic-window 3}
                  config)})

(defn validate-tpg
  "Validate TPG structural invariants.

   opts:
   - :extra-operator-ids — set of additional valid operator IDs (e.g. wiring operators)

   Returns {:valid? boolean :errors [string ...]}"
  ([tpg] (validate-tpg tpg {}))
  ([tpg {:keys [extra-operator-ids] :or {extra-operator-ids #{}}}]
  (let [errors (atom [])
        err! (fn [msg] (swap! errors conj msg))
        all-operator-ids (into operator-ids extra-operator-ids)
        team-ids (set (map :team/id (:teams tpg)))]

    ;; Check: at least one team
    (when (empty? (:teams tpg))
      (err! "TPG has no teams"))

    ;; Check: root team exists
    (let [root-id (or (get-in tpg [:config :root-team])
                      (:team/id (first (:teams tpg))))]
      (when-not (team-ids root-id)
        (err! (str "Root team " root-id " not found in teams"))))

    ;; Check: every team has at least one program
    (doseq [team (:teams tpg)]
      (when (empty? (:programs team))
        (err! (str "Team " (:team/id team) " has no programs"))))

    ;; Check: reachability — every team can reach an operator
    (let [;; Build adjacency: team-id → set of reachable team-ids and operator-ids
          can-reach-operator?
          (fn can-reach? [team-id visited]
            (if (visited team-id)
              false ;; cycle
              (let [team (team-by-id tpg team-id)
                    visited' (conj visited team-id)]
                (some (fn [program]
                        (let [{:keys [type target]} (:action program)]
                          (case type
                            :operator (contains? all-operator-ids target)
                            :team (can-reach? target visited')
                            false)))
                      (:programs team)))))]
      (doseq [team (:teams tpg)]
        (when-not (can-reach-operator? (:team/id team) #{})
          (err! (str "Team " (:team/id team) " cannot reach any operator")))))

    ;; Check: all team references are valid
    (doseq [team (:teams tpg)
            program (:programs team)]
      (let [{:keys [type target]} (:action program)]
        (case type
          :team (when-not (team-ids target)
                  (err! (str "Program " (:program/id program) " in team "
                             (:team/id team) " references unknown team " target)))
          :operator (when-not (all-operator-ids target)
                      (err! (str "Program " (:program/id program) " in team "
                                 (:team/id team) " references unknown operator " target)))
          (err! (str "Program " (:program/id program) " has unknown action type " type)))))

    ;; Check: program weights match diagnostic dimension
    (doseq [team (:teams tpg)
            program (:programs team)]
      (when (not= (count (:weights program)) diag/diagnostic-dim)
        (err! (str "Program " (:program/id program) " has "
                   (count (:weights program)) " weights, expected "
                   diag/diagnostic-dim))))

    {:valid? (empty? @errors)
     :errors @errors})))

;; =============================================================================
;; SEED TPGs (hand-crafted starting points for evolution)
;; =============================================================================

(defn seed-tpg-simple
  "Create a simple seed TPG with one root team routing to all 8 operators.

   Each program responds to a single diagnostic dimension:
   - High entropy → conservation (tame it)
   - Low entropy → expansion (shake it up)
   - High change → consolidation (stabilize)
   - Low change → transformation (disrupt)
   - High autocorr → differentiation (break symmetry)
   - Low autocorr → momentum (build structure)
   - High diversity → conditional (let phenotype decide)
   - Low diversity → adaptation (follow context)"
  []
  (make-tpg
   "seed-simple"
   [(make-team
     :root
     [;; High entropy → conservation
      (make-program :p-entropy-hi
                    [0.8 0.0 0.0 0.0 0.0 0.0] 0.0
                    :operator :conservation)
      ;; Low entropy → expansion
      (make-program :p-entropy-lo
                    [-0.8 0.0 0.0 0.0 0.0 0.0] 0.0
                    :operator :expansion)
      ;; High change → consolidation
      (make-program :p-change-hi
                    [0.0 0.8 0.0 0.0 0.0 0.0] 0.0
                    :operator :consolidation)
      ;; Low change → transformation
      (make-program :p-change-lo
                    [0.0 -0.8 0.0 0.0 0.0 0.0] 0.0
                    :operator :transformation)
      ;; High autocorr → differentiation
      (make-program :p-autocorr-hi
                    [0.0 0.0 0.8 0.0 0.0 0.0] 0.0
                    :operator :differentiation)
      ;; Low autocorr → momentum
      (make-program :p-autocorr-lo
                    [0.0 0.0 -0.8 0.0 0.0 0.0] 0.0
                    :operator :momentum)
      ;; High diversity → conditional
      (make-program :p-diversity-hi
                    [0.0 0.0 0.0 0.8 0.0 0.0] 0.0
                    :operator :conditional)
      ;; Low diversity → adaptation (fallback / neutral)
      (make-program :p-diversity-lo
                    [0.0 0.0 0.0 -0.8 0.0 0.0] 0.0
                    :operator :adaptation)])]
   {:root-team :root}))

(defn seed-tpg-hierarchical
  "Create a 2-level TPG: root routes to specialist teams by regime.

   Root team detects high-level regime (frozen / chaotic / edge-of-chaos).
   Specialist teams make finer-grained operator choices within each regime."
  []
  (make-tpg
   "seed-hierarchical"
   [;; Root: route by entropy+change to regime-specialist teams
    (make-team
     :root
     [;; Low entropy + low change → frozen regime
      (make-program :p-frozen
                    [-0.6 -0.6 0.0 0.0 0.0 0.0] -0.2
                    :team :frozen-team)
      ;; High entropy + high change → chaotic regime
      (make-program :p-chaotic
                    [0.6 0.6 0.0 0.0 0.0 0.0] -0.2
                    :team :chaotic-team)
      ;; Otherwise → edge-of-chaos regime (positive bias wins by default)
      (make-program :p-eoc
                    [0.0 0.0 0.0 0.0 0.0 0.0] 0.3
                    :team :eoc-team)])

    ;; Frozen regime: try to increase activity
    (make-team
     :frozen-team
     [(make-program :p-frozen-expand
                    [0.0 0.0 0.0 -0.5 0.0 0.0] 0.1
                    :operator :expansion)
      (make-program :p-frozen-transform
                    [0.0 0.0 0.0 0.5 0.0 0.0] 0.0
                    :operator :transformation)
      (make-program :p-frozen-momentum
                    [0.0 -0.3 0.0 0.0 0.0 0.5] 0.0
                    :operator :momentum)])

    ;; Chaotic regime: try to impose structure
    (make-team
     :chaotic-team
     [(make-program :p-chaotic-conserve
                    [0.5 0.0 0.0 0.0 0.0 0.0] 0.1
                    :operator :conservation)
      (make-program :p-chaotic-consolidate
                    [0.0 0.5 0.0 0.0 0.0 0.0] 0.0
                    :operator :consolidation)
      (make-program :p-chaotic-conditional
                    [0.0 0.0 0.0 0.0 0.5 0.0] 0.0
                    :operator :conditional)])

    ;; Edge-of-chaos regime: fine-tune to stay in the zone
    (make-team
     :eoc-team
     [(make-program :p-eoc-adapt
                    [0.0 0.0 0.0 0.0 0.0 0.0] 0.2
                    :operator :adaptation)
      (make-program :p-eoc-differentiate
                    [0.0 0.0 0.5 0.0 0.0 0.0] 0.0
                    :operator :differentiation)
      (make-program :p-eoc-conditional
                    [0.0 0.0 0.0 0.0 0.6 0.0] 0.0
                    :operator :conditional)])]
   {:root-team :root}))

;; =============================================================================
;; OPERATOR → EXOTYPE BRIDGE
;; =============================================================================

(defn operator->global-rule
  "Convert an operator-id to a global physics rule number.

   D2: hexagram-aligned. Each operator maps to family × energy → rule.

   The rule number encodes: (hexagram-1) × 4 + energy-id
   where hexagram is the first hexagram in the family."
  [operator-id]
  (let [op (get operator-by-id operator-id)]
    (when op
      (let [family (:family op)
            first-hexagram (inc (* family 8)) ;; 1-indexed
            energy-id (case (:energy op)
                        :peng 0
                        :lu 1
                        :ji 2
                        :an 3
                        0)]
        (+ (* (dec first-hexagram) 4) energy-id)))))

(defn operator->bend-mode
  "Default bend mode for an operator.

   Conservation/consolidation use :matrix (parameter composition).
   Expansion/transformation use :blend (output mixing).
   Others use :sequential (apply in order)."
  [operator-id]
  (case operator-id
    (:conservation :consolidation) :matrix
    (:expansion :transformation) :blend
    :sequential))
