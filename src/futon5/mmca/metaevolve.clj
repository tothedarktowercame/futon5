(ns futon5.mmca.metaevolve
  "Outer-loop evolution of static meta-rules."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.adapters :as adapters]
            [futon5.mmca.meta-lift :as meta-lift]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.operators :as operators]
            [futon5.mmca.render :as render]
            [futon5.mmca.runtime :as mmca]))

(def ^:private default-length 50)
(def ^:private default-runs 50)
(def ^:private default-generations 50)

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
    "  --feedback-edn PATH  Write accumulated sigils to EDN."
    "  --feedback-load PATH Load initial sigils from EDN."
    "  --aif-weight P       Blend AIF score into composite (0-1)."
    "  --aif-guide          Use AIF score to gate feedback sigils."
    "  --aif-guide-min P    Minimum AIF score (0-100) to fully accept sigils."
    "  --aif-mutate         Use AIF deficits to steer kernel selection."
    "  --aif-mutate-min P   Apply AIF steering when score below P (0-100)."
    "  --save-top N         Save the top N runs (EDN + image)."
    "  --save-top-dir PATH  Directory for saved runs (default ./mmca_top_runs)."
    "  --save-top-pdf PATH  Render saved images into a PDF (optional)."
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

          (= "--save-top" flag)
          (recur (rest more) (assoc opts :save-top (parse-int (first more))))

          (= "--save-top-dir" flag)
          (recur (rest more) (assoc opts :save-top-dir (first more)))

          (= "--save-top-pdf" flag)
          (recur (rest more) (assoc opts :save-top-pdf (first more)))

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

(defn- random-kernel [^java.util.Random rng]
  (let [ks (vec (keys ca/kernels))]
    (nth ks (rng-int rng (count ks)))))

(defn- toggle [x]
  (not (boolean x)))

(defn- random-meta-rule [^java.util.Random rng _no-freeze? require-phenotype? require-gate?]
  (let [genotype-gate (if require-gate? true (rng-bool rng))
        phenotype? (if require-phenotype?
                     true
                     (if genotype-gate true (rng-bool rng)))]
    {:kernel (random-kernel rng)
     :lock-kernel (rng-bool rng)
     :freeze-genotype false
     :genotype-gate genotype-gate
     :use-operators true
     :operator-scope :genotype
     :genotype-mode :random
     :global-rule nil
     :phenotype? phenotype?}))

(defn- mutate-meta-rule [^java.util.Random rng _no-freeze? require-phenotype? require-gate? rule]
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
               rule)]
    (case pick
      :kernel (assoc rule :kernel (random-kernel rng))
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
      rule)))

(defn- enforce-baldwin [rule]
  (-> rule
      (assoc :kernel :blending-baldwin
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
        {:keys [aif/regime-score aif/regime-bonus aif/regime-mid-area aif/regime-components]}
        (regime-score gen-history)
        score (max 0.0 (+ (* base-score (- 1.0 (* 0.6 sticky-penalty)))
                          aif/regime-bonus))]
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
     :aif/regime-score aif/regime-score
     :aif/regime-bonus aif/regime-bonus
     :aif/regime-mid-area aif/regime-mid-area
     :aif/regime-components aif/regime-components
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
    (vec (distinct pool))))

