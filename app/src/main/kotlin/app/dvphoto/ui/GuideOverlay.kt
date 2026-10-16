package app.dvphoto.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.dvphoto.core.DvRules
import app.dvphoto.core.FaceReading
import app.dvphoto.core.Guide

enum class Tone { NONE, ADJUST, GOOD }

/**
 * Shapes drawn over the live preview (a square):
 *  - dimmed vignette with a cut-out target head oval
 *  - shoulder guide, eye-line band (the allowed eye range) and centre ticks
 *  - a white outline of YOUR detected head: line it up with the coloured target
 */
@Composable
fun GuideOverlay(reading: FaceReading?, tone: Tone, mirrored: Boolean, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        when (tone) { Tone.NONE -> Color.White.copy(alpha = 0.7f); Tone.ADJUST -> Pal.Amber; Tone.GOOD -> Pal.Mint },
        tween(250), label = "tone",
    )
    val r = reading
    val anim = tween<Float>(160)
    val top by animateFloatAsState(r?.headTop ?: Guide.headTop, anim, label = "top")
    val chin by animateFloatAsState(r?.chin ?: Guide.chin, anim, label = "chin")
    val cx by animateFloatAsState(r?.let { if (mirrored) 1f - it.centerX else it.centerX } ?: 0.5f, anim, label = "cx")
    val hw by animateFloatAsState(r?.headWidth ?: Guide.headWidth, anim, label = "hw")
    val eyeY by animateFloatAsState(r?.eyeY ?: Guide.eyeY, anim, label = "eye")

    Canvas(modifier) {
        val w = size.width
        val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))

        // Vignette with a head-shaped window
        val tw = Guide.headWidth * w
        val th = (Guide.chin - Guide.headTop) * w
        val tl = Offset(w / 2f - tw / 2f, Guide.headTop * w)
        val dim = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, w, w))
            addOval(Rect(tl, Size(tw, th)))
        }
        drawPath(dim, Color.Black.copy(alpha = 0.40f))

        // Allowed eye band + target eye line
        val bandTop = (1f - DvRules.EYE_MAX) * w
        val bandH = (DvRules.EYE_MAX - DvRules.EYE_MIN) * w
        drawRect(color.copy(alpha = 0.10f), Offset(0f, bandTop), Size(w, bandH))
        drawLine(color.copy(alpha = 0.6f), Offset(w * 0.16f, Guide.eyeY * w), Offset(w * 0.84f, Guide.eyeY * w), 2.dp.toPx(), pathEffect = dash)

        // Target head oval
        drawOval(color, tl, Size(tw, th), style = Stroke(3.dp.toPx(), pathEffect = dash))

        // Shoulders
        val shoulders = Path().apply {
            moveTo(0.03f * w, w)
            cubicTo(0.05f * w, 0.86f * w, 0.28f * w, 0.80f * w, 0.40f * w, 0.76f * w)
            moveTo(0.97f * w, w)
            cubicTo(0.95f * w, 0.86f * w, 0.72f * w, 0.80f * w, 0.60f * w, 0.76f * w)
        }
        drawPath(shoulders, color.copy(alpha = 0.55f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

        // Centre ticks
        val tick = 0.035f * w
        drawLine(color.copy(alpha = 0.6f), Offset(w / 2f, 0f), Offset(w / 2f, tick), 2.dp.toPx())
        drawLine(color.copy(alpha = 0.6f), Offset(w / 2f, w - tick), Offset(w / 2f, w), 2.dp.toPx())

        // Your detected head
        if (r != null) {
            val dw = hw * w * 1.08f
            drawOval(
                Color.White,
                Offset(cx * w - dw / 2f, top * w),
                Size(dw, (chin - top) * w),
                style = Stroke(2.dp.toPx()),
            )
            drawCircle(Color.White, 4.dp.toPx(), Offset(cx * w, eyeY * w))
        }
    }
}
