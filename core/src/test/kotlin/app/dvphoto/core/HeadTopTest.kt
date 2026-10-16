package app.dvphoto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** A synthetic portrait: plain wall, an elliptical head with a skin face and a hair cap, shoulders. */
private class Portrait(
    val side: Int = 1000,
    val wall: Int = 230,
    val hair: Int = 40,
    val skin: Int = 150,
    val headTop: Float = 0.12f,   // top of the hair
    val hairLine: Float = 0.22f,  // where the skin starts
    val chin: Float = 0.70f,
    val cx: Float = 0.5f,
    val halfWidth: Float = 0.21f,
    val wallGradX: Float = 0f, // wall brightness change across the full width
    val wallGradY: Float = 0f,
    val shadowX: Float = -1f,  // x from which the wall is 60 darker (a hard shadow edge); -1 = none
    val patch: FloatArray? = null, // [x0, y0, x1, y1]: a dark rectangle on the wall (poster, picture frame)
    val noise: Int = 0,        // +-noise luma of deterministic camera-like grain
) : PixelSource {
    override val width = side
    override val height = side
    override fun luma(x: Int, y: Int): Int {
        val nx = x.toFloat() / side
        val ny = y.toFloat() / side
        val cy = (headTop + chin) / 2
        val ry = (chin - headTop) / 2
        val dx = (nx - cx) / halfWidth
        val dy = (ny - cy) / ry
        if (dx * dx + dy * dy <= 1f) return if (ny < hairLine) hair else skin
        if (ny > 0.80f && abs(nx - cx) < 0.40f) return 90 // shoulders / clothes
        var v = wall + wallGradX * (nx - 0.5f) + wallGradY * (ny - 0.5f)
        if (shadowX >= 0f && nx > shadowX) v -= 60f
        patch?.let { if (nx in it[0]..it[2] && ny in it[1]..it[3]) v -= 90f }
        if (noise > 0) v += (((x * 73856093) xor (y * 19349663)).mod(2 * noise + 1) - noise).toFloat()
        return v.toInt().coerceIn(0, 255)
    }
}

class HeadTopTest {
    private fun reading(p: Portrait, ext: Float = DvRules.HEAD_TOP_EXTENSION): FaceReading {
        // ML-Kit-like contour: starts at the hairline, ends at the chin
        val ovalTop = p.hairLine
        val faceH = p.chin - ovalTop
        return FaceReading(
            faceCount = 1, headTop = ovalTop - ext * faceH, chin = p.chin, eyeY = p.hairLine + 0.2f * faceH,
            centerX = p.cx, headWidth = p.halfWidth * 2, roll = 0f, yaw = 0f, pitch = 0f, eyesOpen = 1f, smile = 0f,
        )
    }

    @Test fun findsTopOfHairOnPlainWall() {
        val p = Portrait()
        val r = SceneAnalyzer.analyze(p, 0, 0, 1000, reading(p), withSharpness = false).reading
        assertTrue(r.headTopMeasured)
        assertEquals(p.headTop, r.headTop, 0.015f)
    }

    @Test fun bigHairIsMeasuredNotEstimated() {
        val p = Portrait(headTop = 0.05f) // tall hair volume
        val r = SceneAnalyzer.analyze(p, 0, 0, 1000, reading(p), withSharpness = false).reading
        assertTrue(r.headTopMeasured)
        assertEquals(0.05f, r.headTop, 0.015f)
        // the estimate alone would have under-measured this head
        assertTrue(reading(p).headTop > r.headTop + 0.03f)
    }

    @Test fun lightSkinCloseToWallStillFindsHair() {
        val p = Portrait(skin = 215, wall = 235)
        val r = SceneAnalyzer.analyze(p, 0, 0, 1000, reading(p), withSharpness = false).reading
        assertTrue(r.headTopMeasured)
        assertEquals(p.headTop, r.headTop, 0.015f)
    }

    @Test fun darkSkinFindsHair() {
        val p = Portrait(skin = 70, hair = 25)
        val r = SceneAnalyzer.analyze(p, 0, 0, 1000, reading(p), withSharpness = false).reading
        assertTrue(r.headTopMeasured)
        assertEquals(p.headTop, r.headTop, 0.015f)
    }

    @Test fun whiteHairOnWhiteWallFallsBackToEstimate() {
        val p = Portrait(hair = 228, wall = 232)
        val est = reading(p)
        val r = SceneAnalyzer.analyze(p, 0, 0, 1000, est, withSharpness = false).reading
        assertTrue(!r.headTopMeasured)
        assertEquals(est.headTop, r.headTop, 1e-6f)
    }

    @Test fun busyBackgroundIsNotMeasured() {
        val p = Portrait()
        val busy = object : PixelSource by p {
            override fun luma(x: Int, y: Int): Int {
                val v = p.luma(x, y)
                return if (v == 230 && ((x / 30 + y / 30) % 2 == 0)) 120 else v
            }
        }
        val r = SceneAnalyzer.analyze(busy, 0, 0, 1000, reading(p), withSharpness = false).reading
        assertTrue(!r.headTopMeasured)
    }

    @Test fun smoothLightingGradientIsNotABackgroundProblem() {
        val p = Portrait(wall = 205, wallGradX = 60f, wallGradY = -30f)
        val res = SceneAnalyzer.analyze(p, 0, 0, 1000, reading(p), withSharpness = false)
        assertTrue("residual std ${res.stats.bgStd}", res.stats.bgStd < 4f)
        assertEquals(60f, res.stats.bgGradX, 6f)
        assertEquals(-30f, res.stats.bgGradY, 6f)
        val bg = Validator.validate(res.reading, res.stats, 1000, false).first { it.id == CheckId.BACKGROUND }
        assertEquals(Status.PASS, bg.status)
        // and the hair line is still found against the tilted wall
        assertTrue(res.reading.headTopMeasured)
        assertEquals(p.headTop, res.reading.headTop, 0.02f)
    }

