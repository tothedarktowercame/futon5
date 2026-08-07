(ns intrinsic-objective-controller
  "Offline-gated episodic controller for the intrinsic compressibility objective.

   `offline` never runs the simulator.  `episode` is a single, explicitly
   requested evaluation (not a closed loop) and measures the objective and its
   observables on the same fixed rows 0--250.

   The frozen observable surface predates the aligned instrument and covers
   rows 100--300.  Offline replay therefore requires an explicit
   `--legacy-window-lower-bound` acknowledgement and labels its result."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]))

(def ^:private gamma-min 1.0)
(def ^:private gamma-max 64.0)
(def ^:private kappa-min 0.0)
(def ^:private kappa-max 1.0)
(def ^:private aligned-row-count 250)
(def ^:private surrogate-gate 0.2)
(def ^:private random-replays-per-start 1000)
(def ^:private random-seed 90210)
(def ^:private absorbing-kinds #{:collapser :even1 :even4 :even8})

(defn- usage []
  (str
   "Usage:\n"
   "  clojure -M scripts/intrinsic_objective_controller.clj offline "
   "--objective PATH --observables PATH --legacy-window-lower-bound\n"
   "  clojure -M scripts/intrinsic_objective_controller.clj episode "
   "--gamma G --kappa K --seed N\n\n"
   "`offline` is simulator-free. `episode` performs exactly one aligned rows "
   "0--250 evaluation; this script intentionally has no live-loop command.\n"))

(defn- parse-options [args]
  (loop [remaining args
         options {}]
    (if (empty? remaining)
      options
      (let [[option & more] remaining]
        (if (= option "--legacy-window-lower-bound")
          (recur more (assoc options :legacy-window-lower-bound? true))
          (let [[value & rest-args] more]
            (when (or (nil? value) (str/starts-with? value "--"))
              (throw (ex-info (str option " requires a value") {:option option})))
            (recur rest-args
                   (assoc options
                          (case option
                            "--objective" :objective
                            "--observables" :observables
                            "--gamma" :gamma
                            "--kappa" :kappa
                            "--seed" :seed
                            (throw (ex-info (str "unknown option " option)
                                            {:option option})))
                          value))))))))

(defn- parse-number [label value]
  (try
    (Double/parseDouble value)
    (catch NumberFormatException _
      (throw (ex-info (str label " requires a number")
                      {:label label :value value})))))

(defn- parse-integer [label value]
  (try
    (Long/parseLong value)
    (catch NumberFormatException _
      (throw (ex-info (str label " requires an integer")
                      {:label label :value value})))))

(defn- bounded! [label value lower upper]
  (when-not (<= lower value upper)
    (throw (ex-info (format "%s must be in [%s,%s]" label lower upper)
                    {:label label :value value :bounds [lower upper]})))
  value)

(defn- mean [values]
  (when-not (seq values)
    (throw (ex-info "cannot average an empty collection" {})))
  (/ (reduce + 0.0 values) (double (count values))))

(defn- load-objective-api! []
  ;; The existing grid script is deliberately a command-line program and calls
  ;; its -main at EOF.  Load the unchanged definitions but not that one driver
  ;; form, so this apparatus reuses `checked-metaca-step` and `measure-sheet`
  ;; without accidentally launching `grid` or `validate-eca`.
  (let [path (io/file "scripts/local_compressibility_grid.clj")
        source (slurp path)
        driver-form "(apply -main *command-line-args*)"
        occurrences (count (re-seq (re-pattern (java.util.regex.Pattern/quote driver-form))
                                   source))]
    (when-not (= 1 occurrences)
      (throw (ex-info "objective script driver boundary changed; refusing to guess"
                      {:path (.getPath path) :driver-occurrences occurrences})))
    (load-string (str/replace source driver-form ""))
    (let [objective-ns (find-ns 'local-compressibility-grid)
          resolve-required
          (fn [symbol]
            (or (ns-resolve objective-ns symbol)
                (throw (ex-info "required objective API is absent"
                                {:symbol symbol :path (.getPath path)}))))]
      {:width @(resolve-required 'width)
       :burn-in @(resolve-required 'burn-in)
       :rows @(resolve-required 'sheet-rows)
       :patch @(resolve-required 'patch-size)
       :stride @(resolve-required 'patch-stride)
       :initial (resolve-required 'metaca-state)
       :step (resolve-required 'checked-metaca-step)
       :measure (resolve-required 'measure-sheet)})))

(defn- assert-objective-geometry! [{:keys [width burn-in rows patch stride] :as api}]
  (let [expected {:width 250 :burn-in 0 :rows 250 :patch 100 :stride 50}
        actual {:width width :burn-in burn-in :rows rows :patch patch :stride stride}]
    (when-not (= expected actual)
      (throw (ex-info "objective geometry changed; controller is not calibrated"
                      {:expected expected :actual actual}))))
  api)

(defn aligned-episode
  "Run one evaluation, using rows 0--250 for both observables and objective.

   This is intentionally not called by offline replay."
  [gamma kappa seed]
  (bounded! "gamma" gamma gamma-min gamma-max)
  (bounded! "kappa" kappa kappa-min kappa-max)
  (let [{:keys [width initial step measure]}
        (assert-objective-geometry! (load-objective-api!))]
    (loop [state (initial gamma kappa seed)
           row 0
           sheet []
           halting []
           changes []]
      (if (= row aligned-row-count)
        (merge {:gamma gamma
                :kappa kappa
                :seed seed
                :window [0 aligned-row-count]
                :halting (mean halting)
                :change (mean changes)}
               (measure sheet))
        (let [advanced (step state)
              halt (/ (double (count (filter absorbing-kinds (:exotypes advanced))))
                      width)
              change (/ (double (count (filter true?
                                                (map not= (:phenotype state)
                                                     (:phenotype advanced)))))
                        width)]
          (recur advanced (inc row)
                 (conj sheet (:phenotype advanced))
                 (conj halting halt)
                 (conj changes change)))))))

(defn- read-csv [path]
  (with-open [reader (io/reader path)]
    (let [[header & rows] (doall (line-seq reader))
          columns (mapv keyword (str/split header #","))]
      (mapv (fn [line]
              (zipmap columns (str/split line #"," -1)))
            (remove str/blank? rows)))))

(defn- cell [row]
  [(parse-number "gamma" (:gamma row))
   (parse-number "kappa" (:kappa row))])

(defn- means-by-cell [rows fields]
  (into (sorted-map)
        (for [[coordinate grouped] (group-by cell rows)]
          [coordinate
           (into {}
                 (for [field fields]
                   [field (mean (map #(parse-number (name field) (field %)) grouped))]))])))

(defn- parse-bridge-result [output]
  (let [extract-line
        (fn [label marker]
          (if-let [line (first (filter #(str/includes? % marker)
                                      (str/split-lines output)))]
            (if-let [match (re-find #"([+-]?[0-9]+(?:\.[0-9]+)?)\s*$" line)]
              (parse-number label (second match))
              (throw (ex-info "bridge result line has no trailing number"
                              {:label label :line line})))
            (throw (ex-info "bridge output format changed; refusing to guess"
                            {:missing label :output output}))))
        coefficients (re-find
                      #"coefficients: mid = ([+-]?[0-9.]+) ([+-][0-9.]+)\*halting ([+-][0-9.]+)\*change"
                      output)]
    (when-not coefficients
      (throw (ex-info "bridge coefficient format changed; refusing to guess"
                      {:output output})))
    {:held-out-r2 (extract-line "held-out R2" "LEAVE-ONE-OUT held-out R2")
     :in-sample-r2 (extract-line "in-sample R2" "in-sample R2 (NOT the gate")
     :intercept (parse-number "intercept" (nth coefficients 1))
     :halting-weight (parse-number "halting weight" (nth coefficients 2))
     :change-weight (parse-number "change weight" (nth coefficients 3))}))

(defn- bridge-gate! [objective observables]
  (let [{:keys [exit out err]}
        (shell/sh "python3" "scripts/bridge_test_objective.py" objective observables)]
    (when-not (zero? exit)
      (throw (ex-info "bridge test process failed"
                      {:exit exit :stdout out :stderr err})))
    (let [fit (parse-bridge-result out)]
      (when-not (> (:held-out-r2 fit) surrogate-gate)
        (throw (ex-info "held-out surrogate gate failed; controller is disabled"
                        {:threshold surrogate-gate :fit fit})))
      fit)))

(defn- predict [{:keys [intercept halting-weight change-weight]}
                 {:keys [halting change]}]
  (+ intercept (* halting-weight halting) (* change-weight change)))

(defn- grid-index [coordinates]
  (let [gammas (vec (sort (distinct (map first coordinates))))
        kappas (vec (sort (distinct (map second coordinates))))
        gamma-index (zipmap gammas (range))
        kappa-index (zipmap kappas (range))]
    {:gammas gammas
     :kappas kappas
     :indices (into {} (map (fn [[gamma kappa :as coordinate]]
                              [coordinate [(gamma-index gamma) (kappa-index kappa)]])
                            coordinates))}))

(defn- grid-distance [indices a b]
  (let [[ag ak] (indices a)
        [bg bk] (indices b)]
    (+ (abs (- ag bg)) (abs (- ak bk)))))

(defn- snap-to-grid [coordinates [proposed-gamma proposed-kappa]]
  (first
   (sort-by (fn [[gamma kappa]]
              [(+ (Math/pow (/ (- gamma proposed-gamma)
                               (- gamma-max gamma-min))
                            2.0)
                  (Math/pow (/ (- kappa proposed-kappa)
                               (- kappa-max kappa-min))
                            2.0))
               gamma
               kappa])
            coordinates)))

(defn propose-next
  "Propose the nearest unvisited grid point to the best surrogate-scored visit.

   Ties are deterministic: lower gamma, then lower kappa.  At an observable
   ridge the policy uses only the surrogate's small score difference (if any),
   adds no invented disambiguating signal, and leaves both cells explorable."
  [coordinates predictions history]
  (let [visited (set history)
        unvisited (remove visited coordinates)]
    (when (seq unvisited)
      (let [{:keys [indices]} (grid-index coordinates)
            best (first (sort-by (fn [[gamma kappa :as coordinate]]
                                   [(- (predictions coordinate)) gamma kappa])
                                 history))]
        (first (sort-by (fn [[gamma kappa :as coordinate]]
                          [(grid-distance indices best coordinate) gamma kappa])
                        unvisited))))))

(defn- quantile-threshold [values fraction]
  (let [ordered (vec (sort values))
        index (dec (long (Math/ceil (* fraction (count ordered)))))]
    (nth ordered index)))

(defn- policy-time [coordinates predictions high? start]
  (loop [current start
         history []
         episode 1]
    (let [history' (conj history current)]
      (cond
        (high? current) episode
        (= episode (count coordinates)) (inc episode)
        :else (recur (snap-to-grid
                      coordinates
                      (propose-next coordinates predictions history'))
                     history'
                     (inc episode))))))

(defn- neighbours [indices coordinates coordinate]
  (filterv #(= 1 (grid-distance indices coordinate %)) coordinates))

(defn- random-sampling-time
  "Episodes for random sampling WITHOUT replacement to reach a high cell.

  This, not `random-walk-time`, is the fair baseline.  An episodic controller may
  jump anywhere in (gamma, kappa) between episodes -- locality is a constraint the
  policy imposes on itself, not one the problem imposes.  A random WALK revisits
  cells and so cannot lose to any policy; against sampling the top-quartile margin
  reported by the first version of this script (3.31 vs 8.86) disappears entirely
  (sampling reaches a top-quartile cell in 3.28).  Added on review, claude-14."
  [coordinates high? seed]
  (let [random (java.util.Random. seed)
        shuffled (loop [remaining (vec coordinates) out []]
                   (if (empty? remaining)
                     out
                     (let [i (.nextInt random (count remaining))]
                       (recur (into (subvec remaining 0 i) (subvec remaining (inc i)))
                              (conj out (nth remaining i))))))]
    (or (first (keep-indexed (fn [i c] (when (high? c) (inc i))) shuffled))
        (inc (count coordinates)))))

(defn- random-walk-time
  "Local random walk with revisits.  RETAINED FOR COMPARISON ONLY -- it is not a
  fair baseline; see `random-sampling-time`."
  [coordinates high? start seed]
  (let [{:keys [indices]} (grid-index coordinates)
        random (java.util.Random. seed)
        horizon (count coordinates)]
    (loop [current start
           episode 1]
      (cond
        (high? current) episode
        (= episode horizon) (inc horizon)
        :else (let [options (neighbours indices coordinates current)]
                (recur (nth options (.nextInt random (count options)))
                       (inc episode)))))))

(defn- collision-pairs [observables]
  (let [coordinates (vec (keys observables))]
    (vec
     (for [left-index (range (count coordinates))
           right-index (range (inc left-index) (count coordinates))
           :let [left (nth coordinates left-index)
                 right (nth coordinates right-index)
                 left-observation (observables left)
                 right-observation (observables right)
                 delta-h (- (:halting left-observation) (:halting right-observation))
                 delta-c (- (:change left-observation) (:change right-observation))
                 distance (Math/sqrt (+ (* delta-h delta-h) (* delta-c delta-c)))]
           :when (< distance 0.02)]
       {:left left :right right :distance distance}))))

(defn- offline-replay! [{:keys [objective observables legacy-window-lower-bound?]}]
  (when-not (and objective observables)
    (throw (ex-info "offline requires --objective and --observables" {})))
  (when-not legacy-window-lower-bound?
    (throw (ex-info
            (str "the frozen observable surface is rows 100--300, not aligned rows 0--250; "
                 "pass --legacy-window-lower-bound to acknowledge this offline limitation")
            {:objective-window [0 250] :observable-window [100 300]})))
  (let [fit (bridge-gate! objective observables)
        objective-cells (means-by-cell (read-csv objective) [:mid_range])
        observable-cells (means-by-cell (read-csv observables) [:halting :change])
        coordinates (vec (sort (filter #(contains? observable-cells %)
                                       (keys objective-cells))))
        actual (into {} (map (fn [coordinate]
                               [coordinate (:mid_range (objective-cells coordinate))])
                             coordinates))
        predictions (into {} (map (fn [coordinate]
                                    [coordinate (predict fit (observable-cells coordinate))])
                                  coordinates))
        threshold (quantile-threshold (vals actual) 0.75)
        high? #(>= (actual %) threshold)
        policy-times (mapv #(policy-time coordinates predictions high? %) coordinates)
        random-times
        (vec
         (for [[start-index start] (map-indexed vector coordinates)
               replay (range random-replays-per-start)]
           (random-walk-time coordinates high? start
                             (+ random-seed (* start-index random-replays-per-start)
                                replay))))
        sampling-times
        (vec (for [replay (range (* (count coordinates) random-replays-per-start))]
               (random-sampling-time coordinates high? (+ random-seed replay))))
        top3 (set (take-last 3 (sort-by actual (keys actual))))
        top3? #(contains? top3 %)
        top3-policy-times (mapv #(policy-time coordinates predictions top3? %) coordinates)
        top3-sampling-times
        (vec (for [replay (range (* (count coordinates) random-replays-per-start))]
               (random-sampling-time coordinates top3? (+ random-seed replay))))
        horizon (count coordinates)
        collisions (collision-pairs observable-cells)]
    (println "INTRINSIC OBJECTIVE CONTROLLER — OFFLINE REPLAY")
    (println "data-window: LEGACY LOWER BOUND (objective 0--250; observables 100--300)")
    (printf "cells: %d; surrogate held-out R2: %.4f; in-sample R2: %.4f; gate: > %.1f PASS%n"
            (count coordinates) (:held-out-r2 fit) (:in-sample-r2 fit) surrogate-gate)
    (printf "fit: mid = %.3f %+.3f*halting %+.3f*change%n"
            (:intercept fit) (:halting-weight fit) (:change-weight fit))
    (printf "high-cell threshold (top quartile): %.6f%n" threshold)
    (printf "policy mean episodes to high cell: %.4f (max %d)%n"
            (mean policy-times) (apply max policy-times))
    (printf "random-SAMPLING mean episodes to high cell: %.4f   <- the fair baseline%n"
            (mean sampling-times))
    (printf "random-walk mean episodes to high cell: %.4f; hit by %d: %.2f%% (NOT a fair baseline)%n"
            (mean random-times) horizon
            (* 100.0 (/ (count (filter #(<= % horizon) random-times))
                        (double (count random-times)))))
    ;; Top quartile is 10 of 35 cells, so random search wins in ~3.3 episodes and no
    ;; policy has room to show anything.  Top-3 is where the surrogate's value is
    ;; visible: policy ~4.66 vs sampling ~9.01.  Report both.
    (printf "TOP-3 criterion -- policy %.4f vs random sampling %.4f%n"
            (mean top3-policy-times) (mean top3-sampling-times))
    (printf "observable collisions within 0.02: %d of %d pairs%n"
            (count collisions) (/ (* (count coordinates) (dec (count coordinates))) 2))
    (println "policy: expand from the best visited surrogate score to its nearest unvisited cell; ties use lower gamma then lower kappa.")
    (println "ridge: the policy uses only the surrogate's small score difference, invents no extra distinction, and leaves both colliding cells explorable; exact ties use lower gamma then kappa.")
    {:fit fit
     :policy-mean (mean policy-times)
     :random-mean (mean random-times)
     :collisions collisions}))

(defn -main [& args]
  (let [[command & option-args] args]
    (try
      (case command
        "offline" (offline-replay! (parse-options option-args))
        "episode" (let [{:keys [gamma kappa seed]} (parse-options option-args)]
                    (when-not (and gamma kappa seed)
                      (throw (ex-info "episode requires --gamma, --kappa, and --seed" {})))
                    (prn (aligned-episode (parse-number "gamma" gamma)
                                          (parse-number "kappa" kappa)
                                          (parse-integer "seed" seed))))
        (throw (ex-info "an explicit offline or episode command is required"
                        {:command command})))
      (catch clojure.lang.ExceptionInfo error
        (binding [*out* *err*]
          (println "ERROR:" (ex-message error))
          (when-let [data (seq (ex-data error))]
            (println "DATA:" (into {} data)))
          (println (usage)))
        (System/exit 2)))))

(try
  (apply -main *command-line-args*)
  (finally
    (shutdown-agents)))
