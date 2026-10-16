package app.dvphoto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Bottom sheet asking the user to confirm something. Plain Compose (not a platform dialog) so it looks the same everywhere. */
@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).clickable(onClick = onCancel),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Pal.Surface)
                .clickable(enabled = true) {}.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = Pal.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(body, color = Pal.Muted, fontSize = 15.sp, lineHeight = 21.sp)
            AppButton(confirmText, onConfirm, Modifier.fillMaxWidth(), BtnStyle.Danger)
            AppButton(cancelText, onCancel, Modifier.fillMaxWidth(), BtnStyle.Secondary)
        }
    }
}
