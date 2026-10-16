package app.dvphoto.core

/**
 * The numbers the State Department publishes for DV Lottery photos, plus the geometry the app aims for.
 *
 * Two other places complete the picture:
 *  - [Strictness] holds everything the user can tune (safety margins and the limits for things the State
 *    Department only describes in words, such as pose, light and background).
 *  - [RuleCatalogue] records where every rule comes from, so the app can show its sources honestly.
 *
 * Re-compare this file with the official pages before each registration period and bump [RULES_AS_OF]:
 *  - https://travel.state.gov/content/travel/en/us-visas/visa-information-resources/photos.html
 *  - https://dvprogram.state.gov  (entry site, shows the digital image requirements)
 */
object DvRules {
    /** Month (yyyy-MM) in which these rules were last compared with the official pages. */
    const val RULES_AS_OF = "2026-10"
    const val OFFICIAL_PHOTO_URL = "https://travel.state.gov/content/travel/en/us-visas/visa-information-resources/photos.html"
    const val DV_ENTRY_URL = "https://dvprogram.state.gov"

    // Digital image (official)
    const val MIN_SIDE_PX = 600
    const val MAX_SIDE_PX = 1200
    const val MAX_FILE_BYTES = 240 * 1024

    /**
     * The saved photo is this many pixels square. The Digital Image Requirements page allows 600-1200 px and the
     * Department's own crop tool produces exactly 600 x 600, so 600 is valid whichever wording is current - and it
     * is the lightest file that is always allowed (more JPEG quality per kilobyte, less memory).
     */
    const val OUTPUT_TARGET_SIDE = MIN_SIDE_PX

    /** The photo must have been taken within the last 6 months (about 183 days). */
    const val MAX_PHOTO_AGE_MONTHS = 6
    const val MAX_PHOTO_AGE_DAYS = 183

    // Official bands (fractions of image height)
    const val HEAD_MIN = 0.50f
    const val HEAD_MAX = 0.69f
    const val EYE_MIN = 0.56f // eye line measured from the bottom edge
    const val EYE_MAX = 0.69f

    // Where we aim: the middle of the bands. The strictness level decides how far from it a photo may stray.
    const val HEAD_TARGET = 0.60f
    const val EYE_TARGET = 0.625f

    // Measurement parameters (how the picture is read, not what passes). They do not depend on the strictness level.

    /** A step of this many luma levels between neighbouring wall samples is an edge (shadow border, frame, door). */
    const val BG_EDGE_STEP = 30f

    /** The head-top finder gives up on walls that are less even than this (luma standard deviation after the plane fit). */
    const val HEAD_TOP_MAX_BG_STD = 33f

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
