package com.shiostudios.dumplingrings.core.solver

import com.shiostudios.dumplingrings.core.engine.GameState
import com.shiostudios.dumplingrings.core.engine.LegalAction
import com.shiostudios.dumplingrings.core.engine.RuleEngine
import com.shiostudios.dumplingrings.core.engine.StateKey
import java.util.PriorityQueue

data class SolveResult(
    val solvable: Boolean,
    val solution: List<LegalAction.RotateTo>,
    /** true when the search space below the solution length was exhausted => solution length is the proven minimum */
    val optimalProven: Boolean,
    val statesExplored: Int,
    val avgBranching: Double,
    val deadEndRatio: Double,
    val millis: Long,
    val budgetExhausted: Boolean,
    val method: String = "bfs",
) {
    val moves: Int get() = solution.size
}

/**
 * Two-tier deterministic solver over the discrete state space (see docs/SOLVER.md).
 *
 * 1. Breadth-first search with a transposition table. Every move costs 1 (one drag = one move), so when BFS
 *    finishes it returns a provably minimal solution. A state budget guards against large levels.
 * 2. If the budget is exhausted: greedy best-first search (heuristic = active rings + blocked constraints) finds a
 *    solution quickly, then depth-limited DFS with a transposition table tries to shorten it. If a depth is fully
 *    exhausted without a solution the result is proven optimal, otherwise it is an upper bound ("bounded").
 *
 * Moves are signature-pruned by [RuleEngine.legalActions], which keeps the branching factor manageable.
 */
class Solver(private val engine: RuleEngine, private val maxStates: Int = 2_000_000, private val fallbackBudget: Int = 400_000, private val greedyBudget: Int = fallbackBudget / 4) {

    private class Node(val state: GameState, val parent: Node?, val action: LegalAction.RotateTo?, val depth: Int)

    fun solve(start: GameState = engine.initialState()): SolveResult {
        val t0 = System.currentTimeMillis()
        if (start.isWin) return SolveResult(true, emptyList(), true, 1, 0.0, 0.0, 0, false)
        val bfs = if (maxStates > 0) bfs(start, t0) else SolveResult(false, emptyList(), false, 0, 0.0, 0.0, 0, true)
        if (bfs.solvable || !bfs.budgetExhausted) return bfs
        // ---- fallback tier
        val greedy = bestFirst(start, greedyBudget) ?: return bfs.copy(method = "bfs+greedy-failed", millis = System.currentTimeMillis() - t0)
        var best = greedy.first
        var explored = bfs.statesExplored + greedy.second
        var proven = false
        var exhausted = true
        var d = best.size - 1
        while (d >= 1) {
            val r = depthLimited(start, d, fallbackBudget)
            explored += r.nodes
            when {
                r.solution != null -> { best = r.solution; d = best.size - 1 }
                r.complete -> { proven = true; exhausted = false; break }
                else -> break
            }
        }
        return SolveResult(true, best, proven, explored, bfs.avgBranching, bfs.deadEndRatio, System.currentTimeMillis() - t0, exhausted, "bfs+bestfirst+dls")
    }

