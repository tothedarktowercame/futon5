(ns futon5.exotype.grid
  "A per-cell exotype grid and four local transmission policies.

   Exotypes are names from the measured propagator vocabulary. All legacy
   positional permutations are converted through the historical Elisp
   neighbourhood ordering before use. Policies read only the cell, its two
   neighbours, and the current local phenotype neighbourhood."
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.selection :as selection]
            [futon5.xenotype.generator :as gen]))

(def exotype-kinds
  "The selectable vocabulary. Widened from 4 to 12 at S0b/H5 (TN-baldwin-reboot.md
  42.3): the EFE dynamics converged to :collapser because the score-cache only
  iterated over the four declared kinds, so the objective could never select
  the non-freezing kinds. All 12 now have kind-specific rows in the conditional
  model (S0b, 42.2).

  The original four come first to preserve ordering for any code that indexes
  positionally. The eight additions are the P2 probe kinds: the absorbing axis
  (even4/even8/even1) and the rate axis (odd53/fix2/fix3/fix4/fix6)."
  [:builder :collapser :chaos :identity
   :even4 :even8 :even1 :odd53 :fix2 :fix3 :fix4 :fix6])
(def arms [:uniform-fixed :heterogeneous-fixed :conformist :boring-triggered
           :s8-uniform :s8-stratified])

(def ^:private elisp-table
  ["000" "001" "010" "100" "011" "101" "110" "111"])

