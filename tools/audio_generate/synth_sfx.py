#!/usr/bin/env python3
"""Procedural SFX + ambience synthesis for Dumpling Rings (numpy only, no samples).

Writes WAV sources to assets/audio/source/sfx/ and OGG (libvorbis q5) to
assets/audio/sfx/ and assets/audio/ambience/, and merges entries into
assets/audio/manifest.json. Deterministic (seeded) so re-runs are identical.
Usage: python3 tools/audio_generate/synth_sfx.py [--force]
"""
import os, sys, json, struct, subprocess, wave
import numpy as np

SR = 44100
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "assets", "audio", "source", "sfx")
SFX = os.path.join(ROOT, "assets", "audio", "sfx")
AMB = os.path.join(ROOT, "assets", "audio", "ambience")
MANIFEST = os.path.join(ROOT, "assets", "audio", "manifest.json")
PEAK = 10 ** (-3 / 20)  # -3 dBFS
rng = np.random.default_rng(20261001)

# ---------------------------------------------------------------- building blocks
def t_(dur): return np.arange(int(dur * SR)) / SR

def env_ad(dur, a=0.002, d=None, curve=4.0):
    """Attack + exponential-ish decay envelope."""
    n = int(dur * SR); d = dur - a if d is None else d
    t = t_(dur); e = np.ones(n)
    na = max(1, int(a * SR)); e[:na] = np.linspace(0, 1, na)
    dec = np.exp(-curve * np.clip((t - a) / max(d, 1e-3), 0, None)); e *= dec
    return e

def sine(f, dur, ph=0.0): return np.sin(2 * np.pi * f * t_(dur) + ph)

def partials(f0, dur, ratios, amps, decays, a=0.002, detune=0.0):
    """Additive synthesis: each partial has its own decay time (seconds)."""
    t = t_(dur); out = np.zeros_like(t)
    for r, am, dc in zip(ratios, amps, decays):
        f = f0 * r * (1 + detune * rng.normal() * 0.002)
        out += am * np.sin(2 * np.pi * f * t) * np.exp(-t / dc)
    na = max(1, int(a * SR)); out[:na] *= np.linspace(0, 1, na)
    return out

def noise(dur): return rng.normal(0, 1, int(dur * SR))

def onepole_lp(x, fc):
    a = np.exp(-2 * np.pi * fc / SR); y = np.empty_like(x); acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc; y[i] = acc
    return y

def lp(x, fc, order=2):
    """Cheap IIR low-pass via scipy-free biquad (Butterworth-ish)."""
    for _ in range(order): x = _biquad(x, fc, "lp")
    return x

def hp(x, fc, order=2):
    for _ in range(order): x = _biquad(x, fc, "hp")
    return x

def bp(x, fc, q=2.0): return _biquad(x, fc, "bp", q)

def _biquad(x, fc, kind, q=0.7071):
    fc = min(fc, SR * 0.45); w = 2 * np.pi * fc / SR; al = np.sin(w) / (2 * q); c = np.cos(w)
    if kind == "lp": b0 = (1 - c) / 2; b1 = 1 - c; b2 = b0
    elif kind == "hp": b0 = (1 + c) / 2; b1 = -(1 + c); b2 = b0
    else: b0 = al; b1 = 0; b2 = -al
    a0 = 1 + al; a1 = -2 * c; a2 = 1 - al
    b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0
    y = np.zeros_like(x); x1 = x2 = y1 = y2 = 0.0
    # vectorised via lfilter-style recursion in chunks is complex; plain loop is fine for <2s sounds
    for i in range(len(x)):
        xi = x[i]; yi = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, xi, y1, yi; y[i] = yi
    return y

def fast_lp(x, fc):
    """FFT brick-wall-ish low-pass with soft knee; used for long ambience beds (fast)."""
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR)
    g = 1 / np.sqrt(1 + (f / fc) ** 4); return np.fft.irfft(X * g, len(x))

