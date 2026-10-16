package androidx.camera.core

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Size
import androidx.camera.core.resolutionselector.ResolutionSelector
import com.google.common.util.concurrent.ListenableFuture
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
annotation class ExperimentalGetImage

interface ImageInfo { val rotationDegrees: Int }

interface ImageProxy : AutoCloseable {
    interface PlaneProxy { val rowStride: Int; val pixelStride: Int; val buffer: ByteBuffer }
    val cropRect: Rect
    val width: Int
    val height: Int
    val planes: Array<PlaneProxy>
    val imageInfo: ImageInfo
    @ExperimentalGetImage val image: android.media.Image?
    fun toBitmap(): Bitmap
    override fun close()
}

interface ExtendableBuilder<T> { fun build(): T }

abstract class UseCase

class ImageAnalysis private constructor() : UseCase() {
    fun interface Analyzer { fun analyze(image: ImageProxy) }
    fun setAnalyzer(executor: Executor, analyzer: Analyzer) {}

    class Builder : ExtendableBuilder<ImageAnalysis> {
        fun setResolutionSelector(selector: ResolutionSelector): Builder = this
        fun setBackpressureStrategy(strategy: Int): Builder = this
        fun setOutputImageFormat(format: Int): Builder = this
        override fun build(): ImageAnalysis = ImageAnalysis()
    }

    companion object {
        const val STRATEGY_KEEP_ONLY_LATEST = 0
        const val OUTPUT_IMAGE_FORMAT_YUV_420_888 = 1
    }
}

class Preview private constructor() : UseCase() {
    fun interface SurfaceProvider { fun onSurfaceRequested(request: Any) }
    var surfaceProvider: SurfaceProvider? = null

    class Builder : ExtendableBuilder<Preview> {
        fun setResolutionSelector(selector: ResolutionSelector): Builder = this
        override fun build(): Preview = Preview()
    }
}

class ImageCaptureException : Exception()

class ImageCapture private constructor() : UseCase() {
    abstract class OnImageCapturedCallback {
        open fun onCaptureSuccess(image: ImageProxy) {}
        open fun onError(exception: ImageCaptureException) {}
    }

    fun takePicture(executor: Executor, callback: OnImageCapturedCallback) {}

    class Builder : ExtendableBuilder<ImageCapture> {
        fun setCaptureMode(mode: Int): Builder = this
        fun setFlashMode(mode: Int): Builder = this
        fun setOutputFormat(format: Int): Builder = this
        fun setResolutionSelector(selector: ResolutionSelector): Builder = this
        override fun build(): ImageCapture = ImageCapture()
    }

    companion object {
        const val CAPTURE_MODE_MAXIMIZE_QUALITY = 0
        const val CAPTURE_MODE_MINIMIZE_LATENCY = 1
        const val FLASH_MODE_OFF = 2
        const val OUTPUT_FORMAT_JPEG = 0
    }
}

class CameraSelector private constructor() {
    companion object {
        @JvmField val DEFAULT_FRONT_CAMERA = CameraSelector()
        @JvmField val DEFAULT_BACK_CAMERA = CameraSelector()
    }
}

abstract class MeteringPoint
abstract class MeteringPointFactory { fun createPoint(x: Float, y: Float): MeteringPoint = object : MeteringPoint() {} }
class FocusMeteringResult

class FocusMeteringAction private constructor() {
    class Builder(point: MeteringPoint, mode: Int) {
        fun setAutoCancelDuration(duration: Long, timeUnit: TimeUnit): Builder = this
        fun build(): FocusMeteringAction = FocusMeteringAction()
    }
    companion object { const val FLAG_AF = 1; const val FLAG_AE = 2; const val FLAG_AWB = 4 }
}

interface CameraControl { fun startFocusAndMetering(action: FocusMeteringAction): ListenableFuture<FocusMeteringResult> }
interface Camera { val cameraControl: CameraControl }

class ViewPort

class UseCaseGroup private constructor() {
    class Builder {
        fun addUseCase(useCase: UseCase): Builder = this
        fun setViewPort(viewPort: ViewPort): Builder = this
        fun build(): UseCaseGroup = UseCaseGroup()
    }
}
