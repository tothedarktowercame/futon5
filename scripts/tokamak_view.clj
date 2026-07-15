(require '[futon5.aif.retarget-demo :as d]
         '[futon5.aif.controller :as ctrl]
         '[futon5.aif.preference :as pref]
         '[futon5.aif.forward :as forward]
         '[futon5.ca.core :as ca]
         '[clojure.string :as str])

;; Generates a self-contained HTML view into the tokamak runs:
;;  - the CA spacetime for :aif vs :null (same seed, same C schedule)
;;  - the macro-feature trajectories and what is being treated as SIGNAL
;;  - the evaluation set-up that produced the assessment

(def sigil-index
  (into {} (map-indexed (fn [i e] [(first (str (:sigil e))) i]) (ca/sigil-entries))))

(defn row->indices [s]
  (mapv #(get sigil-index % 0) (seq s)))

(defn run-arm-capture
  [mode {:keys [seed windows generations length schedule]}]
  (loop [i 0
         state (d/initial-state seed length)
         p-state nil
         rows []]
    (if (>= i windows)
      {:records rows :spacetime (mapv row->indices (:gen-history state))}
      (let [target (schedule i)
            opts {:seed (+ seed i) :generations generations :W generations}
            result (when (= mode :aif)
                     (ctrl/choose-actions-aif
                      state nil (assoc opts :target-c target :precision-state p-state)))
            action (if (= mode :null) :hold (first (:actions result)))
            fp (forward/forward-predict state action opts)
            obs (:mean fp)]
        (recur (inc i)
               (:next-state fp)
               (:precision-state result)
               (conj rows {:window i
                           :phase (if (< i (quot windows 2)) "A" "B")
                           :action (name action)
                           :pressure (:pressure obs)
                           :selectivity (:selectivity obs)
                           :activity (:activity obs)
                           :structure (:structure obs)
                           :regime (str (:regime obs))
                           :params (select-keys (get-in (:next-state fp) [:exotype :params])
                                                [:update-prob :match-threshold])
                           :distance (d/distance-to-c obs target)}))))))

;; --- tiny JSON emitter (numeric arrays + flat maps only) --------------------
(declare ->json)
(defn ->json [x]
  (cond
    (nil? x) "null"
    (number? x) (let [d (double x)] (if (Double/isNaN d) "null" (format "%.5f" d)))
    (string? x) (str "\"" (str/escape x {\" "\\\"" \\ "\\\\"}) "\"")
    (keyword? x) (->json (name x))
    (map? x) (str "{" (str/join "," (map (fn [[k v]] (str (->json (name k)) ":" (->json v))) x)) "}")
    (sequential? x) (str "[" (str/join "," (map ->json x)) "]")
    :else (->json (str x))))

(def cfg {:seed 1000 :windows 12 :generations 8 :length 64
          :schedule (fn [i] (if (< i 6) d/target-A d/target-B))})

(println "running :aif ...")
(def aif (run-arm-capture :aif cfg))
(println "running :null ...")
(def null (run-arm-capture :null cfg))

(def payload
  {:aif {:records (:records aif) :spacetime (:spacetime aif)}
   :null {:records (:records null) :spacetime (:spacetime null)}
   :targetA (into {} (for [[k v] d/target-A] [k (:mean v)]))
   :targetB (into {} (for [[k v] d/target-B] [k (:mean v)]))
   :scored (mapv name pref/numeric-channels)
   :switchAt 6})

(def html (str "<!doctype html>
<html><head><meta charset=\"utf-8\"><title>Tokamak vs Null — what is going on inside</title>
<style>
 body{font:14px/1.5 -apple-system,Segoe UI,Roboto,sans-serif;margin:0;background:#0f1115;color:#e6e6e6}
 .wrap{max-width:1180px;margin:0 auto;padding:28px}
 h1{font-size:22px;margin:0 0 4px} h2{font-size:16px;margin:30px 0 8px;color:#9ad}
 .sub{color:#8b93a7;margin-bottom:18px}
 .grid{display:grid;grid-template-columns:1fr 1fr;gap:18px}
 .card{background:#171a21;border:1px solid #262b36;border-radius:8px;padding:14px}
 .card h3{margin:0 0 8px;font-size:14px}
 canvas{image-rendering:pixelated;width:100%;border:1px solid #2b313d;background:#000}
 table{border-collapse:collapse;width:100%;font-size:12px}
 th,td{border-bottom:1px solid #262b36;padding:3px 6px;text-align:right}
 th:first-child,td:first-child{text-align:left}
 .warn{background:#2a1f16;border:1px solid #6b4a22;border-radius:8px;padding:12px;margin:14px 0}
 .good{background:#16241d;border:1px solid #2c5c45;border-radius:8px;padding:12px;margin:14px 0}
 code{background:#0b0d11;padding:1px 5px;border-radius:4px;color:#9fd}
 .legend{color:#8b93a7;font-size:12px;margin-top:6px}
</style></head><body><div class=\"wrap\">
<h1>Tokamak (:aif) vs no-control (:null) — inside the runs</h1>
<div class=\"sub\">Same seed, same genotype, same C schedule. C switches <b>target A &rarr; target B</b> at window 6 (the red line).</div>

<div class=\"warn\"><b>Read this first — what is currently treated as &ldquo;signal&rdquo;.</b>
The controller only ever sees four <i>bulk summary statistics</i> per window
(<code>windowed-macro-features</code>). It has <b>no access to structure inside the CA</b> — no domains,
no particles, no causal states. And those four channels collapse to <b>two</b>:
<ul>
<li><code>pressure = normalize(avg-change)</code> &mdash; the real signal #1</li>
<li><code>activity = normalize(avg-change)</code> &mdash; <b>literally the same input</b> (metrics.clj:626,629)</li>
<li><code>structure = normalize(temporal-autocorr)</code>, and <code>temporal-autocorr = avg(1-hamming) = 1-avg-change</code>
    &mdash; the <b>complement</b> of pressure; also <code>nil</code> on the metrics path</li>
<li><code>selectivity = normalize(1-avg-unique)</code> &mdash; the real signal #2</li>
</ul>
So &ldquo;edge of chaos&rdquo; is being chased through a 2-D bulk average. This is the thing to judge.</div>

<h2>1. The CA spacetime</h2>
<div class=\"sub\">Each row = one generation, each column = one cell, greyscale = sigil index (0..255). This is the system the tokamak is governing.</div>
<div class=\"grid\">
 <div class=\"card\"><h3>:aif (tokamak)</h3><canvas id=\"stA\"></canvas><div class=\"legend\">red line = C switches A&rarr;B</div></div>
 <div class=\"card\"><h3>:null (no control)</h3><canvas id=\"stN\"></canvas><div class=\"legend\">identical seed &amp; genotype</div></div>
</div>

<h2>2. What the controller sees, and what it does</h2>
<div class=\"grid\">
 <div class=\"card\"><h3>pressure (= avg-change)</h3><canvas id=\"chP\" height=\"170\"></canvas>
   <div class=\"legend\">blue = :aif, grey = :null, dashed = target C</div></div>
 <div class=\"card\"><h3>selectivity (= 1-avg-unique)</h3><canvas id=\"chS\" height=\"170\"></canvas>
   <div class=\"legend\">the only other independent channel</div></div>
</div>

<h2>3. Per-window detail (:aif)</h2>
<div class=\"card\"><div id=\"tbl\"></div></div>

<h2>4. How the assessment was set up</h2>
<div class=\"card\">
<p><b>Metric:</b> <code>distance = euclid(observed[pressure,selectivity], targetC[pressure,selectivity])</code>
 &mdash; scored over the two independent channels only (the duplicate <code>activity</code> and the
 complement/nil <code>structure</code> are excluded, else change-rate is counted 3&times;).</p>
<p><b>Claim under test (A):</b> the controller drives the CA toward whatever C it is given, holds it,
 <b>beats :null</b>, and <b>re-targets</b> when C moves at window 6. It makes <b>no</b> claim that any
 target is edge-of-chaos (that needs a SeparatesEoC-valid discriminator, which does not exist yet).</p>
<p><b>Result:</b> :aif did <b>not</b> beat :null in either phase. The signal-to-noise probe found the knob's
 authority over pressure across its <i>whole</i> range (0.070) is smaller than the CA's own variance at a
 <i>fixed</i> knob setting (sd 0.123) &rarr; SNR 0.57.</p>
<p><b>Caveat on that conclusion (important):</b> SNR was measured on <code>pressure</code> &mdash; a bulk average.
 That is what the tokamak currently treats as signal, so &ldquo;it cannot steer its own observable&rdquo; holds.
 But a bulk average is <b>not</b> the meaningful structure of a CA. The analogue of &ldquo;where the food is&rdquo;
 (ants) or &ldquo;where the tension is&rdquo; (War Machine) would be <b>causal-state structure &mdash; domains and
 particles (CSSR)</b>. The tokamak <b>never consumed CSSR</b>: the causal-state upgrade stopped at the DERIVE,
 so <code>local_causal_states.clj</code> exists but the controller's C does not use it. Whether control moves
 the <i>causal-state</i> structure is <b>untested</b>.</p>
</div>

<script>
const D = " (->json payload) ";
function drawSpace(id, rows){
  const c=document.getElementById(id), h=rows.length, w=rows[0].length;
  c.width=w; c.height=h; const ctx=c.getContext('2d'), img=ctx.createImageData(w,h);
  for(let y=0;y<h;y++)for(let x=0;x<w;x++){const v=rows[y][x], i=(y*w+x)*4;
    img.data[i]=v; img.data[i+1]=v; img.data[i+2]=v; img.data[i+3]=255;}
  ctx.putImageData(img,0,0);
  const sw = Math.floor(h * (D.switchAt/ (D.aif.records.length)));
  ctx.fillStyle='rgba(255,60,60,.9)'; ctx.fillRect(0,sw,w,1);
}
drawSpace('stA', D.aif.spacetime); drawSpace('stN', D.null.spacetime);

function chart(id, key){
  const c=document.getElementById(id); c.width=c.clientWidth*2; c.height=340;
  const ctx=c.getContext('2d'), W=c.width, H=c.height, P=34;
  const n=D.aif.records.length;
  const X=i=>P+(W-2*P)*(i/(n-1)), Y=v=>H-P-(H-2*P)*v;
  ctx.fillStyle='#0b0d11'; ctx.fillRect(0,0,W,H);
  ctx.strokeStyle='#2b313d'; ctx.lineWidth=2;
  ctx.beginPath(); ctx.moveTo(P,Y(0)); ctx.lineTo(W-P,Y(0)); ctx.moveTo(P,Y(0)); ctx.lineTo(P,Y(1)); ctx.stroke();
  ctx.fillStyle='#8b93a7'; ctx.font='20px sans-serif';
  ctx.fillText('1.0',4,Y(1)+7); ctx.fillText('0.0',4,Y(0)+7);
  // target C (dashed, switches at 6)
  ctx.setLineDash([8,6]); ctx.strokeStyle='#c9a227'; ctx.beginPath();
  ctx.moveTo(X(0),Y(D.targetA[key])); ctx.lineTo(X(D.switchAt-1),Y(D.targetA[key]));
  ctx.moveTo(X(D.switchAt),Y(D.targetB[key])); ctx.lineTo(X(n-1),Y(D.targetB[key])); ctx.stroke();
  ctx.setLineDash([]);
  // switch line
  ctx.strokeStyle='rgba(255,60,60,.8)'; ctx.beginPath(); ctx.moveTo(X(D.switchAt-0.5),Y(0)); ctx.lineTo(X(D.switchAt-0.5),Y(1)); ctx.stroke();
  const line=(recs,col)=>{ctx.strokeStyle=col;ctx.lineWidth=3;ctx.beginPath();
    recs.forEach((r,i)=>{const v=r[key]; if(v==null)return; i?ctx.lineTo(X(i),Y(v)):ctx.moveTo(X(i),Y(v));}); ctx.stroke();};
  line(D.null.records,'#7a8496'); line(D.aif.records,'#4da3ff');
}
chart('chP','pressure'); chart('chS','selectivity');

let t='<table><tr><th>w</th><th>phase</th><th>action</th><th>pressure</th><th>selectivity</th><th>activity</th><th>structure</th><th>upd-prob</th><th>match-th</th><th>dist</th></tr>';
D.aif.records.forEach(r=>{t+='<tr><td>'+r.window+'</td><td>'+r.phase+'</td><td>'+r.action+'</td><td>'+
  (r.pressure==null?'&mdash;':r.pressure.toFixed(3))+'</td><td>'+(r.selectivity==null?'&mdash;':r.selectivity.toFixed(3))+
  '</td><td>'+(r.activity==null?'&mdash;':r.activity.toFixed(3))+'</td><td>'+(r.structure==null?'<b style=color:#e77>nil</b>':r.structure.toFixed(3))+
  '</td><td>'+(r.params&&r.params['update-prob']!=null?r.params['update-prob'].toFixed(2):'&mdash;')+
  '</td><td>'+(r.params&&r.params['match-threshold']!=null?r.params['match-threshold'].toFixed(2):'&mdash;')+
  '</td><td>'+r.distance.toFixed(3)+'</td></tr>';});
document.getElementById('tbl').innerHTML=t+'</table>';
</script></div></body></html>"))

(spit "/tmp/tokamak_view.html" html)
(println "wrote /tmp/tokamak_view.html")
(println "aif windows:" (count (:records aif)) " spacetime rows:" (count (:spacetime aif)))
(println "null windows:" (count (:records null)) " spacetime rows:" (count (:spacetime null)))
