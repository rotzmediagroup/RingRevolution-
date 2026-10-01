#!/usr/bin/env python3
"""Image-based lighting for the 3D board: one HDR studio environment per world, GGX-prefiltered for 6 roughness levels
plus a cosine irradiance map, packed as a stacked equirect atlas (256x128 per level, 7 levels) with a gamma range encoding
(rgb = (hdr / 16) ^ (1/2.4)) so GLES 3.0 can sample it as a plain RGB texture. Output: assets/generated/env/world{w}_env.png
Mapping (matches Shaders.GLBRING_FS): u = atan2(d.y, d.x) / 2pi + 0.5, v = acos(d.z) / pi (z up, v=0 is the zenith).
"""
import numpy as np, os, sys
from PIL import Image
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
W, H = 256, 128
LEVELS = [0.0, 0.2, 0.4, 0.6, 0.8, 1.0]
RANGE, GAMMA = 16.0, 2.4

def dirs(w, h):
    u = (np.arange(w) + 0.5) / w; v = (np.arange(h) + 0.5) / h
    phi = (u - 0.5) * 2 * np.pi; theta = v * np.pi
    P, T = np.meshgrid(phi, theta)
    return np.stack([np.sin(T) * np.cos(P), np.sin(T) * np.sin(P), np.cos(T)], -1)

def disc(d, center, radius_deg, col, soft=1.0):
    c = np.array(center, float); c /= np.linalg.norm(c)
    cosang = np.clip((d * c).sum(-1), -1, 1); ang = np.degrees(np.arccos(cosang))
    w = 1 - np.clip((ang - radius_deg) / (radius_deg * soft + 1e-3), 0, 1)
    return w[..., None] * np.array(col, float)

def window(d, az_deg, el_deg, w_deg, h_deg, col):
    az = np.degrees(np.arctan2(d[..., 1], d[..., 0])); el = np.degrees(np.arcsin(np.clip(d[..., 2], -1, 1)))
    da = (az - az_deg + 180) % 360 - 180; de = el - el_deg
    m = (1 - np.clip((np.abs(da) - w_deg / 2) / 4, 0, 1)) * (1 - np.clip((np.abs(de) - h_deg / 2) / 3, 0, 1))
    return m[..., None] * np.array(col, float)

def environment(world, d):
    up = np.clip(d[..., 2], -1, 1)[..., None]
    if world == 1:   # teahouse morning: cream sky, warm haze, wooden floor, big soft shoji window + sun
        sky = np.array([1.05, 1.0, 0.92]) * 1.1; hz = np.array([0.95, 0.78, 0.55]); gnd = np.array([0.32, 0.2, 0.1])
        e = np.where(up > 0, hz + (sky - hz) * up ** 0.6, gnd + (hz - gnd) * np.clip(1 + up, 0, 2) ** 3)
        e = e + window(d, 150, 28, 70, 40, [4.5, 4.2, 3.6]) + window(d, -40, 20, 40, 25, [1.4, 1.5, 1.7])
        e = e + disc(d, [-0.55, 0.30, 0.70], 2.5, [60, 52, 40], 1.5)
    elif world == 2: # night market: deep blue night, warm paper lanterns all around, red neon band, dark stone floor
        sky = np.array([0.04, 0.05, 0.14]); hz = np.array([0.18, 0.09, 0.08]); gnd = np.array([0.08, 0.05, 0.03])
        e = np.where(up > 0, hz + (sky - hz) * up ** 0.5, gnd + (hz - gnd) * np.clip(1 + up, 0, 2) ** 3)
        for az, el, s in [(150, 24, 1.0), (60, 30, 0.7), (-110, 18, 0.8), (-30, 35, 0.5), (100, 10, 0.6), (-160, 28, 0.6)]:
            e = e + disc(d, [np.cos(np.radians(az)) * np.cos(np.radians(el)), np.sin(np.radians(az)) * np.cos(np.radians(el)), np.sin(np.radians(el))], 4.0, np.array([28, 15, 5]) * s, 1.2)
        e = e + window(d, 0, 12, 360, 6, [1.6, 0.25, 0.3]) * 0.6
        e = e + disc(d, [-0.50, 0.25, 0.65], 3.0, [45, 24, 9], 1.5)
    else:            # mountain moon: cool sky, moon, snow ground bounce, cold rim window
        sky = np.array([0.10, 0.14, 0.30]); hz = np.array([0.26, 0.32, 0.5]); gnd = np.array([0.38, 0.42, 0.52])
        e = np.where(up > 0, hz + (sky - hz) * up ** 0.7, gnd + (hz - gnd) * np.clip(1 + up, 0, 2) ** 2)
        e = e + disc(d, [-0.55, 0.30, 0.65], 2.0, [55, 62, 80], 1.5) + window(d, 150, 25, 60, 30, [2.0, 2.4, 3.2]) + window(d, -30, 15, 50, 20, [0.9, 1.1, 1.5])
    return np.clip(e, 0, RANGE)

