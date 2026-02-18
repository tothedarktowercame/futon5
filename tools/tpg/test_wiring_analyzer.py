#!/usr/bin/env python3
"""Tests for the wiring diagram analyzer.

Tests with well-known Wolfram rules:
- Rule 90:  L XOR R          -- linear, Class III (Sierpinski triangle)
- Rule 110: (C OR R) AND NOT(L AND C AND R) -- nonlinear, Class IV
- Rule 184: (C AND R) OR (L AND NOT C)      -- nonlinear, Class II
- Rule 0:   constant 0       -- Class I
- Rule 255: constant 1       -- Class I

Usage:
    python test_wiring_analyzer.py
"""

import json
import sys

from wiring_analyzer import (
    analyze,
    build_truth_table,
    check_linearity_gf2,
    check_surjectivity,
    compute_sensitivity,
    truth_table_to_rule_number,
    rule_number_to_truth_table,
    verify_truth_table_z3,
)


# ============================================================
# Test Wiring Diagrams (JSON format matching the EDN originals)
# ============================================================

RULE_90_WIRING = {
    "wiring": {
        "meta": {
            "id": "rule-090",
            "formula": "L XOR R"
        },
        "diagram": {
            "nodes": [
                {"id": "pred", "component": "context-pred"},
                {"id": "succ", "component": "context-succ"},
                {"id": "xor-lr", "component": "bit-xor"},
                {"id": "output", "component": "output-sigil"}
            ],
            "edges": [
                {"from": "pred", "to": "xor-lr", "to-port": "a"},
                {"from": "succ", "to": "xor-lr", "to-port": "b"},
                {"from": "xor-lr", "to": "output"}
            ],
            "output": "output"
        }
    }
}

RULE_110_WIRING = {
    "wiring": {
        "meta": {
            "id": "rule-110",
            "formula": "(C OR R) AND NOT(L AND C AND R)"
        },
        "diagram": {
            "nodes": [
                {"id": "pred", "component": "context-pred"},
                {"id": "self", "component": "context-self"},
                {"id": "succ", "component": "context-succ"},
                {"id": "or-cr", "component": "bit-or"},
                {"id": "and-lc", "component": "bit-and"},
                {"id": "and-lcr", "component": "bit-and"},
                {"id": "not-lcr", "component": "bit-not"},
                {"id": "final", "component": "bit-and"},
                {"id": "output", "component": "output-sigil"}
            ],
            "edges": [
                {"from": "self", "to": "or-cr", "to-port": "a"},
                {"from": "succ", "to": "or-cr", "to-port": "b"},
                {"from": "pred", "to": "and-lc", "to-port": "a"},
                {"from": "self", "to": "and-lc", "to-port": "b"},
                {"from": "and-lc", "to": "and-lcr", "to-port": "a"},
                {"from": "succ", "to": "and-lcr", "to-port": "b"},
                {"from": "and-lcr", "to": "not-lcr"},
                {"from": "or-cr", "to": "final", "to-port": "a"},
                {"from": "not-lcr", "to": "final", "to-port": "b"},
                {"from": "final", "to": "output"}
            ],
            "output": "output"
        }
    }
}

RULE_184_WIRING = {
    "wiring": {
        "meta": {
            "id": "rule-184",
            "formula": "(C AND R) OR (L AND NOT C)"
        },
        "diagram": {
            "nodes": [
                {"id": "pred", "component": "context-pred"},
                {"id": "self", "component": "context-self"},
                {"id": "succ", "component": "context-succ"},
                {"id": "and-cr", "component": "bit-and"},
                {"id": "not-c", "component": "bit-not"},
                {"id": "and-l-notc", "component": "bit-and"},
                {"id": "or-final", "component": "bit-or"},
                {"id": "output", "component": "output-sigil"}
            ],
            "edges": [
                {"from": "self", "to": "and-cr", "to-port": "a"},
                {"from": "succ", "to": "and-cr", "to-port": "b"},
                {"from": "self", "to": "not-c"},
                {"from": "pred", "to": "and-l-notc", "to-port": "a"},
                {"from": "not-c", "to": "and-l-notc", "to-port": "b"},
                {"from": "and-cr", "to": "or-final", "to-port": "a"},
                {"from": "and-l-notc", "to": "or-final", "to-port": "b"},
                {"from": "or-final", "to": "output"}
            ],
            "output": "output"
        }
    }
}

