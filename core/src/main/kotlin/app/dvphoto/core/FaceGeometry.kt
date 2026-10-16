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
