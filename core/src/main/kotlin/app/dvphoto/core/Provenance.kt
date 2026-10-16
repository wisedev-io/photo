package app.dvphoto.core

import kotlin.math.roundToInt

/**
 * Turns [MetadataFindings] into checks about filters/edits and the age of the photo.
 * The State Department requires a recent, unedited photo: no filters, beauty apps, retouching or AI.
 *
 * Definite violations (AI, beauty and filter apps, a photo older than the limit) fail at every strictness
 * level. Softer evidence (editing software, portrait mode, edit records, a missing date) is a warning
 * until the level asks for more, see [Thresholds.failOnEditSoftware] and [Thresholds.failOnUnknownDate].
 */
object Provenance {
    /** Days per month used to express a photo's age. */
    private const val DAYS_PER_MONTH = 30.4375f

    fun evaluate(f: MetadataFindings, todayEpochDay: Long, level: Strictness = Strictness.DEFAULT): List<Check> =
        listOf(editing(f, level), age(f, todayEpochDay, level))

    fun editing(f: MetadataFindings, level: Strictness = Strictness.DEFAULT): Check {
        val fl = f.flags
        val soft = if (level.t.failOnEditSoftware) Status.FAIL else Status.WARN
        return when {
            EditFlag.AI_TRAINED in fl || EditFlag.ALGORITHMIC_MEDIA in fl || EditFlag.AI_TOOL in fl ->
                Check(CheckId.EDITING, Status.FAIL, Hint.EDIT_AI)
            EditFlag.BEAUTY_APP in fl -> Check(CheckId.EDITING, Status.FAIL, Hint.EDIT_APP)
            EditFlag.CREDENTIALS_EDITED in fl -> Check(CheckId.EDITING, soft, Hint.EDIT_CREDENTIALS)
            EditFlag.PORTRAIT_MODE in fl -> Check(CheckId.EDITING, soft, Hint.EDIT_PORTRAIT)
            EditFlag.EDITOR_APP in fl -> Check(CheckId.EDITING, soft, Hint.EDIT_SOFTWARE)
            else -> Check(CheckId.EDITING, Status.PASS, Hint.OK)
        }
    }

    fun age(f: MetadataFindings, todayEpochDay: Long, level: Strictness = Strictness.DEFAULT): Check {
        val t = level.t
        val taken = f.takenAt
            ?: return Check(CheckId.PHOTO_AGE, if (t.failOnUnknownDate) Status.FAIL else Status.WARN, Hint.AGE_UNKNOWN)
        val days = todayEpochDay - taken.epochDay
        val months = days / DAYS_PER_MONTH
        return when {
            days < -2 -> Check(CheckId.PHOTO_AGE, Status.WARN, Hint.AGE_FUTURE)
            days > DvRules.MAX_PHOTO_AGE_DAYS -> Check(CheckId.PHOTO_AGE, Status.FAIL, Hint.AGE_OLD, months.roundToInt().toFloat())
            // inside the official limit, but too close to it: the entry may be submitted weeks from now
            days > t.maxPhotoAgeDays -> Check(CheckId.PHOTO_AGE, Status.FAIL, Hint.AGE_NEAR_LIMIT, months)
            else -> Check(CheckId.PHOTO_AGE, Status.PASS, Hint.OK, maxOf(0f, months))
        }
    }
}
