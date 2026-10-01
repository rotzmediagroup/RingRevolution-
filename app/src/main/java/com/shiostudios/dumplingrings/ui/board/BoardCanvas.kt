package com.shiostudios.dumplingrings.ui.board

import android.graphics.BitmapShader
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shiostudios.dumplingrings.DumplingRingsApp
import com.shiostudios.dumplingrings.assets.AssetCatalog
import com.shiostudios.dumplingrings.core.geometry.Geometry
import com.shiostudios.dumplingrings.core.model.ObstacleShape
import com.shiostudios.dumplingrings.core.model.RingDef
import com.shiostudios.dumplingrings.core.model.WeavePattern
import com.shiostudios.dumplingrings.game.GameController
import com.shiostudios.dumplingrings.ui.theme.DR
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One crossing between two rings with the over/under decision resolved. */
private class CrossInfo(val a: Int, val b: Int, val x: Double, val y: Double, val angleOnA: Double, val angleOnB: Double, val aOver: Boolean)

/**
 * Procedural, exact ring renderer: gaps, rotation and hit boxes come straight from the rule engine's geometry,
 * so what the player sees is what the solver reasons about. Materials are hand-painted tiles used as shaders.
 */
@Composable
fun BoardCanvas(
    controller: GameController,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    highContrast: Boolean = false,
    effectQuality: String = "high",
    themeMaterial: String? = null,
    onTapEmpty: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val assets = DumplingRingsApp.of(ctx).assets
    val level = controller.level
    val engine = controller.engine
    val crossings = remember(level.id) {
        val out = ArrayList<CrossInfo>()
        for (a in 0 until engine.n) for (b in a + 1 until engine.n) {
            val cr = Geometry.crossings(level.rings[a], level.rings[b])
            if (cr.size < 2) continue
            val pattern = engine.weave(a, b)
            cr.forEachIndexed { idx, c -> out.add(CrossInfo(a, b, c.x, c.y, c.angleOnA, c.angleOnB, Geometry.aOverAt(pattern, idx))) }
        }
        out
    }
    val materials = remember(level.id, themeMaterial) {
        level.rings.map { r -> assets.image(AssetCatalog.materialPath(themeMaterial ?: r.materialId)) }
    }
    val sprites = remember {
        mapOf(
            "sparkle" to (1..4).mapNotNull { assets.sprite("sheet14/sparkle_pop_f0$it") },
            "steam" to (1..4).mapNotNull { assets.sprite("sheet14/steam_puff_f0$it") },
            "dumpling" to (1..4).mapNotNull { assets.sprite("sheet12/dumpling_cheer_f0$it") },
            "hand" to (1..5).mapNotNull { assets.sprite("sheet14/tap_hand_f0$it") },
            "petal" to (1..6).mapNotNull { assets.sprite("sheet03/petal_f0$it") },
        )
    }
    var now by mutableLongStateOf(System.currentTimeMillis())
    LaunchedEffect(Unit) { while (true) { withFrameNanos { now = System.currentTimeMillis() }; controller.pruneAnimations(now) } }
    val touchSlop = with(density) { 22.dp.toPx() }
    val minHit = with(density) { 24.dp.toPx() }
    val paints = remember { Paints() }
    val petals = remember(level.id) { Petals(if (effectQuality == "low") 0 else if (effectQuality == "medium") 8 else 14, level.seed) }

    Canvas(
        modifier
            .semantics { contentDescription = "Puzzle board" }
            .pointerInput(level.id) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent().changes.firstOrNull { it.pressed && it.changedToDownIgnoreConsumed() } ?: continue
                        val size = this.size
                        val S = min(size.width, size.height).toFloat()
                        val ox = (size.width - S) / 2f; val oy = (size.height - S) / 2f
                        val p = down.position
                        val hit = hitRing(controller, p, S, ox, oy, minHit)
                        if (hit == null) { onTapEmpty(); controller.select(null); continue }
                        val r = level.rings[hit]
                        val cx = ox + r.center[0].toFloat() * S; val cy = oy + (1 - r.center[1].toFloat()) * S
                        var lastAngle = Math.toDegrees(atan2((cy - p.y).toDouble(), (p.x - cx).toDouble())).toFloat()
                        var total = 0f
                        var moved = false
                        controller.beginDrag(hit)
                        down.consume()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) { if (moved) controller.endDrag() else { controller.endDrag(); controller.select(hit) }; break }
                            val q = ch.position
                            if (!moved && hypot(q.x - p.x, q.y - p.y) > touchSlop * 0.35f) moved = true
                            val ang = Math.toDegrees(atan2((cy - q.y).toDouble(), (q.x - cx).toDouble())).toFloat()
                            var d = ang - lastAngle
                            if (d > 180) d -= 360; if (d < -180) d += 360
                            lastAngle = ang
                            if (moved) { total += d; controller.dragTo(total) }
                            ch.consume()
                        }
                    }
                }
            },
    ) {
        controller.revision // read for invalidation
        val S = min(size.width, size.height)
        val ox = (size.width - S) / 2f; val oy = (size.height - S) / 2f
        fun px(x: Double) = ox + x.toFloat() * S
        fun py(y: Double) = oy + (1 - y.toFloat()) * S
        val state = controller.state
        val angles = FloatArray(engine.n) { controller.visualAngleFor(it, now) }

        if (!reduceMotion && petals.count > 0) petals.draw(this, now, S, ox, oy, sprites["petal"].orEmpty())

        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            // ---- obstacles' shadows & rings (bottom layer): rings first, then obstacles on top
            val order = (0 until engine.n).toList()
            // ring shadows
            for (i in order) {
                if (controller.isRemoved(i)) continue
                val r = level.rings[i]
                val t = r.thickness.toFloat() * S
                drawRingArcs(c, r, angles[i], px(r.center[0]) + t * 0.15f, py(r.center[1]) + t * 0.35f, r.radius.toFloat() * S, t * 1.1f, paints.shadow, null)
            }
            for (i in order) {
                if (controller.isRemoved(i)) continue
                val r = level.rings[i]
                val anim = controller.releases.firstOrNull { it.ring == i }
                var cx = px(r.center[0]); var cy = py(r.center[1])
                var scale = 1f; var alpha = 1f
                if (anim != null) {
                    val tA = ((now - anim.startMs) / 450f).coerceIn(0f, 1f)
                    val e = 1 - (1 - tA) * (1 - tA)
                    scale = 1f + 0.25f * e; alpha = 1f - e
                    val ex = cos(Math.toRadians(anim.exitDeg.toDouble())).toFloat(); val ey = -sin(Math.toRadians(anim.exitDeg.toDouble())).toFloat()
                    cx += ex * 0.18f * S * e; cy += ey * 0.18f * S * e
                    if (reduceMotion) { scale = 1f; cx = px(r.center[0]); cy = py(r.center[1]) }
                }
                if (controller.shakeRing == i) { val k = ((controller.shakeUntil - now) / 350f).coerceIn(0f, 1f); cx += (sin(now / 18.0) * 5 * k).toFloat() }
                val rad = r.radius.toFloat() * S * scale
                val t = r.thickness.toFloat() * S * scale
                val selected = controller.selected == i
                if (selected) {
                    paints.glow.color = DR.Gold.toArgb(); paints.glow.alpha = (150 * alpha).toInt(); paints.glow.strokeWidth = t * 1.9f
                    drawRingArcs(c, r, angles[i], cx, cy, rad, t * 1.9f, paints.glow, null)
                }
                val mat = materials[i]
                drawRing(c, r, angles[i], cx, cy, rad, t, mat, paints, alpha, highContrast, controller.isLocked(i), r.colorId)
            }
            // ---- over/under: redraw the over ring's segment around each crossing (with a contact shadow)
            for (cr in crossings) {
                val top = if (cr.aOver) cr.a else cr.b
                val under = if (cr.aOver) cr.b else cr.a
                if (controller.isRemoved(top) || controller.isRemoved(under)) continue
                if (controller.releases.any { it.ring == top || it.ring == under }) continue
                val r = level.rings[top]
                val angOnTop = if (cr.aOver) cr.angleOnA else cr.angleOnB
                val local = Geometry.normDeg(angOnTop - angles[top])
                if (Geometry.inAnyGap(r.gaps, 0.0, local, 0.0)) continue
                val tUnder = level.rings[under].thickness
                val span = Math.toDegrees(asin(min(1.0, (tUnder * 0.9 + r.thickness * 0.25) / r.radius)))
                val a0 = angOnTop - span; val a1 = angOnTop + span
                val rad = r.radius.toFloat() * S; val t = r.thickness.toFloat() * S
                val cx = px(r.center[0]); val cy = py(r.center[1])
                // clip the over segment to the ring's existing wire
                val segs = wireSegments(r, angles[top]).mapNotNull { (s, e) -> intersectArc(s, e, a0, a1) }
                for ((s, e) in segs) {
                    arcStroke(c, cx, cy, rad, s, e, t * 1.35f, paints.contactShadow)
                    materialArc(c, cx, cy, rad, s, e, t, materials[top], paints, 1f, r, highContrast, r.colorId, capped = false)
                }
            }
            // ---- obstacles on top of everything
            for ((oi, o) in level.obstacles.withIndex()) {
                val drv = engine.obstacleDriver[oi]
                if (drv >= 0 && controller.isRemoved(drv)) continue
                val driverAngle = if (drv >= 0) angles[drv] else 0f
                drawObstacle(c, o, driverAngle, S, ox, oy, paints, assets, highContrast)
            }
            // ---- decorations: locks, link indicators, arc ranges, colour badges
            for (i in order) {
                if (controller.isRemoved(i)) continue
                val r = level.rings[i]
                val cx = px(r.center[0]); val cy = py(r.center[1]); val rad = r.radius.toFloat() * S; val t = r.thickness.toFloat() * S
                r.arc?.let { arc -> drawArcRange(c, cx, cy, rad + t * 0.95f, arc.minDeg.toFloat(), arc.maxDeg.toFloat(), angles[i], t, paints) }
                if (controller.isLocked(i)) drawLock(c, cx, cy, t, paints, assets)
                r.linkGroup?.let { g ->
                    for (j in engine.group[i]) if (j > i && !controller.isRemoved(j)) {
                        val rj = level.rings[j]
                        drawLink(c, cx, cy, px(rj.center[0]), py(rj.center[1]), level.rings[j].linkRatio * r.linkRatio < 0, t, paints)
                    }
                }
            }
            // ---- booster overlay (hint / peek ghost)
            controller.overlay?.let { ov ->
                ov.hints.forEachIndexed { idx, h ->
                    val r = level.rings[h.ring]
                    val cx = px(r.center[0]); val cy = py(r.center[1]); val rad = r.radius.toFloat() * S; val t = r.thickness.toFloat() * S
                    paints.ghost.alpha = if (idx == 0) 110 else 60
                    drawRingArcs(c, r, h.targetAngleDeg.toFloat(), cx, cy, rad, t * 0.9f, paints.ghost, null)
                    drawDirectionArrow(c, cx, cy, rad + t * 1.6f, h.clockwise, now, paints)
                }
            }
            // ---- release FX
            for (anim in controller.releases) {
                val r = level.rings[anim.ring]
                val tA = ((now - anim.startMs) / 650f).coerceIn(0f, 1f)
                if (tA <= 0f) continue
                val cx = px(r.center[0]); val cy = py(r.center[1])
                val sp = sprites["sparkle"].orEmpty(); val st = sprites["steam"].orEmpty(); val du = sprites["dumpling"].orEmpty()
                if (!reduceMotion && effectQuality != "low" && sp.isNotEmpty()) drawSprite(c, sp[(tA * (sp.size - 1)).toInt()], cx, cy - r.radius.toFloat() * S * 0.3f, S * 0.22f * (0.6f + tA), (255 * (1 - tA)).toInt())
                if (!reduceMotion && st.isNotEmpty()) drawSprite(c, st[(tA * (st.size - 1)).toInt()], cx, cy - tA * S * 0.12f, S * 0.18f * (0.8f + tA * 0.6f), (200 * (1 - tA)).toInt())
                if (du.isNotEmpty()) {
                    val e = 1 - (1 - tA) * (1 - tA)
                    drawSprite(c, du[(tA * (du.size - 1)).toInt()], cx, cy - e * S * 0.35f, S * 0.17f * (0.5f + 0.7f * sin(tA * Math.PI).toFloat()), (255 * (1 - tA * tA)).toInt())
                }
            }
            // ---- tutorial pointer
            controller.tutorialTarget?.let { ring ->
                val hand = sprites["hand"].orEmpty()
                if (hand.isNotEmpty() && !controller.isRemoved(ring)) {
                    val r = level.rings[ring]
                    val phase = (now % 1800) / 1800f
                    val ang = Math.toRadians((angles[ring] + 20 + phase * 60).toDouble())
                    val hx = px(r.center[0]) + (r.radius * S * cos(ang)).toFloat(); val hy = py(r.center[1]) - (r.radius * S * sin(ang)).toFloat()
                    drawSprite(c, hand[((phase * 10).toInt()) % hand.size], hx + S * 0.05f, hy + S * 0.06f, S * 0.16f, 235)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- hit testing

private fun hitRing(controller: GameController, p: Offset, S: Float, ox: Float, oy: Float, minHit: Float): Int? {
    val level = controller.level
    var best: Int? = null; var bestD = Float.MAX_VALUE
    for (i in level.rings.indices) {
        if (controller.isRemoved(i)) continue
        val r = level.rings[i]
        val cx = ox + r.center[0].toFloat() * S; val cy = oy + (1 - r.center[1].toFloat()) * S
        val d = hypot(p.x - cx, p.y - cy)
        val band = abs(d - r.radius.toFloat() * S)
        val tol = maxOf(r.thickness.toFloat() * S * 0.75f, minHit)
        if (band <= tol) {
            // ignore taps inside the ring's gap (the wire is not there)
            val ang = Geometry.normDeg(Math.toDegrees(atan2((cy - p.y).toDouble(), (p.x - cx).toDouble())))
            val local = Geometry.normDeg(ang - controller.visualAngles[i])
            val inGap = Geometry.inAnyGap(r.gaps, 0.0, local, -6.0)
            val score = band + (if (inGap) tol * 0.9f else 0f) + (if (controller.selected == i) -tol * 0.3f else 0f)
            if (score < bestD) { bestD = score; best = i }
        }
    }
    return best
}

// ---------------------------------------------------------------- drawing helpers

private class Paints {
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x33000000; strokeCap = Paint.Cap.ROUND }
    val contactShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x55000000; strokeCap = Paint.Cap.BUTT }
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; maskFilter = android.graphics.BlurMaskFilter(18f, android.graphics.BlurMaskFilter.Blur.NORMAL) }
    val material = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    val edgeDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    val edgeLight = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    val gapMark = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val ghost = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xFFFFFFFF.toInt() }
    val arcRange = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xAAFFFFFF.toInt() }
    val link = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xCC5A3A28.toInt() }
    val stick = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val sprite = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val rect = RectF()
    val matrix = Matrix()
}