# Rule 0: constant 0 -- simplest possible: output always 0
# We build this as: NOT(pred OR NOT pred) = NOT(1) = 0
# Actually simpler: use bit-and with pred and bit-not of pred -> always 0
# Simplest: just have a constant-producing circuit.
# We can use: L AND (NOT L) which is always 0.
RULE_0_WIRING = {
    "wiring": {
        "meta": {
            "id": "rule-000",
            "formula": "L AND (NOT L)"
        },
        "diagram": {
            "nodes": [
                {"id": "pred", "component": "context-pred"},
                {"id": "not-l", "component": "bit-not"},
                {"id": "and-l-notl", "component": "bit-and"},
                {"id": "output", "component": "output-sigil"}
            ],
            "edges": [
                {"from": "pred", "to": "not-l"},
                {"from": "pred", "to": "and-l-notl", "to-port": "a"},
                {"from": "not-l", "to": "and-l-notl", "to-port": "b"},
                {"from": "and-l-notl", "to": "output"}
            ],
            "output": "output"
        }
    }
}

# Rule 255: constant 1 -- L OR (NOT L) which is always 1
RULE_255_WIRING = {
    "wiring": {
        "meta": {
            "id": "rule-255",
            "formula": "L OR (NOT L)"
        },
        "diagram": {
            "nodes": [
                {"id": "pred", "component": "context-pred"},
                {"id": "not-l", "component": "bit-not"},
                {"id": "or-l-notl", "component": "bit-or"},
                {"id": "output", "component": "output-sigil"}
            ],
            "edges": [
                {"from": "pred", "to": "not-l"},
                {"from": "pred", "to": "or-l-notl", "to-port": "a"},
                {"from": "not-l", "to": "or-l-notl", "to-port": "b"},
                {"from": "or-l-notl", "to": "output"}
            ],
            "output": "output"
        }
    }
}

# Rule 30: L XOR (C OR R) -- Class III, chaotic
RULE_30_WIRING = {
    "wiring": {
        "meta": {
            "id": "rule-030",
            "formula": "L XOR (C OR R)"
        },
        "diagram": {
            "nodes": [
                {"id": "pred", "component": "context-pred"},
                {"id": "self", "component": "context-self"},
                {"id": "succ", "component": "context-succ"},
                {"id": "or-cr", "component": "bit-or"},
                {"id": "xor-l-or", "component": "bit-xor"},
                {"id": "output", "component": "output-sigil"}
            ],
            "edges": [
                {"from": "self", "to": "or-cr", "to-port": "a"},
                {"from": "succ", "to": "or-cr", "to-port": "b"},
                {"from": "pred", "to": "xor-l-or", "to-port": "a"},
                {"from": "or-cr", "to": "xor-l-or", "to-port": "b"},
                {"from": "xor-l-or", "to": "output"}
            ],
            "output": "output"
        }
    }
}


# ============================================================
# Test Utilities
# ============================================================

def format_truth_table(tt):
    """Format truth table for readable display."""
    lines = []
    for i in range(8):
        input_idx = 7 - i
        l = (input_idx >> 2) & 1
        c = (input_idx >> 1) & 1
        r = input_idx & 1
        lines.append(f"  ({l},{c},{r}) -> {tt[i]}")
    return "\n".join(lines)


def assert_eq(name, expected, actual):
    """Assert equality with descriptive error message."""
    if expected != actual:
        print(f"  FAIL: {name}")
        print(f"    expected: {expected}")
        print(f"    actual:   {actual}")
        return False
    else:
        print(f"  OK: {name}")
        return True


# ============================================================
# Tests
# ============================================================

def test_rule_90():
    """Test Rule 90: L XOR R -- linear, Class III."""
    print("\n--- Rule 90: L XOR R ---")
    result = analyze(RULE_90_WIRING)

    passed = True
    passed &= assert_eq("wolfram_rule", 90, result["wolfram_rule"])
    passed &= assert_eq("truth_table", [0, 1, 0, 1, 1, 0, 1, 0],
                         result["truth_table"])
    passed &= assert_eq("is_linear", True, result["is_linear"])
    passed &= assert_eq("surjective", True, result["surjective"])
    passed &= assert_eq("z3_verified", True, result["z3_verified"])

    # Rule 90 has known class III
    passed &= assert_eq("structural_class_hint", "III",
                         result["structural_class_hint"])

    print(f"  Sensitivity: mean={result['sensitivity']['mean']}")
    print(f"  Class confidence: {result['class_confidence']}")
    print(f"  Linearity: {result['analysis']['linearity_detail']}")
    print(f"  Time: {result['analysis_time_ms']:.0f}ms")
    return passed


