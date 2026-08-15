#!/bin/bash
P=$1
cd ~/code/futon5
SCALE=reports/exotype-policy-epistemic-scale-v2.edn
for ARM in off epistemic; do
  clojure -Sdeps "{:paths [\"src\" \"resources\"]}" -M scripts/exotype_policy_epistemic_v2.clj run $SCALE $SCALE $P $ARM /tmp/epi/cell-$P-$ARM.edn > /tmp/epi/cell-$P-$ARM.log 2>&1
done
echo "lane2 $P done" >> /tmp/epi/lanes2.done
