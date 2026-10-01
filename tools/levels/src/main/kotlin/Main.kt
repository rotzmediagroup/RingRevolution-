package com.shiostudios.dumplingrings.tools

import com.shiostudios.dumplingrings.core.content.LevelCodec
import com.shiostudios.dumplingrings.core.engine.RuleEngine
import com.shiostudios.dumplingrings.core.generator.LevelGenerator
import com.shiostudios.dumplingrings.core.generator.LevelSpec
import com.shiostudios.dumplingrings.core.model.LevelDefinition
import com.shiostudios.dumplingrings.core.model.SolveMetadata
import com.shiostudios.dumplingrings.core.solver.Solver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Level tooling CLI.
 *   generate [from] [to]   -> content/levels/world_0x/level_NNN.json + content/qa/solve/level_NNN.json
 *   validate               -> re-solves every campaign level, checks all acceptance rules, writes content/qa/validation_report.{json,md}; exit 1 on failure
 *   report                 -> docs/LEVEL_REPORT.md overview
 */
fun main(args: Array<String>) {
    val root = File(System.getProperty("user.dir")).let { if (File(it, "content").exists()) it else it.parentFile.parentFile }
    val cmd = args.firstOrNull() ?: "validate"
    when (cmd) {
        "generate" -> generate(root, args.getOrNull(1)?.toInt() ?: 1, args.getOrNull(2)?.toInt() ?: 150)
        "validate" -> if (!validate(root)) System.exit(1)
        "report" -> report(root)
        else -> { System.err.println("unknown command $cmd"); System.exit(2) }
    }
}

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

fun loadRoadmap(root: File): List<LevelSpec> = json.decodeFromString(File(root, "content/roadmap.json").readText())

fun levelFile(root: File, index: Int) = File(root, "content/" + LevelCodec.resourcePath(index))
fun metaFile(root: File, index: Int) = File(root, "content/qa/solve/level_%03d.json".format(index))

fun generate(root: File, from: Int, to: Int) {
    val specs = loadRoadmap(root)
    val gen = LevelGenerator()
    val used = HashSet<String>()
    // keep topologies of levels outside the range unique too
    for (i in 1..150) if (i !in from..to) levelFile(root, i).takeIf { it.exists() }?.let { used.add(LevelCodec.decode(it.readText()).topologyHash) }
    for (spec in specs) {
        if (spec.index !in from..to) continue
        val t0 = System.currentTimeMillis()
        val out = gen.generate(spec, used)
        levelFile(root, spec.index).apply { parentFile.mkdirs(); writeText(LevelCodec.encode(out.level)) }
        metaFile(root, spec.index).apply { parentFile.mkdirs(); writeText(LevelCodec.encodeMeta(out.meta)) }
        println("L%03d rings=%d par=%d proven=%s band=%s states=%d attempts=%d %dms %s %s".format(spec.index, out.level.rings.size, out.level.parMoves, out.meta.optimalProven,
            gen.parBand(spec), out.meta.statesExplored, out.attempts, System.currentTimeMillis() - t0, out.meta.notes.joinToString(), out.reasons))
        System.out.flush()
    }
}

data class Check(val level: Int, val ok: Boolean, val message: String)

