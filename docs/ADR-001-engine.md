# ADR-001 — Engine and platform architecture

**Status:** accepted · **Date:** 2026-10-01

## Context
The repository was empty. The project bible (§16) recommends Godot 4.x for greenfield unless a present codebase argues otherwise, and
the owner's instruction for this build is explicit: *"we need to make a native Android app out of this and later iOS. Android first."*
The build environment has JDK 21, Gradle and an Android SDK, but no Godot binary, no emulator and no Xcode.

## Decision
1. **Native Android app in Kotlin** (`app` module): Jetpack Compose for every screen, `Canvas` (Skia-backed) for the puzzle board,
   `SoundPool`/`MediaPlayer` for audio, DataStore/atomic files for saves, official Google Mobile Ads + UMP + Play Billing SDKs behind
   adapter interfaces with fake providers for tests.
2. **Pure-Kotlin `core` module** with zero Android dependencies: ring model, geometry, rule engine, action dispatcher, solver, hint
   engine, generator, progression/economy/booster/daily logic. It compiles for the JVM today and is laid out so it can be switched to a
   Kotlin Multiplatform module (`commonMain`) to serve a SwiftUI iOS shell later. All content (150 levels, worlds, chapters, cosmetics,
   economy, locales) is JSON shared by both platforms.
3. **`tools/levels` JVM CLI** (generator, validator, reporter) and Python asset/audio pipelines that are never shipped in the app.

## Consequences
- + Exactly what the owner asked for (native Android first), with official ad/billing SDKs instead of third-party Godot plugins.
- + Deterministic puzzle logic is unit-tested on the JVM in seconds; the solver runs on-device for hints.
- + Clear path to iOS via KMP without rewriting the puzzle engine or content.
- − No shared renderer with iOS: the Compose board renderer must be re-implemented in SwiftUI/Metal later (the renderer is ~1 file,
  driven entirely by core state, to keep that port small).
- − No emulator in the build environment: on-device smoke tests are a manual step for the owner (see docs/TEST_REPORT.md).