/** Wire segments (math angles, absolute) of a ring at a visual angle: the complement of its gaps, unwrapped. */
private fun wireSegments(r: RingDef, angle: Float): List<Pair<Double, Double>> {
    val gaps = r.gaps.map { Geometry.normDeg(it.startDeg) to it.widthDeg }.sortedBy { it.first }
    val out = ArrayList<Pair<Double, Double>>()
    var cur = 0.0
    var wrapTrim = 0.0
    for ((s, w) in gaps) {
        if (s > cur) out.add(cur to s)
        cur = maxOf(cur, s + w)
    }
    if (cur < 360) out.add(cur to 360.0) else wrapTrim = cur - 360
    val res = out.map { (a, b) -> a to b }.toMutableList()
    if (wrapTrim > 0 && res.isNotEmpty()) res[0] = maxOf(res[0].first, wrapTrim) to res[0].second
    return res.filter { it.second - it.first > 0.5 }.map { (a, b) -> a + angle to b + angle }
}

/** Intersection of arc [s,e] with window [a0,a1] (absolute degrees), handling wrap by testing shifted copies. */
private fun intersectArc(s: Double, e: Double, a0: Double, a1: Double): Pair<Double, Double>? {
    for (k in -1..1) {
        val ss = s + k * 360; val ee = e + k * 360
        val lo = maxOf(ss, a0); val hi = minOf(ee, a1)
        if (hi - lo > 0.3) return lo to hi
    }
    return null
}

