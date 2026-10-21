package com.shiostudios.dumplingrings.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.shiostudios.dumplingrings.DumplingRingsApp
import com.shiostudios.dumplingrings.ui.theme.DR
import com.shiostudios.dumplingrings.ui.theme.LocalReduceMotion
import kotlin.math.sin

/** The same porcelain surface and restrained ring engraving connects every utility screen. */
@Composable
fun StudioBackdrop(dark: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(if (dark)
        listOf(Color(0xFF102B32), Color(0xFF173B43), DR.IndigoDeep)
        else listOf(Color(0xFFFFFBF4), DR.Cream, Color(0xFFF0E5D6))))) {
        Canvas(Modifier.fillMaxSize()) {
            val ink = if (dark) DR.Gold.copy(alpha = .09f) else DR.Wood.copy(alpha = .07f)
            val center = Offset(size.width * 1.02f, size.height * .20f)
            repeat(3) { drawCircle(ink, size.width * (.35f + it * .08f), center, style = Stroke(1.5.dp.toPx())) }
            drawCircle(ink, size.width * .4f, Offset(0f, size.height * .9f), style = Stroke(1.dp.toPx()))
        }
        content()
    }
}

/** One cell of bespoke art; source atlas stays intact and is shared by all cards. */
@Composable
fun MedalArt(index: Int, modifier: Modifier = Modifier, description: String? = null) {
    val catalog = DumplingRingsApp.of(LocalContext.current).assets
    val path = if (index < 12) "art/medals.webp" else "art/milestones.webp"
    val tile = if (index < 12) index else index - 12
    var bitmap by remember(path) { mutableStateOf(catalog.cached(path)) }
    LaunchedEffect(path) { bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { catalog.image(path, false) } }
    val art = bitmap
    val semantics = if (description == null) modifier else modifier.then(Modifier.semanticsDescription(description))
    Canvas(semantics.clip(RoundedCornerShape(20.dp))) {
        if (art != null) {
            val cell = art.width / 4
            drawImage(art, srcOffset = IntOffset((tile % 4) * cell, (tile / 4) * cell), srcSize = IntSize(cell, cell),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        }
    }
}

private fun Modifier.semanticsDescription(label: String) = this.then(Modifier.semantics { contentDescription = label })

/** Distant key art, drifting light, independently moving petals and foreground character form four planes. */
@Composable
fun LayeredMenuScene(content: @Composable BoxScope.() -> Unit) {
    val reduce = LocalReduceMotion.current
    var time by remember { mutableFloatStateOf(0f) }
    if (!reduce) LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1_000_000_000f }
    }
    Box(Modifier.fillMaxSize().background(DR.IndigoDeep)) {
        AssetImage("menu/keyart.webp", Modifier.fillMaxSize().graphicsLayer {
            scaleX = 1.07f; scaleY = 1.07f
            translationX = if (reduce) 0f else sin(time * .18f) * 8.dp.toPx()
            translationY = if (reduce) 0f else sin(time * .13f) * 5.dp.toPx()
        }, ContentScale.Crop, downsample = true)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xA6102B32), Color(0x18102B32), Color(0xED102B32)))))
        Canvas(Modifier.fillMaxSize()) {
            repeat(22) { i ->
                val x = ((i * .137f + time * (.004f + i % 3 * .002f)) % 1.15f) * size.width
                val y = ((i * .193f + time * .013f) % 1.1f) * size.height
                val drift = sin(time * .6f + i) * 18.dp.toPx()
                drawOval(if (i % 3 == 0) DR.Gold.copy(alpha = .5f) else DR.Sakura.copy(alpha = .6f),
                    Offset(x + drift, y), androidx.compose.ui.geometry.Size((3 + i % 4).dp.toPx(), (2 + i % 3).dp.toPx()))
            }
        }
        content()
    }
}
