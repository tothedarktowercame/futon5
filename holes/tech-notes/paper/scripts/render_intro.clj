;; intro.edn -> intro-generated.tex
;;
;; The EDN holds the introduction's prose in a point/subpoint tree.  Rendering
;; concatenates it: each :point becomes a paragraph, its :subpoints the sentences
;; of that paragraph, in order.  Nothing is paraphrased, so the .tex is a pure
;; function of the .edn and the argument structure is editable as structure.
;;
;;   clojure -M scripts/render_intro.clj intro.edn intro-generated.tex
(require '[clojure.edn :as edn] '[clojure.string :as str])

(defn- unwrap
  "EDN strings are indented for readability; collapse to single-spaced prose."
  [s] (-> s str/trim (str/replace #"\s*\n\s*" " ") (str/replace #"\s{2,}" " ")))

(defn render [{:keys [section thesis points]}]
  (str "% GENERATED FROM intro.edn by scripts/render_intro.clj -- DO NOT EDIT.\n"
       "% Edit intro.edn and re-render; the argument structure lives there.\n"
       "\\section{" (:title section) "}\n"
       "\\label{" (:label section) "}\n\n"
       "% THESIS -- every point below serves this.\n"
       (unwrap thesis) "\n\n"
       (str/join "\n\n"
         (for [{:keys [id point subpoints]} points]
           ;; The point is the paragraph's LEAD SENTENCE, not a comment: the
           ;; structure has to survive rendering, or the .edn is decoration.
           (str "% [" (name id) "]\n"
                (unwrap point) " "
                (str/join " " (map unwrap subpoints)))))
       "\n"))

(let [[in out] *command-line-args*
      doc (edn/read-string (slurp in))]
  (assert (:thesis doc) "intro.edn must carry a :thesis")
  (assert (seq (:points doc)) "intro.edn must carry :points")
  (doseq [p (:points doc)]
    (assert (:point p) (str "point " (:id p) " has no :point"))
    (assert (seq (:subpoints p)) (str "point " (:id p) " has no :subpoints")))
  (spit out (render doc))
  (println (format "rendered %d points, %d subpoints -> %s"
                   (count (:points doc))
                   (reduce + (map (comp count :subpoints) (:points doc)))
                   out)))
