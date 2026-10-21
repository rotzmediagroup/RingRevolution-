# Ring Revolution: owner setup and acceptance checks

Privacy policy: https://www.shiostudios.com/privacy-policy

Package identity remains `com.shiostudios.dumplingrings`. Display name is Ring Revolution. This is a native Kotlin/Compose Android app, with a pure Kotlin game core.

## Configure the build

Copy `services.properties.example` to git-ignored `secrets.properties`, or set environment variables. Environment variables take precedence, followed by `secrets.properties`, checked-in public AdMob IDs in `admob.properties`, then defaults. Blank values fall through to the next source. Release ads are enabled with the owner live units. Debug ads remain disabled and use test units. PGS and billing remain disabled until their console configuration is supplied.

Release workflow reads these keys from GitHub repository **Variables**, including `PLAY_BILLING_PUBLIC_KEY` (a public key, not a private credential). Signing passwords remain repository **Secrets**. Populate the variables before a production release. Debug CI keeps ads disabled, while the release bundle uses live AdMob units. Build targets Android API 36 with AGP 8.11.1.

## Google Play Games Services

Create/link the Android game with this package and the Play App Signing certificate SHA-1. Add the debug/upload SHA-1 only to the appropriate test credentials. Add test accounts while the game-services project is unpublished. Enable **Saved Games**. Supply the numeric project ID as `PGS_APP_ID`, all achievement/leaderboard IDs below, then `PGS_ENABLED=true`.

Create all **25 standard**, initially revealed achievements. Local thresholds are cumulative and SDK calls unlock the matching achievement; do not configure these as incremental achievements. Art is in `docs/play-console-art/`, each image is 512 × 512 PNG and differs by milestone.

| Stable key / property suffix | Unlock criterion | Art |
|---|---|---|
| `FIRST_CLEAR` | Complete one campaign puzzle | `first_clear.png` |
| `FIRST_CHAPTER` | Complete L001–L010 | `first_chapter.png` |
| `BLOSSOM_MASTER` | Complete L001–L050 | `blossom_master.png` |
| `LANTERN_MASTER` | Complete L051–L100 | `lantern_master.png` |
| `MOUNTAIN_MASTER` | Complete all 150 campaign puzzles | `mountain_master.png` |
| `STAR_GARDEN` | Earn 150 campaign stars | `star_garden.png` |
| `PERFECT_CONSTELLATION` | Earn all 450 campaign stars | `perfect_constellation.png` |
| `TRIPLE_RELEASE` | Release at least three rings in one move | `triple_release.png` |
| `DAILY_RITUAL` | Complete daily puzzles on seven distinct dates | `daily_ritual.png` |
| `THOUSAND_RINGS` | Release 1,000 rings total | `thousand_rings.png` |
| `TEAHOUSE_REGULAR` | Complete 25 campaign puzzles. | `teahouse_regular.png` |
| `HALFWAY_HOME` | Complete 75 campaign puzzles. | `halfway_home.png` |
| `CENTURY_CLUB` | Complete 100 campaign puzzles. | `century_club.png` |
| `MOUNTAIN_KEEPER` | Complete all 50 Moonlit Mountain Kitchen puzzles. | `mountain_keeper.png` |
| `CHEF_APPRENTICE` | Complete one chef level. | `chef_apprentice.png` |
| `CHEF_SPECIALIST` | Complete five chef levels. | `chef_specialist.png` |
| `CHEF_GRANDMASTER` | Complete all 15 chef levels. | `chef_grandmaster.png` |
| `STAR_SEED` | Collect 30 campaign stars. | `star_seed.png` |
| `STAR_ORCHARD` | Collect 300 campaign stars. | `star_orchard.png` |
| `PERFECT_TEN` | Earn three stars on 10 different campaign puzzles. | `perfect_ten.png` |
| `PERFECT_FIFTY` | Earn three stars on 50 different campaign puzzles. | `perfect_fifty.png` |
| `FIRST_DAILY` | Complete your first daily puzzle. | `first_daily.png` |
| `DAILY_DEVOTEE` | Complete daily puzzles on 30 different days. | `daily_devotee.png` |
| `HUNDRED_RINGS` | Release a total of 100 rings. | `hundred_rings.png` |
| `CASCADE_MASTER` | Release five rings in one move. | `cascade_master.png` |

Each property is prefixed `PGS_ACHIEVEMENT_`. Display names/descriptions are in Android resource files, `ach_<key>` / `ach_<key>_desc`; copy the localized values into Play Console as desired.

Create two public integer leaderboards, **larger is better**:

- `PGS_LEADERBOARD_STARS`: total campaign stars, range 0–450.
- `PGS_LEADERBOARD_CAMPAIGN`: unique completed campaign levels, range 0–150.

Rankings use Play Games native UI. No fake local competitors or fabricated global ranks are displayed. Progress is submitted on foreground and progression changes; unlocked achievements are retried after reconnect. The SDK uses the account selected by Play Games. The app never requires sign-in for puzzles.

## Saved Games

The Achievements & cloud screen offers explicit backup and restore to the current Play Games account. Named snapshot: `ring_revolution_progress_v1`; envelope version 1. Existing cloud progress is shown before replacement. A two-revision SDK conflict is resolved by choosing cloud A/B, then comparing the chosen cloud revision to this device. Cancel closes snapshots without overwriting local progress.

