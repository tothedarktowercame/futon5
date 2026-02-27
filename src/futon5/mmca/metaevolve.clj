(ns futon5.mmca.metaevolve
  "Outer-loop evolution of static meta-rules."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.ct.tensor-closed-loop :as tensor-loop]
            [futon5.mmca.adapters :as adapters]
            [futon5.mmca.meta-lift :as meta-lift]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.operators :as operators]
            [futon5.mmca.payload :as payload]
            [futon5.mmca.render :as render]
            [futon5.mmca.runtime :as mmca]))

(def ^:private default-length 50)
(def ^:private default-runs 50)
(def ^:private default-generations 50)
(def ^:dynamic *quiet-kernel?* false)

(defn- usage []
  (str/join
   "\n"
   ["MetaMetaCA outer-loop runner"
    ""
    "Usage:"
    "  bb -cp src:resources -m futon5.mmca.metaevolve [options]"
    ""
    "Options:"
    "  --length N       Genotype/phenotype length (default 50)."
    "  --generations N  Generations per run (default 50)."
    "  --runs N         Number of runs (default 50)."
    "  --seed N         Seed for meta-evolution RNG."
    "  --engine NAME    Engine: mmca (default) or tensor-closed-loop."
    "  --report PATH   Write full run report to EDN."
    "  --no-freeze      Deprecated (freeze-genotype is disabled)."
    "  --require-phenotype  Require phenotype in all runs."
    "  --require-gate       Require genotype-gate when not frozen."
    "  --baldwin-share P    Fraction of runs forced to Baldwin (0-1)."
    "  --lesion             Apply mid-run lesion to half the field."
    "  --lesion-tick N       Tick to apply lesion (default mid-run)."
    "  --lesion-target T     lesion target: phenotype, genotype, or both."
    "  --lesion-half H       lesion half: left or right."
    "  --lesion-mode M       lesion mode: zero or default."
    "  --feedback-top K     Accumulate top-K lifted sigils per run."
    "  --feedback-edn PATH  Write accumulated sigils/learned specs to EDN."
    "  --feedback-load PATH Load initial sigils/specs from EDN."
    "  --feedback-every N   Write feedback EDN every N runs (streaming)."
    "  --leaderboard-size N Keep top-N rules and reuse them across runs."
    "  --leaderboard-chance P  Probability to seed a run from leaderboard (0-1)."
    "  --aif-weight P       Blend AIF score into composite (0-1)."
    "  --aif-guide          Use AIF score to gate feedback sigils."
    "  --aif-guide-min P    Minimum AIF score (0-100) to fully accept sigils."
    "  --aif-mutate         Use AIF deficits to steer kernel selection."
    "  --aif-mutate-min P   Apply AIF steering when score below P (0-100)."
    "  --kernel-context     Use run-local heredity to mutate kernel specs."
    "  --exotype-update-threshold P  Only update kernel output when match <= P (0-1)."
    "  --exotype-invert     Invert match check when phenotype bit is black."
    "  --quiet-kernel       Bias kernel specs toward metastability (no mix, low mutation)."
    "  --pulses             Enable pulse-based operators (default off)."
    "  --no-pulses          Disable pulse-based operators (override)."
    "  --save-top N         Save the top N runs (EDN + image)."
    "  --save-top-dir PATH  Directory for saved runs (default ./mmca_top_runs)."
    "  --save-top-pdf PATH  Render saved images into a PDF (optional)."
    "  --render-exotype     Render genotype/phenotype/exotype triptychs."
    "  --report-every N     Write report EDN every N runs (streaming)."
    "  --tensor-rule-sigil SIGIL  Initial tensor rule sigil (tensor engine)."
    "  --tensor-backend NAME      Tensor backend: clj or jax (tensor engine)."
    "  --tensor-wrap              Use wrapped boundary mode (tensor engine)."
    "  --tensor-boundary-bit N    Boundary bit for open mode (tensor engine)."
    "  --strict-parity            Fail on tensor/meta-lift parity mismatch (tensor engine)."
    "  --no-strict-parity         Continue on parity mismatch (tensor engine)."
    "  --tensor-explore-rate P    Rule exploration rate 0..1 (tensor engine)."
    "  --help           Show this message."]))

