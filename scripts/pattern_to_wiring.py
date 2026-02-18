#!/usr/bin/env python3
"""Pattern → Wiring Diagram Compiler

Compiles flexiarg pattern files into executable wiring diagram EDN.
The xenotype lift: a pattern's IF/HOWEVER/THEN/BECAUSE structure becomes
a DAG of wiring components, parameterised by the exotype-params.

Works for any pattern in the library:
  - Hexagrams: use explicit exotype-params from @mmca-interpretation
  - Exotypes (iiching): use params from the exotype-program-manifest
  - All others: use the bridge's 8-bit encoding → manifest param lookup

Usage:
    python3 scripts/pattern_to_wiring.py iching/hexagram-11-tai
    python3 scripts/pattern_to_wiring.py software-design/adapter-pattern
    python3 scripts/pattern_to_wiring.py --all-hexagrams
    python3 scripts/pattern_to_wiring.py --all
    python3 scripts/pattern_to_wiring.py --stats
"""

import argparse
import json
import re
import sys
from pathlib import Path

CODE_ROOT = Path(__file__).resolve().parent.parent.parent
FUTON3 = CODE_ROOT / "futon3"
FUTON5 = CODE_ROOT / "futon5"
LIBRARY_DIR = FUTON3 / "library"
WIRINGS_DIR = FUTON5 / "resources" / "xenotype-wirings"
BRIDGE_PATH = FUTON5 / "resources" / "pattern-exotype-bridge.edn"
MANIFEST_PATH = FUTON5 / "resources" / "exotype-program-manifest.edn"


# ---------------------------------------------------------------------------
# Parsing
# ---------------------------------------------------------------------------

def parse_flexiarg(filepath):
    """Parse a flexiarg file into structured data."""
    text = filepath.read_text(encoding="utf-8")
    result = {}

    # Top-level @ fields (single-line only — [ \t]+ prevents matching across lines)
    for m in re.finditer(r'^@(\S+)[ \t]+(.*?)$', text, re.MULTILINE):
        key, val = m.group(1), m.group(2).strip()
        result[key] = val

    # Exotype params from @mmca-interpretation :exotype-params {…}
    m = re.search(
        r':exotype-params\s*\{(.*?)\}',
        text, re.DOTALL
    )
    if m:
        result["exotype-params"] = parse_edn_map(m.group(1))

    # Exotype params from @exotype-params section (iiching files — flat, no braces)
    if "exotype-params" not in result:
        m = re.search(
            r'^@exotype-params\s*\n(.*?)(?=\n@|\Z)',
            text, re.MULTILINE | re.DOTALL
        )
        if m:
            result["exotype-params"] = parse_edn_map(m.group(1))

    # Bits from @mmca-interpretation :sigil-encoding
    m = re.search(
        r':sigil-encoding\s*\{[^}]*:bits\s+"([01]{8})"',
        text, re.DOTALL
    )
    if m:
        result["bits"] = m.group(1)

    # Bits from @bits (iiching files)
    m = re.search(r'^@bits\s+([01]{8})', text, re.MULTILINE)
    if m:
        result.setdefault("bits", m.group(1))

    # CT interpretation - as-morphism
    m = re.search(
        r':as-morphism\s*\{(.*?)\}',
        text, re.DOTALL
    )
    if m:
        result["ct-morphism"] = parse_edn_map(m.group(1))

    # IF/HOWEVER/THEN/BECAUSE sections
    for section in ["IF", "HOWEVER", "THEN", "BECAUSE"]:
        pattern = re.compile(
            rf'^\s+\+\s+{section}\s*:\s*\n?(.*?)(?=\n\s+\+\s+(?:IF|HOWEVER|THEN|BECAUSE|NEXT-STEPS|evidence)|(?:\n@|\Z))',
            re.MULTILINE | re.DOTALL | re.IGNORECASE
        )
        m = pattern.search(text)
        if m:
            raw = m.group(1).strip()
            lines = []
            for line in raw.split("\n"):
                stripped = line.strip()
                if stripped.startswith("+ evidence:"):
                    break
                lines.append(stripped)
            result[section.lower()] = " ".join(lines).strip()

    return result


