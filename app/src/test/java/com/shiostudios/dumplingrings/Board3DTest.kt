package com.shiostudios.dumplingrings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shiostudios.dumplingrings.core.model.GapDef
import com.shiostudios.dumplingrings.core.model.RingDef
import com.shiostudios.dumplingrings.ui.board3d.BoardCamera
import com.shiostudios.dumplingrings.ui.board3d.GlbLoader
import com.shiostudios.dumplingrings.ui.board3d.TorusMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class Board3DTest {
    @Test fun cameraProjectUnprojectRoundTrip() {
        val cam = BoardCamera(1080, 2200); cam.update()
        for ((x, y) in listOf(0.5f to 0.5f, 0.1f to 0.2f, 0.9f to 0.85f, 0.3f to 0.7f)) {
            val p = cam.project(x, y, 0f)
            val b = cam.unproject(p[0], p[1], 0f)
            assertEquals(x, b[0], 1e-3f); assertEquals(y, b[1], 1e-3f)
        }
        // centre of the board lands near the centre of the viewport; the board fits inside the viewport
        val c = cam.project(0.5f, 0.5f, 0f); assertEquals(540f, c[0], 1f)
        val l = cam.project(0f, 0.5f, 0f); val r = cam.project(1f, 0.5f, 0f)
        assertTrue(l[0] > 0f && r[0] < 1080f)
    }

    @Test fun torusWireSegmentsAndMesh() {
        val r = RingDef("r", listOf(0.5, 0.5), 0.2, 0.05, listOf(GapDef(0.0, 60.0), GapDef(150.0, 40.0)), 0, 30, 0)
        val segs = TorusMesh.wireSegments(r)
        assertEquals(2, segs.size)
        assertEquals(60.0, segs[0].first, 1e-9); assertEquals(150.0, segs[0].second, 1e-9)
        assertEquals(190.0, segs[1].first, 1e-9); assertEquals(360.0, segs[1].second, 1e-9)
        val m = TorusMesh.build(r)
        assertTrue(m.vertexCount > 100 && m.indexCount % 3 == 0)
        // no vertex falls inside a gap (angles are in local radians)
        for (i in 0 until m.vertexCount) {
            val a = Math.toDegrees(m.vertices.get(i * 5).toDouble())
            val inGap = (a > 2 && a < 58) || (a > 152 && a < 188)
            assertTrue("vertex at $a inside a gap", !inGap)
        }
        // single-gap ring: wire is one segment that wraps through the reference angle
        val r1 = RingDef("r", listOf(0.5, 0.5), 0.2, 0.05, listOf(GapDef(30.0, 60.0)), 0, 30, 0)
        val s1 = TorusMesh.wireSegments(r1); assertEquals(1, s1.size); assertEquals(90.0, s1[0].first, 1e-9); assertEquals(390.0, s1[0].second, 1e-9)
    }

    /** Evaluate the vertex-shader maths on the CPU and export an OBJ for the software render in tools/level_preview. */
    @Test fun exportMeshObjForVisualCheck() {
        val r = RingDef("r", listOf(0.5, 0.5), 0.2, 0.05, listOf(GapDef(0.0, 70.0)), 0, 30, 0)
        val m = TorusMesh.build(r)
        val bumps = listOf(Math.toRadians(120.0).toFloat() to 0.028f, Math.toRadians(250.0).toFloat() to -0.028f)
        val sigma = (0.025f * 2.6f / 0.2f)
        fun bumpAt(wa: Float): Float { var z = 0f; for ((b, h) in bumps) { var d = wa - b; d -= (6.2831853f * Math.floor(((d + 3.14159265f) / 6.2831853f).toDouble()).toFloat()); z += h * exp(-0.5f * d * d / (sigma * sigma)) }; return z }
        val sb = StringBuilder()
        for (i in 0 until m.vertexCount) {
            val la = m.vertices.get(i * 5); val ta = m.vertices.get(i * 5 + 1)
            val capScale = kotlin.math.abs(m.vertices.get(i * 5 + 4))
            val rad = floatArrayOf(cos(la), sin(la))
            val z0 = 0.025f * 1.02f + bumpAt(la)
            val n = floatArrayOf(cos(ta) * rad[0], cos(ta) * rad[1], sin(ta))
            val p = floatArrayOf(0.5f + rad[0] * 0.2f + n[0] * 0.025f * capScale, 0.5f + rad[1] * 0.2f + n[1] * 0.025f * capScale, z0 + n[2] * 0.025f * capScale)
            sb.append("v ${p[0]} ${p[1]} ${p[2]}\n")
        }
        for (i in 0 until m.indexCount / 3) sb.append("f ${m.indices.get(i * 3) + 1} ${m.indices.get(i * 3 + 1) + 1} ${m.indices.get(i * 3 + 2) + 1}\n")
        val out = File("build/screenshots").apply { mkdirs() }
        File(out, "ring_mesh.obj").writeText(sb.toString())
        assertTrue(File(out, "ring_mesh.obj").length() > 1000)
    }

    @Test fun glbLoaderParsesMinimalModel() {
        // one triangle, uint16 indices, positions+normals+uv, no material
        val bin = ByteBuffer.allocate(3 * 3 * 4 + 3 * 3 * 4 + 3 * 2 * 4 + 3 * 2 + 2).order(ByteOrder.LITTLE_ENDIAN)
        for (v in floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)) bin.putFloat(v)
        for (v in floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f)) bin.putFloat(v)
        for (v in floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f)) bin.putFloat(v)
        bin.putShort(0); bin.putShort(1); bin.putShort(2); bin.putShort(0)
        val binBytes = bin.array()
        val json = """{"asset":{"version":"2.0"},"meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1,"TEXCOORD_0":2},"indices":3}]}],
          "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},{"bufferView":1,"componentType":5126,"count":3,"type":"VEC3"},{"bufferView":2,"componentType":5126,"count":3,"type":"VEC2"},{"bufferView":3,"componentType":5123,"count":3,"type":"SCALAR"}],
          "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},{"buffer":0,"byteOffset":36,"byteLength":36},{"buffer":0,"byteOffset":72,"byteLength":24},{"buffer":0,"byteOffset":96,"byteLength":6}],"buffers":[{"byteLength":${binBytes.size}}]}"""
        var jb = json.toByteArray(); while (jb.size % 4 != 0) jb += ' '.code.toByte()
        val total = 12 + 8 + jb.size + 8 + binBytes.size
        val glb = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        glb.putInt(0x46546C67); glb.putInt(2); glb.putInt(total)
        glb.putInt(jb.size); glb.putInt(0x4E4F534A); glb.put(jb)
        glb.putInt(binBytes.size); glb.putInt(0x004E4942); glb.put(binBytes)
        val model = GlbLoader.load(glb.array())
        assertNotNull(model)
        assertEquals(3, model!!.indexCount); assertEquals(9, model.positions.capacity())
        assertEquals(1f, model.bounds[3], 1e-6f); assertEquals(1f, model.bounds[4], 1e-6f)
    }

    @Test fun ringCutterCutsRealGapsWithClosedFaces() {
        // synthetic tube-space torus: 96 segments around, 12 around the tube
        val segs = 96; val sides = 12; val F = com.shiostudios.dumplingrings.ui.board3d.RingModel.FLOATS_PER_VERTEX
        val v = FloatArray(segs * sides * F); val idx = ArrayList<Int>()
        for (a in 0 until segs) for (b in 0 until sides) {
            val o = (a * sides + b) * F; val phi = (2 * Math.PI * a / segs - Math.PI).toFloat(); val t = (2 * Math.PI * b / sides).toFloat()
            v[o] = phi; v[o + 1] = kotlin.math.cos(t); v[o + 2] = kotlin.math.sin(t); v[o + 3] = a / segs.toFloat(); v[o + 4] = b / sides.toFloat()
            v[o + 5] = kotlin.math.cos(t); v[o + 7] = kotlin.math.sin(t)
        }
        for (a in 0 until segs) for (b in 0 until sides) {
            val p0 = a * sides + b; val p1 = ((a + 1) % segs) * sides + b; val p2 = ((a + 1) % segs) * sides + (b + 1) % sides; val p3 = a * sides + (b + 1) % sides
            idx += listOf(p0, p1, p2, p0, p2, p3)
        }
        val gaps = listOf(Math.toRadians(10.0).toFloat() to Math.toRadians(62.0).toFloat(), Math.toRadians(200.0).toFloat() to Math.toRadians(40.0).toFloat())
        val r = com.shiostudios.dumplingrings.ui.board3d.RingCutter.cut(v, idx.toIntArray(), gaps)
        val shrunk = gaps.map { (s, w) -> (s + 1e-3f) to (w - 2e-3f) }
        for (k in 0 until r.vertexCount) assertFalse("vertex inside a gap", com.shiostudios.dumplingrings.ui.board3d.RingCutter.inGap(r.vertices[k * F], shrunk))
        // every triangle of the result is referenced in range
        assertTrue(r.indices.all { it in 0 until r.vertexCount })
        // 4 gap edges; each tube quad = 2 triangles crossing the edge -> 2 * sides outline segments per closed cut face
        assertEquals(4 * 2 * sides, r.capTriangles)
        // each cut face's outline is closed: every outline point (fan vertices 2 and 3) occurs an even number of times
        val capStart = r.indices.size - r.capTriangles * 3
        val counts = HashMap<String, Int>()
        for (t in 0 until r.capTriangles) for (k in 1..2) {
            val vi = r.indices[capStart + t * 3 + k]
            val key = "%.4f/%.4f/%.4f".format(r.vertices[vi * F], r.vertices[vi * F + 1], r.vertices[vi * F + 2])
            counts[key] = (counts[key] ?: 0) + 1
        }
        assertTrue("open cut outline", counts.values.all { it % 2 == 0 })
    }
}
