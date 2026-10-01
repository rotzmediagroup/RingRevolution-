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
