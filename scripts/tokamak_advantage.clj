(ns tokamak-advantage
  "TOKAMAK run 4: state-conditioned per-window advantage, with rendered traces.

  HISTORY OF THIS EXPERIMENT (read before trusting any number here)

  Run 1  Greedy one-step controller LOST to the best fixed propagator (.2291 v .2464).
         Diagnosis offered: myopia -- it picked rotate+1 (Figure 8), whose transport
         starts at .319, the highest in the set, and decays to exactly 0.
  Run 2  Gave it memory across runs (Monte Carlo return G_w). It converged to
         'always rotate+2' and LOST to greedy on held-out seeds (.2354 v .2396).
         TWO defects found afterwards, both mine:
         (a) CREDIT CONTAMINATION. G_w credits an action with transport realised from
             its window to the END of the run -- but later windows ran DIFFERENT
             propagators. rotate+1 scored .226 (not the ~0 I predicted) because
             rotate+2 carried the rest of the run. The trap was BAILED OUT by its
             successors, then never revisited (n=1), so the estimate never corrected.
         (b) The claim was scored across DIFFERENT SEEDS for different arms. Sloppy.
  Run 2's real finding, which reframes run 1: greedy picked rotate+1 5/24 times on the
  held-out seeds and WON. memo picked it 0/24 and LOST. A single window of rotate+1 is
  the best move in the set; only HOLDING it is fatal (fixed arm = .0000). Greedy
  re-probes every window, so it never holds anything -- it surfs the transient and
  leaves. Its 'myopia' was adaptive. THE TRAP IS ONLY A TRAP IF HELD.

  RUN 4 = that hypothesis, made testable.

    A(a | s) = r_w + V(s_{w+1}) - V(s_w)

  Why not a plain per-window score: r_w alone IS the one-step probe, which the trap
  defeats by construction. The damage is not in rotate+1's window, it is in the STATE
  IT LEAVES. V is what sees that. The -V(s_w) term stops a propagator taking credit for
  merely arriving somewhere already good.

  Why STATE-CONDITIONED Q[phi][a] and not marginal Q[a]: marginal Q averages 'rotate+1
  from a rich state' (excellent) with 'rotate+1 from a collapsing state' (fatal) into
  the meaningless .226 run 2 recorded. Conditioning separates them -- and a marginal Q
  cannot express a switching policy at all, it just picks one action forever, which was
  the third defect in my killed run-3 script.

  V IS LEARNED, NOT HAND-CODED. Offline, two passes, no bootstrapping:
    PASS 1  40 UNIFORM RANDOM runs. Random is deliberate (unbiased coverage of the trap,
            not greedy's once-and-never-again) and cheap (6 segments/run v greedy's 36).
    FIT     V[phi] = mean G_w over transitions from phi
            Q[phi][a] = mean (r + V[phi'] - V[phi]), backing off to marginal when thin
    PASS 2  held-out seeds, NEVER learned on. Advantage v greedy v all 5 fixed arms,
            ALL ON THE SAME SEEDS (fixing run 2 defect (b)).

  NOTE G_w survives here but ONLY to fit V over STATES -- never as an action score.
  That was run 2's error: G_w is a fine estimate of 'how good was this situation' and a
  terrible estimate of 'how good was this action'.

  STATE FEATURE is binned distinct-rule count. This is NOT the objective -- 'preserve
  diversity' was rejected as an objective and stays rejected (run 1: the winning arm
  ended on 16.7 rules, the loser on 34.3). It is a feature for V, a different job:
  collapse is what kills a run, so it is what V must be able to see.

  PREDICTIONS, ON THE RECORD (so they can fail):
    P1  Q[:rich][rotate+1] > 0  -- from a rich state the trap is a GOOD move
    P2  Q[:collapsing][rotate+1] << 0 and Q[:dead][rotate+1] << 0 -- holding is what kills
    P3  the advantage arm SWITCHES (does not converge to one action) and takes rotate+1
        from rich states only
  P1+P2 together are 'the trap is only a trap if held'. If Q[rotate+1] is uniformly
  mid-field across bins, that hypothesis is dead and run 1's myopia story needs
  rethinking, not repairing."
  (:require [futon5.ca.core :as ca] [futon5.wiring.runtime :as rt]
            [futon5.xenotype.generator :as gen]
            [futon5.mmca.diagonal-transport :as transport]
            [futon5.mmca.render :as render]
            [clojure.data.json :as json]))

(def elisp-table ["000" "001" "010" "100" "011" "101" "110" "111"])
(defn nb [positional] (gen/positional-sigma->neighbourhood-sigma positional elisp-table))
(def actions
  {:rotate+2      (nb [2 3 4 5 6 7 0 1])
   :three+five    (nb [1 2 0 4 5 6 7 3])
   :sigma-5127    (nb [5 1 2 7 6 0 4 3])
   :rotate+1      (nb [1 2 3 4 5 6 7 0])   ; FIGURE 8 -- the trap
   :identity      (nb [0 1 2 3 4 5 6 7])})
(def ACTS (vec (keys actions)))

(defn diagram [sigma]
  {:nodes [{:id :ctx-pred :component :context-pred} {:id :ctx-self :component :context-self}
           {:id :ctx-succ :component :context-succ} {:id :combine :component :blend-cell}
           {:id :mutate :component :rule-permute :params {:sigma sigma}}
           {:id :out :component :output-sigil}]
   :edges [{:from :ctx-pred :from-port :sigil :to :combine :to-port :pred}
           {:from :ctx-self :from-port :sigil :to :combine :to-port :self}
           {:from :ctx-succ :from-port :sigil :to :combine :to-port :succ}
           {:from :combine :from-port :result :to :mutate :to-port :rule}
           {:from :mutate :from-port :result :to :out :to-port :sigil}]
   :output :out})

(def W 60) (def WINDOW 20) (def WINDOWS 6)
(def LEARN (vec (range 40)))
(def SCORE (vec (range 100 104)))
(def LAB "holes/labs/M-aif-tokamak")

(defn bits-rows [gs] (mapv (fn [s] (mapv #(Integer/parseInt (ca/bits-for (str %)) 2) (seq s))) gs))
(defn segment [g0 p0 action seed]
  (ca/with-seed seed
    (let [r (rt/run-wiring {:wiring {:diagram (diagram (get actions action))}
                            :genotype g0 :phenotype p0 :generations WINDOW})]
      {:genotype (last (:gen-history r)) :phenotype (last (:phe-history r))
       :gen-rows (vec (:gen-history r)) :phe-rows (vec (:phe-history r))})))
(defn transport-of [gen-rows]
  (let [rows (bits-rows gen-rows)
        planes (for [b (range 8)]
                 (mapv (fn [row] (mapv #(bit-and (bit-shift-right % b) 1) row)) rows))
        scores (for [p planes] (try (transport/median-score
                                     (transport/profile p {:window-size 10 :stride 5 :max-speed 3}))
                                    (catch Exception _ 0.0)))]
    (/ (reduce + scores) 8.0)))
(defn mean [xs] (if (seq xs) (/ (reduce + xs) (double (count xs))) 0.0))

(defn phi [g]
  (let [n (count (distinct (seq g)))]
    (cond (<= n 1) :dead (<= n 4) :collapsing (<= n 12) :lean (<= n 24) :mid :else :rich)))
(def BINS [:dead :collapsing :lean :mid :rich])

(defn init-state [seed]
  [(ca/with-seed seed (ca/random-sigil-string W))
   (ca/with-seed (inc seed) (apply str (repeatedly W #(if (< (ca/rnd) 0.5) "0" "1"))))])

(defn rollout [seed choose-fn]
  (let [[g0 p0] (init-state seed)]
    (loop [g g0 p p0 w 0 trans []]
      (if (>= w WINDOWS)
        trans
        (let [a (choose-fn g p w)
              s (segment g p a (+ seed w))]
          (recur (:genotype s) (:phenotype s) (inc w)
                 (conj trans {:w w :action a :phi (phi g) :phi' (phi (:genotype s))
                              :r (transport-of (:gen-rows s))
                              :gen-rows (:gen-rows s) :phe-rows (:phe-rows s)})))))))

(println "=== TOKAMAK 4: state-conditioned advantage  A(a|s) = r + V(s') - V(s) ===") (flush)
(def out (atom {}))
(defn flush! [] (spit "/tmp/tok4.json" (json/write-str @out)))

;; ---------- PASS 1 ----------
(println (format "\n--- PASS 1: %d random runs (unbiased coverage; no probes) ---" (count LEARN))) (flush)
(def traj
  (doall (for [seed LEARN]
           (let [rng (java.util.Random. (long (+ 7000 seed)))
                 t (rollout seed (fn [_ _ _] (nth ACTS (.nextInt rng (count ACTS)))))]
             (print ".") (flush)
             t))))
(println)

(def transitions
  (vec (mapcat (fn [t] (map-indexed (fn [i x]
                                      (-> x (assoc :G (transport-of (vec (mapcat :gen-rows (drop i t)))))
                                          (dissoc :gen-rows :phe-rows)))
                                    t))
               traj)))
(def V (into {} (map (fn [[k xs]] [k (mean (map :G xs))]) (group-by :phi transitions))))
(defn adv [x] (+ (:r x) (- (get V (:phi' x) 0.0) (get V (:phi x) 0.0))))
(def Qm (into {} (map (fn [[k xs]] [k {:q (mean (map adv xs)) :n (count xs)}])
                      (group-by :action transitions))))
(def Qsa (into {} (map (fn [[k xs]] [k {:q (mean (map adv xs)) :n (count xs)}])
                       (group-by (juxt :phi :action) transitions))))
(defn Q* "state-conditioned, backing off to marginal when thin"
  [s a] (let [c (get Qsa [s a])]
          (if (and c (>= (:n c) 3)) (:q c) (:q (get Qm a {:q 0.0})))))

(println "--- LEARNED V (state value, from returns) ---")
(doseq [k BINS] (when (V k) (println (format "  V[%-11s] = %.4f   (n=%d)" (name k) (V k)
                                             (count (filter #(= (:phi %) k) transitions))))))
(println "\n--- LEARNED Q[phi][action]  (per-window advantage; '.' = thin, backed off) ---")
(println (format "  %-12s %s" "action" (apply str (map #(format "%12s" (name %)) BINS))))
(doseq [a ACTS]
  (println (format "  %-12s %s%s" (name a)
                   (apply str (map (fn [s] (let [c (get Qsa [s a])]
                                             (if (and c (>= (:n c) 3))
                                               (format "%12.4f" (:q c)) (format "%12s" "."))))
                                   BINS))
                   (if (= a :rotate+1) "   <- THE TRAP" ""))))
(println "\n--- marginal Q[action] (what run 2 effectively used) ---")
(doseq [[a m] (sort-by (comp - :q val) Qm)]
  (println (format "  %-12s %8.4f  (n=%d)" (name a) (:q m) (:n m))))
(swap! out assoc :V (into {} (map (fn [[k v]] [(name k) v]) V))
       :Qm (into {} (map (fn [[k v]] [(name k) v]) Qm))
       :Qsa (into {} (map (fn [[[s a] v]] [(str (name s) "|" (name a)) v]) Qsa)))
(flush!) (flush)

;; ---------- PASS 2 ----------
(println (format "\n--- PASS 2: held-out seeds %s (all arms, SAME seeds) ---" SCORE))
(println (format "  %-12s %-54s %s" "arm" "actions" "transport  rules")) (flush)
(doseq [mode (into [:advantage :greedy] ACTS)]
  (let [rs (doall (for [seed SCORE]
                    (let [choose (case mode
                                   :advantage (fn [g _ _] (apply max-key #(Q* (phi g) %) ACTS))
                                   :greedy (fn [g p w] (->> ACTS
                                                            (map (juxt identity
                                                                       #(transport-of (:gen-rows (segment g p % (+ seed w))))))
                                                            (apply max-key second) first))
                                   (fn [_ _ _] mode))
                          t (rollout seed choose)]
                      {:t (transport-of (vec (mapcat :gen-rows t)))
                       :per-window (mapv :r t)
                       :rules (count (distinct (seq (last (:gen-rows (last t))))))
                       :actions (mapv (comp name :action) t)
                       :phis (mapv (comp name :phi) t)
                       :gen (vec (mapcat :gen-rows t)) :phe (vec (mapcat :phe-rows t))})))
        tm (mean (map :t rs))]
    (println (format "  %-12s %-54s %.4f     %.1f" (name mode)
                     (if (#{:advantage :greedy} mode) (pr-str (:actions (first rs))) "(fixed)")
                     tm (mean (map :rules rs))))
    ;; TRACE: 256-colour genotype | white sep | b/w phenotype -- the lab standard
    ;; (futon5.mmca.render), not a bespoke renderer. Every diagram gets a phenotype.
    (let [r0 (first rs)]
      (render/write-ppm! (str LAB "/tok4-trace-" (name mode) ".ppm")
                         (render/render-history-phenotype (:gen r0) (:phe r0))
                         :comment (str "tokamak4 " (name mode))))
    (swap! out update :arms (fnil conj [])
           {:mode (name mode) :transport tm :rules (mean (map :rules rs))
            :actions (:actions (first rs)) :phis (:phis (first rs))
            :per-window (mapv :per-window rs) :per-seed (mapv :t rs)})
    (flush!) (flush)))

(println "\n  run 1: best fixed .2464 / greedy .2291    run 2: memo .2354 / greedy .2396")
(println "wrote /tmp/tok4.json + tok4-trace-*.ppm") (flush)