(defn- parse-int [s]
  (try
    (Long/parseLong s)
    (catch Exception _
      nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--generations" flag)
          (recur (rest more) (assoc opts :generations (parse-int (first more))))

          (= "--runs" flag)
          (recur (rest more) (assoc opts :runs (parse-int (first more))))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

          (= "--engine" flag)
          (recur (rest more) (assoc opts :engine (first more)))

          (= "--report" flag)
          (recur (rest more) (assoc opts :report (first more)))

          (= "--no-freeze" flag)
          (recur more (assoc opts :no-freeze true))

          (= "--require-phenotype" flag)
          (recur more (assoc opts :require-phenotype true))

          (= "--require-gate" flag)
          (recur more (assoc opts :require-gate true))

          (= "--baldwin-share" flag)
          (recur (rest more) (assoc opts :baldwin-share (Double/parseDouble (first more))))

          (= "--lesion" flag)
          (recur more (assoc opts :lesion true))

          (= "--lesion-tick" flag)
          (recur (rest more) (assoc opts :lesion-tick (parse-int (first more))))

          (= "--lesion-target" flag)
          (recur (rest more) (assoc opts :lesion-target (keyword (first more))))

          (= "--lesion-half" flag)
          (recur (rest more) (assoc opts :lesion-half (keyword (first more))))

          (= "--lesion-mode" flag)
          (recur (rest more) (assoc opts :lesion-mode (keyword (first more))))

          (= "--feedback-top" flag)
          (recur (rest more) (assoc opts :feedback-top (parse-int (first more))))

          (= "--feedback-edn" flag)
          (recur (rest more) (assoc opts :feedback-edn (first more)))

          (= "--feedback-load" flag)
          (recur (rest more) (assoc opts :feedback-load (first more)))

          (= "--feedback-every" flag)
          (recur (rest more) (assoc opts :feedback-every (parse-int (first more))))

          (= "--leaderboard-size" flag)
          (recur (rest more) (assoc opts :leaderboard-size (parse-int (first more))))

          (= "--leaderboard-chance" flag)
          (recur (rest more) (assoc opts :leaderboard-chance (Double/parseDouble (first more))))

          (= "--aif-weight" flag)
          (recur (rest more) (assoc opts :aif-weight (Double/parseDouble (first more))))

          (= "--aif-guide" flag)
          (recur more (assoc opts :aif-guide true))

          (= "--aif-guide-min" flag)
          (recur (rest more) (assoc opts :aif-guide-min (Double/parseDouble (first more))))

          (= "--aif-mutate" flag)
          (recur more (assoc opts :aif-mutate true))

          (= "--aif-mutate-min" flag)
          (recur (rest more) (assoc opts :aif-mutate-min (Double/parseDouble (first more))))

          (= "--kernel-context" flag)
          (recur more (assoc opts :kernel-context true))

          (= "--exotype-update-threshold" flag)
          (recur (rest more) (assoc opts :exotype-update-threshold (Double/parseDouble (first more))))

          (= "--exotype-invert" flag)
          (recur more (assoc opts :exotype-invert true))

          (= "--quiet-kernel" flag)
          (recur more (assoc opts :quiet-kernel true))

          (= "--pulses" flag)
          (recur more (assoc opts :pulses true))

          (= "--no-pulses" flag)
          (recur more (assoc opts :no-pulses true))

          (= "--save-top" flag)
          (recur (rest more) (assoc opts :save-top (parse-int (first more))))

          (= "--save-top-dir" flag)
          (recur (rest more) (assoc opts :save-top-dir (first more)))

          (= "--save-top-pdf" flag)
          (recur (rest more) (assoc opts :save-top-pdf (first more)))

          (= "--render-exotype" flag)
          (recur more (assoc opts :render-exotype true))

          (= "--report-every" flag)
          (recur (rest more) (assoc opts :report-every (parse-int (first more))))

          (= "--tensor-rule-sigil" flag)
          (recur (rest more) (assoc opts :tensor-rule-sigil (first more)))

          (= "--tensor-backend" flag)
          (recur (rest more) (assoc opts :tensor-backend (keyword (first more))))

          (= "--tensor-wrap" flag)
          (recur more (assoc opts :tensor-wrap true))

          (= "--tensor-boundary-bit" flag)
          (recur (rest more) (assoc opts :tensor-boundary-bit (parse-int (first more))))

          (= "--strict-parity" flag)
          (recur more (assoc opts :strict-parity true))

          (= "--no-strict-parity" flag)
          (recur more (assoc opts :strict-parity false))

          (= "--tensor-explore-rate" flag)
          (recur (rest more) (assoc opts :tensor-explore-rate (Double/parseDouble (first more))))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- rule->bits [n]
  (let [bits (Integer/toBinaryString (int n))
        padded (format "%8s" bits)]
    (str/replace padded #" " "0")))

(defn- rule->sigil [n]
  (ca/sigil-for (rule->bits n)))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-bool [^java.util.Random rng]
  (zero? (rng-int rng 2)))

(def ^:private blend-modes [:none :neighbors :all])
(def ^:private template-modes [:none :context :collection])
(def ^:private mutation-modes [:none :fixed :baldwin])
(def ^:private mutation-probs [0.2 0.33 0.5 1.0])
(def ^:private balance-chances [0.02 0.05 0.1])
(def ^:private balance-min-ones [1 2 3])
(def ^:private balance-max-ones [5 6 7])
(def ^:private balance-flip-counts [1 2])
(def ^:private mix-modes [:none :rotate-left :rotate-right :reverse :xor-neighbor :majority :swap-halves :scramble])
(def ^:private mix-shifts [0 1 2 3])
(def ^:private quiet-blend-modes [:none :neighbors])
(def ^:private quiet-template-modes [:context :collection])
(def ^:private quiet-mutation-modes [:none :fixed])
(def ^:private quiet-mutation-probs [0.2 0.33])

(defn- rng-choice [^java.util.Random rng xs]
  (nth xs (rng-int rng (count xs))))

(defn- rng-choice-except [^java.util.Random rng xs current]
  (let [candidates (vec (remove #{current} xs))]
    (if (seq candidates)
      (rng-choice rng candidates)
      current)))

(defn- random-mutation
  ([^java.util.Random rng] (random-mutation rng (rng-choice rng mutation-modes)))
  ([^java.util.Random rng mode]
   (case mode
     :fixed {:mode :fixed
             :count (inc (rng-int rng 3))
             :prob (rng-choice rng mutation-probs)}
     :baldwin {:mode :baldwin
               :offset (inc (rng-int rng 3))
               :prob (rng-choice rng mutation-probs)}
     {:mode :none :count 0 :prob 0.0})))

(defn- normalize-balance
  [{:keys [min-ones max-ones] :as balance}]
  (let [min-ones (long (or min-ones 0))
        max-ones (long (or max-ones min-ones))
        [min-ones max-ones] (if (< min-ones max-ones)
                              [min-ones max-ones]
                              [max-ones (inc min-ones)])]
    (assoc balance :min-ones min-ones :max-ones max-ones)))

(defn- random-balance [^java.util.Random rng]
  (normalize-balance
   {:enabled? (rng-bool rng)
    :min-ones (rng-choice rng balance-min-ones)
    :max-ones (rng-choice rng balance-max-ones)
    :chance (rng-choice rng balance-chances)
    :flip-count (rng-choice rng balance-flip-counts)}))

(defn- random-kernel-spec [^java.util.Random rng]
  (ca/normalize-kernel-spec
   (let [quiet? *quiet-kernel?*]
     (cond-> {:blend-mode (rng-choice rng (if quiet? quiet-blend-modes blend-modes))
              :flip? (rng-bool rng)
              :template-mode (rng-choice rng (if quiet? quiet-template-modes template-modes))
              :mutation (if quiet?
                          (random-mutation rng (rng-choice rng quiet-mutation-modes))
                          (random-mutation rng))
              :balance (random-balance rng)
              :mix-mode (rng-choice rng (if quiet? [:none] mix-modes))
              :mix-shift (rng-choice rng mix-shifts)
              :reserved (apply str (repeatedly 6 #(rng-int rng 2)))}
       quiet? (update :mutation (fn [m] (update m :prob (fn [_] (rng-choice rng quiet-mutation-probs)))))))))

(defn- toggle [x]
  (not (boolean x)))

(defn- random-meta-rule [^java.util.Random rng _no-freeze? require-phenotype? require-gate?]
  (let [kernel (random-kernel-spec rng)
        needs-context? (ca/kernel-spec-needs-context? kernel)
        genotype-gate (if require-gate? true (rng-bool rng))
        phenotype? (cond
                     require-phenotype? true
                     needs-context? true
                     genotype-gate true
                     :else (rng-bool rng))]
    {:kernel kernel
     :lock-kernel (rng-bool rng)
     :freeze-genotype false
     :genotype-gate genotype-gate
     :use-operators true
     :operator-scope :genotype
     :genotype-mode :random
     :global-rule nil
      :phenotype? phenotype?}))

(defn- mutate-kernel-spec [^java.util.Random rng kernel]
  (let [spec (ca/normalize-kernel-spec kernel)
        mutations (if *quiet-kernel?*
                    [:blend-mode :flip :template-mode :mutation-mode :mutation-count
                     :mutation-prob :mutation-offset :balance-enable :balance-chance :balance-thresholds]
                    [:blend-mode :flip :template-mode :mutation-mode :mutation-count
                     :mutation-prob :mutation-offset :balance-enable :balance-chance :balance-thresholds
                     :mix-mode :mix-shift :reserved-bit])
        pick (rng-choice rng mutations)
        prob-pool (if *quiet-kernel?* quiet-mutation-probs mutation-probs)
        update-mutation
        (fn [spec mode]
          (assoc spec :mutation (random-mutation rng mode)))]
    (-> (case pick
          :blend-mode (assoc spec :blend-mode (rng-choice-except rng blend-modes (:blend-mode spec)))
          :flip (update spec :flip? toggle)
          :template-mode (assoc spec :template-mode (rng-choice-except rng template-modes (:template-mode spec)))
          :mutation-mode (update-mutation spec (rng-choice-except rng mutation-modes (get-in spec [:mutation :mode])))
          :mutation-count (if (= :none (get-in spec [:mutation :mode]))
                            (update-mutation spec :fixed)
                            (update-in spec [:mutation :count] (fn [_] (inc (rng-int rng 3)))))
          :mutation-prob (if (= :none (get-in spec [:mutation :mode]))
                           (update-mutation spec :fixed)
                           (update-in spec [:mutation :prob] (fn [_] (rng-choice rng prob-pool))))
          :mutation-offset (if (= :none (get-in spec [:mutation :mode]))
                             (update-mutation spec :baldwin)
                             (update-in spec [:mutation :offset] (fn [_] (inc (rng-int rng 3)))))
          :balance-enable (update-in spec [:balance :enabled?] toggle)
          :balance-chance (update-in spec [:balance :chance] (fn [_] (rng-choice rng balance-chances)))
          :balance-thresholds (normalize-balance
                               (assoc (:balance spec)
                                      :min-ones (rng-choice rng balance-min-ones)
                                      :max-ones (rng-choice rng balance-max-ones)))
          :mix-mode (assoc spec :mix-mode (rng-choice-except rng mix-modes (:mix-mode spec)))
          :mix-shift (assoc spec :mix-shift (rng-choice-except rng mix-shifts (:mix-shift spec)))
          :reserved-bit (let [bits (vec (ca/kernel-spec->bits spec))
                              idx (rng-int rng (count bits))
                              bit (bits idx)
                              flipped (if (= bit \0) \1 \0)]
                          (ca/bits->kernel-spec (apply str (assoc bits idx flipped)))))
        (assoc :label nil)
        (cond-> *quiet-kernel?* (assoc :mix-mode :none :mix-shift 0)))))

(defn- noisy-summary? [summary]
  (and summary
       (> (double (or (:gen/avg-change summary) (:avg-change summary) 0.0)) 0.35)
       (> (double (or (:gen/lz78-ratio summary) (:lz78-ratio summary) 0.0)) 0.7)))

(defn- stable-summary? [summary]
  (and summary
       (< (double (or (:gen/avg-change summary) (:avg-change summary) 0.0)) 0.08)
       (> (double (or (:gen/temporal-autocorr summary) (:temporal-autocorr summary) 0.0)) 0.85)))

(defn- clamp-mutation [mutation]
  (let [mode (:mode mutation)
        count (long (or (:count mutation) 0))
        prob (double (or (:prob mutation) 0.0))
        offset (long (or (:offset mutation) 0))]
    (cond-> mutation
      (= mode :fixed) (assoc :count (min count 2))
      (= mode :baldwin) (assoc :offset (min (max offset 1) 2))
      (number? prob) (assoc :prob (min prob 0.5)))))

(defn- dampen-kernel-spec [kernel summary]
  (if (ca/kernel-spec? kernel)
    (let [spec (ca/normalize-kernel-spec kernel)
          mutation (clamp-mutation (:mutation spec))
          spec (assoc spec :mutation mutation)]
      (cond
        (noisy-summary? summary)
        (assoc spec :mutation {:mode :fixed :count 1 :prob 0.2})

        (stable-summary? summary)
        (if (= :none (:mode mutation))
          (assoc spec :mutation {:mode :fixed :count 1 :prob 0.2})
          (assoc spec :mutation (assoc mutation :count (max 1 (:count mutation))
                                       :prob (max 0.33 (:prob mutation)))))

        :else spec))
    kernel))

(defn- mutate-meta-rule [^java.util.Random rng _no-freeze? require-phenotype? require-gate? rule kernel-context]
  (let [mutations [:kernel :lock-kernel :phenotype? :genotype-gate]
        pick (nth mutations (rng-int rng (count mutations)))
        rule (assoc rule
                    :freeze-genotype false
                    :genotype-mode :random
                    :global-rule nil
                    :use-operators true
                    :operator-scope :genotype)
        rule (if (and (:genotype-gate rule) (not (:phenotype? rule)))
               (assoc rule :phenotype? true)
               rule)
        rule (case pick
               :kernel (assoc rule :kernel (if kernel-context
                                             (ca/mutate-kernel-spec-contextual (:kernel rule) kernel-context)
                                             (mutate-kernel-spec rng (:kernel rule))))
               :lock-kernel (update rule :lock-kernel toggle)
               :phenotype? (if require-phenotype?
                             (assoc rule :phenotype? true)
                             (let [next (toggle (:phenotype? rule))]
                               (cond-> (assoc rule :phenotype? next)
                                 (not next) (assoc :genotype-gate false))))
               :genotype-gate (if require-gate?
                                (assoc rule :genotype-gate true :phenotype? true)
                                (let [next (toggle (:genotype-gate rule))]
                                  (cond-> (assoc rule :genotype-gate next)
                                    next (assoc :phenotype? true))))
               rule)
        kernel (:kernel rule)]
    (cond-> rule
      (ca/kernel-spec-needs-context? kernel) (assoc :phenotype? true)
      (and require-phenotype? (not (:phenotype? rule))) (assoc :phenotype? true)
      (and require-gate? (not (:genotype-gate rule))) (assoc :genotype-gate true :phenotype? true))))

(defn- enforce-baldwin [rule]
  (-> rule
      (assoc :kernel (ca/kernel-spec-for :blending-baldwin)
             :freeze-genotype false
             :genotype-gate true
             :phenotype? true
             :use-operators true
             :operator-scope :genotype
             :genotype-mode :random
             :global-rule nil)))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(defn- rng-phenotype-string [^java.util.Random rng length]
  (apply str (repeatedly length #(rng-int rng 2))))

(defn- normalize-sigil [sigil]
  (cond
    (nil? sigil) nil
    (string? sigil) sigil
    (keyword? sigil) (name sigil)
    (char? sigil) (str sigil)
    :else (str sigil)))

(defn- normalize-sigils [sigils]
  (vec (remove nil? (map normalize-sigil sigils))))

(defn- sigil-at [s idx]
  (if (and s (<= 0 idx) (< idx (count s)))
    (str (nth s idx))
    ca/default-sigil))

(defn- bit-at [s idx]
  (if (and s (<= 0 idx) (< idx (count s)))
    (nth s idx)
    \0))

(defn- kernel-context
  [gen-history phe-history ^java.util.Random rng {:keys [exotype-update-threshold exotype-invert?]}]
  (let [rows (count gen-history)
        cols (count (or (first gen-history) ""))]
    (when (and (> rows 1) (pos? cols))
      (let [t (rng-int rng (dec rows))
            x (rng-int rng cols)
            row (nth gen-history t)
            next-row (nth gen-history (inc t))
            pred (sigil-at row (dec x))
            self (sigil-at row x)
            next (sigil-at row (inc x))
            out (sigil-at next-row x)
            phe (when (and phe-history (> (count phe-history) (inc t)))
                  (let [phe-row (nth phe-history t)
                        phe-next (nth phe-history (inc t))]
                    (str (bit-at phe-row (dec x))
                         (bit-at phe-row x)
                         (bit-at phe-row (inc x))
                         (bit-at phe-next x))))]
        (cond-> {:context-sigils [pred self next out]
                 :phenotype-context phe
                 :rotation (rng-int rng 4)}
          (number? exotype-update-threshold)
          (assoc :match-threshold (min 1.0 (max 0.0 (double exotype-update-threshold))))
          exotype-invert? (assoc :invert-on-phenotype? true))))))

(defn- normalize-counts [counts]
  (reduce (fn [acc [sigil count]]
            (if-let [s (normalize-sigil sigil)]
              (update acc s (fnil + 0.0) (double count))
              acc))
          {}
          counts))

(defn- merge-sigils [current more]
  (vec (distinct (concat (normalize-sigils (or current []))
                         (normalize-sigils more)))))

(defn- learned-strength [counts sigil]
  (let [counts (or counts {})
        max-count (double (apply max 1 (vals counts)))
        sigil (normalize-sigil sigil)
        count (double (or (get counts sigil)
                          (when (and (string? sigil) (seq sigil))
                            (get counts (first sigil)))
                          0))]
    (if (pos? max-count)
      (/ count max-count)
      0.0)))

(defn- learned-params [counts sigil]
  (let [strength (learned-strength counts sigil)
        apply-rate (min 0.9 (+ 0.15 (* 0.65 strength)))
        period (max 2 (int (Math/round (- 10 (* 7 strength)))))]
    {:apply_rate apply-rate
     :pulse_period period
     :trigger_mode :local
     :local_radius 2
     :local_threshold 0.25
     :strength strength}))

(defn- learned-spec [sigil counts]
  {:sigil (normalize-sigil sigil)
   :parameters (learned-params counts sigil)
   :source :metaevolve})

(defn- update-learned-specs [learned-specs {:keys [top-sigils sigil-counts]}]
  (reduce (fn [acc sigil]
            (let [sigil (normalize-sigil sigil)]
              (if (or (nil? sigil)
                      (= sigil ca/default-sigil)
                      (contains? acc sigil))
                acc
                (assoc acc sigil (learned-spec sigil sigil-counts)))))
          learned-specs
          (distinct (normalize-sigils top-sigils))))

(defn- clamp-01 [x]
  (cond
    (not (number? x)) 0.0
    (< x 0.0) 0.0
    (> x 1.0) 1.0
    :else (double x)))

(defn- biodiversity-score [summary]
  (let [avg-unique (double (or (:avg-unique summary) 0.0))
        diversity (Math/sqrt (clamp-01 avg-unique))]
    {:aif/biodiversity avg-unique
     :aif/biodiversity-score diversity}))

(defn- log2 [x]
  (/ (Math/log x) (Math/log 2.0)))

(defn- entropy-norm [samples]
  (let [total (double (count samples))]
    (if (pos? total)
      (let [counts (frequencies samples)
            entropy (- (reduce (fn [acc [_ cnt]]
                                 (let [p (/ cnt total)]
                                   (if (pos? p)
                                     (+ acc (* p (log2 p)))
                                     acc)))
                               0.0
                               counts))
            denom (log2 (max 2.0 (min 256.0 total)))]
        (if (pos? denom)
          (/ entropy denom)
          0.0))
      0.0)))

(defn- classify-entropy [entropy-n]
  (cond
    (< entropy-n 0.25) :low
    (< entropy-n 0.65) :mid
    :else :high))

(defn- mid-cells
  [grid radius]
  (let [rows (count grid)
        cols (count (first grid))]
    (reduce (fn [acc [r c]]
              (let [r0 (max 0 (- r radius))
                    r1 (min (dec rows) (+ r radius))
                    c0 (max 0 (- c radius))
                    c1 (min (dec cols) (+ c radius))
                    window (for [rr (range r0 (inc r1))
                                 cc (range c0 (inc c1))]
                             (get-in grid [rr cc]))
                    cls (classify-entropy (entropy-norm window))]
                (if (= cls :mid)
                  (conj acc [r c])
                  acc)))
            #{}
            (for [r (range rows)
                  c (range cols)]
              [r c]))))

(defn- neighbors [[r c]]
  [[(dec r) c] [(inc r) c] [r (dec c)] [r (inc c)]])

(defn- component-sizes [cells]
  (loop [remaining cells
         sizes []]
    (if (empty? remaining)
      sizes
      (let [start (first remaining)
            visited (loop [queue [start]
                           seen #{start}]
                      (if (empty? queue)
                        seen
                        (let [node (peek queue)
                              rest-queue (pop queue)
                              nexts (filter remaining (neighbors node))
                              new (remove seen nexts)
                              queue' (into rest-queue new)
                              seen' (into seen new)]
                          (recur queue' seen'))))
            remaining' (reduce disj remaining visited)]
        (recur remaining' (conj sizes (count visited)))))))

(defn- regime-score [gen-history]
  (let [rows (count gen-history)
        cols (count (or (first gen-history) ""))]
    (if (or (zero? rows) (zero? cols))
      {:aif/regime-score 0.0
       :aif/regime-bonus 0.0
       :aif/regime-mid-area 0.0
       :aif/regime-components 0}
      (let [grid (mapv vec gen-history)
            radius 2
            mids (mid-cells grid radius)
            total (* rows cols)
            mid-count (count mids)
            mid-area (if (pos? total) (/ (double mid-count) total) 0.0)
            components (component-sizes mids)
            comp-count (count components)
            avg-size (if (pos? comp-count) (/ (double mid-count) comp-count) 0.0)
            size-ratio (if (pos? total) (/ avg-size total) 0.0)
            area-score (metrics/centered-score (clamp-01 (/ mid-area 0.6)))
            size-score (metrics/centered-score (clamp-01 (/ size-ratio 0.12)))
            regime-score (max 0.0 (+ (* 0.6 area-score) (* 0.4 size-score)))
            bonus (* 12.0 regime-score)]
        {:aif/regime-score regime-score
         :aif/regime-bonus bonus
         :aif/regime-mid-area mid-area
         :aif/regime-components comp-count}))))

(defn- trail-score [summary]
  (let [temporal (double (or (:temporal-autocorr summary) 0.0))
        change (double (or (:avg-change summary) (:phe-change summary) 0.0))
        trail (metrics/centered-score temporal)
        change-factor (clamp-01 (/ (- change 0.05) 0.25))]
    {:trail-score (* trail change-factor)
     :trail-autocorr temporal
     :trail-change change}))

(defn- sticky-penalty [summary]
  (let [temporal (double (or (:temporal-autocorr summary) 0.0))
        change (double (or (:avg-change summary) (:phe-change summary) 0.0))
        autocorr-bad (clamp-01 (/ (- temporal 0.8) 0.15))
        change-bad (clamp-01 (/ (- 0.18 change) 0.18))
        penalty (* autocorr-bad change-bad)]
    {:sticky-penalty penalty
     :sticky-autocorr temporal
     :sticky-change change}))

(defn- aif-food-score [meta-lift learned-specs mmca-score summary gen-history]
  (let [counts (normalize-counts (:sigil-counts meta-lift))
        total (double (reduce + 0.0 (vals counts)))
        learned (set (keys (or learned-specs {})))
        new-sigils (->> (keys counts)
                        (remove #(or (= % ca/default-sigil)
                                     (contains? learned %)))
                        vec)
        new-mass (if (pos? total)
                   (/ (double (reduce + 0.0 (map counts new-sigils))) total)
                   0.0)
        quality (clamp-01 (when (number? mmca-score) (/ mmca-score 100.0)))
        {:keys [trail-score trail-autocorr trail-change]} (trail-score summary)
        {:keys [sticky-penalty sticky-autocorr sticky-change]} (sticky-penalty summary)
        {:aif/keys [biodiversity biodiversity-score]} (biodiversity-score summary)
        food-score (* new-mass (+ 0.4 (* 0.3 quality) (* 0.3 trail-score)))
        base-score (* 100.0 (+ (* 0.7 food-score) (* 0.3 biodiversity-score)))
        {:aif/keys [regime-score regime-bonus regime-mid-area regime-components]}
        (regime-score gen-history)
        score (max 0.0 (+ (* base-score (- 1.0 (* 0.6 sticky-penalty)))
                          regime-bonus))]
    {:aif/food-mass new-mass
     :aif/food-count (count new-sigils)
     :aif/food-quality quality
     :aif/food-score food-score
     :aif/new-sigils new-sigils
     :aif/trail-score trail-score
     :aif/trail-autocorr trail-autocorr
     :aif/trail-change trail-change
     :aif/sticky-penalty sticky-penalty
     :aif/sticky-autocorr sticky-autocorr
     :aif/sticky-change sticky-change
     :aif/biodiversity biodiversity
     :aif/biodiversity-score biodiversity-score
     :aif/regime-score regime-score
     :aif/regime-bonus regime-bonus
     :aif/regime-mid-area regime-mid-area
     :aif/regime-components regime-components
     :aif/score score}))

(defn- blend-score [mmca-score aif-score weight]
  (let [w (clamp-01 weight)
        base (double (or mmca-score 0.0))
        aif (double (or aif-score 0.0))]
    (+ (* (- 1.0 w) base) (* w aif))))

(defn- guided-sigils [sigils summary ^java.util.Random rng aif-guide? aif-min]
  (let [sigils (vec sigils)]
    (if (and aif-guide? (seq sigils))
      (let [score (double (or (:aif-score summary) 0.0))
            min-score (double (or aif-min 0.0))
            scale (max 1.0 (- 100.0 min-score))
            normalized (clamp-01 (/ (max 0.0 (- score min-score)) scale))
            pick? (fn [] (< (.nextDouble rng) normalized))]
        (vec (filter (fn [_] (pick?)) sigils)))
      sigils)))

(def ^:private default-aif-mutate-min 45.0)

(defn- aif-kernel-pool [summary]
  (let [trail (double (or (:aif/trail-score summary) 0.0))
        biodiversity (double (or (:aif/biodiversity-score summary) 0.0))
        food (double (or (:aif/food-mass summary) 0.0))
        pool (cond-> []
               (< trail 0.45) (into [:blending-mutation :blending-flip :mutating-template :ad-hoc-template])
               (< biodiversity 0.6) (into [:collection-template :ad-hoc-template :mutating-template])
               (< food 0.45) (into [:blending :mutating-template :ad-hoc-template]))]
    (mapv ca/kernel-spec-for (distinct pool))))

(defn- aif-guided-rule [rule summary ^java.util.Random rng aif-mutate? aif-mutate-min]
  (if (and aif-mutate? summary)
    (let [score (double (or (:aif-score summary) 0.0))
          min-score (double (or aif-mutate-min default-aif-mutate-min))]
      (if (< score min-score)
        (let [pool (aif-kernel-pool summary)
              base (if (seq pool)
                     (rng-choice rng pool)
                     (random-kernel-spec rng))
              kernel (if (rng-bool rng)
                       (mutate-kernel-spec rng base)
                       (mutate-kernel-spec rng (:kernel rule)))]
          (cond-> (-> rule
                      (assoc :kernel kernel)
                      (assoc :lock-kernel false))
            (ca/kernel-spec-needs-context? kernel) (assoc :phenotype? true)))
        rule))
    rule))

(defn- update-top-runs [top-runs result top-n]
  (if (pos? (long (or top-n 0)))
    (let [entry (select-keys result [:summary :rule :seed :policy :run-result])
          ranked (->> (conj top-runs entry)
                      (sort-by (comp :composite-score :summary) >)
                      (take (long top-n)))]
      (vec ranked))
    top-runs))

(defn- build-run-opts [rule length generations ^java.util.Random rng lesion feedback-sigils learned-specs pulses-enabled]
  (let [genotype (if (= :global-rule (:genotype-mode rule))
                   (apply str (repeat length (rule->sigil (:global-rule rule))))
                   (rng-sigil-string rng length))
        phenotype (when (:phenotype? rule)
                    (rng-phenotype-string rng length))
        learned-specs (vals (or learned-specs {}))
        learned-operators (when (seq learned-specs)
                            (mapv operators/learned-sigil-op learned-specs))
        learned-sigils (vec (distinct (keep :sigil learned-specs)))
        base (cond-> {:genotype genotype
                      :generations generations
                      :kernel (:kernel rule)
                      :lock-kernel (:lock-kernel rule)
                      :freeze-genotype (:freeze-genotype rule)
                      :genotype-gate (:genotype-gate rule)
                      :use-operators (:use-operators rule)
                      :operator-scope (:operator-scope rule)}
               phenotype (assoc :phenotype phenotype)
               lesion (assoc :lesion lesion))
        merged (if (seq feedback-sigils)
                 (adapters/apply-sigils base feedback-sigils)
                 base)
        use-operators? (not (false? (:use-operators merged)))
        opts (cond-> (dissoc merged :use-operators)
               (not use-operators?) (assoc :operators [])
               (and use-operators? (seq learned-operators)) (assoc :learned-operators learned-operators)
               (and use-operators? (seq learned-sigils)) (update :pattern-sigils merge-sigils learned-sigils)
               (false? pulses-enabled) (assoc :pulses-enabled false))]
    {:opts opts
     :genotype genotype
     :phenotype phenotype}))

(defn- sha1 [s]
  (let [md (java.security.MessageDigest/getInstance "SHA-1")
        bytes (.digest md (.getBytes (str s) "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) bytes))))

(defn- evaluate-rule [rule length generations run-seed lesion feedback-sigils feedback-top learned-specs aif-weight capture-run? pulses-enabled]
  (let [rng (java.util.Random. (long run-seed))
        {:keys [opts genotype phenotype]} (build-run-opts rule length generations rng lesion feedback-sigils learned-specs pulses-enabled)
        result (mmca/run-mmca opts)
        summary (metrics/summarize-run result)
        mmca-score (:composite-score summary)
        lift (meta-lift/lift-history (:gen-history result))
        top-sigils (->> (:sigil-counts lift)
                        (sort-by val >)
                        (take (or feedback-top 8))
                        (map first)
                        (normalize-sigils))
        learned-sigils (vec (distinct (keep :sigil (vals (or learned-specs {})))))
        aif (aif-food-score lift learned-specs mmca-score summary (:gen-history result))
        composite (blend-score mmca-score (:aif/score aif) aif-weight)
        summary (-> summary
                    (assoc :mmca-score mmca-score
                           :aif-score (:aif/score aif)
                           :composite-score composite)
                    (merge aif))]
    {:rule rule
     :summary summary
     :seed run-seed
     :genotype-hash (sha1 genotype)
     :phenotype-hash (when phenotype (sha1 phenotype))
     :gen-history (:gen-history result)
     :phe-history (:phe-history result)
     :meta-lift {:top-sigils top-sigils
                 :sigil-counts (:sigil-counts lift)}
     :learned-sigils learned-sigils
     :run-result (when capture-run? result)}))

(defn- report-top [ranked]
  (println "Top meta-rules:")
  (doseq [{:keys [rule summary]} (take 5 ranked)]
    (let [kernel (:kernel rule)
          kernel-label (ca/kernel-label kernel)
          kernel-summary (ca/kernel-spec-summary kernel)]
      (println (format "score %.2f | kernel %s | lock %s | freeze %s | ops %s | mode %s | rule %s"
                       (double (:composite-score summary))
                       kernel-label
                       (boolean (:lock-kernel rule))
                       (boolean (:freeze-genotype rule))
                       (boolean (:use-operators rule))
                       (name (:genotype-mode rule))
                       (or (:global-rule rule) "-")))
      (when kernel-summary
        (println (str "  kernel " kernel-summary)))
      (println (format "  interesting %.2f | compress %.3f | autocorr %.3f"
                       (double (or (:score summary) 0.0))
                       (double (or (:lz78-ratio summary) 0.0))
                       (double (or (:temporal-autocorr summary) 0.0)))))))

(defn- report-baseline [history]
  (let [grouped (group-by (comp boolean :genotype-gate :rule) history)
        summarize (fn [entries]
                    (let [scores (seq (map (comp :composite-score :summary) entries))]
                      {:count (count entries)
                       :avg (metrics/avg scores)
                       :best (if (seq scores) (apply max scores) 0.0)}))]
    (println "Baseline comparison:")
    (doseq [[label entries] [[true (get grouped true)]
                             [false (get grouped false)]]]
      (let [{:keys [count avg best]} (summarize entries)]
        (println (format "  gate=%s | runs %d | avg %.2f | best %.2f"
                         label
                         (long count)
                         (double (or avg 0.0))
                         (double (or best 0.0))))))))

(defn- log-run [idx runs {:keys [rule summary seed genotype-hash phenotype-hash policy policy-source meta-lift learned-sigils]} feedback-count learned-total learned-new]
  (let [kernel (:kernel rule)
        kernel-label (ca/kernel-label kernel)
        kernel-summary (ca/kernel-spec-summary kernel)]
    (println (format "run %02d/%02d | score %.2f | kernel %s | lock %s | freeze %s | ops %s | mode %s | rule %s"
                     (inc idx)
                     runs
                     (double (:composite-score summary))
                     kernel-label
                     (boolean (:lock-kernel rule))
                     (boolean (:freeze-genotype rule))
                     (boolean (:use-operators rule))
                     (name (:genotype-mode rule))
                     (or (:global-rule rule) "-")))
    (when kernel-summary
      (println (str "  kernel " kernel-summary))))
  (println (format "  policy %s | source %s | gate %s | seed %s | gen %s | phe %s"
                   (or policy "-")
                   (or policy-source "-")
                   (boolean (:genotype-gate rule))
                   (or seed "-")
                   (or genotype-hash "-")
                   (or phenotype-hash "-")))
  (when feedback-count
    (println (format "  feedback sigils: %d" (long feedback-count))))
  (when (or (pos? (long (or learned-total 0))) (seq learned-sigils))
    (println (format "  learned ops: %d | new %d"
                     (long (or learned-total (count learned-sigils)))
                     (long (or learned-new 0)))))
  (when-let [aif-score (:aif/score summary)]
    (println (format "  aif %.2f | food %d | mass %.2f"
                     (double aif-score)
                     (long (or (:aif/food-count summary) 0))
                     (double (or (:aif/food-mass summary) 0.0)))))
  (when-let [trail (:aif/trail-score summary)]
    (println (format "  trail %.2f | autocorr %.3f | change %.3f"
                     (double trail)
                     (double (or (:aif/trail-autocorr summary) 0.0))
                     (double (or (:aif/trail-change summary) 0.0)))))
  (when-let [sticky (:aif/sticky-penalty summary)]
    (when (pos? (double sticky))
      (println (format "  sticky %.2f | autocorr %.3f | change %.3f"
                       (double sticky)
                       (double (or (:aif/sticky-autocorr summary) 0.0))
                       (double (or (:aif/sticky-change summary) 0.0))))))
  (when-let [biodiversity (:aif/biodiversity-score summary)]
    (println (format "  biodiversity %.2f | avg-unique %.3f"
                     (double biodiversity)
                     (double (or (:aif/biodiversity summary) 0.0)))))
  (when (seq (:top-sigils meta-lift))
    (println (format "  meta-lift top sigils: %s"
                     (str/join " " (:top-sigils meta-lift)))))
  (println (format "  interesting %.2f | compress %.3f | autocorr %.3f"
                   (double (or (:score summary) 0.0))
                   (double (or (:lz78-ratio summary) 0.0))
                   (double (or (:temporal-autocorr summary) 0.0))))
  (flush))

(defn- policy-key [policy]
  (or policy :baseline))

(defn- policy-best-line [policy-best]
  (let [baldwin (get policy-best :baldwin)
        baseline (get policy-best :baseline)
        leader (cond
                 (and baldwin baseline (> baldwin baseline)) :baldwin
                 (and baldwin baseline (< baldwin baseline)) :baseline
                 baldwin :baldwin
                 baseline :baseline
                 :else :none)
        gap (when (and baldwin baseline)
              (Math/abs (- (double baldwin) (double baseline))))]
    (format "  policy-best baldwin %s | baseline %s | lead %s%s"
            (if baldwin (format "%.2f" (double baldwin)) "-")
            (if baseline (format "%.2f" (double baseline)) "-")
            (name leader)
            (if gap (format " (%.2f)" (double gap)) ""))))

(declare update-leaderboard pick-leaderboard-rule)

(defn evolve-meta-rules [runs length generations seed no-freeze? require-phenotype? require-gate? baldwin-share lesion feedback-top initial-feedback initial-learned-specs aif-opts kernel-context-opts pulses-enabled save-top leaderboard-opts checkpoint-opts report-opts]
  (loop [i 0
         best nil
         history []
         policy-rng (java.util.Random. (long (+ seed 4242)))
         feedback-sigils (vec (distinct (or initial-feedback [])))
         learned-specs (or initial-learned-specs {})
         policy-best {}
         leaderboard (vec (or (:initial leaderboard-opts) []))
         top-runs []
         last-summary nil
         last-history nil]
    (if (= i runs)
      (let [ranked (sort-by (comp :composite-score :summary) > history)]
        {:best best
         :ranked ranked
         :history history
         :feedback-sigils feedback-sigils
         :learned-specs learned-specs
         :leaderboard leaderboard
         :top-runs top-runs})
      (let [rng (java.util.Random. (long (+ seed i)))
            guide-rng (java.util.Random. (long (+ seed i 42420)))
            aif-weight (:weight aif-opts)
            aif-guide? (:guide? aif-opts)
            aif-guide-min (:guide-min aif-opts)
            aif-mutate? (:mutate? aif-opts)
            aif-mutate-min (:mutate-min aif-opts)
            leaderboard-size (:size leaderboard-opts)
            leaderboard-chance (:chance leaderboard-opts)
            kernel-context? (boolean (:enabled? kernel-context-opts))
            context (when (and kernel-context? last-history)
                      (kernel-context (:gen-history last-history)
                                      (:phe-history last-history)
                                      rng
                                      {:exotype-update-threshold (:exotype-update-threshold kernel-context-opts)
                                       :exotype-invert? (:exotype-invert? kernel-context-opts)}))
            leaderboard-rule (pick-leaderboard-rule leaderboard leaderboard-chance rng)
            base-rule (or leaderboard-rule (when (and best (pos? i)) (:rule best)))
            policy-source (cond
                            leaderboard-rule :leaderboard
                            base-rule :evolve
                            :else :random)
            candidate (if base-rule
                        (mutate-meta-rule rng no-freeze? require-phenotype? require-gate? base-rule context)
                        (random-meta-rule rng no-freeze? require-phenotype? require-gate?))
            policy (when (number? baldwin-share)
                     (if (< (.nextDouble policy-rng) baldwin-share)
                       :baldwin
                       :baseline))
            candidate (case policy
                        :baldwin (enforce-baldwin candidate)
                        candidate)
            candidate (aif-guided-rule candidate last-summary guide-rng aif-mutate? aif-mutate-min)
            candidate (update candidate :kernel dampen-kernel-spec last-summary)
            run-seed (+ seed i 1000)
            result (assoc (evaluate-rule candidate length generations run-seed lesion feedback-sigils feedback-top learned-specs aif-weight (pos? (long (or save-top 0))) pulses-enabled)
                          :policy policy
                          :policy-source policy-source)
            best' (if (or (nil? best)
                          (> (get-in result [:summary :composite-score])
                             (get-in best [:summary :composite-score])))
                    result
                    best)
            guided-top (guided-sigils (get-in result [:meta-lift :top-sigils])
                                      (:summary result)
                                      guide-rng
                                      aif-guide?
                                      aif-guide-min)
            feedback-sigils' (if (number? feedback-top)
                               (vec (distinct (concat feedback-sigils guided-top)))
                               feedback-sigils)
            learned-specs' (if (number? feedback-top)
                             (update-learned-specs learned-specs {:top-sigils guided-top
                                                                  :sigil-counts (get-in result [:meta-lift :sigil-counts])})
                             learned-specs)
            learned-new (count (payload/learned-specs-delta learned-specs learned-specs'))
            learned-total (count (keys learned-specs'))
            policy-k (policy-key policy)
            score (double (or (get-in result [:summary :composite-score]) 0.0))
            prev-best (get policy-best policy-k)
            policy-best' (assoc policy-best policy-k (if (number? prev-best) (max prev-best score) score))
            policy-line (when (or (nil? prev-best) (> score prev-best))
                          (policy-best-line policy-best'))
            leaderboard' (update-leaderboard leaderboard {:score score :rule (:rule result) :seed run-seed} leaderboard-size)
            top-runs' (update-top-runs top-runs result save-top)
            last-history' (select-keys result [:gen-history :phe-history])]
        (log-run i runs result (count feedback-sigils') learned-total learned-new)
        (when policy-line
          (println policy-line))
        (when (and checkpoint-opts
                   (:path checkpoint-opts)
                   (number? (:every checkpoint-opts))
                   (pos? (long (:every checkpoint-opts)))
                   (zero? (mod (inc i) (long (:every checkpoint-opts)))))
          (spit (:path checkpoint-opts)
                (pr-str (payload/feedback-payload {:meta (:meta checkpoint-opts)
                                                   :feedback-sigils feedback-sigils'
                                                   :learned-specs learned-specs'
                                                   :leaderboard leaderboard'
                                                   :runs-completed (inc i)}))))
        (when (and report-opts
                   (:path report-opts)
                   (number? (:every report-opts))
                   (pos? (long (:every report-opts)))
                   (zero? (mod (inc i) (long (:every report-opts)))))
          (let [ranked (sort-by (comp :composite-score :summary) > (conj history result))
                payload (payload/report-payload {:meta (merge (:meta report-opts)
                                                              {:runs-completed (inc i)})
                                                 :ranked ranked
                                                 :learned-specs learned-specs'
                                                 :leaderboard leaderboard'
                                                 :runs-detail (conj history result)
                                                 :top-runs (map #(dissoc % :run-result) top-runs)})]
            (spit (:path report-opts) (pr-str payload))))
        (recur (inc i) best' (conj history result) policy-rng feedback-sigils' learned-specs' policy-best' leaderboard' top-runs' (:summary result) last-history')))))

(defn- load-feedback [path]
  (try
    (let [data (edn/read-string (slurp path))]
      (cond
        (vector? data) {:feedback-sigils data}
        (map? data) {:feedback-sigils (:feedback-sigils data)
                     :learned-specs (:learned-specs data)
                     :leaderboard (:leaderboard data)}
        :else {}))
    (catch Exception _
      {})))

(defn- update-leaderboard [leaderboard {:keys [score rule seed]} max-size]
  (let [entry {:score score :rule rule :seed seed}
        ranked (->> (conj (vec (or leaderboard [])) entry)
                    (sort-by :score >))]
    (if (and (number? max-size) (pos? (long max-size)))
      (vec (take (long max-size) ranked))
      (vec ranked))))

(defn- pick-leaderboard-rule [leaderboard chance ^java.util.Random rng]
  (when (and (seq leaderboard) (number? chance) (< (.nextDouble rng) (double chance)))
    (:rule (nth leaderboard (rng-int rng (count leaderboard))))))

(defn- safe-name [s]
  (-> s str (str/replace #"[^a-zA-Z0-9._-]" "_")))

(defn- ensure-dir! [path]
  (let [f (io/file path)]
    (.mkdirs f)
    path))

(defn- write-top-runs! [dir top-runs render-opts]
  (let [dir (ensure-dir! dir)]
    (mapv (fn [idx {:keys [summary rule run-result seed]}]
            (let [score (format "%.2f" (double (or (:composite-score summary) 0.0)))
                  kernel (ca/kernel-id (:kernel rule))
                  base (safe-name (format "run%02d_score%s_seed%s_%s"
                                          (inc idx)
                                          score
                                          (or seed "na")
                                          kernel))
                  edn-path (str dir "/" base ".edn")
                  img-path (str dir "/" base ".ppm")]
              (when run-result
                (spit edn-path (pr-str run-result))
                (render/render-run->file! run-result img-path render-opts))
              {:edn edn-path
               :img img-path
               :seed seed}))
          (range (count top-runs))
          top-runs)))

(defn- render-pdf! [image-paths out]
  (when (seq image-paths)
    (apply shell/sh
           (concat ["convert"]
                   image-paths
                   [out]))))

(def ^:private supported-engines
  #{:mmca :tensor-closed-loop})

(def ^:private tensor-unsupported-keys
  [:no-freeze :require-gate :baldwin-share
   :lesion :lesion-tick :lesion-target :lesion-half :lesion-mode
   :aif-weight :aif-guide :aif-guide-min :aif-mutate :aif-mutate-min
   :kernel-context :exotype-update-threshold :exotype-invert :quiet-kernel
   :pulses :no-pulses :render-exotype :save-top :save-top-dir :save-top-pdf
   :report-every :feedback-every])

(defn- present? [v]
  (cond
    (nil? v) false
    (false? v) false
    (and (string? v) (empty? v)) false
    (and (sequential? v) (empty? v)) false
    :else true))

(defn- normalize-engine [engine]
  (let [raw (cond
              (nil? engine) :mmca
              (keyword? engine) engine
              (string? engine) (keyword engine)
              :else engine)
        engine (case raw
                 :tensor :tensor-closed-loop
                 :tensor-loop :tensor-closed-loop
                 raw)]
    (when-not (contains? supported-engines engine)
      (throw (ex-info "Unsupported metaevolve engine"
                      {:engine engine
                       :supported (vec (sort supported-engines))})))
    engine))

(defn- tensor-unsupported-opts [opts]
  (->> tensor-unsupported-keys
       (filter #(present? (get opts %)))
       vec))

(defn- report-top-tensor [ranked]
  (println "Top tensor closed-loop rules:")
  (doseq [{:keys [summary rule]} (take 5 ranked)]
    (println (format "score %.2f | rule-sigil %s | backend %s"
                     (double (or (:composite-score summary) 0.0))
                     (or (:rule-sigil rule) "-")
                     (name (or (:backend rule) :clj))))))

(defn -main [& args]
  (let [parsed (parse-args args)
        {:keys [help unknown engine length generations runs seed report no-freeze
                require-phenotype require-gate baldwin-share
                lesion lesion-tick lesion-target lesion-half lesion-mode
                feedback-top feedback-edn feedback-load feedback-every leaderboard-size leaderboard-chance aif-weight
                aif-guide aif-guide-min aif-mutate aif-mutate-min
                kernel-context exotype-update-threshold exotype-invert quiet-kernel
                pulses no-pulses render-exotype
                save-top save-top-dir save-top-pdf report-every
                tensor-rule-sigil tensor-backend tensor-wrap tensor-boundary-bit
                strict-parity tensor-explore-rate]} parsed]
    (cond
      help
      (println (usage))

      unknown
      (do
        (println "Unknown option:" unknown)
        (println)
        (println (usage)))

      :else
      (let [length (or length default-length)
            generations (or generations default-generations)
            runs (or runs default-runs)
            seed (or seed (System/currentTimeMillis))
            engine (normalize-engine engine)
            strict-parity (if (contains? parsed :strict-parity)
                            (boolean strict-parity)
                            true)
            aif-weight (if (number? aif-weight) aif-weight 0.2)
            aif-guide-min (when (number? aif-guide-min) aif-guide-min)
            aif-mutate-min (when (number? aif-mutate-min) aif-mutate-min)
            save-top (when (number? save-top) save-top)
            exotype-threshold (if (and quiet-kernel (not (number? exotype-update-threshold)))
                                0.1
                                exotype-update-threshold)
            kernel-context-opts (when (or kernel-context exotype-threshold exotype-invert)
                                  {:enabled? (boolean kernel-context)
                                   :exotype-update-threshold exotype-threshold
                                   :exotype-invert? (boolean exotype-invert)})
            render-opts (when render-exotype {:exotype? true})
            pulses-enabled (and (boolean pulses) (not (boolean no-pulses)))
            lesion-map (when (or lesion lesion-tick lesion-target lesion-half lesion-mode)
                         (cond-> {}
                           lesion-tick (assoc :tick lesion-tick)
                           lesion-target (assoc :target lesion-target)
                           lesion-half (assoc :half lesion-half)
                           lesion-mode (assoc :mode lesion-mode)))
            loaded-feedback (when feedback-load (load-feedback feedback-load))
            initial-feedback (:feedback-sigils loaded-feedback)
            initial-learned-specs (:learned-specs loaded-feedback)
            initial-leaderboard (:leaderboard loaded-feedback)
            leaderboard-size (when (number? leaderboard-size) leaderboard-size)
            leaderboard-chance (cond
                                 (number? leaderboard-chance) leaderboard-chance
                                 (number? leaderboard-size) 0.25
                                 :else nil)
            aif-opts {:weight aif-weight
                      :guide? (boolean aif-guide)
                      :guide-min aif-guide-min
                      :mutate? (boolean aif-mutate)
                      :mutate-min aif-mutate-min}
            leaderboard-opts {:size leaderboard-size
                              :chance leaderboard-chance
                              :initial initial-leaderboard}
            checkpoint-opts (when (and feedback-edn (number? feedback-every) (pos? (long feedback-every)))
                              {:path feedback-edn
                               :every feedback-every
                               :meta {:seed seed
                                      :length length
                                      :generations generations
                                      :runs runs}})
            report-opts (when (and report (number? report-every) (pos? (long report-every)))
                          {:path report
                           :every report-every
                           :meta {:seed seed
                                  :length length
                                  :generations generations
                                  :runs runs
                                  :aif-weight aif-weight
                                  :aif-guide (boolean aif-guide)
                                  :aif-guide-min aif-guide-min
                                  :aif-mutate (boolean aif-mutate)
                                  :aif-mutate-min aif-mutate-min
                                  :leaderboard-size leaderboard-size
                                  :leaderboard-chance leaderboard-chance
                                  :exotype-update-threshold exotype-update-threshold
                                  :exotype-invert (boolean exotype-invert)
                                  :pulses-enabled pulses-enabled}})]
        (case engine
          :tensor-closed-loop
          (let [unsupported (tensor-unsupported-opts parsed)
                _ (when (seq unsupported)
                    (throw (ex-info "Tensor engine does not support selected metaevolve options"
                                    {:engine engine
                                     :unsupported unsupported})))
                init-rule-sigil (or tensor-rule-sigil
                                    (get-in initial-leaderboard [0 :rule :rule-sigil])
                                    (first initial-feedback))
                explore-rate (cond
                               (number? tensor-explore-rate) tensor-explore-rate
                               (number? leaderboard-chance) leaderboard-chance
                               :else 0.0)
                tensor-result (tensor-loop/run-tensor-closed-loop
                               (cond-> {:seed seed
                                        :runs runs
                                        :length length
                                        :generations generations
                                        :feedback-top feedback-top
                                        :init-feedback-sigils initial-feedback
                                        :explore-rate explore-rate
                                        :strict-parity strict-parity
                                        :with-phenotype (boolean require-phenotype)}
                                 init-rule-sigil (assoc :init-rule-sigil init-rule-sigil)
                                 tensor-backend (assoc :backend tensor-backend)
                                 (contains? parsed :tensor-wrap) (assoc :wrap? (boolean tensor-wrap))
                                 (number? tensor-boundary-bit) (assoc :boundary-bit tensor-boundary-bit)
                                 (number? leaderboard-size) (assoc :leaderboard-size leaderboard-size)))]
            (println (format "Evolved %d tensor closed-loop runs | length %d | generations %d"
                             runs length generations))
            (println (format "Parity %.2f%% | history parity %.2f%%"
                             (* 100.0 (double (or (get-in tensor-result [:summary :parity-rate]) 0.0)))
                             (* 100.0 (double (or (get-in tensor-result [:summary :history-parity-rate]) 0.0)))))
            (report-top-tensor (:ranked tensor-result))
            (when feedback-edn
              (spit feedback-edn
                    (pr-str (tensor-loop/feedback-payload tensor-result
                                                          {:leaderboard-size leaderboard-size}))))
            (when report
              (spit report
                    (pr-str (tensor-loop/report-payload tensor-result
                                                        {:leaderboard-size leaderboard-size})))))

          :mmca
          (let [{:keys [ranked history feedback-sigils learned-specs top-runs] :as result}
                (binding [*quiet-kernel?* (boolean quiet-kernel)]
                  (evolve-meta-rules runs length generations seed no-freeze require-phenotype require-gate baldwin-share lesion-map feedback-top initial-feedback initial-learned-specs aif-opts kernel-context-opts pulses-enabled save-top leaderboard-opts checkpoint-opts report-opts))]
            (println (format "Evolved %d runs | length %d | generations %d" runs length generations))
            (report-baseline history)
            (report-top ranked)
            (when (and save-top (pos? save-top))
              (let [dir (or save-top-dir "./mmca_top_runs")
                    paths (write-top-runs! dir top-runs render-opts)
                    images (mapv :img paths)]
                (println (format "Saved top %d runs to %s" (long (count paths)) dir))
                (when save-top-pdf
                  (render-pdf! images save-top-pdf)
                  (println "Wrote PDF" save-top-pdf))))
            (when feedback-edn
              (spit feedback-edn
                    (pr-str (payload/feedback-payload {:meta {:seed seed
                                                              :length length
                                                              :generations generations
                                                              :runs runs}
                                                       :feedback-sigils feedback-sigils
                                                       :learned-specs learned-specs
                                                       :leaderboard (:leaderboard result)}))))
            (when report
              (spit report
                    (pr-str (payload/report-payload {:meta {:seed seed
                                                            :length length
                                                            :generations generations
                                                            :runs runs
                                                            :aif-weight aif-weight
                                                            :aif-guide (boolean aif-guide)
                                                            :aif-guide-min aif-guide-min
                                                            :aif-mutate (boolean aif-mutate)
                                                            :aif-mutate-min aif-mutate-min
                                                            :leaderboard-size leaderboard-size
                                                            :leaderboard-chance leaderboard-chance
                                                            :exotype-update-threshold exotype-update-threshold
                                                            :exotype-invert (boolean exotype-invert)
                                                            :pulses-enabled pulses-enabled}
                                                       :ranked ranked
                                                       :learned-specs learned-specs
                                                       :leaderboard (:leaderboard result)
                                                       :runs-detail history
                                                       :top-runs (map #(dissoc % :run-result) top-runs)}))))))))))
