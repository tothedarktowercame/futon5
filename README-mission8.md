Claude-ready overview + questions

Context
We ran Mission 7 and Mission 8 pilots in futon5 (MetaCA) and updated `resources/exotic-programming-notebook.org` with images, summaries, and protocol notes. Mission 7 explores exotic programming vs direct optimization; Mission 8 explores local vs recursive-local evaluators. We now suspect the xenotype layer needs a “ratchet” (memoryful + curriculum) to show learning over time.

Key finding (interpretation paragraph)
Overnight A/B runs show stationary score distributions rather than upward drift: Regime B is consistently higher than A (mean ~40.9 vs ~34.1) with similar confetti rates, but windowed means oscillate without clear upward trend. This suggests xenotype is acting as a static evaluator, not a curriculum-shaping mechanism. The CT layer gives us a handle on evaluator structure, but without a higher-order ratchet we don’t see cumulative improvement—only a better stationary distribution.

What we learned
- Mission 7 A/B: Regime B (exotype evolution + xenotype guard) shifts the score distribution upward, lowers stasis vs A, and produces richer exotype banding. Lock-2 (context replay) preserves nomination structure best.
- Context-only baselines damp structured bands while leaving exotype stripes active, indicating evaluator shortcuts via context/phenotype cues; this supports a xenotype guardrail against context hacks.
- Mission 8 (local vs recursive-local): depth-2/3 recursion doesn’t radically increase mean score, but depth-3 reduces confetti; stasis drops for rec2/rec3 vs local. Phenotype layers appear flatter than genotype/exotype bands.

Speculative next steps (explicitly xenotype regimes)
We think the “ratchet” must be a xenotype regime:
- Memoryful xenotype: score improvement over windows (delta-mean/q50), not absolute score.
- Curriculum xenotype: progressively tighten constraints (e.g., confetti tolerance, diversity minimum) every N windows.
- Portfolio xenotype: preserve multiple evaluator word-classes to avoid convergence to a single regime.

Concrete proposal (Mission 7b)
- Window size = 100; ratchet target = +delta-mean and +delta-q50 vs previous window.
- Penalty if confetti rises or diversity collapses.
- Curriculum schedule: tighten thresholds every 5 windows.
- Evaluate outcomes by word-class using CT manifest.

Questions for review
1) Does the “stationary distribution vs learning” interpretation fit the evidence, or is there a better read?
2) What is the minimal xenotype ratchet that would convincingly show cumulative learning (without over-engineering)?
3) How would you tie the CT layer to the ratchet (e.g., word-class-conditioned thresholds or curriculum by program word)?
4) Are there better guardrails for context-only evaluator shortcuts than the current randomized context baseline?
5) For Mission 8, do you see a plausible mechanism to improve phenotype richness without sacrificing the genotype/exotype complexity we observe?
