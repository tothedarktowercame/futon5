#!/usr/bin/env python3
"""Z3/SMT-based static analysis of Tangled Program Graphs.

Encodes TPG routing as an SMT problem to analyze:
1. Operator reachability — which operators can actually be reached?
2. Coverage regions — what diagnostic space maps to each operator?
3. Dead programs — programs that can never win any bid competition
4. Verifier satisfiability — can the TPG satisfy all verifier bands?

Usage:
    echo '{"tpg": {...}, "verifier_spec": {...}}' | python smt_analyzer.py

Input JSON:
    tpg: TPG structure with teams, programs, config
    verifier_spec: {verifier_key: [center, width], ...}

Output JSON:
    reachable_operators: [operator_id, ...]
    unreachable_operators: [operator_id, ...]
    dead_programs: [{team_id, program_id, reason}, ...]
    coverage: {operator_id: {volume_estimate, example_diagnostic}, ...}
    verifier_satisfiable: bool
    satisfying_diagnostic: [d0, ..., d5] or null
    analysis_time_ms: float
"""

import json
import sys
import time
from itertools import combinations

import z3

# Diagnostic dimensions
DIAG_DIM = 6
DIAG_KEYS = ["entropy", "change", "autocorr", "diversity",
             "phenotype_coupling", "damage_spread"]

ALL_OPERATORS = [
    "expansion", "conservation", "adaptation", "momentum",
    "conditional", "differentiation", "transformation", "consolidation"
]


def make_diagnostic_vars(prefix="d"):
    """Create Z3 real variables for a diagnostic vector in [0, 1]."""
    dvars = [z3.Real(f"{prefix}_{i}") for i in range(DIAG_DIM)]
    constraints = []
    for d in dvars:
        constraints.append(d >= 0)
        constraints.append(d <= 1)
    return dvars, constraints


def encode_program_bid(program, dvars):
    """Encode program bid as Z3 expression: w . D + b"""
    weights = program["weights"]
    bias = program.get("bias", 0.0)
    bid = z3.RealVal(bias)
    for i, w in enumerate(weights):
        if i < len(dvars):
            bid = bid + z3.RealVal(w) * dvars[i]
    return bid


def encode_team_winner(team, dvars, prefix=""):
    """Encode: which program wins in a team (highest bid)?

    Returns list of (condition, action) pairs where condition is a Z3 formula
    expressing that this program has the highest bid.
    """
    programs = team["programs"]
    bids = [(p, encode_program_bid(p, dvars)) for p in programs]

    winners = []
    for i, (prog_i, bid_i) in enumerate(bids):
        # prog_i wins if its bid >= all other bids
        conditions = []
        for j, (prog_j, bid_j) in enumerate(bids):
            if i != j:
                conditions.append(bid_i >= bid_j)
        # For strict uniqueness in ties, use index ordering (lower index wins)
        # This matches Clojure's max-key behavior (last maximal element wins)
        # Actually Clojure's max-key returns the last element with the max value
        # For SMT, we just need coverage so >= is fine
        win_cond = z3.And(*conditions) if conditions else z3.BoolVal(True)
        winners.append((win_cond, prog_i["action"]))

    return winners


def build_team_index(tpg):
    """Build team-id -> team map."""
    return {t["team_id"]: t for t in tpg["teams"]}


