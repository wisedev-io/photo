package app.dvphoto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dvphoto.R

/**
 * Checklist shown over the camera before the first shot. It is a plain overlay (not a platform dialog) so it
 * looks the same everywhere and can be rendered by the harness.
 */
@Composable
fun BeforeYouStartSheet(onGo: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).clickable(enabled = true) {},
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Pal.Surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.before_title), color = Pal.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            listOf(R.string.before_1, R.string.before_2, R.string.before_3, R.string.before_4).forEach {
                Row(verticalAlignment = Alignment.Top) {
                    Text("•", color = Pal.Mint, fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(it), color = Pal.Text, fontSize = 16.sp, lineHeight = 22.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            AppButton(stringResource(R.string.before_go), onGo, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun PermissionPrompt(blocked: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.perm_title), color = Pal.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.perm_body), color = Pal.Muted, textAlign = TextAlign.Center, lineHeight = 20.sp,
            modifier = Modifier.padding(vertical = 14.dp),
        )
        if (blocked) AppButton(stringResource(R.string.perm_settings), onOpenSettings, Modifier.fillMaxWidth())
        else AppButton(stringResource(R.string.perm_allow), onAllow, Modifier.fillMaxWidth())
    }
}

@Composable
fun NoCameraPrompt(onUpload: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.nocam_title), color = Pal.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.nocam_body), color = Pal.Muted, textAlign = TextAlign.Center, lineHeight = 20.sp,
            modifier = Modifier.padding(vertical = 14.dp),
        )
        AppButton(stringResource(R.string.nocam_upload), onUpload, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        AppButton(stringResource(R.string.action_back), onClose, Modifier.fillMaxWidth(), BtnStyle.Ghost)
    }
}

@Composable
fun BusyScreen(message: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Pal.Mint, modifier = Modifier.size(48.dp))
        Text(message, color = Pal.Muted, modifier = Modifier.padding(top = 20.dp), textAlign = TextAlign.Center)
    }
}
