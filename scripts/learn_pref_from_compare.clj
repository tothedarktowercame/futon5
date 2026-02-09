(ns learn-pref-from-compare
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.scripts.output :as out]))

(defn- usage []
  (str/join
   "\n"
   ["Learn preference weights from Mission 17a compare table."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/learn_pref_from_compare.clj --table PATH --labels PATH [options]"
    ""
    "Options:"
    "  --table PATH     Compare table org file."
    "  --labels PATH    EDN labels map (seed -> {:label :baseline/:exotic/:tie})."
    "  --out PATH       Output weights EDN (default /tmp/mission-17a-pref-weights.edn)."
    "  --epochs N       Training epochs (default 500)."
    "  --lr P           Learning rate (default 0.1)."
    "  --eps P          Tie margin for reporting (default 0.1)."
    "  --help           Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-double [s]
  (try (Double/parseDouble s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--table" flag)
          (recur (rest more) (assoc opts :table (first more)))

          (= "--labels" flag)
          (recur (rest more) (assoc opts :labels (first more)))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          (= "--epochs" flag)
          (recur (rest more) (assoc opts :epochs (parse-int (first more))))

          (= "--lr" flag)
          (recur (rest more) (assoc opts :lr (parse-double (first more))))

          (= "--eps" flag)
          (recur (rest more) (assoc opts :eps (parse-double (first more))))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- parse-table [path]
  (let [lines (->> (slurp path) str/split-lines (map str/trim) (remove str/blank?))
        rows (drop 2 lines)]
    (mapv (fn [line]
            (let [parts (map str/trim (str/split (subs line 1 (dec (count line))) #"\|"))
                  seed (Long/parseLong (nth parts 0))
                  vals (mapv #(Double/parseDouble %) (subvec (vec parts) 1))]
              {:seed seed
               :short-b (nth vals 0) :short-e (nth vals 1)
               :env-b (nth vals 2) :env-e (nth vals 3)
               :triad-b (nth vals 4) :triad-e (nth vals 5)
               :shift-b (nth vals 6) :shift-e (nth vals 7)
               :fil-b (nth vals 8) :fil-e (nth vals 9)}))
          rows)))

(defn- deltas [row]
  [(double (- (:short-e row) (:short-b row)))
   (double (- (:env-e row) (:env-b row)))
   (double (- (:triad-e row) (:triad-b row)))
   (double (- (:shift-e row) (:shift-b row)))
   (double (- (:fil-e row) (:fil-b row)))])

(defn- mean [xs]
  (if (seq xs) (/ (reduce + 0.0 xs) (double (count xs))) 0.0))

(defn- stddev [xs]
  (let [m (mean xs)
        var (mean (map (fn [x] (let [d (- x m)] (* d d))) xs))]
    (Math/sqrt (double var))))

(defn- standardize [xs]
  (let [m (mean xs)
        s (stddev xs)]
    (if (pos? s)
      (mapv (fn [x] (/ (- x m) s)) xs)
      (mapv (fn [_] 0.0) xs))))

(defn- sigmoid [x]
  (/ 1.0 (+ 1.0 (Math/exp (- (double x))))))

(defn- dot [a b]
  (reduce + 0.0 (map * a b)))

(defn- train-logreg
  [xs ys epochs lr]
  (let [n (count xs)
        d (count (first xs))]
    (loop [w (vec (repeat d 0.0))
           b 0.0
           i 0]
      (if (>= i epochs)
        {:w w :b b}
        (let [preds (map (fn [x] (sigmoid (+ (dot w x) b))) xs)
              grad-w (mapv (fn [j]
                             (/ (reduce + 0.0
                                        (map (fn [x y p] (* (- p y) (nth x j)))
                                             xs ys preds))
                                (double n)))
                           (range d))
              grad-b (/ (reduce + 0.0 (map - preds ys)) (double n))
              w' (mapv (fn [wj gw] (- (double wj) (* lr (double gw)))) w grad-w)
              b' (- b (* lr grad-b))]
          (recur w' b' (inc i)))))))

(defn- predict [model x]
  (sigmoid (+ (dot (:w model) x) (:b model))))

(defn- label->y [label]
  (case label :exotic 1.0 :baseline 0.0 nil))

(defn- confusion [pairs eps]
  (reduce (fn [acc {:keys [label score]}]
            (let [pred (cond
                         (< (Math/abs (- score 0.5)) eps) :tie
                         (> score 0.5) :exotic
                         :else :baseline)]
              (update acc [label pred] (fnil inc 0))))
          {}
          pairs))

(defn- loocv [xs ys epochs lr]
  (let [n (count xs)
        d (count (first xs))]
    (loop [i 0
           preds []]
      (if (>= i n)
        preds
        (let [train-x (vec (concat (subvec xs 0 i) (subvec xs (inc i))))
              train-y (vec (concat (subvec ys 0 i) (subvec ys (inc i))))
              model (train-logreg train-x train-y epochs lr)
              score (predict model (nth xs i))]
          (recur (inc i) (conj preds score)))))))

(defn- accuracy [scores ys]
  (let [pairs (map (fn [s y] {:score s :label (if (= y 1.0) :exotic :baseline)}) scores ys)
        correct (count (filter (fn [{:keys [score label]}]
                                 (= label (if (> score 0.5) :exotic :baseline)))
                               pairs))]
    {:correct correct
     :total (count ys)}))

(defn -main [& args]
  (let [{:keys [help unknown table labels out epochs lr eps]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      (or (nil? table) (nil? labels)) (do (println "Missing --table or --labels") (println) (println (usage)))
      :else
      (let [rows (parse-table table)
            label-map (edn/read-string (slurp labels))
            labeled (->> rows
                         (map (fn [row]
                                (let [label (get-in label-map [(:seed row) :label])]
                                  (assoc row :label label))))
                         (filter (fn [row] (#{:baseline :exotic} (:label row)))))
            xs (mapv deltas labeled)
            ys (mapv (comp label->y :label) labeled)
            cols (apply map vector xs)
            means (mapv mean cols)
            stds (mapv stddev cols)
            xs-n (mapv (fn [x]
                         (mapv (fn [v m s]
                                 (if (pos? s) (/ (- v m) s) 0.0))
                               x means stds))
                       xs)
            epochs (int (or epochs 500))
            lr (double (or lr 0.1))
            eps (double (or eps 0.1))
            model (train-logreg xs-n ys epochs lr)
            out (or out "/tmp/mission-17a-pref-weights.edn")
            preds (mapv (fn [row x]
                          (let [score (predict model x)]
                            {:seed (:seed row)
                             :label (:label row)
                             :score score}))
                        labeled xs-n)
            conf (confusion preds eps)
            cv-scores (loocv xs-n ys epochs lr)
            cv-acc (accuracy cv-scores ys)]
        (out/spit-text! out (pr-str {:features [:delta-short :delta-envelope :delta-triad :delta-shift :delta-filament]
                                     :means means
                                     :stds stds
                                     :model model
                                     :epochs epochs
                                     :lr lr
                                     :eps eps}))
        (println "Model weights:" (:w model))
        (println "Bias:" (:b model))
        (println "LOOCV accuracy:" (:correct cv-acc) "/" (:total cv-acc))
        (println "Confusion (eps tie):" conf)
        (println "Predictions:")
        (doseq [{:keys [seed label score]} preds]
          (println (format "seed %d | label %s | p(exotic)=%.3f" (long seed) (name label) (double score))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
