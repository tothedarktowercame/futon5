#!/usr/bin/env bash
set -euo pipefail

# Sweep entropy envelope regimes (50 runs each).
# Usage:
#   bash futon5/scripts/mission_0_envelope_sweep.sh

log_root="/tmp/mission-0"
mkdir -p "$log_root"
timestamp="$(date +%Y%m%d-%H%M%S)"

declare -a regimes=(
  "legacy:0.50:0.20:0.18:0.12"
  "legacy:0.55:0.25:0.20:0.15"
  "triad:0.60:0.25:0.20:0.15"
  "triad:0.60:0.30:0.22:0.18"
  "shift:0.60:0.25:0.20:0.15"
  "shift:0.65:0.20:0.22:0.15"
  "triad:0.70:0.20:0.25:0.20"
  "legacy:0.70:0.30:0.25:0.20"
)

run_one() {
  local mode="$1"
  local ent_center="$2"
  local ent_width="$3"
  local ch_center="$4"
  local ch_width="$5"
  local tag="env-${mode}-e${ent_center}-w${ent_width}-c${ch_center}-cw${ch_width}"
  local log_path="$log_root/mission-0-${tag}-${timestamp}.edn"

  bb -cp futon5/src:futon5/resources -m futon5.mmca.exoevolve \
    --runs 50 \
    --update-every 25 \
    --length 50 \
    --generations 60 \
    --hexagram-weight 0.0 \
    --score-mode "$mode" \
    --envelope-center "$ent_center" \
    --envelope-width "$ent_width" \
    --envelope-change-center "$ch_center" \
    --envelope-change-width "$ch_width" \
    --hexagram-log "$log_root/psr-pur-${tag}-${timestamp}.edn" \
    --log "$log_path"

  echo "Log: $log_path"
}

for entry in "${regimes[@]}"; do
  IFS=":" read -r mode ent_center ent_width ch_center ch_width <<< "$entry"
  echo "Running $mode envelope=${ent_center}/${ent_width} change=${ch_center}/${ch_width}"
  run_one "$mode" "$ent_center" "$ent_width" "$ch_center" "$ch_width"
done
