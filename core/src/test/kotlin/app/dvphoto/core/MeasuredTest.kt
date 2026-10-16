package app.dvphoto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasuredTest {
    private val today = CivilDate(2026, 10, 16).epochDay
    private val scene = SceneStats(140f, 0.05f, 225f, 5f, 6f, 90f)

    private fun reading(head: Float = 0.60f, roll: Float = 0f) =
        FaceReading(1, 0.15f, 0.15f + head, 0.375f, 0.5f, 0.43f, roll, 0f, 0f, 0.95f, 0.05f)

    private fun measured(
        r: FaceReading? = reading(),
        findings: MetadataFindings? = null,
        fileBytes: Int = 180_000,
        fileSide: Int = 1000,
    ) = Measured(r, scene, 1000, fileBytes, fileSide, findings, today)

    @Test fun picturesFromTheCameraAreNeverFlaggedAsEdited() {
        val m = measured(findings = null)
        for (level in Strictness.values()) {
            val checks = m.checks(level)
            assertEquals(Status.PASS, checks.first { it.id == CheckId.EDITING }.status)
            assertTrue("a camera picture has no age to check", checks.none { it.id == CheckId.PHOTO_AGE })
            assertTrue(checks.all { it.status == Status.PASS })
        }
    }

    @Test fun orderIsPictureThenAuthenticityThenFile() {
        val m = measured(findings = MetadataFindings(ImageFormat.JPEG, takenAt = CivilDate(2026, 9, 1)))
        val ids = m.checks(Strictness.STANDARD).map { it.id }
        assertEquals(CheckId.EDITING, ids[ids.size - 3])
        assertEquals(CheckId.PHOTO_AGE, ids[ids.size - 2])
        assertEquals(CheckId.FILE, ids.last())
        assertEquals(CheckId.FACE, ids.first())
    }

    @Test fun theSameMeasurementsAreJudgedPerLevel() {
        // head 51 %: inside the official 50-69 % band, but only just
        val m = measured(r = reading(head = 0.51f))
        val v = m.verdicts()
        assertEquals(Strictness.values().toList(), v.map { it.level })
        assertEquals(listOf(true, false, false), v.map { it.ok })
        assertEquals(listOf(0, 1, 1), v.map { it.failures })
        assertEquals(m.checks(Strictness.STRICT).count { it.status == Status.FAIL }, v[2].failures)
    }

    @Test fun editingSoftwareIsAWarningUntilStrict() {
        val edited = MetadataFindings(ImageFormat.JPEG, flags = setOf(EditFlag.EDITOR_APP), takenAt = CivilDate(2026, 10, 1))
        val v = measured(findings = edited).verdicts()
        assertEquals(listOf(true, true, false), v.map { it.ok })
        assertEquals(listOf(1, 1, 0), v.map { it.warnings }) // a warning where it does not fail
        assertEquals(listOf(0, 0, 1), v.map { it.failures })
    }

    @Test fun anAiPhotoFailsEverywhere() {
        val ai = MetadataFindings(ImageFormat.JPEG, flags = setOf(EditFlag.AI_TRAINED), takenAt = CivilDate(2026, 10, 1))
        assertTrue(measured(findings = ai).verdicts().none { it.ok })
    }

    @Test fun fileLimitsAreOfficialAtEveryLevel() {
        val tooBig = measured(fileBytes = 300_000).verdicts()
        assertTrue(tooBig.none { it.ok })
        val tooWide = measured(fileSide = 1300).verdicts()
        assertTrue(tooWide.none { it.ok })
        assertTrue(measured(fileBytes = DvRules.MAX_FILE_BYTES, fileSide = DvRules.MAX_SIDE_PX).verdicts().all { it.ok })
    }

    @Test fun noFaceFailsEverywhereAndKeepsTheFileCheck() {
        val m = measured(r = null)
        for (level in Strictness.values()) {
            val checks = m.checks(level)
            assertEquals(Hint.NO_FACE, checks.first().hint)
            assertEquals(CheckId.FILE, checks.last().id)
            assertFalse(Verdict.of(level, checks).ok)
            assertNull(checks.firstOrNull { it.id == CheckId.HEAD_SIZE })
        }
    }

    @Test fun verdictCountsFailuresAndWarnings() {
        val v = Verdict.of(
            Strictness.STANDARD,
            listOf(Check(CheckId.FACE, Status.PASS, Hint.OK), Check(CheckId.POSE, Status.FAIL, Hint.TURNED), Check(CheckId.EXPRESSION, Status.WARN, Hint.SMILING)),
        )
        assertEquals(1, v.failures)
        assertEquals(1, v.warnings)
        assertFalse(v.ok)
    }
}
