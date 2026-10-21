package com.shiostudios.dumplingrings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Development APKs keep live ads off; the release variant is checked separately in CI. */
class BuildFlagsTest {
    @Test fun adsDefaultOff() { assertFalse(BuildConfig.ADS_ENABLED) }
    @Test fun iapDefaultOff() { assertFalse(BuildConfig.IAP_ENABLED) }
    @Test fun testIdsOnly() {
        assertTrue(BuildConfig.ADS_USE_TEST_IDS)
        assertTrue(BuildConfig.AD_UNIT_REWARDED.startsWith("ca-app-pub-3940256099942544/"))
        assertTrue(BuildConfig.AD_UNIT_INTERSTITIAL.startsWith("ca-app-pub-3940256099942544/"))
    }
}