def test_rule_110():
    """Test Rule 110: (C OR R) AND NOT(L AND C AND R) -- nonlinear, Class IV."""
    print("\n--- Rule 110: (C OR R) AND NOT(L AND C AND R) ---")
    result = analyze(RULE_110_WIRING)

    passed = True
    passed &= assert_eq("wolfram_rule", 110, result["wolfram_rule"])
    passed &= assert_eq("truth_table", [0, 1, 1, 0, 1, 1, 1, 0],
                         result["truth_table"])
    passed &= assert_eq("is_linear", False, result["is_linear"])
    passed &= assert_eq("surjective", True, result["surjective"])
    passed &= assert_eq("z3_verified", True, result["z3_verified"])

    # Rule 110 is known Class IV
    passed &= assert_eq("structural_class_hint", "IV",
                         result["structural_class_hint"])

    print(f"  Sensitivity: mean={result['sensitivity']['mean']}")
    print(f"  Class confidence: {result['class_confidence']}")
    print(f"  Linearity: {result['analysis']['linearity_detail']}")
    print(f"  Time: {result['analysis_time_ms']:.0f}ms")
    return passed


def test_rule_184():
    """Test Rule 184: (C AND R) OR (L AND NOT C) -- nonlinear, Class II."""
    print("\n--- Rule 184: (C AND R) OR (L AND NOT C) ---")
    result = analyze(RULE_184_WIRING)

    passed = True
    passed &= assert_eq("wolfram_rule", 184, result["wolfram_rule"])
    passed &= assert_eq("truth_table", [1, 0, 1, 1, 1, 0, 0, 0],
                         result["truth_table"])
    passed &= assert_eq("is_linear", False, result["is_linear"])
    passed &= assert_eq("surjective", True, result["surjective"])
    passed &= assert_eq("z3_verified", True, result["z3_verified"])

    print(f"  Sensitivity: mean={result['sensitivity']['mean']}")
    print(f"  Structural hint: {result['structural_class_hint']}")
    print(f"  Class confidence: {result['class_confidence']}")
    print(f"  Linearity: {result['analysis']['linearity_detail']}")
    print(f"  Time: {result['analysis_time_ms']:.0f}ms")
    return passed


def test_rule_0():
    """Test Rule 0: constant 0 -- Class I."""
    print("\n--- Rule 0: L AND (NOT L) = constant 0 ---")
    result = analyze(RULE_0_WIRING)

    passed = True
    passed &= assert_eq("wolfram_rule", 0, result["wolfram_rule"])
    passed &= assert_eq("truth_table", [0, 0, 0, 0, 0, 0, 0, 0],
                         result["truth_table"])
    passed &= assert_eq("surjective", False, result["surjective"])
    passed &= assert_eq("z3_verified", True, result["z3_verified"])

    # Sensitivity should be all zeros for constant function
    passed &= assert_eq("sensitivity_mean", 0.0, result["sensitivity"]["mean"])

    # Class I
    passed &= assert_eq("structural_class_hint", "I",
                         result["structural_class_hint"])

    print(f"  Sensitivity: mean={result['sensitivity']['mean']}")
    print(f"  Surjectivity: {result['analysis']['surjectivity_detail']}")
    print(f"  Time: {result['analysis_time_ms']:.0f}ms")
    return passed


def test_rule_255():
    """Test Rule 255: constant 1 -- Class I."""
    print("\n--- Rule 255: L OR (NOT L) = constant 1 ---")
    result = analyze(RULE_255_WIRING)

    passed = True
    passed &= assert_eq("wolfram_rule", 255, result["wolfram_rule"])
    passed &= assert_eq("truth_table", [1, 1, 1, 1, 1, 1, 1, 1],
                         result["truth_table"])
    passed &= assert_eq("surjective", False, result["surjective"])
    passed &= assert_eq("z3_verified", True, result["z3_verified"])

    # Sensitivity should be all zeros for constant function
    passed &= assert_eq("sensitivity_mean", 0.0, result["sensitivity"]["mean"])

    # Class I
    passed &= assert_eq("structural_class_hint", "I",
                         result["structural_class_hint"])

    print(f"  Sensitivity: mean={result['sensitivity']['mean']}")
    print(f"  Surjectivity: {result['analysis']['surjectivity_detail']}")
    print(f"  Time: {result['analysis_time_ms']:.0f}ms")
    return passed


