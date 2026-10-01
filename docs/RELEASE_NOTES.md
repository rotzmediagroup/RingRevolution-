## Dumpling Rings 1.1.8 — splash screen

- Owner key art (Ring Revolution logo, rings and the three dumpling friends) as an animated splash: fade in, gentle zoom, hold, fade out; tap to skip; portrait uses a 4:5 centre crop over a dimmed copy, landscape the full painting; respects Reduce Motion
- Source art kept in assets/imported/brand (key art) and assets/imported/sheets/sheet19 (logo + character sheet)

## Dumpling Rings 1.1.7 — new app icon

- Owner-supplied icon (three dumpling friends on interlocked rings in a bamboo steamer): legacy launcher icon with transparent corners, full-bleed adaptive foreground, cream adaptive background; store icon at docs/store_icon_512.png

## Dumpling Rings 1.1.6 — fix: black board background

- The GL board now renders into a translucent TextureView on its own EGL thread instead of a SurfaceView. A SurfaceView punches a hole in the window, so the painted scene behind it was never visible (black). The painted world scene, particles and vignette now show under the rings and the HUD stays on top

## Dumpling Rings 1.1.5 — image-based lighting (artefact-viewer quality)

- Each world has an HDR studio environment (shoji window and sun; paper lanterns and neon; moon and snow bounce), GGX-prefiltered at six roughness levels plus a cosine irradiance map (`tools/asset_generate/make_env_maps.py`)
- Rings are shaded with real image-based lighting: diffuse from irradiance, specular from the prefiltered environment through the split-sum BRDF, on top of the three direct lights. Metals mirror the room, lacquer and stone pick up soft window light, exactly as in a game's inspect-the-treasure view

## Dumpling Rings 1.1.4 — rings are rigid Meshy models

- No more per-vertex bending: every ring is the Meshy GLB placed as a rigid body (position, uniform scale, rotation) and therefore perfectly round
- Over/under at crossings is shown by a rigid lean of the whole ring (least-squares plane through the crossing heights), like interlocked bangles; shadows and end caps follow the same transform
- Ready for a free-camera 3D mode: the ring transform is a plain model matrix

## Dumpling Rings 1.1.3 — cinematic lighting

- Three-point cinematic rig per world (hot low key that slowly sweeps, dim complementary fill, strong rim from behind, dark ambient): warm morning in the teahouse, amber lantern and deep blue in the night market, moonlight on the mountain
- Clear-coat highlight lobe and a sharper softbox reflection for lustre on metal, stone and lacquer; filmic (ACES) tone mapping

## Dumpling Rings 1.1.2 — fix: board empty from the second level on

- The GL board view kept the first level's renderer (a GLSurfaceView takes one renderer); it is now recreated per level and theme

## Dumpling Rings 1.1.1 — Meshy rings drawn as modelled

- Every ring is the Meshy GLB drawn uniformly scaled to the level radius (its own tube thickness and surface detail, no re-sculpting); gaps are cut in-shader and closed with matte end caps in the model's colour
- Tag v1.1.0 was created by a cancelled build and has no release; v1.1.1 supersedes it

## Dumpling Rings 1.1.0 — real 3D rings, dramatic lighting, particles

### New in 1.1
- The board is now rendered in real 3D (OpenGL ES 3.0): dough tubes with rounded ends, a true woven over/under profile at every crossing, per-world key/fill/rim lighting with specular highlights and soft contact shadows — overlapping rings finally read unambiguously
- Every ring is now a premium Meshy AI 3D model with PBR textures: polished gold, brushed silver, rose gold, black onyx with gold inlay, jade and white marble in the campaign; bronze, obsidian, pearl and red urushi lacquer plus gold rope, beaded pearl, jade bamboo and silver wave as star-unlocked ring themes in the Collection
- Metal, stone and lacquer show real lustre: studio environment reflections, normal-mapped surfaces, sRGB-correct colour, rim-light selection glow
- Living backgrounds: slow camera drift, breathing light pool, sakura petals / lantern embers and fireflies / moon dust and snow, steam, vignette; combo bursts and sparkles on release
- Colour-gate rings carry a stripe with a per-colour dash pattern (colour-blind safe)

## Dumpling Rings 1.0.0 — Android release candidate

Premium-cozy rotate-rings puzzle by Shio Studios: 3 worlds, 15 chapters, 150 solver-validated levels, fully offline.

### Highlights
- Native Android (Kotlin, Jetpack Compose) with a pure-Kotlin puzzle core, ready for iOS via Kotlin Multiplatform
- Deterministic weave/lift-out puzzle engine, two-tier solver, hint engine, four transactional solver-validated boosters
- Mechanics: overlap, locked order, stacked rings, colour gates, restricted arcs, chains, hinges, rotating lantern arms, double gaps, chef levels
- World maps, chapter scenes, collection, daily puzzle, shop, Premium, settings, accessibility (rotate buttons, high contrast, reduce motion), NL/EN/DE
- Original music (Suno), synthesised SFX, generated hand-painted art in the Dumpling Diner style plus 1251 reused sprites
- Ads and in-app purchases are OFF by default and use Google test identifiers only (`docs/AD_CONFIGURATION.md`)

### Verification
- 150/150 levels validated: 2783 checks, 0 failures (`content/qa/validation_report.md`)
- 28 automated tests green (engine, property-based, systems, build flags, Robolectric screenshot renders); lint clean
- Not verified on hardware: no emulator or device was available during the build (`docs/TEST_REPORT.md`)

### Assets
- `DumplingRings-<tag>-debug.apk` — installable debug build
- `DumplingRings-<tag>-release.apk` / `.aab` — R8-minified; signed only when the keystore secrets are configured, otherwise unsigned

### Before store publication (owner)
Signing keystore, Play Console listing and the `dumpling_rings_premium` product, AdMob IDs and app-ads.txt, privacy policy URL, device smoke test, music-rights confirmation. See `docs/RELEASE_CHECKLIST.md`.
