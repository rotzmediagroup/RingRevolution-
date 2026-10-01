#!/usr/bin/env python3
"""Dumpling Rings - premium ring set (thin torus in a material) via the generate_3d.py pipeline.

Per ring:
  1. reference image (image API, klein) with a THIN-TORUS prompt; the hole is measured on the
     image (white fraction across the centre row) and the image is regenerated with a new seed /
     stronger prompt until the hole is wide enough.
  2. Meshy Image-to-3D (meshy-6-lite, triangle, 30k target polycount, remesh, symmetry auto)
  3. Meshy Retexture (enable_pbr, 2k), guided by the reference image (text fallback)
  4. download GLB + PBR textures, inspect, fit a torus (outer radius / tube thickness) and reject
     meshes with thickness / outer_radius > 0.3.

Records everything under manifest["rings"][name]. Re-runnable (cached task ids / files).

    export IMAGE_API_KEY=... MESHY_API_KEY=...   # never commit
    python3 tools/asset_generate/generate_rings.py --only ring_gold ring_silver --jobs 3
    python3 tools/asset_generate/generate_rings.py --measure      # only re-measure existing GLBs
"""
import argparse
import datetime as dt
import json
import math
import os
import struct
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import requests
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_3d as g  # noqa: E402  (reuse the proven pipeline helpers)
from glb_inspect import inspect_glb, parse_glb, COMPONENT_SIZE, TYPE_COUNT  # noqa: E402

ROOT, OUT, log = g.ROOT, g.OUT, g.log

SHAPE = (
    "a thin smooth circular ring (torus) like a bracelet, the hole is wide: tube thickness about one sixth "
    "of the ring radius, perfectly round, lying flat on a plain pure white background seen from a "
    "three-quarter top view, product photography, studio lighting, high detail, no text"
)
SHAPE_STRONG = (
    "a very thin delicate circular bangle bracelet ring (slim torus) with a very large open hole, the tube is "
    "slim like a wire: tube thickness about one eighth of the ring radius, perfectly round, lying flat on a plain "
    "pure white background seen from a three-quarter top view, product photography, studio lighting, "
    "high detail, no text, nothing else in the image"
)
SHAPE_STONE = (
    "a slim thin round bangle ring, a thin torus with a very wide open hole, the tube is slim like a thick wire: "
    "tube thickness about one eighth of the ring radius, uniform round tube, perfectly circular, lying flat "
    "on a plain pure white background seen from above at a slight angle, product photography, studio lighting, "
    "high detail, no text, nothing else in the image"
)
NEG = ", no face, no decoration other than the surface material, single object only, no shadow, no other objects"

RINGS = [
    {"name": "ring_gold", "material": "made of polished 24k gold with a subtle engraved cloud pattern on the surface",
     "texture": "polished reflective 24k yellow gold metal with subtle engraved cloud pattern"},
    {"name": "ring_silver", "material": "made of brushed sterling silver with a polished mirror rim",
     "texture": "brushed sterling silver metal with fine linear brush marks and a polished mirror rim"},
    {"name": "ring_rose_gold", "material": "made of polished rose gold",
     "texture": "polished reflective rose gold metal, warm pink-copper tint"},
    {"name": "ring_onyx", "material": "made of polished black onyx stone with a thin inlaid gold line running around it", "stone": True,
     "texture": "polished glossy black onyx stone with a thin inlaid gold line"},
    {"name": "ring_jade", "material": "made of translucent green jade stone with a fine shallow carved line pattern on the surface", "stone": True,
     "texture": "translucent green jade stone with subtle veining and fine carved relief"},
    {"name": "ring_marble", "material": "made of polished white marble with grey veins", "stone": True,
     "texture": "polished white marble with soft grey veins"},
    {"name": "ring_bronze", "material": "made of antique bronze with green patina highlights", "stone": True,
     "texture": "antique dark bronze metal with green-blue patina in the recesses"},
    {"name": "ring_obsidian", "material": "made of glossy black volcanic obsidian glass", "stone": True,
     "texture": "glossy jet-black volcanic obsidian glass with faint sharp reflections"},
    {"name": "ring_pearl", "material": "made of iridescent pearl mother-of-pearl ceramic", "stone": True,
     "texture": "iridescent white mother-of-pearl ceramic with soft rainbow sheen"},
    {"name": "ring_lacquer", "material": "made of glossy deep red urushi lacquer sprinkled with gold flakes", "stone": True,
     "texture": "glossy deep red urushi lacquer with scattered gold leaf flakes"},
]

