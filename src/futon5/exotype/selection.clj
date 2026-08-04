(ns futon5.exotype.selection
  "Local selection over heritable genotype rules.

   Fitness observes transient expressed-rule behaviour over a fixed window.
   Selection can copy only an immediate neighbour's heritable genotype; this
   namespace never applies an exotype or constructs an expressed rule."
  (:require [futon5.ca.core :as ca]))

(def selection-window-size
  "Fixed observation horizon inherited from the successful pre-build pilot."
  40)

(def preference-targets
  "The existing EFE C targets, shared with `futon5.exotype.efe/preferences`."
  {:rule-change 0.15 :hunger 0.05})

(defmulti fitness-values
  "Return one fitness value per cell from an accumulated observation window."
  (fn [fitness-kind _window] fitness-kind))

(defmethod fitness-values :preferences
  [_ {:keys [steps expressed-changes hunger]}]
  (mapv (fn [changes hungry]
          (- (+ (Math/abs (- (/ (double changes) steps)
                              (:rule-change preference-targets)))
                (Math/abs (- (/ (double hungry) steps)
                              (:hunger preference-targets))))))
        expressed-changes hunger))

(defmethod fitness-values :divergence
  [_ {:keys [steps divergence]}]
  (mapv #(/ (double %) steps) divergence))

(defn empty-window [width]
  {:steps 0
   :expressed-changes (vec (repeat width 0))
   :hunger (vec (repeat width 0))
   :divergence (vec (repeat width 0.0))})

(defn observe
  "Accumulate one synchronous observation of expressed behaviour."
  [window expressed previous-expressed phenotype]
  (let [width (count expressed)
        previous (or previous-expressed expressed)
        at #(mod % width)]
    (-> window
        (update :steps inc)
        (update :expressed-changes
                #(mapv + % (mapv (fn [now before]
                                   (if (= now before) 0 1))
                                 expressed previous)))
        (update :hunger
                #(mapv + %
                       (mapv (fn [index]
                               (if (and (= (nth expressed index)
                                           (nth previous index))
                                        (= (nth phenotype (at (dec index)))
                                           (nth phenotype index)
                                           (nth phenotype (at (inc index)))))
                                 1 0))
                             (range width))))
        (update :divergence
                #(mapv + %
                       (mapv (fn [index]
                               (/ (+ (if (= (nth phenotype index)
                                             (nth phenotype (at (dec index)))) 0 1)
                                     (if (= (nth phenotype index)
                                             (nth phenotype (at (inc index)))) 0 1))
                                  2.0))
                             (range width)))))))

(defn select-genotypes
  "Synchronously copy a uniformly selected immediate neighbour when its
   relative fitness wins a strength-weighted draw. Strength zero is identity."
  [genotypes fitness strength draw-seed]
  (when-not (<= 0.0 (double strength) 1.0)
    (throw (ex-info "selection strength must be in [0,1]"
                    {:selection-strength strength})))
  (if (zero? (double strength))
    genotypes
    (let [width (count genotypes)]
      (mapv
       (fn [index]
         (ca/with-seed (+ (long draw-seed) index)
           (let [neighbour-index (mod (+ index (if (< (ca/rnd) 0.5) -1 1)) width)
                 advantage (- (double (nth fitness neighbour-index))
                              (double (nth fitness index)))
                 probability (min 1.0 (* (double strength) (max 0.0 advantage)))]
             (if (< (ca/rnd) probability)
               (nth genotypes neighbour-index)
               (nth genotypes index)))))
       (range width)))))

(defn advance
  "Observe one step and, at the fixed window boundary, apply local selection.
   Returns the heritable genotype and reset/continued observation window."
  [{:keys [genotypes expressed previous-expressed phenotype window
           fitness-kind selection-strength draw-seed]}]
  (let [observed (observe (or window (empty-window (count genotypes)))
                          expressed previous-expressed phenotype)]
    (if (= selection-window-size (:steps observed))
      {:genotype (select-genotypes genotypes
                                   (fitness-values fitness-kind observed)
                                   selection-strength draw-seed)
       :window (empty-window (count genotypes))
       :selected? true}
      {:genotype genotypes :window observed :selected? false})))
