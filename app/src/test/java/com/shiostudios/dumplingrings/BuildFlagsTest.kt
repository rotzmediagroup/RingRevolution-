package com.shiostudios.dumplingrings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Both downloadable APK variants use the owner live configuration. */
class BuildFlagsTest {
    @Test fun adsEnabled() { assertTrue(BuildConfig.ADS_ENABLED) }
    @Test fun iapDefaultOff() { assertFalse(BuildConfig.IAP_ENABLED) }
    @Test fun ownerLiveIds() {
        assertFalse(BuildConfig.ADS_USE_TEST_IDS)
        assertEquals("ca-app-pub-3722169369385142/7657383681", BuildConfig.AD_UNIT_REWARDED)
        assertEquals("ca-app-pub-3722169369385142/5699725172", BuildConfig.AD_UNIT_INTERSTITIAL)
        assertEquals("ca-app-pub-3722169369385142/8325888516", BuildConfig.AD_UNIT_BANNER)
    }
}