def fast_bp(x, lo, hi):
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR)
    g = 1 / np.sqrt(1 + (lo / np.maximum(f, 1e-3)) ** 4) / np.sqrt(1 + (f / hi) ** 4); return np.fft.irfft(X * g, len(x))

def reverb(x, mix=0.18, size=0.6, tail=0.5):
    """Small Schroeder reverb: 4 parallel combs -> 2 series allpass."""
    n = len(x) + int(tail * SR); x = np.pad(x, (0, int(tail * SR)))
    out = np.zeros(n)
    for d in (0.0297, 0.0371, 0.0411, 0.0437):
        D = int(d * size * SR * 1.5) + 1; g = 0.72; y = np.zeros(n)
        for i in range(n):
            y[i] = x[i] + (g * y[i - D] if i >= D else 0.0)
        out += y
    out /= 4
    for d in (0.005, 0.0017):
        D = int(d * SR) + 1; g = 0.5; y = np.zeros(n)
        for i in range(n):
            xd = x[i - D] if i >= D else 0.0; yd = y[i - D] if i >= D else 0.0
            # standard allpass: y = -g x + x[n-D] + g y[n-D]
            y[i] = -g * out[i] + (out[i - D] if i >= D else 0.0) + g * yd
        out = y
    return x * (1 - mix) + out * mix

def pitch_glide(f0, f1, dur, curve=1.0):
    t = t_(dur); f = f0 + (f1 - f0) * (t / dur) ** curve
    return np.sin(2 * np.pi * np.cumsum(f) / SR)

def normalize(x, peak=PEAK):
    m = np.max(np.abs(x)) or 1.0; return x / m * peak

def fade(x, fin=0.002, fout=0.02):
    ni, no = int(fin * SR), int(fout * SR)
    if ni: x[:ni] *= np.linspace(0, 1, ni)
    if no: x[-no:] *= np.linspace(1, 0, no)
    return x

def mix(*parts):
    n = max(len(p) for p in parts); out = np.zeros(n)
    for p in parts: out[:len(p)] += p
    return out

def at(x, when, total=None):
    """Place x at time `when` (seconds) inside a buffer."""
    n = int(when * SR); out = np.zeros(n + len(x)); out[n:] = x; return out

# ---------------------------------------------------------------- SFX recipes
def dough_tap():
    body = partials(140, 0.25, [1, 1.6, 2.4], [1, 0.3, 0.1], [0.06, 0.03, 0.02], a=0.001)
    thud = lp(noise(0.08), 400) * env_ad(0.08, 0.001, curve=8)
    return reverb(mix(body, 0.5 * thud), 0.12)

def ring_select():
    sq = pitch_glide(330, 220, 0.18) * env_ad(0.18, 0.003, curve=5)
    soft = lp(noise(0.1), 1200) * env_ad(0.1, 0.002, curve=9) * 0.25
    return reverb(mix(sq, soft), 0.1)

def rotate_tick(variant):
    f0 = [1900, 2300, 2750][variant]
    click = partials(f0, 0.07, [1, 1.52, 2.9], [1, 0.4, 0.2], [0.012, 0.008, 0.005], a=0.0005)
    wood = bp(noise(0.05), 900 * (1 + 0.15 * variant), 1.5) * env_ad(0.05, 0.0005, curve=10) * 0.8
    return reverb(mix(click, wood), 0.08, tail=0.15)

def snap():
    ceramic = partials(3200, 0.22, [1, 1.41, 2.03, 2.8], [1, 0.5, 0.3, 0.15], [0.05, 0.035, 0.025, 0.015], a=0.0005)
    body = partials(620, 0.2, [1, 2.1], [0.6, 0.2], [0.05, 0.03])
    tr = hp(noise(0.03), 4000) * env_ad(0.03, 0.0005, curve=12) * 0.5
    return reverb(mix(ceramic, body, tr), 0.15)

