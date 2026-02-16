#!/usr/bin/env bash
set -euo pipefail

# Mission 0 batch runs (hexagram xenotype + logging).
# Usage:
#   bash futon5/scripts/mission_0_batch.sh modest
#   bash futon5/scripts/mission_0_batch.sh overnight

mode="${1:-modest}"
log_root="/tmp/mission-0"
mkdir -p "$log_root"

case "$mode" in
  modest)
    runs=400
    update_every=100
    length=50
    generations=60
    hex_weight=0.0
    score_mode=triad
    ;;
  modest50)
    runs=50
    update_every=25
    length=50
    generations=60
    hex_weight=0.0
    score_mode=triad
    ;;
  overnight)
    runs=2000
    update_every=200
    length=50
    generations=80
    hex_weight=0.0
    score_mode=triad
    ;;
  *)
    echo "Unknown mode: $mode" >&2
    exit 1
    ;;
esac

timestamp="$(date +%Y%m%d-%H%M%S)"
log_path="$log_root/mission-0-${mode}-${timestamp}.edn"
csv_path="$log_root/mission-0-${mode}-${timestamp}-hex.csv"
psr_pur_path="$log_root/psr-pur-${mode}-${timestamp}.edn"

bb -cp futon5/src:futon5/resources -m futon5.mmca.exoevolve \
  --runs "$runs" \
  --update-every "$update_every" \
  --length "$length" \
  --generations "$generations" \
  --hexagram-weight "$hex_weight" \
  --score-mode "$score_mode" \
  --hexagram-log "$psr_pur_path" \
  --log "$log_path"

bb -cp futon5/src:futon5/resources -m futon5.mmca.hex-batch \
  --log "$log_path" \
  --update-every "$update_every" \
  --out "$csv_path"

bb -cp futon5/src:futon5/resources futon5/scripts/mission_0_persist.clj \
  --log "$log_path" \
  --update-every "$update_every" \
  --top-k 6 \
  --out-dir "/home/joe/code/futon3/library/iiching"

echo "Outputs:"
echo "  Log:        $log_path"
echo "  PSR/PUR:    $psr_pur_path"
echo "  Hex summary: $csv_path"
