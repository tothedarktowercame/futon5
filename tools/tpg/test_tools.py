#!/usr/bin/env python3
"""Quick smoke test for SMT analyzer and JAX refiner."""

import json
import numpy as np

# Sample TPG matching seed-tpg-simple
SAMPLE_TPG = {
    "tpg/id": "seed-simple",
    "teams": [
        {
            "team/id": "root",
            "programs": [
                {"program/id": "p-entropy-hi",
                 "weights": [0.8, 0.0, 0.0, 0.0, 0.0, 0.0], "bias": 0.0,
                 "action": {"type": "operator", "target": "conservation"}},
                {"program/id": "p-entropy-lo",
                 "weights": [-0.8, 0.0, 0.0, 0.0, 0.0, 0.0], "bias": 0.0,
                 "action": {"type": "operator", "target": "expansion"}},
                {"program/id": "p-change-hi",
                 "weights": [0.0, 0.8, 0.0, 0.0, 0.0, 0.0], "bias": 0.0,
                 "action": {"type": "operator", "target": "consolidation"}},
                {"program/id": "p-change-lo",
                 "weights": [0.0, -0.8, 0.0, 0.0, 0.0, 0.0], "bias": 0.0,
                 "action": {"type": "operator", "target": "transformation"}},
                {"program/id": "p-autocorr-hi",
                 "weights": [0.0, 0.0, 0.8, 0.0, 0.0, 0.0], "bias": 0.0,
                 "action": {"type": "operator", "target": "differentiation"}},
                {"program/id": "p-autocorr-lo",
                 "weights": [0.0, 0.0, -0.8, 0.0, 0.0, 0.0], "bias": 0.0,
                 "action": {"type": "operator", "target": "momentum"}},
                {"program/id": "p-diversity-hi",
                 "weights": [0.0, 0.0, 0.0, 0.8, 0.0, 0.0], "bias": 0.0,
                 "action": {"type": "operator", "target": "conditional"}},
                {"program/id": "p-diversity-lo",
                 "weights": [0.0, 0.0, 0.0, -0.8, 0.0, 0.0], "bias": 0.0,
                 "action": {"type": "operator", "target": "adaptation"}}
            ]
        }
    ],
    "config": {"root-team": "root", "max-depth": 4}
}

# Hierarchical TPG
HIERARCHICAL_TPG = {
    "tpg/id": "seed-hierarchical",
    "teams": [
        {
            "team/id": "root",
            "programs": [
                {"program/id": "p-frozen",
                 "weights": [-0.6, -0.6, 0.0, 0.0, 0.0, 0.0], "bias": -0.2,
                 "action": {"type": "team", "target": "frozen-team"}},
                {"program/id": "p-chaotic",
                 "weights": [0.6, 0.6, 0.0, 0.0, 0.0, 0.0], "bias": -0.2,
                 "action": {"type": "team", "target": "chaotic-team"}},
                {"program/id": "p-eoc",
                 "weights": [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], "bias": 0.3,
                 "action": {"type": "team", "target": "eoc-team"}}
            ]
        },
        {
            "team/id": "frozen-team",
            "programs": [
                {"program/id": "p-frozen-expand",
                 "weights": [0.0, 0.0, 0.0, -0.5, 0.0, 0.0], "bias": 0.1,
                 "action": {"type": "operator", "target": "expansion"}},
                {"program/id": "p-frozen-transform",
                 "weights": [0.0, 0.0, 0.0, 0.5, 0.0, 0.0], "bias": 0.0,
                 "action": {"type": "operator", "target": "transformation"}},
                {"program/id": "p-frozen-momentum",
                 "weights": [0.0, -0.3, 0.0, 0.0, 0.0, 0.5], "bias": 0.0,
                 "action": {"type": "operator", "target": "momentum"}}
            ]
        },
        {
            "team/id": "chaotic-team",
            "programs": [
                {"program/id": "p-chaotic-conserve",
                 "weights": [0.5, 0.0, 0.0, 0.0, 0.0, 0.0], "bias": 0.1,
                 "action": {"type": "operator", "target": "conservation"}},
                {"program/id": "p-chaotic-consolidate",
                 "weights": [0.0, 0.5, 0.0, 0.0, 0.0, 0.0], "bias": 0.0,
                 "action": {"type": "operator", "target": "consolidation"}},
                {"program/id": "p-chaotic-conditional",
                 "weights": [0.0, 0.0, 0.0, 0.0, 0.5, 0.0], "bias": 0.0,
                 "action": {"type": "operator", "target": "conditional"}}
            ]
        },
        {
            "team/id": "eoc-team",
            "programs": [
                {"program/id": "p-eoc-adapt",
                 "weights": [0.0, 0.0, 0.0, 0.0, 0.0, 0.0], "bias": 0.2,
                 "action": {"type": "operator", "target": "adaptation"}},
                {"program/id": "p-eoc-differentiate",
                 "weights": [0.0, 0.0, 0.5, 0.0, 0.0, 0.0], "bias": 0.0,
                 "action": {"type": "operator", "target": "differentiation"}},
                {"program/id": "p-eoc-conditional",
                 "weights": [0.0, 0.0, 0.0, 0.0, 0.6, 0.0], "bias": 0.0,
                 "action": {"type": "operator", "target": "conditional"}}
            ]
        }
    ],
    "config": {"root-team": "root", "max-depth": 4}
}

