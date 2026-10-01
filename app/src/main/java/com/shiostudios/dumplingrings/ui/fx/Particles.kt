package com.shiostudios.dumplingrings.ui.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.shiostudios.dumplingrings.DumplingRingsApp
import com.shiostudios.dumplingrings.ui.board3d.drawSprite
import com.shiostudios.dumplingrings.ui.components.AssetImage
import kotlin.math.cos
import kotlin.math.sin

/**
 * Living background: slow Ken-Burns drift on the painted scene, a breathing warm light pool, world-specific ambient
 * particles (sakura petals / lantern embers and fireflies / moon dust and snow), rising steam wisps from the table edge
 * and a soft animated vignette. Everything is driven by a frame clock and respects Reduce Motion.
 */
@Composable
fun AnimatedSceneBackground(path: String, world: Int, reduceMotion: Boolean, quality: String, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(reduceMotion) { if (!reduceMotion) while (true) withFrameNanos { now = it / 1_000_000 } }
    val t = now / 1000f
    Box(modifier.fillMaxSize()) {
        // Ken Burns: ±2% scale, slow drift
        AssetImage(path, Modifier.fillMaxSize().graphicsLayer {
            if (!reduceMotion) { val s = 1.04f + 0.02f * sin(t * 0.11f); scaleX = s; scaleY = s; translationX = 8f * sin(t * 0.07f); translationY = 6f * cos(t * 0.05f) }
            else { scaleX = 1.02f; scaleY = 1.02f }
        }, ContentScale.Crop, downsample = true)
        if (!reduceMotion && quality != "low") ParticleLayer(world, now, quality)
        // breathing light pool / vignette
        Canvas(Modifier.fillMaxSize()) {
            val breathe = if (reduceMotion) 0f else 0.5f + 0.5f * sin(t * 0.6f)
            val warm = when (world) { 2 -> Color(0xFFFFB347); 3 -> Color(0xFFB9C8FF); else -> Color(0xFFFFE2A8) }
            drawRect(Brush.radialGradient(listOf(warm.copy(alpha = 0.10f + 0.06f * breathe), Color.Transparent), center = Offset(size.width * 0.5f, size.height * (0.42f + 0.02f * breathe)), radius = size.minDimension * 0.75f))
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xFF2A1608).copy(alpha = 0.28f)), center = Offset(size.width / 2, size.height / 2), radius = size.maxDimension * 0.78f))
        }
        content()
    }
}

private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var size: Float, var phase: Float, var frame: Int, var life: Float, var kind: Int)

/** Sprite particles per world; cheap (≤ 60 sprites) and deterministic per seed. */
@Composable
fun ParticleLayer(world: Int, nowMs: Long, quality: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val assets = DumplingRingsApp.of(ctx).assets
    val sprites = remember(world) {
        when (world) {
            2 -> listOf((1..3).mapNotNull { assets.sprite("sheet16/lantern_glow_f0$it") }, (1..4).mapNotNull { assets.sprite("sheet14/sparkle_pop_f0$it") })
            3 -> listOf((1..8).mapNotNull { assets.sprite("sheet16/sparkle_glow_f0$it") }, (1..4).mapNotNull { assets.sprite("sheet14/steam_puff_f0$it") })
            else -> listOf((1..6).mapNotNull { assets.sprite("sheet03/petal_f0$it") }, (1..4).mapNotNull { assets.sprite("sheet14/steam_puff_f0$it") })
        }
    }
    val count = if (quality == "high") 46 else 24
    val particles = remember(world, count) {
        val rnd = java.util.Random(world * 7919L)
        List(count) { i ->
            val kind = if (i % 5 == 0) 1 else 0
            Particle(rnd.nextFloat(), rnd.nextFloat(), 0f, 0f, 0.025f + rnd.nextFloat() * 0.035f, rnd.nextFloat() * 6.28f, rnd.nextInt(6), rnd.nextFloat(), kind)
        }
    }
    var last by remember { mutableLongStateOf(nowMs) }
    val dt = ((nowMs - last).coerceIn(0, 50)) / 1000f
    last = nowMs
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawIntoCanvas { cv ->
            val c = cv.nativeCanvas
            for (p in particles) {
                val t = nowMs / 1000f
                when (world) {
                    1 -> { // petals: drift down with sway; steam wisps rise slowly
                        if (p.kind == 0) { p.y += dt * (0.045f + p.size); p.x += dt * 0.03f * sin(t * 1.3f + p.phase); if (p.y > 1.08f) { p.y = -0.08f; p.x = (p.x + 0.37f) % 1f } }
                        else { p.y -= dt * 0.025f; p.x += dt * 0.012f * sin(t * 0.7f + p.phase); if (p.y < -0.1f) { p.y = 1.05f } }
                    }
                    2 -> { // embers rise and flicker; fireflies wander
                        if (p.kind == 0) { p.y -= dt * (0.02f + p.size * 0.5f); p.x += dt * 0.02f * sin(t * 2f + p.phase); if (p.y < -0.05f) { p.y = 1.05f; p.x = (p.x + 0.41f) % 1f } }
                        else { p.x += dt * 0.03f * cos(t * 0.9f + p.phase); p.y += dt * 0.03f * sin(t * 1.1f + p.phase * 2f) }
                    }
                    else -> { // moon dust drifts, snow falls gently
                        if (p.kind == 0) { p.y += dt * 0.02f; p.x += dt * 0.015f * sin(t * 0.8f + p.phase); if (p.y > 1.05f) { p.y = -0.05f } }
                        else { p.y += dt * 0.05f; p.x += dt * 0.02f * sin(t + p.phase); if (p.y > 1.05f) { p.y = -0.05f; p.x = (p.x + 0.29f) % 1f } }
                    }
                }
                val set = sprites[p.kind.coerceIn(0, sprites.size - 1)]
                if (set.isEmpty()) continue
                val img: ImageBitmap = set[(p.frame + (nowMs / 240 % set.size).toInt()) % set.size]
                val twinkle = 0.55f + 0.45f * sin(t * 2.2f + p.phase)
                val alpha = (if (world == 1 && p.kind == 1) 110f else 165f) * (if (world != 1) twinkle else 1f)
                drawSprite(c, img, p.x * w, p.y * h, size.minDimension * p.size * (if (world == 3 && p.kind == 1) 0.6f else 1f), alpha.toInt())
            }
        }
    }
}
