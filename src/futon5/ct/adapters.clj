(ns futon5.ct.adapters
  "Registry of reusable string-diagram adaptors for FUTON5 data shapes."
  (:require [futon5.ct.dsl :as ct]))

(defonce registry
  (atom {}))

(defn register-adapter!
  "Register an adaptor for a given data-type and role (:split, :merge, etc.)."
  [data-type role adaptor]
  (swap! registry assoc-in [data-type role] adaptor)
  adaptor)

(defn adaptor
  "Lookup an adaptor map for the given data-type and role."
  [data-type role]
  (get-in @registry [data-type role]))

(defn available-types []
  (keys @registry))

(defn adapter-names []
  (->> @registry
       vals
       (mapcat vals)
       (map :name)
       set))

(defn adapters-for [data-type]
  (vals (get @registry data-type)))

(defn- make-split [name input outputs]
  {:name name
   :role :split
   :input input
   :outputs outputs
   :diagram (ct/primitive-diagram {:name name
                                   :domain [input]
                                   :codomain outputs})
   :description (format "Split %s into %s" input outputs)})

(defn- make-merge [name inputs output]
  {:name name
   :role :merge
   :inputs inputs
   :output output
   :diagram (ct/primitive-diagram {:name name
                                   :domain inputs
                                   :codomain [output]})
   :description (format "Merge %s into %s" inputs output)})

;; Default adaptors -----------------------------------------------------------

(def rule-bits-type :rule/bits)
(def metrics-type :metrics/vector)
(def grid-row-type :grid/row)

(register-adapter! rule-bits-type :split-half
                   (make-split :rule/split-half rule-bits-type
                               [:rule/bits-left :rule/bits-right]))

(register-adapter! rule-bits-type :merge-half
                   (make-merge :rule/merge-half
                               [:rule/bits-left :rule/bits-right]
                               rule-bits-type))

(register-adapter! metrics-type :split-even-odd
                   (make-split :metrics/split-even-odd metrics-type
                               [:metrics/even :metrics/odd]))

(register-adapter! metrics-type :merge-even-odd
                   (make-merge :metrics/merge-even-odd
                               [:metrics/even :metrics/odd]
                               metrics-type))

(register-adapter! grid-row-type :split-front-back
                   (make-split :grid/split-front-back grid-row-type
                               [:grid/front :grid/back]))

(register-adapter! grid-row-type :merge-front-back
                   (make-merge :grid/merge-front-back
                               [:grid/front :grid/back]
                               grid-row-type))
