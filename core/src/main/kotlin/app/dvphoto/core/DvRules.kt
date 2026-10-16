package app.dvphoto.core

/**
 * DV Lottery photo rules. Keep every number here so a rule change is a one-file edit.
 * Source: U.S. Department of State DV photo requirements - re-verify before each registration period.
 */
object DvRules {
    const val MIN_SIDE_PX = 600
    const val MAX_SIDE_PX = 1200
    const val MAX_FILE_BYTES = 240 * 1024
    const val OUTPUT_TARGET_SIDE = 1000

    // Official bands (fractions of image height)
    const val HEAD_MIN = 0.50f
    const val HEAD_MAX = 0.69f
    const val EYE_MIN = 0.56f // eye line measured from the bottom edge
    const val EYE_MAX = 0.69f

    // Where we aim, and how far inside the official band we insist on being
    const val HEAD_TARGET = 0.60f
    const val EYE_TARGET = 0.625f
    const val MARGIN = 0.02f

    // Pose / expression
    const val MAX_ROLL = 5f
    const val MAX_YAW = 8f
    const val MAX_PITCH = 10f
    const val MIN_EYES_OPEN = 0.6f
    const val MAX_SMILE = 0.5f
    const val MAX_OFF_CENTER = 0.05f

    // Light / background / sharpness (luma 0..255). Tunable on real devices.
    const val FACE_LUMA_MIN = 55f
    const val FACE_LUMA_MAX = 215f
    const val MAX_LIGHT_ASYMMETRY = 0.20f
    const val BG_LUMA_MIN = 170f
    const val BG_STD_MAX = 22f
    const val BG_CHROMA_MAX = 28f
    const val MIN_SHARPNESS = 25f

    /** Face-contour top sits at the hairline; the real "top of head" is a bit higher. */
    const val HEAD_TOP_EXTENSION = 0.15f
}

/** On-screen target geometry, in fractions of the square frame (y from the top). */
object Guide {
    val eyeY: Float = 1f - DvRules.EYE_TARGET
    val headTop: Float = eyeY - 0.45f * DvRules.HEAD_TARGET
    val chin: Float = eyeY + 0.55f * DvRules.HEAD_TARGET
    val headWidth: Float = DvRules.HEAD_TARGET * 0.72f
}