VERIFIER_SPEC = {
    "entropy": [0.6, 0.35],
    "change": [0.2, 0.2],
    "autocorr": [0.6, 0.3],
    "diversity": [0.4, 0.3]
}


def test_smt():
    print("=" * 60)
    print("SMT ANALYZER TEST")
    print("=" * 60)
    from smt_analyzer import analyze

    for name, tpg in [("simple", SAMPLE_TPG), ("hierarchical", HIERARCHICAL_TPG)]:
        print(f"\n--- {name} TPG ---")
        result = analyze({"tpg": tpg, "verifier_spec": VERIFIER_SPEC})
        print(f"  Reachable operators: {result['reachable_operators']}")
        print(f"  Unreachable operators: {result['unreachable_operators']}")
        print(f"  Dead programs: {len(result['dead_programs'])}")
        for dp in result['dead_programs']:
            print(f"    {dp['program_id']}: {dp['reason']}")
        print(f"  Verifier satisfiable: {result['verifier_satisfiable']}")
        if result['satisfying_diagnostic']:
            diag = result['satisfying_diagnostic']
            print(f"  Satisfying diagnostic: [{', '.join(f'{d:.3f}' for d in diag)}]")
            print(f"  Routes to: {result['satisfying_operator']}")
        print(f"  Coverage:")
        for op, info in sorted(result['coverage'].items()):
            if info['sample_count'] > 0:
                print(f"    {op:20s} {info['volume_estimate']:.1%} ({info['sample_count']} samples)")
        print(f"  Analysis time: {result['analysis_time_ms']:.0f}ms")


def test_jax():
    print("\n" + "=" * 60)
    print("JAX REFINER TEST")
    print("=" * 60)
    from jax_refine import refine_weights

    # Generate synthetic diagnostic traces
    rng = np.random.RandomState(42)
    n_runs = 3
    n_gens = 15
    traces = rng.random((n_runs, n_gens, 6)).tolist()

    for name, tpg in [("simple", SAMPLE_TPG), ("hierarchical", HIERARCHICAL_TPG)]:
        print(f"\n--- {name} TPG ---")
        data = {
            "tpg": tpg,
            "traces": traces,
            "verifier_spec": VERIFIER_SPEC,
            "config": {
                "learning_rate": 0.01,
                "n_steps": 50,
                "temperature": 0.1
            }
        }
        result = refine_weights(data, data["config"])
        print(f"  Original satisfaction:  {result['original_satisfaction']:.4f}")
        print(f"  Refined satisfaction:   {result['refined_satisfaction']:.4f}")
        print(f"  Improvement:            {result['improvement']:+.4f} ({result['improvement_pct']:+.1f}%)")
        print(f"  Original op dist:")
        for op, p in sorted(result['original_operator_dist'].items(), key=lambda x: -x[1]):
            if p > 0.01:
                print(f"    {op:20s} {p:.1%}")
        print(f"  Refined op dist:")
        for op, p in sorted(result['refined_operator_dist'].items(), key=lambda x: -x[1]):
            if p > 0.01:
                print(f"    {op:20s} {p:.1%}")
        print(f"  Time: {result['time_ms']:.0f}ms")


if __name__ == "__main__":
    test_smt()
    test_jax()
    print("\n" + "=" * 60)
    print("ALL TESTS PASSED")
    print("=" * 60)
