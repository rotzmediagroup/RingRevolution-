#!/usr/bin/env python3
"""Verify every asset referenced by code/content exists in app/src/main/assets and the runtime manifest is consistent."""
import json, os, re, sys, glob
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
A = os.path.join(ROOT, "app/src/main/assets")
missing = []
refs = set()
for f in glob.glob(os.path.join(ROOT, "app/src/main/java/**/*.kt"), recursive=True):
    s = open(f).read()
    refs |= {("sprites/%s.webp" % m) for m in re.findall(r'"(sheet\d\d/[a-z0-9_]+(?:_f0\d|_idle|_cheer|_happy|_f01)?)"', s)}
    refs |= set(re.findall(r'"((?:bg|map|menu|scenes|materials|ui)/[a-z0-9_]+\.webp)"', s))
    refs |= {("audio/sfx/sfx_%s.ogg" % m) for m in re.findall(r'"(dough_tap|ring_select|rotate_tick_\d|snap|ring_release_pop|dumpling_jump|invalid|combo_\d|coin_chime|star_\d|level_complete_bell|chapter_bell|button_tap|button_back|booster_hint|booster_twist|booster_golden|undo|lock_click|gate_open|unlock)"', s)}
for f in ("worlds.json", "chapters.json", "cosmetics.json"):
    s = open(os.path.join(ROOT, "content", f)).read()
    refs |= set(re.findall(r'"((?:bg|map|menu|scenes|materials)/[a-z0-9_]+(?:\.webp)?)"', s))
    refs |= set(re.findall(r'"(audio/[a-z/_0-9]+\.ogg)"', s))
for r in sorted(refs):
    p = os.path.join(A, r)
    if r.startswith("bg/world") and not r.endswith(".webp"):
        ok = os.path.exists(p + "_portrait.webp") and os.path.exists(p + "_landscape.webp")
    elif r.startswith("materials/") and not r.endswith(".webp"):
        ok = os.path.exists(p + ".webp")
    elif r.startswith("sprites/") and re.search(r"/(sheet\d\d)/([a-z_]+)\.webp$", r) and not os.path.exists(p):
        base = p[:-5]
        ok = any(os.path.exists(base + suf + ".webp") for suf in ("", "_idle", "_cheer", "_happy", "_celebrate"))
    else:
        ok = os.path.exists(p)
    if not ok: missing.append(r)
for w in (1, 2, 3):
    wj = json.load(open(os.path.join(ROOT, "content/worlds.json")))
    for key in ("music", "finaleMusic"):
        if not os.path.exists(os.path.join(A, "audio/music/%s.ogg" % wj[w - 1][key])): missing.append("audio/music/%s.ogg" % wj[w - 1][key])
    if not os.path.exists(os.path.join(A, "audio/ambience/%s.ogg" % wj[w - 1]["ambience"])): missing.append("audio/ambience/%s.ogg" % wj[w - 1]["ambience"])
levels = glob.glob(os.path.join(A, "content/levels/world_0*/level_*.json"))
if len(levels) != 150: missing.append("expected 150 packed levels, found %d" % len(levels))
man = json.load(open(os.path.join(ROOT, "assets/runtime_manifest.json")))
for m in man:
    if not os.path.exists(os.path.join(ROOT, m["file"])): missing.append("manifest entry without file: " + m["file"])
if missing:
    print("MISSING:"); [print("  " + m) for m in missing]; sys.exit(1)
print("runtime assets OK: %d references, %d manifest entries, %d levels" % (len(refs), len(man), len(levels)))
