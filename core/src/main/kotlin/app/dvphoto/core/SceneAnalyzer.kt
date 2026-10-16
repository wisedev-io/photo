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

/** Result of [SceneAnalyzer.analyze]: scene statistics plus the reading with the head top measured on the pixels. */
data class SceneResult(val stats: SceneStats, val reading: FaceReading)

object SceneAnalyzer {
    fun analyze(
        src: PixelSource,
        fx: Int,
        fy: Int,
        side: Int,
        r: FaceReading,
        withSharpness: Boolean,
    ): SceneResult {
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
        val xs = ArrayList<Float>(224)
        val ys = ArrayList<Float>(224)
        val vs = ArrayList<Float>(224)
        var cn = 0
        var csum = 0.0
        val grid = Array(14) { FloatArray(16) { Float.NaN } }
        for (gy in 0 until 14) {
            val ny = 0.02f + 0.66f * (gy + 0.5f) / 14
            for (gx in 0 until 16) {
                val nx = 0.02f + 0.96f * (gx + 0.5f) / 16
                val dx = (nx - r.centerX) / rx
                val dy = (ny - cy) / ry
                if (dx * dx + dy * dy < 1f) continue
                if (ny > r.chin - 0.02f && abs(nx - r.centerX) < rx * 1.2f) continue // neck
                val v = lum(nx, ny).toFloat()
                xs += nx; ys += ny; vs += v
                grid[gy][gx] = v
                val c = src.chroma(px(nx), py(ny))
                if (c >= 0) { cn++; csum += c }
            }
        }
        val bgLuma: Float
        val bgStd: Float
        var gradX = 0f
        var gradY = 0f
        var outliers = 0f
        var edges = 0f
        if (vs.size < 30) { // face fills the frame: background cannot be verified
            bgLuma = 0f; bgStd = 255f
        } else {
            val fit = fitPlane(xs, ys, vs)
            bgLuma = fit.a // brightness of the wall at the centre of the frame
            gradX = fit.gx; gradY = fit.gy
            bgStd = fit.residualStd
            outliers = fit.outlierFraction
            edges = edgeFraction(grid)
        }
        val bgChroma = if (cn > 0) (csum / cn).toFloat() else null

        val sharp = if (withSharpness) sharpness(src, fx, fy, side, r, top, bottom, halfW) else null
        val stats = SceneStats(faceLuma, asym, bgLuma, bgStd, bgChroma, sharp, gradX, gradY, outliers, edges)
        val measuredTop = HeadTopFinder.measure(src, fx, fy, side, r, bgLuma, bgStd, gradX, gradY)
        val refined = if (measuredTop != null) r.copy(headTop = measuredTop, headTopMeasured = true) else r
        return SceneResult(stats, refined)
    }

    /** Share of horizontally/vertically adjacent wall samples that differ by a sharp step. */
    fun edgeFraction(grid: Array<FloatArray>): Float {
        var pairs = 0
        var edges = 0
        for (j in grid.indices) for (i in grid[j].indices) {
            val v = grid[j][i]
            if (v.isNaN()) continue
            if (i + 1 < grid[j].size && !grid[j][i + 1].isNaN()) { pairs++; if (abs(grid[j][i + 1] - v) > DvRules.BG_EDGE_STEP) edges++ }
            if (j + 1 < grid.size && !grid[j + 1][i].isNaN()) { pairs++; if (abs(grid[j + 1][i] - v) > DvRules.BG_EDGE_STEP) edges++ }
        }
        return if (pairs == 0) 0f else edges.toFloat() / pairs
    }

    class Plane(val a: Float, val gx: Float, val gy: Float, val residualStd: Float, val outlierFraction: Float)

    /**
     * Least-squares plane v = a + gx*(x-0.5) + gy*(y-0.5) through the samples; returns its tilt and the standard
     * deviation of what is left. Falls back to a flat fit when the samples cannot define a plane.
     */
    fun fitPlane(xs: List<Float>, ys: List<Float>, vs: List<Float>): Plane {
        val n = vs.size
        var sx = 0.0; var sy = 0.0; var sv = 0.0
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0; var sxv = 0.0; var syv = 0.0
        for (i in 0 until n) {
            val x = xs[i] - 0.5; val y = ys[i] - 0.5; val v = vs[i].toDouble()
            sx += x; sy += y; sv += v
            sxx += x * x; syy += y * y; sxy += x * y; sxv += x * v; syv += y * v
        }
        // normal equations for [a, gx, gy]
        val m = arrayOf(
            doubleArrayOf(n.toDouble(), sx, sy, sv),
            doubleArrayOf(sx, sxx, sxy, sxv),
            doubleArrayOf(sy, sxy, syy, syv),
        )
        var a = sv / n; var gx = 0.0; var gy = 0.0
        if (solve3(m)) { a = m[0][3]; gx = m[1][3]; gy = m[2][3] }
        var ss = 0.0
        val residuals = DoubleArray(n)
        for (i in 0 until n) {
            val e = vs[i] - (a + gx * (xs[i] - 0.5) + gy * (ys[i] - 0.5))
            residuals[i] = e
            ss += e * e
        }
        // robust spread (median absolute deviation) so that camera noise alone never counts as an outlier
        val sorted = residuals.map { abs(it) }.sorted()
        val mad = sorted[n / 2]
        val limit = max(30.0, 4.0 * 1.4826 * mad)
        val outliers = residuals.count { abs(it) > limit }
        return Plane(a.toFloat(), gx.toFloat(), gy.toFloat(), sqrt(ss / n).toFloat(), outliers.toFloat() / n)
    }

    /** Gauss-Jordan on a 3x4 augmented matrix (in place); false when singular. */
    private fun solve3(m: Array<DoubleArray>): Boolean {
        for (c in 0 until 3) {
            var p = c
            for (r in c + 1 until 3) if (abs(m[r][c]) > abs(m[p][c])) p = r
            if (abs(m[p][c]) < 1e-9) return false
            val t = m[c]; m[c] = m[p]; m[p] = t
            val d = m[c][c]
            for (k in c until 4) m[c][k] /= d
            for (r in 0 until 3) {
                if (r == c) continue
                val f = m[r][c]
                for (k in c until 4) m[r][k] -= f * m[c][k]
            }
        }
        return true
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
