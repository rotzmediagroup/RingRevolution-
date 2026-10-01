package com.shiostudios.dumplingrings.core.systems

import com.shiostudios.dumplingrings.core.engine.GameSession
import com.shiostudios.dumplingrings.core.engine.GameState
import com.shiostudios.dumplingrings.core.engine.LegalAction
import com.shiostudios.dumplingrings.core.engine.RuleEngine
import com.shiostudios.dumplingrings.core.hint.Hint
import com.shiostudios.dumplingrings.core.hint.HintEngine
import com.shiostudios.dumplingrings.core.solver.Solver
import kotlinx.serialization.Serializable

// ------------------------------------------------------------------ economy & boosters

@Serializable
enum class Booster(val id: String) {
    STEAM_HINT("hint"), STEAM_PEEK("steam_peek"), CHEFS_TWIST("chefs_twist"), GOLDEN_STEAMER("golden_steamer");
    companion object { fun byId(id: String) = entries.firstOrNull { it.id == id } }
}

@Serializable
data class EconomyConfig(
    val startCoins: Int = 0,
    val startInventory: Map<String, Int> = mapOf("hint" to 3, "steam_peek" to 2, "chefs_twist" to 1, "golden_steamer" to 0),
    val prices: Map<String, Int> = mapOf("hint" to 60, "steam_peek" to 100, "chefs_twist" to 180, "golden_steamer" to 240),
    val starBonus: Map<Int, Int> = mapOf(1 to 0, 2 to 5, 3 to 12),
    val replayCoins: Int = 0,
    val dailyCoins: Int = 25,
    val premiumDailyClaim: Map<String, Int> = mapOf("hint" to 1),
    val rewardedCoinsMultiplier: Int = 2,
)

@Serializable
data class LevelProgress(val completed: Boolean = false, val bestMoves: Int = 0, val stars: Int = 0, val plays: Int = 0)

@Serializable
data class InProgressLevel(val levelId: String, val angles: List<Int>, val removed: List<Boolean>, val moves: Int)

@Serializable
data class Settings(
    val music: Boolean = true, val sfx: Boolean = true, val haptics: Boolean = true,
    val reduceMotion: Boolean = false, val highContrast: Boolean = false, val effectQuality: String = "high",
    val language: String = "system", val rotateButtons: Boolean = false, val musicVolume: Float = 0.8f, val sfxVolume: Float = 1f,
    val reminders: Boolean = false,
)

@Serializable
data class DailyRecord(val dayId: String, val completed: Boolean, val moves: Int, val rewardClaimed: Boolean)

@Serializable
data class SaveData(
    val schemaVersion: Int = 1,
    val levels: Map<String, LevelProgress> = emptyMap(),
    val coins: Int = 0,
    val inventory: Map<String, Int> = emptyMap(),
    val unlockedCosmetics: List<String> = emptyList(),
    val selectedTheme: String = "default",
    val settings: Settings = Settings(),
    val premium: Boolean = false,
    val premiumVerifiedAt: Long = 0,
    val dailyHistory: List<DailyRecord> = emptyList(),
    val lastPremiumClaimDay: String = "",
    val lastRewardTxIds: List<String> = emptyList(),
    val inProgress: InProgressLevel? = null,
    val completedLevelsSinceInterstitial: Int = 0,
    val lastInterstitialAt: Long = 0,
    val interstitialsThisSession: Int = 0,
    val totalMoves: Long = 0,
    val totalReleases: Long = 0,
    val bestCombo: Int = 0,
    val firstRunDone: Boolean = false,
    val consentStatus: String = "unknown",
    val seenChapterScenes: List<Int> = emptyList(),
    val sessionCount: Int = 0,
) {
    val highestCompleted: Int get() = levels.filter { it.value.completed }.keys.maxOfOrNull { it.drop(1).toInt() } ?: 0
    fun isUnlocked(index: Int): Boolean = index == 1 || levels["L" + (index - 1).toString().padStart(3, '0')]?.completed == true
    fun starsInWorld(world: Int): Int = levels.filter { it.key.drop(1).toInt() in ((world - 1) * 50 + 1)..(world * 50) }.values.sumOf { it.stars }
    val totalStars: Int get() = levels.values.sumOf { it.stars }
    fun chapterComplete(chapter: Int): Boolean = ((chapter - 1) * 10 + 1..chapter * 10).all { levels["L" + it.toString().padStart(3, '0')]?.completed == true }
}

