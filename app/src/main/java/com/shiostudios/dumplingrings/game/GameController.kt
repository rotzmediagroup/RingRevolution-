package com.shiostudios.dumplingrings.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shiostudios.dumplingrings.AppContainer
import com.shiostudios.dumplingrings.core.engine.GameSession
import com.shiostudios.dumplingrings.core.engine.LegalAction
import com.shiostudios.dumplingrings.core.engine.RuleEngine
import com.shiostudios.dumplingrings.core.geometry.Geometry
import com.shiostudios.dumplingrings.core.hint.Hint
import com.shiostudios.dumplingrings.core.hint.HintEngine
import com.shiostudios.dumplingrings.core.model.LevelDefinition
import com.shiostudios.dumplingrings.core.systems.Booster
import com.shiostudios.dumplingrings.core.systems.BoosterEffects
import com.shiostudios.dumplingrings.core.systems.BoosterLedger
import com.shiostudios.dumplingrings.core.systems.InProgressLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/** A ring release in flight (visual only; the logical state already changed). */
class ReleaseAnim(val ring: Int, val startMs: Long, val wave: Int, val exitDeg: Int, val combo: Int)

data class BoosterOverlay(val hints: List<Hint>, val untilMs: Long, val kind: Booster)

/**
 * Gameplay state for one level: wraps the pure [GameSession] and adds everything presentation needs
 * (continuous visual angles while dragging, release animations, combos, hints, booster transactions, persistence).
 * All logical mutations go through the session's dispatcher so undo/replay/save/solver agree.
 */
