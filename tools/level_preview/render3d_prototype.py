#!/usr/bin/env python3
"""Software reference render of the 3D ring look (torus rings with gaps, woven z-profile at crossings, PBR-ish lighting).
Used to validate the art direction before/while porting to the GLES renderer. Pure numpy (ray-marched SDF).
Usage: python3 tools/level_preview/render3d_prototype.py content/levels/world_01/level_025.json out.png [size]
"""
import json, math, sys
import numpy as np
from PIL import Image

def norm(v): return v / (np.linalg.norm(v, axis=-1, keepdims=True) + 1e-9)

def crossings(a, b):
    dx, dy = b["center"][0]-a["center"][0], b["center"][1]-a["center"][1]
    d = math.hypot(dx, dy)
    if d < 1e-9 or d > a["radius"]+b["radius"] or d < abs(a["radius"]-b["radius"]): return []
    x = (d*d + a["radius"]**2 - b["radius"]**2)/(2*d); h2 = a["radius"]**2 - x*x
    if h2 < 0: return []
    h = math.sqrt(h2); ux, uy = dx/d, dy/d
    px, py = a["center"][0]+ux*x, a["center"][1]+uy*x
    pts = [(px-uy*h, py+ux*h), (px+uy*h, py-ux*h)]
    out = [(p, math.degrees(math.atan2(p[1]-a["center"][1], p[0]-a["center"][0])) % 360,
               math.degrees(math.atan2(p[1]-b["center"][1], p[0]-b["center"][0])) % 360) for p in pts]
    return sorted(out, key=lambda t: t[1])

MAT = {"dough_sesame": (0.93, 0.83, 0.66), "dough_matcha": (0.60, 0.74, 0.47), "dough_beet": (0.85, 0.45, 0.55),
       "dough_ube": (0.63, 0.50, 0.78), "dough_gold": (0.95, 0.76, 0.35), "dough_bamboo": (0.76, 0.68, 0.43)}

def build(level):
    rings = level["rings"]; weaves = {(w["a"], w["b"]): w["pattern"] for w in level.get("weaves", [])}
    bumps = {r["id"]: [] for r in rings}   # (world angle deg, +1 over / -1 under)
    for i, a in enumerate(rings):
        for j, b in enumerate(rings):
            if i >= j: continue
            cr = crossings(a, b)
            if len(cr) < 2: continue
            pat = weaves.get((a["id"], b["id"]), "alt1")
            for idx, (p, angA, angB) in enumerate(cr):
                a_over = {"alt1": idx == 0, "alt2": idx == 1, "a_over": True, "b_over": False}[pat]
                bumps[a["id"]].append((angA, 1 if a_over else -1))
                bumps[b["id"]].append((angB, -1 if a_over else 1))
    return bumps

