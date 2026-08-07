(ns interrupter-identity-hub
  "Identity-hub win-fraction measurement for TN-interrupter-fable-answer.md 2.4.
   Three-way landscape competitions: a fix-0 cell choosing among hold,
   adopt-:identity, adopt-B over all B x 9 observation bins, epistemic arm.
   Run: clojure -Sdeps (quote {:paths [\"src\" \"resources\"]}) -M scripts/interrupter_identity_hub.clj"
  (:require [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.policy-epistemic :as pe]))
(def kappa 0.47821902791182086)
(def lambda 0.55)
(def five [:collapser :even4 :even8 :even1 :odd53])
(def observations
  (for [a [0.0 (/ 1.0 3.0) (/ 2.0 3.0)] d [(/ 1.0 3.0) (/ 2.0 3.0) 1.0]]
    {:activity a :diversity d}))
(defn total [p own cand]
  (fn [obs]
    (:total (efe/score-policy :efe-full cand obs
             {:lambda lambda :apply-probability p
              :epistemic-coefficient kappa
              :epistemic-value (pe/pair-value own cand)}))))
;; three-way competition: own (fix-0) cell, neighbours = :identity and B.
;; does the cell adopt :identity?
(doseq [p [0.3 0.6 1.0]]
  (let [cases (for [own five
                    b grid/exotype-kinds
                    :when (and (not= b own) (not= b :identity))
                    obs observations
                    :let [h ((total p own own) obs)
                          i ((total p own :identity) obs)
                          bb ((total p own b) obs)]]
                (cond (and (< i h) (<= i bb)) :identity
                      (< bb h) :other
                      :else :hold))
        f (frequencies cases)
        n (count cases)]
    (println (format "p=%.2f identity-wins %.3f other-wins %.3f hold %.3f (n=%d)"
                     p (/ (get f :identity 0) (double n))
                     (/ (get f :other 0) (double n))
                     (/ (get f :hold 0) (double n)) n))))
;; and the reverse cell: an :identity cell flanked by A and B (both fix-0) - does it leave?
(doseq [p [0.3 0.6 1.0]]
  (let [cases (for [a five b five :when (not= a b) obs observations
                    :let [h ((total p :identity :identity) obs)
                          ta ((total p :identity a) obs)
                          tb ((total p :identity b) obs)]]
                (if (or (< ta h) (< tb h)) :leaves :holds))
        f (frequencies cases) n (count cases)]
    (println (format "p=%.2f identity-cell leaves %.3f (n=%d)"
                     p (/ (get f :leaves 0) (double n)) n))))
