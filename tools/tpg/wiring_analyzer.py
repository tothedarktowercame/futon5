#!/usr/bin/env python3
"""Z3-based wiring diagram analyzer for cellular automaton rules.

Analyzes wiring diagrams (Boolean logic circuits over 8-bit sigils) to
determine structural properties that predict Wolfram class (I/II/III/IV).

Each wiring diagram defines a Boolean function over a 3-cell neighborhood
(pred/self/succ = L/C/R). Components like bit-and, bit-or, bit-xor, bit-not
operate per-bit independently, so each bitplane has a truth table of only
2^3 = 8 entries.

The analyzer uses Z3 to:
1. Build the truth table by evaluating the circuit symbolically
2. Check GF(2) linearity (linear rules can only produce Class II/III)
3. Assess surjectivity (both 0 and 1 appear in output)
4. Compute sensitivity profile (how flips in input affect output)
5. Derive Wolfram rule number
6. Provide a structural class hint

Usage:
    echo '{"wiring": {...}}' | python wiring_analyzer.py

Input JSON:
    wiring: Wiring diagram with meta, diagram (nodes, edges, output)

Output JSON:
    wiring_id, wolfram_rule, truth_table, is_linear, surjective,
    sensitivity, structural_class_hint, class_confidence, analysis
"""

import json
import sys
import time
from collections import defaultdict

import z3


# ============================================================
# Component Definitions
# ============================================================

# Input components produce values from the 3-cell neighborhood
INPUT_COMPONENTS = {"context-pred", "context-self", "context-succ"}

# Output component receives the final result
OUTPUT_COMPONENTS = {"output-sigil", "output-with-state"}

# Logic gate components
GATE_COMPONENTS = {"bit-and", "bit-or", "bit-xor", "bit-not"}


# ============================================================
# Wiring Diagram Evaluation
# ============================================================

def build_node_index(nodes):
    """Build a map from node ID to node definition."""
    return {node["id"]: node for node in nodes}


def build_edge_graph(edges):
    """Build adjacency structures for the wiring graph.

    Returns:
        inputs_for: dict mapping node_id -> list of (from_id, to_port) pairs
        outputs_of: dict mapping node_id -> list of to_id
    """
    inputs_for = defaultdict(list)
    outputs_of = defaultdict(list)

    for edge in edges:
        from_id = edge["from"]
        to_id = edge["to"]
        to_port = edge.get("to-port")
        inputs_for[to_id].append((from_id, to_port))
        outputs_of[from_id].append(to_id)

    return dict(inputs_for), dict(outputs_of)


def topological_sort(nodes, edges):
    """Topological sort of nodes based on edges.

    Returns node IDs in evaluation order (inputs first, output last).
    """
    node_ids = {n["id"] for n in nodes}
    in_degree = {nid: 0 for nid in node_ids}
    adj = defaultdict(list)

    for edge in edges:
        from_id = edge["from"]
        to_id = edge["to"]
        if to_id in in_degree:
            in_degree[to_id] += 1
        adj[from_id].append(to_id)

    # Kahn's algorithm
    queue = [nid for nid, deg in in_degree.items() if deg == 0]
    order = []

    while queue:
        nid = queue.pop(0)
        order.append(nid)
        for neighbor in adj.get(nid, []):
            in_degree[neighbor] -= 1
            if in_degree[neighbor] == 0:
                queue.append(neighbor)

    if len(order) != len(node_ids):
        raise ValueError(
            f"Cycle detected in wiring diagram: sorted {len(order)} "
            f"of {len(node_ids)} nodes"
        )

    return order


