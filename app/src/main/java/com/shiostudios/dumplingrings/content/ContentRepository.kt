package com.shiostudios.dumplingrings.content

import android.content.Context
import com.shiostudios.dumplingrings.core.content.LevelCodec
import com.shiostudios.dumplingrings.core.generator.LevelSpec
import com.shiostudios.dumplingrings.core.model.LevelDefinition
import com.shiostudios.dumplingrings.core.systems.EconomyConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WorldDef(
    val id: String, val number: Int, val levels: List<Int>, val nameKey: String, val palette: Map<String, String>,
    val music: String, val finaleMusic: String, val ambience: String, val background: String, val map: String,
    val intro: String, val character: String, val unlockStars: Int = 0,
)

@Serializable
data class ChapterDef(
    val number: Int, val world: Int, val levels: List<Int>, val name: Map<String, String>, val mechanic: String,
    val scene: String, val rewardCharacter: String, val rewardCosmetic: String, val rewardBoosters: Map<String, Int>, val rewardCoins: Int,
)

@Serializable
data class CosmeticDef(val id: String, val kind: String, val nameKey: String, val unlock: Map<String, kotlinx.serialization.json.JsonElement>, val sprite: String) {
    val unlockChapter: Int? get() = unlock["chapter"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() }
    val unlockPremium: Boolean get() = unlock["premium"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content == "true" } ?: false
}

/** Loads all shipped content (levels, worlds, chapters, cosmetics, economy) from the APK assets. Offline only. */
class ContentRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private fun read(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }

    val worlds: List<WorldDef> by lazy { json.decodeFromString(read("content/worlds.json")) }
    val chapters: List<ChapterDef> by lazy { json.decodeFromString(read("content/chapters.json")) }
    val cosmetics: List<CosmeticDef> by lazy { json.decodeFromString(read("content/cosmetics.json")) }
    val economy: EconomyConfig by lazy { json.decodeFromString(read("content/economy.json")) }
    val roadmap: List<LevelSpec> by lazy { json.decodeFromString(read("content/roadmap.json")) }

    private val levelCache = HashMap<Int, LevelDefinition>()

    @Synchronized
    fun level(index: Int): LevelDefinition = levelCache.getOrPut(index) { LevelCodec.decode(read("content/" + LevelCodec.resourcePath(index))) }

    fun levelExists(index: Int): Boolean = try { context.assets.open("content/" + LevelCodec.resourcePath(index)).close(); true } catch (e: Exception) { false }

    val levelCount: Int get() = 150
    fun world(number: Int) = worlds.first { it.number == number }
    fun worldOf(levelIndex: Int) = world(LevelCodec.worldNumber(levelIndex))
    fun chapter(number: Int) = chapters.first { it.number == number }
    fun chapterOf(levelIndex: Int) = chapter(LevelCodec.chapterFor(levelIndex))
}
