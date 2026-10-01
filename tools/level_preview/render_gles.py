#!/usr/bin/env python3
"""Headless render of a level with the app's real GLES shaders (extracted from Shaders.kt) on Mesa llvmpipe via EGL.
Mirrors Board3DRenderer: camera, torus meshes, weave bumps, shadows, materials, lighting. Props are drawn as capsules.
Usage: python3 tools/level_preview/render_gles.py content/levels/world_01/level_025.json out.png [world] [w] [h]
"""
import ctypes, json, math, os, re, sys
import numpy as np
from PIL import Image
os.environ.setdefault("PYOPENGL_PLATFORM", "egl")
from OpenGL import EGL
from OpenGL.EGL import *
from OpenGL import GL
from OpenGL.GL import *

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

def shaders():
    s = open(os.path.join(ROOT, "app/src/main/java/com/shiostudios/dumplingrings/ui/board3d/Shaders.kt")).read()
    d = {n: b.replace("$MAX_BUMPS", "24") for n, b in re.findall(r'val (\w+) = """(.*?)"""', s, flags=re.S)}
    d["SHADOW_VS"] = d["RING_VS"].replace("+ n * r;   // local", "+ n * r * 1.8;   // local").replace("vec3 p = uTilt * pl + vec3(uCenter, uZ0);", "vec3 p = uTilt * pl + vec3(uCenter, uZ0); p.xy += vec2(0.35, -0.25) * p.z; p.z = 0.0015;")
    return d

# ---------------- camera (mirror of BoardCamera)
def look_at(eye, c, up):
    f = c - eye; f /= np.linalg.norm(f); s = np.cross(f, up); s /= np.linalg.norm(s); u = np.cross(s, f)
    m = np.eye(4, dtype=np.float32); m[0, :3] = s; m[1, :3] = u; m[2, :3] = -f
    m[0, 3] = -s @ eye; m[1, 3] = -u @ eye; m[2, 3] = f @ eye
    return m
def perspective(fov, aspect, n, fa):
    f = 1 / math.tan(math.radians(fov) / 2); m = np.zeros((4, 4), np.float32)
    m[0, 0] = f / aspect; m[1, 1] = f; m[2, 2] = (fa + n) / (n - fa); m[2, 3] = 2 * fa * n / (n - fa); m[3, 2] = -1
    return m
def camera(w, h, tilt=26.0, fov=34.0):
    aspect = w / h; dist = (0.56 / math.tan(math.radians(fov / 2))) * (1 / aspect if aspect < 1 else 1)
    t = math.radians(tilt); eye = np.array([0.5, 0.5 - dist * math.sin(t), dist * math.cos(t)], np.float32)
    return perspective(fov, aspect, 0.1, 10) @ look_at(eye, np.array([0.5, 0.5, 0], np.float32), np.array([0, 0, 1], np.float32)), eye

# ---------------- mesh (mirror of TorusMesh)
def wire_segments(r):
    gaps = sorted([(g["startDeg"] % 360, g["widthDeg"]) for g in r["gaps"]])
    out = []; cur = 0.0; wrap = 0.0
    for s, w in gaps:
        if s > cur: out.append((cur, s))
        cur = max(cur, s + w)
    if cur < 360: out.append((cur, 360.0))
    else: wrap = cur - 360
    if wrap > 0 and out: out[0] = (max(out[0][0], wrap), out[0][1])
    if len(out) >= 2 and out[-1][1] >= 360 - 1e-6 and out[0][0] <= 1e-6:
        return [(out[-1][0], out[0][1] + 360)] + out[1:-1]
    return [s for s in out if s[1] - s[0] > 0.5]

def build(r, seg_per_deg=0.45, sides=16):
    verts = []; idx = []
    uscale = 2 * math.pi * r["radius"] / (r["thickness"] * 3.2)
    for a0, a1 in wire_segments(r):
        span = a1 - a0; n = max(6, int(span * seg_per_deg)); base = len(verts) // 5
        capRings = 4; capDeg = math.degrees(r["thickness"] * 0.5 / r["radius"]) * 0.95; total = n + 1 + 2 * capRings
        for i in range(total):
            if i < capRings: capT = i / capRings; ang = a0 + capT * capDeg
            elif i > n + capRings: k = i - (n + capRings); capT = 1 - k / capRings; ang = a1 - capT * capDeg
            else: capT = 1.0; ang = (a0 + capDeg) + (span - 2 * capDeg) * (i - capRings) / n
            capScale = math.sqrt(max(0.0, 1 - (1 - capT) ** 2))
            u = math.radians(ang) * uscale / (2 * math.pi)
            for s in range(sides + 1):
                verts += [math.radians(ang), 2 * math.pi * s / sides, u, s / sides, 1.0 if capT >= 1 else (-capScale if i < capRings else capScale)]
        stride = sides + 1
        for i in range(total - 1):
            for s in range(sides):
                a = base + i * stride + s; b = a + stride
                idx += [a, b, a + 1, a + 1, b, b + 1]
    return np.array(verts, np.float32), np.array(idx, np.uint16)

