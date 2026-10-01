#!/usr/bin/env python3
"""Regenerate docs/MESHY_3D_REPORT.md from assets/generated/3d/manifest.json (rings section)."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
m = json.loads((ROOT / "assets/generated/3d/manifest.json").read_text())
rings = m.get("rings", {})
runs = [r for r in m.get("runs", []) if "rings" in r]

lines = ["# Meshy 3D ring set report", "",
         "Generated with `tools/asset_generate/generate_rings.py` (reference image -> Meshy image-to-3D "
         "meshy-6-lite, 30k tris, triangle topology, remesh, symmetry auto -> Meshy retexture, PBR 2k). "
         "Rings only. Keys are env vars, never in the repo.", "",
         "## Shape QA rules", "",
         "- Reference image: hole (background run across the centre row) must be >= 60% of the object width; "
         "the image is regenerated with another seed / stronger prompt otherwise.",
         "- Mesh: torus fit (plane normal = thinnest bbox axis). Reject if tube_thickness / outer_radius > 0.30 "
         "or the hole is < 30% of the outer radius.", "",
         "## Rings", "",
         "| ring | status | tris | verts | outer R | tube (normal) | tube (radial) | thickness/outer | hole | credits | ref hole | ref model | textures |",
         "|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
total = 0
for name, r in rings.items():
    s = r.get("glb_stats") or r.get("mesh_glb_stats") or {}
    f = r.get("torus_fit") or {}
    total += r.get("credits_used", 0)
    lines.append(f"| {name} | {r.get('status')} | {s.get('triangles', '-')} | {s.get('vertices', '-')} | "
                 f"{f.get('outer_radius', '-')} | {f.get('tube_thickness_normal', '-')} | {f.get('tube_thickness_radial', '-')} | "
                 f"{f.get('ratio_thickness_over_outer', '-')} | {f.get('hole_fraction', '-')} | {r.get('credits_used', 0)} | "
                 f"{r.get('reference_hole_ratio', '-')} | {r.get('reference_model') or 'klein'} | {len(r.get('textures', []))} |")
lines += ["", f"Total credits recorded on rings: **{total}**", "", "## Runs", "",
          "| started | rings | balance before | balance after | spent |", "|---|---|---|---|---|"]
for run in runs:
    b, a = run.get("balance_before"), run.get("balance_after")
    lines.append(f"| {run.get('started_at', '')[:19]} | {', '.join(run['rings'])} | {b} | {a} | "
                 f"{(b - a) if a is not None else '-'} |")
lines += ["", "## Per-ring notes", ""]
for name, r in rings.items():
    lines.append(f"### {name}")
    lines.append(f"- prompt: {r.get('prompt')}")
    lines.append(f"- image-to-3d task: `{r.get('meshy_image_to_3d_task')}`; retexture task: `{r.get('meshy_retexture_task')}`")
    lines.append(f"- files: {r.get('glb')} ; textures: {', '.join(r.get('textures', []))}")
    if r.get("reference_attempts"):
        rej = [a for a in r["reference_attempts"] if a["hole_ratio"] < 0.6]
        lines.append(f"- reference attempts: {len(r['reference_attempts'])} ({len(rej)} rejected as too fat / wrong)")
    if r.get("rejected_meshes"):
        lines.append(f"- rejected meshes: {json.dumps(r['rejected_meshes'])}")
    lines.append(f"- QA: {r.get('qa_note') or r.get('error') or '-'}")
    lines.append("")
(ROOT / "docs/MESHY_3D_REPORT.md").write_text("\n".join(lines))
print("wrote docs/MESHY_3D_REPORT.md,", len(rings), "rings, total credits", total)
