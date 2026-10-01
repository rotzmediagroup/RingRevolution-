package com.shiostudios.dumplingrings.core.generator

import com.shiostudios.dumplingrings.core.content.LevelCodec
import com.shiostudios.dumplingrings.core.engine.RuleEngine
import com.shiostudios.dumplingrings.core.geometry.Geometry
import com.shiostudios.dumplingrings.core.model.*
import com.shiostudios.dumplingrings.core.solver.SolveResult
import com.shiostudios.dumplingrings.core.solver.Solver
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One row of the 150-level roadmap (content/roadmap.json). */
@Serializable
data class LevelSpec(
    val index: Int,
    val world: Int,
    val chapterName: String,
    val rings: Int,
    val focus: String,
    val variation: String,
    val difficulty: Int,
    val tutorialCueId: String? = null,
)

/** Mechanic recipe for a chapter (global chapter number 1..15). */
data class Recipe(
    val pegs: IntRange,
    val locks: Int = 0,
    val stacked: Int = 0,
    val colours: Int = 0,
    val gates: Int = 0,
    val arcs: Int = 0,
    val chains: Int = 0,
    val hinges: Int = 0,
    val doubleGaps: Int = 0,
    val rotors: Int = 0,
    val gapWidth: IntRange = 60..90,
    val tags: List<String>,
)

data class GenerationOutcome(val level: LevelDefinition, val meta: SolveMetadata, val attempts: Int, val reasons: Map<String, Int> = emptyMap())

/**
 * Deterministic level generator: layout -> mechanics -> random start angles -> solver filter.
 * Every candidate is fully solver-validated; only candidates that match the roadmap's difficulty band
 * and are not releasable at start are accepted. The accepted seed is stored in the level file so the
 * exact level can be regenerated.
 */
class LevelGenerator(private val maxAttempts: Int = 400, private val solverBudget: Int = 60_000) {

    private val chapterNames = mapOf(
        1 to "Eerste stoom", 2 to "Bloesempad", 3 to "Bamboemandjes", 4 to "De theemeester", 5 to "Het lentefeest",
        6 to "Avondlicht", 7 to "Lampionstraat", 8 to "De kruidentuin", 9 to "De wokmeester", 10 to "Middernachtfeest",
        11 to "Maanpoort", 12 to "Jadepaviljoen", 13 to "Wolkentrap", 14 to "Sterrenkeuken", 15 to "De laatste stoom",
    )

    fun recipe(chapter: Int, k: Int, n: Int): Recipe = when (chapter) {
        1 -> Recipe(pegs = (if (n <= 2) 1..2 else 1..2), gapWidth = 75..90, tags = listOf("basic_rotation", "release"))
        2 -> Recipe(pegs = 0..1, gapWidth = 60..90, tags = listOf("overlap"))
        3 -> Recipe(pegs = 0..1, locks = 1 + (k - 1) / 4, gapWidth = 60..90, tags = listOf("overlap", "locked_order"))
        4 -> Recipe(pegs = 0..1, stacked = 1 + (k - 1) / 5, gapWidth = 60..90, tags = listOf("overlap", "nested"))
        5 -> Recipe(pegs = 0..1, locks = 1, stacked = 1, gapWidth = 60..80, tags = listOf("overlap", "locked_order", "nested", "combo"))
        6 -> Recipe(pegs = 0..1, locks = (k - 1) / 5, gapWidth = 50..70, tags = listOf("dense_overlap"))
        7 -> Recipe(pegs = 0..0, colours = 2, gates = 1 + (k - 1) / 3, gapWidth = 55..75, tags = listOf("overlap", "colour_gate"))
        8 -> Recipe(pegs = 0..1, arcs = 1 + (k - 1) / 3, gapWidth = 55..75, tags = listOf("overlap", "restricted_arc"))
        9 -> Recipe(pegs = 0..1, chains = 1 + (k - 1) / 5, gapWidth = 55..75, tags = listOf("overlap", "rotation_chain"))
        10 -> Recipe(pegs = 0..1, colours = 2, gates = 1, chains = 1, rotors = (k - 1) / 5, gapWidth = 50..70, tags = listOf("overlap", "colour_gate", "rotation_chain", "rotating_obstacle"))
        11 -> Recipe(pegs = 0..1, locks = 1 + (k - 1) / 4, stacked = 1, gapWidth = 50..70, tags = listOf("overlap", "locked_order", "nested", "complex_sequence"))
        12 -> Recipe(pegs = 0..1, doubleGaps = 2 + (k - 1) / 3, stacked = 1, gapWidth = 40..55, tags = listOf("overlap", "double_gap", "nested"))
        13 -> Recipe(pegs = 0..1, hinges = 1 + (k - 1) / 5, gapWidth = 50..70, tags = listOf("overlap", "hinge_link"))
        14 -> Recipe(pegs = 0..1, locks = 1, colours = 2, gates = 1, arcs = 1, chains = (k % 2), hinges = ((k + 1) % 2), doubleGaps = 1, rotors = (k - 1) / 5, gapWidth = 45..65, tags = listOf("overlap", "locked_order", "colour_gate", "restricted_arc", "rotation_chain", "hinge_link", "double_gap", "rotating_obstacle"))
        else -> Recipe(pegs = 0..1, locks = 1, stacked = 1, colours = 2, gates = 1, arcs = 1, chains = 1, hinges = 1, doubleGaps = 1 + (k - 1) / 5, rotors = 1, gapWidth = 45..60, tags = listOf("overlap", "locked_order", "nested", "colour_gate", "restricted_arc", "rotation_chain", "hinge_link", "double_gap", "rotating_obstacle", "finale"))
    }