(defn- positional [s]
  (mapv #(Character/digit ^char % 10) s))

(def propagators
  "Four σ, hand-picked and named, out of 40,320 permutations -- or 16,777,216 maps
   if the family is widened to include the 2015 bug's shape. **Why these four has
   never been justified** (TN-baldwin-reboot.md 15, an open hole).

   The names mislead and the measured coordinates are the antidote. Every σ has a
   computable signature; nothing here needs to be taken on trust:

     kind        fix(σ)  cycle type      rate    floor   bytes immune to it
     :identity     8     (1^8)          1.0000   1.000         0
     :builder      5     (3,1,1,1,1,1)  0.8125   0.625         0
     :chaos        1     (4,3,1)        0.5625   0.125         0
     :collapser    0     (6,2)          0.5000   0.000         4

   `:identity` is the MOST disruptive of the four, not the least -- a fixed point
   of σ is an unconditional flip (see `gen/rule-permute`). `:collapser` is the only
   all-even cycle type and so the only one admitting a byte it cannot change.

   Rates are DERIVED by `gen/rule-change-rate`, not declared; the floor
   `fix(σ)/8` holds for any byte distribution. Both are pinned by
   `futon5.exotype.invariants-test`."
  {:builder (gen/positional-sigma->neighbourhood-sigma
             (positional "51034267") elisp-table)
   :collapser (gen/positional-sigma->neighbourhood-sigma
               (positional "10345672") elisp-table)
   :chaos (gen/positional-sigma->neighbourhood-sigma
           (positional "13407265") elisp-table)
   :identity (gen/positional-sigma->neighbourhood-sigma
              [0 1 2 3 4 5 6 7] elisp-table)

   ;; --- P2 vocabulary probe (TN-baldwin-reboot.md 30-31) -------------------
   ;; NOT in `exotype-kinds`, so the default vocabulary and everything derived
   ;; from it are unchanged. These exist to test one claim: `:rule-change` cannot
   ;; distinguish a propagator that can FREEZE from one that cannot, because
   ;; rate = 0.5 + fix/16 and both of these have fix = 0, hence rate 0.5000
   ;; exactly -- while one has 16 absorbing bytes and the other none.
   ;; The five ALL-EVEN cycle types are the entire immune-byte axis, and every
   ;; one of them has fix = 0, hence rate 0.5000. Together with an odd control at
   ;; the same rate this is a five-point dose-response the model cannot see.
   :even4 (gen/positional-sigma->neighbourhood-sigma
           (positional "10325476") elisp-table)   ; (2,2,2,2) -- 16 immune bytes
   :even8 (gen/positional-sigma->neighbourhood-sigma
           (positional "12305476") elisp-table)   ; (4,2,2)   --  8 immune bytes
   :even1 (gen/positional-sigma->neighbourhood-sigma
           (positional "12345670") elisp-table)   ; (8)       --  2 immune bytes
   :odd53 (gen/positional-sigma->neighbourhood-sigma
           (positional "12340675") elisp-table)   ; (5,3)     --  0 immune bytes

   ;; The RATE axis. rate = 0.5 + fix/16 exactly, so fix is the only rate dial and
   ;; it has nine notches. The default four cover fix 0,1,5,8; these fill the
   ;; contiguous middle gap 2,3,4,6 (fix=7 is impossible). All have 0 absorbing
   ;; bytes, since absorbing requires all-even cycles which force fix=0.
   :fix2 (gen/positional-sigma->neighbourhood-sigma
          (positional "12345067") elisp-table)    ; (6,1,1)         rate 0.6250
   :fix3 (gen/positional-sigma->neighbourhood-sigma
          (positional "12340567") elisp-table)    ; (5,1,1,1)       rate 0.6875
   :fix4 (gen/positional-sigma->neighbourhood-sigma
          (positional "12304567") elisp-table)    ; (4,1,1,1,1)     rate 0.7500
   :fix6 (gen/positional-sigma->neighbourhood-sigma
          (positional "10234567") elisp-table)  ; (2,1,1,1,1,1,1) rate 0.8750

   ;; --- E0b: cycle-type controls (claude-14, 2026-08-04) ---------------------
   ;; NOT in `exotype-kinds`. These break the rank-correlation between absorbing
   ;; count and max cycle length among the all-even types, and give :odd53 a
   ;; matched non-halting replicate.
   :even44 (gen/positional-sigma->neighbourhood-sigma
            (positional "12305674") elisp-table)  ; (4,4)     rate 0.5000  4 immune bytes
   ;; "12034576" was wrong here (claude-14 review, 2026-08-04): it gives
   ;; 3->3, 4->4, 5->5, i.e. cycle type (3,2,1,1,1), fix 3, rate 0.6875 -- which
   ;; is NOT a matched control for :odd53 and would have contaminated any
   ;; experiment using it. Second hand-built permutation to come out wrong today
   ;; (see :fix2, 37.1). Printing the coordinates is what catches these.
   :odd332 (gen/positional-sigma->neighbourhood-sigma
            (positional "12045376") elisp-table)})  ; (3,3,2)   rate 0.5000  0 immune bytes

;; --- P-008: open S8 vocabulary (exotype DESIGN-P008.md) -------------------
;; An S8 exotype is a vector P of 8 longs: P[k] is the `ca/truth-table-3` index of
;; the neighbourhood sigma sends truth-table-3[k] to. Keywords stay the July
;; vocabulary and take EXACTLY the old code paths (G1 byte identity).

(defn s8?
  "Is EXOTYPE an S8 vector rather than a named kind?"
  [exotype]
  (vector? exotype))

(def ^:private s8->sigma
  (memoize
   (fn [p]
     (let [tt (vec ca/truth-table-3)]
       (when-not (= (sort p) (range 8))
         (throw (ex-info "S8 exotype is not a permutation of 0..7" {:exotype p})))
       (into {} (map-indexed (fn [k j] [(nth tt k) (nth tt (long j))]) p))))))

;; --- P-008b §7 item 1: End(8) representation (wired via *allow-end8?*) ---
;; e8->sigma is the same table construction as s8->sigma WITHOUT the
;; permutation check: DESIGN-P008b.md §7 item 1. It accepts any 8-vector with
;; integer entries in 0..7 (all of End(8)); anything else still throws.
;; sigma-of sends a non-injective vector here only when *allow-end8?* is
;; bound true (step 1b); permutations always take s8->sigma. Non-injective
;; maps may hit code that assumes a permutation once the flag is on:
;; gen/rule-permute via grid/apply-exotype (grid.clj apply-exotype /
;; transfer) sends position k to f(k) and is fine, but
;; chain_risk/positional (chain_risk.clj:32), policy-epistemic pair/blend
;; values, self_tuning/s8-score and s8-transition (self_tuning.clj s8
;; section), and efe/score-policy's 1/8 and inverse-map assumptions (items
;; 2-3 of §7) do not hold for non-injective f. Do not fix here.

(defn perm?
  "Is EXOTYPE a permutation of 0..7 (an S8 vector)? DESIGN-P008b §7 item 1."
  [exotype]
  (and (vector? exotype)
       (= 8 (count exotype))
       (every? #(and (integer? %) (<= 0 (long %) 7)) exotype)
       (= (sort exotype) (range 8))))

(def ^:private e8->sigma
  (memoize
   (fn [f]
     (let [tt (vec ca/truth-table-3)]
       (when-not (and (vector? f) (= 8 (count f))
                      (every? #(and (integer? %) (<= 0 (long %) 7)) f))
         (throw (ex-info "E8 exotype must be a vector of 8 integer entries in 0..7"
                         {:exotype f})))
       (into {} (map-indexed (fn [k j] [(nth tt k) (nth tt (long j))]) f))))))

(def ^:dynamic *allow-end8?*
  "DESIGN-P008b §7 item 1: \"Non-injective vectors are accepted only under an
   explicit config flag `:allow-end8? true`; without it, `s8->sigma`'s throw
   stays in place. That keeps G1/G1b fail-closed.\" Bound around an End(8)
   trajectory (the CA path is single-threaded, ca/core.clj:92); default
   false, so every existing call site keeps today's behaviour."
  false)

(defn sigma-of
  "Neighbourhood map for EXOTYPE: a named kind (looked up in `propagators`) or an
   S8 vector (converted). The single access point for sigma under P-008.
   P-008b §7 item 1: with *allow-end8?* bound true, a NON-INJECTIVE vector
   goes through e8->sigma; in every other case exactly today's path -- a
   permutation always goes through s8->sigma, flag or not, so the S8 path is
   byte-identical under the flag."
  [exotype]
  (if (s8? exotype)
    (if (and *allow-end8?* (not (perm? exotype)))
      (e8->sigma exotype)
      (s8->sigma exotype))
    (get propagators exotype)))

(defn assert-exotype-allowed!
  "P-008b §7 item 3 (amended; lon-claude-4 ruling 2026-11-15): fail-closed
   guard for the memoised S8 wrappers. A vector exotype that is not a
   permutation of 0..7 throws (same message family as `s8->sigma`) unless
   `*allow-end8?*` is bound true; keywords and permutations pass, returning
   nil. Wrappers call this on every exotype argument BEFORE the memo lookup,
   so a value cached under a flag-on call can never be served to a flag-off
   caller."
  [exotype]
  (when (and (s8? exotype)
             (not (perm? exotype))
             (not *allow-end8?*))
    (throw (ex-info "S8 exotype is not a permutation of 0..7 (End(8) needs *allow-end8?* true)"
                    {:exotype exotype})))
  nil)

(defn kind->s8
  "The S8 vector of a named kind (same sigma, as data)."
  [kind]
  (let [tt (vec ca/truth-table-3) sigma (get propagators kind)]
    (mapv #(long (.indexOf ^java.util.List tt (get sigma (nth tt %) (nth tt %)))) (range 8))))

(defn- random-s8 []
  (let [a (long-array (range 8))]
    (doseq [i (range 7 0 -1)]
      (let [j (ca/rnd-int (inc i)) t (aget a i)]
        (aset a i (aget a j)) (aset a j t)))
    (vec a)))

(def cycle-types
  "The 22 partitions of 8, largest part first."
  (letfn [(parts [n mx] (if (zero? n) [[]]
                          (for [k (range (min n mx) 0 -1) r (parts (- n k) k)] (cons k r))))]
    (mapv vec (parts 8 8))))

(defn cycle-type
  "Cycle type of an S8 vector, as a descending vector. P-008b §7 item 2: the
   argument must be a permutation; on a non-permutation the walk below can
   loop forever, so a non-permutation throws instead."
  [p]
  (when-not (perm? p)
    (throw (ex-info "cycle-type requires a permutation; use fg-signature for End(8)"
                    {:exotype p})))
  (loop [seen #{} k 0 acc []]
    (cond (= k 8) (vec (sort > acc))
          (seen k) (recur seen (inc k) acc)
          :else (let [cyc (loop [j k c []]
                            (if (and (seq c) (= j k)) c (recur (long (nth p j)) (conj c j))))]
                  (recur (into seen cyc) (inc k) (conj acc (count cyc)))))))

(defn- random-s8-of-type [partition]
  (let [order (random-s8) p (long-array 8)]
    (loop [ps partition off 0]
      (when (seq ps)
        (let [n (first ps) cyc (subvec order off (+ off n))]
          (doseq [i (range n)] (aset p (long (nth cyc i)) (long (nth cyc (mod (inc i) n)))))
          (recur (rest ps) (+ off n)))))
    (vec p)))

;; --- P-008b §7 item 2: End(8) classes ---------------------------------------
;; Conjugation formula (stated per the packet's STOP clause): for a
;; permutation p of {0..7} used as a RELABELLING of positions, the conjugate
;; of f is g = p o f o p^-1, i.e. g[p(k)] = p(f(k)), so g[i] = p(f(p^-1(i))).
;; fg-class takes the lexicographically least such g over all 40,320 p.
;; This is the §7 item 2 construction as the packet states it; §12(e)
;; prescribes the linear-time AHU canonical form for V1 scale instead --
;; flagged to claude-1, not decided here.

(defn- fg-cycles
  "Vector of the cycles of f (vectors of positions), each sorted. Works for
   any End(8) vector."
  [f]
  (let [f (long-array f) seen (boolean-array 8) cycles (volatile! [])]
    (dotimes [k 8]
      (when-not (aget seen k)
        (let [cyc (loop [j k c []]
                    (cond (and (seq c) (= j k)) c
                          (or (aget seen j) (> (count c) 8)) nil
                          :else (recur (aget f j) (conj c j))))]
          (when cyc
            (doseq [j cyc] (aset seen j true))
            (vswap! cycles conj (vec (sort cyc)))))))
    @cycles))

(def ^:private uie-sets
  "U/I/E index sets (§4). Index i names neighbourhood value x = 7 - i
   (mirror-test convention), so value 0 <-> index 7, value 7 <-> index 0.
   U = {000,111} -> indices {0,7}; I = {010,101} -> indices {2,5};
   E = edges = the remaining four values {001,011,100,110} -> indices
   {1,3,4,6}. E-as-complement is an assumption, stated here and flagged."
  {:u #{0 7} :i #{2 5} :e #{1 3 4 6}})

(defn- uie-counts [positions]
  (let [{:keys [u i e]} uie-sets]
    {:u (count (filter u positions))
     :i (count (filter i positions))
     :e (count (filter e positions))}))

(defn- hunting-positions
  "Union hunting rule (§1, derived 2026-11-15): j hunts in some class iff
   A(j) = {j} plus every k with f^n(k) = j, n >= 1, contains an odd cycle
   (a fixed point included) or at least two unwritten positions (positions
   not in im(f)). Unwritten positions never hunt."
  [f]
  (let [cycles (fg-cycles f)
        odd-cycle-nodes (set (mapcat #(nth cycles %)
                                     (filter #(odd? (count (nth cycles %)))
                                             (range (count cycles)))))
        unwritten (set (remove (set f) (range 8)))
        ancestors (fn [j]
                    (let [f' (long-array f)]
                      (loop [up #{} k 0]
                        (if (= k 8)
                          (conj up j)
                          (recur (if (loop [x (aget f' k) n 1]
                                     (cond (= x j) true
                                           (> n 8) false
                                           :else (recur (aget f' x) (inc n))))
                                   (conj up k) up)
                                 (inc k))))))]
    (set (filter (fn [j]
                   (let [a (ancestors j)]
                     (or (some odd-cycle-nodes a)
                         (<= 2 (count (filter unwritten a))))))
                 (range 8)))))

(defn fg-signature
  "Covariates of an End(8) map f, as a map. §4's list, quoted:
    - rank; number of components; cycle-part partition; number of fixed
      points; whether all cycles are even (whether immune bytes exist);
      in-degree multiset; maximum tree height; number of hunting positions,
      i.e. positions that can never settle; im(f) ∩ U/I/E and unwritten ∩
      U/I/E, with U = {000, 111}, I = {010, 101}, E = edges.
  Implementation notes: in-degree multiset is {in-degree -> count};
  cycle-part is the cycle type of f restricted to its recurrent set,
  descending; hunting positions follow the §1 union rule; the E set is the
  complement of U ∪ I in index space (assumption, see uie-sets)."
  [f]
  (when-not (and (vector? f) (= 8 (count f))
                 (every? #(and (integer? %) (<= 0 (long %) 7)) f))
    (throw (ex-info "fg-signature requires an End(8) vector" {:exotype f})))
  (let [f' (vec f)
        cycles (fg-cycles f')
        im (set f')
        cycle-node? (set (mapcat identity cycles))
        height (fn [k]
                 (loop [x k n 0]
                   (if (cycle-node? x) n (recur (nth f' x) (inc n)))))
        max-height (reduce max (map height (range 8)))
        hunts (hunting-positions f')]
    {:rank (count im)
     :components (count cycles)
     :cycle-part (vec (sort > (map count cycles)))
     :fixed-points (count (filter #(= 1 (count %)) cycles))
     :all-cycles-even? (every? even? (map count cycles))
     :in-degree-multiset (into (sorted-map) (frequencies f'))
     :max-tree-height (long max-height)
     :hunt-count (count hunts)
     :hunting-positions (vec (sort hunts))
     :im-uie (uie-counts im)
     :unwritten-uie (uie-counts (set (remove im (range 8))))}))

(def ^:private s8-relabellings
  "All 40,320 permutations and their inverses as two flat long-arrays
   (permutation c occupies (* 8 c) .. (+ 8 c) - 1), so the fg-class inner
   loop stays in primitives."
  (delay
    (let [n 40320
          ps (long-array (* 8 n))
          invs (long-array (* 8 n))
          idx (atom 0)]
      (letfn [(go [used perm]
                (if (= 8 (count perm))
                  (let [c (long (dec (swap! idx inc)))
                        p (long-array 8) q (long-array 8)]
                    (dotimes [k 8] (aset p k (long (nth perm k))))
                    (dotimes [k 8] (aset q (aget p k) k))
                    (dotimes [k 8]
                      (aset ps (+ (* 8 c) k) (aget p k))
                      (aset invs (+ (* 8 c) k) (aget q k))))
                  (dotimes [j 8]
                    (when-not (used j)
                      (go (conj used j) (conj perm j))))))]
        (go #{} []))
      [ps invs])))

(def fg-class
  "Canonical form of an End(8) map: the lexicographically least relabelling
   over the 40,320 conjugations (\u00a77 item 2), memoised. Conjugation:
   g[i] = p(f(p^-1(i))). NOTE \u00a712(e) prescribes the linear-time AHU
   canonical form for V1 scale; this is the \u00a77 item 2 brute force as the
   packet states it (cold-call wall time reported in the packet's tests)."
  (memoize
   (fn [f]
     (let [[^longs ps ^longs invs] @s8-relabellings
           f (long-array f)
           best (long-array 8)
           tmp (long-array 8)]
       (dotimes [i 8] (aset best i (aget f i)))
       (loop [c 0]
         (when (< c 40320)
           (let [off (long (* 8 c))]
             (dotimes [i 8]
               (aset tmp i (aget ps (+ off (aget f (aget invs (+ off (long i))))))))
             (loop [i 0]
               (when (< i 8)
                 (let [t (aget tmp i) b (aget best i)]
                   (cond (< t b) (do (dotimes [k 8] (aset best k (aget tmp k)))
                                     (recur 8))
                         (= t b) (recur (inc i)))))))
           (recur (inc c))))
       (vec best)))))

(def ^:private e8-strata
  "Cache: stratum [rank cycle-part] -> vector of members packed as 24-bit
   longs (entry k = bits 3k)."
  (atom {}))

(defn- cycle-parts-of
  "Cycle-part (descending) of any End(8) vector: the multiset of cycle
   lengths of f restricted to its recurrent set."
  [f]
  (let [fa (long-array f)
        done (boolean-array 8)
        parts (volatile! [])]
    (dotimes [k 8]
      (when-not (aget done k)
        (let [local (volatile! {k 0})]
          (loop [j (aget fa k) n 1]
            (cond (aget done j) nil
                  (contains? @local j)
                  (vswap! parts conj (- n (get @local j)))
                  :else (do (vswap! local assoc j n)
                            (recur (aget fa j) (inc n)))))
          (doseq [j (keys @local)]
            (aset done j true)))))
    (vec (sort > @parts))))

(defn- e8-stratum-members
  "All End(8) maps whose signature has RANK and CYCLE-PART, packed. One
   full 8^8 enumeration pass per stratum (chunk-parallel), cached. The
   filter uses the cheap rank + cycle-parts-of, not full fg-signature
   (hunting positions are too heavy for 16.7M maps)."
  [rank cycle-part]
  (let [m (get @e8-strata [rank cycle-part])]
    (if m
      m
      (let [total 16777216
            chunk 1048576
            pack1 (fn pack1 [n]
                    (loop [x n k 0 acc 0]
                      (if (= k 8)
                        acc
                        (recur (quot x 8) (inc k)
                               (+ acc (* (mod x 8)
                                         (bit-shift-left 1 (* 3 k))))))))
            scan (fn scan [c]
                   (let [lo (* c chunk)
                         hi (min total (* (inc c) chunk))
                         out (java.util.ArrayList.)]
                     (loop [n lo]
                       (when (< n hi)
                         (let [f (long-array 8)]
                           (loop [x n k 0]
                             (when (< k 8)
                               (aset f k (long (mod x 8)))
                               (recur (quot x 8) (inc k))))
                           (let [seen-img (boolean-array 8)
                                 _ (dotimes [k 8]
                                     (aset seen-img (aget f k) true))
                                 r (loop [k 0 acc 0]
                                     (if (= k 8)
                                       acc
                                       (recur (inc k)
                                              (if (aget seen-img k)
                                                (inc acc)
                                                acc))))
                                 cp (cycle-parts-of f)]
                             (when (and (= r rank) (= cycle-part cp))
                               (.add out (pack1 n))))
                         (recur (inc n)))))
                     (vec out)))
            members (vec (mapcat identity (pmap scan (range 16))))]
        (swap! e8-strata assoc [rank cycle-part] members)
        members))))

(defn random-e8-of-stratum
  "Uniform draw from the stratum (rank, cycle-part) of §4's allocation.
   Uses the engine's seeded RNG convention: ca/rnd-int under ca/*rng* (same
   as random-s8 / random-s8-of-type), so ca/with-seed makes the draws
   reproducible. Members are enumerated exhaustively (one cached 8^8 pass
   per stratum), so the draw is exactly uniform over the stratum."
  [rank cycle-part]
  (let [members (e8-stratum-members rank cycle-part)
        n (count members)]
    (when (zero? n)
      (throw (ex-info "empty stratum" {:rank rank :cycle-part cycle-part})))
    (let [packed (nth members (ca/rnd-int n))
          f (long-array 8)]
      (dotimes [k 8]
        (aset f k (long (mod (bit-shift-right packed (* 3 k)) 8))))
      (vec f))))

(defn boring?
  "The three-bit circular phenotype neighbourhood is uniform."
  [phenotype index]
  (let [width (count phenotype)
        at #(nth phenotype (mod % width))]
    (= (at (dec index)) (at index) (at (inc index)))))

(defn initial-grid
  "Create the exotype grid for ARM. Transmitting arms share the same seeded
   heterogeneous initialization as :heterogeneous-fixed."
  [arm width]
  (case arm
    :uniform-fixed (vec (repeat width :builder))
    :s8-uniform (vec (repeatedly width random-s8))
    :s8-stratified (vec (repeatedly width #(random-s8-of-type (ca/rnd-nth cycle-types))))
    (:heterogeneous-fixed :conformist :boring-triggered)
    (vec (repeatedly width #(ca/rnd-nth exotype-kinds)))
    (throw (ex-info "unknown exotype-grid arm" {:arm arm :available arms}))))

(defn- local-majority [grid index]
  (let [width (count grid)
        self (nth grid index)
        values [(nth grid (mod (dec index) width))
                self
                (nth grid (mod (inc index) width))]
        winner (first (for [[value n] (frequencies values) :when (>= n 2)] value))]
    (or winner self)))

(defn transmit
  "Advance exotypes synchronously under ARM. No policy reads global state."
  [arm grid phenotype]
  (case arm
    (:uniform-fixed :heterogeneous-fixed :s8-uniform :s8-stratified) grid
    :conformist
    (mapv #(local-majority grid %) (range (count grid)))
    :boring-triggered
    (mapv (fn [index]
            (if (boring? phenotype index)
              (nth grid (mod (dec index) (count grid)))
              (nth grid index)))
          (range (count grid)))
    (throw (ex-info "unknown exotype-grid arm" {:arm arm :available arms}))))

(defn apply-exotype
  "Apply EXOTYPE to a local rule. At transfer fraction Q, the rule source is
   taken from the fixed offset +1 neighbour with probability Q; otherwise it
   remains the cell's own rule. The three-argument arity is the byte-compatible
   legacy path."
  ([sigil exotype draw-seed]
   (ca/with-mixed-seed draw-seed
     (ca/sigil-for (#'gen/rule-permute (ca/bits-for (str sigil))
                                       (sigma-of exotype)))))
  ([sigil neighbour-sigil exotype q draw-seed]
   (when-not (<= 0.0 (double q) 1.0)
     (throw (ex-info "transfer fraction must be in [0,1]" {:transfer-fraction q})))
   (if (zero? (double q))
     (apply-exotype sigil exotype draw-seed)
     (ca/with-mixed-seed draw-seed
       (let [source (if (< (ca/rnd) (double q))
                      neighbour-sigil
                      sigil)]
         (ca/sigil-for (#'gen/rule-permute (ca/bits-for (str source))
                                           (sigma-of exotype))))))))

(defn blend-rule
  "Deterministically blend LEFT and RIGHT around CENTRE, following the 2014
   neighbour-agreement rule. Where the neighbours agree, retain their bit;
   where they disagree, evaluate CENTRE on that (left, centre, right) triple."
  [left centre right]
  (let [left-bits (ca/bits->ints (ca/bits-for (str left)))
        centre-bits (ca/bits->ints (ca/bits-for (str centre)))
        right-bits (ca/bits->ints (ca/bits-for (str right)))
        centre-rule (ca/local-rule-table (str centre))]
    (ca/sigil-for
     (ca/ints->bits
      (mapv (fn [left-bit centre-bit right-bit]
              (if (= left-bit right-bit)
                left-bit
                (get centre-rule (str left-bit centre-bit right-bit))))
            left-bits centre-bits right-bits)))))

(def ^:private blend-stream-tag
  "Separates the blend coin's stream from the propagator draw's."
  0x0B1E4D)

(defn apply-exotype-blend
  "Choose the complete two-neighbour blend with probability BETA, otherwise
   choose SIGIL, then apply EXOTYPE. The blend coin has a deterministic stream
   distinct from the propagator draw. TRANSFER-FRACTION retains its existing
   optional source-transfer semantics after this selection."
  [left sigil right exotype transfer-fraction beta draw-seed]
  (when-not (<= 0.0 (double beta) 1.0)
    (throw (ex-info "blend strength must be in [0,1]" {:blend-strength beta})))
  (if (zero? (double beta))
    (apply-exotype sigil right exotype transfer-fraction draw-seed)
    (let [blended (blend-rule left sigil right)
          ;; Stream tag, then avalanche-mix. The previous form XORed the seed
          ;; with 0x5DEECE66D -- the very constant java.util.Random XORs it back
          ;; with -- so it cancelled the scramble instead of adding entropy.
          blend? (ca/with-mixed-seed (bit-xor (long draw-seed) blend-stream-tag)
                   (< (ca/rnd) (double beta)))
          source (if blend? blended sigil)]
      (apply-exotype source right exotype transfer-fraction draw-seed))))

(defn- phenotype-step [genotype phenotype]
  (let [width (count genotype)]
    (apply str
           (for [index (range width)]
             (ca/evolve-digits-by-rule
              (str (nth phenotype (mod (dec index) width)))
              (str (nth phenotype index))
              (str (nth phenotype (mod (inc index) width)))
              (ca/bits-for (str (nth genotype index))))))))

(defn- expressed-grid
  [genotype exotypes transfer-fraction blend-strength seed time]
  (let [width (count genotype)]
    (mapv (fn [index sigil exotype]
            (apply-exotype-blend
             (nth genotype (mod (dec index) width))
             sigil
             (nth genotype (mod (inc index) width))
             exotype transfer-fraction blend-strength
             (+ (long seed) (* (long time) width) index)))
          (range width) genotype exotypes)))

(defn step
  "Advance phenotype, genotype, and exotype grids synchronously."
  [{:keys [genotype phenotype exotypes arm seed time transfer-fraction blend-strength
           selection-strength fitness-kind write-back? expressed previous-expressed
           selection-window]
    :or {seed 0 time 0 transfer-fraction 0.0 blend-strength 0.0
         selection-strength 0.0 fitness-kind :preferences write-back? true}
    :as state}]
  (let [selection-family? (or (contains? state :selection-strength)
                              (contains? state :fitness-kind)
                              (contains? state :write-back?)
                              (false? write-back?))
        behaviour-rules (if selection-family? (or expressed genotype) genotype)
        selection-result
        (when selection-family?
          (selection/advance
           {:genotypes genotype
            :expressed behaviour-rules
            :previous-expressed (or previous-expressed behaviour-rules)
            :phenotype phenotype
            :window selection-window
            :fitness-kind fitness-kind
            :selection-strength (if write-back? 0.0 selection-strength)
            :draw-seed (bit-xor (+ (long seed) (* (long time) (count genotype)))
                                0xC0FFEE)}))
        heritable-base (if (and selection-family? (false? write-back?))
                         (:genotype selection-result)
                         genotype)
        next-expressed (expressed-grid heritable-base exotypes transfer-fraction
                                       blend-strength seed time)
        next-genotype (if (and selection-family? (false? write-back?))
                        heritable-base
                        next-expressed)
        advanced
        {:arm arm
         :seed seed
         :time (inc time)
         :phenotype (phenotype-step behaviour-rules phenotype)
         :genotype next-genotype
         ;; The EGO that produced this NEXT. Not new state: the five-read
         ;; breakdown (LEFT RIGHT EGO NEXT PHENO) already implies it -- NEXT's
         ;; previous genotype IS the EGO, so from generation 2 onward "previous"
         ;; is available in the standard exotype model and was merely not being
         ;; written down (Joe, 2026-08-04). Only EGO's own predecessor at
         ;; generation 1 is genuinely undefined.
         ;;
         ;; `self-tuning/step` has always carried it; `grid/step` did not, which
         ;; left `local-observation`'s :static? and :hungry? structurally false
         ;; past t=0. TN-baldwin-reboot.md 24-25.
         :previous-genotype genotype
         :exotypes (transmit arm exotypes phenotype)}]
    (cond-> advanced
      (contains? state :transfer-fraction)
      (assoc :transfer-fraction transfer-fraction)

      (contains? state :blend-strength)
      (assoc :blend-strength blend-strength)

      selection-family?
      (assoc :selection-strength selection-strength
             :fitness-kind fitness-kind
             :write-back? write-back?
             :expressed next-expressed
             :previous-expressed behaviour-rules
             :selection-window (:window selection-result)))))

(defn run-steps [state steps]
  (nth (iterate step state) steps))
