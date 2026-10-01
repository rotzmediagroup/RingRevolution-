#!/usr/bin/env python3
"""Dumpling Rings - 3D prop pipeline (reference image -> Meshy image-to-3D -> Meshy retexture).

Mandatory workflow per prop:
  1. reference image via the owner's image API (klein, 1024x1024, white background)
  2. Meshy Image-to-3D (POST /openapi/v1/image-to-3d, mesh only, data-URI image)
  3. Meshy Retexture (POST /openapi/v1/retexture, image_style_url = reference, enable_pbr)
  4. download textured GLB + PBR textures + thumbnail

Re-runnable: each step's output is cached on disk under assets/generated/3d/<name>/ and
in assets/generated/3d/manifest.json, so an interrupted run resumes without re-spending credits.

Usage:
    export IMAGE_API_KEY=...   # never commit
    export MESHY_API_KEY=...   # never commit
    python3 tools/asset_generate/generate_3d.py            # all props
    python3 tools/asset_generate/generate_3d.py --only dumpling --jobs 3
    python3 tools/asset_generate/generate_3d.py --inspect   # only re-run GLB stats
"""
import argparse
import base64
import datetime as dt
import json
import os
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import requests

from glb_inspect import inspect_glb

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "assets" / "generated" / "3d"
MANIFEST = OUT / "manifest.json"
IMAGE_API_URL = os.environ.get("IMAGE_API_URL", "https://llm.rotz.ai/v1/images/generations")
MESHY_BASE = os.environ.get("MESHY_API_URL", "https://api.meshy.ai")
POLL_SECONDS = 15
LOCK = threading.Lock()

STYLE = (
    "hand-painted cozy mobile game prop, chibi kawaii proportions, smooth painterly shading, "
    "warm soft colors, clean readable silhouette, single object only, centered, three-quarter view, "
    "plain pure white background, soft even studio light, no shadow on ground, no text, no letters, "
    "no watermark, no logo"
)

PROPS = [
    {
        "name": "chopstick",
        "subject": "a single lacquered wooden chopstick, reddish-brown polished wood with a thin gold band "
                   "near the thick end, perfectly straight, long and thin, lying diagonally, full length visible",
        "texture": "glossy reddish-brown lacquered wood with a thin gold metal band, subtle wood grain",
        "ai_model": "meshy-7.1", "polycount": 2000,
    },
    {
        "name": "lantern_gate",
        "subject": "a small round red paper lantern with gold rims and a tiny gold tassel, mounted on top of a short "
                   "stubby wooden peg post, standing upright",
        "texture": "warm red paper lantern softly glowing from inside, gold trim, honey-colored wood peg",
        "ai_model": "meshy-7.1", "polycount": 5000,
    },
    {
        "name": "lantern_arm",
        "subject": "a slim long straight bamboo rod with a small glowing round paper lantern hanging at one end, "
                   "lantern warm orange glow, rod held diagonally",
        "texture": "light tan bamboo rod with dark nodes, glowing warm orange paper lantern with red rims",
        "ai_model": "meshy-7.1", "polycount": 4000,
    },
    {
        "name": "bamboo_basket",
        "subject": "a round woven bamboo steamer basket with its woven lid tilted slightly open, light tan bamboo, "
                   "cozy cute proportions",
        "texture": "natural light tan woven bamboo strips, tight weave, slightly darker rim bands",
        "ai_model": "meshy-7.1", "polycount": 8000,
    },
    {
        "name": "dumpling",
        "subject": "a cute smiling steamed bao dumpling character, round soft puffy white dough with pleated swirl on top, "
                   "tiny chibi face with closed happy eyes, rosy blush cheeks",
        "texture": "soft matte white steamed bun dough, pale cream shading in creases, tiny chibi face with rosy pink blush",
        "ai_model": "meshy-7.1", "polycount": 6000,
    },
    {
        "name": "ring_sesame",
        "subject": "a plain smooth torus ring shaped like a thin donut, pale golden steamed dough dusted with black and "
                   "white sesame seeds, thin tube and large hole, ratio of ring radius to tube radius about six to one, "
                   "perfectly symmetric, no face, no decoration",
        "texture": "pale golden matte steamed bread dough with scattered black and white sesame seeds",
        "ai_model": "meshy-6-lite", "polycount": 3000,
    },
]


def log(msg):
    with LOCK:
        print(f"[{dt.datetime.now().strftime('%H:%M:%S')}] {msg}", flush=True)


