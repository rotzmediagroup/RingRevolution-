package com.shiostudios.dumplingrings.ui.board3d

import kotlin.math.PI

/**
 * Cuts the level's gaps out of a Meshy ring as real geometry (no shader discard): every triangle that crosses a gap edge
 * is clipped exactly at that edge's radial plane, and each edge gets a closed cut face built from the clip outline, so the
 * ring's own material continues onto the cut. Works in RingModel's tube space (9 floats: phi, rhoN, zN, u, v, nR, nT, nZ, pad),
 * so the result is still placed rigidly by GLBRING_VS. Pure Kotlin: unit-tested on the JVM.
 */
object RingCutter {
    private const val F = RingModel.FLOATS_PER_VERTEX
    private const val TWO_PI = (2 * PI).toFloat()

    class Result(val vertices: FloatArray, val vertexCount: Int, val indices: IntArray, val capTriangles: Int)

    /** gaps: (startRad, widthRad) in the ring's local frame, same convention as the level (startDeg/widthDeg). */
    fun cut(verts: FloatArray, idx: IntArray, gaps: List<Pair<Float, Float>>): Result {
        if (gaps.isEmpty()) return Result(verts, verts.size / F, idx, 0)
        val out = FloatArrayBuilder(verts.size + 4096); val tris = IntArrayBuilder(idx.size + 2048)
        // boundary planes: (angle, keepBelow) — keep phi <= start, keep phi >= end
        val planes = ArrayList<Pair<Float, Boolean>>()
        for ((s, w) in gaps) { planes.add(s to true); planes.add(s + w to false) }
        val segs = Array(planes.size) { ArrayList<FloatArray>() }   // per plane: list of clipped edge endpoints (pairs of vertices)
        val tri = Array(3) { FloatArray(F) }
        for (t in 0 until idx.size / 3) {
            for (k in 0..2) System.arraycopy(verts, idx[t * 3 + k] * F, tri[k], 0, F)
            // unwrap phi around the first vertex
            for (k in 1..2) tri[k][0] = unwrapNear(tri[k][0], tri[0][0])
            val inside = BooleanArray(3) { inGap(tri[it][0], gaps) }
            if (inside.all { it }) continue
            if (inside.none { it }) { val b = out.size / F; for (k in 0..2) out.add(tri[k]); tris.add(b); tris.add(b + 1); tris.add(b + 2); continue }
            // the triangle crosses a gap edge: find the plane between its inside and outside vertices
            val pIn = tri[inside.indexOfFirst { it }][0]; val pOut = tri[inside.indexOfFirst { !it }][0]
            var best = -1; var bestAng = 0f
            for ((pi, pl) in planes.withIndex()) {
                val a = unwrapNear(pl.first, (pIn + pOut) / 2)
                if ((a - pIn) * (a - pOut) <= 0f) { best = pi; bestAng = a; break }
            }
            if (best < 0) continue
            val keepBelow = planes[best].second
            val poly = clip(tri.toList(), bestAng, keepBelow, segs[best])
            if (poly.size < 3) continue
            val b = out.size / F; for (v in poly) out.add(v)
            for (k in 1 until poly.size - 1) { tris.add(b); tris.add(b + k); tris.add(b + k + 1) }
        }
        // cut faces: fan from the outline's centroid; each fan triangle takes its UVs from its own outline edge so the
        // texture of the ring surface continues into the cut (no colour from other UV islands is mixed in)
        var capTris = 0
        for ((pi, pl) in planes.withIndex()) {
            val list = segs[pi]; if (list.size < 2) continue
            var cr = 0f; var cz = 0f; for (v in list) { cr += v[1]; cz += v[2] }; cr /= list.size; cz /= list.size
            val nT = if (pl.second) 1f else -1f        // face points into the gap
            for (k in 0 until list.size / 2) {
                val p = list[k * 2]; val q = list[k * 2 + 1]
                val c = FloatArray(F); c[0] = p[0]; c[1] = cr; c[2] = cz; c[3] = (p[3] + q[3]) / 2; c[4] = (p[4] + q[4]) / 2
                val b = out.size / F
                for (v in arrayOf(c, p.copyOf(), q.copyOf())) { v[5] = 0f; v[6] = nT; v[7] = 0f; out.add(v) }
                tris.add(b); tris.add(b + 1); tris.add(b + 2); capTris++
            }
        }
        return Result(out.toArray(), out.size / F, tris.toArray(), capTris)
    }

    fun inGap(phi: Float, gaps: List<Pair<Float, Float>>): Boolean {
        for ((s, w) in gaps) { var d = (phi - s) % TWO_PI; if (d < 0) d += TWO_PI; if (d < w) return true }
        return false
    }

    private fun unwrapNear(a: Float, ref: Float): Float { var x = a; while (x - ref > PI) x -= TWO_PI; while (ref - x > PI) x += TWO_PI; return x }

    /** Sutherland–Hodgman against the radial plane phi = ang; records the two new outline points of this triangle. */
    private fun clip(poly: List<FloatArray>, ang: Float, keepBelow: Boolean, seg: ArrayList<FloatArray>): List<FloatArray> {
        fun d(v: FloatArray) = if (keepBelow) ang - v[0] else v[0] - ang     // >= 0 means kept
        val res = ArrayList<FloatArray>(); val cut = ArrayList<FloatArray>()
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[(i + 1) % poly.size]; val da = d(a); val db = d(b)
            if (da >= 0) res.add(a)
            if ((da >= 0) != (db >= 0)) {
                val t = da / (da - db); val v = FloatArray(F) { a[it] + (b[it] - a[it]) * t }
                v[0] = ang
                val nl = kotlin.math.sqrt(v[5] * v[5] + v[6] * v[6] + v[7] * v[7]).coerceAtLeast(1e-6f); v[5] /= nl; v[6] /= nl; v[7] /= nl
                res.add(v); cut.add(v)
            }
        }
        if (cut.size == 2) { seg.add(cut[0]); seg.add(cut[1]) }
        return res
    }

    private class FloatArrayBuilder(cap: Int) {
        var data = FloatArray(cap); var size = 0
        fun add(v: FloatArray) { if (size + v.size > data.size) data = data.copyOf(maxOf(data.size * 2, size + v.size)); System.arraycopy(v, 0, data, size, v.size); size += v.size }
        fun toArray() = data.copyOf(size)
    }
    private class IntArrayBuilder(cap: Int) {
        var data = IntArray(cap); var size = 0
        fun add(v: Int) { if (size == data.size) data = data.copyOf(data.size * 2); data[size++] = v }
        fun toArray() = data.copyOf(size)
    }
}
