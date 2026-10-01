#!/usr/bin/env python3
"""Dumpling Rings - generated art pipeline.

Re-runnable: jobs whose output file already exists are skipped (use --force or
--only <id> to regenerate). Every job is recorded in assets/generated/manifest.json.

Usage:
    export IMAGE_API_KEY=...        # never commit the key
    python3 tools/asset_generate/generate_art.py            # run all pending jobs
    python3 tools/asset_generate/generate_art.py --only world1_teahouse_bg --seed 42 --force
    python3 tools/asset_generate/generate_art.py --group A  # only group A jobs
    python3 tools/asset_generate/generate_art.py --list
"""
import argparse
import base64
import datetime as dt
import json
import os
import sys
import time
from pathlib import Path

import requests
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "assets" / "generated"
MANIFEST = OUT / "manifest.json"
API_URL = os.environ.get("IMAGE_API_URL", "https://llm.rotz.ai/v1/images/generations")

STYLE = (
    "hand-painted cozy mobile game illustration, warm soft cinematic lighting, gentle bloom, "
    "subtle depth of field, chibi kawaii proportions, smooth painterly shading, rich saturated but "
    "soft colors, high detail, no text, no watermark, no letters, no logo, no humans, no people, "
    "no signboards with writing, plain blank lanterns"
)
NEGATIVE = (
    "text, letters, watermark, signature, photo-realistic, 3D render look, harsh black outlines, "
    "grid, checkerboard, clutter, dark gritty, horror, humans"
)

W1 = ("honey wood, bamboo green, cream, sakura pink and morning gold palette, cherry blossoms, "
      "bamboo steamers with soft steam, teacups, unlit paper lanterns, morning sun")
W2 = ("indigo dusk, amber lantern glow, red, gold and soft mist palette, glowing paper lanterns, "
      "night market food stalls, warm reflections, bokeh")
W3 = ("moon blue, jade green, lavender, snow white and gold palette, mountain temple kitchen, "
      "moonlight, soft clouds, jade ornaments, winter blossoms, magical glowing steam")

BG_RULE = ("wide empty plain wooden table surface filling the center of the frame, completely clear "
           "and uncluttered center, soft low-contrast muted center, decorative elements only along the "
           "edges and corners, no objects in the middle, no characters, ")

CHAPTERS = [
    (1, 1, "Welcome, First Steam", "a cute smiling bao dumpling character lifting the lid of a bamboo steamer on a teahouse veranda, first puff of soft steam, morning sun"),
    (2, 1, "Bamboo Garden", "cute bao dumpling and a chibi tanuki in a bamboo garden with stone lanterns and a koi pond, dappled morning light"),
    (3, 1, "Tea Set", "chibi rabbit in a kimono pouring tea from a green floral teapot for a cute dumpling character, tea set on a low wooden table, sakura branch"),
    (4, 1, "Blossom Festival", "cherry blossom festival at a teahouse, cute dumpling characters and a chibi fox in pink kimono under falling petals, unlit paper lanterns"),
    (5, 1, "Master of the Teahouse", "elegant chibi crane bird animal character wearing a kimono, presenting a tower of bamboo steamers to a crowd of happy cute dumplings, teahouse interior, golden morning light"),
    (6, 2, "First Lanterns", "cute bao dumpling character hanging the first glowing paper lantern at dusk in an empty quiet night market street, indigo sky, amber glow"),
    (7, 2, "Colourful Stalls", "row of colourful night market food stalls with red and blue awnings, cute dumpling characters and a chibi red panda animal shopping, blank awnings with no writing, glowing lanterns"),
    (8, 2, "Busy Alleys", "busy lantern-lit night market alley, crowd made only of cute dumpling characters and chibi cat and panda animals carrying food, blank lanterns, bokeh lights, mist"),
    (9, 2, "Festival Night", "night market festival with hanging lantern strings, drums and fireworks, cute dumpling characters dancing with a chibi fox animal, crowd of only dumplings and animals, deep indigo sky"),
    (10, 2, "Chef of the Market", "proud chibi dragon chef in an apron at a glowing night market stall serving steaming dumplings to a crowd of cute dumpling characters, amber and gold"),
    (11, 3, "Mountain Path", "cute bao dumpling character and a chibi rabbit walking a winding moonlit mountain path with stone steps, soft clouds, jade pines, lavender sky"),
    (12, 3, "Jade Kitchen", "mountain temple kitchen with jade ornaments, only cute dumpling characters and a chibi cat animal cooking with glowing magical steam, moonlight through the window"),
    (13, 3, "Moon Bridge", "arched stone bridge under a huge full moon above clouds, cute dumpling characters and a chibi cloud dragon crossing, snow white and jade"),
    (14, 3, "Star Steam", "cute dumpling characters releasing sparkling star-filled magical steam into a night sky from a bamboo steamer on an open mountain peak under the stars, lavender and gold"),
    (15, 3, "The Great Dumpling Feast", "grand moonlit feast on a mountain temple terrace, long table full of dumplings and tea, all the chibi animal friends and cute dumpling characters celebrating, jade and gold lanterns"),
]

