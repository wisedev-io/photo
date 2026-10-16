package app.dvphoto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    private fun good(
        headTop: Float = Guide.headTop,
        chin: Float = Guide.chin,
        eyeY: Float = Guide.eyeY,
        cx: Float = 0.5f,
        roll: Float = 0f, yaw: Float = 0f, pitch: Float = 0f,
        eyes: Float? = 0.95f, smile: Float? = 0.05f, faces: Int = 1,
    ) = FaceReading(faces, headTop, chin, eyeY, cx, 0.43f, roll, yaw, pitch, eyes, smile)

    private val goodScene = SceneStats(140f, 0.05f, 225f, 5f, 6f, 90f)

    private fun statusOf(c: List<Check>, id: CheckId) = c.first { it.id == id }

    @Test fun perfectPhotoPassesEverything() {
        val c = Validator.validate(good(), goodScene, 1000, mirrored = false)
        assertTrue(c.toString(), c.all { it.status == Status.PASS })
        assertNull(Validator.firstProblem(c))
    }

    @Test fun noFaceIsASingleFailure() {
        val c = Validator.validate(null, null, 1000, false)
        assertEquals(listOf(Check(CheckId.FACE, Status.FAIL, Hint.NO_FACE)), c)
    }

    @Test fun headSizeBands() {
        val small = good(headTop = 0.2f, chin = 0.2f + 0.50f) // exactly on the official edge: still too small for strict mode
        assertEquals(Hint.MOVE_CLOSER, statusOf(Validator.validate(small, goodScene, 1000, false), CheckId.HEAD_SIZE).hint)
        val big = good(headTop = 0.05f, chin = 0.05f + 0.70f)
        assertEquals(Hint.MOVE_BACK, statusOf(Validator.validate(big, goodScene, 1000, false), CheckId.HEAD_SIZE).hint)
        val ok = good(headTop = 0.1f, chin = 0.1f + 0.52f)
        assertEquals(Status.PASS, statusOf(Validator.validate(ok, goodScene, 1000, false), CheckId.HEAD_SIZE).status)
    }

    @Test fun eyeLineHints() {
        assertEquals(Hint.EYES_LOW, statusOf(Validator.validate(good(eyeY = 0.5f), goodScene, 1000, false), CheckId.EYE_LINE).hint)
        assertEquals(Hint.EYES_HIGH, statusOf(Validator.validate(good(eyeY = 0.25f), goodScene, 1000, false), CheckId.EYE_LINE).hint)
    }

    @Test fun centerHintFollowsMirroring() {
        val right = good(cx = 0.6f)
        assertEquals(Hint.MOVE_LEFT, statusOf(Validator.validate(right, goodScene, 1000, false), CheckId.CENTER).hint)
        // mirrored selfie preview: raw x=0.6 appears on the LEFT of the screen, so the user must move right
        assertEquals(Hint.MOVE_RIGHT, statusOf(Validator.validate(right, goodScene, 1000, true), CheckId.CENTER).hint)
    }

    @Test fun poseAndEyes() {
        assertEquals(Hint.TILT_HEAD, statusOf(Validator.validate(good(roll = 9f), goodScene, 1000, false), CheckId.POSE).hint)
        assertEquals(Hint.TURNED, statusOf(Validator.validate(good(yaw = 15f), goodScene, 1000, false), CheckId.POSE).hint)
        assertEquals(Hint.EYES_CLOSED, statusOf(Validator.validate(good(eyes = 0.1f), goodScene, 1000, false), CheckId.EYES_OPEN).hint)
        assertEquals(Status.WARN, statusOf(Validator.validate(good(smile = 0.9f), goodScene, 1000, false), CheckId.EXPRESSION).status)
    }

    @Test fun sceneProblems() {
        fun scene(face: Float = 140f, asym: Float = 0.05f, bg: Float = 225f, std: Float = 5f, chroma: Float? = 6f, sharp: Float? = 90f) =
            SceneStats(face, asym, bg, std, chroma, sharp)
        fun hint(s: SceneStats, id: CheckId) = statusOf(Validator.validate(good(), s, 1000, false), id).hint
        assertEquals(Hint.TOO_DARK, hint(scene(face = 30f), CheckId.LIGHTING))
        assertEquals(Hint.TOO_BRIGHT, hint(scene(face = 240f), CheckId.LIGHTING))
        assertEquals(Hint.UNEVEN_LIGHT, hint(scene(asym = 0.4f), CheckId.LIGHTING))
        assertEquals(Hint.NOT_LIGHT, hint(scene(bg = 120f), CheckId.BACKGROUND))
        assertEquals(Hint.NOT_PLAIN, hint(scene(std = 40f), CheckId.BACKGROUND))
        assertEquals(Hint.NOT_NEUTRAL, hint(scene(chroma = 60f), CheckId.BACKGROUND))
        assertEquals(Hint.BLURRY, hint(scene(sharp = 5f), CheckId.SHARPNESS))
    }

    @Test fun liveFramesSkipUnknownChecks() {
        val live = SceneStats(140f, 0.05f, 225f, 5f, null, null)
        val c = Validator.validate(good(), live, null, true)
        assertTrue(c.none { it.id == CheckId.SHARPNESS || it.id == CheckId.RESOLUTION })
        assertTrue(c.all { it.status == Status.PASS })
    }

    @Test fun lowResolutionAndFile() {
        val c = Validator.validate(good(), goodScene, 500, false)
        assertEquals(Hint.LOW_RES, statusOf(c, CheckId.RESOLUTION).hint)
        assertEquals(Hint.FILE_TOO_LARGE, Validator.fileCheck(300_000, 1000).hint)
        assertEquals(Hint.FILE_SIZE_RANGE, Validator.fileCheck(100_000, 1300).hint)
        assertEquals(Status.PASS, Validator.fileCheck(200_000, 1000).status)
    }

    @Test fun firstProblemFollowsPriority() {
        val c = Validator.validate(good(roll = 20f, cx = 0.8f), goodScene, 1000, false)
        assertEquals(CheckId.CENTER, Validator.firstProblem(c)!!.id)
    }

    private fun raw(top: Float, chin: Float, cx: Float, eyeY: Float, halfW: Float = 150f) = RawFace(
        ovalTop = top, chin = chin, minX = cx - halfW, maxX = cx + halfW,
        eyeLeftX = cx - 60, eyeLeftY = eyeY, eyeRightX = cx + 60, eyeRightY = eyeY,
        roll = 0f, yaw = 0f, pitch = 0f, eyesOpen = 1f, smile = 0f,
    )

    @Test fun smartCropHitsTargets() {
        val f = raw(top = 600f, chin = 1300f, cx = 2000f, eyeY = 850f)
        val crop = SmartCrop.forFace(4000, 3000, f)
        assertTrue(!crop.clamped)
        val reading = FaceGeometry.reading(f, crop.left.toFloat(), crop.top.toFloat(), crop.side.toFloat(), 1)
        assertEquals(DvRules.HEAD_TARGET, reading.headHeight, 0.01f)
        assertEquals(DvRules.EYE_TARGET, reading.eyeFromBottom, 0.01f)
        assertEquals(0.5f, reading.centerX, 0.01f)
    }

    @Test fun smartCropClampsWhenPhotoIsTooTight() {
        val f = raw(top = 100f, chin = 900f, cx = 500f, eyeY = 400f)
        val crop = SmartCrop.forFace(1000, 1000, f)
        assertTrue(crop.clamped)
        assertTrue(crop.left >= 0 && crop.top >= 0 && crop.left + crop.side <= 1000 && crop.top + crop.side <= 1000)
    }

    private class Synthetic(
        override val width: Int = 1000,
        override val height: Int = 1000,
        val pixel: (Int, Int) -> Int,
    ) : PixelSource {
        override fun luma(x: Int, y: Int) = pixel(x, y)
    }

    @Test fun sceneAnalyzerSeesWhiteWallAndFace() {
        val src = Synthetic { x, y -> if (x in 350..650 && y in 120..690) 130 else 230 }
        val s = SceneAnalyzer.analyze(src, 0, 0, 1000, good(), withSharpness = false).stats
        assertEquals(130f, s.faceLuma, 1f)
        assertEquals(0f, s.lightAsymmetry, 0.01f)
        assertEquals(230f, s.bgLuma, 2f)
        assertTrue("bg std ${s.bgStd}", s.bgStd < 10f)
        assertNull(s.bgChroma)
        assertNull(s.sharpness)
    }

    @Test fun sceneAnalyzerFlagsBusyBackgroundAndShadow() {
        val busy = Synthetic { x, y -> if (x in 350..650 && y in 120..690) { if (x < 500) 60 else 160 } else if ((x / 40 + y / 40) % 2 == 0) 80 else 240 }
        val s = SceneAnalyzer.analyze(busy, 0, 0, 1000, good(), withSharpness = false).stats
        assertTrue("asym ${s.lightAsymmetry}", s.lightAsymmetry > DvRules.MAX_LIGHT_ASYMMETRY)
        assertTrue("bg std ${s.bgStd}", s.bgStd > DvRules.BG_STD_MAX)
        assertNotNull(Validator.firstProblem(Validator.validate(good(), s, 1000, false)))
    }

    @Test fun sharpnessSeparatesCrispFromFlat() {
        val crisp = Synthetic { x, y -> if ((x / 6 + y / 6) % 2 == 0) 60 else 200 }
        val flat = Synthetic { _, _ -> 128 }
        val sc = SceneAnalyzer.analyze(crisp, 0, 0, 1000, good(), true).stats.sharpness!!
        val sf = SceneAnalyzer.analyze(flat, 0, 0, 1000, good(), true).stats.sharpness!!
        assertTrue("crisp $sc", sc > DvRules.MIN_SHARPNESS)
        assertTrue("flat $sf", sf < DvRules.MIN_SHARPNESS)
    }
}
