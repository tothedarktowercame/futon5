(ns propagator-render-selection
  "Render the frozen cluster exemplars to paired genotype/phenotype panels.

  WHY THIS IS A SEPARATE SCRIPT. The overnight census
  (scripts/propagator_index.clj) stores only a 121x256 rule-COUNT histogram per
  seed; it discards the 60 spatial cell positions, so genotype/phenotype panels
  cannot be reconstructed from it (codex-6's fail-closed finding, H-propagator-features
  -- correct, and it was my spec error to promise otherwise). The information exists at
  RUNTIME: run-propagator returns :gen and :phe spatial rows and the seam is
  deterministic ((random (format \"prop-%d\" seed))), so a targeted replay of the ~10
  selected sigmas is BIT-EXACT to the census rows the features were computed from -- same
  engine, same seed, same rows. This replays ONLY the frozen selection (10 runs, seconds),
  NOT the sim at large.

  THIS FILE IS NOT A FINGERPRINT INPUT (see scripts/propagator_index.clj source-files):
  running it does not disturb the overnight build. It also does NOT re-select attractive
  pictures -- the selection is read verbatim from cluster-selection.edn, which codex-6
  froze precisely so the choice cannot be gamed at render time.

  NO EoC CLAIM. Panels are labelled by sigma / cluster / role only. Cluster ids are
  structural (k=2 body/tail, silhouette .44 -- weak, and labelled weak). Transport,
  activity, class-4 are unanchored proxies (M-propagators 4b); none is an EoC verdict."
  (:require [clojure.string :as str] [clojure.edn :as edn] [clojure.java.io :as io]
            [clojure.java.shell :as sh]))

(def LAB "holes/labs/M-aif-tokamak/propagator-clusters")
(def SEL (str LAB "/cluster-selection.edn"))
(def WIDTH 60) (def STEPS 120) (def SEED 0)   ; seed 0 = the census's first seed
(def HARNESS "scripts/elisp-harness/run.el")

;; ---- read the frozen selection (verbatim; no re-selection) ----
(defn selections []
  (let [data (edn/read-string (slurp SEL))
        sels (or (:selections data) (:selection data)
                 (->> (tree-seq coll? seq data)
                      (filter #(and (map? %) (:sigma %) (:role %)))))]
    (when (empty? sels) (throw (ex-info "no selections found in cluster-selection.edn" {})))
    (mapv #(select-keys % [:sigma :cluster :role]) sels)))

;; ---- deterministic replay of ONE sigma via the authoritative elisp seam ----
;; The genotype is coloured by the LEGACY engine's own hex (3rd elt of
;; get-genotype-from-sigil), NOT by futon5.ca.core/color-for -- the legacy sigil
;; alphabet (一, χ, ¤, ...) is disjoint from futon5's, so the futon5 renderer silently
;; falls every legacy cell back to one default colour (the "uniform genotype" bug). These
;; are the exact colours 256ca.el assigned in eoc.png.
(defn replay
  "Return {:gen-hex [row-of-hex] :phe [bitstring]} for one perm, bit-exact to census."
  [perm]
  (let [expr (format
              (str "(progn (add-to-list 'load-path \"scripts/elisp-harness\")"
                   " (require 'run)"
                   " (let ((r (run-propagator '%s %d %d %d)))"
                   "   (princ \"GEN\\n\")"
                   "   (dolist (g (plist-get r :gen))"
                   "     (dolist (c (append g nil))"
                   "       (princ (third (get-genotype-from-sigil (char-to-string c)))) (princ \" \"))"
                   "     (princ \"\\n\"))"
                   "   (princ \"PHE\\n\")"
                   "   (dolist (p (plist-get r :phe)) (princ p) (princ \"\\n\"))))")
              (pr-str (vec perm)) SEED WIDTH STEPS)
        {:keys [out err exit]} (sh/sh "emacs" "-Q" "--batch"
                                      "-l" "scripts/elisp-harness/clcompat.el"
                                      "--eval" expr)]
    (when-not (zero? exit)
      (throw (ex-info "replay failed" {:perm perm :err (str/trim err)})))
    (let [lines (str/split-lines out)
          gi (.indexOf ^java.util.List lines "GEN")
          pi (.indexOf ^java.util.List lines "PHE")
          gen (->> (subvec (vec lines) (inc gi) pi) (remove str/blank?)
                   (mapv #(vec (str/split (str/trim %) #"\s+"))))
          phe (vec (remove str/blank? (subvec (vec lines) (inc pi) (count lines))))]
      {:gen-hex gen :phe phe})))

(defn hex->rgb [h]
  (let [h (str/replace (or h "#000000") "#" "")]
    [(Integer/parseInt (subs h 0 2) 16)
     (Integer/parseInt (subs h 2 4) 16)
     (Integer/parseInt (subs h 4 6) 16)]))

(defn panel-ppm!
  "Write [genotype (legacy hex) | white | phenotype (b/w)] as a P3 PPM."
  [path gen-hex phe comment]
  (let [white [255 255 255] black [0 0 0]
        rows (map (fn [ghex pbits]
                    (concat (map hex->rgb ghex) [white]
                            (map #(if (= \1 %) white black) pbits)))
                  gen-hex phe)
        w (count (first rows)) h (count rows)
        sb (StringBuilder. (str "P3\n# " comment "\n" w " " h "\n255\n"))]
    (doseq [row rows] (doseq [[r g b] row] (.append sb (str r " " g " " b " "))) (.append sb "\n"))
    (io/make-parents path)
    (spit path (.toString sb))))

(defn ppm->png [ppm]
  (let [png (str/replace ppm #"\.ppm$" ".png")]
    (sh/sh "convert" ppm png) (io/delete-file ppm true) png))

(defn -main [& _]
  (.mkdirs (io/file (str LAB "/panels")))
  (let [sels (selections)]
    (println (format "rendering %d frozen exemplars (seed %d, bit-exact to census)" (count sels) SEED))
    (doseq [{:keys [sigma cluster role]} sels]
      (let [{:keys [gen-hex phe]} (replay sigma)
            base (str LAB "/panels/c" cluster "-" role "-sigma-" (str/join "" sigma))
            ppm (str base ".ppm")]
        (panel-ppm! ppm gen-hex phe (format "cluster %s %s sigma %s" cluster role (str/join "" sigma)))
        (let [png (ppm->png ppm)
              ncol (count (distinct (mapcat identity gen-hex)))]
          (println (format "  c%s %-11s sigma %s  gen-rows=%d phe-rows=%d  genotype-colours=%d  -> %s"
                           cluster role (str/join "" sigma) (count gen-hex) (count phe) ncol png)))))
    (println "done; panels in" (str LAB "/panels/"))))

(-main)
