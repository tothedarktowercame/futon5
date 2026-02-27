#!/usr/bin/env python3
"""One-step bitplane CA evolution using JAX.

Reads JSON on stdin:
{
  "bitplanes": [[0,1,...], ...]  # 8 planes x width
  "rule-bits": "01011010"       # lookup bits in order 000..111
  "wrap?": true|false
  "boundary-bit": 0|1
}

Writes JSON:
{"bitplanes-next": [[...], ...]}
"""

import json
import sys

import jax.numpy as jnp


def _normalize_bit(x):
    if x in (0, "0", False):
        return 0
    if x in (1, "1", True):
        return 1
    raise ValueError(f"expected binary value, got {x!r}")


def _as_array(planes):
    if len(planes) != 8:
        raise ValueError(f"expected 8 bitplanes, got {len(planes)}")
    normalized = [[_normalize_bit(v) for v in plane] for plane in planes]
    widths = {len(p) for p in normalized}
    if len(widths) != 1:
        raise ValueError(f"bitplanes must have equal widths, got {sorted(widths)}")
    return jnp.asarray(normalized, dtype=jnp.int32)


def _rule_table(rule_bits):
    if not isinstance(rule_bits, str) or len(rule_bits) != 8 or set(rule_bits) - {"0", "1"}:
        raise ValueError("rule-bits must be an 8-char binary string")
    return jnp.asarray([1 if ch == "1" else 0 for ch in rule_bits], dtype=jnp.int32)


def _step(bitplanes, rule_table, wrap, boundary_bit):
    # bitplanes shape: (8, width)
    if bitplanes.shape[1] == 0:
        return bitplanes

    if wrap:
        left = jnp.roll(bitplanes, 1, axis=1)
        center = bitplanes
        right = jnp.roll(bitplanes, -1, axis=1)
    else:
        width = bitplanes.shape[1]
        boundary_col = jnp.full((8, 1), int(boundary_bit), dtype=jnp.int32)
        left = jnp.concatenate([boundary_col, bitplanes[:, : width - 1]], axis=1)
        center = bitplanes
        right = jnp.concatenate([bitplanes[:, 1:], boundary_col], axis=1)

    idx = left * 4 + center * 2 + right
    return jnp.take(rule_table, idx)


def main():
    raw = sys.stdin.read()
    data = json.loads(raw)

    bitplanes = _as_array(data.get("bitplanes", []))
    rule_table = _rule_table(data.get("rule-bits"))
    wrap = bool(data.get("wrap?", True))
    boundary_bit = _normalize_bit(data.get("boundary-bit", 0))

    next_bitplanes = _step(bitplanes, rule_table, wrap, boundary_bit)
    out = {"bitplanes-next": next_bitplanes.tolist()}
    sys.stdout.write(json.dumps(out))


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"jax_step error: {e}", file=sys.stderr)
        sys.exit(1)
