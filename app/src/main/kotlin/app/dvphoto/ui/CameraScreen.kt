package app.dvphoto.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size as AndroidSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.dvphoto.AppViewModel
import app.dvphoto.R
import app.dvphoto.camera.LiveAnalyzer
import app.dvphoto.core.Status
import app.dvphoto.core.Validator
import java.util.concurrent.Executors

@Composable
fun CameraScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    var acknowledged by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::goHome) { Icon(Icons.Default.Close, null, tint = Pal.Text) }
            Spacer(Modifier.weight(1f))
            if (granted) {
                Row(
                    Modifier.clip(CircleShape).clickable { vm.setMirrored(!vm.mirrored) }
                        .background(Pal.Surface2).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Refresh, null, tint = Pal.Text, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.cam_flip), color = Pal.Text, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        if (granted) LiveCamera(vm) else PermissionPrompt { launcher.launch(Manifest.permission.CAMERA) }
    }

    if (!acknowledged) BeforeYouStart { acknowledged = true }
}

private fun selector(w: Int, h: Int) = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(ResolutionStrategy(AndroidSize(w, h), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
    .build()

@Composable
private fun ColumnScope.LiveCamera(vm: AppViewModel) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val cam = remember {
        LifecycleCameraController(ctx).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            setImageAnalysisResolutionSelector(selector(640, 480))
            setImageCaptureResolutionSelector(selector(1600, 1200))
            cameraSelector = if (vm.mirrored) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            setImageAnalysisAnalyzer(analysisExecutor, LiveAnalyzer(vm.liveDetector, vm::onLiveFrame))
        }
    }
    DisposableEffect(owner) {
        cam.bindToLifecycle(owner)
        onDispose { cam.unbind(); analysisExecutor.shutdown() }
    }
    LaunchedEffect(vm.mirrored) {
        cam.cameraSelector = if (vm.mirrored) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    }
    LaunchedEffect(vm.shutterTick) {
        if (vm.shutterTick > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            cam.takePicture(ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) = vm.onCaptured(image)
                override fun onError(exception: ImageCaptureException) = vm.onCaptureFailed()
            })
        }
    }

    val live = vm.live
    val problem = Validator.firstProblem(live.checks)
    val tone = when {
        live.reading == null -> Tone.NONE
        problem != null -> Tone.ADJUST
        else -> Tone.GOOD
    }
    val instruction = when {
        problem != null -> problem.hint.res()
        live.reading == null -> R.string.cam_looking
        else -> R.string.cam_perfect
    }
    val pillColor by animateColorAsState(
        when (tone) { Tone.GOOD -> Pal.Mint; Tone.ADJUST -> Pal.Amber; Tone.NONE -> Pal.Surface2 }, tween(250), label = "pill",
    )

    // Instruction pill
    Box(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(20.dp)).background(pillColor)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(instruction), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
            color = if (tone == Tone.NONE) Pal.Text else Pal.Ink,
        )
    }

    // Square viewfinder + guide shapes
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(Color.Black),
    ) {
        AndroidView(
            factory = { c ->
                PreviewView(c).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    controller = cam
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        GuideOverlay(live.reading, tone, vm.mirrored, Modifier.fillMaxSize())
    }

    // Check chips
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Group.entries.forEach { g ->
            val inGroup = live.checks.filter { it.id in g.ids }
            val c = when {
                inGroup.isEmpty() -> Pal.Muted
                inGroup.any { it.status == Status.FAIL } -> Pal.Amber
                else -> Pal.Mint
            }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).border(1.dp, c.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .background(c.copy(alpha = 0.12f)).padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(g.label), color = c, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
    }

    Spacer(Modifier.weight(1f))
    Shutter(progress = live.progress, onClick = vm::requestShutter)
    Text(
        stringResource(R.string.cam_shutter_hint), color = Pal.Muted, fontSize = 12.sp,
        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
    )
}

@Composable
private fun Shutter(progress: Float, onClick: () -> Unit) {
    val p by animateFloatAsState(progress, tween(180), label = "shutter")
    Box(Modifier.size(84.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 6.dp.toPx()
            drawCircle(Pal.Surface2, style = Stroke(sw))
            drawArc(
                Pal.Mint, -90f, 360f * p, false, topLeft = Offset(sw / 2, sw / 2),
                size = Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
        Box(Modifier.size(62.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun PermissionPrompt(onAllow: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.cam_perm_title), color = Pal.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.cam_perm_body), color = Pal.Muted, textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Button(onClick = onAllow) { Text(stringResource(R.string.cam_perm_button)) }
    }
}

@Composable
private fun BeforeYouStart(onGo: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = Pal.Surface,
        title = { Text(stringResource(R.string.before_title), color = Pal.Text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(R.string.before_1, R.string.before_2, R.string.before_3, R.string.before_4).forEach {
                    Row {
                        Text("•", color = Pal.Mint, modifier = Modifier.padding(end = 10.dp))
                        Text(stringResource(it), color = Pal.Text)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onGo, colors = ButtonDefaults.buttonColors(containerColor = Pal.Mint, contentColor = Pal.Ink)) {
                Text(stringResource(R.string.before_go), fontWeight = FontWeight.Bold)
            }
        },
    )
}
