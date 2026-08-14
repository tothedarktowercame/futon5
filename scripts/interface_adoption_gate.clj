;; GATE — does adoption on realized local interface beat a yoked blind control?
;;
;; Fable design-2 arm (e): every dwell steps, a cell compares its realized
;; windowed local interface against a band and may copy a neighbour's operator.
;; No forward model exists, so the ~20-step actuation lag cannot be got wrong;
;; the leverage is consumed at its native timescale.
;;
;; The three arms share ONE decision stream: the same cells wake at the same
;; steps and consider the SAME neighbour. Only the fitness differs. That is what
;; makes :yoked a control on the observable rather than on churn.
;;
;;   :none     no adoption (baseline; :heterogeneous-fixed)
;;   :band     fitness = -(|windowed local interface - TARGET|)
;;   :yoked    identical decisions, fitness vector SPATIALLY PERMUTED --
;;             same values, same magnitudes, attached to the wrong cells
;;   :activity fitness = -(|windowed local change-rate - TARGET|)   <- wrong-observable
;;
;; Parameters fixed before running (see PREREG-interface-abundance for the
;; discipline). T-WINDOW=10 because the structural test found short windows
;; discriminate best (P=0.797 at T=10, falling to 0.574 at T=160). DWELL is
;; jittered per cell in [15,25] because a fixed dwell near the breather period
;; (w+1=16) can entrain the degenerate limit cycle Fable identified.
(require '[futon5.ca.core :as ca] '[futon5.exotype.grid :as grid])

(def WIDTH 120)
(def STEPS 1200)
(def SETTLE 15)          ; w, the settling window
(def T-WINDOW 10)        ; realized-outcome averaging window
(def DWELL-LO 15)
(def DWELL-HI 25)
(def TARGET 0.20)        ; interior band centre, inside the observed operator range
(def MUT 0.02)           ; epsilon mutation on adoption
(def kinds (vec (keys grid/propagators)))

;; The first run of this gate was DEGENERATE: from generic random initialisation
;; every arm coexisted 8/8, including :none, so there was no headroom to measure
;; into. Fable's design asks for a start the uncontrolled system does not escape.
;;
;; No single operator absorbs at this width and duration (measured: the most
;; settling, :collapser, reaches only 0.497), so a collapsing start cannot be had
;; by operator choice. Absorption in the sheet experiments came from the
;; SELF-TUNING arm's beta/kappa, which this harness does not use. What is
;; available, and is the honest analogue, is a near-frozen START: a mostly
;; uniform phenotype under settling-biased operators. The controller must then
;; GENERATE interface rather than merely fail to lose it.
(def FROZEN-LEANING [:collapser :even1 :fix2 :even8])
(def UNIFORM-FRACTION 0.92)   ; share of cells starting on the same phenotype value

