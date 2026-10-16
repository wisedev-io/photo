package app.dvphoto

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.dvphoto.core.CaptureGate
import app.dvphoto.core.Check
import app.dvphoto.core.CheckId
import app.dvphoto.core.Hint
import app.dvphoto.core.Dates
import app.dvphoto.core.FaceGeometry
import app.dvphoto.core.FaceReading
import app.dvphoto.core.FileNaming
import app.dvphoto.core.MetadataScanner
import app.dvphoto.core.PhotoRole
import app.dvphoto.core.Provenance
import app.dvphoto.core.RawFace
import app.dvphoto.core.SceneAnalyzer
import app.dvphoto.core.SceneStats
import app.dvphoto.core.SmartCrop
import app.dvphoto.core.SquareCrop
import app.dvphoto.core.Status
import app.dvphoto.core.Validator
import app.dvphoto.data.AppDb
import app.dvphoto.data.PhotoRecord
import app.dvphoto.platform.BitmapSource
import app.dvphoto.platform.CameraState
import app.dvphoto.platform.LiveFrame
import app.dvphoto.platform.MlKit
import app.dvphoto.platform.PhotoExporter
import app.dvphoto.platform.PhotoStorage
import app.dvphoto.platform.Prefs
import app.dvphoto.platform.awaitResult
import app.dvphoto.platform.decodeBounded
import app.dvphoto.platform.readExifExtras
import app.dvphoto.platform.readHeader
import app.dvphoto.ui.LiveUi
import app.dvphoto.ui.NameUi
import app.dvphoto.ui.RecentItem
import app.dvphoto.ui.ResultUi
import app.dvphoto.ui.SaveFolder
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

/** A finished photo: the exact JPEG that would be saved, what it looks like, and every check's outcome. */
class ResultData(
    val jpeg: ByteArray,
    val preview: ImageBitmap,
    val side: Int,
    val checks: List<Check>,
    val fromUpload: Boolean,
    val autoCropped: Boolean,
) {
    val ui = ResultUi(preview, side, jpeg.size, checks, fromUpload, autoCropped)
    val ok: Boolean get() = checks.none { it.status == Status.FAIL }
}

class SavedInfo(
    val data: ResultData,
    val fileName: String,
    /** Human-readable folder, or null when the user picked a custom location. */
    val location: String?,
    val uri: Uri?,
)

sealed interface Screen {
    data object Home : Screen
    data object Camera : Screen
    class Busy(@StringRes val label: Int) : Screen
    class Result(val data: ResultData) : Screen
    class Name(val data: ResultData) : Screen
    class Saved(val info: SavedInfo) : Screen
    data object Settings : Screen
    data object Help : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val dao = AppDb.get(app).photos()

    val history: StateFlow<List<PhotoRecord>> =
        dao.recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- navigation / generic state -----------------------------------------------------------

    var screen by mutableStateOf<Screen>(Screen.Home); private set
    var message by mutableStateOf<Int?>(null); private set
    var openRequest by mutableStateOf<String?>(null); private set
    var confirmDiscard by mutableStateOf(false); private set

    // ---- settings (mirrored from Prefs so Compose sees changes) --------------------------------

    var autoCapture by mutableStateOf(prefs.autoCapture); private set
    var voice by mutableStateOf(prefs.voice); private set
    var measurements by mutableStateOf(prefs.measurements); private set
    var languageTag by mutableStateOf(prefs.language); private set

    // ---- live camera --------------------------------------------------------------------------

    var live by mutableStateOf(LiveUi()); private set
    var mirrored by mutableStateOf(prefs.preferFront); private set
    var cameraState by mutableStateOf<CameraState?>(null); private set
    var briefed by mutableStateOf(false)
    var shutterTick by mutableIntStateOf(0); private set

    private var shutterBusy = false
    private val gate = CaptureGate()

    private val liveLazy = lazy { FaceDetection.getClient(MlKit.measureOptions(accurate = false)) }
    private val measureLazy = lazy { FaceDetection.getClient(MlKit.measureOptions(accurate = true)) }
    private val countLazy = lazy { FaceDetection.getClient(MlKit.countOptions()) }
    private val liveDetector: FaceDetector by liveLazy
    private val measureDetector: FaceDetector by measureLazy
    private val countDetector: FaceDetector by countLazy
    fun liveFaceDetector(): FaceDetector = liveDetector

    // ---- name & save ---------------------------------------------------------------------------

