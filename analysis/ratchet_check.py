"""Figure ratchet: no future exotype figure may score worse than the
2026-08-03 12:51 baseline (slice5 mu=0.10 lambda=0.550, no EIG).

Usage: python3 analysis/ratchet_check.py <figure.png> <cell-width> [panel]
       panel: 'full' (single-layer image) or 'right' (exotype third of a triptych)
"""
import sys, collections
from PIL import Image
PAL={(54,162,235):'builder',(245,166,35):'collapser',(220,55,65):'chaos',(55,190,105):'identity'}
def near(px):
    b,bd=None,1e9
    for c,n in PAL.items():
        d=sum((a-b2)**2 for a,b2 in zip(px,c))
        if d<bd: bd,b=d,n
    return b if bd<4000 else None

def profile(path,w,panel='full',nrows=80):
    im=Image.open(path).convert('RGB'); W,H=im.size
    x0,x1=(int(W*2/3)+2,W-2) if panel=='right' else (0,W)
    tot=collections.Counter(); cls=collections.Counter()
    for i in range(nrows):
        y=int(H*(0.10+0.88*i/nrows))
        row=[near(im.getpixel((x0+int(j*(x1-x0-1)/w),y))) for j in range(w)]
        tot.update(x for x in row if x)
        runs=[];cur=row[0];n=1
        for v in row[1:]:
            if v==cur:n+=1
            else:runs.append(n);cur=v;n=1
        runs.append(n); mr=sum(runs)/len(runs)
        cls['consensus' if mr>w/3 else ('confetti' if mr<2.5 else 'domains')]+=1
    s=sum(tot.values()) or 1
    shares=sorted((v/s for v in tot.values()),reverse=True)
    return dict(top_share=shares[0],
                second_share=shares[1] if len(shares)>1 else 0.0,
                kinds_above_15pct=sum(1 for x in shares if x>=.15),
                domains=cls['domains']/nrows, confetti=cls['confetti']/nrows,
                consensus=cls['consensus']/nrows)

# Baseline, measured from the 12:51 figure.
BASELINE=dict(top_share=.49, kinds_above_15pct=2, domains=.57, confetti=.00)

if __name__=='__main__':
    path,w = sys.argv[1], int(sys.argv[2])
    panel = sys.argv[3] if len(sys.argv)>3 else 'full'
    p=profile(path,w,panel)
    print(f"\n{path}")
    print(f"  dominant kind share   {p['top_share']:.3f}   (ratchet: <= {BASELINE['top_share']:.2f})")
    print(f"  kinds above 15%       {p['kinds_above_15pct']}       (ratchet: >= {BASELINE['kinds_above_15pct']})")
    print(f"  domain rows           {p['domains']:.3f}   (ratchet: >= {BASELINE['domains']:.2f})")
    print(f"  confetti rows         {p['confetti']:.3f}   (ratchet: <= {BASELINE['confetti']:.2f})")
    print(f"  consensus rows        {p['consensus']:.3f}   (informational)")
    fails=[]
    if p['top_share']>BASELINE['top_share']+.02: fails.append("one kind dominates")
    if p['kinds_above_15pct']<BASELINE['kinds_above_15pct']: fails.append("coexistence lost")
    if p['domains']<BASELINE['domains']-.05: fails.append("structure lost")
    if p['confetti']>BASELINE['confetti']+.05: fails.append("confetti")
    print(f"\n  {'RATCHET HELD' if not fails else 'RATCHET BROKEN: '+', '.join(fails)}\n")
