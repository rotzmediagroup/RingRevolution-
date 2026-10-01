# Test report

Environment: Linux x86_64 cloud container, JDK 21.0.11, Gradle 8.14.3, Kotlin 2.1.20, AGP 8.9.1, Android SDK platform 35 /
build-tools 35.0.0, Python 3.11, Robolectric 4.14.1 (native graphics, SDK 33 runtime). **No Android emulator or device was
available** (no KVM); everything below ran on the JVM. A desktop/JVM run does not prove an Android/iOS release build on hardware.

| Date (UTC) | Command | Result | Notes |
|---|---|---|---|
| 2026-10-01 | `./gradlew :core:test` | ✅ 18/18 | RulesTest (6), SystemsTest (8), PropertyTest (4: generated puzzles solvable + replay + undo exactness, hints reach a win, Golden Steamer keeps solvability, RNG determinism) |
| 2026-10-01 | `levels generate 1 150` | ✅ 150 files | deterministic seeds; ~75 min total; recipe for chapter 15 trimmed after L143 found no candidate (see docs/SOLVER.md) |
| 2026-10-01 | `levels validate` (run 1, pre-tightening validator) | 2710 checks, 58 "par mismatch" | every level solvable, canonical solutions replay, unique topologies; mismatches were loose/bounded pars from the generation-time solver budget |
| 2026-10-01 | `levels validate` (run 2, tightening validator, BFS 600k / fallback 200k) | ✅ 2803 checks, 0 failures | 36 level files had their par tightened to the shorter solution found (written back) |
| 2026-10-01 | `levels validate` (run 3, confirmation on final files) | see `content/qa/validation_report.md` | started after repacking; result recorded in the file (the validator is deterministic, so run 3 reproduces run 2) |
| 2026-10-01 | `levels report` | ✅ | docs/LEVEL_REPORT.md |
| 2026-10-01 | `python3 tools/asset_import/check_runtime_assets.py` | ✅ | 78 references, 755 manifest entries, 150 packed levels |
| 2026-10-01 | `./gradlew :app:testDebugUnitTest` | ✅ 10/10 | BuildFlagsTest (3: ads/IAP off, test ids) + ScreenshotTest (7: 23 real-asset renders of menu, welcome, world maps, chapter grid, 10 gameplay levels, world-2 transition pixel assertion, collection/shop/premium/settings/daily/chapter scene) |
| 2026-10-01 | `./gradlew :app:lintDebug` | ✅ 0 errors, 82 warnings | two real findings fixed: `java.time` on minSdk 24, unremembered state |
| 2026-10-01 | `./gradlew :app:assembleDebug` | ✅ | `app/build/outputs/apk/debug/app-debug.apk` (48.8 MB) |
| 2026-10-01 | `./gradlew :app:assembleRelease` | ✅ unsigned | R8 + resource shrinking OK, `app-release-unsigned.apk` (37.5 MB); signing needs owner keystore |
| — | Instrumented tests / device smoke tests | ⛔ not run | no emulator/device in the environment |
| — | Live test-ad impressions, sandbox purchases, UMP form | ⛔ not run | requires device + Play/AdMob accounts; adapters compile and are policy-tested with fakes |

## Property-based coverage (bible §27)
* every generated puzzle is solvable without boosters; solver actions are legal; replay of the canonical solution ends in a win; undo restores the exact previous state — `PropertyTest`
* rotation around 0°/360°, gap intervals, locks, dependencies, release, win condition, undo, reset — `RulesTest`
* serialisation/migration, reward idempotency, Premium/ads policy — `SystemsTest`
* content: exactly 150 levels, 15 chef levels, assets exist, no duplicate topology, mechanics introduced before combination, all UI keys in NL/EN/DE (string tables generated from one source) — `levels validate`, `check_runtime_assets.py`

## Visual QA
Screenshots rendered on the JVM with the real pipeline are in `docs/screenshots/` (phone portrait 411×914 dp). Observed and fixed
during this pass: board auto-fit for small clusters, HUD legibility pill, background not updating between worlds, cosmetic id
collision between the two foxes. Known cosmetic notes are listed in docs/IMPLEMENTATION_STATUS.md.

## Performance
Not measured on hardware. Design budgets: ≤ 12 rings × ≤ 4 arcs per ring per frame plus ≤ 14 petal sprites; material shaders
cached; images LRU-cached with low-memory downsampling; solver work for hints/boosters/daily runs off the main thread.
Owner action: profile on a mid-range device with effect quality High during a cascade (release checklist).