    var nameText by mutableStateOf(""); private set
    var role by mutableStateOf(PhotoRole.APPLICANT); private set
    var folder by mutableStateOf(runCatching { SaveFolder.valueOf(prefs.folder) }.getOrDefault(SaveFolder.GALLERY)); private set
    var saving by mutableStateOf(false); private set
    var saveError by mutableStateOf<Int?>(null); private set
    private var existing by mutableStateOf<Set<String>>(emptySet())
    private var nameTouched = false

    // ===========================================================================================
    // Navigation
    // ===========================================================================================

    fun openCamera() { resetLive(); screen = Screen.Camera }
    fun goHome() { resetLive(); confirmDiscard = false; screen = Screen.Home }
    fun openSettings() { screen = Screen.Settings }
    fun openHelp() { screen = Screen.Help }
    fun consumeMessage() { message = null }
    fun consumeOpenRequest() { openRequest = null }
    fun showMessage(@StringRes res: Int) { message = res }

    /** Back button / gesture. Returns true when handled (false lets the system close the app). */
    fun back(): Boolean = when (screen) {
        Screen.Home -> false
        is Screen.Busy -> true
        is Screen.Name -> { confirmDiscard = !confirmDiscard; true } // a second back dismisses the question
        else -> { goHome(); true }
    }

    fun dismissDiscard() { confirmDiscard = false }

    private fun resetLive() {
        shutterBusy = false; gate.reset(); live = LiveUi()
    }

    // ===========================================================================================
    // Settings
    // ===========================================================================================

    fun updateAutoCapture(v: Boolean) { autoCapture = v; prefs.autoCapture = v }
    fun updateVoice(v: Boolean) { voice = v; prefs.voice = v }
    fun updateMeasurements(v: Boolean) { measurements = v; prefs.measurements = v }
    fun updateLanguage(tag: String) { languageTag = tag; prefs.language = tag }

    // ===========================================================================================
    // Live camera
    // ===========================================================================================

    fun onCameraState(state: CameraState?) {
        cameraState = state
        if (state != null) { mirrored = state.front; prefs.preferFront = state.front }
    }

    fun flipCamera() { mirrored = !mirrored; prefs.preferFront = mirrored; resetLive() }

    fun onLiveFrame(f: LiveFrame) {
        if (screen != Screen.Camera || shutterBusy) return
        val checks = Validator.validate(f.reading, f.scene, null, mirrored)
        val r = f.reading
        val ok = r != null && checks.none { it.status == Status.FAIL }
        val progress = gate.update(r, ok, SystemClock.elapsedRealtime())
        val shown = if (autoCapture) progress else 0f
        live = LiveUi(r, checks, shown, f.scene)
        if (shown >= 1f && briefed) requestShutter()
    }

    fun requestShutter() {
        if (shutterBusy) return
        shutterBusy = true
        shutterTick++
    }

    fun onCaptureFailed() { shutterBusy = false; gate.reset(); message = R.string.err_capture }

    /** Takes ownership of [image] (always closed). */
    fun onCaptured(image: ImageProxy) {
        viewModelScope.launch {
            val square = withContext(Dispatchers.Default) {
                try {
                    squareOf(image.toBitmap(), image.imageInfo.rotationDegrees)
                } catch (t: Throwable) {
                    null
                } finally {
                    image.close()
                }
            }
            if (square == null) { onCaptureFailed(); return@launch }
            screen = Screen.Busy(R.string.busy)
            val out = withContext(Dispatchers.Default) {
                PhotoExporter.renderSquare(square, 0, 0, square.width, PhotoExporter.outputSide(square.width)).also { square.recycle() }
            }
            // the picture comes straight from the sensor: only cropped and resized, never filtered
            val result = evaluate(out, fromUpload = false, autoCropped = false, extra = listOf(Check(CheckId.EDITING, Status.PASS, Hint.OK)))
            shutterBusy = false
            route(result)
        }
    }

    /** Rotates upright and takes the centred square (the part the viewfinder showed). */
    private fun squareOf(raw: Bitmap, rotation: Int): Bitmap {
        val upright = if (rotation == 0) raw else {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                .also { if (it !== raw) raw.recycle() }
        }
        if (upright.width == upright.height) return upright
        val crop = SmartCrop.centered(upright.width, upright.height)
        return Bitmap.createBitmap(upright, crop.left, crop.top, crop.side, crop.side).also { upright.recycle() }
    }

    // ===========================================================================================
    // Upload
    // ===========================================================================================

