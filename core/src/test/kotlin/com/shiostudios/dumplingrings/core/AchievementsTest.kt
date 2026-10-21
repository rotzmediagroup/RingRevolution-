package com.shiostudios.dumplingrings.core

import com.shiostudios.dumplingrings.core.systems.*
import kotlin.test.*
import org.junit.Test

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
}
