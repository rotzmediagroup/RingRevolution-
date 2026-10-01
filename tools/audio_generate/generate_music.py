#!/usr/bin/env python3
"""Generate Dumpling Rings music via the Suno API (sunoapi.org) and post-process it.

Re-runnable: tracks whose final .ogg already exists are skipped. Task IDs are
cached in assets/audio/source/music/suno_tasks.json so an interrupted run can
resume polling without spending credits again.

Requires: SUNO_API_KEY env var, ffmpeg, python3 + numpy.
Usage: python3 tools/audio_generate/generate_music.py [--only name,name] [--no-loop]
"""
import json, os, sys, time, subprocess, urllib.request, urllib.error
import numpy as np

API = "https://api.sunoapi.org"
MODEL = "V6"            # current model per docs (V5/V4_5 are deprecated)
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "assets", "audio", "source", "music")
OUT = os.path.join(ROOT, "assets", "audio", "music")
TASKS = os.path.join(SRC, "suno_tasks.json")
MANIFEST = os.path.join(ROOT, "assets", "audio", "manifest.json")
NEG = "vocals, singing, voice, lyrics, choir, heavy drums, distorted guitar, EDM, trap, harsh, aggressive, loud"

TRACKS = {
  "world1_blossom_teahouse": dict(dur=150, loop=True, title="Blossom Teahouse Morning",
    style="gentle uplifting acoustic instrumental, koto and shamisen hints, soft felt piano, light hand percussion, warm spring morning in a Japanese teahouse, cozy mobile puzzle game background music, calm, loopable, 85 bpm, no vocals"),
  "world2_lantern_night_market": dict(dur=150, loop=True, title="Lantern Night Market",
    style="playful cozy instrumental, pizzicato strings, marimba, soft hand drums, erhu and dizi colour, warm festive Asian night market evening, lanterns, slightly energetic, mobile puzzle game loop, 100 bpm, no vocals"),
  "world3_moonlit_mountain_kitchen": dict(dur=150, loop=True, title="Moonlit Mountain Kitchen",
    style="dreamy magical atmospheric instrumental, soft warm pads, music box, guzheng, celesta, gentle sense of wonder and accomplishment, moonlit mountain night, calm mobile puzzle game loop, 75 bpm, no vocals"),
  "menu_theme": dict(dur=140, loop=True, title="Teahouse Lounge",
    style="cozy lounge instrumental, nylon acoustic guitar, warm piano, light jazz brushes, upright bass, inviting and relaxed, subtle Asian pentatonic colour, menu music for a cozy mobile game, 90 bpm, no vocals"),
  "chef_finale_variation_w1": dict(dur=150, loop=True, title="Blossom Teahouse Chef Finale",
    style="celebratory rich acoustic instrumental, koto, shamisen, soft piano, taiko-lite hand percussion, warm string section swells, joyful spring morning teahouse, triumphant but cozy, mobile puzzle game loop, 88 bpm, no vocals"),
  "chef_finale_variation_w2": dict(dur=150, loop=True, title="Lantern Market Chef Finale",
    style="celebratory festive instrumental, pizzicato and bowed strings, marimba, lively hand drums and shakers, erhu and dizi melody, bright night market celebration, richer and more energetic, mobile puzzle game loop, 104 bpm, no vocals"),
  "chef_finale_variation_w3": dict(dur=150, loop=True, title="Moonlit Mountain Chef Finale",
    style="celebratory dreamy orchestral instrumental, music box, guzheng, celesta, warm string ensemble, soft timpani and chimes, magical sense of accomplishment, moonlit mountain, richer variation, mobile puzzle game loop, 78 bpm, no vocals"),
  "level_complete_sting": dict(dur=12, loop=False, title="Level Complete",
    style="short bright warm satisfying musical sting, celesta, koto, soft bells, a single resolving uplifting phrase, 10 seconds, instrumental jingle for a cozy puzzle game, no vocals"),
  "chapter_complete_fanfare": dict(dur=16, loop=False, title="Chapter Complete",
    style="short warm celebratory fanfare, strings, bells, koto, soft hand drums, rising triumphant phrase with gentle ending, 15 seconds, instrumental jingle for a cozy puzzle game, no vocals"),
  "world_unlock_fanfare": dict(dur=18, loop=False, title="New World Unlocked",
    style="short magical reveal fanfare, shimmering celesta and chimes, guzheng glissando, warm strings swell into a bright hopeful chord, 15 seconds, instrumental jingle for a cozy puzzle game, no vocals"),
}

