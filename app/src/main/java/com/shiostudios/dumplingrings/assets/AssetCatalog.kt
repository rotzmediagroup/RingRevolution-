package com.shiostudios.dumplingrings.assets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Loads optimised runtime images from the APK assets with an LRU cache sized to a fraction of the heap.
 * Large backgrounds are decoded with a sample size when the device is memory constrained (low-memory variants).
 */
class AssetCatalog(private val context: Context, private val lowMemory: Boolean) {
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / (if (lowMemory) 8 else 5)).toInt()
    private val cache = object : LruCache<String, ImageBitmap>(maxKb) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4 / 1024
    }

    fun exists(path: String): Boolean = try { context.assets.open(path).close(); true } catch (e: Exception) { false }

    /** path is relative to assets/, e.g. "sprites/sheet14/sparkle_pop_f01.webp" or "bg/world1_portrait.webp" */
    fun image(path: String, allowDownsample: Boolean = false): ImageBitmap? {
        cache.get(path)?.let { return it }
        val bmp = decode(path, allowDownsample) ?: return null
        val img = bmp.asImageBitmap()
        cache.put(path, img)
        return img
    }

    fun sprite(id: String): ImageBitmap? = image("sprites/$id.webp")

    private fun decode(path: String, allowDownsample: Boolean): Bitmap? = try {
        context.assets.open(path).use { s ->
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = if (allowDownsample && lowMemory) 2 else 1
            }
            BitmapFactory.decodeStream(s, null, opts)
        }
    } catch (e: Exception) { null }

    fun trim() { cache.evictAll() }
    fun trimToHalf() { cache.trimToSize(cache.maxSize() / 2) }

    companion object {
        fun backgroundFor(world: Int, landscape: Boolean) = "bg/world${world}_${if (landscape) "landscape" else "portrait"}.webp"
        fun materialPath(materialId: String): String = "materials/" + when (materialId) {
            "dough_sesame" -> "sesame_dough"; "dough_matcha" -> "matcha"; "dough_beet" -> "beet_pink"
            "dough_ube" -> "ube_purple"; "dough_gold" -> "gold"; "dough_bamboo" -> "bamboo"; else -> "sesame_dough"
        } + ".webp"
    }
}
