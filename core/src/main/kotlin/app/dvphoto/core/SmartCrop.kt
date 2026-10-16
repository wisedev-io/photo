package app.dvphoto.core

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

data class SquareCrop(val left: Int, val top: Int, val side: Int, val clamped: Boolean)

object SmartCrop {
    /**
     * Chooses the square that puts the head at [DvRules.HEAD_TARGET] of the height and the eyes on
     * [DvRules.EYE_TARGET] from the bottom. If the picture is too tight to allow that, the square is
     * clamped inside the image and [SquareCrop.clamped] is true - the validator then judges the result.
     */
    fun forFace(imgW: Int, imgH: Int, f: RawFace): SquareCrop {
        val headH = f.chin - FaceGeometry.headTopPx(f)
        val ideal = headH / DvRules.HEAD_TARGET
        val side = min(ideal, min(imgW, imgH).toFloat())
        var left = FaceGeometry.centerXPx(f) - side / 2f
        var top = FaceGeometry.eyeYPx(f) - (1f - DvRules.EYE_TARGET) * side
        val cl = left.coerceIn(0f, imgW - side)
        val ct = top.coerceIn(0f, imgH - side)
        val clamped = side < ideal - 1f || abs(cl - left) > 1f || abs(ct - top) > 1f
        left = cl; top = ct
        return SquareCrop(left.roundToInt(), top.roundToInt(), side.roundToInt(), clamped)
    }

    fun centered(imgW: Int, imgH: Int): SquareCrop {
        val s = min(imgW, imgH)
        return SquareCrop((imgW - s) / 2, (imgH - s) / 2, s, false)
    }
}