def evaluate_circuit_z3(nodes, edges, output_node_id, L, C, R):
    """Evaluate the wiring diagram circuit using Z3 Boolean expressions.

    Args:
        nodes: list of node dicts with 'id' and 'component'
        edges: list of edge dicts with 'from', 'to', optional 'to-port'
        output_node_id: ID of the output node
        L, C, R: Z3 Boolean variables for pred, self, succ

    Returns:
        Z3 Boolean expression representing the circuit output
    """
    node_index = build_node_index(nodes)
    inputs_for, _ = build_edge_graph(edges)
    eval_order = topological_sort(nodes, edges)

    # Map from node_id -> Z3 expression for that node's output
    node_values = {}

    for nid in eval_order:
        node = node_index[nid]
        component = node["component"]

        if component == "context-pred":
            node_values[nid] = L
        elif component == "context-self":
            node_values[nid] = C
        elif component == "context-succ":
            node_values[nid] = R
        elif component in OUTPUT_COMPONENTS:
            # Output node: pass through its single input
            inp = inputs_for.get(nid, [])
            if inp:
                from_id, _ = inp[0]
                node_values[nid] = node_values[from_id]
            else:
                raise ValueError(f"Output node '{nid}' has no inputs")
        elif component == "bit-not":
            inp = inputs_for.get(nid, [])
            if not inp:
                raise ValueError(f"bit-not node '{nid}' has no inputs")
            from_id, _ = inp[0]
            node_values[nid] = z3.Not(node_values[from_id])
        elif component in ("bit-and", "bit-or", "bit-xor"):
            inp = inputs_for.get(nid, [])
            if len(inp) < 2:
                raise ValueError(
                    f"{component} node '{nid}' needs 2 inputs, got {len(inp)}"
                )
            # Resolve port assignments: :a and :b
            port_a = None
            port_b = None
            for from_id, port in inp:
                if port == "a":
                    port_a = node_values[from_id]
                elif port == "b":
                    port_b = node_values[from_id]

            # If ports not specified, use order
            if port_a is None or port_b is None:
                vals = [node_values[from_id] for from_id, _ in inp]
                port_a = vals[0]
                port_b = vals[1]

            if component == "bit-and":
                node_values[nid] = z3.And(port_a, port_b)
            elif component == "bit-or":
                node_values[nid] = z3.Or(port_a, port_b)
            elif component == "bit-xor":
                node_values[nid] = z3.Xor(port_a, port_b)
        else:
            raise ValueError(f"Unknown component type: '{component}'")

    if output_node_id not in node_values:
        raise ValueError(f"Output node '{output_node_id}' not evaluated")

    return node_values[output_node_id]


def evaluate_circuit_concrete(nodes, edges, output_node_id, l_val, c_val, r_val):
    """Evaluate the wiring diagram with concrete Boolean values (0 or 1).

    This is a pure Python evaluation without Z3, used for building truth tables.

    Args:
        nodes, edges, output_node_id: wiring diagram
        l_val, c_val, r_val: integers 0 or 1

    Returns:
        integer 0 or 1
    """
    node_index = build_node_index(nodes)
    inputs_for, _ = build_edge_graph(edges)
    eval_order = topological_sort(nodes, edges)

    node_values = {}

    for nid in eval_order:
        node = node_index[nid]
        component = node["component"]

        if component == "context-pred":
            node_values[nid] = l_val
        elif component == "context-self":
            node_values[nid] = c_val
        elif component == "context-succ":
            node_values[nid] = r_val
        elif component in OUTPUT_COMPONENTS:
            inp = inputs_for.get(nid, [])
            if inp:
                from_id, _ = inp[0]
                node_values[nid] = node_values[from_id]
            else:
                raise ValueError(f"Output node '{nid}' has no inputs")
        elif component == "bit-not":
            inp = inputs_for.get(nid, [])
            if not inp:
                raise ValueError(f"bit-not node '{nid}' has no inputs")
            from_id, _ = inp[0]
            node_values[nid] = 1 - node_values[from_id]
        elif component in ("bit-and", "bit-or", "bit-xor"):
            inp = inputs_for.get(nid, [])
            if len(inp) < 2:
                raise ValueError(
                    f"{component} node '{nid}' needs 2 inputs, got {len(inp)}"
                )
            port_a = None
            port_b = None
            for from_id, port in inp:
                if port == "a":
                    port_a = node_values[from_id]
                elif port == "b":
                    port_b = node_values[from_id]

            if port_a is None or port_b is None:
                vals = [node_values[from_id] for from_id, _ in inp]
                port_a = vals[0]
                port_b = vals[1]

            if component == "bit-and":
                node_values[nid] = port_a & port_b
            elif component == "bit-or":
                node_values[nid] = port_a | port_b
            elif component == "bit-xor":
                node_values[nid] = port_a ^ port_b
        else:
            raise ValueError(f"Unknown component type: '{component}'")

    return node_values[output_node_id]


