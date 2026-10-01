package com.shiostudios.dumplingrings.ui.board3d

import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Shared camera for the 3D board: a perspective camera tilted `tiltDeg` from vertical, looking at the centre of the
 * unit board (z = 0 plane). Both the GL renderer and the Compose overlay use it, so decorations and touch hit-testing
 * line up exactly with the rendered rings.
 *
 * Board space: x,y in [0,1] (y up), z up out of the table. World space == board space (the board is 1 unit wide).
 */
class BoardCamera(var viewportW: Int = 1, var viewportH: Int = 1, var tiltDeg: Float = 26f, var fovDeg: Float = 34f) {
    val view = FloatArray(16)
    val proj = FloatArray(16)
    val viewProj = FloatArray(16)
    private val invViewProj = FloatArray(16)
    val eye = FloatArray(3)

    fun update() {
        val aspect = viewportW.toFloat() / viewportH.coerceAtLeast(1)
        // distance so that the unit board fits the narrower viewport dimension with a small margin
        val halfFov = Math.toRadians(fovDeg / 2.0)
        val fitHalf = 0.56f
        val dist = (fitHalf / tan(halfFov)).toFloat() * (if (aspect < 1f) 1f / aspect else 1f)
        val t = Math.toRadians(tiltDeg.toDouble())
        val cx = 0.5f; val cy = 0.5f
        eye[0] = cx; eye[1] = (cy - dist * sin(t)).toFloat(); eye[2] = (dist * cos(t)).toFloat()
        Matrix.setLookAtM(view, 0, eye[0], eye[1], eye[2], cx, cy, 0f, 0f, 0f, 1f)
        Matrix.perspectiveM(proj, 0, fovDeg, aspect, 0.1f, 10f)
        Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0)
        Matrix.invertM(invViewProj, 0, viewProj, 0)
    }

    /** Board-space point -> viewport pixels (origin top-left). */
    fun project(x: Float, y: Float, z: Float, out: FloatArray = FloatArray(2)): FloatArray {
        val v = floatArrayOf(x, y, z, 1f); val r = FloatArray(4)
        Matrix.multiplyMV(r, 0, viewProj, 0, v, 0)
        val w = if (r[3] == 0f) 1e-6f else r[3]
        out[0] = (r[0] / w * 0.5f + 0.5f) * viewportW
        out[1] = (1f - (r[1] / w * 0.5f + 0.5f)) * viewportH
        return out
    }

    /** Viewport pixels -> board-space point on the plane z = planeZ (ray cast). */
    fun unproject(px: Float, py: Float, planeZ: Float = 0f, out: FloatArray = FloatArray(2)): FloatArray {
        val nx = px / viewportW * 2f - 1f; val ny = 1f - py / viewportH * 2f
        val near = floatArrayOf(nx, ny, -1f, 1f); val far = floatArrayOf(nx, ny, 1f, 1f)
        val a = FloatArray(4); val b = FloatArray(4)
        Matrix.multiplyMV(a, 0, invViewProj, 0, near, 0); Matrix.multiplyMV(b, 0, invViewProj, 0, far, 0)
        for (i in 0..2) { a[i] /= a[3]; b[i] /= b[3] }
        val dz = b[2] - a[2]
        val t = if (kotlin.math.abs(dz) < 1e-9f) 0f else (planeZ - a[2]) / dz
        out[0] = a[0] + (b[0] - a[0]) * t; out[1] = a[1] + (b[1] - a[1]) * t
        return out
    }

    /** Approximate on-screen pixel size of one board unit at the board centre (for touch tolerances). */
    fun pixelsPerUnit(): Float {
        val p0 = project(0.5f, 0.5f, 0f); val p1 = project(0.6f, 0.5f, 0f)
        return (p1[0] - p0[0]) * 10f
    }
}
