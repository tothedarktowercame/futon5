#!/usr/bin/env python3
"""mention-graph-as-of-T extractor for the E-α (next-locus) experiment.

Git-replays each mission/campaign doc's file-mentions over time (NO checkout — via
`git show <sha>:<path>`), so the forecast harness can ask: what code-files did
mission M mention as-of time T? Explicit path/ns rules only (no fuzzy matching,
principle #8); code-files only (.clj/.cljs/.cljc/.el/.py/.bb). Mirrors the harness's
git_dep_graph_asof / git_callsig_asof pattern. Feeds the E1 bridge-score signal:
F_M(T) = mention-set at the latest doc-commit <= T.
"""
import subprocess, re, json, glob, os
from collections import defaultdict

ROOT = os.path.expanduser("~/code")
REPOS = ["futon0","futon1a","futon2","futon3","futon3a","futon3c","futon4","futon5","futon5a","futon6","futon7"]
CODE_EXT = ("clj","cljs","cljc","el","py","bb")
OUT = f"{ROOT}/futon5a/data/mention-graph-asof.json"

# ---- current-stack code-file index for resolution ----
allfiles = []
for repo in REPOS:
    base = f"{ROOT}/{repo}"
    if not os.path.isdir(base): continue
    for ext in CODE_EXT:
        for p in glob.glob(f"{base}/**/*.{ext}", recursive=True):
            if "/.worktrees/" in p or "/.venv" in p or "/.state/" in p: continue
            allfiles.append(os.path.relpath(p, ROOT))
byname = defaultdict(set); bysuffix = defaultdict(set)
for rp in allfiles:
    byname[os.path.basename(rp)].add(rp)
    parts = rp.split("/")
    for i in range(len(parts)-1, max(0, len(parts)-4), -1):
        bysuffix["/".join(parts[i:])].add(rp)

PATH_RE = re.compile(r'((?:src|dev|test|scripts|emacs|tools|resources|holes)/[\w./\-]+\.(?:clj[cs]?|el|py|bb))')
NS_RE   = re.compile(r'\b(futon[0-9a-z]*(?:[.][a-z0-9][a-z0-9\-]*){2,})\b')
BARE_RE = re.compile(r'\b([\w\-]+\.(?:clj[cs]?|el|py|bb))\b')

def ns_tails(ns):
    core = ns.replace("-", "_").replace(".", "/")
    return [f"{core}.{e}" for e in ("clj","cljc","cljs","bb")]

def resolve(text):
    files = set()
    for ref in set(PATH_RE.findall(text)):
        parts = ref.split("/")
        for i in range(len(parts)):
            tail = "/".join(parts[i:])
            if tail in bysuffix:
                files |= bysuffix[tail]; break
    for ns in set(NS_RE.findall(text)):
        for tail in ns_tails(ns):
            if tail in bysuffix:
                files |= bysuffix[tail]
    for b in set(BARE_RE.findall(text)):
        if b in byname and len(byname[b]) == 1:
            files |= byname[b]
    return sorted(files)

def git(repo, *a):
    return subprocess.run(["git", "-C", f"{ROOT}/{repo}", *a],
                          capture_output=True, text=True, errors="replace").stdout

# ---- mission/campaign docs across repos ----
docs = []
for repo in REPOS:
    for pat in ("M-*.md", "C-*.md"):
        for p in glob.glob(f"{ROOT}/{repo}/holes/**/{pat}", recursive=True):
            docs.append((repo, os.path.relpath(p, f"{ROOT}/{repo}")))

out = {}
for repo, rel in docs:
    mid = os.path.basename(rel)[:-3]
    log = git(repo, "log", "--format=%H %ct", "--reverse", "--", rel).strip().splitlines()
    timeline = []; last = None
    for line in log:
        sp = line.split()
        if len(sp) != 2: continue
        sha, ct = sp[0], int(sp[1])
        files = resolve(git(repo, "show", f"{sha}:{rel}"))
        if files != last:                       # record only when the mention-set changes
            timeline.append({"ct": ct, "files": files})
            last = files
    if timeline:
        # basename collisions across repos: keep the longest-developed timeline
        if mid not in out or len(timeline) > len(out[mid]["timeline"]):
            out[mid] = {"repo": repo, "path": rel, "timeline": timeline}

json.dump(out, open(OUT, "w"), indent=1)
nv = sum(len(v["timeline"]) for v in out.values())
print(f"docs:{len(docs)}  missions/campaigns with mentions:{len(out)}  timeline-versions:{nv}")
print("wrote", OUT)
