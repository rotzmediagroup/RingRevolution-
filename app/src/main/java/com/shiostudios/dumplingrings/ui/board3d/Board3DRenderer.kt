package com.shiostudios.dumplingrings.ui.board3d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import com.shiostudios.dumplingrings.core.geometry.Geometry
import com.shiostudios.dumplingrings.core.model.LevelDefinition
import com.shiostudios.dumplingrings.core.model.ObstacleShape
import com.shiostudios.dumplingrings.ui.board.BoardFit
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Immutable per-frame scene snapshot produced by the UI thread (GameController) and consumed on the GL thread. */
class RingState(val angleDeg: Float, val removed: Boolean, val liftT: Float, val exitDeg: Int, val selected: Boolean, val locked: Boolean, val ghostAngleDeg: Float?, val ghostAlpha: Float)
class PropAnim(val x: Float, val y: Float, val t: Float) // dumpling pop
class SceneSnapshot(val rings: List<RingState>, val obstacleDriverAngles: FloatArray, val obstacleHidden: BooleanArray, val pops: List<PropAnim>, val timeMs: Long, val world: Int, val reduceMotion: Boolean)

/**
 * GLES 3.0 renderer: soft contact shadows, woven torus rings (vertex-shader rotation + weave profile), Meshy props
 * (chopsticks, lantern gates, lantern arms, dumpling), glow discs. Lighting per world (warm morning / lantern amber / moonlight).
 */
