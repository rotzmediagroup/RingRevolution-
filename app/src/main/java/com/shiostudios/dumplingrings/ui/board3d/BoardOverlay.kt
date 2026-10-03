package com.shiostudios.dumplingrings.ui.board3d

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.shiostudios.dumplingrings.DumplingRingsApp
import com.shiostudios.dumplingrings.game.GameController
import com.shiostudios.dumplingrings.ui.board.BoardFit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 2D decorations drawn above the GL board, positioned through the shared camera: lock badges, chain/hinge links,
 * restricted-arc ranges, hint direction arrows, release sparkles/steam, tutorial hand, combo flashes.
 */
@Composable
fun BoardOverlay(controller: GameController, camera: BoardCamera, fit: BoardFit, now: Long, reduceMotion: Boolean, highContrast: Boolean, effectQuality: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val assets = DumplingRingsApp.of(ctx).assets
    val level = controller.level
    val engine = controller.engine
    val sprites = remember {
        mapOf(
            "sparkle" to (1..4).mapNotNull { assets.sprite("sheet14/sparkle_pop_f0$it") },
            "steam" to (1..4).mapNotNull { assets.sprite("sheet14/steam_puff_f0$it") },
            "hand" to (1..5).mapNotNull { assets.sprite("sheet14/tap_hand_f0$it") },
            "lock" to listOfNotNull(assets.sprite("sheet15/node_lock_pulse_f01")),
            "combo" to listOfNotNull(assets.sprite("sheet14/fx_combo_burst")),
        )
    }
    val p = remember { OverlayPaints() }
    Canvas(modifier) {
        controller.revision
        camera.viewportW = size.width.toInt().coerceAtLeast(1); camera.viewportH = size.height.toInt().coerceAtLeast(1); camera.update()
        val ppu = camera.pixelsPerUnit()
        fun bx(x: Double) = 0.5f + (x - fit.cx).toFloat() * fit.k
        fun by(y: Double) = 0.5f + (y - fit.cy).toFloat() * fit.k
        val tmp = FloatArray(2)
        drawIntoCanvas { cv ->
            val c = cv.nativeCanvas
            val state = controller.state
            // ---- links, arcs, locks
            for (i in level.rings.indices) {
                if (controller.isRemoved(i)) continue
                val r = level.rings[i]
                val cx = bx(r.center[0]); val cy = by(r.center[1]); val rad = r.radius.toFloat() * fit.k; val t = r.thickness.toFloat() * fit.k
                r.arc?.let { arc ->
                    val span = ((arc.maxDeg - arc.minDeg) % 360 + 360) % 360
                    p.dash.strokeWidth = t * ppu * 0.25f; p.dash.pathEffect = if (p.dashOk) DashPathEffect(floatArrayOf(t * ppu * 0.5f, t * ppu * 0.45f), 0f) else null
                    val path = android.graphics.Path()
                    var k = 0
                    while (k <= span) { val a = Math.toRadians((arc.minDeg + k).toDouble()); camera.project(cx + (rad + t * 1.0f) * cos(a).toFloat(), cy + (rad + t * 1.0f) * sin(a).toFloat(), 0f, tmp); if (k == 0) path.moveTo(tmp[0], tmp[1]) else path.lineTo(tmp[0], tmp[1]); k += 6 }
                    c.drawPath(path, p.dash)
                    val ka = Math.toRadians(controller.visualAngleFor(i, now).toDouble())
                    camera.project(cx + (rad + t) * cos(ka).toFloat(), cy + (rad + t) * sin(ka).toFloat(), t, tmp)
                    p.fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(tmp[0], tmp[1], t * ppu * 0.3f, p.fill); p.fill.color = 0xFF5A3A28.toInt(); c.drawCircle(tmp[0], tmp[1], t * ppu * 0.16f, p.fill)
                }
                r.linkGroup?.let {
                    for (j in engine.group[i]) if (j > i && !controller.isRemoved(j)) {
                        val rj = level.rings[j]
                        camera.project(cx, cy, t * 2.5f, tmp); val x0 = tmp[0]; val y0 = tmp[1]
                        camera.project(bx(rj.center[0]), by(rj.center[1]), t * 2.5f, tmp); val x1 = tmp[0]; val y1 = tmp[1]
                        p.link.strokeWidth = t * ppu * 0.2f; p.link.pathEffect = if (p.dashOk) DashPathEffect(floatArrayOf(t * ppu * 0.35f, t * ppu * 0.35f), 0f) else null
                        c.drawLine(x0, y0, x1, y1, p.link); p.link.pathEffect = null
                        val mx = (x0 + x1) / 2; val my = (y0 + y1) / 2; val s = t * ppu
                        p.fill.color = 0xF0FFF4E3.toInt(); c.drawCircle(mx, my, s * 0.7f, p.fill)
                        p.link.strokeWidth = s * 0.16f
                        if (rj.linkRatio * r.linkRatio > 0) { c.drawCircle(mx - s * 0.22f, my, s * 0.26f, p.link); c.drawCircle(mx + s * 0.22f, my, s * 0.26f, p.link) }
                        else { c.drawArc(RectF(mx - s * 0.4f, my - s * 0.4f, mx + s * 0.4f, my + s * 0.4f), 200f, 140f, false, p.link); c.drawArc(RectF(mx - s * 0.4f, my - s * 0.4f, mx + s * 0.4f, my + s * 0.4f), 20f, 140f, false, p.link) }
                    }
                }
                if (controller.isLocked(i)) { camera.project(cx, cy, t * 2f, tmp); sprites["lock"]?.firstOrNull()?.let { drawSprite(c, it, tmp[0], tmp[1], t * ppu * 2.6f, 255) } }
            }
            // ---- hint arrows
            controller.overlay?.hints?.forEach { h ->
                val r = level.rings[h.ring]; val cx = bx(r.center[0]); val cy = by(r.center[1]); val rad = r.radius.toFloat() * fit.k + r.thickness.toFloat() * fit.k * 1.8f
                val phase = (now % 1200) / 1200f
                val start = 60f + (if (h.clockwise) -1 else 1) * phase * 40f
                val sweep = 70f; val a0 = if (h.clockwise) start - sweep else start
                val path = android.graphics.Path(); var k = 0
                while (k <= sweep.toInt()) { val a = Math.toRadians((a0 + k).toDouble()); camera.project(cx + rad * cos(a).toFloat(), cy + rad * sin(a).toFloat(), 0.01f, tmp); if (k == 0) path.moveTo(tmp[0], tmp[1]) else path.lineTo(tmp[0], tmp[1]); k += 5 }
                p.arrow.strokeWidth = rad * ppu * 0.06f; c.drawPath(path, p.arrow)
                val tip = if (h.clockwise) a0 else a0 + sweep; val dir = if (h.clockwise) -1 else 1
                val ka = Math.toRadians(tip.toDouble()); camera.project(cx + rad * cos(ka).toFloat(), cy + rad * sin(ka).toFloat(), 0.01f, tmp); val tx = tmp[0]; val ty = tmp[1]
                val tang = Math.toRadians(tip + 90.0 * dir)
                val ax = (cos(tang) * rad * ppu * 0.12f).toFloat(); val ay = -(sin(tang) * rad * ppu * 0.12f).toFloat()
                val nx = (cos(ka) * rad * ppu * 0.09f).toFloat(); val ny = -(sin(ka) * rad * ppu * 0.09f).toFloat()
                p.fill.color = 0xFFFFD88A.toInt()
                c.drawPath(android.graphics.Path().apply { moveTo(tx + ax, ty + ay); lineTo(tx + nx, ty + ny); lineTo(tx - nx, ty - ny); close() }, p.fill)
            }
            // ---- release FX
            for (anim in controller.releases) {
                val r = level.rings[anim.ring]
                val tA = ((now - anim.startMs) / 650f).coerceIn(0f, 1f); if (tA <= 0f) continue
                camera.project(bx(r.center[0]), by(r.center[1]), 0.2f + tA * 0.3f, tmp)
                val sp = sprites["sparkle"].orEmpty(); val st = sprites["steam"].orEmpty()
                val s = r.radius.toFloat() * fit.k * ppu
                if (!reduceMotion && effectQuality != "low") {
                    // light flash: a warm bloom that blooms and fades, plus a thin ring of light expanding outwards
                    val fa = (1f - tA) * (1f - tA)
                    p.fill.shader = android.graphics.RadialGradient(tmp[0], tmp[1], s * (0.9f + tA * 0.9f),
                        intArrayOf(android.graphics.Color.argb((200 * fa).toInt(), 255, 244, 214), android.graphics.Color.argb((90 * fa).toInt(), 255, 196, 110), 0),
                        floatArrayOf(0f, 0.45f, 1f), android.graphics.Shader.TileMode.CLAMP)
                    c.drawCircle(tmp[0], tmp[1], s * (0.9f + tA * 0.9f), p.fill); p.fill.shader = null
                    p.arrow.strokeWidth = ppu * 0.012f * (1f - tA); p.arrow.alpha = (220 * fa).toInt()
                    c.drawCircle(tmp[0], tmp[1], s * (1f + tA * 0.8f), p.arrow); p.arrow.alpha = 255
                }
                if (!reduceMotion && effectQuality != "low" && sp.isNotEmpty()) drawSprite(c, sp[(tA * (sp.size - 1)).toInt()], tmp[0], tmp[1] - s * 0.3f, s * 1.2f * (0.6f + tA), (255 * (1 - tA)).toInt())
                if (!reduceMotion && st.isNotEmpty()) drawSprite(c, st[(tA * (st.size - 1)).toInt()], tmp[0], tmp[1] + s * 0.2f - tA * s * 0.6f, s * (0.8f + tA * 0.6f), (200 * (1 - tA)).toInt())
                if (anim.combo >= 2 && anim.wave == 0 && tA < 0.6f) sprites["combo"]?.firstOrNull()?.let { drawSprite(c, it, tmp[0], tmp[1] - s * 1.2f, s * 2.2f * (0.8f + tA * 0.5f), (255 * (1 - tA / 0.6f)).toInt()) }
            }
            // ---- tutorial hand
            controller.tutorialTarget?.let { ring ->
                val hand = sprites["hand"].orEmpty()
                if (hand.isNotEmpty() && !controller.isRemoved(ring)) {
                    val r = level.rings[ring]; val phase = (now % 1800) / 1800f
                    val ang = Math.toRadians((controller.visualAngles[ring] + 20 + phase * 60).toDouble())
                    camera.project(bx(r.center[0]) + (r.radius * fit.k * cos(ang)).toFloat(), by(r.center[1]) + (r.radius * fit.k * sin(ang)).toFloat(), 0.05f, tmp)
                    drawSprite(c, hand[((phase * 10).toInt()) % hand.size], tmp[0] + ppu * 0.05f, tmp[1] + ppu * 0.06f, ppu * 0.16f, 235)
                }
            }
        }
    }
}

private class OverlayPaints {
    val dashOk = android.os.Build.VERSION.SDK_INT >= 28
    val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xAAFFFFFF.toInt() }
    val link = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xCC5A3A28.toInt() }
    val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xFFFFD88A.toInt() }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
}

internal fun drawSprite(c: android.graphics.Canvas, img: ImageBitmap, cx: Float, cy: Float, sizePx: Float, alpha: Int) {
    val b = img.asAndroidBitmap(); val ratio = b.height.toFloat() / b.width
    val w = sizePx; val h = sizePx * ratio
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha.coerceIn(0, 255) }
    c.drawBitmap(b, null, RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), paint)
}
