#!/bin/bash

set -euo pipefail

cat > /tmp/mission-07-xeno.edn <<'EOF'
{}
EOF

# Config
batches=10
runs_per_batch=500
base_seed=4242

for b in $(seq 1 $batches); do
  seed=$((base_seed + b))
  a_log=/tmp/mission-07-a-batch-${b}.edn
  b_log=/tmp/mission-07-b-batch-${b}.edn

  if [ ! -s "${a_log}" ]; then
    bb -cp src:resources -m futon5.mmca.genoevolve \
      --runs ${runs_per_batch} \
      --length 50 \
      --generations 30 \
      --pop 32 \
      --update-every 100 \
      --mutation-rate 0.1 \
      --seed ${seed} \
      --log ${a_log}
  fi

  if [ ! -s "${b_log}" ]; then
    bb -cp src:resources -m futon5.mmca.exoevolve \
      --runs ${runs_per_batch} \
      --length 50 \
      --generations 30 \
      --pop 32 \
      --update-every 100 \
      --tier both \
      --seed ${seed} \
      --xeno-spec /tmp/mission-07-xeno.edn \
      --xeno-weight 0.5 \
      --log ${b_log}
  fi
done

# Merge logs after completion
cat /tmp/mission-07-a-batch-*.edn > /tmp/mission-07-a-overnight.edn
cat /tmp/mission-07-b-batch-*.edn > /tmp/mission-07-b-overnight.edn
