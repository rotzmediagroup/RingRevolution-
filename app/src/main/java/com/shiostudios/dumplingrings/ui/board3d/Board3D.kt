package com.shiostudios.dumplingrings.ui.board3d

import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.shiostudios.dumplingrings.core.geometry.Geometry
import com.shiostudios.dumplingrings.game.GameController
import com.shiostudios.dumplingrings.ui.board.BoardFit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * 3D puzzle board: a translucent GL TextureView (rings, shadows, props) over the painted scene and under a Compose overlay (decorations, FX, hints,
 * tutorial hand). Touch is handled on the GL view with the shared camera so hit-testing matches the rendered geometry.
 */
@Composable
fun Board3D(
    controller: GameController,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    highContrast: Boolean = false,
    effectQuality: String = "high",
    themeMaterial: String? = null,
    world: Int = 1,
    onTapEmpty: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val level = controller.level
    val fit = remember(level.id) { BoardFit.of(level) }
    val camera = remember(level.id) { BoardCamera(tiltDeg = if (reduceMotion) 18f else 26f) }
    val renderer = remember(level.id, themeMaterial) { Board3DRenderer(ctx, level, fit, camera, themeMaterial) }
    val minHit = with(density) { 24.dp.toPx() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val releaseStart = remember(level.id) { HashMap<Int, Long>() }

    LaunchedEffect(level.id) {
        while (true) {
            withFrameNanos { now = System.currentTimeMillis() }
            controller.pruneAnimations(now)
            renderer.snapshot = buildSnapshot(controller, now, world, reduceMotion, releaseStart)
        }
    }

    Box(modifier) {
        // one GL view per renderer: recreate it whenever the level or theme changes
        androidx.compose.runtime.key(level.id, themeMaterial) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                // Robolectric (JVM screenshot tests) has no hardware renderer for a TextureView: host an empty view there
                if (android.os.Build.FINGERPRINT.contains("robolectric")) android.view.View(c) else
                object : GLTextureView(c) {
                    init { setRenderer(renderer) }
                    private var dragRing: Int? = null
                    private var lastAngle = 0f; private var total = 0f; private var moved = false
                    private var downX = 0f; private var downY = 0f
                    private val tmp = FloatArray(2)

                    override fun onTouchEvent(e: MotionEvent): Boolean {
                        when (e.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                downX = e.x; downY = e.y
                                camera.unproject(e.x, e.y, 0f, tmp)
                                val hit = hitRing(controller, fit, tmp[0], tmp[1], camera, minHit)
                                if (hit == null) { onTapEmpty(); controller.select(null); dragRing = null; return true }
                                dragRing = hit; moved = false; total = 0f
                                lastAngle = angleAround(controller, fit, hit, tmp[0], tmp[1])
                                controller.beginDrag(hit)
                                parent?.requestDisallowInterceptTouchEvent(true)
                                return true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val r = dragRing ?: return true
                                if (!moved && hypot(e.x - downX, e.y - downY) > minHit * 0.3f) moved = true
                                camera.unproject(e.x, e.y, 0f, tmp)
                                val a = angleAround(controller, fit, r, tmp[0], tmp[1])
                                var d = a - lastAngle; if (d > 180) d -= 360; if (d < -180) d += 360
                                lastAngle = a
                                if (moved) { total += d; controller.dragTo(total) }
                                return true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                val r = dragRing ?: return true
                                dragRing = null
                                if (moved) controller.endDrag() else { controller.endDrag(); controller.select(r) }
                                return true
                            }
                        }
                        return true
                    }
                }
            },
            onRelease = { (it as? GLTextureView)?.onPause() },
        )
        }
        BoardOverlay(controller, camera, fit, now, reduceMotion, highContrast, effectQuality, Modifier.fillMaxSize())
    }
}

private fun angleAround(controller: GameController, fit: BoardFit, ring: Int, bx: Float, by: Float): Float {
    val r = controller.level.rings[ring]
    val cx = 0.5f + (r.center[0] - fit.cx).toFloat() * fit.k; val cy = 0.5f + (r.center[1] - fit.cy).toFloat() * fit.k
    return Math.toDegrees(atan2((by - cy).toDouble(), (bx - cx).toDouble())).toFloat()
}

/** Hit-test in board space (after unprojection); tolerance expressed in pixels converted through the camera scale. */
private fun hitRing(controller: GameController, fit: BoardFit, bx: Float, by: Float, camera: BoardCamera, minHitPx: Float): Int? {
    val level = controller.level
    val ppu = camera.pixelsPerUnit().coerceAtLeast(1f)
    val minHit = minHitPx / ppu
    var best: Int? = null; var bestD = Float.MAX_VALUE
    for (i in level.rings.indices) {
        if (controller.isRemoved(i)) continue
        val r = level.rings[i]
        val cx = 0.5f + (r.center[0] - fit.cx).toFloat() * fit.k; val cy = 0.5f + (r.center[1] - fit.cy).toFloat() * fit.k
        val d = hypot(bx - cx, by - cy)
        val band = abs(d - r.radius.toFloat() * fit.k)
        val tol = maxOf(r.thickness.toFloat() * fit.k * 0.8f, minHit)
        if (band <= tol) {
            val ang = Geometry.normDeg(Math.toDegrees(atan2((by - cy).toDouble(), (bx - cx).toDouble())))
            val local = Geometry.normDeg(ang - controller.visualAngles[i])
            val inGap = Geometry.inAnyGap(r.gaps, 0.0, local, -6.0)
            val score = band + (if (inGap) tol * 0.9f else 0f) + (if (controller.selected == i) -tol * 0.3f else 0f)
            if (score < bestD) { bestD = score; best = i }
        }
    }
    return best
}

private fun buildSnapshot(controller: GameController, now: Long, world: Int, reduceMotion: Boolean, releaseStart: HashMap<Int, Long>): SceneSnapshot {
    val level = controller.level
    val state = controller.state
    val overlay = controller.overlay
    val rings = ArrayList<RingState>(level.rings.size)
    val pops = ArrayList<PropAnim>()
    for (i in level.rings.indices) {
        val anim = controller.releases.firstOrNull { it.ring == i }
        val removed = state.removed[i]
        val liftT = when {
            anim != null -> ((now - anim.startMs) / 450f).coerceIn(0f, 1f)
            removed -> 1f
            else -> 0f
        }
        if (anim != null) { pops.add(PropAnim(level.rings[i].center[0].toFloat(), level.rings[i].center[1].toFloat(), ((now - anim.startMs) / 650f).coerceIn(0f, 1f))) }
        val ghost = overlay?.hints?.firstOrNull { it.ring == i }
        rings.add(RingState(controller.visualAngleFor(i, now), removed, if (reduceMotion && anim != null) 1f else liftT, level.rings[i].exitAngleDeg,
            controller.selected == i, controller.isLocked(i), ghost?.targetAngleDeg?.toFloat(), if (ghost != null) (if (overlay.hints.indexOf(ghost) == 0) 0.45f else 0.28f) else 0f))
    }
    val engine = controller.engine
    val drivers = FloatArray(level.obstacles.size) { oi -> val d = engine.obstacleDriver[oi]; if (d >= 0) controller.visualAngleFor(d, now) else 0f }
    val hidden = BooleanArray(level.obstacles.size) { oi -> val d = engine.obstacleDriver[oi]; d >= 0 && state.removed[d] }
    return SceneSnapshot(rings, drivers, hidden, pops, now, world, reduceMotion)
}