def build_caps(r, sides=16):
    verts = []; idx = []; uscale = 2 * math.pi * r["radius"] / (r["thickness"] * 3.2); capRings = 5
    capDeg = math.degrees(r["thickness"] * 0.5 / r["radius"]) * 0.95
    for a0, a1 in wire_segments(r):
        for end in (0, 1):
            base = len(verts) // 5
            for i in range(capRings + 1):
                capT = i / capRings; ang = a0 + capT * capDeg if end == 0 else a1 - capT * capDeg
                cs = math.sqrt(max(0.0, 1 - (1 - capT) ** 2)); signed = 1.0 if capT >= 1 else (-cs if end == 0 else cs)
                u = math.radians(ang) * uscale / (2 * math.pi)
                for s_ in range(sides + 1): verts += [math.radians(ang), 2 * math.pi * s_ / sides, u, s_ / sides, signed]
            stride = sides + 1
            for i in range(capRings):
                for s_ in range(sides):
                    a = base + i * stride + s_; b = a + stride; idx += [a, b, a + 1, a + 1, b, b + 1]
    return np.array(verts, np.float32), np.array(idx, np.uint16)

def crossings(a, b):
    dx, dy = b["center"][0]-a["center"][0], b["center"][1]-a["center"][1]; d = math.hypot(dx, dy)
    if d < 1e-9 or d > a["radius"]+b["radius"] or d < abs(a["radius"]-b["radius"]): return []
    x = (d*d + a["radius"]**2 - b["radius"]**2)/(2*d); h2 = a["radius"]**2 - x*x
    if h2 < 0: return []
    h = math.sqrt(h2); ux, uy = dx/d, dy/d; px, py = a["center"][0]+ux*x, a["center"][1]+uy*x
    pts = [(px-uy*h, py+ux*h), (px+uy*h, py-ux*h)]
    out = [(math.atan2(p[1]-a["center"][1], p[0]-a["center"][0]) % (2*math.pi), math.atan2(p[1]-b["center"][1], p[0]-b["center"][0]) % (2*math.pi)) for p in pts]
    return sorted(out)

# ---------------- Meshy ring meshes (mirror of GlbLoader + RingModel)
import struct, io
def load_glb(path):
    b = open(path, "rb").read(); ln = struct.unpack("<I", b[12:16])[0]; j = json.loads(b[20:20 + ln]); bl = struct.unpack("<I", b[20 + ln:24 + ln])[0]; bin_ = b[28 + ln:28 + ln + bl]
    acc = j["accessors"]; views = j["bufferViews"]
    def accf(i):
        a = acc[i]; v = views[a["bufferView"]]; comps = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[a["type"]]
        off = v.get("byteOffset", 0) + a.get("byteOffset", 0); stride = v.get("byteStride", comps * 4)
        if stride == comps * 4: return np.frombuffer(bin_, np.float32, a["count"] * comps, off).reshape(-1, comps)
        return np.array([[struct.unpack_from("<f", bin_, off + i * stride + c * 4)[0] for c in range(comps)] for i in range(a["count"])], np.float32)
    prim = j["meshes"][0]["primitives"][0]; at = prim["attributes"]
    pos = accf(at["POSITION"]); nrm = accf(at["NORMAL"]) if "NORMAL" in at else None; uv = accf(at["TEXCOORD_0"]) if "TEXCOORD_0" in at else np.zeros((len(pos), 2), np.float32)
    ia = acc[prim["indices"]]; iv = views[ia["bufferView"]]; ioff = iv.get("byteOffset", 0) + ia.get("byteOffset", 0)
    idx = np.frombuffer(bin_, np.uint32 if ia["componentType"] == 5125 else np.uint16, ia["count"], ioff).astype(np.uint32)
    imgs = {}
    mat = j["materials"][prim["material"]] if "material" in prim else {}
    def img(ref):
        if not ref: return None
        src = j["textures"][ref["index"]]["source"]; im = j["images"][src]; v = views[im["bufferView"]]
        return Image.open(io.BytesIO(bin_[v.get("byteOffset", 0):v.get("byteOffset", 0) + v["byteLength"]])).convert("RGB")
    pbr = mat.get("pbrMetallicRoughness", {})
    return pos, nrm, uv, idx, img(pbr.get("baseColorTexture")), img(pbr.get("metallicRoughnessTexture")), img(mat.get("normalTexture")), pbr.get("metallicFactor", 1.0), pbr.get("roughnessFactor", 1.0)

