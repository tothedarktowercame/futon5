(ns futon5.mmca.xenotype
  "Xenotypes: evaluators of exotypes over full run histories."
  (:require [futon5.ca.core :as ca]
            [futon5.mmca.metrics :as metrics]))

(def ^:private default-spec
  {:weights {:edge 0.7 :diversity 0.3}
   :targets {:entropy [0.6 0.35]
             :change [0.2 0.2]
             :autocorr [0.6 0.3]
             :diversity [0.4 0.3]}
   :penalties {:stasis-after 6
               :confetti-change 0.45
               :confetti-entropy 0.8
               :dead-change 0.05
               :dead-entropy 0.2}})

(defn normalize-spec [spec]
  (let [spec (merge default-spec (or spec {}))
        weights (:weights spec)
        total (double (reduce + 0.0 (vals weights)))
        weights (if (pos? total)
                  (into {} (map (fn [[k v]] [k (/ (double v) total)]) weights))
                  (:weights default-spec))]
    (assoc spec :weights weights)))

(defn- clamp-01 [x]
  (cond
    (not (number? x)) 0.0
    (< x 0.0) 0.0
    (> x 1.0) 1.0
    :else (double x)))

(defn- band-score [x center width]
  (when (and (number? x) (number? center) (pos? (double width)))
    (clamp-01 (- 1.0 (/ (Math/abs (- (double x) (double center))) (double width))))))

(defn- stasis-penalty [gen-history stasis-after]
  (if-let [step (ca/first-stasis-step gen-history)]
    (if (<= step (long (or stasis-after 0)))
      (clamp-01 (/ (double step) (double (max 1 stasis-after))))
      1.0)
    1.0))

(defn- confetti-penalty [{:keys [avg-change avg-entropy-n]} {:keys [confetti-change confetti-entropy]}]
  (if (and (number? avg-change) (number? avg-entropy-n)
           (>= avg-change (double (or confetti-change 0.0)))
           (>= avg-entropy-n (double (or confetti-entropy 0.0))))
    0.6
    1.0))

(defn- dead-penalty [{:keys [avg-change avg-entropy-n]} {:keys [dead-change dead-entropy]}]
  (if (and (number? avg-change) (number? avg-entropy-n)
           (<= avg-change (double (or dead-change 0.0)))
           (<= avg-entropy-n (double (or dead-entropy 0.0))))
    0.6
    1.0))

(defn score-run
  "Score a run with a xenotype spec (0-1)."
  [xeno result]
  (let [{:keys [weights targets penalties]} (normalize-spec xeno)
        summary (metrics/summarize-run result)
        {:keys [avg-entropy-n avg-change avg-unique temporal-autocorr phe-entropy phe-change]} summary
        [entropy-center entropy-width] (get targets :entropy)
        [change-center change-width] (get targets :change)
        [auto-center auto-width] (get targets :autocorr)
        [div-center div-width] (get targets :diversity)
        entropy-score (band-score avg-entropy-n entropy-center entropy-width)
        change-score (band-score avg-change change-center change-width)
        autocorr-score (band-score temporal-autocorr auto-center auto-width)
        diversity-score (band-score avg-unique div-center div-width)
        phe-entropy-score (when (number? phe-entropy)
                            (band-score (clamp-01 phe-entropy) entropy-center entropy-width))
        phe-change-score (when (number? phe-change)
                           (band-score (clamp-01 phe-change) change-center change-width))
        edge-scores (remove nil? [entropy-score change-score autocorr-score phe-entropy-score phe-change-score])
        edge-score (when (seq edge-scores)
                     (/ (reduce + 0.0 edge-scores) (double (count edge-scores))))
        edge-score (or edge-score 0.0)
        diversity-score (or diversity-score 0.0)
        base (+ (* (double (or (:edge weights) 0.0)) edge-score)
                (* (double (or (:diversity weights) 0.0)) diversity-score))
        stasis (stasis-penalty (:gen-history result) (:stasis-after penalties))
        confetti (confetti-penalty summary penalties)
        dead (dead-penalty summary penalties)
        score (-> base (* stasis confetti dead) clamp-01)]
    {:score score
     :components {:edge edge-score
                  :diversity diversity-score
                  :stasis stasis
                  :confetti confetti
                  :dead dead}}))

(defn- centered-score [x]
  (when (number? x)
    (clamp-01 (- 1.0 (* 2.0 (Math/abs (- (double x) 0.5)))))))

(defn- variance [xs]
  (let [xs (seq xs)]
    (when xs
      (let [mean (/ (reduce + 0.0 xs) (double (count xs)))]
        (/ (reduce + 0.0 (map (fn [x] (let [d (- (double x) mean)] (* d d))) xs))
           (double (count xs)))))))

(defn fitness
  "Fitness for a xenotype based on its score distribution over runs."
  [xeno results]
  (let [scores (mapv (comp :score #(score-run xeno %)) results)
        mean (metrics/avg scores)
        var (variance scores)
        var-n (if (number? var) (clamp-01 (/ (double var) 0.25)) 0.0)
        mean-score (or (centered-score mean) 0.0)
        fitness (+ (* 0.6 var-n) (* 0.4 mean-score))]
    {:fitness fitness
     :mean mean
     :variance var
     :count (count scores)}))

(defn random-spec
  [^java.util.Random rng]
  (let [pick (fn [lo hi] (+ lo (* (.nextDouble rng) (- hi lo))))
        spec {:weights {:edge (pick 0.4 0.9)
                        :diversity (pick 0.1 0.6)}
              :targets {:entropy [(pick 0.45 0.7) (pick 0.2 0.4)]
                        :change [(pick 0.1 0.3) (pick 0.15 0.3)]
                        :autocorr [(pick 0.45 0.75) (pick 0.2 0.4)]
                        :diversity [(pick 0.25 0.55) (pick 0.2 0.4)]}
              :penalties (:penalties default-spec)}]
    (normalize-spec spec)))

(defn mutate
  [^java.util.Random rng xeno]
  (let [{:keys [weights targets]} (normalize-spec xeno)
        jitter (fn [x amt]
                 (clamp-01 (+ (double x)
                              (* (- (.nextDouble rng) 0.5) 2.0 (double amt)))))
        tweak-target (fn [[center width]]
                       [(jitter center 0.08)
                        (max 0.1 (jitter width 0.08))])
        weights (-> weights
                    (update :edge jitter 0.1)
                    (update :diversity jitter 0.1))
        targets (-> targets
                    (update :entropy tweak-target)
                    (update :change tweak-target)
                    (update :autocorr tweak-target)
                    (update :diversity tweak-target))]
    (normalize-spec (assoc xeno :weights weights :targets targets))))
