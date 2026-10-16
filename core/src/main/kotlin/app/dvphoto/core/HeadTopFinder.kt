package app.dvphoto.core

import kotlin.math.abs
import kotlin.math.max

/**
 * Measures where the top of the head (hair included, as the State Department measures it) really is,
 * by walking up from the forehead until the plain background starts. Needs the plain light background
 * the rules ask for anyway; returns null whenever it cannot be sure so callers fall back to an estimate.
 */
object HeadTopFinder {
    private const val COLUMNS = 9
    private const val COLUMN_SPACING = 0.022f
    private const val BG_RUN = 4
    private const val MIN_RISE = 0.06f

    /**
     * @return top of the head as a fraction of the frame height (0 = top edge), or null.
     */
    fun measure(
        src: PixelSource, fx: Int, fy: Int, side: Int, r: FaceReading,
        bgLuma: Float, bgStd: Float, gradX: Float = 0f, gradY: Float = 0f,
    ): Float? {
        if (bgLuma < 150f || bgStd > DvRules.HEAD_TOP_MAX_BG_STD) return null
        val faceH = r.headHeight / (1f + DvRules.HEAD_TOP_EXTENSION)
        if (faceH <= 0.05f) return null
        val ovalTop = r.chin - faceH
        val start = ovalTop + 0.06f * faceH
        val limit = max(0.0f, ovalTop - 0.45f * faceH)
        val thresh = max(28f, 4f * bgStd)
        val step = max(1, side / 160).toFloat() / side

        fun lum(nx: Float, ny: Float): Float {
            val x = (fx + nx * side).toInt().coerceIn(0, src.width - 1)
            val y = (fy + ny * side).toInt().coerceIn(0, src.height - 1)
            return src.luma(x, y).toFloat()
        }

        // expected wall brightness at a point, following the lighting gradient of the background plane
        fun wall(nx: Float, ny: Float) = bgLuma + gradX * (nx - 0.5f) + gradY * (ny - 0.5f)

        val tops = ArrayList<Float>(COLUMNS)
        for (k in -(COLUMNS / 2)..(COLUMNS / 2)) {
            val nx = r.centerX + k * COLUMN_SPACING
            if (nx < 0.02f || nx > 0.98f) continue
            var y = start
            var lastNonBg = start
            var seenNonBg = false
            var run = 0
            var found: Float? = null
            while (y > limit) {
                val isBg = abs(lum(nx, y) - wall(nx, y)) <= thresh
                if (isBg) {
                    run++
                    if (run >= BG_RUN && seenNonBg) { found = lastNonBg; break }
                } else {
                    run = 0
                    seenNonBg = true
                    lastNonBg = y
                }
                y -= step
            }
            if (found != null) tops += found
        }
        if (tops.size < 5) return null
        tops.sort()
        val top = tops[1] // second highest: ignore a single stray strand or spike
        // The skull (plus hair) always rises clearly above the forehead contour. If the boundary found is
        // barely above it, the hair is as light as the wall (or the head is bald) and the scan only saw the
        // forehead: report "unknown" so the caller uses the anthropometric estimate instead.
        if (top > ovalTop - MIN_RISE * faceH) return null
        return top
    }
}
