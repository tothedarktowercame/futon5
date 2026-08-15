#!/bin/bash
P=$1
cd ~/code/futon5
CLJ="clojure -Sdeps {:paths[\"src\"\"resources\"]}"
SCALE=reports/exotype-policy-epistemic-scale-v2.edn
clojure -Sdeps "{:paths [\"src\" \"resources\"]}" -M scripts/exotype_policy_epistemic_v2.clj calibrate-churn $SCALE $P /tmp/epi/churn-$P.edn > /tmp/epi/churn-$P.log 2>&1 || exit 1
for ARM in off epistemic matched-churn; do
  clojure -Sdeps "{:paths [\"src\" \"resources\"]}" -M scripts/exotype_policy_epistemic_v2.clj run $SCALE /tmp/epi/churn-$P.edn $P $ARM /tmp/epi/cell-$P-$ARM.edn > /tmp/epi/cell-$P-$ARM.log 2>&1
done
echo "lane $P done" >> /tmp/epi/lanes.done