    fun onPicked(uri: Uri) {
        screen = Screen.Busy(R.string.busy)
        viewModelScope.launch {
            val app = getApplication<Application>()
            val src = withContext(Dispatchers.IO) { decodeBounded(app, uri) }
            if (src == null) { message = R.string.err_decode; screen = Screen.Home; return@launch }

            // What the file says about itself: filters, AI, editing apps, date taken
            val provenance = withContext(Dispatchers.IO) {
                val header = readHeader(app, uri)
                var findings = MetadataScanner.scan(header)
                val extra = readExifExtras(app, uri)
                findings = findings.withExtra(extra.software, extra.takenAt)
                Provenance.evaluate(findings, Dates.epochDayOf(System.currentTimeMillis()))
            }

            val (out, auto) = frameUpload(src)
            val result = evaluate(out, fromUpload = true, autoCropped = auto, extra = provenance)
            route(result)
        }
    }

    /** Finds the face in [src], crops the best square around it and renders it at output size. */
    private suspend fun frameUpload(src: Bitmap): Pair<Bitmap, Boolean> {
        val found = detect(src, countOthers = false)
        var crop: SquareCrop = found?.raw?.let { SmartCrop.forFace(src.width, src.height, it) }
            ?: SmartCrop.centered(src.width, src.height)
        var out = withContext(Dispatchers.Default) {
            PhotoExporter.renderSquare(src, crop.left, crop.top, crop.side, PhotoExporter.outputSide(crop.side))
        }
        if (found != null) {
            // Second pass: measure the real head (hair included) in the first crop and re-frame once.
            val first = analyze(out, countOthers = false).reading
            if (first != null) {
                val better = SmartCrop.refine(crop, first, src.width, src.height)
                if (abs(better.side - crop.side) > 8 || abs(better.left - crop.left) > 8 || abs(better.top - crop.top) > 8) {
                    out.recycle()
                    crop = better
                    out = withContext(Dispatchers.Default) {
                        PhotoExporter.renderSquare(src, crop.left, crop.top, crop.side, PhotoExporter.outputSide(crop.side))
                    }
                }
            }
        }
        src.recycle()
        return out to (found != null)
    }

    // ===========================================================================================
    // Analysis
    // ===========================================================================================

    private class Detected(val raw: RawFace, val others: Int)
    private class Analysis(val reading: FaceReading?, val scene: SceneStats?)

    private suspend fun detect(bmp: Bitmap, countOthers: Boolean): Detected? {
        val faces = try {
            measureDetector.process(InputImage.fromBitmap(bmp, 0)).awaitResult()
        } catch (t: Throwable) {
            return null
        }
        val main = MlKit.largest(faces) ?: return null
        val raw = MlKit.toRaw(main) ?: return null
        var others = 0
        if (countOthers) {
            val all = try { countDetector.process(InputImage.fromBitmap(bmp, 0)).awaitResult() } catch (t: Throwable) { emptyList() }
            others = MlKit.otherPeople(all, main)
        }
        return Detected(raw, others)
    }

    private suspend fun analyze(bmp: Bitmap, countOthers: Boolean): Analysis {
        val det = detect(bmp, countOthers) ?: return Analysis(null, null)
        val side = bmp.width
        val first = FaceGeometry.reading(det.raw, 0f, 0f, side.toFloat(), 1 + det.others)
        val result = withContext(Dispatchers.Default) {
            SceneAnalyzer.analyze(BitmapSource(bmp), 0, 0, side, first, withSharpness = true)
        }
        return Analysis(result.reading, result.stats)
    }