fun validate(root: File): Boolean {
    val specs = loadRoadmap(root)
    val checks = ArrayList<Check>()
    val topologies = HashMap<String, Int>()
    val introduced = HashSet<String>()
    val metas = ArrayList<SolveMetadata>()
    val tutorialOrder = listOf("basic_rotation", "release", "overlap", "locked_order", "nested", "combo", "dense_overlap", "colour_gate", "restricted_arc", "rotation_chain", "rotating_obstacle", "complex_sequence", "double_gap", "hinge_link", "finale", "chef_level")
    for (spec in specs) {
        val f = levelFile(root, spec.index)
        if (!f.exists()) { checks.add(Check(spec.index, false, "missing file ${f.path}")); continue }
        val level: LevelDefinition = try { LevelCodec.decode(f.readText()) } catch (e: Exception) { checks.add(Check(spec.index, false, "unparsable: ${e.message}")); continue }
        fun chk(ok: Boolean, msg: String) = checks.add(Check(spec.index, ok, msg))
        chk(level.id == LevelCodec.levelId(spec.index), "id matches index")
        chk(level.worldId == LevelCodec.worldIdFor(spec.index), "world id matches campaign layout")
        chk(level.chapter == LevelCodec.chapterFor(spec.index), "chapter matches")
        chk(level.rings.size == spec.rings, "ring count ${level.rings.size} == roadmap ${spec.rings}")
        chk(level.isChefLevel == (spec.index % 10 == 0), "chef level flag")
        chk(level.rings.all { r -> r.center.all { it in 0.03..0.97 } && r.center[0] - r.radius >= 0.02 && r.center[0] + r.radius <= 0.98 && r.center[1] - r.radius >= 0.02 && r.center[1] + r.radius <= 0.98 }, "rings inside safe area")
        chk(level.rings.all { it.radius >= 0.08 && it.thickness >= 0.03 }, "ring size touchable (>=44pt at 390pt board)")
        val engine = try { RuleEngine(level) } catch (e: Exception) { chk(false, "engine rejects level: ${e.message}"); continue }
        val s0 = engine.initialState()
        chk((0 until engine.n).none { engine.canRelease(s0, it) }, "no ring releasable at start")
        chk((0 until engine.n).none { i -> engine.pairPass[i].all { it == null } && engine.obstaclePass[i].all { it == null } }, "no free (non-interacting) ring")
        val res = Solver(engine).solve(s0)
        chk(res.solvable, "solvable without boosters (states=${res.statesExplored})")
        if (res.solvable) {
            chk(res.moves == level.parMoves, "parMoves ${level.parMoves} equals solver optimum ${res.moves}")
            chk(Solver(engine).verify(level.canonicalSolution.map { Solver.decode(engine, it) }, s0), "canonical solution replays to a win")
            chk(level.goodMoves > level.parMoves, "2-star threshold above par")
        }
        val prev = topologies.put(level.topologyHash, spec.index)
        chk(prev == null, "unique topology hash" + (prev?.let { " (duplicate of L$it)" } ?: ""))
        chk(engine.topologyHash() == level.topologyHash, "stored topology hash matches geometry")
        // every mechanic must have been introduced before being combined
        val newTags = level.mechanicTags.filter { it !in introduced }
        chk(newTags.size <= 1 || spec.index <= 1 || newTags.all { it in listOf("combo", "finale", "chef_level", "complex_sequence") }, "at most one new mechanic (${newTags})")
        introduced.addAll(level.mechanicTags)
        chk(level.mechanicTags.all { it in tutorialOrder }, "known mechanic tags")
        if (spec.index <= 10) chk(level.tutorialCueId != null, "tutorial cue present")
        chk(level.reward.coins > 0, "reward defined")
        metas.add(SolveMetadata(level.id, res.solvable, res.moves, res.optimalProven, res.statesExplored, res.avgBranching, res.deadEndRatio, res.millis, res.solution.map { Solver.encode(engine, it) }, level.topologyHash))
    }
    val failures = checks.filter { !it.ok }
    val reportDir = File(root, "content/qa").apply { mkdirs() }
    File(reportDir, "validation_report.json").writeText(json.encodeToString(metas))
    val md = StringBuilder()
    md.append("# Level validation report\n\nGenerated: ${java.time.ZonedDateTime.now()}\n\nLevels checked: ${specs.size}; checks: ${checks.size}; failures: ${failures.size}\n\n")
    if (failures.isNotEmpty()) { md.append("## Failures\n\n"); failures.forEach { md.append("- L%03d: %s\n".format(it.level, it.message)) }; md.append("\n") }
    md.append("## Per level\n\n| Level | Rings | Optimal | Proven | States | Branching | Dead-end ratio | ms |\n|---:|---:|---:|---|---:|---:|---:|---:|\n")
    metas.forEach { md.append("| %s | %d | %d | %s | %d | %.2f | %.2f | %d |\n".format(it.levelId, LevelCodec.decode(levelFile(root, it.levelId.drop(1).toInt()).readText()).rings.size, it.optimalMoves, it.optimalProven, it.statesExplored, it.avgBranching, it.deadEndRatio, it.solveMillis)) }
    File(reportDir, "validation_report.md").writeText(md.toString())
    println("checks=${checks.size} failures=${failures.size}")
    failures.forEach { println("FAIL L%03d: %s".format(it.level, it.message)) }
    return failures.isEmpty()
}

fun report(root: File) {
    val specs = loadRoadmap(root)
    val sb = StringBuilder("# Dumpling Rings — Level overview\n\n| Level | World | Chapter | Rings | Mechanics | Par | 2★ | D | Seed | Solver states |\n|---:|---|---|---:|---|---:|---:|---:|---:|---:|\n")
    for (s in specs) {
        val f = levelFile(root, s.index); if (!f.exists()) continue
        val l = LevelCodec.decode(f.readText())
        val m = metaFile(root, s.index).takeIf { it.exists() }?.let { LevelCodec.decodeMeta(it.readText()) }
        sb.append("| ${l.id} | ${l.worldId} | ${l.chapter} ${s.chapterName} | ${l.rings.size} | ${l.mechanicTags.joinToString(", ")} | ${l.parMoves} | ${l.goodMoves} | ${l.difficulty} | ${l.seed} | ${m?.statesExplored ?: "-"} |\n")
    }
    File(root, "docs/LEVEL_REPORT.md").writeText(sb.toString())
    println("wrote docs/LEVEL_REPORT.md")
}
