#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
usage: scripts/learning_loop_nonstarter.sh <runs-dir> [tag] --nonstarter-db PATH --hypothesis-id ID [opts]

Runs:
  1) scripts/learning_loop_compliance.sh <runs-dir> [tag]
  2) Registers a study preregistration in Nonstarter, attaching health + loop outputs

Required:
  --nonstarter-db PATH
  --hypothesis-id ID

Optional:
  --study-name NAME       (default: "learning-loop <tag>")
  --study-status STATUS   (default: complete)
  --study-notes TEXT
  --study-design EDN
  --study-metrics EDN
  --study-seeds EDN
  --wirings-dir DIR       (passed to learning_loop_compliance.sh)

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

if [[ ${1:-} != "" && ${1:-} != "--nonstarter-db" && ${1:-} != "--hypothesis-id" && ${1:-} != "--wirings-dir" ]]; then
  tag="$1"
  shift
else
  tag="$(basename "$runs_dir")"
fi

nonstarter_db=""
hypothesis_id=""
wirings_dir="${WIRINGS_DIR:-}"
study_name="learning-loop ${tag}"
study_status="complete"
study_notes=""
study_design=""
study_metrics=""
study_seeds=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --nonstarter-db)
      nonstarter_db="$2"
      shift 2
      ;;
    --hypothesis-id)
      hypothesis_id="$2"
      shift 2
      ;;
    --study-name)
      study_name="$2"
      shift 2
      ;;
    --study-status)
      study_status="$2"
      shift 2
      ;;
    --study-notes)
      study_notes="$2"
      shift 2
      ;;
    --study-design)
      study_design="$2"
      shift 2
      ;;
    --study-metrics)
      study_metrics="$2"
      shift 2
      ;;
    --study-seeds)
      study_seeds="$2"
      shift 2
      ;;
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

if [[ -z "$nonstarter_db" || -z "$hypothesis_id" ]]; then
  usage
  exit 2
fi

if [[ -n "$wirings_dir" ]]; then
  scripts/learning_loop_compliance.sh "$runs_dir" "$tag" --wirings-dir "$wirings_dir"
else
  scripts/learning_loop_compliance.sh "$runs_dir" "$tag"
fi

health_dir="${HEALTH_DIR:-reports/health}"
loop_md="${LOOP_MD:-reports/wirings/learning-loop-analysis.md}"
health_md="${health_dir}/${tag}-health.md"
health_csv="${health_dir}/${tag}-health.csv"

results_edn=$(cat <<EOF_RES
{:tag "${tag}"
 :runs-dir "${runs_dir}"
 :health-md "${health_md}"
 :health-csv "${health_csv}"
 :loop-md "${loop_md}"}
EOF_RES
)

cmd=(clj -M -m scripts.nonstarter-study register
  --db "$nonstarter_db"
  --hypothesis-id "$hypothesis_id"
  --study-name "$study_name"
  --status "$study_status"
  --results "$results_edn")

if [[ -n "$study_notes" ]]; then
  cmd+=(--notes "$study_notes")
fi
if [[ -n "$study_design" ]]; then
  cmd+=(--design "$study_design")
fi
if [[ -n "$study_metrics" ]]; then
  cmd+=(--metrics "$study_metrics")
fi
if [[ -n "$study_seeds" ]]; then
  cmd+=(--seeds "$study_seeds")
fi

"${cmd[@]}"

echo "\nNonstarter study registered for hypothesis ${hypothesis_id}."