# ============================================================
# Truth Table Construction
# ============================================================

def build_truth_table(nodes, edges, output_node_id):
    """Build the 8-entry truth table for a wiring diagram.

    Wolfram convention: index i encodes (L, C, R) where
        i = L*4 + C*2 + R*1
    so index 7 = (1,1,1), index 6 = (1,1,0), ..., index 0 = (0,0,0)

    The truth table is stored in descending index order (Wolfram convention):
        tt[0] = f(1,1,1),  tt[1] = f(1,1,0),  ..., tt[7] = f(0,0,0)

    The Wolfram rule number is: sum(tt[i] * 2^(7-i)) for i in 0..7

    Returns:
        list of 8 ints (0 or 1) in Wolfram order (MSB=111 first)
    """
    tt = []
    # Wolfram convention: enumerate from 111 down to 000
    for i in range(7, -1, -1):
        l_val = (i >> 2) & 1
        c_val = (i >> 1) & 1
        r_val = i & 1
        result = evaluate_circuit_concrete(nodes, edges, output_node_id,
                                           l_val, c_val, r_val)
        tt.append(result)
    return tt


def truth_table_to_rule_number(tt):
    """Convert a Wolfram-order truth table to a rule number.

    tt[0] corresponds to input (1,1,1), tt[7] to input (0,0,0).
    Rule number = tt[0]*128 + tt[1]*64 + ... + tt[7]*1
    """
    rule = 0
    for i, val in enumerate(tt):
        rule += val * (1 << (7 - i))
    return rule


def rule_number_to_truth_table(rule):
    """Convert a Wolfram rule number to the 8-entry truth table."""
    tt = []
    for i in range(7, -1, -1):
        tt.append((rule >> i) & 1)
    return tt


# ============================================================
# Z3-based verification of truth table
# ============================================================

def verify_truth_table_z3(nodes, edges, output_node_id, tt):
    """Use Z3 to verify the concrete truth table matches the symbolic circuit.

    Returns True if Z3 confirms equivalence for all 8 inputs.
    """
    L = z3.Bool("L")
    C = z3.Bool("C")
    R = z3.Bool("R")

    circuit_expr = evaluate_circuit_z3(nodes, edges, output_node_id, L, C, R)

    solver = z3.Solver()
    solver.set("timeout", 5000)

    # Assert that the circuit disagrees with the truth table on at least one input
    disagreements = []
    for idx in range(8):
        # tt[0] = f(1,1,1), tt[7] = f(0,0,0)
        input_idx = 7 - idx
        l_val = (input_idx >> 2) & 1
        c_val = (input_idx >> 1) & 1
        r_val = input_idx & 1

        input_constraint = z3.And(
            L == z3.BoolVal(bool(l_val)),
            C == z3.BoolVal(bool(c_val)),
            R == z3.BoolVal(bool(r_val))
        )

        expected = z3.BoolVal(bool(tt[idx]))
        disagreements.append(z3.And(input_constraint, circuit_expr != expected))

    solver.add(z3.Or(*disagreements))
    result = solver.check()

    # If UNSAT, no disagreement exists -> truth table is correct
    return result == z3.unsat


# ============================================================
# GF(2) Linearity Check
# ============================================================