def ring_model(pos, nrm, uv):
    mn = pos.min(0); mx = pos.max(0); ext = mx - mn; axis = int(np.argmin(ext)); c = (mn + mx) / 2
    ax = {0: [1, 2, 0], 1: [0, 2, 1], 2: [0, 1, 2]}[axis]
    p = pos - c; px, py, pz = p[:, ax[0]], p[:, ax[1]], p[:, ax[2]]
    rho = np.hypot(px, py); major = (rho.max() + rho.min()) / 2; minor = max((rho.max() - rho.min()) / 2, ext[axis] / 2, 1e-4)
    phi = np.arctan2(py, px); rN = (rho - major) / minor; zN = pz / minor
    if nrm is not None:
        nx, ny, nz = nrm[:, ax[0]], nrm[:, ax[1]], nrm[:, ax[2]]; cr, sr = np.cos(phi), np.sin(phi)
        nR = nx * cr + ny * sr; nT = -nx * sr + ny * cr; nZ = nz
    else: nR = np.zeros_like(phi); nT = np.zeros_like(phi); nZ = np.ones_like(phi)
    v = np.stack([phi, rN, zN, uv[:, 0], uv[:, 1], nR, nT, nZ, np.zeros_like(phi)], 1).astype(np.float32)
    return v, minor / rho.max()

def compile_prog(vs, fs):
    def sh(t, src):
        s = glCreateShader(t); glShaderSource(s, src); glCompileShader(s)
        if not glGetShaderiv(s, GL_COMPILE_STATUS): raise RuntimeError(glGetShaderInfoLog(s).decode())
        return s
    p = glCreateProgram(); glAttachShader(p, sh(GL_VERTEX_SHADER, vs)); glAttachShader(p, sh(GL_FRAGMENT_SHADER, fs)); glLinkProgram(p)
    if not glGetProgramiv(p, GL_LINK_STATUS): raise RuntimeError(glGetProgramInfoLog(p).decode())
    return p

LIGHTS = {1: ([-0.55, 0.3, 0.7], [1.65, 1.35, 1.0], [0.7, -0.25, 0.4], [0.2, 0.27, 0.42], [0.25, -0.85, 0.45], [1.3, 1.0, 0.8], [0.42, 0.48, 0.62], [0.16, 0.11, 0.07]),
          2: ([-0.5, 0.25, 0.65], [1.75, 0.95, 0.42], [0.7, -0.35, 0.4], [0.14, 0.17, 0.45], [0.15, -0.85, 0.5], [1.45, 0.5, 0.28], [0.2, 0.15, 0.32], [0.09, 0.05, 0.04]),
          3: ([-0.55, 0.3, 0.65], [1.2, 1.35, 1.75], [0.7, -0.3, 0.4], [0.18, 0.22, 0.4], [0.25, -0.85, 0.45], [0.9, 1.1, 1.45], [0.26, 0.32, 0.55], [0.06, 0.08, 0.14])}
MATTEX = {"dough_sesame": "sesame_dough", "dough_matcha": "matcha", "dough_beet": "beet_pink", "dough_ube": "ube_purple", "dough_gold": "gold", "dough_bamboo": "bamboo"}