object SaveMigrations {
    const val CURRENT = 1
    fun migrate(data: SaveData): SaveData = when {
        data.schemaVersion >= CURRENT -> data
        else -> data.copy(schemaVersion = CURRENT)
    }
}

object Stars {
    fun forMoves(moves: Int, parMoves: Int, goodMoves: Int): Int = when {
        moves <= parMoves -> 3
        moves <= goodMoves -> 2
        else -> 1
    }
}

/** Pure functions over SaveData: the single place where progress and economy change. */
object Progression {
    fun completeLevel(save: SaveData, levelId: String, moves: Int, par: Int, good: Int, baseCoins: Int, boosterReward: Map<String, Int>, economy: EconomyConfig): Pair<SaveData, LevelReward> {
        val stars = Stars.forMoves(moves, par, good)
        val prev = save.levels[levelId] ?: LevelProgress()
        val first = !prev.completed
        val improvedStars = stars > prev.stars
        val coins = (if (first) baseCoins else economy.replayCoins) + (if (improvedStars) (economy.starBonus[stars] ?: 0) - (economy.starBonus[prev.stars] ?: 0) else 0)
        val inv = save.inventory.toMutableMap()
        if (first) boosterReward.forEach { (k, v) -> inv[k] = (inv[k] ?: 0) + v }
        val np = LevelProgress(true, if (prev.completed) minOf(prev.bestMoves, moves) else moves, maxOf(prev.stars, stars), prev.plays + 1)
        val ns = save.copy(levels = save.levels + (levelId to np), coins = save.coins + coins, inventory = inv, inProgress = null,
            completedLevelsSinceInterstitial = save.completedLevelsSinceInterstitial + 1, totalMoves = save.totalMoves + moves)
        return ns to LevelReward(stars, coins, if (first) boosterReward else emptyMap(), first, improvedStars)
    }
}

@Serializable
data class LevelReward(val stars: Int, val coins: Int, val boosters: Map<String, Int>, val firstClear: Boolean, val improvedStars: Boolean)

/**
 * Transactional, idempotent booster use (bible §22). A requestId is remembered so a repeated commit
 * (after an app restart mid-animation, an interrupted ad, …) can never double-charge or double-grant.
 */
object BoosterLedger {
    data class Result(val save: SaveData, val applied: Boolean, val reason: String? = null)

    fun consume(save: SaveData, booster: Booster, requestId: String): Result {
        if (requestId in save.lastRewardTxIds) return Result(save, true, "already applied")
        val have = save.inventory[booster.id] ?: 0
        if (have <= 0) return Result(save, false, "empty")
        val inv = save.inventory + (booster.id to have - 1)
        return Result(save.copy(inventory = inv, lastRewardTxIds = (save.lastRewardTxIds + requestId).takeLast(64)), true)
    }

    fun grant(save: SaveData, booster: Booster, amount: Int, requestId: String): Result {
        if (requestId in save.lastRewardTxIds) return Result(save, true, "already granted")
        val inv = save.inventory + (booster.id to (save.inventory[booster.id] ?: 0) + amount)
        return Result(save.copy(inventory = inv, lastRewardTxIds = (save.lastRewardTxIds + requestId).takeLast(64)), true)
    }

    fun grantCoins(save: SaveData, amount: Int, requestId: String): Result {
        if (requestId in save.lastRewardTxIds) return Result(save, true, "already granted")
        return Result(save.copy(coins = save.coins + amount, lastRewardTxIds = (save.lastRewardTxIds + requestId).takeLast(64)), true)
    }

    fun buy(save: SaveData, booster: Booster, economy: EconomyConfig): Result {
        val price = economy.prices[booster.id] ?: return Result(save, false, "no price")
        if (save.coins < price) return Result(save, false, "insufficient coins")
        return Result(save.copy(coins = save.coins - price, inventory = save.inventory + (booster.id to (save.inventory[booster.id] ?: 0) + 1)), true)
    }
}

