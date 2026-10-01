package com.shiostudios.dumplingrings.ui.board

/** Maps level coordinates (unit board) to the drawn board so the ring cluster fills it: x' = 0.5 + (x - cx) * k. */
class BoardFit(val cx: Double, val cy: Double, val k: Float) {
    fun px(x: Double, S: Float, ox: Float) = ox + (0.5f + (x - cx).toFloat() * k) * S
    fun py(y: Double, S: Float, oy: Float) = oy + (1f - (0.5f + (y - cy).toFloat() * k)) * S
    companion object {
        fun of(level: com.shiostudios.dumplingrings.core.model.LevelDefinition): BoardFit {
            var x0 = 1.0; var y0 = 1.0; var x1 = 0.0; var y1 = 0.0
            for (r in level.rings) { val e = r.radius + r.thickness; x0 = minOf(x0, r.center[0] - e); x1 = maxOf(x1, r.center[0] + e); y0 = minOf(y0, r.center[1] - e); y1 = maxOf(y1, r.center[1] + e) }
            for (o in level.obstacles) for (pt in listOf(o.from, o.to, o.center)) if (pt.size == 2) { x0 = minOf(x0, pt[0] - 0.03); x1 = maxOf(x1, pt[0] + 0.03); y0 = minOf(y0, pt[1] - 0.03); y1 = maxOf(y1, pt[1] + 0.03) }
            val w = maxOf(x1 - x0, y1 - y0, 0.2)
            val k = (0.92 / w).coerceIn(1.0, 1.8).toFloat()
            return BoardFit((x0 + x1) / 2, (y0 + y1) / 2, k)
        }
    }
}
