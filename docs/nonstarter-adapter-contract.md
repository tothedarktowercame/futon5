# Nonstarter Mission 1: Adapter Contract (New-Style Surface)

Date: 2026-01-25

## Intent

Define the adapter surface between futon3a Portal output and futon5 Nonstarter
policy simulation. This contract targets the **new-style devmap surface**
(flexiarg / multiarg patterns as seen in futon0 and futon3a devmaps).

## New-style surface (pattern shape)

The new-style surface assumes **multiarg/flexiarg** patterns with structured
blocks and metadata. The adapter normalizes any portal candidate into the
shape below, so downstream consumers can rely on consistent fields.

### Required fields

- `:pattern/id` (string)
- `:pattern/title` (string)
- `:pattern/surface` (keyword, `:multiarg`)
- `:blocks` map (may contain nils if missing)

### Optional fields

- `:pattern/namespace`
- `:pattern/tags`
- `:pattern/score` (portal score / ANN score)
- `:evidence` (vector of evidence refs)
- `:next` (vector of next-step refs)
- `:psr-example`
- `:pur-template`
- `:raw` (raw portal candidate for traceability)

### Normalized pattern (EDN)

```clojure
{:pattern/id "futon3a/P1"
 :pattern/title "Sidecar Audit Trail"
 :pattern/surface :multiarg
 :pattern/namespace "futon3a"
 :pattern/score 0.72
 :blocks {:context "You need machine-checkable evidence..."
          :if "Agents run and produce artifacts..."
          :however "Without structured logging..."
          :then "Sidecar appends PSR..."
          :because "PSR/PUR discipline..."}
 :evidence ["src/musn/core.clj" "musn_chat_supervisor.clj"]
 :next ["Add confidence scoring" "Implement chain validation"]
 :psr-example "Selected sidecar..."
 :pur-template "Used sidecar in {{session-id}}..."
 :raw {...}}
```

## Adapter mapping (raw portal → normalized)

The adapter should prefer explicit flexiarg/multiarg fields when available and
fall back gracefully.

| Raw portal field | Normalized field |
| --- | --- |
| `:id`, `:name`, `:external-id` | `:pattern/id` |
| `:title`, `:name` | `:pattern/title` |
| `:namespace` | `:pattern/namespace` |
| `:score` | `:pattern/score` |
| `:blocks` or `:flexiarg` map | `:blocks` |
| `:evidence`, `:next-steps` | `:evidence`, `:next` |
| `:psr-example`, `:pur-template` | same |
| full candidate | `:raw` |

If a candidate lacks structured blocks, the adapter should still emit
`:blocks` with missing keys set to `nil` to preserve shape.

## Policy simulation input (contract)

The policy simulator expects a turn-level envelope:

```clojure
{:session/id "sess-2026-01-25-01"
 :turn 3
 :intent "Draft Nonstarter Mission 1 plan"
 :aif {:tau 0.6 :g-scores {...}}
 :candidates [<normalized-pattern> ...]}
```

The simulator must return a recommendation envelope:

```clojure
{:policy/id "policy-portal-v0"
 :chosen "futon3a/P1"
 :confidence 0.64
 :scores [{:pattern/id "futon3a/P1" :score 0.64}
          {:pattern/id "futon3a/P0" :score 0.52}]}
```

## Notes

- This contract **targets new-style multiarg/flexiarg patterns** as the primary
  surface; older patterns are allowed but considered incomplete.
- The adapter must be lossless: raw portal candidates are attached to each
  normalized pattern for traceability.
- When future devmaps are upgraded, they should slot cleanly into the
  `:blocks` surface without changing the adapter.

