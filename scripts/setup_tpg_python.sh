#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VENV_DIR="${ROOT_DIR}/.venv-tpg"
REQ_FILE="${ROOT_DIR}/requirements-tpg.txt"
PYTHON_BIN="${PYTHON:-python3}"

if ! command -v "${PYTHON_BIN}" >/dev/null 2>&1; then
  echo "ERROR: Python interpreter '${PYTHON_BIN}' was not found in PATH." >&2
  exit 1
fi

if [[ ! -f "${REQ_FILE}" ]]; then
  echo "ERROR: Missing requirements file at ${REQ_FILE}" >&2
  exit 1
fi

echo "Creating/updating ${VENV_DIR} with ${PYTHON_BIN}..."
"${PYTHON_BIN}" -m venv "${VENV_DIR}"

echo "Installing TPG Python dependencies..."
"${VENV_DIR}/bin/python3" -m pip install --upgrade pip
"${VENV_DIR}/bin/pip" install -r "${REQ_FILE}"

cat <<EOF
TPG Python environment is ready:
  ${VENV_DIR}/bin/python3

futon5.tpg.compare resolves Python in this order:
1. FUTON5_TPG_PYTHON
2. .venv-tpg/bin/python3
3. python3 from PATH

GPU flow (for a temporary Linode):
- Build/install a GPU-capable JAX environment on the GPU host.
- Point futon5 at that interpreter:
    export FUTON5_TPG_PYTHON=/path/to/gpu-env/bin/python3
EOF