def api(path, body=None):
    key = os.environ.get("SUNO_API_KEY")
    if not key: sys.exit("SUNO_API_KEY env var is required")
    req = urllib.request.Request(API + path, headers={"Authorization": "Bearer " + key, "Content-Type": "application/json", "User-Agent": "curl/8.0 dumpling-rings-audio-tool", "Accept": "application/json"},
                                 data=json.dumps(body).encode() if body else None, method="POST" if body else "GET")
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=60) as r: return json.load(r)
        except urllib.error.HTTPError as e:
            txt = e.read().decode(errors="ignore"); print("  HTTP", e.code, txt[:300])
            if e.code in (429, 500, 502, 503, 504): time.sleep(10 * (attempt + 1)); continue
            return {"code": e.code, "msg": txt}
        except Exception as e:
            print("  net error", e); time.sleep(10)
    return {"code": -1, "msg": "network failure"}

def load_tasks():
    return json.load(open(TASKS)) if os.path.exists(TASKS) else {}

def save_tasks(t):
    os.makedirs(SRC, exist_ok=True); json.dump(t, open(TASKS, "w"), indent=2)

def submit(name, spec):
    body = {"customMode": True, "instrumental": True, "model": MODEL, "title": spec["title"],
            "style": spec["style"], "negativeTags": NEG, "duration": spec["dur"],
            "styleWeight": 0.7, "weirdnessConstraint": 0.2, "variety": 1,
            "callBackUrl": "https://example.com/callback"}
    r = api("/api/v1/generate", body)
    if r.get("code") != 200: print(f"  submit FAILED for {name}: {r}"); return None
    return r["data"]["taskId"]

def poll(task_id, max_wait=900):
    t0 = time.time()
    while time.time() - t0 < max_wait:
        r = api(f"/api/v1/generate/record-info?taskId={task_id}")
        d = r.get("data") or {}
        st = d.get("status")
        if st == "SUCCESS":
            return d["response"]["sunoData"], d
        if st in ("CREATE_TASK_FAILED", "GENERATE_AUDIO_FAILED", "CALLBACK_EXCEPTION", "SENSITIVE_WORD_ERROR"):
            print("  task failed:", st, d.get("errorMessage")); return None, d
        print(f"  ... {st} ({int(time.time()-t0)}s)"); time.sleep(20)
    print("  timeout waiting for task"); return None, None

def download(url, path):
    for attempt in range(3):
        try:
            urllib.request.urlretrieve(url, path); return True
        except Exception as e: print("  download error", e); time.sleep(5)
    return False

def run(cmd): subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def ffprobe_dur(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path], capture_output=True, text=True).stdout
    return float(out.strip() or 0)

def make_loop(wav_in, wav_out, xfade=2.0, sr=44100):
    """Seamless loop: crossfade the last `xfade` seconds into the start with equal-power curves."""
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", wav_in, "-f", "f32le", "-ac", "2", "-ar", str(sr), "-"], capture_output=True).stdout
    x = np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).astype(np.float64)
    n = int(xfade * sr)
    if len(x) < 4 * n: n = len(x) // 4
    head, tail = x[:n], x[-n:]
    t = np.linspace(0, 1, n)[:, None]
    fin, fout = np.sin(t * np.pi / 2), np.cos(t * np.pi / 2)
    blended = head * fin + tail * fout
    y = np.concatenate([blended, x[n:-n]])            # tail folded into the head; loop point is sample 0
    y = np.clip(y, -1, 1).astype(np.float32)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ac", "2", "-ar", str(sr), "-i", "-", wav_out], input=y.tobytes(), check=True)

