# Meshy 3D ring set report

Generated with `tools/asset_generate/generate_rings.py` (reference image -> Meshy image-to-3D meshy-6-lite, 30k tris, triangle topology, remesh, symmetry auto -> Meshy retexture, PBR 2k). Rings only. Keys are env vars, never in the repo.

## Shape QA rules

- Reference image: hole (background run across the centre row) must be >= 60% of the object width; the image is regenerated with another seed / stronger prompt otherwise.
- Mesh: torus fit (plane normal = thinnest bbox axis). Reject if tube_thickness / outer_radius > 0.30 or the hole is < 30% of the outer radius.

## Rings

| ring | status | tris | verts | outer R | tube (normal) | tube (radial) | thickness/outer | hole | credits | ref hole | ref model | textures |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| ring_gold | SUCCEEDED | 31476 | 19929 | 0.969 | 0.0683 | 0.1107 | 0.071 | 0.886 | 15 | 0.754 | klein | 4 |
| ring_silver | SUCCEEDED | 31415 | 19256 | 1.0469 | 0.1527 | 0.3105 | 0.146 | 0.703 | 15 | 0.718 | klein | 4 |
| ring_rose_gold | SUCCEEDED | 30290 | 24357 | 0.9994 | 0.1545 | 0.2358 | 0.155 | 0.764 | 15 | 0.664 | klein | 4 |
| ring_onyx | SUCCEEDED | 31120 | 18890 | 0.9785 | 0.24 | 0.2546 | 0.245 | 0.74 | 15 | 0.718 | flux2 | 4 |
| ring_jade | SUCCEEDED | 31358 | 19089 | 0.9882 | 0.2358 | 0.2773 | 0.239 | 0.719 | 15 | 0.713 | flux2 | 4 |
| ring_marble | SUCCEEDED | 31374 | 19005 | 0.9972 | 0.1944 | 0.2566 | 0.195 | 0.743 | 15 | 0.818 | flux2 | 4 |
| ring_bronze | SUCCEEDED | 31348 | 19291 | 0.951 | 0.1016 | 0.1034 | 0.107 | 0.891 | 15 | 0.742 | flux2 | 4 |
| ring_obsidian | SUCCEEDED | 31390 | 19031 | 0.9983 | 0.1975 | 0.2593 | 0.198 | 0.74 | 15 | 0.801 | flux2 | 4 |
| ring_pearl | SUCCEEDED | 31373 | 18910 | 1.0018 | 0.1982 | 0.2666 | 0.198 | 0.734 | 15 | 0.843 | flux2 | 4 |
| ring_lacquer | SUCCEEDED | 31344 | 19642 | 1.024 | 0.1828 | 0.2816 | 0.179 | 0.725 | 15 | 0.811 | flux2 | 4 |
| ring_gold_rope | REFERENCE_READY | - | - | - | - | - | - | - | 0 | 0.76 | flux2 | 0 |
| ring_pearl_beaded | REFERENCE_READY | - | - | - | - | - | - | - | 0 | 0.837 | flux2 | 0 |
| ring_jade_bamboo | REFERENCE_READY | - | - | - | - | - | - | - | 0 | 0.787 | flux2 | 0 |
| ring_silver_wave | REFERENCE_READY | - | - | - | - | - | - | - | 0 | 0.753 | flux2 | 0 |

Total credits recorded on rings: **150**

## Runs

| started | rings | balance before | balance after | spent |
|---|---|---|---|---|
| 2026-10-01T14:13:41 | ring_gold, ring_silver, ring_rose_gold | 841 | 796 | 45 |
| 2026-10-01T14:37:15 | ring_onyx, ring_jade, ring_marble | 796 | 751 | 45 |
| 2026-10-01T14:38:47 | ring_bronze, ring_obsidian, ring_pearl, ring_lacquer | 751 | 691 | 60 |

## Per-ring notes

### ring_gold
- prompt: a very thin delicate circular bangle bracelet ring (slim torus) with a very large open hole, the tube is slim like a wire: tube thickness about one eighth of the ring radius, perfectly round, lying flat on a plain pure white background seen from a three-quarter top view, product photography, studio lighting, high detail, no text, nothing else in the image, made of polished 24k gold with a subtle engraved cloud pattern on the surface, no face, no decoration other than the surface material, single object only, no shadow, no other objects
- image-to-3d task: `01a0f7d0-5f39-7604-bcdc-cca81c794ba0`; retexture task: `01a0f7d0-ac2e-7505-ad8b-7db35d814a81`
- files: assets/generated/3d/ring_gold/ring_gold.glb ; textures: assets/generated/3d/ring_gold/ring_gold_base_color.png, assets/generated/3d/ring_gold/ring_gold_metallic.png, assets/generated/3d/ring_gold/ring_gold_roughness.png, assets/generated/3d/ring_gold/ring_gold_normal.png
- reference attempts: 4 (3 rejected as too fat / wrong)
- QA: thin torus OK (thickness/outer 0.071, hole 0.886), 31476 tris, 4 PBR maps; VISUAL: very slim tube (ratio 0.07), engraved cloud pattern only faintly visible

