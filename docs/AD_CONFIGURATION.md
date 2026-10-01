# Ad, consent and purchase configuration checklist

## Defaults shipped in git (safe)
| Flag / id | Default | Where |
|---|---|---|
| `ADS_ENABLED` | `false` | `app/build.gradle.kts` → `BuildConfig.ADS_ENABLED` |
| `ADS_USE_TEST_IDS` | `true` | same |
| `IAP_ENABLED` | `false` | same |
| AdMob app id | Google sample `ca-app-pub-3940256099942544~3347511713` | manifest placeholder `admobAppId` |
| Rewarded / interstitial / banner units | Google **test** units | `BuildConfig.AD_UNIT_*` |
| Premium product id | `dumpling_rings_premium` | `BuildConfig.PREMIUM_PRODUCT_ID` |
| Banner | disabled (`AdsConfig.bannerEnabled=false`) | `AppContainer.adsConfig` |

With the defaults the Google Mobile Ads SDK is **never initialised**, no consent form is requested, no ad space is
reserved anywhere (`NoAdsProvider`, `NoConsentProvider`), and the Premium screen shows "store unavailable".

## Enabling test ads (development)
Create `secrets.properties` in the repo root (git-ignored) or export env vars:
```
ADS_ENABLED=true
ADS_USE_TEST_IDS=true
```
Rebuild. The UMP consent flow runs at launch (`AdsManager.initIfAllowed`); rewarded ads are offered only after an explicit
tap on an empty-booster dialog or the "double coins" button; interstitials follow `AdsPolicy` (never in levels 1–10, never on
chef levels, never in the first session, ≥ 4 completed levels and ≥ 8 minutes apart, ≤ 3 per session, never for Premium, never
without consent, only between screens). No-fill or offline ⇒ the UI returns immediately with a friendly note.

## Production (owner actions)
1. Create the AdMob app + 3 ad units; set `ADMOB_APP_ID`, `ADMOB_REWARDED_ID`, `ADMOB_INTERSTITIAL_ID`, `ADMOB_BANNER_ID`,
   `ADS_USE_TEST_IDS=false` in CI secrets / `secrets.properties`.
2. Publish `app-ads.txt` on the developer domain; fill Play Data Safety with: Advertising ID (ads), no personal data collected by the game itself.
3. Decide `ADS_ENABLED=true` only after a release decision; keep the banner off unless the menu layout has room.
4. In Play Console create the in-app product `dumpling_rings_premium` (one-time, non-consumable); set `IAP_ENABLED=true`.
   Prices are fetched from the store at runtime; nothing is hard-coded.
5. Test with license testers: purchase, cancel, interrupted purchase, restore on a second device, airplane mode (cached
   entitlement is kept; it is only cleared by a verified "not owned" answer from the store).

## Verified in this build
* Unit tests cover `AdsPolicy` caps and the idempotent reward ledger (`core/src/test/.../SystemsTest.kt`).
* `FakeAdsProvider` / `FakePurchaseProvider` exist for instrumented/manual tests (`app/platform/*.kt`).
* SDK integration compiles against play-services-ads 23.6.0 and billing-ktx 7.1.1. Real test-ad impressions and sandbox
  purchases could not be exercised in the build environment (no device/emulator) — see docs/TEST_REPORT.md.