class Board3DRenderer(private val context: Context, private val level: LevelDefinition, private val fit: BoardFit, val camera: BoardCamera,
                      private val materialOverride: String?) : GLSurfaceView.Renderer {
    @Volatile var snapshot: SceneSnapshot? = null
    private var ringProg = 0; private var shadowProg = 0; private var propProg = 0; private var quadProg = 0; private var glbRingProg = 0
    /** Meshy ring meshes per material id (premium materials); null => procedural dough torus fallback */
    private val ringModels = HashMap<String, GlRingModel?>()
    private val capMeshes = ArrayList<TorusMesh>(); private val capVbos = IntArray(level.rings.size); private val capIbos = IntArray(level.rings.size)
    private val gapBuf = FloatArray(8)
    private class GlRingModel(val vbo: Int, val ibo: Int, val indexType: Int, val indexCount: Int, val base: Int, val mr: Int, val nrm: Int, val metal: Float, val rough: Float, val avgColor: FloatArray, val tubeRatio: Float)
    private val meshes = ArrayList<TorusMesh>()
    private val vbos = IntArray(level.rings.size); private val ibos = IntArray(level.rings.size)
    private val textures = HashMap<String, Int>()
    private val props = HashMap<String, GlProp>()
    private var quadVbo = 0
    private val centers = Array(level.rings.size) { FloatArray(2) }
    private val radii = FloatArray(level.rings.size); private val minors = FloatArray(level.rings.size)
    private val crossings = ArrayList<Crossing>()
    private val bumpBuf = FloatArray(Shaders.MAX_BUMPS * 2)
    private var whiteTex = 0

    private class Crossing(val a: Int, val b: Int, val angA: Float, val angB: Float, val aOver: Boolean)
    private class GlProp(val vbo: Int, val nbo: Int, val ubo: Int, val ibo: Int, val indexType: Int, val indexCount: Int, val tex: Int, val bounds: FloatArray)

    init {
        for ((i, r) in level.rings.withIndex()) {
            centers[i][0] = 0.5f + (r.center[0] - fit.cx).toFloat() * fit.k
            centers[i][1] = 0.5f + (r.center[1] - fit.cy).toFloat() * fit.k
            radii[i] = r.radius.toFloat() * fit.k; minors[i] = r.thickness.toFloat() * fit.k * 0.5f
        }
        val engineWeave = com.shiostudios.dumplingrings.core.engine.RuleEngine(level)
        for (a in level.rings.indices) for (b in a + 1 until level.rings.size) {
            val cr = Geometry.crossings(level.rings[a], level.rings[b]); if (cr.size < 2) continue
            val pat = engineWeave.weave(a, b)
            cr.forEachIndexed { idx, c -> crossings.add(Crossing(a, b, Math.toRadians(c.angleOnA).toFloat(), Math.toRadians(c.angleOnB).toFloat(), Geometry.aOverAt(pat, idx))) }
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST); GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDisable(GLES30.GL_CULL_FACE) // tubes/caps are closed; dome caps at the two ends have opposite winding
        ringProg = program(Shaders.RING_VS, Shaders.RING_FS)
        shadowProg = program(shadowVs(), Shaders.SHADOW_FS)
        propProg = program(Shaders.PROP_VS, Shaders.PROP_FS)
        quadProg = program(Shaders.QUAD_VS, Shaders.QUAD_FS)
        glbRingProg = program(Shaders.GLBRING_VS, Shaders.GLBRING_FS)
        meshes.clear(); capMeshes.clear(); ringModels.clear()
        // rings are ALWAYS the Meshy premium meshes, drawn uniformly scaled (the model's own tube thickness); an unknown/missing asset falls back to the silver Meshy ring
        for (mat in level.rings.map { materialOverride ?: it.materialId }.toSet()) ringModels[mat] = loadRingModel(ringAsset(mat)) ?: loadRingModel("ring_silver")
        for ((i, r) in level.rings.withIndex()) {
            val cm = TorusMesh.buildCaps(r.copy(thickness = visualThickness(i))); capMeshes.add(cm)
            val ids = IntArray(2); GLES30.glGenBuffers(2, ids, 0); capVbos[i] = ids[0]; capIbos[i] = ids[1]
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, capVbos[i]); GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, maxOf(cm.vertexCount, 1) * TorusMesh.FLOATS_PER_VERTEX * 4, cm.vertices, GLES30.GL_STATIC_DRAW)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, capIbos[i]); GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, maxOf(cm.indexCount, 1) * 2, cm.indices, GLES30.GL_STATIC_DRAW)
        }
        for ((i, r) in level.rings.withIndex()) {
            val m = TorusMesh.build(r); meshes.add(m)
            val ids = IntArray(2); GLES30.glGenBuffers(2, ids, 0); vbos[i] = ids[0]; ibos[i] = ids[1]
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[i]); GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, m.vertexCount * TorusMesh.FLOATS_PER_VERTEX * 4, m.vertices, GLES30.GL_STATIC_DRAW)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibos[i]); GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.indexCount * 2, m.indices, GLES30.GL_STATIC_DRAW)
        }
        val q = IntArray(1); GLES30.glGenBuffers(1, q, 0); quadVbo = q[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo); GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 6 * 4 * 4, QuadMesh.vertices, GLES30.GL_STATIC_DRAW)
        whiteTex = texture(Bitmap.createBitmap(intArrayOf(-1, -1, -1, -1), 2, 2, Bitmap.Config.ARGB_8888))
        textures.clear(); props.clear()
        for (name in listOf("sesame_dough", "matcha", "beet_pink", "ube_purple", "gold", "bamboo")) loadTexture("materials/$name.webp")
        for (p in listOf("chopstick", "lantern_gate", "lantern_arm", "dumpling", "bamboo_basket")) loadProp(p)
    }

    /** Campaign materials map onto the premium Meshy ring set; themes map directly. */
    private fun ringAsset(materialId: String): String = when (materialId) {
        "dough_sesame" -> "ring_silver"; "dough_matcha" -> "ring_jade"; "dough_beet" -> "ring_rose_gold"; "dough_ube" -> "ring_onyx"
        "dough_gold" -> "ring_gold"; "dough_bamboo" -> "ring_marble"
        else -> materialId
    }

    /** Visual tube diameter of ring i: the Meshy model's own proportions scaled to the level radius (never the level's abstract thickness). */
    private fun visualThickness(i: Int): Double {
        val r = level.rings[i]; val m = ringModels[materialOverride ?: r.materialId] ?: return r.thickness
        return 2.0 * r.radius * m.tubeRatio
    }

    private fun loadRingModel(name: String): GlRingModel? = try {
        val bytes = context.assets.open("3d/rings/$name.glb").use { it.readBytes() }
        val glb = GlbLoader.load(bytes) ?: return null
        val rm = RingModel.from(glb)
        val ids = IntArray(2); GLES30.glGenBuffers(2, ids, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0]); GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, rm.vertexCount * RingModel.FLOATS_PER_VERTEX * 4, rm.vertices, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[1]); GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, rm.indexCount * (if (rm.indexType == GLES30.GL_UNSIGNED_INT) 4 else 2), rm.indices, GLES30.GL_STATIC_DRAW)
        val base = glb.baseColor?.let { texture(it, repeat = true) } ?: whiteTex
        val mr = glb.metallicRoughness?.let { texture(it, repeat = true) } ?: 0
        val nm = glb.normalMap?.let { texture(it, repeat = true) } ?: 0
        // cap colour = per-channel median of the texels the mesh actually uses (robust against inlays/atlas padding)
        val avg = glb.baseColor?.let { b -> val uvs = glb.uvs ?: return@let null; val cnt = glb.positions.capacity() / 3
            val rs = ArrayList<Float>(); val gs = ArrayList<Float>(); val bs = ArrayList<Float>()
            for (i in 0 until cnt step maxOf(1, cnt / 400)) {
                val x = ((uvs.get(i * 2) % 1f + 1f) % 1f * (b.width - 1)).toInt(); val y = ((uvs.get(i * 2 + 1) % 1f + 1f) % 1f * (b.height - 1)).toInt()
                val px = b.getPixel(x, y); rs.add((px shr 16 and 255) / 255f); gs.add((px shr 8 and 255) / 255f); bs.add((px and 255) / 255f) }
            if (rs.isEmpty()) null else floatArrayOf(rs.sorted()[rs.size / 2], gs.sorted()[gs.size / 2], bs.sorted()[bs.size / 2]) } ?: floatArrayOf(0.8f, 0.7f, 0.5f)
        Log.i("Board3D", "ring model $name: ${rm.vertexCount} verts, thickness ratio ${rm.thicknessRatio}")
        GlRingModel(ids[0], ids[1], rm.indexType, rm.indexCount, base, mr, nm, glb.metallicFactor, glb.roughnessFactor, avg, rm.minorRadius / rm.majorRadius)
    } catch (e: Exception) { Log.i("Board3D", "ring model $name not available: ${e.message}"); null }

    private fun shadowVs() = Shaders.RING_VS
        .replace("vec3 p = vec3(uCenter + radial * uMajor, z0) + n * r;", "vec3 p = vec3(uCenter + radial * uMajor, z0) + n * r * 1.8; p.xy += vec2(0.35, -0.25) * p.z; p.z = 0.0015;")

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        camera.viewportW = width; camera.viewportH = height; camera.update()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val s = snapshot ?: return
        camera.update()
        val lights = lighting(s.world, s.timeMs)
        // --- glow discs under selected rings (additive-ish)
        GLES30.glDepthMask(false)
        GLES30.glUseProgram(quadProg)
        for ((i, rs) in s.rings.withIndex()) if (!rs.removed && rs.selected) {
            val pulse = 0.5f + 0.5f * sin(s.timeMs / 300.0).toFloat()
            drawQuad(centers[i][0], centers[i][1], radii[i] * 1.55f, floatArrayOf(1f, 0.82f, 0.4f, 0.22f + 0.1f * pulse))
        }
        // --- shadows
        GLES30.glUseProgram(shadowProg)
        for ((i, rs) in s.rings.withIndex()) if (!rs.removed || rs.liftT < 1f) drawRing(shadowProg, i, rs, s, lights, shadow = true)
        GLES30.glDepthMask(true)
        // --- rings: premium Meshy meshes only
        for ((i, rs) in s.rings.withIndex()) {
            if (rs.removed && rs.liftT >= 1f) continue
            val model = ringModels[materialOverride ?: level.rings[i].materialId] ?: continue
            drawGlbRing(model, i, rs, s, lights)
            rs.ghostAngleDeg?.let { ga -> drawGlbRing(model, i, RingState(ga, false, 0f, rs.exitDeg, false, false, null, 0f), s, lights, ghost = rs.ghostAlpha) }
        }
        // --- obstacles / props
        GLES30.glUseProgram(propProg)
        for ((oi, o) in level.obstacles.withIndex()) {
            if (s.obstacleHidden.getOrNull(oi) == true) continue
            drawObstacle(o, s.obstacleDriverAngles.getOrNull(oi) ?: 0f, lights)
        }
        for (pop in s.pops) drawPop(pop, lights)
        GLES30.glBindVertexArray(0)
    }

    private class Lights(val key: FloatArray, val keyCol: FloatArray, val fill: FloatArray, val fillCol: FloatArray, val rim: FloatArray, val rimCol: FloatArray, val sky: FloatArray, val ground: FloatArray)
    /** Cinematic three-point rig per world: strong low warm/cool key, dim complementary fill, hot rim from behind, dark ambient; the key sweeps slowly. */
    private fun lighting(world: Int, timeMs: Long): Lights {
        val base = when (world) {
            2 -> Lights(floatArrayOf(-0.50f, 0.25f, 0.65f), floatArrayOf(1.75f, 0.95f, 0.42f), floatArrayOf(0.70f, -0.35f, 0.40f), floatArrayOf(0.14f, 0.17f, 0.45f), floatArrayOf(0.15f, -0.85f, 0.50f), floatArrayOf(1.45f, 0.50f, 0.28f), floatArrayOf(0.20f, 0.15f, 0.32f), floatArrayOf(0.09f, 0.05f, 0.04f))
            3 -> Lights(floatArrayOf(-0.55f, 0.30f, 0.65f), floatArrayOf(1.20f, 1.35f, 1.75f), floatArrayOf(0.70f, -0.30f, 0.40f), floatArrayOf(0.18f, 0.22f, 0.40f), floatArrayOf(0.25f, -0.85f, 0.45f), floatArrayOf(0.90f, 1.10f, 1.45f), floatArrayOf(0.26f, 0.32f, 0.55f), floatArrayOf(0.06f, 0.08f, 0.14f))
            else -> Lights(floatArrayOf(-0.55f, 0.30f, 0.70f), floatArrayOf(1.65f, 1.35f, 1.00f), floatArrayOf(0.70f, -0.25f, 0.40f), floatArrayOf(0.20f, 0.27f, 0.42f), floatArrayOf(0.25f, -0.85f, 0.45f), floatArrayOf(1.30f, 1.00f, 0.80f), floatArrayOf(0.42f, 0.48f, 0.62f), floatArrayOf(0.16f, 0.11f, 0.07f))
        }
        val sw = sin(timeMs / 4200.0).toFloat() * 0.14f
        val k = base.key; val c = cos(sw); val sn = sin(sw)
        return Lights(floatArrayOf(k[0] * c - k[1] * sn, k[0] * sn + k[1] * c, k[2]), base.keyCol, base.fill, base.fillCol, base.rim, base.rimCol, base.sky, base.ground)
    }

    private fun setLights(prog: Int, l: Lights) {
        GLES30.glUniform3fv(u(prog, "uEye"), 1, camera.eye, 0)
        GLES30.glUniform3fv(u(prog, "uKeyDir"), 1, l.key, 0); GLES30.glUniform3fv(u(prog, "uKeyCol"), 1, l.keyCol, 0)
        GLES30.glUniform3fv(u(prog, "uFillDir"), 1, l.fill, 0); GLES30.glUniform3fv(u(prog, "uFillCol"), 1, l.fillCol, 0)
        GLES30.glUniform3fv(u(prog, "uRimDir"), 1, l.rim, 0); GLES30.glUniform3fv(u(prog, "uRimCol"), 1, l.rimCol, 0)
        GLES30.glUniform3fv(u(prog, "uAmbientSky"), 1, l.sky, 0); GLES30.glUniform3fv(u(prog, "uAmbientGround"), 1, l.ground, 0)
    }

    private fun drawRing(prog: Int, i: Int, rs: RingState, s: SceneSnapshot, l: Lights, shadow: Boolean, ghost: Float = 0f) {
        val r = level.rings[i]
        val m = meshes[i]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[i]); GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibos[i])
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, 20, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 1, GLES30.GL_FLOAT, false, 20, 16)
        GLES30.glUniformMatrix4fv(u(prog, "uViewProj"), 1, false, camera.viewProj, 0)
        GLES30.glUniform2f(u(prog, "uCenter"), centers[i][0], centers[i][1])
        GLES30.glUniform1f(u(prog, "uMajor"), radii[i]); GLES30.glUniform1f(u(prog, "uMinor"), minors[i])
        GLES30.glUniform1f(u(prog, "uRot"), Math.toRadians(rs.angleDeg.toDouble()).toFloat())
        // release animation: lift, slide along exit, fade
        val t = rs.liftT.coerceIn(0f, 1f); val e = 1f - (1f - t) * (1f - t)
        val ex = cos(Math.toRadians(rs.exitDeg.toDouble())).toFloat(); val ey = sin(Math.toRadians(rs.exitDeg.toDouble())).toFloat()
        GLES30.glUniform1f(u(prog, "uLift"), if (shadow) 0f else e * 0.35f)
        GLES30.glUniform2f(u(prog, "uSlide"), ex * 0.22f * e, ey * 0.22f * e)
        GLES30.glUniform1f(u(prog, "uScale"), 1f + 0.15f * e)
        // weave bumps: only crossings whose partner is still on the table
        var n = 0
        for (c in crossings) {
            val me = if (c.a == i) 0 else if (c.b == i) 1 else -1
            if (me < 0) continue
            val partner = if (me == 0) c.b else c.a
            if (s.rings[partner].removed) continue
            val over = if (me == 0) c.aOver else !c.aOver
            if (n < Shaders.MAX_BUMPS) { bumpBuf[n * 2] = if (me == 0) c.angA else c.angB; bumpBuf[n * 2 + 1] = (if (over) 1f else -1f) * minors[i] * 1.15f; n++ }
        }
        GLES30.glUniform1i(u(prog, "uBumpCount"), n)
        GLES30.glUniform2fv(u(prog, "uBumps"), Shaders.MAX_BUMPS, bumpBuf, 0)
        GLES30.glUniform1f(u(prog, "uBumpSigma"), (minors[i] * 2.6f / radii[i]).coerceIn(0.12f, 0.5f))
        if (shadow) {
            GLES30.glUniform1f(u(prog, "uAlpha"), 0.32f * (1f - e))
        } else {
            setLights(prog, l)
            val mat = materialOverride ?: r.materialId
            val texName = "materials/" + when (mat) { "dough_sesame" -> "sesame_dough"; "dough_matcha" -> "matcha"; "dough_beet" -> "beet_pink"; "dough_ube" -> "ube_purple"; "dough_gold" -> "gold"; "dough_bamboo" -> "bamboo"; else -> "sesame_dough" } + ".webp"
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[texName] ?: whiteTex); GLES30.glUniform1i(u(prog, "uAlbedo"), 0)
            GLES30.glUniform3f(u(prog, "uTint"), 1f, 1f, 1f)
            GLES30.glUniform1f(u(prog, "uRough"), if (mat == "dough_gold") 0.28f else 0.58f)
            val pulse = 0.5f + 0.5f * sin(s.timeMs / 320.0).toFloat()
            val em = when { ghost > 0f -> floatArrayOf(0.45f, 0.35f, 0.1f); rs.selected -> floatArrayOf(0.22f * pulse + 0.08f, 0.17f * pulse + 0.05f, 0.03f); else -> floatArrayOf(0f, 0f, 0f) }
            GLES30.glUniform3fv(u(prog, "uEmissive"), 1, em, 0)
            GLES30.glUniform1f(u(prog, "uAlpha"), if (ghost > 0f) ghost else 1f - e)
            GLES30.glUniform1f(u(prog, "uDarken"), if (rs.locked) 1f else 0f)
            val stripe = colorOf(r.colorId)
            GLES30.glUniform3fv(u(prog, "uStripe"), 1, stripe, 0); GLES30.glUniform1f(u(prog, "uStripeOn"), if (r.colorId != null) 1f else 0f)
        }
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, m.indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glDisableVertexAttribArray(1)
    }

    /** Common ring-placement uniforms (rotation, release animation, weave bumps) for any ring program. */
    private fun setRingPlacement(prog: Int, i: Int, rs: RingState, s: SceneSnapshot, shadow: Boolean): Float {
        GLES30.glUniformMatrix4fv(u(prog, "uViewProj"), 1, false, camera.viewProj, 0)
        GLES30.glUniform2f(u(prog, "uCenter"), centers[i][0], centers[i][1])
        GLES30.glUniform1f(u(prog, "uMajor"), radii[i]); GLES30.glUniform1f(u(prog, "uMinor"), minors[i])
        GLES30.glUniform1f(u(prog, "uRot"), Math.toRadians(rs.angleDeg.toDouble()).toFloat())
        val t = rs.liftT.coerceIn(0f, 1f); val e = 1f - (1f - t) * (1f - t)
        val ex = cos(Math.toRadians(rs.exitDeg.toDouble())).toFloat(); val ey = sin(Math.toRadians(rs.exitDeg.toDouble())).toFloat()
        GLES30.glUniform1f(u(prog, "uLift"), if (shadow) 0f else e * 0.35f)
        GLES30.glUniform2f(u(prog, "uSlide"), ex * 0.22f * e, ey * 0.22f * e)
        GLES30.glUniform1f(u(prog, "uScale"), 1f + 0.15f * e)
        var n = 0
        for (c in crossings) {
            val me = if (c.a == i) 0 else if (c.b == i) 1 else -1
            if (me < 0) continue
            val partner = if (me == 0) c.b else c.a
            if (s.rings[partner].removed) continue
            val over = if (me == 0) c.aOver else !c.aOver
            if (n < Shaders.MAX_BUMPS) { bumpBuf[n * 2] = if (me == 0) c.angA else c.angB; bumpBuf[n * 2 + 1] = (if (over) 1f else -1f) * minors[i] * 1.15f; n++ }
        }
        GLES30.glUniform1i(u(prog, "uBumpCount"), n)
        GLES30.glUniform2fv(u(prog, "uBumps"), Shaders.MAX_BUMPS, bumpBuf, 0)
        GLES30.glUniform1f(u(prog, "uBumpSigma"), (minors[i] * 2.6f / radii[i]).coerceIn(0.12f, 0.5f))
        return e
    }

    private fun drawGlbRing(model: GlRingModel, i: Int, rs: RingState, s: SceneSnapshot, l: Lights, ghost: Float = 0f) {
        val r = level.rings[i]
        val prog = glbRingProg
        GLES30.glUseProgram(prog)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, model.vbo); GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, model.ibo)
        val stride = RingModel.FLOATS_PER_VERTEX * 4
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, stride, 20)
        val e = setRingPlacement(prog, i, rs, s, false)
        val minorVis = radii[i] * model.tubeRatio; GLES30.glUniform1f(u(prog, "uMinor"), minorVis)   // uniform scale of the Meshy model
        setLights(prog, l)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, model.base); GLES30.glUniform1i(u(prog, "uAlbedo"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (model.mr != 0) model.mr else whiteTex); GLES30.glUniform1i(u(prog, "uMetalRough"), 1)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (model.nrm != 0) model.nrm else whiteTex); GLES30.glUniform1i(u(prog, "uNormalMap"), 2)
        GLES30.glUniform1f(u(prog, "uHasMR"), if (model.mr != 0) 1f else 0f); GLES30.glUniform1f(u(prog, "uHasNormal"), if (model.nrm != 0) 1f else 0f)
        GLES30.glUniform1f(u(prog, "uMetalFactor"), model.metal); GLES30.glUniform1f(u(prog, "uRoughFactor"), model.rough)
        val pulse = 0.5f + 0.5f * sin(s.timeMs / 320.0).toFloat()
        val em = when { ghost > 0f -> floatArrayOf(0.45f, 0.35f, 0.1f); rs.selected -> floatArrayOf(0.22f * pulse + 0.08f, 0.17f * pulse + 0.05f, 0.03f); else -> floatArrayOf(0f, 0f, 0f) }
        GLES30.glUniform3fv(u(prog, "uEmissive"), 1, em, 0)
        GLES30.glUniform1f(u(prog, "uAlpha"), if (ghost > 0f) ghost else 1f - e)
        GLES30.glUniform1f(u(prog, "uDarken"), if (rs.locked) 1f else 0f)
        val stripe = colorOf(r.colorId); GLES30.glUniform3fv(u(prog, "uStripe"), 1, stripe, 0); GLES30.glUniform1f(u(prog, "uStripeOn"), if (r.colorId != null) 1f else 0f)
        // gaps in local radians, widened by the dome length so the caps own the edge
        val capRad = model.tubeRatio * 0.95f
        var g = 0
        for (gap in r.gaps.take(4)) { gapBuf[g * 2] = Math.toRadians(gap.startDeg).toFloat() + capRad; gapBuf[g * 2 + 1] = (Math.toRadians(gap.widthDeg).toFloat() - 2 * capRad).coerceAtLeast(0f); g++ }
        GLES30.glUniform1i(u(prog, "uGapCount"), g); GLES30.glUniform2fv(u(prog, "uGaps"), 4, gapBuf, 0); GLES30.glUniform1f(u(prog, "uCapRad"), capRad)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, model.indexCount, model.indexType, 0)
        GLES30.glDisableVertexAttribArray(1); GLES30.glDisableVertexAttribArray(2)
        // domed caps at the gap edges, shaded with the model's average colour via the dough ring program
        GLES30.glUseProgram(ringProg)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, capVbos[i]); GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, capIbos[i])
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, 20, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 1, GLES30.GL_FLOAT, false, 20, 16)
        setRingPlacement(ringProg, i, rs, s, false); GLES30.glUniform1f(u(ringProg, "uMinor"), minorVis); setLights(ringProg, l)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, whiteTex); GLES30.glUniform1i(u(ringProg, "uAlbedo"), 0)
        GLES30.glUniform3fv(u(ringProg, "uTint"), 1, model.avgColor, 0); GLES30.glUniform1f(u(ringProg, "uRough"), 0.72f)   // matte cut end in the model's own colour
        GLES30.glUniform3fv(u(ringProg, "uEmissive"), 1, em, 0); GLES30.glUniform1f(u(ringProg, "uAlpha"), if (ghost > 0f) ghost else 1f - e); GLES30.glUniform1f(u(ringProg, "uDarken"), if (rs.locked) 1f else 0f)
        GLES30.glUniform1f(u(ringProg, "uStripeOn"), 0f)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, capMeshes[i].indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glDisableVertexAttribArray(1)
    }

    private fun colorOf(id: String?): FloatArray = when (id) { "red" -> floatArrayOf(0.88f, 0.32f, 0.28f); "jade" -> floatArrayOf(0.3f, 0.72f, 0.56f); "ube" -> floatArrayOf(0.6f, 0.45f, 0.82f); else -> floatArrayOf(0.4f, 0.25f, 0.15f) }

    private fun drawQuad(cx: Float, cy: Float, size: Float, color: FloatArray) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glUniformMatrix4fv(u(quadProg, "uViewProj"), 1, false, camera.viewProj, 0)
        GLES30.glUniform3f(u(quadProg, "uCenter"), cx, cy, 0.001f); GLES30.glUniform2f(u(quadProg, "uSize"), size, size)
        GLES30.glUniform4fv(u(quadProg, "uColor"), 1, color, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
    }

    // ------------------------------------------------------------- props

    private fun drawObstacle(o: com.shiostudios.dumplingrings.core.model.ObstacleDef, driverAngle: Float, l: Lights) {
        if (o.shape != ObstacleShape.SEGMENT) return
        val rot = Math.toRadians(o.baseAngleDeg + driverAngle.toDouble())
        fun rp(pt: List<Double>): FloatArray {
            val x = pt[0] - o.center[0]; val y = pt[1] - o.center[1]
            val wx = o.center[0] + x * cos(rot) - y * sin(rot); val wy = o.center[1] + x * sin(rot) + y * cos(rot)
            return floatArrayOf(0.5f + (wx - fit.cx).toFloat() * fit.k, 0.5f + (wy - fit.cy).toFloat() * fit.k)
        }
        val a = rp(o.from); val b = rp(o.to)
        val len = hypot(b[0] - a[0], b[1] - a[1]); val ang = atan2(b[1] - a[1], b[0] - a[0])
        val thick = o.thickness.toFloat() * fit.k
        val z = (level.rings.maxOfOrNull { it.thickness }?.toFloat() ?: 0.04f) * fit.k * 2.35f
        val model = FloatArray(16); Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, a[0], a[1], z); Matrix.rotateM(model, 0, Math.toDegrees(ang.toDouble()).toFloat(), 0f, 0f, 1f)
        val propName = when (o.visualId) { "lantern_gate" -> "lantern_gate"; "lantern_arm" -> "lantern_arm"; else -> "chopstick" }
        val tint = if (o.colorId != null) colorOf(o.colorId) else floatArrayOf(0.62f, 0.4f, 0.22f)
        val prop = props[propName]
        if (prop != null) {
            // model bounds -> fit length along X, thickness along Y/Z
            val bw = prop.bounds[3] - prop.bounds[0]; val bh = maxOf(prop.bounds[4] - prop.bounds[1], prop.bounds[5] - prop.bounds[2])
            val sx = len / maxOf(bw, 1e-4f); val sy = thick * 1.3f / maxOf(bh, 1e-4f)
            val m2 = model.copyOf(); Matrix.translateM(m2, 0, -prop.bounds[0] * sx, 0f, -prop.bounds[2] * sy)
            Matrix.scaleM(m2, 0, sx, sy, sy)
            drawProp(prop, m2, if (o.colorId != null) tint else floatArrayOf(1f, 1f, 1f), 0.45f, l, if (o.visualId == "lantern_gate") floatArrayOf(0.25f * tint[0], 0.2f * tint[1], 0.1f) else floatArrayOf(0f, 0f, 0f))
        } else {
            drawCapsule(model, len, thick * 0.5f, tint, l)
            if (o.visualId != "chopstick") {
                val m2 = FloatArray(16); Matrix.setIdentityM(m2, 0); Matrix.translateM(m2, 0, b[0], b[1], z + thick * 0.4f)
                drawSphere(m2, thick * 1.1f, if (o.visualId == "lantern_arm") floatArrayOf(1f, 0.7f, 0.3f) else tint, l, floatArrayOf(0.35f, 0.22f, 0.08f))
            }
        }
    }

    private fun drawPop(p: PropAnim, l: Lights) {
        val t = p.t.coerceIn(0f, 1f); val e = 1f - (1f - t) * (1f - t)
        val cx = 0.5f + (p.x - fit.cx).toFloat() * fit.k; val cy = 0.5f + (p.y - fit.cy).toFloat() * fit.k
        val model = FloatArray(16); Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, cx, cy, 0.02f + e * 0.3f)
        val sc = 0.09f * fit.k * (0.4f + 0.8f * sin(t * PI).toFloat())
        val prop = props["dumpling"]
        if (prop != null) {
            val bw = maxOf(prop.bounds[3] - prop.bounds[0], prop.bounds[4] - prop.bounds[1], prop.bounds[5] - prop.bounds[2])
            Matrix.rotateM(model, 0, 90f, 1f, 0f, 0f); Matrix.scaleM(model, 0, sc / bw, sc / bw, sc / bw)
            Matrix.translateM(model, 0, -(prop.bounds[0] + prop.bounds[3]) / 2, -prop.bounds[1], -(prop.bounds[2] + prop.bounds[5]) / 2)
            drawProp(prop, model, floatArrayOf(1f, 1f, 1f), 0.6f, l, floatArrayOf(0f, 0f, 0f), 1f - t * t)
        } else drawSphere(model, sc * 0.5f, floatArrayOf(0.98f, 0.9f, 0.78f), l, floatArrayOf(0f, 0f, 0f), 1f - t * t)
    }

    private fun drawProp(p: GlProp, model: FloatArray, tint: FloatArray, rough: Float, l: Lights, emissive: FloatArray, alpha: Float = 1f) {
        GLES30.glUseProgram(propProg)
        GLES30.glUniformMatrix4fv(u(propProg, "uViewProj"), 1, false, camera.viewProj, 0)
        GLES30.glUniformMatrix4fv(u(propProg, "uModel"), 1, false, model, 0)
        val nm = normalMatrix(model); GLES30.glUniformMatrix3fv(u(propProg, "uNormalM"), 1, false, nm, 0)
        setLights(propProg, l)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (p.tex != 0) p.tex else whiteTex); GLES30.glUniform1i(u(propProg, "uAlbedo"), 0)
        GLES30.glUniform1f(u(propProg, "uHasTex"), if (p.tex != 0) 1f else 0f)
        GLES30.glUniform3fv(u(propProg, "uTint"), 1, tint, 0); GLES30.glUniform1f(u(propProg, "uRough"), rough)
        GLES30.glUniform3fv(u(propProg, "uEmissive"), 1, emissive, 0); GLES30.glUniform1f(u(propProg, "uAlpha"), alpha)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, p.vbo); GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, p.nbo); GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, p.ubo); GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, p.ibo)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, p.indexCount, p.indexType, 0)
        GLES30.glDisableVertexAttribArray(1); GLES30.glDisableVertexAttribArray(2)
    }

    // procedural fallbacks (capsule along +X of length len, sphere) built lazily
    private var capsule: GlProp? = null; private var sphere: GlProp? = null
    private fun drawCapsule(model: FloatArray, len: Float, radius: Float, tint: FloatArray, l: Lights) {
        val c = capsule ?: uploadProp(ProceduralMeshes.capsule(1f, 1f, 14, 10), 0).also { capsule = it }
        val m = model.copyOf(); Matrix.scaleM(m, 0, len, radius, radius)
        drawProp(c, m, tint, 0.4f, l, floatArrayOf(0f, 0f, 0f))
    }
    private fun drawSphere(model: FloatArray, radius: Float, tint: FloatArray, l: Lights, emissive: FloatArray, alpha: Float = 1f) {
        val sph = sphere ?: uploadProp(ProceduralMeshes.sphere(1f, 18, 12), 0).also { sphere = it }
        val m = model.copyOf(); Matrix.scaleM(m, 0, radius, radius, radius)
        drawProp(sph, m, tint, 0.5f, l, emissive, alpha)
    }

    private fun loadProp(name: String) {
        try {
            val bytes = context.assets.open("3d/$name.glb").use { it.readBytes() }
            val model = GlbLoader.load(bytes) ?: return
            val tex = model.baseColor?.let { texture(it) } ?: 0
            props[name] = uploadProp(ProceduralMeshes.Mesh(model.positions, model.normals ?: model.positions, model.uvs ?: model.positions, model.indices, model.indexType, model.indexCount, model.bounds), tex)
        } catch (e: Exception) { Log.i("Board3D", "prop $name not available: ${e.message}") }
    }

    private fun uploadProp(m: ProceduralMeshes.Mesh, tex: Int): GlProp {
        val ids = IntArray(4); GLES30.glGenBuffers(4, ids, 0)
        fun up(target: Int, id: Int, buf: java.nio.Buffer, bytes: Int) { GLES30.glBindBuffer(target, id); GLES30.glBufferData(target, bytes, buf, GLES30.GL_STATIC_DRAW) }
        up(GLES30.GL_ARRAY_BUFFER, ids[0], m.positions, m.positions.capacity() * 4)
        up(GLES30.GL_ARRAY_BUFFER, ids[1], m.normals, m.normals.capacity() * 4)
        up(GLES30.GL_ARRAY_BUFFER, ids[2], m.uvs, m.uvs.capacity() * 4)
        up(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[3], m.indices, m.indexCount * (if (m.indexType == GLES30.GL_UNSIGNED_INT) 4 else 2))
        return GlProp(ids[0], ids[1], ids[2], ids[3], m.indexType, m.indexCount, tex, m.bounds)
    }

    // ------------------------------------------------------------- GL helpers

    private fun loadTexture(path: String) {
        try { context.assets.open(path).use { s -> BitmapFactory.decodeStream(s)?.let { textures[path] = texture(it, repeat = true) } } } catch (e: Exception) { Log.w("Board3D", "texture $path: ${e.message}") }
    }

    private fun texture(bmp: Bitmap, repeat: Boolean = false): Int {
        val ids = IntArray(1); GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        val wrap = if (repeat) GLES30.GL_REPEAT else GLES30.GL_CLAMP_TO_EDGE
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap); GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
        return ids[0]
    }

    private val uniformCache = HashMap<Long, Int>()
    private fun u(prog: Int, name: String): Int = uniformCache.getOrPut((prog.toLong() shl 32) or name.hashCode().toLong().and(0xffffffffL)) { GLES30.glGetUniformLocation(prog, name) }

    private fun program(vs: String, fs: String): Int {
        fun shader(type: Int, src: String): Int {
            val id = GLES30.glCreateShader(type); GLES30.glShaderSource(id, src); GLES30.glCompileShader(id)
            val ok = IntArray(1); GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) { Log.e("Board3D", "shader: " + GLES30.glGetShaderInfoLog(id)); }
            return id
        }
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, shader(GLES30.GL_VERTEX_SHADER, vs)); GLES30.glAttachShader(p, shader(GLES30.GL_FRAGMENT_SHADER, fs))
        GLES30.glLinkProgram(p)
        val ok = IntArray(1); GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) Log.e("Board3D", "link: " + GLES30.glGetProgramInfoLog(p))
        return p
    }

    private fun normalMatrix(model: FloatArray): FloatArray {
        val inv = FloatArray(16); Matrix.invertM(inv, 0, model, 0)
        val t = FloatArray(16); Matrix.transposeM(t, 0, inv, 0)
        return floatArrayOf(t[0], t[1], t[2], t[4], t[5], t[6], t[8], t[9], t[10])
    }
}