def release_pop():
    pop = pitch_glide(180, 480, 0.12, 0.6) * env_ad(0.12, 0.002, curve=6)
    spark = np.zeros(int(0.9 * SR))
    for i, f in enumerate([1046, 1318, 1568, 2093, 2637]):
        spark = mix(spark, at(partials(f, 0.5, [1, 3.01], [1, 0.15], [0.18, 0.08]) * 0.35, 0.05 + i * 0.07))
    return reverb(mix(pop, spark), 0.22, tail=0.4)

def dumpling_jump():
    wh = bp(noise(0.35), 800, 1.0); wh *= np.interp(t_(0.35), [0, 0.2, 0.35], [0.2, 1, 0])
    wh = wh * pitch_glide(1, 1, 0.35) * 0 + wh  # keep simple band noise
    sweep = fast_bp(noise(0.35), 300, 3000) * np.interp(t_(0.35), [0, 0.18, 0.35], [0.1, 0.9, 0.0])
    thud = partials(110, 0.3, [1, 1.8, 2.7], [1, 0.3, 0.1], [0.08, 0.04, 0.02], a=0.001)
    thud = mix(thud, lp(noise(0.06), 350) * env_ad(0.06, 0.001, curve=10) * 0.6)
    return reverb(mix(0.5 * sweep, at(thud, 0.33)), 0.15)

def invalid():
    body = partials(165, 0.35, [1, 1.5, 2.2], [1, 0.25, 0.1], [0.12, 0.06, 0.03], a=0.002)
    second = at(partials(140, 0.3, [1, 1.5], [0.8, 0.2], [0.1, 0.05]), 0.11)
    dull = lp(noise(0.1), 300) * env_ad(0.1, 0.002, curve=8) * 0.4
    return reverb(mix(body, second, dull), 0.1)

def chime(f, dur=0.9, amp=1.0):
    return amp * partials(f, dur, [1, 2.76, 5.4, 8.9], [1, 0.35, 0.12, 0.05], [0.35, 0.18, 0.08, 0.04], a=0.001)

def combo(level):
    notes = [[523.25, 659.25], [587.33, 783.99, 987.77], [659.25, 830.61, 1046.5, 1318.5]][level - 1]
    out = np.zeros(1)
    for i, f in enumerate(notes): out = mix(out, at(chime(f, 0.8, 0.8), i * 0.07))
    return reverb(out, 0.25, tail=0.5)

def coin():
    return reverb(mix(chime(1760, 0.5, 0.8), at(chime(2349, 0.6, 0.9), 0.06)), 0.2)

def star(i):
    f = [1046.5, 1318.5, 1568.0][i]
    tw = partials(f, 0.9, [1, 2.0, 3.0, 4.2], [1, 0.4, 0.2, 0.08], [0.4, 0.2, 0.1, 0.05], a=0.001)
    shimmer = hp(noise(0.3), 6000) * env_ad(0.3, 0.001, curve=9) * 0.06
    return reverb(mix(tw, shimmer), 0.3, tail=0.5)

def bell(f, dur, amp=1.0):
    return amp * partials(f, dur, [0.56, 0.92, 1.19, 1.71, 2.0, 2.74, 3.0, 3.76], [0.3, 0.6, 1.0, 0.5, 0.35, 0.2, 0.15, 0.08],
                          [dur * 0.9, dur * 0.8, dur * 0.7, dur * 0.5, dur * 0.4, dur * 0.3, dur * 0.25, dur * 0.2], a=0.001)

def level_bell():
    return reverb(mix(bell(880, 1.0, 0.9), at(bell(1108.7, 0.9, 0.7), 0.12), at(bell(1318.5, 1.0, 0.8), 0.24)), 0.3, tail=0.4)

def chapter_bell():
    return reverb(mix(bell(523.25, 1.2, 0.8), at(bell(659.25, 1.1, 0.7), 0.15), at(bell(783.99, 1.1, 0.7), 0.3), at(bell(1046.5, 1.2, 0.9), 0.45)), 0.35, tail=0.5)