    private val materials = listOf("dough_sesame", "dough_matcha", "dough_beet", "dough_ube", "dough_gold", "dough_bamboo")
    private val colourIds = listOf("red", "jade", "ube")

    /** Par band from the roadmap difficulty: optimal moves must fall inside [min, max]. */
    fun parBand(spec: LevelSpec): IntRange {
        val n = spec.rings; val d = spec.difficulty
        val k = (spec.index - 1) % 10 + 1
        var lo = max(if (spec.index <= 2) 1 else 2, (n * 0.6).toInt() + (d - 1) / 3)
        var hi = n + d / 2 + 2
        if (k == 4) { lo = max(1, lo - 1); hi = max(lo, hi - 2) }           // shorter route
        if (k == 8 || k == 9) lo += 1                                          // multi-step / combination
        if (k == 10) { lo += 1; hi += 2 }                                      // chef level
        if (hi < lo) hi = lo
        return lo..hi
    }

    fun generate(spec: LevelSpec, usedTopologies: MutableSet<String>): GenerationOutcome {
        val chapter = (spec.index - 1) / 10 + 1
        val k = (spec.index - 1) % 10 + 1
        val recipe = recipe(chapter, k, spec.rings)
        val band = parBand(spec)
        var best: Triple<LevelDefinition, SolveResult, Double>? = null
        val reasons = LinkedHashMap<String, Int>()
        val verbose = System.getenv("DR_VERBOSE") != null
        fun reject(r: String) { reasons[r] = (reasons[r] ?: 0) + 1; if (verbose) println("  attempt reject: $r") }
        for (attempt in 0 until maxAttempts) {
            val seed = spec.index * 100_000L + attempt
            val rng = Rng(seed)
            val level = try { buildCandidate(spec, recipe, k, rng, seed) } catch (e: IllegalArgumentException) { null }
            if (level == null) { reject("layout"); continue }
            if (!recipeSatisfied(level, recipe)) { reject("recipe"); continue }
            val engine = try { RuleEngine(level) } catch (e: Exception) { reject("engine"); continue }
            val s0 = engine.initialState()
            if ((0 until engine.n).any { engine.canRelease(s0, it) }) { reject("releasable_at_start"); continue }
            // every ring must interact with something, otherwise it is a free ring (bad design)
            if ((0 until engine.n).any { i -> engine.pairPass[i].all { it == null } && engine.obstaclePass[i].all { it == null } }) { reject("free_ring"); continue }
            val topo = engine.topologyHash()
            if (topo in usedTopologies) { reject("duplicate"); continue }
            val ts = System.currentTimeMillis()
            val res = Solver(engine, if (engine.n >= 7) 0 else solverBudget, solverBudget, 15_000).solve(s0)
            if (verbose) println("  attempt $attempt solve ${System.currentTimeMillis() - ts}ms solvable=${res.solvable} moves=${res.moves} method=${res.method} states=${res.statesExplored}")
            if (!res.solvable) { reject("unsolvable_in_budget"); continue }
            if (!Solver(engine).verify(res.solution, s0)) { reject("verify"); continue }
            val score = scoreCandidate(res, band, spec)
            if (res.moves in band) {
                val finished = finish(level, engine, res, spec, chapter, k, recipe, topo)
                usedTopologies.add(topo)
                return GenerationOutcome(finished.first, finished.second, attempt + 1, reasons)
            }
            reject(if (res.moves < band.first) "par_too_low" else "par_too_high")
            if (best == null || score > best.third) best = Triple(level, res, score)
        }
        // Fallback: closest candidate (still solver-validated); flagged in metadata notes for QA.
        val (level, res, _) = best ?: error("no solvable candidate for level ${spec.index}: $reasons")
        val engine = RuleEngine(level)
        val topo = engine.topologyHash()
        usedTopologies.add(topo)
        val finished = finish(level, engine, res, spec, chapter, k, recipe, topo, note = "par outside band ${band} (optimal=${res.moves}); best effort")
        return GenerationOutcome(finished.first, finished.second, maxAttempts, reasons)
    }

