#!/usr/bin/env python3
"""Render a level JSON to PNG for visual QA (mirrors the in-app weave rendering: over/under at crossings)."""
import json, math, sys
from PIL import Image, ImageDraw

MAT = {"dough_sesame": (233, 205, 160), "dough_matcha": (150, 190, 120), "dough_beet": (226, 120, 150),
       "dough_ube": (165, 130, 200), "dough_gold": (235, 190, 90), "dough_bamboo": (190, 170, 110)}
COL = {"red": (220, 70, 60), "jade": (70, 180, 140), "ube": (140, 90, 200), None: (120, 90, 60)}

def to_px(p, S): return (p[0] * S, (1 - p[1]) * S)

def crossings(a, b):
    dx, dy = b["center"][0]-a["center"][0], b["center"][1]-a["center"][1]
    d = math.hypot(dx, dy)
    if d < 1e-9 or d > a["radius"]+b["radius"] or d < abs(a["radius"]-b["radius"]): return []
    x = (d*d + a["radius"]**2 - b["radius"]**2)/(2*d); h2 = a["radius"]**2 - x*x
    if h2 < 0: return []
    h = math.sqrt(h2); ux, uy = dx/d, dy/d
    px, py = a["center"][0]+ux*x, a["center"][1]+uy*x
    pts = [(px-uy*h, py+ux*h), (px+uy*h, py-ux*h)]
    out = [(p, (math.degrees(math.atan2(p[1]-a["center"][1], p[0]-a["center"][0])) % 360)) for p in pts]
    return sorted(out, key=lambda t: t[1])

def draw_ring_arc(d, r, S, a0, a1, color, width, angle):
    # PIL arcs: angles clockwise in image coords; our math angles are CCW with y up -> image angle = -angle
    cx, cy = to_px(r["center"], S); R = r["radius"]*S
    box = [cx-R, cy-R, cx+R, cy+R]
    d.arc(box, start=-(a1), end=-(a0), fill=color, width=width)

def wire_intervals(r):
    """local intervals of wire (complement of gaps), as list of (start,end) in degrees 0..360 (may wrap)"""
    gaps = sorted([(g["startDeg"] % 360, g["widthDeg"]) for g in r["gaps"]])
    ints = []; cur = 0.0
    for s, w in gaps:
        if s > cur: ints.append((cur, s))
        cur = max(cur, s + w)
    if cur < 360: ints.append((cur, 360))
    # merge wrap: gap starting at 0 handled; if last gap wraps past 360 trim first interval
    over = cur - 360
    if over > 0 and ints and ints[0][0] < over: ints[0] = (over, ints[0][1])
    return [(a, b) for a, b in ints if b - a > 0.5]

def render(level, S=800, angles=None):
    img = Image.new("RGBA", (S, S), (250, 240, 225, 255)); d = ImageDraw.Draw(img)
    rings = level["rings"]; angles = angles or {r["id"]: r["initialAngleDeg"] for r in rings}
    width = lambda r: max(3, int(r["thickness"]*S))
    def draw_full(r, col=None):
        for a, b in wire_intervals(r):
            draw_ring_arc(d, r, S, a+angles[r["id"]], b+angles[r["id"]], col or MAT.get(r["materialId"], (200,180,150)), width(r), angles[r["id"]])
    for r in rings: draw_full(r)
    # over segments at crossings
    weaves = {(w["a"], w["b"]): w["pattern"] for w in level.get("weaves", [])}
    byid = {r["id"]: r for r in rings}
    for i, a in enumerate(rings):
        for j, b in enumerate(rings):
            if i >= j: continue
            cr = crossings(a, b)
            if len(cr) < 2: continue
            pat = weaves.get((a["id"], b["id"]), "alt1")
            for idx, (p, angA) in enumerate(cr):
                a_over = {"alt1": idx == 0, "alt2": idx == 1, "a_over": True, "b_over": False}[pat]
                top = a if a_over else b
                ang = math.degrees(math.atan2(p[1]-top["center"][1], p[0]-top["center"][0])) % 360
                local = (ang - angles[top["id"]]) % 360
                # only redraw if wire exists there
                if any(s <= local <= e for s, e in wire_intervals(top)):
                    span = math.degrees(math.asin(min(1, (top["thickness"]*1.6)/top["radius"])))
                    draw_ring_arc(d, top, S, ang-span, ang+span, MAT.get(top["materialId"]), width(top), 0)
                    # outline for readability
    for r in rings:
        cx, cy = to_px(r["center"], S)
        d.text((cx-8, cy-8), r["id"], fill=(80, 50, 30, 255))
        if r.get("lockedBy"): d.text((cx-8, cy+6), "lock<" + ",".join(r["lockedBy"]), fill=(150, 30, 30, 255))
        if r.get("linkGroup"): d.text((cx-8, cy+18), f'{r["linkGroup"]}{"+" if r["linkRatio"]==1 else "-"}', fill=(30, 30, 150, 255))
        if r.get("arc"): d.text((cx-8, cy+30), f'arc {r["arc"]["minDeg"]}-{r["arc"]["maxDeg"]}', fill=(30, 100, 30, 255))
        if r.get("colorId"):
            d.ellipse([cx-6, cy-30, cx+6, cy-18], fill=COL[r["colorId"]])
    for o in level.get("obstacles", []):
        col = COL.get(o.get("colorId"), (120, 90, 60))
        if o["shape"] == "segment":
            rot = math.radians(o.get("baseAngleDeg", 0))
            def rp(p):
                x, y = p[0]-o["center"][0], p[1]-o["center"][1]
                if o.get("linkedRingId"):
                    rot2 = rot + math.radians(angles[o["linkedRingId"]])
                else: rot2 = rot
                return (o["center"][0] + x*math.cos(rot2) - y*math.sin(rot2), o["center"][1] + x*math.sin(rot2) + y*math.cos(rot2))
            d.line([to_px(rp(o["from"]), S), to_px(rp(o["to"]), S)], fill=col, width=max(3, int(o["thickness"]*S)))
    d.text((10, 10), f'{level["id"]} par={level["parMoves"]} tags={",".join(level["mechanicTags"])}', fill=(60, 40, 20, 255))
    return img

if __name__ == "__main__":
    for f in sys.argv[1:]:
        lv = json.load(open(f)); out = f.replace(".json", ".png").replace("content/levels", "content/qa/preview")
        import os; os.makedirs(os.path.dirname(out), exist_ok=True)
        render(lv).save(out); print(out)