def parse_edn_map(s):
    """Parse a simple EDN map string into a Python dict."""
    result = {}
    # Match :key value pairs
    for m in re.finditer(r':(\S+)\s+(\S+)', s):
        key = m.group(1)
        val = m.group(2)
        # Parse value types
        if val in ("true", "false"):
            result[key] = val == "true"
        elif val.startswith(":"):
            result[key] = val
        elif val.startswith('"'):
            result[key] = val.strip('"')
        else:
            try:
                result[key] = float(val)
            except ValueError:
                result[key] = val
    return result


# ---------------------------------------------------------------------------
# Manifest and bridge loaders
# ---------------------------------------------------------------------------

_manifest_cache = None

def load_manifest_params():
    """Load the exotype-program-manifest and build bits→params lookup."""
    global _manifest_cache
    if _manifest_cache is not None:
        return _manifest_cache

    content = MANIFEST_PATH.read_text(encoding="utf-8")
    _manifest_cache = {}

    for m in re.finditer(
        r':params\s*\{(.*?)\}.*?:bits\s*"([01]{8})"',
        content, re.DOTALL
    ):
        params_str, bits = m.group(1), m.group(2)
        params = {}
        for key in ["rotation", "match-threshold", "update-prob",
                     "mix-mode", "invert-on-phenotype?", "mix-shift"]:
            km = re.search(rf':{re.escape(key)}\s+(\S+?)(?:,|\s|$)', params_str)
            if km:
                val = km.group(1).rstrip(",")
                if val in ("true", "false"):
                    params[key] = val == "true"
                elif val.startswith(":"):
                    params[key] = val
                else:
                    try:
                        params[key] = float(val)
                    except ValueError:
                        params[key] = val
        _manifest_cache[bits] = params

    return _manifest_cache


_bridge_cache = None

def load_bridge():
    """Load the pattern-exotype-bridge and return {pattern_id: {exotype_8bit, ...}}."""
    global _bridge_cache
    if _bridge_cache is not None:
        return _bridge_cache

    content = BRIDGE_PATH.read_text(encoding="utf-8")
    _bridge_cache = {}

    # Parse pattern entries from the bridge EDN
    for m in re.finditer(
        r':pattern-id\s+"([^"]+)".*?:exotype-8bit\s+"([01]{8})".*?:confidence\s+([\d.]+)',
        content, re.DOTALL
    ):
        pid, bits, conf = m.group(1), m.group(2), float(m.group(3))
        _bridge_cache[pid] = {"exotype_8bit": bits, "confidence": conf}

    return _bridge_cache


def resolve_params(pattern_id, parsed):
    """Resolve exotype-params for any pattern.

    Priority:
      1. Explicit params from flexiarg (hexagrams have these)
      2. Manifest lookup via bits field from flexiarg (iiching exotypes)
      3. Manifest lookup via bridge's 8-bit encoding (everything else)
      4. Defaults
    """
    # 1. Explicit params in flexiarg
    if parsed.get("exotype-params"):
        return parsed["exotype-params"], "flexiarg"

    # 2. Manifest lookup via bits in flexiarg
    bits = parsed.get("bits")
    if bits:
        manifest = load_manifest_params()
        if bits in manifest:
            return manifest[bits], "manifest"

    # 3. Bridge lookup
    bridge = load_bridge()
    if pattern_id in bridge:
        bits = bridge[pattern_id]["exotype_8bit"]
        parsed["bits"] = bits  # store for later use
        parsed["bridge_confidence"] = bridge[pattern_id]["confidence"]
        manifest = load_manifest_params()
        if bits in manifest:
            return manifest[bits], "bridge"

    # 4. Defaults
    return {
        "rotation": 0,
        "match-threshold": 0.5,
        "update-prob": 0.5,
        "mix-mode": ":none",
        "invert-on-phenotype?": False,
    }, "default"


