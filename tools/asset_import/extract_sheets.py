#!/usr/bin/env python3
"""Dumpling Diner sprite-sheet importer.

Slices every sheet in assets/imported/sheets/*.png into individual sprites,
drops baked-in label pills / captions / title banners, names sprites from
tools/asset_import/sheets_config.json, and writes:

  assets/imported/sprites/<sheetId>/<name>.png   (bbox crop + 4px padding)
  assets/imported/brand/dumpling_diner_logo.png  (sheet16 logo)
  assets/imported/manifest.json
  assets/imported/contact_sheets/<sheetId>.png   (QA sheet with names)

Usage:
  python3 tools/asset_import/extract_sheets.py            # full run
  python3 tools/asset_import/extract_sheets.py --debug    # also writes indexed
                                                          # debug contact sheets
  python3 tools/asset_import/extract_sheets.py --only 07 15

Pipeline per sheet (see sheets_config.json):
  1. alpha mask (alpha > 8)
  2. label pass: connected components with a tiny dilation; components that
     look like label pills (solid rounded rect, wide-and-short, one dominant
     colour) or white caption text are masked out, as are config
     `exclude_rects`.
  3. each config `region` is sliced with one of the methods
       cc    : dilated connected components + overlap merge (irregular sheets)
       grid  : uniform cols x rows cells, bbox of alpha inside each cell
       rows  : y-projection row bands, then x-projection inside each row
       cols  : x-projection column bands, then y-projection inside each col
       boxes : explicit manual boxes
  4. boxes get names from the region's `names` list (reading order) or a
     `prefix` pattern, are cropped with padding and written out.
"""
import argparse
import hashlib
import json
import os
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

try:
    from scipy import ndimage  # optional, faster
except Exception:  # pragma: no cover
    ndimage = None

ROOT = Path(__file__).resolve().parents[2]
SHEET_DIR = ROOT / "assets/imported/sheets"
OUT_DIR = ROOT / "assets/imported/sprites"
BRAND_DIR = ROOT / "assets/imported/brand"
CONTACT_DIR = ROOT / "assets/imported/contact_sheets"
MANIFEST = ROOT / "assets/imported/manifest.json"
CONFIG = Path(__file__).resolve().parent / "sheets_config.json"

ALPHA_T = 24  # connectivity threshold; the sheets carry a wide alpha 1-20 fringe that bridges neighbours
MIN_SIZE = 24
PAD = 4
SOURCE = "Dumpling Diner asset sheet (Shio Studios, owner-supplied)"
LICENSE = "owner-supplied, reuse permitted per project bible"


# ----------------------------------------------------------------- helpers
def dilate(mask, r):
    if r <= 0:
        return mask
    if ndimage is not None:
        return ndimage.binary_dilation(mask, iterations=r)
    m = mask.copy()
    for _ in range(r):
        p = np.pad(m, 1)
        m = (p[1:-1, 1:-1] | p[:-2, 1:-1] | p[2:, 1:-1] | p[1:-1, :-2] | p[1:-1, 2:])
    return m


def label_components(mask):
    """Return list of bboxes (x0,y0,x1,y1 exclusive) of 8-connected components."""
    if ndimage is not None:
        lab, n = ndimage.label(mask, structure=np.ones((3, 3)))
        objs = ndimage.find_objects(lab)
        out = []
        for sl in objs:
            if sl is None:
                continue
            ys, xs = sl
            out.append((xs.start, ys.start, xs.stop, ys.stop))
        return out
    # pure numpy/python fallback: union-find on runs
    h, w = mask.shape
    parent = {}

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[ra] = rb

    runs = []  # (y, x0, x1, id)
    prev = []
    for y in range(h):
        row = mask[y]
        xs = np.flatnonzero(np.diff(np.concatenate(([0], row.view(np.int8), [0]))))
        cur = []
        for i in range(0, len(xs), 2):
            x0, x1 = int(xs[i]), int(xs[i + 1])
            rid = len(runs)
            parent[rid] = rid
            runs.append([y, x0, x1, rid])
            for py, px0, px1, pid in prev:
                if px0 <= x1 and x0 <= px1:  # 8-connectivity (touching diag)
                    union(rid, pid)
            cur.append((y, x0, x1, rid))
        prev = cur
    boxes = {}
    for y, x0, x1, rid in runs:
        r = find(rid)
        b = boxes.get(r)
        if b is None:
            boxes[r] = [x0, y, x1, y + 1]
        else:
            b[0] = min(b[0], x0); b[1] = min(b[1], y); b[2] = max(b[2], x1); b[3] = max(b[3], y + 1)
    return [tuple(b) for b in boxes.values()]


def tight_bbox(alpha, box):
    x0, y0, x1, y1 = box
    sub = alpha[y0:y1, x0:x1] > ALPHA_T
    if not sub.any():
        return None
    ys = np.flatnonzero(sub.any(axis=1))
    xs = np.flatnonzero(sub.any(axis=0))
    return (x0 + int(xs[0]), y0 + int(ys[0]), x0 + int(xs[-1]) + 1, y0 + int(ys[-1]) + 1)


