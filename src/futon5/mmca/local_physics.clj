(ns futon5.mmca.local-physics
  "Shared local-physics evolution helpers for MMCA runtime + TPG runner."
  (:require [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]))

(defn- ensure-sigil
  [result]
  (cond
    (string? result) result
    (map? result) (or (:sigil result) ca/default-sigil)
    (char? result) (str result)
    :else ca/default-sigil))

(defn- make-kernel
  [base-fn]
  (fn [sigil pred succ context]
    (let [mutation-rate (or (:mutation-rate context) 0.2)
          result (if (< (rand) mutation-rate)
                   (ensure-sigil (base-fn sigil pred succ))
                   sigil)]
      {:sigil result})))

(defn- blend-fn
  [sigil pred succ]
  (let [bits-self (ca/bits-for sigil)
        bits-pred (ca/bits-for pred)
        bits-succ (ca/bits-for succ)
        blended (apply str
                       (map (fn [s p n]
                              (let [ones (count (filter #(= \1 %) [s p n]))]
                                (if (>= ones 2) \1 \0)))
                            bits-self bits-pred bits-succ))
        entry (ca/entry-for-bits blended)]
    (or (:sigil entry) ca/default-sigil)))

(defn- mult-fn
  [sigil pred succ]
  (let [kernel-fn (ca/kernel-fn :mutating-template)
        result (binding [ca/*evolve-sigil-fn* kernel-fn]
                 (ca/evolve-sigil sigil pred succ))]
    (or (:sigil result) ca/default-sigil)))

(def local-physics-kernels
  "Kernel functions used by local physics mode. Shared across runtime paths."
  {:blending (make-kernel blend-fn)
   :multiplication (make-kernel mult-fn)
   :ad-hoc-template (make-kernel mult-fn)
   :blending-mutation (make-kernel blend-fn)
   :blending-baldwin (make-kernel blend-fn)
   :collection-template (make-kernel mult-fn)
   :mutating-template (make-kernel mult-fn)})

(defn previous-genotype
  "Get the previous genotype from state history, defaulting to current genotype."
  [state]
  (let [history (get-in state [:history :genotypes])]
    (or (when (> (count history) 1)
          (nth history (- (count history) 2)))
        (:genotype state))))

(defn evolve-local
  "Evolve one local-physics step.

   Returns {:genotype :rules :kernels}. When global-rule is set,
   :rules/:kernels are nil because the bent-global path does not expose them."
  ([genotype phenotype prev-genotype global-rule bend-mode]
   (evolve-local genotype phenotype prev-genotype global-rule bend-mode local-physics-kernels))
  ([genotype phenotype prev-genotype global-rule bend-mode kernels]
   (if global-rule
     {:genotype (exotype/evolve-with-global-exotype genotype
                                                    phenotype
                                                    prev-genotype
                                                    global-rule
                                                    bend-mode
                                                    kernels)
      :rules nil
      :kernels nil}
     (exotype/evolve-string-local genotype phenotype prev-genotype kernels))))

(defn advance-state
  "Advance one generation under local physics.

   opts:
   - :track-local? (default false): append :local-physics-runs metadata."
  ([state global-rule bend-mode]
   (advance-state state global-rule bend-mode {}))
  ([state global-rule bend-mode {:keys [track-local?]
                                 :or {track-local? false}}]
   (let [genotype (:genotype state)
         phenotype (:phenotype state)
         prev-genotype (previous-genotype state)
         result (evolve-local genotype phenotype prev-genotype global-rule bend-mode)
         next-gen (:genotype result)
         next-phe (when phenotype
                    (ca/evolve-phenotype-against-genotype genotype phenotype))
         state' (-> state
                    (assoc :generation (inc (:generation state))
                           :genotype next-gen)
                    (cond-> next-phe (assoc :phenotype next-phe))
                    (update-in [:history :genotypes] conj next-gen)
                    (cond-> next-phe
                      (update-in [:history :phenotypes]
                                 (fn [hist]
                                   (if hist
                                     (conj hist next-phe)
                                     [next-phe])))))]
     (if track-local?
       (update state' :local-physics-runs (fnil conj [])
               {:generation (:generation state)
                :rules (:rules result)
                :kernels (:kernels result)
                :rule-diversity (when (:rules result)
                                  (count (set (:rules result))))})
       state'))))
