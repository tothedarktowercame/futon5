(ns nonstarter.core
  "Nonstarter: a desire market built on the meme layer.

   Core insight: funding IS the output.
   - Proposals are memes (fuzzy desires)
   - Votes are weak arrows (expressions of desire)
   - Funding promotes the meme to a FACT about belief
   - Delivery is orthogonal (the 'nonstarter' inversion)

   The fact established by funding isn't 'X exists' but
   'at time T, someone put $N behind X existing.'

   This is epistemically clean: we're recording evidence
   of desire, not promises of delivery."
  (:require [clojure.string :as str]))

;; =============================================================================
;; Pool: the collective resource
;; =============================================================================

(defonce ^:private pool-state
  (atom {:balance 0
         :donations []
         :funded []}))

(defn pool-balance []
  (:balance @pool-state))

(defn donate!
  "Add to the pool. Returns the donation record."
  [amount & {:keys [donor note]}]
  (let [donation {:id (str (random-uuid))
                  :amount amount
                  :donor (or donor :anonymous)
                  :note note
                  :timestamp (java.time.Instant/now)}]
    (swap! pool-state
           (fn [s]
             (-> s
                 (update :balance + amount)
                 (update :donations conj donation))))
    donation))

;; =============================================================================
;; Proposals: memes seeking funding
;; =============================================================================

(defonce ^:private proposals
  (atom {}))

(defn propose!
  "Create a proposal (a meme seeking funding).

   Required: :title, :ask (amount requested)
   Optional: :description, :sigil, :proposer"
  [{:keys [title ask description sigil proposer] :as proposal}]
  (when-not (and title ask)
    (throw (ex-info "Proposal requires :title and :ask" {:proposal proposal})))
  (let [id (str (random-uuid))
        record (merge proposal
                      {:id id
                       :status :proposed
                       :votes []
                       :vote-weight 0
                       :created-at (java.time.Instant/now)})]
    (swap! proposals assoc id record)
    record))

(defn get-proposal [id]
  (get @proposals id))

(defn list-proposals
  "List proposals, optionally filtered by status."
  ([] (vals @proposals))
  ([status] (filter #(= status (:status %)) (vals @proposals))))

;; =============================================================================
;; Votes: weak arrows expressing desire
;; =============================================================================

(defn vote!
  "Cast a vote for a proposal.

   Votes are weak arrows - expressions of desire that accumulate.
   They decay over time (see decay-votes!)."
  [proposal-id & {:keys [voter weight] :or {weight 1}}]
  (when-let [proposal (get-proposal proposal-id)]
    (when (= :proposed (:status proposal))
      (let [vote {:voter (or voter :anonymous)
                  :weight weight
                  :timestamp (java.time.Instant/now)}]
        (swap! proposals update-in [proposal-id :votes] conj vote)
        (swap! proposals update-in [proposal-id :vote-weight] + weight)
        vote))))

(defn decay-votes!
  "Apply time decay to all votes.

   This prevents early lock-in and keeps weirdness viable.
   Decay factor: votes lose (1 - decay-rate) of their weight."
  [& {:keys [decay-rate] :or {decay-rate 0.1}}]
  (let [decay-fn (fn [proposal]
                   (let [new-weight (* (:vote-weight proposal) (- 1 decay-rate))]
                     (assoc proposal :vote-weight new-weight)))]
    (swap! proposals #(into {} (map (fn [[k v]] [k (decay-fn v)]) %)))))

;; =============================================================================
;; Funding: the market clearing event
;; =============================================================================

(defn- can-fund? [proposal pool-balance]
  (and (= :proposed (:status proposal))
       (>= pool-balance (:ask proposal))))

(defn fund!
  "Fund a proposal from the pool.

   This is the market clearing event. The fact established is:
   'At this moment, this desire was worth funding.'

   Delivery is NOT guaranteed. That's the nonstarter inversion."
  [proposal-id & {:keys [note]}]
  (let [proposal (get-proposal proposal-id)
        balance (pool-balance)]
    (when (can-fund? proposal balance)
      (let [amount (:ask proposal)
            funding-event {:proposal-id proposal-id
                           :amount amount
                           :timestamp (java.time.Instant/now)
                           :note (or note "funded - delivery not guaranteed")
                           ;; The epistemic record
                           :fact-established (str "On " (java.time.Instant/now)
                                                  ", " amount " was committed to '"
                                                  (:title proposal) "'")}]
        ;; Deduct from pool
        (swap! pool-state update :balance - amount)
        (swap! pool-state update :funded conj funding-event)
        ;; Update proposal status
        (swap! proposals assoc-in [proposal-id :status] :funded)
        (swap! proposals assoc-in [proposal-id :funded-at] (java.time.Instant/now))
        funding-event))))

(defn check-thresholds!
  "Auto-fund proposals that meet criteria.

   Current criteria: vote-weight >= threshold AND pool has funds.
   Returns list of newly funded proposals."
  [& {:keys [threshold] :or {threshold 10}}]
  (let [eligible (->> (list-proposals :proposed)
                      (filter #(>= (:vote-weight %) threshold))
                      (filter #(can-fund? % (pool-balance)))
                      (sort-by :vote-weight >))]
    (doall
     (for [proposal eligible
           :while (can-fund? proposal (pool-balance))]
       (fund! (:id proposal) :note "auto-funded via threshold")))))

;; =============================================================================
;; Queries
;; =============================================================================

(defn funding-history []
  (:funded @pool-state))

(defn donation-history []
  (:donations @pool-state))

(defn proposal-summary [proposal-id]
  (when-let [p (get-proposal proposal-id)]
    {:title (:title p)
     :ask (:ask p)
     :status (:status p)
     :vote-weight (:vote-weight p)
     :vote-count (count (:votes p))
     :sigil (:sigil p)}))

;; =============================================================================
;; Demo / REPL
;; =============================================================================

(comment
  ;; Seed the pool
  (donate! 1000 :donor "patron-saint" :note "believing in weird futures")

  ;; Propose something unlikely
  (def horse-cream
    (propose! {:title "Horse Ice Cream"
               :ask 500
               :description "Delicious meaty frozen treat. May never ship."
               :sigil "[🐴/冰]"
               :proposer "visionary"}))

  ;; Vote for it
  (vote! (:id horse-cream) :voter "believer-1" :weight 3)
  (vote! (:id horse-cream) :voter "believer-2" :weight 5)
  (vote! (:id horse-cream) :voter "believer-3" :weight 4)

  ;; Check status
  (proposal-summary (:id horse-cream))

  ;; Auto-fund if threshold met
  (check-thresholds! :threshold 10)

  ;; See what happened
  (funding-history)
  ;; => [{:proposal-id "...",
  ;;      :fact-established "On 2026-01-16T..., 500 was committed to 'Horse Ice Cream'"}]

  ;; The market cleared. Delivery? That's orthogonal.
  )
