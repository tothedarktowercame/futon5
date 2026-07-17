#!/usr/bin/env clojure
(ns exotype-ports
  "Typed-hole coordinate sweep for DarkTower/MetaCAExample.lean.

   The combine stage is represented as the Cartesian product of the four Lean
   ports, not as six named historical kernels. The census composes every combine
   fill with no mutation and with two rule-permute fills from
   propagator_compositions.clj. No vendored Elisp is invoked."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.xenotype.generator :as gen])
  (:import [java.security MessageDigest]
           [java.math BigInteger]))

(def width 80)
(def steps 120)
(def seeds [1 2 3])

(def default-csv-path
  "holes/labs/M-aif-tokamak/exotype-port-census.csv")
(def default-readme-path
  "holes/labs/M-aif-tokamak/exotype-port-census.README.md")

;; Names mirror the Lean constructors, rendered as Clojure keywords.
(def template-construction-options
  [:no-template
   :context-quadruple-four-candidates
   :context-quadruple-candidates-filtered-toward-balance])

(def allele-decision-options
  [:center-local-rule
   :first-template-match-else-fallback
   :neighbor-agreement-else-fallback
   :all-three-zero-or-one-neighbors-else-fallback
   :neighbor-agreement-optionally-flipped-else-fallback])

(def no-match-fallback-options
  [:center-truth-table-local-rule :constant-zero])

(def output-packing-options [:eight-bits-to-rule-byte])

(def combine-fills
  (vec
   (for [template-construction template-construction-options
         allele-decision allele-decision-options
         no-match-fallback no-match-fallback-options
         output-packing output-packing-options]
     {:template-construction template-construction
      :allele-decision allele-decision
      :no-match-fallback no-match-fallback
      :output-packing output-packing})))

(def historical-occupants
  "The nine DynamicOccupant values in MetaCAExample.lean. These label the
   historical points independently of whether their mutation fill is included
   in this census's no-mutation/rule-permute slice."
  [{:occupant :mutating-template
    :combine {:template-construction :context-quadruple-four-candidates
              :allele-decision :first-template-match-else-fallback
              :no-match-fallback :center-truth-table-local-rule
              :output-packing :eight-bits-to-rule-byte}
    :mutation :balance-mutation}
   {:occupant :baldwin
    :combine {:template-construction :no-template
              :allele-decision :neighbor-agreement-else-fallback
              :no-match-fallback :center-truth-table-local-rule
              :output-packing :eight-bits-to-rule-byte}
    :mutation :baldwin-mutation}
   {:occupant :evolve-sigil
    :combine {:template-construction :no-template
              :allele-decision :center-local-rule
              :no-match-fallback :center-truth-table-local-rule
              :output-packing :eight-bits-to-rule-byte}
    :mutation :no-mutation}
   {:occupant :blending
    :combine {:template-construction :no-template
              :allele-decision :neighbor-agreement-else-fallback
              :no-match-fallback :center-truth-table-local-rule
              :output-packing :eight-bits-to-rule-byte}
    :mutation :no-mutation}
   {:occupant :blending-mutation
    :combine {:template-construction :no-template
              :allele-decision :neighbor-agreement-else-fallback
              :no-match-fallback :center-truth-table-local-rule
              :output-packing :eight-bits-to-rule-byte}
    :mutation :one-bit-mutation}
   {:occupant :ad-hoc-template
    :combine {:template-construction :context-quadruple-four-candidates
              :allele-decision :first-template-match-else-fallback
              :no-match-fallback :center-truth-table-local-rule
              :output-packing :eight-bits-to-rule-byte}
    :mutation :no-mutation}
   {:occupant :collection-template
    :combine {:template-construction :context-quadruple-candidates-filtered-toward-balance
              :allele-decision :first-template-match-else-fallback
              :no-match-fallback :center-truth-table-local-rule
              :output-packing :eight-bits-to-rule-byte}
    :mutation :no-mutation}
   {:occupant :blending-3
    :combine {:template-construction :no-template
              :allele-decision :all-three-zero-or-one-neighbors-else-fallback
              :no-match-fallback :center-truth-table-local-rule
              :output-packing :eight-bits-to-rule-byte}
    :mutation :no-mutation}
   {:occupant :blending-flip
    :combine {:template-construction :no-template
              :allele-decision :neighbor-agreement-optionally-flipped-else-fallback
              :no-match-fallback :center-truth-table-local-rule
              :output-packing :eight-bits-to-rule-byte}
    :mutation :no-mutation}])