    private fun scoreCandidate(res: SolveResult, band: IntRange, spec: LevelSpec): Double {
        val target = (band.first + band.last) / 2.0
        return -abs(res.moves - target)
    }

    private fun recipeSatisfied(l: LevelDefinition, r: Recipe): Boolean {
        if (r.locks > 0 && l.rings.none { it.lockedBy.isNotEmpty() }) return false
        if (r.chains > 0 && l.rings.none { it.linkGroup != null && it.linkRatio == 1 }) return false
        if (r.hinges > 0 && l.rings.none { it.linkGroup != null && it.linkRatio == -1 }) return false
        if (r.gates > 0 && l.obstacles.none { it.colorId != null }) return false
        if (r.arcs > 0 && l.rings.none { it.arc != null }) return false
        if (r.doubleGaps > 0 && l.rings.none { it.gaps.size >= 2 }) return false
        if (r.stacked > 0 && l.weaves.none { it.pattern == WeavePattern.A_OVER || it.pattern == WeavePattern.B_OVER }) return false
        return true
    }

    private fun finish(level: LevelDefinition, engine: RuleEngine, res: SolveResult, spec: LevelSpec, chapter: Int, k: Int, recipe: Recipe, topo: String, note: String? = null): Pair<LevelDefinition, SolveMetadata> {
        val sol = res.solution.map { Solver.encode(engine, it) }
        val par = res.moves
        val good = par + max(2, par / 2)
        val coins = 20 + (chapter - 1) * 3 + (if (k == 10) 30 else 0) + spec.difficulty * 2
        val boosterReward = when (k) { 5 -> mapOf("hint" to 1); 10 -> mapOf("steam_peek" to 1, "hint" to 1); else -> emptyMap() }
        val finished = level.copy(
            name = "${chapterNames[chapter]} ${k}",
            mechanicTags = recipe.tags + (if (k == 10) listOf("chef_level") else emptyList()),
            parMoves = par, goodMoves = good, difficulty = spec.difficulty,
            reward = RewardDef(coins, boosterReward), tutorialCueId = spec.tutorialCueId, isChefLevel = k == 10,
            canonicalSolution = sol, topologyHash = topo,
        )
        val meta = SolveMetadata(finished.id, true, par, res.optimalProven, res.statesExplored, res.avgBranching, res.deadEndRatio, res.millis, sol, topo, listOfNotNull(note))
        return finished to meta
    }

    // ---------------------------------------------------------------- candidate construction

