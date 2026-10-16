package app.dvphoto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** A synthetic photo of any size: plain wall, an elliptical head with a hair cap, shoulders. Coordinates in pixels. */
private class SceneSource(
    override val width: Int,
    override val height: Int,
    val hairTop: Float,
    val hairLine: Float,
    val chin: Float,
    val cx: Float,
    val halfWidth: Float,
) : PixelSource {
    override fun luma(x: Int, y: Int): Int {
        val cy = (hairTop + chin) / 2
        val ry = (chin - hairTop) / 2
        val dx = (x - cx) / halfWidth
        val dy = (y - cy) / ry
        if (dx * dx + dy * dy <= 1f) return if (y < hairLine) 40 else 150
        val shoulderTop = chin + (chin - hairTop) * 0.22f
        if (y > shoulderTop && abs(x - cx) < halfWidth * 2.2f) return 90
        return 232
    }

    /** What a face detector would report (ML Kit style: contour from the hairline to the chin). */
    fun truth(): RawFace {
        val faceH = chin - hairLine
        val eyeY = hairLine + 0.2f * faceH
        return RawFace(
            ovalTop = hairLine, chin = chin, minX = cx - halfWidth * 0.9f, maxX = cx + halfWidth * 0.9f,
            eyeLeftX = cx - halfWidth * 0.45f, eyeLeftY = eyeY, eyeRightX = cx + halfWidth * 0.45f, eyeRightY = eyeY,
            roll = 0f, yaw = 0f, pitch = 0f, eyesOpen = 1f, smile = 0f,
        )
    }
}

/** The source seen through a square crop, resampled to [out] x [out] (nearest neighbour) - what the app renders. */
private class CropSource(val src: PixelSource, val crop: SquareCrop, val out: Int) : PixelSource {
    override val width get() = out
    override val height get() = out
    override fun luma(x: Int, y: Int): Int {
        val sx = (crop.left + (x + 0.5f) * crop.side / out).toInt().coerceIn(0, src.width - 1)
        val sy = (crop.top + (y + 0.5f) * crop.side / out).toInt().coerceIn(0, src.height - 1)
        return src.luma(sx, sy)
    }
}

class UploadPipelineTest {
    /** Mirrors AppViewModel.frameUpload: first crop from the contour, measure, re-frame once, final analysis. */
    private fun frame(scene: SceneSource): Triple<SquareCrop, FaceReading, List<Check>> {
        val raw = scene.truth()
        var crop = SmartCrop.forFace(scene.width, scene.height, raw)
        fun analyse(c: SquareCrop): Pair<FaceReading, SceneStats> {
            val out = PhotoSize.outputSide(c.side)
            val first = FaceGeometry.reading(raw, c.left.toFloat(), c.top.toFloat(), c.side.toFloat(), 1)
            val res = SceneAnalyzer.analyze(CropSource(scene, c, out), 0, 0, out, first, withSharpness = false)
            return res.reading to res.stats
        }
        val (r0, _) = analyse(crop)
        val better = SmartCrop.refine(crop, r0, scene.width, scene.height)
        if (abs(better.side - crop.side) > 8 || abs(better.left - crop.left) > 8 || abs(better.top - crop.top) > 8) crop = better
        val (r, s) = analyse(crop)
        return Triple(crop, r, Validator.validate(r, s, PhotoSize.outputSide(crop.side), mirrored = false))
    }

    @Test fun anyReasonablePhotoEndsUpInsideTheOfficialBands() {
        val rnd = Random(2026)
        var feasible = 0
        repeat(500) {
            val w = rnd.nextInt(1200, 3200)
            val h = rnd.nextInt(1200, 3200)
            val minSide = minOf(w, h)
            val headH = minSide * (0.18f + rnd.nextFloat() * 0.5f)     // 18% .. 68% of the short side
            val hairRise = headH * (0.14f + rnd.nextFloat() * 0.12f)   // hair/skull above the hairline
            val faceH = headH - hairRise
            val chin = rnd.nextFloat() * (h - headH * 1.6f) + headH * 1.1f
            val hairLine = chin - faceH
            val cx = rnd.nextFloat() * (w - headH) + headH * 0.5f
            val scene = SceneSource(w, h, hairLine - hairRise, hairLine, chin, cx, halfWidth = headH * 0.36f)

            val (crop, reading, checks) = frame(scene)
            val needsSide = headH / DvRules.HEAD_TARGET
            val inside = crop.left >= 0 && crop.top >= 0 && crop.left + crop.side <= w && crop.top + crop.side <= h
            assertTrue("crop $crop outside ${w}x$h", inside)
            if (!crop.clamped && needsSide <= minSide && crop.side >= DvRules.MIN_SIDE_PX) {
                feasible++
                val failures = checks.filter { it.status == Status.FAIL }
                assertTrue("trial $it ($w x $h head ${headH.roundToInt()} @ $cx,$chin): $failures reading=$reading crop=$crop", failures.isEmpty())
                assertEquals(DvRules.HEAD_TARGET, reading.headHeight, 0.04f)
                assertEquals(0.5f, reading.centerX, 0.03f)
            }
        }
        assertTrue("only $feasible feasible trials", feasible > 120)
    }

    @Test fun aTinyFaceInAHugePhotoIsFlaggedAsLowResolutionNotSilentlyUpscaled() {
        // face is 6% of a 1500 px photo: the framed square would be only ~150 px
        val scene = SceneSource(1500, 1500, 600f, 640f, 700f, 750f, 30f)
        val (crop, _, checks) = frame(scene)
        assertTrue("side ${crop.side}", crop.side < DvRules.MIN_SIDE_PX)
        assertEquals(Hint.LOW_RES, checks.first { it.id == CheckId.RESOLUTION }.hint)
    }

    @Test fun aFaceTooCloseToTheEdgeIsFlaggedNotInvented() {
        // head touching the right border of the photo: no valid centred square exists
        val scene = SceneSource(2000, 3000, 700f, 800f, 1500f, 1960f, 260f)
        val (crop, _, checks) = frame(scene)
        assertTrue(crop.left + crop.side <= 2000)
        assertTrue("expected a framing failure, got $checks", checks.any { it.status == Status.FAIL && (it.id == CheckId.CENTER || it.id == CheckId.HEAD_SIZE || it.id == CheckId.EYE_LINE) })
    }
}
