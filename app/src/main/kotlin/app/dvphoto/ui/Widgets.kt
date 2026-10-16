package app.dvphoto.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

/** Widest a screen's content grows: keeps layouts tidy on tablets, foldables and landscape. */
val MaxContentWidth: Dp = 560.dp

enum class BtnStyle { Primary, Secondary, Ghost, Danger }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BtnStyle = BtnStyle.Primary,
    enabled: Boolean = true,
    leading: ImageVector? = null,
) {
    val bg: Color
    val fg: Color
    when (style) {
        BtnStyle.Primary -> { bg = Pal.Mint; fg = Pal.Ink }
        BtnStyle.Secondary -> { bg = Pal.Surface2; fg = Pal.Text }
        BtnStyle.Ghost -> { bg = Color.Transparent; fg = Pal.Muted }
        BtnStyle.Danger -> { bg = Pal.Coral.copy(alpha = 0.16f); fg = Pal.Coral }
    }
    val a = if (enabled) 1f else 0.4f
    Row(
        modifier
            .heightIn(min = if (style == BtnStyle.Ghost) 44.dp else 54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg.copy(alpha = bg.alpha * a))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = fg.copy(alpha = a), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text, color = fg.copy(alpha = a), fontWeight = FontWeight.Bold, fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Round icon button used in top bars. [description] is read by screen readers. */
@Composable
fun CircleButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Pal.Surface2)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Chevron that points "back" in both reading directions. */
@Composable
fun BackChevron(color: Color = Pal.Text, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val dir = if (rtl) -1f else 1f
        val cx = w / 2f
        val p = Path().apply {
            moveTo(cx + dir * w * 0.14f, h * 0.2f)
            lineTo(cx - dir * w * 0.14f, h * 0.5f)
            lineTo(cx + dir * w * 0.14f, h * 0.8f)
        }
        drawPath(p, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun ScreenTopBar(
    title: String,
    onBack: (() -> Unit)?,
    backDescription: String = "",
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            CircleButton(backDescription, onBack) { BackChevron() }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title, color = Pal.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Pal.Surface)
            .border(1.dp, Pal.Hairline, RoundedCornerShape(24.dp)),
    ) { content() }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Pal.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp, modifier = modifier,
    )
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Compact, accessible on/off switch in the app's style. The tap area is 48dp tall like every other control. */
@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val x by animateDpAsState(if (checked) 24.dp else 3.dp, tween(160), label = "knob")
    val bg by animateColorAsState(if (checked) Pal.Mint else Pal.Surface2, tween(160), label = "track")
    Box(
        modifier
            .size(width = 58.dp, height = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 54.dp, height = 32.dp)
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, if (checked) Pal.Mint else Pal.Muted.copy(alpha = 0.4f), CircleShape),
        ) {
            Box(
                Modifier
                    .offset(x = x, y = 3.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (checked) Pal.Ink else Pal.Muted),
            )
        }
    }
}

@Composable
fun SettingRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Pal.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Pal.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        Toggle(checked, onChange)
    }
}

/** Two or more mutually exclusive options laid out as one rounded control. */
@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Pal.Surface2).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Pal.Mint else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .padding(vertical = 11.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, color = if (on) Pal.Ink else Pal.Text, fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Selectable pill used for the "who is this photo for" choice. */
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        color = if (selected) Pal.Ink else Pal.Text,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Pal.Mint else Pal.Surface2)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Centres [content] and caps its width so phones, tablets and foldables all look right. */
@Composable
fun ScreenFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Pal.Ink), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = MaxContentWidth).fillMaxSize()) { content() }
    }
}

/** Taps anywhere on the content (outside text fields) with no ripple; used to dismiss the keyboard. */
fun Modifier.onTapOutside(action: () -> Unit): Modifier = pointerInput(Unit) { detectTapGestures(onTap = { action() }) }

/** Simple wrapping row (FlowRow is still experimental in some Compose versions). Children keep their natural width. */
@Composable
fun ChipFlow(modifier: Modifier = Modifier, spacing: Dp = 8.dp, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val maxW = constraints.maxWidth
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val rows = ArrayList<List<Placeable>>()
        var cur = ArrayList<Placeable>()
        var curW = 0
        for (p in placeables) {
            val needed = if (cur.isEmpty()) p.width else curW + gap + p.width
            if (needed > maxW && cur.isNotEmpty()) {
                rows.add(cur); cur = ArrayList(); curW = 0
            }
            curW = if (cur.isEmpty()) p.width else curW + gap + p.width
            cur.add(p)
        }
        if (cur.isNotEmpty()) rows.add(cur)
        val rowHeights = rows.map { r -> r.maxOf { it.height } }
        val height = rowHeights.sum() + gap * (rows.size - 1).coerceAtLeast(0)
        layout(maxW, height) {
            var y = 0
            rows.forEachIndexed { i, r ->
                var x = 0
                r.forEach { p -> p.placeRelative(x, y); x += p.width + gap }
                y += rowHeights[i] + gap
            }
        }
    }
}
