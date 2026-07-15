(require '[futon5.mmca.runtime :as runtime]
         '[futon5.mmca.particle-detection :as pd]
         '[futon5.ca.core :as ca]
         '[clojure.edn :as edn]
         '[clojure.string :as str])

;; COHORT VALIDATION — does Cμ track Joe's VISUAL sense of edge-of-chaos?
;;
;; Cμ (statistical complexity = causal-state count) PASSED the ECA SeparatesEoC
;; anchor, clearing the Rule-110 bar that every informational measure missed.
;; But the spec is explicit that MetaCA is a different animal (256-valued,
;; per-cell rules, evolving) and needs its OWN validation slice.
;;
;; data/known-good-runset-20.edn is the "user-specified known good examples" —
;; 20 exactly-re-runnable historical models (genotype + phenotype + seed +
;; exotype), including the M17a baseline/exotic (工) contrast pairs.
;;
;; This renders each model's spacetime NEXT TO its Cμ, sorted by Cμ, so the
;; ordering can be checked against the eye. If Cμ tracks the visual grading,
;; it is a validated MetaCA EoC signal — and can then become the tokamak's C
;; (discriminate -> steer). If it does not, it is banked with the others.

(def sigil-index
  (into {} (map-indexed (fn [i e] [(first (str (:sigil e))) i]) (ca/sigil-entries))))

(defn coarse-row
  "Bin the 256-sigil alphabet into k bins (EVALUATOR-SPEC §3.5 'coarse'):
   the full alphabet is sample-starved (256^8 possible light-cones)."
  [k row]
  (mapv (fn [c] (let [i (get sigil-index c 0)]
                  (int (/ (* i k) 256))))
        (seq row)))

(defn run-model [m]
  (let [r (runtime/run-mmca
           {:genotype (:genotype m)
            :phenotype (:phenotype m)
            :generations (:generations m 120)
            :kernel (:kernel m)
            :lock-kernel (boolean (:lock-kernel m))
            :exotype (:exotype m)
            :exotype-mode (or (:exotype-mode m) :inline)
            :seed (:seed m)})]
    (:gen-history r)))

(def cfg (edn/read-string (slurp "data/known-good-runset-20.edn")))
(def models (:models cfg))
(println "cohort models:" (count models))

(def results
  (doall
   (for [m models]
     (let [st (run-model m)
           coarse (mapv #(coarse-row 2 %) st)
           obs (pd/observe coarse {:past-depth 2 :future-depth 1 :min-support 15})]
       (println (format "  %-42s Cmu=%-4d cov=%.3f den=%.3f labelled=%d"
                        (str (:id m)) (:n-states obs)
                        (:domain-coverage obs) (:particle-density obs)
                        (:n-labelled obs)))
       {:id (str (:id m))
        :label (:label m)
        :seed (:seed m)
        :cmu (:n-states obs)
        :coverage (:domain-coverage obs)
        :density (:particle-density obs)
        :spacetime (mapv (fn [row] (mapv #(get sigil-index % 0) (seq row))) st)}))))

;; --- HTML ------------------------------------------------------------------
(defn ->json [x]
  (cond
    (nil? x) "null"
    (number? x) (let [d (double x)] (if (Double/isNaN d) "null" (format "%.4f" d)))
    (string? x) (str "\"" (str/escape x {\" "\\\"" \\ "\\\\"}) "\"")
    (map? x) (str "{" (str/join "," (map (fn [[k v]] (str (->json (name k)) ":" (->json v))) x)) "}")
    (sequential? x) (str "[" (str/join "," (map ->json x)) "]")
    :else (->json (str x))))

(def sorted-res (reverse (sort-by :cmu results)))

(def html
  (str "<!doctype html><html><head><meta charset=\"utf-8\">
<title>Cohort — does Cμ track the eye?</title><style>
body{font:14px/1.5 -apple-system,Segoe UI,Roboto,sans-serif;margin:0;background:#0f1115;color:#e6e6e6}
.wrap{max-width:1250px;margin:0 auto;padding:26px}
h1{font-size:22px;margin:0 0 4px}.sub{color:#8b93a7;margin-bottom:16px}
.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px}
.card{background:#171a21;border:1px solid #262b36;border-radius:8px;padding:10px}
canvas{image-rendering:pixelated;width:100%;border:1px solid #2b313d;background:#000}
.cmu{font-size:20px;font-weight:700;color:#4da3ff}.lab{font-size:11px;color:#8b93a7;height:32px;overflow:hidden}
.meta{font-size:11px;color:#6b7488}
.note{background:#2a1f16;border:1px solid #6b4a22;border-radius:8px;padding:12px;margin:12px 0}
code{background:#0b0d11;padding:1px 5px;border-radius:4px;color:#9fd}
</style></head><body><div class=\"wrap\">
<h1>Historical cohort, ranked by Cμ (statistical complexity)</h1>
<div class=\"sub\">The 20 user-specified &ldquo;known good&rdquo; models, re-run from their exact recorded histories. Sorted by Cμ, highest first.</div>
<div class=\"note\"><b>What to check:</b> Cμ passed the ECA anchor (Rule&nbsp;110 = 26.4 vs chaotic 4.6/9.0, frozen 1.0) &mdash; but MetaCA is a different animal and needs its own validation.
<b>Does this ordering match your eye?</b> If the ones you read as clear edge-of-chaos cluster at the top, and the &ldquo;awful&rdquo; ones at the bottom, Cμ is a validated MetaCA EoC signal and can become the tokamak's C (discriminate &rarr; steer). If the ordering looks wrong to you, Cμ gets banked with the rest of the fail-bank &mdash; your eye is the ground truth here, not the number.
<br><br>Alphabet coarse-grained to 2 bins over the 256 sigils (<code>EVALUATOR-SPEC §3.5</code>): the full alphabet is sample-starved (256^8 light-cones).</div>
<div class=\"grid\" id=\"g\"></div>
<script>
const R = " (->json (mapv #(dissoc % :spacetime) sorted-res)) ";
const ST = " (->json (mapv :spacetime sorted-res)) ";
const g=document.getElementById('g');
R.forEach((r,i)=>{
  const d=document.createElement('div'); d.className='card';
  d.innerHTML='<div class=\"cmu\">Cμ '+r.cmu+'</div><div class=\"lab\">'+r.label+'</div>'+
   '<canvas id=\"c'+i+'\"></canvas>'+
   '<div class=\"meta\">seed '+r.seed+' &middot; cov '+r.coverage.toFixed(2)+' &middot; den '+r.density.toFixed(2)+'</div>';
  g.appendChild(d);
});
ST.forEach((rows,i)=>{
  const c=document.getElementById('c'+i), h=rows.length, w=rows[0].length;
  c.width=w;c.height=h;const ctx=c.getContext('2d'),img=ctx.createImageData(w,h);
  for(let y=0;y<h;y++)for(let x=0;x<w;x++){const v=rows[y][x],j=(y*w+x)*4;
    img.data[j]=v;img.data[j+1]=v;img.data[j+2]=v;img.data[j+3]=255;}
  ctx.putImageData(img,0,0);
});
</script></div></body></html>"))

(spit "holes/labs/M-aif-tokamak/cohort_cmu.html" html)
(println)
(println "=== Cμ ranking (high -> low) ===")
(doseq [r sorted-res] (println (format "  Cμ %-4d  %s" (:cmu r) (:label r))))
(println)
(println "wrote holes/labs/M-aif-tokamak/cohort_cmu.html")
