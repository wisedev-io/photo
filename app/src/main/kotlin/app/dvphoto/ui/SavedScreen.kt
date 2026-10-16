package app.dvphoto.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dvphoto.R

@Composable
fun SavedScreen(
    saved: SavedUi,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onAnother: () -> Unit,
    onDone: () -> Unit,
) {
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.size(88.dp).scale(pop.value).clip(CircleShape).background(Pal.Mint),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Check, null, tint = Pal.Ink, modifier = Modifier.size(48.dp)) }

        Text(stringResource(R.string.saved_title), color = Pal.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)

        Image(
            saved.preview, null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(148.dp).clip(RoundedCornerShape(24.dp)),
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(saved.fileName, color = Pal.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(saved.locationLabel, color = Pal.Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.saved_size, saved.sizeKb), color = Pal.Muted, fontSize = 13.sp)
            }
        }

        AppButton(stringResource(R.string.action_open), onOpen, Modifier.fillMaxWidth())
        AppButton(stringResource(R.string.action_share), onShare, Modifier.fillMaxWidth(), BtnStyle.Secondary, leading = Icons.Default.Share)
        AppButton(stringResource(R.string.action_another), onAnother, Modifier.fillMaxWidth(), BtnStyle.Secondary)
        AppButton(stringResource(R.string.action_home), onDone, Modifier.fillMaxWidth(), BtnStyle.Ghost)
        Text(stringResource(R.string.saved_tip), color = Pal.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 17.sp)
    }
}
