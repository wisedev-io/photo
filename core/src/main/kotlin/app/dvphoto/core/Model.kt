package app.dvphoto.core

enum class Status { PASS, WARN, FAIL }

/** Declaration order is also the order in which problems are shown to the user. */
enum class CheckId {
    FACE, HEAD_SIZE, CENTER, EYE_LINE, POSE, EYES_OPEN, EXPRESSION,
    LIGHTING, BACKGROUND, SHARPNESS, RESOLUTION, FILE
}

enum class Hint {
    OK, NO_FACE, MULTIPLE_FACES, MOVE_CLOSER, MOVE_BACK, MOVE_LEFT, MOVE_RIGHT,
    EYES_LOW, EYES_HIGH, TILT_HEAD, TURNED, CHIN_LEVEL, EYES_CLOSED, SMILING,
    TOO_DARK, TOO_BRIGHT, UNEVEN_LIGHT, NOT_LIGHT, NOT_PLAIN, NOT_NEUTRAL,
    BLURRY, LOW_RES, FILE_TOO_LARGE, FILE_SIZE_RANGE
}

data class Check(val id: CheckId, val status: Status, val hint: Hint)

/** Face measurements normalised to the square frame (0..1, y measured from the top). */
data class FaceReading(
    val faceCount: Int,
    val headTop: Float,
    val chin: Float,
    val eyeY: Float,
    val centerX: Float,
    val headWidth: Float,
    val roll: Float,
    val yaw: Float,
    val pitch: Float,
    val eyesOpen: Float?,
    val smile: Float?,
) {
    val headHeight: Float get() = chin - headTop
    val eyeFromBottom: Float get() = 1f - eyeY
}

data class SceneStats(
    val faceLuma: Float,
    val lightAsymmetry: Float,
    val bgLuma: Float,
    val bgStd: Float,
    val bgChroma: Float?,
    val sharpness: Float?,
)