def render(level, size=700, tilt=32.0):
    rings = level["rings"]; bumps = build(level)
    # camera: orthographic-ish perspective looking down at `tilt` degrees from vertical
    S = size
    ys, xs = np.mgrid[0:S, 0:S]
    u = (xs + 0.5) / S; v = 1 - (ys + 0.5) / S
    t = math.radians(tilt)
    # board plane z=0, camera above looking down; screen v maps to y with foreshortening
    fwd = np.array([0, math.sin(t), -math.cos(t)]); right = np.array([1, 0, 0]); up = np.cross(right, fwd)
    cam = np.array([0.5, 0.5, 0.0]) - fwd * 1.6
    ro = cam[None, None, :] + right[None, None, :] * ((u - 0.5) * 1.05)[..., None] + up[None, None, :] * ((v - 0.5) * 1.05)[..., None]
    rd = np.broadcast_to(fwd, ro.shape).copy()
    H, W = S, S
    def sdf(p):
        best = np.full(p.shape[:-1], 1e9); mid = np.full(p.shape[:-1], -1)
        for i, r in enumerate(rings):
            c = np.array([r["center"][0], r["center"][1], 0.0]); R = r["radius"]; th = r["thickness"] * 0.5
            q = p - c
            ang = (np.degrees(np.arctan2(q[..., 1], q[..., 0])) ) % 360
            local = (ang - r["initialAngleDeg"]) % 360
            # weave height
            z0 = np.zeros_like(ang)
            for (wa, s) in bumps[r["id"]]:
                d = (ang - wa + 180) % 360 - 180
                z0 += s * th * 1.05 * np.exp(-(d / (14.0 + 10 * th / R)) ** 2 * 0.5)
            ringxy = np.sqrt(q[..., 0] ** 2 + q[..., 1] ** 2) - R
            dist = np.sqrt(ringxy ** 2 + (q[..., 2] - z0 - th * 1.05) ** 2) - th
            ingap = np.zeros_like(dist, dtype=bool)
            for g in r["gaps"]:
                rel = (local - g["startDeg"]) % 360
                ingap |= (rel > 1.5) & (rel < g["widthDeg"] - 1.5)
            dist = np.where(ingap, 1e3, dist)
            m = dist < best; best = np.where(m, dist, best); mid = np.where(m, i, mid)
        # table plane
        plane = p[..., 2]
        m = plane < best; best = np.where(m, plane, best); mid = np.where(m, -2, mid)
        return best, mid
    p = ro.copy(); hit = np.zeros((H, W), bool); mid = np.full((H, W), -1)
    for _ in range(120):
        d, m = sdf(p)
        active = ~hit
        hit |= (d < 0.0008)
        mid = np.where(hit & (mid == -1), m, mid)
        p = p + rd * np.clip(d, 0.0005, 0.08)[..., None] * active[..., None]
    # normals
    eps = 0.0015
    def grad():
        d0, _ = sdf(p); n = np.zeros_like(p)
        for k in range(3):
            e = np.zeros(3); e[k] = eps
            d1, _ = sdf(p + e); n[..., k] = (d1 - d0) / eps
        return norm(n)
    n = grad()
    key = norm(np.array([-0.45, 0.35, 0.82])); fill = norm(np.array([0.6, -0.2, 0.5])); rim = norm(np.array([0.2, -0.9, 0.3]))
    viewd = -rd
    col = np.zeros((H, W, 3))
    base = np.zeros((H, W, 3)); rough = np.full((H, W), 0.55)
    for i, r in enumerate(rings):
        base[mid == i] = MAT.get(r["materialId"], (0.9, 0.8, 0.7))
    base[mid == -2] = (0.80, 0.62, 0.42)
    # ambient occlusion-ish contact shadow on the table from rings above
    ao = np.ones((H, W))
    tbl = mid == -2
    for r in rings:
        c = np.array([r["center"][0], r["center"][1]]); q = p[..., :2] - c
        ringxy = np.abs(np.sqrt(q[..., 0] ** 2 + q[..., 1] ** 2) - r["radius"])
        ao = np.where(tbl, ao * (1 - 0.55 * np.exp(-(ringxy / (r["thickness"] * 1.3)) ** 2)), ao)
    def shade(l, c, inten):
        ndl = np.clip((n * l).sum(-1), 0, 1)
        h = norm(l + viewd); ndh = np.clip((n * h).sum(-1), 0, 1)
        spec = ndh ** (2 / (rough ** 2 + 1e-3)) * (1 - rough) * 0.9
        return (ndl[..., None] * base + spec[..., None]) * np.array(c) * inten
    col += shade(key, (1.0, 0.95, 0.85), 1.25) + shade(fill, (0.75, 0.8, 1.0), 0.35) + shade(rim, (1.0, 0.8, 0.6), 0.5)
    col += base * 0.22 * np.array([0.9, 0.9, 1.0])
    fres = (1 - np.clip((n * viewd).sum(-1), 0, 1)) ** 3
    col += fres[..., None] * 0.18 * np.array([1.0, 0.9, 0.8])
    col *= ao[..., None]
    col = np.where(hit[..., None], col, np.array([0.80, 0.62, 0.42]) * 0.9)
    col = col / (1 + col) * 1.35   # tone map
    img = (np.clip(col, 0, 1) ** (1 / 2.2) * 255).astype(np.uint8)
    return Image.fromarray(img)

if __name__ == "__main__":
    lv = json.load(open(sys.argv[1])); out = sys.argv[2]; size = int(sys.argv[3]) if len(sys.argv) > 3 else 600
    render(lv, size).save(out); print(out)
