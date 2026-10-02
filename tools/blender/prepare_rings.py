"""Turn each Meshy ring into a clean game model with Blender (headless, `pip install bpy==5.0.1`).

For every assets/generated/3d/ring_*/ring_*.glb (the textured Meshy download, never modified):
  1. import, join to one mesh, weld duplicate vertices (UV seams stay as per-loop UVs)
  2. align: principal-axis fit puts the ring axis on +Z, centre at the origin, major radius scaled to 1
  3. decimate (collapse) to ~TARGET_TRIS triangles, keeping UV seams and the silhouette; smooth normals
  4. export assets/generated/3d/<ring>/<ring>_game.glb (single mesh, single PBR material, textures embedded)
Usage: python3 tools/blender/prepare_rings.py [ring_name ...]
"""
import bpy, bmesh, numpy as np, os, sys, glob, json
from mathutils import Matrix, Vector

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TARGET_TRIS = 12000

def prepare(src, dst):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=src)
    meshes = [o for o in bpy.context.scene.objects if o.type == 'MESH']
    bpy.ops.object.select_all(action='DESELECT')
    for o in meshes: o.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    if len(meshes) > 1: bpy.ops.object.join()
    obj = bpy.context.view_layer.objects.active
    bpy.ops.object.parent_clear(type='CLEAR_KEEP_TRANSFORM')
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    me = obj.data
    tris_in = sum(len(p.vertices) - 2 for p in me.polygons)

    # weld duplicates (glTF splits vertices on UV seams; Blender keeps the seams as per-loop UVs)
    bm = bmesh.new(); bm.from_mesh(me)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5 * max(obj.dimensions))
    bm.to_mesh(me); bm.free()

    # principal axes: smallest-variance axis = ring axis -> +Z; centre -> origin; major radius -> 1
    P = np.array([v.co[:] for v in me.vertices]); c = P.mean(0); Q = P - c
    w, V = np.linalg.eigh(Q.T @ Q)
    axis = V[:, 0]; ex = V[:, 2]; ey = np.cross(axis, ex)
    R = np.stack([ex, ey, axis])                  # rows: new x, y, z
    L = Q @ R.T
    rho = np.hypot(L[:, 0], L[:, 1]); major = 0.5 * (rho.max() + rho.min())
    s = 1.0 / major
    M = Matrix(((R[0, 0] * s, R[0, 1] * s, R[0, 2] * s, -(R[0] @ c) * s),
                (R[1, 0] * s, R[1, 1] * s, R[1, 2] * s, -(R[1] @ c) * s),
                (R[2, 0] * s, R[2, 1] * s, R[2, 2] * s, -(R[2] @ c) * s),
                (0, 0, 0, 1)))
    me.transform(M)

    # decimate, keep UV seams
    ratio = min(1.0, TARGET_TRIS / max(tris_in, 1))
    if ratio < 0.98:
        mod = obj.modifiers.new("dec", 'DECIMATE'); mod.decimate_type = 'COLLAPSE'; mod.ratio = ratio
        mod.use_collapse_triangulate = True
        bpy.ops.object.modifier_apply(modifier=mod.name)
    for p in me.polygons: p.use_smooth = True
    tris_out = sum(len(p.vertices) - 2 for p in me.polygons)

    L = np.array([v.co[:] for v in me.vertices]); rho = np.hypot(L[:, 0], L[:, 1])
    stats = dict(tris_in=tris_in, tris_out=tris_out, minor=float((rho.max() - rho.min()) / 2), height=float(L[:, 2].max() - L[:, 2].min()))
    bpy.ops.object.select_all(action='DESELECT'); obj.select_set(True)
    bpy.ops.export_scene.gltf(filepath=dst, export_format='GLB', use_selection=True, export_apply=True,
                              export_texcoords=True, export_normals=True, export_tangents=False,
                              export_materials='EXPORT', export_image_format='AUTO', export_yup=False)
    return stats

if __name__ == "__main__":
    names = sys.argv[1:] or sorted(os.path.basename(d) for d in glob.glob(os.path.join(ROOT, "assets/generated/3d/ring_*")) if os.path.isdir(d))
    report = {}
    for n in names:
        src = os.path.join(ROOT, "assets/generated/3d", n, n + ".glb")
        if not os.path.exists(src): print("skip", n); continue
        dst = os.path.join(ROOT, "assets/generated/3d", n, n + "_game.glb")
        st = prepare(src, dst); report[n] = st
        print(n, st, os.path.getsize(dst) // 1024, "KB")
    json.dump(report, open(os.path.join(ROOT, "assets/generated/3d/game_models.json"), "w"), indent=1)
