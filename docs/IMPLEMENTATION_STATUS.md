# Implementation status — Dumpling Rings (Android first)

Last updated: 2026-10-01 (end of the one-shot build). Status legend: ✅ built and tested · 🟡 built, verified on JVM only · ⛔ needs owner action.

## Milestones (bible §28)
| Milestone | Status | Evidence |
|---|---|---|
| M0 Audit | ✅ | docs/AUDIT.md, docs/ADR-001-engine.md, reproducible Gradle build |
| M1 Vertical slice | ✅ | levels 1–10 with tutorial cues, rotation, release, solver, undo, saves, world-1 art |
| M2 Systems | ✅/🟡 | all screens, accessibility (rotate buttons, high contrast, reduce motion, content descriptions), 4 boosters, economy, audio/haptics, daily mode, ads/IAP adapters (JVM-verified) |
| M3 Content | ✅ | 150 level files, solver validation green, 3 worlds, 15 chapter scenes, collection, NL/EN/DE |
| M4 Monetisation | 🟡 | feature-flagged AdMob/UMP/Billing with official test ids, fakes and policy tests; no live impressions/purchases possible without a device |
| M5 Release candidate | ⛔ | debug APK builds; release signing, store accounts and device smoke tests are owner actions |

## Feature checklist
### Puzzle engine & content
- [x] Deterministic weave/lift-out semantics (docs/SOLVER.md), pass tables, cascade release, combos
- [x] Mechanics: basic rotation, overlap, locked order, stacked (nested) rings, dense overlap, colour gates, restricted arcs, rotation chains, hinges, rotating lantern arms, double gaps, chef levels
- [x] Two-tier solver (BFS proven-optimal; best-first + depth-limited improvement for large levels), hint engine
- [x] Generator with per-chapter recipes and stored seeds; 150 unique topologies; par bands per difficulty
- [x] Validator & reporter CLI; CI fails on any content rule violation
- [x] Free unlimited undo, restart, resume of an in-progress level after process death
- [ ] In-engine visual level *editor* — replaced by the generator + `tools/level_preview/preview.py` (constraint overlay = crossing over/under and obstacles rendered from the same geometry). A GUI editor was not built.
- [ ] 15° step levels — supported by the engine, not used in the campaign (solver tractability); all campaign levels use 30°.

### Presentation
- [x] **v1.1: real 3D board** — GLES 3.0 torus rings with a woven height profile at every crossing (over/under physically visible), PBR-lite lighting per world (warm morning / lantern amber / moonlight), GGX specular, Fresnel, projected soft shadows, selection glow discs, domed ring ends, colour-gate stripes with per-colour dash patterns; Meshy-generated props (chopsticks, lantern gates, lantern arms, dumpling) with procedural fallbacks; release lift-and-fly animation
- [x] **v1.1: living background** — Ken-Burns drift, breathing light pool, world particles (petals / embers & fireflies / moon dust & snow), steam, vignette; combo bursts, sparkles and steam on release; all respect Reduce Motion and effect quality
- [x] Procedural 2D ring renderer (v1.0, kept as reference) with hand-painted material tiles, bevels, gap notches, crossing over/under with contact shadows, selection glow, shake feedback, release fly-out + dumpling pop + sparkles + steam, blossom petals, tutorial hand
- [x] Drag-to-rotate (one drag = one move), snapping with ticks/haptics, arc clamping during drag, linked rings follow visually, accessibility rotate buttons
- [x] Portrait phone layout; landscape/tablet layout with side panel; safe-area padding; board auto-fit
- [x] Per-world backgrounds (portrait + landscape), world maps, chapter scenes, world intros, menu key art, app icon
- [x] Original music (menu + 3 worlds + 3 chef variations + 3 stingers), 27 SFX, 3 ambience loops; volume/mute; interruption handling
- [x] Reduce Motion, high contrast, effect quality Low/Medium/High, low-memory image downsampling

### Meta & monetisation
- [x] World map (3) → chapters (15) → level nodes (150) with stars; chapter scenes; world unlock scenes
- [x] Economy: coins, star bonuses, no farmable replays; booster shop (coins only); starting inventory 3/2/1/0
- [x] Boosters: Steam Hint, Steam Peek, Chef's Twist, Golden Steamer — transactional/idempotent, solver-validated
- [x] Daily puzzle: offline, calendar-seeded, solver-validated, streak, +25 coins, Premium free daily booster claim
- [x] Collection: 15 characters, 15 decor/theme rewards + Premium ube theme; ring theme selection
- [x] Statistics, credits, privacy screen, settings (music/sfx/haptics/motion/contrast/rotate buttons/quality/language)
- [x] Save store: atomic writes, backup, corruption recovery, migrations, in-progress level
- [x] AdsPolicy (caps, tutorial/chef/first-session/premium/consent exclusions), rewarded flow with idempotent grant, interstitial only between screens, no reserved ad space, Premium removes everything
- [x] Play Billing adapter: purchase, pending, cancel, restore, acknowledged, offline entitlement cache never cleared by network failure
- [x] UMP consent adapter + privacy options entry

## Verified vs. not verified
* Verified on the JVM: engine, solver, generator, systems, 150-level validation, Compose screens rendered with real assets (Robolectric native graphics), debug APK build, build flags default OFF.
* **Not verified** (no emulator/device in the build environment): touch feel and 60 fps on hardware, audio playback on device, real test-ad impressions, sandbox purchases, TalkBack walkthrough, release signing. See docs/TEST_REPORT.md and docs/RELEASE_CHECKLIST.md.

## Known limitations / follow-ups
1. Par values for 7–12 ring levels are solver *upper bounds* (not proven minimal) — 3 stars can only be easier than intended, never harder.
2. The world-1 map painting contains one tiny chibi figure and chapter scene 13 a small blank plaque (generated art; owner may regenerate with `tools/asset_generate/generate_art.py --only ...`).
3. Music rights depend on the sunoapi.org/Suno plan (docs/AUDIO_REPORT.md).
4. iOS: `core` is Android-free and KMP-ready; the SwiftUI shell and renderer port are future work (scripts/build_ios.sh).
5. Banner ads are intentionally not implemented beyond the policy flag (default OFF); the design has no banner slot.
6. 3D (Meshy) assets were not needed for the 2D game and were not generated.