def encode_routing(tpg, dvars, max_depth=4):
    """Encode TPG routing as a set of (path_condition, operator_id) pairs.

    Each pair says: if path_condition holds for the diagnostic, routing
    ends at operator_id.
    """
    team_index = build_team_index(tpg)
    root_id = tpg.get("config", {}).get("root_team")
    if not root_id:
        root_id = tpg["teams"][0]["team_id"] if tpg["teams"] else None

    if not root_id or root_id not in team_index:
        return []

    # BFS through the routing graph, collecting path conditions
    # State: (team_id, path_condition, depth, path)
    routes = []
    frontier = [(root_id, z3.BoolVal(True), 0, [root_id])]

    while frontier:
        team_id, path_cond, depth, path = frontier.pop(0)

        if team_id not in team_index:
            # Missing team — fallback to adaptation
            routes.append((path_cond, "adaptation", path, True))
            continue

        if depth >= max_depth:
            # Depth limit — find operator program or fallback
            team = team_index[team_id]
            for p in team["programs"]:
                if p["action"]["type"] == "operator":
                    routes.append((path_cond, p["action"]["target"], path, True))
                    break
            else:
                routes.append((path_cond, "adaptation", path, True))
            continue

        team = team_index[team_id]
        winners = encode_team_winner(team, dvars, prefix=f"d{depth}")

        for win_cond, action in winners:
            combined_cond = z3.And(path_cond, win_cond)
            if action["type"] == "operator":
                routes.append((combined_cond, action["target"], path, False))
            elif action["type"] == "team":
                next_team = action["target"]
                if next_team in path:
                    # Cycle — fallback
                    routes.append((combined_cond, "adaptation",
                                   path + [next_team], True))
                else:
                    frontier.append((next_team, combined_cond, depth + 1,
                                     path + [next_team]))

    return routes


def analyze_reachability(routes, dvars, bounds):
    """Check which operators are reachable (have satisfiable path conditions)."""
    reachable = {}
    unreachable = []

    # Group routes by operator
    op_routes = {}
    for cond, op_id, path, is_fallback in routes:
        if op_id not in op_routes:
            op_routes[op_id] = []
        op_routes[op_id].append((cond, path, is_fallback))

    for op_id in ALL_OPERATORS:
        if op_id not in op_routes:
            unreachable.append(op_id)
            continue

        # Check if any route to this operator is satisfiable
        solver = z3.Solver()
        solver.set("timeout", 2000)  # 2s timeout per operator
        solver.add(bounds)

        # Disjunction of all path conditions leading to this operator
        conditions = [cond for cond, _, _ in op_routes[op_id]]
        solver.add(z3.Or(*conditions))

        result = solver.check()
        if result == z3.sat:
            model = solver.model()
            example = [float(model.eval(d, model_completion=True).as_fraction())
                       for d in dvars]
            non_fallback = any(not fb for _, _, fb in op_routes[op_id])
            reachable[op_id] = {
                "example_diagnostic": example,
                "n_routes": len(op_routes[op_id]),
                "has_direct_route": non_fallback
            }
        else:
            unreachable.append(op_id)

    return reachable, unreachable


def analyze_dead_programs(tpg, dvars, bounds):
    """Find programs that can never win in their team."""
    dead = []
    team_index = build_team_index(tpg)

    for team in tpg["teams"]:
        programs = team["programs"]
        if len(programs) <= 1:
            continue

        bids = [(p, encode_program_bid(p, dvars)) for p in programs]

        for i, (prog_i, bid_i) in enumerate(bids):
            # Check: is there any diagnostic where prog_i has the highest bid?
            solver = z3.Solver()
            solver.set("timeout", 1000)
            solver.add(bounds)

            # prog_i must beat all others
            for j, (prog_j, bid_j) in enumerate(bids):
                if i != j:
                    solver.add(bid_i > bid_j)  # strict inequality

            result = solver.check()
            if result == z3.unsat:
                dead.append({
                    "team_id": team["team_id"],
                    "program_id": prog_i.get("program_id", f"program_{i}"),
                    "reason": "never wins: bid always dominated by another program"
                })

    return dead


def estimate_coverage(routes, dvars, bounds, n_samples=50):
    """Estimate coverage volume for each operator using random sampling.

    Uses Z3 to check satisfiability at random diagnostic points,
    then routes to determine which operator would be selected.
    """
    import random
    random.seed(42)

    operator_counts = {op: 0 for op in ALL_OPERATORS}
    total = 0

    for _ in range(n_samples):
        # Random diagnostic in [0, 1]^6
        point = [random.random() for _ in range(DIAG_DIM)]

        # Evaluate routing at this point (no Z3 needed, just arithmetic)
        op = evaluate_routing_at_point(routes, point)
        if op:
            operator_counts[op] = operator_counts.get(op, 0) + 1
            total += 1

    coverage = {}
    for op in ALL_OPERATORS:
        count = operator_counts.get(op, 0)
        coverage[op] = {
            "volume_estimate": count / max(1, total),
            "sample_count": count
        }

    return coverage