    private fun buildCandidate(spec: LevelSpec, recipe: Recipe, k: Int, rng: Rng, seed: Long): LevelDefinition? {
        val n = spec.rings
        val stepDeg = 30 // 15° is supported by the engine but kept out of the campaign for solver tractability
        val layout = layout(n, k, rng) ?: return null
        val ids = (0 until n).map { "r${it + 1}" }
        val crossPairs = ArrayList<Pair<Int, Int>>()
        for (a in 0 until n) for (b in a + 1 until n) if (Geometry.circlesCross(layout[a], layout[b])) crossPairs.add(a to b)
        if (n >= 2 && crossPairs.isEmpty()) return null

        // colours
        val colourOf = arrayOfNulls<String>(n)
        if (recipe.colours > 0) for (i in 0 until n) colourOf[i] = colourIds[rng.nextInt(recipe.colours)]

        // link groups (chains / hinges)
        val linkGroup = arrayOfNulls<String>(n)
        val linkRatio = IntArray(n) { 1 }
        var gIdx = 0
        fun makeGroup(ratioNeg: Boolean) {
            if (n < 3) return
            val size = if (n >= 7 && rng.nextInt(3) == 0) 3 else 2
            val free = (0 until n).filter { linkGroup[it] == null }
            if (free.size < size) return
            val members = free.shuffled(rng).take(size)
            val name = "g${++gIdx}"
            members.forEachIndexed { j, m -> linkGroup[m] = name; linkRatio[m] = if (ratioNeg && j > 0) -1 else 1 }
        }
        repeat(recipe.chains) { makeGroup(false) }
        repeat(recipe.hinges) { makeGroup(true) }

        // locks (DAG): locked ring is locked by one or two others
        val lockedBy = Array(n) { ArrayList<String>() }
        if (recipe.locks > 0 && n >= 2) {
            val order = (0 until n).toList().shuffled(rng)
            var placed = 0
            for (pos in 1 until n) {
                if (placed >= recipe.locks) break
                val target = order[pos]
                if (linkGroup[target] != null && rng.nextInt(2) == 0) continue
                val lockers = (0 until pos).map { order[it] }.shuffled(rng).take(1 + rng.nextInt(if (n >= 5) 2 else 1))
                lockers.forEach { lockedBy[target].add(ids[it]) }
                placed++
            }
        }

        // weave patterns
        val weaves = ArrayList<WeaveDef>()
        val stackPairs = crossPairs.shuffled(rng).take(recipe.stacked)
        for ((a, b) in crossPairs) {
            val pattern = when {
                (a to b) in stackPairs -> if (rng.nextBoolean()) WeavePattern.A_OVER else WeavePattern.B_OVER
                else -> if (rng.nextBoolean()) WeavePattern.ALT1 else WeavePattern.ALT2
            }
            weaves.add(WeaveDef(ids[a], ids[b], pattern))
        }

        // gaps
        val gapsOf = Array(n) {
            val w = rng.nextIntRange(recipe.gapWidth).toDouble()
            listOf(GapDef(0.0, w))
        }
        if (recipe.doubleGaps > 0) {
            val chosen = (0 until n).toList().shuffled(rng).take(min(n, recipe.doubleGaps))
            for (i in chosen) {
                val w = rng.nextIntRange(recipe.gapWidth).toDouble() * 0.8
                val sep = 120.0 + rng.nextInt(5) * 15.0 // 120..180 apart
                gapsOf[i] = listOf(GapDef(0.0, w), GapDef(sep, w))
            }
        }

        // arcs (restricted rotation) around the initial angle, chosen later -> store relative span
        val arcSpan = IntArray(n) { 0 }
        if (recipe.arcs > 0) {
            val chosen = (0 until n).filter { linkGroup[it] == null }.toList().shuffled(rng).take(recipe.arcs)
            for (i in chosen) arcSpan[i] = 120 + rng.nextInt(4) * 30 // 120..210 degrees of freedom
        }

        // obstacles: pegs, colour gates, rotors
        val obstacles = ArrayList<ObstacleDef>()
        var oIdx = 0
        fun peg(ring: Int, colour: String?, visual: String) {
            val r = layout[ring]
            val ang = Math.toRadians(rng.nextInt(12) * 30.0 + 15.0)
            val inner = r.radius * (0.55 + rng.nextDouble() * 0.15)
            val outer = r.radius * (1.35 + rng.nextDouble() * 0.2)
            val from = listOf(r.center[0] + inner * cos(ang), r.center[1] + inner * sin(ang))
            val to = listOf(r.center[0] + outer * cos(ang), r.center[1] + outer * sin(ang))
            if (to.any { it < 0.02 || it > 0.98 }) return
            val mid = listOf((from[0] + to[0]) / 2, (from[1] + to[1]) / 2)
            obstacles.add(ObstacleDef("o${++oIdx}", ObstacleShape.SEGMENT, mid, from = from, to = to, thickness = 0.028, colorId = colour, visualId = visual))
        }
        val pegCount = rng.nextIntRange(recipe.pegs)
        repeat(pegCount) { peg(rng.nextInt(n), null, "chopstick") }
        if (recipe.gates > 0) repeat(recipe.gates) {
            val ring = rng.nextInt(n)
            // a gate of a DIFFERENT colour than the ring blocks it; same colour is a decoy that lets it pass
            val other = colourIds.take(recipe.colours).filter { it != colourOf[ring] }
            val col = if (other.isNotEmpty() && rng.nextInt(4) != 0) other[rng.nextInt(other.size)] else colourOf[ring]
            peg(ring, col, "lantern_gate")
        }
        if (recipe.rotors > 0 && n >= 3) repeat(recipe.rotors) {
            // a lantern arm pivoting on ring c's centre that rotates with c; it starts OUTSIDE c's own band and
            // sweeps across ring a (which crosses c), so c's angle decides whether a can be lifted.
            val pair = crossPairs[rng.nextInt(crossPairs.size)]
            val (c, a) = if (rng.nextBoolean()) pair else pair.second to pair.first
            val rc = layout[c]; val ra = layout[a]
            val toA = Math.toDegrees(kotlin.math.atan2(ra.center[1] - rc.center[1], ra.center[0] - rc.center[0]))
            val dist = kotlin.math.hypot(ra.center[0] - rc.center[0], ra.center[1] - rc.center[1])
            val start = rc.radius + rc.thickness * 1.2
            val end = min(dist + ra.radius * 0.55, 0.95)
            if (end - start < ra.thickness * 2) return@repeat
            val pivot = listOf(rc.center[0], rc.center[1])
            val from = listOf(rc.center[0] + start, rc.center[1])
            val to = listOf(rc.center[0] + end, rc.center[1])
            obstacles.add(ObstacleDef("o${++oIdx}", ObstacleShape.SEGMENT, pivot, from = from, to = to, thickness = 0.026,
                linkedRingId = ids[c], baseAngleDeg = Geometry.normDegInt(((toA + 15) / 30).toInt() * 30), visualId = "lantern_arm"))
        }

        // initial angles (multiples of step)
        val stepsN = 360 / stepDeg
        val initial = IntArray(n) { rng.nextInt(stepsN) * stepDeg }
        val rings = (0 until n).map { i ->
            val r = layout[i]
            val arc = if (arcSpan[i] > 0) ArcDef(Geometry.normDegInt(initial[i] - arcSpan[i] / 2 / 30 * 30), Geometry.normDegInt(initial[i] + arcSpan[i] / 2 / 30 * 30)) else null
            val exit = Geometry.normDegInt(Math.toDegrees(kotlin.math.atan2(r.center[1] - 0.5, r.center[0] - 0.5)).toInt())
            RingDef(ids[i], r.center, r.radius, r.thickness, gapsOf[i], initial[i], stepDeg, exit, arc, lockedBy[i], linkGroup[i], linkRatio[i],
                materialId = materials[(i + spec.index) % materials.size], colorId = colourOf[i])
        }
        return LevelDefinition(
            id = LevelCodec.levelId(spec.index), index = spec.index, worldId = LevelCodec.worldIdFor(spec.index),
            chapter = LevelCodec.chapterFor(spec.index), chapterLevel = k, seed = seed,
            rings = rings, obstacles = obstacles, weaves = weaves, parMoves = 0, goodMoves = 0, difficulty = spec.difficulty,
        )
    }

