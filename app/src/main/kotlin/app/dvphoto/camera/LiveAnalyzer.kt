package app.dvphoto.camera

import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.dvphoto.core.FaceGeometry
import app.dvphoto.core.FaceReading
import app.dvphoto.core.PixelSource
import app.dvphoto.core.SceneAnalyzer
import app.dvphoto.core.SceneStats
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector
import java.nio.ByteBuffer
import kotlin.math.min

class LiveFrame(val reading: FaceReading?, val scene: SceneStats?)

/**
 * Throttled (~6 fps) live analysis of the centre square of each camera frame.
 * Only the Y (luma) plane is touched - no bitmaps are allocated per frame.
 */
class LiveAnalyzer(
    private val detector: FaceDetector,
    private val onFrame: (LiveFrame) -> Unit,
) : ImageAnalysis.Analyzer {
    private var last = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val media = image.image
        if (now - last < MIN_INTERVAL_MS || media == null) { image.close(); return }
        last = now

        val rot = image.imageInfo.rotationDegrees
        val upW = if (rot % 180 == 0) image.width else image.height
        val upH = if (rot % 180 == 0) image.height else image.width
        val side = min(upW, upH)
        val fx = (upW - side) / 2
        val fy = (upH - side) / 2
        val luma = YPlane(image.planes[0].buffer, image.planes[0].rowStride, image.width, image.height, rot)

        detector.process(InputImage.fromMediaImage(media, rot))
            .addOnSuccessListener { faces ->
                val face = MlKit.largest(faces)
                val raw = face?.let { MlKit.toRaw(it) }
                if (raw == null) {
                    onFrame(LiveFrame(null, null))
                } else {
                    val r = FaceGeometry.reading(raw, fx.toFloat(), fy.toFloat(), side.toFloat(), faces.size)
                    val scene = SceneAnalyzer.analyze(luma, fx, fy, side, r, withSharpness = false)
                    onFrame(LiveFrame(r, scene))
                }
            }
            .addOnCompleteListener { image.close() }
    }

    private companion object { const val MIN_INTERVAL_MS = 150L }
}

/** Upright view onto a camera Y plane, undoing the sensor rotation on the fly. */
private class YPlane(
    private val buf: ByteBuffer,
    private val rowStride: Int,
    private val w: Int,
    private val h: Int,
    private val rotation: Int,
) : PixelSource {
    override val width = if (rotation % 180 == 0) w else h
    override val height = if (rotation % 180 == 0) h else w

    override fun luma(x: Int, y: Int): Int {
        val sx: Int
        val sy: Int
        when (rotation) {
            90 -> { sx = y; sy = h - 1 - x }
            180 -> { sx = w - 1 - x; sy = h - 1 - y }
            270 -> { sx = w - 1 - y; sy = x }
            else -> { sx = x; sy = y }
        }
        return buf.get(sy.coerceIn(0, h - 1) * rowStride + sx.coerceIn(0, w - 1)).toInt() and 0xFF
    }
}