def evaluate_routing_at_point(routes, point):
    """Evaluate which operator would be selected at a given diagnostic point.

    This does pure arithmetic evaluation (no Z3) for speed.
    """
    # We need to trace through the TPG like the Clojure code does
    # But we have pre-computed routes as Z3 conditions
    # Instead, let's just evaluate the bids directly

    # Actually, for sampling we need the TPG structure, not the Z3 encoding
    # Let's use a simpler approach: store the TPG and evaluate directly
    return None  # Will be replaced by direct evaluation


def _get_team_id(team):
    """Get team ID handling both normalized and Clojure-style keys."""
    return team.get("team_id") or team.get("team/id")


def _get_config_val(config, key, default=None):
    """Get config value handling both underscore and hyphen keys."""
    return config.get(key) or config.get(key.replace("_", "-"), default)


def evaluate_tpg_at_point(tpg, point):
    """Evaluate TPG routing at a concrete diagnostic point."""
    team_index = {_get_team_id(t): t for t in tpg["teams"]}
    config = tpg.get("config", {})
    root_id = _get_config_val(config, "root_team")
    if not root_id:
        root_id = _get_team_id(tpg["teams"][0]) if tpg["teams"] else None

    current_id = root_id
    visited = set()
    max_depth = _get_config_val(config, "max_depth", 4)

    for depth in range(max_depth + 1):
        if current_id in visited or current_id not in team_index:
            return "adaptation"
        visited.add(current_id)

        team = team_index[current_id]
        # Find winning program (highest bid)
        best_bid = float('-inf')
        best_action = None
        for prog in team["programs"]:
            bid = prog.get("bias", 0.0)
            for i, w in enumerate(prog["weights"]):
                if i < len(point):
                    bid += w * point[i]
            if bid >= best_bid:
                best_bid = bid
                best_action = prog["action"]

        if not best_action:
            return "adaptation"

        if best_action["type"] == "operator":
            return best_action["target"]
        elif best_action["type"] == "team":
            current_id = best_action["target"]
        else:
            return "adaptation"

    return "adaptation"  # depth limit


def analyze_coverage_direct(tpg, n_samples=200):
    """Estimate coverage by direct evaluation at random points."""
    import random
    random.seed(42)

    operator_counts = {}
    for _ in range(n_samples):
        point = [random.random() for _ in range(DIAG_DIM)]
        op = evaluate_tpg_at_point(tpg, point)
        operator_counts[op] = operator_counts.get(op, 0) + 1

    coverage = {}
    for op in ALL_OPERATORS:
        count = operator_counts.get(op, 0)
        coverage[op] = {
            "volume_estimate": count / n_samples,
            "sample_count": count
        }
    return coverage


def analyze_verifier_satisfiability(tpg, verifier_spec, dvars, bounds, routes):
    """Check if there exists a diagnostic where the TPG routes to an operator
    that would satisfy all verifier bands.

    This checks: is there a diagnostic D such that:
    1. The TPG routes to some operator at D
    2. D itself falls within all verifier target bands

    This is a necessary condition for the TPG to achieve perfect satisfaction.
    """
    solver = z3.Solver()
    solver.set("timeout", 5000)
    solver.add(bounds)

    # Verifier band constraints on the diagnostic itself
    diag_key_to_idx = {k: i for i, k in enumerate(DIAG_KEYS)}
    band_constraints = []
    for vkey, (center, width) in verifier_spec.items():
        idx = diag_key_to_idx.get(vkey)
        if idx is not None:
            # |d[idx] - center| < width  <==>  center - width < d[idx] < center + width
            band_constraints.append(dvars[idx] > z3.RealVal(center - width))
            band_constraints.append(dvars[idx] < z3.RealVal(center + width))

    solver.add(band_constraints)

    # Also require that routing doesn't fall back
    # (i.e., there's a non-fallback route whose condition is satisfiable
    #  within the verifier bands)
    non_fallback_conditions = [cond for cond, _, _, is_fb in routes if not is_fb]
    if non_fallback_conditions:
        solver.add(z3.Or(*non_fallback_conditions))

    result = solver.check()
    if result == z3.sat:
        model = solver.model()
        satisfying = [float(model.eval(d, model_completion=True).as_fraction())
                      for d in dvars]
        # Find which operator this diagnostic routes to
        op = evaluate_tpg_at_point(tpg, satisfying)
        return True, satisfying, op
    else:
        return False, None, None


