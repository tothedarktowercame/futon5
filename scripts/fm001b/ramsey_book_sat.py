#!/usr/bin/env python3
"""Ramsey book-graph SAT encoding for FM-001b experiments.

This script builds the n-book Ramsey instance used in Task f3-sat-encode, emits a
CNF through PySAT, and optionally runs a SAT solver plus structural checks.
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

try:
    from pysat.card import CardEnc, EncType, UnsupportedBound
    from pysat.formula import CNF, IDPool
    from pysat.solvers import Solver
except ImportError as exc:  # pragma: no cover
    raise SystemExit(
        "PySAT not found. Install with `pip install python-sat[pblib,aiger]`."
    ) from exc


EncodingName = {
    "seqcounter": EncType.seqcounter,
    "cardnetwrk": EncType.cardnetwrk,
}


def _edge_count(vertices: int) -> int:
    return vertices * (vertices - 1) // 2


@dataclass
class EncoderStats:
    vertices: int
    book_n: int
    encoding: str
    clauses: int
    variables: int
    edge_vars: int


class BookRamseyEncoder:
    """Constructs book-graph Ramsey CNFs with auxiliary helper variables."""

    def __init__(self, vertex_count: int, target_n: int, encoding: str = "seqcounter"):
        if target_n < 3:
            raise ValueError("book_n must be >= 3")
        self.vertex_count = vertex_count
        self.target_n = target_n
        if encoding not in EncodingName:
            raise ValueError(f"Unknown encoding '{encoding}'")
        self.encoding = encoding
        self.pool = IDPool()
        self.cnf = CNF()
        self.edge_vars: Dict[Tuple[int, int], int] = {}
        self.var_to_edge: Dict[int, Tuple[int, int]] = {}
        self._init_edge_vars()

    def _init_edge_vars(self) -> None:
        for i in range(self.vertex_count):
            for j in range(i + 1, self.vertex_count):
                var = self.pool.id(("e", i, j))
                self.edge_vars[(i, j)] = var
                self.var_to_edge[var] = (i, j)

    def edge_var(self, i: int, j: int) -> int:
        if i == j:
            raise ValueError("self-loops not allowed")
        a, b = (i, j) if i < j else (j, i)
        return self.edge_vars[(a, b)]

    def _new_aux(self, kind: str, indices: Tuple[int, int, int]) -> int:
        return self.pool.id((kind, indices[0], indices[1], indices[2]))

    def build(self) -> CNF:
        max_common = self.target_n - 2  # forbid B_{n-1}
        max_complement = self.target_n - 1  # forbid complement B_n
        for u in range(self.vertex_count):
            for v in range(u + 1, self.vertex_count):
                uv = self.edge_var(u, v)
                y_vars: List[int] = []
                z_vars: List[int] = []
                for w in range(self.vertex_count):
                    if w == u or w == v:
                        continue
                    yw = self._new_aux("y", (u, v, w))
                    y_vars.append(yw)
                    self._encode_triple_and(yw, uv, self.edge_var(u, w), self.edge_var(v, w))
                    zw = self._new_aux("z", (u, v, w))
                    z_vars.append(zw)
                    self._encode_triple_nand(zw, uv, self.edge_var(u, w), self.edge_var(v, w))
                self._at_most(y_vars, max_common)
                self._at_most(z_vars, max_complement)
        return self.cnf

    def _encode_triple_and(self, aux: int, uv: int, uw: int, vw: int) -> None:
        # aux <=> (uv ∧ uw ∧ vw)
        self.cnf.append([-aux, uv])
        self.cnf.append([-aux, uw])
        self.cnf.append([-aux, vw])
        self.cnf.append([aux, -uv, -uw, -vw])

    def _encode_triple_nand(self, aux: int, uv: int, uw: int, vw: int) -> None:
        # aux <=> (¬uv ∧ ¬uw ∧ ¬vw)
        self.cnf.append([-aux, -uv])
        self.cnf.append([-aux, -uw])
        self.cnf.append([-aux, -vw])
        self.cnf.append([aux, uv, uw, vw])

    def _at_most(self, lits: Sequence[int], bound: int) -> None:
        if bound >= len(lits):
            return
        try:
            enc = CardEnc.atmost(
                lits=list(lits),
                bound=bound,
                vpool=self.pool,
                encoding=EncodingName[self.encoding],
            )
        except UnsupportedBound as exc:  # pragma: no cover - configuration error
            raise SystemExit(
                f"Encoding '{self.encoding}' does not support at-most-{bound} constraints "
                f"for {len(lits)} literals."
            ) from exc
        self.cnf.extend(enc.clauses)

    def stats(self) -> EncoderStats:
        return EncoderStats(
            vertices=self.vertex_count,
            book_n=self.target_n,
            encoding=self.encoding,
            clauses=len(self.cnf.clauses),
            variables=self.pool.top,
            edge_vars=len(self.edge_vars),
        )


def adjacency_from_model(
    model: Sequence[int], vertex_count: int, var_to_edge: Dict[int, Tuple[int, int]]
) -> List[List[bool]]:
    adj = [[False] * vertex_count for _ in range(vertex_count)]
    edge_set = {lit for lit in model if lit > 0}
    for var, (i, j) in var_to_edge.items():
        if var in edge_set:
            adj[i][j] = adj[j][i] = True
    return adj


def adjacency_bitstring(adj: List[List[bool]]) -> str:
    bits: List[str] = []
    v = len(adj)
    for i in range(v):
        for j in range(i + 1, v):
            bits.append("1" if adj[i][j] else "0")
    return "".join(bits)


def find_book_violation(
    adj: List[List[bool]], threshold: int, require_edge: bool
) -> Optional[Dict[str, object]]:
    n = len(adj)
    for u in range(n):
        for v in range(u + 1, n):
            base_edge = adj[u][v]
            if require_edge and not base_edge:
                continue
            if (not require_edge) and base_edge:
                continue
            witnesses: List[int] = []
            for w in range(n):
                if w == u or w == v:
                    continue
                if require_edge:
                    ok = adj[u][w] and adj[v][w]
                else:
                    ok = (not adj[u][w]) and (not adj[v][w])
                if ok:
                    witnesses.append(w)
                    if len(witnesses) >= threshold:
                        return {
                            "pair": (u, v),
                            "witnesses": witnesses[:],
                            "require_edge": require_edge,
                        }
    return None


def verify_book_constraints(adj: List[List[bool]], book_n: int) -> Tuple[bool, Optional[Dict[str, object]]]:
    violation = find_book_violation(adj, threshold=book_n - 1, require_edge=True)
    if violation:
        violation["kind"] = f"B_{book_n - 1}"
        return False, violation
    violation = find_book_violation(adj, threshold=book_n, require_edge=False)
    if violation:
        violation["kind"] = f"complement-B_{book_n}"
        return False, violation
    return True, None


def solve_cnf(cnf: CNF, solver_name: str) -> Tuple[bool, Optional[List[int]]]:
    with Solver(name=solver_name, bootstrap_with=cnf.clauses) as solver:
        sat = solver.solve()
        model = solver.get_model() if sat else None
    return sat, model


def write_json(path: Optional[Path], payload: Dict[str, object]) -> None:
    if not path:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def write_text(path: Optional[Path], text: str) -> None:
    if not path:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text + "\n", encoding="utf-8")


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--vertices", type=int, default=30, help="number of graph vertices")
    parser.add_argument("--book-n", type=int, default=8, help="target n for B_{n-1}/complement-B_n")
    parser.add_argument(
        "--encoding",
        type=str,
        choices=sorted(EncodingName.keys()),
        default="seqcounter",
        help="cardinality encoding used by PySAT",
    )
    parser.add_argument("--emit-cnf", type=Path, help="destination for DIMACS CNF", default=None)
    parser.add_argument(
        "--solve",
        action="store_true",
        help="run a SAT solver (PySAT) after emitting the CNF",
    )
    parser.add_argument(
        "--solver",
        type=str,
        default="cadical153",
        help="name of the PySAT solver to invoke when --solve is set",
    )
    parser.add_argument(
        "--solution-json",
        type=Path,
        default=None,
        help="write solver outcome + adjacency bits to this JSON file",
    )
    parser.add_argument(
        "--adjacency-bits",
        type=Path,
        default=None,
        help="write the upper-triangular adjacency bitstring when SAT",
    )
    parser.add_argument(
        "--summary-json",
        type=Path,
        default=None,
        help="write encoder stats regardless of solving",
    )
    return parser.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    encoder = BookRamseyEncoder(args.vertices, args.book_n, args.encoding)
    cnf = encoder.build()
    stats = encoder.stats()
    print(
        f"[fm001b] n={args.book_n}, V={args.vertices}, |E|={_edge_count(args.vertices)}; "
        f"vars={stats.variables}, clauses={stats.clauses}, encoding={stats.encoding}"
    )
    if args.emit_cnf:
        args.emit_cnf.parent.mkdir(parents=True, exist_ok=True)
        cnf.to_file(str(args.emit_cnf))
        print(f"[fm001b] wrote CNF to {args.emit_cnf}")
    write_json(args.summary_json, stats.__dict__)
    result: Dict[str, object] = {
        "stats": stats.__dict__,
        "vertices": args.vertices,
        "book_n": args.book_n,
        "encoding": args.encoding,
    }
    if args.solve:
        sat, model = solve_cnf(cnf, args.solver)
        result["solver"] = args.solver
        result["sat"] = sat
        print(f"[fm001b] solver {args.solver} ⇒ {'SAT' if sat else 'UNSAT'}")
        if sat and model:
            adj = adjacency_from_model(model, args.vertices, encoder.var_to_edge)
            ok, violation = verify_book_constraints(adj, args.book_n)
            result["verified"] = ok
            if violation:
                result["violation"] = violation
                print(f"[fm001b] verification failed: {violation}")
            else:
                print(
                    f"[fm001b] witness verified (B_{args.book_n - 1}-free + "
                    f"complement-B_{args.book_n}-free)"
                )
            bits = adjacency_bitstring(adj)
            result["adjacency_bits"] = bits
            write_text(args.adjacency_bits, bits)
        write_json(args.solution_json, result)
        return 0 if sat else 2
    else:
        write_json(args.solution_json, result)
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
