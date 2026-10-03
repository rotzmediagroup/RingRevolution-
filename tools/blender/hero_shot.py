"""Hero shot of the whole ring collection on dark velvet (Blender Cycles, headless).

Rings are scattered in a loose overlapping fan on a velvet cushion, a warm key and hot rims make the metals glow, shallow
depth of field focuses the middle. Outputs assets/generated/hero/rings_hero.png (1536x1024) and
docs/store_feature_1024x500.png (Play Store feature graphic crop).
Usage: python3 tools/blender/hero_shot.py [samples]
"""
import bpy, math, os, sys, glob, random
import numpy as np
from mathutils import Matrix

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SAMPLES = int(sys.argv[1]) if len(sys.argv) > 1 else 96
ORDER = ["ring_gold_rope", "ring_jade", "ring_onyx", "ring_rose_gold", "ring_pearl_beaded", "ring_lacquer", "ring_silver_wave",
         "ring_marble", "ring_bronze", "ring_jade_bamboo", "ring_obsidian", "ring_gold", "ring_pearl", "ring_silver"]

bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
sc.render.engine = 'CYCLES'; sc.cycles.device = 'CPU'; sc.cycles.samples = SAMPLES; sc.cycles.use_denoising = True
sc.render.resolution_x, sc.render.resolution_y = 1536, 1024
try: sc.view_settings.view_transform = 'AgX'; sc.view_settings.look = 'AgX - Medium High Contrast'
except Exception: pass

def flat(obj):
    bpy.ops.object.select_all(action='DESELECT'); obj.select_set(True); bpy.context.view_layer.objects.active = obj
    bpy.ops.object.parent_clear(type='CLEAR_KEEP_TRANSFORM'); bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    P = np.array([v.co[:] for v in obj.data.vertices]); c = P.mean(0); Q = P - c
    _, V = np.linalg.eigh(Q.T @ Q); ax = V[:, 0]; ex = V[:, 2]; ey = np.cross(ax, ex); R = np.stack([ex, ey, ax])
    obj.data.transform(Matrix(((*R[0], -(R[0] @ c)), (*R[1], -(R[1] @ c)), (*R[2], -(R[2] @ c)), (0, 0, 0, 1))))
    zmin = min(v.co.z for v in obj.data.vertices); return -zmin

rng = random.Random(7)
for k, name in enumerate(ORDER):
    glb = os.path.join(ROOT, "assets/generated/3d", name, name + "_game.glb")
    if not os.path.exists(glb): continue
    before = set(sc.objects); bpy.ops.import_scene.gltf(filepath=glb)
    obj = [o for o in sc.objects if o not in before and o.type == 'MESH'][0]
    lift = flat(obj)
    # loose golden-angle spiral pile: centre rings sit higher and lean on their neighbours
    ang = k * 2.39996; rad = 0.98 * math.sqrt(k + 0.4)
    x = rad * math.cos(ang) * 1.3; y = rad * math.sin(ang) * 0.8 + 0.8
    tilt = math.radians(rng.uniform(6, 18) if k else 4)
    obj.rotation_euler = (tilt, rng.uniform(-0.25, 0.25), rng.uniform(0, math.tau))
    obj.location = (x, y, lift + 0.05 + max(0.0, 0.55 - 0.1 * k))

# velvet cushion
bpy.ops.mesh.primitive_plane_add(size=40, location=(0, 0, 0)); plane = bpy.context.object
m = bpy.data.materials.new("velvet"); m.use_nodes = True; b = m.node_tree.nodes["Principled BSDF"]
b.inputs["Base Color"].default_value = (0.05, 0.006, 0.01, 1); b.inputs["Roughness"].default_value = 0.98
for key in ("Sheen Weight", "Sheen"):
    if key in b.inputs: b.inputs[key].default_value = 0.15; break
plane.data.materials.append(m)

w = bpy.data.worlds.new("w"); sc.world = w; w.use_nodes = True
w.node_tree.nodes["Background"].inputs[0].default_value = (0.35, 0.25, 0.18, 1); w.node_tree.nodes["Background"].inputs[1].default_value = 0.04
target = bpy.data.objects.new("target", None); sc.collection.objects.link(target); target.location = (0.0, 0.8, 0.3)
def area(name, loc, energy, color, size):
    d = bpy.data.lights.new(name, 'AREA'); d.energy = energy; d.color = color; d.size = size
    o = bpy.data.objects.new(name, d); sc.collection.objects.link(o); o.location = loc
    c = o.constraints.new('TRACK_TO'); c.target = target; c.track_axis = 'TRACK_NEGATIVE_Z'; c.up_axis = 'UP_Y'
area("key", (-5, -6, 7), 1500, (1.0, 0.86, 0.68), 4)
area("rim", (3, 9, 3), 3000, (1.0, 0.7, 0.45), 2.5)
area("fill", (8, -4, 2), 250, (0.6, 0.7, 1.0), 6)
cam_d = bpy.data.cameras.new("cam"); cam_d.lens = 58; cam_d.dof.use_dof = True; cam_d.dof.focus_object = target; cam_d.dof.aperture_fstop = 2.2
cam = bpy.data.objects.new("cam", cam_d); sc.collection.objects.link(cam); sc.camera = cam; cam.location = (0.4, -13.5, 7.0)
c = cam.constraints.new('TRACK_TO'); c.target = target; c.track_axis = 'TRACK_NEGATIVE_Z'; c.up_axis = 'UP_Y'

out = os.path.join(ROOT, "assets/generated/hero/rings_hero.png"); os.makedirs(os.path.dirname(out), exist_ok=True)
sc.render.filepath = out; bpy.ops.render.render(write_still=True)
from PIL import Image
im = Image.open(out).convert("RGB"); w_, h_ = im.size; ch = int(w_ * 500 / 1024)
im.crop((0, (h_ - ch) // 2, w_, (h_ - ch) // 2 + ch)).resize((1024, 500), Image.LANCZOS).save(os.path.join(ROOT, "docs/store_feature_1024x500.png"))
print("hero", out)
