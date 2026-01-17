(ns futon5.arrow.term
  "Term grammar and application for arrow discovery."
  (:require [futon5.mmca.exotype :as exotype]))

(defn- clamp-01 [x]
  (cond
    (not (number? x)) 0.0
    (< x 0.0) 0.0
    (> x 1.0) 1.0
    :else (double x)))

(defn count-atoms
  "Count atomic patches in a term."
  [term]
  (cond
    (and (vector? term) (= :seq (first term)))
    (reduce + 0 (map count-atoms (rest term)))
    (vector? term) 1
    :else 0))

(defn classify-term
  "Classify a term for optional word-class penalties."
  [term]
  (cond
    (and (vector? term) (= :seq (first term))) :seq
    (vector? term) (first term)
    :else :unknown))

(defn cost
  "Compute term cost from atom count and word-class penalty."
  [term word-class-weights]
  (let [atoms (count-atoms term)
        wc (classify-term term)
        penalty (get word-class-weights wc 0)]
    (+ atoms penalty)))

(defn- update-exotype-param [exo k delta]
  (let [params (or (:params exo) {})
        curr (double (or (get params k) 0.0))
        next (clamp-01 (+ curr (double delta)))]
    (assoc exo :params (assoc params k next))))

(defn apply-term
  "Apply a term to a run config map."
  [state term]
  (cond
    (and (vector? term) (= :seq (first term)))
    (reduce apply-term state (rest term))

    (vector? term)
    (let [[op & args] term]
      (case op
        :set-exotype
        (let [[sigil tier] args]
          (assoc state :exotype (exotype/resolve-exotype {:sigil sigil
                                                         :tier (or tier :local)})))

        :set-tier
        (let [[tier] args]
          (update state :exotype #(assoc (exotype/resolve-exotype %) :tier tier)))

        :set-param
        (let [[param delta] args]
          (update state :exotype #(update-exotype-param (exotype/resolve-exotype %) param delta)))

        state))

    :else state))

(defn generate-candidates
  "Generate term candidates up to a max cost k."
  [{:keys [sigils]} k]
  (let [atoms (vec (concat
               (map (fn [sigil] [:set-exotype sigil :local]) sigils)
               (map (fn [sigil] [:set-exotype sigil :super]) sigils)
               [[:set-param :update-prob -0.25]
                [:set-param :update-prob 0.25]
                [:set-param :match-threshold -0.1]
                [:set-param :match-threshold 0.1]
                [:set-param :mix-shift -1]
                [:set-param :mix-shift 1]
                [:set-param :rotation -1]
                [:set-param :rotation 1]
                [:set-tier :local]
                [:set-tier :super]]))
        seqs (when (>= k 2)
               (for [a (take 6 atoms)
                     b (take 6 atoms)]
                 [:seq a b]))]
    (->> (concat atoms seqs)
         (filter #(<= (count-atoms %) k))
         vec)))
