package com.shiostudios.dumplingrings.platform

import android.app.Activity
import com.google.android.gms.games.PlayGames
import com.shiostudios.dumplingrings.BuildConfig
import com.shiostudios.dumplingrings.core.systems.SaveData
import com.shiostudios.dumplingrings.save.SaveStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** PGS never gates offline play. Retryable progress remains in the local save until reconnect. */
class PlayGamesService(private val save: SaveStore) {
    data class Status(val signedIn: Boolean = false, val busy: Boolean = false, val failed: Boolean = false)
    private val status = MutableStateFlow(Status())
    val state: StateFlow<Status> = status
    val configured get() = BuildConfig.PGS_ENABLED && BuildConfig.PGS_APP_ID.isNotBlank()
    private val ids by lazy { JSONObject(BuildConfig.PGS_IDS) }
    private val sent = mutableSetOf<String>()
    private var playerId: String? = null
    private var syncing = false
    private var activity: Activity? = null
    private fun id(key: String) = ids.optString(key).takeIf { it.isNotBlank() }

    fun attach(activity: Activity, interactive: Boolean = false) {
        this.activity = activity
        if (!configured || status.value.busy) return
        status.value = status.value.copy(busy = true, failed = false)
        val client = PlayGames.getGamesSignInClient(activity)
        val task = if (interactive) client.signIn() else client.isAuthenticated
        task.addOnSuccessListener { result ->
            status.value = Status(result.isAuthenticated)
            if (result.isAuthenticated) {
                PlayGames.getPlayersClient(activity).currentPlayer.addOnSuccessListener { player ->
                    if (playerId != player.playerId) { sent.clear(); playerId = player.playerId }
                    sync(save.current)
                }.addOnFailureListener { status.value = status.value.copy(failed = true) }
            } else { sent.clear(); playerId = null }
        }.addOnFailureListener { status.value = Status(failed = true) }
    }

    fun detach(activity: Activity) { if (this.activity === activity) this.activity = null }

    /** Called on foreground and after durable progress changes; successful unlocks are cached per account. */
    fun sync(data: SaveData) {
        val activity = activity ?: return
        if (!configured || !status.value.signedIn || playerId == null || syncing) return
        syncing = true
        val client = PlayGames.getAchievementsClient(activity)
        data.achievements.filterNot { it in sent }.forEach { key ->
            id("achievement_$key")?.let { remote ->
                client.unlockImmediate(remote).addOnSuccessListener { sent.add(key) }
                    .addOnFailureListener { status.value = status.value.copy(failed = true) }
            }
        }
        val boards = PlayGames.getLeaderboardsClient(activity)
        // Play Console: both boards use LARGER_IS_BETTER; no currency or paid advantage is ranked.
        id("leaderboard_stars")?.let { boards.submitScoreImmediate(it, data.totalStars.toLong())
            .addOnFailureListener { status.value = status.value.copy(failed = true) } }
        id("leaderboard_campaign")?.let { boards.submitScoreImmediate(it, data.levels.count { p -> p.value.completed }.toLong())
            .addOnFailureListener { status.value = status.value.copy(failed = true) } }
        syncing = false
    }

    fun showAchievements(activity: Activity) {
        if (!state.value.signedIn) return attach(activity, true)
        PlayGames.getAchievementsClient(activity).achievementsIntent.addOnSuccessListener { activity.startActivityForResult(it, 9101) }
            .addOnFailureListener { status.value = status.value.copy(failed = true) }
    }

    fun showLeaderboards(activity: Activity) {
        if (!state.value.signedIn) return attach(activity, true)
        PlayGames.getLeaderboardsClient(activity).allLeaderboardsIntent.addOnSuccessListener { activity.startActivityForResult(it, 9102) }
            .addOnFailureListener { status.value = status.value.copy(failed = true) }
    }
}