def meshy_headers():
    key = os.environ.get("MESHY_API_KEY")
    if not key:
        sys.exit("MESHY_API_KEY not set")
    return {"Authorization": f"Bearer {key}"}


def meshy_balance():
    r = requests.get(f"{MESHY_BASE}/openapi/v1/balance", headers=meshy_headers(), timeout=60)
    r.raise_for_status()
    return r.json()["balance"]


# ----------------------------------------------------------------------------- manifest
def load_manifest():
    if MANIFEST.exists():
        return json.loads(MANIFEST.read_text())
    return {"props": {}, "runs": []}


def save_manifest(m):
    with LOCK:
        MANIFEST.write_text(json.dumps(m, indent=2))


def update_prop(m, name, **fields):
    with LOCK:
        m["props"].setdefault(name, {"name": name}).update(fields)
    save_manifest(m)


# ----------------------------------------------------------------------------- step 1: reference
def gen_reference(prop, outdir, force=False):
    path = outdir / "reference.png"
    prompt = f"{prop['subject']}, {STYLE}"
    if path.exists() and not force:
        return path, prompt
    key = os.environ.get("IMAGE_API_KEY")
    if not key:
        sys.exit("IMAGE_API_KEY not set")
    body = {"model": "klein", "prompt": prompt, "size": "1024x1024", "n": 1, "response_format": "b64_json"}
    for attempt in range(3):
        r = requests.post(IMAGE_API_URL, json=body, headers={"Authorization": f"Bearer {key}"}, timeout=600)
        if r.ok:
            break
        log(f"{prop['name']}: image API {r.status_code} {r.text[:200]} (attempt {attempt + 1})")
        time.sleep(10)
    r.raise_for_status()
    data = r.json()["data"][0]["b64_json"]
    path.write_bytes(base64.b64decode(data))
    log(f"{prop['name']}: reference image written {path.relative_to(ROOT)}")
    return path, prompt


def data_uri(path):
    mime = "image/png" if path.suffix.lower() == ".png" else "image/jpeg"
    return f"data:{mime};base64,{base64.b64encode(path.read_bytes()).decode()}"


# ----------------------------------------------------------------------------- Meshy tasks
def meshy_create(endpoint, payload):
    r = requests.post(f"{MESHY_BASE}/openapi/v1/{endpoint}", json=payload, headers=meshy_headers(), timeout=120)
    if not r.ok:
        raise RuntimeError(f"Meshy {endpoint} create failed {r.status_code}: {r.text[:500]}")
    return r.json()["result"]


def meshy_poll(endpoint, task_id, label):
    while True:
        r = requests.get(f"{MESHY_BASE}/openapi/v1/{endpoint}/{task_id}", headers=meshy_headers(), timeout=60)
        if r.status_code >= 500:
            log(f"{label}: poll {r.status_code}, retrying")
            time.sleep(POLL_SECONDS)
            continue
        r.raise_for_status()
        task = r.json()
        status = task.get("status")
        if status == "SUCCEEDED":
            return task
        if status in ("FAILED", "CANCELED", "EXPIRED"):
            raise RuntimeError(f"{label}: task {task_id} {status}: {task.get('task_error')}")
        log(f"{label}: {status} {task.get('progress', 0)}%")
        time.sleep(POLL_SECONDS)


def download(url, path):
    with requests.get(url, stream=True, timeout=600) as r:
        r.raise_for_status()
        with open(path, "wb") as f:
            for chunk in r.iter_content(1 << 16):
                f.write(chunk)
    return path