POLYCOUNT = 30000
MAX_RATIO = 0.30
MIN_HOLE = 0.60


# ----------------------------------------------------------------------------- manifest (rings key)
def update_ring(m, name, **fields):
    with g.LOCK:
        m.setdefault("rings", {}).setdefault(name, {"name": name}).update(fields)
    g.save_manifest(m)


# ----------------------------------------------------------------------------- reference + hole check
def hole_ratio(path):
    """Fraction of the object's horizontal extent (through its centre) that is background (hole)."""
    im = Image.open(path).convert("L")
    w, h = im.size
    px = im.load()
    # background-adaptive threshold: the backdrop is "white" but often slightly grey
    corners = sorted(px[x, y] for x in (8, w - 9) for y in (8, h - 9))
    thr = min(235, corners[1] - 18)
    xs, ys = [], []
    for y in range(0, h, 2):
        for x in range(0, w, 2):
            if px[x, y] < thr:
                xs.append(x)
                ys.append(y)
    if not xs:
        return 0.0, "empty image"
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    best = 0.0
    for dy in (-h // 40, 0, h // 40):   # a few rows around the centre
        row = [px[x, cy + dy] for x in range(x0, x1 + 1)]
        # longest run of background inside the object's span
        run = longest = 0
        for v in row:
            run = run + 1 if v >= thr else 0
            longest = max(longest, run)
        best = max(best, longest / max(1, x1 - x0))
    # also vertical span through the centre column (three-quarter view -> ellipse)
    col = [px[cx, y] for y in range(y0, y1 + 1)]
    run = longest = 0
    for v in col:
        run = run + 1 if v >= thr else 0
        longest = max(longest, run)
    vert = longest / max(1, y1 - y0)
    return round(best, 3), f"hole horizontal {best:.2f}, vertical {vert:.2f}, object bbox {x1 - x0}x{y1 - y0}"


def gen_image(prompt, path, seed=None):
    key = os.environ.get("IMAGE_API_KEY")
    if not key:
        sys.exit("IMAGE_API_KEY not set")
    body = {"model": "klein", "prompt": prompt, "size": "1024x1024", "n": 1, "response_format": "b64_json"}
    if seed is not None:
        body["seed"] = seed
    for attempt in range(3):
        r = requests.post(g.IMAGE_API_URL, json=body, headers={"Authorization": f"Bearer {key}"}, timeout=600)
        if r.ok:
            break
        log(f"image API {r.status_code} {r.text[:200]} (attempt {attempt + 1})")
        time.sleep(10)
    r.raise_for_status()
    import base64
    path.write_bytes(base64.b64decode(r.json()["data"][0]["b64_json"]))
    return path


def gen_reference(ring, outdir, m, strong=False, force=False):
    name = ring["name"]
    path = outdir / "reference.png"
    rec = m.get("rings", {}).get(name, {})
    if path.exists() and not force and rec.get("reference_ok"):
        return path, rec["prompt"]
    attempts = rec.get("reference_attempts", [])
    for i in range(8):
        shape = SHAPE_STRONG if (strong or i >= 2) else (SHAPE_STONE if ring.get('stone') else SHAPE)
        prompt = f"{shape}, {ring['material']}{NEG}"
        seed = 1000 + 37 * len(attempts)
        gen_image(prompt, path, seed)
        ratio, note = hole_ratio(path)
        keep = outdir / f"reference_try{len(attempts)}.png"
        path.replace(keep) if ratio < MIN_HOLE else None
        attempts.append({"seed": seed, "prompt": prompt, "hole_ratio": ratio, "note": note,
                         "file": str((keep if ratio < MIN_HOLE else path).relative_to(ROOT))})
        update_ring(m, name, reference_attempts=attempts)
        log(f"{name}: reference try {len(attempts)} hole={ratio} ({note})")
        if ratio >= MIN_HOLE:
            update_ring(m, name, reference_image=str(path.relative_to(ROOT)), prompt=prompt,
                        reference_seed=seed, reference_hole_ratio=ratio, reference_ok=True)
            return path, prompt
    raise RuntimeError(f"{name}: could not get a thin-ring reference after {len(attempts)} tries")


# ----------------------------------------------------------------------------- torus fit
def read_positions(path):
    gltf, bin_chunk = parse_glb(path)
    acc = gltf["accessors"]
    views = gltf["bufferViews"]
    pts = []
    for mesh in gltf.get("meshes", []):
        for prim in mesh.get("primitives", []):
            a = acc[prim["attributes"]["POSITION"]]
            bv = views[a["bufferView"]]
            base = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
            stride = bv.get("byteStride") or COMPONENT_SIZE[a["componentType"]] * TYPE_COUNT[a["type"]]
            for i in range(a["count"]):
                pts.append(struct.unpack_from("<fff", bin_chunk, base + i * stride))
    return pts


def torus_fit(path):
    """Fit a torus: plane normal = axis of smallest extent (bbox), measure outer radius, inner radius,
    tube thickness (normal extent) and the key ratio thickness / outer_radius."""
    pts = read_positions(path)
    n = len(pts)
    c = [sum(p[k] for p in pts) / n for k in range(3)]
    ext = [max(p[k] for p in pts) - min(p[k] for p in pts) for k in range(3)]
    axis = ext.index(min(ext))
    plane = [k for k in range(3) if k != axis]
    radial = [math.hypot(p[plane[0]] - c[plane[0]], p[plane[1]] - c[plane[1]]) for p in pts]
    outer = max(radial)
    inner = min(radial)
    thickness = ext[axis]
    radial_thickness = outer - inner
    return {
        "axis": "xyz"[axis], "outer_radius": round(outer, 4), "inner_radius": round(inner, 4),
        "tube_thickness_normal": round(thickness, 4), "tube_thickness_radial": round(radial_thickness, 4),
        "ratio_thickness_over_outer": round(thickness / outer, 3),
        "ratio_radial_thickness_over_outer": round(radial_thickness / outer, 3),
        "hole_fraction": round(inner / outer, 3),
        "is_thin_torus": thickness / outer <= MAX_RATIO and inner > 0.3 * outer,
    }


# ----------------------------------------------------------------------------- per ring
def run_ring(ring, m, force=False):
    name = ring["name"]
    outdir = OUT / name
    outdir.mkdir(parents=True, exist_ok=True)
    rec = m.get("rings", {}).get(name, {})
    credits = rec.get("credits_used", 0)
    try:
        strong = rec.get("status") == "REJECTED_FAT"
        ref, prompt = gen_reference(ring, outdir, m, strong=strong, force=force or strong)
        update_ring(m, name, status="reference_done")

        i23d_id = rec.get("meshy_image_to_3d_task") if not strong else None
        if not i23d_id or force:
            before = g.meshy_balance()
            payload = {
                "image_url": g.data_uri(ref), "model_type": "standard", "ai_model": "meshy-6-lite",
                "should_texture": False, "should_remesh": True, "topology": "triangle",
                "target_polycount": POLYCOUNT, "symmetry_mode": "auto", "target_formats": ["glb"],
            }
            i23d_id = g.meshy_create("image-to-3d", payload)
            after = g.meshy_balance()
            credits += before - after
            update_ring(m, name, meshy_image_to_3d_task=i23d_id, credits_used=credits,
                        image_to_3d_request={k: v for k, v in payload.items() if k != "image_url"},
                        status="image_to_3d_running")
            log(f"{name}: image-to-3d {i23d_id} ({before - after} credits)")
        task = g.meshy_poll("image-to-3d", i23d_id, f"{name}/i23d")
        mesh_glb = outdir / f"{name}_mesh.glb"
        if not mesh_glb.exists() or force or strong:
            g.download(task["model_urls"]["glb"], mesh_glb)
        fit = torus_fit(mesh_glb)
        update_ring(m, name, status="image_to_3d_done", mesh_glb=str(mesh_glb.relative_to(ROOT)),
                    mesh_glb_stats=inspect_glb(mesh_glb), torus_fit=fit)
        log(f"{name}: mesh fit {fit}")
        if not fit["is_thin_torus"]:
            update_ring(m, name, status="REJECTED_FAT", credits_used=credits,
                        qa_note=f"mesh too fat (ratio {fit['ratio_thickness_over_outer']}), re-run with stronger prompt")
            log(f"{name}: REJECTED (fat torus) - rerun will use the strong prompt")
            return

        rt_id = rec.get("meshy_retexture_task") if not strong else None
        if not rt_id or force:
            before = g.meshy_balance()
            payload = {"input_task_id": i23d_id, "image_style_url": g.data_uri(ref), "ai_model": "latest",
                       "enable_original_uv": True, "enable_pbr": True, "texture_resolution": "2k",
                       "target_formats": ["glb"]}
            try:
                rt_id = g.meshy_create("retexture", payload)
            except RuntimeError as e:
                log(f"{name}: image-style retexture rejected ({e}); text style")
                payload.pop("image_style_url")
                payload["text_style_prompt"] = ring["texture"]
                rt_id = g.meshy_create("retexture", payload)
            after = g.meshy_balance()
            credits += before - after
            update_ring(m, name, meshy_retexture_task=rt_id, credits_used=credits,
                        retexture_request={k: v for k, v in payload.items() if k != "image_style_url"},
                        status="retexture_running")
            log(f"{name}: retexture {rt_id} ({before - after} credits)")
        task = g.meshy_poll("retexture", rt_id, f"{name}/retexture")

        glb = outdir / f"{name}.glb"
        g.download(task["model_urls"]["glb"], glb)
        textures = []
        for i, tex in enumerate(task.get("texture_urls") or []):
            for kind, url in tex.items():
                p = outdir / f"{name}_{kind}{'' if i == 0 else i}.png"
                g.download(url, p)
                textures.append(str(p.relative_to(ROOT)))
        thumb = None
        if task.get("thumbnail_url"):
            thumb = outdir / "thumbnail.png"
            g.download(task["thumbnail_url"], thumb)
        stats = inspect_glb(glb)
        fit = torus_fit(glb)
        update_ring(m, name, status="SUCCEEDED", glb=str(glb.relative_to(ROOT)), textures=textures,
                    thumbnail=str(thumb.relative_to(ROOT)) if thumb else None, credits_used=credits,
                    glb_stats=stats, torus_fit=fit, texture_prompt=ring["texture"],
                    qa_note=f"thin torus OK (thickness/outer {fit['ratio_thickness_over_outer']}, "
                            f"hole {fit['hole_fraction']}), {stats['triangles']} tris, {len(textures)} PBR maps",
                    finished_at=dt.datetime.now(dt.timezone.utc).isoformat())
        log(f"{name}: done {stats['triangles']} tris ratio {fit['ratio_thickness_over_outer']} {credits} credits")
    except Exception as e:
        log(f"{name}: FAILED {e}")
        update_ring(m, name, status="FAILED", error=str(e), credits_used=credits)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", nargs="*")
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--jobs", type=int, default=3)
    ap.add_argument("--measure", action="store_true")
    ap.add_argument("--reference-only", action="store_true")
    args = ap.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    m = g.load_manifest()
    m.setdefault("rings", {})
    rings = [r for r in RINGS if not args.only or r["name"] in args.only]
    if args.measure:
        for r in rings:
            for key in ("glb", "mesh_glb"):
                p = m["rings"].get(r["name"], {}).get(key)
                if p and (ROOT / p).exists():
                    print(r["name"], key, json.dumps(torus_fit(ROOT / p)))
        return
    if args.reference_only:
        for r in rings:
            (OUT / r["name"]).mkdir(parents=True, exist_ok=True)
            gen_reference(r, OUT / r["name"], m, force=args.force)
        return
    start = g.meshy_balance()
    log(f"Meshy balance before: {start}")
    run = {"started_at": dt.datetime.now(dt.timezone.utc).isoformat(), "balance_before": start,
           "rings": [r["name"] for r in rings]}
    with ThreadPoolExecutor(max_workers=args.jobs) as ex:
        list(ex.map(lambda r: run_ring(r, m, args.force), rings))
    run["balance_after"] = g.meshy_balance()
    run["finished_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
    m.setdefault("runs", []).append(run)
    g.save_manifest(m)
    log(f"Meshy balance after: {run['balance_after']} (spent {start - run['balance_after']})")


if __name__ == "__main__":
    main()
