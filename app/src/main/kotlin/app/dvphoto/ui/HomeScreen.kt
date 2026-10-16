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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dvphoto.R
import app.dvphoto.data.PhotoRecord

@Composable
fun HomeScreen(history: List<PhotoRecord>, onCamera: () -> Unit, onUpload: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("DV Photo", color = Pal.Mint, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text(
            stringResource(R.string.home_tagline), color = Pal.Text, fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp,
        )

        // Hero: the main action
        Box(
            Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(32.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4B5BFF), Color(0xFF16B8A0))))
                .clickable(onClick = onCamera),
        ) {
            HeroArt(Modifier.align(Alignment.CenterEnd).size(150.dp).padding(end = 12.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                Text(stringResource(R.string.home_take), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    stringResource(R.string.home_take_sub), color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, end = 120.dp),
                )
            }
        }

        // Secondary: upload
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Pal.Surface2)
                .clickable(onClick = onUpload).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Pal.Indigo.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, null, tint = Pal.Indigo)
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(stringResource(R.string.home_upload), color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(stringResource(R.string.home_upload_sub), color = Pal.Muted, fontSize = 13.sp)
            }
        }

        // Rules
        Text(stringResource(R.string.home_rules), color = Pal.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RuleChip(R.string.rule_size, Modifier.weight(1f))
            RuleChip(R.string.rule_file, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RuleChip(R.string.rule_head, Modifier.weight(1f))
            RuleChip(R.string.rule_bg, Modifier.weight(1f))
        }

        if (history.isNotEmpty()) {
            Text(stringResource(R.string.home_recent), color = Pal.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(history, key = { it.id }) { rec -> Thumb(rec.uri, Modifier.size(96.dp)) }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.home_privacy), color = Pal.Muted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun RuleChip(text: Int, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(Pal.Surface).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(stringResource(text), color = Pal.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