    private class Circle(val center: List<Double>, val radius: Double, val thickness: Double)

    private fun Circle.ring() = RingDef("tmp", center, radius, thickness, listOf(GapDef(0.0, 60.0)), 0, 30, 0)

    /** Random layout where every ring crosses at least one other and the crossing graph is connected. */
    private fun layout(n: Int, k: Int, rng: Rng): List<RingDef>? {
        val thickness = when { n <= 3 -> 0.055; n <= 6 -> 0.048; n <= 9 -> 0.040; else -> 0.034 }
        val rMin = when { n <= 2 -> 0.17; n <= 4 -> 0.14; n <= 6 -> 0.12; n <= 9 -> 0.095; else -> 0.08 }
        val rMax = when { n <= 2 -> 0.25; n <= 4 -> 0.22; n <= 6 -> 0.19; n <= 9 -> 0.15; else -> 0.125 }
        val template = when (k) { 2, 7 -> 1; 3 -> 2; 5, 9 -> 3; else -> rng.nextInt(4) }
        for (attempt in 0 until 150) {
            val circles = ArrayList<Circle>()
            val asym = template == 2
            val cx0 = if (asym) 0.5 + (rng.nextDouble() - 0.5) * 0.25 else 0.5
            val cy0 = if (asym) 0.5 + (rng.nextDouble() - 0.5) * 0.25 else 0.5
            var ok = true
            for (i in 0 until n) {
                var placed = false
                for (t in 0 until 400) {
                    val r = rMin + rng.nextDouble() * (rMax - rMin)
                    val c: List<Double> = if (i == 0) listOf(cx0, cy0) else {
                        val base = circles[rng.nextInt(circles.size)]
                        val ang = rng.nextDouble() * Math.PI * 2
                        // distance chosen so the circles cross properly (angle >= ~35 deg)
                        val dmin = abs(base.radius - r) + 0.45 * min(base.radius, r)
                        val dmax = base.radius + r - 0.4 * min(base.radius, r)
                        if (dmax <= dmin) continue
                        val d = dmin + rng.nextDouble() * (dmax - dmin)
                        listOf(base.center[0] + d * cos(ang), base.center[1] + d * sin(ang))
                    }
                    val cand = Circle(c, r, thickness)
                    val margin = (if (n >= 8) 0.04 else 0.06) + thickness
                    if (c[0] - r < margin || c[0] + r > 1 - margin || c[1] - r < margin || c[1] + r > 1 - margin) continue
                    if (circles.any { !acceptablePair(it, cand) }) continue
                    circles.add(cand); placed = true; break
                }
                if (!placed) { ok = false; break }
            }
            if (!ok) continue
            val rings = circles.map { it.ring() }
            if (!connected(rings)) continue
            // template 1 (dense) wants more crossings
            val crossings = (0 until n).sumOf { a -> (a + 1 until n).count { b -> Geometry.circlesCross(rings[a], rings[b]) } }
            if (template == 1 && n >= 4 && crossings < n) continue
            if (template == 3 && n >= 4 && crossings < n - 1 + (n / 3)) continue
            return rings.mapIndexed { i, r -> r.copy(id = "r${i + 1}") }
        }
        return null
    }