# ---------------------------------------------------------------------------
# Wiring diagram generation
# ---------------------------------------------------------------------------

# Map mix-mode keywords to wiring components and their edge patterns
MIX_MODE_MAP = {
    ":none":          {"component": None,              "doc": "identity (keep self)"},
    ":rotate-left":   {"component": ":bit-shift-left", "doc": "rotate bits left"},
    ":rotate-right":  {"component": ":bit-shift-right","doc": "rotate bits right"},
    ":reverse":       {"component": ":bit-not",        "doc": "invert all bits"},
    ":xor-neighbor":  {"component": ":bit-xor",        "doc": "XOR with neighbor"},
    ":scramble":      {"component": ":mutate",          "doc": "random mutation"},
    ":majority":      {"component": ":majority",        "doc": "per-bit majority vote"},
    ":swap-halves":   {"component": ":crossover",       "doc": "swap upper/lower nibbles"},
}

# Map CT preservation to action style
PRESERVATION_MAP = {
    ":full":    "identity",     # keep self
    ":partial": "blend",        # weighted average
    ":none":    "replace",      # complete replacement
}


def compile_pattern(parsed, pattern_id):
    """Compile a parsed flexiarg into a wiring diagram.

    The compilation strategy:
    1. CONTEXT: extract local neighborhood
    2. IF-condition: compute a metric from the neighborhood
    3. THEN-action: the active path (what the pattern does when triggered)
    4. HOWEVER-fallback: the conservative path (what happens otherwise)
    5. GATE: threshold-sigil chooses between THEN and HOWEVER paths
    6. POST: optional rotation, probabilistic application
    7. OUTPUT: final sigil
    """
    params, params_source = resolve_params(pattern_id, parsed)
    ct = parsed.get("ct-morphism", {})

    match_threshold = params.get("match-threshold", 0.5)
    update_prob = params.get("update-prob", 0.5)
    rotation = int(params.get("rotation", 0))
    mix_mode = params.get("mix-mode", ":none")
    invert_on_phe = params.get("invert-on-phenotype?", False)

    nodes = []
    edges = []

    # --- CONTEXT extraction (always present) ---
    nodes.extend([
        {"id": ":ctx-self", "component": ":context-self"},
        {"id": ":ctx-pred", "component": ":context-pred"},
        {"id": ":ctx-succ", "component": ":context-succ"},
        {"id": ":ctx-prev", "component": ":context-prev"},
        {"id": ":neighbors", "component": ":context-neighbors"},
    ])

    # --- IF-condition: what triggers this pattern? ---
    # Choose metric based on what the pattern cares about
    if_metric, if_doc = choose_if_metric(parsed, params, ct)
    nodes.append({"id": ":if-metric", "component": if_metric})

    # Wire metric input
    if if_metric in (":diversity", ":entropy", ":evenness", ":dominance",
                     ":autocorr"):
        edges.append(edge(":neighbors", "sigils", ":if-metric", "sigils"))
    elif if_metric == ":similarity":
        edges.append(edge(":ctx-self", "sigil", ":if-metric", "a"))
        edges.append(edge(":ctx-prev", "sigil", ":if-metric", "b"))
    elif if_metric == ":change-rate":
        # Need to construct two lists — use pred+self+succ for both gens
        # Simplified: use similarity between self and prev as proxy
        nodes[-1] = {"id": ":if-metric", "component": ":similarity"}
        edges.append(edge(":ctx-self", "sigil", ":if-metric", "a"))
        edges.append(edge(":ctx-prev", "sigil", ":if-metric", "b"))

    # --- THEN-action: what the pattern does when triggered ---
    then_node_id, then_port = build_then_path(
        nodes, edges, mix_mode, update_prob, rotation, params
    )

    # --- HOWEVER-fallback: conservative action ---
    however_node_id, however_port = build_however_path(
        nodes, edges, ct
    )

    # --- GATE: choose between THEN and HOWEVER based on IF-metric ---
    nodes.append({"id": ":gate", "component": ":threshold-sigil"})

    # Wire the gate
    edges.append(edge_from_metric(":if-metric", if_metric, ":gate", "score"))
    edges.append(literal(match_threshold, "scalar", ":gate", "threshold"))
    edges.append(edge(then_node_id, then_port, ":gate", "above"))
    edges.append(edge(however_node_id, however_port, ":gate", "below"))

    # --- PHENOTYPE INVERSION (optional) ---
    if invert_on_phe:
        # When phenotype says invert, swap the gate's sense
        nodes.append({"id": ":phe", "component": ":context-phe"})
        nodes.append({"id": ":phe-bit", "component": ":bit-test"})
        nodes.append({"id": ":phe-gate", "component": ":if-then-else-sigil"})

        edges.append(edge(":phe", "bits", ":phe-bit", "sigil"))
        edges.append(literal(0, "int", ":phe-bit", "index"))

        # If phenotype bit 0 is set, invert: use HOWEVER when metric is high
        edges.append(edge(":phe-bit", "bit", ":phe-gate", "cond"))
        edges.append(edge(however_node_id, however_port, ":phe-gate", "then"))
        edges.append(edge(":gate", "result", ":phe-gate", "else"))

        output_node = ":phe-gate"
    else:
        output_node = ":gate"

    # --- POST: probabilistic application ---
    if update_prob < 1.0:
        nodes.append({"id": ":prob-gate", "component": ":threshold-sigil"})
        # Use a "random" score — approximate with balance of self
        nodes.append({"id": ":self-balance", "component": ":balance"})
        edges.append(edge(":ctx-self", "sigil", ":self-balance", "sigil"))
        edges.append(edge(":self-balance", "bal", ":prob-gate", "score"))
        edges.append(literal(1.0 - update_prob, "scalar", ":prob-gate", "threshold"))
        edges.append(edge(output_node, "result", ":prob-gate", "above"))
        edges.append(edge(":ctx-self", "sigil", ":prob-gate", "below"))
        output_node = ":prob-gate"

    # --- OUTPUT terminal: route through :output-sigil for wiring runtime ---
    nodes.append({"id": ":output", "component": ":output-sigil"})
    edges.append(edge(output_node, "result", ":output", "sigil"))

    # Build the diagram
    diagram = {
        "nodes": nodes,
        "edges": edges,
        "output": ":output",
    }

    # Build metadata
    title = parsed.get("title", pattern_id)
    bits = parsed.get("bits", "00000000")
    meta = {
        "id": pattern_id,
        "title": title,
        "bits": bits,
        "params-source": params_source,
        "match-threshold": match_threshold,
        "update-prob": update_prob,
        "rotation": rotation,
        "mix-mode": mix_mode,
        "invert-on-phenotype?": invert_on_phe,
    }

    # Build interpretation from pattern text
    interp_parts = []
    if parsed.get("if"):
        interp_parts.append(f"IF: {parsed['if'][:120]}")
    if parsed.get("however"):
        interp_parts.append(f"HOWEVER: {parsed['however'][:120]}")
    if parsed.get("then"):
        interp_parts.append(f"THEN: {parsed['then'][:120]}")
    interpretation = " | ".join(interp_parts) if interp_parts else title

    return {
        "meta": meta,
        "diagram": diagram,
        "interpretation": interpretation,
    }


