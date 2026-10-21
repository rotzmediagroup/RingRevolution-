package com.shiostudios.dumplingrings.core.systems

/** Stable keys are shared with Play Console configuration and the illustrated medal atlas. */
data class Achievement(val key: String, val art: Int, val target: Int, val progress: (SaveData) -> Int)

object Achievements {
    val all = listOf(
        Achievement("first_clear", 0, 1) { s -> s.levels.count { it.value.completed } },
        Achievement("first_chapter", 1, 10) { s -> (1..10).count { s.levels["L" + it.toString().padStart(3, '0')]?.completed == true } },
        Achievement("blossom_master", 2, 50) { s -> (1..50).count { s.levels["L" + it.toString().padStart(3, '0')]?.completed == true } },
        Achievement("lantern_master", 3, 50) { s -> (51..100).count { s.levels["L" + it.toString().padStart(3, '0')]?.completed == true } },
        Achievement("mountain_master", 4, 150) { s -> s.levels.count { it.value.completed && it.key.matches(Regex("L\\d{3}")) } },
        Achievement("star_garden", 5, 150) { it.totalStars },
        Achievement("perfect_constellation", 6, 450) { it.totalStars },
        Achievement("triple_release", 7, 3) { it.bestCombo },
        Achievement("daily_ritual", 8, 7) { s -> s.dailyHistory.filter { it.completed }.map { it.dayId }.distinct().size },
        Achievement("thousand_rings", 9, 1000) { it.totalReleases.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() },
    )

    fun reconcile(save: SaveData): SaveData {
        val unlocked = (save.achievements + all.filter { it.progress(save) >= it.target }.map { it.key }).distinct()
        return if (unlocked == save.achievements) save else save.copy(achievements = unlocked)
    }
}