def analyze_operator_regions(routes, dvars, bounds):
    """For each reachable operator, find the boundary conditions.

    Returns symbolic conditions for when each operator is selected.
    This helps understand the decision boundaries.
    """
    # Group by operator
    op_conditions = {}
    for cond, op_id, path, is_fallback in routes:
        if op_id not in op_conditions:
            op_conditions[op_id] = []
        op_conditions[op_id].append({
            "path": path,
            "is_fallback": is_fallback
        })

    return op_conditions


def parse_tpg_json(data):
    """Parse TPG from JSON, handling keyword-style keys from Clojure EDN."""
    tpg = data["tpg"]

    # Normalize team structure
    teams = []
    for team in tpg.get("teams", []):
        team_id = team.get("team/id") or team.get("team_id")
        programs = []
        for prog in team.get("programs", []):
            action = prog.get("action", {})
            programs.append({
                "program_id": prog.get("program/id") or prog.get("program_id"),
                "weights": prog.get("weights", [0] * DIAG_DIM),
                "bias": prog.get("bias", 0.0),
                "action": {
                    "type": action.get("type", "operator"),
                    "target": action.get("target", "adaptation")
                }
            })
        teams.append({
            "team_id": team_id,
            "programs": programs
        })

    config = tpg.get("config", {})
    root_team = config.get("root-team") or config.get("root_team")
    if not root_team and teams:
        root_team = teams[0]["team_id"]

    normalized = {
        "tpg_id": tpg.get("tpg/id") or tpg.get("tpg_id", "unknown"),
        "teams": teams,
        "config": {
            "root_team": root_team,
            "max_depth": config.get("max-depth", config.get("max_depth", 4))
        }
    }

    # Parse verifier spec
    verifier_spec = data.get("verifier_spec", {
        "entropy": [0.6, 0.35],
        "change": [0.2, 0.2],
        "autocorr": [0.6, 0.3],
        "diversity": [0.4, 0.3]
    })

    return normalized, verifier_spec


def analyze(data):
    """Run full SMT analysis on a TPG."""
    start = time.time()

    tpg, verifier_spec = parse_tpg_json(data)

    # Create diagnostic variables
    dvars, bounds = make_diagnostic_vars()

    # Encode routing
    routes = encode_routing(tpg, dvars)

    # 1. Reachability
    reachable, unreachable = analyze_reachability(routes, dvars, bounds)

    # 2. Dead programs
    dead = analyze_dead_programs(tpg, dvars, bounds)

    # 3. Coverage estimation (direct evaluation, faster than Z3)
    coverage = analyze_coverage_direct(tpg, n_samples=500)

    # 4. Verifier satisfiability
    sat, sat_diag, sat_op = analyze_verifier_satisfiability(
        tpg, verifier_spec, dvars, bounds, routes)

    # 5. Operator region info
    regions = analyze_operator_regions(routes, dvars, bounds)

    elapsed = (time.time() - start) * 1000

    result = {
        "tpg_id": tpg["tpg_id"],
        "n_teams": len(tpg["teams"]),
        "n_programs": sum(len(t["programs"]) for t in tpg["teams"]),
        "reachable_operators": list(reachable.keys()),
        "unreachable_operators": unreachable,
        "reachable_details": reachable,
        "dead_programs": dead,
        "coverage": coverage,
        "verifier_satisfiable": sat,
        "satisfying_diagnostic": sat_diag,
        "satisfying_operator": sat_op,
        "operator_routes": {
            op: len(info) for op, info in regions.items()
        },
        "analysis_time_ms": round(elapsed, 1)
    }

    return result


def main():
    data = json.load(sys.stdin)
    result = analyze(data)
    json.dump(result, sys.stdout, indent=2)
    print()  # newline


if __name__ == "__main__":
    main()