def main():
    lv = json.load(open(sys.argv[1])); out = sys.argv[2]; world = int(sys.argv[3]) if len(sys.argv) > 3 else 1
    W = int(sys.argv[4]) if len(sys.argv) > 4 else 720; H = int(sys.argv[5]) if len(sys.argv) > 5 else 900
    dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY); eglInitialize(dpy, None, None)
    cfg_attr = [EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_DEPTH_SIZE, 16, EGL_NONE]
    cfgs = (EGLConfig * 1)(); n = EGLint()
    eglChooseConfig(dpy, (EGLint * len(cfg_attr))(*cfg_attr), cfgs, 1, n)
    eglBindAPI(EGL_OPENGL_ES_API)
    ctx = eglCreateContext(dpy, cfgs[0], EGL_NO_CONTEXT, (EGLint * 3)(EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE))
    surf = eglCreatePbufferSurface(dpy, cfgs[0], (EGLint * 5)(EGL_WIDTH, W, EGL_HEIGHT, H, EGL_NONE))
    eglMakeCurrent(dpy, surf, surf, ctx)
    print("GL:", glGetString(GL_VERSION).decode())
    S = shaders()
    ringP = compile_prog(S["RING_VS"], S["RING_FS"]); shadowP = compile_prog(S["SHADOW_VS"], S["SHADOW_FS"]); propP = compile_prog(S["PROP_VS"], S["PROP_FS"]); glbP = compile_prog(S["GLBRING_VS"], S["GLBRING_FS"])
    RINGS = os.environ.get("RING_GLB")   # optional: one Meshy ring GLB for all rings; default = the app's per-material mapping
    RING_MAP = {"dough_sesame": "ring_silver", "dough_matcha": "ring_jade", "dough_beet": "ring_rose_gold", "dough_ube": "ring_onyx", "dough_gold": "ring_gold", "dough_bamboo": "ring_marble"}
    def tex(im, repeat=True):
        if im is None: return 0
        im = im.resize((1024, 1024)); t = glGenTextures(1); glBindTexture(GL_TEXTURE_2D, t)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 1024, 1024, 0, GL_RGB, GL_UNSIGNED_BYTE, np.array(im, np.uint8).tobytes()); glGenerateMipmap(GL_TEXTURE_2D)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT); return t
    def load_ring(path):
        pos, nrm, uv, idx, base, mr, nm, mf, rf = load_glb(path); rv, ratio = ring_model(pos, nrm, uv); print("ring glb:", os.path.basename(path), len(pos), "verts, thickness ratio %.3f" % ratio)
        tube = float(rv[:, 1].max() - rv[:, 1].min()) / 2.0  # tube radius in tube units (~1)
        return dict(v=rv, idx=idx, base=tex(base), mr=tex(mr), nm=tex(nm), mf=mf, rf=rf, ratio=ratio, avg=(np.array(base.convert('RGB'))[(np.mod(uv[::50, 1], 1.0) * (base.height - 1)).astype(int), (np.mod(uv[::50, 0], 1.0) * (base.width - 1)).astype(int)]) if False else (np.median(np.array(base.convert('RGB'))[(np.mod(uv[::50, 1], 1.0) * (base.height - 1)).astype(int), (np.mod(uv[::50, 0], 1.0) * (base.width - 1)).astype(int)], axis=0) / 255 if base is not None and uv is not None else np.array([0.8, 0.7, 0.5])))
    glbs = {}
    if RINGS and os.path.exists(RINGS):
        one = load_ring(RINGS); glbs = {m: one for m in RING_MAP}
    elif not os.environ.get("NO_GLB"):
        for m, name in RING_MAP.items():
            path = os.path.join(ROOT, "app/src/main/assets/3d/rings", name + ".glb")
            if os.path.exists(path): glbs[m] = load_ring(path)
    glViewport(0, 0, W, H); glEnable(GL_DEPTH_TEST); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
    glClearColor(0.80, 0.62, 0.42, 1.0); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
    vp, eye = camera(W, H)
    rings = lv["rings"]
    # fit
    x0 = min(r["center"][0] - r["radius"] - r["thickness"] for r in rings); x1 = max(r["center"][0] + r["radius"] + r["thickness"] for r in rings)
    y0 = min(r["center"][1] - r["radius"] - r["thickness"] for r in rings); y1 = max(r["center"][1] + r["radius"] + r["thickness"] for r in rings)
    k = min(max(0.92 / max(x1 - x0, y1 - y0, 0.2), 1.0), 1.8); cx = (x0 + x1) / 2; cy = (y0 + y1) / 2
    weaves = {(w["a"], w["b"]): w["pattern"] for w in lv.get("weaves", [])}
    bumps = {r["id"]: [] for r in rings}
    for i, a in enumerate(rings):
        for j, b in enumerate(rings):
            if i >= j: continue
            cr = crossings(a, b)
            if len(cr) < 2: continue
            pat = weaves.get((a["id"], b["id"]), "alt1")
            for idx, (angA, angB) in enumerate(cr):
                a_over = {"alt1": idx == 0, "alt2": idx == 1, "a_over": True, "b_over": False}[pat]
                bumps[a["id"]].append((angA, (1 if a_over else -1) * a["thickness"] * k * 0.5 * 1.15))
                bumps[b["id"]].append((angB, (-1 if a_over else 1) * b["thickness"] * k * 0.5 * 1.15))
    textures = {}
    for mid, name in MATTEX.items():
        im = Image.open(os.path.join(ROOT, f"assets/generated/materials/mat_{name}.png")).convert("RGB").resize((256, 256))
        t = glGenTextures(1); glBindTexture(GL_TEXTURE_2D, t)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 256, 256, 0, GL_RGB, GL_UNSIGNED_BYTE, np.array(im, np.uint8).tobytes())
        glGenerateMipmap(GL_TEXTURE_2D); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
        textures[mid] = t
    L = LIGHTS[world]
    def set_lights(p):
        for name, val in zip(["uKeyDir", "uKeyCol", "uFillDir", "uFillCol", "uRimDir", "uRimCol", "uAmbientSky", "uAmbientGround"], L): glUniform3f(glGetUniformLocation(p, name), *val)
        glUniform3f(glGetUniformLocation(p, "uEye"), *eye)
    meshes = [build(r) for r in rings]
    def tube_ratio(r):
        g = glbs.get(r["materialId"]); return g["ratio"] / (1.0 - g["ratio"]) if g else r["thickness"] * 0.5 / r["radius"]   # tube radius / major radius
    caps = [build_caps(dict(r, thickness=2.0 * r["radius"] * tube_ratio(r))) for r in rings]
    def draw_glb_ring(i, r, sel=False):
        p = glbP; glUseProgram(p); u = lambda n: glGetUniformLocation(p, n)
        glb = glbs[r["materialId"]]; tr = tube_ratio(r)
        v = glb["v"]; ix = glb["idx"].astype(np.uint32)
        vbo = glGenBuffers(1); glBindBuffer(GL_ARRAY_BUFFER, vbo); glBufferData(GL_ARRAY_BUFFER, v.nbytes, v, GL_STATIC_DRAW)
        ibo = glGenBuffers(1); glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo); glBufferData(GL_ELEMENT_ARRAY_BUFFER, ix.nbytes, ix, GL_STATIC_DRAW)
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_FLOAT, False, 36, ctypes.c_void_p(0))
        glEnableVertexAttribArray(1); glVertexAttribPointer(1, 2, GL_FLOAT, False, 36, ctypes.c_void_p(12))
        glEnableVertexAttribArray(2); glVertexAttribPointer(2, 3, GL_FLOAT, False, 36, ctypes.c_void_p(20))
        placement(p, r); glUniform1f(u("uMinor"), r["radius"] * k * tr); set_lights(p)   # uniform scale of the Meshy model
        for unit, (name, t) in enumerate([("uAlbedo", glb["base"]), ("uMetalRough", glb["mr"]), ("uNormalMap", glb["nm"])]):
            glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(GL_TEXTURE_2D, t); glUniform1i(u(name), unit)
        glUniform1f(u("uHasMR"), 1.0 if glb["mr"] else 0.0); glUniform1f(u("uHasNormal"), 1.0 if glb["nm"] else 0.0)
        glUniform1f(u("uMetalFactor"), glb["mf"]); glUniform1f(u("uRoughFactor"), glb["rf"])
        glUniform3f(u("uEmissive"), *((0.2, 0.15, 0.03) if sel else (0, 0, 0))); glUniform1f(u("uAlpha"), 1); glUniform1f(u("uDarken"), 0); glUniform1f(u("uStripeOn"), 0); glUniform3f(u("uStripe"), 0, 0, 0)
        capRad = tr * 0.95
        gaps = np.zeros(8, np.float32)
        for gi, g in enumerate(r["gaps"][:4]): gaps[gi * 2] = math.radians(g["startDeg"]) + capRad; gaps[gi * 2 + 1] = max(0.0, math.radians(g["widthDeg"]) - 2 * capRad)
        glUniform1i(u("uGapCount"), min(4, len(r["gaps"]))); glUniform2fv(u("uGaps"), 4, gaps); glUniform1f(u("uCapRad"), capRad)
        glDrawElements(GL_TRIANGLES, len(ix), GL_UNSIGNED_INT, ctypes.c_void_p(0))
        glDisableVertexAttribArray(1); glDisableVertexAttribArray(2)
        # caps
        p = ringP; glUseProgram(p); u = lambda n: glGetUniformLocation(p, n)
        cv, cix = caps[i]
        vbo = glGenBuffers(1); glBindBuffer(GL_ARRAY_BUFFER, vbo); glBufferData(GL_ARRAY_BUFFER, cv.nbytes, cv, GL_STATIC_DRAW)
        ibo = glGenBuffers(1); glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo); glBufferData(GL_ELEMENT_ARRAY_BUFFER, cix.nbytes, cix, GL_STATIC_DRAW)
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 4, GL_FLOAT, False, 20, ctypes.c_void_p(0))
        glEnableVertexAttribArray(1); glVertexAttribPointer(1, 1, GL_FLOAT, False, 20, ctypes.c_void_p(16))
        placement(p, r); glUniform1f(u("uMinor"), r["radius"] * k * tr); set_lights(p)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, white); glUniform1i(u("uAlbedo"), 0)
        glUniform3f(u("uTint"), *glb["avg"]); glUniform1f(u("uRough"), 0.72)
        glUniform3f(u("uEmissive"), 0, 0, 0); glUniform1f(u("uAlpha"), 1); glUniform1f(u("uDarken"), 0); glUniform1f(u("uStripeOn"), 0)
        glDrawElements(GL_TRIANGLES, len(cix), GL_UNSIGNED_SHORT, ctypes.c_void_p(0))
    def placement(p, r):
        u = lambda n: glGetUniformLocation(p, n)
        glUniformMatrix4fv(u("uViewProj"), 1, True, vp)
        glUniform2f(u("uCenter"), 0.5 + (r["center"][0] - cx) * k, 0.5 + (r["center"][1] - cy) * k)
        glUniform1f(u("uMajor"), r["radius"] * k); glUniform1f(u("uMinor"), r["thickness"] * k * 0.5)
        glUniform1f(u("uRot"), math.radians(r["initialAngleDeg"])); glUniform1f(u("uLift"), 0); glUniform2f(u("uSlide"), 0, 0); glUniform1f(u("uScale"), 1)
        R = r["radius"] * k; mn = r["thickness"] * k * 0.5
        pts = [(math.cos(ba) * R, math.sin(ba) * R, bh) for ba, bh in bumps[r["id"]]]
        sxx = sum(x * x for x, y, z in pts); syy = sum(y * y for x, y, z in pts); sxy = sum(x * y for x, y, z in pts); sxz = sum(x * z for x, y, z in pts); syz = sum(y * z for x, y, z in pts)
        det = sxx * syy - sxy * sxy; a = b = 0.0
        if det > 1e-12: a = (sxz * syy - syz * sxy) / det; b = (syz * sxx - sxz * sxy) / det
        elif sxx + syy > 1e-12: g = (sxz + syz) / max(sxx + syy + 2 * sxy, 1e-12); a = b = g
        ms = 1.6 * mn / R; mag = math.hypot(a, b)
        if mag > ms: a *= ms / mag; b *= ms / mag
        nx, ny = -a, -b; l = math.sqrt(nx * nx + ny * ny + 1.0); x, y, z = nx / l, ny / l, 1.0 / l; kk = 1.0 / (1.0 + z)
        m = np.array([[1 - x * x * kk, -x * y * kk, x], [-x * y * kk, 1 - y * y * kk, y], [-x, -y, z]], np.float32)   # GL row-major view
        glUniformMatrix3fv(u("uTilt"), 1, True, m); glUniform1f(u("uZ0"), mn * 1.02 + 0.55 * math.hypot(a, b) * R)
    white = glGenTextures(1); glBindTexture(GL_TEXTURE_2D, white); glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 2, 2, 0, GL_RGB, GL_UNSIGNED_BYTE, bytes([255] * 12)); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
    def draw_ring(p, i, r, shadow, sel=False):
        v, ix = meshes[i]
        vbo = glGenBuffers(1); glBindBuffer(GL_ARRAY_BUFFER, vbo); glBufferData(GL_ARRAY_BUFFER, v.nbytes, v, GL_STATIC_DRAW)
        ibo = glGenBuffers(1); glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo); glBufferData(GL_ELEMENT_ARRAY_BUFFER, ix.nbytes, ix, GL_STATIC_DRAW)
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 4, GL_FLOAT, False, 20, ctypes.c_void_p(0))
        glEnableVertexAttribArray(1); glVertexAttribPointer(1, 1, GL_FLOAT, False, 20, ctypes.c_void_p(16))
        u = lambda n: glGetUniformLocation(p, n)
        glUniformMatrix4fv(u("uViewProj"), 1, True, vp)
        glUniform2f(u("uCenter"), 0.5 + (r["center"][0] - cx) * k, 0.5 + (r["center"][1] - cy) * k)
        glUniform1f(u("uMajor"), r["radius"] * k); glUniform1f(u("uMinor"), r["thickness"] * k * 0.5)
        glUniform1f(u("uRot"), math.radians(r["initialAngleDeg"])); glUniform1f(u("uLift"), 0); glUniform2f(u("uSlide"), 0, 0); glUniform1f(u("uScale"), 1)
        R = r["radius"] * k; mn = r["thickness"] * k * 0.5
        pts = [(math.cos(ba) * R, math.sin(ba) * R, bh) for ba, bh in bumps[r["id"]]]
        sxx = sum(x * x for x, y, z in pts); syy = sum(y * y for x, y, z in pts); sxy = sum(x * y for x, y, z in pts); sxz = sum(x * z for x, y, z in pts); syz = sum(y * z for x, y, z in pts)
        det = sxx * syy - sxy * sxy; a = b = 0.0
        if det > 1e-12: a = (sxz * syy - syz * sxy) / det; b = (syz * sxx - sxz * sxy) / det
        elif sxx + syy > 1e-12: g = (sxz + syz) / max(sxx + syy + 2 * sxy, 1e-12); a = b = g
        ms = 1.6 * mn / R; mag = math.hypot(a, b)
        if mag > ms: a *= ms / mag; b *= ms / mag
        nx, ny = -a, -b; l = math.sqrt(nx * nx + ny * ny + 1.0); x, y, z = nx / l, ny / l, 1.0 / l; kk = 1.0 / (1.0 + z)
        m = np.array([[1 - x * x * kk, -x * y * kk, x], [-x * y * kk, 1 - y * y * kk, y], [-x, -y, z]], np.float32)   # GL row-major view
        glUniformMatrix3fv(u("uTilt"), 1, True, m); glUniform1f(u("uZ0"), mn * 1.02 + 0.55 * math.hypot(a, b) * R)
        if shadow: glUniform1f(u("uAlpha"), 0.32)
        else:
            set_lights(p); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, textures.get(r["materialId"], textures["dough_sesame"])); glUniform1i(u("uAlbedo"), 0)
            glUniform3f(u("uTint"), 1, 1, 1); glUniform1f(u("uRough"), 0.28 if r["materialId"] == "dough_gold" else 0.58)
            glUniform3f(u("uEmissive"), *( (0.2, 0.15, 0.03) if sel else (0, 0, 0))); glUniform1f(u("uAlpha"), 1); glUniform1f(u("uDarken"), 1.0 if r.get("lockedBy") else 0.0)
            col = {"red": (0.88, 0.32, 0.28), "jade": (0.3, 0.72, 0.56), "ube": (0.6, 0.45, 0.82)}.get(r.get("colorId"), (0.4, 0.25, 0.15))
            glUniform3f(u("uStripe"), *col); glUniform1f(u("uStripeOn"), 1.0 if r.get("colorId") else 0.0)
        glDrawElements(GL_TRIANGLES, len(ix), GL_UNSIGNED_SHORT, ctypes.c_void_p(0))
    glDepthMask(GL_FALSE); glUseProgram(shadowP)
    for i, r in enumerate(rings): draw_ring(shadowP, i, r, True)
    glDepthMask(GL_TRUE); glUseProgram(ringP)
    for i, r in enumerate(rings):
        if r["materialId"] in glbs: draw_glb_ring(i, r, sel=(i == 0))
        else: draw_ring(ringP, i, r, False, sel=(i == 0))
    glFinish()
    data = glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE)
    img = Image.frombytes("RGBA", (W, H), data).transpose(Image.FLIP_TOP_BOTTOM)
    img.save(out); print(out)

if __name__ == "__main__": main()
