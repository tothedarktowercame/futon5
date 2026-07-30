(ns futon5.mmca.causal-score
  "Seeded damage-spreading score for phenotype-coupled rule fields.

   The protocol matches `mmca-clj/scripts/regime_placement.clj`: evolve an
   80-cell system to t*=60, flip one phenotype bit, evolve both forks for 59
   further steps, and count differing phenotype cells.

   A field update receives pre-drawn `:source-draws` and `:gate-coins`.
   Both vectors are drawn unconditionally, before the callback is invoked.
   Consequently a phenotype-dependent gate cannot short-circuit past an RNG
   draw. Gate coins use a stream separate from propagator/source draws, and
   both streams are cloned at the damage fork."
  (:refer-clojure :exclude [rand-int]))

(def ^:private uint32-mask 0xffffffff)
(def ^:private emacs-int-mask (dec (bit-shift-left 1 61)))
(def ^:private lattice-width 80)
(def ^:private burn-in-steps 60)
(def ^:private damage-steps 59)

(defrecord ^:private EmacsRng [state index])

(defn- u32 [n]
  (bit-and (long n) uint32-mask))

(defn- string-seed [s]
  (let [bytes (.getBytes (str s) "UTF-8")
        folded (reduce (fn [acc i]
                         (update acc (mod i 4)
                                 bit-xor (bit-and 0xff (aget bytes i))))
                       [0 0 0 0]
                       (range (alength bytes)))]
    (reduce-kv (fn [n i b]
                 (bit-or n (bit-shift-left (long b) (* 8 i))))
               0
               folded)))

(defn- glibc-state [seed]
  (let [seed (long (if (zero? seed) 1 seed))]
    (loop [xs [seed]
           i 1]
      (cond
        (< i 31)
        (recur (conj xs (mod (* 16807 (peek xs)) 2147483647)) (inc i))

        (< i 34)
        (recur (conj xs (nth xs (- i 31))) (inc i))

        (< i 344)
        (recur (conj xs (u32 (+ (nth xs (- i 31))
                                 (nth xs (- i 3)))))
               (inc i))

        :else xs))))

(defn- glibc-random! [^EmacsRng rng]
  (let [i @(.index rng)
        xs @(.state rng)
        next-value (u32 (+ (nth xs (- i 31)) (nth xs (- i 3))))]
    (swap! (.state rng) conj next-value)
    (swap! (.index rng) inc)
    (unsigned-bit-shift-right next-value 1)))

(defn- draw! [^EmacsRng rng]
  (let [value (loop [i 0
                     value 0]
                (if (= i 2)
                  value
                  (let [r (glibc-random! rng)]
                    (recur (inc i)
                           (bit-xor r
                                    (bit-shift-left value 31)
                                    (unsigned-bit-shift-right value 33))))))
        mixed (bit-xor value (unsigned-bit-shift-right value 2))]
    (bit-and mixed emacs-int-mask)))

(defn- make-rng [seed-string]
  (let [rng (->EmacsRng (atom (glibc-state (string-seed seed-string)))
                        (atom 344))]
    ;; GNU Emacs consumes the value returned by `(random seed-string)`.
    (draw! rng)
    rng))

(defn- clone-rng [^EmacsRng rng]
  (->EmacsRng (atom @(.state rng)) (atom @(.index rng))))

(defn- rand-int [rng limit]
  {:pre [(pos-int? limit)]}
  (let [limit (long limit)
        difference-limit (- emacs-int-mask limit -1)]
    (loop []
      (let [r (draw! rng)
            remainder (mod r limit)
            difference (- r remainder)]
        (if (< difference-limit difference)
          (recur)
          remainder)))))

(defn- rand-double [rng]
  (/ (double (draw! rng)) 2305843009213693952.0))

