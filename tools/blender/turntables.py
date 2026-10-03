"""Studio turntables of the Meshy rings in Blender Cycles (headless) for the Collection.

For each assets/generated/3d/ring_*/ring_*_game.glb: the ring stands tilted like a bangle on display and spins one full
turn in FRAMES steps under a three-light studio (warm key, cool fill, hot rim) with a soft gradient world for reflections.
Transparent film, AgX view transform. Writes assets/generated/3d/<ring>/turntable.png: a horizontal strip of FRAMES
square frames (SIZE px each), plus still.png (frame 0 at 2x size) for a hero shot.
Usage: python3 tools/blender/turntables.py [ring ...]
"""
import bpy, math, os, sys, glob
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FRAMES, SIZE, SAMPLES = 16, 256, 48

def scene_for(glb):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'; sc.cycles.device = 'CPU'; sc.cycles.samples = SAMPLES; sc.cycles.use_denoising = True
    sc.render.film_transparent = True; sc.render.resolution_x = sc.render.resolution_y = SIZE
    try: sc.view_settings.view_transform = 'AgX'; sc.view_settings.look = 'AgX - Medium High Contrast'
    except Exception: pass
    bpy.ops.import_scene.gltf(filepath=glb)
    ring = [o for o in sc.objects if o.type == 'MESH'][0]
    # lay the ring flat whatever axis convention the GLB used: its thinnest principal axis -> +Z, centred
    bpy.ops.object.select_all(action='DESELECT'); ring.select_set(True); bpy.context.view_layer.objects.active = ring
    bpy.ops.object.parent_clear(type='CLEAR_KEEP_TRANSFORM'); bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    from mathutils import Matrix
    P = np.array([v.co[:] for v in ring.data.vertices]); c = P.mean(0); Q = P - c
    _, V = np.linalg.eigh(Q.T @ Q); ax = V[:, 0]; ex = V[:, 2]; ey = np.cross(ax, ex); R = np.stack([ex, ey, ax])
    ring.data.transform(Matrix(((*R[0], -(R[0] @ c)), (*R[1], -(R[1] @ c)), (*R[2], -(R[2] @ c)), (0, 0, 0, 1))))
    pivot = bpy.data.objects.new("pivot", None); sc.collection.objects.link(pivot)
    ring.parent = pivot
    ring.rotation_euler = (math.radians(24), 0, 0)          # bangle on a jewellery cushion: gently tilted
    # world: warm studio gradient so metals have something to reflect
    w = bpy.data.worlds.new("studio"); sc.world = w; w.use_nodes = True
    nt = w.node_tree; bg = nt.nodes['Background']
    tc = nt.nodes.new('ShaderNodeTexCoord'); sep = nt.nodes.new('ShaderNodeSeparateXYZ'); ramp = nt.nodes.new('ShaderNodeValToRGB')
    nt.links.new(tc.outputs['Generated'], sep.inputs[0]); nt.links.new(sep.outputs['Z'], ramp.inputs[0])
    ramp.color_ramp.elements[0].position = 0.45; ramp.color_ramp.elements[0].color = (0.05, 0.035, 0.025, 1)
    ramp.color_ramp.elements[1].position = 0.75; ramp.color_ramp.elements[1].color = (1.0, 0.9, 0.75, 1)
    nt.links.new(ramp.outputs[0], bg.inputs['Color']); bg.inputs['Strength'].default_value = 0.9
    def area(name, loc, energy, color, size):
        d = bpy.data.lights.new(name, 'AREA'); d.energy = energy; d.color = color; d.size = size
        o = bpy.data.objects.new(name, d); sc.collection.objects.link(o); o.location = loc
        c = o.constraints.new('TRACK_TO'); c.target = pivot; c.track_axis = 'TRACK_NEGATIVE_Z'; c.up_axis = 'UP_Y'
    area("key", (2.2, -2.4, 2.6), 420, (1.0, 0.88, 0.72), 1.6)
    area("fill", (-3.0, -1.5, 0.8), 90, (0.65, 0.75, 1.0), 2.5)
    area("rim", (0.3, 3.0, 1.6), 520, (1.0, 0.75, 0.5), 1.0)
    cam_d = bpy.data.cameras.new("cam"); cam_d.lens = 70
    cam = bpy.data.objects.new("cam", cam_d); sc.collection.objects.link(cam); sc.camera = cam
    cam.location = (0, -5.0, 2.6)
    c = cam.constraints.new('TRACK_TO'); c.target = pivot; c.track_axis = 'TRACK_NEGATIVE_Z'; c.up_axis = 'UP_Y'
    return sc, pivot

def render(name):
    d = os.path.join(ROOT, "assets/generated/3d", name)
    glb = os.path.join(d, name + "_game.glb")
    if not os.path.exists(glb): print("skip", name); return
    sc, pivot = scene_for(glb)
    tmp = os.path.join(d, "_tt.png"); frames = []
    for f in range(FRAMES):
        pivot.rotation_euler = (0, 0, 2 * math.pi * f / FRAMES)
        sc.render.filepath = tmp; bpy.ops.render.render(write_still=True)
        frames.append(Image.open(tmp).convert("RGBA").copy())
    strip = Image.new("RGBA", (SIZE * FRAMES, SIZE))
    for i, im in enumerate(frames): strip.paste(im, (i * SIZE, 0))
    strip.save(os.path.join(d, "turntable.png"))
    pivot.rotation_euler = (0, 0, math.radians(20)); sc.render.resolution_x = sc.render.resolution_y = SIZE * 2; sc.cycles.samples = SAMPLES * 2
    sc.render.filepath = os.path.join(d, "still.png"); bpy.ops.render.render(write_still=True)
    os.remove(tmp); print("done", name)

if __name__ == "__main__":
    names = sys.argv[1:] or sorted(os.path.basename(p) for p in glob.glob(os.path.join(ROOT, "assets/generated/3d/ring_*")) if os.path.isdir(p))
    for n in names: render(n)
