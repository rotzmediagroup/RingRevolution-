package com.shiostudios.dumplingrings.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiostudios.dumplingrings.DumplingRingsApp
import com.shiostudios.dumplingrings.ui.theme.DR
import com.shiostudios.dumplingrings.ui.theme.LocalReduceMotion

/** Draws an image from the runtime asset catalog (cached). */
@Composable
fun AssetImage(path: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit, contentDescription: String? = null, alpha: Float = 1f, downsample: Boolean = false) {
    val ctx = LocalContext.current
    val img: ImageBitmap? = remember(path) { DumplingRingsApp.of(ctx).assets.image(path, downsample) }
    if (img != null) Image(img, contentDescription, modifier, contentScale = contentScale, alpha = alpha)
    else Box(modifier)
}

@Composable
fun Sprite(id: String, modifier: Modifier = Modifier, contentDescription: String? = null, alpha: Float = 1f) =
    AssetImage("sprites/$id.webp", modifier, ContentScale.Fit, contentDescription, alpha)

/** Character sprite by base id + pose, falling back through similar poses because the sheets differ per character. */
@Composable
fun CharSprite(base: String, pose: String, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val ctx = LocalContext.current
    val assets = DumplingRingsApp.of(ctx).assets
    val candidates = when (pose) {
        "cheer" -> listOf("cheer", "happy", "celebrate", "excited", "idle")
        "happy" -> listOf("happy", "cheer", "celebrate", "idle")
        "celebrate" -> listOf("celebrate", "cheer", "happy", "idle")
        else -> listOf(pose, "idle")
    }
    val id = remember(base, pose) { candidates.map { "$base" + "_" + it }.firstOrNull { assets.exists("sprites/$it.webp") } ?: (base + "_idle") }
    Sprite(id, modifier, alpha = alpha)
}

/** Full-bleed background with a soft vignette so UI stays readable. */
@Composable
fun SceneBackground(path: String, modifier: Modifier = Modifier, dim: Float = 0f, content: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.fillMaxSize()) {
        AssetImage(path, Modifier.fillMaxSize(), ContentScale.Crop, downsample = true)
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        content()
    }
}

/** Hand-painted wooden sign panel used for dialogs and cards (9-sliced by stretching the generated sign board). */
@Composable
fun WoodPanel(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(20.dp), tint: Color = DR.Cream, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .shadow(10.dp, RoundedCornerShape(26.dp), ambientColor = DR.WoodDeep, spotColor = DR.WoodDeep)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.97f).compositeOverCream())))
            .border(4.dp, Brush.verticalGradient(listOf(DR.Wood, DR.WoodDark)), RoundedCornerShape(26.dp))
            .padding(padding),
        content = content,
    )
}

private fun Color.compositeOverCream(): Color = Color(red * 0.96f, green * 0.94f, blue * 0.9f, 1f)

/** Primary wooden button: generous 52dp min height, press squash, haptic click sound handled by caller. */
@Composable
fun WoodButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    color: Color = DR.Wood, textColor: Color = Color.White, minHeight: Dp = 52.dp, icon: (@Composable RowScope.() -> Unit)? = null,
    style: TextStyle = MaterialTheme.typography.labelLarge,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(if (pressed && !reduce) 0.95f else 1f, tween(90), label = "press")
    val ctx = LocalContext.current
    Box(
        modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.5f)
            .defaultMinSize(minHeight = minHeight)
            .shadow(if (enabled) 6.dp else 0.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(color.lighten(0.18f), color, color.darken(0.12f))))
            .border(2.dp, color.darken(0.3f), RoundedCornerShape(18.dp))
            .clickable(src, null, enabled = enabled, role = Role.Button) { DumplingRingsApp.of(ctx).audio.sfx("button_tap", 0.8f); onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            icon?.invoke(this)
            Text(text, style = style, color = textColor, textAlign = TextAlign.Center)
        }
    }
}

fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)
fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)

/** Round icon button (back, pause, settings…) with ≥48dp touch target. */
@Composable
fun RoundIconButton(sprite: String? = null, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 52.dp, color: Color = DR.Wood, sfx: String = "button_tap", content: (@Composable BoxScope.() -> Unit)? = null) {
    val ctx = LocalContext.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(if (pressed && !reduce) 0.92f else 1f, tween(90), label = "press")
    Box(
        modifier.size(size).scale(scale).shadow(5.dp, CircleShape).clip(CircleShape)
            .background(Brush.radialGradient(listOf(color.lighten(0.2f), color.darken(0.1f))))
            .border(2.dp, color.darken(0.3f), CircleShape)
            .clickable(src, null, role = Role.Button) { DumplingRingsApp.of(ctx).audio.sfx(sfx, 0.8f); onClick() }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (sprite != null) Sprite(sprite, Modifier.size(size * 0.62f)) else content?.invoke(this)
    }
}

