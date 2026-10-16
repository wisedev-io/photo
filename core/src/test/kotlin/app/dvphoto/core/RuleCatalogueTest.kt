package app.dvphoto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleCatalogueTest {
    @Test fun everyCheckHasExactlyOneRuleWithASource() {
        assertEquals(CheckId.values().toList(), RuleCatalogue.all.map { it.check }.sortedBy { it.ordinal })
        assertEquals(CheckId.values().size, RuleCatalogue.all.map { it.check }.toSet().size)
        for (id in CheckId.values()) assertEquals(id, RuleCatalogue.of(id).check)
    }

    /**
     * The figures below are the ones the State Department publishes as of [DvRules.RULES_AS_OF]
     * (see docs/RULES.md). Changing a number in [DvRules] without re-checking the official page makes this fail on purpose.
     */
    @Test fun officialNumbersAreThePublishedOnes() {
        fun limit(id: CheckId) = RuleCatalogue.of(id).official!!
        assertEquals(0.50f, limit(CheckId.HEAD_SIZE).min!!, 0f)
        assertEquals(0.69f, limit(CheckId.HEAD_SIZE).max!!, 0f)
        assertEquals(0.56f, limit(CheckId.EYE_LINE).min!!, 0f)
        assertEquals(0.69f, limit(CheckId.EYE_LINE).max!!, 0f)
        assertEquals(600f, limit(CheckId.RESOLUTION).min!!, 0f)
        assertEquals(1200f, limit(CheckId.RESOLUTION).max!!, 0f)
        assertNull(limit(CheckId.FILE).min)
        assertEquals(240f, limit(CheckId.FILE).max!!, 0f)
        assertNull(limit(CheckId.PHOTO_AGE).min)
        assertEquals(6f, limit(CheckId.PHOTO_AGE).max!!, 0f)
        assertEquals(183, DvRules.MAX_PHOTO_AGE_DAYS) // six months, rounded up to whole days
        assertEquals(240 * 1024, DvRules.MAX_FILE_BYTES)
    }

    @Test fun onlyNumericRulesCarryAnOfficialLimit() {
        for (rule in RuleCatalogue.all) {
            when (rule.origin) {
                Origin.OFFICIAL_NUMBER -> assertNotNull("${rule.check} needs its printed limit", rule.official)
                Origin.OFFICIAL_WORDS -> assertNull("${rule.check} has no printed number to show", rule.official)
            }
        }
        // soft-measurement checks must not pretend to be official numbers
        val words = RuleCatalogue.all.filter { it.origin == Origin.OFFICIAL_WORDS }.map { it.check }.toSet()
        for (id in listOf(CheckId.POSE, CheckId.EYES_OPEN, CheckId.EXPRESSION, CheckId.LIGHTING, CheckId.BACKGROUND, CheckId.SHARPNESS, CheckId.EDITING, CheckId.CENTER)) {
            assertTrue("$id is only described in words", id in words)
        }
    }

    @Test fun thePlainPassOfEachNumericRuleMatchesWhatTheValidatorEnforcesWhenRelaxed() {
        val relaxed = Strictness.RELAXED.t
        val head = RuleCatalogue.of(CheckId.HEAD_SIZE).official!!
        assertEquals(head.min!!, relaxed.headMin, 0f)
        assertEquals(head.max!!, relaxed.headMax, 0f)
        val eye = RuleCatalogue.of(CheckId.EYE_LINE).official!!
        assertEquals(eye.min!!, relaxed.eyeMin, 0f)
        assertEquals(eye.max!!, relaxed.eyeMax, 0f)
        val size = RuleCatalogue.of(CheckId.RESOLUTION).official!!
        assertEquals(size.min!!.toInt(), DvRules.MIN_SIDE_PX)
        assertEquals(size.max!!.toInt(), DvRules.MAX_SIDE_PX)
        assertEquals(RuleCatalogue.of(CheckId.PHOTO_AGE).official!!.max!!.toInt(), DvRules.MAX_PHOTO_AGE_MONTHS)
    }

    @Test fun sourcesAreOfficialStateDepartmentPages() {
        val hosts = setOf("travel.state.gov", "fam.state.gov", "dvprogram.state.gov")
        for (source in Source.values()) {
            assertTrue("${source.name} must be https", source.url.startsWith("https://"))
            val host = source.url.removePrefix("https://").substringBefore('/')
            assertTrue("${source.name} host $host", host in hosts)
            assertTrue("${source.name} has no spaces", source.url.none { it.isWhitespace() })
            assertTrue(source.title.isNotBlank())
        }
        assertEquals(Source.values().size, Source.values().map { it.title }.toSet().size)
        assertEquals(DvRules.OFFICIAL_PHOTO_URL, Source.PHOTO_REQUIREMENTS.url)
        // every check's source is one of the declared pages
        assertTrue(RuleCatalogue.all.all { it.source in Source.values() })
    }

    @Test fun everyCheckTheValidatorCanProduceIsCatalogued() {
        val r = FaceReading(1, Guide.headTop, Guide.chin, Guide.eyeY, 0.5f, 0.43f, 0f, 0f, 0f, 0.95f, 0.05f)
        val m = Measured(r, SceneStats(140f, 0.05f, 225f, 5f, 6f, 90f), 1000, 180_000, 1000, MetadataFindings(ImageFormat.JPEG), 0L)
        val produced = Strictness.values().flatMap { m.checks(it) }.map { it.id }.toSet()
        assertEquals(CheckId.values().toSet(), produced)
        assertTrue(produced.all { id -> RuleCatalogue.all.any { it.check == id } })
    }

    // --- staleness -------------------------------------------------------------------------

    private fun day(y: Int, m: Int, d: Int) = Dates.daysFromCivil(y, m, d)

    @Test fun rulesDateIsWellFormed() {
        assertTrue(DvRules.RULES_AS_OF, Regex("""\d{4}-(0[1-9]|1[0-2])""").matches(DvRules.RULES_AS_OF))
        val (y, m) = DvRules.RULES_AS_OF.split('-').map { it.toInt() }
        assertEquals(0, RuleCatalogue.monthsSinceChecked(day(y, m, 15)))
    }

    @Test fun rulesAreFreshForTheNextRegistrationAndStaleAfterwards() {
        val (y, m) = DvRules.RULES_AS_OF.split('-').map { it.toInt() }
        fun months(n: Int): Long { // first day of the month n months after the check
            val total = y * 12 + (m - 1) + n
            return day(total / 12, total % 12 + 1, 1)
        }
        assertNull(RuleCatalogue.staleMonths(months(0)))
        // exactly STALE_AFTER_MONTHS months old is still fine; one more month is not
        val atLimit = months(RuleCatalogue.STALE_AFTER_MONTHS) + 5
        assertEquals(RuleCatalogue.STALE_AFTER_MONTHS, RuleCatalogue.monthsSinceChecked(atLimit))
        assertNull(RuleCatalogue.staleMonths(atLimit))
        val stale = RuleCatalogue.staleMonths(months(RuleCatalogue.STALE_AFTER_MONTHS + 1) + 2)
        assertNotNull(stale)
        assertEquals(RuleCatalogue.STALE_AFTER_MONTHS + 1, stale)
        assertEquals(24, RuleCatalogue.staleMonths(months(24) + 3))
        // a phone clock set before the check never produces a warning
        assertNull(RuleCatalogue.staleMonths(day(2020, 1, 1)))
        assertTrue(RuleCatalogue.monthsSinceChecked(day(2020, 1, 1)) < 0)
    }
}
