# Mission 1: Fulab Wiring Survey and Mission Definition

## Intent
Stabilize the compression loop for fulab and music by surveying existing MMCA wiring diagrams, extracting reusable wiring primitives, and defining a clear fulab wiring mission for Futon5.

This mission treats MMCA as instrumentation (playback and stress-testing), not a generator. The goal is repeatable compression from lived process into wiring structures with lineage traces.

## Why now
- We already have fulab terminology and a growing body of MMCA wiring artifacts.
- The next step is to make compression stability a first-class criterion and to define a wiring mission based on observed evidence.

## Scope
In scope:
- Survey of existing MMCA wiring diagrams that showed interesting behavior.
- Selection of exemplars that transfer to ants or behave distinctly in MMCA.
- Definition of a fulab wiring mission grounded in observed lineage.

Out of scope:
- Exhaustive MMCA search.
- New generative algorithms.
- Large-scale refactors.

## Inputs and references
- fulab terminal vocabulary: futon3/docs/fulab-terminal-vocabulary.md
- futon3 holes devmaps (context for lineage and compression arcs)
- Existing MMCA wiring artifacts in futon5 (reports, outputs, and logs)

## Core invariants
- Compression stability: similar situations compress to similar diagrams.
- Lineage traceability: run -> PAR -> diagram -> MMCA -> transfer (where applicable).
- Instrumentation boundary: MMCA instances must originate from upstream diagrams.

## Work plan
### 1) Survey MMCA wiring artifacts
Identify a short list of diagrams that are "interesting" by behavior or transfer.
Examples: "Creative" and any diagram that exhibited strong or unusual behavior.

Capture for each:
- Name / ID
- Origin (PAR, manual diagram, or other)
- Behavior in MMCA (observed, not explained)
- Transfer to ants (yes/no; note behavior)
- Evidence (paths to runs, logs, screenshots, or reports)
- Stability (repeatable? sensitive? brittle?)
- Notes (surprises, contradictions)

### 2) Extract candidate wiring primitives
From the survey, identify recurring structures or motifs.
Each primitive should have:
- Minimal structural definition (nodes, edges, roles)
- Behavioral signature (what it tends to produce)
- Context of use (when it appears in lineage)

### 3) Define the fulab wiring mission
Draft a mission statement and success criteria based on the survey.
Minimum mission definition:
- Objective: stabilize compression into wiring exemplars
- Inputs: PARs + selected MMCA runs + transfer notes
- Method: consistent pipeline from PAR to wiring to MMCA to transfer
- Success criteria: stability, lineage completeness, and transferability
- Deliverables: a small curated set of exemplars with provenance

### 4) Decide on vocabulary placement
Move or duplicate fulab-terminal-vocabulary.md into futon5/docs once the mission is finalized.
Keep a single source of truth to avoid drift.

## Deliverables
- MMCA wiring survey (short list with evidence)
- Candidate wiring primitives
- Fulab wiring mission definition
- Vocabulary placement decision (documented)

## Notes
This mission does not require new tooling. The emphasis is on selecting, documenting, and stabilizing what already exists.
