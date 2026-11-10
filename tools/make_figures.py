#!/usr/bin/env python3
"""Builds the tabular-figure variant of Tajawal used for amounts ("Adam Figures").
Every digit gets the width of the widest digit and is centred in it, so balances don't jump
while they roll. Tajawal is SIL OFL 1.1; the derived font is renamed as the licence asks.
usage: make_figures.py <tajawal.ttf> <out.ttf>
"""
import sys
from fontTools.ttLib import TTFont
from fontTools.pens.recordingPen import DecomposingRecordingPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.pens.transformPen import TransformPen

src, out = sys.argv[1], sys.argv[2]
f = TTFont(src)
cmap = f.getBestCmap()
hmtx = f["hmtx"]
glyf = f["glyf"]
gs = f.getGlyphSet()
digits = [cmap[ord(c)] for c in "0123456789"]
width = max(hmtx[g][0] for g in digits)
for g in digits:
    adv, _ = hmtx[g]
    shift = (width - adv) // 2
    rec = DecomposingRecordingPen(gs)
    gs[g].draw(rec)
    pen = TTGlyphPen(None)
    rec.replay(TransformPen(pen, (1, 0, 0, 1, shift, 0)))
    new = pen.glyph()
    glyf[g] = new
    new.recalcBounds(glyf)
    hmtx[g] = (width, getattr(new, "xMin", 0))
name = f["name"]
style = name.getDebugName(2) or "Regular"
for rec in name.names:
    if rec.nameID in (1, 16):
        rec.string = "Adam Figures"
    elif rec.nameID == 4:
        rec.string = f"Adam Figures {style}"
    elif rec.nameID == 6:
        rec.string = f"AdamFigures-{style}"
f.save(out)
print(out, "digit width", width)
