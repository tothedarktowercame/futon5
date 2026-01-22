(ns cyber-mmca-wiring-ab-validate
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.cyber-mmca.core :as cyber-core]
            [futon5.hexagram.metrics :as hex-metrics]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.filament :as filament]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.register-shift :as register-shift]
            [futon5.mmca.render :as render]
            [futon5.mmca.runtime :as runtime]
            [futon5.hexagram.logging :as hex-log]
            [futon5.xenotype.interpret :as interpret]
            [futon5.xenotype.wiring :as wiring]))

(def ^:private default-scale 250)

(declare winner)

(defn- usage []
  (str/join
   "\n"
   ["A/B validation for wiring diagrams using Cyber-MMCA runs and ensemble scoring."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_wiring_ab_validate.clj [options]"
    ""
    "Options:"
    "  --n N              Number of fresh seeds (default 10)."
    "  --seed N           RNG seed for seed selection (default 4242)."
    "  --seeds LIST       Comma-separated explicit seeds (overrides --n/--seed)."
    "  --controller-a KW  Controller for A (null, hex, sigil, wiring)."
    "  --controller-b KW  Controller for B (null, hex, sigil, wiring)."
    "  --wiring-a PATH    Wiring diagram EDN path for A."
    "  --wiring-a-index N Wiring candidate index for A (default 0)."
    "  --wiring-b PATH    Wiring diagram EDN path for B."
    "  --wiring-b-index N Wiring candidate index for B (default 0)."
    "  --windows N        Number of control windows (default 12)."
    "  --W N              Window length in generations (default 10)."
    "  --S N              Window stride (default 10)."
    "  --length N         Genotype length (default 32)."
    "  --phenotype-length N Phenotype length (optional; default length)."
    "  --kernel KW        Kernel keyword (default :mutating-template)."
    "  --sigil STR        Base exotype sigil (default ca/default-sigil)."
    "  --sigil-count N    Control sigil count (default 16)."
    "  --wiring-actions EDN Action vector for wiring controller."
    "  --allow-kernel-switch Allow kernel changes during run."
    "  --exotype-mode KW  Exotype mode (default :inline)."
    "  --out-dir PATH     Output dir for run EDN (default /tmp/cyber-mmca-wiring-ab-<ts>)."
    "  --render-dir PATH  Output dir for PNG renders (default futon5/resources/figures)."
    "  --scale PCT        Resize PNGs by percent (default 250)."
    "  --scores PATH      Output CSV (default /tmp/cyber-mmca-wiring-ab-<ts>.csv)."
    "  --out-table PATH   Output org table (default /tmp/cyber-mmca-wiring-ab-<ts>.org)."
    "  --pairs PATH       Output EDN with A/B run paths (default /tmp/cyber-mmca-wiring-ab-<ts>.edn)."
    "  --inputs PATH      Output inputs list for judge-cli (default /tmp/cyber-mmca-wiring-ab-<ts>-inputs.txt)."
    "  --psr-pur PATH     Append PSR/PUR-style entries to EDN log (optional)."
    "  --hit PATH         HIT judgements EDN lines (optional; enables HIT agreement)."
    "  --ants-tiebreaker  Use ants comparison as tiebreaker when ensemble vote ties."
    "  --ants-top-k N     Number of sigils to stack per side (default 5)."
    "  --ants-runs N      Ant runs per tiebreak (default 20)."
    "  --ants-ticks N     Ant ticks per run (default 200)."
    "  --ants-out-dir PATH Output dir for ants tiebreak (default /tmp/mission-0/ants-tiebreak-<ts>)."
    "  --ants-include-aif Include AIF in ants comparison."
    "  --ants-no-termination Disable early termination in ants comparison."
    "  --ants-sigils-a CSV Override sigil stack for A (comma-separated)."
    "  --ants-sigils-b CSV Override sigil stack for B (comma-separated)."
    "  --help             Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-seeds [s]
  (->> (str/split (or s "") #",")
       (map str/trim)
       (remove str/blank?)
       (map parse-int)
       (remove nil?)
       vec))

(defn- parse-sigils [s]
  (->> (str/split (or s "") #",")
       (map str/trim)
       (remove str/blank?)
       vec))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--n" flag)
          (recur (rest more) (assoc opts :n (parse-int (first more))))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

          (= "--seeds" flag)
          (recur (rest more) (assoc opts :seeds (parse-seeds (first more))))

          (= "--controller-a" flag)
          (recur (rest more) (assoc opts :controller-a (keyword (first more))))

          (= "--controller-b" flag)
          (recur (rest more) (assoc opts :controller-b (keyword (first more))))

          (= "--wiring-a" flag)
          (recur (rest more) (assoc opts :wiring-a (first more)))

          (= "--wiring-a-index" flag)
          (recur (rest more) (assoc opts :wiring-a-index (parse-int (first more))))

          (= "--wiring-b" flag)
          (recur (rest more) (assoc opts :wiring-b (first more)))

          (= "--wiring-b-index" flag)
          (recur (rest more) (assoc opts :wiring-b-index (parse-int (first more))))

          (= "--windows" flag)
          (recur (rest more) (assoc opts :windows (parse-int (first more))))

          (= "--W" flag)
          (recur (rest more) (assoc opts :W (parse-int (first more))))

          (= "--S" flag)
          (recur (rest more) (assoc opts :S (parse-int (first more))))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--phenotype-length" flag)
          (recur (rest more) (assoc opts :phenotype-length (parse-int (first more))))

          (= "--kernel" flag)
          (recur (rest more) (assoc opts :kernel (keyword (first more))))

          (= "--sigil" flag)
          (recur (rest more) (assoc opts :sigil (first more)))

          (= "--sigil-count" flag)
          (recur (rest more) (assoc opts :sigil-count (parse-int (first more))))

          (= "--wiring-actions" flag)
          (recur (rest more) (assoc opts :wiring-actions (edn/read-string (first more))))

          (= "--allow-kernel-switch" flag)
          (recur more (assoc opts :allow-kernel-switch true))

          (= "--exotype-mode" flag)
          (recur (rest more) (assoc opts :exotype-mode (keyword (first more))))

          (= "--out-dir" flag)
          (recur (rest more) (assoc opts :out-dir (first more)))

          (= "--render-dir" flag)
          (recur (rest more) (assoc opts :render-dir (first more)))

          (= "--scale" flag)
          (recur (rest more) (assoc opts :scale (parse-int (first more))))

          (= "--scores" flag)
          (recur (rest more) (assoc opts :scores (first more)))

          (= "--out-table" flag)
          (recur (rest more) (assoc opts :out-table (first more)))

          (= "--pairs" flag)
          (recur (rest more) (assoc opts :pairs (first more)))

          (= "--inputs" flag)
          (recur (rest more) (assoc opts :inputs (first more)))

          (= "--psr-pur" flag)
          (recur (rest more) (assoc opts :psr-pur (first more)))

          (= "--hit" flag)
          (recur (rest more) (assoc opts :hit (first more)))

          (= "--ants-tiebreaker" flag)
          (recur more (assoc opts :ants-tiebreaker true))

          (= "--ants-top-k" flag)
          (recur (rest more) (assoc opts :ants-top-k (parse-int (first more))))

          (= "--ants-runs" flag)
          (recur (rest more) (assoc opts :ants-runs (parse-int (first more))))

          (= "--ants-ticks" flag)
          (recur (rest more) (assoc opts :ants-ticks (parse-int (first more))))

          (= "--ants-out-dir" flag)
          (recur (rest more) (assoc opts :ants-out-dir (first more)))

          (= "--ants-include-aif" flag)
          (recur more (assoc opts :ants-include-aif true))

          (= "--ants-no-termination" flag)
          (recur more (assoc opts :ants-no-termination true))

          (= "--ants-sigils-a" flag)
          (recur (rest more) (assoc opts :ants-sigils-a (parse-sigils (first more))))

          (= "--ants-sigils-b" flag)
          (recur (rest more) (assoc opts :ants-sigils-b (parse-sigils (first more))))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(defn- sigils-for-count [n]
  (->> (ca/sigil-entries)
       (map :sigil)
       (take (max 1 (int n)))
       vec))

(defn- resolve-diagram [path idx]
  (let [data (edn/read-string (slurp path))
        idx (int (or idx 0))]
    (cond
      (and (map? data) (:nodes data)) data
      (and (map? data) (:diagram data)) (:diagram data)
      (and (map? data) (:candidates data))
      (let [cands (:candidates data)
            max-idx (max 0 (dec (count cands)))
            idx (max 0 (min idx max-idx))]
        (nth cands idx))
      :else data)))

(defn- safe-kernel [candidate fallback]
  (try
    (when candidate
      (ca/kernel-fn candidate)
      candidate)
    (catch Exception _ fallback)))

(defn- clamp [x lo hi]
  (max lo (min hi x)))

(defn- adjust-params [params actions]
  (let [params (or params {})
        pressure-step 0.1
        select-step 0.1
        update-default (or (:update-prob params) 0.5)
        match-default (or (:match-threshold params) 0.5)
        apply-action (fn [p action]
                       (case action
                         :pressure-up (update p :update-prob
                                              #(clamp (+ (double (or % update-default)) pressure-step) 0.05 1.0))
                         :pressure-down (update p :update-prob
                                                #(clamp (- (double (or % update-default)) pressure-step) 0.05 1.0))
                         :selectivity-up (update p :match-threshold
                                                 #(clamp (+ (double (or % match-default)) select-step) 0.0 1.0))
                         :selectivity-down (update p :match-threshold
                                                   #(clamp (- (double (or % match-default)) select-step) 0.0 1.0))
                         p))]
    (reduce apply-action params actions)))

(defn- normalize-wiring-score
  [score]
  (when (number? score)
    (let [score (double score)
          scaled (cond
                   (<= score 1.0) score
                   (> score 1.0) (/ score 100.0)
                   :else score)]
      (clamp scaled 0.0 1.0))))

(defn- choose-actions-wiring
  [diagram window {:keys [actions] :or {actions [:pressure-up :selectivity-up :selectivity-down :pressure-down]}}]
  (let [context {:summary (:summary window)}
        {:keys [output]} (interpret/evaluate-diagram diagram context)
        pass? (:pass? output)
        score (normalize-wiring-score (:score output))
        action-list (vec (or actions [:hold]))
        action (cond
                 (and (boolean? pass?) (= (count action-list) 2))
                 (if pass? (first action-list) (second action-list))

                 (and (number? score) (pos? (count action-list)))
                 (let [idx (min (dec (count action-list))
                                (int (Math/floor (* score (count action-list)))))]
                   (nth action-list idx))

                 :else
                 :hold)]
    {:actions [(or action :hold)]
     :wiring-pass pass?
     :wiring-score score}))

(defn- run-cmd
  [{:keys [cmd dir]}]
  (let [{:keys [exit out err]} (apply shell/sh (concat cmd (when dir [:dir dir])))]
    (when-not (zero? exit)
      (throw (ex-info "Command failed" {:cmd cmd :dir dir :out out :err err})))
    {:out out :err err}))

(defn- write-cyberants!
  [sigils out-path]
  (run-cmd {:cmd (concat ["clj" "-M" "-m" "futon5.adapters.cyberant-cli"]
                         (mapcat (fn [sigil] ["--sigil" sigil]) sigils)
                         ["--out" out-path])
            :dir "futon5"}))

(defn- run-ants-compare!
  [{:keys [hex-path sigil-path out runs ticks include-aif no-termination]}]
  (run-cmd {:cmd (concat ["clj" "-M" "-m" "ants.compare"
                          "--hex" hex-path
                          "--sigil" sigil-path
                          "--runs" (str (or runs 20))
                          "--ticks" (str (or ticks 200))
                          "--out" out]
                         (when include-aif ["--include-aif"])
                         (when no-termination ["--no-termination"]))
            :dir "futon2"}))

(defn- sigil-stack
  [result {:keys [top-k fallback]}]
  (let [k (max 1 (int (or top-k 5)))
        sigils (->> (:windows result)
                    (map :sigil)
                    (remove nil?)
                    frequencies
                    (sort-by (fn [[_ n]] (- n)))
                    (map first)
                    (take k)
                    vec)
        fallback (or fallback ca/default-sigil)
        padded (into sigils (repeat (max 0 (- k (count sigils))) fallback))]
    padded))

(defn- ants-tiebreak
  [{:keys [seed sigils-a sigils-b out-dir runs ticks include-aif no-termination]}]
  (let [dir (str (io/file out-dir (format "seed-%d" seed)))
        _ (.mkdirs (io/file dir))
        path-a (str (io/file dir "ants-a.edn"))
        path-b (str (io/file dir "ants-b.edn"))
        out (str (io/file dir "ants-compare.edn"))]
    (write-cyberants! sigils-a path-a)
    (write-cyberants! sigils-b path-b)
    (run-ants-compare! {:hex-path path-a
                        :sigil-path path-b
                        :out out
                        :runs runs
                        :ticks ticks
                        :include-aif include-aif
                        :no-termination no-termination})
    (let [result (edn/read-string (slurp out))
          score-a (double (get-in result [:mean-score :cyber] 0.0))
          score-b (double (get-in result [:mean-score :cyber-sigil] 0.0))
          winner (winner score-a score-b)]
      {:winner winner
       :score-a score-a
       :score-b score-b
       :out out
       :sigils-a sigils-a
       :sigils-b sigils-b})))

(defn- run-controller-full
  [{:keys [controller seed windows W S kernel length sigil sigil-count
           wiring-path wiring-index wiring-actions
           phenotype-length allow-kernel-switch exotype-mode]}]
  (let [rng (when seed (java.util.Random. (long seed)))
        length (max 1 (int (or length 32)))
        kernel (or kernel :mutating-template)
        sigil (or sigil ca/default-sigil)
        windows (max 1 (int (or windows 10)))
        W (max 1 (int (or W 10)))
        S (max 1 (int (or S W)))
        genotype (if rng
                   (rng-sigil-string rng length)
                   (ca/random-sigil-string length))
        phenotype (when phenotype-length
                    (ca/random-phenotype-string (max 1 (int phenotype-length))))
        base-exotype (exotype/resolve-exotype {:sigil sigil :tier :super})
        sigils (when (= controller :sigil)
                 (sigils-for-count (or sigil-count 16)))
        wiring-diagram (when (= controller :wiring)
                         (let [lib (wiring/load-components)
                               diagram (resolve-diagram wiring-path wiring-index)
                               validation (wiring/validate-diagram lib diagram)]
                           (when-not (:ok? validation)
                             (throw (ex-info "Invalid wiring diagram." {:errors (:errors validation)})))
                           diagram))
        wiring-actions (or wiring-actions
                           [:pressure-up :selectivity-up :selectivity-down :pressure-down])]
    (loop [idx 0
           state {:genotype genotype
                  :phenotype phenotype
                  :kernel kernel
                  :kernel-fn nil
                  :exotype base-exotype
                  :metrics-history []
                  :gen-history []
                  :phe-history []}
           windows-out []]
      (if (>= idx windows)
        {:state state
         :windows windows-out}
        (let [result (runtime/run-mmca {:genotype (:genotype state)
                                        :phenotype (:phenotype state)
                                        :generations W
                                        :kernel (:kernel state)
                                        :kernel-fn (:kernel-fn state)
                                        :lock-kernel (not (true? allow-kernel-switch))
                                        :exotype (:exotype state)
                                        :exotype-mode (or exotype-mode :inline)
                                        :seed (when seed (+ seed idx))})
              metrics-history (into (:metrics-history state) (:metrics-history result))
              gen-history (into (:gen-history state) (:gen-history result))
              phe-history (into (:phe-history state) (:phe-history result))
              window (last (metrics/windowed-macro-features
                            {:metrics-history metrics-history
                             :gen-history gen-history
                             :phe-history phe-history}
                            {:W W :S S}))
              {:keys [actions chosen-sigil wiring-score wiring-pass]} (cond
                                                                        (= controller :null) {:actions [:hold]}
                                                                        (= controller :sigil)
                                                                        (let [{:keys [sigil actions]} (cyber-core/choose-actions-sigil sigils window)]
                                                                          {:actions actions :chosen-sigil sigil})
                                                                        (= controller :wiring)
                                                                        (choose-actions-wiring wiring-diagram
                                                                                               window
                                                                                               {:actions wiring-actions})
                                                                        :else {:actions (cyber-core/choose-actions-hex window)})
              params-before (get-in state [:exotype :params])
              params-after (if (seq (remove #{:hold} actions))
                             (adjust-params params-before actions)
                             params-before)
              delta-update (when (and (:update-prob params-after) (:update-prob params-before))
                             (- (double (:update-prob params-after))
                                (double (:update-prob params-before))))
              delta-match (when (and (:match-threshold params-after) (:match-threshold params-before))
                            (- (double (:match-threshold params-after))
                               (double (:match-threshold params-before))))
              applied? (or (and (number? delta-update) (not (zero? delta-update)))
                           (and (number? delta-match) (not (zero? delta-match))))
              exotype' (assoc (:exotype state) :params params-after)
              record {:controller controller
                      :seed seed
                      :window idx
                      :w-start (:w-start window)
                      :w-end (:w-end window)
                      :regime (:regime window)
                      :pressure (:pressure window)
                      :selectivity (:selectivity window)
                      :structure (:structure window)
                      :activity (:activity window)
                      :actions actions
                      :sigil chosen-sigil
                      :wiring-score wiring-score
                      :wiring-pass wiring-pass
                      :update-prob (:update-prob params-after)
                      :match-threshold (:match-threshold params-after)
                      :delta-update delta-update
                      :delta-match delta-match
                      :applied? applied?
                      :exotype-mode (or exotype-mode :inline)}]
          (recur (inc idx)
                 {:genotype (or (last (:gen-history result)) (:genotype state))
                  :phenotype (or (last (:phe-history result)) (:phenotype state))
                  :kernel (safe-kernel (:kernel result) (:kernel state))
                  :kernel-fn (or (:kernel-fn result) (:kernel-fn state))
                  :exotype exotype'
                  :metrics-history metrics-history
                  :gen-history gen-history
                  :phe-history phe-history}
                 (conj windows-out record)))))))

(defn- run->edn!
  [{:keys [state windows meta]} out-dir label idx]
  (let [seed (or (:seed (first windows)) 0)
        metrics-history (:metrics-history state)
        gen-history (:gen-history state)
        phe-history (:phe-history state)
        generations (or (:generations meta)
                        (count (or gen-history metrics-history)))
        base (format "%s-%02d-seed-%d" label idx seed)
        path (str (io/file out-dir (str base ".edn")))
        run {:metrics-history metrics-history
             :gen-history gen-history
             :phe-history phe-history
             :genotype (last gen-history)
             :phenotype (last phe-history)
             :kernel (:kernel state)
             :exotype (:exotype state)
             :cyber-mmca/windows windows
             :cyber-mmca/meta meta
             :hit/meta {:label base
                        :seed seed
                        :generations generations
                        :controller :wiring
                        :note "replayed from Cyber-MMCA wiring AB validation"}}]
    (spit path (pr-str run))
    path))

(defn- band-score [x center width]
  (let [x (double (or x 0.0))
        center (double (or center 0.0))
        width (double (or width 1.0))]
    (if (pos? width)
      (max 0.0 (- 1.0 (/ (Math/abs (- x center)) width)))
      0.0)))

(defn- envelope-score [summary]
  (let [ent (band-score (:avg-entropy-n summary) 0.6 0.25)
        chg (band-score (:avg-change summary) 0.2 0.15)]
    (* 100.0 (/ (+ ent chg) 2.0))))

(defn- triad-score [summary hex-score]
  (let [gen-score (double (or (:gen/composite-score summary) (:composite-score summary) 0.0))
        phe-score (double (or (:phe/composite-score summary) (:composite-score summary) 0.0))
        exo-score (double (or hex-score 0.0))]
    (/ (+ gen-score phe-score exo-score) 3.0)))

(defn- phe->grid [s]
  [(mapv (fn [ch] (if (= ch \1) 1 0)) (seq (or s "")))])

(defn- phe->frames [phe-history]
  (mapv phe->grid (or phe-history [])))

(defn- score-run [run]
  (let [summary (metrics/summarize-run run)
        hex-transition (hex-metrics/run->transition-matrix run)
        hex-signature (hex-metrics/transition-matrix->signature hex-transition)
        hex-class (hex-metrics/signature->hexagram-class hex-signature)
        hex-score (* 100.0 (hex-metrics/hexagram-fitness hex-class))
        shift (register-shift/register-shift-summary run)
        filament-score (double (or (:score (filament/analyze-run (phe->frames (:phe-history run)) {})) 0.0))]
    {:short (double (or (:composite-score summary) 0.0))
     :envelope (envelope-score summary)
     :triad (triad-score summary hex-score)
     :shift (double (or (:shift/composite shift) 0.0))
     :filament filament-score
     :hex {:class hex-class :score hex-score}}))

(defn- render-run! [run path]
  (render/render-run->file! run path {:exotype? true}))

(defn- write-png! [ppm png]
  (shell/sh "convert" ppm png))

(defn- scale-png! [png scale]
  (when (and scale (pos? scale))
    (shell/sh "mogrify" "-resize" (str scale "%") png)))

(defn- winner [a b]
  (cond
    (> a b) :a
    (< a b) :b
    :else :tie))

(defn- winners [scores-a scores-b]
  (map (fn [k] [k (winner (double (or (get scores-a k) 0.0))
                          (double (or (get scores-b k) 0.0)))])
       [:short :envelope :triad :shift :filament]))

(defn- votes [pairs]
  (frequencies (map second pairs)))

(defn- now []
  (.toString (java.time.Instant/now)))

(defn- psr-entry
  [{:keys [side seed controller wiring-path wiring-index wiring-actions windows W S length phenotype-length kernel sigil exotype-mode run-path alternatives]}]
  {:event :psr
   :timestamp (now)
   :intention "Compare wiring candidates for steering signal quality."
   :pattern (cond-> {:type controller
                     :side side}
              (= controller :wiring) (assoc :path wiring-path :index wiring-index))
   :why-now "A/B validation on fresh seeds."
   :side side
   :seed seed
   :controller controller
   :wiring (when (= controller :wiring)
             {:path wiring-path :index wiring-index})
   :wiring-actions wiring-actions
   :alternatives alternatives
   :run/path run-path
   :params {:windows windows
            :W W
            :S S
            :length length
            :phenotype-length phenotype-length
            :kernel kernel
            :sigil sigil
            :exotype-mode exotype-mode}})

(defn- pur-entry
  [{:keys [side seed run-path scores vote-winner evidence next-use agreement]}]
  {:event :pur
   :timestamp (now)
   :side side
   :seed seed
   :run/path run-path
   :scores scores
   :vote-winner vote-winner
   :evidence evidence
   :next-use next-use
   :agreement agreement})

(defn- read-hit-judgements
  [path]
  (when path
    (let [lines (->> (str/split-lines (slurp path))
                     (map str/trim)
                     (remove str/blank?))]
      (->> lines
           (map edn/read-string)
           (filter :path)
           (reduce (fn [m {:keys [path label]}]
                     (assoc m path label))
                   {})))))

(defn- label->score [label]
  (case label
    :eoc 2
    :borderline 1
    :not-eoc 0
    nil))

(defn- hit-winner
  [label-a label-b]
  (let [score-a (label->score label-a)
        score-b (label->score label-b)]
    (cond
      (and (number? score-a) (number? score-b))
      (winner score-a score-b)
      :else nil)))

(defn -main [& args]
  (let [{:keys [help unknown n seed seeds controller-a controller-b wiring-a wiring-a-index wiring-b wiring-b-index
                windows W S length phenotype-length kernel sigil sigil-count
                wiring-actions allow-kernel-switch exotype-mode out-dir render-dir scale scores out-table pairs inputs psr-pur hit
                ants-tiebreaker ants-top-k ants-runs ants-ticks ants-out-dir ants-include-aif ants-no-termination
                ants-sigils-a ants-sigils-b]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      (and (= (or controller-a :wiring) :wiring) (nil? wiring-a)) (do
                                                                    (println "Missing --wiring-a for controller A.")
                                                                    (println)
                                                                    (println (usage)))
      (and (= (or controller-b :wiring) :wiring) (nil? wiring-b)) (do
                                                                    (println "Missing --wiring-b for controller B.")
                                                                    (println)
                                                                    (println (usage)))
      :else
      (let [ts (System/currentTimeMillis)
            n (int (or n 10))
            seed (long (or seed 4242))
            scale (int (or scale default-scale))
            out-dir (or out-dir (format "/tmp/cyber-mmca-wiring-ab-%d" ts))
            render-dir (or render-dir "futon5/resources/figures")
            scores (or scores (format "/tmp/cyber-mmca-wiring-ab-%d.csv" ts))
            out-table (or out-table (format "/tmp/cyber-mmca-wiring-ab-%d.org" ts))
            pairs (or pairs (format "/tmp/cyber-mmca-wiring-ab-%d.edn" ts))
            inputs (or inputs (format "/tmp/cyber-mmca-wiring-ab-%d-inputs.txt" ts))
            ants-out-dir (or ants-out-dir (format "/tmp/mission-0/ants-tiebreak-%d" ts))
            windows (int (or windows 12))
            W (int (or W 10))
            S (int (or S 10))
            length (int (or length 32))
            phenotype-length (or phenotype-length length)
            rng (java.util.Random. seed)
            hit-judgements (read-hit-judgements hit)
            seeds (if (seq seeds)
                    seeds
                    (mapv (fn [_] (rng-int rng Integer/MAX_VALUE)) (range n)))]
        (.mkdirs (io/file out-dir))
        (.mkdirs (io/file render-dir))
        (let [rows
              (vec
               (for [[idx run-seed] (map-indexed vector seeds)]
                 (let [common {:seed run-seed
                               :windows windows
                               :W W
                               :S S
                               :length length
                               :phenotype-length phenotype-length
                               :kernel (or kernel :mutating-template)
                               :sigil (or sigil ca/default-sigil)
                               :sigil-count (or sigil-count 16)
                               :allow-kernel-switch (boolean allow-kernel-switch)
                               :exotype-mode (or exotype-mode :inline)}
                       controller-a (or controller-a :wiring)
                       controller-b (or controller-b :wiring)
                       result-a (run-controller-full (assoc common
                                                            :controller controller-a
                                                            :wiring-path wiring-a
                                                            :wiring-index (or wiring-a-index 0)))
                       result-b (run-controller-full (assoc common
                                                            :controller controller-b
                                                            :wiring-path wiring-b
                                                            :wiring-index (or wiring-b-index 0)))
                       base-label (format "cyber-mmca-wiring-ab-%02d-seed-%d" (inc idx) run-seed)
                       label-a "cyber-mmca-wiring-ab-A"
                       label-b "cyber-mmca-wiring-ab-B"
                       run-a (assoc (:state result-a)
                                    :metrics-history (get-in result-a [:state :metrics-history])
                                    :gen-history (get-in result-a [:state :gen-history])
                                    :phe-history (get-in result-a [:state :phe-history]))
                       run-b (assoc (:state result-b)
                                    :metrics-history (get-in result-b [:state :metrics-history])
                                    :gen-history (get-in result-b [:state :gen-history])
                                    :phe-history (get-in result-b [:state :phe-history]))
                       path-a (run->edn! (assoc result-a
                                                :meta {:controller controller-a
                                                       :seed run-seed
                                                       :windows windows
                                                       :generations (* windows W)
                                                       :wiring-path wiring-a
                                                       :wiring-index (or wiring-a-index 0)
                                                       :label "A"})
                                       out-dir label-a (inc idx))
                       path-b (run->edn! (assoc result-b
                                                :meta {:controller controller-b
                                                       :seed run-seed
                                                       :windows windows
                                                       :generations (* windows W)
                                                       :wiring-path wiring-b
                                                       :wiring-index (or wiring-b-index 0)
                                                       :label "B"})
                                       out-dir label-b (inc idx))
                       ppm-a (str (io/file render-dir (str base-label "-A.ppm")))
                       png-a (str (io/file render-dir (str base-label "-A.png")))
                       ppm-b (str (io/file render-dir (str base-label "-B.ppm")))
                       png-b (str (io/file render-dir (str base-label "-B.png")))
                       score-a (score-run run-a)
                       score-b (score-run run-b)
                       wins (winners score-a score-b)
                       vote (votes wins)
                       vote-winner (if (> (get vote :a 0) (get vote :b 0)) :a
                                     (if (> (get vote :b 0) (get vote :a 0)) :b :tie))
                       ensemble-agreement (/ (double (apply max 0 (vals vote))) 5.0)
                       next-use (format "Prefer %s if ensemble vote aligns with HIT."
                                        (case vote-winner :a "A" :b "B" "tie"))
                       hit-label-a (get hit-judgements path-a)
                       hit-label-b (get hit-judgements path-b)
                       hit-winner (hit-winner hit-label-a hit-label-b)
                       hit-agreement (when hit-winner (= hit-winner vote-winner))
                       agreement {:ensemble ensemble-agreement
                                  :hit {:available? (boolean hit-winner)
                                        :winner hit-winner
                                        :matches-ensemble? (boolean hit-agreement)}}
                       base-sigil-a (or (get-in result-a [:state :exotype :sigil]) sigil ca/default-sigil)
                       base-sigil-b (or (get-in result-b [:state :exotype :sigil]) sigil ca/default-sigil)
                       sigils-a (or (seq ants-sigils-a)
                                    (sigil-stack result-a {:top-k ants-top-k :fallback base-sigil-a}))
                       sigils-b (or (seq ants-sigils-b)
                                    (sigil-stack result-b {:top-k ants-top-k :fallback base-sigil-b}))
                       ants-result (when (and ants-tiebreaker (= vote-winner :tie))
                                     (ants-tiebreak {:seed run-seed
                                                     :sigils-a sigils-a
                                                     :sigils-b sigils-b
                                                     :out-dir ants-out-dir
                                                     :runs ants-runs
                                                     :ticks ants-ticks
                                                     :include-aif ants-include-aif
                                                     :no-termination ants-no-termination}))
                       final-winner (or (:winner ants-result) vote-winner)]
                   (when psr-pur
                     (let [alternatives {:b {:wiring-path wiring-b
                                             :wiring-index (or wiring-b-index 0)}}]
                       (hex-log/append-entry! psr-pur (psr-entry {:side :a
                                                                :seed run-seed
                                                                :controller controller-a
                                                                :wiring-path wiring-a
                                                                :wiring-index (or wiring-a-index 0)
                                                                :wiring-actions wiring-actions
                                                                :windows windows
                                                                :W W
                                                                :S S
                                                                :length length
                                                                :phenotype-length phenotype-length
                                                                :kernel (or kernel :mutating-template)
                                                                :sigil (or sigil ca/default-sigil)
                                                                :exotype-mode (or exotype-mode :inline)
                                                                :run-path path-a
                                                                :alternatives alternatives}))
                       (hex-log/append-entry! psr-pur (psr-entry {:side :b
                                                                :seed run-seed
                                                                :controller controller-b
                                                                :wiring-path wiring-b
                                                                :wiring-index (or wiring-b-index 0)
                                                                :wiring-actions wiring-actions
                                                                :windows windows
                                                                :W W
                                                                :S S
                                                                :length length
                                                                :phenotype-length phenotype-length
                                                                :kernel (or kernel :mutating-template)
                                                                :sigil (or sigil ca/default-sigil)
                                                                :exotype-mode (or exotype-mode :inline)
                                                                :run-path path-b
                                                                :alternatives {:a {:wiring-path wiring-a
                                                                                   :wiring-index (or wiring-a-index 0)}}})))
                   (render-run! run-a ppm-a)
                   (write-png! ppm-a png-a)
                   (scale-png! png-a scale)
                   (render-run! run-b ppm-b)
                   (write-png! ppm-b png-b)
                   (scale-png! png-b scale)
                    (when psr-pur
                      (hex-log/append-entry! psr-pur (pur-entry {:side :a
                                                                :seed run-seed
                                                                :run-path path-a
                                                                :scores score-a
                                                                :vote-winner final-winner
                                                                :evidence {:scores score-a
                                                                           :vote vote
                                                                           :vote-winner vote-winner
                                                                           :ants ants-result}
                                                                :next-use next-use
                                                                :agreement agreement}))
                     (hex-log/append-entry! psr-pur (pur-entry {:side :b
                                                                :seed run-seed
                                                                :run-path path-b
                                                                :scores score-b
                                                                :vote-winner final-winner
                                                                :evidence {:scores score-b
                                                                           :vote vote
                                                                           :vote-winner vote-winner
                                                                           :ants ants-result}
                                                                :next-use next-use
                                                                :agreement agreement})))
                   {:seed run-seed
                    :path-a path-a
                    :path-b path-b
                    :image-a png-a
                    :image-b png-b
                    :controller-a controller-a
                    :controller-b controller-b
                    :scores-a score-a
                    :scores-b score-b
                    :wins wins
                    :vote vote
                    :vote-winner vote-winner
                    :ants ants-result
                    :final-winner final-winner}))))]
          (let [header (str/join "," ["seed"
                                      "controller_a" "controller_b"
                                      "short_a" "short_b"
                                      "envelope_a" "envelope_b"
                                      "triad_a" "triad_b"
                                      "shift_a" "shift_b"
                                      "filament_a" "filament_b"
                                      "vote_winner" "ants_winner" "final_winner"
                                      "ants_score_a" "ants_score_b"])
                lines (map (fn [{:keys [seed controller-a controller-b scores-a scores-b vote-winner ants final-winner]}]
                             (str/join "," [(str seed)
                                            (name controller-a)
                                            (name controller-b)
                                            (format "%.3f" (double (or (:short scores-a) 0.0)))
                                            (format "%.3f" (double (or (:short scores-b) 0.0)))
                                            (format "%.3f" (double (or (:envelope scores-a) 0.0)))
                                            (format "%.3f" (double (or (:envelope scores-b) 0.0)))
                                            (format "%.3f" (double (or (:triad scores-a) 0.0)))
                                            (format "%.3f" (double (or (:triad scores-b) 0.0)))
                                            (format "%.3f" (double (or (:shift scores-a) 0.0)))
                                            (format "%.3f" (double (or (:shift scores-b) 0.0)))
                                            (format "%.3f" (double (or (:filament scores-a) 0.0)))
                                            (format "%.3f" (double (or (:filament scores-b) 0.0)))
                                            (name vote-winner)
                                            (name (or (:winner ants) :na))
                                            (name final-winner)
                                            (format "%.3f" (double (or (:score-a ants) 0.0)))
                                            (format "%.3f" (double (or (:score-b ants) 0.0)))]))
                           rows)]
            (spit scores (str header "\n" (str/join "\n" lines) "\n")))
          (let [table-header "| seed | A | B | ctrl A | ctrl B | short A | short B | env A | env B | triad A | triad B | shift A | shift B | filament A | filament B | vote | ants | final |"
                table-sep "|-"
                table-lines (map (fn [{:keys [seed image-a image-b controller-a controller-b scores-a scores-b vote-winner ants final-winner]}]
                                   (format "| %d | [[file:%s]] | [[file:%s]] | %s | %s | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f | %.3f | %.3f | %s | %s | %s |"
                                           seed
                                           image-a
                                           image-b
                                           (name controller-a)
                                           (name controller-b)
                                           (double (or (:short scores-a) 0.0))
                                           (double (or (:short scores-b) 0.0))
                                           (double (or (:envelope scores-a) 0.0))
                                           (double (or (:envelope scores-b) 0.0))
                                           (double (or (:triad scores-a) 0.0))
                                           (double (or (:triad scores-b) 0.0))
                                           (double (or (:shift scores-a) 0.0))
                                           (double (or (:shift scores-b) 0.0))
                                           (double (or (:filament scores-a) 0.0))
                                           (double (or (:filament scores-b) 0.0))
                                           (name vote-winner)
                                           (name (or (:winner ants) :na))
                                           (name final-winner)))
                                 rows)
                table (str/join "\n" (concat [table-header table-sep] table-lines))]
            (spit out-table table))
          (spit pairs (pr-str (mapv (fn [{:keys [seed path-a path-b image-a image-b]}]
                                      {:seed seed
                                       :a {:path path-a :image image-a}
                                       :b {:path path-b :image image-b}})
                                    rows)))
          (spit inputs (str/join "\n" (mapcat (fn [{:keys [path-a path-b]}] [path-a path-b]) rows)))
          (println "Runs:" out-dir)
          (println "Scores:" scores)
          (println "Table:" out-table)
          (println "Pairs:" pairs)
          (println "Inputs:" inputs))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
