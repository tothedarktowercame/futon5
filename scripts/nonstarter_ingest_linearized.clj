(ns scripts.nonstarter-ingest-linearized
  "Ingest mission/excursion items into Nonstarter hypotheses."
  (:require [clojure.string :as str]
            [nonstarter.db :as db]
            [nonstarter.schema :as schema]))

(defn- usage []
  "usage: bb -m scripts.nonstarter-ingest-linearized --db PATH [--update]\n")

(defn- parse-args [args]
  (loop [opts {:update false}
         remaining args]
    (if (empty? remaining)
      opts
      (case (first remaining)
        "--db" (recur (assoc opts :db (second remaining)) (nnext remaining))
        "--update" (recur (assoc opts :update true) (next remaining))
        (recur opts (next remaining))))))

(def ^:private items
  [{:title "Mission 0 (P0)"
    :statement "Instrumentation sufficient for controlled experiments; evaluation harness."
    :status "active"
    :context "docs/Excursions-overview.md"
    :priority 100
    :mana-estimate 500}
   {:title "Excursion 1 (P1)"
    :statement "Xenotype motifs improve MMCA trajectory health; deliver paper + 64 hexagram families."
    :status "planned"
    :context "docs/Excursions-overview.md"
    :priority 80
    :mana-estimate 300}
   {:title "Excursion 2 (P1 ext)"
    :statement "Promote 64 hexagrams to 256 sigils systematically; full sigil library."
    :status "planned"
    :context "docs/Excursion-2-hexagram-to-sigil-promotion.md"
    :priority 75
    :mana-estimate 250}
   {:title "Excursion 3 (P2)"
    :statement "Abstract CT spec instantiates to MMCA; adaptation interface."
    :status "planned"
    :context "docs/Excursions-overview.md"
    :priority 70
    :mana-estimate 250}
   {:title "Excursion 4 (P3)"
    :statement "CT spec instantiates to AIF ants; transfer validation."
    :status "planned"
    :context "docs/Excursions-overview.md"
    :priority 65
    :mana-estimate 200}
   {:title "Excursion 5 (P4)"
    :statement "Motifs improve coding-agent collectives; kata benchmarks."
    :status "planned"
    :context "docs/Excursions-overview.md"
    :priority 60
    :mana-estimate 200}
   {:title "Excursion 6 (P6)"
    :statement "Cross-domain invariants exist; ablation matrix."
    :status "planned"
    :context "docs/Excursions-overview.md"
    :priority 55
    :mana-estimate 150}
   {:title "Excursion 7+"
    :statement "Platform, proof search, deployment; future work."
    :status "future"
    :context "docs/Excursions-overview.md"
    :priority 30
    :mana-estimate 100}
   {:title "Mission 9.5: Arrow Discovery"
    :statement "Kolmogorov arrow discovery pilot; empirical CT grounding."
    :status "active"
    :context "docs/mission-9.5-arrow-discovery.md"
    :priority 90
    :mana-estimate 400}
   {:title "Mission 9.6: Arrow Atlas"
    :statement "Arrow atlas refinement; robustness and lever mapping."
    :status "proposed"
    :context "docs/mission-9.6-arrow-atlas.md"
    :priority 50
    :mana-estimate 200}
   {:title "Nonstarter Mission 1"
    :statement "Pattern economy + AIF transfer; portal/sidecar/meme integration."
    :status "draft"
    :context "docs/nonstarter-mission-1.md"
    :priority 45
    :mana-estimate 150}])

(defn- index-by-title [records]
  (into {} (map (juxt :title identity) records)))

(defn -main [& args]
  (let [{:keys [db update]} (parse-args args)]
    (when (str/blank? (str db))
      (binding [*out* *err*]
        (println "--db is required")
        (println (usage)))
      (System/exit 2))
    (let [ds (schema/connect! db)
          existing (index-by-title (db/list-hypotheses ds))]
      (doseq [{:keys [title statement context status priority mana-estimate]} items]
        (if-let [found (get existing title)]
          (do
            (when update
              (db/update-hypothesis! ds (:id found) {:status status}))
            (println "SKIP" title "(exists)"))
          (do
            (db/create-hypothesis! ds {:title title
                                       :statement statement
                                       :context context
                                       :status status
                                       :priority priority
                                       :mana-estimate mana-estimate})
            (println "ADD" title "(" status ")")))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