def choose_if_metric(parsed, params, ct):
    """Choose the IF-condition metric based on pattern semantics.

    Maps the pattern's intent to a measurable property of the neighborhood.
    """
    mix_mode = params.get("mix-mode", ":none")
    threshold = params.get("match-threshold", 0.5)

    # Heuristic: match mix-mode to appropriate sensor
    #   majority/blend → diversity (coordination patterns)
    #   xor/scramble → similarity (differentiation patterns)
    #   rotate/reverse → entropy (transformation patterns)
    #   none → stability (preservation patterns)

    if mix_mode in (":majority",):
        return ":diversity", "neighborhood diversity"
    elif mix_mode in (":xor-neighbor", ":scramble"):
        return ":similarity", "self-neighbor similarity"
    elif mix_mode in (":rotate-left", ":rotate-right", ":reverse",
                      ":swap-halves"):
        return ":entropy", "neighborhood entropy"
    elif mix_mode == ":none":
        return ":similarity", "self-stability"
    else:
        return ":diversity", "neighborhood diversity"


def build_then_path(nodes, edges, mix_mode, update_prob, rotation, params):
    """Build the THEN (active) path — what happens when the pattern fires.

    Returns (node_id, output_port).
    """
    mix_info = MIX_MODE_MAP.get(mix_mode, MIX_MODE_MAP[":none"])
    component = mix_info["component"]

    if component is None:
        # :none → keep self (identity)
        return ":ctx-self", "sigil"

    if component == ":majority":
        nodes.append({"id": ":then-mix", "component": ":majority"})
        edges.append(edge(":neighbors", "sigils", ":then-mix", "sigils"))
        result_id, result_port = ":then-mix", "result"

    elif component == ":bit-xor":
        nodes.append({"id": ":then-mix", "component": ":bit-xor"})
        edges.append(edge(":ctx-pred", "sigil", ":then-mix", "a"))
        edges.append(edge(":ctx-succ", "sigil", ":then-mix", "b"))
        result_id, result_port = ":then-mix", "result"

    elif component == ":bit-shift-left":
        nodes.append({"id": ":then-mix", "component": ":bit-shift-left"})
        edges.append(edge(":ctx-self", "sigil", ":then-mix", "sigil"))
        edges.append(literal(max(1, rotation), "int", ":then-mix", "n"))
        result_id, result_port = ":then-mix", "result"

    elif component == ":bit-shift-right":
        nodes.append({"id": ":then-mix", "component": ":bit-shift-right"})
        edges.append(edge(":ctx-self", "sigil", ":then-mix", "sigil"))
        edges.append(literal(max(1, rotation), "int", ":then-mix", "n"))
        result_id, result_port = ":then-mix", "result"

    elif component == ":bit-not":
        nodes.append({"id": ":then-mix", "component": ":bit-not"})
        edges.append(edge(":ctx-self", "sigil", ":then-mix", "sigil"))
        result_id, result_port = ":then-mix", "result"

    elif component == ":mutate":
        nodes.append({"id": ":then-mix", "component": ":mutate"})
        edges.append(edge(":ctx-self", "sigil", ":then-mix", "sigil"))
        edges.append(literal(update_prob, "scalar", ":then-mix", "rate"))
        result_id, result_port = ":then-mix", "result"

    elif component == ":crossover":
        # swap-halves: crossover self with pred at midpoint (bit 4)
        nodes.append({"id": ":then-mix", "component": ":crossover"})
        edges.append(edge(":ctx-self", "sigil", ":then-mix", "a"))
        edges.append(edge(":ctx-pred", "sigil", ":then-mix", "b"))
        edges.append(literal(4, "int", ":then-mix", "point"))
        result_id, result_port = ":then-mix", "result"

    else:
        return ":ctx-self", "sigil"

    # Optional post-rotation
    if rotation > 0 and component not in (":bit-shift-left", ":bit-shift-right"):
        nodes.append({"id": ":then-rotate", "component": ":bit-shift-left"})
        edges.append(edge(result_id, result_port, ":then-rotate", "sigil"))
        edges.append(literal(rotation, "int", ":then-rotate", "n"))
        return ":then-rotate", "result"

    return result_id, result_port


