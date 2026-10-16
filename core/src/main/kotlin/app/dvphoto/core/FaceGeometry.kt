package app.dvphoto.core

/** Detector output in absolute pixels of the analysed image. */
data class RawFace(
    val ovalTop: Float,
    val chin: Float,
    val minX: Float,
    val maxX: Float,
    val eyeLeftX: Float,
    val eyeLeftY: Float,
    val eyeRightX: Float,
    val eyeRightY: Float,
    val roll: Float,
    val yaw: Float,
    val pitch: Float,
    val eyesOpen: Float?,
    val smile: Float?,
)

object FaceGeometry {
    fun headTopPx(f: RawFace): Float = f.ovalTop - DvRules.HEAD_TOP_EXTENSION * (f.chin - f.ovalTop)
    fun eyeYPx(f: RawFace): Float = (f.eyeLeftY + f.eyeRightY) / 2f
    fun centerXPx(f: RawFace): Float = (f.minX + f.maxX) / 2f

    /** Normalises [f] to the square frame whose top-left is (fx, fy) and whose side is [side] pixels. */
    fun reading(f: RawFace, fx: Float, fy: Float, side: Float, faceCount: Int): FaceReading =
        FaceReading(
            faceCount = faceCount,
            headTop = (headTopPx(f) - fy) / side,
            chin = (f.chin - fy) / side,
            eyeY = (eyeYPx(f) - fy) / side,
            centerX = (centerXPx(f) - fx) / side,
            headWidth = (f.maxX - f.minX) / side,
            roll = f.roll,
            yaw = f.yaw,
            pitch = f.pitch,
            eyesOpen = f.eyesOpen,
            smile = f.smile,
        )
}

/** Integer rectangle in pixel space (right/bottom exclusive). */
data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

object FrameGeometry {
    /**
     * Rotates [r], given in the coordinates of a raw sensor buffer of [w] x [h] pixels, into the coordinates of
     * the upright image obtained by rotating that buffer clockwise by [rotationDegrees].
     */
    fun rotateRect(r: PxRect, w: Int, h: Int, rotationDegrees: Int): PxRect = when (((rotationDegrees % 360) + 360) % 360) {
        90 -> PxRect(h - r.bottom, r.left, h - r.top, r.right)
        180 -> PxRect(w - r.right, h - r.bottom, w - r.left, h - r.top)
        270 -> PxRect(r.top, w - r.right, r.bottom, w - r.left)
        else -> r
    }

    /** Largest centred square inside [r]. */
    fun centeredSquare(r: PxRect): PxRect {
        val s = minOf(r.width, r.height)
        val l = r.left + (r.width - s) / 2
        val t = r.top + (r.height - s) / 2
        return PxRect(l, t, l + s, t + s)
    }
}
