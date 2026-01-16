(ns nonstarter.db
  "Persistent operations for nonstarter.

   All the desire market mechanics, backed by SQLite.
   The facts established are permanent records."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [nonstarter.schema :as schema]))

(def ^:private query-opts
  {:builder-fn rs/as-unqualified-maps})

;; =============================================================================
;; Pool operations
;; =============================================================================

(defn pool-balance
  "Get current pool balance."
  [ds]
  (-> (jdbc/execute-one! ds ["SELECT balance FROM pool WHERE id = 1"] query-opts)
      :balance))

(defn update-pool-balance!
  "Adjust pool balance by delta (positive or negative)."
  [ds delta]
  (jdbc/execute! ds ["UPDATE pool SET balance = balance + ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1"
                     delta]))

(defn donate!
  "Add money to the pool. Returns the donation record."
  [ds amount & {:keys [donor note]}]
  (let [id (str (random-uuid))
        record {:id id
                :amount amount
                :donor (or donor "anonymous")
                :note note}]
    (jdbc/execute! ds ["INSERT INTO donations (id, amount, donor, note) VALUES (?, ?, ?, ?)"
                       id amount (:donor record) note])
    (update-pool-balance! ds amount)
    record))

;; =============================================================================
;; Proposal operations
;; =============================================================================

(defn propose!
  "Create a proposal (a meme seeking funding)."
  [ds {:keys [title ask description sigil proposer]}]
  (when-not (and title ask)
    (throw (ex-info "Proposal requires :title and :ask" {})))
  (let [id (str (random-uuid))
        record {:id id
                :title title
                :ask ask
                :description description
                :sigil sigil
                :proposer (or proposer "anonymous")
                :status "proposed"
                :vote_weight 0}]
    (jdbc/execute! ds ["INSERT INTO proposals (id, title, description, ask, sigil, proposer, status, vote_weight)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                       id title description ask sigil (:proposer record) "proposed" 0])
    record))

(defn get-proposal
  "Get a proposal by ID."
  [ds id]
  (jdbc/execute-one! ds ["SELECT * FROM proposals WHERE id = ?" id] query-opts))

(defn list-proposals
  "List proposals, optionally filtered by status."
  ([ds]
   (jdbc/execute! ds ["SELECT * FROM proposals ORDER BY created_at DESC"] query-opts))
  ([ds status]
   (jdbc/execute! ds ["SELECT * FROM proposals WHERE status = ? ORDER BY vote_weight DESC" status] query-opts)))

;; =============================================================================
;; Vote operations
;; =============================================================================

(defn vote!
  "Cast a vote for a proposal. Votes are weak arrows of desire."
  [ds proposal-id & {:keys [voter weight] :or {weight 1}}]
  (when-let [proposal (get-proposal ds proposal-id)]
    (when (= "proposed" (:status proposal))
      (let [id (str (random-uuid))]
        (jdbc/execute! ds ["INSERT INTO votes (id, proposal_id, voter, weight, decayed_weight)
                           VALUES (?, ?, ?, ?, ?)"
                          id proposal-id (or voter "anonymous") weight weight])
        (jdbc/execute! ds ["UPDATE proposals SET vote_weight = vote_weight + ? WHERE id = ?"
                          weight proposal-id])
        {:id id :proposal-id proposal-id :voter voter :weight weight}))))

(defn decay-votes!
  "Apply time decay to all votes. Prevents early lock-in.

   decay-rate: fraction of weight to remove (default 0.1 = 10%)"
  [ds & {:keys [decay-rate] :or {decay-rate 0.1}}]
  (let [multiplier (- 1 decay-rate)]
    ;; Decay individual vote weights
    (jdbc/execute! ds ["UPDATE votes SET decayed_weight = decayed_weight * ?" multiplier])
    ;; Recalculate proposal totals
    (jdbc/execute! ds ["UPDATE proposals SET vote_weight = (
                         SELECT COALESCE(SUM(decayed_weight), 0)
                         FROM votes WHERE votes.proposal_id = proposals.id
                       ) WHERE status = 'proposed'"])))

(defn get-votes
  "Get all votes for a proposal."
  [ds proposal-id]
  (jdbc/execute! ds ["SELECT * FROM votes WHERE proposal_id = ? ORDER BY created_at DESC"
                     proposal-id] query-opts))

;; =============================================================================
;; Funding operations (the market clearing)
;; =============================================================================

(defn- can-fund?
  "Check if a proposal can be funded."
  [proposal pool-balance]
  (and (= "proposed" (:status proposal))
       (>= pool-balance (:ask proposal))))

(defn fund!
  "Fund a proposal from the pool.

   This is the market clearing event. The fact established:
   'At this moment, this desire was worth funding.'

   Delivery is NOT guaranteed. That's the nonstarter inversion."
  [ds proposal-id & {:keys [note]}]
  (let [proposal (get-proposal ds proposal-id)
        balance (pool-balance ds)]
    (when (can-fund? proposal balance)
      (let [id (str (random-uuid))
            amount (:ask proposal)
            timestamp (java.time.Instant/now)
            fact (str "On " timestamp ", " amount " was committed to '" (:title proposal) "'")]
        ;; Record the funding event (the fact)
        (jdbc/execute! ds ["INSERT INTO funding_events (id, proposal_id, amount, note, fact_established)
                           VALUES (?, ?, ?, ?, ?)"
                          id proposal-id amount
                          (or note "funded - delivery not guaranteed")
                          fact])
        ;; Update proposal status
        (jdbc/execute! ds ["UPDATE proposals SET status = 'funded', funded_at = CURRENT_TIMESTAMP WHERE id = ?"
                          proposal-id])
        ;; Deduct from pool
        (update-pool-balance! ds (- amount))
        {:id id
         :proposal-id proposal-id
         :amount amount
         :fact-established fact}))))

(defn check-thresholds!
  "Auto-fund proposals that meet criteria.

   Proposals are funded in order of vote_weight until pool is exhausted."
  [ds & {:keys [threshold] :or {threshold 10}}]
  (let [eligible (jdbc/execute! ds
                   ["SELECT * FROM proposals
                     WHERE status = 'proposed' AND vote_weight >= ?
                     ORDER BY vote_weight DESC"
                    threshold]
                   query-opts)]
    (doall
     (for [proposal eligible
           :let [balance (pool-balance ds)]
           :while (can-fund? proposal balance)]
       (fund! ds (:id proposal) :note "auto-funded via threshold")))))

;; =============================================================================
;; Queries & History
;; =============================================================================

(defn funding-history
  "Get all funding events (the facts established)."
  [ds]
  (jdbc/execute! ds ["SELECT * FROM funding_events ORDER BY created_at DESC"] query-opts))

(defn donation-history
  "Get all donations."
  [ds]
  (jdbc/execute! ds ["SELECT * FROM donations ORDER BY created_at DESC"] query-opts))

(defn pool-stats
  "Get pool statistics."
  [ds]
  (let [balance (pool-balance ds)
        total-donated (-> (jdbc/execute-one! ds ["SELECT COALESCE(SUM(amount), 0) as total FROM donations"] query-opts)
                          :total)
        total-funded (-> (jdbc/execute-one! ds ["SELECT COALESCE(SUM(amount), 0) as total FROM funding_events"] query-opts)
                         :total)
        proposal-count (-> (jdbc/execute-one! ds ["SELECT COUNT(*) as n FROM proposals WHERE status = 'proposed'"] query-opts)
                           :n)]
    {:balance balance
     :total-donated total-donated
     :total-funded total-funded
     :active-proposals proposal-count}))

;; =============================================================================
;; Xenotypes: counterfactual timelines
;; =============================================================================

(defn create-simulation!
  "Create a simulation container for xenotype branches.

   This is where fake people buy nonexistent products."
  [ds {:keys [name description parameters]}]
  (let [id (str (random-uuid))]
    (jdbc/execute! ds ["INSERT INTO simulations (id, name, description, parameters)
                        VALUES (?, ?, ?, ?)"
                       id name description (pr-str parameters)])
    {:id id :name name}))

(defn simulate-vote!
  "A fake person expresses fake desire for a nonexistent product.

   This is capitalism's logic made fully explicit."
  [ds simulation-id proposal-id & {:keys [fake-voter amount notes timeline-name]
                                    :or {fake-voter "simulacrum"
                                         amount 1}}]
  (let [id (str (random-uuid))
        proposal (get-proposal ds proposal-id)]
    (jdbc/execute! ds ["INSERT INTO xenotypes
                        (id, simulation_id, timeline_name, proposal_id, fake_voter, fake_amount, notes)
                        VALUES (?, ?, ?, ?, ?, ?, ?)"
                       id simulation-id timeline-name proposal-id fake-voter amount notes])
    {:id id
     :simulation-id simulation-id
     :counterfactual (str "In timeline " (or timeline-name simulation-id)
                          ", " fake-voter " put " amount
                          " behind '" (:title proposal) "'")}))

(defn simulate-funding!
  "Record a counterfactual funding event.

   In this timeline, this nonexistent product was funded by fake people."
  [ds simulation-id proposal-id & {:keys [divergence-point timeline-name fake-amount notes]}]
  (let [id (str (random-uuid))
        proposal (get-proposal ds proposal-id)
        amount (or fake-amount (:ask proposal))
        outcome (str "'" (:title proposal) "' was funded for " amount
                     " (delivery still not guaranteed, even counterfactually)")]
    (jdbc/execute! ds ["INSERT INTO xenotypes
                        (id, simulation_id, timeline_name, divergence_point, proposal_id,
                         fake_amount, counterfactual_outcome, notes)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                       id simulation-id timeline-name divergence-point proposal-id
                       amount outcome notes])
    {:id id
     :simulation-id simulation-id
     :divergence-point divergence-point
     :counterfactual-outcome outcome}))

(defn list-xenotypes
  "List all counterfactual events in a simulation."
  [ds simulation-id]
  (jdbc/execute! ds ["SELECT * FROM xenotypes WHERE simulation_id = ? ORDER BY created_at"
                     simulation-id] query-opts))

(defn list-simulations
  "List all simulations."
  [ds]
  (jdbc/execute! ds ["SELECT * FROM simulations ORDER BY created_at DESC"] query-opts))

(defn alternate-history
  "Generate a narrative of what happened in an alternate timeline."
  [ds simulation-id]
  (let [sim (jdbc/execute-one! ds ["SELECT * FROM simulations WHERE id = ?" simulation-id] query-opts)
        events (list-xenotypes ds simulation-id)]
    {:simulation (:name sim)
     :description (:description sim)
     :timeline (mapv (fn [e]
                       (or (:counterfactual_outcome e)
                           (str (:fake_voter e) " desired '"
                                (:proposal_id e) "' for " (:fake_amount e))))
                     events)}))

;; =============================================================================
;; Demo / CLI helpers
;; =============================================================================

(defn demo-db
  "Create an in-memory database for testing."
  []
  (schema/connect! ":memory:"))

(defn file-db
  "Connect to a file-based database."
  [path]
  (schema/connect! path))

(comment
  ;; Quick test
  (def ds (demo-db))

  (donate! ds 1000 :donor "patron" :note "believing in weird futures")
  (pool-balance ds)  ;=> 1000.0

  (def horse (propose! ds {:title "Horse Ice Cream"
                           :ask 500
                           :description "Meaty frozen treat. May never ship."
                           :sigil "[🐴/冰]"}))

  (vote! ds (:id horse) :voter "believer-1" :weight 5)
  (vote! ds (:id horse) :voter "believer-2" :weight 6)

  (get-proposal ds (:id horse))

  (check-thresholds! ds :threshold 10)

  (funding-history ds)
  ;; => [{:fact_established "On 2026-01-16T..., 500 was committed to 'Horse Ice Cream'", ...}]

  (pool-stats ds)
  ;; => {:balance 500.0, :total-donated 1000.0, :total-funded 500.0, :active-proposals 0}

  ;; === XENOTYPES: Fake people buying nonexistent products ===

  ;; Create an alternate timeline
  (def sim (create-simulation! ds
             {:name "Timeline Beta"
              :description "What if we had more believers?"}))

  ;; Fake people express fake desire
  (simulate-vote! ds (:id sim) (:id horse)
                  :fake-voter "simulacrum-1"
                  :amount 100
                  :timeline-name "Beta")

  (simulate-vote! ds (:id sim) (:id horse)
                  :fake-voter "simulacrum-2"
                  :amount 200
                  :notes "This fake person really wanted horse ice cream")

  ;; Record the counterfactual funding
  (simulate-funding! ds (:id sim) (:id horse)
                     :divergence-point "simulacrum-2 joined the timeline"
                     :timeline-name "Beta"
                     :notes "In this timeline, horse ice cream was funded earlier")

  ;; View the alternate history
  (alternate-history ds (:id sim))
  ;; => {:simulation "Timeline Beta"
  ;;     :timeline ["simulacrum-1 desired '...' for 100"
  ;;                "simulacrum-2 desired '...' for 200"
  ;;                "'Horse Ice Cream' was funded for 500 (delivery still not guaranteed, even counterfactually)"]}

  ;; The market cleared in a timeline that never happened.
  ;; Capitalism's final form: pure simulation of desire.
  )
