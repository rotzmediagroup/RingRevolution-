package com.shiostudios.dumplingrings.platform

import android.app.Activity
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.shiostudios.dumplingrings.core.systems.SaveData
import com.shiostudios.dumplingrings.core.systems.SaveMigrations
import com.shiostudios.dumplingrings.save.SaveStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.shiostudios.dumplingrings.core.systems.CloudArchive
import com.shiostudios.dumplingrings.core.systems.CloudSaves
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Manual, explicit backup/restore. Never merges spendable balances or trusts a cloud purchase entitlement. */
class CloudSaveService(private val save: SaveStore, private val games: PlayGamesService) {
    data class Choice(val local: SaveData, val remote: SaveData, val savedAt: Long, val conflict: Boolean, val upload: Boolean = false)
    data class Status(val busy: Boolean = false, val message: Result? = null, val choice: Choice? = null)
    enum class Result { SAVED, RESTORED, FAILED, NEED_SIGN_IN }
    private val mutable = MutableStateFlow(Status())
    val state: StateFlow<Status> = mutable
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var client: SnapshotsClient? = null
    private var opened: Snapshot? = null
    private var alternate: Snapshot? = null
    private var conflictId: String? = null
    private var pendingUpload = false

    fun open(activity: Activity, upload: Boolean) {
        if (mutable.value.busy || mutable.value.choice != null) return
        if (!games.state.value.signedIn) { mutable.value = Status(message = Result.NEED_SIGN_IN); return }
        mutable.value = Status(busy = true)
        pendingUpload = upload
        val c = PlayGames.getSnapshotsClient(activity)
        client = c
        c.open("ring_revolution_progress_v1", true, SnapshotsClient.RESOLUTION_POLICY_MANUAL)
            .addOnSuccessListener { result ->
                if (result.isConflict) {
                    val conflict = result.conflict ?: return@addOnSuccessListener fail()
                    opened = conflict.snapshot
                    alternate = conflict.conflictingSnapshot
                    conflictId = conflict.conflictId
                    // Let the player choose explicitly between the two cloud revisions first.
                    showChoice(conflict.snapshot, conflict.conflictingSnapshot, true)
                } else {
                    val snapshot = result.data ?: return@addOnSuccessListener fail()
                    opened = snapshot
                    val bytes = runCatching { snapshot.snapshotContents.readFully() }.getOrElse { fail(); return@addOnSuccessListener }
                    if (bytes.isEmpty()) {
                        if (upload) commit(save.current) else { close(); mutable.value = Status(message = Result.FAILED) }
                    } else showChoice(snapshot, null, false)
                }
            }.addOnFailureListener { fail() }
    }

    private fun decode(snapshot: Snapshot): CloudArchive {
        val bytes = snapshot.snapshotContents.readFully()
        require(bytes.size <= 512_000)
        val archive = json.decodeFromString<CloudArchive>(bytes.toString(Charsets.UTF_8))
        CloudSaves.validate(archive)
        return archive
    }

    private fun showChoice(first: Snapshot, second: Snapshot?, conflict: Boolean) {
        try {
            val a = decode(first)
            val b = second?.let { decode(it) }
            mutable.value = Status(choice = Choice(b?.save ?: save.current, a.save, a.savedAt, conflict, pendingUpload))
        } catch (_: Exception) { fail() }
    }

    /** For SDK conflicts choose cloud revision A/B, then confirm upload/restore against local progress. */
    fun choose(remote: Boolean) {
        val choice = mutable.value.choice ?: return
        mutable.value = Status(busy = true)
        if (choice.conflict) {
            val selected = (if (remote) opened else alternate) ?: return fail()
            val unused = if (remote) alternate else opened
            val id = conflictId ?: return fail()
            client?.resolveConflict(id, selected)?.addOnSuccessListener { result ->
                unused?.let { client?.discardAndClose(it) }
                alternate = null; conflictId = null
                if (result.isConflict) { close(); fail() }
                else {
                    opened = result.data
                    val snap = opened ?: return@addOnSuccessListener fail()
                    showChoice(snap, null, false)
                }
            }?.addOnFailureListener { fail() }
        } else if (pendingUpload) {
            // Upload requires choosing the device copy. The remote choice cancels without a write.
            if (!remote) commit(save.current) else cancel()
        } else if (remote) {
            val current = save.current
            val restored = CloudSaves.restore(current, choice.remote)
            save.update { restored }
            close(); mutable.value = Status(message = Result.RESTORED)
            games.sync(save.current)
        } else cancel()
    }

    private fun commit(data: SaveData) {
        val snapshot = opened ?: return fail()
        try {
            val cloud = CloudSaves.forBackup(data)
            check(snapshot.snapshotContents.writeBytes(json.encodeToString(CloudArchive(savedAt = System.currentTimeMillis(), save = cloud)).toByteArray()))
            val change = SnapshotMetadataChange.Builder().setDescription("${data.totalStars} stars · ${data.levels.count { it.value.completed }} / 150").build()
            client?.commitAndClose(snapshot, change)?.addOnSuccessListener {
                opened = null; mutable.value = Status(message = Result.SAVED)
            }?.addOnFailureListener { fail() }
        } catch (_: Exception) { fail() }
    }

    fun cancel() { close(); mutable.value = Status() }
    private fun close() {
        opened?.let { client?.discardAndClose(it) }; alternate?.let { client?.discardAndClose(it) }
        opened = null; alternate = null; conflictId = null
    }
    private fun fail() { close(); mutable.value = Status(message = Result.FAILED) }
}