# ----------------------------------------------------------------------------- per prop
def run_prop(prop, m, force=False):
    name = prop["name"]
    outdir = OUT / name
    outdir.mkdir(parents=True, exist_ok=True)
    rec = m["props"].get(name, {})
    credits = rec.get("credits_used", 0)
    try:
        # 1. reference image
        ref, prompt = gen_reference(prop, outdir, force)
        update_prop(m, name, reference_image=str(ref.relative_to(ROOT)), prompt=prompt,
                    status="reference_done")

        # 2. image-to-3D (mesh only; texturing is a separate Meshy step below)
        i23d_id = rec.get("meshy_image_to_3d_task")
        if not i23d_id or force:
            before = meshy_balance()
            payload = {
                "image_url": data_uri(ref),
                "model_type": "standard",
                "ai_model": prop["ai_model"],
                "should_texture": False,
                "should_remesh": True,
                "topology": "triangle",
                "target_polycount": prop["polycount"],
                "symmetry_mode": "auto",
                "target_formats": ["glb"],
            }
            i23d_id = meshy_create("image-to-3d", payload)
            after = meshy_balance()
            credits += before - after
            update_prop(m, name, meshy_image_to_3d_task=i23d_id, credits_used=credits,
                        image_to_3d_request={k: v for k, v in payload.items() if k != "image_url"},
                        status="image_to_3d_running")
            log(f"{name}: image-to-3d task {i23d_id} ({before - after} credits)")
        task = meshy_poll("image-to-3d", i23d_id, f"{name}/i23d")
        mesh_glb = outdir / f"{name}_mesh.glb"
        if not mesh_glb.exists() or force:
            download(task["model_urls"]["glb"], mesh_glb)
        update_prop(m, name, status="image_to_3d_done", mesh_glb=str(mesh_glb.relative_to(ROOT)))

        # 3. retexture with PBR (texture/refine step), guided by the reference image
        rt_id = rec.get("meshy_retexture_task")
        if not rt_id or force:
            before = meshy_balance()
            payload = {
                "input_task_id": i23d_id,
                "image_style_url": data_uri(ref),
                "ai_model": "latest",
                "enable_original_uv": True,
                "enable_pbr": True,
                "texture_resolution": "2k",
                "target_formats": ["glb"],
            }
            try:
                rt_id = meshy_create("retexture", payload)
            except RuntimeError as e:
                # fall back to text style prompt if the image style is rejected
                log(f"{name}: retexture with image style rejected ({e}); retrying with text style")
                payload.pop("image_style_url")
                payload["text_style_prompt"] = prop["texture"]
                rt_id = meshy_create("retexture", payload)
            after = meshy_balance()
            credits += before - after
            update_prop(m, name, meshy_retexture_task=rt_id, credits_used=credits,
                        retexture_request={k: v for k, v in payload.items() if k != "image_style_url"},
                        status="retexture_running")
            log(f"{name}: retexture task {rt_id} ({before - after} credits)")
        task = meshy_poll("retexture", rt_id, f"{name}/retexture")

        # 4. download textured GLB + textures
        glb = outdir / f"{name}.glb"
        download(task["model_urls"]["glb"], glb)
        textures = []
        for i, tex in enumerate(task.get("texture_urls") or []):
            for kind, url in tex.items():
                p = outdir / f"{name}_{kind}{'' if i == 0 else i}.png"
                download(url, p)
                textures.append(str(p.relative_to(ROOT)))
        thumb = None
        if task.get("thumbnail_url"):
            thumb = outdir / "thumbnail.png"
            download(task["thumbnail_url"], thumb)
        stats = inspect_glb(glb)
        update_prop(m, name, status="SUCCEEDED", glb=str(glb.relative_to(ROOT)), textures=textures,
                    thumbnail=str(thumb.relative_to(ROOT)) if thumb else None, credits_used=credits,
                    glb_stats=stats, finished_at=dt.datetime.now(dt.timezone.utc).isoformat())
        log(f"{name}: done, {stats['triangles']} tris, {len(textures)} textures, {credits} credits")
    except Exception as e:  # record the failure, keep going with other props
        log(f"{name}: FAILED {e}")
        update_prop(m, name, status="FAILED", error=str(e), credits_used=credits)


def inspect_all(m):
    for name, rec in m["props"].items():
        for key in ("glb", "mesh_glb"):
            p = rec.get(key)
            if p and (ROOT / p).exists():
                rec["glb_stats" if key == "glb" else "mesh_glb_stats"] = inspect_glb(ROOT / p)
    save_manifest(m)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", action="append")
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--jobs", type=int, default=3)
    ap.add_argument("--inspect", action="store_true", help="only recompute GLB stats")
    args = ap.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    m = load_manifest()
    if args.inspect:
        inspect_all(m)
        for n, r in m["props"].items():
            print(n, json.dumps(r.get("glb_stats")))
        return
    props = [p for p in PROPS if not args.only or p["name"] in args.only]
    start_balance = meshy_balance()
    log(f"Meshy balance before: {start_balance}")
    run = {"started_at": dt.datetime.now(dt.timezone.utc).isoformat(), "balance_before": start_balance,
           "props": [p["name"] for p in props]}
    with ThreadPoolExecutor(max_workers=args.jobs) as ex:
        list(ex.map(lambda p: run_prop(p, m, args.force), props))
    inspect_all(m)
    run["balance_after"] = meshy_balance()
    run["finished_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
    m["runs"].append(run)
    save_manifest(m)
    log(f"Meshy balance after: {run['balance_after']} (spent {start_balance - run['balance_after']})")


if __name__ == "__main__":
    main()