def test_rule_30():
    """Test Rule 30: L XOR (C OR R) -- nonlinear, Class III."""
    print("\n--- Rule 30: L XOR (C OR R) ---")
    result = analyze(RULE_30_WIRING)

    passed = True
    passed &= assert_eq("wolfram_rule", 30, result["wolfram_rule"])
    passed &= assert_eq("truth_table", [0, 0, 0, 1, 1, 1, 1, 0],
                         result["truth_table"])
    passed &= assert_eq("is_linear", False, result["is_linear"])
    passed &= assert_eq("surjective", True, result["surjective"])
    passed &= assert_eq("z3_verified", True, result["z3_verified"])

    print(f"  Sensitivity: mean={result['sensitivity']['mean']}")
    print(f"  Structural hint: {result['structural_class_hint']}")
    print(f"  Class confidence: {result['class_confidence']}")
    print(f"  Time: {result['analysis_time_ms']:.0f}ms")
    return passed


def test_truth_table_helpers():
    """Test truth table conversion helpers."""
    print("\n--- Truth table helpers ---")
    passed = True

    # Round-trip: rule number -> tt -> rule number
    for rule in [0, 30, 90, 110, 184, 255]:
        tt = rule_number_to_truth_table(rule)
        recovered = truth_table_to_rule_number(tt)
        passed &= assert_eq(f"round-trip rule {rule}", rule, recovered)

    return passed


def test_linearity_known_rules():
    """Test linearity classification for known linear and nonlinear rules."""
    print("\n--- Linearity classification ---")
    passed = True

    # Known linear rules (XOR-based): 90, 60, 150, 170, 204
    # Rule 90 = L XOR R
    tt_90 = rule_number_to_truth_table(90)
    is_lin, _ = check_linearity_gf2(tt_90)
    passed &= assert_eq("Rule 90 linear", True, is_lin)

    # Rule 60 = L XOR C
    tt_60 = rule_number_to_truth_table(60)
    is_lin, _ = check_linearity_gf2(tt_60)
    passed &= assert_eq("Rule 60 linear", True, is_lin)

    # Rule 150 = L XOR C XOR R
    tt_150 = rule_number_to_truth_table(150)
    is_lin, _ = check_linearity_gf2(tt_150)
    passed &= assert_eq("Rule 150 linear", True, is_lin)

    # Rule 204 = C (identity, trivially linear)
    tt_204 = rule_number_to_truth_table(204)
    is_lin, _ = check_linearity_gf2(tt_204)
    passed &= assert_eq("Rule 204 linear", True, is_lin)

    # Known nonlinear rules
    tt_110 = rule_number_to_truth_table(110)
    is_lin, detail = check_linearity_gf2(tt_110)
    passed &= assert_eq("Rule 110 nonlinear", False, is_lin)
    print(f"  Rule 110 counterexample: {detail}")

    tt_30 = rule_number_to_truth_table(30)
    is_lin, detail = check_linearity_gf2(tt_30)
    passed &= assert_eq("Rule 30 nonlinear", False, is_lin)

    tt_184 = rule_number_to_truth_table(184)
    is_lin, detail = check_linearity_gf2(tt_184)
    passed &= assert_eq("Rule 184 nonlinear", False, is_lin)

    return passed


def test_sensitivity_extremes():
    """Test sensitivity for extreme cases."""
    print("\n--- Sensitivity extremes ---")
    passed = True

    # Constant function: zero sensitivity
    tt_0 = [0] * 8
    sens_0 = compute_sensitivity(tt_0)
    passed &= assert_eq("Rule 0 mean sensitivity", 0.0, sens_0["mean"])
    passed &= assert_eq("Rule 0 max sensitivity", 0.0, sens_0["max"])

    tt_255 = [1] * 8
    sens_255 = compute_sensitivity(tt_255)
    passed &= assert_eq("Rule 255 mean sensitivity", 0.0, sens_255["mean"])

    # XOR (Rule 90): each input that participates flips the output
    # Rule 90 = L XOR R (C is irrelevant)
    # For any input, flipping L or R flips output, flipping C does not.
    # So sensitivity per input = 2/3 everywhere
    tt_90 = rule_number_to_truth_table(90)
    sens_90 = compute_sensitivity(tt_90)
    passed &= assert_eq("Rule 90 uniform sensitivity",
                         True,
                         all(abs(s - 2/3) < 0.001 for s in sens_90["per_input"]))
    print(f"  Rule 90 sensitivity per input: {sens_90['per_input']}")

    return passed


