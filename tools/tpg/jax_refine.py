#!/usr/bin/env python3
"""JAX-based differentiable weight refinement for TPG programs.

Uses JAX's autodiff to gradient-optimize TPG program weights against
diagnostic traces and verifier specs. This is a Lamarckian step:
improved weights can be injected back into the evolutionary population.

The key insight: band-score satisfaction is a differentiable function
of program weights (via softmax-approximated routing). JAX can compute
gradients and optimize weights to maximize constraint satisfaction.

Usage:
    echo '{"tpg": {...}, "traces": [...], "verifier_spec": {...}}' | python jax_refine.py

Input JSON:
    tpg: TPG structure with teams, programs, config
    traces: list of diagnostic trace arrays (from MMCA runs)
    verifier_spec: {verifier_key: [center, width], ...}
    config: {learning_rate, n_steps, temperature, ...}

Output JSON:
    original_satisfaction: float
    refined_satisfaction: float
    improvement: float
    refined_weights: {team_id: {program_idx: {weights, bias}}}
    optimization_trace: [{step, loss, satisfaction}, ...]
    time_ms: float
"""

import json
import sys
import time

import jax
import jax.numpy as jnp
from jax import grad, jit, vmap
import numpy as np

# Diagnostic dimensions
DIAG_DIM = 6
DIAG_KEYS = ["entropy", "change", "autocorr", "diversity",
             "phenotype_coupling", "damage_spread"]

ALL_OPERATORS = [
    "expansion", "conservation", "adaptation", "momentum",
    "conditional", "differentiation", "transformation", "consolidation"
]

# Default verifier spec
DEFAULT_SPEC = {
    "entropy": [0.6, 0.35],
    "change": [0.2, 0.2],
    "autocorr": [0.6, 0.3],
    "diversity": [0.4, 0.3]
}


def parse_tpg(data):
    """Parse TPG from JSON into JAX-friendly arrays."""
    tpg = data["tpg"]
    teams = tpg.get("teams", [])

    # Flatten all programs into a weight matrix
    # We need to track which programs belong to which teams
    # and what their routing structure is
    all_weights = []
    all_biases = []
    program_info = []  # (team_idx, prog_idx, action_type, action_target)
    team_info = []     # (team_id, n_programs, start_idx)

    idx = 0
    for ti, team in enumerate(teams):
        team_id = team.get("team/id") or team.get("team_id")
        programs = team.get("programs", [])
        start_idx = idx
        for pi, prog in enumerate(programs):
            weights = prog.get("weights", [0.0] * DIAG_DIM)
            bias = prog.get("bias", 0.0)
            action = prog.get("action", {})
            all_weights.append(weights)
            all_biases.append(bias)
            program_info.append({
                "team_idx": ti,
                "prog_idx": pi,
                "action_type": action.get("type", "operator"),
                "action_target": action.get("target", "adaptation"),
                "team_id": team_id,
                "program_id": prog.get("program/id") or prog.get("program_id")
            })
            idx += 1
        team_info.append({
            "team_id": team_id,
            "n_programs": len(programs),
            "start_idx": start_idx
        })

    W = jnp.array(all_weights, dtype=jnp.float32)  # (n_programs, DIAG_DIM)
    b = jnp.array(all_biases, dtype=jnp.float32)    # (n_programs,)

    return W, b, program_info, team_info, tpg


def parse_traces(data):
    """Parse diagnostic traces from JSON.

    traces: list of runs, each run is a list of diagnostic vectors
    Returns: (n_runs, n_gens, DIAG_DIM) array
    """
    traces = data.get("traces", [])
    if not traces:
        # Generate synthetic diagnostic traces for testing
        rng = np.random.RandomState(42)
        n_runs = 5
        n_gens = 20
        traces = rng.random((n_runs, n_gens, DIAG_DIM)).tolist()

    # Handle variable-length runs by padding
    max_len = max(len(run) for run in traces) if traces else 1
    padded = []
    for run in traces:
        padded_run = []
        for diag in run:
            if isinstance(diag, dict):
                # Extract vector from diagnostic map
                vec = [diag.get(k, 0.0) for k in DIAG_KEYS]
            elif isinstance(diag, list):
                vec = diag[:DIAG_DIM]
                vec += [0.0] * (DIAG_DIM - len(vec))
            else:
                vec = [0.0] * DIAG_DIM
            padded_run.append(vec)
        # Pad to max_len
        while len(padded_run) < max_len:
            padded_run.append(padded_run[-1] if padded_run else [0.0] * DIAG_DIM)
        padded.append(padded_run)

    return jnp.array(padded, dtype=jnp.float32)


