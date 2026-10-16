package app.dvphoto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class StrictnessTest {
    private val relaxed = Strictness.RELAXED.t
    private val standard = Strictness.STANDARD.t
    private val strict = Strictness.STRICT.t

    // --- the table itself -------------------------------------------------------------------

    /** (name, value, true when a larger number is the stricter one). */
    private class Knob(val name: String, val of: (Thresholds) -> Float, val higherIsStricter: Boolean)

    private val knobs = listOf(
        Knob("margin", { it.margin }, true),
        Knob("maxRoll", { it.maxRoll }, false), Knob("maxYaw", { it.maxYaw }, false), Knob("maxPitch", { it.maxPitch }, false),
        Knob("minEyesOpen", { it.minEyesOpen }, true), Knob("maxSmile", { it.maxSmile }, false),
        Knob("maxOffCenter", { it.maxOffCenter }, false),
        Knob("faceLumaMin", { it.faceLumaMin }, true), Knob("faceLumaMax", { it.faceLumaMax }, false),
        Knob("maxLightAsymmetry", { it.maxLightAsymmetry }, false),
        Knob("bgLumaMin", { it.bgLumaMin }, true), Knob("bgStdMax", { it.bgStdMax }, false),
        Knob("bgOutlierMax", { it.bgOutlierMax }, false), Knob("bgEdgeMax", { it.bgEdgeMax }, false),
        Knob("bgChromaMax", { it.bgChromaMax }, false),
        Knob("minSharpness", { it.minSharpness }, true),
        Knob("maxPhotoAgeDays", { it.maxPhotoAgeDays.toFloat() }, false),
        Knob("holdMs", { it.holdMs.toFloat() }, true), Knob("steadyTolerance", { it.steadyTolerance }, false),
    )

    @Test fun everyLimitGetsStricterFromRelaxedToStrict() {
        for (k in knobs) {
            val (a, b, c) = listOf(relaxed, standard, strict).map(k.of)
            if (k.higherIsStricter) assertTrue("${k.name}: $a $b $c", a <= b && b <= c)
            else assertTrue("${k.name}: $a $b $c", a >= b && b >= c)
            assertTrue("${k.name} must differ between relaxed and strict", a != c)
        }
        assertFalse(relaxed.failOnUnknownDate || standard.failOnUnknownDate)
        assertTrue(strict.failOnUnknownDate)
        assertFalse(relaxed.failOnEditSoftware || standard.failOnEditSoftware)
        assertTrue(strict.failOnEditSoftware)
    }

    @Test fun standardKeepsTheValuesTheAppWasCalibratedWith() {
        assertEquals(0.02f, standard.margin, 0f)
        assertEquals(listOf(5f, 8f, 10f), listOf(standard.maxRoll, standard.maxYaw, standard.maxPitch))
        assertEquals(0.6f, standard.minEyesOpen, 0f)
        assertEquals(0.5f, standard.maxSmile, 0f)
        assertEquals(0.05f, standard.maxOffCenter, 0f)
        assertEquals(55f, standard.faceLumaMin, 0f)
        assertEquals(215f, standard.faceLumaMax, 0f)
        assertEquals(0.20f, standard.maxLightAsymmetry, 0f)
        assertEquals(listOf(170f, 22f, 0.06f, 0.02f, 28f), listOf(standard.bgLumaMin, standard.bgStdMax, standard.bgOutlierMax, standard.bgEdgeMax, standard.bgChromaMax))
        assertEquals(25f, standard.minSharpness, 0f)
        assertEquals(DvRules.MAX_PHOTO_AGE_DAYS, standard.maxPhotoAgeDays)
        assertEquals(1200L, standard.holdMs)
        assertEquals(0.02f, standard.steadyTolerance, 0f)
        assertEquals(Strictness.STANDARD, Strictness.DEFAULT)
    }

    @Test fun officialLimitsNeverLoosen() {
        for (level in Strictness.values()) {
            val t = level.t
            val name = level.name
            assertTrue("$name margin", t.margin >= 0f)
            assertTrue("$name head min", t.headMin >= DvRules.HEAD_MIN)
            assertTrue("$name head max", t.headMax <= DvRules.HEAD_MAX)
            assertTrue("$name eye min", t.eyeMin >= DvRules.EYE_MIN)
            assertTrue("$name eye max", t.eyeMax <= DvRules.EYE_MAX)
            assertTrue("$name age", t.maxPhotoAgeDays in 1..DvRules.MAX_PHOTO_AGE_DAYS)
            // the targets the guide aims at stay comfortably inside what the level accepts
            assertTrue("$name head target", DvRules.HEAD_TARGET - t.headMin >= 0.05f && t.headMax - DvRules.HEAD_TARGET >= 0.05f)
            assertTrue("$name eye target", DvRules.EYE_TARGET - t.eyeMin >= 0.03f && t.eyeMax - DvRules.EYE_TARGET >= 0.03f)
            // sanity of the unit ranges
            assertTrue("$name luma", t.faceLumaMin < t.faceLumaMax && t.faceLumaMax <= 255f && t.bgLumaMin <= 255f)
            assertTrue("$name probabilities", t.minEyesOpen in 0f..1f && t.maxSmile in 0f..1f && t.maxLightAsymmetry in 0f..1f)
        }
        assertEquals(0f, relaxed.margin, 0f) // relaxed = exactly the official numbers
        assertEquals(DvRules.HEAD_MIN, relaxed.headMin, 0f)
        assertEquals(DvRules.EYE_MAX, relaxed.eyeMax, 0f)
    }

    @Test fun idsRoundTripAndUnknownValuesFallBackToStandard() {
        assertEquals(Strictness.values().size, Strictness.values().map { it.id }.toSet().size)
        for (level in Strictness.values()) assertEquals(level, Strictness.fromId(level.id))
        assertEquals(Strictness.STANDARD, Strictness.fromId(null))
        assertEquals(Strictness.STANDARD, Strictness.fromId(""))
        assertEquals(Strictness.STANDARD, Strictness.fromId("ultra"))
    }

    @Test fun nextCyclesThroughAllLevels() {
        assertEquals(Strictness.STANDARD, Strictness.RELAXED.next())
        assertEquals(Strictness.STRICT, Strictness.STANDARD.next())
        assertEquals(Strictness.RELAXED, Strictness.STRICT.next())
    }

    // --- boundaries ------------------------------------------------------------------------

    private fun face(head: Float, eyeFromBottom: Float = 0.625f, cx: Float = 0.5f, roll: Float = 0f, yaw: Float = 0f, pitch: Float = 0f) =
        FaceReading(1, 0.15f, 0.15f + head, 1f - eyeFromBottom, cx, 0.43f, roll, yaw, pitch, 0.95f, 0.05f)

    private val calm = SceneStats(140f, 0.05f, 225f, 5f, 6f, 90f)

    private fun statusAt(level: Strictness, id: CheckId, r: FaceReading, s: SceneStats = calm) =
        Validator.validate(r, s, 1000, false, level).first { it.id == id }

    private fun statuses(id: CheckId, r: FaceReading, s: SceneStats = calm) =
        Strictness.values().map { statusAt(it, id, r, s).status }

    @Test fun headSizeBoundaryMovesWithTheLevel() {
        assertEquals(listOf(Status.PASS, Status.FAIL, Status.FAIL), statuses(CheckId.HEAD_SIZE, face(0.51f))) // inside the official band, inside the margin
        assertEquals(listOf(Status.PASS, Status.PASS, Status.FAIL), statuses(CheckId.HEAD_SIZE, face(0.525f)))
        assertEquals(listOf(Status.PASS, Status.PASS, Status.PASS), statuses(CheckId.HEAD_SIZE, face(0.60f)))
        assertEquals(listOf(Status.PASS, Status.PASS, Status.FAIL), statuses(CheckId.HEAD_SIZE, face(0.665f)))
        assertEquals(listOf(Status.PASS, Status.FAIL, Status.FAIL), statuses(CheckId.HEAD_SIZE, face(0.68f)))
        // outside the official band: fails everywhere, with the right instruction
        for (level in Strictness.values()) {
            assertEquals(Hint.MOVE_CLOSER, statusAt(level, CheckId.HEAD_SIZE, face(0.49f)).hint)
            assertEquals(Hint.MOVE_BACK, statusAt(level, CheckId.HEAD_SIZE, face(0.70f)).hint)
        }
    }

    @Test fun eyeLineAndCenteringAndPoseFollowTheLevel() {
        assertEquals(listOf(Status.PASS, Status.FAIL, Status.FAIL), statuses(CheckId.EYE_LINE, face(0.6f, eyeFromBottom = 0.565f)))
        assertEquals(listOf(Status.PASS, Status.PASS, Status.FAIL), statuses(CheckId.EYE_LINE, face(0.6f, eyeFromBottom = 0.585f)))
        assertEquals(listOf(Status.PASS, Status.FAIL, Status.FAIL), statuses(CheckId.EYE_LINE, face(0.6f, eyeFromBottom = 0.685f)))
        assertEquals(listOf(Status.PASS, Status.FAIL, Status.FAIL), statuses(CheckId.CENTER, face(0.6f).copy(centerX = 0.56f)))
        assertEquals(listOf(Status.PASS, Status.PASS, Status.FAIL), statuses(CheckId.CENTER, face(0.6f).copy(centerX = 0.54f)))
        assertEquals(listOf(Status.PASS, Status.FAIL, Status.FAIL), statuses(CheckId.POSE, face(0.6f, roll = 6f)))
        assertEquals(listOf(Status.PASS, Status.PASS, Status.FAIL), statuses(CheckId.POSE, face(0.6f, yaw = 6f)))
        assertEquals(listOf(Status.FAIL, Status.FAIL, Status.FAIL), statuses(CheckId.POSE, face(0.6f, pitch = 20f)))
    }

    @Test fun lightBackgroundAndSharpnessFollowTheLevel() {
        fun scene(face: Float = 140f, bg: Float = 225f, std: Float = 5f, chroma: Float? = 6f, sharp: Float? = 90f, asym: Float = 0.05f) =
            SceneStats(face, asym, bg, std, chroma, sharp)
        val r = face(0.6f)
        assertEquals(listOf(Status.PASS, Status.FAIL, Status.FAIL), statuses(CheckId.LIGHTING, r, scene(face = 50f)))
        assertEquals(listOf(Status.PASS, Status.PASS, Status.FAIL), statuses(CheckId.LIGHTING, r, scene(face = 60f)))
        assertEquals(listOf(Status.PASS, Status.PASS, Status.FAIL), statuses(CheckId.LIGHTING, r, scene(asym = 0.15f)))
        assertEquals(listOf(Status.PASS, Status.FAIL, Status.FAIL), statuses(CheckId.BACKGROUND, r, scene(bg = 160f)))
        assertEquals(listOf(Status.PASS, Status.PASS, Status.FAIL), statuses(CheckId.BACKGROUND, r, scene(std = 18f)))
        assertEquals(listOf(Status.PASS, Status.FAIL, Status.FAIL), statuses(CheckId.BACKGROUND, r, scene(chroma = 32f)))
        assertEquals(listOf(Status.PASS, Status.FAIL, Status.FAIL), statuses(CheckId.SHARPNESS, r, scene(sharp = 20f)))
        assertEquals(listOf(Status.PASS, Status.PASS, Status.FAIL), statuses(CheckId.SHARPNESS, r, scene(sharp = 30f)))
    }

    @Test fun smileOnlyWarnsAtEveryLevel() {
        val r = face(0.6f).copy(smile = 0.9f)
        for (level in Strictness.values()) {
            assertTrue(statusAt(level, CheckId.EXPRESSION, r).status != Status.FAIL)
        }
        assertEquals(listOf(Status.WARN, Status.WARN, Status.WARN), statuses(CheckId.EXPRESSION, r))
        val mild = face(0.6f).copy(smile = 0.4f)
        assertEquals(listOf(Status.PASS, Status.PASS, Status.WARN), statuses(CheckId.EXPRESSION, mild))
    }

    // --- properties ------------------------------------------------------------------------

    private fun randomReading(rnd: Random): FaceReading {
        val head = 0.40f + rnd.nextFloat() * 0.35f
        val top = 0.02f + rnd.nextFloat() * 0.25f
        return FaceReading(
            faceCount = if (rnd.nextInt(10) == 0) 2 else 1,
            headTop = top, chin = top + head,
            eyeY = 0.25f + rnd.nextFloat() * 0.3f, centerX = 0.4f + rnd.nextFloat() * 0.2f, headWidth = 0.4f,
            roll = (rnd.nextFloat() - 0.5f) * 30f, yaw = (rnd.nextFloat() - 0.5f) * 30f, pitch = (rnd.nextFloat() - 0.5f) * 36f,
            eyesOpen = if (rnd.nextInt(6) == 0) null else rnd.nextFloat(),
            smile = if (rnd.nextInt(6) == 0) null else rnd.nextFloat(),
        )
    }

    private fun randomScene(rnd: Random) = SceneStats(
        faceLuma = 20f + rnd.nextFloat() * 230f, lightAsymmetry = rnd.nextFloat() * 0.5f,
        bgLuma = 110f + rnd.nextFloat() * 145f, bgStd = rnd.nextFloat() * 45f,
        bgChroma = if (rnd.nextInt(5) == 0) null else rnd.nextFloat() * 50f,
        sharpness = if (rnd.nextInt(5) == 0) null else rnd.nextFloat() * 80f,
        bgOutliers = rnd.nextFloat() * 0.15f, bgEdges = rnd.nextFloat() * 0.07f,
    )

    private fun severity(s: Status) = s.ordinal // PASS < WARN < FAIL

    @Test fun aStricterLevelNeverPassesWhatALooserOneFails() {
        val rnd = Random(2026)
        repeat(20_000) {
            val r = if (rnd.nextInt(40) == 0) null else randomReading(rnd)
            val s = if (rnd.nextInt(8) == 0) null else randomScene(rnd)
            val side = listOf(null, 500, 1000)[rnd.nextInt(3)]
            val mirrored = rnd.nextBoolean()
            val perLevel = Strictness.values().map { Validator.validate(r, s, side, mirrored, it) }
            // the same checks appear at every level
            assertEquals(perLevel[0].map { it.id }, perLevel[1].map { it.id })
            assertEquals(perLevel[1].map { it.id }, perLevel[2].map { it.id })
            for (i in perLevel[0].indices) {
                val (a, b, c) = perLevel.map { severity(it[i].status) }
                assertTrue("${perLevel[0][i].id}: relaxed=$a standard=$b strict=$c for $r $s", a <= b && b <= c)
            }
        }
    }

    @Test fun officialViolationsFailAtEveryLevel() {
        val rnd = Random(7)
        repeat(5_000) {
            val r = randomReading(rnd).copy(faceCount = 1)
            val s = randomScene(rnd)
            for (level in Strictness.values()) {
                val c = Validator.validate(r, s, 500, false, level)
                if (r.headHeight < DvRules.HEAD_MIN || r.headHeight > DvRules.HEAD_MAX) {
                    assertEquals(Status.FAIL, c.first { it.id == CheckId.HEAD_SIZE }.status)
                }
                if (r.eyeFromBottom < DvRules.EYE_MIN || r.eyeFromBottom > DvRules.EYE_MAX) {
                    assertEquals(Status.FAIL, c.first { it.id == CheckId.EYE_LINE }.status)
                }
                assertEquals(Hint.LOW_RES, c.first { it.id == CheckId.RESOLUTION }.hint) // 500 px is below the official minimum
            }
        }
        // two people are never acceptable
        for (level in Strictness.values()) {
            val two = Validator.validate(face(0.6f).copy(faceCount = 2), calm, 1000, false, level)
            assertEquals(Hint.MULTIPLE_FACES, two.first { it.id == CheckId.FACE }.hint)
        }
    }

    @Test fun aPerfectPhotoPassesEveryLevel() {
        val r = FaceReading(1, Guide.headTop, Guide.chin, Guide.eyeY, 0.5f, 0.43f, 0f, 0f, 0f, 0.95f, 0.05f)
        for (level in Strictness.values()) {
            val c = Validator.validate(r, calm, 1000, false, level)
            assertTrue("$level $c", c.all { it.status == Status.PASS })
            assertEquals(null, Validator.firstProblem(c))
        }
    }

    // --- authenticity checks by level --------------------------------------------------------

    private val today = CivilDate(2026, 10, 16).epochDay
    private fun editing(vararg flags: EditFlag, level: Strictness) =
        Provenance.editing(MetadataFindings(ImageFormat.JPEG, flags = flags.toSet()), level)

    private fun age(daysOld: Int?, level: Strictness): Check {
        val taken = daysOld?.let { CivilDate(2026, 10, 16).epochDay - it }?.let { epoch -> civil(epoch) }
        return Provenance.age(MetadataFindings(ImageFormat.JPEG, takenAt = taken), today, level)
    }

    /** Inverse of [Dates.daysFromCivil] for the test (java.time is fine in tests). */
    private fun civil(epochDay: Long): CivilDate {
        val d = java.time.LocalDate.ofEpochDay(epochDay)
        return CivilDate(d.year, d.monthValue, d.dayOfMonth)
    }

    @Test fun aiAndBeautyAppsFailAtEveryLevel() {
        for (level in Strictness.values()) {
            assertEquals(Status.FAIL, editing(EditFlag.AI_TRAINED, level = level).status)
            assertEquals(Status.FAIL, editing(EditFlag.AI_TOOL, level = level).status)
            assertEquals(Status.FAIL, editing(EditFlag.BEAUTY_APP, level = level).status)
            assertEquals(Status.PASS, editing(level = level).status)
        }
    }

    @Test fun editingSoftwareWarnsUntilStrictThenFails() {
        for (flag in listOf(EditFlag.EDITOR_APP, EditFlag.PORTRAIT_MODE, EditFlag.CREDENTIALS_EDITED)) {
            assertEquals(Status.WARN, editing(flag, level = Strictness.RELAXED).status)
            assertEquals(Status.WARN, editing(flag, level = Strictness.STANDARD).status)
            assertEquals(Status.FAIL, editing(flag, level = Strictness.STRICT).status)
        }
        // the hint stays the same: only how serious it is changes
        assertEquals(Hint.EDIT_SOFTWARE, editing(EditFlag.EDITOR_APP, level = Strictness.STRICT).hint)
    }

    @Test fun photoAgeMarginsByLevel() {
        // 160 days: inside the official 6 months, but too close to it for strict
        assertEquals(Status.PASS, age(160, Strictness.RELAXED).status)
        assertEquals(Status.PASS, age(160, Strictness.STANDARD).status)
        val near = age(160, Strictness.STRICT)
        assertEquals(Status.FAIL, near.status)
        assertEquals(Hint.AGE_NEAR_LIMIT, near.hint)
        assertEquals(5.3f, near.value!!, 0.1f)
        // beyond the official limit: fails everywhere, with the "too old" message
        for (level in Strictness.values()) {
            assertEquals(Hint.AGE_OLD, age(200, level).hint)
            assertEquals(Status.FAIL, age(200, level).status)
            assertEquals(Status.PASS, age(10, level).status)
        }
        // no date: a warning until strict
        assertEquals(Status.WARN, age(null, Strictness.RELAXED).status)
        assertEquals(Status.WARN, age(null, Strictness.STANDARD).status)
        assertEquals(Status.FAIL, age(null, Strictness.STRICT).status)
        assertEquals(Hint.AGE_UNKNOWN, age(null, Strictness.STRICT).hint)
    }

    @Test fun authenticityNestsLikeTheRest() {
        val rnd = Random(11)
        val all = EditFlag.values()
        repeat(3_000) {
            val flags = all.filter { rnd.nextInt(4) == 0 }.toSet()
            val taken = if (rnd.nextInt(5) == 0) null else civil(today - (rnd.nextInt(260) - 3))
            val f = MetadataFindings(ImageFormat.JPEG, flags = flags, takenAt = taken)
            val perLevel = Strictness.values().map { Provenance.evaluate(f, today, it) }
            for (i in perLevel[0].indices) {
                val (a, b, c) = perLevel.map { it[i].status.ordinal }
                assertTrue("flags=$flags taken=$taken: $a $b $c", a <= b && b <= c)
            }
        }
    }

    @Test fun autoCaptureGetsMorePatientWithStrictness() {
        assertTrue(relaxed.holdMs < standard.holdMs && standard.holdMs < strict.holdMs)
        assertTrue(relaxed.steadyTolerance > standard.steadyTolerance && standard.steadyTolerance > strict.steadyTolerance)
        // the gate honours the level's numbers
        val s = Strictness.STRICT.t
        val gate = CaptureGate(s.holdMs, s.steadyTolerance)
        val f = face(0.6f)
        gate.update(f, true, 0)
        assertEquals(0.5f, gate.update(f, true, s.holdMs / 2), 0.01f)
    }
}
