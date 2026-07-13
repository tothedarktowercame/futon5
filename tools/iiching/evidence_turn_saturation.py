#!/usr/bin/env python3
"""evidence_turn_saturation.py — the REAL saturation measurement. Pull live chat-turns from the
Evidence Landscape (XTDB, ~35.7k entries via /api/alpha/evidence), pair user(operator)->assistant(agent),
run the trigram-factored retract (operator->upper, agent->lower => hexagram), and histogram over the 64.
Answers "is iching turn-tagging degenerate?" with the RIGHT data and OUR retract (not the legacy tagger).
claude-6 + Joe, 2026-06-02.   futon3a/.venv/bin/python evidence_turn_saturation.py /tmp/evidence.json
"""
import sys, re, json, math, pathlib, collections
import numpy as np
from sentence_transformers import SentenceTransformer

ICHING = pathlib.Path("/home/joe/code/futon3/library/iching")
TRIGRAMS = [
    ("heaven","111","the creative: strong active initiating force, pure assertion, driving will, origination"),
    ("earth","000","the receptive: yielding devoted support, nurturing, giving form, holding and carrying"),
    ("thunder","100","the arousing: sudden movement and shock, the impulse to begin, startling initiative"),
    ("water","010","the abysmal: danger and depth, flowing through difficulty, the unfathomable, risk"),
    ("mountain","001","keeping still: stopping, stability, rest, holding a boundary, immovable calm"),
    ("wind","011","the gentle: penetrating gradual influence, dispersal, flexible persistent shaping"),
    ("fire","101","the clinging: light and clarity, illumination, attachment, brightness and dependence"),
    ("lake","110","the joyous: openness and pleasure, exchange, communication, satisfaction and ease"),
]
tn=[t[0] for t in TRIGRAMS]; tb={t[0]:t[1] for t in TRIGRAMS}
hexbin={}
for p in ICHING.glob("hexagram-*.flexiarg"):
    txt=p.read_text(errors="ignore"); b=re.search(r"@binary (\d{6})",txt); ti=re.search(r"@title (.+)",txt)
    if b: hexbin[b.group(1)]=(ti.group(1).strip() if ti else p.stem)

entries=json.loads(pathlib.Path(sys.argv[1]).read_text()).get("entries",[])
ct=[(x.get("evidence/session-id"), x.get("evidence/at"), x["evidence/body"].get("role"), x["evidence/body"].get("text",""))
    for x in entries if x.get("evidence/body",{}).get("event")=="chat-turn" and x["evidence/body"].get("text")]
# pair user(operator)->next assistant(agent) within a session, in time order
bysess=collections.defaultdict(list)
for s,at,role,text in ct: bysess[s].append((at,role,text))
turns=[]
for s,evs in bysess.items():
    evs.sort(key=lambda e:e[0] or "")
    pend=None
    for at,role,text in evs:
        if role=="user": pend=text
        elif role=="assistant" and pend is not None:
            turns.append((pend,text)); pend=None
print(f"[corpus] {len(entries)} evidence entries -> {len(ct)} chat-turns -> {len(turns)} operator->agent turns")
if not turns: sys.exit("no paired turns in this page")

model=SentenceTransformer("sentence-transformers/all-MiniLM-L6-v2")
T=np.asarray(model.encode([t[2] for t in TRIGRAMS],normalize_embeddings=True))
def tri(texts):
    V=np.asarray(model.encode([t[:2000] for t in texts],normalize_embeddings=True))
    return [tn[int(i)] for i in (V@T.T).argmax(1)]
ups=tri([o for o,_ in turns]); los=tri([a for _,a in turns])
hexes=[tb[u]+tb[l] for u,l in zip(ups,los)]
hist=collections.Counter(hexes)

used=len(hist); tot=len(hexes)
probs=np.array(list(hist.values()))/tot
H=-(probs*np.log2(probs)).sum(); eff=2**H
print(f"[saturation] {tot} turns -> {used}/64 hexagrams used; entropy {H:.2f} bits; "
      f"effective #patterns {eff:.1f}/64  (degenerate if eff<<64)")
print(f"[concentration] top-3 share {sum(c for _,c in hist.most_common(3))/tot*100:.0f}%  "
      f"top-5 {sum(c for _,c in hist.most_common(5))/tot*100:.0f}%")
print("[top hexagrams]")
for b,c in hist.most_common(8):
    print(f"  {b} {hexbin.get(b,'?'):34s} {c:4d} ({100*c/tot:.0f}%)")
print(f"[trigram balance] operator(upper): {dict(collections.Counter(ups))}")
print(f"                  agent(lower):    {dict(collections.Counter(los))}")
