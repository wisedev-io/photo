package app.dvphoto.core

enum class Status { PASS, WARN, FAIL }

/** Declaration order is also the order in which problems are shown to the user. */
enum class CheckId {
    FACE, HEAD_SIZE, CENTER, EYE_LINE, POSE, EYES_OPEN, EXPRESSION,
    LIGHTING, BACKGROUND, SHARPNESS, RESOLUTION, EDITING, PHOTO_AGE, FILE
}

enum class Hint {
    OK, NO_FACE, MULTIPLE_FACES, MOVE_CLOSER, MOVE_BACK, MOVE_LEFT, MOVE_RIGHT,
    EYES_LOW, EYES_HIGH, TILT_HEAD, TURNED, CHIN_LEVEL, EYES_CLOSED, SMILING,
    TOO_DARK, TOO_BRIGHT, UNEVEN_LIGHT, NOT_LIGHT, NOT_PLAIN, NOT_NEUTRAL,
    BLURRY, LOW_RES,
    EDIT_AI, EDIT_APP, EDIT_CREDENTIALS, EDIT_PORTRAIT, EDIT_SOFTWARE,
    AGE_OLD, AGE_UNKNOWN, AGE_FUTURE,
    FILE_TOO_LARGE, FILE_SIZE_RANGE
}

/**
 * @param value optional measurement for display: a fraction (0..1) for [CheckId.HEAD_SIZE] and
 *  [CheckId.EYE_LINE], the age in months for [CheckId.PHOTO_AGE].
 */
data class Check(val id: CheckId, val status: Status, val hint: Hint, val value: Float? = null)

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
    /** True when [headTop] was measured on the pixels (hair included) rather than estimated. */
    val headTopMeasured: Boolean = false,
) {
    val headHeight: Float get() = chin - headTop
    val eyeFromBottom: Float get() = 1f - eyeY
}

/**
 * @param bgStd spread of the background brightness AFTER removing a smooth planar lighting gradient, so an evenly
 *  lit wall that is a little brighter on one side is fine, while patterns, edges and shadows are not.
 * @param bgGradX / [bgGradY] brightness change across the frame (luma per full frame width / height) of that plane.
 */
data class SceneStats(
    val faceLuma: Float,
    val lightAsymmetry: Float,
    val bgLuma: Float,
    val bgStd: Float,
    val bgChroma: Float?,
    val sharpness: Float?,
    val bgGradX: Float = 0f,
    val bgGradY: Float = 0f,
    /** Fraction of background samples that deviate strongly from the plane: patches, posters, shadow edges. */
    val bgOutliers: Float = 0f,
    /** Fraction of neighbouring wall sample pairs separated by a sharp step: shadow borders, frames, furniture. */
    val bgEdges: Float = 0f,
)
