package com.shiostudios.dumplingrings.core.geometry

import com.shiostudios.dumplingrings.core.model.GapDef
import com.shiostudios.dumplingrings.core.model.ObstacleDef
import com.shiostudios.dumplingrings.core.model.ObstacleShape
import com.shiostudios.dumplingrings.core.model.RingDef
import com.shiostudios.dumplingrings.core.model.WeavePattern
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deterministic topological escape model ("weave / lift-out model"), see docs/SOLVER.md.
 *
 * Rings are flat hoops lying on the table. Where two hoops cross, one of them lies OVER the other at each of the
 * two crossing points (the weave pattern of the pair). A ring is released by lifting it straight off the table:
 * every piece of wire that lies OVER the ring at a crossing must pass through a gap of the lifted ring,
 * unless that piece of wire is itself absent (the other ring's gap is at that crossing, or the ring was removed).
 * Obstacles (chopsticks, arcs, colour gates) always rest on top of the rings.
 *
 * Everything is pairwise and depends only on the lifted ring's angle and the other body's driver angle, so it is
 * pre-computed into boolean [PassTable]s by the rule engine.
 */
object Geometry {
    const val SAMPLE_DEG = 1.0
    const val EXTRA_MARGIN_DEG = 1.0

    fun normDeg(a: Double): Double { var x = a % 360.0; if (x < 0) x += 360.0; return x }
    fun normDegInt(a: Int): Int = ((a % 360) + 360) % 360

    /** Is world angle `phi` inside the gap `g` of a ring rotated by `ringAngleDeg`, shrunk by `marginDeg` on both sides? */
    fun inGap(g: GapDef, ringAngleDeg: Double, phiDeg: Double, marginDeg: Double): Boolean {
        val start = g.startDeg + ringAngleDeg + marginDeg
        val width = g.widthDeg - 2 * marginDeg
        if (width <= 0) return false
        return normDeg(phiDeg - start) <= width
    }

    fun inAnyGap(gaps: List<GapDef>, ringAngleDeg: Double, phiDeg: Double, marginDeg: Double): Boolean =
        gaps.any { inGap(it, ringAngleDeg, phiDeg, marginDeg) }

    /** Sample points of a ring's wire (excluding gaps) at the given absolute angle. */
    fun ringWire(r: RingDef, angleDeg: Double): List<DoubleArray> {
        val pts = ArrayList<DoubleArray>(360)
        var local = 0.0
        while (local < 360.0) {
            if (!inAnyGap(r.gaps, 0.0, local, 0.0)) {
                val w = Math.toRadians(local + angleDeg)
                pts.add(doubleArrayOf(r.center[0] + r.radius * cos(w), r.center[1] + r.radius * sin(w)))
            }
            local += SAMPLE_DEG
        }
        return pts
    }

    /** Sample points of an obstacle's wire for the driver angle (0 if static). */
    fun obstacleWire(o: ObstacleDef, driverAngleDeg: Double): List<DoubleArray> {
        val rot = Math.toRadians(o.baseAngleDeg + driverAngleDeg)
        val pts = ArrayList<DoubleArray>()
        when (o.shape) {
            ObstacleShape.ARC -> {
                var a = 0.0
                while (a <= o.widthDeg) {
                    val w = Math.toRadians(o.startDeg + a) + rot
                    pts.add(doubleArrayOf(o.center[0] + o.radius * cos(w), o.center[1] + o.radius * sin(w)))
                    a += SAMPLE_DEG
                }
            }
            ObstacleShape.SEGMENT -> {
                val fx = o.from[0] - o.center[0]; val fy = o.from[1] - o.center[1]
                val tx = o.to[0] - o.center[0]; val ty = o.to[1] - o.center[1]
                val len = sqrt((tx - fx) * (tx - fx) + (ty - fy) * (ty - fy))
                val n = maxOf(2, (len / 0.003).toInt())
                val c = cos(rot); val s = sin(rot)
                for (i in 0..n) {
                    val t = i.toDouble() / n
                    val x = fx + (tx - fx) * t; val y = fy + (ty - fy) * t
                    pts.add(doubleArrayOf(o.center[0] + x * c - y * s, o.center[1] + x * s + y * c))
                }
            }
        }
        return pts
    }

    /** Intersection points of two ring circles, sorted by world angle around `a`'s center; empty if none. */
    fun crossings(a: RingDef, b: RingDef): List<Crossing> {
        val dx = b.center[0] - a.center[0]; val dy = b.center[1] - a.center[1]
        val d = sqrt(dx * dx + dy * dy)
        if (d < 1e-9) return emptyList()
        if (d > a.radius + b.radius || d < abs(a.radius - b.radius)) return emptyList()
        val x = (d * d + a.radius * a.radius - b.radius * b.radius) / (2 * d)
        val h2 = a.radius * a.radius - x * x
        if (h2 < 0) return emptyList()
        val h = sqrt(h2)
        val ux = dx / d; val uy = dy / d
        val px = a.center[0] + ux * x; val py = a.center[1] + uy * x
        val pts = listOf(doubleArrayOf(px - uy * h, py + ux * h), doubleArrayOf(px + uy * h, py - ux * h))
        return pts.map { p ->
            Crossing(p[0], p[1],
                normDeg(Math.toDegrees(atan2(p[1] - a.center[1], p[0] - a.center[0]))),
                normDeg(Math.toDegrees(atan2(p[1] - b.center[1], p[0] - b.center[0]))))
        }.sortedBy { it.angleOnA }
    }

    /** Does `a` lie over `b` at crossing index `idx` (0 or 1, sorted around a's center) under `pattern` (from a's perspective)? */
    fun aOverAt(pattern: WeavePattern, idx: Int): Boolean = when (pattern) {
        WeavePattern.ALT1 -> idx == 0
        WeavePattern.ALT2 -> idx == 1
        WeavePattern.A_OVER -> true
        WeavePattern.B_OVER -> false
    }

    /**
     * Can ring `a` (rotated to aDeg) be lifted past the given wire points that rest on top of it?
     * Every wire sample inside a's band must lie in one of a's gaps.
     */
    fun canLiftPast(a: RingDef, aDeg: Double, wire: List<DoubleArray>, wireThickness: Double): Boolean {
        val halfBand = (a.thickness + wireThickness) / 2.0
        for (p in wire) {
            val qx = p[0] - a.center[0]; val qy = p[1] - a.center[1]
            val dist = sqrt(qx * qx + qy * qy)
            if (abs(dist - a.radius) > halfBand) continue
            val phi = Math.toDegrees(atan2(qy, qx))
            if (!inAnyGap(a.gaps, aDeg, phi, EXTRA_MARGIN_DEG)) return false
        }
        return true
    }

    /** Wire samples of `b` that lie over `a` according to the weave pattern. */
    fun overWire(a: RingDef, b: RingDef, bWire: List<DoubleArray>, pattern: WeavePattern): List<DoubleArray> {
        val cr = crossings(a, b)
        if (cr.isEmpty()) return emptyList()
        val halfBand = (a.thickness + b.thickness) / 2.0 + 0.002
        val out = ArrayList<DoubleArray>()
        for (p in bWire) {
            val qx = p[0] - a.center[0]; val qy = p[1] - a.center[1]
            val dist = sqrt(qx * qx + qy * qy)
            if (abs(dist - a.radius) > halfBand) continue
            // which crossing is this sample part of?
            var best = 0; var bestD = Double.MAX_VALUE
            for (i in cr.indices) {
                val ddx = p[0] - cr[i].x; val ddy = p[1] - cr[i].y
                val dd = ddx * ddx + ddy * ddy
                if (dd < bestD) { bestD = dd; best = i }
            }
            val bOver = !aOverAt(pattern, best)
            if (bOver) out.add(p)
        }
        return out
    }

    fun circlesCross(a: RingDef, b: RingDef): Boolean = crossings(a, b).size == 2

    /** Crossing angle between the two circles at their intersection (deg, 0..90). Small angles = ugly, shallow crossings. */
    fun crossingAngleDeg(a: RingDef, b: RingDef): Double {
        val c = crossings(a, b)
        if (c.isEmpty()) return 0.0
        val p = c[0]
        val t1 = doubleArrayOf(-(p.y - a.center[1]), p.x - a.center[0])
        val t2 = doubleArrayOf(-(p.y - b.center[1]), p.x - b.center[0])
        val dot = abs(t1[0] * t2[0] + t1[1] * t2[1]) / (a.radius * b.radius)
        return Math.toDegrees(kotlin.math.acos(dot.coerceIn(0.0, 1.0)))
    }
}

data class Crossing(val x: Double, val y: Double, val angleOnA: Double, val angleOnB: Double)

/** pass[moverAngleIdx][driverAngleIdx] */
class PassTable(val rows: Int, val cols: Int) {
    val data = BooleanArray(rows * cols)
    operator fun get(r: Int, c: Int) = data[r * cols + c]
    operator fun set(r: Int, c: Int, v: Boolean) { data[r * cols + c] = v }
    fun row(r: Int): BooleanArray = data.copyOfRange(r * cols, (r + 1) * cols)
    fun col(c: Int): BooleanArray = BooleanArray(rows) { data[it * cols + c] }
    val allTrue: Boolean get() = data.all { it }
}
