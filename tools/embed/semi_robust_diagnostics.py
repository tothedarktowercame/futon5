#!/usr/bin/env python3
"""Semi-robust code-diagnostic baselines for M-categorical-code.

The experiment freezes extraction and evaluation within each batch, then
rotates three commit windows. Output contains no clock-derived fields and is
therefore reproducible for a pinned repository HEAD.
"""
from __future__ import annotations

import argparse
import collections
import json
import math
import subprocess
from pathlib import Path

DEFAULT_REPO = "/home/joe/code/futon3c"
DEFAULT_OUTPUT = "reports/categorical-code/semi-robust-baselines.json"
OFFSETS = (450, 350, 250)
WINDOW_COMMITS = 100
SOURCE_PREFIXES = ("src/", "dev/")
SOURCE_SUFFIXES = (".clj", ".cljc")

PAIRS = {
    "structural-entropy": "weighted-degree",
    "categorical-coupling": "degree-centrality",
    "churn-sensitivity": "prior-churn",
}
BASELINES = ("degree-centrality", "weighted-degree", "prior-churn")


def git(repo: str, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", repo, *args], check=True, capture_output=True, text=True
    )
    return result.stdout


def source_file(path: str) -> bool:
    return (
        path.startswith(SOURCE_PREFIXES)
        and path.endswith(SOURCE_SUFFIXES)
        and "/test/" not in path
        and not path.endswith("_test.clj")
    )


def read_history(repo: str) -> list[dict]:
    raw = git(repo, "log", "--pretty=format:@%H %ct", "--name-only")
    commits: list[dict] = []
    current = None
    for line in raw.splitlines():
        if line.startswith("@"):
            if current is not None:
                commits.append(current)
            sha, timestamp = line[1:].split()
            current = {"sha": sha, "timestamp": int(timestamp), "files": []}
        elif line.strip() and current is not None and source_file(line.strip()):
            current["files"].append(line.strip())
    if current is not None:
        commits.append(current)
    return commits


def average_precision(scores: dict[str, float], positives: set[str]) -> float:
    ordered = sorted(scores, key=lambda path: (-scores[path], path))
    hits = 0
    total = 0.0
    for rank, path in enumerate(ordered, 1):
        if path in positives:
            hits += 1
            total += hits / rank
    return total / max(1, len(positives))


def ranks(scores: dict[str, float]) -> dict[str, float]:
    ordered = sorted(scores.items(), key=lambda item: (item[1], item[0]))
    result = {}
    index = 0
    while index < len(ordered):
        end = index + 1
        while end < len(ordered) and ordered[end][1] == ordered[index][1]:
            end += 1
        rank = (index + 1 + end) / 2.0
        for path, _ in ordered[index:end]:
            result[path] = rank
        index = end
    return result


def spearman(left: dict[str, float], right: dict[str, float]) -> float:
    keys = sorted(set(left) & set(right))
    if len(keys) < 2:
        return 0.0
    lr, rr = ranks({key: left[key] for key in keys}), ranks({key: right[key] for key in keys})
    lv, rv = [lr[key] for key in keys], [rr[key] for key in keys]
    lm, rm = sum(lv) / len(lv), sum(rv) / len(rv)
    numerator = sum((a - lm) * (b - rm) for a, b in zip(lv, rv))
    denominator = math.sqrt(
        sum((a - lm) ** 2 for a in lv) * sum((b - rm) ** 2 for b in rv)
    )
    return numerator / denominator if denominator else 0.0


def diagnostic_scores(commits: list[dict], nodes: set[str]) -> dict[str, dict[str, float]]:
    churn = collections.Counter()
    partners = collections.defaultdict(collections.Counter)
    bursts = collections.defaultdict(list)
    for commit in commits:
        files = set(commit["files"])
        for path in files:
            if path not in nodes:
                continue
            churn[path] += 1
            others = files - {path}
            bursts[path].append(len(others))
            for other in others:
                partners[path][other] += 1

    def entropy(path: str) -> float:
        weights = list(partners[path].values())
        total = sum(weights)
        return -sum((weight / total) * math.log2(weight / total) for weight in weights) if total else 0.0

    return {
        "structural-entropy": {path: entropy(path) for path in nodes},
        "categorical-coupling": {path: float(len(partners[path])) for path in nodes},
        "churn-sensitivity": {
            path: sum(bursts[path]) / len(bursts[path]) if bursts[path] else 0.0
            for path in nodes
        },
        "degree-centrality": {path: float(len(partners[path])) for path in nodes},
        "weighted-degree": {path: float(sum(partners[path].values())) for path in nodes},
        "prior-churn": {path: float(churn[path]) for path in nodes},
    }


