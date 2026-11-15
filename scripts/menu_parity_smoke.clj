;; Parity and smoke runs for the menu option on metaca-state (exotype
;; DESIGN-general 2b). Run from a futon5 checkout:
;;
;;   clojure -M scripts/menu_parity_smoke.clj MODE GAMMA KAPPA SEED STEPS [MENU-SEED N]
;;
;; MODE
;;   july        no menu: the July state. Runs on any futon5 checkout, including
;;               one without the menu option, so its output is the reference.
;;   named       menu = the 12 named kinds. Must print the same hashes as july.
;;   vectors     menu = the 12 named kinds written as permutation vectors. The
;;               operators are the same, but their rows are measured for this
;;               menu, so the trajectory is expected to differ from july.
;;   random      menu = N permutations drawn from S8 with MENU-SEED.
;;
;; Each line is a step number and the SHA-256 of (pr-str [genotype phenotype
;; exotypes]) at that step, then a census of the exotypes held at the end.
(require '[clojure.string :as str]
         '[futon5.exotype.grid :as grid]
         '[futon5.xenotype.generator :as gen])
(let [src (slurp "scripts/local_compressibility_grid.clj")]
  (load-string (str/replace src "(apply -main *command-line-args*)" "")))
(def ns* (find-ns 'local-compressibility-grid))
(def metaca-state @(ns-resolve ns* 'metaca-state))
(def metaca-step @(ns-resolve ns* 'checked-metaca-step))

(defn- sha [state]
  (let [d (java.security.MessageDigest/getInstance "SHA-256")
        text (pr-str [(:genotype state) (:phenotype state) (:exotypes state)])]
    (apply str (map #(format "%02x" %) (.digest d (.getBytes ^String text "UTF-8"))))))

(defn- random-menu [menu-seed n]
  (let [r (java.util.Random. (long menu-seed))]
    (loop [menu []]
      (if (= n (count menu))
        menu
        (let [l (java.util.ArrayList. ^java.util.Collection (range 8))
              _ (java.util.Collections/shuffle l r)
              sigma (vec l)]
          (recur (if (some #{sigma} menu) menu (conj menu sigma))))))))

(let [[mode g k s n menu-seed size] *command-line-args*
      gamma (Double/parseDouble g) kappa (Double/parseDouble k)
      seed (Long/parseLong s) steps (Long/parseLong n)
      menu (case mode
             "july" nil
             "named" grid/exotype-kinds
             "vectors" (mapv #(gen/sigma-positional (get grid/propagators %)) grid/exotype-kinds)
             "random" (random-menu (Long/parseLong menu-seed) (Long/parseLong size)))
      start (if menu (metaca-state gamma kappa seed menu) (metaca-state gamma kappa seed))]
  (println "mode" mode "gamma" gamma "kappa" kappa "seed" seed "steps" steps)
  (when menu (println "menu" (pr-str menu)))
  (loop [state start t 0]
    (when (or (zero? (mod t 50)) (= t steps))
      (println t (sha state)))
    (if (< t steps)
      (recur (metaca-step state) (inc t))
      (do (println "exotypes at end" (pr-str (sort-by (comp - val) (frequencies (:exotypes state)))))
          (println "distinct rules at end" (count (distinct (:genotype state))))))))
(shutdown-agents)
