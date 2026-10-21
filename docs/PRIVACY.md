# Privacy integration notes

Published policy URL supplied by the owner: https://www.shiostudios.com/privacy-policy
The app links to this URL from Settings → Privacy & ads. These notes describe the implementation; they do not amend the published policy.

Progress, currency, inventory, cosmetics and settings are stored in private app files with Android Auto Backup enabled. Puzzles work offline without an account. There is no custom analytics SDK, custom tracking server or push notification service.

When enabled, Google Play Games provides account sign-in, achievement/score reporting and optional user-triggered Saved Games backups. Those features transmit data to Google and use the selected Play Games account. Cloud restore never grants purchase ownership and keeps local privacy choices and settings. Users can choose device or cloud progress before replacement.

Google Play Billing handles the one-time Premium product. The app caches the entitlement for offline use, verifies signed purchase data locally, acknowledges completed purchases, restores ownership and refreshes it from Play. No payment-card data is collected by game code.

When ads are enabled, UMP obtains applicable privacy choices before Mobile Ads loads. AdMob may process advertising identifiers and other SDK data according to Google disclosures and configured consent. The required privacy-options form can be reopened in the app. Optional rewarded ads are user-triggered; interstitials follow placement/cap policy. Premium removes interstitials and banners.

The owner must complete Play Data Safety, target-audience declarations, AdMob/UMP configuration and app-ads.txt using the actual enabled SDKs and their current disclosures. Do not claim no data collection merely because game code has no custom analytics. The current implementation is not an age gate or a child-directed/mixed-audience configuration.

All online features are disabled in the default build. See GAME_SERVICES_SETUP.md for activation and device acceptance checks.