def test_stdin_stdout_roundtrip():
    """Test that the analyzer works as a stdin/stdout JSON pipe."""
    print("\n--- stdin/stdout roundtrip ---")
    import subprocess
    import os

    # Get the path to the venv python
    venv_python = os.path.join(
        os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
        ".venv-tpg", "bin", "python"
    )
    analyzer_path = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "wiring_analyzer.py"
    )

    input_json = json.dumps(RULE_110_WIRING)

    proc = subprocess.run(
        [venv_python, analyzer_path],
        input=input_json,
        capture_output=True,
        text=True,
        timeout=30
    )

    passed = True
    if proc.returncode != 0:
        print(f"  FAIL: process exited with code {proc.returncode}")
        print(f"  stderr: {proc.stderr}")
        return False

    try:
        result = json.loads(proc.stdout)
        passed &= assert_eq("pipe: wolfram_rule", 110, result["wolfram_rule"])
        passed &= assert_eq("pipe: is_linear", False, result["is_linear"])
        print(f"  OK: stdin/stdout pipe works correctly")
    except json.JSONDecodeError as e:
        print(f"  FAIL: invalid JSON output: {e}")
        print(f"  stdout: {proc.stdout[:500]}")
        passed = False

    return passed


def test_full_output_format():
    """Test that all expected fields are present in the output."""
    print("\n--- Output format completeness ---")
    result = analyze(RULE_110_WIRING)

    expected_keys = [
        "wiring_id", "wolfram_rule", "truth_table", "is_linear",
        "surjective", "sensitivity", "structural_class_hint",
        "class_confidence", "z3_verified", "analysis", "analysis_time_ms"
    ]

    passed = True
    for key in expected_keys:
        if key not in result:
            print(f"  FAIL: missing key '{key}'")
            passed = False
        else:
            print(f"  OK: key '{key}' present")

    # Check nested structures
    sens_keys = ["mean", "max", "min", "per_input"]
    for key in sens_keys:
        if key not in result.get("sensitivity", {}):
            print(f"  FAIL: missing sensitivity.{key}")
            passed = False
        else:
            print(f"  OK: sensitivity.{key} present")

    analysis_keys = ["linearity_detail", "surjectivity_detail",
                     "sensitivity_detail", "class_reasoning"]
    for key in analysis_keys:
        if key not in result.get("analysis", {}):
            print(f"  FAIL: missing analysis.{key}")
            passed = False
        else:
            print(f"  OK: analysis.{key} present")

    # Verify truth_table length
    passed &= assert_eq("truth_table length", 8, len(result["truth_table"]))

    # Verify per_input sensitivity length
    passed &= assert_eq("per_input length", 8,
                         len(result["sensitivity"]["per_input"]))

    return passed


# ============================================================
# Main
# ============================================================

def main():
    print("=" * 60)
    print("WIRING DIAGRAM ANALYZER TESTS")
    print("=" * 60)

    all_passed = True

    # Core tests
    all_passed &= test_truth_table_helpers()
    all_passed &= test_linearity_known_rules()
    all_passed &= test_sensitivity_extremes()

    # Full pipeline tests with wiring diagrams
    all_passed &= test_rule_90()
    all_passed &= test_rule_110()
    all_passed &= test_rule_184()
    all_passed &= test_rule_0()
    all_passed &= test_rule_255()
    all_passed &= test_rule_30()

    # Integration tests
    all_passed &= test_full_output_format()
    all_passed &= test_stdin_stdout_roundtrip()

    print("\n" + "=" * 60)
    if all_passed:
        print("ALL TESTS PASSED")
    else:
        print("SOME TESTS FAILED")
        sys.exit(1)
    print("=" * 60)


if __name__ == "__main__":
    main()
