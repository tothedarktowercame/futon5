(ns futon5.ct.tensor
  "Executable tensor helpers for sigil/phenotype evolution.

   Tensor conventions:
   - Genotype tensor: width x 8 (per-cell bit vectors)
   - Bitplane tensor: 8 x width (per-bit histories across cells)
   - Phenotype tensor: width vector of 0/1"
  (:require [futon5.ca.core :as ca]
            [futon5.ct.tensor-jax :as tensor-jax]))

(defn- normalize-sigil-row
  [row]
  (cond
    (nil? row) []
    (string? row) (mapv str row)
    (sequential? row) (mapv (fn [x]
                              (cond
                                (string? x) x
                                (char? x) (str x)
                                :else (str x)))
                            row)
    :else (throw (ex-info "Unsupported sigil row type"
                          {:value row
                           :type (type row)}))))

(defn- normalize-bit
  [x]
  (cond
    (= x 0) 0
    (= x 1) 1
    (= x \0) 0
    (= x \1) 1
    (= x false) 0
    (= x true) 1
    :else (throw (ex-info "Expected binary value (0/1)"
                          {:value x
                           :type (type x)}))))

(defn- normalize-bit-vector
  [xs]
  (mapv normalize-bit xs))

(defn sigil-row->tensor
  "Convert sigil row to width x 8 tensor of 0/1 values."
  [row]
  (mapv (fn [sigil]
          (-> sigil
              ca/bits-for
              ca/bits->ints))
        (normalize-sigil-row row)))

(defn tensor->sigil-row
  "Convert width x 8 tensor back to sigil string."
  [tensor]
  (apply str
         (map (fn [cell]
                (let [bits (normalize-bit-vector cell)]
                  (when-not (= 8 (count bits))
                    (throw (ex-info "Genotype tensor cell must have width 8"
                                    {:cell bits
                                     :count (count bits)})))
                  (ca/sigil-for (ca/ints->bits bits))))
              tensor)))

(defn tensor->bitplanes
  "Transpose width x 8 tensor into 8 x width bitplanes."
  [tensor]
  (let [tensor (mapv normalize-bit-vector tensor)]
    (when-not (every? #(= 8 (count %)) tensor)
      (throw (ex-info "All genotype tensor cells must have width 8"
                      {:tensor tensor})))
    (if (empty? tensor)
      (vec (repeat 8 []))
      (mapv (fn [i]
              (mapv #(nth % i) tensor))
            (range 8)))))

(defn bitplanes->tensor
  "Transpose 8 x width bitplanes into width x 8 tensor."
  [bitplanes]
  (let [bitplanes (mapv normalize-bit-vector bitplanes)]
    (when-not (= 8 (count bitplanes))
      (throw (ex-info "Bitplane tensor must contain exactly 8 planes"
                      {:plane-count (count bitplanes)})))
    (let [widths (set (map count bitplanes))]
      (when-not (= 1 (count widths))
        (throw (ex-info "All bitplanes must have equal width"
                        {:widths (vec widths)})))
      (let [width (or (first widths) 0)]
        (mapv (fn [x]
                (mapv #(nth % x) bitplanes))
              (range width))))))

(defn phenotype->tensor
  "Convert phenotype string/sequence to width vector of 0/1."
  [phenotype]
  (cond
    (nil? phenotype) []
    (string? phenotype) (mapv normalize-bit phenotype)
    (sequential? phenotype) (mapv normalize-bit phenotype)
    :else (throw (ex-info "Unsupported phenotype type"
                          {:value phenotype
                           :type (type phenotype)}))))

(defn tensor->phenotype
  "Convert phenotype tensor back to 0/1 string."
  [phenotype-tensor]
  (apply str (map (fn [x] (if (= 1 (normalize-bit x)) \1 \0))
                  phenotype-tensor)))

(defn- neighbor-bit
  [bits idx offset wrap? boundary-bit]
  (let [n (count bits)
        j (+ idx offset)]
    (cond
      (zero? n) boundary-bit
      wrap? (nth bits (mod j n))
      (or (neg? j) (>= j n)) boundary-bit
      :else (nth bits j))))

(defn step-bitplane
  "Evolve one bitplane using a sigil rule table.

   opts:
   - :wrap? (default true): use circular boundaries
   - :boundary-bit (default 0): used when wrap? is false"
  ([bitplane rule-sigil] (step-bitplane bitplane rule-sigil {}))
  ([bitplane rule-sigil {:keys [wrap? boundary-bit]
                         :or {wrap? true boundary-bit 0}}]
   (let [bits (normalize-bit-vector bitplane)
         boundary-bit (normalize-bit boundary-bit)
         rule (ca/local-rule-table rule-sigil)]
     (mapv (fn [idx]
             (let [l (neighbor-bit bits idx -1 wrap? boundary-bit)
                   s (neighbor-bit bits idx 0 wrap? boundary-bit)
                   r (neighbor-bit bits idx 1 wrap? boundary-bit)]
               (int (get rule (str l s r) 0))))
           (range (count bits))))))

(defn step-bitplanes
  "Evolve all 8 bitplanes using the same rule sigil.

   opts:
   - :backend (default :clj): :clj or :jax"
  ([bitplanes rule-sigil] (step-bitplanes bitplanes rule-sigil {}))
  ([bitplanes rule-sigil opts]
   (let [backend (or (:backend opts) :clj)
         step-opts (dissoc opts :backend)]
     (case backend
       :clj (mapv #(step-bitplane % rule-sigil step-opts) bitplanes)
       :jax (tensor-jax/step-bitplanes-jax bitplanes rule-sigil step-opts)
       (throw (ex-info "Unsupported tensor backend"
                       {:backend backend
                        :supported [:clj :jax]}))))))

(defn step-sigil-row
  "Evolve a sigil row by converting to tensors, stepping bitplanes, and decoding."
  ([row rule-sigil] (step-sigil-row row rule-sigil {}))
  ([row rule-sigil opts]
   (-> row
       sigil-row->tensor
       tensor->bitplanes
       (step-bitplanes rule-sigil opts)
       bitplanes->tensor
       tensor->sigil-row)))

(defn blend-tensors-by-mask
  "Blend old/new genotype tensors by a binary per-cell mask.

   mask bit 1 keeps old-tensor cell; 0 takes new-tensor cell."
  [old-tensor new-tensor mask]
  (let [old (mapv normalize-bit-vector old-tensor)
        new (mapv normalize-bit-vector new-tensor)
        mask (mapv normalize-bit mask)]
    (when-not (= (count old) (count new) (count mask))
      (throw (ex-info "Mask and tensors must have matching widths"
                      {:old-width (count old)
                       :new-width (count new)
                       :mask-width (count mask)})))
    (mapv (fn [m o n]
            (if (= m 1) o n))
          mask old new)))

(defn gate-sigil-row-by-phenotype
  "Combine old/new sigil rows with phenotype as binary gate.

   phenotype bit 1 keeps old row cell; 0 takes new row cell."
  [old-row new-row phenotype]
  (-> (blend-tensors-by-mask (sigil-row->tensor old-row)
                             (sigil-row->tensor new-row)
                             (phenotype->tensor phenotype))
      tensor->sigil-row))

(defn step-sigil-row-with-phenotype-gate
  "Run tensor step and then gate against old row using phenotype mask."
  ([row rule-sigil phenotype]
   (step-sigil-row-with-phenotype-gate row rule-sigil phenotype {}))
  ([row rule-sigil phenotype opts]
   (let [next-row (step-sigil-row row rule-sigil opts)]
     (gate-sigil-row-by-phenotype row next-row phenotype))))
