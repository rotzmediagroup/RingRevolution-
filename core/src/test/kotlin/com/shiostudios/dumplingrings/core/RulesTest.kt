package com.shiostudios.dumplingrings.core

import com.shiostudios.dumplingrings.core.engine.GameSession
import com.shiostudios.dumplingrings.core.engine.LegalAction
import com.shiostudios.dumplingrings.core.engine.RuleEngine
import com.shiostudios.dumplingrings.core.model.*
import com.shiostudios.dumplingrings.core.solver.Solver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulesTest {
    private fun ring(id: String, cx: Double, cy: Double, r: Double, gapStart: Double, angle: Int, exit: Int, gapW: Double = 60.0) =
        RingDef(id, listOf(cx, cy), r, 0.05, listOf(GapDef(gapStart, gapW)), angle, 30, exit)

    /** One ring whose exit path (east) is crossed by a vertical chopstick: it must rotate its gap to the east. */
    private fun oneRingOneStick(): LevelDefinition {
        // chopstick from (0.6,0.5) (inside the ring) to (0.9,0.5): crosses the ring once, at world angle 0 (east)
        val stick = ObstacleDef("o1", ObstacleShape.SEGMENT, listOf(0.75, 0.5), from = listOf(0.6, 0.5), to = listOf(0.9, 0.5))
        return LevelDefinition(id = "T1", index = 1, worldId = "w", chapter = 1, chapterLevel = 1, seed = 1,
            rings = listOf(ring("r1", 0.5, 0.5, 0.2, 0.0, 180, 0)), obstacles = listOf(stick), parMoves = 1, goodMoves = 2, difficulty = 1)
    }

    @Test fun stickBlocksUntilGapFacesIt() {
        val e = RuleEngine(oneRingOneStick())
        val s0 = e.initialState()
        assertFalse(e.canRelease(s0, 0), "gap at 180 faces away from the stick -> blocked")
        val res = e.apply(s0, LegalAction.RotateTo(0, 330 / 30)) // gap covers 330..30 (centered on 0 = east)
        assertTrue(res.legal)
        assertTrue(res.state.removed[0], "ring released once its gap is under the chopstick")
    }

    @Test fun solverFindsOneMove() {
        val e = RuleEngine(oneRingOneStick())
        val r = Solver(e).solve()
        assertTrue(r.solvable); assertEquals(1, r.moves); assertTrue(r.optimalProven)
        assertTrue(Solver(e).verify(r.solution))
    }

    @Test fun twoCrossingRingsNeedOrder() {
        // r1 at left exits west, r2 at right exits east; they cross each other.
        val l = LevelDefinition(id = "T2", index = 2, worldId = "w", chapter = 1, chapterLevel = 2, seed = 2,
            rings = listOf(ring("r1", 0.4, 0.5, 0.2, 0.0, 90, 180), ring("r2", 0.6, 0.5, 0.2, 0.0, 90, 0)),
            parMoves = 2, goodMoves = 3, difficulty = 1)
        val e = RuleEngine(l)
        val r = Solver(e).solve()
        assertTrue(r.solvable, "two crossing rings solvable")
        assertTrue(r.moves in 1..2)
        assertTrue(Solver(e).verify(r.solution))
    }

    @Test fun undoRestoresExactState() {
        val e = RuleEngine(oneRingOneStick())
        val s = GameSession(e)
        val before = s.state.copy()
        s.dispatch(LegalAction.RotateTo(0, 11))
        assertTrue(s.isWin)
        assertTrue(s.undo())
        assertEquals(before, s.state)
        assertEquals(0, s.moves)
    }

    @Test fun lockedRingCannotRotateOrRelease() {
        val stick = ObstacleDef("o1", ObstacleShape.SEGMENT, listOf(0.75, 0.5), from = listOf(0.6, 0.5), to = listOf(0.9, 0.5))
        val r1 = ring("r1", 0.5, 0.5, 0.2, 0.0, 180, 0).copy(lockedBy = listOf("r2"))
        val r2 = ring("r2", 0.3, 0.3, 0.1, 0.0, 180, 180)
        val stick2 = ObstacleDef("o2", ObstacleShape.SEGMENT, listOf(0.15, 0.3), from = listOf(0.25, 0.3), to = listOf(0.05, 0.3))
        val l = LevelDefinition(id = "T3", index = 3, worldId = "w", chapter = 1, chapterLevel = 3, seed = 3,
            rings = listOf(r1, r2), obstacles = listOf(stick, stick2), parMoves = 2, goodMoves = 3, difficulty = 1)
        val e = RuleEngine(l)
        val s0 = e.initialState()
        assertFalse(e.canRotate(s0, 0))
        val r = Solver(e).solve()
        assertTrue(r.solvable); assertEquals(2, r.moves)
        assertEquals("r2", e.rings[r.solution[0].ring].id)
    }

    @Test fun angleWrapAround() {
        val e = RuleEngine(oneRingOneStick())
        val s0 = e.initialState()
        val a = e.rotatedAngles(s0, 0, 0)!!
        assertEquals(0, a[0])
        val b = e.rotatedAngles(s0, 0, -1)!!
        assertEquals(11, b[0])
    }
}
