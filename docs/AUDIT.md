# Dumpling Rings — M0 Audit

Date: 2026-10-01 · Repository: `rotzmediagroup/RingRevolution-` (branch `ccr-69047aa3-0zxott`)

## 1. Repository state at start
- Empty git repository (no commits, no files). No existing Dumpling Diner engine or code was supplied; only asset sheets.
- Conclusion: greenfield. Engine choice recorded in `docs/ADR-001-engine.md`.

## 2. Supplied assets (owner-supplied Dumpling Diner sheets)
All 18 sheets are RGBA PNGs with real alpha. Stored unmodified in `assets/imported/sheets/`; sliced sprites in
`assets/imported/sprites/<sheet>/`, catalogued in `assets/imported/manifest.json` (see `docs/ASSET_IMPORT_REPORT.md`).

| Sheet | Size | Content | Planned use |
|---|---|---|---|
| 01 | 1254² | Crane, rabbit, tanuki, fox chibi characters (8 poses each) | Collection characters, chapter scenes, map NPCs |
| 02 | 1254² | Sakura mochi, tea bowl, bento, bao face sprites (16 each) | Level-complete reactions, world-1 decor |
| 03 | 1254² | Petals, wind chimes, koi, tea steam, lanterns, sparrow, waterfall, rope (6 frames each) | Particles & ambient animation |
| 04 | 1254² | Isometric garden tiles (grass, stone, deck, bridges, ponds, walls, props) | World-map decoration |
| 05 | 1254² | Tea, dango, bao, parfait faces + sakura badge, stamp, coin, lantern, letter icons | UI icons (coin, badge), reactions |
| 06 | 1254² | Red dragon, red panda, black cat, cloud dragon (5 poses) | World 2/3 characters, collection |
| 07 | 1254² | Night-market props: lanterns, banners, stalls, gates, tables, drums, carts | World-2 map & background dressing |
| 08 | 1254² | Cloud dumpling, star jelly, tri-dango, golden bao, rainbow cup faces (5 poses) | World-3 characters/collection |
| 09 | 1254² | Forest raccoon, moss tanuki, white fox, bamboo panda (5 poses) | World-1/3 characters |
| 10 | 1254² | Zongzi, mystery basket, jade mochi, soup, bao, rice (6 states) | Rewards, shop, collection |
| 11 | 1448×1086 | "Living Foods B" animated desserts (idle/blink/bounce/cheer/serve frames) — has baked labels | Dumpling-pop animation on release |
| 12 | 1448×1086 | "Living Foods A" animated foods — has baked labels | Same |
| 13 | 1448×1086 | Cat/panda/rabbit/fox/frog 6 emotions — has labels | Hint companion reactions |
| 14 | 1448×1086 | FX: tap hand, ripple, success ring, sparkles, steam, hearts, combo, check, coins, boosters | Gameplay FX, tutorial pointer |
| 15 | 1448×1086 | World-map meta kit: level nodes, locks, paths, bridges, water, trees, buildings, chests, stars | World map |
| 16 | 1448×1086 | Diner decor: lanterns, steamers, dishes, banners, shelves, blossoms, FX | Background dressing, basket |
| 17 | 1448×1086 | Fox/sheep/raccoon/shiba/capybara animated customers | Collection |
| 18 | 1448×1086 | Orange cat/bunny/panda/frog/gray cat animated customers | Collection |

Observations: sheets 11–18 are lower resolution (~90–130 px sprites) — adequate for icons/particles and small companions, not for hero
art; sheets 1–10 give ~150–250 px sprites, fine for cards and reactions when displayed ≤ 200 dp. Baked text labels in 11–18 are
excluded by the import pipeline. Licence: owner-supplied, reuse permitted per project bible §1/§7.

## 3. Generation APIs (verified live on 2026-10-01)
| API | Status | Notes |
|---|---|---|
| Image: `https://llm.rotz.ai/v1/images/generations` | OK | FLUX.2 Klein 9B (`klein`), FLUX.2 dev, SDXL. OpenAI-compatible, b64_json. ~40 s/image warm. |
| Music: sunoapi.org | OK (8440 credits) | Model V6, instrumental custom mode. Cloudflare needs a custom User-Agent. |
| 3D: Meshy | OK (856 credits) | Optional; the game is 2D. Pipeline script provided, used for a hero trophy model only if time permits. |

Keys are read from environment variables by the pipeline scripts and are never embedded in the app or committed.

## 4. Toolchain in the build environment
- JDK 21, Gradle 8.14.3 (wrapper generated), Kotlin 2.1.20, AGP 8.9.1.
- Android SDK installed to `/opt/android-sdk` (platform-tools, platforms;android-35, build-tools;35.0.0). No emulator / no KVM → no on-device run possible here.
- No Godot, no Flutter, no Xcode (iOS build impossible in this environment).
- Python 3.11 + Pillow + numpy, ffmpeg, ImageMagick for the asset pipeline.

## 5. Risks and decisions
- **Engine**: the bible suggests Godot for greenfield, but the owner explicitly asked for a *native Android app first, iOS later*. Chosen:
  Kotlin, Jetpack Compose + Canvas rendering, with the whole puzzle engine/solver/generator in a pure-Kotlin `core` module that has no
  Android dependency (ready to become a Kotlin Multiplatform module for iOS). See ADR-001.
- **Puzzle semantics**: a first "slide-out" formulation was prototyped and rejected (a single-gap ring can almost never slide past a wire,
  because the wire must enter and leave the ring). Final model is the deterministic *weave / lift-out* model (docs/SOLVER.md): rings
  interlock at crossings with explicit over/under patterns; a ring lifts free when every wire resting on it passes through a gap.
- **Solver cost**: branching is controlled by signature pruning; budget 2M states; levels that exceed it are rejected by the generator.
- **Audio rights**: Suno output via sunoapi.org — see docs/AUDIO_REPORT.md; the owner must confirm the plan's commercial terms.
- **Ads/IAP**: Google Mobile Ads, UMP and Play Billing SDKs are integrated behind adapters with feature flags default OFF and test IDs only.
