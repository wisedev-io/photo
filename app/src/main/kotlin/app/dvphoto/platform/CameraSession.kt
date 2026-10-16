package app.dvphoto.platform

import android.content.Context
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExtendableBuilder
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** What the camera hardware offers and what was actually started. */
class CameraState(
    val hasFront: Boolean,
    val hasBack: Boolean,
    /** True when the camera that was bound is the front one. */
    val front: Boolean,
    /** False when the device could not run preview + analysis + capture together (manual mode). */
    val liveAnalysis: Boolean,
)

/**
 * CameraX wiring for a still portrait.
 *
 * "No filters" is enforced here, at the source:
 *  - no CameraX Extensions are ever requested (those are the OEM HDR / Night / Bokeh / Face-Retouch modes);
 *  - every use case pins the Camera2 effect mode to OFF and the scene mode to DISABLED under plain AUTO control;
 *  - flash is off and the output is a standard SDR JPEG (never Ultra HDR);
 *  - the app itself never touches pixels except to crop and resize.
 */
class CameraSession(private val context: Context, private val analyzer: ImageAnalysis.Analyzer) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var capture: ImageCapture? = null
    private var camera: Camera? = null
    private var released = false

    /** Binds the camera to [owner] once [view] has been laid out. [onState] is called on the main thread. */
    fun bind(owner: LifecycleOwner, view: PreviewView, wantFront: Boolean, onState: (CameraState?) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (released) return@addListener
            val p = try { future.get() } catch (t: Throwable) { onState(null); return@addListener }
            provider = p
            val hasFront = safeHas(p, CameraSelector.DEFAULT_FRONT_CAMERA)
            val hasBack = safeHas(p, CameraSelector.DEFAULT_BACK_CAMERA)
            if (!hasFront && !hasBack) { onState(null); return@addListener }
            val front = if (wantFront) hasFront else !hasBack
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            view.doOnLayout {
                if (released) return@doOnLayout
                val live = bindUseCases(p, owner, view, selector)
                if (live == null) onState(null) else onState(CameraState(hasFront, hasBack, front, live))
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun safeHas(p: ProcessCameraProvider, selector: CameraSelector): Boolean =
        try { p.hasCamera(selector) } catch (t: Throwable) { false }

    /** @return true when everything is live, false for capture-only mode, null when nothing could be bound. */
    private fun bindUseCases(p: ProcessCameraProvider, owner: LifecycleOwner, view: PreviewView, selector: CameraSelector): Boolean? {
        p.unbindAll()
        val preview = Preview.Builder()
            .setResolutionSelector(selector43(1280, 960))
            .also { pinNoFilters(it) }
            .build()
        preview.surfaceProvider = view.surfaceProvider

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(selector43(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .also { pinNoFilters(it) }
            .build()
        analysis.setAnalyzer(executor, analyzer)

        val still = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG)
            .setResolutionSelector(selector43(2048, 1536))
            .also { pinNoFilters(it) }
            .build()

        // 1) everything together, cropped to exactly what the square viewfinder shows
        try {
            camera = p.bindToLifecycle(owner, selector, group(view, preview, analysis, still))
            capture = still
            return true
        } catch (t: Throwable) {
            p.unbindAll()
        }
        // 2) old hardware that cannot stream three outputs at once: preview + still only (manual shutter)
        return try {
            val preview2 = Preview.Builder().setResolutionSelector(selector43(1280, 960)).also { pinNoFilters(it) }.build()
            preview2.surfaceProvider = view.surfaceProvider
            val still2 = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG)
                .setResolutionSelector(selector43(1600, 1200))
                .also { pinNoFilters(it) }
                .build()
            camera = p.bindToLifecycle(owner, selector, group(view, preview2, null, still2))
            capture = still2
            false
        } catch (t: Throwable) {
            p.unbindAll()
            null
        }
    }

    private fun group(view: PreviewView, vararg cases: UseCase?): UseCaseGroup {
        val b = UseCaseGroup.Builder()
        cases.filterNotNull().forEach { b.addUseCase(it) }
        view.viewPort?.let { b.setViewPort(it) }
        return b.build()
    }

    /** 4:3 frames no larger than [w] x [h] (landscape sensor orientation); a smaller size is used if needed. */
    private fun selector43(w: Int, h: Int): ResolutionSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(ResolutionStrategy(Size(w, h), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
        .build()

    /** Pins Camera2 to "no effects, no scene mode" so nothing can colour, smooth or relight the picture. */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun <T> pinNoFilters(builder: ExtendableBuilder<T>) {
        Camera2Interop.Extender(builder)
            .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            .setCaptureRequestOption(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_DISABLED)
    }

    /** Takes one picture. [onOk] owns the returned image and must close it. */
    fun takePicture(onOk: (ImageProxy) -> Unit, onFail: () -> Unit) {
        val c = capture
        if (c == null) { onFail(); return }
        c.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) = onOk(image)
            override fun onError(exception: ImageCaptureException) = onFail()
        })
    }

    /** Focus and meter exposure on the face (a bright white wall otherwise darkens it). [x],[y] are view pixels. */
    fun meterAt(view: PreviewView, x: Float, y: Float) {
        val control = camera?.cameraControl ?: return
        runCatching {
            val point = view.meteringPointFactory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
            control.startFocusAndMetering(action)
        }
    }

    fun release() {
        released = true
        runCatching { provider?.unbindAll() }
        executor.shutdown()
        capture = null
        camera = null
    }
}