def button_tap():
    return reverb(mix(partials(1200, 0.08, [1, 2.2], [1, 0.3], [0.02, 0.01], a=0.0005), lp(noise(0.03), 2500) * env_ad(0.03, 0.0005, curve=12) * 0.4), 0.06, tail=0.1)

def button_back():
    return reverb(mix(pitch_glide(900, 600, 0.1) * env_ad(0.1, 0.001, curve=6), lp(noise(0.03), 2000) * env_ad(0.03, 0.0005, curve=12) * 0.3), 0.06, tail=0.1)

def booster_hint():
    hiss = fast_bp(noise(0.7), 1500, 7000) * np.interp(t_(0.7), [0, 0.08, 0.4, 0.7], [0, 0.5, 0.35, 0]) * 0.5
    return reverb(mix(hiss, at(bell(1318.5, 0.8, 0.8), 0.25), at(bell(1760, 0.7, 0.6), 0.4)), 0.25, tail=0.4)

def booster_twist():
    dur = 0.8; t = t_(dur)
    f = 300 + 900 * np.sin(np.pi * t / dur) ** 2
    whirl = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.interp(t, [0, 0.1, 0.6, 0.8], [0, 0.6, 0.5, 0])
    air = fast_bp(noise(dur), 600, 4000) * (0.5 + 0.5 * np.sin(2 * np.pi * 9 * t)) * np.interp(t, [0, 0.1, 0.7, 0.8], [0, 0.5, 0.4, 0])
    return reverb(mix(whirl * 0.6, air), 0.2, tail=0.3)

def booster_golden():
    out = np.zeros(1)
    for i, f in enumerate([1568, 1975.5, 2349.3, 2793.8, 3136, 3520]):
        out = mix(out, at(partials(f, 0.9, [1, 2.0, 3.0], [1, 0.2, 0.08], [0.45, 0.2, 0.1]) * 0.5, i * 0.045))
    shimmer = hp(noise(1.0), 5000) * np.interp(t_(1.0), [0, 0.1, 0.5, 1.0], [0, 0.12, 0.08, 0]) 
    return reverb(mix(out, shimmer), 0.35, tail=0.5)

def undo():
    dur = 0.4; t = t_(dur)
    sw = fast_bp(noise(dur), 400, 3500) * np.interp(t, [0, 0.3, 0.4], [0, 0.9, 0])[::-1]
    tone = pitch_glide(700, 350, dur) * env_ad(dur, 0.01, curve=3) * 0.4
    return reverb(mix(sw, tone), 0.12)

def lock_click():
    return reverb(mix(partials(2600, 0.1, [1, 1.3, 2.1], [1, 0.4, 0.2], [0.015, 0.01, 0.006], a=0.0005),
                      at(partials(1900, 0.1, [1, 1.3], [0.7, 0.3], [0.02, 0.01], a=0.0005), 0.06),
                      lp(noise(0.05), 1800) * env_ad(0.05, 0.0005, curve=10) * 0.4), 0.1)

def gate_open():
    dur = 0.9; t = t_(dur)
    wood = fast_bp(noise(dur), 150, 900) * np.interp(t, [0, 0.1, 0.6, 0.9], [0, 0.5, 0.4, 0]) * (0.6 + 0.4 * np.sin(2 * np.pi * 14 * t))
    creak = pitch_glide(220, 330, 0.6) * env_ad(0.6, 0.05, curve=2) * 0.25
    end = at(partials(180, 0.3, [1, 1.7], [1, 0.3], [0.08, 0.04]), 0.65)
    return reverb(mix(wood, creak, end, at(bell(1318.5, 0.6, 0.4), 0.7)), 0.2, tail=0.3)

def unlock():
    out = np.zeros(1)
    for i, f in enumerate([783.99, 987.77, 1174.7, 1568]):
        out = mix(out, at(bell(f, 0.9, 0.8), i * 0.09))
    return reverb(mix(at(lock_click() * 0.6, 0.0), at(out, 0.1)), 0.3, tail=0.5)