def bands(profile, min_gap, min_band):
    """Split a 1-D boolean profile into [start, stop) bands separated by >= min_gap zeros."""
    idx = np.flatnonzero(profile)
    if len(idx) == 0:
        return []
    out = []
    s = p = int(idx[0])
    for i in idx[1:]:
        i = int(i)
        if i - p > min_gap:
            out.append([s, p + 1])
            s = i
        p = i
    out.append([s, p + 1])
    # merge/drop tiny bands
    res = []
    for b in out:
        if b[1] - b[0] < min_band:
            continue
        res.append(b)
    return res


def is_pill(rgba, box):
    """Heuristic: baked-in label pill (rounded solid rect, wide & short, one dominant colour)."""
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    if h > 110 or w < 40 or w > 700 or w / h < 1.4:
        return False
    sub = rgba[y0:y1, x0:x1]
    a = sub[..., 3] >= 128
    fill = a.mean()
    if fill < 0.80:            # pills are solid rounded rects
        return False
    if min(a[:2].mean(), a[-2:].mean()) < 0.5:   # straight top/bottom edge
        return False
    rgb = sub[..., :3][a].astype(np.int32)
    white = ((rgb > 215).all(axis=1)).mean()      # text pixels
    dark = ((rgb < 90).all(axis=1)).mean()        # dark text on beige pills
    q = (rgb // 24)
    keys = q[:, 0] * 10000 + q[:, 1] * 100 + q[:, 2]
    _, counts = np.unique(keys, return_counts=True)
    dom = counts.max() / len(keys)                # one dominant fill colour
    med = np.median(rgb, axis=0)
    brown = 60 < med[0] < 130 and 30 < med[1] < 80 and 20 < med[2] < 65
    if brown and 0.02 < white < 0.45 and dom > 0.25:
        return True
    beige = med[0] > 200 and med[1] > 160 and med[2] > 110 and med[2] < 200
    if beige and 0.02 < dark < 0.4 and dom > 0.3:
        return True
    # other coloured pills (orange/pink/green/purple row labels) with white text
    return dom > 0.35 and 0.03 < white < 0.4 and fill > 0.85


# Flat fills used by the sheet author's coloured label pills.  Only the dark
# brown pill is matched by default (it never occurs as a flat sprite fill);
# the others are opt-in per sheet via "pill_colors" because cream/pink sprites
# (bao buns, mochi, panda faces) would match them.
PILL_COLORS = []
NAMED_PILL_COLORS = {
    "beige": [(241, 195, 155), (251, 230, 202)],
    "orange": [(212, 106, 50), (230, 92, 34), (227, 103, 33)],
    "pink": [(220, 91, 94), (231, 91, 107), (237, 87, 116)],
    "green": [(71, 89, 38)], "rust": [(171, 62, 33)],
}


def colour_mask(rgba, extra_colors=()):
    a = rgba[..., 3] >= 200
    rgb = rgba[..., :3].astype(np.int16)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    brown = (r >= 40) & (r <= 125) & (g >= 20) & (g <= 75) & (b >= 10) & (b <= 55) & (r > g) & (g >= b)
    mask = brown
    cols = list(PILL_COLORS)
    for x in extra_colors:
        cols += NAMED_PILL_COLORS[x] if isinstance(x, str) else [tuple(x)]
    for c in cols:
        mask |= (np.abs(rgb - np.array(c, np.int16)).max(axis=2) <= 22)
    return mask & a, rgb


def colour_pills(rgba, extra_colors=()):
    """Second label detector: flat-colour rounded rectangles.  Pills are a single
    flat colour (dark brown by default, or one of PILL_COLORS), so a mask of
    'pixels close to a pill colour' yields solid wide blobs with text holes even
    when sparkles or glows touch the pill.  Returns bboxes."""
    mask, rgb = colour_mask(rgba, extra_colors)
    out = []
    for bx in label_components(mask):
        bx = tight_bbox(mask.astype(np.uint8) * 255, bx)
        if not bx:
            continue
        x0, y0, x1, y1 = bx
        w, h = x1 - x0, y1 - y0
        if w < 40 or h < 16 or h > 120 or w / h < 1.3 or w > 700:
            continue
        sub = mask[y0:y1, x0:x1]
        fill = sub.mean()
        if fill < 0.45:
            continue
        px = rgb[y0:y1, x0:x1][sub]
        med = np.median(px, axis=0)
        flat = (np.abs(px - med).max(axis=1) <= 14).mean()
        if flat < 0.55:                          # wood grain / shading, not a flat pill
            continue
        srgb = rgb[y0:y1, x0:x1][rgba[y0:y1, x0:x1, 3] >= 200]
        white = ((srgb > 215).all(axis=1)).mean()
        dark = ((srgb < 80).all(axis=1)).mean()
        if white < 0.02 and dark < 0.02:          # no text
            continue
        colfill = sub.mean(axis=0)
        core = colfill[int(w * 0.1):int(w * 0.9)]
        edges = min(sub[:3].mean(), sub[-3:].mean())   # straight top & bottom edges
        if (core >= 0.5).mean() >= 0.85 and edges >= 0.45:
            out.append((x0, y0, x1, y1))
    return out


def is_caption(rgba, box):
    """Loose white caption text (no pill)."""
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    if h > 26 or w < 30 or w / h < 2.5:
        return False
    sub = rgba[y0:y1, x0:x1]
    a = sub[..., 3] >= 128
    if a.mean() > 0.6:
        return False
    rgb = sub[..., :3][a]
    white = ((rgb > 200).all(axis=1)).mean()
    return white > 0.8


def inside(box, rect):
    x0, y0, x1, y1 = box
    rx0, ry0, rx1, ry1 = rect
    return x0 >= rx0 and y0 >= ry0 and x1 <= rx1 and y1 <= ry1


def overlap_frac(a, b):
    ix = max(0, min(a[2], b[2]) - max(a[0], b[0]))
    iy = max(0, min(a[3], b[3]) - max(a[1], b[1]))
    return ix * iy / max(1, min((a[2]-a[0])*(a[3]-a[1]), (b[2]-b[0])*(b[3]-b[1])))


def merge_boxes(boxes, x_overlap=None, y_gap=0, merge_frac=0.3):
    """Merge boxes that intersect by >= merge_frac of the smaller box, or (when
    x_overlap is set) whose x ranges overlap >= x_overlap of the narrower box and
    that are vertically within y_gap px (detached steam/thought bubbles)."""
    boxes = [list(b) for b in boxes]
    changed = True
    while changed:
        changed = False
        out = []
        while boxes:
            a = boxes.pop()
            merged = False
            for b in out:
                ix = min(a[2], b[2]) - max(a[0], b[0])
                iy = min(a[3], b[3]) - max(a[1], b[1])
                nw = min(a[2]-a[0], b[2]-b[0])
                if (ix > 0 and iy > 0 and overlap_frac(a, b) >= merge_frac) or \
                   (x_overlap is not None and ix >= x_overlap * nw and iy >= -y_gap):
                    b[0] = min(a[0], b[0]); b[1] = min(a[1], b[1])
                    b[2] = max(a[2], b[2]); b[3] = max(a[3], b[3])
                    merged = True; changed = True
                    break
            if not merged:
                out.append(a)
        boxes = out
    return [tuple(b) for b in boxes]


def reading_order(boxes, row_tol=0.5):
    """Sort boxes into rows (by vertical overlap) then left-to-right."""
    boxes = sorted(boxes, key=lambda b: (b[1], b[0]))
    rows = []
    for b in boxes:
        placed = False
        for r in rows:
            ry0 = min(x[1] for x in r); ry1 = max(x[3] for x in r)
            ov = min(b[3], ry1) - max(b[1], ry0)
            if ov > row_tol * min(b[3] - b[1], ry1 - ry0):
                r.append(b); placed = True; break
        if not placed:
            rows.append([b])
    rows.sort(key=lambda r: sum(x[1] for x in r) / len(r))
    out = []
    for r in rows:
        out.extend(sorted(r, key=lambda b: b[0]))
    return out, rows


# ---------------------------------------------------------------- slicing
def slice_cc(alpha, rect, dil, min_size, x_overlap, y_gap, merge_frac=0.3):
    x0, y0, x1, y1 = rect
    sub = alpha[y0:y1, x0:x1] > ALPHA_T
    m = dilate(sub, dil)
    boxes = [(bx0 + x0, by0 + y0, bx1 + x0, by1 + y0) for bx0, by0, bx1, by1 in label_components(m)]
    boxes = [tight_bbox(alpha, b) for b in boxes]
    boxes = [b for b in boxes if b]
    boxes = merge_boxes(boxes, x_overlap, y_gap, merge_frac)
    boxes = [b for b in boxes if (b[2]-b[0]) >= min_size and (b[3]-b[1]) >= min_size]
    boxes, _ = reading_order(boxes)
    return boxes


def slice_grid(alpha, rect, cols, rows, assign="centroid", dil=2, warnings=None, sid="",
               col_bounds=None, row_bounds=None):
    """Uniform grid. assign='clip': bbox of alpha inside each cell.
    assign='centroid': connected components are assigned to the cell holding their
    centroid, so sparkles/petals/steam that stray over a cell border stay with
    their sprite and sprites are never clipped by the cell edge."""
    x0, y0, x1, y1 = rect
    xb = col_bounds or [x0 + (x1 - x0) * i / cols for i in range(cols + 1)]
    yb = row_bounds or [y0 + (y1 - y0) * i / rows for i in range(rows + 1)]
    cols, rows = len(xb) - 1, len(yb) - 1
    cell_rect = lambda r, c: (int(xb[c]), int(yb[r]), int(xb[c+1]), int(yb[r+1]))

    def cell_of(cx, cy):
        c = max(0, min(cols - 1, int(np.searchsorted(xb, cx, side="right") - 1)))
        r = max(0, min(rows - 1, int(np.searchsorted(yb, cy, side="right") - 1)))
        return r, c

    cells = {}

    def add(key, b):
        if key in cells:
            o = cells[key]
            cells[key] = (min(o[0], b[0]), min(o[1], b[1]), max(o[2], b[2]), max(o[3], b[3]))
        else:
            cells[key] = b

    if assign == "clip":
        for r in range(rows):
            for c in range(cols):
                b = tight_bbox(alpha, cell_rect(r, c))
                if b:
                    cells[(r, c)] = b
    else:
        sub = alpha[y0:y1, x0:x1] > ALPHA_T
        for bx0, by0, bx1, by1 in label_components(dilate(sub, dil)):
            b = tight_bbox(alpha, (bx0 + x0, by0 + y0, bx1 + x0, by1 + y0))
            if not b:
                continue
            r0, c0 = cell_of(b[0] + 1, b[1] + 1)
            r1, c1 = cell_of(b[2] - 1, b[3] - 1)
            cw, ch = xb[c0+1] - xb[c0], yb[r0+1] - yb[r0]
            if (b[2] - b[0]) > 1.25 * cw or (b[3] - b[1]) > 1.25 * ch:
                # component bridges several cells (touching neighbours): clip per cell
                for r in range(r0, r1 + 1):
                    for c in range(c0, c1 + 1):
                        cr = cell_rect(r, c)
                        cb = tight_bbox(alpha, (max(b[0], cr[0]), max(b[1], cr[1]), min(b[2], cr[2]), min(b[3], cr[3])))
                        if cb:
                            add((r, c), cb)
                continue
            add(cell_of((b[0] + b[2]) / 2, (b[1] + b[3]) / 2), b)
    out = []
    for r in range(rows):
        for c in range(cols):
            b = cells.get((r, c))
            if b and (b[2]-b[0]) >= MIN_SIZE and (b[3]-b[1]) >= MIN_SIZE:
                out.append((r, c, b))
            elif warnings is not None:
                warnings.append(f"{sid}: empty grid cell row {r} col {c}")
    return out


def slice_rows(alpha, rect, min_gap, min_band, min_size, inner_gap=None, axis="rows"):
    x0, y0, x1, y1 = rect
    sub = alpha[y0:y1, x0:x1] > ALPHA_T
    inner_gap = min_gap if inner_gap is None else inner_gap
    out = []
    if axis == "rows":
        for ry0, ry1 in bands(sub.any(axis=1), min_gap, min_band):
            strip = sub[ry0:ry1]
            for cx0, cx1 in bands(strip.any(axis=0), inner_gap, min_size):
                b = tight_bbox(alpha, (x0+cx0, y0+ry0, x0+cx1, y0+ry1))
                if b and (b[2]-b[0]) >= min_size and (b[3]-b[1]) >= min_size:
                    out.append(b)
    else:
        for cx0, cx1 in bands(sub.any(axis=0), min_gap, min_band):
            strip = sub[:, cx0:cx1]
            for ry0, ry1 in bands(strip.any(axis=1), inner_gap, min_size):
                b = tight_bbox(alpha, (x0+cx0, y0+ry0, x0+cx1, y0+ry1))
                if b and (b[2]-b[0]) >= min_size and (b[3]-b[1]) >= min_size:
                    out.append(b)
        out, _ = reading_order(out)
    return out


def valleys(p, min_dist, rel_depth=0.3, rel_level=0.7):
    """Indices of prominent local minima of a 1-D profile (frame junctions)."""
    n = len(p)
    if n < 3:
        return []
    top = p.max()
    if top <= 0:
        return []
    cand = []
    for i in range(1, n - 1):
        if p[i] <= p[i - 1] and p[i] <= p[i + 1] and p[i] <= rel_level * top:
            prom = min(p[:i].max(), p[i + 1:].max()) - p[i]
            if prom >= rel_depth * top:
                cand.append((p[i], i))
    cand.sort()
    out = []
    for v, i in cand:
        if all(abs(i - j) >= min_dist for j in out) and min_dist <= i <= n - min_dist:
            out.append(i)
    return sorted(out)


def sm_full(profile, w=5):
    return np.convolve(profile.astype(float), np.ones(w) / w, mode="same")


def refine_bounds(profile, bounds, search):
    """Nudge each inner boundary to the emptiest column within +-search px."""
    out = [bounds[0]]
    for b in bounds[1:-1]:
        lo, hi = max(0, int(b - search)), min(len(profile) - 1, int(b + search))
        seg = profile[lo:hi + 1]
        out.append(lo + int(np.argmin(seg)))
    out.append(bounds[-1])
    return out


def slice_groups(alpha, rect, row_bounds, group_bounds, cols, min_gap=6, min_band=30):
    """Animation sheets: rows of foods/animals, each row made of groups
    (idle/blink/...) of evenly spaced frames.  Rows come from row_bounds or a
    y-projection; group x-ranges are fixed per sheet; frames are split uniformly
    inside a group with boundaries nudged to the emptiest column.
    Returns list of (row_index, group_index, frame_index, box)."""
    x0, y0, x1, y1 = rect
    sub = alpha[y0:y1, x0:x1] > ALPHA_T
    if row_bounds is None:
        gx0, gx1 = group_bounds[0] - x0, group_bounds[1] - x0
        rb = bands(sub[:, gx0:gx1].any(axis=1), min_gap, min_band)
        row_bounds = [y0] + [(a[1] + b[0]) // 2 + y0 for a, b in zip(rb, rb[1:])] + [y1]
    out = []
    for r in range(len(row_bounds) - 1):
        ry0, ry1 = int(row_bounds[r]), int(row_bounds[r + 1])
        strip = (alpha[ry0:ry1] > ALPHA_T).sum(axis=0)
        for g in range(len(group_bounds) - 1):
            gx0, gx1 = int(group_bounds[g]), int(group_bounds[g + 1])
            n = cols[g]
            # bands of touching frames inside the group (ignore stray petals: > 2 px tall)
            gb = bands(strip[gx0:gx1] > 2, 1, 8)
            if not gb:
                continue
            cw = sum(b1 - b0 for b0, b1 in gb) / n
            # stubs of a neighbouring group's frame bleeding over the boundary
            gb = [b for b in gb if (b[1] - b[0]) >= 0.35 * cw] or gb
            cw = sum(b1 - b0 for b0, b1 in gb) / n
            counts = [max(1, int(round((b1 - b0) / cw))) for b0, b1 in gb]
            while sum(counts) > n:          # too many bands: drop the narrowest
                i = min(range(len(gb)), key=lambda k: gb[k][1] - gb[k][0])
                if counts[i] > 1:
                    counts[i] -= 1
                else:
                    gb.pop(i); counts.pop(i)
            while sum(counts) < n:          # too few: the widest band holds more frames
                i = max(range(len(gb)), key=lambda k: (gb[k][1] - gb[k][0]) / counts[k])
                counts[i] += 1
            # split each band at prominent valleys of the (smoothed) column profile;
            # the configured frame count is only a fallback
            cells = []
            for (b0, b1), k in zip(gb, counts):
                prof = strip[gx0 + b0:gx0 + b1].astype(float)
                sm = np.convolve(prof, np.ones(5) / 5, mode="same")
                cuts = valleys(sm, min_dist=max(12, int(cw * 0.45)))
                if cuts and len(cuts) + 1 == k:
                    edges = [gx0 + b0] + [gx0 + b0 + c for c in cuts] + [gx0 + b1]
                else:
                    bw = (b1 - b0) / k
                    edges = [gx0 + b0 + bw * i for i in range(k + 1)]
                    if k > 1:
                        edges = refine_bounds(sm_full(strip), edges, bw * 0.2)
                cs = [[int(edges[i]), int(edges[i + 1])] for i in range(len(edges) - 1)]
                # normalise: merge slivers into the narrower neighbour, split over-wide cells
                ref = cw
                changed = True
                while changed and len(cs) > 1:
                    changed = False
                    for i, (c0, c1) in enumerate(cs):
                        if c1 - c0 < 0.3 * ref:
                            j = i - 1 if i > 0 and (i == len(cs) - 1 or (cs[i-1][1]-cs[i-1][0]) <= (cs[i+1][1]-cs[i+1][0])) else i + 1
                            cs[j][0], cs[j][1] = min(cs[j][0], c0), max(cs[j][1], c1)
                            cs.pop(i); changed = True
                            break
                cells += [(c0, c1) for c0, c1 in cs]
            boxes = []
            for cx0, cx1 in cells:
                b = tight_bbox(alpha, (cx0, ry0, cx1, ry1))
                if b and (b[2]-b[0]) >= 8 and (b[3]-b[1]) >= 8:
                    boxes.append(list(b))
            # merge loose sparkle/petal fragments (much shorter than the frames)
            # into the nearest frame box of the same group
            if boxes:
                hmed = float(np.median([b[3] - b[1] for b in boxes]))
                frames = [b for b in boxes if (b[3] - b[1]) >= 0.5 * hmed and (b[2] - b[0]) >= MIN_SIZE]
                frags = [b for b in boxes if b not in frames]
                for f in frags:
                    if not frames:
                        break
                    fc = (f[0] + f[2]) / 2
                    t = min(frames, key=lambda b: abs((b[0] + b[2]) / 2 - fc))
                    t[0], t[1], t[2], t[3] = min(t[0], f[0]), min(t[1], f[1]), max(t[2], f[2]), max(t[3], f[3])
                for i, b in enumerate(sorted(frames, key=lambda b: b[0])):
                    out.append((r, g, i, tuple(b)))
    return out


def slice_rowbands(alpha, row_bounds, x0, x1, x_cuts, min_size, min_gap=1):
    """Explicit horizontal row bands; inside each row, x-projection bands with
    optional manual extra cuts (for touching tiles).  Returns (row_idx, box)."""
    out = []
    for r in range(len(row_bounds) - 1):
        ry0, ry1 = int(row_bounds[r]), int(row_bounds[r + 1])
        strip = alpha[ry0:ry1, x0:x1] > ALPHA_T
        xb = bands(strip.any(axis=0), min_gap, 10)
        cuts = sorted(x_cuts.get(str(r), []))
        cells = []
        for b0, b1 in xb:
            b0, b1 = b0 + x0, b1 + x0
            inner = [c for c in cuts if b0 < c < b1]
            edges = [b0] + inner + [b1]
            cells += [(edges[i], edges[i + 1]) for i in range(len(edges) - 1)]
        for cx0, cx1 in cells:
            b = tight_bbox(alpha, (cx0, ry0, cx1, ry1))
            if b and (b[2]-b[0]) >= min_size and (b[3]-b[1]) >= min_size:
                out.append((r, b))
    return out


# --------------------------------------------------------------- pipeline
def font(size):
    for p in ["/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
              "/usr/share/fonts/dejavu/DejaVuSans.ttf"]:
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def snake(s):
    s = re.sub(r"[^a-z0-9]+", "_", s.lower()).strip("_")
    return s


def process_sheet(path, cfg, debug, report):
    global ALPHA_T
    ALPHA_T = cfg.get("alpha_t", 24)
    sheet_id = path.name[:7]            # sheet01
    im = Image.open(path).convert("RGBA")
    rgba = np.array(im)
    alpha = rgba[..., 3].copy()
    H, W = alpha.shape

    # ---- label pass
    removed = []
    if cfg.get("labels", True):
        # pills are solid, so detect them at a high alpha threshold where sprite
        # glows/halos no longer bridge into them
        m = dilate(alpha >= cfg.get("label_alpha", 128), 1)
        for b in label_components(m):
            b = tight_bbox((alpha >= cfg.get("label_alpha", 128)).astype(np.uint8) * 255, b)
            if not b:
                continue
            if is_pill(rgba, b) or is_caption(rgba, b):
                removed.append(b)
        for b in colour_pills(rgba, cfg.get("pill_colors", ())):
            removed.append(b)
        # label_rects: bands that hold only captions; any pill-coloured blob there is a label
        if cfg.get("label_rects"):
            mask, _ = colour_mask(rgba, cfg.get("pill_colors", ()))
            for lr in cfg["label_rects"]:
                lx0, ly0, lx1, ly1 = lr
                sub = np.zeros_like(mask)
                sub[ly0:ly1, lx0:lx1] = mask[ly0:ly1, lx0:lx1]
                for b in label_components(dilate(sub, 2)):
                    if (b[2] - b[0]) >= 30 and (b[3] - b[1]) >= 12:
                        removed.append(b)
        protect = [tuple(r) for r in cfg.get("protect_rects", [])]
        removed = [b for b in removed if not any(overlap_frac(b, pr) > 0 for pr in protect)]
    for r in cfg.get("exclude_rects", []):
        removed.append(tuple(r))
    for x0, y0, x1, y1 in removed:
        alpha[y0:y1, x0:x1] = 0

    # ---- brand extraction (full alpha, before masking)
    sprites = []
    for br in cfg.get("brand", []):
        x0, y0, x1, y1 = br["rect"]
        full = rgba[..., 3]
        b = tight_bbox(full, (x0, y0, x1, y1))
        sprites.append(dict(name=br["name"], box=b, category="brand", tags=br.get("tags", []),
                            out=BRAND_DIR / f"{br['name']}.png", src=rgba))
        alpha[y0:y1, x0:x1] = 0

    alpha_vis = alpha.copy()
    # ---- regions
    warnings = []
    sheet_alpha_t = ALPHA_T
    for reg in cfg["regions"]:
        ALPHA_T = reg.get("alpha_t", sheet_alpha_t)
        rect = tuple(reg.get("rect", [0, 0, W, H]))
        method = reg.get("method", "cc")
        min_size = reg.get("min_size", MIN_SIZE)
        if method == "cc":
            boxes = slice_cc(alpha, rect, reg.get("dilate", 4), min_size,
                             reg.get("x_overlap"), reg.get("y_gap", 0), reg.get("merge_frac", 0.3))
        elif method == "grid":
            if reg.get("col_bounds") or reg.get("row_bounds"):
                cb, rb = reg.get("col_bounds"), reg.get("row_bounds")
                rect = (int(cb[0]) if cb else rect[0], int(rb[0]) if rb else rect[1],
                        int(cb[-1]) if cb else rect[2], int(rb[-1]) if rb else rect[3])
            cells = slice_grid(alpha, rect, reg.get("cols", 1), reg.get("rows", 1),
                               reg.get("assign", "centroid"), reg.get("dilate", 2), warnings, sheet_id,
                               reg.get("col_bounds"), reg.get("row_bounds"))
            boxes = [b for _, _, b in cells]
            if reg.get("grid_names"):
                gnames = reg["grid_names"]
                names = [gnames["pattern"].format(row=gnames["rows"][r] if "rows" in gnames else r + 1,
                                                  col=gnames["cols"][c] if "cols" in gnames else c + 1,
                                                  ci=c + 1, ri=r + 1) for r, c, _ in cells]
                reg = dict(reg, names=names)
        elif method in ("rows", "cols"):
            boxes = slice_rows(alpha, rect, reg.get("min_gap", 3), reg.get("min_band", 20),
                               min_size, reg.get("inner_gap"), method)
        elif method == "boxes":
            boxes = [tight_bbox(alpha, tuple(b)) for b in reg["boxes"]]
        elif method == "rowbands":
            items = slice_rowbands(alpha, reg["row_bounds"], rect[0], rect[2], reg.get("x_cuts", {}), min_size, reg.get("min_gap", 1))
            boxes = [b for _, b in items]
            rn = reg.get("row_names", [])
            names, counter = [], {}
            for r, b in items:
                counter[r] = counter.get(r, 0) + 1
                rname = rn[r] if r < len(rn) else f"row{r+1}"
                names.append(f"{rname}_{counter[r]:02d}")
            reg = dict(reg, names=names)
        elif method == "groups":
            items = slice_groups(alpha, rect, reg.get("row_bounds"), reg["group_bounds"], reg["cols"],
                                 reg.get("min_gap", 6), reg.get("min_band", 30))
            boxes = [it[3] for it in items]
            rn, gn = reg.get("row_names", []), reg.get("group_names", [])
            names = []
            for r, g, i, b in items:
                rname = rn[r] if r < len(rn) else f"row{r+1}"
                gname = gn[g] if g < len(gn) else f"anim{g+1}"
                names.append(f"{rname}_{gname}_f{i+1:02d}")
            reg = dict(reg, names=names)
            if rn and len(set(it[0] for it in items)) != len(rn):
                warnings.append(f"{sheet_id} region '{reg.get('label','?')}': {len(set(it[0] for it in items))} rows found, {len(rn)} row names")
        else:
            raise SystemExit(f"unknown method {method}")
        skip = set(reg.get("skip", []))
        boxes = [b for i, b in enumerate(boxes) if i not in skip]
        if reg.get("row_names") and method in ("cc", "rows"):
            _, rows_ = reading_order(boxes)
            rn = reg["row_names"]
            names = []
            for ri, row in enumerate(rows_):
                rname = rn[ri] if ri < len(rn) else f"row{ri+1}"
                names += [f"{rname}_{i+1:02d}" for i in range(len(row))]
            boxes = [b for row in rows_ for b in sorted(row, key=lambda b: b[0])]
            if len(rows_) != len(rn):
                warnings.append(f"{sheet_id} region '{reg.get('label','?')}': {len(rows_)} rows found, {len(rn)} row names")
            reg = dict(reg, names=names)
        # later regions must not re-extract this region's pixels
        alpha[rect[1]:rect[3], rect[0]:rect[2]] = 0
        names = reg.get("names")
        if names and len(names) != len(boxes):
            warnings.append(f"{sheet_id} region '{reg.get('label','?')}': {len(boxes)} boxes but {len(names)} names")
        for i, b in enumerate(boxes):
            if names and i < len(names):
                nm = names[i]
            else:
                pre = reg.get("prefix", "sprite")
                nm = f"{pre}{i+1:02d}" if pre.endswith("_f") else f"{pre.rstrip('_')}_{i+1:02d}"
            if isinstance(nm, dict):
                name, cat, tags, anim = nm["name"], nm.get("category", reg.get("category")), nm.get("tags", []), nm.get("anim")
            else:
                name, cat, tags, anim = nm, reg.get("category", "prop"), [], None
            # tags & anim derived from name pattern <base>_<anim>_fNN
            mm = re.match(r"^(.*)_([a-z0-9]+)_f(\d+)$", name)
            if anim is None and mm:
                anim = {"name": mm.group(2), "frame": int(mm.group(3))}
            tags = list(dict.fromkeys(list(reg.get("tags", [])) + tags + name.split("_")))
            sprites.append(dict(name=snake(name), box=b, category=cat, tags=tags, anim=anim,
                                out=OUT_DIR / sheet_id / f"{snake(name)}.png", src=rgba))

    # ---- write sprites
    seen = {}
    entries = []
    for s in sprites:
        x0, y0, x1, y1 = s["box"]
        crop = s["src"][y0:y1, x0:x1]
        out = np.zeros((y1-y0+2*PAD, x1-x0+2*PAD, 4), np.uint8)
        out[PAD:-PAD, PAD:-PAD] = crop
        name = s["name"]
        if name in seen:
            seen[name] += 1
            name = f"{name}_{seen[name]}"
            warnings.append(f"{sheet_id}: duplicate name {s['name']} -> {name}")
            s["out"] = s["out"].with_name(name + ".png")
        else:
            seen[name] = 1
        s["out"].parent.mkdir(parents=True, exist_ok=True)
        Image.fromarray(out).save(s["out"], optimize=True)
        data = s["out"].read_bytes()
        entries.append({
            "id": f"{sheet_id}/{name}",
            "file": str(s["out"].relative_to(ROOT)).replace(os.sep, "/"),
            "sheet": path.name,
            "bbox": [int(x0), int(y0), int(x1-x0), int(y1-y0)],
            "width": int(x1-x0+2*PAD), "height": int(y1-y0+2*PAD),
            "category": s["category"],
            "tags": s.get("tags", []),
            **({"anim": s["anim"]} if s.get("anim") else {}),
            "source": SOURCE, "license": LICENSE,
            "hash": hashlib.sha1(data).hexdigest(),
        })

    contact_sheet(sheet_id, sprites, CONTACT_DIR / f"{sheet_id}.png", indexed=False)
    if debug:
        contact_sheet(sheet_id, sprites, CONTACT_DIR / f"_debug_{sheet_id}.png", indexed=True)
        masked = rgba.copy()
        masked[..., 3] = np.where(alpha_vis > sheet_alpha_t, 255, 0)
        dbg = Image.new("RGBA", im.size, (30, 30, 40, 255))
        dbg.alpha_composite(Image.fromarray(masked))
        dbg = dbg.convert("RGB")
        d = ImageDraw.Draw(dbg)
        for b in removed:
            d.rectangle(b, outline=(255, 0, 0), width=2)
        for i, s in enumerate(sprites):
            d.rectangle(s["box"], outline=(0, 255, 0), width=2)
            d.text((s["box"][0], s["box"][1]), str(i), fill=(255, 255, 0), font=font(14))
        dbg.save(CONTACT_DIR / f"_debug_boxes_{sheet_id}.png")
    report[sheet_id] = dict(sheet=path.name, count=len(entries), removed=len(removed), warnings=warnings)
    for wmsg in warnings:
        print("WARN", wmsg)
    print(f"{sheet_id}: {len(entries)} sprites, {len(removed)} labels removed")
    return entries


def contact_sheet(sheet_id, sprites, out, indexed):
    if not sprites:
        return
    cell = 150
    cols = 8
    rows = (len(sprites) + cols - 1) // cols
    img = Image.new("RGB", (cols * cell, rows * (cell + 30) + 30), (54, 58, 80))
    d = ImageDraw.Draw(img)
    f = font(11)
    d.text((6, 6), f"{sheet_id} - {len(sprites)} sprites", fill=(255, 255, 255), font=font(14))
    for i, s in enumerate(sprites):
        x0, y0, x1, y1 = s["box"]
        sp = Image.fromarray(s["src"][y0:y1, x0:x1])
        sp.thumbnail((cell - 8, cell - 8))
        cx, cy = (i % cols) * cell, 30 + (i // cols) * (cell + 30)
        bg = Image.new("RGBA", sp.size, (80, 84, 110, 255))
        bg.alpha_composite(sp)
        img.paste(bg.convert("RGB"), (cx + (cell - sp.width) // 2, cy + (cell - sp.height) // 2))
        label = f"{i}:{s['name']}" if indexed else s["name"]
        d.text((cx + 3, cy + cell + 2), label[:26], fill=(255, 230, 170), font=f)
        if len(label) > 26:
            d.text((cx + 3, cy + cell + 15), label[26:52], fill=(255, 230, 170), font=f)
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--debug", action="store_true")
    ap.add_argument("--only", nargs="*", default=None, help="sheet numbers, e.g. 07 15")
    args = ap.parse_args()
    cfg = json.loads(CONFIG.read_text())
    sheets = sorted(SHEET_DIR.glob("*.png"))
    if args.only:
        sheets = [s for s in sheets if s.name[5:7] in args.only]
    manifest = []
    report = {}
    for p in sheets:
        sid = p.name[:7]
        scfg = cfg["sheets"].get(sid, {"regions": [{"method": "cc"}]})
        # clean old outputs for the sheet
        od = OUT_DIR / sid
        if od.exists():
            for f in od.glob("*.png"):
                f.unlink()
        manifest.extend(process_sheet(p, scfg, args.debug, report))
    if not args.only:
        MANIFEST.parent.mkdir(parents=True, exist_ok=True)
        MANIFEST.write_text(json.dumps(manifest, indent=1))
        (CONTACT_DIR / "_report.json").write_text(json.dumps(report, indent=1))
        print(f"manifest: {len(manifest)} sprites -> {MANIFEST}")
    else:
        print("(partial run: manifest not written)")


if __name__ == "__main__":
    main()