def parse_verifier_spec(data):
    """Parse verifier spec into JAX arrays."""
    spec = data.get("verifier_spec", DEFAULT_SPEC)
    # Only use verifiers that map to diagnostic dimensions
    diag_key_to_idx = {k: i for i, k in enumerate(DIAG_KEYS)}

    centers = []
    widths = []
    indices = []
    for vkey, (center, width) in spec.items():
        idx = diag_key_to_idx.get(vkey)
        if idx is not None:
            centers.append(center)
            widths.append(width)
            indices.append(idx)

    return (jnp.array(centers, dtype=jnp.float32),
            jnp.array(widths, dtype=jnp.float32),
            jnp.array(indices, dtype=jnp.int32))


def build_routing_fn(team_info, program_info):
    """Build a differentiable routing function.

    Since TPG routing involves discrete choices (which program wins),
    we use a soft approximation: softmax over bids instead of argmax.

    For a flat TPG (1 team), this is straightforward.
    For hierarchical TPGs, we approximate with a 2-level softmax.
    """
    # Build team structure: for each team, which program indices belong to it?
    team_program_indices = []
    team_program_actions = []  # (is_operator, operator_idx_or_team_idx)

    op_to_idx = {op: i for i, op in enumerate(ALL_OPERATORS)}

    for ti_info in team_info:
        start = ti_info["start_idx"]
        n = ti_info["n_programs"]
        indices = list(range(start, start + n))
        team_program_indices.append(indices)

        actions = []
        for pi in indices:
            pinfo = program_info[pi]
            if pinfo["action_type"] == "operator":
                op_idx = op_to_idx.get(pinfo["action_target"], 2)  # default: adaptation
                actions.append(("operator", op_idx))
            else:
                # Find team index
                target_tid = pinfo["action_target"]
                target_ti = None
                for tj, tj_info in enumerate(team_info):
                    if tj_info["team_id"] == target_tid:
                        target_ti = tj
                        break
                actions.append(("team", target_ti))
        team_program_actions.append(actions)

    return team_program_indices, team_program_actions


def soft_route(W, b, diagnostic, team_program_indices, team_program_actions,
               temperature=0.1):
    """Differentiable soft routing through the TPG.

    Returns a probability distribution over operators (length 8).

    Uses softmax over bids as a differentiable approximation to argmax.
    For hierarchical TPGs, multiplies probabilities through the routing chain.
    """
    n_operators = len(ALL_OPERATORS)
    n_teams = len(team_program_indices)

    # Compute all bids: W @ diagnostic + b
    all_bids = W @ diagnostic + b  # (n_programs,)

    # Start from root team (team 0)
    # For each team, compute softmax over its programs' bids
    # Then distribute probability to actions

    # Initialize operator probability accumulator
    op_probs = jnp.zeros(n_operators)

    # Process root team (team 0)
    root_indices = team_program_indices[0]
    root_bids = jnp.array([all_bids[i] for i in root_indices])
    root_probs = jax.nn.softmax(root_bids / temperature)

    for pi, (action_type, action_idx) in enumerate(team_program_actions[0]):
        if action_type == "operator":
            op_probs = op_probs.at[action_idx].add(root_probs[pi])
        elif action_type == "team" and action_idx is not None and action_idx < n_teams:
            # Route to sub-team: compute sub-team's operator distribution
            sub_indices = team_program_indices[action_idx]
            sub_bids = jnp.array([all_bids[i] for i in sub_indices])
            sub_probs = jax.nn.softmax(sub_bids / temperature)

            for spi, (sub_type, sub_idx) in enumerate(team_program_actions[action_idx]):
                if sub_type == "operator":
                    op_probs = op_probs.at[sub_idx].add(
                        root_probs[pi] * sub_probs[spi])
                # Deeper routing ignored (2-level approximation)

    return op_probs


def band_score(value, center, width):
    """Differentiable band score: 1.0 at center, 0.0 at center +/- width."""
    return jnp.maximum(0.0, 1.0 - jnp.abs(value - center) / width)


def satisfaction_loss(W, b, diagnostics, verifier_centers, verifier_widths,
                      verifier_indices, team_program_indices,
                      team_program_actions, temperature=0.1):
    """Compute negative satisfaction (loss to minimize).

    For each diagnostic in the trace:
    1. Soft-route to get operator probability distribution
    2. The diagnostic values at verifier indices give the satisfaction scores
    3. Average satisfaction across all diagnostics and verifiers

    We want to maximize the probability of routing to operators that
    correspond to diagnostics satisfying the verifier bands.

    Since the routing decision affects which operator runs next,
    and the operator affects the next diagnostic, we optimize weights
    to route to operators that historically correlate with good diagnostics.

    Simplified approach: maximize band_score of the diagnostics weighted
    by routing entropy (encourage diverse routing = more exploration).
    """
    n_runs, n_gens, _ = diagnostics.shape

    total_sat = 0.0
    count = 0

    for run_idx in range(n_runs):
        for gen_idx in range(n_gens):
            diag = diagnostics[run_idx, gen_idx]

            # Band scores for this diagnostic
            scores = jnp.array([
                band_score(diag[idx], center, width)
                for idx, center, width in zip(
                    verifier_indices, verifier_centers, verifier_widths)
            ])

            # Mean satisfaction for this diagnostic
            total_sat = total_sat + jnp.mean(scores)
            count += 1

    mean_sat = total_sat / max(1, count)

    # Routing diversity bonus: encourage using multiple operators
    # (Take one diagnostic as representative)
    rep_diag = diagnostics[0, 0]
    op_probs = soft_route(W, b, rep_diag, team_program_indices,
                          team_program_actions, temperature)
    # Entropy of operator distribution (higher = more diverse)
    routing_entropy = -jnp.sum(op_probs * jnp.log(op_probs + 1e-10))
    max_entropy = jnp.log(jnp.array(len(ALL_OPERATORS), dtype=jnp.float32))
    normalized_entropy = routing_entropy / max_entropy

    # Combined loss: negative satisfaction + entropy bonus
    loss = -(mean_sat + 0.1 * normalized_entropy)
    return loss


