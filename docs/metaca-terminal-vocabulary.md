# MetaCA Terminal Vocabulary (Draft)

This draft inventories the concrete "terminal" levers and signals in the
MetaCA/MMCA world. The intent is to treat these as the atomic wiring points
for CT-backed programs, not just high-level narratives.

CT interpretations for sigils live in `~/futon3/library/iiching` and should be
kept in sync with any terminal vocabulary updates.

## Core Observations (per tick)

State fields emitted each generation:

- :generation - current tick.
- :genotype - sigil string.
- :phenotype - optional bit string.
- :kernel - active kernel keyword or kernel-spec map.
- :kernel-spec - normalized kernel spec when present.
- :metrics - per-tick derived metrics:
  - :entropy
  - :change-rate
  - :unique-sigils
  - :length
  - :sigil-counts

## History / Traces

Sequence-level views used for scoring and CT evidence:

- :history {:genotypes [...] :phenotypes [...]} - raw evolution history.
- :metrics-history - per-tick metric snapshots.
- :gen-history / :phe-history - run summaries returned by the runtime.

## Internal / Latent State

Runtime state that shapes evolution or operator behavior:

- :mode - :god applies operator effects inline; :classic records proposals.
- :lock-kernel - refuse kernel changes (operator and exotype deltas ignored).
- :freeze-genotype - evolve phenotype only; genotype fixed.
- :genotype-gate - gate genotype updates by phenotype bits.
- :genotype-gate-signal - phenotype bit that keeps old genotype (default \1).
- :operators - active operator list (sigil, pattern, context, parameters, functor).
- :meta-states - operator-local meta state keyed by sigil.
- :exotype - resolved exotype descriptor (sigil, tier, params).
- :exotype-mode - :inline (apply kernel changes) or :nominate (record candidates).
- :exotype-contexts - captured or replayed context samples.
- :exotype-context-mode - :history or :random.
- :exotype-context-depth - recursion depth for context sampling.
- :exotype-nominations - nominated kernel candidates when exotype-mode is :nominate.

## Action Terminals (operator effects)

Operator hooks may emit any of these keys:

- :rule - kernel delta (keyword, fn, or {:kernel ... :kernel-fn ... :kernel-spec ...}).
- :grid - genotype/phenotype delta (string, fn, or {:genotype ... :phenotype ...}).
- :metrics - custom metrics to merge into :metrics and :metrics-history.
- :meta - operator-local state passed to the next hook stage.

In :classic mode, :rule and :grid effects are recorded as proposals rather
than applied to the running state.

## Operator Lifecycle Hooks

Hooks are invoked in order for each operator:

- :init - initialize meta state and initial metrics.
- :observe - read world state and derive signals.
- :decide - choose internal decisions or targets.
- :act - emit :rule, :grid, and/or :metrics updates.

Each hook receives {:world ... :meta ... :params ...} where :world contains the
current state fields listed above.

## Interventions and Constraints

Manual or external interventions treated as terminals:

- :lesion {:tick N :target :phenotype|:genotype|:both :half :left|:right :mode :zero|:default}.
- :operator-scope - which sigils activate operators (:genotype, :pattern, :all).
- :pattern-sigils - explicit sigils to activate in the operator set.
- :pulses-enabled - enable periodic operator pulses when supported.

## Outcome / Utility Signals

High-level summaries used to compare runs (see `futon5/docs/metrics.md`):

- interestingness (entropy, change-rate, diversity)
- compressibility (lz78 tokens, ratio)
- autocorrelation (spatial, temporal)
- coherence and composite-score

## Normalization Guarantees (Draft)

Unless otherwise stated:

- Scalar metrics used for comparisons SHOULD be normalized to [0,1].
- History summaries SHOULD declare windowing as (W,S) where W is window
  length and S is stride; default behavior is whole-run aggregation.
- Operator-emitted metrics MUST be normalized before merge when they are
  intended for cross-run or cross-domain transfer.

## MetaCA Macro-Action Layer (Proposed)

Macro-actions are a small, fixed vocabulary inferred from operator effects.
They do not replace :rule or :grid; they wrap them so transfer can target a
stable control surface.

Derived macro-actions (examples):

- :pressure-up / :pressure-down
  (inferred from rewrite frequency x delta magnitude).
- :selectivity-up / :selectivity-down
  (inferred from match behavior and gating).
- :structure-preserve / :structure-disrupt
  (inferred from filament signals and change-rate coupling).

Macro-actions are not parameters; they describe what the control loop did.

Suggested derivations (draft):

- pressure: normalize avg change-rate.
- selectivity: normalize (1 - avg unique ratio).
- structure: compare temporal autocorr to change-rate; high autocorr with
  low change-rate implies preserve, otherwise disrupt.

Example macro trace:

```clojure
[[:pressure-up :selectivity-down]
 [:pressure-up :structure-disrupt]
 [:pressure-down :structure-preserve]]
```

## Episode Summary Record (Proposed ABI)

Canonical summary map emitted at end of run:

```clojure
{:regime :static|:eoc|:chaos|:magma
 :macro-trace [[:pressure-up :selectivity-down] ...]
 :pressure-avg 0.42
 :selectivity-avg 0.55
 :metastability 0.31
 :robustness {:contexts 0.7 :seeds 0.6}
 :judge {:label :exotic :confidence :high}}
```
