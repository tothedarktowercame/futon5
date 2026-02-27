(ns nonstarter.db
  "Persistent operations for nonstarter.

   All the desire market mechanics, backed by SQLite.
   The facts established are permanent records."
  (:require [clojure.string :as str]
            [nonstarter.schema :as schema]
            [nonstarter.sql :as sql]))
;; =============================================================================
;; Pool operations
;; =============================================================================

(defn pool-balance
  "Get current pool balance."
  [ds]
  (-> (sql/execute-one! ds ["SELECT balance FROM pool WHERE id = 1"])
      :balance))

(defn update-pool-balance!
  "Adjust pool balance by delta (positive or negative)."
  [ds delta]
  (sql/execute! ds ["UPDATE pool SET balance = balance + ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1"
                     delta]))

(defn donate!
  "Add money to the pool. Returns the donation record."
  [ds amount & {:keys [donor note]}]
  (let [id (str (random-uuid))
        record {:id id
                :amount amount
                :donor (or donor "anonymous")
                :note note}]
    (sql/execute! ds ["INSERT INTO donations (id, amount, donor, note) VALUES (?, ?, ?, ?)"
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
    (sql/execute! ds ["INSERT INTO proposals (id, title, description, ask, sigil, proposer, status, vote_weight)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                       id title description ask sigil (:proposer record) "proposed" 0])
    record))

(defn get-proposal
  "Get a proposal by ID."
  [ds id]
  (sql/execute-one! ds ["SELECT * FROM proposals WHERE id = ?" id]))

(defn list-proposals
  "List proposals, optionally filtered by status."
  ([ds]
   (sql/query ds ["SELECT * FROM proposals ORDER BY created_at DESC"]))
  ([ds status]
   (sql/query ds ["SELECT * FROM proposals WHERE status = ? ORDER BY vote_weight DESC" status])))

;; =============================================================================
;; Vote operations
;; =============================================================================

(defn vote!
  "Cast a vote for a proposal. Votes are weak arrows of desire."
  [ds proposal-id & {:keys [voter weight] :or {weight 1}}]
  (when-let [proposal (get-proposal ds proposal-id)]
    (when (= "proposed" (:status proposal))
      (let [id (str (random-uuid))]
        (sql/execute! ds ["INSERT INTO votes (id, proposal_id, voter, weight, decayed_weight)
                           VALUES (?, ?, ?, ?, ?)"
                          id proposal-id (or voter "anonymous") weight weight])
        (sql/execute! ds ["UPDATE proposals SET vote_weight = vote_weight + ? WHERE id = ?"
                          weight proposal-id])
        {:id id :proposal-id proposal-id :voter voter :weight weight}))))

(defn decay-votes!
  "Apply time decay to all votes. Prevents early lock-in.

   decay-rate: fraction of weight to remove (default 0.1 = 10%)"
  [ds & {:keys [decay-rate] :or {decay-rate 0.1}}]
  (let [multiplier (- 1 decay-rate)]
    ;; Decay individual vote weights
    (sql/execute! ds ["UPDATE votes SET decayed_weight = decayed_weight * ?" multiplier])
    ;; Recalculate proposal totals
    (sql/execute! ds ["UPDATE proposals SET vote_weight = (
                         SELECT COALESCE(SUM(decayed_weight), 0)
                         FROM votes WHERE votes.proposal_id = proposals.id
                       ) WHERE status = 'proposed'"])))

(defn get-votes
  "Get all votes for a proposal."
  [ds proposal-id]
  (sql/query ds ["SELECT * FROM votes WHERE proposal_id = ? ORDER BY created_at DESC"
                     proposal-id]))

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
        (sql/execute! ds ["INSERT INTO funding_events (id, proposal_id, amount, note, fact_established)
                           VALUES (?, ?, ?, ?, ?)"
                          id proposal-id amount
                          (or note "funded - delivery not guaranteed")
                          fact])
        ;; Update proposal status
        (sql/execute! ds ["UPDATE proposals SET status = 'funded', funded_at = CURRENT_TIMESTAMP WHERE id = ?"
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
  (let [eligible (sql/query ds
                            ["SELECT * FROM proposals
                              WHERE status = 'proposed' AND vote_weight >= ?
                              ORDER BY vote_weight DESC"
                             threshold])]
    (doall
     (for [proposal eligible
           :let [balance (pool-balance ds)]
           :while (can-fund? proposal balance)]
       (fund! ds (:id proposal) :note "auto-funded via threshold")))))

;; =============================================================================
;; Mana ledger (session budget)
;; =============================================================================

(defn mana-balance
  "Get current mana balance for a session."
  [ds session-id]
  (-> (sql/execute-one! ds
                         ["SELECT COALESCE(SUM(delta), 0) as balance
                           FROM mana_events WHERE session_id = ?"
                          session-id]
                        )
      :balance))

(defn record-mana!
  "Record a mana credit/debit for a session turn."
  [ds {:keys [session-id turn delta reason note]}]
  (let [id (str (random-uuid))
        balance (+ (mana-balance ds session-id) delta)
        reason* (when reason (if (keyword? reason) (name reason) (str reason)))]
    (sql/execute! ds
                   ["INSERT INTO mana_events
                     (id, session_id, turn, delta, reason, note, balance)
                     VALUES (?, ?, ?, ?, ?, ?, ?)"
                    id session-id turn delta reason* note balance])
    {:id id
     :session-id session-id
     :turn turn
     :delta delta
     :reason reason
     :note note
     :balance balance}))

(defn mana-summary
  "Summarize mana activity for a session."
  [ds session-id]
  (let [{:keys [earned spent balance]}
        (sql/execute-one! ds
                           ["SELECT
                               COALESCE(SUM(CASE WHEN delta > 0 THEN delta ELSE 0 END), 0) as earned,
                               COALESCE(SUM(CASE WHEN delta < 0 THEN delta ELSE 0 END), 0) as spent,
                               COALESCE(SUM(delta), 0) as balance
                             FROM mana_events WHERE session_id = ?"
                            session-id]
                          )]
    {:session-id session-id
     :earned earned
     :spent (Math/abs (double spent))
     :balance balance}))

;; =============================================================================
;; Sidecar events (adjunct metadata)
;; =============================================================================

(defn record-sidecar-event!
  "Record a sidecar event payload for audit linkage."
  [ds {:keys [session-id turn event-type payload]}]
  (let [id (str (random-uuid))
        event-type* (if (keyword? event-type) (name event-type) (str event-type))
        payload* (when payload (pr-str payload))]
    (sql/execute! ds
                   ["INSERT INTO sidecar_events
                     (id, session_id, turn, event_type, payload)
                     VALUES (?, ?, ?, ?, ?)"
                    id session-id turn event-type* payload*])
    {:id id
     :session-id session-id
     :turn turn
     :event-type event-type
     :payload payload}))

(defn list-sidecar-events
  "List sidecar events for a session."
  [ds session-id]
  (sql/query ds
             ["SELECT * FROM sidecar_events
               WHERE session_id = ? ORDER BY created_at DESC"
              session-id]))

;; =============================================================================
;; Hypotheses + study preregistrations
;; =============================================================================

(defn create-hypothesis!
  "Create a hypothesis entry."
  [ds {:keys [title statement context status priority mana-estimate]}]
  (when-not (and title statement (not (str/blank? context)) (number? mana-estimate))
    (throw (ex-info "Hypothesis requires :title, :statement, :context, and numeric :mana-estimate" {})))
  (let [id (str (random-uuid))
        record {:id id
                :title title
                :statement statement
                :context context
                :status (or status "active")
                :priority (or priority 0)
                :mana_estimate mana-estimate}]
    (sql/execute! ds
                   ["INSERT INTO hypotheses (id, title, statement, context, status, priority, mana_estimate)
                     VALUES (?, ?, ?, ?, ?, ?, ?)"
                    id title statement context (:status record) priority mana-estimate])
    record))

(defn get-hypothesis
  "Get hypothesis by id."
  [ds id]
  (sql/execute-one! ds ["SELECT * FROM hypotheses WHERE id = ?" id]))

(defn vote-hypothesis!
  "Cast a vote for a hypothesis."
  [ds hypothesis-id & {:keys [voter weight note] :or {weight 1}}]
  (when-let [hypothesis (get-hypothesis ds hypothesis-id)]
    (let [id (str (random-uuid))
          voter* (or voter "anonymous")]
      (sql/execute! ds ["INSERT INTO hypothesis_votes (id, hypothesis_id, voter, weight, note)
                         VALUES (?, ?, ?, ?, ?)"
                         id hypothesis-id voter* weight note])
      {:id id
       :hypothesis-id hypothesis-id
       :voter voter*
       :weight weight
       :note note
       :title (:title hypothesis)})))

(defn hypothesis-vote-sums
  "Return total vote weights per hypothesis."
  [ds]
  (sql/query ds ["SELECT hypothesis_id, COALESCE(SUM(weight), 0) as vote_score
                      FROM hypothesis_votes GROUP BY hypothesis_id"]
                ))

(defn list-hypotheses
  "List hypotheses, optionally filtered by status."
  ([ds]
   (sql/query ds ["SELECT * FROM hypotheses
                       ORDER BY priority IS NULL, priority DESC, created_at DESC"]
                 ))
  ([ds status]
   (sql/query ds ["SELECT * FROM hypotheses WHERE status = ?
                       ORDER BY priority IS NULL, priority DESC, created_at DESC"
                      (name status)]
                  )))

(defn update-hypothesis!
  "Update hypothesis fields."
  [ds id {:keys [status priority mana-estimate]}]
  (let [status* (when status (name status))]
    (sql/execute! ds
                   ["UPDATE hypotheses
                     SET status = COALESCE(?, status),
                         priority = COALESCE(?, priority),
                         mana_estimate = COALESCE(?, mana_estimate),
                         updated_at = CURRENT_TIMESTAMP
                     WHERE id = ?"
                    status* priority mana-estimate id]))
  (get-hypothesis ds id))

(defn register-study!
  "Create a study preregistration linked to a hypothesis."
  [ds {:keys [hypothesis-id study-name design metrics seeds status results notes priority mana-estimate]}]
  (when-not (and hypothesis-id study-name)
    (throw (ex-info "Study preregistration requires :hypothesis-id and :study-name" {})))
  (let [id (str (random-uuid))
        record {:id id
                :hypothesis-id hypothesis-id
                :study-name study-name
                :design (when design (pr-str design))
                :metrics (when metrics (pr-str metrics))
                :seeds (when seeds (pr-str seeds))
                :status (or status "preregistered")
                :results (when results (pr-str results))
                :notes notes
                :priority priority
                :mana_estimate mana-estimate}]
    (sql/execute! ds
                   ["INSERT INTO study_preregistrations
                     (id, hypothesis_id, study_name, design, metrics, seeds, status, results, notes, priority, mana_estimate)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    id hypothesis-id study-name (:design record) (:metrics record) (:seeds record)
                    (:status record) (:results record) notes priority mana-estimate])
    record))

(defn update-study-results!
  "Attach results and update status for a preregistered study."
  [ds id {:keys [results status notes priority mana-estimate]}]
  (let [results* (when results (pr-str results))
        status* (when status (name status))]
    (sql/execute! ds
                   ["UPDATE study_preregistrations
                     SET results = COALESCE(?, results),
                         status = COALESCE(?, status),
                         notes = COALESCE(?, notes),
                         priority = COALESCE(?, priority),
                         mana_estimate = COALESCE(?, mana_estimate),
                         updated_at = CURRENT_TIMESTAMP
                     WHERE id = ?"
                    results* status* notes priority mana-estimate id])
    (sql/execute-one! ds ["SELECT * FROM study_preregistrations WHERE id = ?" id])))

(defn list-studies
  "List study preregistrations, optionally filtered by hypothesis id."
  ([ds]
   (sql/query ds ["SELECT * FROM study_preregistrations
                       ORDER BY priority IS NULL, priority DESC, created_at DESC"]
                 ))
  ([ds hypothesis-id]
   (sql/query ds ["SELECT * FROM study_preregistrations
                       WHERE hypothesis_id = ?
                       ORDER BY priority IS NULL, priority DESC, created_at DESC"
                      hypothesis-id]
                  )))

;; =============================================================================
;; Queries & History
;; =============================================================================

(defn funding-history
  "Get all funding events (the facts established)."
  [ds]
  (sql/query ds ["SELECT * FROM funding_events ORDER BY created_at DESC"]))

(defn donation-history
  "Get all donations."
  [ds]
  (sql/query ds ["SELECT * FROM donations ORDER BY created_at DESC"]))

(defn pool-stats
  "Get pool statistics."
  [ds]
  (let [balance (pool-balance ds)
        total-donated (-> (sql/execute-one! ds ["SELECT COALESCE(SUM(amount), 0) as total FROM donations"])
                          :total)
        total-funded (-> (sql/execute-one! ds ["SELECT COALESCE(SUM(amount), 0) as total FROM funding_events"])
                         :total)
        proposal-count (-> (sql/execute-one! ds ["SELECT COUNT(*) as n FROM proposals WHERE status = 'proposed'"])
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
    (sql/execute! ds ["INSERT INTO simulations (id, name, description, parameters)
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
    (sql/execute! ds ["INSERT INTO xenotypes
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
    (sql/execute! ds ["INSERT INTO xenotypes
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
  (sql/query ds ["SELECT * FROM xenotypes WHERE simulation_id = ? ORDER BY created_at"
                     simulation-id]))

(defn list-simulations
  "List all simulations."
  [ds]
  (sql/query ds ["SELECT * FROM simulations ORDER BY created_at DESC"]))

(defn alternate-history
  "Generate a narrative of what happened in an alternate timeline."
  [ds simulation-id]
  (let [sim (sql/execute-one! ds ["SELECT * FROM simulations WHERE id = ?" simulation-id])
        events (list-xenotypes ds simulation-id)]
    {:simulation (:name sim)
     :description (:description sim)
     :timeline (mapv (fn [e]
                       (or (:counterfactual_outcome e)
                           (str (:fake_voter e) " desired '"
                                (:proposal_id e) "' for " (:fake_amount e))))
                     events)}))

;; =============================================================================
;; Portfolio heartbeats (weekly action-level bid/clear)
;; =============================================================================

(defn upsert-heartbeat-bids!
  "Record intended actions for a week.
   bids: [{:action :work-on :mission \"M-foo\" :effort :hard} ...]
   mode-prediction: keyword (:BUILD, :MAINTAIN, :CONSOLIDATE)"
  [ds {:keys [week-id bids mode-prediction]}]
  (when-not week-id
    (throw (ex-info "Heartbeat requires :week-id" {})))
  (sql/execute! ds
    ["INSERT INTO portfolio_heartbeats (week_id, bids, mode_prediction)
      VALUES (?, ?, ?)
      ON CONFLICT(week_id)
      DO UPDATE SET bids = ?, mode_prediction = ?, updated_at = CURRENT_TIMESTAMP"
     week-id (pr-str bids) (when mode-prediction (name mode-prediction))
     (pr-str bids) (when mode-prediction (name mode-prediction))]))

(defn upsert-heartbeat-clears!
  "Record actual actions for a week + AIF snapshot.
   clears: [{:action :work-on :mission \"M-foo\" :effort :hard :outcome :partial} ...]
   mode-observed: keyword
   delta: computed prediction errors (EDN)
   aif-snapshot: portfolio AIF state at clear time (EDN)"
  [ds {:keys [week-id clears mode-observed delta aif-snapshot]}]
  (when-not week-id
    (throw (ex-info "Heartbeat requires :week-id" {})))
  (sql/execute! ds
    ["UPDATE portfolio_heartbeats
      SET clears = ?, mode_observed = ?, delta = ?, aif_snapshot = ?,
          updated_at = CURRENT_TIMESTAMP
      WHERE week_id = ?"
     (pr-str clears) (when mode-observed (name mode-observed))
     (when delta (pr-str delta)) (when aif-snapshot (pr-str aif-snapshot))
     week-id]))

(defn get-heartbeat
  "Get a heartbeat by week-id. Returns parsed EDN fields."
  [ds week-id]
  (when-let [row (sql/execute-one! ds
                   ["SELECT * FROM portfolio_heartbeats WHERE week_id = ?" week-id])]
    (-> row
        (update :bids #(when % (read-string %)))
        (update :clears #(when % (read-string %)))
        (update :mode_prediction #(when % (keyword %)))
        (update :mode_observed #(when % (keyword %)))
        (update :delta #(when % (read-string %)))
        (update :aif_snapshot #(when % (read-string %))))))

(defn list-heartbeats
  "List recent heartbeats, newest first."
  ([ds] (list-heartbeats ds 10))
  ([ds n]
   (->> (sql/query ds
          ["SELECT * FROM portfolio_heartbeats ORDER BY week_id DESC LIMIT ?" n])
        (mapv #(-> %
                   (update :bids (fn [v] (when v (read-string v))))
                   (update :clears (fn [v] (when v (read-string v))))
                   (update :mode_prediction (fn [v] (when v (keyword v))))
                   (update :mode_observed (fn [v] (when v (keyword v))))
                   (update :delta (fn [v] (when v (read-string v))))
                   (update :aif_snapshot (fn [v] (when v (read-string v)))))))))

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