MATERIALS = [
    ("mat_sesame_dough", "flat continuous pale golden steamed bread dough surface, smooth matte crust with scattered black and white sesame seeds, one single surface, no individual buns, no circles, close-up macro of one flat crust"),
    ("mat_matcha", "fine matcha green tea powder dusted surface, soft velvety mint-green, subtle grain"),
    ("mat_beet_pink", "flat continuous smooth soft beet pink dough surface, gentle soft creases and light flour dust, one single surface, no individual dumplings"),
    ("mat_ube_purple", "smooth soft ube purple yam dough surface, subtle swirl, gentle lavender highlights"),
    ("mat_gold", "polished warm gold metal surface with faint flowing embossed cloud swirls, continuous brushed metal, even lighting across the whole image, no tiles, no grid, no panels, no seams"),
    ("mat_bamboo", "woven bamboo basket strips, natural light tan bamboo, tight weave"),
]


def job(id, file, prompt, size, group, world=0, model="klein", seed=None, post=None, negative=NEGATIVE):
    return dict(id=id, file=file, prompt=prompt, size=size, group=group, world=world,
                model=model, seed=seed, post=post, negative=negative)


def build_jobs():
    jobs = []
    # A. gameplay backgrounds
    bgs = [
        ("world1_teahouse", 1, "cozy blossom teahouse veranda background, " + BG_RULE + W1),
        ("world2_nightmarket", 2, "lantern night market street background, " + BG_RULE + W2),
        ("world3_mountain", 3, "moonlit mountain temple kitchen background, " + BG_RULE + W3),
    ]
    for name, w, p in bgs:
        jobs.append(job(f"{name}_bg", f"backgrounds/{name}_bg.png", p + ", portrait composition, " + STYLE, "1024x1536", "A", w))
        jobs.append(job(f"{name}_bg_land", f"backgrounds/{name}_bg_land.png", p + ", wide landscape composition, " + STYLE, "1536x1024", "A", w))
    # B. world maps
    maps = [
        ("world1_map", 1, "tall illustrated level-select map painting of a blossom teahouse garden village seen from above at a gentle angle, a winding empty dirt path snaking from bottom to top through cherry blossom trees, teahouses, bamboo groves and koi ponds, cute tiny props, path completely empty with no markers, no numbers, no signs, " + W1),
        ("world2_map", 2, "tall illustrated level-select map painting of a lantern night market town seen from above at a gentle angle, a winding empty cobblestone path snaking from bottom to top between glowing food stalls, lantern strings, red gates and bridges, plain blank lanterns without symbols, no people, flat painted ground only, " + W2),
        ("world3_map", 3, "tall illustrated level-select map painting of a moonlit mountain seen from above at a gentle angle, a winding empty stone path snaking from bottom to top past temple kitchens, jade pagodas, clouds and a moon bridge toward a peak, " + W3),
    ]
    for name, w, p in maps:
        jobs.append(job(name, f"map/{name}.png", p + ", " + STYLE, "1024x1536", "B", w))
    # C. menu key art
    jobs.append(job("menu_keyart", "menu/menu_keyart.png",
                    "cozy blossom teahouse interior scene, one cute smiling chibi bao dumpling character with rosy cheeks sitting on a wooden table beside a tall stack of bamboo steamers with soft steam, teacups, cherry blossom branch, warm morning sunlight, empty space at the top of the frame, " + W1 + ", " + STYLE,
                    "1024x1536", "C", 1))
    # E. materials
    for name, desc in MATERIALS:
        jobs.append(job(name, f"materials/{name}.png",
                        f"seamless tileable texture, flat lit, top-down, {desc}, uniform even lighting, no shadows, no vignette, repeating pattern, painterly, no text, no watermark",
                        "512x512", "E", 0, post="seamless"))
    # G. app icon
    jobs.append(job("app_icon_1024", "icon/app_icon_1024.png",
                    "app icon, a single cute smiling chibi bao dumpling with rosy cheeks sitting centered inside a glowing golden open ring, warm honey wood background, simple bold composition, centered, " + STYLE,
                    "1024x1024", "G", 0))
    # D. chapter scenes
    for n, w, title, desc in CHAPTERS:
        pal = {1: W1, 2: W2, 3: W3}[w]
        jobs.append(job(f"chapter_{n:02d}", f"scenes/chapter_{n:02d}.png",
                        f"{desc}, {pal}, {STYLE}", "1024x768", "D", w))
    # F. world intros
    intros = [
        (1, "wide establishing illustration of the blossom teahouse at sunrise, cute bao dumpling character waving from the veranda, blank hanging plaque, cherry blossom trees, " + W1),
        (2, "wide establishing illustration of the lantern night market entrance gate at dusk, cute bao dumpling character holding a glowing lantern, empty street with only cute dumpling characters, blank gate plaque, " + W2),
        (3, "wide establishing illustration of the moonlit mountain temple kitchen above the clouds, cute bao dumpling character on the moon bridge, " + W3),
    ]
    for w, p in intros:
        jobs.append(job(f"world_intro_{w}", f"scenes/world_intro_{w}.png", p + ", " + STYLE, "1024x768", "F", w))
    # H. UI ornaments on white
    ui = [
        ("ui_sign_board", "a single horizontal wooden sign board panel, rounded plank with carved golden trim and small cherry blossom corner ornaments, blank empty surface, centered, isolated on a flat pure white background", "1536x1024"),
        ("ui_button_plate", "a single round wooden button plate, polished honey wood disc with golden rim, blank center, centered, isolated on a flat pure white background", "1024x1024"),
        ("ui_bamboo_frame", "a single rectangular bamboo frame border made of tied bamboo poles with rope knots at the corners, empty hollow white center, centered, isolated on a flat pure white background", "1024x1024"),
    ]
    for name, p, size in ui:
        jobs.append(job(name, f"ui/{name}.png", p + ", game UI asset, " + STYLE, size, "H", 0, post="key_white"))
    return jobs


