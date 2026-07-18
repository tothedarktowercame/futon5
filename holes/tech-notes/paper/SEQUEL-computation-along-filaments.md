# Sequel seed: computation along the edge-of-chaos filaments

From the Discussion (`sec:discussion`) of draft2: the order/chaos interface of
the sustaining operators (offset+2; the river) is a persistent, scale-invariant
fractal (D≈1.5 / 1.7). If those interface filaments are domain walls, they are
candidate *wires*. The computational angle (Joe, 2026-07-18) — a separate paper:

1. **Wall census.** Identify interface cells over time; read off which genotypes
   occupy them. NOTE: the sub-hypothesis "walls are made of complex/class-4
   rules" was tested this session and REFUTED (complex rules not enriched on the
   interface). So the question is transport, not composition.
2. **Transport along walls.** Perturb a cell *on* a wall (E1 paired-perturbation
   machinery, aimed along the interface, not isotropically) and test whether the
   disturbance propagates down the filament vs dissipates into the domains.
3. **Wall collisions.** Where two filaments meet, does the collision act as a
   gate (annihilate / pass / produce a third)? Particle collisions are where CA
   computation lives (Mitchell/Crutchfield-Hanson).

Reuses three existing tools: interface extraction (this Discussion), E1
perturbation-response, E5 local causal states (the particles). Pre-req that also
helps the main paper: a WRAP/torus engine in mmca-clj (the paper's operators are
wrap; the interface analysis was run on the fixed-boundary line variant).

## Follow-on findings from this session (2026-07-18) — grist for the sequel

**Fractal D is NOT the discriminator of "interesting"; regime BALANCE is.**
(Surfaced by Joe: the tinted sigma=16250374 is visibly less structured than
offset+2, yet both have essentially the same box-counting D.)
- offset+2: D=1.56, regime-entropy 1.45 bits (ordered .28 / complex .18 / chaotic .52 — balanced 3-way).
- sigma=16250374: D=1.57 (**same**), regime-entropy 0.92 (complex .79 — one regime dominates; ~48% Rule 110).
So a fractal interface is necessary but not sufficient. What singles out the
"interesting" edge of chaos is the *balanced coexistence of distinct regimes*
(high Shannon entropy over ordered/complex/chaotic), NOT the dimension of the
boundary. Regime-entropy is a direct measurement of the paper's own
universality-vs-diversity thesis (Conclusion): winning the universal rule (110)
costs regime balance. Candidate headline observable for the sequel.

**Which regimes form domains vs flicker (temporal persistence).** Survival
P(same regime at t+k | at t), offset+2:
- ordered: stable domains. moderate-chaos (~0.5, renders PALE in coolwarm):
  MOST persistent (survival 0.63 @k=25, runs up to ~98 gens) — stable disordered domains DO exist.
- complex-band (0.30-0.48): transient (0.21). deep-red (>0.60): transient (0.16), and rare.
So the *edge activity* (complex + extreme-chaos) is intrinsically transient; the
*domains* it separates (ordered + moderate-chaos) persist. Directly relevant to
"transport along walls": the walls are transient boundary layers between stable
domains. (Joe's question "do we ever get stabilised red?" — answer: moderate
disorder stabilises and renders pale; genuinely deep-red never consolidates.)
Colormap note: score 0.5 = white CENTER of coolwarm, so most disorder looks pale;
true red needs score >~0.7. A three-way (ordered/complex/chaotic) tint that
separates class-3 from class-4 (e.g. via damage-spreading, not activity) would
help — the activity score conflates them (known-hard).

**CAVEAT before building on E5/E7 feedback results.** The E5/E7 matched-ablation
control (`run-river-ablated`, mmca-clj 4300434) was found this session to NOT cut
the feedback as documented: its genotype step reads the *live evolving* phenotype
(3 of 4 context bits), freezing only the one-step look-ahead — NOT the frozen-p0
X->G cut its docstring/E5-results.md claim. So the "feedback does not drive extra
coherent structure" reversal is ON HOLD pending a corrected control + re-run
(fix: capture p0, pass it frozen to `original-paper-river-genotype-step`). The
sequel must NOT assume feedback is (or isn't) a driver of interface density until
that control is fixed. The feedforward joint gain (+0.0646 bits/bit) is unaffected.
