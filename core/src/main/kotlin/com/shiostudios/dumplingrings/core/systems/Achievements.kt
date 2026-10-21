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
        Achievement("teahouse_regular", 12, 25) { s -> s.levels.count { it.value.completed } },
        Achievement("halfway_home", 13, 75) { s -> s.levels.count { it.value.completed } },
        Achievement("century_club", 14, 100) { s -> s.levels.count { it.value.completed } },
        Achievement("mountain_keeper", 15, 50) { s -> (101..150).count { s.levels["L" + it.toString().padStart(3, '0')]?.completed == true } },
        Achievement("chef_apprentice", 16, 1) { s -> (1..15).count { s.levels["L" + (it * 10).toString().padStart(3, '0')]?.completed == true } },
        Achievement("chef_specialist", 17, 5) { s -> (1..15).count { s.levels["L" + (it * 10).toString().padStart(3, '0')]?.completed == true } },
        Achievement("chef_grandmaster", 18, 15) { s -> (1..15).count { s.levels["L" + (it * 10).toString().padStart(3, '0')]?.completed == true } },
        Achievement("star_seed", 19, 30) { s -> s.totalStars },
        Achievement("star_orchard", 20, 300) { s -> s.totalStars },
        Achievement("perfect_ten", 21, 10) { s -> s.levels.count { it.value.completed && it.value.stars == 3 } },
        Achievement("perfect_fifty", 22, 50) { s -> s.levels.count { it.value.completed && it.value.stars == 3 } },
        Achievement("first_daily", 23, 1) { s -> s.dailyHistory.filter { it.completed }.map { it.dayId }.distinct().size },
        Achievement("daily_devotee", 24, 30) { s -> s.dailyHistory.filter { it.completed }.map { it.dayId }.distinct().size },
        Achievement("hundred_rings", 25, 100) { s -> s.totalReleases.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() },
        Achievement("cascade_master", 26, 5) { s -> s.bestCombo },
    )

    fun reconcile(save: SaveData): SaveData {
        val unlocked = (save.achievements + all.filter { it.progress(save) >= it.target }.map { it.key }).distinct()
        return if (unlocked == save.achievements) save else save.copy(achievements = unlocked)
    }
}
