package app.dvphoto.host

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import android.net.Uri
import app.dvphoto.AppViewModel
import app.dvphoto.R
import app.dvphoto.Screen
import app.dvphoto.core.DvRules
import app.dvphoto.platform.LocaleHelper
import app.dvphoto.platform.PhotoStorage
import app.dvphoto.ui.BusyScreen
import app.dvphoto.ui.ConfirmSheet
import app.dvphoto.ui.HelpScreen
import app.dvphoto.ui.HomeScreen
import app.dvphoto.ui.LanguageOption
import app.dvphoto.ui.NameSaveScreen
import app.dvphoto.ui.ResultScreen
import app.dvphoto.ui.SavedScreen
import app.dvphoto.ui.SavedUi
import app.dvphoto.ui.ScreenFrame
import app.dvphoto.ui.SettingsScreen
import app.dvphoto.ui.SettingsUi

/** Connects the view model to the pure screens and to everything only Android can do (pickers, permissions, intents). */
@Composable
fun AppRoot(vm: AppViewModel, versionName: String, onRecreate: () -> Unit) {
    val ctx = LocalContext.current
    val history by vm.history.collectAsState()

    // one-shot toasts and "open this photo" requests
    val message = vm.message
    LaunchedEffect(message) {
        if (message != null) { Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show(); vm.consumeMessage() }
    }
    val openRequest = vm.openRequest
    LaunchedEffect(openRequest) {
        if (openRequest != null) { openImage(ctx, Uri.parse(openRequest)); vm.consumeOpenRequest() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.onPicked(uri) }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri -> if (uri != null) vm.saveAs(uri) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> vm.onStoragePermissionResult(ok) }
    val requestSave = {
        val needs = PhotoStorage.needsLegacyPermission() &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needs) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else vm.save()
    }

    BackHandler(enabled = vm.screen != Screen.Home) { vm.back() }

    ScreenFrame {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Crossfade(targetState = vm.screen, label = "screen") { screen ->
                when (screen) {
                    Screen.Home -> HomeScreen(
                        recent = vm.recentItems(history),
                        thumbnail = { item, modifier -> RecentThumb(item.uri, item.passed, modifier) },
                        onCamera = vm::openCamera,
                        onUpload = { pick() },
                        onSettings = vm::openSettings,
                        onHelp = vm::openHelp,
                        onOpenRecent = vm::openRecent,
                    )
                    Screen.Camera -> CameraHost(vm) { pick() }
                    is Screen.Busy -> BusyScreen(stringResource(screen.label))
                    is Screen.Result -> ResultScreen(
                        result = screen.data.ui,
                        onRetry = { if (screen.data.fromUpload) pick() else vm.openCamera() },
                        onSaveAnyway = { vm.openName(screen.data) },
                        onHome = vm::goHome,
                    )
                    is Screen.Name -> Box(Modifier.fillMaxSize()) {
                        NameSaveScreen(
                            result = screen.data.ui,
                            name = vm.nameUi(),
                            onBack = { vm.back() },
                            onText = vm::onNameText,
                            onRole = vm::onRole,
                            onFolder = vm::onFolder,
                            onAscii = vm::useAscii,
                            onSave = { requestSave() },
                            onSaveAs = { createDocument.launch(vm.nameUi().finalName) },
                        )
                        if (vm.confirmDiscard) {
                            ConfirmSheet(
                                title = stringResource(R.string.discard_title),
                                body = stringResource(R.string.discard_body),
                                confirmText = stringResource(R.string.discard_yes),
                                cancelText = stringResource(R.string.discard_no),
                                onConfirm = vm::goHome,
                                onCancel = vm::dismissDiscard,
                            )
                        }
                    }
                    is Screen.Saved -> {
                        val info = screen.info
                        SavedScreen(
                            saved = SavedUi(
                                fileName = info.fileName,
                                locationLabel = info.location ?: stringResource(R.string.saved_custom),
                                sizeKb = (info.data.jpeg.size + 512) / 1024,
                                preview = info.data.preview,
                            ),
                            onOpen = { info.uri?.let { openImage(ctx, it) } },
                            onShare = { info.uri?.let { shareImage(ctx, it) } },
                            onAnother = vm::openCamera,
                            onDone = vm::goHome,
                        )
                    }
                    Screen.Settings -> {
                        val system = stringResource(R.string.lang_system)
                        SettingsScreen(
                            state = SettingsUi(
                                languageTag = vm.languageTag,
                                languages = LocaleHelper.tags.map { LanguageOption(it, LocaleHelper.nativeName(it) ?: system) },
                                autoCapture = vm.autoCapture,
                                voice = vm.voice,
                                measurements = vm.measurements,
                                versionName = versionName,
                            ),
                            onBack = vm::goHome,
                            onLanguage = { tag -> if (tag != vm.languageTag) { vm.updateLanguage(tag); onRecreate() } },
                            onAutoCapture = vm::updateAutoCapture,
                            onVoice = vm::updateVoice,
                            onMeasurements = vm::updateMeasurements,
                            onOpenOfficial = { openUrl(ctx, DvRules.OFFICIAL_PHOTO_URL) },
                        )
                    }
                    Screen.Help -> HelpScreen(
                        onBack = vm::goHome,
                        onOpenOfficial = { openUrl(ctx, DvRules.OFFICIAL_PHOTO_URL) },
                        onOpenEntry = { openUrl(ctx, DvRules.DV_ENTRY_URL) },
                    )
                }
            }
        }
    }
}
