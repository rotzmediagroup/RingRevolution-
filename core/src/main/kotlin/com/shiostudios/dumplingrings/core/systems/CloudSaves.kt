package com.shiostudios.dumplingrings.core.systems

import kotlinx.serialization.Serializable

@Serializable
data class CloudArchive(val version: Int = 1, val savedAt: Long, val save: SaveData)

/** Store entitlements and privacy choices are device/store-owned, never awarded by a cloud payload. */
object CloudSaves {
    fun validate(archive: CloudArchive) {
        require(archive.version == 1 && archive.save.schemaVersion <= SaveMigrations.CURRENT)
        require(archive.savedAt >= 0)
        require(archive.save.levels.all { (key, p) ->
            key.matches(Regex("L[0-9]{3}")) && key.drop(1).toInt() in 1..150 && p.stars in 0..3 && p.bestMoves >= 0
        })
        require(archive.save.coins >= 0 && archive.save.inventory.values.all { it >= 0 })
        archive.save.inProgress?.let { progress ->
            require(progress.levelId.matches(Regex("L[0-9]{3}")) && progress.levelId.drop(1).toInt() in 1..150)
            require(progress.moves >= 0 && progress.angles.isNotEmpty() && progress.angles.size == progress.removed.size)
            require(progress.angles.all { it in 0..359 })
        }
    }

    fun forBackup(data: SaveData): SaveData = data.copy(premium = false, premiumVerifiedAt = 0,
        consentStatus = "unknown", unlockedCosmetics = data.unlockedCosmetics.filterNot { it == "theme_premium_ube" })

    fun restore(local: SaveData, remote: SaveData): SaveData = remote.copy(
        settings = local.settings, premium = local.premium, premiumVerifiedAt = local.premiumVerifiedAt,
        selectedTheme = if (!local.premium && remote.selectedTheme == "theme_premium_ube") "default" else remote.selectedTheme,
        consentStatus = local.consentStatus, sessionCount = local.sessionCount,
        lastInterstitialAt = local.lastInterstitialAt, interstitialsThisSession = local.interstitialsThisSession,
        rewardedDay = local.rewardedDay, rewardedCount = local.rewardedCount,
        achievements = (local.achievements + remote.achievements).distinct(),
        unlockedCosmetics = remote.unlockedCosmetics.filterNot { it == "theme_premium_ube" } +
            local.unlockedCosmetics.filter { it == "theme_premium_ube" },
    )
}