def evaluate_batch(repo: str, history: list[dict], offset: int) -> tuple[dict, dict]:
    if offset + WINDOW_COMMITS > len(history):
        raise ValueError(f"repository has {len(history)} commits; batch needs {offset + WINDOW_COMMITS}")
    cutoff = history[offset]
    target_end = history[offset - WINDOW_COMMITS]
    nodes = {
        path
        for path in git(repo, "ls-tree", "-r", "--name-only", cutoff["sha"]).splitlines()
        if source_file(path)
    }
    prior = history[offset:]
    target = history[offset - WINDOW_COMMITS : offset]
    scores = diagnostic_scores(prior, nodes)
    eligible = {path for path in nodes if scores["prior-churn"][path] > 0}
    scores = {name: {path: values[path] for path in eligible} for name, values in scores.items()}
    positives = {path for commit in target for path in commit["files"] if path in eligible}
    aps = {name: average_precision(values, positives) for name, values in scores.items()}
    comparisons = {
        diagnostic: {
            "baseline": baseline,
            "diagnostic-ap": aps[diagnostic],
            "baseline-ap": aps[baseline],
            "effect": aps[diagnostic] - aps[baseline],
            "ranking-agreement": spearman(scores[diagnostic], scores[baseline]),
            "all-baseline-effects": {
                candidate: aps[diagnostic] - aps[candidate]
                for candidate in BASELINES
            },
        }
        for diagnostic, baseline in PAIRS.items()
    }
    record = {
        "batch": f"head-minus-{offset}",
        "cutoff": cutoff["sha"],
        "target-end": target_end["sha"],
        "target-commits": WINDOW_COMMITS,
        "eligible-files": len(eligible),
        "reworked-files": len(positives),
        "comparisons": comparisons,
    }
    return record, scores


def summarize(batches: list[dict], score_sets: list[dict]) -> dict:
    summary = {}
    for diagnostic, baseline in PAIRS.items():
        rows = [batch["comparisons"][diagnostic] for batch in batches]
        effects = [row["effect"] for row in rows]
        mean = sum(effects) / len(effects)
        variance = sum((effect - mean) ** 2 for effect in effects) / len(effects)
        disagreements = []
        for previous, current in zip(score_sets, score_sets[1:]):
            common = sorted(set(previous[diagnostic]) & set(current[diagnostic]))
            for path in common:
                diagnostic_changed = previous[diagnostic][path] != current[diagnostic][path]
                baseline_changed = previous[baseline][path] != current[baseline][path]
                if diagnostic_changed != baseline_changed:
                    disagreements.append({"path": path, "changed": diagnostic if diagnostic_changed else baseline})
        summary[diagnostic] = {
            "baseline": baseline,
            "mean-effect": mean,
            "effect-variance": variance,
            "mean-ranking-agreement": sum(row["ranking-agreement"] for row in rows) / len(rows),
            "change-disagreements": disagreements[:10],
        }
    return summary


def run(repo: str) -> dict:
    history = read_history(repo)
    evaluated = [evaluate_batch(repo, history, offset) for offset in OFFSETS]
    batches, score_sets = zip(*evaluated)
    return {
        "schema": "futon5/semi-robust-code-diagnostics-v1",
        "experiment": {
            "repo": repo,
            "head": git(repo, "rev-parse", "HEAD").strip(),
            "batch-offsets": list(OFFSETS),
            "target-window-commits": WINDOW_COMMITS,
            "source-prefixes": list(SOURCE_PREFIXES),
            "source-suffixes": list(SOURCE_SUFFIXES),
            "target": "file reworked at least once in the following window",
            "evaluation": "average precision",
        },
        "batches": list(batches),
        "summary": summarize(list(batches), list(score_sets)),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=DEFAULT_REPO)
    parser.add_argument("--output", default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    result = run(args.repo)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print(output)


if __name__ == "__main__":
    main()
