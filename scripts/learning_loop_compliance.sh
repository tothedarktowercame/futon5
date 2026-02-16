#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
usage: scripts/learning_loop_compliance.sh <runs-dir> [tag] [--wirings-dir DIR]

Runs:
  1) run_health_report.clj -> reports/health/<tag>-health.{md,csv}
  2) wiring_learning_loop.clj -> reports/wirings/learning-loop-analysis.md

Defaults:
  tag            = basename of runs-dir
  wirings-dir    = data
  health-dir     = reports/health
  output report  = reports/wirings/learning-loop-analysis.md

Environment overrides:
  HEALTH_DIR, WIRINGS_DIR, LOOP_MD
USAGE
}

if [[ ${1:-} == "-h" || ${1:-} == "--help" || $# -lt 1 ]]; then
  usage
  exit 2
fi

runs_dir="$1"
shift

# Optional tag
if [[ ${1:-} != "" && ${1:-} != "--wirings-dir" ]]; then
  tag="$1"
  shift
else
  tag="$(basename "$runs_dir")"
fi

wirings_dir="${WIRINGS_DIR:-data}"
health_dir="${HEALTH_DIR:-reports/health}"
loop_md="${LOOP_MD:-reports/wirings/learning-loop-analysis.md}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --wirings-dir)
      wirings_dir="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 2
      ;;
  esac
done

mkdir -p "$health_dir"

bb -cp src:resources scripts/run_health_report.clj \
  --runs-dir "$runs_dir" \
  --markdown "$health_dir/${tag}-health.md" \
  --csv "$health_dir/${tag}-health.csv"

bb -cp src:resources scripts/wiring_learning_loop.clj \
  --wirings-dir "$wirings_dir" \
  --health-dir "$health_dir" \
  --markdown "$loop_md"

printf "\nLearning loop compliance complete:\n"
printf "  health: %s/%s-health.{md,csv}\n" "$health_dir" "$tag"
printf "  loop:   %s\n" "$loop_md"
