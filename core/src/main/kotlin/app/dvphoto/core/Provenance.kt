package app.dvphoto.core

import kotlin.math.roundToInt

/**
 * Turns [MetadataFindings] into checks about filters/edits and the age of the photo.
 * The State Department requires a recent, unedited photo: no filters, beauty apps, retouching or AI.
 */
object Provenance {
    /** Days per month used to express a photo's age. */
    private const val DAYS_PER_MONTH = 30.4375f

    fun evaluate(f: MetadataFindings, todayEpochDay: Long): List<Check> = listOf(editing(f), age(f, todayEpochDay))

    fun editing(f: MetadataFindings): Check {
        val fl = f.flags
        return when {
            EditFlag.AI_TRAINED in fl || EditFlag.ALGORITHMIC_MEDIA in fl || EditFlag.AI_TOOL in fl ->
                Check(CheckId.EDITING, Status.FAIL, Hint.EDIT_AI)
            EditFlag.BEAUTY_APP in fl -> Check(CheckId.EDITING, Status.FAIL, Hint.EDIT_APP)
            EditFlag.CREDENTIALS_EDITED in fl -> Check(CheckId.EDITING, Status.WARN, Hint.EDIT_CREDENTIALS)
            EditFlag.PORTRAIT_MODE in fl -> Check(CheckId.EDITING, Status.WARN, Hint.EDIT_PORTRAIT)
            EditFlag.EDITOR_APP in fl -> Check(CheckId.EDITING, Status.WARN, Hint.EDIT_SOFTWARE)
            else -> Check(CheckId.EDITING, Status.PASS, Hint.OK)
        }
    }

    fun age(f: MetadataFindings, todayEpochDay: Long): Check {
        val taken = f.takenAt ?: return Check(CheckId.PHOTO_AGE, Status.WARN, Hint.AGE_UNKNOWN)
        val days = todayEpochDay - taken.epochDay
        val months = days / DAYS_PER_MONTH
        return when {
            days < -2 -> Check(CheckId.PHOTO_AGE, Status.WARN, Hint.AGE_FUTURE)
            days > DvRules.MAX_PHOTO_AGE_DAYS -> Check(CheckId.PHOTO_AGE, Status.FAIL, Hint.AGE_OLD, months.roundToInt().toFloat())
            else -> Check(CheckId.PHOTO_AGE, Status.PASS, Hint.OK, maxOf(0f, months))
        }
    }
}