private fun arcStroke(c: android.graphics.Canvas, cx: Float, cy: Float, rad: Float, a0: Double, a1: Double, width: Float, paint: Paint) {
    paint.strokeWidth = width
    val rect = RectF(cx - rad, cy - rad, cx + rad, cy + rad)
    c.drawArc(rect, -a1.toFloat(), (a1 - a0).toFloat(), false, paint)
}

private fun drawRingArcs(c: android.graphics.Canvas, r: RingDef, angle: Float, cx: Float, cy: Float, rad: Float, width: Float, paint: Paint, unused: Any?) {
    val capDeg = Math.toDegrees(asin(min(1.0, (width / 2.0) / rad)))
    paint.strokeWidth = width
    val rect = RectF(cx - rad, cy - rad, cx + rad, cy + rad)
    for ((a, b) in wireSegments(r, angle)) {
        val s = a + capDeg; val e = b - capDeg
        if (e <= s) continue
        c.drawArc(rect, -e.toFloat(), (e - s).toFloat(), false, paint)
    }
}

private fun materialArc(c: android.graphics.Canvas, cx: Float, cy: Float, rad: Float, a: Double, b: Double, t: Float, mat: ImageBitmap?, p: Paints, alpha: Float, r: RingDef, highContrast: Boolean, colorId: String?, capped: Boolean) {
    val capDeg = if (capped) Math.toDegrees(asin(min(1.0, (t / 2.0) / rad))) else 0.0
    val s = a + capDeg; val e = b - capDeg
    if (e <= s) return
    val rect = RectF(cx - rad, cy - rad, cx + rad, cy + rad)
    val start = -e.toFloat(); val sweep = (e - s).toFloat()
    p.material.strokeCap = if (capped) Paint.Cap.ROUND else Paint.Cap.BUTT
    p.edgeDark.strokeCap = p.material.strokeCap; p.edgeLight.strokeCap = p.material.strokeCap
    if (mat != null) {
        val bmp = mat.asAndroidBitmap()
        val shader = BitmapShader(bmp, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        p.matrix.reset(); val sc = (t * 3.2f) / bmp.width; p.matrix.setScale(sc, sc); p.matrix.postTranslate(cx, cy)
        shader.setLocalMatrix(p.matrix)
        p.material.shader = shader; p.material.color = 0xFFFFFFFF.toInt()
    } else { p.material.shader = null; p.material.color = 0xFFE9CDA0.toInt() }
    p.material.alpha = (255 * alpha).toInt(); p.material.strokeWidth = t
    c.drawArc(rect, start, sweep, false, p.material)
    // soft bevel: darker outer edge + light inner highlight
    p.edgeDark.color = (if (highContrast) 0xFF2A1A10 else 0x55402A1A).toInt(); p.edgeDark.alpha = ((if (highContrast) 255 else 85) * alpha).toInt(); p.edgeDark.strokeWidth = if (highContrast) t * 0.14f else t * 0.1f
    val ro = rad + t / 2 - p.edgeDark.strokeWidth / 2
    c.drawArc(RectF(cx - ro, cy - ro, cx + ro, cy + ro), start, sweep, false, p.edgeDark)
    val ri = rad - t / 2 + p.edgeDark.strokeWidth / 2
    c.drawArc(RectF(cx - ri, cy - ri, cx + ri, cy + ri), start, sweep, false, p.edgeDark)
    p.edgeLight.color = 0x66FFFFFF; p.edgeLight.alpha = (100 * alpha).toInt(); p.edgeLight.strokeWidth = t * 0.12f
    val rl = rad - t * 0.22f
    c.drawArc(RectF(cx - rl, cy - rl, cx + rl, cy + rl), start, sweep, false, p.edgeLight)
    // colour id stripe (colour gates) + dot pattern for colour-blind players
    if (colorId != null) {
        p.edgeLight.color = DR.gateColor(colorId).toArgb(); p.edgeLight.alpha = (230 * alpha).toInt(); p.edgeLight.strokeWidth = t * 0.16f
        val rc = rad + t * 0.3f
        c.drawArc(RectF(cx - rc, cy - rc, cx + rc, cy + rc), start, sweep, false, p.edgeLight)
        p.fill.color = DR.gateColor(colorId).toArgb(); p.fill.alpha = (255 * alpha).toInt()
        val step = when (colorId) { "red" -> 14.0; "jade" -> 22.0; else -> 30.0 }
        var ang = s + step / 2
        while (ang < e) { val k = Math.toRadians(ang); c.drawCircle(cx + (rad * cos(k)).toFloat(), cy - (rad * sin(k)).toFloat(), t * (if (colorId == "ube") 0.11f else 0.08f), p.fill); ang += step }
    }
}

private fun drawRing(c: android.graphics.Canvas, r: RingDef, angle: Float, cx: Float, cy: Float, rad: Float, t: Float, mat: ImageBitmap?, p: Paints, alpha: Float, highContrast: Boolean, locked: Boolean, colorId: String?) {
    for ((a, b) in wireSegments(r, angle)) materialArc(c, cx, cy, rad, a, b, t, mat, p, alpha, r, highContrast, colorId, capped = true)
    // gap markers: small pale notches at both gap edges so the opening reads clearly even on busy boards
    p.gapMark.color = if (highContrast) 0xFFFFFFFF.toInt() else 0xCCFFF4E3.toInt(); p.gapMark.alpha = (p.gapMark.alpha * alpha).toInt()
    for (g in r.gaps) {
        for (edge in listOf(g.startDeg, g.startDeg + g.widthDeg)) {
            val k = Math.toRadians(edge + angle)
            c.drawCircle(cx + (rad * cos(k)).toFloat(), cy - (rad * sin(k)).toFloat(), t * 0.14f, p.gapMark)
        }
    }
    if (locked) { p.fill.color = 0x66000000; p.fill.alpha = (90 * alpha).toInt(); for ((a, b) in wireSegments(r, angle)) arcStroke(c, cx, cy, rad, a, b, t, p.fill.apply { style = Paint.Style.STROKE; strokeWidth = t }); p.fill.style = Paint.Style.FILL }
}

private fun drawObstacle(c: android.graphics.Canvas, o: com.shiostudios.dumplingrings.core.model.ObstacleDef, driverAngle: Float, S: Float, ox: Float, oy: Float, p: Paints, assets: AssetCatalog, highContrast: Boolean) {
    fun px(x: Double) = ox + x.toFloat() * S
    fun py(y: Double) = oy + (1 - y.toFloat()) * S
    val rot = Math.toRadians(o.baseAngleDeg + driverAngle.toDouble())
    fun rp(pt: List<Double>): Pair<Float, Float> {
        val x = pt[0] - o.center[0]; val y = pt[1] - o.center[1]
        return px(o.center[0] + x * cos(rot) - y * sin(rot)) to py(o.center[1] + x * sin(rot) + y * cos(rot))
    }
    val t = o.thickness.toFloat() * S
    val col = if (o.colorId != null) DR.gateColor(o.colorId).toArgb() else 0xFF8B5A2B.toInt()
    when (o.shape) {
        ObstacleShape.SEGMENT -> {
            val (x0, y0) = rp(o.from); val (x1, y1) = rp(o.to)
            // shadow, body, highlight
            p.stick.color = 0x44000000; p.stick.strokeWidth = t * 1.25f; c.drawLine(x0 + t * 0.2f, y0 + t * 0.45f, x1 + t * 0.2f, y1 + t * 0.45f, p.stick)
            p.stick.color = col; p.stick.strokeWidth = t; c.drawLine(x0, y0, x1, y1, p.stick)
            p.stick.color = 0x55FFFFFF; p.stick.strokeWidth = t * 0.3f; c.drawLine(x0, y0 - t * 0.22f, x1, y1 - t * 0.22f, p.stick)
            if (highContrast) { p.stick.color = 0xFF000000.toInt(); p.stick.strokeWidth = t * 0.1f; c.drawLine(x0, y0, x1, y1, p.stick) }
            if (o.visualId == "lantern_gate") {
                // little lantern at the outer end in the gate colour
                p.fill.color = col; p.fill.alpha = 255
                c.drawCircle(x1, y1, t * 1.1f, p.fill)
                p.fill.color = 0xAAFFFFFF.toInt(); c.drawCircle(x1 - t * 0.3f, y1 - t * 0.3f, t * 0.35f, p.fill)
            } else if (o.visualId == "lantern_arm") {
                val (pxv, pyv) = rp(listOf(o.center[0], o.center[1]))
                p.fill.color = 0xFF5A3A28.toInt(); p.fill.alpha = 255; c.drawCircle(pxv, pyv, t * 0.9f, p.fill)
                p.fill.color = 0xFFFFB347.toInt(); c.drawCircle(x1, y1, t * 1.1f, p.fill)
            }
        }
        ObstacleShape.ARC -> {
            val cx = px(o.center[0]); val cy = py(o.center[1]); val rad = o.radius.toFloat() * S
            p.stick.color = col; p.stick.strokeWidth = t
            val a0 = o.startDeg + Math.toDegrees(rot); val a1 = a0 + o.widthDeg
            c.drawArc(RectF(cx - rad, cy - rad, cx + rad, cy + rad), -a1.toFloat(), (a1 - a0).toFloat(), false, p.stick)
        }
    }
}

private fun drawArcRange(c: android.graphics.Canvas, cx: Float, cy: Float, rad: Float, minDeg: Float, maxDeg: Float, angle: Float, t: Float, p: Paints) {
    val span = ((maxDeg - minDeg) % 360 + 360) % 360
    p.arcRange.strokeWidth = t * 0.22f
    p.arcRange.pathEffect = DashPathEffect(floatArrayOf(t * 0.5f, t * 0.45f), 0f)
    c.drawArc(RectF(cx - rad, cy - rad, cx + rad, cy + rad), -(minDeg + span), span, false, p.arcRange)
    p.arcRange.pathEffect = null
    // marker: the ring's local 0° (its reference) must stay inside the dashed range
    val k = Math.toRadians(angle.toDouble())
    p.fill.color = 0xFFFFFFFF.toInt(); p.fill.alpha = 230
    c.drawCircle(cx + (rad * cos(k)).toFloat(), cy - (rad * sin(k)).toFloat(), t * 0.26f, p.fill)
    p.fill.color = 0xFF5A3A28.toInt(); c.drawCircle(cx + (rad * cos(k)).toFloat(), cy - (rad * sin(k)).toFloat(), t * 0.14f, p.fill)
}

private fun drawLock(c: android.graphics.Canvas, cx: Float, cy: Float, t: Float, p: Paints, assets: AssetCatalog) {
    val img = assets.sprite("sheet15/node_lock_pulse_f01")
    if (img != null) drawSprite(c, img, cx, cy, t * 2.6f, 255)
    else { p.fill.color = 0xFF5A3A28.toInt(); p.fill.alpha = 255; c.drawRoundRect(RectF(cx - t * 0.6f, cy - t * 0.3f, cx + t * 0.6f, cy + t * 0.7f), t * 0.2f, t * 0.2f, p.fill) }
}

private fun drawLink(c: android.graphics.Canvas, x0: Float, y0: Float, x1: Float, y1: Float, hinge: Boolean, t: Float, p: Paints) {
    p.link.strokeWidth = t * 0.22f
    p.link.pathEffect = DashPathEffect(floatArrayOf(t * 0.35f, t * 0.35f), 0f)
    c.drawLine(x0, y0, x1, y1, p.link)
    p.link.pathEffect = null
    val mx = (x0 + x1) / 2; val my = (y0 + y1) / 2
    p.fill.color = 0xFFFFF4E3.toInt(); p.fill.alpha = 240; c.drawCircle(mx, my, t * 0.7f, p.fill)
    p.link.strokeWidth = t * 0.16f
    // chain: two small linked circles; hinge: opposing arrows
    if (!hinge) { c.drawCircle(mx - t * 0.22f, my, t * 0.26f, p.link); c.drawCircle(mx + t * 0.22f, my, t * 0.26f, p.link) }
    else { c.drawArc(RectF(mx - t * 0.4f, my - t * 0.4f, mx + t * 0.4f, my + t * 0.4f), 200f, 140f, false, p.link); c.drawArc(RectF(mx - t * 0.4f, my - t * 0.4f, mx + t * 0.4f, my + t * 0.4f), 20f, 140f, false, p.link) }
}

private fun drawDirectionArrow(c: android.graphics.Canvas, cx: Float, cy: Float, rad: Float, clockwise: Boolean, now: Long, p: Paints) {
    val phase = ((now % 1200) / 1200f)
    val start = 60f + (if (clockwise) -1 else 1) * phase * 40f
    p.ghost.color = 0xFFFFD88A.toInt(); p.ghost.alpha = 230; p.ghost.strokeWidth = rad * 0.06f
    val sweep = 70f
    val a0 = if (clockwise) start - sweep else start
    c.drawArc(RectF(cx - rad, cy - rad, cx + rad, cy + rad), -(a0 + sweep), sweep, false, p.ghost)
    val tip = if (clockwise) a0 else a0 + sweep
    val k = Math.toRadians(tip.toDouble()); val dir = if (clockwise) -1 else 1
    val tx = cx + (rad * cos(k)).toFloat(); val ty = cy - (rad * sin(k)).toFloat()
    val tang = Math.toRadians(tip + 90.0 * dir)
    val ax = (cos(tang) * rad * 0.12f).toFloat(); val ay = -(sin(tang) * rad * 0.12f).toFloat()
    val nx = (cos(k) * rad * 0.09f).toFloat(); val ny = -(sin(k) * rad * 0.09f).toFloat()
    p.fill.color = 0xFFFFD88A.toInt(); p.fill.alpha = 240
    val path = android.graphics.Path().apply { moveTo(tx + ax, ty + ay); lineTo(tx + nx, ty + ny); lineTo(tx - nx, ty - ny); close() }
    c.drawPath(path, p.fill)
}

private fun drawSprite(c: android.graphics.Canvas, img: ImageBitmap, cx: Float, cy: Float, sizePx: Float, alpha: Int) {
    val b = img.asAndroidBitmap()
    val ratio = b.height.toFloat() / b.width
    val w = sizePx; val h = sizePx * ratio
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha.coerceIn(0, 255) }
    c.drawBitmap(b, null, RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), paint)
}

