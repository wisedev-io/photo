package app.dvphoto.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Minimal pixel access so the same maths runs on camera YUV frames and on Bitmaps. */
interface PixelSource {
    val width: Int
    val height: Int
    fun luma(x: Int, y: Int): Int

    /** max(R,G,B) - min(R,G,B), or -1 when colour is unavailable (live YUV frames). */
    fun chroma(x: Int, y: Int): Int = -1
}

object SceneAnalyzer {
    fun analyze(
        src: PixelSource,
        fx: Int,
        fy: Int,
        side: Int,
        r: FaceReading,
        withSharpness: Boolean,
    ): SceneStats {
        fun px(nx: Float) = (fx + nx * side).toInt().coerceIn(0, src.width - 1)
        fun py(ny: Float) = (fy + ny * side).toInt().coerceIn(0, src.height - 1)
        fun lum(nx: Float, ny: Float) = src.luma(px(nx), py(ny))

        val headH = r.headHeight
        val halfW = r.headWidth * 0.36f
        val top = r.headTop + headH * 0.30f
        val bottom = r.chin - headH * 0.12f

        // Face brightness and left/right balance (cheek-to-cheek shadow detector)
        val cols = 10
        val rows = 8
        var l = 0L; var ln = 0; var rr = 0L; var rn = 0
        for (j in 0 until rows) {
            val ny = top + (bottom - top) * (j + 0.5f) / rows
            for (i in 0 until cols) {
                val nx = r.centerX - halfW + 2 * halfW * (i + 0.5f) / cols
                val v = lum(nx, ny)
                if (i < cols / 2) { l += v; ln++ } else { rr += v; rn++ }
            }
        }
        val lm = l.toFloat() / ln
        val rm = rr.toFloat() / rn
        val faceLuma = (lm + rm) / 2f
        val asym = abs(lm - rm) / max(1f, faceLuma)

        // Background: everything above the shoulders that is not the head
        val cy = (r.headTop + r.chin) / 2f
        val rx = r.headWidth * 0.75f
        val ry = headH * 0.62f
        var n = 0; var sum = 0.0; var sum2 = 0.0; var cn = 0; var csum = 0.0
        for (gy in 0 until 14) {
            val ny = 0.02f + 0.66f * (gy + 0.5f) / 14
            for (gx in 0 until 16) {
                val nx = 0.02f + 0.96f * (gx + 0.5f) / 16
                val dx = (nx - r.centerX) / rx
                val dy = (ny - cy) / ry
                if (dx * dx + dy * dy < 1f) continue
                if (ny > r.chin - 0.02f && abs(nx - r.centerX) < rx * 1.2f) continue // neck
                val v = lum(nx, ny).toDouble()
                n++; sum += v; sum2 += v * v
                val c = src.chroma(px(nx), py(ny))
                if (c >= 0) { cn++; csum += c }
            }
        }
        val bgLuma: Float
        val bgStd: Float
        if (n < 30) { // face fills the frame: background cannot be verified
            bgLuma = 0f; bgStd = 255f
        } else {
            val mean = sum / n
            bgLuma = mean.toFloat()
            bgStd = sqrt(max(0.0, sum2 / n - mean * mean)).toFloat()
        }
        val bgChroma = if (cn > 0) (csum / cn).toFloat() else null

        val sharp = if (withSharpness) sharpness(src, fx, fy, side, r, top, bottom, halfW) else null
        return SceneStats(faceLuma, asym, bgLuma, bgStd, bgChroma, sharp)
    }

    /** Variance of the Laplacian over the face, sampled at a resolution-independent scale. */
    private fun sharpness(src: PixelSource, fx: Int, fy: Int, side: Int, r: FaceReading, top: Float, bottom: Float, halfW: Float): Float {
        val step = max(1, ((r.headWidth * side) / 96f).toInt())
        val x0 = (fx + (r.centerX - halfW) * side).toInt().coerceIn(step, src.width - step - 1)
        val x1 = (fx + (r.centerX + halfW) * side).toInt().coerceIn(step, src.width - step - 1)
        val y0 = (fy + top * side).toInt().coerceIn(step, src.height - step - 1)
        val y1 = (fy + bottom * side).toInt().coerceIn(step, src.height - step - 1)
        var n = 0; var s = 0.0; var s2 = 0.0
        var y = y0
        while (y <= y1) {
            var x = x0
            while (x <= x1) {
                val lap = 4.0 * src.luma(x, y) - src.luma(x - step, y) - src.luma(x + step, y) -
                    src.luma(x, y - step) - src.luma(x, y + step)
                n++; s += lap; s2 += lap * lap
                x += step
            }
            y += step
        }
        if (n == 0) return 0f
        val mean = s / n
        return (s2 / n - mean * mean).toFloat()
    }
}
