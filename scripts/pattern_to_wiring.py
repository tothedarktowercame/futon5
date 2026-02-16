#!/usr/bin/env python3
"""Compatibility entrypoint for pattern->wiring regeneration.

Delegates to the Clojure implementation in scripts/pattern_to_wiring.clj.
"""

from __future__ import annotations

import subprocess
import sys


def main() -> int:
    cmd = ["bb", "-cp", "src:resources", "scripts/pattern_to_wiring.clj", *sys.argv[1:]]
    try:
        completed = subprocess.run(cmd, check=False)
    except FileNotFoundError:
        sys.stderr.write("error: `bb` is required but was not found in PATH\n")
        return 127
    return completed.returncode


if __name__ == "__main__":
    raise SystemExit(main())

