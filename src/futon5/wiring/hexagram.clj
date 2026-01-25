(ns futon5.wiring.hexagram
  "Generate wiring diagrams from I Ching hexagrams.

   Each hexagram's 6 lines (yin/yang) determine wiring structure:
   - Lower trigram (lines 1-3): input processing & first transform
   - Upper trigram (lines 4-6): aggregation & output processing

   Yang (solid) lines → active/creative components (XOR, diversity)
   Yin (broken) lines → receptive/stable components (AND, majority)"
  (:require [futon5.hexagram.lines :as lines]
            [futon5.mmca.iching :as iching]))

;;; ============================================================
;;; Component Palettes
;;; ============================================================

;; Each line position has a yang (active) and yin (passive) choice
(def ^:private line-components
  {;; Line 1 (bottom): Which neighbors to use
   1 {:yang [:context-pred :context-succ]     ;; Both neighbors (expansive)
      :yin  [:context-self]}                   ;; Self only (receptive)

   ;; Line 2: First operation
   2 {:yang :bit-xor                          ;; XOR (creative change)
      :yin  :bit-and}                          ;; AND (conservative)

   ;; Line 3: Second operation / aggregation
   3 {:yang :bit-or                           ;; OR (expansive)
      :yin  :bit-not}                          ;; NOT (inversion)

   ;; Line 4: Third operation
   4 {:yang :bit-xor                          ;; Another XOR (more change)
      :yin  :bit-and}                          ;; Another AND (more stability)

   ;; Line 5: Modulation
   5 {:yang :majority                         ;; Majority vote (democratic)
      :yin  :blend}                            ;; Weighted blend (smooth)

   ;; Line 6 (top): Final transform
   6 {:yang :bit-xor                          ;; Final XOR (dynamic output)
      :yin  :bit-or}})                         ;; Final OR (accumulative)

(defn- line-choice [line-value position]
  (let [palette (get line-components position)]
    (if (= line-value :yang)
      (:yang palette)
      (:yin palette))))

;;; ============================================================
;;; Wiring Generation
;;; ============================================================

(defn- make-node [id component & [params]]
  (cond-> {:id id :component component}
    params (assoc :params params)))

