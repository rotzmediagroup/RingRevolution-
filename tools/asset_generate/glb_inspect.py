#!/usr/bin/env python3
"""Minimal GLB inspector (no external libs): bounding box, vertex and triangle counts, textures.

    python3 tools/asset_generate/glb_inspect.py assets/generated/3d/dumpling/dumpling.glb
"""
import json
import struct
import sys
from pathlib import Path

COMPONENT_SIZE = {5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4}
TYPE_COUNT = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
MODE_DIVISOR = {4: 3, 5: 1, 6: 1}  # TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN


def parse_glb(path):
    data = Path(path).read_bytes()
    magic, version, length = struct.unpack_from("<4sII", data, 0)
    if magic != b"glTF":
        raise ValueError("not a GLB file")
    off = 12
    gltf, bin_chunk = None, None
    while off < length:
        clen, ctype = struct.unpack_from("<II", data, off)
        chunk = data[off + 8: off + 8 + clen]
        if ctype == 0x4E4F534A:
            gltf = json.loads(chunk.decode("utf-8"))
        elif ctype == 0x004E4942:
            bin_chunk = chunk
        off += 8 + clen
    return gltf, bin_chunk


def inspect_glb(path):
    gltf, _ = parse_glb(path)
    accessors = gltf.get("accessors", [])
    verts = tris = 0
    mins = [float("inf")] * 3
    maxs = [float("-inf")] * 3
    for mesh in gltf.get("meshes", []):
        for prim in mesh.get("primitives", []):
            pos = accessors[prim["attributes"]["POSITION"]]
            verts += pos["count"]
            if "min" in pos and "max" in pos:
                mins = [min(a, b) for a, b in zip(mins, pos["min"])]
                maxs = [max(a, b) for a, b in zip(maxs, pos["max"])]
            mode = prim.get("mode", 4)
            count = accessors[prim["indices"]]["count"] if "indices" in prim else pos["count"]
            if mode == 4:
                tris += count // 3
            elif mode in (5, 6):
                tris += max(count - 2, 0)
    size = [round(b - a, 4) for a, b in zip(mins, maxs)]
    return {
        "vertices": verts,
        "triangles": tris,
        "bbox_min": [round(v, 4) for v in mins],
        "bbox_max": [round(v, 4) for v in maxs],
        "size": size,
        "meshes": len(gltf.get("meshes", [])),
        "materials": len(gltf.get("materials", [])),
        "images": [img.get("name") or img.get("uri") or img.get("mimeType") for img in gltf.get("images", [])],
        "file_bytes": Path(path).stat().st_size,
    }


if __name__ == "__main__":
    for p in sys.argv[1:]:
        print(p, json.dumps(inspect_glb(p), indent=2))