class GameController(
    val container: AppContainer,
    val level: LevelDefinition,
    val isDaily: Boolean = false,
    private val scope: CoroutineScope,
) {
    val engine = RuleEngine(level)
    val session = GameSession(engine)
    private val hintEngine = HintEngine(engine, level.canonicalSolution)
    private val boosterEffects = BoosterEffects(engine, hintEngine)
    private val history = ArrayList<LegalAction.RotateTo>()

    var revision by mutableIntStateOf(0); private set
    var selected by mutableStateOf<Int?>(null); private set
    /** continuous visual angles in degrees, one per ring */
    val visualAngles = FloatArray(engine.n) { engine.angleDeg(session.initialStateSnapshot(), it).toFloat() }
    val releases = mutableStateListOf<ReleaseAnim>()
    var combo by mutableIntStateOf(0); private set
    var moves by mutableIntStateOf(0); private set
    var won by mutableStateOf(false); private set
    var overlay by mutableStateOf<BoosterOverlay?>(null); private set
    var hintBusy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var shakeRing by mutableStateOf<Int?>(null); private set
    var shakeUntil = 0L
    var tutorialStep by mutableIntStateOf(0)
    var failedReleaseTaps = 0; private set
    var lastTickStep = 0
    private var dragRing: Int? = null
    private var dragStartAngles: FloatArray? = null
    private var dragDelta = 0f

    init {
        container.save.current.inProgress?.let { ip ->
            if (!isDaily && ip.levelId == level.id && ip.angles.size == engine.n) {
                session.restore(ip.angles.toIntArray(), ip.removed.toBooleanArray(), ip.moves)
                moves = ip.moves
                for (i in 0 until engine.n) visualAngles[i] = engine.angleDeg(session.state, i).toFloat()
            }
        }
    }

    private fun GameSession.initialStateSnapshot() = engine.initialState()

    val state get() = session.state
    fun isRemoved(i: Int) = state.removed[i] && releases.none { it.ring == i }
    fun isLocked(i: Int) = engine.isLocked(state, i)
    fun canRotate(i: Int) = engine.canRotate(state, i)
    fun targetAngle(i: Int) = engine.angleDeg(state, i)

    // ---------------------------------------------------------------- selection & drag

    fun select(i: Int?) {
        if (i != null && state.removed[i]) return
        if (i != selected) { selected = i; if (i != null) { container.audio.sfx("ring_select", 0.8f); container.haptics.tick() } }
        revision++
    }

    fun beginDrag(i: Int) {
        if (state.removed[i]) return
        select(i)
        if (!canRotate(i)) { bump(i); return }
        dragRing = i
        dragStartAngles = visualAngles.copyOf()
        dragDelta = 0f
        lastTickStep = 0
    }

    /** delta in degrees (math convention: positive = counter-clockwise) accumulated since drag start */
    fun dragTo(totalDelta: Float) {
        val i = dragRing ?: return
        val start = dragStartAngles ?: return
        var d = totalDelta
        // arc clamp for every active member of the link group
        val members = engine.activeGroup(state, i)
        val ratioSelf = level.rings[i].linkRatio
        for (m in members) {
            val sign = ratioSelf * level.rings[m].linkRatio
            val arc = level.rings[m].arc ?: continue
            val a0 = start[m]
            val span = Geometry.normDegInt(arc.maxDeg - arc.minDeg).toFloat()
            val rel0 = Geometry.normDeg((a0 - arc.minDeg).toDouble()).toFloat()
            val minD = -rel0; val maxD = span - rel0
            val md = d * sign
            val clamped = md.coerceIn(minD, maxD)
            d = clamped * sign
        }
        dragDelta = d
        for (m in members) {
            val sign = ratioSelf * level.rings[m].linkRatio
            visualAngles[m] = start[m] + d * sign
        }
        val stepNow = (d / engine.stepDeg[i]).roundToInt()
        if (stepNow != lastTickStep) { container.audio.tick(stepNow); container.haptics.tick(); lastTickStep = stepNow }
        revision++
    }

    fun endDrag() {
        val i = dragRing ?: return
        dragRing = null
        val stepDeg = engine.stepDeg[i]
        val steps = (dragDelta / stepDeg).roundToInt()
        val cur = state.angles[i]
        val target = ((cur + steps) % engine.steps[i] + engine.steps[i]) % engine.steps[i]
        if (steps == 0 || target == cur) { snapBack(); return }
        val res = engine.rotatedAngles(state, i, target)
        if (res == null) { snapBack(); bump(i); return }
        dispatch(LegalAction.RotateTo(i, target))
    }

    private fun snapBack() {
        for (i in 0 until engine.n) visualAngles[i] = engine.angleDeg(state, i).toFloat()
        container.audio.sfx("snap", 0.5f)
        revision++
    }

    /** Accessibility buttons: rotate the selected ring by one step. */
    fun rotateSelected(clockwise: Boolean) {
        val i = selected ?: return
        if (!canRotate(i)) { bump(i); return }
        val cur = state.angles[i]
        val target = ((cur + (if (clockwise) -1 else 1)) % engine.steps[i] + engine.steps[i]) % engine.steps[i]
        if (engine.rotatedAngles(state, i, target) == null) { bump(i); return }
        dispatch(LegalAction.RotateTo(i, target))
    }

    private fun bump(i: Int) {
        shakeRing = i; shakeUntil = System.currentTimeMillis() + 350
        container.audio.sfx("invalid", 0.7f); container.haptics.error()
        failedReleaseTaps++
        revision++
    }

    // ---------------------------------------------------------------- dispatch

    fun dispatch(action: LegalAction) {
        val before = state
        val res = session.dispatch(action)
        if (!res.legal) { snapBack(); return }
        if (action is LegalAction.RotateTo) history.add(action)
        moves = session.moves
        container.audio.sfx("snap", 0.6f); container.haptics.snap()
        for (i in 0 until engine.n) if (!before.removed[i]) visualAngles[i] = engine.angleDeg(state, i).toFloat()
        val now = System.currentTimeMillis()
        val released = res.releasedRings
        if (released.isNotEmpty()) {
            combo = if (released.size > 1 || res.releases.size > 1) released.size else 0
            res.releases.forEachIndexed { w, wave -> wave.rings.forEach { r -> releases.add(ReleaseAnim(r, now + w * 140L, w, level.rings[r].exitAngleDeg, released.size)) } }
            container.audio.sfx("ring_release_pop", 1f, 1f + 0.04f * (released.size - 1))
            if (released.size >= 2) container.audio.sfx("combo_${released.size.coerceAtMost(3)}")
            container.haptics.release()
            if (selected != null && state.removed[selected!!]) selected = null
            container.save.update { it.copy(totalReleases = it.totalReleases + released.size, bestCombo = maxOf(it.bestCombo, released.size)) }
        } else combo = 0
        overlay = null
        persist()
        if (state.isWin) won = true
        revision++
    }

    fun undo() {
        if (!session.canUndo) return
        session.undo()
        if (history.isNotEmpty()) history.removeAt(history.size - 1)
        moves = session.moves
        releases.clear()
        for (i in 0 until engine.n) visualAngles[i] = engine.angleDeg(state, i).toFloat()
        container.audio.sfx("undo"); won = false; overlay = null
        persist(); revision++
    }

    fun restart() {
        session.restart(); history.clear(); moves = 0; releases.clear(); won = false; overlay = null; combo = 0
        for (i in 0 until engine.n) visualAngles[i] = engine.angleDeg(state, i).toFloat()
        container.audio.sfx("button_tap"); persist(); revision++
    }

    private fun persist() {
        if (isDaily) return
        container.save.update { it.copy(inProgress = if (state.isWin) null else InProgressLevel(level.id, state.angles.toList(), state.removed.toList(), session.moves)) }
    }

    // ---------------------------------------------------------------- boosters (transactional)

    fun useBooster(b: Booster, onDone: (Boolean) -> Unit) {
        if (hintBusy || won) return onDone(false)
        val save = container.save.current
        if ((save.inventory[b.id] ?: 0) <= 0) return onDone(false)
        hintBusy = true
        val requestId = "bst-" + UUID.randomUUID()
        val hist = history.toList()
        scope.launch {
            val effect: (() -> Boolean)? = withContext(Dispatchers.Default) {
                when (b) {
                    Booster.STEAM_HINT -> boosterEffects.steamHint(session, hist)?.let { h -> { overlay = BoosterOverlay(listOf(h), System.currentTimeMillis() + 6000, b); true } }
                    Booster.STEAM_PEEK -> boosterEffects.steamPeek(session, hist).takeIf { it.isNotEmpty() }?.let { hs -> { overlay = BoosterOverlay(hs, System.currentTimeMillis() + 3000, b); true } }
                    Booster.CHEFS_TWIST -> boosterEffects.chefsTwist(session, hist, selected)?.let { a -> { dispatch(a); true } }
                    Booster.GOLDEN_STEAMER -> selected?.let { s -> boosterEffects.goldenSteamer(session, s)?.let { a -> { dispatch(a); true } } }
                }
            }
            hintBusy = false
            if (effect == null) { message = "none"; onDone(false); return@launch }
            // charge first (idempotent), then apply the solver-validated effect
            val r = BoosterLedger.consume(container.save.current, b, requestId)
            if (!r.applied) { onDone(false); return@launch }
            container.save.update { r.save }
            container.audio.sfx(when (b) { Booster.STEAM_HINT, Booster.STEAM_PEEK -> "booster_hint"; Booster.CHEFS_TWIST -> "booster_twist"; Booster.GOLDEN_STEAMER -> "booster_golden" })
            effect(); revision++
            onDone(true)
        }
    }

    /** Free contextual explanation when the player keeps bumping into a blocked ring (never a booster). */
    fun explain(i: Int): List<String> = hintEngine.explainBlock(state, i)

    fun pruneAnimations(now: Long) {
        if (releases.removeAll { now - it.startMs > 700 }) revision++
        overlay?.let { if (now > it.untilMs) { overlay = null; revision++ } }
        if (shakeRing != null && now > shakeUntil) { shakeRing = null; revision++ }
    }

    fun visualAngleFor(i: Int, now: Long): Float {
        // ease towards the logical angle when not dragging this ring's group
        val target = targetAngle(i).toFloat()
        if (dragRing != null && i in engine.activeGroup(state, dragRing!!)) return visualAngles[i]
        val cur = visualAngles[i]
        var diff = ((target - cur) % 360 + 540) % 360 - 180
        if (abs(diff) < 0.05f) { visualAngles[i] = target; return target }
        visualAngles[i] = cur + diff * 0.35f
        return visualAngles[i]
    }

    val isDragging: Boolean get() = dragRing != null

    /** Ring the tutorial hand points at (first canonical move) during the first levels, until the player moves. */
    val tutorialTarget: Int?
        get() = if (level.index <= 3 && moves == 0 && !won && level.canonicalSolution.isNotEmpty() && !isDragging)
            runCatching { com.shiostudios.dumplingrings.core.solver.Solver.decode(engine, level.canonicalSolution[0]).ring }.getOrNull() else null
}
