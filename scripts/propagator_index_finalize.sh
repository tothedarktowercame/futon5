#!/usr/bin/env bash
set -euo pipefail

# Finalize a successfully completed background propagator index without ever
# staging unrelated shared-worktree changes. The driver writes :complete only
# after checksum-validating every representative and clearing all anchor gates.

if [[ $# -ne 1 ]]; then
  echo "usage: $0 DRIVER_PID" >&2
  exit 2
fi

driver_pid=$1
repo=$(cd "$(dirname "$0")/.." && pwd)
cd "$repo"

while kill -0 "$driver_pid" 2>/dev/null; do
  sleep 60
done

read -r status fingerprint representatives sigmas < <(
  bb -e '
    (require (quote [clojure.edn :as edn]))
    (let [x (edn/read-string (slurp "data/propagator-index/coverage.edn"))]
      (println (name (:status x)) (:fingerprint x)
               (get-in x [:coverage :representatives-complete])
               (get-in x [:coverage :sigmas-represented])))')

if [[ "$status" != "complete" || "$representatives" != "20256" || "$sigmas" != "40320" ]]; then
  echo "index did not complete: status=$status representatives=$representatives sigmas=$sigmas" >&2
  exit 1
fi

artifact_dir="data/propagator-index/artifacts/$fingerprint"
manifest_dir="data/propagator-index/manifests/$fingerprint"
index_file="data/propagator-index/index-$fingerprint.edn.gz"
report_file="holes/labs/M-aif-tokamak/propagator_index_REPORT.md"

artifact_count=$(find "$artifact_dir" -type f -name '*.edn.gz' | wc -l)
manifest_count=$(find "$manifest_dir" -type f -name '*.edn' | wc -l)
partial_count=$(find "$artifact_dir" "$manifest_dir" -type f -name '.partial-*' | wc -l)
if [[ "$artifact_count" != "20256" || "$manifest_count" != "20256" || "$partial_count" != "0" ]]; then
  echo "artifact audit failed: artifacts=$artifact_count manifests=$manifest_count partials=$partial_count" >&2
  exit 1
fi

clj-kondo --lint scripts/propagator_index.clj
emacs -Q --batch -l /home/joe/code/futon4/dev/check-parens.el \
  --eval '(arxana-check-parens-cli)' -- --no-defaults \
  scripts/propagator_index.clj scripts/propagator_index_worker.el \
  scripts/propagator_orbit_probe.el

git add -f "$artifact_dir" "$manifest_dir" "$index_file" \
  data/propagator-index/coverage.edn
git add "$report_file"
git diff --cached --check -- "$artifact_dir" "$manifest_dir" "$index_file" \
  data/propagator-index/coverage.edn "$report_file"

git commit --only -m \
  "Propagator composition index: 40320/40320 via 20256 mirror orbits" \
  "$artifact_dir" "$manifest_dir" "$index_file" \
  data/propagator-index/coverage.edn "$report_file"
final_sha=$(git rev-parse HEAD)
printf '%s\n' "$final_sha" > /tmp/propagator-index-final.sha

printf '%s\n' \
  "BACKGROUND PROPAGATOR INDEX COMPLETE. Commit $final_sha covers 40,320/40,320 sigma via 20,256 proven mirror orbits; 3 seeds each; dense 121x256 standard-Wolfram censuses; all anchors and static gates pass." \
  | python3 /home/joe/code/futon3c/scripts/agency_send.py \
      --to claude-3 --from codex-7 --kind bell --type answer \
      --ref invoke-1784138059136-567-09b2bf3d
