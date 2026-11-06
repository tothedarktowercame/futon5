# Vendored Peeragogy pattern sources (M-formal-patterns S4)

The eleven `#+roam_tags: PAT` files of the Peeragogy v4 handbook,
fetched verbatim from https://github.com/Peeragogy/Peeragogy.github.io
at commit 3ba572c553b1a45627e5abe4927fd42784b12d2f (master, last
committer date 2022-07-15), via:

    for f in carrying distributed_roadmap heartbeat newcomer pattern_audit \
             peeragogy roadmap reduce scrapbook specific wrapper; do
      curl -s "https://raw.githubusercontent.com/Peeragogy/Peeragogy.github.io/\
3ba572c553b1a45627e5abe4927fd42784b12d2f/src/$f.org" -o src/$f.org
    done

Vendored so the balance check (scripts/peeragogy/
check_pattern_language_balance.clj) is reproducible offline and pinned;
do not edit these files by hand — refetch at a new pinned commit
instead and update PROVENANCE.md.
