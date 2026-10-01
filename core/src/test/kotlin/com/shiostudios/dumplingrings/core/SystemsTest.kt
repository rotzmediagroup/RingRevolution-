package com.shiostudios.dumplingrings.core

import com.shiostudios.dumplingrings.core.content.LevelCodec
import com.shiostudios.dumplingrings.core.systems.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemsTest {
    private val eco = EconomyConfig()

    @Test fun starsThresholds() {
        assertEquals(3, Stars.forMoves(5, 5, 8)); assertEquals(2, Stars.forMoves(7, 5, 8)); assertEquals(1, Stars.forMoves(9, 5, 8))
    }

    @Test fun completeLevelFirstClearAndReplay() {
        val s0 = SaveData()
        val (s1, r1) = Progression.completeLevel(s0, "L001", 3, 3, 5, 20, mapOf("hint" to 1), eco)
        assertTrue(r1.firstClear); assertEquals(3, r1.stars); assertEquals(20 + 12, s1.coins); assertEquals(1, s1.inventory["hint"])
        assertEquals(1, s1.highestCompleted); assertTrue(s1.isUnlocked(2)); assertFalse(s1.isUnlocked(3))
        val (s2, r2) = Progression.completeLevel(s1, "L001", 9, 3, 5, 20, mapOf("hint" to 1), eco)
        assertFalse(r2.firstClear); assertEquals(0, r2.coins, "replays are not farmable"); assertEquals(3, s2.levels["L001"]!!.stars, "stars never decrease"); assertEquals(1, s2.inventory["hint"])
    }

    @Test fun boosterLedgerIsIdempotent() {
        val s = SaveData(inventory = mapOf("hint" to 1))
        val a = BoosterLedger.consume(s, Booster.STEAM_HINT, "tx1"); assertTrue(a.applied); assertEquals(0, a.save.inventory["hint"])
        val b = BoosterLedger.consume(a.save, Booster.STEAM_HINT, "tx1"); assertTrue(b.applied); assertEquals(0, b.save.inventory["hint"], "same request id never double-charges")
        val c = BoosterLedger.consume(b.save, Booster.STEAM_HINT, "tx2"); assertFalse(c.applied)
        val g = BoosterLedger.grant(c.save, Booster.STEAM_HINT, 1, "rw1"); val g2 = BoosterLedger.grant(g.save, Booster.STEAM_HINT, 1, "rw1")
        assertEquals(1, g2.save.inventory["hint"], "same rewarded id never double-grants")
        val coins = BoosterLedger.grantCoins(g2.save, 40, "rwc"); val coins2 = BoosterLedger.grantCoins(coins.save, 40, "rwc")
        assertEquals(40, coins2.save.coins)
    }

    @Test fun buyRequiresCoins() {
        val s = SaveData(coins = 59)
        assertFalse(BoosterLedger.buy(s, Booster.STEAM_HINT, eco).applied)
        val ok = BoosterLedger.buy(s.copy(coins = 60), Booster.STEAM_HINT, eco)
        assertTrue(ok.applied); assertEquals(0, ok.save.coins); assertEquals(1, ok.save.inventory["hint"])
    }

    @Test fun adsPolicyDefaultsOffAndRespectsCaps() {
        val off = AdsConfig()
        val s = SaveData(completedLevelsSinceInterstitial = 10)
        assertFalse(AdsPolicy.interstitialAllowed(off, s, 20, false, 0, true, false, false))
        val on = AdsConfig(enabled = true, interstitialEnabled = true, rewardedEnabled = true)
        assertTrue(AdsPolicy.interstitialAllowed(on, s.copy(lastInterstitialAt = 0), 20, false, 10 * 60_000L, true, false, false))
        assertFalse(AdsPolicy.interstitialAllowed(on, s, 5, false, 10 * 60_000L, true, false, false), "never in tutorial")
        assertFalse(AdsPolicy.interstitialAllowed(on, s, 20, true, 10 * 60_000L, true, false, false), "never on chef levels")
        assertFalse(AdsPolicy.interstitialAllowed(on, s.copy(premium = true), 20, false, 10 * 60_000L, true, false, false), "never for premium")
        assertFalse(AdsPolicy.interstitialAllowed(on, s, 20, false, 10 * 60_000L, false, false, false), "never without consent")
        assertFalse(AdsPolicy.interstitialAllowed(on, s, 20, false, 10 * 60_000L, true, true, false), "never right after a purchase")
        assertFalse(AdsPolicy.interstitialAllowed(on, s, 20, false, 10 * 60_000L, true, false, true), "never in the first session")
        assertFalse(AdsPolicy.interstitialAllowed(on, s.copy(lastInterstitialAt = 9 * 60_000L), 20, false, 10 * 60_000L, true, false, false), "8 minute interval")
        assertFalse(AdsPolicy.interstitialAllowed(on, s.copy(interstitialsThisSession = 3), 20, false, 10 * 60_000L, true, false, false), "session cap")
        assertFalse(AdsPolicy.rewardedAllowed(off, true)); assertTrue(AdsPolicy.rewardedAllowed(on, true)); assertFalse(AdsPolicy.rewardedAllowed(on, false))
    }

    @Test fun saveRoundTripAndMigration() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val s = SaveData(levels = mapOf("L001" to LevelProgress(true, 3, 3, 1)), coins = 12, premium = true)
        val back: SaveData = json.decodeFromString(json.encodeToString(s))
        assertEquals(s, back)
        val legacy: SaveData = json.decodeFromString("""{"schemaVersion":0,"coins":5,"unknownField":1}""")
        assertEquals(1, SaveMigrations.migrate(legacy).schemaVersion); assertEquals(5, legacy.coins)
    }

    @Test fun chapterCompletionAndWorldUnlock() {
        var s = SaveData()
        for (i in 1..10) s = Progression.completeLevel(s, LevelCodec.levelId(i), 5, 3, 5, 20, emptyMap(), eco).first
        assertTrue(s.chapterComplete(1)); assertFalse(s.chapterComplete(2))
        assertEquals(10 * 20 + 10 * 5, s.coins)
    }

    @Test fun dailySeedIsDeterministicAndDistinctPerDay() {
        assertEquals(DailyPuzzle.seedFor("2026-10-01"), DailyPuzzle.seedFor("2026-10-01"))
        assertTrue(DailyPuzzle.seedFor("2026-10-01") != DailyPuzzle.seedFor("2026-10-02"))
        val idx = DailyPuzzle.templateIndex("2026-10-01", 40)
        assertTrue(idx in 11..40)
        assertTrue(DailyPuzzle.templateIndex("2026-10-01", 0) in 11..20, "clamped to a fair template range for new players")
    }
}
