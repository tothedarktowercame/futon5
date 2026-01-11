(ns futon5.ii-v-i
  "Quick-win ii-V-I tab generator for 5-string banjo."
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]))

(def tuning [60 43 48 64 57])

(def shape-strings [1 2 3 4])
(def bass-string-index 1)
(def rendered-string-indices [1 2 3 4])

(def superset-allowed-shapes #{:deltaFive})
(def superset-penalty 3)
(def shape-bonus {:deltaFive -2})

;; Offsets derived from resources/chicago.tex (x=string, y=fret offset).
(def chicago-shapes
  {:majOneA {:offsets [-0 -2 -3 -2]
             :qualities #{:min :dim :maj}}
   :majOne {:offsets [-2 -1 0 0]
            :qualities #{:min :dim :maj :sus2 :sus4}}
   :majFive {:offsets [-2 -2 -2 0]
             :qualities #{:min :maj :min7 :maj7 :sus2 :sus4 :aug}}
   :majFiveB {:offsets [0 0 0 -3]
              :qualities #{:min :dim :maj :min7 :sus2 :sus4 :aug}}
   :majThree {:offsets [-3 -1 -2 -1]
              :qualities #{:min :dim :add9 :maj :maj7 :dom7 :sus2 :sus4}}
   :minOne {:offsets [-2 0 0 0]
            :qualities #{:min :dim :add9 :maj :min7 :maj7 :dom7 :sus2 :sus4 :aug}}
   :minFive {:offsets [-2 -2 -1 0]
             :qualities #{:min :dim :add9 :maj :sus2 :sus4}}
   :minThree {:offsets [-3 -2 -3 -1]
              :qualities #{:min :dim :add9 :maj :min7 :sus2 :sus4}}
   :deltaFive {:offsets [0 0 0 -1]
               :qualities #{:min :dim :maj :min7 :maj7 :dom7 :sus2 :sus4 :aug}}
   :deltaOne {:offsets [0 -2 -1 -2]
              :qualities #{:min :add9 :maj :min7 :sus2 :sus4}}
   :deltaSeven {:offsets [0 -1 0 0]
                :qualities #{:min :dim :maj :min7 :maj7 :sus2 :sus4}}
   :deltaThree {:offsets [-1 -2 0 -2]
                :qualities #{:min :dim}}
   :deltaStrange {:offsets [-2 -1 0 2]
                  :qualities #{:min :dim :maj :sus2 :sus4}}
   ;; Derived for dom7 on strings 1-4: base 0 -> G7 (G B D F).
   :deltaDom7 {:offsets [0 2 1 2]
               :qualities #{:dom7}}
   ;; Extras: exploratory shapes for later experiments.
   :majBarre {:offsets [0 0 0 0]
              :qualities #{:min :dim :add9 :maj :min7 :maj7 :dom7 :sus2 :sus4 :aug}}
   :minBarre {:offsets [0 0 -1 0]
              :qualities #{:min :dim :add9 :maj :min7 :maj7 :dom7 :sus2 :sus4 :aug}}
   :susTwo {:offsets [-2 0 2 0]
            :qualities #{:dim}}
   :susFour {:offsets [-2 0 1 0]
             :qualities #{:min :dim :add9 :maj :sus2 :sus4}}
   :dimTriad {:offsets [-2 -1 -2 -1]
              :qualities #{:min :dim :add9 :maj :maj7 :dom7 :sus2 :sus4}}
   :augTriad {:offsets [-2 -1 -1 0]
              :qualities #{:min :dim :maj :dom7 :aug}}
   :addNine {:offsets [-2 -1 2 0]
             :qualities #{}}})

(def circle-of-fourths
  [["C" 0] ["F" 5] ["Bb" 10] ["Eb" 3] ["Ab" 8] ["Db" 1]
   ["Gb" 6] ["B" 11] ["E" 4] ["A" 9] ["D" 2] ["G" 7]])

(def note->pc
  {"C" 0 "C#" 1 "Db" 1 "D" 2 "D#" 3 "Eb" 3 "E" 4 "F" 5 "F#" 6 "Gb" 6
   "G" 7 "G#" 8 "Ab" 8 "A" 9 "A#" 10 "Bb" 10 "B" 11})

(def pc->name ["C" "C#" "D" "Eb" "E" "F" "F#" "G" "Ab" "A" "Bb" "B"])

(def intervals
  {:maj [0 4 7]
   :min [0 3 7]
   :sus2 [0 2 7]
   :sus4 [0 5 7]
   :dim [0 3 6]
   :aug [0 4 8]
   :add9 [0 4 7 14]
   :dom7 [0 4 7 10]
   :min7 [0 3 7 10]
   :maj7 [0 4 7 11]})

(def drone-string-index 0)

(defn pc [midi]
  (mod midi 12))

(defn frets->pitches [open-midi frets]
  (mapv (fn [open fret]
          (when (number? fret)
            (+ open fret)))
        open-midi
        frets))

(defn- select-indices [coll indices]
  (mapv (fn [idx] (nth coll idx)) indices))

(defn- rendered-frets [frets]
  (select-indices (vec frets) rendered-string-indices))

(defn pitches->pcs [pitches]
  (into #{} (keep (fn [p] (when (number? p) (pc p))) pitches)))

(defn midi->name [midi]
  (let [name (nth pc->name (pc midi))
        octave (dec (quot midi 12))]
    (str name octave)))

(defn chord-pcs [root quality]
  (into #{} (map (fn [i] (mod (+ root i) 12)) (get intervals quality))))

(defn chord-label [root-name quality]
  (case quality
    :maj root-name
    :min (str root-name "m")
    :dim (str root-name "dim")
    :aug (str root-name "aug")
    :dom7 (str root-name "7")
    :min7 (str root-name "m7")
    :maj7 (str root-name "maj7")
    root-name))

(def interval->suffix
  {1 "b9"
   2 "add9"
   3 "#9"
   5 "add11"
   6 "#11"
   8 "b13"
   9 "6"
   10 "7"
   11 "maj7"})

(def interval->degree
  {0 "1"
   1 "b9"
   2 "2"
   3 "b3"
   4 "3"
   5 "4"
   6 "b5"
   7 "5"
   8 "b13"
   9 "6"
   10 "b7"
   11 "7"})

(def grip-fingerings
  {"5-1-b3-5" [3 4 2 1]
   "1-5-b7-3" [1 3 2 4]
   "5-1-3-1" [1 1 1 4]
   "b3-5-1-b3" [3 2 4 1]
   "5-1-3-5" [2 3 4 1]
   "b7-3-5-1" [1 2 1 1]
   "1-b3-5-1" [3 1 1 1]
   "3-b7-1-5" [2 3 1 4]})

(declare chord-label-for-candidate)

(defn- drone-pc []
  (pc (nth tuning drone-string-index)))

(defn- drone-interval [root]
  (mod (- (drone-pc) root) 12))

(defn- allow-open-drone? [root pcs]
  (or (contains? pcs (drone-pc))
      (contains? interval->suffix (drone-interval root))))

(defn- with-open-drone [chord]
  (let [frets (:frets chord)
        pcs (pitches->pcs (frets->pitches tuning frets))
        root (:root chord)]
    (if (allow-open-drone? root pcs)
      (-> chord
          (assoc :frets (assoc frets drone-string-index 0)
                 :label (chord-label-for-candidate root (:quality chord) (conj pcs (drone-pc)))
                 :include-drone? true))
      chord)))
(defn- label-with-extras [root-name quality extras]
  (let [base (chord-label root-name quality)
        suffixes (->> extras
                      (map #(get interval->suffix % "add?"))
                      distinct
                      (sort)
                      (str/join "+"))]
    (if (seq suffixes)
      (str base suffixes)
      base)))

(defn- chord-label-for-candidate [root quality pcs]
  (let [root-name (nth pc->name root)
        base-pcs (chord-pcs root quality)
        extras (->> (set/difference pcs base-pcs)
                    (map (fn [p] (mod (- p root) 12)))
                    set)]
    (label-with-extras root-name quality extras)))

(defn- grip-name [root frets]
  (let [rendered-frets (rendered-frets frets)
        rendered-open (select-indices (vec tuning) rendered-string-indices)
        rendered-pitches (frets->pitches rendered-open rendered-frets)]
    (->> rendered-pitches
         (map (fn [p]
                (if p
                  (get interval->degree (mod (- (pc p) root) 12) "?")
                  "x")))
         (str/join "-"))))

(defn- fingering-cost [rendered-frets fingering]
  (let [fretted (keep (fn [f] (when (pos? (or f 0)) f)) rendered-frets)
        base (if (seq fretted) (apply min fretted) 0)
        span (if (seq fretted) (- (apply max fretted) (apply min fretted)) 0)
        span-weight (cond
                      (<= base 2) 3
                      (<= base 5) 2
                      :else 1)
        span-penalty (* span-weight span)
        nil-penalty (count (filter nil? rendered-frets))
        per-note (reduce + 0
                         (for [[fret finger] (map vector rendered-frets fingering)
                               :when (and (pos? (or fret 0)) (number? finger))]
                           (let [target (+ base (dec finger))
                                 deviation (Math/abs ^long (- fret target))
                                 stretch (cond
                                           (< fret base) (* 2 (- base fret))
                                           (> fret (+ base 3)) (* 2 (- fret (+ base 3)))
                                           :else 0)]
                             (+ deviation stretch))))
        finger-frets (reduce (fn [m [fret finger]]
                               (if (pos? (or fret 0))
                                 (update m finger (fnil conj []) fret)
                                 m))
                             {}
                             (map vector rendered-frets fingering))
        missing-finger? (some (fn [[fret finger]]
                                (and (pos? (or fret 0)) (not (number? finger))))
                              (map vector rendered-frets fingering))
        invalid? (some (fn [[_ frets]]
                         (and (seq frets) (not= 1 (count (set frets)))))
                       finger-frets)
        barre-bonus (->> finger-frets
                         (map (fn [[_ frets]]
                                (max 0 (dec (count frets)))))
                         (reduce + 0)
                         (min 2))]
    (if (or invalid? missing-finger?)
      999
      (max 0 (- (+ span-penalty per-note nil-penalty) barre-bonus)))))

(defn- infer-fingering [rendered-frets]
  (let [opts (mapv (fn [f] (if (pos? (or f 0)) [1 2 3 4] [nil])) rendered-frets)
        combos (for [f1 (nth opts 0)
                     f2 (nth opts 1)
                     f3 (nth opts 2)
                     f4 (nth opts 3)]
                 [f1 f2 f3 f4])
        best (apply min-key :cost
                    (for [fing combos
                          :let [cost (fingering-cost rendered-frets fing)]
                          :when (< cost 999)]
                      {:fingering fing :cost cost}))]
    (if best
      {:fingering (:fingering best) :fingering-cost (:cost best)}
      {:fingering [nil nil nil nil] :fingering-cost 0})))

(defn- assign-fingering* [grip rendered-frets]
  (let [explicit (get grip-fingerings grip)
        explicit-fingering (when explicit
                             (mapv (fn [fret finger]
                                     (when (pos? (or fret 0)) finger))
                                   rendered-frets
                                   explicit))]
    (if explicit-fingering
      {:fingering explicit-fingering
       :fingering-cost (fingering-cost rendered-frets explicit-fingering)}
      (infer-fingering rendered-frets))))

(def assign-fingering (memoize assign-fingering*))

(defn- quality-label [quality]
  (case quality
    :maj "maj"
    :min "min"
    :dom7 "dom7"
    :min7 "min7"
    :maj7 "maj7"
    (name (or quality :unknown))))

(defn- chord-qualities-for-root [root pcs]
  (->> (keys intervals)
       (filter (fn [q] (set/subset? (chord-pcs root q) pcs)))
       set))

(defn- span [frets]
  (let [vals (keep (fn [f] (when (number? f) f)) frets)]
    (if (empty? vals) 0 (- (apply max vals) (apply min vals)))))

(defn- valid-frets? [{:keys [max-fret max-span]} frets]
  (and (every? (fn [f]
                 (or (nil? f)
                     (and (integer? f)
                          (<= 0 f max-fret))))
               frets)
       (<= (span frets) max-span)))

(defn- static-cost [frets]
  (+ (* 2 (span frets))
     (count (filter nil? frets))
     (if (pos? (or (nth frets 0) 0)) 1 0)))

(defn- chord-cost [frets]
  (let [rf (rendered-frets frets)]
    (+ (* 2 (span rf))
       (count (filter nil? rf)))))

(defn- candidate-cost [{:keys [frets extra-count shape fingering-cost]}]
  (max 0 (+ (or fingering-cost (chord-cost frets))
            (* superset-penalty (or extra-count 0))
            (get shape-bonus shape 0))))

(defn- selected-chord-cost [chord]
  (or (:chord-cost chord)
      (chord-cost (:frets chord))))

(defn- max-span-for-base [base]
  (cond
    (<= base 2) 4
    (<= base 4) 5
    (<= base 7) 6
    :else 7))

(defn- bruteforce-candidates
  [{:keys [max-fret max-span-rendered require-root? require-root-rendered? exact-match? enforce-dynamic-span?]}
   {:keys [root pcs]}]
  (let [max-fret (or max-fret 12)
        rf-range (range 0 (inc max-fret))]
    (->> (for [f1 rf-range
               f2 rf-range
               f3 rf-range
               f4 rf-range
               :let [rendered-frets [f1 f2 f3 f4]
                     base-rendered (first (sort (filter pos? rendered-frets)))
                     dynamic-span (if base-rendered (max-span-for-base base-rendered) (max-span-for-base 1))
                     frets [nil f1 f2 f3 f4]
                     pitches (frets->pitches (select-indices (vec tuning) rendered-string-indices) rendered-frets)
                     pcs-found (pitches->pcs pitches)
                     extra-pcs (set/difference pcs-found pcs)
                     exact? (empty? extra-pcs)
                     grip (grip-name root frets)
                     fingering-info (assign-fingering grip rendered-frets)]
               :when (and (or (not exact-match?) exact?)
                          (or (not require-root?) (contains? pcs-found root))
                          (or (not require-root-rendered?) (contains? pcs-found root))
                          (or (nil? max-span-rendered) (<= (span rendered-frets) max-span-rendered))
                          (or (not enforce-dynamic-span?) (<= (span rendered-frets) dynamic-span)))]
           {:frets frets
            :pcs pcs-found
            :shape :bruteforce
            :base (or base-rendered 0)
            :bass-fret f1
            :extra-count (count extra-pcs)
            :grip-name grip
            :fingering (:fingering fingering-info)
            :fingering-cost (:fingering-cost fingering-info)
            :chord-cost (candidate-cost {:frets frets
                                         :extra-count (count extra-pcs)
                                         :shape :bruteforce
                                         :fingering-cost (:fingering-cost fingering-info)})
            :static-cost (static-cost frets)})
         (sort-by :static-cost)
         vec)))

(defn- apply-shape [base bass-fret offsets]
  (let [start (vec (repeat 5 nil))
        with-bass (if (some #{bass-string-index} shape-strings)
                    start
                    (assoc start bass-string-index bass-fret))]
    (reduce (fn [acc [idx offset]]
              ;; Offsets are relative to the base: negative means higher fret (toward bridge).
              (assoc acc idx (- base offset)))
            with-bass
            (map vector shape-strings offsets))))

(defn- delta-shape? [shape]
  (str/starts-with? (name shape) "delta"))

(defn candidate-voicings
  [{:keys [base-range bass-range max-fret max-span max-span-rendered require-root? window-span require-root-rendered? allowed-shapes ignore-quality? exact-match? enforce-dynamic-span? allow-superset-shapes?]}
   {:keys [root pcs quality]}]
  (let [opts {:max-fret max-fret :max-span max-span}]
    (->> (for [base base-range
               bass-fret bass-range
               [shape {:keys [offsets qualities]}] chicago-shapes
               :when (or (nil? allowed-shapes) (contains? allowed-shapes shape))
               :when (or ignore-quality? (nil? qualities) (contains? qualities quality))
               :let [frets (apply-shape base bass-fret offsets)
                     s0 (nth frets 0)
                     variants (if (zero? (or s0 0))
                                [frets]
                                [frets (assoc frets 0 0)])]
               frets variants
               :when (and (valid-frets? opts frets)
                          (or (nil? window-span)
                              (<= (span frets) window-span)))
               :let [pitches (frets->pitches tuning frets)
                     rendered-pitches (select-indices (vec pitches) rendered-string-indices)
                     pcs-found (pitches->pcs rendered-pitches)
                     pcs-rendered pcs-found
                     extra-pcs (set/difference pcs-found pcs)
                     exact? (empty? extra-pcs)
                     superset-ok? (or (not exact-match?)
                                      (and allow-superset-shapes?
                                           (contains? superset-allowed-shapes shape)))
                     rendered-frets (rendered-frets frets)
                     grip (grip-name root frets)
                     fingering-info (assign-fingering grip rendered-frets)
                     base-rendered (first (sort (filter (fn [f] (and (number? f) (pos? f))) rendered-frets)))
                     dynamic-span (if base-rendered (max-span-for-base base-rendered) (max-span-for-base 1))]
               :when (and (set/subset? pcs pcs-found)
                          (or (not exact-match?) exact? superset-ok?)
                          (or (not require-root?) (contains? pcs-found root))
                          (or (not require-root-rendered?) (contains? pcs-rendered root))
                          (or (nil? max-span-rendered)
                              (<= (span rendered-frets) max-span-rendered))
                          (or (not enforce-dynamic-span?)
                              (<= (span rendered-frets) dynamic-span)))]
           {:frets frets
            :pcs pcs-found
            :shape shape
            :base base
            :bass-fret bass-fret
            :extra-count (count extra-pcs)
            :grip-name grip
            :fingering (:fingering fingering-info)
            :fingering-cost (:fingering-cost fingering-info)
            :chord-cost (candidate-cost {:frets frets
                                         :extra-count (count extra-pcs)
                                         :shape shape
                                         :fingering-cost (:fingering-cost fingering-info)})
            :static-cost (static-cost frets)})
         (sort-by :static-cost)
         vec)))

(defn- fret-class [f]
  (cond
    (nil? f) :open
    (zero? f) :open
    :else :fret))

(defn transition-cost [a b]
  (let [pairs (map vector a b)
        base-cost (reduce
                   (fn [acc [fa fb]]
                     (let [ca (fret-class fa)
                           cb (fret-class fb)]
                       (+ acc
                          (cond
                            (= fa fb) 0
                            (and (nil? fa) (some? fb)) 1
                            (and (some? fa) (nil? fb)) 1
                            (and (= ca :open) (= cb :fret)) (+ 1 (quot fb 5))
                            (and (= ca :fret) (= cb :open)) 1
                            (and (= ca :fret) (= cb :fret))
                            (+ 2 (Math/abs ^long (- fa fb)))
                            :else 0))))
                   0
                   pairs)
        kept (->> pairs
                  (filter (fn [[fa fb]] (and (number? fa) (pos? fa) (= fa fb))))
                  (map first)
                  frequencies)
        kept-count (reduce + 0 (vals kept))
        barre-count (reduce + 0 (map (fn [[_ n]] (max 0 (dec n))) kept))
        anchor-bonus (+ (min 2 kept-count) (min 2 barre-count))]
    (max 0 (- base-cost anchor-bonus))))

(defn- transition-cost-rendered [a b]
  (transition-cost (rendered-frets a) (rendered-frets b)))

(defn- sequence-total-cost [chords]
  (if (seq chords)
    (let [pair-costs (->> (partition 2 1 chords)
                          (map (fn [[a b]]
                                 (transition-cost-rendered (:frets a) (:frets b))))
                          (reduce + 0))]
      (+ (reduce + 0 (map selected-chord-cost chords))
         pair-costs))
    0))

(defn- best-pair [as bs]
  (when (and (seq as) (seq bs))
    (apply min-key :cost
           (for [a as
                 b bs]
             {:cost (+ (:chord-cost a)
                       (transition-cost-rendered (:frets a) (:frets b))
                       (:chord-cost b))
              :from a
              :to b}))))

(defn- best-triplet [ii vs is]
  (when (and (seq ii) (seq vs) (seq is))
    (let [best-v (for [v vs]
                   (let [best (apply min-key :cost
                                     (for [i ii]
                                       {:cost (+ (:chord-cost i)
                                                 (transition-cost-rendered (:frets i) (:frets v))
                                                 (:chord-cost v))
                                        :from i}))]
                     {:v v
                      :cost (:cost best)
                      :from (:from best)}))
          best-i (apply min-key :cost
                        (for [i is
                              v best-v]
                          {:cost (+ (:cost v)
                                    (transition-cost-rendered (:frets (:v v)) (:frets i))
                                    (:chord-cost i))
                           :from (:from v)
                           :mid (:v v)
                           :to i}))]
      best-i)))

(defn- best-sequence [candidate-lists]
  (when (every? seq candidate-lists)
    (let [layers (vec candidate-lists)
          init (mapv (fn [cand]
                       {:cand cand
                        :cost (:chord-cost cand)
                        :prev nil})
                     (first layers))
          dp (reduce (fn [acc idx]
                       (let [prev (last acc)
                             curr-cands (nth layers idx)
                             curr (mapv (fn [cand]
                                          (let [best (apply min-key :cost
                                                            (map-indexed
                                                             (fn [pidx prev-entry]
                                                               {:prev pidx
                                                                :cost (+ (:cost prev-entry)
                                                                         (transition-cost-rendered (:frets (:cand prev-entry))
                                                                                                   (:frets cand))
                                                                         (:chord-cost cand))})
                                                             prev))]
                                            {:cand cand
                                             :cost (:cost best)
                                             :prev (:prev best)}))
                                        curr-cands)]
                         (conj acc curr)))
                     [init]
                     (range 1 (count layers)))
          last-layer (last dp)
          last-idx (->> last-layer
                        (map-indexed (fn [idx {:keys [cost]}] {:idx idx :cost cost}))
                        (apply min-key :cost)
                        :idx)]
      (loop [layer-idx (dec (count dp))
             idx last-idx
             acc []]
        (let [entry (nth (nth dp layer-idx) idx)
              acc (conj acc (:cand entry))]
          (if (nil? (:prev entry))
            (vec (reverse acc))
            (recur (dec layer-idx) (:prev entry) acc)))))))

(defn- chord-specs [key-pc]
  {:ii {:root (mod (+ key-pc 2) 12) :quality :min}
   :v {:root (mod (+ key-pc 7) 12) :quality :dom7}
   :i {:root key-pc :quality :maj}})

(defn- scale-specs [key-pc v7?]
  {:i {:root key-pc :quality :maj}
   :ii {:root (mod (+ key-pc 2) 12) :quality :min}
   :iii {:root (mod (+ key-pc 4) 12) :quality :min}
   :iv {:root (mod (+ key-pc 5) 12) :quality :maj}
   :v {:root (mod (+ key-pc 7) 12) :quality (if v7? :dom7 :maj)}
   :vi {:root (mod (+ key-pc 9) 12) :quality :min}
   :vii {:root (mod (+ key-pc 11) 12) :quality :dim}})

(defn- chord-output [{:keys [root quality]}]
  {:root root
   :quality quality
   :pcs (chord-pcs root quality)})

(defn- print-candidates [label candidates]
  (println (str label " candidates:"))
  (doseq [cand (take 5 candidates)]
    (println (str "  " (:frets cand) " " (:shape cand)
                  " grip=" (:grip-name cand) " pcs=" (:pcs cand)))))

(defn- print-tab [{:keys [label frets chord-cost fingerings]}]
  (println (str label " " frets " cost=" chord-cost
                (when (seq fingerings) (str " fingering=" (vec fingerings)))))
  (doseq [idx (range (count tuning))]
    (println (format "s%d(%s): %s"
                     idx
                     (midi->name (nth tuning idx))
                     (let [f (nth frets idx)]
                       (if (nil? f) "x" f))))))

(defn- select-sequence
  [{:keys [candidate-limit require-root? scale-v7?] :as opts}
   key-name key-pc mode]
  (let [specs (chord-specs key-pc)
        scale-specs (scale-specs key-pc scale-v7?)
        ii (chord-output (:ii specs))
        v (chord-output (:v specs))
        i (chord-output (:i specs))
        base-range (range (:base-min opts) (inc (:base-max opts)))
        bass-range (range (:bass-min opts) (inc (:bass-max opts)))
        cand-opts {:base-range base-range
                   :bass-range bass-range
                   :max-fret (:max-fret opts)
                   :max-span (:max-span opts)
                   :max-span-rendered (:max-span-rendered opts)
                   :window-span (:window-span opts)
                   :require-root-rendered? (:require-root-rendered? opts)
                   :enforce-dynamic-span? true
                   :exact-match? (:exact-match? opts)
                   :allow-superset-shapes? (:allow-superset-grips? opts)
                   :require-root? require-root?}
        retry-spans (fn [span]
                      (if (nil? span)
                        [nil]
                        [span (inc span) nil]))
        gen-cands (fn [chord opts]
                    (let [spans (retry-spans (:max-span-rendered opts))
                          exact? (:exact-match? opts)
                          allow-superset? (:allow-superset-shapes? opts)
                          attempt (fn [opts']
                                    (some (fn [span]
                                            (let [cands (candidate-voicings (assoc opts'
                                                                                   :max-span-rendered span
                                                                                   :exact-match? exact?)
                                                                            chord)]
                                              (when (seq cands) (take candidate-limit cands))))
                                          spans))]
                      (or (attempt (assoc opts :allow-superset-shapes? allow-superset?))
                          (when exact?
                            (attempt (assoc opts :allow-superset-shapes? true)))
                          [])))
        v-opts (assoc cand-opts
                      :allowed-shapes (set (filter delta-shape? (keys chicago-shapes)))
                      :ignore-quality? true)
        v-any-opts (assoc cand-opts :ignore-quality? true)
        chord-cands (fn [chord]
                      (let [use-v? (= (:quality chord) :dom7)
                            opts (if use-v? v-opts cand-opts)
                            cands (gen-cands chord opts)]
                        (if (seq cands)
                          cands
                          (take candidate-limit (bruteforce-candidates opts chord)))))
        ii-cands (chord-cands ii)
        v-cands (gen-cands v v-opts)
        i-cands (chord-cands i)
        ii-label (chord-label (nth pc->name (:root ii)) (:quality ii))
        v-label (chord-label (nth pc->name (:root v)) (:quality v))
        i-label (chord-label (nth pc->name (:root i)) (:quality i))
        scale-chords (map (fn [k] (chord-output (get scale-specs k)))
                          [:i :ii :iii :iv :v :vi :vii])
        scale-cand-opts (assoc cand-opts
                               :exact-match? true
                               :allow-superset-shapes? false)
        scale-cands (mapv (fn [chord]
                            (let [use-v? (= (:quality chord) :dom7)
                                  opts (if use-v? v-opts scale-cand-opts)
                                  cands (gen-cands chord opts)]
                              (if (seq cands)
                                cands
                                (take candidate-limit (bruteforce-candidates opts chord)))))
                          scale-chords)
        scale-labels {:i (chord-label (nth pc->name (:root (:i scale-specs))) (:quality (:i scale-specs)))
                      :ii (chord-label (nth pc->name (:root (:ii scale-specs))) (:quality (:ii scale-specs)))
                      :iii (chord-label (nth pc->name (:root (:iii scale-specs))) (:quality (:iii scale-specs)))
                      :iv (chord-label (nth pc->name (:root (:iv scale-specs))) (:quality (:iv scale-specs)))
                      :v (chord-label (nth pc->name (:root (:v scale-specs))) (:quality (:v scale-specs)))
                      :vi (chord-label (nth pc->name (:root (:vi scale-specs))) (:quality (:vi scale-specs)))
                      :vii (chord-label (nth pc->name (:root (:vii scale-specs))) (:quality (:vii scale-specs)))}
        best (case mode
               "ii-V" (when-let [best (best-pair ii-cands v-cands)]
                        [{:label (chord-label-for-candidate (:root ii) (:quality ii) (:pcs (:from best)))
                          :frets (:frets (:from best)) :shape (:shape (:from best)) :root (:root ii) :quality (:quality ii)
                          :extra-count (:extra-count (:from best)) :chord-cost (:chord-cost (:from best))
                          :grip-name (:grip-name (:from best)) :fingerings (:fingering (:from best))}
                         {:label (chord-label-for-candidate (:root v) (:quality v) (:pcs (:to best)))
                          :frets (:frets (:to best)) :shape (:shape (:to best)) :root (:root v) :quality (:quality v)
                          :extra-count (:extra-count (:to best)) :chord-cost (:chord-cost (:to best))
                          :grip-name (:grip-name (:to best)) :fingerings (:fingering (:to best))}])
               "V-I" (when-let [best (best-pair v-cands i-cands)]
                       [{:label (chord-label-for-candidate (:root v) (:quality v) (:pcs (:from best)))
                         :frets (:frets (:from best)) :shape (:shape (:from best)) :root (:root v) :quality (:quality v)
                         :extra-count (:extra-count (:from best)) :chord-cost (:chord-cost (:from best))
                         :grip-name (:grip-name (:from best)) :fingerings (:fingering (:from best))}
                        {:label (chord-label-for-candidate (:root i) (:quality i) (:pcs (:to best)))
                         :frets (:frets (:to best)) :shape (:shape (:to best)) :root (:root i) :quality (:quality i)
                         :extra-count (:extra-count (:to best)) :chord-cost (:chord-cost (:to best))
                         :grip-name (:grip-name (:to best)) :fingerings (:fingering (:to best))}])
               "ii-V-I" (when-let [best (best-triplet ii-cands v-cands i-cands)]
                          [{:label (chord-label-for-candidate (:root ii) (:quality ii) (:pcs (:from best)))
                            :frets (:frets (:from best)) :shape (:shape (:from best)) :root (:root ii) :quality (:quality ii)
                            :extra-count (:extra-count (:from best)) :chord-cost (:chord-cost (:from best))
                            :grip-name (:grip-name (:from best)) :fingerings (:fingering (:from best))}
                           {:label (chord-label-for-candidate (:root v) (:quality v) (:pcs (:mid best)))
                            :frets (:frets (:mid best)) :shape (:shape (:mid best)) :root (:root v) :quality (:quality v)
                            :extra-count (:extra-count (:mid best)) :chord-cost (:chord-cost (:mid best))
                            :grip-name (:grip-name (:mid best)) :fingerings (:fingering (:mid best))}
                           {:label (chord-label-for-candidate (:root i) (:quality i) (:pcs (:to best)))
                            :frets (:frets (:to best)) :shape (:shape (:to best)) :root (:root i) :quality (:quality i)
                            :extra-count (:extra-count (:to best)) :chord-cost (:chord-cost (:to best))
                            :grip-name (:grip-name (:to best)) :fingerings (:fingering (:to best))}])
               "scale" (when-let [best (best-sequence scale-cands)]
                         (mapv (fn [chord cand]
                                 {:label (chord-label-for-candidate (:root chord) (:quality chord) (:pcs cand))
                                  :frets (:frets cand) :shape (:shape cand) :root (:root chord) :quality (:quality chord)
                                  :extra-count (:extra-count cand) :chord-cost (:chord-cost cand)
                                  :grip-name (:grip-name cand) :fingerings (:fingering cand)})
                               scale-chords
                               best))
               nil)
        relaxed-best (when (and (nil? best) (not= mode "scale"))
                       (let [relax-ii? (empty? ii-cands)
                             relax-v? (empty? v-cands)
                             relax-i? (empty? i-cands)
                             relaxed-opts (assoc cand-opts
                                                 :exact-match? (not relax-ii?)
                                                 :max-span-rendered nil
                                                 :enforce-dynamic-span? false)
                             relaxed-i-opts (assoc cand-opts
                                                   :exact-match? (not relax-i?)
                                                   :max-span-rendered nil
                                                   :enforce-dynamic-span? false)
                             relaxed-v-opts (assoc v-opts
                                                   :exact-match? (not relax-v?)
                                                   :max-span-rendered nil
                                                   :enforce-dynamic-span? false)
                             relaxed-v-any-opts (assoc v-any-opts
                                                       :exact-match? (not relax-v?)
                                                       :max-span-rendered nil
                                                       :enforce-dynamic-span? false)
                             ii2 (let [cands (gen-cands ii relaxed-opts)]
                                   (if (seq cands)
                                     cands
                                     (take candidate-limit (bruteforce-candidates relaxed-opts ii))))
                             v2 (gen-cands v (if (seq v-cands) relaxed-v-opts relaxed-v-any-opts))
                             i2 (let [cands (gen-cands i relaxed-i-opts)]
                                  (if (seq cands)
                                    cands
                                    (take candidate-limit (bruteforce-candidates relaxed-i-opts i))))
                             scale-relaxed-opts (assoc cand-opts
                                                       :exact-match? false
                                                       :max-span-rendered nil
                                                       :enforce-dynamic-span? false)
                             scale-relaxed-cands (mapv (fn [chord]
                                                         (let [use-v? (= (:quality chord) :dom7)
                                                               opts (if use-v?
                                                                      (assoc v-opts
                                                                             :exact-match? false
                                                                             :max-span-rendered nil
                                                                             :enforce-dynamic-span? false)
                                                                      scale-relaxed-opts)
                                                               cands (gen-cands chord opts)]
                                                           (if (seq cands)
                                                             cands
                                                             (take candidate-limit (bruteforce-candidates opts chord)))))
                                                       scale-chords)]
                         (case mode
                           "ii-V" (when-let [best (best-pair ii2 v2)]
                                    [{:label (chord-label-for-candidate (:root ii) (:quality ii) (:pcs (:from best)))
                                      :frets (:frets (:from best)) :shape (:shape (:from best)) :root (:root ii) :quality (:quality ii)
                                      :extra-count (:extra-count (:from best)) :chord-cost (:chord-cost (:from best))
                                      :grip-name (:grip-name (:from best)) :fingerings (:fingering (:from best))}
                                     {:label (chord-label-for-candidate (:root v) (:quality v) (:pcs (:to best)))
                                      :frets (:frets (:to best)) :shape (:shape (:to best)) :root (:root v) :quality (:quality v)
                                      :extra-count (:extra-count (:to best)) :chord-cost (:chord-cost (:to best))
                                      :grip-name (:grip-name (:to best)) :fingerings (:fingering (:to best))}])
                           "V-I" (when-let [best (best-pair v2 i2)]
                                   [{:label (chord-label-for-candidate (:root v) (:quality v) (:pcs (:from best)))
                                     :frets (:frets (:from best)) :shape (:shape (:from best)) :root (:root v) :quality (:quality v)
                                     :extra-count (:extra-count (:from best)) :chord-cost (:chord-cost (:from best))
                                     :grip-name (:grip-name (:from best)) :fingerings (:fingering (:from best))}
                                    {:label (chord-label-for-candidate (:root i) (:quality i) (:pcs (:to best)))
                                     :frets (:frets (:to best)) :shape (:shape (:to best)) :root (:root i) :quality (:quality i)
                                     :extra-count (:extra-count (:to best)) :chord-cost (:chord-cost (:to best))
                                     :grip-name (:grip-name (:to best)) :fingerings (:fingering (:to best))}])
                           "ii-V-I" (when-let [best (best-triplet ii2 v2 i2)]
                                      [{:label (chord-label-for-candidate (:root ii) (:quality ii) (:pcs (:from best)))
                                        :frets (:frets (:from best)) :shape (:shape (:from best)) :root (:root ii) :quality (:quality ii)
                                        :extra-count (:extra-count (:from best)) :chord-cost (:chord-cost (:from best))
                                        :grip-name (:grip-name (:from best)) :fingerings (:fingering (:from best))}
                                       {:label (chord-label-for-candidate (:root v) (:quality v) (:pcs (:mid best)))
                                        :frets (:frets (:mid best)) :shape (:shape (:mid best)) :root (:root v) :quality (:quality v)
                                        :extra-count (:extra-count (:mid best)) :chord-cost (:chord-cost (:mid best))
                                        :grip-name (:grip-name (:mid best)) :fingerings (:fingering (:mid best))}
                                       {:label (chord-label-for-candidate (:root i) (:quality i) (:pcs (:to best)))
                                        :frets (:frets (:to best)) :shape (:shape (:to best)) :root (:root i) :quality (:quality i)
                                        :extra-count (:extra-count (:to best)) :chord-cost (:chord-cost (:to best))
                                        :grip-name (:grip-name (:to best)) :fingerings (:fingering (:to best))}])
                           "scale" (when-let [best (best-sequence scale-relaxed-cands)]
                                     (mapv (fn [chord cand]
                                             {:label (chord-label-for-candidate (:root chord) (:quality chord) (:pcs cand))
                                              :frets (:frets cand) :shape (:shape cand) :root (:root chord) :quality (:quality chord)
                                              :extra-count (:extra-count cand) :chord-cost (:chord-cost cand)
                                              :grip-name (:grip-name cand) :fingerings (:fingering cand)})
                                           scale-chords
                                           best))
                           nil)))]
    {:key-name key-name
     :mode mode
     :candidates (merge {:ii ii-cands :v v-cands :i i-cands}
                        (when (= mode "scale")
                          {:scale scale-cands}))
     :labels (merge {:ii ii-label :v v-label :i i-label}
                    (when (= mode "scale") scale-labels))
     :best (or best relaxed-best)
     :relaxed? (and (nil? best) (some? relaxed-best))}))

(defn- sequence-label [mode labels]
  (case mode
    "ii-V" (str (:ii labels) " " (:v labels))
    "V-I" (str (:v labels) " " (:i labels))
    "ii-V-I" (str (:ii labels) " " (:v labels) " " (:i labels))
    "scale" (str (:i labels) " " (:ii labels) " " (:iii labels) " "
                 (:iv labels) " " (:v labels) " " (:vi labels) " "
                 (:vii labels))
    ""))

(defn- sequence-label-from-chords [mode chords]
  (let [names (map :label chords)]
    (case mode
      "ii-V" (str (first names) " " (second names))
      "V-I" (str (first names) " " (second names))
      "ii-V-I" (str (first names) " " (second names) " " (nth names 2))
      "scale" (str/join " " names)
      (str/join " " names))))

(defn- solve-key
  [{:keys [candidate-limit require-root? show-candidates] :as opts}
   key-name key-pc mode]
  (let [{:keys [candidates labels best relaxed?]} (select-sequence opts key-name key-pc mode)
        {:keys [ii v i]} candidates
        ii-cands ii
        v-cands v
        i-cands i
        {:keys [ii v i]} labels
        ii-label ii
        v-label v
        i-label i]
    (println (str "Key " key-name " (" (nth pc->name key-pc) "): " mode))
    (println (if (seq best)
               (sequence-label-from-chords mode best)
               (sequence-label mode labels)))
    (when relaxed?
      (println "note: relaxed to allow supersets for a playable path"))
    (when (seq best)
      (println (format "total cost=%d" (sequence-total-cost best))))
    (when show-candidates
      (if (= mode "scale")
        (doseq [chord best]
          (println (str (:label chord) " candidates:"))
          (println (str "  " (:frets chord) " " (:shape chord)
                        " grip=" (:grip-name chord))))
        (do
          (print-candidates ii-label ii-cands)
          (print-candidates v-label v-cands)
          (print-candidates i-label i-cands))))
    (case mode
      "ii-V"
      (if best
        (do
          (doseq [chord best]
            (print-tab chord))
          (when (= 2 (count best))
            (println (format "transition cost=%d"
                             (transition-cost-rendered (:frets (first best))
                                                       (:frets (second best)))))))
        (println "No ii-V candidate path found."))

      "V-I"
      (if best
        (do
          (doseq [chord best]
            (print-tab chord))
          (when (= 2 (count best))
            (println (format "transition cost=%d"
                             (transition-cost-rendered (:frets (first best))
                                                       (:frets (second best)))))))
        (println "No V-I candidate path found."))

      "ii-V-I"
      (if best
        (do
          (doseq [chord best]
            (print-tab chord))
          (when (= 3 (count best))
            (println (format "transition costs=%d,%d"
                             (transition-cost-rendered (:frets (first best))
                                                       (:frets (second best)))
                             (transition-cost-rendered (:frets (second best))
                                                       (:frets (nth best 2)))))))
        (println "No ii-V-I candidate path found."))

      "scale"
      (if best
        (do
          (doseq [chord best]
            (print-tab chord))
          (let [costs (->> (partition 2 1 best)
                           (map (fn [[a b]]
                                  (transition-cost-rendered (:frets a) (:frets b))))
                           (str/join ","))]
            (println (format "transition costs=%s" costs))))
        (println "No scale candidate path found."))

      (println "Unknown mode. Use ii-V, V-I, ii-V-I, or scale."))))

(defn- parse-int [s default]
  (try
    (Integer/parseInt s)
    (catch Exception _ default)))

(defn- match-qualities
  "Return a map of quality -> count for matches across bases/variants.
  A match requires the root pitch class to be present in the produced pcs."
  [opts offsets]
  (let [bases (range (:base-min opts) (inc (:base-max opts)))
        opts-valid {:max-fret (:max-fret opts)
                    :max-span (:max-span opts)}]
    (frequencies
     (for [base bases
           :let [frets (apply-shape base base offsets)
                 s0 (nth frets 0)
                 variants (if (zero? (or s0 0))
                            [frets]
                            [frets (assoc frets 0 0)])]
           frets variants
           :when (valid-frets? opts-valid frets)
           :let [pcs (pitches->pcs (frets->pitches tuning frets))]
           root pcs
           quality (chord-qualities-for-root root pcs)]
       quality))))

(defn- validate-shape-library [opts]
  (let [bases (range (:base-min opts) (inc (:base-max opts)))
        opts-valid {:max-fret (:max-fret opts)
                    :max-span (:max-span opts)}]
    (doseq [[shape {:keys [offsets qualities]}] chicago-shapes]
      (let [match-hist (match-qualities opts offsets)
            expected (or qualities #{})
            suggested (set (keys match-hist))
            missing (set/difference expected suggested)]
        (println (format "%s -> expected %s, suggested %s, missing %s"
                         (name shape)
                         (if (seq expected) expected #{})
                         (if (seq suggested) suggested #{})
                         (if (seq missing) missing #{})))
        (when (seq match-hist)
          (println (format "  matches %s" match-hist)))))))

(defn- parse-args [args]
  (loop [opts {:mode "ii-V-I"
               :key "C"
               :circle false
               :tikz nil
               :validate-shapes false
               :candidate-limit 80
               :max-fret 24
               :max-span 7
               :max-span-rendered 5
               :window-span nil
               :require-root-rendered? false
               :exact-match? true
               :allow-superset-grips? false
               :scale-v7? false
               :base-min 0
               :base-max 12
               :bass-min 0
               :bass-max 12
               :require-root? true
               :show-candidates false}
         xs args]
    (if (empty? xs)
      opts
      (let [arg (first xs)]
        (case arg
          "--key" (recur (assoc opts :key (second xs)) (nnext xs))
          "--mode" (recur (assoc opts :mode (second xs)) (nnext xs))
          "--circle" (recur (assoc opts :circle true) (next xs))
          "--limit" (recur (assoc opts :candidate-limit (parse-int (second xs) (:candidate-limit opts))) (nnext xs))
          "--max-fret" (recur (assoc opts :max-fret (parse-int (second xs) (:max-fret opts))) (nnext xs))
          "--max-span" (recur (assoc opts :max-span (parse-int (second xs) (:max-span opts))) (nnext xs))
          "--max-span-rendered" (recur (assoc opts :max-span-rendered (parse-int (second xs) (:max-span-rendered opts))) (nnext xs))
          "--window-span" (recur (assoc opts :window-span (parse-int (second xs) (:window-span opts))) (nnext xs))
          "--base-min" (recur (assoc opts :base-min (parse-int (second xs) (:base-min opts))) (nnext xs))
          "--base-max" (recur (assoc opts :base-max (parse-int (second xs) (:base-max opts))) (nnext xs))
          "--bass-min" (recur (assoc opts :bass-min (parse-int (second xs) (:bass-min opts))) (nnext xs))
          "--bass-max" (recur (assoc opts :bass-max (parse-int (second xs) (:bass-max opts))) (nnext xs))
          "--require-root" (recur (assoc opts :require-root? true) (next xs))
          "--no-require-root" (recur (assoc opts :require-root? false) (next xs))
          "--require-root-rendered" (recur (assoc opts :require-root-rendered? true) (next xs))
          "--no-require-root-rendered" (recur (assoc opts :require-root-rendered? false) (next xs))
          "--exact-match" (recur (assoc opts :exact-match? true) (next xs))
          "--allow-superset" (recur (assoc opts :exact-match? false) (next xs))
          "--allow-superset-grips" (recur (assoc opts :allow-superset-grips? true) (next xs))
          "--no-allow-superset-grips" (recur (assoc opts :allow-superset-grips? false) (next xs))
          "--scale-v7" (recur (assoc opts :scale-v7? true) (next xs))
          "--no-scale-v7" (recur (assoc opts :scale-v7? false) (next xs))
          "--show-candidates" (recur (assoc opts :show-candidates true) (next xs))
          "--tikz" (recur (assoc opts :tikz (second xs)) (nnext xs))
          "--validate-shapes" (recur (assoc opts :validate-shapes true) (next xs))
          "--help" (recur (assoc opts :help true) (next xs))
          (recur opts (next xs)))))))

(defn- usage []
  (println "Usage: clj -M -m futon5.ii-v-i [--key C] [--mode ii-V-I] [--circle]")
  (println "Options: --limit N --max-fret N --max-span N --max-span-rendered N --base-min N --base-max N")
  (println "         --bass-min N --bass-max N --window-span N --require-root --no-require-root")
  (println "         --require-root-rendered --no-require-root-rendered")
  (println "         --exact-match --allow-superset --allow-superset-grips --no-allow-superset-grips")
  (println "         --scale-v7 --no-scale-v7")
  (println "         --show-candidates --tikz path.tex --validate-shapes"))

(def tikz-window-span 3)

(defn- latex-escape [s]
  (when s
    (-> s
        (str/replace "#" "♯")
        (str/replace #"b(?=\\d)" "♭")
        (str/replace #"([A-G])b" "$1♭"))))

(defn- tikz-chord
  [scale {:keys [label frets fingerings root chord-cost grip-name show-drone?]}]
  (let [safe-label (latex-escape label)
        shape-label (when grip-name
                      (latex-escape (str "grip: " grip-name)))
        rendered-indices (if show-drone?
                           (vec (cons drone-string-index rendered-string-indices))
                           rendered-string-indices)
        rendered-frets (select-indices (vec frets) rendered-indices)
        rendered-open (select-indices (vec tuning) rendered-indices)
        pressed (sort (filter (fn [f] (and (number? f) (pos? f))) rendered-frets))
        base (if (seq pressed) (first pressed) 1)
        max-pressed (if (seq pressed) (last pressed) base)
        top (max (+ base tikz-window-span) max-pressed)
        fret-label (if (seq pressed)
                     (str "fret " base)
                     "open")
        rendered-pitches (frets->pitches rendered-open rendered-frets)
        rendered-notes (->> rendered-pitches
                            (map (fn [p] (if p (midi->name p) "x")))
                            (str/join " ")
                            latex-escape)
        cost (or chord-cost (chord-cost frets))
        open-used? (or show-drone?
                       (some (fn [f] (and (number? f) (zero? f))) rendered-frets))
        strings (count rendered-frets)
        fret-span (max 1 (- top base))
        width (dec strings)
        label-x -0.6
        fret-label-x -0.2
        nut-y (+ 1.0 (/ 1.0 (double fret-span)))
        display-fingerings (when fingerings
                             (if show-drone?
                               (vec (cons nil fingerings))
                               fingerings))
        finger-y (+ nut-y 0.25)
        label-line (when shape-label
                     (format "\\node[anchor=west] at (%.1f,-0.3) {\\scriptsize %s};\n"
                             0.0 shape-label))
        notes-line (format "\\node[anchor=west] at (%.1f,-0.6) {\\scriptsize %s};\n"
                           0.0 rendered-notes)
        cost-line (format "\\node[anchor=west] at (%.1f,-0.9) {\\scriptsize cost: %d};\n"
                          0.0 cost)]
    (str
     (format "\\begin{tikzpicture}[x=%.2fcm,y=%.2fcm]\n" scale scale)
     (format "\\node[anchor=east] at (%.1f,1.6) {%s};\n" label-x safe-label)
     (format "\\node[anchor=east] at (%.1f,1.2) {%s};\n" label-x fret-label)
     (apply str
            (for [s (range strings)]
              (let [y-min (if (and show-drone? (= s 0)) nut-y 0.0)]
                (format "\\draw (%.1f,%.3f) -- (%.1f,1);\n" (double s) y-min (double s)))))
     (apply str
            (for [f (range base (inc top))]
              (let [y (- 1.0 (/ (double (- f base)) (double fret-span)))
                    start-x (if show-drone? 1.0 0.0)]
                (str
                 (format "\\draw (%.1f,%.3f) -- (%.1f,%.3f);\n" start-x y (double width) y)
                 (format "\\node[anchor=east] at (%.2f,%.3f) {\\scriptsize %d};\n"
                         fret-label-x y f)))))
     (when open-used?
       (format "\\draw[line width=0.8pt] (0,%.3f) -- (%.1f,%.3f);\n" nut-y (double width) nut-y))
     (when display-fingerings
       (apply str
              (for [s (range strings)]
                (let [f (nth rendered-frets s)
                      finger (nth display-fingerings s nil)]
                  (when (and (pos? (or f 0)) (number? finger))
                    (format "\\node at (%.1f,%.3f) {\\scriptsize %d};\n"
                            (double s) finger-y finger))))))
     (apply str
            (for [s (range strings)]
              (let [f (nth rendered-frets s)
                    midi (when (number? f) (+ (nth rendered-open s) f))
                    is-root? (and (number? midi) (= (pc midi) root))]
                (cond
                  (nil? f) (if open-used?
                             (format "\\node at (%.1f,%.3f) {x};\n" (double s) nut-y)
                             (format "\\node at (%.1f,1.05) {x};\n" (double s)))
                  (zero? f) (if is-root?
                              (format "\\draw[red] (%.1f,%.3f) circle (0.08);\n" (double s) nut-y)
                              (format "\\draw (%.1f,%.3f) circle (0.08);\n" (double s) nut-y))
                  :else (let [y (- 1.0 (/ (double (- f base)) (double fret-span)))]
                          (format "\\fill[%s] (%.1f,%.3f) circle (0.10);\n"
                                  (if is-root? "red" "black")
                                  (double s) y))))))
     label-line
     notes-line
     cost-line
     "\\end{tikzpicture}\n")))

(defn- tikz-transition [scale cost]
  (str
   (format "\\begin{tikzpicture}[x=%.2fcm,y=%.2fcm]\n" scale scale)
   "\\draw[->,thick] (0,0.5) -- (2.0,0.5);\n"
   (format "\\node at (1.0,0.8) {\\scriptsize %d};\n" cost)
   "\\end{tikzpicture}\n"))

(defn- render-sections [sequences include-drone?]
  (reduce
   (fn [out {:keys [key-name mode chords relaxed?]}]
     (let [chord-list (vec chords)
           chord-list (if include-drone?
                        (mapv (fn [chord]
                                (assoc (with-open-drone chord) :show-drone? true))
                              chord-list)
                        chord-list)
           scale (if include-drone? 0.55 0.7)
           total-cost (sequence-total-cost chord-list)]
       (if (seq chord-list)
         (let [costs (->> (partition 2 1 chord-list)
                          (map (fn [[a b]]
                                 (transition-cost-rendered (:frets a) (:frets b))))
                          vec)
               pieces (if (= 1 (count chord-list))
                        (map (partial tikz-chord scale) chord-list)
                        (mapcat (fn [idx chord]
                                  (let [piece [(tikz-chord scale chord)]]
                                    (if (< idx (count costs))
                                      (conj piece (tikz-transition scale (nth costs idx)))
                                      piece)))
                                (range)
                                chord-list))]
           (let [left (str (format "\\textit{%s}\\\\\n"
                                   (latex-escape (sequence-label-from-chords mode chord-list)))
                           (when relaxed?
                             (format "\\textit{(relaxed supersets)}\\\\\n"))
                           (apply str pieces))]
             (conj out
                   (str (format "\\subsection*{%s (%s)}\n" key-name mode)
                        "\\begin{tabular}{@{}p{0.89\\linewidth}r@{}}\n"
                        (format "\\begin{minipage}[t]{0.89\\linewidth}\\raggedright\n%s\\end{minipage} & "
                                left)
                        (format "\\begin{minipage}[t]{0.09\\linewidth}\\raggedleft\\textit{total cost: %d}\\end{minipage}\\\\\n"
                                total-cost)
                        "\\end{tabular}\n"
                        "\\bigskip\n"))))
         out)))
   []
   sequences))

(defn- emit-tikz [path title sequences scale-sequences]
  (let [sections (render-sections sequences false)
        drone-sections (render-sections sequences true)
        scale-sections (when (seq scale-sequences)
                         (render-sections scale-sequences false))
        content (str
                 "\\documentclass{article}\n"
                 "\\usepackage[margin=1cm]{geometry}\n"
                 "\\usepackage{tikz}\n"
                 "\\usepackage{fontspec}\n"
                 "\\usepackage{newunicodechar}\n"
                 "\\newfontfamily\\coda[Scale=MatchLowercase]{Noto Sans Math}\n"
                 "\\newunicodechar{♯}{\\raisebox{.5ex}{{\\coda ♯}}}\n"
                 "\\newunicodechar{♭}{\\raisebox{.5ex}{{\\coda {\\large ♭}}}}\n"
                 "\\begin{document}\n"
                 (format "\\section*{%s}\n" title)
                 (apply str sections)
                 (format "\\section*{%s (open drone variants)}\n" title)
                 (apply str drone-sections)
                 (when scale-sections
                   (str (format "\\section*{%s (scale paths)}\n" title)
                        (apply str scale-sections)))
                 "\\end{document}\n")]
    (spit (io/file path) content)))

(defn- normalize-key [s]
  (let [s (str/trim (or s ""))]
    (if (empty? s)
      s
      (str (str/upper-case (subs s 0 1)) (subs s 1)))))

(defn -main [& args]
  (let [{:keys [key mode circle help tikz validate-shapes] :as opts} (parse-args args)
        key-name (normalize-key key)
        key-pc (get note->pc key-name)]
    (cond
      help (usage)
      validate-shapes (do
                        (validate-shape-library opts)
                        (System/exit 0))
      circle (let [tikz-opts (cond-> opts
                               true (assoc :require-root-rendered? true))
                   sequences (for [[name pc-val] circle-of-fourths]
                               (do
                                 (solve-key opts name pc-val mode)
                                 (println)
                                 (let [result (select-sequence tikz-opts name pc-val mode)]
                                   {:key-name name
                                    :mode mode
                                    :labels (:labels result)
                                    :relaxed? (:relaxed? result)
                                    :chords (:best result)})))
                   scale-sequences (when (not= mode "scale")
                                     (for [[name pc-val] circle-of-fourths]
                                       (let [result (select-sequence tikz-opts name pc-val "scale")]
                                         {:key-name name
                                          :mode "scale"
                                          :labels (:labels result)
                                          :relaxed? (:relaxed? result)
                                          :chords (:best result)})))]
               (when tikz
                 (emit-tikz tikz "FUTON5 ii-V-I cheat sheet" sequences scale-sequences)))
      key-pc (let [tikz-opts (cond-> opts
                               true (assoc :require-root-rendered? true))
                   tikz-result (select-sequence tikz-opts key-name key-pc mode)
                   scale-result (when (not= mode "scale")
                                  (select-sequence tikz-opts key-name key-pc "scale"))]
               (solve-key opts key-name key-pc mode)
               (when tikz
                 (emit-tikz tikz
                            (str "FUTON5 ii-V-I cheat sheet (" key-name ")")
                            [{:key-name key-name
                              :mode mode
                              :labels (:labels tikz-result)
                              :relaxed? (:relaxed? tikz-result)
                              :chords (:best tikz-result)}]
                            (when scale-result
                              [{:key-name key-name
                                :mode "scale"
                                :labels (:labels scale-result)
                                :relaxed? (:relaxed? scale-result)
                                :chords (:best scale-result)}]))))
      :else (do
              (println "Unknown key. Use --key C, --key F#, --key Bb, etc.")
              (usage)))))