def build_however_path(nodes, edges, ct):
    """Build the HOWEVER (conservative) path — fallback when not triggered.

    Based on CT preservation:
      :full → keep self
      :partial → average self with previous
      :none → use previous (defer)
    """
    preservation = ct.get("preservation", ":partial")

    if preservation == ":full" or preservation == "full":
        return ":ctx-self", "sigil"
    elif preservation == ":none" or preservation == "none":
        return ":ctx-prev", "sigil"
    else:
        # :partial → blend self with previous
        nodes.append({"id": ":however-blend", "component": ":sigil-avg"})
        edges.append(edge(":ctx-self", "sigil", ":however-blend", "a"))
        edges.append(edge(":ctx-prev", "sigil", ":however-blend", "b"))
        return ":however-blend", "result"


# ---------------------------------------------------------------------------
# Edge helpers
# ---------------------------------------------------------------------------

def edge(from_id, from_port, to_id, to_port):
    return {"from": from_id, "from-port": from_port, "to": to_id, "to-port": to_port}


def literal(value, value_type, to_id, to_port):
    return {"value": value, "value-type": value_type, "to": to_id, "to-port": to_port}


def edge_from_metric(metric_id, metric_component, to_id, to_port):
    """Get the right output port name for a metric component."""
    port_map = {
        ":diversity": "score",
        ":entropy": "score",
        ":evenness": "score",
        ":dominance": "score",
        ":autocorr": "score",
        ":similarity": "score",
        ":change-rate": "rate",
    }
    port = port_map.get(metric_component, "score")
    return edge(metric_id, port, to_id, to_port)


