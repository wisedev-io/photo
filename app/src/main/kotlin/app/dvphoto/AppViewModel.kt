package app.dvphoto

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.dvphoto.camera.LiveFrame
import app.dvphoto.camera.MlKit
import app.dvphoto.camera.awaitResult
import app.dvphoto.core.Check
import app.dvphoto.core.FaceGeometry
import app.dvphoto.core.FaceReading
import app.dvphoto.core.RawFace
import app.dvphoto.core.SceneAnalyzer
import app.dvphoto.core.SmartCrop
import app.dvphoto.core.SquareCrop
import app.dvphoto.core.Status
import app.dvphoto.core.Validator
import app.dvphoto.data.AppDb
import app.dvphoto.data.PhotoRecord
import app.dvphoto.imaging.BitmapSource
import app.dvphoto.imaging.PhotoExporter
import app.dvphoto.imaging.decodeBounded
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Source { CAMERA, UPLOAD }

class LiveState(
    val reading: FaceReading? = null,
    val checks: List<Check> = emptyList(),
    val progress: Float = 0f,
)

class ResultData(
    val jpeg: ByteArray,
    val preview: Bitmap,
    val side: Int,
    val checks: List<Check>,
    val source: Source,
    val autoCropped: Boolean,
) {
    var savedUri by mutableStateOf<Uri?>(null)
    val ok: Boolean get() = checks.none { it.status == Status.FAIL }
}

