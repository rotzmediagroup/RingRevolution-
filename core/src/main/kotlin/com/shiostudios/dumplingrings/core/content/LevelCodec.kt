package com.shiostudios.dumplingrings.core.content

import com.shiostudios.dumplingrings.core.model.LevelDefinition
import com.shiostudios.dumplingrings.core.model.SolveMetadata
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object LevelCodec {
    val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true; prettyPrintIndent = "  " }
    fun decode(text: String): LevelDefinition = json.decodeFromString(text)
    fun encode(level: LevelDefinition): String = json.encodeToString(level)
    fun encodeMeta(m: SolveMetadata): String = json.encodeToString(m)
    fun decodeMeta(text: String): SolveMetadata = json.decodeFromString(text)

    /** world id for a 1-based campaign level index */
    fun worldIdFor(index: Int): String = when {
        index <= 50 -> "blossom_teahouse"
        index <= 100 -> "lantern_night_market"
        else -> "moonlit_mountain_kitchen"
    }
    fun worldNumber(index: Int): Int = (index - 1) / 50 + 1
    fun chapterFor(index: Int): Int = (index - 1) / 10 + 1          // 1..15 global
    fun chapterInWorld(index: Int): Int = ((index - 1) % 50) / 10 + 1 // 1..5
    fun levelId(index: Int): String = "L" + index.toString().padStart(3, '0')
    fun resourcePath(index: Int): String = "levels/world_%02d/level_%03d.json".format(worldNumber(index), index)
}