# ---------------------------------------------------------------- post-processing

def make_seamless(path: Path, blend=0.18):
    """Mirror-blend edges so the tile wraps. Offsets the image by half, then
    cross-fades the original over the seams with a smooth ramp."""
    im = Image.open(path).convert("RGB")
    w, h = im.size
    # roll by half so seams are in the middle
    rolled = Image.new("RGB", (w, h))
    rolled.paste(im.crop((w // 2, 0, w, h)), (0, 0))
    rolled.paste(im.crop((0, 0, w // 2, h)), (w // 2, 0))
    r2 = Image.new("RGB", (w, h))
    r2.paste(rolled.crop((0, h // 2, w, h)), (0, 0))
    r2.paste(rolled.crop((0, 0, w, h // 2)), (0, h // 2))
    # mask: 1 at center cross (seams), 0 elsewhere, smooth ramp
    bw, bh = int(w * blend), int(h * blend)
    mask = Image.new("L", (w, h), 0)
    px = mask.load()
    for y in range(h):
        dy = abs(y - h / 2)
        fy = max(0.0, 1 - dy / bh)
        for x in range(w):
            dx = abs(x - w / 2)
            fx = max(0.0, 1 - dx / bw)
            f = max(fx, fy)
            f = f * f * (3 - 2 * f)
            px[x, y] = int(255 * f)
    mask = mask.filter(ImageFilter.GaussianBlur(4))
    out = Image.composite(im, r2, mask)
    # roll back so the result keeps original layout (seams now at edges blended)
    out.save(path)


def key_white(path: Path, soft=28.0, hard=90.0):
    """White-background matting. Alpha ramps from distance-to-white, but only for
    near-white regions connected to the image border or the exact centre (flood fill),
    so interior white highlights of the ornament stay opaque. Writes <stem>_alpha.png."""
    from PIL import ImageDraw
    im = Image.open(path).convert("RGB")
    w, h = im.size
    src = im.load()
    dist = Image.new("L", (w, h))
    dp = dist.load()
    for y in range(h):
        for x in range(w):
            r, g, b = src[x, y]
            d = ((255 - r) ** 2 + (255 - g) ** 2 + (255 - b) ** 2) ** 0.5
            dp[x, y] = min(255, int(d))
    # region that may become transparent: near-white (d < hard) connected to border/centre
    region = dist.point(lambda v: 0 if v < hard else 255)  # 0 = candidate, 255 = solid
    seeds = [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1), (w // 2, 0), (w // 2, h - 1),
             (0, h // 2), (w - 1, h // 2), (w // 2, h // 2)]
    for sx, sy in seeds:
        if region.getpixel((sx, sy)) == 0:
            ImageDraw.floodfill(region, (sx, sy), 128)
    rp = region.load()
    rgba = Image.new("RGBA", (w, h))
    op = rgba.load()
    for y in range(h):
        for x in range(w):
            r, g, b = src[x, y]
            d = dp[x, y]
            if rp[x, y] != 128:
                a = 255
            elif d <= soft:
                a = 0
            else:
                t = (d - soft) / (hard - soft)
                t = t * t * (3 - 2 * t)
                a = int(t * 255)
            if 0 < a < 255:
                f = 255.0 / a
                r = max(0, min(255, int(255 - (255 - r) * f)))
                g = max(0, min(255, int(255 - (255 - g) * f)))
                b = max(0, min(255, int(255 - (255 - b) * f)))
            op[x, y] = (r, g, b, a)
    rgba.save(path.with_name(path.stem + "_alpha.png"))


POST = {"seamless": make_seamless, "key_white": key_white}


# ---------------------------------------------------------------- api

def generate(prompt, size, model, seed, key, retries=3):
    body = {"model": model, "prompt": prompt, "size": size, "n": 1, "response_format": "b64_json"}
    if seed is not None:
        body["seed"] = seed
    last = None
    for attempt in range(retries):
        try:
            r = requests.post(API_URL, headers={"Authorization": f"Bearer {key}"}, json=body, timeout=600)
            if r.status_code == 200:
                j = r.json()
                return base64.b64decode(j["data"][0]["b64_json"]), j
            last = f"HTTP {r.status_code}: {r.text[:200]}"
        except Exception as e:  # noqa: BLE001
            last = repr(e)
        print(f"   retry {attempt + 1}: {last}")
        time.sleep(5 * (attempt + 1))
    raise RuntimeError(last)


def load_manifest():
    if MANIFEST.exists():
        return json.loads(MANIFEST.read_text())
    return []


def save_manifest(m):
    MANIFEST.write_text(json.dumps(m, indent=2))


def record(manifest, entry):
    manifest[:] = [e for e in manifest if e["id"] != entry["id"]]
    manifest.append(entry)
    manifest.sort(key=lambda e: e["file"])
    save_manifest(manifest)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", nargs="*", help="job ids to run")
    ap.add_argument("--group", help="A..H")
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--seed", type=int)
    ap.add_argument("--model")
    ap.add_argument("--prompt-suffix", default="")
    ap.add_argument("--list", action="store_true")
    args = ap.parse_args()

    jobs = build_jobs()
    if args.only:
        jobs = [j for j in jobs if j["id"] in args.only]
    if args.group:
        jobs = [j for j in jobs if j["group"] == args.group]
    if args.list:
        for j in jobs:
            print(f"{j['group']} {j['id']:24s} {j['size']:10s} {j['file']}")
        return

    key = os.environ.get("IMAGE_API_KEY")
    if not key:
        sys.exit("IMAGE_API_KEY is not set (see tools/asset_generate/README.md)")

    manifest = load_manifest()
    for j in jobs:
        out = OUT / j["file"]
        if out.exists() and not args.force:
            print(f"skip   {j['id']} (exists)")
            continue
        model = args.model or j["model"]
        seed = args.seed if args.seed is not None else (j["seed"] if j["seed"] is not None else int(time.time()) % 100000)
        prompt = j["prompt"] + args.prompt_suffix
        print(f"gen    {j['id']} {j['size']} {model} seed={seed}")
        t0 = time.time()
        entry = dict(id=j["id"], file=j["file"], prompt=prompt, negative=j["negative"], model=model,
                     size=j["size"], seed=seed, createdAt=dt.datetime.now(dt.timezone.utc).isoformat(),
                     usage={}, world=j["world"], status="failed", qaNote="")
        try:
            data, meta = generate(prompt, j["size"], model, seed, key)
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(data)
            if j["post"]:
                POST[j["post"]](out)
            entry["usage"] = {"generation_seconds": meta.get("generation_seconds"),
                              "cold_start": meta.get("cold_start"), "wall_seconds": round(time.time() - t0, 1)}
            entry["seed"] = meta.get("seed", seed)
            entry["status"] = "generated"
            print(f"   ok {round(time.time() - t0)}s -> {out.relative_to(ROOT)}")
        except Exception as e:  # noqa: BLE001
            entry["qaNote"] = f"generation failed: {e}"
            print(f"   FAILED {e}")
        record(manifest, entry)


if __name__ == "__main__":
    main()