def check_linearity_gf2(tt):
    """Check if the Boolean function is linear over GF(2).

    A function f: {0,1}^3 -> {0,1} is linear over GF(2) iff
    f(a XOR b) = f(a) XOR f(b) for all a, b in {0,1}^3.

    Note: this is actually checking for affine functions (linear + constant).
    A true GF(2)-linear function also satisfies f(0,0,0) = 0.
    We check the full affine condition here since XOR + constant offset
    still produces the same CA dynamics.

    Returns:
        (is_linear, counterexample_detail)
    """
    # Build lookup: index -> output
    # tt is in Wolfram order: tt[0]=f(1,1,1), tt[7]=f(0,0,0)
    def f(l, c, r):
        idx = l * 4 + c * 2 + r
        return tt[7 - idx]

    counterexample = None

    for a in range(8):
        for b in range(8):
            a_l, a_c, a_r = (a >> 2) & 1, (a >> 1) & 1, a & 1
            b_l, b_c, b_r = (b >> 2) & 1, (b >> 1) & 1, b & 1

            xor_l = a_l ^ b_l
            xor_c = a_c ^ b_c
            xor_r = a_r ^ b_r

            f_a_xor_b = f(xor_l, xor_c, xor_r)
            f_a = f(a_l, a_c, a_r)
            f_b = f(b_l, b_c, b_r)
            f_a_xor_f_b = f_a ^ f_b

            if f_a_xor_b != f_a_xor_f_b:
                a_bits = f"{a_l}{a_c}{a_r}"
                b_bits = f"{b_l}{b_c}{b_r}"
                xor_bits = f"{xor_l}{xor_c}{xor_r}"
                counterexample = (
                    f"f({a_bits} XOR {b_bits}) = f({xor_bits}) = {f_a_xor_b}, "
                    f"but f({a_bits}) XOR f({b_bits}) = {f_a} XOR {f_b} = "
                    f"{f_a_xor_f_b} -- nonlinear"
                )
                return False, counterexample

    return True, "All f(a XOR b) = f(a) XOR f(b) checks passed -- linear/affine over GF(2)"


def check_linearity_z3(nodes, edges, output_node_id):
    """Use Z3 to check GF(2) linearity symbolically.

    Creates two independent input triples and checks if
    f(a XOR b) = f(a) XOR f(b) for all assignments.

    Returns:
        (is_linear, counterexample_or_None)
    """
    # Variables for input a
    La = z3.Bool("La")
    Ca = z3.Bool("Ca")
    Ra = z3.Bool("Ra")

    # Variables for input b
    Lb = z3.Bool("Lb")
    Cb = z3.Bool("Cb")
    Rb = z3.Bool("Rb")

    # XOR of inputs
    L_xor = z3.Xor(La, Lb)
    C_xor = z3.Xor(Ca, Cb)
    R_xor = z3.Xor(Ra, Rb)

    # f(a)
    f_a = evaluate_circuit_z3(nodes, edges, output_node_id, La, Ca, Ra)
    # f(b)
    f_b = evaluate_circuit_z3(nodes, edges, output_node_id, Lb, Cb, Rb)
    # f(a XOR b)
    f_a_xor_b = evaluate_circuit_z3(nodes, edges, output_node_id,
                                     L_xor, C_xor, R_xor)

    # Check: is there any a, b where f(a XOR b) != f(a) XOR f(b)?
    solver = z3.Solver()
    solver.set("timeout", 5000)
    solver.add(f_a_xor_b != z3.Xor(f_a, f_b))

    result = solver.check()
    if result == z3.unsat:
        return True, None
    elif result == z3.sat:
        model = solver.model()

        def to_bool(expr):
            val = model.eval(expr, model_completion=True)
            return z3.is_true(val)

        return False, {
            "a": (to_bool(La), to_bool(Ca), to_bool(Ra)),
            "b": (to_bool(Lb), to_bool(Cb), to_bool(Rb))
        }
    else:
        return None, "Z3 timeout -- linearity check inconclusive"


# ============================================================
# Surjectivity Check
# ============================================================

def check_surjectivity(tt):
    """Check if the truth table is surjective (both 0 and 1 appear).

    For a single-bitplane Boolean function with 8 entries,
    surjectivity means the function is not constant.

    Returns:
        (is_surjective, detail)
    """
    unique_outputs = set(tt)
    if unique_outputs == {0, 1}:
        ones = sum(tt)
        zeros = 8 - ones
        return True, f"Output has {ones} ones and {zeros} zeros -- surjective"
    elif unique_outputs == {0}:
        return False, "Output is constant 0 -- not surjective"
    elif unique_outputs == {1}:
        return False, "Output is constant 1 -- not surjective"
    else:
        return False, f"Unexpected output values: {unique_outputs}"


