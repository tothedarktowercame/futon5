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
        (cond->>
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
  "Generate a wiring based on hexagram lines.

   Uses a 3-stage structure with 64 unique wirings:
   - Stage 1: Input topology (lines 1-2) - which contexts feed where
   - Stage 2: Core operation (lines 3-4) - first binary operation
   - Stage 3: Output operation (lines 5-6) - second binary operation

   Each pair of lines selects one of 4 variants (4×4×4 = 64 unique)."
  [hexagram-number]
  (let [hex-lines (lines/hexagram-number->lines hexagram-number)
        hex-info (lines/hexagram-number->hexagram hexagram-number)

        ;; Convert line pairs to 2-bit indices
        pair->idx (fn [l1 l2]
                    (+ (if (= l1 :yang) 2 0)
                       (if (= l2 :yang) 1 0)))

        ;; Stage indices
        topo-idx (pair->idx (nth hex-lines 0) (nth hex-lines 1))
        core-idx (pair->idx (nth hex-lines 2) (nth hex-lines 3))
        output-idx (pair->idx (nth hex-lines 4) (nth hex-lines 5))

        ;; Stage 1: Input topology - determines which contexts feed core vs final
        ;; 4 distinct routing patterns:
        ;;   0: pred+succ→core, self→final  (neighbors combine, self modulates)
        ;;   1: self+succ→core, pred→final  (self+right combine, left modulates)
        ;;   2: pred+self→core, succ→final  (left+self combine, right modulates)
        ;;   3: self+self→core, pred→final  (self-interaction, left modulates - identity-like)
        topo-names ["nei" "suc" "pre" "slf"]
        [core-a core-b final-mod]
        (case topo-idx
          0 [:ctx-pred :ctx-succ :ctx-self]   ;; neighbors → core, self modulates
          1 [:ctx-self :ctx-succ :ctx-pred]   ;; self+succ → core, pred modulates
          2 [:ctx-pred :ctx-self :ctx-succ]   ;; pred+self → core, succ modulates
          3 [:ctx-self :ctx-self :ctx-pred])  ;; self+self → core (tends to identity)

        ;; Stage 2 & 3: Operations (4 distinct each)
        stage2-opts [:bit-and :bit-or :bit-xor :bit-nand]
        stage3-opts [:bit-and :bit-or :bit-xor :bit-nor]

        core-comp (nth stage2-opts core-idx)
        output-comp (nth stage3-opts output-idx)

        ;; Build nodes
        nodes [{:id :ctx-self :component :context-self}
               {:id :ctx-pred :component :context-pred}
               {:id :ctx-succ :component :context-succ}
               {:id :core :component core-comp}
               {:id :final :component output-comp}
               {:id :output :component :output-sigil}]

        ;; Build edges based on topology
        edges [{:from core-a :to :core :to-port :a}
               {:from core-b :to :core :to-port :b}
               {:from :core :to :final :to-port :a :from-port :result}
               {:from final-mod :to :final :to-port :b}
               {:from :final :to :output :to-port :sigil :from-port :result}]

        ;; Formula includes topology for uniqueness
        formula (str (nth topo-names topo-idx) ":" (name core-comp) "→" (name output-comp))]

    {:meta {:id (keyword (str "hex-" hexagram-number))
            :hexagram-number hexagram-number
            :hexagram-name (:name hex-info)
            :lines hex-lines
            :topology topo-idx
            :formula formula}
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
