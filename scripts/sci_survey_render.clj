#!/usr/bin/env bb
;; Render spacetime diagrams for the SCI detection sampler report.
;;
;; Produces:
;;   - Sigil-level spacetime diagrams for each wiring rule + champion
;;   - Bitplane decomposition views for Rule 110, Rule 30, and champion
;;
;; Usage:
;;   bb -cp src:resources scripts/sci_survey_render.clj

(require '[futon5.wiring.runtime :as wrt]
         '[futon5.mmca.runtime :as runtime]
         '[futon5.mmca.render :as render]
         '[futon5.ca.core :as ca]
         '[futon5.mmca.bitplane-analysis :as bp]
         '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clojure.java.shell :as shell])

(def out-dir "out/sci-survey")

(defn ensure-dir! [dir]
  (.mkdirs (java.io.File. dir)))

(defn ppm->png!
  "Convert PPM to scaled PNG via ImageMagick."
  [ppm-path png-path scale]
  (let [result (shell/sh "convert" ppm-path
                         "-scale" (str scale "%")
                         "-interpolate" "Nearest"
                         "-filter" "point"
                         png-path)]
    (when (zero? (:exit result))
      (println (str "    → " png-path))
      png-path)))

;; =============================================================================
;; Sigil-level rendering
;; =============================================================================

(defn render-sigil-spacetime!
  "Render a genotype history as a sigil-color spacetime diagram."
  [history label]
  (let [ppm-path (str out-dir "/" label ".ppm")
        png-path (str out-dir "/" label ".png")
        pixels (render/render-history history)]
    (render/write-ppm! ppm-path pixels :comment label)
    (ppm->png! ppm-path png-path 400)))

;; =============================================================================
;; Bitplane rendering
;; =============================================================================

(defn bitplane->pixels
  "Convert a binary bitplane history to grayscale RGB pixels.
   0 → white, 1 → black."
  [bitplane-history]
  (mapv (fn [row]
          (mapv (fn [bit]
                  (if (= bit 1) [0 0 0] [255 255 255]))
                row))
        bitplane-history))

(defn render-bitplane-grid!
  "Render all 8 bitplanes as a 2x4 grid."
  [history label]
  (let [bitplanes (bp/decompose-history history)
        ;; Render each bitplane
        panels (mapv bitplane->pixels bitplanes)
        ;; Dimensions
        rows (count (first panels))
        cols (count (first (first panels)))
        ;; Separator width
        sep 2
        ;; Grid: 2 columns x 4 rows of bitplanes
        grid-cols 2
        grid-rows 4
        total-w (+ (* grid-cols cols) (* (dec grid-cols) sep))
        total-h (+ (* grid-rows rows) (* (dec grid-rows) sep))
        ;; Build pixel array
        grey [128 128 128]
        pixels (vec
                (for [gy (range grid-rows)
                      y (concat (range rows) (when (< gy (dec grid-rows)) (range sep)))]
                  (vec
                   (for [gx (range grid-cols)
                         x (concat (range cols) (when (< gx (dec grid-cols)) (range sep)))]
                     (let [in-sep-x? (>= x cols)
                           in-sep-y? (>= y rows)
                           plane-idx (+ (* gy grid-cols) gx)]
                       (cond
                         (or in-sep-x? in-sep-y?) grey
                         (< plane-idx 8) (nth (nth (nth panels plane-idx) y) x)
                         :else grey))))))]
    (let [ppm-path (str out-dir "/" label "-bitplanes.ppm")
          png-path (str out-dir "/" label "-bitplanes.png")]
      (render/write-ppm! ppm-path pixels :comment (str label " bitplanes"))
      (ppm->png! ppm-path png-path 300))))

(defn render-single-bitplane!
  "Render a single bitplane (the best one)."
  [history plane-idx label]
  (let [bitplanes (bp/decompose-history history)
        pixels (bitplane->pixels (nth bitplanes plane-idx))
        ppm-path (str out-dir "/" label "-bp" plane-idx ".ppm")
        png-path (str out-dir "/" label "-bp" plane-idx ".png")]
    (render/write-ppm! ppm-path pixels :comment (str label " bitplane " plane-idx))
    (ppm->png! ppm-path png-path 400)))

;; =============================================================================
;; Coupling heatmap
;; =============================================================================

(defn coupling-matrix->pixels
  "Render an 8x8 coupling matrix as a heatmap."
  [matrix]
  (let [;; Find max off-diagonal value for normalization
        off-diag (for [i (range 8) j (range 8) :when (not= i j)]
                   (nth (nth matrix i) j))
        max-val (if (seq off-diag) (max 0.001 (apply max off-diag)) 0.001)
        cell-size 12
        label-size 0
        total (+ label-size (* 8 cell-size))]
    (vec
     (for [py (range (* 8 cell-size))]
       (vec
        (for [px (range (* 8 cell-size))]
          (let [i (quot py cell-size)
                j (quot px cell-size)
                val (nth (nth matrix i) j)
                ;; Heat color: black (0) → red → yellow → white (1)
                norm (if (= i j) 1.0 (min 1.0 (/ val max-val)))
                r (int (min 255 (* 255 (min 1.0 (* 2.0 norm)))))
                g (int (min 255 (* 255 (max 0.0 (- (* 2.0 norm) 1.0)))))
                b (int (min 255 (* 255 (max 0.0 (- (* 3.0 norm) 2.0)))))]
            [r g b])))))))

(defn render-coupling-heatmap!
  "Render the coupling matrix as a heatmap."
  [history label]
  (let [bitplanes (bp/decompose-history history)
        matrix (bp/coupling-matrix bitplanes)
        pixels (coupling-matrix->pixels matrix)
        ppm-path (str out-dir "/" label "-coupling.ppm")
        png-path (str out-dir "/" label "-coupling.png")]
    (render/write-ppm! ppm-path pixels :comment (str label " coupling matrix"))
    (ppm->png! ppm-path png-path 400)))

;; =============================================================================
;; MAIN
;; =============================================================================

(ensure-dir! out-dir)

(println "Rendering SCI survey spacetime diagrams...")
(println (str "Output: " out-dir "/\n"))

;; Wiring rules
(def wiring-rules
  [{:id "rule-030" :path "data/wiring-rules/rule-030.edn"}
   {:id "rule-054" :path "data/wiring-rules/rule-054.edn"}
   {:id "rule-090" :path "data/wiring-rules/rule-090.edn"}
   {:id "rule-110" :path "data/wiring-rules/rule-110.edn"}
   {:id "rule-184" :path "data/wiring-rules/rule-184.edn"}])

(def run-params {:width 120 :generations 200 :seed 352362012})

(def rule-results
  (mapv (fn [{:keys [id path]}]
          (println (str "  " id ":"))
          (let [wiring (edn/read-string (slurp path))
                genotype (wrt/random-genotype (:width run-params) (:seed run-params))
                result (wrt/run-wiring {:wiring wiring :genotype genotype
                                        :generations (:generations run-params)
                                        :collect-metrics? false})
                history (:gen-history result)]
            (println (str "    Running " (count history) " generations..."))
            ;; Sigil spacetime
            (render-sigil-spacetime! history id)
            ;; Bitplane grid for key rules
            (when (#{"rule-110" "rule-030" "rule-090" "rule-184"} id)
              (render-bitplane-grid! history id))
            ;; Best bitplane for Rule 110 and Rule 30
            (when (= id "rule-110")
              (render-single-bitplane! history 4 id))
            (when (= id "rule-030")
              (render-single-bitplane! history 0 id))
            ;; Coupling heatmap for Rule 184
            (when (= id "rule-184")
              (render-coupling-heatmap! history id))
            {:id id :history history}))
        wiring-rules))

;; Champion MMCA
(println "\n  champion:")
(let [result (runtime/run-mmca
              {:genotype "下为八尤午火大勿个土叫心五丸石扔巨刊认毛占术世日巴节土帅史忆击二亏风六力双毛厂无劝友风乎令兄心艺北刊白尤无劝只且飞日凡风布尸午田风及仓弓厂火书父车已飞一且付丸仅打从艺仪凡用电元井升犬刊刊支火不丸手白心二仗上乌由见元于刀允仔平一扔叮叫从大北印"
               :phenotype "000100000000101101110100111011111111111010101110000010000100011001110011110100000010110111010111000001011000110001000011"
               :generations 120
               :seed 352362012
               :kernel :mutating-template
               :exotype-mode :local-physics})
      gen-history (:gen-history result)
      phe-history (:phe-history result)]
  ;; Sigil spacetime with phenotype
  (let [pixels (render/render-run result)
        ppm-path (str out-dir "/champion.ppm")
        png-path (str out-dir "/champion.png")]
    (render/write-ppm! ppm-path pixels :comment "champion")
    (ppm->png! ppm-path png-path 400))
  ;; Bitplane grid
  (render-bitplane-grid! gen-history "champion")
  ;; Coupling heatmap
  (render-coupling-heatmap! gen-history "champion"))

;; Summary composite: side-by-side Rule 110 bp4 vs Rule 30 bp0
(println "\n  Generating comparison composites...")
(let [r110-history (:history (first (filter #(= "rule-110" (:id %)) rule-results)))
      r030-history (:history (first (filter #(= "rule-030" (:id %)) rule-results)))
      bp110 (nth (bp/decompose-history r110-history) 4)
      bp030 (nth (bp/decompose-history r030-history) 0)
      px110 (bitplane->pixels bp110)
      px030 (bitplane->pixels bp030)
      ;; Join side-by-side with separator
      sep-width 4
      grey [180 180 180]
      joined (mapv (fn [i]
                     (vec (concat (nth px110 i)
                                  (repeat sep-width grey)
                                  (nth px030 i))))
                   (range (min (count px110) (count px030))))
      ppm-path (str out-dir "/rule110-vs-rule030-bitplane.ppm")
      png-path (str out-dir "/rule110-vs-rule030-bitplane.png")]
  (render/write-ppm! ppm-path joined :comment "Rule 110 bp4 vs Rule 30 bp0")
  (ppm->png! ppm-path png-path 400))

(println "\nDone! Images in" out-dir)
