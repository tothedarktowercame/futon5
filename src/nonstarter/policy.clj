(ns nonstarter.policy
  "Minimal policy simulator stub for Mission 1.

   Deterministic scoring to support the adapter contract.")

(def ^:private policy-id "policy-portal-v0")

(defn- block-completeness [blocks]
  (let [present (count (remove nil? (vals blocks)))
        total (max 1 (count blocks))]
    (/ present total)))

(defn- score-candidate [candidate]
  (let [base (double (or (:pattern/score candidate) 0.0))
        completeness (block-completeness (:blocks candidate))]
    (+ base (* 0.25 completeness))))

(defn recommend
  "Return a deterministic recommendation based on normalized candidates.

   Expects the adapter contract input shape:
   {:session/id .. :turn .. :intent .. :candidates [...]}
   Returns a recommendation envelope compatible with nonstarter terminals.
  "
  [{:keys [candidates]}]
  (let [scores (->> (or candidates [])
                    (map (fn [candidate]
                           (let [score (score-candidate candidate)]
                             {:pattern/id (:pattern/id candidate)
                              :score score})))
                    (sort-by (juxt (comp - :score) :pattern/id))
                    vec)
        chosen (some-> scores first :pattern/id)
        top-score (some-> scores first :score)
        confidence (if top-score
                     (min 1.0 (max 0.0 top-score))
                     0.0)]
    {:policy/id policy-id
     :chosen chosen
     :confidence confidence
     :scores scores}))

(defn dp-sequence
  "Stub DP selector: returns a ranked sequence of top candidates.

   This keeps the DP interface in place without enforcing policy overrides."
  [{:keys [candidates]} & {:keys [horizon] :or {horizon 2}}]
  (let [scored (->> (or candidates [])
                    (map (fn [candidate]
                           (let [score (score-candidate candidate)]
                             {:pattern/id (:pattern/id candidate)
                              :score score})))
                    (sort-by (juxt (comp - :score) :pattern/id)))
        selected (take horizon scored)]
    {:sequence (mapv :pattern/id selected)
     :costs (mapv (fn [{:keys [score]}] (- score)) selected)
     :horizon horizon}))