# ---------------------------------------------------------------------------
# EDN output
# ---------------------------------------------------------------------------

def to_edn(obj, indent=0):
    """Convert a Python object to EDN string."""
    pad = " " * indent

    if isinstance(obj, dict):
        items = []
        for k, v in obj.items():
            edn_key = f":{k}" if not str(k).startswith(":") else str(k)
            items.append(f"{pad} {edn_key} {to_edn(v, indent + 1)}")
        return "{\n" + "\n".join(items) + "}"

    elif isinstance(obj, list):
        if not obj:
            return "[]"
        # Check if items are simple
        if all(isinstance(x, (int, float, str, bool)) for x in obj):
            return "[" + " ".join(to_edn(x) for x in obj) + "]"
        items = [f"{pad} {to_edn(x, indent + 1)}" for x in obj]
        return "[\n" + "\n".join(items) + "]"

    elif isinstance(obj, bool):
        return "true" if obj else "false"

    elif isinstance(obj, int):
        return str(obj)

    elif isinstance(obj, float):
        return str(obj)

    elif isinstance(obj, str):
        if obj.startswith(":"):
            return obj
        return f'"{obj}"'

    else:
        return str(obj)


def format_wiring_edn(wiring):
    """Format a wiring diagram as clean, readable EDN."""
    meta = wiring["meta"]
    diagram = wiring["diagram"]
    interp = wiring["interpretation"]

    def edn_str(s):
        return s.replace('\\', '\\\\').replace('"', '\\"')

    lines = []
    lines.append(f'{{:meta {{:id "{edn_str(meta["id"])}"')
    lines.append(f'        :title "{edn_str(meta["title"])}"')
    lines.append(f'        :bits "{meta["bits"]}"')
    lines.append(f'        :params-source :{meta["params-source"]}')
    lines.append(f'        :match-threshold {meta["match-threshold"]}')
    lines.append(f'        :update-prob {meta["update-prob"]}')
    lines.append(f'        :rotation {meta["rotation"]}')
    lines.append(f'        :mix-mode {meta["mix-mode"]}')
    lines.append(f'        :invert-on-phenotype? {str(meta["invert-on-phenotype?"]).lower()}}}')
    lines.append("")

    # Diagram
    lines.append(" :diagram")
    lines.append(" {:nodes")
    lines.append("  [")
    for node in diagram["nodes"]:
        nid = node["id"]
        comp = node["component"]
        lines.append(f'   {{:id {nid} :component {comp}}}')
    lines.append("  ]")
    lines.append("")

    lines.append("  :edges")
    lines.append("  [")
    for e in diagram["edges"]:
        if "from" in e:
            parts = [f':from {e["from"]}', f':from-port :{e["from-port"]}',
                     f':to {e["to"]}', f':to-port :{e["to-port"]}']
            lines.append(f'   {{{" ".join(parts)}}}')
        else:
            val = e["value"]
            vtype = e["value-type"]
            if vtype == "int":
                val_str = str(int(val))
            else:
                val_str = str(val)
            parts = [f':value {val_str}', f':value-type :{vtype}',
                     f':to {e["to"]}', f':to-port :{e["to-port"]}']
            lines.append(f'   {{{" ".join(parts)}}}')
    lines.append("  ]")
    lines.append("")

    lines.append(f'  :output {diagram["output"]}}}')
    lines.append("")

    # Interpretation (escape double quotes and backslashes for valid EDN)
    lines.append(f' :interpretation')
    safe_interp = interp.replace('\\', '\\\\').replace('"', '\\"')
    lines.append(f' "{safe_interp}"}}')

    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def compile_one(pattern_id):
    """Compile a single pattern to a wiring diagram."""
    fpath = LIBRARY_DIR / f"{pattern_id}.flexiarg"

    if fpath.exists():
        parsed = parse_flexiarg(fpath)
    else:
        # No flexiarg — build minimal parsed data from bridge
        bridge = load_bridge()
        if pattern_id not in bridge:
            print(f"ERROR: {pattern_id} not in library or bridge", file=sys.stderr)
            return None
        parsed = {"title": pattern_id.split("/")[-1].replace("-", " ").title()}

    wiring = compile_pattern(parsed, pattern_id)
    return wiring


