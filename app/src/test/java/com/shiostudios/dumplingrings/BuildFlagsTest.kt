package com.shiostudios.dumplingrings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Monetisation must be OFF and on test identifiers in any build that is not explicitly configured by the owner. */
class BuildFlagsTest {
    @Test fun adsDefaultOff() { assertFalse(BuildConfig.ADS_ENABLED) }
    @Test fun iapDefaultOff() { assertFalse(BuildConfig.IAP_ENABLED) }
    @Test fun testIdsOnly() {
        assertTrue(BuildConfig.ADS_USE_TEST_IDS)
        assertTrue(BuildConfig.AD_UNIT_REWARDED.startsWith("ca-app-pub-3940256099942544/"))
        assertTrue(BuildConfig.AD_UNIT_INTERSTITIAL.startsWith("ca-app-pub-3940256099942544/"))
    }
}