### ring_silver
- prompt: a thin smooth circular ring (torus) like a bracelet, the hole is wide: tube thickness about one sixth of the ring radius, perfectly round, lying flat on a plain pure white background seen from a three-quarter top view, product photography, studio lighting, high detail, no text, made of brushed sterling silver with a polished mirror rim, no face, no decoration other than the surface material, single object only, no shadow, no other objects
- image-to-3d task: `01a0f7d0-5edf-77da-a666-dabd86f4b086`; retexture task: `01a0f7d0-aba3-77ef-84d6-8a934f960617`
- files: assets/generated/3d/ring_silver/ring_silver.glb ; textures: assets/generated/3d/ring_silver/ring_silver_base_color.png, assets/generated/3d/ring_silver/ring_silver_metallic.png, assets/generated/3d/ring_silver/ring_silver_roughness.png, assets/generated/3d/ring_silver/ring_silver_normal.png
- reference attempts: 2 (1 rejected as too fat / wrong)
- QA: thin torus OK (thickness/outer 0.146, hole 0.703), 31415 tris, 4 PBR maps

### ring_rose_gold
- prompt: a thin smooth circular ring (torus) like a bracelet, the hole is wide: tube thickness about one sixth of the ring radius, perfectly round, lying flat on a plain pure white background seen from a three-quarter top view, product photography, studio lighting, high detail, no text, made of polished rose gold, no face, no decoration other than the surface material, single object only, no shadow, no other objects
- image-to-3d task: `01a0f7d0-5f21-76bb-8014-990fad803893`; retexture task: `01a0f7d0-a9d9-7157-95b2-2de6ad03d5ff`
- files: assets/generated/3d/ring_rose_gold/ring_rose_gold.glb ; textures: assets/generated/3d/ring_rose_gold/ring_rose_gold_base_color.png, assets/generated/3d/ring_rose_gold/ring_rose_gold_metallic.png, assets/generated/3d/ring_rose_gold/ring_rose_gold_roughness.png, assets/generated/3d/ring_rose_gold/ring_rose_gold_normal.png
- reference attempts: 1 (0 rejected as too fat / wrong)
- QA: thin torus OK (thickness/outer 0.155, hole 0.764), 30290 tris, 4 PBR maps; VISUAL: tube cross-section is slightly faceted/angular instead of round (acceptable at game scale, candidate for a re-run)

### ring_onyx
- prompt: a thin circular hoop like a large keyring, the hoop surface made of polished black onyx stone with a thin inlaid gold line running around it, very slim uniform round tube, huge open hole in the middle, the tube is only one tenth of the ring radius thick, perfectly circular, lying flat on a pure white background seen from a three-quarter top view, product photography, studio lighting, high detail, no text, no face, no decoration other than the surface material, single object only, no shadow, no other objects
- image-to-3d task: `01a0f7e5-eec3-7632-a64f-25ab64d7db70`; retexture task: `01a0f7e6-78ec-7614-9fb0-498d004399c9`
- files: assets/generated/3d/ring_onyx/ring_onyx.glb ; textures: assets/generated/3d/ring_onyx/ring_onyx_base_color.png, assets/generated/3d/ring_onyx/ring_onyx_metallic.png, assets/generated/3d/ring_onyx/ring_onyx_roughness.png, assets/generated/3d/ring_onyx/ring_onyx_normal.png
- reference attempts: 15 (13 rejected as too fat / wrong)
- QA: thin torus OK (thickness/outer 0.245, hole 0.74), 31120 tris, 4 PBR maps

### ring_jade
- prompt: a thin circular hoop like a large keyring, the hoop surface made of translucent green jade stone with a fine shallow carved line pattern on the surface, very slim uniform round tube, huge open hole in the middle, the tube is only one tenth of the ring radius thick, perfectly circular, lying flat on a pure white background seen from a three-quarter top view, product photography, studio lighting, high detail, no text, no face, no decoration other than the surface material, single object only, no shadow, no other objects
- image-to-3d task: `01a0f7e5-eeb5-731c-9887-2b834d98fb4f`; retexture task: `01a0f7e6-7898-72d2-b657-e0815646f75b`
- files: assets/generated/3d/ring_jade/ring_jade.glb ; textures: assets/generated/3d/ring_jade/ring_jade_base_color.png, assets/generated/3d/ring_jade/ring_jade_metallic.png, assets/generated/3d/ring_jade/ring_jade_roughness.png, assets/generated/3d/ring_jade/ring_jade_normal.png
- reference attempts: 7 (6 rejected as too fat / wrong)
- QA: thin torus OK (thickness/outer 0.239, hole 0.719), 31358 tris, 4 PBR maps

