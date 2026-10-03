#!/usr/bin/env python3
"""Pack optimised runtime assets into app/src/main/assets and launcher icons into res/mipmap-*.

Sources (never modified): assets/imported/sprites, assets/generated, assets/audio, content/.
Re-runnable; writes assets/runtime_manifest.json describing every shipped file.
Usage: python3 tools/asset_import/pack_runtime_assets.py [--force]
"""
import json, os, re, shutil, sys, hashlib
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app/src/main/assets")
RES = os.path.join(ROOT, "app/src/main/res")
FORCE = "--force" in sys.argv
manifest = []

def sha1(p):
    h = hashlib.sha1()
    with open(p, "rb") as f: h.update(f.read())
    return h.hexdigest()

def put(rel, src, note, world=None):
    manifest.append({"id": rel, "file": "app/src/main/assets/" + rel, "source": os.path.relpath(src, ROOT), "usage": note, "world": world,
                     "bytes": os.path.getsize(os.path.join(OUT, rel)), "sha1": sha1(os.path.join(OUT, rel))})

def webp(src, rel, max_w=None, max_h=None, quality=85, lossless=False, note="", world=None):
    dst = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    if not FORCE and os.path.exists(dst) and os.path.getmtime(dst) >= os.path.getmtime(src):
        put(rel, src, note, world); return
    im = Image.open(src).convert("RGBA")
    if max_w and (im.width > max_w or (max_h and im.height > max_h)):
        im.thumbnail((max_w, max_h or max_w), Image.LANCZOS)
    im.save(dst, "WEBP", quality=quality, lossless=lossless, method=6)
    put(rel, src, note, world)

def copy(src, rel, note, world=None):
    dst = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    if FORCE or not os.path.exists(dst) or os.path.getmtime(dst) < os.path.getmtime(src):
        shutil.copyfile(src, dst)
    put(rel, src, note, world)


def glb_opt(src, rel, note, max_tex=1024, quality=88):
    """Copy a GLB with every embedded image downscaled to max_tex and re-encoded as JPEG (rings are thin on screen)."""
    import struct, io
    dst = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    if not FORCE and os.path.exists(dst) and os.path.getmtime(dst) >= os.path.getmtime(src):
        put(rel, src, note); return
    b = open(src, "rb").read()
    jl = struct.unpack_from("<I", b, 12)[0]; j = json.loads(b[20:20 + jl]); bl = struct.unpack_from("<I", b, 20 + jl)[0]
    bin_ = b[28 + jl:28 + jl + bl]
    views = j["bufferViews"]; images = j.get("images", [])
    img_views = {im["bufferView"]: i for i, im in enumerate(images) if "bufferView" in im}
    out = bytearray()
    for vi, v in enumerate(views):
        off = v.get("byteOffset", 0); data = bin_[off:off + v["byteLength"]]
        if vi in img_views:
            im = Image.open(io.BytesIO(data)).convert("RGB")
            if max(im.size) > max_tex: im.thumbnail((max_tex, max_tex), Image.LANCZOS)
            buf = io.BytesIO(); im.save(buf, "JPEG", quality=quality, optimize=True); data = buf.getvalue()
            images[img_views[vi]]["mimeType"] = "image/jpeg"
        while len(out) % 4: out.append(0)
        v["byteOffset"] = len(out); v["byteLength"] = len(data); out += data
    while len(out) % 4: out.append(0)
    j["buffers"][0]["byteLength"] = len(out)
    js = json.dumps(j, separators=(",", ":")).encode()
    while len(js) % 4: js += b" "
    total = 12 + 8 + len(js) + 8 + len(out)
    with open(dst, "wb") as f:
        f.write(struct.pack("<III", 0x46546C67, 2, total)); f.write(struct.pack("<II", len(js), 0x4E4F534A)); f.write(js)
        f.write(struct.pack("<II", len(out), 0x004E4942)); f.write(out)
    put(rel, src, note)

G = os.path.join(ROOT, "assets/generated")
S = os.path.join(ROOT, "assets/imported/sprites")

# --- generated art
for w in (1, 2, 3):
    name = {1: "world1_teahouse", 2: "world2_nightmarket", 3: "world3_mountain"}[w]
    webp(f"{G}/backgrounds/{name}_bg.png", f"bg/world{w}_portrait.webp", 1024, 1536, 82, note="gameplay background portrait", world=w)
    webp(f"{G}/backgrounds/{name}_bg_land.png", f"bg/world{w}_landscape.webp", 1536, 1024, 82, note="gameplay background landscape", world=w)
    webp(f"{G}/map/world{w}_map.png", f"map/world{w}.webp", 1024, 1536, 84, note="world map scene", world=w)
    webp(f"{G}/scenes/world_intro_{w}.png", f"scenes/world_intro_{w}.webp", 1024, 768, 82, note="world transition scene", world=w)
