# Privacy

**Data the game itself collects: none.** Progress, settings, coins, boosters, cosmetics and the Premium entitlement cache are
stored only in the app's private files (`save.json` + backup), included in Android Auto Backup. No account, no login wall,
no analytics SDK, no custom tracking server, no push notifications (reminders are off by default and not implemented as
push). The game is fully playable offline.

**Third-party SDKs present in the binary** (inactive unless the owner enables them at build time):
* Google Mobile Ads + User Messaging Platform — only initialised when `ADS_ENABLED=true` **and** UMP consent allows it;
  uses the Advertising ID (`AD_ID` permission) for ads only. EEA/UK users get the UMP consent form before any ad request;
  "Privacy options" is reachable from Settings → Privacy. Under-age treatment is left conservative (`setTagForUnderAgeOfConsent(false)`,
  no child-directed tag); the owner decides the final age rating and must adjust `AdMobAdsProvider`/UMP params accordingly.
* Google Play Billing — only when `IAP_ENABLED=true`; one non-consumable product, no subscriptions.

**Store disclosures to fill (owner):** Play Data Safety (Advertising ID → ads, if ads enabled; otherwise "no data collected"),
privacy policy URL (templated text in `app/src/main/res/values*/strings.xml` → `privacy_text`), `app-ads.txt`, iOS ATT and
App Privacy labels when the iOS build exists.