    private fun backgroundOf(p: Portrait): Check {
        val res = SceneAnalyzer.analyze(p, 0, 0, 1000, reading(p), withSharpness = false)
        return Validator.validate(res.reading, res.stats, 1000, false).first { it.id == CheckId.BACKGROUND }
    }

    @Test fun hardShadowOnTheWallIsFlagged() {
        assertEquals(Hint.NOT_PLAIN, backgroundOf(Portrait(shadowX = 0.8f)).hint)
        assertEquals(Hint.NOT_PLAIN, backgroundOf(Portrait(shadowX = 0.85f)).hint) // the last 15% of the width
    }

    @Test fun aPosterOnTheWallIsFlaggedButGrainIsNot() {
        val poster = Portrait(patch = floatArrayOf(0.04f, 0.06f, 0.20f, 0.30f)) // ~4% of the frame, ~9% of the wall
        val flagged = backgroundOf(poster)
        assertEquals(flagged.toString(), Hint.NOT_PLAIN, flagged.hint)
        assertEquals(Status.PASS, backgroundOf(Portrait(noise = 12)).status)
        assertEquals(Status.PASS, backgroundOf(Portrait()).status)
    }

    @Test fun planeFitRecoversTiltAndNoise() {
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>(); val vs = ArrayList<Float>()
        for (j in 0 until 14) for (i in 0 until 16) {
            val x = (i + 0.5f) / 16; val y = (j + 0.5f) / 14
            xs += x; ys += y; vs += 180f + 40f * (x - 0.5f) - 20f * (y - 0.5f)
        }
        val exact = SceneAnalyzer.fitPlane(xs, ys, vs)
        assertEquals(180f, exact.a, 0.1f); assertEquals(40f, exact.gx, 0.1f); assertEquals(-20f, exact.gy, 0.1f)
        assertTrue(exact.residualStd < 0.01f)
        // a single dark object stands out from the plane
        vs[100] = 20f
        assertTrue(SceneAnalyzer.fitPlane(xs, ys, vs).residualStd > 5f)
        // degenerate input falls back to a flat fit
        val flat = SceneAnalyzer.fitPlane(listOf(0.5f, 0.5f, 0.5f), listOf(0.5f, 0.5f, 0.5f), listOf(10f, 20f, 30f))
        assertEquals(20f, flat.a, 0.01f)
    }

    @Test fun measuredTopFeedsHeadSizeCheck() {
        val p = Portrait(headTop = 0.10f, chin = 0.70f) // true head height 60%
        val est = reading(p)
        val refined = SceneAnalyzer.analyze(p, 0, 0, 1000, est, withSharpness = false)
        assertEquals(0.60f, refined.reading.headHeight, 0.02f)
        val checks = Validator.validate(refined.reading, refined.stats, 1000, false)
        val head = checks.first { it.id == CheckId.HEAD_SIZE }
        assertEquals(Status.PASS, head.status)
        assertNotNull(head.value)
        assertEquals(0.60f, head.value!!, 0.02f)
    }

    @Test fun smartCropRefineCorrectsAnOverestimatedHead() {
        // The first crop assumed a smaller head than measured; refine must enlarge the square accordingly.
        val crop = SquareCrop(500, 300, 1000, false)
        val r = FaceReading(1, 0.10f, 0.80f, 0.40f, 0.5f, 0.45f, 0f, 0f, 0f, 1f, 0f, true) // head = 70% of the crop
        val refined = SmartCrop.refine(crop, r, 4000, 3000)
        assertEquals(0.70f * 1000 / DvRules.HEAD_TARGET, refined.side.toFloat(), 2f)
        // and the eye line of the refined square lands on the target
        val eyePx = crop.top + r.eyeY * crop.side
        assertEquals(DvRules.EYE_TARGET, 1f - (eyePx - refined.top) / refined.side, 0.01f)
    }

    @Test fun refineClampsInsideTheImage() {
        val crop = SquareCrop(0, 0, 1000, false)
        val r = FaceReading(1, 0.05f, 0.95f, 0.40f, 0.5f, 0.6f, 0f, 0f, 0f, 1f, 0f, true) // huge head -> wants a square bigger than the photo
        val refined = SmartCrop.refine(crop, r, 1200, 1500)
        assertTrue(refined.clamped)
        assertTrue(refined.side <= 1200 && refined.left >= 0 && refined.top >= 0)
        assertTrue(refined.left + refined.side <= 1200 && refined.top + refined.side <= 1500)
    }

    @Test fun rotateRectMatchesImageRotation() {
        // 4x2 buffer, rect covering the left 2 columns of the top row
        val r = PxRect(0, 0, 2, 1)
        assertEquals(r, FrameGeometry.rotateRect(r, 4, 2, 0))
        // clockwise 90: buffer becomes 2 wide x 4 tall; top-left block moves to the top-right
        assertEquals(PxRect(1, 0, 2, 2), FrameGeometry.rotateRect(r, 4, 2, 90))
        assertEquals(PxRect(2, 1, 4, 2), FrameGeometry.rotateRect(r, 4, 2, 180))
        assertEquals(PxRect(0, 2, 1, 4), FrameGeometry.rotateRect(r, 4, 2, 270))
        assertEquals(PxRect(0, 2, 1, 4), FrameGeometry.rotateRect(r, 4, 2, -90))
        val sq = FrameGeometry.centeredSquare(PxRect(0, 0, 640, 480))
        assertEquals(PxRect(80, 0, 560, 480), sq)
    }
}
