package com.shiostudios.dumplingrings.core.hint

import com.shiostudios.dumplingrings.core.engine.GameState
import com.shiostudios.dumplingrings.core.engine.LegalAction
import com.shiostudios.dumplingrings.core.engine.RuleEngine
import com.shiostudios.dumplingrings.core.solver.Solver

/** A suggested move: which ring, which target angle (deg), and the shorter rotation direction. */
data class Hint(val ring: Int, val targetAngleDeg: Int, val clockwise: Boolean, val stepsAway: Int, val action: LegalAction.RotateTo)

/**
 * Produces hints from the *current* state by running the solver (fast on device: levels are ≤ 12 rings).
 * Falls back to the stored canonical solution when the player is still on the canonical path.
 * Shared by Steam Hint (1 step), Steam Peek (2 steps), Chef's Twist (execute 1 step).
 */
class HintEngine(private val engine: RuleEngine, private val canonical: List<String> = emptyList(), private val budget: Int = 150_000) {

    fun nextMoves(state: GameState, count: Int, movesSoFar: List<LegalAction.RotateTo> = emptyList()): List<Hint> {
        if (state.isWin) return emptyList()
        // canonical fast path: if the moves so far are exactly a prefix of the canonical solution
        val canon = canonical.map { Solver.decode(engine, it) }
        if (canon.isNotEmpty() && movesSoFar.size < canon.size && canon.take(movesSoFar.size) == movesSoFar) {
            val rest = canon.drop(movesSoFar.size).take(count)
            if (replayValid(state, rest)) return toHints(state, rest)
        }
        val res = Solver(engine, budget, budget).solve(state)
        if (!res.solvable) return emptyList()
        return toHints(state, res.solution.take(count))
    }

    private fun replayValid(state: GameState, moves: List<LegalAction.RotateTo>): Boolean {
        var s = state
        for (m in moves) { val r = engine.apply(s, m); if (!r.legal) return false; s = r.state }
        return true
    }

    private fun toHints(state: GameState, moves: List<LegalAction.RotateTo>): List<Hint> {
        val out = ArrayList<Hint>()
        var s = state
        for (m in moves) {
            val steps = engine.steps[m.ring]
            val cur = s.angles[m.ring]
            val cw = ((cur - m.target) % steps + steps) % steps   // clockwise = decreasing angle (math convention)
            val ccw = ((m.target - cur) % steps + steps) % steps
            val clockwise = cw <= ccw
            out.add(Hint(m.ring, m.target * engine.stepDeg[m.ring], clockwise, minOf(cw, ccw), m))
            s = engine.apply(s, m).state
        }
        return out
    }

    /** A friendly explanation of why a ring cannot be released right now (free contextual help, never a booster). */
    fun explainBlock(state: GameState, ring: Int): List<String> = engine.blockers(state, ring)
}
