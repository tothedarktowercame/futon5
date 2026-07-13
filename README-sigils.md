# README-sigils — how to compute a mission/excursion HEAD sigil

A **sigil** is the numerical fingerprint of a mission's HEAD: an 8-bit *exotype*
(the whole HEAD's address in pattern space) plus a per-clause *xenotype-32* (one
8-bit word for each of the IF / HOWEVER / THEN / BECAUSE clauses), each tagged
with its nearest I-Ching / iiching anchor. It is the first artifact of the **AIF
reading** of a mission (see `futon6/holes/anatomy-of-a-futonic-mission.md` §"the
organism reading" and `README-aif+.md`). This doc lets you produce one in a
single shot without re-deriving the toolchain.

**Cost/safety:** CPU-light. MiniLM is small and cached; a run is ~seconds and a
few hundred MB RAM. This is **not** the heavy math.ct mining job — it is safe to
run even when the laptop is otherwise loaded. No network needed once the model
is cached.

## TL;DR — one command

```bash
# venv: only futon6/.venv has sklearn + sentence_transformers (futon3c/.venv does NOT)
/home/joe/code/futon6/.venv/bin/python \
  /home/joe/code/futon5/scripts/head_exotype_probe.py \
  <path/to/M-your-mission.md> --emit-health
```

This prints the readout and writes `<mission>.health.json` next to the input.
That JSON is the committable artifact (see the worked example below).

## What the input file must contain

The probe reads the markdown by **regex**, so the headings/format matter. Two
independent extractions happen:

1. **Whole-HEAD exotype** — needs a section that starts with `## HEAD` (matched
   `^## HEAD.*?$` up to the next `## `). The full prose of that section is
   embedded and projected to 8 bits → nearest hexagram anchor.

2. **Per-clause xenotype-32** — needs the HEAD recast into **four bold-colon
   blocks**, literally:

   ```markdown
   **IF:** the stack keeps building the same slot-waiting-to-be-filled six times …

   **HOWEVER:** they are built separately; the shared datatype is invisible …

   **THEN:** name the one datatype and the one operation, six surfaces as views …

   **BECAUSE:** one operator is deterministic where six are racy …
   ```

   Each block is matched as `\*\*IF:\*\* … (?=blank-line-then-bold | heading | EOF)`.

### Format gotchas (these bit me — save yourself the round-trip)

- It must be `**IF:**` **with the colon inside the bold**. `**IF**` (bullet
  style, no colon) yields a *null* per-section result — the whole-HEAD exotype
  still computes, but xenotype-completeness comes back empty.
- **Terminate the BECAUSE block with a heading.** Put a `## …` heading right
  after BECAUSE (e.g. `## HEAD — sigil, birth vitals & failure conditions`).
  Otherwise the regex's `\Z` fallback lets BECAUSE swallow everything below it
  (sigil tables, vitals) and the embedding is polluted.
- A blank line between blocks is what separates them — keep them paragraph-style,
  not as sub-bullets under one bullet.

## Output: how to read it

- `sigil.exotype` — 8 bits for the whole HEAD. Look it up against the 64 hexagram
  anchors; an exact bit-match (or nearest by cosine) is the **CT-domain hook** —
  the HEAD's address in the shared iiching pattern lexicon.
- `sigil.xenotype-32` — `IF·HOWEVER·THEN·BECAUSE`, 8 bits each, `·`-joined.
- `sigil.per-section.<CLAUSE>` — `bits`, `conf` (decisiveness of the projection,
  0–1), `anchor` (nearest hexagram), `cos` (cosine to that anchor; the bigger,
  the more distinctive that clause is).
- `health.bit-confidence` — mean per-clause confidence.
- `health.xenotype-completeness` — fraction of bit-positions confidently set
  (~0.89 is healthy; near-0 means the `**IF:**` blocks didn't parse — re-check
  the format gotchas).
- `health.reading` — the plain-language "alive with moderate signal …" summary.

## Worked example (M-typed-holes, 2026-06-14)

HEAD of a mission about *gathering six scattered slot-things into one*:

- whole-HEAD exotype **`00011000`** → **`iching/hexagram-45-cui`** (萃 *Cui*,
  "Gathering Together") — an **exact** bit-match. The HEAD of a mission about
  gathering landed on the hexagram for gathering.
- per-clause `00101000·01001000·00001001·00001000` (bit-conf 0.40,
  completeness 0.89). Strongest clause **IF → 需 Xu "Waiting"** (cos 0.415 — the
  IF clause is literally about *a slot waiting to be filled*); HOWEVER → 比 Bi
  "Holding Together", THEN → 解 Jie "Deliverance", BECAUSE → 屯 Zhun "Difficulty
  at the Beginning".

Artifact: `futon3c/holes/missions/M-typed-holes.health.json`; the prose readout
lives in that mission's `## HEAD — sigil, birth vitals & failure conditions`.
Note: the *strongest* anchor is an empirical output — don't predict it. (On that
run the "waiting" IF clause won, not the THEN/BECAUSE pair one might guess.)

## How it works (provenance of the numbers)

`head_exotype_probe.py` wraps `pattern_exotype_bridge.py`:

- **Embeddings:** `sentence-transformers/all-MiniLM-L6-v2` (cached), with
  pre-embedded anchors at
  `futon3a/resources/notions/minilm_pattern_embeddings.json`.
- **Anchors (320):** 64 I-Ching hexagrams (`futon3/library/iching/hexagram-*.flexiarg`)
  + 256 iiching exotypes (`futon3/library/iiching/exotype-*.flexiarg`), each
  carrying an `@bits` 8-bit field.
- **Projector:** ridge regression (`RidgeCV`) from the MiniLM embedding space onto
  the 8 bit-axes, trained on the anchors each run.
- So a sigil = "where does this HEAD's MiniLM embedding land, expressed in the
  iiching's own 8-bit coordinate system, named by its nearest hexagram."

## Related

- `README-aif+.md` — the AIF+ lifeform seeding the sigil is the first step of.
- `futon6/holes/anatomy-of-a-futonic-mission.md` — the sigil's role in the
  organism reading (vitals, failure conditions, death clause).
- `futon6/holes/missions/E-mission-head.{md,aif.edn}` — the original worked
  Golemization run this toolchain was built for.
- `scripts/sigil_assignment.clj`, `scripts/exotype_behavior_demo.clj` — the
  Clojure side (exotype→behaviour).
