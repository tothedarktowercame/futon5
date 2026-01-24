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
              (map? context) (or (:phenotype-context context) (str context))
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
  (let [ctx (cond
              (string? context) context
              (map? context) (:phenotype-context context)
              :else nil)]
    (when (and (string? ctx) (seq ctx))
      (let [digits (bits->ints ctx)
            target (last digits)
            olds (butlast digits)]
        (count (filter #(= % target) olds))))))

(defn randomly-flip-selected [bits value quantity]
  (let [positions (keep-indexed (fn [idx bit]
                                  (when (= bit value) idx))
                                bits)
        to-flip (->> positions shuffle (take (min quantity (count positions))))]
    (reduce (fn [acc idx]
              (update acc idx flip-bit))
            bits
            to-flip)))

(def ^:private default-balance-spec
  {:enabled? false
   :min-ones 2
   :max-ones 6
   :chance 0.05
   :flip-count 1})

(def ^:private default-mutation-spec
  {:mode :none
   :count 0
   :offset 2
   :prob 0.33})

(def ^:private default-kernel-reserved-bits (apply str (repeat 6 "0")))

(def ^:private default-kernel-spec
  {:blend-mode :none
   :flip? false
   :template-mode :none
   :mutation default-mutation-spec
   :balance default-balance-spec
   :mix-mode :none
   :mix-shift 0
   :reserved default-kernel-reserved-bits})

(def ^:private kernel-spec-keys
  #{:blend-mode :flip? :template-mode :mutation :balance :label})

(defn kernel-spec?
  [spec]
  (and (map? spec)
       (some #(contains? spec %) kernel-spec-keys)))

(defn normalize-kernel-spec
  [spec]
  (let [spec (merge default-kernel-spec (or spec {}))
        mutation (merge default-mutation-spec (:mutation spec))
        balance (merge default-balance-spec (:balance spec))
        reserved (or (:reserved spec) default-kernel-reserved-bits)]
    (assoc spec :mutation mutation :balance balance :reserved reserved)))

(def ^:private kernel-specs
  {:multiplication {:blend-mode :none
                    :template-mode :none}
   :blending {:blend-mode :neighbors
              :template-mode :none}
   :blending-3 {:blend-mode :all
                :template-mode :none}
   :blending-flip {:blend-mode :neighbors
                   :flip? true
                   :template-mode :none}
   :blending-mutation {:blend-mode :neighbors
                       :template-mode :none
                       :mutation {:mode :fixed :count 1}}
   :blending-baldwin {:blend-mode :neighbors
                      :template-mode :none
                      :mutation {:mode :baldwin :offset 2 :prob 0.33}}
   :ad-hoc-template {:blend-mode :none
                     :template-mode :context
                     :balance {:enabled? true}}
   :collection-template {:blend-mode :none
                         :template-mode :collection}
   :mutating-template {:blend-mode :none
                       :template-mode :context
                       :balance {:enabled? true}}})

(def ^:private kernel-prob-values [0.2 0.33 0.5 1.0])
(def ^:private kernel-balance-min-values [1 2 3 4])
(def ^:private kernel-balance-max-values [5 6 7 7])
(def ^:private kernel-balance-chance-values [0.02 0.05 0.1 0.2])

(def ^:private kernel-mix-modes
  [:none :rotate-left :rotate-right :reverse :xor-neighbor :majority :swap-halves :scramble])

(def ^:private kernel-mix-shifts [0 1 2 3])

(def ^:private kernel-bit-layout
  [[:blend-mode 2]
   [:flip? 1]
   [:template-mode 2]
   [:mutation-mode 2]
   [:mutation-count 2]
   [:mutation-offset 2]
   [:mutation-prob 2]
   [:balance-enabled 1]
   [:balance-min 2]
   [:balance-max 2]
   [:balance-chance 2]
   [:balance-flip 1]
   [:mix-mode 3]
   [:mix-shift 2]
   [:reserved 6]])

(defn- pad-right [s len fill]
  (let [s (or s "")]
    (cond
      (= (count s) len) s
      (< (count s) len) (str s (apply str (repeat (- len (count s)) fill)))
      :else (subs s 0 len))))

(defn- bits->num [bits]
  (reduce (fn [acc bit] (+ (* acc 2) bit)) 0 bits))

(defn- num->bits [n width]
  (let [bits (loop [i width
                    acc []
                    v (long n)]
               (if (zero? i)
                 acc
                 (recur (dec i) (conj acc (bit-and v 1)) (bit-shift-right v 1))))]
    (vec (reverse bits))))

(defn- value->index [value values]
  (let [idx (.indexOf ^java.util.List values value)]
    (if (neg? idx) 0 idx)))

(defn- index->value [idx values]
  (nth values (min idx (dec (count values)))))

(defn kernel-spec->bits
  [spec]
  (let [{:keys [blend-mode flip? template-mode mutation balance mix-mode mix-shift reserved]}
        (normalize-kernel-spec spec)
        mut-mode (:mode mutation)
        mut-count (long (or (:count mutation) 0))
        mut-offset (long (or (:offset mutation) 0))
        mut-prob (double (or (:prob mutation) 0.0))
        balance? (boolean (:enabled? balance))
        balance-min (long (or (:min-ones balance) 0))
        balance-max (long (or (:max-ones balance) 0))
        balance-chance (double (or (:chance balance) 0.0))
        balance-flip (long (or (:flip-count balance) 1))
        reserved (or reserved default-kernel-reserved-bits)
        fields {:blend-mode (case blend-mode :none 0 :neighbors 1 :all 2 3)
                :flip? (if flip? 1 0)
                :template-mode (case template-mode :none 0 :context 1 :collection 2 3)
                :mutation-mode (case mut-mode :none 0 :fixed 1 :baldwin 2 3)
                :mutation-count (max 0 (min 3 mut-count))
                :mutation-offset (max 0 (min 3 (dec (max 1 mut-offset))))
                :mutation-prob (value->index mut-prob kernel-prob-values)
                :balance-enabled (if balance? 1 0)
                :balance-min (value->index balance-min kernel-balance-min-values)
                :balance-max (value->index balance-max kernel-balance-max-values)
                :balance-chance (value->index balance-chance kernel-balance-chance-values)
                :balance-flip (if (>= balance-flip 2) 1 0)
                :mix-mode (value->index mix-mode kernel-mix-modes)
                :mix-shift (value->index mix-shift kernel-mix-shifts)
                :reserved (bits->ints (pad-right reserved 6 "0"))}
        bits (reduce (fn [acc [field width]]
                       (if (= field :reserved)
                         (into acc (take width (:reserved fields)))
                         (into acc (num->bits (get fields field 0) width))))
                     []
                     kernel-bit-layout)]
    (ints->bits bits)))

(defn bits->kernel-spec
  [bits]
  (let [bits (bits->ints (pad-right bits 32 "0"))
        fields (loop [idx 0
                      layout kernel-bit-layout
                      acc {}]
                 (if (empty? layout)
                   acc
                   (let [[field width] (first layout)
                         slice (subvec bits idx (+ idx width))
                         value (if (= field :reserved)
                                 slice
                                 (bits->num slice))]
                     (recur (+ idx width) (rest layout) (assoc acc field value)))))
        blend-mode (nth [:none :neighbors :all :none] (:blend-mode fields))
        template-mode (nth [:none :context :collection :none] (:template-mode fields))
        mutation-mode (nth [:none :fixed :baldwin :none] (:mutation-mode fields))
        mutation (case mutation-mode
                   :fixed {:mode :fixed
                           :count (max 1 (:mutation-count fields))
                           :prob (index->value (:mutation-prob fields) kernel-prob-values)}
                   :baldwin {:mode :baldwin
                             :offset (inc (:mutation-offset fields))
                             :prob (index->value (:mutation-prob fields) kernel-prob-values)}
                   {:mode :none :count 0 :prob 0.0})
        balance {:enabled? (= 1 (:balance-enabled fields))
                 :min-ones (index->value (:balance-min fields) kernel-balance-min-values)
                 :max-ones (index->value (:balance-max fields) kernel-balance-max-values)
                 :chance (index->value (:balance-chance fields) kernel-balance-chance-values)
                 :flip-count (if (= 1 (:balance-flip fields)) 2 1)}
        mix-mode (index->value (:mix-mode fields) kernel-mix-modes)
        mix-shift (index->value (:mix-shift fields) kernel-mix-shifts)
        reserved (ints->bits (:reserved fields))]
    (normalize-kernel-spec {:blend-mode blend-mode
                            :flip? (= 1 (:flip? fields))
                            :template-mode template-mode
                            :mutation mutation
                            :balance balance
                            :mix-mode mix-mode
                            :mix-shift mix-shift
                            :reserved reserved})))

(defn kernel-spec->sigils
  [spec]
  (let [bits (kernel-spec->bits spec)
        groups (partition 8 8 nil bits)]
    (mapv (fn [group]
            (sigil-for (apply str group)))
          groups)))

(defn kernel-sigils->bits
  [sigils]
  (apply str (map bits-for sigils)))

(defn kernel-sigils->spec
  [sigils]
  (bits->kernel-spec (kernel-sigils->bits sigils)))

(defn- sigil-bits [sigil]
  (bits->ints (bits-for sigil)))

(defn- kernel-output-bits
  [input-bits output-rule context-bits]
  (mapv (fn [idx]
          (let [parent [(nth (input-bits 0) idx)
                        (nth (input-bits 1) idx)
                        (nth (input-bits 2) idx)]
                ctx (when context-bits
                      [(nth (context-bits 0) idx)
                       (nth (context-bits 1) idx)
                       (nth (context-bits 2) idx)
                       (nth (context-bits 3) idx)])
                templates (when ctx (context->templates (ints->bits ctx)))
                template-result (when templates (match-template templates parent))
                triple (apply str parent)]
            (or template-result (output-rule triple))))
        (range 8)))

(defn- match-rate
  [a b]
  (when (and (seq a) (seq b) (= (count a) (count b)))
    (let [matches (count (filter true? (map = a b)))
          total (double (count a))]
      (if (pos? total)
        (/ matches total)
        0.0))))

(defn- phenotype-inverts?
  [phenotype-context]
  (when (and phenotype-context (>= (count phenotype-context) 2))
    (= (nth phenotype-context 1) \0)))

(defn- kernel-baldwin-mutate
  [bits-str phenotype-context]
  (if (and phenotype-context (< (rand) 0.33))
    (let [matches (context-match-count phenotype-context)]
      (mutate-rule-n bits-str (+ (or matches 0) 2)))
    bits-str))

(defn mutate-kernel-spec-contextual
  [spec {:keys [context-sigils phenotype-context rotation match-threshold invert-on-phenotype?]}]
  (let [kernel-sigils (kernel-spec->sigils spec)
        rotation (long (or rotation 0))
        idxs (mapv #(mod (+ rotation %) 4) (range 4))
        input-sigils (mapv kernel-sigils (subvec idxs 0 3))
        output-idx (nth idxs 3)
        output-sigil (kernel-sigils output-idx)
        output-rule (local-rule-table output-sigil)
        input-bits (mapv sigil-bits input-sigils)
        context-sigils (when (and (seq context-sigils)
                                  (= 4 (count context-sigils)))
                         context-sigils)
        context-bits (when context-sigils
                       (mapv sigil-bits context-sigils))
        output-bits (kernel-output-bits input-bits output-rule context-bits)
        observed-bits (when context-sigils
                        (sigil-bits (nth context-sigils 3)))
        match-bits (if (and invert-on-phenotype?
                            (phenotype-inverts? phenotype-context))
                     (flip-ints output-bits)
                     output-bits)
        match-score (when observed-bits
                      (match-rate match-bits observed-bits))
        match-threshold (when (number? match-threshold) (double match-threshold))
        update? (or (nil? match-threshold)
                    (nil? match-score)
                    (< match-score match-threshold))
        output-str (ints->bits output-bits)
        output-str (kernel-baldwin-mutate output-str phenotype-context)
        new-output (sigil-for output-str)
        new-sigils (assoc kernel-sigils output-idx new-output)]
    (if update?
      (-> (kernel-sigils->spec new-sigils)
          (assoc :label nil))
      spec)))

(defn kernel-spec-for
  [kernel]
  (cond
    (kernel-spec? kernel) (normalize-kernel-spec kernel)
    (keyword? kernel) (if-let [spec (get kernel-specs kernel)]
                        (normalize-kernel-spec (assoc spec :label (name kernel)))
                        (throw (ex-info "Unknown kernel for spec" {:kernel kernel})))
    :else (throw (ex-info "Unsupported kernel spec" {:kernel kernel}))))

(defn kernel-spec-needs-context?
  [kernel]
  (let [spec (kernel-spec-for kernel)
        template? (not= :none (:template-mode spec))
        mutation? (= :baldwin (get-in spec [:mutation :mode]))]
    (or template? mutation?)))

(defn kernel-label
  [kernel]
  (cond
    (keyword? kernel) (name kernel)
    (kernel-spec? kernel) (or (:label kernel)
                              (let [spec-hash (Integer/toHexString (hash (dissoc kernel :label)))
                                    suffix (subs spec-hash 0 (min 6 (count spec-hash)))]
                                (str "spec-" suffix)))
    (fn? kernel) "custom"
    (nil? kernel) "none"
    :else (str kernel)))

(defn kernel-id
  [kernel]
  (cond
    (keyword? kernel) (name kernel)
    (kernel-spec? kernel)
    (let [label (or (:label kernel) "spec")
          spec-hash (Integer/toHexString (hash (dissoc kernel :label)))
          suffix (subs spec-hash 0 (min 6 (count spec-hash)))]
      (str label "-" suffix))
    (fn? kernel) "custom"
    (nil? kernel) "none"
    :else (str kernel)))

(defn- balance-mutation* [bits-str {:keys [enabled? min-ones max-ones chance flip-count]}]
  (if (not enabled?)
    bits-str
    (let [bits (vec (bits->ints bits-str))
          ones (count (filter #(= 1 %) bits))
          chance (double (or chance 0.0))
          flip-count (max 1 (long (or flip-count 1)))
          min-ones (long (or min-ones 0))
          max-ones (long (or max-ones (count bits)))]
      (cond
        (and (> ones max-ones) (< (rand) chance))
        (ints->bits (randomly-flip-selected bits 1 flip-count))
        (and (< ones min-ones) (< (rand) chance))
        (ints->bits (randomly-flip-selected bits 0 flip-count))
        :else bits-str))))

(defn balance-mutation [bits-str]
  (balance-mutation* bits-str (assoc default-balance-spec :enabled? true)))

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

(defn kernel-spec-summary
  [kernel]
  (when (kernel-spec? kernel)
    (let [{:keys [blend-mode template-mode flip? mutation balance mix-mode mix-shift]} (normalize-kernel-spec kernel)
          mut-mode (:mode mutation)
          mut-count (:count mutation)
          mut-offset (:offset mutation)
          mut-prob (:prob mutation)
          balance? (:enabled? balance)]
      (format "blend=%s template=%s flip=%s mutation=%s count=%s offset=%s prob=%.2f balance=%s mix=%s shift=%s"
              (name blend-mode)
              (name template-mode)
              (boolean flip?)
              (name mut-mode)
              (long (or mut-count 0))
              (long (or mut-offset 0))
              (double (or mut-prob 0.0))
              (if balance? "on" "off")
              (name mix-mode)
              (long (or mix-shift 0))))))

(defn- template-entries
  [spec sigil context]
  (when (and context (not= :none (:template-mode spec)))
    (let [strictness (when (map? context) (:template-strictness context))
          strictness (when (number? strictness) (double strictness))
          strictness (when strictness (max 0.0 (min 1.0 strictness)))
          allow? (or (nil? strictness) (< (rand) strictness))]
      (when allow?
        (let [raw (context->templates context)]
          (when (seq raw)
            (case (:template-mode spec)
              :collection
              (let [ones (count (filter #(= 1 %) (bits->ints (bits-for sigil))))
                    to-swap (- 4 ones)]
                (cond
                  (> to-swap 0) (remove #(zero? (:result %)) raw)
                  (< to-swap 0) (remove #(= 1 (:result %)) raw)
                  :else raw))
              raw)))))))

(defn- base-output-fn
  [spec]
  (let [{:keys [blend-mode flip?]} spec
        flip? (boolean flip?)]
    (fn [ctx triple ints]
      (case blend-mode
        :neighbors (cond
                     (neighbors-match? ints 0) (if flip? 1 0)
                     (neighbors-match? ints 1) (if flip? 0 1)
                     :else (apply-rule ctx triple))
        :all (cond
               (all-same? ints 0) (if flip? 1 0)
               (all-same? ints 1) (if flip? 0 1)
               :else (apply-rule ctx triple))
        :none (apply-rule ctx triple)
        (apply-rule ctx triple)))))

(defn- mutation-count
  [{:keys [mode count offset prob]} context]
  (let [prob (double (or prob 1.0))]
    (if (and (not= mode :none) (< (rand) prob))
      (case mode
        :fixed (max 0 (long (or count 0)))
        :baldwin (let [matches (context-match-count context)
                       offset (long (or offset 0))]
                   (if (number? matches)
                     (+ offset matches)
                     0))
        0)
      0)))

(defn- apply-mutation
  [bits-str mutation context]
  (let [ctx-rate (when (and context (contains? context :mutation-rate))
                   (double (:mutation-rate context)))
        ctx-bias (when (and context (contains? context :mutation-bias))
                   (double (:mutation-bias context)))
        mutation (cond-> mutation
                   ctx-rate (assoc :prob (max 0.0 (min 1.0 ctx-rate)))
                   ctx-bias (assoc :prob (max 0.0 (min 1.0 ctx-bias)))
                   (and (or ctx-rate ctx-bias) (= :none (:mode mutation)))
                   (assoc :mode :fixed :count 1))
        count (mutation-count mutation context)]
    (if (pos? count)
      (mutate-rule-n bits-str count)
      bits-str)))

(defn- rotate-left [v n]
  (let [n (mod (long n) (count v))]
    (vec (concat (subvec v n) (subvec v 0 n)))))

(defn- rotate-right [v n]
  (let [n (mod (long n) (count v))
        split (- (count v) n)]
    (vec (concat (subvec v split) (subvec v 0 split)))))

(defn- xor-neighbor [v shift]
  (let [n (count v)
        shift (max 1 (mod (long shift) n))]
    (vec (map-indexed (fn [idx bit]
                        (bit-xor bit (v (mod (+ idx shift) n))))
                      v))))

(defn- majority-neighbor [v shift]
  (let [n (count v)
        shift (max 1 (mod (long shift) n))]
    (vec (map-indexed (fn [idx bit]
                        (let [a bit
                              b (v (mod (+ idx shift) n))
                              c (v (mod (+ idx (- n shift)) n))
                              total (+ a b c)]
                          (if (>= total 2) 1 0)))
                      v))))

(defn- swap-halves [v]
  (let [n (count v)
        mid (quot n 2)]
    (vec (concat (subvec v mid) (subvec v 0 mid)))))

(defn- scramble-bits [v shift]
  (let [n (count v)
        order [0 2 4 6 1 3 5 7]
        rotated (rotate-left order shift)]
    (vec (mapv v rotated))))

(defn- apply-mix
  [bits-str {:keys [mix-mode mix-shift]}]
  (let [bits (vec (bits->ints bits-str))
        shift (long (or mix-shift 0))]
    (case mix-mode
      :none bits-str
      :rotate-left (ints->bits (rotate-left bits shift))
      :rotate-right (ints->bits (rotate-right bits shift))
      :reverse (ints->bits (vec (reverse bits)))
      :xor-neighbor (ints->bits (xor-neighbor bits shift))
      :majority (ints->bits (majority-neighbor bits shift))
      :swap-halves (ints->bits (swap-halves bits))
      :scramble (ints->bits (scramble-bits bits shift))
      bits-str)))

(defn kernel-spec->fn
  [kernel]
  (let [spec (normalize-kernel-spec kernel)
        base (base-output-fn spec)]
    (fn [sigil pred next context]
      (let [ctx (neighborhood-context sigil pred next)
            templates (template-entries spec sigil context)
            output-fn (if (seq templates)
                        (fn [ctx triple ints]
                          (or (match-template templates ints)
                              (base ctx triple ints)))
                        base)
            outputs (compute-outputs ctx output-fn)
            outputs (apply-mutation outputs (:mutation spec) context)
            outputs (apply-mix outputs spec)
            outputs (balance-mutation* outputs (:balance spec))]
        (entry-for-bits outputs)))))

(defn kernel-fn [kernel]
  (cond
    (fn? kernel) kernel
    (kernel-spec? kernel) (kernel-spec->fn kernel)
    (keyword? kernel) (or (get kernels kernel)
                          (throw (ex-info "Unknown kernel" {:kernel kernel})))
    :else (throw (ex-info "Unknown kernel" {:kernel kernel}))))

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