def post_process(mp3, name, loop):
    tmp = os.path.join(SRC, name + ".tmp.wav")
    norm = os.path.join(SRC, name + ".norm.wav")
    ogg = os.path.join(OUT, name + ".ogg")
    # 1) decode + loudness normalise to -16 LUFS (two-pass loudnorm would be stricter; single-pass is fine for game music)
    run(["ffmpeg", "-y", "-i", mp3, "-af", "loudnorm=I=-16:TP=-1.5:LRA=11", "-ar", "44100", "-ac", "2", norm])
    if loop:
        make_loop(norm, tmp); os.replace(tmp, norm)
    else:
        # stingers: trim trailing silence and add a short fade-out so they end cleanly
        d = ffprobe_dur(norm)
        run(["ffmpeg", "-y", "-i", norm, "-af", f"silenceremove=stop_periods=1:stop_threshold=-50dB:stop_duration=0.5,afade=t=out:st={max(0,d-1.5)}:d=1.5", tmp]); os.replace(tmp, norm)
    run(["ffmpeg", "-y", "-i", norm, "-c:a", "libvorbis", "-q:a", "5", ogg])
    os.remove(norm)
    return ogg

def update_manifest(entry):
    m = json.load(open(MANIFEST)) if os.path.exists(MANIFEST) else {"assets": []}
    m["assets"] = [a for a in m["assets"] if a["id"] != entry["id"]] + [entry]
    json.dump(m, open(MANIFEST, "w"), indent=2)

def main():
    only = None; want_loop = "--no-loop" not in sys.argv
    if "--only" in sys.argv: only = sys.argv[sys.argv.index("--only") + 1].split(",")
    os.makedirs(SRC, exist_ok=True); os.makedirs(OUT, exist_ok=True)
    tasks = load_tasks(); failures = []
    # phase 1: submit everything that has no task yet (parallel generation on Suno's side)
    for name, spec in TRACKS.items():
        if only and name not in only: continue
        if os.path.exists(os.path.join(OUT, name + ".ogg")): print("skip (exists)", name); continue
        if name in tasks and tasks[name].get("taskId"): continue
        print("submit", name); tid = submit(name, spec)
        if tid: tasks[name] = {"taskId": tid, "model": MODEL, "style": spec["style"], "title": spec["title"], "duration": spec["dur"]}; save_tasks(tasks)
        else: failures.append(name)
        time.sleep(2)
    # phase 2: poll + download + post-process
    for name, spec in TRACKS.items():
        if only and name not in only: continue
        if os.path.exists(os.path.join(OUT, name + ".ogg")): continue
        info = tasks.get(name)
        if not info: continue
        print("poll", name, info["taskId"])
        clips, d = poll(info["taskId"])
        if not clips: failures.append(name); continue
        got = []
        for i, c in enumerate(clips):
            url = c.get("audio_url") or c.get("source_audio_url")
            if not url: continue
            dst = os.path.join(SRC, f"{name}_alt{i+1}.mp3")
            if download(url, dst): got.append((dst, c))
        if not got: failures.append(name); continue
        # pick the clip whose length is closest to the requested duration as the primary
        got.sort(key=lambda g: abs(ffprobe_dur(g[0]) - spec["dur"]))
        primary = got[0][0]; mp3 = os.path.join(SRC, name + ".mp3"); os.replace(primary, mp3)
        ogg = post_process(mp3, name, spec["loop"] and want_loop)
        update_manifest({"id": name, "file": os.path.relpath(ogg, ROOT), "type": "music" if spec["loop"] else "stinger",
            "source": "Suno API (sunoapi.org)", "model": MODEL, "prompt": spec["style"], "negativeTags": NEG,
            "taskId": info["taskId"], "clipIds": [g[1].get("id") for g in got], "sourceMp3": os.path.relpath(mp3, ROOT),
            "alternates": [os.path.relpath(g[0], ROOT) for g in got[1:]],
            "durationSec": round(ffprobe_dur(ogg), 2), "loop": bool(spec["loop"]),
            "license": "AI-generated via sunoapi.org; commercial rights depend on the Suno account plan - owner must confirm (see docs/AUDIO_REPORT.md)"})
        print("  done", ogg)
    if failures: print("FAILURES:", sorted(set(failures)))

if __name__ == "__main__": main()