Currency and inventory are replaced with the chosen save, **never added together**. Local settings, consent choices, current session/ad limits and store-verified Premium ownership remain on the device. Previously earned local achievement medals remain unlocked. A cloud payload cannot award Premium. Future versions, invalid progress and negative inventory/balances are rejected. Backup is user-triggered, not background autosync; remember to back up before switching devices. No custom server is required for this implementation.

## Google Play Billing

Create an active, one-time **non-consumable** product with ID `dumpling_rings_premium` (or configure `PREMIUM_PRODUCT_ID`). The existing offer removes interstitials/banners, grants the onyx ring theme and offers a daily booster claim. No subscriptions, loot boxes or cash consumable SKUs are introduced. Pricing and countries are controlled by Play Console and prices are loaded from Play, never hard-coded.

Set the app licensing **Base64 RSA public key** from Play Console as `PLAY_BILLING_PUBLIC_KEY`, then `IAP_ENABLED=true`. Billing 8 handles purchases, pending transactions, cancellation, acknowledgement, restore and foreground refresh. Entitlement is granted only for a PURCHASED transaction with a valid Play signature and successful acknowledgement (or an already acknowledged purchase). Failures do not grant ownership. Successful store queries with no owned product clear cached Premium; network failures retain it. Themed access follows verified ownership.

The signature check is client-side. It does not provide server-side purchase-token validation, real-time developer notifications or hardened anti-tamper protection. If those are required, add a trusted purchase-verification backend before launch; none is deployed here.

Upload `premium.png` as the product artwork. Test via an internal/closed Play track with license testers; a sideloaded development APK is insufficient evidence of production purchase readiness. Test success, cancellation, pending→paid, acknowledgement retry, restore after reinstall, refund/ownership refresh and offline startup.

## AdMob and consent

Set up the Android app, rewarded and interstitial units, UMP messages and applicable regional privacy requirements in AdMob. Supply `ADMOB_APP_ID`, `ADMOB_REWARDED_ID`, `ADMOB_INTERSTITIAL_ID`. Release defaults are `ADS_ENABLED=true` and `ADS_USE_TEST_IDS=false`, as requested by the owner for the Play Store build. The release workflow explicitly uses those values. Debug builds override ads to disabled and force test identifiers. Production configuration rejects missing/sample identifiers.

UMP runs before SDK initialization or loading. A player can reopen required privacy choices from Settings → Privacy & ads. Every ad presentation rechecks the current permission to request ads. A rewarded grant is delivered exactly once by the SDK reward callback, never merely for closing an ad. Rewarded ads are capped at ten per local date. Interstitial policy excludes first session, early levels, chef levels and Premium, and enforces spacing/session caps. No banners are placed.

Test consent required/not required, privacy-choice changes, no-fill, offline/reconnect, dismiss-before-reward, reward-before-process-exit, daily cap and Premium ad removal. Consider the game’s actual target audience when configuring AdMob/UMP; this implementation does not collect age or implement a mixed-audience age gate.

## Localization and visual review

English, Spanish, Brazilian Portuguese, German, French, Japanese, Korean, Hindi, Indonesian and Arabic are the provisional target set. Existing Dutch remains supported. Selection is based on Android reach and puzzle-game fit, not measured acquisition/retention data. Revisit with actual market data.

All 280 resource strings and 15 chapter names are translated. Android 13 language settings and the in-game picker include all locales. Arabic uses RTL; Arabic, Devanagari and CJK use system fonts for script coverage. Translation tables are in `tools/localization/`; run `generate.py` after editing and `validate.py` to check coverage and formatting. Native-speaker review is still required before store launch.

Review menu, maps, chapters, gameplay/boosters, results/pause, daily puzzle, collection, shop, Premium, settings, privacy, credits, stats and achievements/cloud at 320dp width, landscape, tablet, large fonts, Reduce Motion, high contrast and Arabic RTL. CI produces Robolectric screenshots and reports as `android-review`; real-device GPU, animation smoothness and live service flows still require device testing.

## Owner-provided AdMob identifiers

The app and three units are configured in `admob.properties` and the example configuration:

- `ADMOB_APP_ID`: `ca-app-pub-3722169369385142~7224384827`
- `ADMOB_REWARDED_ID`: `ca-app-pub-3722169369385142/7657383681`
- `ADMOB_INTERSTITIAL_ID`: `ca-app-pub-3722169369385142/5699725172`
- `ADMOB_BANNER_ID`: `ca-app-pub-3722169369385142/8325888516`

Release builds now default to `ADS_ENABLED=true` and `ADS_USE_TEST_IDS=false`; debug builds keep ads off and use test identifiers. Rewarded/interstitial placements are already implemented; the banner ID is stored for future use, and banners remain disabled. Production unit loading uses the configured IDs when `ADS_ENABLED=true` and `ADS_USE_TEST_IDS=false`.

CI checks the generated release BuildConfig to confirm live ads are enabled, test mode is off and all unit IDs match `admob.properties`. The release bundle remains unsigned unless owner signing configuration is supplied; complete Play Games and billing configuration separately before those services can be offered.