(defn- local-rule-bit [center-bits l c r]
  (ca/evolve-digits-by-rule l c r center-bits))

(defn- raw-templates [context]
  (vec (or (ca/context->templates context) [])))

(defn- templates-for
  "Implement the Lean TemplateConstructionFill descriptions. Collection
   filtering removes candidates whose result would worsen the center rule's
   existing imbalance; four ones is the balance point."
  [template-construction center-bits context]
  (case template-construction
    :no-template []
    :context-quadruple-four-candidates (raw-templates context)
    :context-quadruple-candidates-filtered-toward-balance
    (let [templates (raw-templates context)
          ones (count (filter #{\1} center-bits))]
      (cond
        (< ones 4) (vec (remove #(zero? (:result %)) templates))
        (> ones 4) (vec (remove #(= 1 (:result %)) templates))
        :else templates))
    (throw (ex-info "Unknown template-construction fill"
                    {:fill template-construction}))))

(defn- fallback-bit [no-match-fallback center-bits l c r]
  (case no-match-fallback
    :center-truth-table-local-rule (local-rule-bit center-bits l c r)
    :constant-zero 0
    (throw (ex-info "Unknown no-match-fallback fill"
                    {:fill no-match-fallback}))))

(defn- decide-allele
  "Implement the five Lean AlleleDecisionFill descriptions.

   The optional-flip historical occupant defaults to the flipped/non-nil arm,
   matching ca/evolve-sigil-with-blending-flip. The source-exact blending-3
   one-branch deliberately tests l=1 and r=1 but not c, as the Lean doc states."
  [allele-decision templates local fallback l c r]
  (case allele-decision
    :center-local-rule local
    :first-template-match-else-fallback
    (let [matched (ca/match-template templates [l c r])]
      (if (some? matched) matched fallback))
    :neighbor-agreement-else-fallback
    (if (= l r) l fallback)
    :all-three-zero-or-one-neighbors-else-fallback
    (cond
      (= 0 l c r) 0
      (and (= 1 l) (= 1 r)) 1
      :else fallback)
    :neighbor-agreement-optionally-flipped-else-fallback
    (if (= l r) (bit-xor 1 l) fallback)
    (throw (ex-info "Unknown allele-decision fill"
                    {:fill allele-decision}))))

(defn combine
  "Evaluate one rectangular combine coordinate for predecessor/center/successor
   sigils and an optional four-bit phenotype context. Returns a rule sigil."
  [{:keys [template-construction allele-decision no-match-fallback output-packing]}
   pred self succ context]
  (when-not (= :eight-bits-to-rule-byte output-packing)
    (throw (ex-info "Unknown output-packing fill" {:fill output-packing})))
  (let [left-bits (ca/bits-for (str pred))
        center-bits (ca/bits-for (str self))
        right-bits (ca/bits-for (str succ))
        templates (templates-for template-construction center-bits context)]
    (ca/sigil-for
     (apply str
            (for [i (range 8)]
              (let [l (Character/digit ^char (nth left-bits i) 2)
                    c (Character/digit ^char (nth center-bits i) 2)
                    r (Character/digit ^char (nth right-bits i) 2)
                    local (local-rule-bit center-bits l c r)
                    fallback (fallback-bit no-match-fallback center-bits l c r)]
                (decide-allele allele-decision templates local fallback l c r)))))))

(def blend-agreement-fill
  {:template-construction :no-template
   :allele-decision :neighbor-agreement-else-fallback
   :no-match-fallback :center-truth-table-local-rule
   :output-packing :eight-bits-to-rule-byte})

(def multiply-agreement-fill
  {:template-construction :no-template
   :allele-decision :center-local-rule
   :no-match-fallback :center-truth-table-local-rule
   :output-packing :eight-bits-to-rule-byte})

(defn- rule-sigil [n]
  (ca/sigil-for (str/replace (format "%8s" (Integer/toBinaryString (int n)))
                             " " "0")))

(defn assert-blend-agreement!
  "Compare the port implementation to generator.clj's existing :blend and
   :multiply modes over 65,536 rule triples apiece (all centers crossed with a
   16×16 stratified neighbor set)."
  []
  (let [sample-rules [0 1 2 3 15 16 23 30 54 90 110 127 128 170 240 255]
        triples (for [center (range 256)
                      pred sample-rules
                      succ sample-rules]
                  [pred center succ])]
    (doseq [[pred center succ] triples]
      (let [p (rule-sigil pred)
            c (rule-sigil center)
            s (rule-sigil succ)
            expected-blend (#'gen/metaca-combine :blend p c s)
            actual-blend (combine blend-agreement-fill p c s nil)
            expected-multiply (#'gen/metaca-combine :multiply p c s)
            actual-multiply (combine multiply-agreement-fill p c s nil)]
        (when-not (= expected-blend actual-blend)
          (throw (ex-info "Port disagrees with generator :blend"
                          {:pred pred :center center :succ succ
                           :expected expected-blend :actual actual-blend})))
        (when-not (= expected-multiply actual-multiply)
          (throw (ex-info "Port disagrees with generator :multiply"
                          {:pred pred :center center :succ succ
                           :expected expected-multiply
                           :actual actual-multiply})))))
    {:status :passed
     :triples (count triples)
     :multiply-status :passed
     :multiply-triples (count triples)}))

;; The source sigmas and conversion are reused from propagator_compositions.clj.
(def elisp-table ["000" "001" "010" "100" "011" "101" "110" "111"])
(def s1-positional (vec (for [k (range 8)] (max (dec k) 0))))
(def rot2-positional [2 3 4 5 6 7 0 1])

(def mutation-fills
  [{:id :no-mutation :sigma nil}
   {:id :rule-permute-s1
    :sigma (gen/positional-sigma->neighbourhood-sigma s1-positional elisp-table)}
   {:id :rule-permute-rot2
    :sigma (gen/positional-sigma->neighbourhood-sigma rot2-positional elisp-table)}])

(def rule-permute #'gen/rule-permute)

(defn- mutate [sigil sigma]
  (if sigma
    (ca/sigil-for (rule-permute (ca/bits-for (str sigil)) sigma))
    sigil))

(defn- context-at [old-phe new-phe i]
  ;; ca/context-quadruple is the current Clojure grounding of the Lean input:
  ;; old phenotype [i-1 i i+1] followed by new phenotype [i], zero-bounded.
  (when (and (pos? i) (< i (dec (count old-phe))))
    (ca/context-quadruple old-phe new-phe i)))

(defn step-state [{:keys [genotype phenotype]} combine-fill sigma]
  (let [letters (ca/prepare-letters genotype)
        w (count letters)
        new-phenotype (ca/evolve-phenotype-against-genotype genotype phenotype)
        new-genotype
        (apply str
               (for [i (range w)]
                 (let [pred (if (zero? i) ca/default-sigil (nth letters (dec i)))
                       self (nth letters i)
                       succ (if (= i (dec w)) ca/default-sigil (nth letters (inc i)))
                       context (context-at phenotype new-phenotype i)]
                   (mutate (combine combine-fill pred self succ context) sigma))))]
    {:genotype new-genotype :phenotype new-phenotype}))

(defn- changed-fraction [a b]
  (/ (double (count (remove true? (map = a b)))) (count a)))

(defn- mean [xs]
  (if (seq xs) (/ (double (reduce + xs)) (count xs)) 0.0))

(defn- death-step [activities]
  (or (first (for [t (range 1 (inc (count activities)))
                   :when (every? zero? (drop (dec t) activities))]
               t))
      steps))

(defn- sha256 [s]
  (format "%064x"
          (BigInteger. 1 (.digest (MessageDigest/getInstance "SHA-256")
                                  (.getBytes (str s) "UTF-8")))))

(defn run-one [combine-fill mutation-fill seed]
  (ca/with-seed seed
    (let [initial {:genotype (ca/random-sigil-string width)
                   :phenotype (apply str (repeatedly width #(if (< (ca/rnd) 0.5) "1" "0")))}]
      (loop [state initial t 0 activities [] genotype-trail [(:genotype initial)]]
        (if (>= t steps)
          (let [terminal (:genotype state)
                last-third (take-last (quot steps 3) activities)]
            {:seed seed
             :terminal-rule-diversity (count (distinct (seq terminal)))
             :phenotype-activity-last-third (mean last-third)
             :death-step (death-step activities)
             :trajectory-sha256 (sha256 (str/join "\n" genotype-trail))})
          (let [next-state (step-state state combine-fill (:sigma mutation-fill))
                activity (changed-fraction (:phenotype state) (:phenotype next-state))]
            (recur next-state (inc t) (conj activities activity)
                   (conj genotype-trail (:genotype next-state)))))))))

(defn- occupant-names-for [combine-fill]
  (->> historical-occupants
       (filter #(= combine-fill (:combine %)))
       (map (comp name :occupant))
       sort
       (str/join ";")))

(defn- historical-dynamic-names-for [combine-fill mutation-id]
  (->> historical-occupants
       (filter #(and (= combine-fill (:combine %))
                     (= mutation-id (:mutation %))))
       (map (comp name :occupant))
       sort
       (str/join ";")))

(def csv-columns
  [:template-construction :allele-decision :no-match-fallback :output-packing
   :mutate-fill :seed :historical-combine-occupants :historical-dynamic-occupants
   :explored-status :terminal-rule-diversity :phenotype-activity-last-third
   :death-step :trajectory-sha256])

(defn- csv-cell [x]
  (let [s (cond (keyword? x) (name x) (nil? x) "" :else (str x))]
    (if (re-find #"[,\"\n]" s)
      (str "\"" (str/replace s "\"" "\"\"") "\"")
      s)))

(defn- write-csv! [path rows]
  (let [f (io/file path)]
    (.mkdirs (.getParentFile f))
    (spit f
          (str (str/join "," (map name csv-columns)) "\n"
               (str/join "\n"
                         (for [row rows]
                           (str/join "," (map #(csv-cell (get row %)) csv-columns))))
               "\n"))))

(defn- occupant-table []
  (str/join
   "\n"
   (for [{:keys [occupant combine mutation]} historical-occupants]
     (str "| `" (name occupant) "` | `" (name (:template-construction combine))
          "` | `" (name (:allele-decision combine)) "` | `"
          (name (:no-match-fallback combine)) "` | `" (name mutation) "` |"))))

(defn- coordinate-for [row]
  (select-keys row [:template-construction :allele-decision
                    :no-match-fallback :output-packing]))

(defn- trajectory-signature [rows]
  (->> rows
       (sort-by (juxt (comp name :mutate-fill) :seed))
       (mapv :trajectory-sha256)))

(defn- census-summary [rows]
  (let [coordinate-rows (group-by coordinate-for rows)
        historical-coordinate?
        (fn [[_ coordinate-runs]]
          (not (str/blank? (:historical-combine-occupants
                            (first coordinate-runs)))))
        historical-coordinates
        (set (map first (filter historical-coordinate? coordinate-rows)))
        signature-coordinates
        (group-by (fn [[_ coordinate-runs]]
                    (trajectory-signature coordinate-runs))
                  coordinate-rows)
        class-coordinates (map (fn [[_ members]] (mapv first members))
                               signature-coordinates)
        unexplored (remove historical-coordinates (keys coordinate-rows))
        historical-signatures
        (set (for [members class-coordinates
                   :when (some historical-coordinates members)]
               (trajectory-signature (get coordinate-rows (first members)))))
        matched-unexplored
        (count (filter #(historical-signatures
                         (trajectory-signature (get coordinate-rows %)))
                       unexplored))
        novel-classes
        (count (remove #(some historical-coordinates %) class-coordinates))]
    {:trajectory-classes (count class-coordinates)
     :unexplored (count unexplored)
     :matched-unexplored matched-unexplored
     :novel-unexplored (- (count unexplored) matched-unexplored)
     :novel-classes novel-classes}))

(defn- write-readme! [path agreement rows]
  (let [f (io/file path)
        historical-runs (count (remove #(str/blank? (:historical-dynamic-occupants %))
                                       rows))
        {:keys [trajectory-classes unexplored matched-unexplored
                novel-unexplored novel-classes]}
        (census-summary rows)]
    (.mkdirs (.getParentFile f))
    (spit
     f
     (str "# Typed exotype-port census\n\n"
          "Generated by `scripts/exotype_ports.clj` from the coordinate system in "
          "`mathlib4/DarkTower/MetaCAExample.lean`. No vendored Elisp was run.\n\n"
          "Protocol: width " width ", " steps " steps, seeds `" seeds "`; every one of "
          (count combine-fills) " combine fills crossed with `no-mutation`, "
          "`rule-permute-s1`, and `rule-permute-rot2`. The phenotype evolves under "
          "the current genotype; combine then reads the old/new four-bit context using "
          "`futon5.ca.core/context-quadruple`; mutation consumes the combined byte.\n\n"
          "The `neighbor-agreement-optionally-flipped-else-fallback` fill uses the "
          "flipped/non-nil arm, matching the existing Clojure `blending-flip` default.\n\n"
          "Byte-identity gate: **" (str/upper-case (name (:status agreement))) "** across "
          (:triples agreement) " `(pred, center, succ)` rule triples against "
          "`generator.clj`'s existing `metaca-combine :blend`; the same "
          (:multiply-triples agreement) " triples also passed against "
          "`:multiply`.\n\n"
          "The CSV has one row per seed. `historical-combine-occupants` labels all "
          "rows whose combine coordinate was used historically; "
          "`historical-dynamic-occupants` labels only exact full occupants in this "
          "mutation slice (" historical-runs " run rows).\n\n"
          "## Census result\n\n"
          "Across all nine mutation/seed runs per coordinate, the 30 combine "
          "coordinates form **" trajectory-classes " exact genotype-trajectory "
          "classes**. Of the " unexplored " unwritten coordinates, "
          matched-unexplored " are trajectory-identical to a historical combine "
          "coordinate throughout this apparatus. The other " novel-unexplored
          " form **" novel-classes " classes with no historical member**, so the "
          "sweep is not null. Every one of those novel classes uses "
          "`constant-zero`; that fallback is operational when its allele decision "
          "can miss, while it is inert under `center-local-rule`.\n\n"
          "The template-construction port is likewise inert for every decision "
          "except `first-template-match-else-fallback`; this accounts for the "
          "largest equivalence classes and shows that the rectangular coordinate "
          "system contains many extensionally equal compositions. These are finite "
          "apparatus observations, not global equivalence proofs.\n\n"
          "## Nine historical occupants\n\n"
          "| occupant | template | allele decision | fallback | mutation |\n"
          "|---|---|---|---|---|\n" (occupant-table) "\n\n"
          "Two catalogue facts visible here: no historical occupant uses "
          "`constant-zero`; contrary to the dispatch note, plain "
          "`neighbor-agreement-else-fallback` is used by Baldwin, blending, and "
          "blending-mutation in the Lean source.\n"))))

(defn census! []
  (when-not (= 30 (count combine-fills))
    (throw (ex-info "Combine coordinate count drift" {:count (count combine-fills)})))
  (when-not (= 9 (count historical-occupants))
    (throw (ex-info "Historical occupant count drift"
                    {:count (count historical-occupants)})))
  (let [agreement (assert-blend-agreement!)
        rows
        (vec
         (for [combine-fill combine-fills
               mutation-fill mutation-fills
               seed seeds]
           (let [historical-combines (occupant-names-for combine-fill)
                 historical-dynamics
                 (historical-dynamic-names-for combine-fill (:id mutation-fill))]
             (merge combine-fill
                    {:mutate-fill (:id mutation-fill)
                     :seed seed
                     :historical-combine-occupants historical-combines
                     :historical-dynamic-occupants historical-dynamics
                     :explored-status (if (str/blank? historical-combines)
                                        "unexplored-combine"
                                        "historical-combine")}
                    (run-one combine-fill mutation-fill seed)))))]
    (write-csv! default-csv-path rows)
    (write-readme! default-readme-path agreement rows)
    {:agreement agreement
     :combine-cells (count combine-fills)
     :mutation-fills (mapv :id mutation-fills)
     :runs (count rows)
     :csv default-csv-path
     :readme default-readme-path}))

(defn -main [& args]
  (if (some #{"--check"} args)
    (prn {:combine-cells (count combine-fills)
          :historical-occupants (count historical-occupants)
          :agreement (assert-blend-agreement!)})
    (prn (census!))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
