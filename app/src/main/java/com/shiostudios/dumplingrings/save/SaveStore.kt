package com.shiostudios.dumplingrings.save

import android.content.Context
import android.util.Log
import com.shiostudios.dumplingrings.core.systems.SaveData
import com.shiostudios.dumplingrings.core.systems.SaveMigrations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Local-first save store: atomic write (temp file + rename), rolling backup copy, corruption recovery,
 * schema migrations. All mutations go through [update]; writes happen synchronously so a process kill right
 * after a level completion cannot lose progress (saves are small, < 50 KB).
 */
class SaveStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(context.filesDir, "save.json")
    private val backup = File(context.filesDir, "save.bak.json")
    private val temp = File(context.filesDir, "save.tmp.json")

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SaveData> = _state
    val current: SaveData get() = _state.value
    var recoveredFromBackup = false
        private set

    private fun load(): SaveData {
        fun parse(f: File): SaveData? = try { if (f.exists()) SaveMigrations.migrate(json.decodeFromString<SaveData>(f.readText())) else null } catch (e: Exception) { Log.w("SaveStore", "corrupt ${f.name}: ${e.message}"); null }
        parse(file)?.let { return it }
        parse(backup)?.let { recoveredFromBackup = true; return it }
        return SaveData()
    }

    @Synchronized
    fun update(transform: (SaveData) -> SaveData): SaveData {
        val next = transform(_state.value)
        if (next === _state.value) return next
        _state.value = next
        persist(next)
        return next
    }

    @Synchronized
    private fun persist(data: SaveData) {
        try {
            temp.writeText(json.encodeToString(data))
            if (file.exists()) file.copyTo(backup, overwrite = true)
            if (!temp.renameTo(file)) { file.writeText(temp.readText()); temp.delete() }
        } catch (e: Exception) {
            Log.e("SaveStore", "persist failed", e)
        }
    }

    /** For tests / debug menus. */
    fun reset() = update { SaveData() }
}