/** Solver-validated booster effects on a live session. Every effect is a legal action through the dispatcher. */
class BoosterEffects(private val engine: RuleEngine, private val hints: HintEngine) {
    /** Steam Hint: one productive ring and direction. Costs the booster only if a hint exists. */
    fun steamHint(session: GameSession, history: List<LegalAction.RotateTo>): Hint? = hints.nextMoves(session.state, 1, history).firstOrNull()

    /** Steam Peek: the next two optimal steps as a ghost overlay. */
    fun steamPeek(session: GameSession, history: List<LegalAction.RotateTo>): List<Hint> = hints.nextMoves(session.state, 2, history)

    /** Chef's Twist: executes one legal solver step for the selected ring if possible, else the best next step. */
    fun chefsTwist(session: GameSession, history: List<LegalAction.RotateTo>, selectedRing: Int?): LegalAction.RotateTo? {
        val next = hints.nextMoves(session.state, 6, history)
        val pick = next.firstOrNull { it.ring == selectedRing } ?: next.firstOrNull() ?: return null
        return pick.action
    }

    /**
     * Golden Steamer: removes the selected ring through a solver-validated action. Removing a ring only ever relaxes
     * constraints (locks open, blockers vanish) so the level stays solvable; we still verify with the solver.
     */
    fun goldenSteamer(session: GameSession, selectedRing: Int): LegalAction.ForceRelease? {
        if (session.state.removed[selectedRing]) return null
        val act = LegalAction.ForceRelease(selectedRing)
        val res = engine.apply(session.state, act)
        if (!res.legal) return null
        if (!res.state.isWin && !Solver(engine, 60_000, 60_000).solve(res.state).solvable) return null
        return act
    }
}

// ------------------------------------------------------------------ daily puzzle

/**
 * Offline deterministic daily puzzle: the calendar day id (yyyy-MM-dd, local) selects a built-in seed list entry and
 * a campaign level template whose ring angles are re-rolled (always solver-validated before being offered).
 */
object DailyPuzzle {
    /** 366 built-in seeds so every day of the year maps to a stable puzzle; multiplied by year for variety. */
    fun seedFor(dayId: String): Long {
        val (y, m, d) = dayId.split("-").map { it.toInt() }
        val dayOfYear = (m - 1) * 31 + d
        return 7_000_000L + y * 1000L + dayOfYear * 7L
    }

    /** Template level index for the day: cycles through levels 11..150 so difficulty varies but stays fair. */
    fun templateIndex(dayId: String, highestCompleted: Int): Int {
        val cap = highestCompleted.coerceIn(20, 150)
        val s = seedFor(dayId)
        return 11 + ((s / 7) % (cap - 10)).toInt()
    }
}

// ------------------------------------------------------------------ ads policy (pure)

@Serializable
data class AdsConfig(
    val enabled: Boolean = false,
    val bannerEnabled: Boolean = false,
    val interstitialEnabled: Boolean = false,
    val rewardedEnabled: Boolean = false,
    val interstitialEveryLevels: Int = 4,
    val interstitialMinIntervalMs: Long = 8 * 60_000L,
    val interstitialMaxPerSession: Int = 3,
    val rewardedDailyCap: Int = 10,
)

/** Pure placement policy (bible §23); the Android AdsManager asks this before loading/showing anything. */
object AdsPolicy {
    fun interstitialAllowed(cfg: AdsConfig, save: SaveData, levelIndex: Int, isChef: Boolean, nowMs: Long, consentGiven: Boolean, justPurchased: Boolean, firstSession: Boolean): Boolean {
        if (!cfg.enabled || !cfg.interstitialEnabled) return false
        if (save.premium || !consentGiven || justPurchased || firstSession) return false
        if (levelIndex <= 10 || isChef) return false
        if (save.completedLevelsSinceInterstitial < cfg.interstitialEveryLevels) return false
        if (nowMs - save.lastInterstitialAt < cfg.interstitialMinIntervalMs) return false
        if (save.interstitialsThisSession >= cfg.interstitialMaxPerSession) return false
        return true
    }
    fun bannerAllowed(cfg: AdsConfig, save: SaveData, consentGiven: Boolean, screenIsMenu: Boolean) =
        cfg.enabled && cfg.bannerEnabled && !save.premium && consentGiven && screenIsMenu
    fun rewardedAllowed(cfg: AdsConfig, consentGiven: Boolean) = cfg.enabled && cfg.rewardedEnabled && consentGiven
}