def collect_all_bridge_ids():
    """Get all pattern IDs from the bridge."""
    bridge = load_bridge()
    return sorted(bridge.keys())


def main():
    parser = argparse.ArgumentParser(description="Pattern → Wiring Diagram Compiler")
    parser.add_argument("pattern_id", nargs="?", help="Pattern ID (e.g. iching/hexagram-11-tai)")
    parser.add_argument("--all-hexagrams", action="store_true",
                        help="Compile all 64 hexagram patterns")
    parser.add_argument("--all", action="store_true",
                        help="Compile all patterns in the bridge (791)")
    parser.add_argument("--stats", action="store_true",
                        help="Print compilation statistics")
    parser.add_argument("--out", help="Output directory (default: xenotype-wirings/)")
    args = parser.parse_args()

    out_dir = Path(args.out) if args.out else WIRINGS_DIR
    out_dir.mkdir(parents=True, exist_ok=True)

    if args.all:
        pattern_ids = collect_all_bridge_ids()
    elif args.all_hexagrams:
        pattern_ids = []
        for fpath in sorted(LIBRARY_DIR.glob("iching/hexagram-*.flexiarg")):
            pid = f"iching/{fpath.stem}"
            pattern_ids.append(pid)
    elif args.pattern_id:
        pattern_ids = [args.pattern_id]
    else:
        parser.print_help()
        return

    compiled = 0
    sources = {"flexiarg": 0, "manifest": 0, "bridge": 0, "default": 0}
    mix_modes = {}

    for pid in pattern_ids:
        wiring = compile_one(pid)
        if wiring is None:
            continue

        edn_text = format_wiring_edn(wiring)

        safe_name = pid.replace("/", "-")
        out_path = out_dir / f"compiled-{safe_name}.edn"
        out_path.write_text(edn_text, encoding="utf-8")
        compiled += 1

        # Track stats
        src = wiring["meta"]["params-source"]
        sources[src] = sources.get(src, 0) + 1
        mm = wiring["meta"]["mix-mode"]
        mix_modes[mm] = mix_modes.get(mm, 0) + 1

        if len(pattern_ids) == 1:
            print(edn_text)
        elif not args.stats:
            print(f"  {pid} → {out_path.name}")

    if len(pattern_ids) > 1:
        print(f"\nCompiled {compiled}/{len(pattern_ids)} patterns → {out_dir}/")

    if args.stats or len(pattern_ids) > 10:
        print(f"\nParams sources:")
        for src, count in sorted(sources.items(), key=lambda x: -x[1]):
            print(f"  {src:10s}: {count}")
        print(f"\nMix modes:")
        for mm, count in sorted(mix_modes.items(), key=lambda x: -x[1]):
            print(f"  {mm:18s}: {count}")


if __name__ == "__main__":
    main()
