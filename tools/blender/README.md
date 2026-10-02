# Blender tooling (headless)

Blender runs inside the cloud container as a Python module, no GUI:

```
pip install bpy==5.0.1      # needs Python 3.11; pulls numpy 1.26
python3 -c "import bpy; print(bpy.app.version_string)"
```

Scripts here are run with plain `python3` and use `bpy` directly.

- `render_ring.py <ring.glb> <out.png>`: imports a Meshy ring and renders it with Cycles (CPU, ~5 s at 512 px).

- `prepare_rings.py [ring ...]`: Meshy ring -> clean game model (`<ring>_game.glb`): weld, align to +Z, centre, scale to major radius 1, decimate to 12k tris, export. The packer prefers these. Gaps are cut per level at runtime by `RingCutter.kt` (and mirrored in `render_gles.py`).

Planned: normal-map rebake from the full-res mesh, Draco/KTX2 export, and HDRI turntable renders for the store and the Collection.