SFX_RECIPES = {
  "sfx_dough_tap": dough_tap, "sfx_ring_select": ring_select,
  "sfx_rotate_tick_1": lambda: rotate_tick(0), "sfx_rotate_tick_2": lambda: rotate_tick(1), "sfx_rotate_tick_3": lambda: rotate_tick(2),
  "sfx_snap": snap, "sfx_ring_release_pop": release_pop, "sfx_dumpling_jump": dumpling_jump, "sfx_invalid": invalid,
  "sfx_combo_1": lambda: combo(1), "sfx_combo_2": lambda: combo(2), "sfx_combo_3": lambda: combo(3),
  "sfx_coin_chime": coin, "sfx_star_1": lambda: star(0), "sfx_star_2": lambda: star(1), "sfx_star_3": lambda: star(2),
  "sfx_level_complete_bell": level_bell, "sfx_chapter_bell": chapter_bell,
  "sfx_button_tap": button_tap, "sfx_button_back": button_back,
  "sfx_booster_hint": booster_hint, "sfx_booster_twist": booster_twist, "sfx_booster_golden": booster_golden,
  "sfx_undo": undo, "sfx_lock_click": lock_click, "sfx_gate_open": gate_open, "sfx_unlock": unlock,
}

# ---------------------------------------------------------------- ambience (long, FFT-filtered, seamless loops)
def wind(dur, lo=80, hi=600, depth=0.6, rate=0.07):
    t = t_(dur); n = fast_bp(noise(dur), lo, hi)
    mod = 1 - depth + depth * (0.5 + 0.5 * np.sin(2 * np.pi * rate * t + 1.0)) * (0.6 + 0.4 * np.sin(2 * np.pi * rate * 2.7 * t))
    return n * mod

def bird(f0):
    dur = 0.18 + rng.random() * 0.15; t = t_(dur)
    f = f0 * (1 + 0.12 * np.sin(2 * np.pi * (6 + rng.random() * 6) * t)) * (1 + 0.1 * t / dur)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * t / dur) ** 1.5

def sprinkle(total, make, count, lo_gap, amp):
    out = np.zeros(int(total * SR)); tm = rng.random() * 2
    for _ in range(count):
        s = make(); p = int(tm * SR)
        if p + len(s) < len(out): out[p:p + len(s)] += s * amp * (0.6 + 0.4 * rng.random())
        tm += lo_gap + rng.random() * lo_gap * 1.5
    return out

def steam(dur):
    t = t_(dur); g = np.zeros_like(t)
    for s in np.arange(3, dur - 4, 11 + rng.random() * 6):
        g += np.exp(-((t - s - 1.5) / 1.4) ** 2)
    return fast_bp(noise(dur), 2500, 8000) * g * 0.08

def seamless(x, xf=2.0):
    n = int(xf * SR); head, tail = x[:n], x[-n:]
    w = np.linspace(0, 1, n); return np.concatenate([head * np.sin(w * np.pi / 2) + tail * np.cos(w * np.pi / 2), x[n:-n]])

def amb_teahouse():
    dur = 48
    w = wind(dur, 100, 700, 0.5, 0.05) * 0.35
    birds = sprinkle(dur, lambda: mix(*[at(bird(2200 + rng.random() * 1500), i * 0.25) for i in range(2 + int(rng.random() * 3))]), 10, 2.5, 0.12)
    birds = fast_bp(birds, 1500, 7000) * 0.6 * np.interp(t_(dur), [0, 1, dur - 1, dur], [0.3, 1, 1, 0.3])
    return seamless(mix(w, steam(dur), birds))

