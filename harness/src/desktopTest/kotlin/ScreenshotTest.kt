import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import app.dvphoto.core.Check
import app.dvphoto.core.CheckId
import app.dvphoto.core.FaceReading
import app.dvphoto.core.Guide
import app.dvphoto.core.Hint
import app.dvphoto.core.PhotoRole
import app.dvphoto.core.Status
import app.dvphoto.ui.*
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotTest {
    private val goodReading = FaceReading(
        faceCount = 1, headTop = Guide.headTop, chin = Guide.chin, eyeY = Guide.eyeY, centerX = 0.5f,
        headWidth = Guide.headWidth * 0.9f, roll = 0f, yaw = 0f, pitch = 0f, eyesOpen = 0.95f, smile = 0.1f, headTopMeasured = true,
    )

    private val passAll = listOf(
        Check(CheckId.FACE, Status.PASS, Hint.OK),
        Check(CheckId.HEAD_SIZE, Status.PASS, Hint.OK, 0.60f),
        Check(CheckId.CENTER, Status.PASS, Hint.OK),
        Check(CheckId.EYE_LINE, Status.PASS, Hint.OK, 0.625f),
        Check(CheckId.POSE, Status.PASS, Hint.OK),
        Check(CheckId.EYES_OPEN, Status.PASS, Hint.OK),
        Check(CheckId.EXPRESSION, Status.PASS, Hint.OK),
        Check(CheckId.LIGHTING, Status.PASS, Hint.OK),
        Check(CheckId.BACKGROUND, Status.PASS, Hint.OK),
        Check(CheckId.SHARPNESS, Status.PASS, Hint.OK),
        Check(CheckId.RESOLUTION, Status.PASS, Hint.OK),
        Check(CheckId.FILE, Status.PASS, Hint.OK),
    )

    private val problems = listOf(
        Check(CheckId.FACE, Status.PASS, Hint.OK),
        Check(CheckId.HEAD_SIZE, Status.FAIL, Hint.MOVE_BACK, 0.72f),
        Check(CheckId.EYE_LINE, Status.PASS, Hint.OK, 0.64f),
        Check(CheckId.BACKGROUND, Status.FAIL, Hint.NOT_PLAIN),
        Check(CheckId.EXPRESSION, Status.WARN, Hint.SMILING),
        Check(CheckId.EDITING, Status.FAIL, Hint.EDIT_APP),
        Check(CheckId.PHOTO_AGE, Status.FAIL, Hint.AGE_OLD, 8f),
        Check(CheckId.FILE, Status.PASS, Hint.OK),
    )

    private fun resultUi(ok: Boolean, upload: Boolean = false) =
        ResultUi(Harness.portrait(), 1000, 187_000, if (ok) passAll else problems, upload, autoCropped = upload)

    private fun nameUi(text: String = "DV_Applicant", ascii: Boolean = false, saving: Boolean = false, err: Int? = null) =
        NameUi(text, PhotoRole.APPLICANT, SaveFolder.GALLERY, "$text.jpg", ascii, saving, err)

    @Composable
    private fun Thumb(item: RecentItem, m: Modifier) = Box(m.background(if (item.passed) Color(0xFF2E7D5B) else Color(0xFF8A3B45)), contentAlignment = Alignment.Center) {
        Text(item.name.take(2), color = Color.White)
    }

    private fun check(png: java.io.File, minInk: Double = 0.02) {
        assertTrue("${png.name} looks blank", Harness.inkRatio(png) > minInk)
    }

    private val recent = listOf(
        RecentItem(1, "u1", "DV_Applicant.jpg", true), RecentItem(2, "u2", "DV_Spouse.jpg", true), RecentItem(3, "u3", "DV_Child1.jpg", false),
    )

    @Test fun home() = check(Harness.render("home", heightDp = 1180) {
        HomeScreen(recent, { i, m -> Thumb(i, m) }, {}, {}, {}, {}, {})
    })

    @Test fun homeEmpty() = check(Harness.render("home_empty", heightDp = 1000) {
        HomeScreen(emptyList(), { i, m -> Thumb(i, m) }, {}, {}, {}, {}, {})
    })

    private fun cameraShot(name: String, live: LiveUi, auto: Boolean = true, measure: Boolean = false, before: Boolean = false) =
        check(Harness.render(name) {
            CameraChrome(live, mirrored = false, canFlip = true, autoCapture = auto, showMeasurements = measure, onClose = {}, onFlip = {}, onShutter = {}) {
                Image(Harness.portrait(900), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            if (before) BeforeYouStartSheet {}
        })

    @Test fun cameraLooking() = cameraShot("camera_looking", LiveUi())

    @Test fun cameraAdjust() = cameraShot(
        "camera_adjust",
        LiveUi(goodReading.copy(headTop = 0.22f, chin = 0.62f, centerX = 0.4f), listOf(
            Check(CheckId.FACE, Status.PASS, Hint.OK), Check(CheckId.HEAD_SIZE, Status.FAIL, Hint.MOVE_CLOSER, 0.40f),
            Check(CheckId.CENTER, Status.FAIL, Hint.MOVE_RIGHT), Check(CheckId.POSE, Status.PASS, Hint.OK),
            Check(CheckId.LIGHTING, Status.PASS, Hint.OK), Check(CheckId.BACKGROUND, Status.PASS, Hint.OK),
        )),
    )

    @Test fun cameraGood() = cameraShot("camera_good", LiveUi(goodReading, passAll.filter { it.id != CheckId.FILE }, progress = 0.65f), measure = true)

    @Test fun cameraManual() = cameraShot("camera_manual", LiveUi(goodReading, passAll, 0f), auto = false)

    @Test fun cameraBeforeSheet() = cameraShot("camera_before", LiveUi(), before = true)

    @Test fun prompts() {
        check(Harness.render("perm") { PermissionPrompt(false, {}, {}) }, 0.003)
        check(Harness.render("perm_blocked") { PermissionPrompt(true, {}, {}) }, 0.003)
        check(Harness.render("nocam") { NoCameraPrompt({}, {}) }, 0.003)
        check(Harness.render("busy") { BusyScreen(androidx.compose.ui.res.stringResource(app.dvphoto.R.string.busy)) }, 0.001)
    }

    @Test fun resultFail() = check(Harness.render("result_fail", heightDp = 1500) {
        ResultScreen(resultUi(false, upload = true), {}, {}, {})
    })

    @Test fun nameOk() = check(Harness.render("name_ok", heightDp = 1050) {
        NameSaveScreen(resultUi(true), nameUi(), {}, {}, {}, {}, {}, {}, {})
    })

    @Test fun nameAsciiAndError() = check(Harness.render("name_ascii", heightDp = 1250) {
        NameSaveScreen(resultUi(true, upload = true), nameUi("Камолиддин Расулов", ascii = true, err = app.dvphoto.R.string.err_save), {}, {}, {}, {}, {}, {}, {})
    })

    @Test fun nameWithProblems() = check(Harness.render("name_fail", heightDp = 1700) {
        NameSaveScreen(resultUi(false), nameUi(saving = true), {}, {}, {}, {}, {}, {}, {})
    })

    @Test fun saved() = check(Harness.render("saved", heightDp = 960) {
        SavedScreen(SavedUi("DV_Applicant.jpg", "Pictures/DVPhoto", 183, Harness.portrait()), {}, {}, {}, {})
    })

    private val langs = listOf(
        LanguageOption("", "System default"), LanguageOption("en", "English"), LanguageOption("ru", "Русский"),
        LanguageOption("uz", "Oʻzbekcha"),
    )

    @Test fun settings() = check(Harness.render("settings", heightDp = 1250) {
        SettingsScreen(SettingsUi("ru", langs, autoCapture = true, voice = false, measurements = true, versionName = "0.2.0"), {}, {}, {}, {}, {}, {})
    })

    @Test fun help() = check(Harness.render("help", heightDp = 2300) { HelpScreen({}, {}, {}) })
}

/** Every important screen in every shipped language, so overflow, clipping and RTL problems show up. */
class LanguageScreenshotTest {
    private val goodReading = FaceReading(
        faceCount = 1, headTop = Guide.headTop, chin = Guide.chin, eyeY = Guide.eyeY, centerX = 0.5f,
        headWidth = Guide.headWidth * 0.9f, roll = 0f, yaw = 0f, pitch = 0f, eyesOpen = 0.95f, smile = 0.1f, headTopMeasured = true,
    )
    private val problems = listOf(
        Check(CheckId.HEAD_SIZE, Status.FAIL, Hint.MOVE_BACK, 0.72f),
        Check(CheckId.BACKGROUND, Status.FAIL, Hint.NOT_NEUTRAL),
        Check(CheckId.EDITING, Status.FAIL, Hint.EDIT_CREDENTIALS),
        Check(CheckId.PHOTO_AGE, Status.FAIL, Hint.AGE_OLD, 8f),
        Check(CheckId.EXPRESSION, Status.WARN, Hint.SMILING),
        Check(CheckId.LIGHTING, Status.FAIL, Hint.UNEVEN_LIGHT),
        Check(CheckId.FILE, Status.PASS, Hint.OK),
    )
    private val langs = listOf<String?>(null, "ru", "uz", "es", "fr", "pt", "tr", "ar", "bn")

    @androidx.compose.runtime.Composable
    private fun Thumb(item: RecentItem, m: Modifier) = Box(m.background(Color(0xFF2E7D5B)), contentAlignment = Alignment.Center) { Text("DV", color = Color.White) }

    @Test fun allLanguages() {
        val results = ResultUi(Harness.portrait(), 1000, 187_000, problems, fromUpload = true, autoCropped = true)
        val ok = ResultUi(Harness.portrait(), 1000, 187_000, listOf(Check(CheckId.FACE, Status.PASS, Hint.OK)), false, false)
        val name = NameUi("DV_Applicant", PhotoRole.APPLICANT, SaveFolder.GALLERY, "DV_Applicant (1).jpg", false, false)
        val settings = SettingsUi("", listOf(LanguageOption("", "System"), LanguageOption("ru", "Русский")), true, false, true, "0.2.0")
        for (lang in langs) {
            val rtl = lang == "ar"
            val tag = lang ?: "en"
            fun shot(n: String, h: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
                val f = Harness.render("lang_${n}", lang, heightDp = h, rtl = rtl, content = content)
                assertTrue("${f.name} blank", Harness.inkRatio(f) > 0.01)
            }
            shot("home", 1200) { HomeScreen(listOf(RecentItem(1, "u", "DV_Applicant.jpg", true)), { i, m -> Thumb(i, m) }, {}, {}, {}, {}, {}) }
            shot("camera", 892) {
                CameraChrome(
                    LiveUi(goodReading.copy(centerX = 0.4f), listOf(Check(CheckId.CENTER, Status.FAIL, Hint.MOVE_RIGHT), Check(CheckId.FACE, Status.PASS, Hint.OK)), 0f),
                    mirrored = false, canFlip = true, autoCapture = true, showMeasurements = false, onClose = {}, onFlip = {}, onShutter = {},
                ) { Image(Harness.portrait(900), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            shot("name", 1100) { NameSaveScreen(ok, name, {}, {}, {}, {}, {}, {}, {}) }
            shot("result", 1500) { ResultScreen(results, {}, {}, {}) }
            shot("settings", 1250) { SettingsScreen(settings, {}, {}, {}, {}, {}, {}) }
            shot("help", 2500) { HelpScreen({}, {}, {}) }
        }
    }
}

/** Every hint and every check title must exist and format correctly in every language. */
class AllHintsTest {
    @Test fun everyHintRendersInEveryLanguage() {
        val all = Hint.values().mapIndexed { i, h ->
            val id = CheckId.values()[i % CheckId.values().size]
            Check(id, if (h == Hint.OK) Status.PASS else Status.FAIL, h, if (h == Hint.AGE_OLD) 8f else 0.6f)
        }
        for (lang in listOf<String?>(null, "ru", "uz", "es", "fr", "pt", "tr", "ar", "bn")) {
            val f = Harness.render("allhints", lang, heightDp = 3300, rtl = lang == "ar") {
                androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) { ChecksCard(all) }
            }
            assertTrue(Harness.inkRatio(f) > 0.02)
        }
        // each check id has a title too
        Harness.render("allchecks", null, heightDp = 1400) {
            androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                ChecksCard(CheckId.values().map { Check(it, Status.PASS, Hint.OK) })
            }
        }
    }

    @Test fun discardSheetAndBusy() {
        val f = Harness.render("discard") {
            Box(Modifier.fillMaxSize()) {
                HelpScreen({}, {}, {})
                ConfirmSheet("Discard this photo?", "You haven’t saved it yet. If you leave now it will be deleted.", "Discard", "Keep editing", {}, {})
            }
        }
        assertTrue(Harness.inkRatio(f) > 0.02)
    }
}
