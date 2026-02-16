# Mission Specification with Machine-Checkable Diagrams

Missions in the futon ecosystem are specified as text documents with mermaid
diagrams. This works for humans but doesn't catch structural errors — orphan
outputs, dead components, type mismatches between wires. The
`futon5.ct.mission` namespace provides machine-checkable validation of mission
architecture diagrams using category theory.

## The Problem This Solves

The futon1a rebuild had 82 tests, 5 invariants, and 9 tension resolutions —
but the API contract was never specified. The system verified itself internally
but couldn't describe what it offered to the outside world. This is the
failure mode: internal correctness without boundary specification.

Mermaid diagrams make this visible to a careful reader, but they can't catch
the error mechanically. The EDN representation can.

## Core Idea

A mission is a morphism. It has:

- **Input ports** (domain) — what enters the system
- **Output ports** (codomain) — what the system exposes
- **Components** (internal morphisms) — each with `:accepts` and `:produces`
- **Edges** (wires) — typed connections between ports and components

Components are morphisms too: a validation layer accepts `http-request` and
produces either `http-request` (pass-through) or `error-response`. Its
`:type` (what it *is*, e.g., `:clj-namespace`) is separate from its interface
(what it accepts/produces). This distinction is exactly what category theory
gives you: a morphism f: A → B is defined by source and target, not by
what f is made of.

## EDN Format

```clojure
{:mission/id :my-mission
 :mission/state :active   ; :greenfield | :scoped | :active | :complete

 :ports
 {:input  [{:id :I-data  :name "Input data"  :type :edn-document  :source "user"}]
  :output [{:id :O-api   :name "API"         :type :http-endpoint :consumer "client"
            :spec-ref "2.1"}]}

 ;; Components are morphisms: :accepts (domain), :produces (codomain)
 :components
 [{:id :C-proc :name "Processor" :type :clj-namespace
   :accepts #{:edn-document} :produces #{:http-endpoint}}]

 :edges
 [{:from :I-data :to :C-proc :type :edn-document}
  {:from :C-proc :to :O-api  :type :http-endpoint}]}
```

## Five Validation Checks

| Check | Question | Failure means |
|-------|----------|---------------|
| **Completeness** | Can every output be reached from some input? | Unreachable output — unimplementable |
| **Coverage** | Does every component reach at least one output? | Dead component — missing output or dead code |
| **No orphan inputs** | Does every input connect to something? | Unnecessary input — or missing edge |
| **Type safety** | Do wire types match `:accepts`/`:produces`? | Type mismatch — wrong wiring |
| **Spec coverage** | Does every output have a `:spec-ref`? | Unspecified output — stop-the-line violation |

## Usage

```clojure
(require '[futon5.ct.mission :as m]
         '[clojure.edn :as edn])

;; Load and build
(def spec (edn/read-string (slurp "data/missions/futon1a-rebuild.edn")))
(def diagram (m/mission-diagram spec))

;; Validate (returns all five checks)
(m/validate diagram)
;; => {:all-valid true,
;;     :checks [{:valid true, :check :completeness, ...} ...],
;;     :mission/id :futon1a-rebuild}

;; Quick summary
(m/summary diagram)
;; => {:mission/id :futon1a-rebuild, :input-count 4, :output-count 7,
;;     :component-count 9, :edge-count 23, :all-valid true, :failed-checks []}

;; Regenerate mermaid from EDN
(println (m/diagram->mermaid diagram))

;; Check if two missions can compose
(m/composable? diagram-a diagram-b)
;; => [{:from-mission :a, :from-port :O-api, :to-mission :b, :to-port :B-in, ...}]

;; Compose them
(m/compose-missions diagram-a diagram-b)
;; => merged diagram with combined ports, components, and edges
```

## Workflow

1. **Write the mission spec** (M-*.md) with mermaid diagram per
   `futon-theory/mission-interface-signature`
2. **Write the EDN** (`docs/mission-diagram.edn` in the project repo)
3. **Validate** — all five checks must pass
4. **Iterate** — fix the EDN (and the mission spec) until it passes
5. **At each prototype gate** — re-validate; update EDN if topology changed
6. **Regenerate mermaid** from EDN to keep the human-readable diagram in sync

## Worked Example

The futon1a rebuild is the first mission validated this way. See:

- **EDN**: `futon1a/docs/mission-diagram.edn` (canonical)
- **Mission spec**: `futon3/holes/missions/M-futon1a-rebuild.md`
- **Pattern**: `futon3/library/futon-theory/mission-interface-signature.flexiarg`
- **Validator**: `futon5/src/futon5/ct/mission.clj`
- **Tests**: `futon5/test/futon5/ct/mission_test.clj`

The first validation run found 12 type errors because components had a single
`:type` conflating identity with interface. Fixing the data to use `:accepts`
and `:produces` made all checks pass and produced a more precise architectural
model.

## Available Port Types

The validator ships with a set of port types and coercions. These are the
objects in the mission category:

```
:edn-document :http-endpoint :http-request :http-response
:xtdb-node :xtdb-tx :xtdb-entity :clj-namespace :ring-handler
:model-descriptor :type-registry :proof-path :error-response
:config :migration-data :test-suite :cli-command
```

Coercions (implicit type promotions):
- `:http-request` → `:ring-handler` (request feeds handler)
- `:ring-handler` → `:http-response` (handler produces response)
- `:xtdb-tx` ↔ `:xtdb-entity` (tx produces/consumes entities)
- `:model-descriptor` → `:type-registry` (descriptor registers)
- `:edn-document` ↔ `:config` (EDN is config)

New missions may need new port types. Add them to `port-types` and
`type-coercions` in `futon5.ct.mission`.