(defn seeded-phenotype [width]
  (apply str (repeatedly width #(if (< (ca/rnd) UNIFORM-FRACTION) 0 1))))

(defn settled? [phes t i]
  (and (>= t SETTLE)
       (every? #(= (get-in phes [% i]) (get-in phes [(dec %) i]))
               (range (- t (dec SETTLE)) (inc t)))))

(defn iface-at [phes t i w]
  (let [me (settled? phes t i)]
    (if (or (zero? i) (= i (dec w))) 0
      (if (or (not= me (settled? phes t (mod (dec i) w)))
              (not= me (settled? phes t (mod (inc i) w)))) 1 0))))

(defn windowed [phes t i f]
  (let [lo (max SETTLE (- t (dec T-WINDOW)))
        ts (range lo (inc t))]
    (if (empty? ts) 0.0
      (/ (double (reduce + (map #(f phes % i) ts))) (count ts)))))

(defn change-at [phes t i] (if (= (get-in phes [t i]) (get-in phes [(dec t) i])) 0 1))

(defn run-arm [arm seed]
  (let [st (ca/with-seed seed
             {:arm :heterogeneous-fixed :seed seed :time 0
              :exotypes (vec (repeatedly WIDTH #(ca/rnd-nth FROZEN-LEANING)))
              :genotype (vec (ca/random-sigil-string WIDTH))
              :phenotype (seeded-phenotype WIDTH)})
        ;; the SHARED decision stream: per-cell dwell offsets and periods, and
        ;; the neighbour each cell will consider. Identical across arms because
        ;; it is derived from `seed` alone, never from the arm.
        dwell (vec (for [i (range WIDTH)]
                     (ca/with-mixed-seed (+ seed (* 7919 i))
                       (+ DWELL-LO (ca/rnd-int (inc (- DWELL-HI DWELL-LO)))))))
        offset (vec (for [i (range WIDTH)]
                      (ca/with-mixed-seed (+ seed 13 (* 104729 i)) (ca/rnd-int DWELL-HI))))]
    (loop [s st t 0 phes []]
      (if (= t STEPS)
        {:phe phes :exo (:exotypes s)}
        (let [phes' (conj phes (vec (:phenotype s)))
              due (when (>= t (+ SETTLE T-WINDOW))
                    (vec (for [i (range WIDTH)]
                           (zero? (mod (- t (nth offset i)) (nth dwell i))))))
              fit (when (and due (some true? due) (not= arm :none))
                    (let [raw (vec (for [i (range WIDTH)]
                                     (case arm
                                       (:band :yoked)
                                       (- (Math/abs (- (windowed phes' t i (fn [p tt ii] (iface-at p tt ii WIDTH))) TARGET)))
                                       :activity
                                       (- (Math/abs (- (windowed phes' t i change-at) TARGET))))))]
                      (if (= arm :yoked)
                        ;; same values, same magnitudes, WRONG cells
                        (vec (ca/with-mixed-seed (+ seed 777 t) (ca/rnd-shuffle raw)))
                        raw)))
              exo' (if (nil? fit)
                     (:exotypes s)
                     (vec (for [i (range WIDTH)]
                            (if-not (nth due i)
                              (nth (:exotypes s) i)
                              (ca/with-mixed-seed (+ seed 31 (* 1000 t) i)
                                (let [j (mod (+ i (if (< (ca/rnd) 0.5) -1 1)) WIDTH)]
                                  (cond
                                    (< (ca/rnd) MUT) (ca/rnd-nth kinds)
                                    (> (nth fit j) (nth fit i)) (nth (:exotypes s) j)
                                    :else (nth (:exotypes s) i))))))))
              nx (grid/step (assoc s :exotypes exo' :arm :heterogeneous-fixed))]
          (recur nx (inc t) phes'))))))

(defn summarise [{:keys [phe]}]
  (let [n (count phe)
        settled-frac (fn [t] (/ (double (count (filter #(settled? phe t %) (range WIDTH)))) WIDTH))
        iface-frac (fn [t] (/ (double (reduce + (map #(iface-at phe t % WIDTH) (range WIDTH)))) WIDTH))
        late (range (- n 200) n)
        absorbed? (every? #(= (nth phe %) (nth phe (dec %))) (range (- n 100) n))]
    {:S (/ (reduce + (map iface-frac late)) (count late))
     :settled (/ (reduce + (map settled-frac late)) (count late))
     :absorbed absorbed?}))

(println "arm       seed        S    settled  absorbed")
(let [results (atom {})]
  (doseq [arm [:none :band :yoked :activity]
          seed (range 101 125)]
    (let [r (summarise (run-arm arm seed))]
      (swap! results update arm (fnil conj []) r)
      (printf "%-9s %-5d %8.4f %8.3f %9s%n" arm seed (:S r) (:settled r) (:absorbed r))
      (flush)))
  (println)
  (println "arm        mean S   mean settled   coexisting (not absorbed, 0.02<=settled<=0.98)")
  (doseq [arm [:none :band :yoked :activity]]
    (let [rs (get @results arm)
          co (count (filter #(and (not (:absorbed %)) (<= 0.02 (:settled %) 0.98)) rs))]
      (printf "%-9s %9.4f %13.3f %14d / %d%n" arm
              (/ (reduce + (map :S rs)) (count rs))
              (/ (reduce + (map :settled rs)) (count rs))
              co (count rs))))
  (println)
  ;; PAIRED comparisons -- the arms share seeds AND the decision stream, so the
  ;; per-seed difference is the unit of evidence, not the arm mean.
  (doseq [[a b] [[:band :yoked] [:band :activity] [:band :none]]]
    (let [xs (map :S (get @results a)) ys (map :S (get @results b))
          ds (map - xs ys)
          n (count ds)
          m (/ (reduce + ds) n)
          sd (Math/sqrt (/ (reduce + (map #(let [d (- % m)] (* d d)) ds)) (max 1 (dec n))))
          se (/ sd (Math/sqrt n))
          wins (count (filter pos? ds))]
      (printf "%-8s vs %-9s  mean diff %+.4f  se %.4f  t %+.2f  %d/%d seeds favour %s%n"
              (name a) (name b) m se (if (zero? se) 0.0 (/ m se)) wins n (name a)))))
