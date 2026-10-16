package app.dvphoto.host

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.dvphoto.AppViewModel
import app.dvphoto.platform.CameraSession
import app.dvphoto.platform.LiveAnalyzer
import app.dvphoto.platform.Speaker
import app.dvphoto.ui.BeforeYouStartSheet
import app.dvphoto.ui.CameraChrome
import app.dvphoto.ui.NoCameraPrompt
import app.dvphoto.ui.PermissionPrompt
import app.dvphoto.ui.instructionText
import kotlin.math.hypot
import kotlinx.coroutines.delay

private fun hasCameraPermission(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
fun CameraHost(vm: AppViewModel, onPick: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(hasCameraPermission(ctx)) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; asked = true }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    // coming back from the system settings page
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) granted = hasCameraPermission(ctx) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val hasCamera = remember { ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    if (!hasCamera) {
        NoCameraPrompt(onUpload = { vm.goHome(); onPick() }, onClose = vm::goHome)
    } else if (!granted) {
        val activity = ctx as? Activity
        val blocked = asked && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        PermissionPrompt(
            blocked = blocked,
            onAllow = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { openAppSettings(ctx) },
        )
    } else {
        LiveCamera(vm)
    }
}

@Composable
private fun LiveCamera(vm: AppViewModel) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    // keep the screen awake while framing
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val session = remember { CameraSession(ctx, LiveAnalyzer(vm.liveFaceDetector(), vm::onLiveFrame)) }
    DisposableEffect(session) { onDispose { session.release() } }

    val pv = previewView
    LaunchedEffect(pv, vm.mirrored) {
        if (pv != null) session.bind(owner, pv, vm.mirrored, vm::onCameraState)
    }

    // shutter: automatic (all checks green and steady) or the button
    LaunchedEffect(vm.shutterTick) {
        if (vm.shutterTick > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            session.takePicture(onOk = { vm.onCaptured(it) }, onFail = { vm.onCaptureFailed() })
        }
    }

    // meter focus and exposure on the face; a bright white wall would otherwise darken it
    LaunchedEffect(pv) {
        var lastX = -1f
        var lastY = -1f
        var lastAt = 0L
        while (true) {
            delay(700)
            val r = vm.live.reading
            val v = pv
            if (r != null && v != null && v.width > 0 && v.height > 0) {
                val x = (if (vm.mirrored) 1f - r.centerX else r.centerX) * v.width
                val y = r.eyeY * v.height
                val now = SystemClock.elapsedRealtime()
                val moved = lastAt == 0L || hypot(x - lastX, y - lastY) > 0.08f * v.width
                if (moved || now - lastAt > 4_000) {
                    session.meterAt(v, x, y)
                    lastX = x; lastY = y; lastAt = now
                }
            }
        }
    }

    // spoken guidance
    val speaker = remember(vm.voice, vm.languageTag) { if (vm.voice) Speaker(ctx, vm.languageTag) else null }
    DisposableEffect(speaker) { onDispose { speaker?.shutdown() } }
    val instruction = instructionText(vm.live)
    val speakIt = vm.live.reading != null && vm.briefed
    LaunchedEffect(instruction, speaker, speakIt) {
        if (speaker != null && speakIt) {
            delay(500) // only say what is still true half a second later
            speaker.speak(instruction, SystemClock.elapsedRealtime())
        }
    }

    val state = vm.cameraState
    val liveAnalysis = state?.liveAnalysis != false
    Box(Modifier.fillMaxSize()) {
        CameraChrome(
            live = vm.live,
            mirrored = vm.mirrored,
            canFlip = state != null && state.hasFront && state.hasBack,
            autoCapture = vm.autoCapture && liveAnalysis,
            showMeasurements = vm.measurements && liveAnalysis,
            manualOnly = !liveAnalysis,
            onClose = vm::goHome,
            onFlip = vm::flipCamera,
            onShutter = vm::requestShutter,
        ) {
            AndroidView(
                factory = { c ->
                    PreviewView(c).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                update = { if (previewView !== it) previewView = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!vm.briefed) BeforeYouStartSheet { vm.briefed = true }
    }
}
