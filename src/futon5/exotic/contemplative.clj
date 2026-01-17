(ns futon5.exotic.contemplative
  "Provenance and worthiness scoring placeholders.")

(defn provenance-score
  "Placeholder provenance score based on presence of history."
  [history]
  (if (seq history) 1.0 0.0))

(defn worthiness-score
  "Placeholder worthiness score based on method tag."
  [_score method]
  (case method
    :earned 1.0
    :hacked 0.0
    0.5))

(defn inheritance-check
  "Placeholder inheritance check based on descendant scores."
  [descendants]
  (let [scores (keep :score descendants)
        total (count scores)]
    (if (pos? total)
      (/ (reduce + 0.0 scores) (double total))
      0.0)))
