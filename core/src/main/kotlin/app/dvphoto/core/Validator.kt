package app.dvphoto.core

import kotlin.math.abs

object Validator {
    /**
     * @param sidePx side of the analysed square in real pixels, or null for live preview frames
     * @param mirrored true when the on-screen preview is a mirror image (front camera)
     * @param level how closely the photo has to match the rules; official limits are the same at every level
     */
    fun validate(
        r: FaceReading?,
        s: SceneStats?,
        sidePx: Int?,
        mirrored: Boolean,
        level: Strictness = Strictness.DEFAULT,
    ): List<Check> {
        if (r == null) return listOf(Check(CheckId.FACE, Status.FAIL, Hint.NO_FACE))
        val t = level.t
        val out = ArrayList<Check>(12)
        fun add(id: CheckId, st: Status, h: Hint) = out.add(Check(id, st, h))
        fun pass(id: CheckId) = add(id, Status.PASS, Hint.OK)
        fun fail(id: CheckId, h: Hint) = add(id, Status.FAIL, h)

        if (r.faceCount > 1) fail(CheckId.FACE, Hint.MULTIPLE_FACES) else pass(CheckId.FACE)

        when {
            r.headHeight < t.headMin -> out.add(Check(CheckId.HEAD_SIZE, Status.FAIL, Hint.MOVE_CLOSER, r.headHeight))
            r.headHeight > t.headMax -> out.add(Check(CheckId.HEAD_SIZE, Status.FAIL, Hint.MOVE_BACK, r.headHeight))
            else -> out.add(Check(CheckId.HEAD_SIZE, Status.PASS, Hint.OK, r.headHeight))
        }

        val off = r.centerX - 0.5f
        val shown = if (mirrored) -off else off // + means the face appears right of centre on screen
        when {
            abs(off) <= t.maxOffCenter -> pass(CheckId.CENTER)
            shown > 0 -> fail(CheckId.CENTER, Hint.MOVE_LEFT)
            else -> fail(CheckId.CENTER, Hint.MOVE_RIGHT)
        }

        when {
            r.eyeFromBottom < t.eyeMin -> out.add(Check(CheckId.EYE_LINE, Status.FAIL, Hint.EYES_LOW, r.eyeFromBottom))
            r.eyeFromBottom > t.eyeMax -> out.add(Check(CheckId.EYE_LINE, Status.FAIL, Hint.EYES_HIGH, r.eyeFromBottom))
            else -> out.add(Check(CheckId.EYE_LINE, Status.PASS, Hint.OK, r.eyeFromBottom))
        }

        when {
            abs(r.roll) > t.maxRoll -> fail(CheckId.POSE, Hint.TILT_HEAD)
            abs(r.yaw) > t.maxYaw -> fail(CheckId.POSE, Hint.TURNED)
            abs(r.pitch) > t.maxPitch -> fail(CheckId.POSE, Hint.CHIN_LEVEL)
            else -> pass(CheckId.POSE)
        }

        r.eyesOpen?.let {
            if (it < t.minEyesOpen) fail(CheckId.EYES_OPEN, Hint.EYES_CLOSED) else pass(CheckId.EYES_OPEN)
        }
        r.smile?.let {
            if (it > t.maxSmile) add(CheckId.EXPRESSION, Status.WARN, Hint.SMILING) else pass(CheckId.EXPRESSION)
        }

        if (s != null) {
            when {
                s.faceLuma < t.faceLumaMin -> fail(CheckId.LIGHTING, Hint.TOO_DARK)
                s.faceLuma > t.faceLumaMax -> fail(CheckId.LIGHTING, Hint.TOO_BRIGHT)
                s.lightAsymmetry > t.maxLightAsymmetry -> fail(CheckId.LIGHTING, Hint.UNEVEN_LIGHT)
                else -> pass(CheckId.LIGHTING)
            }
            when {
                s.bgLuma < t.bgLumaMin -> fail(CheckId.BACKGROUND, Hint.NOT_LIGHT)
                s.bgStd > t.bgStdMax || s.bgOutliers > t.bgOutlierMax || s.bgEdges > t.bgEdgeMax -> fail(CheckId.BACKGROUND, Hint.NOT_PLAIN)
                s.bgChroma != null && s.bgChroma > t.bgChromaMax -> fail(CheckId.BACKGROUND, Hint.NOT_NEUTRAL)
                else -> pass(CheckId.BACKGROUND)
            }
            s.sharpness?.let {
                if (it < t.minSharpness) fail(CheckId.SHARPNESS, Hint.BLURRY) else pass(CheckId.SHARPNESS)
            }
        }

        sidePx?.let {
            if (it < DvRules.MIN_SIDE_PX) fail(CheckId.RESOLUTION, Hint.LOW_RES) else pass(CheckId.RESOLUTION)
        }
        return out
    }

    /** Check of the encoded output file itself. Official limits only: identical at every strictness level. */
    fun fileCheck(bytes: Int, sidePx: Int): Check = when {
        bytes > DvRules.MAX_FILE_BYTES -> Check(CheckId.FILE, Status.FAIL, Hint.FILE_TOO_LARGE)
        sidePx !in DvRules.MIN_SIDE_PX..DvRules.MAX_SIDE_PX -> Check(CheckId.FILE, Status.FAIL, Hint.FILE_SIZE_RANGE)
        else -> Check(CheckId.FILE, Status.PASS, Hint.OK)
    }

    /** The most important thing to tell the user right now, or null when everything passes. */
    fun firstProblem(checks: List<Check>): Check? =
        checks.filter { it.status == Status.FAIL }.minByOrNull { it.id.ordinal }
}
