# Release checklist (Android first, iOS later)

## Owner actions required before publication
- [ ] **Signing**: create an upload keystore; export `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`; run `scripts/build_android.sh`.
- [ ] **Play Console**: app listing, content rating questionnaire (puzzle, no violence; decide age rating), Data Safety form (see docs/PRIVACY.md), privacy policy URL.
- [ ] **In-app product** `dumpling_rings_premium` (one-time) with localised prices; then `IAP_ENABLED=true`.
- [ ] **AdMob** app + units, `app-ads.txt`; then `ADMOB_*` ids, `ADS_USE_TEST_IDS=false`, `ADS_ENABLED=true` — only after the explicit release decision; keep banners off.
- [ ] **Audio rights**: confirm the sunoapi.org / Suno plan grants commercial use for the 10 generated tracks (docs/AUDIO_REPORT.md).
- [ ] **Device smoke test** on real phones/tablets (no emulator was available in the build environment): see the matrix in docs/TEST_REPORT.md.
- [ ] Store screenshots and promo art (sources in `assets/generated/`).

## Automated gates (run `scripts/verify_all.sh`)
- [x] 150 campaign levels present, solvable without boosters, par verified, unique topology, mechanics introduced in order
- [x] Core unit + property tests green
- [x] Runtime asset references resolve; no placeholder art in the runtime set
- [x] Debug APK builds; ads/IAP flags default OFF
- [ ] `lintDebug` clean (run in CI)
- [ ] Release build signed

## Manual QA matrix (owner / testers)
| Area | Cases |
|---|---|
| Gameplay | drag rotation on phone and tablet, snapping, linked rings, arcs, locks, colour gates, rotating arms, double gaps, combos |
| Accessibility | rotate buttons mode, TalkBack labels on board/buttons, high contrast, reduce motion, large font |
| Persistence | kill app mid-level → resume state; complete level → progress kept; corrupt save.json → backup restored |
| Audio | music/SFX toggles persist, phone call interruption, background/foreground, headphones |
| Monetisation (test mode) | rewarded no-fill → friendly message; reward granted once; interstitial caps; Premium purchase/cancel/restore; airplane mode keeps entitlement |
| Layout | small phone (360×640), large phone, 7" and 10" tablets portrait + landscape, notch/gesture bar |
| Performance | 60 fps on mid-range device during cascades with high effect quality; memory stable across 30 levels |

## iOS (later)
Follow `scripts/build_ios.sh` and docs/ADR-001-engine.md: KMP conversion of `core`, SwiftUI shell, StoreKit 2 + AdMob iOS + ATT.
