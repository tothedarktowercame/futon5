#!/usr/bin/env bash
# Re-seed rocket.py's in-memory state after a restart (annotations + a log
# line). Keep the annotation list here in sync with the current session's
# marks — this file IS the session's annotation state of record.
set -euo pipefail
B=http://127.0.0.1:8130

curl -s -X POST $B/annotations -d '[
 {"pat":"full-name-at-definition","note":"rocket 2: definition sites carry the full term of art","applied":2,"sites":[{"text":"A rule-rewriting operator is defined by a permutation"},{"text":"A rule-rewriting operator is a permutation"}]},
 {"pat":"false-instead","note":"rocket 3 + hand edit: state the move, not just drop the connective","applied":1,"sites":[{"text":"We then expand the class of automata architectures, and examine criticality by measuring how far the effect of a single-cell change travels"}]},
 {"pat":"hand-edit","note":"Joe (in Emacs): state the move; the regime made vivid; the interactive find as its own sentence","applied":3,"sites":[{"text":"expand the class of automata architectures, and examine criticality"},{"text":"coexist and merge into visibly complex patterns"},{"text":"An external selection policy helped us find this regime interactively"}]},
 {"pat":"frame-the-question","note":"rocket 4: the exotype question stated before the negatives that answer it","applied":1,"sites":[{"text":"we make the operator assignment itself a per-cell heritable state---an exotype---and ask whether local dynamics alone can find and hold the regime"}]},
 {"pat":"scoped-negative-names-its-subject","note":"Joe: the negative names its mechanism (local selection), family (exotypes), and criterion (recover the observed behaviour)","applied":1,"sites":[{"text":"Within the tested family of exotypes, local selection was not able to recover the observed behaviour"}]},
 {"pat":"verdict-not-litany","note":"rocket 5: the litany cut — verdict straight to construction; the evidence chain lives in the intro and Part III","applied":1,"sites":[{"text":"recover the observed behaviour. However, we constructed local interventions"}]}]' > /dev/null

curl -s -X POST $B/log -d '{"msg":"receiver restarted and re-seeded"}' > /dev/null
echo "re-seeded: $(curl -s $B/feed | python3 -c 'import json,sys; d=json.load(sys.stdin); print(len(d["annotations"]), "annotations")')"
