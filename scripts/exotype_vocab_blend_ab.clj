(ns exotype-vocab-blend-ab
  "Crossed A/B: exotype VOCABULARY x lateral COUPLING (blend strength).

   Motivation (TN-baldwin-reboot.md 32). P2 showed the absorbing-byte axis governs
   freezing. But the vertical striping visible even in the never-freezing arm is
   NOT freezing -- at blend 0, `apply-exotype` reads only the cell's own sigil, so
   the genotype field is `width` independent chains and no choice of sigma can
   produce lateral structure. Coupling is the binding constraint, and the two
   interact: absorbing bytes plus strong blend collapse the field to uniformity.
   Hence a CROSS, not two sweeps.

   Metrics, per cell of the cross:
     vertical-persistence  P(cell equals itself one step later)
     lateral-agreement     P(cell equals its right neighbour); chance is ~1/256
     frozen-fraction       share of cells sitting on an absorbing byte at the end
     png-bytes             computed LOCALLY after retrieval, see below

   PNG SIZE AS A COMPLEXITY PROXY (Joe, 2026-08-04). A compressed spacetime
   diagram is a cheap stand-in for algorithmic complexity: frozen fields compress
   hard, structured ones less, noise least. It is only comparable if every image
   is produced identically, so this script writes PPMs and the conversion happens
   in ONE place with ONE encoder invocation. Do not convert piecemeal.
   It is validated, not assumed: the report checks it against the two structure
   measures above, which were computed independently of any image.

   Partitioned by CONDITION per futon0/README-bare-metal.md 5 -- one process per
   vocabulary arm, each sweeping the blend levels.

     clojure -M scripts/exotype_vocab_blend_ab.clj run <kind> <outdir>
     clojure -M scripts/exotype_vocab_blend_ab.clj report <out.md> <in.edn>..."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.mmca.render :as render]
            [futon5.xenotype.generator :as gen]))

(def config
  {:vocab [:odd53 :even1 :collapser :even8 :even4]
   :blends [0.0 0.1 0.25 0.5 0.75]
   :seeds 40 :seed-base 20260804 :width 80 :steps 200
   :figure-seed 20260804 :figure-steps 220})

(defn absorbing-bytes [kind]
  (let [pos (gen/sigma-positional (get grid/propagators kind))
        bit (fn [b i] (bit-and (bit-shift-right b (- 7 i)) 1))]
    (set (for [b (range 256)
               :when (every? #(not= (bit b (nth pos %)) (bit b %)) (range 8))]
           (str (ca/sigil-for (str/replace (format "%8s" (Integer/toBinaryString b)) " " "0")))))))

(defn- initial [kind seed]
  (let [w (:width config)]
    (ca/with-seed seed
      {:arm :heterogeneous-fixed :seed seed :time 0
       :exotypes (vec (repeat w kind))
       :genotype (vec (ca/random-sigil-string w))
       :phenotype (ca/random-phenotype-string w)})))

(defn- one-seed [kind beta seed]
  (let [w (:width config)
        absorbing (absorbing-bytes kind)
        st (assoc (initial kind seed) :blend-strength beta)]
    (loop [s st t 0 vp 0 la 0 n 0]
      (if (= t (:steps config))
        {:vp (/ (double vp) n) :la (/ (double la) n)
         :frozen (/ (count (filter #(absorbing (str %)) (:genotype s))) (double w))}
        (let [nx (grid/step s)
              g (mapv str (:genotype s))
              g2 (mapv str (:genotype nx))]
          (recur nx (inc t)
                 (+ vp (count (filter true? (map = g g2))))
                 (+ la (count (filter #(= (nth g %) (nth g (mod (inc %) w))) (range w))))
                 (+ n w)))))))

(defn- figure! [kind beta outdir]
  (let [st (assoc (initial kind (:figure-seed config)) :blend-strength beta)
        path (format "%s/fig-%s-b%s.ppm" outdir (name kind) (str/replace (str beta) "." "p"))]
    (loop [s st t 0 g [] p []]
      (if (> t (:figure-steps config))
        (do (io/make-parents path)
            (render/write-ppm! path (render/render-history-phenotype g p)
                               :comment (str (name kind) " beta=" beta))
            path)
        (recur (grid/step s) (inc t)
               (conj g (apply str (:genotype s)))
               (conj p (str (:phenotype s))))))))

(defn run-arm [kind outdir]
  (let [seeds (range (:seed-base config) (+ (:seed-base config) (:seeds config)))
        mean (fn [xs] (/ (reduce + xs) (double (count xs))))]
    {:arm kind
     :absorbing (count (absorbing-bytes kind))
     :rate (gen/rule-change-rate (get grid/propagators kind))
     :cells (vec (for [beta (:blends config)]
                   (let [rs (map #(one-seed kind beta %) seeds)]
                     {:blend beta
                      :vertical-persistence (mean (map :vp rs))
                      :lateral-agreement (mean (map :la rs))
                      :frozen-fraction (mean (map :frozen rs))
                      :figure (figure! kind beta outdir)})))
     :config config}))

(defn -main [& [mode a & more]]
  (case mode
    "run" (let [kind (keyword a)
                out (first more)
                r (run-arm kind out)]
            (spit (format "%s/%s.edn" out (name kind)) (pr-str r))
            (println (format "%s absorbing=%d -> %s/%s.edn"
                             a (:absorbing r) out (name kind))))
    "report"
    ;; Figures are resolved next to the EDN being read, not at the path recorded
    ;; in it: that path is wherever the run happened (a remote /tmp), and the PNGs
    ;; are converted after retrieval.
    (let [pairs (map (fn [f] [(.getParent (io/file f)) (edn/read-string (slurp f))]) more)
          png-for (fn [dir fig]
                    (io/file dir (str/replace (.getName (io/file fig)) ".ppm" ".png")))
          rs (sort-by (comp :absorbing second) pairs)]
      (spit a
            (str "# Vocabulary x coupling — crossed A/B\n\n"
                 (format "%d seeds, width %d, %d steps.\n\n"
                         (:seeds config) (:width config) (:steps config))
                 "`lateral agreement` at chance is ~0.004 (1/256). `png bytes` is the "
                 "representative spacetime compressed with one fixed encoder; larger = less "
                 "compressible = more structure.\n\n"
                 "| arm | absorbing | blend | vertical persistence | lateral agreement | frozen | png bytes |\n"
                 "|---|---:|---:|---:|---:|---:|---:|\n"
                 (str/join "\n"
                   (for [[dir r] rs c (:cells r)]
                     (let [png (png-for dir (:figure c))
                           b (if (.exists png) (.length png) -1)]
                       (format "| `%s` | %d | %.2f | %.4f | %.4f | %.2f | %s |"
                               (name (:arm r)) (:absorbing r) (:blend c)
                               (:vertical-persistence c) (:lateral-agreement c)
                               (:frozen-fraction c)
                               (if (neg? b) "—" (str b))))))
                 "\n"))
      (println "wrote" a))
    (println "usage: run <kind> <outdir> | report <out.md> <in.edn>...")))

(apply -main *command-line-args*)