def sample(env, d):
    h, w, _ = env.shape
    u = (np.arctan2(d[..., 1], d[..., 0]) / (2 * np.pi) + 0.5) % 1.0; v = np.arccos(np.clip(d[..., 2], -1, 1)) / np.pi
    x = np.clip((u * w).astype(int), 0, w - 1); y = np.clip((v * h).astype(int), 0, h - 1)
    return env[y, x]

def prefilter(env, rough, n_samples=256, seed=1):
    """split-sum specular prefilter, N = V = R, GGX importance sampling (Karis 2013)."""
    if rough < 1e-3: return env.copy()
    a = rough * rough; N = dirs(W, H); rng = np.random.default_rng(seed)
    out = np.zeros_like(env); wsum = np.zeros(env.shape[:2] + (1,))
    # tangent frame
    upv = np.where(np.abs(N[..., 2:3]) < 0.999, np.array([0, 0, 1.0]), np.array([1.0, 0, 0]))
    T = np.cross(upv, N); T /= np.linalg.norm(T, axis=-1, keepdims=True); B = np.cross(N, T)
    src = env
    # pre-blur the source for rough levels to kill fireflies
    if rough >= 0.4:
        k = int(rough * 6)
        im = Image.fromarray(np.clip(env / RANGE * 255, 0, 255).astype(np.uint8))
        src = env
    for i in range(n_samples):
        e1, e2 = rng.random(), rng.random()
        phi = 2 * np.pi * e1; cos_t = np.sqrt((1 - e2) / (1 + (a * a - 1) * e2)); sin_t = np.sqrt(1 - cos_t * cos_t)
        Hh = T * (sin_t * np.cos(phi)) + B * (sin_t * np.sin(phi)) + N * cos_t
        L = 2 * (N * Hh).sum(-1, keepdims=True) * Hh - N
        nol = np.clip((N * L).sum(-1, keepdims=True), 0, 1)
        out += sample(src, L) * nol; wsum += nol
    return out / np.maximum(wsum, 1e-4)

def irradiance(env, n_samples=1024, seed=2):
    N = dirs(W, H); rng = np.random.default_rng(seed)
    upv = np.where(np.abs(N[..., 2:3]) < 0.999, np.array([0, 0, 1.0]), np.array([1.0, 0, 0]))
    T = np.cross(upv, N); T /= np.linalg.norm(T, axis=-1, keepdims=True); B = np.cross(N, T)
    out = np.zeros_like(env)
    for i in range(n_samples):
        e1, e2 = rng.random(), rng.random()
        phi = 2 * np.pi * e1; cos_t = np.sqrt(1 - e2); sin_t = np.sqrt(e2)   # cosine-weighted
        L = T * (sin_t * np.cos(phi)) + B * (sin_t * np.sin(phi)) + N * cos_t
        out += sample(env, L)
    return out / n_samples

def encode(hdr):
    return (np.clip(hdr / RANGE, 0, 1) ** (1 / GAMMA) * 255 + 0.5).astype(np.uint8)

if __name__ == "__main__":
    worlds = [int(a) for a in sys.argv[1:]] or [1, 2, 3]
    for w in worlds:
        env = environment(w, dirs(W, H))
        rows = [encode(prefilter(env, r)) for r in LEVELS] + [encode(irradiance(env))]
        atlas = np.concatenate(rows, 0)
        out = os.path.join(ROOT, "assets/generated/env", f"world{w}_env.png"); os.makedirs(os.path.dirname(out), exist_ok=True)
        Image.fromarray(atlas).save(out); print("wrote", out, atlas.shape)
