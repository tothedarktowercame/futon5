(ns futon5.ca.core
  "Clojure port of the 256ca meta-cellular automaton core.
  Provides sigil metadata, genotype/phenotype helpers, and evolution
  routines that can later be wired into sound (Overtone) or other
  improvisation substrates."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

;; -----------------------------------------------------------------------------
;; Data tables

(def truth-table-3
  ["000" "001" "010" "011" "100" "101" "110" "111"])

(def sigils
  (delay (-> "futon5/sigils.edn" io/resource slurp edn/read-string)))

(defn sigil-entries [] @sigils)

(def bits->entry-map
  (delay (into {} (map (juxt :bits identity) (sigil-entries)))))

(def sigil->entry-map
  (delay (into {} (map (juxt :sigil identity) (sigil-entries)))))

(def default-sigil "一")

(defn entry-for-sigil [sigil]
  (let [lookup @sigil->entry-map]
    (or (lookup (or sigil default-sigil))
        (throw (ex-info "Unknown sigil" {:sigil sigil})))))

(defn entry-for-bits [bits]
  (or (@bits->entry-map bits)
      (throw (ex-info "Unknown genotype bits" {:bits bits}))))

(defn bits-for [sigil]
  (:bits (entry-for-sigil sigil)))

(defn sigil-for [bits]
  (:sigil (entry-for-bits bits)))

(defn color-for [sigil]
  (:color (entry-for-sigil sigil)))

(defn translation-for [sigil]
  (:translation (entry-for-sigil sigil)))

;; -----------------------------------------------------------------------------
;; Random helpers

(defn random-sigil []
  (:sigil (rand-nth (sigil-entries))))

(defn random-sigil-string [len]
  (apply str (repeatedly len random-sigil)))

(defn random-phenotype-string [len]
  (apply str (repeatedly len #(rand-int 2))))

;; -----------------------------------------------------------------------------
;; Bit manipulation helpers

(defn safe-sigil [sigil]
  (or sigil default-sigil))

(defn bits->ints [bits]
  (mapv #(Character/digit ^char % 2) bits))


(defn ints->bits [ints]
  (apply str ints))

(defn flip-bit [bit]
  (bit-xor bit 1))

(defn flip-ints
  ([ints]
   (mapv flip-bit ints))
  ([ints idxs]
   (reduce (fn [acc idx] (update acc idx flip-bit))
           (vec ints)
           idxs)))

(defn mutate-rule-n [bits-str n]
  (loop [chars (vec bits-str)
         remaining n]
    (if (zero? remaining)
      (apply str chars)
      (let [idx (rand-int (count chars))]
        (recur (assoc chars idx (if (= (chars idx) \0) \1 \0))
               (dec remaining))))))

(defn local-rule-table [sigil]
  (zipmap truth-table-3 (bits->ints (bits-for sigil))))

(defn local-data [pred-bits sig-bits next-bits]
  (map (fn [a b c] (str a b c)) pred-bits sig-bits next-bits))

(defn neighborhood-context [sigil pred next]
  (let [pred-bits (bits-for (safe-sigil pred))
        sig-bits (bits-for sigil)
        next-bits (bits-for (safe-sigil next))]
    {:pred pred-bits
     :sig sig-bits
     :next next-bits
     :rule (local-rule-table sigil)
     :triples (local-data pred-bits sig-bits next-bits)}))

(defn apply-rule [ctx triple]
  ((:rule ctx) triple))

(defn compute-outputs [ctx output-fn]
  (ints->bits
   (map (fn [triple]
          (output-fn ctx triple (bits->ints triple)))
        (:triples ctx))))

(defn context->templates [context]
  (let [ctx (cond
              (nil? context) nil
              (string? context) context
              :else (str context))]
    (when (and ctx (>= (count ctx) 4))
      (let [[a b c d] (take 4 (bits->ints ctx))
            f flip-bit]
        [{:parent [a b c] :result d}
         {:parent [(f a) (f b) (f c)] :result (f d)}
         {:parent [a (f b) c] :result d}
         {:parent [(f a) b (f c)] :result (f d)}]))))

(defn match-template [templates target]
  (some (fn [{:keys [parent result]}]
          (when (= parent target) result))
        templates))

(defn neighbors-match? [ints value]
  (and (= (first ints) value)
       (= (nth ints 2) value)))

(defn all-same? [ints value]
  (every? #(= % value) ints))

(defn context-match-count [context]
  (when (and (string? context) (seq context))
    (let [digits (bits->ints context)
          target (last digits)
          olds (butlast digits)]
      (count (filter #(= % target) olds)))))

(defn randomly-flip-selected [bits value quantity]
  (let [positions (keep-indexed (fn [idx bit]
                                  (when (= bit value) idx))
                                bits)
        to-flip (->> positions shuffle (take (min quantity (count positions))))]
    (reduce (fn [acc idx]
              (update acc idx flip-bit))
            bits
            to-flip)))

(defn balance-mutation [bits-str]
  (let [bits (vec (bits->ints bits-str))
        ones (count (filter #(= 1 %) bits))]
    (cond
      (and (> ones 6) (zero? (rand-int 20))) (ints->bits (randomly-flip-selected bits 1 1))
      (and (< ones 2) (zero? (rand-int 20))) (ints->bits (randomly-flip-selected bits 0 1))
      :else bits-str)))

;; -----------------------------------------------------------------------------
;; Evolution kernels

(defn evolve-sigil-multiplication
  ([sigil] (evolve-sigil-multiplication sigil nil nil nil))
  ([sigil pred next] (evolve-sigil-multiplication sigil pred next nil))
  ([sigil pred next context]
   (let [ctx (neighborhood-context sigil pred next)
         outputs (compute-outputs ctx (fn [ctx triple _] (apply-rule ctx triple)))]
     (entry-for-bits outputs))))

(defn evolve-sigil-with-blending*
  [sigil pred next {:keys [flip?]}]
  (let [ctx (neighborhood-context sigil pred next)
        outputs (compute-outputs ctx (fn [ctx triple ints]
                                      (cond
                                        (neighbors-match? ints 0) (if flip? 1 0)
                                        (neighbors-match? ints 1) (if flip? 0 1)
                                        :else (apply-rule ctx triple))))]
    (entry-for-bits outputs)))

(defn evolve-sigil-with-blending
  ([sigil] (evolve-sigil-with-blending sigil nil nil nil))
  ([sigil pred next] (evolve-sigil-with-blending sigil pred next nil))
  ([sigil pred next context]
   (let [flip? (when (map? context) (boolean (or (:flip? context) (:flip context))))]
     (evolve-sigil-with-blending* sigil pred next {:flip? flip?}))))

(defn evolve-sigil-with-blending-3
  ([sigil] (evolve-sigil-with-blending-3 sigil nil nil nil))
  ([sigil pred next] (evolve-sigil-with-blending-3 sigil pred next nil))
  ([sigil pred next context]
   (let [ctx (neighborhood-context sigil pred next)
         outputs (compute-outputs ctx (fn [ctx triple ints]
                                       (cond
                                         (all-same? ints 0) 0
                                         (all-same? ints 1) 1
                                         :else (apply-rule ctx triple))))]
     (entry-for-bits outputs))))

(defn evolve-sigil-with-blending-flip
  ([sigil] (evolve-sigil-with-blending-flip sigil nil nil {:flip? true}))
  ([sigil pred next] (evolve-sigil-with-blending-flip sigil pred next {:flip? true}))
  ([sigil pred next context]
   (let [flip? (cond
                 (map? context) (boolean (or (:flip? context) (:flip context)))
                 (boolean? context) context
                 :else true)]
     (evolve-sigil-with-blending* sigil pred next {:flip? flip?}))))

(defn evolve-sigil-with-blending-mutation
  ([sigil] (evolve-sigil-with-blending-mutation sigil nil nil nil))
  ([sigil pred next] (evolve-sigil-with-blending-mutation sigil pred next nil))
  ([sigil pred next context]
   (let [ctx (neighborhood-context sigil pred next)
         outputs (compute-outputs ctx (fn [ctx triple ints]
                                       (cond
                                         (neighbors-match? ints 0) 0
                                         (neighbors-match? ints 1) 1
                                         :else (apply-rule ctx triple))))
         mutated (mutate-rule-n outputs 1)]
     (entry-for-bits mutated))))

(defn evolve-sigil-with-blending-baldwin
  ([sigil] (evolve-sigil-with-blending-baldwin sigil nil nil nil))
  ([sigil pred next] (evolve-sigil-with-blending-baldwin sigil pred next nil))
  ([sigil pred next context]
   (let [ctx (neighborhood-context sigil pred next)
         outputs (compute-outputs ctx (fn [ctx triple ints]
                                       (cond
                                         (neighbors-match? ints 0) 0
                                         (neighbors-match? ints 1) 1
                                         :else (apply-rule ctx triple))))
         match-count (context-match-count context)
         final (if (and match-count (zero? (rand-int 3)))
                 (mutate-rule-n outputs (+ match-count 2))
                 outputs)]
     (entry-for-bits final))))

(defn evolve-sigil-with-ad-hoc-template
  ([sigil] (evolve-sigil-with-ad-hoc-template sigil nil nil nil))
  ([sigil pred next] (evolve-sigil-with-ad-hoc-template sigil pred next nil))
  ([sigil pred next context]
   (let [ctx (neighborhood-context sigil pred next)
         templates (context->templates context)
         outputs (compute-outputs ctx (fn [ctx triple ints]
                                       (or (match-template templates ints)
                                           (apply-rule ctx triple))))
         mutated (balance-mutation outputs)]
     (entry-for-bits mutated))))

(defn evolve-sigil-with-collection-template
  ([sigil] (evolve-sigil-with-collection-template sigil nil nil nil))
  ([sigil pred next] (evolve-sigil-with-collection-template sigil pred next nil))
  ([sigil pred next context]
   (let [ctx (neighborhood-context sigil pred next)
         ones (count (filter #(= 1 %) (bits->ints (bits-for sigil))))
         to-swap (- 4 ones)
         raw-templates (context->templates context)
         templates (cond
                     (> to-swap 0) (remove #(zero? (:result %)) raw-templates)
                     (< to-swap 0) (remove #(= 1 (:result %)) raw-templates)
                     :else raw-templates)
         outputs (compute-outputs ctx (fn [ctx triple ints]
                                       (or (match-template templates ints)
                                           (apply-rule ctx triple))))]
     (entry-for-bits outputs))))

(defn evolve-sigil-with-mutating-template
  ([sigil] (evolve-sigil-with-mutating-template sigil nil nil nil))
  ([sigil pred next] (evolve-sigil-with-mutating-template sigil pred next nil))
  ([sigil pred next context]
   (let [pred-bits (bits-for (or pred default-sigil))
         sig-bits (bits-for sigil)
         next-bits (bits-for (or next default-sigil))
         rule (local-rule-table sigil)
         templates (context->templates context)
         outputs (->> (local-data pred-bits sig-bits next-bits)
                      (map (fn [triple]
                             (let [parent (bits->ints triple)]
                               (or (match-template templates parent)
                                   (rule triple)))))
                      ints->bits)
         mutated (balance-mutation outputs)]
     (entry-for-bits mutated))))

(def kernels
  {:multiplication evolve-sigil-multiplication
   :blending evolve-sigil-with-blending
   :blending-3 evolve-sigil-with-blending-3
   :blending-flip evolve-sigil-with-blending-flip
   :blending-mutation evolve-sigil-with-blending-mutation
   :blending-baldwin evolve-sigil-with-blending-baldwin
   :ad-hoc-template evolve-sigil-with-ad-hoc-template
   :collection-template evolve-sigil-with-collection-template
   :mutating-template evolve-sigil-with-mutating-template})

(defn kernel-fn [kernel]
  (or (get kernels kernel)
      (throw (ex-info "Unknown kernel" {:kernel kernel}))))

(def ^:dynamic *evolve-sigil-fn* (kernel-fn :mutating-template))

(defn set-default-kernel! [kernel]
  (alter-var-root #'*evolve-sigil-fn* (constantly (kernel-fn kernel))))

(defmacro with-kernel [kernel & body]
  `(binding [*evolve-sigil-fn* (kernel-fn ~kernel)]
     ~@body))

(defn evolve-sigil
  ([sigil pred next] (evolve-sigil sigil pred next nil))
  ([sigil pred next context]
   (*evolve-sigil-fn* sigil pred next context)))

;; -----------------------------------------------------------------------------
;; String evolution helpers

(defn prepare-letters [s]
  (vec (map str s)))

(defn evolve-sigil-string
  ([genotype] (evolve-sigil-string genotype nil))
  ([genotype landscape]
   (let [letters (prepare-letters genotype)
         len (count letters)
         contexts (vec (if landscape (map str landscape) (repeat len nil)))]
     (cond
       (zero? len) ""
       (= 1 len) (:sigil (evolve-sigil (letters 0) default-sigil default-sigil nil))
       :else
       (let [head (:sigil (evolve-sigil (letters 0) default-sigil (letters 1) nil))
             tail (:sigil (evolve-sigil (letters (dec len)) (letters (- len 2)) default-sigil nil))
             mids (map (fn [idx]
                         (:sigil (evolve-sigil (letters idx)
                                               (letters (dec idx))
                                               (letters (inc idx))
                                               (contexts idx))))
                       (range 1 (dec len)))]
         (apply str (concat [head] mids [tail])))))))

(defn padded-char [s idx]
  (if (and s (<= 0 idx) (< idx (count s)))
    (nth s idx)
    \0))

(defn context-quadruple [old new idx]
  (apply str [(padded-char old (dec idx))
              (padded-char old idx)
              (padded-char old (inc idx))
              (padded-char new idx)]))

(defn evolve-sigil-string-contextually
  ([genotype old-landscape] (evolve-sigil-string-contextually genotype old-landscape nil))
  ([genotype old-landscape new-landscape]
   (let [letters (prepare-letters genotype)
         len (count letters)]
     (cond
       (zero? len) ""
       (= 1 len) (:sigil (evolve-sigil (letters 0) default-sigil default-sigil nil))
       :else
       (let [head (:sigil (evolve-sigil (letters 0) default-sigil (letters 1) nil))
             tail (:sigil (evolve-sigil (letters (dec len)) (letters (- len 2)) default-sigil nil))
             contexts (map (fn [idx] (context-quadruple old-landscape new-landscape idx))
                           (range 1 (dec len)))
             mids (map (fn [idx context]
                         (:sigil (evolve-sigil (letters idx)
                                               (letters (dec idx))
                                               (letters (inc idx))
                                               context)))
                       (range 1 (dec len))
                       contexts)]
         (apply str (concat [head] mids [tail])))))))

;; -----------------------------------------------------------------------------
;; Phenotype helpers

(defn evolve-digits-by-rule [first-digit second-digit third-digit rule]
  (let [triple (str first-digit second-digit third-digit)
        idx (.indexOf truth-table-3 triple)]
    (Character/digit ^char (nth rule idx) 2)))

(defn evolve-phenotype-against-genotype [genotype phenotype]
  (let [digits (vec (map #(Character/digit ^char % 2) phenotype))
        letters (prepare-letters genotype)
        len (count letters)
        prevs (concat [0] (butlast digits))
        selfs digits
        nexts (concat (rest digits) [0])]
    (apply str
           (map (fn [a b c letter]
                  (let [rule (bits-for letter)]
                    (evolve-digits-by-rule a b c rule)))
                prevs selfs nexts letters))))

(defn co-evolve-phenotype-and-genotype [genotype phenotype]
  (let [new-phenotype (evolve-phenotype-against-genotype genotype phenotype)
        new-genotype (evolve-sigil-string-contextually genotype phenotype new-phenotype)]
    [new-genotype new-phenotype]))

;; -----------------------------------------------------------------------------
;; Simulation runners

(defn run-for-generations [genotype n]
  (loop [history [genotype]
         step 0]
    (if (= step n)
      history
      (recur (conj history (evolve-sigil-string (peek history))) (inc step)))))

(defn run-for-generations-3 [genotype phenotype n]
  (loop [gen-history [genotype]
         phe-history [phenotype]
         step 0]
    (if (= step n)
      [gen-history phe-history]
      (let [[next-gen next-phe] (co-evolve-phenotype-and-genotype (peek gen-history)
                                                                  (peek phe-history))]
        (recur (conj gen-history next-gen)
               (conj phe-history next-phe)
               (inc step))))))

;; -----------------------------------------------------------------------------
;; Meta-evolution helpers

(defn hamming-distance [a b]
  (reduce + (map (fn [x y] (if (= x y) 0 1)) a b)))

(defn average-hamming [history]
  (let [pairs (partition 2 1 history)]
    (if (seq pairs)
      (/ (double (reduce + (map (fn [[x y]] (hamming-distance x y)) pairs)))
         (count pairs))
      0.0)))

(defn first-stasis-step [history]
  (some (fn [[idx prev curr]] (when (= prev curr) idx))
        (map vector (iterate inc 1) history (rest history))))

(defn zero-phenotype-step [history]
  (some (fn [[idx val]]
          (when (every? #(= % \0) val) idx))
        (map vector (range (count history)) history)))

(defn simulate-kernel
  [{:keys [kernel genotype phenotype generations]
    :or {kernel :mutating-template generations 32}}]
  (with-kernel kernel
    (if phenotype
      (let [[gen-history phe-history] (run-for-generations-3 genotype phenotype generations)]
        {:kernel kernel
         :gen-history gen-history
         :phe-history phe-history})
      {:kernel kernel
       :gen-history (run-for-generations genotype generations)})))

(defn summarize-kernel [{:keys [kernel] :as opts}]
  (let [{:keys [gen-history phe-history]} (simulate-kernel opts)
        unique-states (count (distinct gen-history))
        unique-sigils (count (set (apply str gen-history)))
        avg-hamming (average-hamming gen-history)
        stasis (first-stasis-step gen-history)
        zero-step (when phe-history (zero-phenotype-step phe-history))]
    {:kernel kernel
     :generations (dec (count gen-history))
     :final-genotype (last gen-history)
     :unique-states unique-states
     :unique-sigils unique-sigils
     :average-hamming avg-hamming
     :stasis-step stasis
     :phenotype-zero-step zero-step
     :gen-history gen-history
     :phe-history phe-history}))

(defn survey-kernels
  ([opts] (survey-kernels (keys kernels) opts))
  ([kernel-order opts]
   (mapv (fn [k]
           (summarize-kernel (assoc opts :kernel k)))
         kernel-order)))
