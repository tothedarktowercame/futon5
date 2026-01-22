#!/usr/bin/env bash
set -euo pipefail

# Wrapper: prepare Cyber-MMCA HIT inputs, then launch judge-cli.
# Example:
#   bash futon5/scripts/cyber_mmca_hit_round.sh \
#     --controllers null,wiring \
#     --seeds 4242,1111 \
#     --windows 8 --W 8 --S 8 \
#     --wiring-path /tmp/xenotype-synth-1768992680987.edn --wiring-index 0 \
#     --out-dir /tmp/cyber-mmca-hit-smoke \
#     --inputs /tmp/cyber-mmca-hit-smoke-inputs.txt \
#     --render-dir /tmp/cyber-mmca-hit-smoke-renders \
#     --exotype --scale 250 \
#     --judgements-out /tmp/mmca-judgements.edn

judgements_out="/tmp/mmca-judgements.edn"
inputs_path="/tmp/cyber-mmca-hit-inputs.txt"
render_dir="/tmp/cyber-mmca-hit-renders"
scale="250"
exotype_flag=""
prepare_args=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --judgements-out)
      judgements_out="$2"; shift 2;;
    --inputs)
      inputs_path="$2"; prepare_args+=("$1" "$2"); shift 2;;
    --render-dir)
      render_dir="$2"; prepare_args+=("$1" "$2"); shift 2;;
    --scale)
      scale="$2"; prepare_args+=("$1" "$2"); shift 2;;
    --exotype)
      exotype_flag="--exotype"; prepare_args+=("$1"); shift 1;;
    *)
      prepare_args+=("$1"); shift 1;;
  esac
done

bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_prepare_hit.clj \
  "${prepare_args[@]}"

bb -cp futon5/src:futon5/resources -m futon5.mmca.judge-cli \
  --inputs "$inputs_path" \
  --out "$judgements_out" \
  --render-dir "$render_dir" \
  $exotype_flag \
  --scale "$scale"

bb -cp futon5/src:futon5/resources futon5/scripts/hit_agreement_report.clj \
  --inputs "$inputs_path" \
  --judgements "$judgements_out" \
  --out "${judgements_out%.edn}-report.txt"
