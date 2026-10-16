package app.dvphoto.core

/**
 * DV Lottery photo rules. Keep every number here so a rule change is a one-file edit.
 *
 * Sources (verify before each registration period - the State Department can change them):
 *  - https://travel.state.gov/content/travel/en/us-visas/visa-information-resources/photos.html
 *  - https://dvprogram.state.gov  (entry site, shows the digital image requirements)
 */
object DvRules {
    const val RULES_AS_OF = "2026-10"
    const val OFFICIAL_PHOTO_URL = "https://travel.state.gov/content/travel/en/us-visas/visa-information-resources/photos.html"
    const val DV_ENTRY_URL = "https://dvprogram.state.gov"

    // Digital image (DV entry form)
    const val MIN_SIDE_PX = 600
    const val MAX_SIDE_PX = 1200
    const val MAX_FILE_BYTES = 240 * 1024
    const val OUTPUT_TARGET_SIDE = 1000

    /** Photo must have been taken within the last 6 months (about 183 days). */
    const val MAX_PHOTO_AGE_DAYS = 183

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
    /** Share of the wall allowed to deviate strongly from the smooth lighting plane (posters, shadows, objects). */
    const val BG_OUTLIER_MAX = 0.06f
    /** A step of this many luma levels between neighbouring wall samples is an edge (shadow border, frame, door). */
    const val BG_EDGE_STEP = 30f
    /** Share of neighbouring wall sample pairs allowed to be edges. */
    const val BG_EDGE_MAX = 0.02f
    const val BG_CHROMA_MAX = 28f
    const val MIN_SHARPNESS = 25f

    /**
     * The face contour ends at the hairline; the real top of the head (hair included) is higher.
     * Used only when the top of the hair cannot be measured directly (see [HeadTopFinder]).
     */
    const val HEAD_TOP_EXTENSION = 0.20f
}

/** On-screen target geometry, in fractions of the square frame (y from the top). */
object Guide {
    val eyeY: Float = 1f - DvRules.EYE_TARGET
    val headTop: Float = eyeY - 0.45f * DvRules.HEAD_TARGET
    val chin: Float = eyeY + 0.55f * DvRules.HEAD_TARGET
    val headWidth: Float = DvRules.HEAD_TARGET * 0.72f
}
