# M-lab-standard — standardize the experimental lab before remote scale-out

- **Status:** DERIVE (drafted 2026-07-13, claude-6; ratification = Joe)
- **Context:** M-sci-reproduction's nb01/nb02 established the genre for
  pheno-geno experiments. The Tier-2 replays (see
  `M-sci-reproduction-replay-ledger.md`) live in the richer
  **pheno-geno-exo-xeno** paradigm. Joe (2026-07-13): "We might want to
  standardize the lab for the next phase of experiments locally before we
  push out work" (to zai runners on linode-chicago/metameso).

## The genre contract (extracted from nb01/nb02 — every lab notebook satisfies it)

1. **Claims table linkage** — each notebook implements named claims (C-numbers)
   from a mission doc; every qualitative claim becomes a measured proposition.
2. **Explicit baselines** — including null models, on the same ICs/seeds:
   fixed-rule ECAs; random-wiring / shuffled-parameter controls; random
   evaluators; null grids. A result without a null is an anecdote.
3. **Explicit state** — all ICs/seeds/configs are EDN artifacts in-repo, named
   by seed; no hidden RNG. Stochastic dynamics use seeded streams persisted
   the same way. Censoring is reported, never dropped.
4. **Executable ground truth + cross-check** — every replay names its
   ground-truth artifact and the clean lab implementation must match it:
   grid-identity where deterministic, metric-identity on shared seeds where
   not. For pheno-geno that was `256ca.el`; for exo/xeno replays it is the
   original `futon5/src/futon5/mmca/{exotype,xenotype,xenoevolve}.clj` +
   `wiring/runtime.clj` stack and the recorded artifacts
   (`data/wiring-ladder/*.edn`, `data/known-good-runset-20.edn`,
   `reports/wirings/wiring-outcomes.edn`).
5. **Findings ledger** — where prose, memory, or old reports disagree with
   executable ground truth, the code wins and the discrepancy is a numbered
   finding (A1-A4 precedent; incl. the two review catches: circular proof,
   mixed-width cohort).
6. **Machine-checked where possible** — enumerable claims become exhaustive
   checks in the notebook (C7 precedent).
7. **Self-reproduction section** — commands, pinned deps, artifact provenance,
   ledger pointer. Rendered HTML committed; browser-viewable with no toolchain.

## Layer-stack standard (phase 2)

| Layer | State | Update semantics | Ground truth |
|---|---|---|---|
| pheno | bit row | local rule applied to phenotype neighborhood | `256ca.el` / paper §Fig 4 (nb03 pins this) |
| geno | byte-rule row | multiply / blend (S3.1/S3.2) | `256ca.el` (done, nb01/nb02) |
| exo | context → rule/kernel mapping per cell | pin per-experiment from `mmca/exotype.clj` (incl. `evolve-with-global-exotype` variants) | futon5 src + the specific run's config EDN |
| xeno | evaluator/policy over runs (band scores, 36-bit policies) | pin per-experiment from `mmca/xenotype.clj`, `xenoevolve.clj` | futon5 src + mission org files (`resources/exotic-programming*.org`) |

Engine plan: `scirepro.engine` stays the trusted pheno-geno core. Phase 2 adds
`scirepro.exo` and `scirepro.xeno` modules **per replay need, not
wholesale** — each module ships with its own cross-check harness against the
original futon5 stack before any new measurement is trusted. Do NOT port the
whole MMCA engine; port exactly what the chartered replay exercises.

## Stochasticity rule

Deterministic replays: grid-identity, full stop. Stochastic experiments
(mutation, evolution loops): (a) explicit seeded streams saved as artifacts,
shared between original and lab engine where the original permits re-seeding;
(b) where it does not, compare distributions over ≥30 seeds with stated
statistics and censoring — never single-run anecdotes. Paired designs on
shared seeds are the default (C2 precedent).

## Dispatch standard (what makes a slice remote-safe)

- **Slice size:** one notebook OR one engine module + tests per slice. zai
  runners die at max-tool-rounds on codex-sized slices (zai-15, 2026-07-13):
  for zai, halve again — e.g. "engine module + tests" and "notebook + render"
  as separate slices.
- **Gates:** kondo, check-parens, tests **run un-piped with the summary line
  pasted** (ZU-4 false-pass class: grep-silence through a timed-out pipe is
  not a verdict); render verified by file existence + content, not exit code.
- **Park every dispatch** (CLAUDE.md protocol); job-id + park-id stated in
  the operator buffer; reviewer re-runs every claimed PASS.
- **Identity:** runners act as themselves, never in another agent's name.
- **Review:** author ≠ reviewer, and the reviewer recomputes headline numbers
  independently (both slice-1 and slice-2 defects were invisible to green
  gates and caught only by independent recomputation).

## Remote preflight (linode-chicago = metameso) — status 2026-07-13

Verified: host reachable; `~/.zai-key` present; clojure + java + emacs
installed; `~/code` repo farm present. Outstanding before first dispatch:
1. Repo sync — today's commits (futon5 `e9e23dc`, futon3c `b7589d0`) are
   local-only; establish the push/pull path and pin the SHA in each handoff.
2. Runner bring-up path — how a zai runner on metameso registers with the
   Agency (local agency vs WS back to Dionysus:7070); verify with ONE
   disposable runner + a trivial parked bell before any real slice.
3. Confirm `futon5` (and `futon2`, for the CyberAnts replay) present and
   current on metameso; confirm `256ca.el` cross-checks run there (emacs
   batch works).
4. Park/lease semantics are Dionysus-side; remote runners only need bell/ack —
   no metameso-side park infra required.

## Sequencing

1. **Local:** nb03 (phenotype — pins the pheno layer semantics, completing
   pheno-geno) and nb04 (mutation — establishes the stochasticity rule in
   practice). These two ARE the lab-standardization work for layers 1-2.
2. **Local:** `scirepro.exo` + cross-check for the boundary-guardian replay
   (Tier-2 #2) — the first exo-layer module, smallest exo replay.
3. **Remote pilot:** 泰-zone (#5) then bitplane-MI (#3) to metameso zai
   runners — engine-local, single-repo, zai-shaped.
4. **Later:** evaluator-population (#4, first xeno module), CyberAnts (#1,
   cross-repo — codex or close review, futon2 SHA pinned).