### ring_marble
- prompt: a thin circular hoop like a large keyring, the hoop surface made of polished white marble with grey veins, very slim uniform round tube, huge open hole in the middle, the tube is only one tenth of the ring radius thick, perfectly circular, lying flat on a pure white background seen from a three-quarter top view, product photography, studio lighting, high detail, no text, no face, no decoration other than the surface material, single object only, no shadow, no other objects
- image-to-3d task: `01a0f7e5-eeb4-7081-8db0-69579f11c543`; retexture task: `01a0f7e6-79a1-7632-b4bf-97ab4d840ffc`
- files: assets/generated/3d/ring_marble/ring_marble.glb ; textures: assets/generated/3d/ring_marble/ring_marble_base_color.png, assets/generated/3d/ring_marble/ring_marble_metallic.png, assets/generated/3d/ring_marble/ring_marble_roughness.png, assets/generated/3d/ring_marble/ring_marble_normal.png
- reference attempts: 1 (0 rejected as too fat / wrong)
- QA: thin torus OK (thickness/outer 0.195, hole 0.743), 31374 tris, 4 PBR maps

### ring_bronze
- prompt: a thin circular hoop like a large keyring, the hoop surface made of antique bronze with green patina highlights, very slim uniform round tube, huge open hole in the middle, the tube is only one tenth of the ring radius thick, perfectly circular, lying flat on a pure white background seen from a three-quarter top view, product photography, studio lighting, high detail, no text, no face, no decoration other than the surface material, single object only, no shadow, no other objects
- image-to-3d task: `01a0f7e7-5854-7620-9d0d-5f580a0a8f84`; retexture task: `01a0f7e7-e15e-70ab-8b09-87fb61bd535a`
- files: assets/generated/3d/ring_bronze/ring_bronze.glb ; textures: assets/generated/3d/ring_bronze/ring_bronze_base_color.png, assets/generated/3d/ring_bronze/ring_bronze_metallic.png, assets/generated/3d/ring_bronze/ring_bronze_roughness.png, assets/generated/3d/ring_bronze/ring_bronze_normal.png
- reference attempts: 1 (0 rejected as too fat / wrong)
- QA: thin torus OK (thickness/outer 0.107, hole 0.891), 31348 tris, 4 PBR maps

### ring_obsidian
- prompt: a thin circular hoop like a large keyring, the hoop surface made of glossy black volcanic obsidian glass, very slim uniform round tube, huge open hole in the middle, the tube is only one tenth of the ring radius thick, perfectly circular, lying flat on a pure white background seen from a three-quarter top view, product photography, studio lighting, high detail, no text, no face, no decoration other than the surface material, single object only, no shadow, no other objects
- image-to-3d task: `01a0f7e7-58af-74ff-8412-1b2d4dafbdcb`; retexture task: `01a0f7e7-e3a7-74d8-b7e9-6b242a0b0d5d`
- files: assets/generated/3d/ring_obsidian/ring_obsidian.glb ; textures: assets/generated/3d/ring_obsidian/ring_obsidian_base_color.png, assets/generated/3d/ring_obsidian/ring_obsidian_metallic.png, assets/generated/3d/ring_obsidian/ring_obsidian_roughness.png, assets/generated/3d/ring_obsidian/ring_obsidian_normal.png
- reference attempts: 1 (0 rejected as too fat / wrong)
- QA: thin torus OK (thickness/outer 0.198, hole 0.74), 31390 tris, 4 PBR maps

### ring_pearl
- prompt: a thin circular hoop like a large keyring, the hoop surface made of iridescent pearl mother-of-pearl ceramic, very slim uniform round tube, huge open hole in the middle, the tube is only one tenth of the ring radius thick, perfectly circular, lying flat on a pure white background seen from a three-quarter top view, product photography, studio lighting, high detail, no text, no face, no decoration other than the surface material, single object only, no shadow, no other objects
- image-to-3d task: `01a0f7e7-5880-7017-b7aa-02c2a6bb3792`; retexture task: `01a0f7e7-e1ed-7564-976c-d5cf525e979a`
- files: assets/generated/3d/ring_pearl/ring_pearl.glb ; textures: assets/generated/3d/ring_pearl/ring_pearl_base_color.png, assets/generated/3d/ring_pearl/ring_pearl_metallic.png, assets/generated/3d/ring_pearl/ring_pearl_roughness.png, assets/generated/3d/ring_pearl/ring_pearl_normal.png
- reference attempts: 1 (0 rejected as too fat / wrong)
- QA: thin torus OK (thickness/outer 0.198, hole 0.734), 31373 tris, 4 PBR maps; VISUAL: iridescent sheen mostly lost, reads as plain off-white ceramic (consider re-texture with text style prompt)

