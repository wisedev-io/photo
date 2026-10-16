package app.dvphoto.ui

import androidx.compose.ui.graphics.ImageBitmap
import app.dvphoto.core.Check
import app.dvphoto.core.FaceReading
import app.dvphoto.core.PhotoRole
import app.dvphoto.core.SceneStats
import app.dvphoto.core.Status

/** Plain state holders the screens render. Nothing here depends on Android, so the harness can render them. */

class RecentItem(val id: Long, val uri: String, val name: String, val passed: Boolean)

class LiveUi(
    val reading: FaceReading? = null,
    val checks: List<Check> = emptyList(),
    val progress: Float = 0f,
    val scene: SceneStats? = null,
)

enum class SaveFolder { GALLERY, DOWNLOADS }

class ResultUi(
    val preview: ImageBitmap,
    val side: Int,
    val sizeBytes: Int,
    val checks: List<Check>,
    val fromUpload: Boolean,
    val autoCropped: Boolean,
) {
    val ok: Boolean = checks.none { it.status == Status.FAIL }
    val warnings: Int = checks.count { it.status == Status.WARN }
    val failures: Int = checks.count { it.status == Status.FAIL }
}

class NameUi(
    val text: String,
    val role: PhotoRole,
    val folder: SaveFolder,
    /** Final file name including extension, after de-duplication, e.g. "DV_Applicant (1).jpg". */
    val finalName: String,
    val needsAscii: Boolean,
    val saving: Boolean,
    val errorRes: Int? = null,
)

class SavedUi(
    val fileName: String,
    val locationLabel: String,
    val sizeKb: Int,
    val preview: ImageBitmap,
)

class LanguageOption(val tag: String, val label: String)

class SettingsUi(
    val languageTag: String,
    val languages: List<LanguageOption>,
    val autoCapture: Boolean,
    val voice: Boolean,
    val measurements: Boolean,
    val versionName: String,
)
