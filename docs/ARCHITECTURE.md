# Architecture

```
core/        pure Kotlin (JVM today, KMP-ready): model, geometry, engine, solver, hint, generator, systems
tools/levels JVM CLI: generate / validate / report
tools/*      Python pipelines: asset_import (sheet slicing, runtime packing), asset_generate (image API), audio_generate, level_preview
app/         Android (Kotlin, Jetpack Compose, Canvas renderer, SoundPool/MediaPlayer, AdMob+UMP+Billing adapters)
content/     150 level JSONs, worlds/chapters/cosmetics/economy/roadmap, qa/ (solver metadata, validation reports)
assets/      imported sheets + sliced sprites, generated art, audio (sources kept separately from runtime assets)
docs/        this documentation set
```

## Modules and responsibilities (bible §26)
| Module | Package / file | Notes |
|---|---|---|
| RingGeometry | `core/geometry/Geometry.kt` | crossings, wire sampling, lift test, pass tables |
| RotationController / Release rules | `core/engine/Rules.kt` (`RuleEngine`) | locks, links, arcs, obstacles, cascade release, legal actions |
| Action dispatcher | `core/engine/Rules.kt` (`GameSession`) | single entry point for all state mutations; free unlimited undo, restart, restore |
| LevelLoader | `core/content/LevelCodec.kt`, `app/content/ContentRepository.kt` | kotlinx.serialization JSON, campaign layout helpers |
| Solver | `core/solver/Solver.kt` | BFS + best-first/DLS fallback, verify, encode/decode moves |
| HintEngine | `core/hint/HintEngine.kt` | solver from the *current* state; canonical fast path; free block explanations |
| Progression / Economy / Boosters | `core/systems/Systems.kt` | pure functions over `SaveData`; idempotent ledger (`requestId`) |
| DailyPuzzle | `core/systems/Systems.kt` + `app/ui/screens/MetaScreens.kt` | calendar-day seed → template level with re-rolled, solver-validated angles |
| SaveStore | `app/save/SaveStore.kt` | atomic write + backup + corruption recovery + migrations |
| AudioHaptics | `app/audio/AudioManager.kt` | SFX cooldown/limiting, looping music + ambience, focus handling, haptics |
| AssetCatalog | `app/assets/AssetCatalog.kt` | LRU bitmap cache, low-memory downsampling |
| AdsManager / ConsentManager / PurchaseManager | `app/DumplingRingsApp.kt`, `app/platform/*.kt` | adapters + fakes; feature flags default OFF; test ids only |
| Presentation | `app/ui/board/BoardCanvas.kt` | procedural ring renderer (exact hitboxes), input, FX |
| Screens | `app/ui/screens/*.kt`, `app/MainActivity.kt` | back-stack navigation, portrait/landscape layouts, safe areas |

## Key design rules
* **Determinism**: all puzzle logic is integer/discrete; sampling is fixed-step; RNG is xorshift64* with stored seeds.
* **One dispatcher**: gameplay, boosters, hints, replay, save-restore and the solver all use `RuleEngine.apply`.
* **Rendering = logic**: the renderer draws gaps, crossings and over/under straight from the engine's geometry.
* **Offline-first**: no backend; all content ships in the APK; ads/IAP are optional adapters behind flags.
* **Secrets**: no API keys, production ad ids or signing secrets in git. Build-time config via env / `secrets.properties`.

## iOS path
`core` has no Android imports. Convert it to a Kotlin Multiplatform module (`commonMain`), expose `RuleEngine`,
`GameSession`, `Solver`, `HintEngine`, `Systems` to Swift, and re-implement `BoardCanvas` (≈600 lines) in SwiftUI/Metal
plus the screens. Content JSON, audio and art are shared as-is.
