# sandbox/

A throwaway copy of the paper sources for the WYSIWYG e2e gates.

**The gates must never write to the real paper.** They did, once: an S3 run
snapshotted `draft8.tex`, and its cleanup restored that snapshot over edits Joe
had made in the browser in the meantime — silently discarding them. Snapshot +
restore is not safe against a live editor working in the same file.

Refresh from the real sources with `./refresh.sh`.