### ring_lacquer
- prompt: a thin circular hoop like a large keyring, the hoop surface made of glossy deep red urushi lacquer sprinkled with gold flakes, very slim uniform round tube, huge open hole in the middle, the tube is only one tenth of the ring radius thick, perfectly circular, lying flat on a pure white background seen from a three-quarter top view, product photography, studio lighting, high detail, no text, no face, no decoration other than the surface material, single object only, no shadow, no other objects
- image-to-3d task: `01a0f7e8-b4fc-7523-ac65-a4a61987c275`; retexture task: `01a0f7e9-0347-75d4-82ab-7d59a5648a3b`
- files: assets/generated/3d/ring_lacquer/ring_lacquer.glb ; textures: assets/generated/3d/ring_lacquer/ring_lacquer_base_color.png, assets/generated/3d/ring_lacquer/ring_lacquer_metallic.png, assets/generated/3d/ring_lacquer/ring_lacquer_roughness.png, assets/generated/3d/ring_lacquer/ring_lacquer_normal.png
- reference attempts: 1 (0 rejected as too fat / wrong)
- QA: thin torus OK (thickness/outer 0.179, hole 0.725), 31344 tris, 4 PBR maps

### ring_gold_rope
- prompt: a thin circular hoop like a large keyring, the hoop surface made of polished gold twisted like a rope, twisted-rope texture along the whole tube, very slim uniform round tube, huge open hole in the middle, the tube is only one tenth of the ring radius thick, perfectly circular, lying flat on a pure white background seen from a three-quarter top view, product photography, studio lighting, high detail, no text, no face, no decoration other than the surface material, single object only, no shadow, no other objects
- image-to-3d task: `None`; retexture task: `None`
- files: None ; textures: 
- reference attempts: 1 (0 rejected as too fat / wrong)
- QA: reference approved (thin torus), Meshy run NOT started: awaiting owner go-ahead (~15 credits each)

### ring_pearl_beaded
- prompt: a thin circular hoop like a large keyring, the hoop surface made of a string of small round white pearls, beads touching each other all around the circle, very slim uniform round tube, huge open hole in the middle, the tube is only one tenth of the ring radius thick, perfectly circular, lying flat on a pure white background seen from a three-quarter top view, product photography, studio lighting, high detail, no text, no face, no decoration other than the surface material, single object only, no shadow, no other objects
- image-to-3d task: `None`; retexture task: `None`
- files: None ; textures: 
- reference attempts: 1 (0 rejected as too fat / wrong)
- QA: reference approved (thin torus), Meshy run NOT started: awaiting owner go-ahead (~15 credits each)

### ring_jade_bamboo
- prompt: a thin circular hoop like a large keyring, the hoop surface made of green jade carved as bamboo segments with raised nodes around the tube, very slim uniform round tube, huge open hole in the middle, the tube is only one tenth of the ring radius thick, perfectly circular, lying flat on a pure white background seen from a three-quarter top view, product photography, studio lighting, high detail, no text, no face, no decoration other than the surface material, single object only, no shadow, no other objects
- image-to-3d task: `None`; retexture task: `None`
- files: None ; textures: 
- reference attempts: 1 (0 rejected as too fat / wrong)
- QA: reference approved (thin torus), Meshy run NOT started: awaiting owner go-ahead (~15 credits each)

### ring_silver_wave
- prompt: a thin circular hoop like a large keyring, the hoop surface made of sterling silver with an engraved wave relief pattern along the tube, very slim uniform round tube, huge open hole in the middle, the tube is only one tenth of the ring radius thick, perfectly circular, lying flat on a pure white background seen from a three-quarter top view, product photography, studio lighting, high detail, no text, no face, no decoration other than the surface material, single object only, no shadow, no other objects
- image-to-3d task: `None`; retexture task: `None`
- files: None ; textures: 
- reference attempts: 1 (0 rejected as too fat / wrong)
- QA: reference approved (thin torus), Meshy run NOT started: awaiting owner go-ahead (~15 credits each)
