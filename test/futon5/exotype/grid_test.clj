(ns futon5.exotype.grid-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.exotype.grid :as grid]))

(deftest fixed-and-local-transmission-policies
  (let [exotypes [:builder :builder :chaos :identity]
        phenotype "0001"]
    (is (= exotypes (grid/transmit :uniform-fixed exotypes phenotype)))
    (is (= exotypes (grid/transmit :heterogeneous-fixed exotypes phenotype)))
    (is (= exotypes
           (grid/transmit :conformist exotypes phenotype)))
    (is (= exotypes
           (grid/transmit :boring-triggered exotypes phenotype)))))

(deftest every-arm-is-selectable-and-deterministic
  (doseq [arm grid/arms]
    (testing (name arm)
      (let [state {:arm arm :genotype (vec (repeat 8 "一"))
                   :phenotype "01010101"
                   :exotypes (vec (repeat 8 :identity))}]
        (is (= (grid/step state) (grid/step state)))))))

(deftest vocabulary-is-neighbourhood-converted
  (is (= #{:builder :collapser :chaos :identity}
         (set (keys grid/propagators))))
  (let [neighbourhoods #{"000" "001" "010" "100" "011" "101" "110" "111"}]
    (is (every? #(and (= neighbourhoods (set (keys %)))
                      (= neighbourhoods (set (vals %))))
                (vals grid/propagators)))))