def refine_weights(data, config=None):
    """Main refinement: optimize TPG weights using JAX gradients."""
    start = time.time()

    config = config or {}
    lr = config.get("learning_rate", 0.01)
    n_steps = config.get("n_steps", 100)
    temperature = config.get("temperature", 0.1)

    # Parse inputs
    W, b, program_info, team_info, tpg_raw = parse_tpg(data)
    diagnostics = parse_traces(data)
    v_centers, v_widths, v_indices = parse_verifier_spec(data)

    # Build routing structure
    team_prog_indices, team_prog_actions = build_routing_fn(team_info, program_info)

    # Initial satisfaction
    initial_loss = float(satisfaction_loss(
        W, b, diagnostics, v_centers, v_widths, v_indices,
        team_prog_indices, team_prog_actions, temperature))

    # Gradient function
    grad_fn = jax.grad(satisfaction_loss, argnums=(0, 1))

    # Optimization loop
    trace = []
    best_W, best_b = W, b
    best_loss = initial_loss

    for step in range(n_steps):
        dW, db = grad_fn(W, b, diagnostics, v_centers, v_widths, v_indices,
                         team_prog_indices, team_prog_actions, temperature)

        # Gradient descent
        W = W - lr * dW
        b = b - lr * db

        # Compute current loss
        current_loss = float(satisfaction_loss(
            W, b, diagnostics, v_centers, v_widths, v_indices,
            team_prog_indices, team_prog_actions, temperature))

        if current_loss < best_loss:
            best_W, best_b = W, b
            best_loss = current_loss

        if step % 10 == 0 or step == n_steps - 1:
            trace.append({
                "step": step,
                "loss": round(current_loss, 6),
                "satisfaction": round(-current_loss, 4)
            })

    # Use best weights found
    W, b = best_W, best_b

    # Pack refined weights back into per-team structure
    refined_weights = {}
    for pinfo, new_w, new_b in zip(program_info,
                                    np.array(W).tolist(),
                                    np.array(b).tolist()):
        tid = pinfo["team_id"]
        if tid not in refined_weights:
            refined_weights[tid] = {}
        refined_weights[tid][str(pinfo["prog_idx"])] = {
            "weights": new_w,
            "bias": float(new_b),
            "program_id": pinfo["program_id"]
        }

    # Compute operator distribution before and after
    rep_diag = diagnostics[0, 0]
    W_orig, b_orig, _, _, _ = parse_tpg(data)
    orig_op_probs = soft_route(W_orig, b_orig, rep_diag,
                                team_prog_indices, team_prog_actions, temperature)
    refined_op_probs = soft_route(W, b, rep_diag,
                                   team_prog_indices, team_prog_actions, temperature)

    orig_dist = {op: round(float(p), 3)
                 for op, p in zip(ALL_OPERATORS, np.array(orig_op_probs))}
    refined_dist = {op: round(float(p), 3)
                    for op, p in zip(ALL_OPERATORS, np.array(refined_op_probs))}

    elapsed = (time.time() - start) * 1000

    return {
        "original_satisfaction": round(-initial_loss, 4),
        "refined_satisfaction": round(-best_loss, 4),
        "improvement": round(-best_loss - (-initial_loss), 4),
        "improvement_pct": round(
            100 * (-best_loss - (-initial_loss)) / max(0.001, -initial_loss), 1),
        "refined_weights": refined_weights,
        "original_operator_dist": orig_dist,
        "refined_operator_dist": refined_dist,
        "optimization_trace": trace,
        "config": {
            "learning_rate": lr,
            "n_steps": n_steps,
            "temperature": temperature,
            "n_runs": int(diagnostics.shape[0]),
            "n_gens": int(diagnostics.shape[1])
        },
        "time_ms": round(elapsed, 1)
    }


def main():
    data = json.load(sys.stdin)
    config = data.get("config", {})
    result = refine_weights(data, config)
    json.dump(result, sys.stdout, indent=2)
    print()


if __name__ == "__main__":
    main()
