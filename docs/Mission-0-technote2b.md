# Mission 0 Technote 2b: Approach 2 (Xenotype Synthesis via Wiring Diagrams)

This plan integrates the high-level wiring-diagram framing with concrete ideas
from FloWrTester that are applicable to Cyber-MMCA xenotype synthesis. It is an
alternative experimental track to the current Mission 0 plan, focused on
building and searching diagrammatic xenotypes rather than tuning static weights.

## Rationale

Wiring diagrams provide:
- compositional, CT-native structure
- a finite, searchable design space
- interpretable selection logic
- transfer-ready components across domains

The goal of Approach 2 is to synthesize xenotypes as wiring diagrams that
compose primitive scorers and gates into a selection function.

## Key ideas from FloWrTester to reuse

1) Type-directed wiring
   - FloWr enumerates valid node connections using type signatures.
   - Apply the same constraint logic to wire macro-feature nodes in Cyber-MMCA.

2) Tests beyond types
   - FloWr uses extra constraints (e.g., IsWord, IntInRange) to refine wiring.
   - For Cyber-MMCA, define constraints such as:
     - entropy-in-range
     - persistence-above-threshold
     - change-rate-not-saturated

3) Spec-derived node metadata
   - FloWr uses clojure.spec to describe nodes and their input/output.
   - Apply this to scorers/combinators so wiring is validated and searchable.

4) Repair by substitution
   - FloWr multi-agent workflow substitutes nodes into failed flows.
   - Use the same idea to mutate wiring diagrams when a xenotype fails.

## Component library (initial)

Primitive scorers:
- short
- envelope
- triad
- shift
- filament
- entropy (if needed)
- stability/persistence (if needed)

Combinators:
- weighted-sum
- rank-fusion (expects precomputed ranks; lower is better)
- disagreement
- normalize
- threshold-gate
- temporal-filter (moving average)

Control:
- if-then-else
- parallel-vote
- sequential-filter

Outputs:
- accept/reject
- scalar score
- ranked list

## Wiring diagram representation

Define a small EDN schema:

- :nodes [{:id :n0 :type :scorer :component :envelope}
          {:id :n1 :type :combinator :component :disagreement}]
- :edges [{:from :n0 :to :n1 :port 0}]
- :output :n1
- :constraints [{:type :entropy-range :min 0.4 :max 0.7}]

This mirrors FloWr’s type-and-constraint-driven composition.

## Work plan

### Phase A: Library and schema

1) Define node types + ports for all scorers/combinators.
2) Encode type constraints and "tests beyond types".
3) Implement wiring diagram validation (type + constraint check).

Artifacts:
- `futon5/resources/xenotype-generator-components.edn`
- `futon5/src/futon5/xenotype/wiring.clj` (validator + interpreter)

### Phase B: Diagram interpreter

1) Implement diagram evaluation: topological execution over nodes.
2) Make each node a pure function from inputs to outputs.
3) Add tracing hooks for interpretability.

Artifacts:
- `futon5/src/futon5/xenotype/interpret.clj`
- Example diagrams in EDN.

### Phase C: Search strategies

Start with two strategies:

1) Template-guided synthesis
   - Patterns like [:scorer* :disagreement :threshold :fallback]
2) Mutation-based search
   - add node, remove node, rewire edge, swap component
   - configurable mutation mix (swap/ablate/rewire) for evolution

Artifacts:
- `futon5/scripts/synthesize_xenotype.clj`
- `futon5/resources/xenotype-templates.edn`

### Phase D: Calibration set (scalable)

Initial labeled set:
- Mission 17a compare set: 20 labeled seeds (human-validated)

Extension strategy:
- generate additional runs on demand
- label using:
  - ensemble consensus (high-confidence only)
  - human HIT sessions for disagreement cases
  - semi-automated labeling (ensemble suggests, human confirms edge cases)

Data split:
- 60% train, 20% validation, 20% test (start with 20 seeds, expand as needed)
- hold out a final test set until the end to avoid overfitting

Fitness:
- accuracy vs labels on validation set
- complexity penalty (node count)
- robustness across splits (k-fold or repeated holdout)

HIT workflow refresh:
- use `futon5/scripts/mission_0_prepare_hit.clj` to replay runs into full EDN
  histories and generate an input list for `futon5.mmca.judge-cli`.
- scale labeling by mixing top/mid/random selections per log.

Artifacts:
- `/tmp/xenotype-synth/*` (top diagrams, fitness curves)

### Phase E: Validation

Run A/B tests:
- baseline ensemble
- best synthesized diagram
- hybrid (ensemble of diagrams)

Compare on fresh seeds:
- EoC hit rate
- diversity of accepted genotypes
- runtime cost

## CT framing (explicit)

Objects: macro observations (entropy, persistence, motifs).
Morphisms: scorer functions to R, filters to Booleans, combinators.
Xenotype: a wiring diagram that composes morphisms into selection.
Synthesis: search for a functor from observation space to selection space.

## Integration points with Mission 0

If Approach 2 yields a strong diagram:
- swap the current ensemble in Mission 0 batch scoring for the synthesized diagram
- log the diagram as PSR in each run
- carry the diagram across domains (e.g., ants) with minor component edits

## Immediate next steps

1) Draft the component library EDN and wiring schema.
2) Implement the interpreter and validator.
3) Run template-guided synthesis on the Mission 17a compare set.
4) Log the top diagram and a short explanation in notebook III.
