package app.dvphoto.platform

import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.dvphoto.core.FaceGeometry
import app.dvphoto.core.FaceReading
import app.dvphoto.core.FrameGeometry
import app.dvphoto.core.PixelSource
import app.dvphoto.core.PxRect
import app.dvphoto.core.SceneAnalyzer
import app.dvphoto.core.SceneStats
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector
import java.nio.ByteBuffer

class LiveFrame(val reading: FaceReading?, val scene: SceneStats?)

/**
 * Throttled (~6 fps) live analysis of the square the user sees in the viewfinder.
 * Only the Y (luma) plane is read - no bitmaps are allocated per frame.
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
        val crop = image.cropRect
        val raw = if (crop.isEmpty) PxRect(0, 0, image.width, image.height)
        else PxRect(crop.left, crop.top, crop.right, crop.bottom)
        // the viewfinder shows the centred square of the visible (upright) region
        val square = FrameGeometry.centeredSquare(FrameGeometry.rotateRect(raw, image.width, image.height, rot))
        val side = square.width
        val plane = image.planes[0]
        val luma = YPlane(plane.buffer, plane.rowStride, image.width, image.height, rot)

        try {
            detector.process(InputImage.fromMediaImage(media, rot))
                .addOnSuccessListener { faces ->
                    // an exception here would crash the app (it runs on the main looper): degrade to "no face"
                    val frame = try {
                        val rawFace = MlKit.largest(faces)?.let { MlKit.toRaw(it) }
                        if (rawFace == null || side <= 0) {
                            LiveFrame(null, null)
                        } else {
                            val r = FaceGeometry.reading(rawFace, square.left.toFloat(), square.top.toFloat(), side.toFloat(), faces.size)
                            val result = SceneAnalyzer.analyze(luma, square.left, square.top, side, r, withSharpness = false)
                            LiveFrame(result.reading, result.stats)
                        }
                    } catch (t: Throwable) {
                        LiveFrame(null, null)
                    }
                    onFrame(frame)
                }
                .addOnCompleteListener { image.close() }
        } catch (t: Throwable) {
            // never leave a frame open: the camera would stop delivering new ones
            image.close()
        }
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
        val ux = x.coerceIn(0, width - 1)
        val uy = y.coerceIn(0, height - 1)
        val sx: Int
        val sy: Int
        when (rotation) {
            90 -> { sx = uy; sy = h - 1 - ux }
            180 -> { sx = w - 1 - ux; sy = h - 1 - uy }
            270 -> { sx = w - 1 - uy; sy = ux }
            else -> { sx = ux; sy = uy }
        }
        val index = sy * rowStride + sx
        return if (index in 0 until buf.limit()) buf.get(index).toInt() and 0xFF else 0
    }
}
