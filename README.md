# Ring Revolution

Premium-cozy rotate-rings puzzle game by Shio Studios — 3 worlds, 15 chapters, 150 solver-validated levels, fully offline.
Native Android (Kotlin + Jetpack Compose) with a pure-Kotlin puzzle core ready for iOS via Kotlin Multiplatform.

## Quick start
```bash
# prerequisites: JDK 21, Android SDK (platform 36, build-tools 36), Python 3 + pillow + numpy
./gradlew :core:test                                   # engine, solver, systems, property tests
./gradlew :tools:levels:installDist && tools/levels/build/install/levels/bin/levels validate
python3 tools/asset_import/pack_runtime_assets.py      # (re)pack optimised runtime assets into app/src/main/assets
./gradlew :app:assembleDebug                           # app/build/outputs/apk/debug/app-debug.apk
scripts/verify_all.sh                                  # everything above + lint
```

## Repository layout
See `docs/ARCHITECTURE.md`. Highlights: `core/` (engine + solver + generator), `content/levels/` (150 JSON levels),
`app/` (Android), `tools/` (asset, audio and level pipelines), `assets/` (sources), `docs/` (bible, ADR, schema, solver,
asset manifest, ad configuration, privacy, test report, release checklist, implementation status).

## Monetisation safety
Ads and in-app purchases are **off by default** and use Google test identifiers only; see `docs/AD_CONFIGURATION.md`.
No API keys or signing secrets are stored in the repository (`secrets.properties` is git-ignored; pipelines read env vars).

## Game services, artwork and languages

See [owner setup and acceptance checks](docs/GAME_SERVICES_SETUP.md) and
[configuration template](services.properties.example) for Play Games, achievements, leaderboards,
Saved Games, Play Billing and AdMob. All services remain off until configured.
Custom achievement and Premium upload art is in [play-console-art](docs/play-console-art/).

The app supports English, Spanish, Brazilian Portuguese, German, French, Japanese, Korean, Hindi,
Indonesian and Arabic, plus the existing Dutch. Translation sources are in `tools/localization/`.
Run `python3 tools/localization/validate.py` to check resource and chapter coverage.

## Content regeneration
```bash
tools/levels/build/install/levels/bin/levels generate 1 150   # deterministic seeds, ~1 h
tools/levels/build/install/levels/bin/levels report           # docs/LEVEL_REPORT.md
python3 tools/level_preview/preview.py content/levels/world_01/level_00*.json
```

