package app.dvphoto.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GateAndRoleTest {
    private fun face(cx: Float = 0.5f, top: Float = 0.105f, chin: Float = 0.705f) =
        FaceReading(1, top, chin, 0.375f, cx, 0.43f, 0f, 0f, 0f, 1f, 0f)

    @Test fun firesOnlyAfterTheFaceHasBeenSteadyAndGreenLongEnough() {
        val gate = CaptureGate(holdMs = 1000)
        assertEquals(0f, gate.update(face(), true, 10_000), 0f)          // countdown starts
        assertEquals(0.5f, gate.update(face(), true, 10_500), 0.001f)
        assertEquals(1f, gate.update(face(), true, 11_000), 0.001f)
        assertEquals(1f, gate.update(face(), true, 11_300), 0.001f)       // stays at 1 (clamped)
    }

    @Test fun movementRestartsTheCountdown() {
        val gate = CaptureGate(holdMs = 1000)
        gate.update(face(), true, 0)
        assertEquals(0.4f, gate.update(face(), true, 400), 0.001f)
        // a sudden 10% shift
        assertEquals(0f, gate.update(face(cx = 0.6f), true, 600), 0.001f)
        assertEquals(0.3f, gate.update(face(cx = 0.6f), true, 900), 0.001f)
        // slow drift inside the tolerance does not restart it
        assertEquals(0.5f, gate.update(face(cx = 0.61f), true, 1100), 0.001f)
    }

    @Test fun anyFailureOrLostFaceResets() {
        val gate = CaptureGate(holdMs = 1000)
        gate.update(face(), true, 0)
        assertEquals(0.5f, gate.update(face(), true, 500), 0.001f)
        assertEquals(0f, gate.update(face(), false, 600), 0f)             // a check failed
        assertEquals(0f, gate.update(face(), true, 700), 0f)              // restarts
        assertEquals(0.5f, gate.update(face(), true, 1200), 0.001f)
        assertEquals(0f, gate.update(null, false, 1300), 0f)              // face lost
        assertEquals(0f, gate.update(face(), true, 1400), 0f)
    }

    @Test fun resetForgetsEverything() {
        val gate = CaptureGate(holdMs = 1000)
        gate.update(face(), true, 0)
        gate.update(face(), true, 900)
        gate.reset()
        assertEquals(0f, gate.update(face(), true, 950), 0f)
    }

    @Test fun nextRoleFollowsTheFamilyOrder() {
        assertEquals(PhotoRole.APPLICANT, FileNaming.nextRole(emptyList()))
        assertEquals(PhotoRole.SPOUSE, FileNaming.nextRole(listOf("DV_Applicant.jpg")))
        assertEquals(PhotoRole.SPOUSE, FileNaming.nextRole(listOf("dv_applicant.JPG", "holiday.jpg")))
        assertEquals(PhotoRole.CHILD1, FileNaming.nextRole(listOf("DV_Applicant.jpg", "DV_Spouse.jpg")))
        // gaps are filled first: the spouse was deleted
        assertEquals(PhotoRole.SPOUSE, FileNaming.nextRole(listOf("DV_Applicant.jpg", "DV_Child1.jpg")))
        val all = PhotoRole.values().filter { it != PhotoRole.OTHER }.map { FileNaming.fileName(it.base) }
        assertEquals(PhotoRole.OTHER, FileNaming.nextRole(all))
    }
}