for c in range(1, 16):
    webp(f"{G}/scenes/chapter_{c:02d}.png", f"scenes/chapter_{c:02d}.webp", 768, 576, 82, note="chapter scene", world=(c - 1) // 5 + 1)
webp(f"{G}/menu/menu_keyart.png", "menu/keyart.webp", 1024, 1536, 84, note="main menu key art")
B = os.path.join(ROOT, "assets/imported/brand")
if os.path.exists(f"{B}/ring_revolution_keyart.png"):
    webp(f"{B}/ring_revolution_keyart.png", "splash/keyart.webp", 1536, 1024, 88, note="splash key art (owner art, landscape 3:2)")
    # portrait splash: a square centre crop keeps the whole logo, the rings and all three characters;
    # plus a heavily blurred, darkened copy that fills the bands above/below on tall phones
    dst = os.path.join(OUT, "splash/keyart_portrait.webp"); dstb = os.path.join(OUT, "splash/keyart_blur.webp")
    if FORCE or not os.path.exists(dstb) or os.path.getmtime(dstb) < os.path.getmtime(f"{B}/ring_revolution_keyart.png"):
        im = Image.open(f"{B}/ring_revolution_keyart.png").convert("RGB"); w, h = im.size; cw = h
        im.crop(((w - cw) // 2, 0, (w + cw) // 2, h)).save(dst, "WEBP", quality=88, method=6)
        from PIL import ImageFilter, ImageEnhance
        blur = ImageEnhance.Brightness(im.resize((96, 64), Image.LANCZOS).filter(ImageFilter.GaussianBlur(6)).resize((768, 512), Image.BICUBIC)).enhance(0.45)
        blur.save(dstb, "WEBP", quality=80, method=6)
    put("splash/keyart_blur.webp", f"{B}/ring_revolution_keyart.png", "splash backdrop, blurred + darkened")
    put("splash/keyart_portrait.webp", f"{B}/ring_revolution_keyart.png", "splash key art, portrait crop")
for m in ("sesame_dough", "matcha", "beet_pink", "ube_purple", "gold", "bamboo"):
    webp(f"{G}/materials/mat_{m}.png", f"materials/{m}.webp", 512, 512, 90, note="ring material tile")
webp(f"{G}/ui/ui_sign_board_alpha.png", "ui/sign_board.webp", 1024, 1024, 88, note="wooden sign panel (alpha)")
webp(f"{G}/ui/ui_button_plate_alpha.png", "ui/button_plate.webp", 512, 512, 88, note="round wooden button plate (alpha)")
webp(f"{G}/ui/ui_bamboo_frame_alpha.png", "ui/bamboo_frame.webp", 768, 768, 88, note="bamboo frame (alpha)")

# --- curated sprites (regex per sheet)
CURATED = {
    "sheet01": r".*",
    "sheet02": r".*_(idle|happy|cheer|laugh|sparkle)$",
    "sheet03": r"(petal|koi_jump|sparrow|windchime|lantern|tea_steam|waterfall|rope).*",
    "sheet05": r".*",
    "sheet06": r".*",
    "sheet07": r".*",
    "sheet08": r".*",
    "sheet09": r".*",
    "sheet10": r"(mystery_basket|bao_bun|matcha_mochi|zongzi)_f0[1-6]",
    "sheet12": r"(dumpling|bao_bun|mochi)_(idle|cheer|bounce)_f0[1-4]",
    "sheet13": r"(panda|cat|rabbit|fox|frog)_.*",
    "sheet14": r"(tap_hand|tap_ripple|success_ring|sparkle_pop|steam_puff|heart_pop|check_mark|coin_pop|fx_combo_burst|combo_text|combo_x3|selected_glow_gold|booster_swirl_idle|booster_bell_idle).*",
    "sheet15": r".*",
    "sheet16": r"(bamboo_steamer_steam|lantern_glow|sakura_lantern|floor_lantern|lantern_string|petal_drift|sparkle_glow|blossom_cluster|teapot|tea_cup|chopstick_holder|bonus_lucky_cat|dish_|stone_lantern|potted|blossom_planter|wall_banner|menu_sign).*",
}
imp = json.load(open(os.path.join(ROOT, "assets/imported/manifest.json")))
count = 0
for e in imp:
    sheet = e["sheet"][:7]
    pat = CURATED.get(sheet)
    if not pat: continue
    name = e["id"].split("/")[1]
    if not re.fullmatch(pat, name): continue
    webp(os.path.join(ROOT, e["file"]), f"sprites/{sheet}/{name}.webp", None, None, 90, note=f"sprite ({e['category']})")
    count += 1
print("sprites packed:", count)

# --- 3D props (Meshy pipeline output: assets/generated/3d/<name>/model.glb)
D3 = os.path.join(ROOT, "assets/generated/3d")
if os.path.isdir(D3):
    for name in sorted(os.listdir(D3)):
        d = os.path.join(D3, name)
        # prefer the Blender-prepared game model (tools/blender/prepare_rings.py), else the raw Meshy download
        glb = None
        if os.path.isdir(d):
            game = os.path.join(d, name + "_game.glb"); raw = os.path.join(d, name + ".glb")
            glb = game if os.path.exists(game) else (raw if os.path.exists(raw) else None)
        if glb and name.startswith("ring_"):
            glb_opt(glb, f"3d/rings/{name}.glb", "premium ring mesh + PBR textures (Meshy, textures 1024 JPEG)")
            thumb = os.path.join(d, "thumbnail.png")
            if os.path.exists(thumb): webp(thumb, f"3d/rings/{name}_thumb.webp", 256, 256, 86, note="ring theme thumbnail (Meshy)")
            tt = os.path.join(d, "turntable.png"); st = os.path.join(d, "still.png")
            if os.path.exists(tt): webp(tt, f"3d/rings/{name}_turn.webp", None, None, 84, note="Cycles studio turntable strip, 16 frames (Blender)")
            if os.path.exists(st): webp(st, f"3d/rings/{name}_still.webp", 384, 384, 88, note="Cycles studio still (Blender)")

# --- Blender hero shot (tools/blender/hero_shot.py)
if os.path.exists(f"{G}/hero/rings_hero.png"): webp(f"{G}/hero/rings_hero.png", "hero/rings_hero.webp", 1200, 800, 86, note="Cycles hero shot of the ring collection")

# --- image-based lighting atlases (tools/asset_generate/make_env_maps.py)
for w in (1, 2, 3):
    src = f"{G}/env/world{w}_env.png"
    if os.path.exists(src): copy(src, f"env/world{w}_env.png", "prefiltered HDR environment atlas for the 3D board", world=w)

# --- audio
A = os.path.join(ROOT, "assets/audio")
for sub in ("music", "sfx", "ambience"):
    d = os.path.join(A, sub)
    for f in sorted(os.listdir(d)):
        if f.endswith(".ogg"):
            copy(os.path.join(d, f), f"audio/{sub}/{f}", f"{sub} track")

# --- content
C = os.path.join(ROOT, "content")
for root, _, files in os.walk(os.path.join(C, "levels")):
    for f in sorted(files):
        if f.endswith(".json"):
            rel = os.path.relpath(os.path.join(root, f), C)
            copy(os.path.join(root, f), f"content/{rel}", "level definition")
for f in ("roadmap.json", "worlds.json", "chapters.json", "cosmetics.json", "economy.json"):
    p = os.path.join(C, f)
    if os.path.exists(p): copy(p, f"content/{f}", "content table")

# --- launcher icons: legacy = the artist's rounded square with transparent corners; adaptive foreground = full-bleed art
#     scaled to the 108dp canvas (the launcher's own mask crops it; the characters sit inside the 72dp safe zone)
icon = Image.open(f"{G}/icon/app_icon_1024.png").convert("RGBA")
full = Image.open(f"{G}/icon/app_icon_full_1024.png").convert("RGBA") if os.path.exists(f"{G}/icon/app_icon_full_1024.png") else icon
for dpi, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    d = os.path.join(RES, f"mipmap-{dpi}"); os.makedirs(d, exist_ok=True)
    icon.resize((px, px), Image.LANCZOS).save(os.path.join(d, "ic_launcher.png"))
    icon.resize((px, px), Image.LANCZOS).save(os.path.join(d, "ic_launcher_round.png"))
    fg_px = int(px * 108 / 48)
    fg = Image.new("RGBA", (fg_px, fg_px), (0, 0, 0, 0))
    inner = int(fg_px * 0.86)   # inset so the dumplings survive round masks
    fg.paste(full.resize((inner, inner), Image.LANCZOS), ((fg_px - inner) // 2, (fg_px - inner) // 2))
    fg.save(os.path.join(d, "ic_launcher_foreground.png"))

os.makedirs(os.path.join(ROOT, "assets"), exist_ok=True)
json.dump(manifest, open(os.path.join(ROOT, "assets/runtime_manifest.json"), "w"), indent=1)
total = sum(m["bytes"] for m in manifest)
print(f"runtime assets: {len(manifest)} files, {total/1e6:.1f} MB")