(defn- initial-phenotype
  "Reproduce the calibration producer's initial-condition tape.

   The producer draws a random rule field and then its phenotype from the same
   `prop-SEED` stream. `reach` accepts an explicit field, so the first WIDTH
   rule-byte draws are deliberately consumed here before drawing phenotype."
  [source-rng width]
  (dotimes [_ width]
    (rand-int source-rng 256))
  (apply str
         (repeatedly width
                     #(char (+ (int \0) (rand-int source-rng 2))))))

(defn- rule-output [rule left centre right]
  (if (bit-test rule (+ (* 4 left) (* 2 centre) right)) 1 0))

(defn- eca-step [field phenotype]
  (let [width (count phenotype)]
    (apply str
           (for [i (range width)]
             (let [left (Character/digit
                         (nth phenotype (mod (dec i) width)) 2)
                   centre (Character/digit (nth phenotype i) 2)
                   right (Character/digit
                          (nth phenotype (mod (inc i) width)) 2)]
               (char (+ (int \0)
                        (rule-output (nth field i) left centre right))))))))

(defn eca-config
  "Configuration for a fixed per-cell elementary-CA rule field.

   The circular boundary matches the published calibration producer."
  []
  {:phenotype-step eca-step})

(defn- advance
  [field phenotype time source-rng gate-rng
   {:keys [phenotype-step field-step draw-count source-limit]
    :or {field-step (fn [{:keys [field]}] field)
         source-limit 8}}]
  (let [opportunities (or draw-count (count field))
        ;; These draws MUST remain unconditional and outside field-step.
        source-draws (mapv (fn [_] (rand-int source-rng source-limit))
                           (range opportunities))
        gate-coins (mapv (fn [_] (rand-double gate-rng))
                         (range opportunities))
        context {:field field
                 :phenotype phenotype
                 :time time
                 :source-draws source-draws
                 :gate-coins gate-coins}
        next-field (field-step context)
        next-phenotype (phenotype-step field phenotype)]
    [next-field next-phenotype]))

(defn- flip-bit [phenotype site]
  (apply str
         (assoc (vec phenotype) site
                (if (= \1 (nth phenotype site)) \0 \1))))

(defn- differing-cells [a b]
  (count (remove true? (map = a b))))

(defn- sample-sd [values]
  (if (< (count values) 2)
    0.0
    (let [mean (/ (reduce + 0.0 values) (double (count values)))
          squares (reduce + 0.0
                          (map (fn [x]
                                 (let [d (- (double x) mean)]
                                   (* d d)))
                               values))]
      (Math/sqrt (/ squares (double (dec (count values))))))))

(defn- mean [values]
  (/ (reduce + 0.0 values) (double (count values))))

(defn- burn-in [field cfg seed width]
  (let [source-rng (make-rng (format "prop-%d" seed))
        gate-rng (make-rng (format "gate-%d" seed))
        phenotype (initial-phenotype source-rng width)]
    (loop [time 0
           current-field field
           current-phenotype phenotype]
      (if (= time burn-in-steps)
        {:field current-field
         :phenotype current-phenotype
         :source-rng source-rng
         :gate-rng gate-rng}
        (let [[next-field next-phenotype]
              (advance current-field current-phenotype time
                       source-rng gate-rng cfg)]
          (recur (inc time) next-field next-phenotype))))))

(defn- damage-at
  [{:keys [field phenotype source-rng gate-rng]}
   cfg site]
  (let [source-a (clone-rng source-rng)
        source-b (clone-rng source-rng)
        gate-a (clone-rng gate-rng)
        gate-b (clone-rng gate-rng)
        perturbed (flip-bit phenotype site)]
    (loop [time burn-in-steps
           field-a field
           phenotype-a phenotype
           field-b field
           phenotype-b perturbed]
      (if (= time (+ burn-in-steps damage-steps))
        (differing-cells phenotype-a phenotype-b)
        (let [[next-field-a next-phenotype-a]
              (advance field-a phenotype-a time source-a gate-a cfg)
              [next-field-b next-phenotype-b]
              (advance field-b phenotype-b time source-b gate-b cfg)]
          (recur (inc time)
                 next-field-a next-phenotype-a
                 next-field-b next-phenotype-b))))))

(defn reach
  "Measure phenotype damage spreading from FIELD under CFG.

   CFG requires `:phenotype-step`, called as `(f field phenotype)`. Its optional
   `:field-step` receives a map containing the current state plus unconditional
   `:source-draws` and independent `:gate-coins`; it returns the next field.

   Options:
   - `:seeds` perturbation seeds (default 0..3)
   - `:sites` perturbation cell indices (default every eighth cell)

   Returns site-level mean/sample-SD plus per-seed means and their sample-SD."
  ([field cfg]
   (reach field cfg {}))
  ([field cfg {:keys [seeds sites]
               :or {seeds (range 4)}}]
   (let [width (count field)
         sites (or sites (range 0 width 8))
         seeds (vec seeds)
         sites (vec sites)]
     (when-not (and (= lattice-width width)
                    (fn? (:phenotype-step cfg))
                    (seq seeds)
                    (seq sites)
                    (every? #(<= 0 % (dec width)) sites))
       (throw (ex-info "Invalid causal-reach field, configuration, or options"
                       {:width width
                        :required-width lattice-width
                        :seeds seeds
                        :sites sites})))
     (let [by-seed
           (mapv (fn [seed]
                   (let [state (burn-in field cfg seed width)
                         damages (mapv #(damage-at state cfg %) sites)]
                     {:seed seed
                      :damages damages
                      :mean (mean damages)}))
                 seeds)
           damages (mapv identity (mapcat :damages by-seed))
           seed-means (mapv :mean by-seed)]
       {:mean (mean damages)
        :sd (sample-sd damages)
        :n (count damages)
        :seed-sd (sample-sd seed-means)
        :by-seed by-seed}))))
