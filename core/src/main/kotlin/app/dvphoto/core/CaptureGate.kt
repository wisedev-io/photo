package app.dvphoto.core

import kotlin.math.abs

/**
 * Decides when the camera may fire on its own: every check must pass AND the face must stay put for [holdMs].
 * Movement, a failing check or losing the face restarts the countdown.
 */
class CaptureGate(private val holdMs: Long = 1200L, private val steadyTolerance: Float = 0.02f) {
    private var since = UNSET
    private var prev: FaceReading? = null

    fun reset() { since = UNSET; prev = null }

    /** @return countdown progress in 0..1; 1 means "take the picture now". */
    fun update(reading: FaceReading?, allPass: Boolean, nowMillis: Long): Float {
        val before = prev
        prev = reading
        if (reading == null || !allPass) { since = UNSET; return 0f }
        if (since == UNSET || !steady(before, reading)) since = nowMillis
        return ((nowMillis - since).toFloat() / holdMs).coerceIn(0f, 1f)
    }

    private companion object { const val UNSET = Long.MIN_VALUE }

    private fun steady(a: FaceReading?, b: FaceReading): Boolean = a != null &&
        abs(a.centerX - b.centerX) < steadyTolerance &&
        abs(a.headTop - b.headTop) < steadyTolerance &&
        abs(a.chin - b.chin) < steadyTolerance
}