    /** Runs every check on [out] (the picture exactly as it would be submitted) and encodes it. */
    private suspend fun evaluate(out: Bitmap, fromUpload: Boolean, autoCropped: Boolean, extra: List<Check>): ResultData {
        val a = analyze(out, countOthers = true)
        val side = out.width
        val enc = withContext(Dispatchers.Default) { PhotoExporter.encode(out) }
        out.recycle()
        val preview = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(enc.bytes, 0, enc.bytes.size) }
        val checks = Validator.validate(a.reading, a.scene, side, mirrored = false) + extra +
            Validator.fileCheck(enc.bytes.size, enc.side)
        return ResultData(enc.bytes, preview.asImageBitmap(), enc.side, checks, fromUpload, autoCropped)
    }

    /** Photos that pass go straight to naming and saving; others show what to fix first. */
    private fun route(r: ResultData) {
        if (r.ok) openName(r) else screen = Screen.Result(r)
    }

    // ===========================================================================================
    // Name & save
    // ===========================================================================================

    fun openName(data: ResultData) {
        nameTouched = false
        saveError = null
        saving = false
        role = PhotoRole.APPLICANT
        nameText = role.base
        screen = Screen.Name(data)
        refreshExisting(pickNextRole = true)
    }

    private fun refreshExisting(pickNextRole: Boolean = false) {
        viewModelScope.launch {
            val names = withContext(Dispatchers.IO) { PhotoStorage.existingNames(getApplication(), folder) }
            existing = names
            if (pickNextRole && !nameTouched) {
                // family flow: suggest the first role whose file does not exist yet
                val next = FileNaming.nextRole(names)
                role = next
                nameText = next.base
            }
        }
    }

    fun onNameText(t: String) { nameTouched = true; nameText = t; saveError = null }
    fun onRole(r: PhotoRole) { nameTouched = true; role = r; nameText = r.base; saveError = null }
    fun onFolder(f: SaveFolder) { folder = f; prefs.folder = f.name; refreshExisting() }

    fun useAscii() {
        val ascii = FileNaming.toAscii(nameText)
        nameText = ascii.ifEmpty { role.base }
        nameTouched = true
    }

    fun nameUi(): NameUi {
        val base = FileNaming.unique(nameText, existing)
        return NameUi(
            text = nameText, role = role, folder = folder,
            finalName = "$base.${FileNaming.EXTENSION}",
            needsAscii = !FileNaming.isAsciiSafe(FileNaming.baseOrDefault(nameText)),
            saving = saving, errorRes = saveError,
        )
    }

    /** Saves to the phone (Pictures/DVPhoto or Download/DVPhoto) under the chosen name. */
    fun save() {
        val s = screen as? Screen.Name ?: return
        if (saving) return
        saving = true
        saveError = null
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    PhotoStorage.save(getApplication(), s.data.jpeg, nameText, folder)
                }
                finishSave(s.data, saved.displayName, saved.location, saved.uri)
            } catch (t: Throwable) {
                saveError = R.string.err_save
            } finally {
                saving = false
            }
        }
    }

    /** Writes into a document chosen with the system file picker. */
    fun saveAs(uri: Uri) {
        val s = screen as? Screen.Name ?: return
        if (saving) return
        saving = true
        saveError = null
        viewModelScope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    PhotoStorage.writeTo(getApplication(), uri, s.data.jpeg)
                    PhotoStorage.displayNameOf(getApplication(), uri)
                } ?: "${FileNaming.baseOrDefault(nameText)}.${FileNaming.EXTENSION}"
                finishSave(s.data, name, null, uri)
            } catch (t: Throwable) {
                saveError = R.string.err_save
            } finally {
                saving = false
            }
        }
    }

    fun onStoragePermissionResult(granted: Boolean) {
        if (granted) save() else saveError = R.string.err_storage_permission
    }

    private suspend fun finishSave(data: ResultData, fileName: String, location: String?, uri: Uri) {
        dao.insert(
            PhotoRecord(
                uri = uri.toString(), name = fileName, createdAt = System.currentTimeMillis(), sizeBytes = data.jpeg.size,
                sidePx = data.side, source = if (data.fromUpload) "UPLOAD" else "CAMERA", role = role.name,
                folder = folder.name, passed = data.ok,
            )
        )
        screen = Screen.Saved(SavedInfo(data, fileName, location, uri))
    }

    // ===========================================================================================
    // History
    // ===========================================================================================

    fun recentItems(records: List<PhotoRecord>): List<RecentItem> = records.map { RecentItem(it.id, it.uri, it.name, it.passed) }

    /** Opens a saved photo, forgetting it when the file has been deleted from the phone. */
    fun openRecent(item: RecentItem) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val exists = withContext(Dispatchers.IO) {
                runCatching { app.contentResolver.openFileDescriptor(Uri.parse(item.uri), "r")?.close(); true }.getOrDefault(false)
            }
            if (exists) openRequest = item.uri else { dao.delete(item.id); message = R.string.err_missing }
        }
    }

    // ===========================================================================================

    override fun onCleared() {
        if (liveLazy.isInitialized()) runCatching { liveDetector.close() }
        if (measureLazy.isInitialized()) runCatching { measureDetector.close() }
        if (countLazy.isInitialized()) runCatching { countDetector.close() }
    }

}
