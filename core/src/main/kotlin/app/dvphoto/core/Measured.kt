package app.dvphoto.core

/**
 * Everything measured about one finished picture, kept apart from the verdict. Nothing in here depends on
 * the strictness level, so the same measurements can be judged again at any level without analysing the
 * picture a second time ("how would this photo do at Strict?").
 */
class Measured(
    val reading: FaceReading?,
    val scene: SceneStats?,
    /** Side of the analysed square, in pixels. */
    val sidePx: Int,
    /** Size and side of the exact JPEG that would be saved. */
    val fileBytes: Int,
    val fileSidePx: Int,
    /** What the file says about itself; null for pictures taken with this app's own camera (never edited). */
    val findings: MetadataFindings?,
    val todayEpochDay: Long,
) {
    /** Every check, judged at [level]. Order: picture checks, then authenticity checks, then the file. */
    fun checks(level: Strictness): List<Check> {
        val authenticity = if (findings == null) {
            listOf(Check(CheckId.EDITING, Status.PASS, Hint.OK))
        } else {
            Provenance.evaluate(findings, todayEpochDay, level)
        }
        return Validator.validate(reading, scene, sidePx, mirrored = false, level = level) +
            authenticity + Validator.fileCheck(fileBytes, fileSidePx)
    }

    /** How the picture does at every level, for the "passes at Relaxed but not at Standard" display. */
    fun verdicts(): List<Verdict> = Strictness.values().map { Verdict.of(it, checks(it)) }
}

/** Summary of one level's checks. */
class Verdict(val level: Strictness, val failures: Int, val warnings: Int) {
    val ok: Boolean get() = failures == 0

    companion object {
        fun of(level: Strictness, checks: List<Check>) =
            Verdict(level, checks.count { it.status == Status.FAIL }, checks.count { it.status == Status.WARN })
    }
}
