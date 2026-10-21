package com.shiostudios.dumplingrings.core

import com.shiostudios.dumplingrings.core.systems.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class AchievementsTest {
    @Test fun thresholdsAreDistinctAndUnlocksSurviveUndo() {
        val before = SaveData(bestCombo = 2, totalReleases = 999)
        assertFalse("triple_release" in Achievements.reconcile(before).achievements)
        val earned = Achievements.reconcile(before.copy(bestCombo = 3, totalReleases = 1000))
        assertTrue("triple_release" in earned.achievements)
        assertTrue("thousand_rings" in earned.achievements)
        assertEquals(earned.achievements, Achievements.reconcile(earned.copy(bestCombo = 0)).achievements)
        assertEquals(earned, Achievements.reconcile(earned))
    }
    @Test fun perfectCampaignEarnsAllCampaignMedals() {
        val levels = (1..150).associate { "L" + it.toString().padStart(3, '0') to LevelProgress(true, 1, 3) }
        val save = Achievements.reconcile(SaveData(levels = levels))
        assertTrue(listOf("first_clear", "first_chapter", "blossom_master", "lantern_master", "mountain_master", "star_garden", "perfect_constellation").all { it in save.achievements })
        assertFalse("daily_ritual" in save.achievements)
    }
    @Test fun duplicateDailyRecordsCannotEarnSevenDays() {
        val record = DailyRecord("2026-10-21", true, 2, true)
        assertFalse("daily_ritual" in Achievements.reconcile(SaveData(dailyHistory = List(7) { record })).achievements)
    }
    @Test fun catalogueHasTwentyFiveDistinctAchievementsAndArtwork() {
        assertEquals(25, Achievements.all.size)
        assertEquals(25, Achievements.all.map { it.key }.toSet().size)
        assertEquals(25, Achievements.all.map { it.art }.toSet().size)
        assertTrue(Achievements.all.none { it.art == 10 || it.art == 11 }) // Premium/cloud art stays reserved.
    }

    private fun campaign(count: Int, stars: Int = 0, start: Int = 1) =
        SaveData(levels = (start until start + count).associate { "L" + it.toString().padStart(3, '0') to LevelProgress(true, 1, stars) })

    @Test fun newMilestonesUnlockOnlyAtTheirThresholds() {
        fun check(key: String, before: SaveData, after: SaveData) {
            assertFalse(key in Achievements.reconcile(before).achievements, key)
            assertTrue(key in Achievements.reconcile(after).achievements, key)
        }
        check("teahouse_regular", campaign(24), campaign(25))
        check("halfway_home", campaign(74), campaign(75))
        check("century_club", campaign(99), campaign(100))
        check("mountain_keeper", campaign(49, start = 101), campaign(50, start = 101))
        check("star_seed", campaign(29, 1), campaign(30, 1))
        check("star_orchard", campaign(99, 3), campaign(100, 3))
        check("perfect_ten", campaign(9, 3), campaign(10, 3))
        check("perfect_fifty", campaign(49, 3), campaign(50, 3))
        check("hundred_rings", SaveData(totalReleases = 99), SaveData(totalReleases = 100))
        check("cascade_master", SaveData(bestCombo = 4), SaveData(bestCombo = 5))
        fun chefs(n: Int) = SaveData(levels = (1..n).associate { "L" + (it * 10).toString().padStart(3, '0') to LevelProgress(true, 1, 1) })
        check("chef_apprentice", chefs(0), chefs(1))
        check("chef_specialist", chefs(4), chefs(5))
        check("chef_grandmaster", chefs(14), chefs(15))
        fun days(n: Int) = SaveData(dailyHistory = (1..n).map { DailyRecord("2026-01-" + it.toString().padStart(2, '0'), true, 1, true) })
        check("first_daily", days(0), days(1))
        check("daily_devotee", days(29), days(30))
    }

    @Test fun replaysAndOrdinaryLevelsCannotSubstituteForDistinctPerfectOrChefLevels() {
        val replay = SaveData(levels = mapOf("L001" to LevelProgress(true, 1, 3, plays = 100)))
        val earned = Achievements.reconcile(replay)
        assertFalse("perfect_ten" in earned.achievements)
        assertFalse("chef_apprentice" in earned.achievements)
        assertFalse("daily_devotee" in Achievements.reconcile(SaveData(dailyHistory = List(30) { DailyRecord("2026-01-01", true, 1, true) })).achievements)
    }
}