def creak():
    dur = 0.5 + rng.random() * 0.4; t = t_(dur)
    f = 180 + 120 * rng.random() + 60 * np.sin(2 * np.pi * 1.5 * t)
    return fast_bp(np.sin(2 * np.pi * np.cumsum(f) / SR) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 23 * t))), 150, 1200) * np.sin(np.pi * t / dur) ** 2

def amb_market():
    dur = 48; t = t_(dur)
    murmur = fast_bp(noise(dur), 180, 1100)
    # multiply by slowly varying "syllable" modulation to feel like distant voices
    syl = fast_lp(np.abs(noise(dur)), 4) ; syl = syl / (np.max(syl) + 1e-9)
    murmur = murmur * (0.4 + 0.6 * syl) * 0.45
    hum = fast_bp(noise(dur), 60, 200) * 0.15
    cr = sprinkle(dur, creak, 9, 3.0, 0.08)
    clink = sprinkle(dur, lambda: partials(2400 + rng.random() * 1500, 0.3, [1, 2.4], [1, 0.3], [0.08, 0.04]), 8, 3.5, 0.05)
    return seamless(mix(murmur, hum, cr, clink))

def amb_mountain():
    dur = 54
    w = wind(dur, 70, 900, 0.7, 0.04) * 0.4
    high = wind(dur, 1200, 4000, 0.8, 0.09) * 0.05
    scale = [1046.5, 1174.7, 1318.5, 1568, 1760, 2093]
    ch = sprinkle(dur, lambda: mix(*[at(partials(scale[int(rng.random() * len(scale))], 1.6, [1, 2.0, 3.0], [1, 0.2, 0.05], [0.7, 0.3, 0.1]), i * (0.08 + rng.random() * 0.2)) for i in range(1 + int(rng.random() * 4))]), 9, 3.0, 0.12)
    return seamless(mix(w, high, ch))

AMB_RECIPES = {"amb_teahouse": amb_teahouse, "amb_market": amb_market, "amb_mountain": amb_mountain}

# ---------------------------------------------------------------- IO
def write_wav(path, x):
    x = np.clip(x, -1, 1); pcm = (x * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())

def to_ogg(wav, ogg):
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", "5", ogg], check=True)

def update_manifest(entries):
    m = json.load(open(MANIFEST)) if os.path.exists(MANIFEST) else {"assets": []}
    ids = {e["id"] for e in entries}
    m["assets"] = [a for a in m["assets"] if a["id"] not in ids] + entries
    m["assets"].sort(key=lambda a: (a["type"], a["id"]))
    json.dump(m, open(MANIFEST, "w"), indent=2)

def main():
    force = "--force" in sys.argv
    for d in (SRC, SFX, AMB): os.makedirs(d, exist_ok=True)
    entries = []
    for group, recipes, outdir, typ, loop in (("sfx", SFX_RECIPES, SFX, "sfx", False), ("ambience", AMB_RECIPES, AMB, "ambience", True)):
        for name, fn in recipes.items():
            ogg = os.path.join(outdir, name + ".ogg"); wav = os.path.join(SRC, name + ".wav")
            if os.path.exists(ogg) and not force:
                print("skip", name)
            else:
                x = fn()
                x = normalize(fade(x) if not loop else x, PEAK)
                if loop: x = normalize(x, PEAK)
                write_wav(wav, x); to_ogg(wav, ogg); print("wrote", ogg, f"{len(x)/SR:.2f}s")
            dur = round(os.path.getsize(wav) / 2 / SR, 2) if os.path.exists(wav) else None
            entries.append({"id": name, "file": os.path.relpath(ogg, ROOT), "type": typ, "source": "procedural synth (numpy)",
                            "model": None, "prompt": f"{fn.__doc__ or name} - additive/noise synthesis, see tools/audio_generate/synth_sfx.py",
                            "taskId": None, "sourceWav": os.path.relpath(wav, ROOT), "durationSec": dur, "loop": loop,
                            "license": "Original procedural synthesis generated in-repo; no third-party samples. Owned by the project."})
    update_manifest(entries)

if __name__ == "__main__": main()