/** Small procedural meshes used when a Meshy prop is unavailable. */
object ProceduralMeshes {
    class Mesh(val positions: FloatBuffer, val normals: FloatBuffer, val uvs: FloatBuffer, val indices: java.nio.Buffer, val indexType: Int, val indexCount: Int, val bounds: FloatArray)

    private fun fb(list: List<Float>): FloatBuffer = ByteBuffer.allocateDirect(list.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { list.forEach { put(it) }; position(0) }
    private fun sb(list: List<Short>) = ByteBuffer.allocateDirect(list.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { list.forEach { put(it) }; position(0) }

    /** Capsule from x=0 to x=len (radius r) with hemispherical caps, axis +X. */
    fun capsule(len: Float, r: Float, sides: Int, capSegs: Int): Mesh {
        val pos = ArrayList<Float>(); val nrm = ArrayList<Float>(); val uv = ArrayList<Float>(); val idx = ArrayList<Short>()
        val rings = ArrayList<Pair<Float, Float>>() // (x, radius scale)
        for (i in 0..capSegs) { val a = PI / 2 * i / capSegs; rings.add((-cos(a) * r).toFloat() to sin(a).toFloat()) }
        for (i in 0..capSegs) { val a = PI / 2 * i / capSegs; rings.add((len + sin(a) * r).toFloat() to cos(a).toFloat()) }
        for ((ri, ring) in rings.withIndex()) {
            val (x, s) = ring
            for (k in 0..sides) {
                val t = 2 * PI * k / sides
                val ny = cos(t).toFloat() * s; val nz = sin(t).toFloat() * s
                val nx = if (x < 0) -(1 - s * s).coerceAtLeast(0f).let { kotlin.math.sqrt(it) } else if (x > len) kotlin.math.sqrt((1 - s * s).coerceAtLeast(0f)) else 0f
                pos.add(x); pos.add(ny * r); pos.add(nz * r); nrm.add(nx); nrm.add(cos(t).toFloat() * s + 0f); nrm.add(sin(t).toFloat() * s); uv.add(x / len); uv.add(k / sides.toFloat())
            }
        }
        val stride = sides + 1
        for (i in 0 until rings.size - 1) for (k in 0 until sides) { val a = i * stride + k; val b = a + stride; idx.add(a.toShort()); idx.add((a + 1).toShort()); idx.add(b.toShort()); idx.add((a + 1).toShort()); idx.add((b + 1).toShort()); idx.add(b.toShort()) }
        return Mesh(fb(pos), fb(nrm), fb(uv), sb(idx), GLES30.GL_UNSIGNED_SHORT, idx.size, floatArrayOf(-r, -r, -r, len + r, r, r))
    }

    fun sphere(r: Float, lon: Int, lat: Int): Mesh {
        val pos = ArrayList<Float>(); val nrm = ArrayList<Float>(); val uv = ArrayList<Float>(); val idx = ArrayList<Short>()
        for (i in 0..lat) { val ph = PI * i / lat; for (j in 0..lon) { val th = 2 * PI * j / lon
            val x = (sin(ph) * cos(th)).toFloat(); val y = (sin(ph) * sin(th)).toFloat(); val z = cos(ph).toFloat()
            pos.add(x * r); pos.add(y * r); pos.add(z * r); nrm.add(x); nrm.add(y); nrm.add(z); uv.add(j / lon.toFloat()); uv.add(i / lat.toFloat()) } }
        val stride = lon + 1
        for (i in 0 until lat) for (j in 0 until lon) { val a = i * stride + j; val b = a + stride; idx.add(a.toShort()); idx.add(b.toShort()); idx.add((a + 1).toShort()); idx.add((a + 1).toShort()); idx.add(b.toShort()); idx.add((b + 1).toShort()) }
        return Mesh(fb(pos), fb(nrm), fb(uv), sb(idx), GLES30.GL_UNSIGNED_SHORT, idx.size, floatArrayOf(-r, -r, -r, r, r, r))
    }
}
