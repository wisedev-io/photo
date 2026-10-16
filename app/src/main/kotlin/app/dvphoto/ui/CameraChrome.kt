package app.dvphoto.ui

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dvphoto.R
import app.dvphoto.core.Status
import app.dvphoto.core.Validator
import kotlin.math.roundToInt

/** What the instruction pill says right now: the single most important thing to fix, or "hold still". */
@Composable
fun instructionText(live: LiveUi): String {
    val problem = Validator.firstProblem(live.checks)
    return when {
        problem != null -> problem.hintText()
        live.reading == null -> stringResource(R.string.cam_looking)
        else -> stringResource(R.string.cam_perfect)
    }
}

fun toneOf(live: LiveUi): Tone = when {
    live.reading == null -> Tone.NONE
    Validator.firstProblem(live.checks) != null -> Tone.ADJUST
    else -> Tone.GOOD
}

@Composable
fun CameraChrome(
    live: LiveUi,
    mirrored: Boolean,
    canFlip: Boolean,
    autoCapture: Boolean,
    showMeasurements: Boolean,
    manualOnly: Boolean = false,
    onClose: () -> Unit,
    onFlip: () -> Unit,
    onShutter: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val tone = toneOf(live)
    val pillColor by animateColorAsState(
        when (tone) { Tone.GOOD -> Pal.Mint; Tone.ADJUST -> Pal.Amber; Tone.NONE -> Pal.Surface2 }, tween(250), label = "pill",
    )
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(stringResource(R.string.action_close), onClose) {
                    Icon(Icons.Default.Close, null, tint = Pal.Text, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                if (canFlip) {
                    Row(
                        Modifier.clip(CircleShape).background(Pal.Surface2)
                            .clickable(role = Role.Button, onClick = onFlip)
                            .heightIn(min = 48.dp).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = Pal.Text, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.cam_flip), color = Pal.Text, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }

            // Instruction pill (announced politely by screen readers)
            Box(
                Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(20.dp)).background(pillColor)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (manualOnly) stringResource(R.string.cam_manual_only) else instructionText(live), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    color = if (tone == Tone.NONE || manualOnly) Pal.Text else Pal.Ink,
                )
            }

            // Square viewfinder + guide shapes
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(Color.Black)) {
                preview()
                GuideOverlay(live.reading, tone, mirrored, Modifier.fillMaxSize())
                if (showMeasurements) {
                    MeasurementsBadge(live, Modifier.align(Alignment.TopStart).padding(10.dp))
                }
                // a constant reminder that nothing is being filtered or edited
                Text(
                    "✓ " + stringResource(R.string.cam_nofilters), color = Pal.Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }

            // Status chips (wrap onto a second line on narrow screens or with large text)
            ChipFlow(Modifier.fillMaxWidth().padding(top = 14.dp), spacing = 6.dp) {
                Group.values().forEach { g ->
                    val inGroup = live.checks.filter { it.id in g.ids }
                    val c = when {
                        inGroup.isEmpty() -> Pal.Muted
                        inGroup.any { it.status == Status.FAIL } -> Pal.Amber
                        else -> Pal.Mint
                    }
                    Box(
                        Modifier.widthIn(min = 62.dp).clip(RoundedCornerShape(12.dp))
                            .border(1.dp, c.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .background(c.copy(alpha = 0.12f)).padding(vertical = 8.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(g.label), color = c, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Shutter(progress = live.progress, onClick = onShutter, description = stringResource(R.string.cam_shutter))
            Text(
                stringResource(if (autoCapture) R.string.cam_hint_auto else R.string.cam_hint_manual),
                color = Pal.Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
            )
        }
    }
}

@Composable
private fun MeasurementsBadge(live: LiveUi, modifier: Modifier) {
    val r = live.reading
    val s = live.scene
    val text = if (r == null) "–" else buildString {
        append("head ${(r.headHeight * 100).roundToInt()}%${if (r.headTopMeasured) "m" else "e"}")
        append("  eyes ${(r.eyeFromBottom * 100).roundToInt()}%")
        append("  x ${((r.centerX - 0.5f) * 100).roundToInt()}")
        if (s != null) append("\nbg ${s.bgLuma.roundToInt()}±${s.bgStd.roundToInt()}  face ${s.faceLuma.roundToInt()}")
        append("\nroll ${r.roll.roundToInt()} yaw ${r.yaw.roundToInt()} pitch ${r.pitch.roundToInt()}")
    }
    Text(
        text, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 13.sp,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(6.dp),
    )
}

@Composable
private fun Shutter(progress: Float, onClick: () -> Unit, description: String) {
    val p by animateFloatAsState(progress, tween(180), label = "shutter")
    Box(
        Modifier.size(84.dp).clip(CircleShape)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
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