(defn hexagram->wiring
  "Generate a wiring diagram from a hexagram number (1-64).

   The 6 lines determine the wiring structure:
   - Lines 1-3 (lower trigram): input selection and initial processing
   - Lines 4-6 (upper trigram): aggregation and output"
  [hexagram-number]
  (let [hex-lines (lines/hexagram-number->lines hexagram-number)
        hex-info (lines/hexagram-number->hexagram hexagram-number)

        ;; Decode each line's choice
        l1 (line-choice (nth hex-lines 0) 1)
        l2 (line-choice (nth hex-lines 1) 2)
        l3 (line-choice (nth hex-lines 2) 3)
        l4 (line-choice (nth hex-lines 3) 4)
        l5 (line-choice (nth hex-lines 4) 5)
        l6 (line-choice (nth hex-lines 5) 6)

        ;; Build nodes based on lines
        ;; Line 1: context inputs
        input-nodes (if (vector? l1)
                      [(make-node :ctx-a (first l1))
                       (make-node :ctx-b (second l1))]
                      [(make-node :ctx-a l1)
                       (make-node :ctx-b :context-self)])

        ;; Always include self for reference
        self-node (make-node :ctx-self :context-self)

        ;; Line 2: first binary op on inputs
        op1-node (make-node :op1 l2)

        ;; Line 3: applied to result + self
        op2-node (if (= l3 :bit-not)
                   (make-node :op2 :bit-not)
                   (make-node :op2 l3))

        ;; Line 4: another transform
        op3-node (make-node :op3 l4)

        ;; Line 5: aggregation (if applicable)
        op4-node (when (#{:majority :blend} l5)
                   (make-node :op4 l5))

        ;; Line 6: final op before output
        op5-node (make-node :op5 l6)

        ;; Output
        output-node (make-node :output :output-sigil)

        ;; Assemble nodes
        all-nodes (filterv some?
                           (concat input-nodes
                                   [self-node op1-node op2-node op3-node]
                                   [op4-node op5-node output-node]))

        ;; Build edges based on structure
        edges
        (cond->
            [;; Line 1 inputs → Line 2 op
             {:from :ctx-a :to :op1 :to-port :a}
             {:from :ctx-b :to :op1 :to-port :b}

             ;; Line 2 result → Line 3
             (if (= l3 :bit-not)
               {:from :op1 :to :op2}
               {:from :op1 :to :op2 :to-port :a})

             ;; Self → Line 3 (if binary op)
             (when (not= l3 :bit-not)
               {:from :ctx-self :to :op2 :to-port :b})

             ;; Line 3 → Line 4
             {:from :op2 :to :op3 :to-port :a}
             {:from :ctx-self :to :op3 :to-port :b}

             ;; Line 4 → Line 5/6
             (if op4-node
               {:from :op3 :to :op4 :to-port :sigils}
               {:from :op3 :to :op5 :to-port :a})

             ;; If Line 5 aggregation, connect to Line 6
             (when op4-node
               {:from :op4 :to :op5 :to-port :a})

             ;; Self → Line 6 (for binary ops)
             {:from :ctx-self :to :op5 :to-port :b}

             ;; Line 6 → output
             {:from :op5 :to :output}]

          ;; Filter out nils
          true (filterv some?))]

    {:meta {:id (keyword (str "hex-" hexagram-number))
            :hexagram-number hexagram-number
            :hexagram-name (:name hex-info)
            :lines hex-lines
            :description (str "Hexagram " hexagram-number ": " (:name hex-info))}
     :diagram {:nodes all-nodes
               :edges edges
               :output :output}}))

;;; ============================================================
;;; Simpler Alternative: Direct Line Mapping
;;; ============================================================

(defn hexagram->simple-wiring
  "Generate a simpler wiring based on hexagram lines.

   Uses a 3-stage structure:
   - Stage 1: Input selection (lines 1-2)
   - Stage 2: Core operation (lines 3-4)
   - Stage 3: Output (lines 5-6)

   Each pair of lines selects one of 4 component variants."
  [hexagram-number]
  (let [hex-lines (lines/hexagram-number->lines hexagram-number)
        hex-info (lines/hexagram-number->hexagram hexagram-number)

        ;; Convert line pairs to 2-bit indices
        pair->idx (fn [l1 l2]
                    (+ (if (= l1 :yang) 2 0)
                       (if (= l2 :yang) 1 0)))

        ;; Component choices for each stage (4 options each)
        ;; Avoid bit-not and majority for simpler port handling
        stage1-opts [:context-self :context-pred :context-succ :context-self]
        stage2-opts [:bit-and :bit-or :bit-xor :bit-and]
        stage3-opts [:bit-and :bit-or :bit-xor :bit-or]

        ;; Select components
        input-comp (nth stage1-opts (pair->idx (nth hex-lines 0) (nth hex-lines 1)))
        core-comp (nth stage2-opts (pair->idx (nth hex-lines 2) (nth hex-lines 3)))
        output-comp (nth stage3-opts (pair->idx (nth hex-lines 4) (nth hex-lines 5)))

        ;; Build wiring - all binary ops use :a/:b ports, output :result
        nodes [{:id :ctx-main :component input-comp}
               {:id :ctx-pred :component :context-pred}
               {:id :ctx-succ :component :context-succ}
               {:id :core :component core-comp}
               {:id :final :component output-comp}
               {:id :output :component :output-sigil}]

        ;; All components here are binary ops with :a/:b inputs and :result output
        ;; output-sigil expects :sigil input
        edges [{:from :ctx-pred :to :core :to-port :a}
               {:from :ctx-succ :to :core :to-port :b}
               {:from :core :to :final :to-port :a :from-port :result}
               {:from :ctx-main :to :final :to-port :b}
               {:from :final :to :output :to-port :sigil :from-port :result}]]

    {:meta {:id (keyword (str "hex-" hexagram-number))
            :hexagram-number hexagram-number
            :hexagram-name (:name hex-info)
            :lines hex-lines
            :formula (str (name input-comp) " → " (name core-comp) " → " (name output-comp))}
     :diagram {:nodes (vec nodes)
               :edges (vec edges)
               :output :output}}))

;;; ============================================================
;;; Batch Generation
;;; ============================================================

(defn generate-all-hexagram-wirings
  "Generate wirings for all 64 hexagrams."
  ([] (generate-all-hexagram-wirings :simple))
  ([style]
   (let [gen-fn (case style
                  :simple hexagram->simple-wiring
                  :full hexagram->wiring
                  hexagram->simple-wiring)]
     (into {}
           (for [n (range 1 65)]
             [(keyword (str "hex-" n)) (gen-fn n)])))))

(defn hexagram-catalog
  "Generate a catalog of all hexagram wirings with metadata."
  []
  (for [n (range 1 65)]
    (let [hex-info (lines/hexagram-number->hexagram n)
          wiring (hexagram->simple-wiring n)]
      {:number n
       :name (:name hex-info)
       :lines (:lines hex-info)
       :formula (get-in wiring [:meta :formula])
       :wiring wiring})))
