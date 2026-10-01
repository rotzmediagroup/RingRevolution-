package com.shiostudios.dumplingrings.ui.board3d

import com.shiostudios.dumplingrings.core.geometry.Geometry
import com.shiostudios.dumplingrings.core.model.RingDef
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural torus-arc mesh for one ring. The mesh is built in the ring's LOCAL frame (angle 0 = ring reference) and
 * is rotated in the vertex shader, which also applies the woven height profile at the fixed world-space crossing angles.
 *
 * Vertex layout (floats): localAngleRad, tubeAngleRad, u, v, capScale   (position is reconstructed in the shader from the two angles
 * plus uniforms: centre, major radius, minor radius). This keeps the mesh tiny and lets rotation/weave happen on the GPU.
 */
class TorusMesh private constructor(val vertices: FloatBuffer, val indices: ShortBuffer, val vertexCount: Int, val indexCount: Int) {
    companion object {
        const val FLOATS_PER_VERTEX = 5

        /** Build the wire segments (complement of gaps) as tube arcs; cap ends are closed with a rounded dome. */
        fun build(ring: RingDef, segmentsPerDeg: Float = 0.45f, tubeSides: Int = 16): TorusMesh {
            val segs = wireSegments(ring)
            val verts = ArrayList<Float>()
            val idx = ArrayList<Short>()
            val uScale = (2 * PI * ring.radius / (ring.thickness * 3.2)).toFloat() // texture repeats along the tube
            for ((a0, a1) in segs) {
                val span = a1 - a0
                val n = maxOf(6, (span * segmentsPerDeg).toInt())
                val base = verts.size / FLOATS_PER_VERTEX
                // dome caps: a few rings of shrinking tube radius are encoded with tube angle offset via v>1 trick:
                // we simply pre/post-pend cap rings by passing a "capScale" through the v coordinate's integer part.
                val capRings = 4
                val capDeg = Math.toDegrees(ring.thickness * 0.5 / ring.radius) * 0.95 // dome lies INSIDE the logical wire extent
                val total = n + 1 + 2 * capRings
                for (i in 0 until total) {
                    val capT: Float; val ang: Double
                    when {
                        i < capRings -> { capT = i / capRings.toFloat(); ang = a0 + capT * capDeg }
                        i > n + capRings -> { val k = (i - (n + capRings)); capT = 1f - k / capRings.toFloat(); ang = a1 - capT * capDeg }
                        else -> { capT = 1f; ang = (a0 + capDeg) + (span - 2 * capDeg) * (i - capRings) / n.toDouble() }
                    }
                    val capScale = kotlin.math.sqrt((1f - (1f - capT) * (1f - capT)).coerceIn(0f, 1f)) // dome profile
                    val capSigned = if (i < capRings) -capScale else capScale // negative = start cap (dome faces decreasing angle)
                    val u = (Math.toRadians(ang) * uScale / (2 * PI)).toFloat()
                    for (s in 0..tubeSides) {
                        val tAng = 2 * PI * s / tubeSides
                        verts.add(Math.toRadians(ang).toFloat()); verts.add(tAng.toFloat())
                        verts.add(u); verts.add(s / tubeSides.toFloat()); verts.add(if (capT >= 1f) 1f else capSigned)
                    }
                }
                val stride = tubeSides + 1
                for (i in 0 until total - 1) for (s in 0 until tubeSides) {
                    val a = base + i * stride + s; val b = a + stride
                    idx.add(a.toShort()); idx.add(b.toShort()); idx.add((a + 1).toShort())
                    idx.add((a + 1).toShort()); idx.add(b.toShort()); idx.add((b + 1).toShort())
                }
            }
            val vb = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            verts.forEach { vb.put(it) }; vb.position(0)
            val ib = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
            idx.forEach { ib.put(it) }; ib.position(0)
            return TorusMesh(vb, ib, verts.size / FLOATS_PER_VERTEX, idx.size)
        }

        /**
         * Dome caps only (for Meshy ring meshes whose gaps are cut in the shader): one dome at each wire-segment end,
         * built with the same signed-cap layout as [build] so the ring shader renders them unchanged.
         */
        fun buildCaps(ring: RingDef, tubeSides: Int = 16): TorusMesh {
            val segs = wireSegments(ring)
            val verts = ArrayList<Float>(); val idx = ArrayList<Short>()
            val uScale = (2 * PI * ring.radius / (ring.thickness * 3.2)).toFloat()
            val capRings = 5
            val capDeg = Math.toDegrees(ring.thickness * 0.5 / ring.radius) * 0.95
            for ((a0, a1) in segs) for (end in 0..1) {
                val base = verts.size / FLOATS_PER_VERTEX
                for (i in 0..capRings) {
                    val capT = i / capRings.toFloat()
                    val ang = if (end == 0) a0 + capT * capDeg else a1 - capT * capDeg
                    val capScale = kotlin.math.sqrt((1f - (1f - capT) * (1f - capT)).coerceIn(0f, 1f))
                    val signed = if (capT >= 1f) 1f else if (end == 0) -capScale else capScale
                    val u = (Math.toRadians(ang) * uScale / (2 * PI)).toFloat()
                    for (s in 0..tubeSides) { val tAng = 2 * PI * s / tubeSides; verts.add(Math.toRadians(ang).toFloat()); verts.add(tAng.toFloat()); verts.add(u); verts.add(s / tubeSides.toFloat()); verts.add(signed) }
                }
                val stride = tubeSides + 1
                for (i in 0 until capRings) for (s in 0 until tubeSides) {
                    val a = base + i * stride + s; val b = a + stride
                    idx.add(a.toShort()); idx.add(b.toShort()); idx.add((a + 1).toShort()); idx.add((a + 1).toShort()); idx.add(b.toShort()); idx.add((b + 1).toShort())
                }
            }
            val vb = ByteBuffer.allocateDirect(maxOf(verts.size, 1) * 4).order(ByteOrder.nativeOrder()).asFloatBuffer(); verts.forEach { vb.put(it) }; vb.position(0)
            val ib = ByteBuffer.allocateDirect(maxOf(idx.size, 1) * 2).order(ByteOrder.nativeOrder()).asShortBuffer(); idx.forEach { ib.put(it) }; ib.position(0)
            return TorusMesh(vb, ib, verts.size / FLOATS_PER_VERTEX, idx.size)
        }

        /** Local wire intervals in degrees (complement of the gaps), unwrapped and merged. */
        fun wireSegments(r: RingDef): List<Pair<Double, Double>> {
            val gaps = r.gaps.map { Geometry.normDeg(it.startDeg) to it.widthDeg }.sortedBy { it.first }
            val out = ArrayList<Pair<Double, Double>>()
            var cur = 0.0; var wrapTrim = 0.0
            for ((s, w) in gaps) { if (s > cur) out.add(cur to s); cur = maxOf(cur, s + w) }
            if (cur < 360) out.add(cur to 360.0) else wrapTrim = cur - 360
            if (wrapTrim > 0 && out.isNotEmpty()) out[0] = maxOf(out[0].first, wrapTrim) to out[0].second
            // merge a segment ending at 360 with one starting at 0 (no gap across the reference angle)
            if (out.size >= 2 && out.last().second >= 360.0 - 1e-6 && out.first().first <= 1e-6) {
                val merged = out.last().first to (out.first().second + 360.0)
                return listOf(merged) + out.subList(1, out.size - 1)
            }
            return out.filter { it.second - it.first > 0.5 }
        }
    }
}

/** A flat unit quad on the board plane used for the soft contact shadow and glow sprites. */
object QuadMesh {
    val vertices: FloatBuffer = ByteBuffer.allocateDirect(6 * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, -1f, -1f, 0f, 0f, 1f, 1f, 1f, 1f, -1f, 1f, 0f, 1f)); position(0)
    }
}