/** Gentle falling blossom petals behind the rings (deterministic per level seed; cheap). */
private class Petals(val count: Int, seed: Long) {
    private val rnd = java.util.Random(seed)
    private val x = FloatArray(count) { rnd.nextFloat() }
    private val speed = FloatArray(count) { 0.5f + rnd.nextFloat() }
    private val phase = FloatArray(count) { rnd.nextFloat() * 6.28f }
    private val frame = IntArray(count) { rnd.nextInt(6) }
    private val sizeF = FloatArray(count) { 0.035f + rnd.nextFloat() * 0.03f }
    fun draw(scope: DrawScope, now: Long, S: Float, ox: Float, oy: Float, sprites: List<ImageBitmap>) {
        if (sprites.isEmpty()) return
        scope.drawIntoCanvas { c ->
            for (i in 0 until count) {
                val t = (now / 1000f) * speed[i] * 0.06f + phase[i]
                val y = (t % 1.3f) - 0.15f
                val xx = x[i] + sin(t * 1.7f + phase[i]) * 0.06f
                val img = sprites[(frame[i] + (now / 220 % sprites.size).toInt()) % sprites.size]
                drawSprite(c.nativeCanvas, img, ox + xx * S, oy + y * S, S * sizeF[i], 140)
            }
        }
    }
}
