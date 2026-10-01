package com.shiostudios.dumplingrings.core

import com.shiostudios.dumplingrings.core.engine.GameSession
import com.shiostudios.dumplingrings.core.engine.LegalAction
import com.shiostudios.dumplingrings.core.engine.RuleEngine
import com.shiostudios.dumplingrings.core.generator.LevelGenerator
import com.shiostudios.dumplingrings.core.generator.LevelSpec
import com.shiostudios.dumplingrings.core.generator.Rng
import com.shiostudios.dumplingrings.core.hint.HintEngine
import com.shiostudios.dumplingrings.core.solver.Solver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Property-based checks over freshly generated puzzles (bible §27). */
class PropertyTest {
    private val specs = listOf(
        LevelSpec(3, 1, "Eerste stoom", 2, "Basisrotatie", "asymmetrische plaatsing", 1, "two_rings_order"),
        LevelSpec(14, 1, "Bloesempad", 4, "Eenvoudige overlap", "kortere oplossingsroute", 2),
        LevelSpec(23, 1, "Bamboemandjes", 4, "Ontgrendelvolgorde", "asymmetrische plaatsing", 3),
        LevelSpec(36, 1, "De theemeester", 6, "Geneste ringen", "afleidende draairichting", 4),
        LevelSpec(63, 2, "Lampionstraat", 6, "Kleurpoorten", "asymmetrische plaatsing", 5),
        LevelSpec(74, 2, "De kruidentuin", 7, "Beperkte rotatiebogen", "kortere oplossingsroute", 6),
        LevelSpec(82, 2, "De wokmeester", 6, "Rotatieketens", "nieuwe topologie", 6),
        LevelSpec(122, 3, "Wolkentrap", 8, "Scharnierende verbindingen", "nieuwe topologie", 7),
    )

    @Test fun generatedPuzzlesAreSolvableAndReplayable() {
        val gen = LevelGenerator(maxAttempts = 120, solverBudget = 60_000)
        val used = HashSet<String>()
        for (spec in specs) {
            val out = gen.generate(spec, used)
            val engine = RuleEngine(out.level)
            val sol = out.level.canonicalSolution.map { Solver.decode(engine, it) }
            assertTrue(sol.isNotEmpty(), "L${spec.index}: has a solution")
            assertTrue(Solver(engine).verify(sol), "L${spec.index}: canonical solution replays to a win")
            assertEquals(out.level.parMoves, sol.size, "par equals solution length")
            val s0 = engine.initialState()
            assertTrue((0 until engine.n).none { engine.canRelease(s0, it) }, "nothing releasable at start")
            // every action produced by the engine is legal and undo restores the exact state
            val session = GameSession(engine)
            for (a in sol) {
                val before = session.state.copy()
                val legal = engine.legalActions(session.state)
                assertTrue(legal.all { engine.apply(session.state, it).legal }, "all enumerated actions are legal")
                assertTrue(session.dispatch(a).legal)
                session.undo()
                assertEquals(before, session.state, "undo restores the previous state exactly")
                session.dispatch(a)
            }
            assertTrue(session.isWin)
        }
    }

    @Test fun hintsFollowASolvablePath() {
        val gen = LevelGenerator(maxAttempts = 120, solverBudget = 60_000)
        val out = gen.generate(specs[1], HashSet())
        val engine = RuleEngine(out.level)
        val hints = HintEngine(engine, out.level.canonicalSolution)
        val session = GameSession(engine)
        var guard = 0
        while (!session.isWin && guard++ < 40) {
            val h = hints.nextMoves(session.state, 1).firstOrNull() ?: error("hint engine returned nothing on a solvable state")
            assertTrue(session.dispatch(h.action).legal)
        }
        assertTrue(session.isWin, "following hints wins the level")
    }

    @Test fun goldenSteamerKeepsLevelSolvable() {
        val gen = LevelGenerator(maxAttempts = 120, solverBudget = 60_000)
        val out = gen.generate(specs[2], HashSet())
        val engine = RuleEngine(out.level)
        val s0 = engine.initialState()
        for (i in 0 until engine.n) {
            val r = engine.apply(s0, LegalAction.ForceRelease(i))
            assertTrue(r.legal)
            assertTrue(r.state.isWin || Solver(engine).solve(r.state).solvable, "force-releasing ring $i keeps the level solvable")
        }
    }

    @Test fun rngIsDeterministic() {
        val a = Rng(42); val b = Rng(42)
        repeat(50) { assertEquals(a.nextInt(1000), b.nextInt(1000)) }
    }
}