(defn- aif-guided-rule [rule summary ^java.util.Random rng aif-mutate? aif-mutate-min]
  (if (and aif-mutate? summary)
    (let [score (double (or (:aif-score summary) 0.0))
          min-score (double (or aif-mutate-min default-aif-mutate-min))]
      (if (< score min-score)
        (let [pool (aif-kernel-pool summary)
              pool (if (seq pool)
                     pool
                     (vec (remove #{:blending-baldwin} (keys ca/kernels))))
              kernel (nth pool (rng-int rng (count pool)))]
          (-> rule
              (assoc :kernel kernel)
              (assoc :lock-kernel false)))
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

(defn- build-run-opts [rule length generations ^java.util.Random rng lesion feedback-sigils learned-specs]
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
               (and use-operators? (seq learned-sigils)) (update :pattern-sigils merge-sigils learned-sigils))]
    {:opts opts
     :genotype genotype
     :phenotype phenotype}))

(defn- sha1 [s]
  (let [md (java.security.MessageDigest/getInstance "SHA-1")
        bytes (.digest md (.getBytes (str s) "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) bytes))))

(defn- evaluate-rule [rule length generations run-seed lesion feedback-sigils feedback-top learned-specs aif-weight capture-run?]
  (let [rng (java.util.Random. (long run-seed))
        {:keys [opts genotype phenotype]} (build-run-opts rule length generations rng lesion feedback-sigils learned-specs)
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
     :meta-lift {:top-sigils top-sigils
                 :sigil-counts (:sigil-counts lift)}
     :learned-sigils learned-sigils
     :run-result (when capture-run? result)}))

(defn- report-top [ranked]
  (println "Top meta-rules:")
  (doseq [{:keys [rule summary]} (take 5 ranked)]
    (println (format "score %.2f | kernel %s | lock %s | freeze %s | ops %s | mode %s | rule %s"
                     (double (:composite-score summary))
                     (name (:kernel rule))
                     (boolean (:lock-kernel rule))
                     (boolean (:freeze-genotype rule))
                     (boolean (:use-operators rule))
                     (name (:genotype-mode rule))
                     (or (:global-rule rule) "-")))
    (println (format "  interesting %.2f | compress %.3f | autocorr %.3f"
                     (double (or (:score summary) 0.0))
                     (double (or (:lz78-ratio summary) 0.0))
                     (double (or (:temporal-autocorr summary) 0.0))))))

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

(defn- log-run [idx runs {:keys [rule summary seed genotype-hash phenotype-hash policy meta-lift learned-sigils]} feedback-count]
  (println (format "run %02d/%02d | score %.2f | kernel %s | lock %s | freeze %s | ops %s | mode %s | rule %s"
                   (inc idx)
                   runs
                   (double (:composite-score summary))
                   (name (:kernel rule))
                   (boolean (:lock-kernel rule))
                   (boolean (:freeze-genotype rule))
                   (boolean (:use-operators rule))
                   (name (:genotype-mode rule))
                   (or (:global-rule rule) "-")))
  (println (format "  policy %s | gate %s | seed %s | gen %s | phe %s"
                   (or policy "-")
                   (boolean (:genotype-gate rule))
                   (or seed "-")
                   (or genotype-hash "-")
                   (or phenotype-hash "-")))
  (when feedback-count
    (println (format "  feedback sigils: %d" (long feedback-count))))
  (when (seq learned-sigils)
    (println (format "  learned ops: %d" (long (count learned-sigils)))))
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

(defn evolve-meta-rules [runs length generations seed no-freeze? require-phenotype? require-gate? baldwin-share lesion feedback-top initial-feedback aif-weight aif-guide? aif-guide-min aif-mutate? aif-mutate-min save-top]
  (loop [i 0
         best nil
         history []
         policy-rng (java.util.Random. (long (+ seed 4242)))
         feedback-sigils (vec (distinct (or initial-feedback [])))
         learned-specs {}
         top-runs []
         last-summary nil]
    (if (= i runs)
      (let [ranked (sort-by (comp :composite-score :summary) > history)]
        {:best best
         :ranked ranked
         :history history
         :feedback-sigils feedback-sigils
         :learned-specs learned-specs
         :top-runs top-runs})
      (let [rng (java.util.Random. (long (+ seed i)))
            guide-rng (java.util.Random. (long (+ seed i 42420)))
            candidate (if (and best (pos? i))
                        (mutate-meta-rule rng no-freeze? require-phenotype? require-gate? (:rule best))
                        (random-meta-rule rng no-freeze? require-phenotype? require-gate?))
            policy (when (number? baldwin-share)
                     (if (< (.nextDouble policy-rng) baldwin-share)
                       :baldwin
                       :baseline))
            candidate (case policy
                        :baldwin (enforce-baldwin candidate)
                        candidate)
            candidate (aif-guided-rule candidate last-summary guide-rng aif-mutate? aif-mutate-min)
            run-seed (+ seed i 1000)
            result (assoc (evaluate-rule candidate length generations run-seed lesion feedback-sigils feedback-top learned-specs aif-weight (pos? (long (or save-top 0))))
                          :policy policy)
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
            top-runs' (update-top-runs top-runs result save-top)]
        (log-run i runs result (count feedback-sigils'))
        (recur (inc i) best' (conj history result) policy-rng feedback-sigils' learned-specs' top-runs' (:summary result))))))

(defn- load-feedback [path]
  (try
    (let [data (edn/read-string (slurp path))]
      (cond
        (vector? data) data
        (map? data) (:feedback-sigils data)
        :else nil))
    (catch Exception _
      nil)))

(defn- safe-name [s]
  (-> s str (str/replace #"[^a-zA-Z0-9._-]" "_")))

(defn- ensure-dir! [path]
  (let [f (io/file path)]
    (.mkdirs f)
    path))

(defn- write-top-runs! [dir top-runs]
  (let [dir (ensure-dir! dir)]
    (mapv (fn [idx {:keys [summary rule run-result seed]}]
            (let [score (format "%.2f" (double (or (:composite-score summary) 0.0)))
                  kernel (name (:kernel rule))
                  base (safe-name (format "run%02d_score%s_seed%s_%s"
                                          (inc idx)
                                          score
                                          (or seed "na")
                                          kernel))
                  edn-path (str dir "/" base ".edn")
                  img-path (str dir "/" base ".ppm")]
              (when run-result
                (spit edn-path (pr-str run-result))
                (render/render-run->file! run-result img-path))
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

(defn -main [& args]
  (let [{:keys [help unknown length generations runs seed report no-freeze
                require-phenotype require-gate baldwin-share
                lesion lesion-tick lesion-target lesion-half lesion-mode
                feedback-top feedback-edn feedback-load aif-weight
                aif-guide aif-guide-min aif-mutate aif-mutate-min
                save-top save-top-dir save-top-pdf]} (parse-args args)]
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
            aif-weight (if (number? aif-weight) aif-weight 0.2)
            aif-guide-min (when (number? aif-guide-min) aif-guide-min)
            aif-mutate-min (when (number? aif-mutate-min) aif-mutate-min)
            save-top (when (number? save-top) save-top)
            lesion-map (when (or lesion lesion-tick lesion-target lesion-half lesion-mode)
                         (cond-> {}
                           lesion-tick (assoc :tick lesion-tick)
                           lesion-target (assoc :target lesion-target)
                           lesion-half (assoc :half lesion-half)
                           lesion-mode (assoc :mode lesion-mode)))
            initial-feedback (when feedback-load (load-feedback feedback-load))
            {:keys [ranked history feedback-sigils learned-specs top-runs] :as result}
            (evolve-meta-rules runs length generations seed no-freeze require-phenotype require-gate baldwin-share lesion-map feedback-top initial-feedback aif-weight aif-guide aif-guide-min aif-mutate aif-mutate-min save-top)]
        (println (format "Evolved %d runs | length %d | generations %d" runs length generations))
        (report-baseline history)
        (report-top ranked)
        (when (and save-top (pos? save-top))
          (let [dir (or save-top-dir "./mmca_top_runs")
                paths (write-top-runs! dir top-runs)
                images (mapv :img paths)]
            (println (format "Saved top %d runs to %s" (long (count paths)) dir))
            (when save-top-pdf
              (render-pdf! images save-top-pdf)
              (println "Wrote PDF" save-top-pdf))))
        (when feedback-edn
          (spit feedback-edn
                (pr-str {:seed seed
                         :length length
                         :generations generations
                         :runs runs
                         :feedback-sigils feedback-sigils})))
        (when report
          (spit report
                (pr-str {:seed seed
                         :length length
                         :generations generations
                         :runs runs
                         :aif-weight aif-weight
                         :aif-guide (boolean aif-guide)
                         :aif-guide-min aif-guide-min
                         :aif-mutate (boolean aif-mutate)
                         :aif-mutate-min aif-mutate-min
                         :ranked ranked
                         :learned-specs learned-specs
                         :runs-detail history
                         :top-runs (map #(dissoc % :run-result) top-runs)})))))))
