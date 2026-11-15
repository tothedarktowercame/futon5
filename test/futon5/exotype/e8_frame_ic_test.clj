(ns futon5.exotype.e8-frame-ic-test
  "P-008b §4 items 6-8 (exotype origin/main 8538186): the E8-2 initial
  condition places the 12 drawn maps exactly as the keyword arms place the
  12 kinds, for the same seed (:heterogeneous-fixed, grid.clj:490-502,
  one ca/with-seed scope, one ca/rnd-nth per cell).
  Registration: exotype LEDGER c1/e8frame fec2f62, before any code.

  Notes (registered item 5, stated not fixed):
  - The IC CONSTRUCTION needs no grid/*allow-end8?* binding: it is pure
    data (a menu of frame maps, one rnd-nth per cell); no validation or
    transition runs here. The binding is needed from the FIRST transmit on
    (the frame's non-permutation maps hit the End(8) guards in
    scoring/transition paths).
  - Nothing at t = 0 (IC construction) touches the predictor: scores are
    computed at the first cell-decision, i.e. the first transmit -- that is
    where the not-frozen pooled s8-predictor would be required."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as st]))

(def ^:private width 250)

(def ^:private frame
  (st/frame-maps (edn/read-string (slurp "results/p008/e8-frame-300.edn"))))

(defn- e8-2-grid
  "The E8-2 initial grid: menu = (frame-draw frame seed) in slot order;
  cells assigned exactly as initial-grid's :heterogeneous-fixed assigns
  them (one ca/with-seed scope, one ca/rnd-nth per cell over the 12-entry
  menu). No src change to grid/initial-grid is needed or made."
  [seed]
  (ca/with-seed seed
    (let [menu (st/frame-draw frame seed)]
      (vec (repeatedly width #(ca/rnd-nth menu))))))

(defn- keyword-grid
  "The keyword-arm initial grid for the SAME seed: the real
  grid/initial-grid :heterogeneous-fixed path, in the same scope shape."
  [seed]
  (ca/with-seed seed
    (grid/initial-grid :heterogeneous-fixed width)))

(def ^:private kind-index
  (into {} (map-indexed (fn [i k] [k i]) grid/exotype-kinds)))

;; --- helper validation ---------------------------------------------------------

(deftest frame-maps-validates
  (testing "the real frame file yields 299 valid End(8) maps"
    (is (= 299 (count frame)))
    (is (every? #(let [v (:vector %)]
                   (and (vector? v) (= 8 (count v))
                        (every? (fn [x] (<= 0 x 7)) v)))
                frame)))
  (testing "wrong counts and invalid vectors throw"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #"exactly 299"
         (st/frame-maps {:entries (conj (vec frame) (first frame))})))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #"exactly 299"
         (st/frame-maps {:entries (vec (butlast frame))})))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #"not a valid End\(8\) vector"
         (st/frame-maps {:entries (assoc (vec frame) 0 {:source :stratified
                                                        :vector [0 1 2 3 4 5 6 8]})})))))

;; --- IC placement correspondence (registered gate) -----------------------------

(defn- correspond?
  "Cell-by-cell: keyword cell = kind at exotype-kinds index k <=> e8 cell =
  menu entry at slot k."
  [kw-grid e8-grid menu]
  (every? (fn [i]
            (let [k (get kind-index (nth kw-grid i))
                  slot-map (nth menu k)]
              (= (nth e8-grid i) slot-map)))
          (range width)))

(deftest ic-placement-matches-keyword-arms
  (doseq [seed [2026115400 2026115401 2026115402 2026115403 2026115404]]
    (testing (str "seed " seed)
      (let [menu (st/frame-draw frame seed)
            kw (keyword-grid seed)
            e8 (e8-2-grid seed)]
        (is (= width (count kw)) "keyword grid width 250")
        (is (= width (count e8)) "E8-2 grid width 250")
        (is (every? keyword? kw) "keyword grid holds the 12 kinds")
        (is (every? (set menu) e8) "E8-2 grid holds only the 12 drawn maps")
        (is (correspond? kw e8 menu)
            "every cell: kind k <=> slot k for the same seed")))))

;; --- bad case: a permuted slot order FAILS the correspondence -------------------

(deftest permuted-menu-fails-correspondence
  (let [seed 2026115400
        menu (st/frame-draw frame seed)
        kw (keyword-grid seed)
        e8 (e8-2-grid seed)
        ;; slots 0 and 1 swapped: what an implementation that permutes the
        ;; slot order would place
        permuted (assoc (assoc (vec menu) 0 (nth menu 1)) 1 (nth menu 0))]
    (testing "the CANONICAL correspondence (kind k <=> slot k of the true
              menu) fails against the permuted placement"
      (is (not (correspond? kw e8 permuted))
          "cells that drew kind slot 0 or 1 now hold the wrong map"))
    (testing "a grid BUILT with the permuted menu also fails the canonical
              correspondence, and differs positionally from the true grid"
      (let [e8-permuted (ca/with-seed seed
                          (vec (repeatedly width #(ca/rnd-nth permuted))))]
        (is (not (correspond? kw e8-permuted menu)))
        (is (not= e8-permuted e8)
            "the slot order is load-bearing: same cell draws, different grid")))))
