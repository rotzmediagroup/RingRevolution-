package com.shiostudios.dumplingrings.ui.board3d

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns an arbitrary Meshy torus mesh into tube-space vertex data so one model can dress ANY level ring:
 * every vertex is expressed as (phi, rhoN, zN) — angle around the ring, signed radial offset from the tube centreline and
 * height, both in units of the model's own tube radius — plus UV and the normal in the local (radial, tangent, up) frame.
 * The vertex shader rebuilds the position for the level's major/minor radius, rotation and weave profile, and the fragment
 * shader cuts the level's gaps by phi. Layout (9 floats): phi, rhoN, zN, u, v, nR, nT, nZ, pad.
 */
class RingModel(val vertices: FloatBuffer, val vertexCount: Int, val indices: java.nio.Buffer, val indexType: Int, val indexCount: Int,
                val model: GlbModel, val majorRadius: Float, val minorRadius: Float, val thicknessRatio: Float) {
    companion object {
        const val FLOATS_PER_VERTEX = 9

        fun from(m: GlbModel): RingModel {
            val n = m.positions.capacity() / 3
            val ext = floatArrayOf(m.bounds[3] - m.bounds[0], m.bounds[4] - m.bounds[1], m.bounds[5] - m.bounds[2])
            val axis = (0..2).minByOrNull { ext[it] } ?: 1   // the ring's axis is the thinnest extent
            val c = floatArrayOf((m.bounds[0] + m.bounds[3]) / 2, (m.bounds[1] + m.bounds[4]) / 2, (m.bounds[2] + m.bounds[5]) / 2)
            // map model axes -> (x, y, z) with the ring axis as z
            val ax = when (axis) { 0 -> intArrayOf(1, 2, 0); 1 -> intArrayOf(0, 2, 1); else -> intArrayOf(0, 1, 2) }
            val px = FloatArray(n); val py = FloatArray(n); val pz = FloatArray(n)
            var rhoMin = Float.MAX_VALUE; var rhoMax = 0f
            for (i in 0 until n) {
                val v = floatArrayOf(m.positions.get(i * 3) - c[0], m.positions.get(i * 3 + 1) - c[1], m.positions.get(i * 3 + 2) - c[2])
                px[i] = v[ax[0]]; py[i] = v[ax[1]]; pz[i] = v[ax[2]]
                val rho = hypot(px[i], py[i]); if (rho < rhoMin) rhoMin = rho; if (rho > rhoMax) rhoMax = rho
            }
            val major = (rhoMax + rhoMin) / 2f
            val minor = maxOf((rhoMax - rhoMin) / 2f, ext[axis] / 2f, 1e-4f)
            val out = ByteBuffer.allocateDirect(n * FLOATS_PER_VERTEX * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            for (i in 0 until n) {
                val phi = atan2(py[i], px[i])
                val rho = hypot(px[i], py[i])
                val rN = (rho - major) / minor; val zN = pz[i] / minor
                val u = m.uvs?.get(i * 2) ?: 0f; val v = m.uvs?.get(i * 2 + 1) ?: 0f
                var nR = 0f; var nT = 0f; var nZ = 1f
                if (m.normals != null) {
                    val nn = floatArrayOf(m.normals.get(i * 3), m.normals.get(i * 3 + 1), m.normals.get(i * 3 + 2))
                    val nx = nn[ax[0]]; val ny = nn[ax[1]]; val nz = nn[ax[2]]
                    val cr = cos(phi); val sr = sin(phi)
                    nR = nx * cr + ny * sr; nT = -nx * sr + ny * cr; nZ = nz
                    val l = sqrt(nR * nR + nT * nT + nZ * nZ); if (l > 1e-6f) { nR /= l; nT /= l; nZ /= l }
                }
                out.put(phi); out.put(rN); out.put(zN); out.put(u); out.put(v); out.put(nR); out.put(nT); out.put(nZ); out.put(0f)
            }
            out.position(0)
            return RingModel(out, n, m.indices, m.indexType, m.indexCount, m, major, minor, minor / maxOf(rhoMax, 1e-4f))
        }
    }
}
