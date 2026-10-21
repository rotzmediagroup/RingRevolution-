package com.shiostudios.dumplingrings.core

import com.shiostudios.dumplingrings.core.systems.*
import kotlin.test.*

class CloudSavesTest {
    @Test fun restoreReplacesCurrencyButKeepsPurchasesPrivacyAndLocalSettings() {
        val local = SaveData(coins = 12, settings = Settings(language = "ar"), premium = false,
            consentStatus = "denied", rewardedDay = "2026-10-21", rewardedCount = 10, achievements = listOf("first_clear"))
        val remote = SaveData(coins = 99, premium = true, premiumVerifiedAt = 123, selectedTheme = "theme_premium_ube",
            unlockedCosmetics = listOf("theme_premium_ube", "theme_gold"))
        val result = CloudSaves.restore(local, remote)
        assertEquals(99, result.coins)
        assertFalse(result.premium)
        assertEquals("default", result.selectedTheme)
        assertFalse("theme_premium_ube" in result.unlockedCosmetics)
        assertEquals(local.settings, result.settings)
        assertEquals("denied", result.consentStatus)
        assertEquals(10, result.rewardedCount)
        assertEquals(local.achievements, result.achievements)
    }
    @Test fun backupStripsEntitlementAndRejectsInvalidOrFutureData() {
        assertFalse(CloudSaves.forBackup(SaveData(premium = true)).premium)
        assertFailsWith<IllegalArgumentException> { CloudSaves.validate(CloudArchive(version = 2, savedAt = 1, save = SaveData())) }
        assertFailsWith<IllegalArgumentException> { CloudSaves.validate(CloudArchive(savedAt = 1, save = SaveData(coins = -1))) }
        assertFailsWith<IllegalArgumentException> { CloudSaves.validate(CloudArchive(savedAt = 1, save = SaveData(levels = mapOf("bad" to LevelProgress())))) }
        CloudSaves.validate(CloudArchive(savedAt = 1, save = SaveData()))
    }
}