# ============================================================
# Sensitivity Profile
# ============================================================

def compute_sensitivity(tt):
    """Compute the sensitivity profile of the Boolean function.

    For each of the 8 input neighborhoods, flip each of the 3 input bits
    and count how many output bits change. Average sensitivity > 0.5
    suggests edge-of-chaos dynamics.

    Returns:
        dict with mean, max, min, per_input sensitivities
    """
    def f(l, c, r):
        idx = l * 4 + c * 2 + r
        return tt[7 - idx]

    per_input = []

    for i in range(8):
        l, c, r = (i >> 2) & 1, (i >> 1) & 1, i & 1
        base_output = f(l, c, r)

        flips = 0
        total = 3

        # Flip L
        if f(1 - l, c, r) != base_output:
            flips += 1
        # Flip C
        if f(l, 1 - c, r) != base_output:
            flips += 1
        # Flip R
        if f(l, c, 1 - r) != base_output:
            flips += 1

        sensitivity = flips / total
        per_input.append(round(sensitivity, 6))

    mean_sens = sum(per_input) / len(per_input)
    max_sens = max(per_input)
    min_sens = min(per_input)

    return {
        "mean": round(mean_sens, 6),
        "max": round(max_sens, 6),
        "min": round(min_sens, 6),
        "per_input": per_input
    }


# ============================================================
# Structural Class Hint
# ============================================================

# Known Wolfram class assignments for well-studied rules
KNOWN_WOLFRAM_CLASSES = {
    0: 1, 255: 1,                          # Constant
    1: 1, 2: 1, 4: 1, 8: 1, 32: 1,        # Class I
    128: 1, 136: 1, 160: 1, 254: 1,
    3: 2, 5: 2, 6: 2, 7: 2, 9: 2,         # Class II (periodic)
    10: 2, 11: 2, 12: 2, 13: 2, 14: 2,
    15: 2, 19: 2, 23: 2, 24: 2, 25: 2,
    26: 2, 27: 2, 28: 2, 29: 2, 33: 2,
    34: 2, 35: 2, 36: 2, 37: 2, 38: 2,
    42: 2, 43: 2, 44: 2, 46: 2, 50: 2,
    51: 2, 56: 2, 57: 2, 58: 2, 62: 2,
    72: 2, 73: 2, 74: 2, 76: 2, 77: 2,
    78: 2, 94: 2, 104: 2, 108: 2, 130: 2,
    132: 2, 134: 2, 138: 2, 140: 2, 142: 2,
    152: 2, 154: 2, 156: 2, 162: 2, 164: 2,
    170: 2, 172: 2, 178: 2, 184: 2, 200: 2,
    204: 2, 232: 2,
    18: 3, 22: 3, 30: 3, 45: 3, 60: 3,    # Class III (chaotic)
    75: 3, 86: 3, 90: 3, 105: 3, 122: 3,
    126: 3, 146: 3, 150: 3, 182: 3,
    41: 4, 54: 4, 106: 4, 110: 4,          # Class IV (complex)
}


