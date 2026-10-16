package app.dvphoto.core

import kotlin.math.abs

object Validator {
    /**
     * @param sidePx side of the analysed square in real pixels, or null for live preview frames
     * @param mirrored true when the on-screen preview is a mirror image (front camera)
     */
    fun validate(r: FaceReading?, s: SceneStats?, sidePx: Int?, mirrored: Boolean): List<Check> {
        if (r == null) return listOf(Check(CheckId.FACE, Status.FAIL, Hint.NO_FACE))
        val out = ArrayList<Check>(12)
        fun add(id: CheckId, st: Status, h: Hint) = out.add(Check(id, st, h))
        fun pass(id: CheckId) = add(id, Status.PASS, Hint.OK)
        fun fail(id: CheckId, h: Hint) = add(id, Status.FAIL, h)

        if (r.faceCount > 1) fail(CheckId.FACE, Hint.MULTIPLE_FACES) else pass(CheckId.FACE)

        val m = DvRules.MARGIN
        when {
            r.headHeight < DvRules.HEAD_MIN + m -> out.add(Check(CheckId.HEAD_SIZE, Status.FAIL, Hint.MOVE_CLOSER, r.headHeight))
            r.headHeight > DvRules.HEAD_MAX - m -> out.add(Check(CheckId.HEAD_SIZE, Status.FAIL, Hint.MOVE_BACK, r.headHeight))
            else -> out.add(Check(CheckId.HEAD_SIZE, Status.PASS, Hint.OK, r.headHeight))
        }

        val off = r.centerX - 0.5f
        val shown = if (mirrored) -off else off // + means the face appears right of centre on screen
        when {
            abs(off) <= DvRules.MAX_OFF_CENTER -> pass(CheckId.CENTER)
            shown > 0 -> fail(CheckId.CENTER, Hint.MOVE_LEFT)
            else -> fail(CheckId.CENTER, Hint.MOVE_RIGHT)
        }

        when {
            r.eyeFromBottom < DvRules.EYE_MIN + m -> out.add(Check(CheckId.EYE_LINE, Status.FAIL, Hint.EYES_LOW, r.eyeFromBottom))
            r.eyeFromBottom > DvRules.EYE_MAX - m -> out.add(Check(CheckId.EYE_LINE, Status.FAIL, Hint.EYES_HIGH, r.eyeFromBottom))
            else -> out.add(Check(CheckId.EYE_LINE, Status.PASS, Hint.OK, r.eyeFromBottom))
        }

        when {
            abs(r.roll) > DvRules.MAX_ROLL -> fail(CheckId.POSE, Hint.TILT_HEAD)
            abs(r.yaw) > DvRules.MAX_YAW -> fail(CheckId.POSE, Hint.TURNED)
            abs(r.pitch) > DvRules.MAX_PITCH -> fail(CheckId.POSE, Hint.CHIN_LEVEL)
            else -> pass(CheckId.POSE)
        }

        r.eyesOpen?.let {
            if (it < DvRules.MIN_EYES_OPEN) fail(CheckId.EYES_OPEN, Hint.EYES_CLOSED) else pass(CheckId.EYES_OPEN)
        }
        r.smile?.let {
            if (it > DvRules.MAX_SMILE) add(CheckId.EXPRESSION, Status.WARN, Hint.SMILING) else pass(CheckId.EXPRESSION)
        }

        if (s != null) {
            when {
                s.faceLuma < DvRules.FACE_LUMA_MIN -> fail(CheckId.LIGHTING, Hint.TOO_DARK)
                s.faceLuma > DvRules.FACE_LUMA_MAX -> fail(CheckId.LIGHTING, Hint.TOO_BRIGHT)
                s.lightAsymmetry > DvRules.MAX_LIGHT_ASYMMETRY -> fail(CheckId.LIGHTING, Hint.UNEVEN_LIGHT)
                else -> pass(CheckId.LIGHTING)
            }
            when {
                s.bgLuma < DvRules.BG_LUMA_MIN -> fail(CheckId.BACKGROUND, Hint.NOT_LIGHT)
                s.bgStd > DvRules.BG_STD_MAX || s.bgOutliers > DvRules.BG_OUTLIER_MAX || s.bgEdges > DvRules.BG_EDGE_MAX -> fail(CheckId.BACKGROUND, Hint.NOT_PLAIN)
                s.bgChroma != null && s.bgChroma > DvRules.BG_CHROMA_MAX -> fail(CheckId.BACKGROUND, Hint.NOT_NEUTRAL)
                else -> pass(CheckId.BACKGROUND)
            }
            s.sharpness?.let {
                if (it < DvRules.MIN_SHARPNESS) fail(CheckId.SHARPNESS, Hint.BLURRY) else pass(CheckId.SHARPNESS)
            }
        }

        sidePx?.let {
            if (it < DvRules.MIN_SIDE_PX) fail(CheckId.RESOLUTION, Hint.LOW_RES) else pass(CheckId.RESOLUTION)
        }
        return out
    }

    /** Check of the encoded output file itself. */
    fun fileCheck(bytes: Int, sidePx: Int): Check = when {
        bytes > DvRules.MAX_FILE_BYTES -> Check(CheckId.FILE, Status.FAIL, Hint.FILE_TOO_LARGE)
        sidePx !in DvRules.MIN_SIDE_PX..DvRules.MAX_SIDE_PX -> Check(CheckId.FILE, Status.FAIL, Hint.FILE_SIZE_RANGE)
        else -> Check(CheckId.FILE, Status.PASS, Hint.OK)
    }

    /** The most important thing to tell the user right now, or null when everything passes. */
    fun firstProblem(checks: List<Check>): Check? =
        checks.filter { it.status == Status.FAIL }.minByOrNull { it.id.ordinal }
}
