# AdMob release configuration

The owner requested live ads for the Play Store release. Release defaults are `ADS_ENABLED=true` and `ADS_USE_TEST_IDS=false`. Public app/unit IDs are in `admob.properties`; environment variables and `secrets.properties` can override IDs. Release CI verifies the actual generated BuildConfig against the owner IDs, and the release workflow explicitly selects live ads.

Both debug and release APKs use the owner live IDs by default. AdMob initializes only after UMP permits ad requests. Existing placement rules, rewarded-grant idempotency, daily limits and interstitial spacing remain active. An adaptive banner is enabled on the main menu for non-premium players after consent. It never overlays the game board. Fullscreen ad loading retries in the foreground after failures.

See [GAME_SERVICES_SETUP.md](GAME_SERVICES_SETUP.md) for all four identifiers, consent setup, acceptance checks and the 25 achievement definitions. Play Games and billing still require their console IDs/public key and remain disabled until supplied.

Live ad impressions cannot be validated by CI. Test the signed Play-track build on a device after configuring applicable AdMob consent messages. SDK versions: Mobile Ads 24.7.0, UMP 3.2.0 and Billing 8.0.0.
