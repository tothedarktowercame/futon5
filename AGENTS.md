AGENTS.md

Samādhi — Concentration

0. Purpose

This directory defines FUTON5: software entities and operator-specs
that transform patterns, sounds, diagrams, and symbolic artefacts into
improvisable structures.

Whereas other related software packages that we interface with will
provide memory, pattern structure, and other features, FUTON5
concentrated, renewable generativity that:

takes recognised patterns,
uplifts them into operators,
and recombines or otherwise expresses them new forms.

Agents here do not optimise for goals: they improvise, concentrate, and transform.

This is the layer where:

- musical riffs can become operators on code,
- category-theoretic morphisms become can pattern transforms,
- cellular automata help us explore an improvisation physics,
- and symbolic patterns become executable meta-patterns.

1. Core Concepts

Pattern

A structured symbolic unit (we will recieve examples from FUTON4,
possibly via the FUTON1 graph).

Operator

A typed transformation over patterns.

Every FUTON5 operator has a signature like this:

(domain-pattern-graph) → (codomain-pattern-graph)

Operators lift descriptive material (“a melody,” “a tension,” “a CT
riff”) into executable transformations.

Meta-Pattern

A pattern about operators: rules for how to mutate, combine, or generate patterns.

Prototype 1 and 1b are the first two meta-patterns in the system.

Sigils (256-symbol lexicon)

Sigils will label operators.

They form an RNA layer: a compact symbolic vocabulary for pattern transformation.

2. Prototype 1 — Patterns of Improvisation Agent

ID: agent.f5.patterns-of-improvisation

First sigil to map to a meta-pattern, e.g., 山 for operatorisation/uplift.

This operator interprets improvisation (in any domain, e.g., musical,
categorical, symbolic) as a system of operators.

It has the ability to transform a pattern-graph into meta-operator
nodes, each describing:

- the action of an improvisation motif
- its domain and codomain
- invariants it preserves
- tensions it alters
- possible musical/CT interpretations

Initial operator examples:

Phase-Switch — turning tension into motion
Return-to-I — looping back to a stable base pattern
Noise Budget — controlled deviation
Kleisli Turn — extending a pattern into a new space
Prototype → Production — uplift from description to operator

Future work: bind such an operator to sound-generators (Alda /
Overtone) so they can be heard and understood musically.

3. Prototype 1b — Sigil–Operator Lexicon Agent

ID: agent.f5.sigil-operator-lexicon

This agent maintains the mapping:

(sigils) ↔ (operators)

and supports:

- dispatching operator calls
- listing available transformations
- discovering new operators emerging from pattern-graphs
- embedding sigils inside generated music / CT sketches
- enabling “improvisation by sigil”: typing a symbol applies a transformation

This lexicon becomes the callable API of FUTON5.

4. Planned Tooling & Ecosystem

FUTON5 will integrate several computational substrates that support improvisation.

4.1 Alda / Overtone (Sound Engine)

Purpose:

Let operators sound their transformations, so that they can be explored sonically / aurally.

Agents will be able to:

- generate riffs and motifs
- enact operator transforms as melodic/rhythmic variations
- produce glissandi / microtonal movement
- audition metapatterns in real time

We expect both Alda and Overtone to be useful:

- Alda for score-like reproducibility
- Overtone for live-coded generativity

4.2 256CA (Meta-Cellular Automata Module)

A reworked Clojure-native version of the Emacs Lisp 256ca.el.

Purpose:

Let agents explore:

- rule evolution
- local/global tension interplay
- edge-of-chaos improvisation zones
- emergent pattern transforms mirroring cognitive improvisation

MetaCA = the “physics engine” of FUTON5 improvisation.

A core goal is to understand the global conditions under which "life"
emerges.

4.3 Category Theory (CT) Mini-Library

Purpose: encode improvisational structures as:

- functors
- natural transformations
- monads / comonads
- adjunction-like structures
- endofunctor-based pattern transforms

This supports translation between:

- musical improvisation
- symbolic improvisation
- CT structure
- design patterns (and meta-patterns)

5. 

Every operator in FUTON5 conforms to a minimal signature:

{:agent/id      ...
 :agent/sigil   ...
 :agent/domain  [:f4/pattern-graph]
 :agent/codomain [:f5/pattern-graph]
 :agent/execute (fn [graph & args] ...)}


and should be:

- pure where possible
- pattern-aware
- operator-expressive (emitting new pattern and meta-patetrn nodes)
- musically-capable (optional, via Alda/Overtone)
- CA-capable (optional, via 256CA)
- CT-capable (optional, via the mini CT library we will use or import)

6. Roadmap (Short)

These milestones can evolve as FUTON5 matures.

Milestone A — Implement Prototypes 1 and 1b

Create operator specs

Emit function stubs

Install sigil dispatch

Seed a minimal improvisation grammar

Milestone B — Integrate Alda + Overtone

Provide a sound protocol

Transform operators → musical events

Enable “audible debugging” of pattern transforms

Milestone C — Port 256CA.el

Bring MetaCA into Clojure

Expose rule-evolution operators to FUTON5 agents

Link CA states to sound patterns (optionally)

Milestone D — Add a CT Mini-Library

Define morphism types for improvisation

Implement basic combinators

Allow F5 agents to produce / consume CT sketches

7. Philosophy

FUTON5 agents do not judge.
They do not optimise.
They do not predict.

They play, concentrate, and mutate patterns in structured, comprehensible ways.

Their purpose is to support:

musical inquiry,

symbolic improvisation,

mathematical creativity,

and non-teleological craft cultivation.

They form the bridge between practice (FUTON4) and [future] argument (FUTON6).

8. Classic MMCA Rulebook (I Ching)

We now ship a structural parse of the 64 classical hexagrams (易經) so
that MetaMetaCA agents can start from a culturally rich rulebook.

- `futon5.mmca.iching` loads `reference/pg25501.txt`, splits it into 64
  entries, and records each hexagram’s judgement, line statements, and
  commentary. The entries live in `core-hexagrams` and retain the raw
  text for later study.
- `futon5.mmca.rulebook` derives a `rule/id`, judgement summary, and a
  staged hint sequence from those entries (init/observe/decide/act →
  reflect/adapt/meta). This gives us 64 reusable MMCA “classic rules”
  that can seed future operators just like EntropyPulse or BlendHand.
- Downstream agents can pull `(rulebook/all-rules)` to browse summaries
  or `(rulebook/rule-by-name "乾")` / `(rulebook/rule-by-id "iching/34")`
  to grab a specific template and translate it into executable hooks.
- The CT layer now exposes diagram primitives (identity / compose /
  tensor) plus a reusable adaptor registry (`futon5.ct.adapters`) so
  operators can split/merge data shapes (rule halves, metric streams,
  grid fronts/backs) before wiring them together in a score. This is
  the foundation for a string-diagram-complete “score compiler.”
- `futon5.mmca.score` walks those diagrams, removes adaptor-only nodes,
  and emits a runnable MMCA schedule (mode, generations, operator
  sequence). You can now sketch a score as a CT diagram, compile it,
  and pass the result straight to `run-mmca`.

Classic mode simulations can use these textual rules as the “fixed
physics” to observe, while God mode operators can remix them into live
rule deltas binding each stage hint to an MMCA hook.
