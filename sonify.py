#!/usr/bin/env python3
"""Sonify Exotic Programming runs into MIDI.

See sonify.md for setup, CLI usage, and extension points.
"""

import argparse
import json
import math
import random
from collections import defaultdict
from typing import Any, Dict, Iterable, List, Optional, Tuple

import mido

BITS = 36
MAX_SIGIL = 255
MIN_PITCH = 36
MAX_PITCH = 96
VAR_NORM_MAX = (BITS * BITS) / 4.0  # Max variance of bounded [0, BITS]

ALIASES = {
    "t": ["t", "time", "tick", "timestep"],
    "agent": ["agent", "id", "agent_id"],
    "sigil": ["sigil", "sig", "glyph"],
    "exotype": ["exotype", "exo", "exotype_bits", "exo36"],
}


def _get_first(d: Dict[str, Any], keys: Iterable[str]) -> Optional[Any]:
    for key in keys:
        if key in d:
            return d[key]
    return None


def normalize_event(raw: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    if not isinstance(raw, dict):
        return None
    t = _get_first(raw, ALIASES["t"])
    agent = _get_first(raw, ALIASES["agent"])
    sigil = _get_first(raw, ALIASES["sigil"])
    exotype = _get_first(raw, ALIASES["exotype"])
    if t is None or agent is None or sigil is None:
        return None
    try:
        t = int(t)
        agent = int(agent)
        sigil = int(sigil)
    except (TypeError, ValueError):
        return None
    return {"t": t, "agent": agent, "sigil": sigil, "exotype": exotype}


def parse_exotype(value: Any) -> int:
    """Parse exotype representations into a 36-bit integer."""
    if value is None:
        return 0
    if isinstance(value, bool):
        return int(value) & ((1 << BITS) - 1)
    if isinstance(value, int):
        return value & ((1 << BITS) - 1)
    if isinstance(value, str):
        s = value.strip().replace("_", "").replace(" ", "")
        if s.startswith("0b") or s.startswith("0x") or s.isdigit():
            try:
                return int(s, 0) & ((1 << BITS) - 1)
            except ValueError:
                pass
        if s and all(ch in "01" for ch in s):
            return int(s, 2) & ((1 << BITS) - 1)
        return 0
    if isinstance(value, (list, tuple)):
        if len(value) == 0:
            return 0
        if len(value) >= BITS and all(isinstance(v, (int, bool)) for v in value[:BITS]):
            bits = 0
            for idx, bit in enumerate(value[:BITS]):
                if int(bit):
                    bits |= 1 << (BITS - 1 - idx)
            return bits & ((1 << BITS) - 1)
        if len(value) == 1 and isinstance(value[0], int):
            return value[0] & ((1 << BITS) - 1)
    return 0


def popcount(value: int) -> int:
    return value.bit_count()


def sigil_to_pitch(sigil: int) -> int:
    """Map sigil (0-255) to MIDI pitch range 36-96.

    Swap or wrap this function to implement scale quantization or
    alternate pitch mappings.
    """
    sigil = max(0, min(MAX_SIGIL, sigil))
    span = MAX_PITCH - MIN_PITCH
    return int(MIN_PITCH + round(sigil * span / MAX_SIGIL))


def load_events(path: str) -> List[Dict[str, Any]]:
    with open(path, "r", encoding="utf-8") as fh:
        content = fh.read().strip()
    events: List[Dict[str, Any]] = []
    if not content:
        return events
    parsed = None
    if content[0] in "[{":
        try:
            parsed = json.loads(content)
        except json.JSONDecodeError:
            parsed = None
    if parsed is not None:
        if isinstance(parsed, list):
            raw_events = parsed
        elif isinstance(parsed, dict):
            if isinstance(parsed.get("events"), list):
                raw_events = parsed["events"]
            else:
                raw_events = None
                for value in parsed.values():
                    if isinstance(value, list):
                        raw_events = value
                        break
                if raw_events is None:
                    raw_events = [parsed]
        else:
            raw_events = []
        for raw in raw_events:
            event = normalize_event(raw)
            if event:
                events.append(event)
        return events
    for line in content.splitlines():
        line = line.strip()
        if not line:
            continue
        raw = json.loads(line)
        event = normalize_event(raw)
        if event:
            events.append(event)
    return events


def generate_synthetic() -> List[Dict[str, Any]]:
    rng = random.Random(1)
    sigil_converged = 42
    base_bits = rng.getrandbits(BITS)
    events: List[Dict[str, Any]] = []
    for t in range(200):
        for agent in range(32):
            if t < 100:
                sigil = rng.randint(0, 255)
                exo = rng.getrandbits(BITS)
            else:
                sigil = sigil_converged
                exo = base_bits
                if agent % 8 == 0:
                    exo ^= 1 << rng.randint(0, BITS - 1)
            events.append({"t": t, "agent": agent, "sigil": sigil, "exotype": exo})
    return events


def compute_stats(popcounts: List[int]) -> Tuple[float, float]:
    """Return normalized (mean, variance) of popcounts."""
    if not popcounts:
        return 0.0, 0.0
    mean = sum(popcounts) / len(popcounts)
    var = sum((x - mean) ** 2 for x in popcounts) / len(popcounts)
    mean_norm = mean / BITS
    var_norm = max(0.0, min(1.0, var / VAR_NORM_MAX))
    return mean_norm, var_norm


def sonify(events: List[Dict[str, Any]], out_path: str, ticks_per_step: int, bpm: int,
           channel: int, program: int) -> Dict[str, Any]:
    """Convert events to MIDI using idempotent sigil-to-pitch logic.

    The main idempotent behavior is driven by recomputing `supporters`
    per timestep and emitting note_on/note_off only when a pitch
    transitions between zero and non-zero supporters.
    """
    if not events:
        raise ValueError("No events to sonify.")
    timeline: Dict[int, List[Dict[str, Any]]] = defaultdict(list)
    for event in events:
        timeline[event["t"]].append(event)
    all_ts = sorted(timeline)
    t_min, t_max = all_ts[0], all_ts[-1]

    midi = mido.MidiFile(ticks_per_beat=480)
    track = mido.MidiTrack()
    midi.tracks.append(track)
    track.append(mido.MetaMessage("set_tempo", tempo=mido.bpm2tempo(bpm), time=0))
    track.append(mido.Message("program_change", program=program, channel=channel, time=0))

    active_notes: Dict[int, int] = defaultdict(int)
    distinct_pitches = set()
    held_counts: List[int] = []
    stability_hits = 0
    prev_pitch_set: Optional[Tuple[int, ...]] = None

    pending_time = 0
    last_cc = None
    prev_t = t_min

    for t in range(t_min, t_max + 1):
        delta_ticks = 0 if t == t_min else (t - prev_t) * ticks_per_step
        pending_time += delta_ticks
        prev_t = t

        by_agent: Dict[int, Dict[str, Any]] = {}
        for event in timeline.get(t, []):
            by_agent[event["agent"]] = event

        supporters: Dict[int, int] = defaultdict(int)
        popcounts = []
        for event in by_agent.values():
            pitch = sigil_to_pitch(event["sigil"])
            supporters[pitch] += 1
            distinct_pitches.add(pitch)
            exo = parse_exotype(event.get("exotype"))
            popcounts.append(popcount(exo))

        mean_norm, var_norm = compute_stats(popcounts)
        velocity = 30 + int(70 * var_norm)
        velocity = max(0, min(127, velocity))
        cc_value = int(127 * mean_norm)

        messages: List[mido.Message] = []
        if popcounts:
            if last_cc is None or abs(cc_value - last_cc) > 2:
                messages.append(mido.Message("control_change", control=74, value=cc_value, channel=channel, time=0))
                last_cc = cc_value

        all_pitches = set(active_notes) | set(supporters)
        for pitch in sorted(all_pitches):
            prev_count = active_notes.get(pitch, 0)
            new_count = supporters.get(pitch, 0)
            if prev_count == 0 and new_count > 0:
                messages.append(mido.Message("note_on", note=pitch, velocity=velocity, channel=channel, time=0))
            elif prev_count > 0 and new_count == 0:
                messages.append(mido.Message("note_off", note=pitch, velocity=0, channel=channel, time=0))

        pitch_set = tuple(sorted(supporters))
        if prev_pitch_set is not None and pitch_set == prev_pitch_set:
            stability_hits += 1
        prev_pitch_set = pitch_set
        held_counts.append(len(pitch_set))

        if messages:
            messages[0].time = pending_time
            pending_time = 0
            for msg in messages:
                track.append(msg)

        active_notes = supporters

    if active_notes:
        pending_time += ticks_per_step
        first = True
        for pitch in sorted(active_notes):
            track.append(mido.Message(
                "note_off",
                note=pitch,
                velocity=0,
                channel=channel,
                time=pending_time if first else 0,
            ))
            first = False
            pending_time = 0

    midi.save(out_path)

    timesteps = len(held_counts)
    stability_score = 0.0
    if timesteps > 1:
        stability_score = stability_hits / (timesteps - 1)

    return {
        "timesteps": timesteps,
        "distinct_pitches": len(distinct_pitches),
        "avg_held": sum(held_counts) / timesteps if timesteps else 0.0,
        "stability_score": stability_score,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Sonify Exotic Programming runs into MIDI.")
    parser.add_argument("--in", dest="in_path", help="Input JSON or JSONL file.")
    parser.add_argument("--out", dest="out_path", help="Output MIDI file.")
    parser.add_argument("--ticks", dest="ticks", type=int, default=120, help="Ticks per timestep.")
    parser.add_argument("--bpm", dest="bpm", type=int, default=90, help="Tempo in BPM.")
    parser.add_argument("--channel", dest="channel", type=int, default=0, help="MIDI channel (0-15).")
    parser.add_argument("--program", dest="program", type=int, default=0, help="MIDI program number.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.in_path:
        events = load_events(args.in_path)
        out_path = args.out_path or "out.mid"
    else:
        events = generate_synthetic()
        out_path = args.out_path or "demo.mid"

    stats = sonify(
        events=events,
        out_path=out_path,
        ticks_per_step=args.ticks,
        bpm=args.bpm,
        channel=max(0, min(15, args.channel)),
        program=max(0, min(127, args.program)),
    )

    print(f"timesteps: {stats['timesteps']}")
    print(f"distinct pitches: {stats['distinct_pitches']}")
    print(f"avg held notes/timestep: {stats['avg_held']:.2f}")
    print(f"stability score: {stats['stability_score']:.3f}")


if __name__ == "__main__":
    main()