    private fun bfs(start: GameState, t0: Long): SolveResult {
        val visited = HashSet<StateKey>(1 shl 14)
        val queue = ArrayDeque<Node>()
        queue.add(Node(start, null, null, 0)); visited.add(start.key())
        var explored = 0
        var branchSum = 0L
        var deadEnds = 0
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            explored++
            val actions = engine.legalActions(node.state)
            branchSum += actions.size
            if (actions.isEmpty()) deadEnds++
            for (a in actions) {
                val res = engine.apply(node.state, a)
                if (!res.legal) continue
                val key = res.state.key()
                if (!visited.add(key)) continue
                val child = Node(res.state, node, a, node.depth + 1)
                if (res.state.isWin) {
                    return SolveResult(true, path(child), true, explored, branchSum.toDouble() / explored,
                        deadEnds.toDouble() / explored, System.currentTimeMillis() - t0, false)
                }
                queue.add(child)
            }
            if (visited.size >= maxStates) {
                return SolveResult(false, emptyList(), false, explored, branchSum.toDouble() / explored,
                    deadEnds.toDouble() / explored, System.currentTimeMillis() - t0, true)
            }
        }
        return SolveResult(false, emptyList(), true, explored, if (explored > 0) branchSum.toDouble() / explored else 0.0,
            if (explored > 0) deadEnds.toDouble() / explored else 0.0, System.currentTimeMillis() - t0, false)
    }

    /** heuristic: active rings weighted by how many bodies still block them */
    private fun heuristic(s: GameState): Double {
        var h = 0.0
        for (i in 0 until engine.n) {
            if (s.removed[i]) continue
            h += 1.0 + 0.35 * engine.blockerCount(s, i)
        }
        return h
    }

    private fun bestFirst(start: GameState, budget: Int): Pair<List<LegalAction.RotateTo>, Int>? {
        class Entry(val node: Node, val f: Double)
        val pq = PriorityQueue<Entry>(compareBy<Entry> { it.f }.thenBy { it.node.depth })
        val visited = HashSet<StateKey>()
        pq.add(Entry(Node(start, null, null, 0), heuristic(start)))
        visited.add(start.key())
        var explored = 0
        while (pq.isNotEmpty() && explored < budget) {
            val e = pq.poll()
            explored++
            for (a in engine.legalActions(e.node.state)) {
                val res = engine.apply(e.node.state, a)
                if (!res.legal || !visited.add(res.state.key())) continue
                val child = Node(res.state, e.node, a, e.node.depth + 1)
                if (res.state.isWin) return path(child) to explored
                pq.add(Entry(child, child.depth * 0.6 + heuristic(res.state)))
            }
        }
        return null
    }

    private class DlsResult(val solution: List<LegalAction.RotateTo>?, val complete: Boolean, val nodes: Int)

    /** Depth-limited DFS with transposition table (state -> largest remaining depth already searched without success). */
    private fun depthLimited(start: GameState, limit: Int, budget: Int): DlsResult {
        val table = HashMap<StateKey, Int>()
        var nodes = 0
        var exhausted = false
        val stack = ArrayList<LegalAction.RotateTo>()
        fun rec(s: GameState, remaining: Int): Boolean {
            if (exhausted) return false
            nodes++
            if (nodes > budget) { exhausted = true; return false }
            if (s.activeCount > remaining * engine.n) return false
            val key = s.key()
            val seen = table[key]
            if (seen != null && seen >= remaining) return false
            table[key] = remaining
            if (remaining == 0) return false
            // order: moves that release rings first
            val actions = engine.legalActions(s)
            val scored = actions.map { a -> engine.apply(s, a) to a }.filter { it.first.legal }
                .sortedBy { it.first.state.activeCount }
            for ((res, a) in scored) {
                stack.add(a)
                if (res.state.isWin) return true
                if (remaining > 1 && rec(res.state, remaining - 1)) return true
                stack.removeAt(stack.size - 1)
            }
            return false
        }
        val found = rec(start, limit)
        return DlsResult(if (found) stack.toList() else null, !exhausted && !found, nodes)
    }

    private fun path(n: Node): List<LegalAction.RotateTo> {
        val out = ArrayList<LegalAction.RotateTo>()
        var c: Node? = n
        while (c?.action != null) { out.add(c.action); c = c.parent }
        out.reverse()
        return out
    }

    /** Replay a solution and verify it ends in a win using only legal actions. */
    fun verify(solution: List<LegalAction.RotateTo>, start: GameState = engine.initialState()): Boolean {
        var s = start
        for (a in solution) {
            val r = engine.apply(s, a)
            if (!r.legal) return false
            s = r.state
        }
        return s.isWin
    }

    companion object {
        fun encode(engine: RuleEngine, a: LegalAction.RotateTo): String =
            engine.rings[a.ring].id + ":" + (a.target * engine.stepDeg[a.ring])

        fun decode(engine: RuleEngine, s: String): LegalAction.RotateTo {
            val (id, deg) = s.split(":")
            val ring = engine.ringIndex.getValue(id)
            return LegalAction.RotateTo(ring, (deg.toInt() % 360 + 360) % 360 / engine.stepDeg[ring])
        }
    }
}