sealed interface Screen {
    data object Home : Screen
    data object Camera : Screen
    data object Busy : Screen
    class Result(val data: ResultData) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).photos()

    val history: StateFlow<List<PhotoRecord>> =
        dao.recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var screen by mutableStateOf<Screen>(Screen.Home); private set
    var live by mutableStateOf(LiveState()); private set
    var mirrored by mutableStateOf(true); private set
    var message by mutableStateOf<Int?>(null); private set
    var shutterTick by mutableIntStateOf(0); private set

    private var shutterBusy = false
    private var passSince = 0L
    private var prev: FaceReading? = null

    private val detectorLazy = lazy { FaceDetection.getClient(MlKit.options(accurate = false)) }
    val liveDetector: FaceDetector by detectorLazy

    // ---- navigation -------------------------------------------------------------------------

    fun openCamera() { resetLive(); screen = Screen.Camera }
    fun goHome() { resetLive(); screen = Screen.Home }
    fun setMirrored(value: Boolean) { mirrored = value; resetLive() }
    fun consumeMessage() { message = null }

    private fun resetLive() {
        shutterBusy = false; passSince = 0L; prev = null; live = LiveState()
    }

    // ---- live camera ------------------------------------------------------------------------

    fun onLiveFrame(f: LiveFrame) {
        if (screen != Screen.Camera || shutterBusy) return
        val checks = Validator.validate(f.reading, f.scene, null, mirrored)
        val r = f.reading
        val ok = r != null && checks.none { it.status == Status.FAIL }
        val now = SystemClock.elapsedRealtime()
        if (!ok || r == null) passSince = 0L
        else if (passSince == 0L || !steady(prev, r)) passSince = now
        prev = r
        val progress = if (passSince == 0L) 0f else ((now - passSince) / HOLD_MS.toFloat()).coerceIn(0f, 1f)
        live = LiveState(r, checks, progress)
        if (progress >= 1f) requestShutter()
    }

    private fun steady(a: FaceReading?, b: FaceReading) = a != null &&
        abs(a.centerX - b.centerX) < 0.02f && abs(a.headTop - b.headTop) < 0.02f && abs(a.chin - b.chin) < 0.02f

    fun requestShutter() {
        if (shutterBusy) return
        shutterBusy = true
        shutterTick++
    }

    fun onCaptureFailed() { shutterBusy = false; passSince = 0L; message = R.string.err_capture }

    fun onCaptured(image: ImageProxy) {
        viewModelScope.launch {
            val square = withContext(Dispatchers.Default) {
                try {
                    val rotation = image.imageInfo.rotationDegrees
                    squareOf(image.toBitmap(), rotation)
                } catch (t: Throwable) {
                    null
                } finally {
                    image.close()
                }
            }
            if (square == null) { onCaptureFailed(); return@launch }
            screen = Screen.Busy
            val result = evaluate(square, Source.CAMERA, autoCropped = false)
            shutterBusy = false
            screen = Screen.Result(result)
        }
    }

    private fun squareOf(raw: Bitmap, rotation: Int): Bitmap {
        val upright = if (rotation == 0) raw else {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                .also { raw.recycle() }
        }
        if (upright.width == upright.height) return upright
        val crop = SmartCrop.centered(upright.width, upright.height)
        return Bitmap.createBitmap(upright, crop.left, crop.top, crop.side, crop.side).also { upright.recycle() }
    }

    // ---- upload -----------------------------------------------------------------------------

    fun onPicked(uri: Uri) {
        screen = Screen.Busy
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.Default) { decodeBounded(getApplication(), uri) }
            if (bmp == null) { message = R.string.err_decode; screen = Screen.Home; return@launch }
            val (square, auto) = withContext(Dispatchers.Default) { cropForUpload(bmp) }
            screen = Screen.Result(evaluate(square, Source.UPLOAD, auto))
        }
    }

    private suspend fun cropForUpload(bmp: Bitmap): Pair<Bitmap, Boolean> {
        val found = detect(bmp)
        val crop: SquareCrop = found?.first?.let { SmartCrop.forFace(bmp.width, bmp.height, it) }
            ?: SmartCrop.centered(bmp.width, bmp.height)
        val sq = Bitmap.createBitmap(bmp, crop.left, crop.top, crop.side, crop.side)
        if (sq !== bmp) bmp.recycle()
        return sq to (found != null)
    }

    // ---- analysis ---------------------------------------------------------------------------

    /** Returns the most prominent face (in the bitmap's pixel space) and the number of faces, or null. */
    private suspend fun detect(bmp: Bitmap): Pair<RawFace, Int>? {
        val detector = FaceDetection.getClient(MlKit.options(accurate = true))
        return try {
            val faces = detector.process(InputImage.fromBitmap(bmp, 0)).awaitResult()
            val raw = MlKit.largest(faces)?.let { MlKit.toRaw(it) }
            raw?.let { it to faces.size }
        } catch (t: Throwable) {
            null
        } finally {
            detector.close()
        }
    }

    private suspend fun evaluate(square: Bitmap, source: Source, autoCropped: Boolean): ResultData {
        val side = square.width
        val found = detect(square)
        return withContext(Dispatchers.Default) {
            val reading = found?.let { FaceGeometry.reading(it.first, 0f, 0f, side.toFloat(), it.second) }
            val scene = reading?.let { SceneAnalyzer.analyze(BitmapSource(square), 0, 0, side, it, withSharpness = true) }
            val checks = Validator.validate(reading, scene, side, mirrored = false)
            val enc = PhotoExporter.encode(square)
            square.recycle()
            val preview = BitmapFactory.decodeByteArray(enc.bytes, 0, enc.bytes.size)
            ResultData(
                jpeg = enc.bytes,
                preview = preview,
                side = enc.side,
                checks = checks + Validator.fileCheck(enc.bytes.size, enc.side),
                source = source,
                autoCropped = autoCropped,
            )
        }
    }

    // ---- saving -----------------------------------------------------------------------------

    /** Saves to Pictures/DVPhoto once, records it, then calls [then] with the saved Uri. */
    fun save(r: ResultData, then: (Uri) -> Unit = {}) {
        viewModelScope.launch {
            val existing = r.savedUri
            if (existing != null) { then(existing); return@launch }
            val uri = withContext(Dispatchers.IO) { PhotoExporter.saveToGallery(getApplication(), r.jpeg) }
            if (uri == null) { message = R.string.err_save; return@launch }
            r.savedUri = uri
            dao.insert(
                PhotoRecord(
                    uri = uri.toString(), createdAt = System.currentTimeMillis(), sizeBytes = r.jpeg.size,
                    sidePx = r.side, source = r.source.name, passed = r.ok,
                )
            )
            message = R.string.saved_ok
            then(uri)
        }
    }

    override fun onCleared() {
        if (detectorLazy.isInitialized()) runCatching { liveDetector.close() }
    }

    private companion object { const val HOLD_MS = 1200L }
}
