package app.dvphoto.core

/** How the State Department states a rule. */
enum class Origin {
    /** The State Department prints the number. The app enforces it, plus the safety margin of the strictness level. */
    OFFICIAL_NUMBER,

    /**
     * The State Department requires it in words only ("plain background", "looking straight at the camera").
     * The app turns the words into measurements with limits of its own, so a pass is an estimate, not a ruling.
     */
    OFFICIAL_WORDS,
}

/** An official page a rule is taken from. [title] is the English name of that page. */
enum class Source(val title: String, val url: String) {
    PHOTO_REQUIREMENTS("Photo Requirements", DvRules.OFFICIAL_PHOTO_URL),
    COMPOSITION_TEMPLATE(
        "Photo Composition Template",
        "https://travel.state.gov/content/travel/en/us-visas/visa-information-resources/photos/photo-composition-template.html",
    ),
    DIGITAL_IMAGE(
        "Digital Image Requirements",
        "https://travel.state.gov/content/travel/en/us-visas/visa-information-resources/photos/digital-image-requirements.html",
    ),
    PHOTO_FAQ(
        "Photo FAQ",
        "https://travel.state.gov/content/travel/en/us-visas/visa-information-resources/photos/frequently-asked-questions.html",
    ),

    /** Lighting and exposure are spelled out for passport photos; the visa pages are less detailed. */
    PASSPORT_PHOTOS("Passport Photos", "https://travel.state.gov/en/passports/apply/help/photos.html"),
    DV_ENTRY("DV entry website", DvRules.DV_ENTRY_URL),
}

/** The printed official limit of a numeric rule; null on a side that has no limit. */
class OfficialLimit(val min: Float?, val max: Float?)

/** Where one check's rule comes from. */
class RuleInfo(val check: CheckId, val origin: Origin, val source: Source, val official: OfficialLimit? = null)

/**
 * Where every rule the app enforces comes from, so the app can say honestly which limits are the State
 * Department's and which are its own estimates. The numbers reference [DvRules]: there is one copy of each.
 *
 * When the official pages change, update [DvRules], this list and [DvRules.RULES_AS_OF] together;
 * `docs/RULES.md` records how each entry was verified.
 */
object RuleCatalogue {
    /** Months after [DvRules.RULES_AS_OF] at which the app starts warning that the rules may have changed. */
    const val STALE_AFTER_MONTHS = 10

    private const val DAYS_PER_MONTH = 30.4375

    val all: List<RuleInfo> = listOf(
        RuleInfo(CheckId.FACE, Origin.OFFICIAL_WORDS, Source.DV_ENTRY),
        RuleInfo(CheckId.HEAD_SIZE, Origin.OFFICIAL_NUMBER, Source.COMPOSITION_TEMPLATE, OfficialLimit(DvRules.HEAD_MIN, DvRules.HEAD_MAX)),
        RuleInfo(CheckId.CENTER, Origin.OFFICIAL_WORDS, Source.PHOTO_FAQ),
        RuleInfo(CheckId.EYE_LINE, Origin.OFFICIAL_NUMBER, Source.COMPOSITION_TEMPLATE, OfficialLimit(DvRules.EYE_MIN, DvRules.EYE_MAX)),
        RuleInfo(CheckId.POSE, Origin.OFFICIAL_WORDS, Source.PHOTO_REQUIREMENTS),
        RuleInfo(CheckId.EYES_OPEN, Origin.OFFICIAL_WORDS, Source.PHOTO_REQUIREMENTS),
        RuleInfo(CheckId.EXPRESSION, Origin.OFFICIAL_WORDS, Source.PHOTO_REQUIREMENTS),
        RuleInfo(CheckId.LIGHTING, Origin.OFFICIAL_WORDS, Source.PASSPORT_PHOTOS),
        RuleInfo(CheckId.BACKGROUND, Origin.OFFICIAL_WORDS, Source.PHOTO_REQUIREMENTS),
        RuleInfo(CheckId.SHARPNESS, Origin.OFFICIAL_WORDS, Source.PHOTO_FAQ),
        RuleInfo(
            CheckId.RESOLUTION, Origin.OFFICIAL_NUMBER, Source.DIGITAL_IMAGE,
            OfficialLimit(DvRules.MIN_SIDE_PX.toFloat(), DvRules.MAX_SIDE_PX.toFloat()),
        ),
        RuleInfo(CheckId.EDITING, Origin.OFFICIAL_WORDS, Source.PHOTO_REQUIREMENTS),
        RuleInfo(CheckId.PHOTO_AGE, Origin.OFFICIAL_NUMBER, Source.PHOTO_REQUIREMENTS, OfficialLimit(null, DvRules.MAX_PHOTO_AGE_MONTHS.toFloat())),
        RuleInfo(CheckId.FILE, Origin.OFFICIAL_NUMBER, Source.DIGITAL_IMAGE, OfficialLimit(null, DvRules.MAX_FILE_BYTES / 1024f)),
    )

    private val byCheck: Map<CheckId, RuleInfo> = all.associateBy { it.check }

    fun of(check: CheckId): RuleInfo = byCheck.getValue(check)

    private val checkedEpochDay: Long = run {
        val (year, month) = DvRules.RULES_AS_OF.split('-').map { it.toInt() }
        Dates.daysFromCivil(year, month, 1)
    }

    /** Whole months since the rules were last compared with the official pages (negative if the phone's clock is behind). */
    fun monthsSinceChecked(todayEpochDay: Long): Int = Math.floor((todayEpochDay - checkedEpochDay) / DAYS_PER_MONTH).toInt()

    /** The age in months when the rules are old enough that they may have changed, otherwise null. */
    fun staleMonths(todayEpochDay: Long): Int? = monthsSinceChecked(todayEpochDay).takeIf { it > STALE_AFTER_MONTHS }
}
