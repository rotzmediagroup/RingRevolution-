# Blender tooling (headless)

Blender runs inside the cloud container as a Python module, no GUI:

```
pip install bpy==5.0.1      # needs Python 3.11; pulls numpy 1.26
python3 -c "import bpy; print(bpy.app.version_string)"
```

Scripts here are run with plain `python3` and use `bpy` directly.

- `render_ring.py <ring.glb> <out.png>`: imports a Meshy ring and renders it with Cycles (CPU, ~5 s at 512 px).

Planned: cut real gaps into the Meshy rings (clean end faces that keep the material), clean-up and decimation with
normal-map rebake, Draco/KTX2 export, and HDRI turntable renders for the store and the Collection.
