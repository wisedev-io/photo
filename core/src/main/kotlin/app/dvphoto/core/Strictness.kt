package app.dvphoto.core

/**
 * Every threshold a photo is judged by at one strictness level.
 *
 * Official limits (600-1200 px, 240 KB, JPEG, one face, taken within 6 months) never change with the level.
 * What changes is
 *  - [margin]: how far inside the official head-size and eye-line bands the photo has to be,
 *  - the limits for things the State Department only describes in words (pose, expression, light, background,
 *    sharpness), which are this app's own measurable stand-ins,
 *  - how the authenticity checks treat editing software and photos without a date,
 *  - how patiently auto-capture waits for a steady face.
 *
 * Directions: "max" limits get tighter (smaller) with strictness, "min" limits get higher.
 */
class Thresholds(
    /** Distance kept inside the official head-size and eye-line bands, as a fraction of the image height. */
    val margin: Float,
    val maxRoll: Float,
    val maxYaw: Float,
    val maxPitch: Float,
    /** Minimum eye-open probability reported by the face detector. */
    val minEyesOpen: Float,
    /** Smile probability above which the app warns (it never fails a photo for a smile). */
    val maxSmile: Float,
    /** Largest distance of the face centre from the middle of the frame (fraction of the width). */
    val maxOffCenter: Float,
    val faceLumaMin: Float,
    val faceLumaMax: Float,
    val maxLightAsymmetry: Float,
    val bgLumaMin: Float,
    val bgStdMax: Float,
    /** Share of the wall allowed to deviate strongly from the smooth lighting plane (posters, shadows, objects). */
    val bgOutlierMax: Float,
    /** Share of neighbouring wall sample pairs allowed to be edges. */
    val bgEdgeMax: Float,
    val bgChromaMax: Float,
    val minSharpness: Float,
    /** Oldest acceptable photo, in days. Never above [DvRules.MAX_PHOTO_AGE_DAYS]. */
    val maxPhotoAgeDays: Int,
    /** A photo whose file has no date fails instead of only warning. */
    val failOnUnknownDate: Boolean,
    /** Edit records, portrait mode and editing software fail instead of only warning. AI and beauty apps always fail. */
    val failOnEditSoftware: Boolean,
    /** How long the face must stay put before auto-capture fires. */
    val holdMs: Long,
    /** How little the face may move (fraction of the frame) during that time. */
    val steadyTolerance: Float,
) {
    // The bands this level asks for: the official ones, pulled in by [margin].
    val headMin: Float get() = DvRules.HEAD_MIN + margin
    val headMax: Float get() = DvRules.HEAD_MAX - margin
    val eyeMin: Float get() = DvRules.EYE_MIN + margin
    val eyeMax: Float get() = DvRules.EYE_MAX - margin
}

/** How closely a photo has to match the rules before the app calls it good. */
enum class Strictness(val id: String, val t: Thresholds) {
    /** Exactly the official numbers, with loose limits wherever the State Department gives none. */
    RELAXED(
        "relaxed",
        Thresholds(
            margin = 0f, maxRoll = 8f, maxYaw = 12f, maxPitch = 15f,
            minEyesOpen = 0.40f, maxSmile = 0.80f, maxOffCenter = 0.08f,
            faceLumaMin = 40f, faceLumaMax = 230f, maxLightAsymmetry = 0.30f,
            bgLumaMin = 150f, bgStdMax = 30f, bgOutlierMax = 0.10f, bgEdgeMax = 0.04f, bgChromaMax = 36f,
            minSharpness = 18f,
            maxPhotoAgeDays = DvRules.MAX_PHOTO_AGE_DAYS, failOnUnknownDate = false, failOnEditSoftware = false,
            holdMs = 800L, steadyTolerance = 0.03f,
        ),
    ),

    /** The recommended level: a small safety margin inside the official numbers and sensible limits elsewhere. */
    STANDARD(
        "standard",
        Thresholds(
            margin = 0.02f, maxRoll = 5f, maxYaw = 8f, maxPitch = 10f,
            minEyesOpen = 0.60f, maxSmile = 0.50f, maxOffCenter = 0.05f,
            faceLumaMin = 55f, faceLumaMax = 215f, maxLightAsymmetry = 0.20f,
            bgLumaMin = 170f, bgStdMax = 22f, bgOutlierMax = 0.06f, bgEdgeMax = 0.02f, bgChromaMax = 28f,
            minSharpness = 25f,
            maxPhotoAgeDays = DvRules.MAX_PHOTO_AGE_DAYS, failOnUnknownDate = false, failOnEditSoftware = false,
            holdMs = 1200L, steadyTolerance = 0.02f,
        ),
    ),

    /** Best odds with a reviewer: a wide safety margin, tight limits and no benefit of the doubt for edits or dates. */
    STRICT(
        "strict",
        Thresholds(
            margin = 0.03f, maxRoll = 3f, maxYaw = 5f, maxPitch = 7f,
            minEyesOpen = 0.75f, maxSmile = 0.30f, maxOffCenter = 0.03f,
            faceLumaMin = 70f, faceLumaMax = 200f, maxLightAsymmetry = 0.12f,
            bgLumaMin = 185f, bgStdMax = 16f, bgOutlierMax = 0.03f, bgEdgeMax = 0.01f, bgChromaMax = 22f,
            minSharpness = 35f,
            maxPhotoAgeDays = 150, failOnUnknownDate = true, failOnEditSoftware = true,
            holdMs = 1600L, steadyTolerance = 0.012f,
        ),
    );

    /** The next level in the order relaxed → standard → strict → relaxed (used by the one-tap switch on the camera). */
    fun next(): Strictness = values()[(ordinal + 1) % values().size]

    companion object {
        val DEFAULT: Strictness = STANDARD

        /** The level stored under [id]; the default when nothing (or something unknown) is stored. */
        fun fromId(id: String?): Strictness = values().firstOrNull { it.id == id } ?: DEFAULT
    }
}