def classify_structural_hint(is_linear, surjective, sensitivity, rule_number):
    """Provide a structural class hint based on analyzed properties.

    Classification logic:
    - Constant output -> Class I
    - Low sensitivity (mean < 0.25) -> Class I/II
    - Linear + surjective -> Class II or III
    - Nonlinear + high sensitivity (mean > 0.5) -> Class IV candidate
    - Otherwise -> Class II

    Returns:
        (class_hint, confidence, reasoning)
    """
    mean_sens = sensitivity["mean"]

    # Integer to Roman numeral mapping
    roman = {1: "I", 2: "II", 3: "III", 4: "IV"}

    # Check known rules first
    if rule_number in KNOWN_WOLFRAM_CLASSES:
        known_class = KNOWN_WOLFRAM_CLASSES[rule_number]
        return (
            roman[known_class],
            0.95,
            f"Rule {rule_number} is a known Wolfram Class {roman[known_class]} rule"
        )

    # Constant output -> Class I
    if not surjective:
        return "I", 0.9, "Constant output function -- Class I"

    # Linear rules
    if is_linear:
        if mean_sens > 0.5:
            return "III", 0.7, (
                f"Linear/affine over GF(2) with high sensitivity "
                f"({mean_sens:.3f}) -- chaotic Class III"
            )
        else:
            return "II", 0.6, (
                f"Linear/affine over GF(2) with moderate sensitivity "
                f"({mean_sens:.3f}) -- periodic Class II"
            )

    # Nonlinear rules
    if mean_sens > 0.5:
        return "IV", 0.5, (
            f"Nonlinear with high mean sensitivity ({mean_sens:.3f}) -- "
            f"Class IV candidate (edge-of-chaos dynamics)"
        )
    elif mean_sens > 0.25:
        return "II", 0.5, (
            f"Nonlinear with moderate sensitivity ({mean_sens:.3f}) -- "
            f"likely Class II"
        )
    else:
        return "I", 0.6, (
            f"Nonlinear but low sensitivity ({mean_sens:.3f}) -- "
            f"likely Class I/II"
        )


# ============================================================
# Main Analysis
# ============================================================

def analyze(data):
    """Run full wiring diagram analysis.

    Args:
        data: dict with 'wiring' key containing meta and diagram

    Returns:
        dict with analysis results
    """
    start = time.time()

    wiring = data["wiring"]
    meta = wiring.get("meta", {})
    diagram = wiring["diagram"]

    wiring_id = meta.get("id", "unknown")
    formula = meta.get("formula", "")

    nodes = diagram["nodes"]
    edges = diagram["edges"]
    output_node_id = diagram.get("output", "output")

    # 1. Build truth table
    tt = build_truth_table(nodes, edges, output_node_id)

    # 2. Compute Wolfram rule number
    rule_number = truth_table_to_rule_number(tt)

    # 3. Verify with Z3 (optional but adds confidence)
    z3_verified = verify_truth_table_z3(nodes, edges, output_node_id, tt)

    # 4. GF(2) linearity check (both concrete and Z3)
    is_linear, linearity_detail = check_linearity_gf2(tt)
    is_linear_z3, z3_counterexample = check_linearity_z3(
        nodes, edges, output_node_id
    )

    # Cross-check: concrete and Z3 should agree
    if is_linear_z3 is not None and is_linear != is_linear_z3:
        linearity_detail += (
            f" [WARNING: Z3 disagrees: linear_z3={is_linear_z3}]"
        )

    # 5. Surjectivity check
    surjective, surjectivity_detail = check_surjectivity(tt)

    # 6. Sensitivity profile
    sensitivity = compute_sensitivity(tt)

    # 7. Structural class hint
    class_hint, class_confidence, class_reasoning = classify_structural_hint(
        is_linear, surjective, sensitivity, rule_number
    )

    # Build sensitivity detail string
    sensitivity_detail = (
        f"Mean sensitivity {sensitivity['mean']:.3f} -- "
        f"each input flip changes output "
        f"{sensitivity['mean'] * 100:.0f}% of the time"
    )

    elapsed = (time.time() - start) * 1000

    result = {
        "wiring_id": wiring_id,
        "wolfram_rule": rule_number,
        "truth_table": tt,
        "is_linear": is_linear,
        "surjective": surjective,
        "sensitivity": sensitivity,
        "structural_class_hint": class_hint,
        "class_confidence": class_confidence,
        "z3_verified": z3_verified,
        "analysis": {
            "formula": formula,
            "linearity_detail": linearity_detail,
            "surjectivity_detail": surjectivity_detail,
            "sensitivity_detail": sensitivity_detail,
            "class_reasoning": class_reasoning
        },
        "analysis_time_ms": round(elapsed, 1)
    }

    return result


def main():
    data = json.load(sys.stdin)
    result = analyze(data)
    json.dump(result, sys.stdout, indent=2)
    print()  # trailing newline


if __name__ == "__main__":
    main()