    private fun acceptablePair(a: Circle, b: Circle): Boolean {
        val dx = a.center[0] - b.center[0]; val dy = a.center[1] - b.center[1]
        val d = sqrt(dx * dx + dy * dy)
        val t = (a.thickness + b.thickness) / 2
        val sum = a.radius + b.radius; val diff = abs(a.radius - b.radius)
        // separated with a clear margin
        if (d > sum + 1.8 * t) return true
        // nested with a clear margin (no crossing)
        if (d < diff - 1.8 * t) return true
        // crossing: must be a clean crossing
        if (d < sum - 1.3 * t && d > diff + 1.3 * t) {
            val ang = Geometry.crossingAngleDeg(a.ring(), b.ring())
            return ang >= 30.0
        }
        return false
    }

    private fun connected(rings: List<RingDef>): Boolean {
        val n = rings.size
        val seen = BooleanArray(n)
        val stack = ArrayDeque<Int>(); stack.add(0); seen[0] = true
        while (stack.isNotEmpty()) {
            val a = stack.removeLast()
            for (b in 0 until n) if (!seen[b] && Geometry.circlesCross(rings[a], rings[b])) { seen[b] = true; stack.add(b) }
        }
        return seen.all { it }
    }
}

/** Small deterministic PRNG (xorshift64*) so generation is reproducible on every platform. */
class Rng(seed: Long) {
    private var s = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed * 0x9E3779B97F4A7C15uL.toLong() + 1
    fun nextLong(): Long { s = s xor (s ushr 12); s = s xor (s shl 25); s = s xor (s ushr 27); return s * 2685821657736338717L }
    fun nextInt(bound: Int): Int { require(bound > 0); return ((nextLong() ushr 1) % bound).toInt() }
    fun nextIntRange(r: IntRange): Int = r.first + nextInt(r.last - r.first + 1)
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))
    fun nextBoolean(): Boolean = nextLong() < 0
}

fun <T> List<T>.shuffled(rng: Rng): List<T> { val l = toMutableList(); for (i in l.size - 1 downTo 1) { val j = rng.nextInt(i + 1); val t = l[i]; l[i] = l[j]; l[j] = t }; return l }
