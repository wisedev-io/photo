package app.dvphoto.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dvphoto.R
import app.dvphoto.core.DvRules
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    recent: List<RecentItem>,
    thumbnail: @Composable (RecentItem, Modifier) -> Unit,
    onCamera: () -> Unit,
    onUpload: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onOpenRecent: (RecentItem) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.app_name).uppercase(), color = Pal.Mint, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.weight(1f),
            )
            CircleButton(stringResource(R.string.help_title), onHelp) {
                Text("?", color = Pal.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            CircleButton(stringResource(R.string.settings_title), onSettings) {
                Icon(Icons.Default.Settings, null, tint = Pal.Text, modifier = Modifier.size(22.dp))
            }
        }

        Text(
            stringResource(R.string.home_tagline), color = Pal.Text, fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold, lineHeight = 35.sp,
        )

        // Hero: the main action. The text keeps clear of the artwork and the card grows with long translations.
        Box(
            Modifier.fillMaxWidth().heightIn(min = 200.dp).clip(RoundedCornerShape(32.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4B5BFF), Color(0xFF16B8A0))))
                .clickable(role = Role.Button, onClick = onCamera),
        ) {
            HeroArt(Modifier.align(Alignment.CenterEnd).size(130.dp).padding(end = 12.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, top = 24.dp, bottom = 24.dp, end = 150.dp)) {
                Text(stringResource(R.string.home_take), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp)
                Text(
                    stringResource(R.string.home_take_sub), color = Color.White.copy(alpha = 0.88f),
                    fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // Secondary: upload an existing photo
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Pal.Surface2)
                .clickable(role = Role.Button, onClick = onUpload).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Pal.Indigo.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Add, null, tint = Pal.Indigo) }
            Column(Modifier.padding(start = 14.dp)) {
                Text(stringResource(R.string.home_upload), color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(stringResource(R.string.home_upload_sub), color = Pal.Muted, fontSize = 13.sp)
            }
        }

        // How it works
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Step(1, R.string.home_step1, Modifier.weight(1f))
            Step(2, R.string.home_step2, Modifier.weight(1f))
            Step(3, R.string.home_step3, Modifier.weight(1f))
        }

        SectionLabel(stringResource(R.string.home_rules))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RuleChip(R.string.rule_size, Modifier.weight(1f))
            RuleChip(R.string.rule_file, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RuleChip(R.string.rule_head, Modifier.weight(1f), (DvRules.HEAD_MIN * 100).roundToInt(), (DvRules.HEAD_MAX * 100).roundToInt())
            RuleChip(R.string.rule_bg, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RuleChip(R.string.rule_nofilters, Modifier.weight(1f))
            RuleChip(R.string.rule_recent, Modifier.weight(1f))
        }

        if (recent.isNotEmpty()) {
            SectionLabel(stringResource(R.string.home_recent))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recent, key = { it.id }) { item ->
                    Column(Modifier.width(96.dp).clickable(role = Role.Button) { onOpenRecent(item) }) {
                        thumbnail(item, Modifier.size(96.dp))
                        Text(
                            item.name, color = Pal.Muted, fontSize = 11.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.home_privacy), color = Pal.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Step(number: Int, text: Int, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Pal.Surface).padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Pal.Mint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text("$number", color = Pal.Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            stringResource(text), color = Pal.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RuleChip(text: Int, modifier: Modifier, vararg args: Any) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Pal.Surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(stringResource(text, *args), color = Pal.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Decorative head-and-shoulders guide, echoing the camera overlay. */
@Composable
private fun HeroArt(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        val c = Color.White.copy(alpha = 0.9f)
        drawOval(c, Offset(w * 0.3f, w * 0.05f), Size(w * 0.4f, w * 0.52f), style = stroke)
        val shoulders = Path().apply {
            moveTo(w * 0.08f, w)
            cubicTo(w * 0.1f, w * 0.78f, w * 0.3f, w * 0.7f, w * 0.5f, w * 0.7f)
            cubicTo(w * 0.7f, w * 0.7f, w * 0.9f, w * 0.78f, w * 0.92f, w)
        }
        drawPath(shoulders, c, style = stroke)
        drawLine(c.copy(alpha = 0.6f), Offset(w * 0.2f, w * 0.28f), Offset(w * 0.8f, w * 0.28f), strokeWidth = 2.dp.toPx())
    }
}