/** Simple glyph icons drawn with paths (no icon font dependency). */
@Composable
fun Glyph(kind: String, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val s = Stroke(w * 0.14f)
        when (kind) {
            "back" -> { drawPath(Path().apply { moveTo(w * 0.65f, h * 0.2f); lineTo(w * 0.35f, h * 0.5f); lineTo(w * 0.65f, h * 0.8f) }, color, style = s) }
            "pause" -> { drawRoundRect(color, Offset(w * 0.25f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.17f, h * 0.6f)); drawRoundRect(color, Offset(w * 0.58f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.17f, h * 0.6f)) }
            "play" -> drawPath(Path().apply { moveTo(w * 0.32f, h * 0.2f); lineTo(w * 0.78f, h * 0.5f); lineTo(w * 0.32f, h * 0.8f); close() }, color)
            "undo" -> { drawArc(color, 300f, 240f, false, Offset(w * 0.2f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.6f), style = s); drawPath(Path().apply { moveTo(w * 0.2f, h * 0.22f); lineTo(w * 0.2f, h * 0.5f); lineTo(w * 0.48f, h * 0.5f); close() }, color) }
            "restart" -> { drawArc(color, 20f, 300f, false, Offset(w * 0.2f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.6f), style = s); drawPath(Path().apply { moveTo(w * 0.8f, h * 0.2f); lineTo(w * 0.8f, h * 0.5f); lineTo(w * 0.52f, h * 0.5f); close() }, color) }
            "gear" -> { drawCircle(color, w * 0.3f, style = s); for (i in 0 until 8) { val a = Math.toRadians(i * 45.0); drawLine(color, Offset(w / 2 + (w * 0.33f * Math.cos(a)).toFloat(), h / 2 + (h * 0.33f * Math.sin(a)).toFloat()), Offset(w / 2 + (w * 0.45f * Math.cos(a)).toFloat(), h / 2 + (h * 0.45f * Math.sin(a)).toFloat()), w * 0.16f) } }
            "close" -> { drawLine(color, Offset(w * 0.25f, h * 0.25f), Offset(w * 0.75f, h * 0.75f), w * 0.14f); drawLine(color, Offset(w * 0.75f, h * 0.25f), Offset(w * 0.25f, h * 0.75f), w * 0.14f) }
            "left" -> drawPath(Path().apply { moveTo(w * 0.7f, h * 0.15f); lineTo(w * 0.3f, h * 0.5f); lineTo(w * 0.7f, h * 0.85f) }, color, style = s)
            "right" -> drawPath(Path().apply { moveTo(w * 0.3f, h * 0.15f); lineTo(w * 0.7f, h * 0.5f); lineTo(w * 0.3f, h * 0.85f) }, color, style = s)
            "check" -> drawPath(Path().apply { moveTo(w * 0.2f, h * 0.52f); lineTo(w * 0.42f, h * 0.74f); lineTo(w * 0.8f, h * 0.3f) }, color, style = s)
            "star" -> drawPath(starPath(w / 2, h / 2, w * 0.46f, w * 0.2f), color)
            "lock" -> { drawRoundRect(color, Offset(w * 0.25f, h * 0.45f), androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.4f)); drawArc(color, 180f, 180f, false, Offset(w * 0.33f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.34f, h * 0.44f), style = Stroke(w * 0.1f)) }
        }
    }
}

fun starPath(cx: Float, cy: Float, outer: Float, inner: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = Math.toRadians(-90.0 + i * 36.0)
        val x = cx + (r * Math.cos(a)).toFloat(); val y = cy + (r * Math.sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@Composable
fun StarRow(stars: Int, modifier: Modifier = Modifier, size: Dp = 28.dp, max: Int = 3) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(max) { i -> Glyph("star", Modifier.size(size), if (i < stars) DR.StarOn else DR.StarOff) }
    }
}

@Composable
fun CoinPill(coins: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.shadow(3.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(DR.WoodDeep.copy(alpha = 0.85f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sprite("sheet05/icon_coin_sakura", Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(coins.toString(), style = MaterialTheme.typography.labelLarge, color = DR.Gold)
    }
}

@Composable
fun TitleText(text: String, modifier: Modifier = Modifier, color: Color = DR.WoodDeep, size: Int = 30) {
    Box(modifier) {
        Text(text, style = MaterialTheme.typography.displayMedium.copy(fontSize = size.sp, lineHeight = (size + 4).sp), color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(top = 2.dp, start = 1.dp), textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.displayMedium.copy(fontSize = size.sp, lineHeight = (size + 4).sp), color = color, textAlign = TextAlign.Center)
    }
}

/** Top bar with a back button and a title; respects the status bar inset through the caller's padding. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(contentDescription = "back", onClick = onBack, sfx = "button_back") { Glyph("back", Modifier.size(26.dp)) }
        Spacer(Modifier.width(10.dp))
        TitleText(title, Modifier.weight(1f), size = 24)
        trailing?.invoke()
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(2.dp).background(DR.Wood.copy(alpha = 0.35f)))
