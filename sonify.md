# Sonify Exotic Runs (Python)

A small CLI that turns Exotic Programming event logs into MIDI with an
idempotent sigil-to-pitch mapping and simple exotype modulation.

## Setup (venv)

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install mido
```

## Run (demo mode)

If no input file is provided, it generates a synthetic run and writes `demo.mid`.

```bash
python3 sonify.py
```

## Run (real input)

```bash
python3 sonify.py --in run.jsonl --out out.mid --ticks 120 --bpm 90
```

Optional flags:

- `--channel` MIDI channel (0-15, default 0)
- `--program` MIDI program number (0-127, default 0)

## Input format

Accepts either JSONL (one event per line) or a JSON array of events.
Per event fields (aliases allowed):

- `t` (aliases: `time`, `tick`, `timestep`)
- `agent` (aliases: `id`, `agent_id`)
- `sigil` (aliases: `sig`, `glyph`)
- `exotype` (aliases: `exo`, `exotype_bits`, `exo36`)

The `exotype` can be:

- 36-bit string ("0101...") or string/int literal (`0b...`, `0x...`),
- list of 36 bits/booleans,
- integer whose low 36 bits are used.

## Output summary

The script prints:

- number of timesteps
- distinct pitches used
- average held notes per timestep (post-idempotent collapse)
- stability score (fraction of timesteps where active pitch set is unchanged)

## Where to change behavior

- Sigil mapping: `sigil_to_pitch()` in `sonify.py`
- Idempotent note logic: `sonify()` uses `supporters` vs `active_notes`
- Exotype modulation: `compute_stats()` and CC74/velocity code in `sonify()`
